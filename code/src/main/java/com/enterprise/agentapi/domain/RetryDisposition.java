package com.enterprise.agentapi.domain;

/**
 * Deterministic retry decision from V2 §7.
 * The agent may choose the next request. This field says whether repeating the last one is safe.
 */
public enum RetryDisposition {
    RETRY_AFTER,
    DO_NOT_RETRY,
    IN_PROGRESS,
    ALREADY_COMPLETED,
    PERMANENT_FAILURE
}
