package com.enterprise.agentapi.agent;

public class AgentRateLimitExceededException extends RuntimeException {
    private final String agentSessionId;
    private final String toolName;
    private final RateLimitScope scope;
    private final int retryAfterSeconds;

    public AgentRateLimitExceededException(String agentSessionId, String toolName,
                                           RateLimitScope scope, int retryAfterSeconds) {
        super(message(agentSessionId, toolName, scope, retryAfterSeconds));
        this.agentSessionId = agentSessionId;
        this.toolName = toolName;
        this.scope = scope;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String message(String agentSessionId, String toolName, RateLimitScope scope, int retryAfterSeconds) {
        return switch (scope) {
            case SESSION -> "Agent session %s exceeded global rate limit. Retry after %d seconds."
                    .formatted(agentSessionId, retryAfterSeconds);
            case TOOL -> "Agent session %s exceeded rate limit for tool %s. Retry after %d seconds."
                    .formatted(agentSessionId, toolName, retryAfterSeconds);
            case LOOP -> "Agent session %s triggered loop detection on tool %s. Retry after %d seconds."
                    .formatted(agentSessionId, toolName, retryAfterSeconds);
        };
    }

    public String agentSessionId() {
        return agentSessionId;
    }

    public String toolName() {
        return toolName;
    }

    public RateLimitScope scope() {
        return scope;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
