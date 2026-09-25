package com.enterprise.agentapi.domain;

import java.util.List;

public record SubscriptionSnapshot(
        SemanticStatus status,
        String message,
        String userId,
        List<String> cancelledMerchants,
        List<String> suggestions
) {}
