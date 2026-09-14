package com.enterprise.agentapi.domain;

import java.util.List;
import java.util.Map;

public record JobResponse(
        SemanticStatus status,
        String message,
        String jobId,
        JobStatus jobStatus,
        Integer pollAfterSeconds,
        Map<String, Object> result,
        List<String> suggestions
) {}
