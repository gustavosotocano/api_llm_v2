package com.enterprise.agentapi.domain;

/**
 * Deterministic agent-facing outcomes from the V2 design document.
 * The agent may decide what to request next; the platform defines what happened.
 */
public enum SemanticStatus {
    SUCCESS,
    ACCEPTED,
    NO_RESULTS_FOUND,
    CLARIFICATION_REQUIRED,
    UNKNOWN_CATEGORY,
    INVALID_PERIOD,
    INVALID_DATE_RANGE,
    INSUFFICIENT_PERMISSIONS,
    OPERATION_REQUIRES_CONFIRMATION,
    RATE_LIMITED,
    AGENT_LOOP_DETECTED,
    BUDGET_EXCEEDED,
    OPERATION_IN_PROGRESS,
    CANCELLED,
    DEPENDENCY_UNAVAILABLE,
    IDEMPOTENCY_CONFLICT,
    CATALOG_CHANGE_PENDING_REVIEW,
    CATALOG_CHANGE_REJECTED
}
