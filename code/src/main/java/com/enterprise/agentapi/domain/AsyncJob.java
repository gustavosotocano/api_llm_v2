package com.enterprise.agentapi.domain;

import java.time.Instant;
import java.util.Map;

public record AsyncJob(
        String jobId,
        String userId,
        String agentSessionId,
        String operation,
        JobStatus status,
        Instant createdAt,
        Instant updatedAt,
        int pollAfterSeconds,
        Map<String, Object> result,
        String errorMessage
) {
    public AsyncJob withStatus(JobStatus next, Instant updatedAt, Map<String, Object> result, String errorMessage) {
        return new AsyncJob(jobId, userId, agentSessionId, operation, next, createdAt, updatedAt,
                pollAfterSeconds, result, errorMessage);
    }
}
