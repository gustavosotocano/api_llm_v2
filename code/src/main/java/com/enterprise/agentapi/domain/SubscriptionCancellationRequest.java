package com.enterprise.agentapi.domain;

public record SubscriptionCancellationRequest(
        String userId,
        String merchant,
        String idempotencyKey,
        String confirmationToken
) {}
