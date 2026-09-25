package com.enterprise.agentapi.ai;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.AgentRateLimitExceededException;
import com.enterprise.agentapi.agent.AgentRateLimitSupport;
import com.enterprise.agentapi.agent.AgentRateLimiter;
import com.enterprise.agentapi.agent.BudgetExceededException;
import com.enterprise.agentapi.agent.ExecutionBudgetService;
import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.agent.RateLimitScope;
import com.enterprise.agentapi.agent.RetryBudgetExceededException;
import com.enterprise.agentapi.agent.RetryBudgetService;
import com.enterprise.agentapi.agent.ToolAccessDeniedException;
import com.enterprise.agentapi.agent.ToolAccessPolicy;
import com.enterprise.agentapi.agent.ToolCallIds;
import com.enterprise.agentapi.application.IdentityGuard;
import com.enterprise.agentapi.domain.OperationRetry;
import com.enterprise.agentapi.domain.RetryDisposition;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.observability.AgentAuditService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class AgentToolSupport {
    public static final String TOOL_CONTRACT_VERSION = "2.0.0";

    private final AgentRateLimiter rateLimiter;
    private final ExecutionBudgetService budgetService;
    private final RetryBudgetService retryBudget;
    private final AgentAuditService auditService;
    private final ToolAccessPolicy accessPolicy;
    private final List<ToolInvocationListener> listeners;

    public AgentToolSupport(AgentRateLimiter rateLimiter,
                            ExecutionBudgetService budgetService,
                            AgentAuditService auditService,
                            ToolAccessPolicy accessPolicy,
                            List<ToolInvocationListener> listeners,
                            RetryBudgetService retryBudget) {
        this.rateLimiter = rateLimiter;
        this.budgetService = budgetService;
        this.auditService = auditService;
        this.accessPolicy = accessPolicy;
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.retryBudget = retryBudget;
    }

    public <T> T execute(String toolName, Map<String, Object> inferredParameters, Supplier<T> action) {
        var context = AgentContextHolder.get();
        if (context == null) {
            throw new IllegalStateException("Agent context is not set for this tool invocation");
        }
        var openedTrace = OperationTrace.enterpriseRequestId() == null;
        if (openedTrace) {
            OperationTrace.begin(null);
        }
        var toolCallId = ToolCallIds.open();
        try {
            return executeWithCallId(toolName, inferredParameters, action, context, toolCallId);
        } finally {
            ToolCallIds.clear();
            if (openedTrace) {
                OperationTrace.clear();
            }
        }
    }

    private <T> T executeWithCallId(String toolName, Map<String, Object> inferredParameters, Supplier<T> action,
                                    AgentContext context, String toolCallId) {
        if (!accessPolicy.allows(context.workflow(), toolName)) {
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_WORKFLOW_DENIED", Map.of(
                            "toolCallId", toolCallId,
                            "tool", toolName,
                            "toolVersion", TOOL_CONTRACT_VERSION,
                            "workflow", context.workflow().name()));
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters,
                    SemanticStatus.INSUFFICIENT_PERMISSIONS, null, false));
            throw new ToolAccessDeniedException(toolName, context.workflow());
        }
        if (IdentityGuard.authorizeCapability(toolName) != null) {
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_IDENTITY_DENIED", Map.of(
                            "toolCallId", toolCallId,
                            "tool", toolName,
                            "toolVersion", TOOL_CONTRACT_VERSION,
                            "identityType", context.identityType().name(),
                            "onBehalfOfUserId", context.onBehalfOfUserId() == null ? "none" : context.onBehalfOfUserId(),
                            "scopes", context.scopes().stream().map(Enum::name).toList()));
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters,
                    SemanticStatus.INSUFFICIENT_PERMISSIONS, null, false));
            throw new ToolAccessDeniedException(toolName, context.workflow(),
                    "Identity " + context.identityType() + " lacks scope for tool " + toolName);
        }
        OperationTrace.markDownstreamBaseline();
        var estimatedCost = budgetService.costOf(toolName);
        int retryCount;
        try {
            retryCount = retryBudget.begin(context.agentSessionId(), toolName, inferredParameters);
        } catch (RetryBudgetExceededException ex) {
            var blocked = trace(toolName, toolCallId, ex.retryCount(), estimatedCost, 0);
            blocked.put("maxRetries", ex.maxRetries());
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_RETRY_BLOCKED", blocked);
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters,
                    SemanticStatus.RETRY_BUDGET_EXCEEDED, null, false));
            throw ex;
        }
        OperationTrace.noteAttempt(retryCount, estimatedCost, 0);
        try {
            rateLimiter.checkAllowed(context.agentSessionId(), toolName, context.userId(), context.channel());
            budgetService.consume(context.agentSessionId(), toolName, context.userId(), context.channel());
            OperationTrace.noteAttempt(retryCount, estimatedCost, estimatedCost);
        } catch (AgentRateLimitExceededException ex) {
            retryBudget.observe(context.agentSessionId(), toolName, inferredParameters, RetryDisposition.RETRY_AFTER);
            var eventType = ex.scope() == RateLimitScope.LOOP ? "TOOL_LOOP_BLOCKED" : "TOOL_RATE_LIMITED";
            var limited = trace(toolName, toolCallId, retryCount, estimatedCost, 0);
            limited.put("scope", ex.scope().name());
            limited.put("retryAfterSeconds", ex.retryAfterSeconds());
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(), eventType, limited);
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters,
                    AgentRateLimitSupport.semanticStatus(ex), null, false));
            throw ex;
        } catch (BudgetExceededException ex) {
            retryBudget.observe(context.agentSessionId(), toolName, inferredParameters, RetryDisposition.DO_NOT_RETRY);
            var blocked = trace(toolName, toolCallId, retryCount, estimatedCost, 0);
            blocked.put("usedUnits", ex.usedUnits());
            blocked.put("maxUnits", ex.maxUnits());
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_BUDGET_BLOCKED", blocked);
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters,
                    SemanticStatus.BUDGET_EXCEEDED, null, false));
            throw ex;
        }

        var startedAt = System.nanoTime();
        var invocation = trace(toolName, toolCallId, retryCount, estimatedCost, estimatedCost);
        invocation.put("identityType", context.identityType().name());
        invocation.put("onBehalfOfUserId", context.onBehalfOfUserId() == null ? "none" : context.onBehalfOfUserId());
        invocation.put("scopes", context.scopes().stream().map(Enum::name).toList());
        invocation.put("workflow", context.workflow().name());
        invocation.put("inferredParameters", inferredParameters);
        auditService.ai(context.agentSessionId(), context.userId(), context.channel(), "TOOL_INVOCATION", invocation);

        try {
            var result = action.get();
            var durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            var status = extractStatus(result);
            if (status != null) {
                retryBudget.observe(context.agentSessionId(), toolName, inferredParameters,
                        OperationRetry.forStatus(status, null).disposition());
            }
            var completed = trace(toolName, toolCallId, retryCount, estimatedCost, estimatedCost);
            completed.put("durationMs", durationMs);
            completed.put("executionDurationMs", durationMs);
            completed.put("success", true);
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_COMPLETED", completed);
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters, status, result, true));
            return result;
        } catch (RuntimeException ex) {
            retryBudget.observe(context.agentSessionId(), toolName, inferredParameters, RetryDisposition.RETRY_AFTER);
            var durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            var failed = trace(toolName, toolCallId, retryCount, estimatedCost, estimatedCost);
            failed.put("durationMs", durationMs);
            failed.put("executionDurationMs", durationMs);
            failed.put("success", false);
            failed.put("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(), "TOOL_FAILED", failed);
            throw ex;
        }
    }

    private static Map<String, Object> trace(String toolName, String toolCallId, int retryCount,
                                             int estimatedCostUnits, int actualCostUnits) {
        var attributes = new LinkedHashMap<String, Object>();
        attributes.put("toolCallId", toolCallId);
        attributes.put("tool", toolName);
        attributes.put("toolVersion", TOOL_CONTRACT_VERSION);
        attributes.put("enterpriseRequestId", OperationTrace.enterpriseRequestId() == null
                ? "none" : OperationTrace.enterpriseRequestId());
        attributes.put("retryCount", retryCount);
        attributes.put("estimatedCostUnits", estimatedCostUnits);
        attributes.put("actualCostUnits", actualCostUnits);
        attributes.put("downstreamCallCount", OperationTrace.downstreamCallCount());
        attributes.put("executionDurationMs", 0L);
        return attributes;
    }

    private void notifyListeners(ToolInvocation invocation) {
        for (var listener : listeners) {
            listener.onInvocation(invocation);
        }
    }

    private static SemanticStatus extractStatus(Object result) {
        if (result == null) {
            return null;
        }
        try {
            var value = result.getClass().getMethod("status").invoke(result);
            return value instanceof SemanticStatus status ? status : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public void logBusinessAction(String action, SemanticStatus status, Map<String, Object> details) {
        var context = AgentContextHolder.get();
        if (context == null) {
            throw new IllegalStateException("Agent context is not set for this tool invocation");
        }
        var attributes = new LinkedHashMap<String, Object>();
        attributes.put("status", status.name());
        attributes.put("toolVersion", TOOL_CONTRACT_VERSION);
        attributes.put("toolCallId", ToolCallIds.current() == null ? "none" : ToolCallIds.current());
        attributes.put("enterpriseRequestId", OperationTrace.enterpriseRequestId() == null
                ? "none" : OperationTrace.enterpriseRequestId());
        attributes.put("retryCount", OperationTrace.retryCount());
        attributes.put("estimatedCostUnits", OperationTrace.estimatedCostUnits());
        attributes.put("actualCostUnits", OperationTrace.actualCostUnits());
        attributes.put("downstreamCallCount", OperationTrace.downstreamCallCount());
        attributes.put("identityType", context.identityType().name());
        attributes.put("onBehalfOfUserId", context.onBehalfOfUserId() == null ? "none" : context.onBehalfOfUserId());
        attributes.put("workflow", context.workflow().name());
        attributes.put("details", details == null ? Map.of() : details);
        auditService.business(context.agentSessionId(), context.userId(), context.channel(), action, attributes);
    }
}
