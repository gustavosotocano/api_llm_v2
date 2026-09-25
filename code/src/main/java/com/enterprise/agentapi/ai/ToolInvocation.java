package com.enterprise.agentapi.ai;

import com.enterprise.agentapi.domain.SemanticStatus;

import java.util.Map;

public record ToolInvocation(
        String toolName,
        String toolVersion,
        Map<String, Object> parameters,
        SemanticStatus status,
        Object result,
        boolean success
) {}
