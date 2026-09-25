package com.enterprise.agentapi.domain;

import java.util.List;

public record CustomerProfileResponse(
        SemanticStatus status,
        String message,
        String userId,
        String displayName,
        String accountStatus,
        String segment,
        List<String> suggestions
) {}
