package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.observability.AgentAuditService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ExecutionBudgetService {
    private final AgentProperties properties;
    private final AgentAuditService auditService;
    private final Map<String, AtomicInteger> unitsBySession = new ConcurrentHashMap<>();

    public ExecutionBudgetService(AgentProperties properties, AgentAuditService auditService) {
        this.properties = properties;
        this.auditService = auditService;
    }

    public void consume(String agentSessionId, String toolName, String userId, String channel) {
        var cost = properties.getBudget().costOf(toolName);
        var max = properties.getBudget().getMaxUnitsPerSession();
        var used = unitsBySession.computeIfAbsent(agentSessionId, ignored -> new AtomicInteger()).addAndGet(cost);
        if (used > max) {
            unitsBySession.get(agentSessionId).addAndGet(-cost);
            auditService.technical(agentSessionId, userId, channel, "BUDGET_EXCEEDED", Map.of(
                    "tool", toolName,
                    "toolCost", cost,
                    "usedUnits", used - cost,
                    "maxUnits", max));
            throw new BudgetExceededException(agentSessionId, toolName, used - cost, max);
        }
        auditService.technical(agentSessionId, userId, channel, "BUDGET_CONSUMED", Map.of(
                "tool", toolName,
                "toolCost", cost,
                "usedUnits", used,
                "maxUnits", max));
    }

    public int usedUnits(String agentSessionId) {
        var counter = unitsBySession.get(agentSessionId);
        return counter == null ? 0 : counter.get();
    }
}
