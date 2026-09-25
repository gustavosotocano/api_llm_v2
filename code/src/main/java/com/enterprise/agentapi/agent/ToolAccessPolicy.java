package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.AgentWorkflow;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ToolAccessPolicy {
    private static final Set<String> READ_TOOLS = Set.of(
            "searchRecurringPayments", "getJobStatus", "getJobResult");
    private static final Set<String> CANCELLATION_TOOLS = Set.of("cancelRecurringSubscription");
    private static final Set<String> GOVERNANCE_TOOLS = Set.of("proposeCatalogChange");
    private static final Set<String> REPORT_TOOLS = Set.of("startCustomerReport");

    public boolean allows(AgentWorkflow workflow, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        var tool = toolName.trim();
        var scope = workflow == null ? AgentWorkflow.READ : workflow;
        return switch (scope) {
            case FULL -> true;
            case READ -> READ_TOOLS.contains(tool);
            case CANCELLATION -> READ_TOOLS.contains(tool) || CANCELLATION_TOOLS.contains(tool);
            case GOVERNANCE -> READ_TOOLS.contains(tool) || GOVERNANCE_TOOLS.contains(tool);
            case REPORT -> READ_TOOLS.contains(tool) || REPORT_TOOLS.contains(tool);
        };
    }
}
