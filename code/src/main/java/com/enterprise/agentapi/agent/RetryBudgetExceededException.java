package com.enterprise.agentapi.agent;

public class RetryBudgetExceededException extends RuntimeException {
    private final String toolName;
    private final int retryCount;
    private final int maxRetries;

    public RetryBudgetExceededException(String toolName, int retryCount, int maxRetries) {
        super("Retry budget exhausted for %s after %d retries. Do not call this operation again."
                .formatted(toolName, retryCount));
        this.toolName = toolName;
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
    }

    public String toolName() {
        return toolName;
    }

    public int retryCount() {
        return retryCount;
    }

    public int maxRetries() {
        return maxRetries;
    }
}
