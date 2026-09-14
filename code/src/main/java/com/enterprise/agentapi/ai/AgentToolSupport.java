package com.enterprise.agentapi.ai;

import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.AgentRateLimitExceededException;
import com.enterprise.agentapi.agent.AgentRateLimiter;
import com.enterprise.agentapi.agent.BudgetExceededException;
import com.enterprise.agentapi.agent.ExecutionBudgetService;
import com.enterprise.agentapi.agent.RateLimitScope;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.observability.AgentAuditService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class AgentToolSupport {
    public static final String TOOL_CONTRACT_VERSION = "2.0.0";

    private final AgentRateLimiter rateLimiter;
    private final ExecutionBudgetService budgetService;
    private final AgentAuditService auditService;

    public AgentToolSupport(AgentRateLimiter rateLimiter,
                            ExecutionBudgetService budgetService,
                            AgentAuditService auditService) {
        this.rateLimiter = rateLimiter;
        this.budgetService = budgetService;
        this.auditService = auditService;
    }

    public <T> T execute(String toolName, Map<String, Object> inferredParameters, Supplier<T> action) {
        var context = AgentContextHolder.get();
        if (context == null) {
            throw new IllegalStateException("Agent context is not set for this tool invocation");
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
            throw ex;
        } catch (BudgetExceededException ex) {
            auditService.technical(context.agentSessionId(), context.userId(), context.channel(),
                    "TOOL_BUDGET_BLOCKED", Map.of(
                            "tool", toolName,
                            "toolVersion", TOOL_CONTRACT_VERSION,
                            "usedUnits", ex.usedUnits(),
                            "maxUnits", ex.maxUnits()));
            throw ex;
        }

        var startedAt = System.nanoTime();
        auditService.ai(context.agentSessionId(), context.userId(), context.channel(),
                "TOOL_INVOCATION", Map.of(
                        "tool", toolName,
                        "toolVersion", TOOL_CONTRACT_VERSION,
                        "identityType", context.identityType().name(),
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
