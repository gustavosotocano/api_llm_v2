package com.enterprise.agentapi.ai;

import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.AgentRateLimitExceededException;
import com.enterprise.agentapi.agent.AgentRateLimitSupport;
import com.enterprise.agentapi.agent.AgentRateLimiter;
import com.enterprise.agentapi.agent.BudgetExceededException;
import com.enterprise.agentapi.agent.ExecutionBudgetService;
import com.enterprise.agentapi.agent.RateLimitScope;
import com.enterprise.agentapi.agent.ToolAccessDeniedException;
import com.enterprise.agentapi.agent.ToolAccessPolicy;
import com.enterprise.agentapi.application.IdentityGuard;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.observability.AgentAuditService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class AgentToolSupport {
    public static final String TOOL_CONTRACT_VERSION = "2.0.0";

    private final AgentRateLimiter rateLimiter;
    private final ExecutionBudgetService budgetService;
    private final AgentAuditService auditService;
    private final ToolAccessPolicy accessPolicy;
    private final List<ToolInvocationListener> listeners;

    public AgentToolSupport(AgentRateLimiter rateLimiter,
                            ExecutionBudgetService budgetService,
                            AgentAuditService auditService,
                            ToolAccessPolicy accessPolicy,
                            List<ToolInvocationListener> listeners) {
        this.rateLimiter = rateLimiter;
        this.budgetService = budgetService;
        this.auditService = auditService;
        this.accessPolicy = accessPolicy;
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
    }

    public <T> T execute(String toolName, Map<String, Object> inferredParameters, Supplier<T> action) {
        var context = AgentContextHolder.get();
        if (context == null) {
            throw new IllegalStateException("Agent context is not set for this tool invocation");
        }
        if (!accessPolicy.allows(context.workflow(), toolName)) {
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_WORKFLOW_DENIED", Map.of(
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
        try {
            rateLimiter.checkAllowed(context.agentSessionId(), toolName, context.userId(), context.channel());
            budgetService.consume(context.agentSessionId(), toolName, context.userId(), context.channel());
        } catch (AgentRateLimitExceededException ex) {
            var eventType = ex.scope() == RateLimitScope.LOOP ? "TOOL_LOOP_BLOCKED" : "TOOL_RATE_LIMITED";
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    eventType, Map.of(
                            "tool", toolName,
                            "toolVersion", TOOL_CONTRACT_VERSION,
                            "scope", ex.scope().name(),
                            "retryAfterSeconds", ex.retryAfterSeconds()));
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters,
                    AgentRateLimitSupport.semanticStatus(ex), null, false));
            throw ex;
        } catch (BudgetExceededException ex) {
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_BUDGET_BLOCKED", Map.of(
                            "tool", toolName,
                            "toolVersion", TOOL_CONTRACT_VERSION,
                            "usedUnits", ex.usedUnits(),
                            "maxUnits", ex.maxUnits()));
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters,
                    SemanticStatus.BUDGET_EXCEEDED, null, false));
            throw ex;
        }

        var startedAt = System.nanoTime();
        auditService.ai(context.agentSessionId(), context.userId(), context.channel(),
                "TOOL_INVOCATION", Map.of(
                        "tool", toolName,
                        "toolVersion", TOOL_CONTRACT_VERSION,
                        "identityType", context.identityType().name(),
                        "onBehalfOfUserId", context.onBehalfOfUserId() == null ? "none" : context.onBehalfOfUserId(),
                        "scopes", context.scopes().stream().map(Enum::name).toList(),
                        "workflow", context.workflow().name(),
                        "inferredParameters", inferredParameters));

        try {
            var result = action.get();
            var durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_COMPLETED", Map.of(
                            "tool", toolName,
                            "toolVersion", TOOL_CONTRACT_VERSION,
                            "durationMs", durationMs,
                            "success", true));
            notifyListeners(new ToolInvocation(toolName, TOOL_CONTRACT_VERSION, inferredParameters,
                    extractStatus(result), result, true));
            return result;
        } catch (RuntimeException ex) {
            var durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_FAILED", Map.of(
                            "tool", toolName,
                            "toolVersion", TOOL_CONTRACT_VERSION,
                            "durationMs", durationMs,
                            "success", false,
                            "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            throw ex;
        }
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
        auditService.business(context.agentSessionId(), context.userId(), context.channel(),
                action, Map.of(
                        "status", status.name(),
                        "toolVersion", TOOL_CONTRACT_VERSION,
                        "details", details));
    }
}
