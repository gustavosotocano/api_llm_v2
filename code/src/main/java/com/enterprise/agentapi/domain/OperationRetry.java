package com.enterprise.agentapi.domain;

/**
 * Machine-readable retry contract. Suggestions remain natural language for the model.
 */
public record OperationRetry(RetryDisposition disposition, Integer retryAfterSeconds) {
    public static OperationRetry forStatus(SemanticStatus status, Integer retryAfterSeconds) {
        var disposition = switch (status) {
            case RATE_LIMITED, AGENT_LOOP_DETECTED -> RetryDisposition.RETRY_AFTER;
            case ACCEPTED, OPERATION_IN_PROGRESS -> RetryDisposition.IN_PROGRESS;
            case SUCCESS -> RetryDisposition.ALREADY_COMPLETED;
            case INSUFFICIENT_PERMISSIONS, DEPENDENCY_UNAVAILABLE -> RetryDisposition.PERMANENT_FAILURE;
            case BUDGET_EXCEEDED, CLARIFICATION_REQUIRED, UNKNOWN_CATEGORY, INVALID_PERIOD, INVALID_DATE_RANGE,
                 IDEMPOTENCY_CONFLICT, NO_RESULTS_FOUND, OPERATION_REQUIRES_CONFIRMATION,
                 CATALOG_CHANGE_PENDING_REVIEW, CATALOG_CHANGE_REJECTED, CANCELLED -> RetryDisposition.DO_NOT_RETRY;
        };
        var delay = disposition == RetryDisposition.RETRY_AFTER || disposition == RetryDisposition.IN_PROGRESS
                ? retryAfterSeconds
                : null;
        return new OperationRetry(disposition, delay);
    }
}
