package com.enterprise.agentapi.agent;

public class BudgetExceededException extends RuntimeException {
    private final String agentSessionId;
    private final String toolName;
    private final int usedUnits;
    private final int maxUnits;

    public BudgetExceededException(String agentSessionId, String toolName, int usedUnits, int maxUnits) {
        super("Session %s exceeded execution budget (%d/%d units) invoking %s."
                .formatted(agentSessionId, usedUnits, maxUnits, toolName));
        this.agentSessionId = agentSessionId;
        this.toolName = toolName;
        this.usedUnits = usedUnits;
        this.maxUnits = maxUnits;
    }

    public String agentSessionId() {
        return agentSessionId;
    }

    public String toolName() {
        return toolName;
    }

    public int usedUnits() {
        return usedUnits;
    }

    public int maxUnits() {
        return maxUnits;
    }
}
