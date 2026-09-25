package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.AgentWorkflow;

public class ToolAccessDeniedException extends RuntimeException {
    private final String toolName;
    private final AgentWorkflow workflow;

    public ToolAccessDeniedException(String toolName, AgentWorkflow workflow) {
        this(toolName, workflow, "Workflow " + workflow + " cannot invoke tool " + toolName
                + ". Narrow the request or elevate the workflow explicitly.");
    }

    public ToolAccessDeniedException(String toolName, AgentWorkflow workflow, String message) {
        super(message);
        this.toolName = toolName;
        this.workflow = workflow;
    }

    public String toolName() {
        return toolName;
    }

    public AgentWorkflow workflow() {
        return workflow;
    }
}
