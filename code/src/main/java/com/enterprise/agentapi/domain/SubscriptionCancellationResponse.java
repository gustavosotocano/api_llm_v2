package com.enterprise.agentapi.domain;

import java.time.Instant;
import java.util.List;

public record SubscriptionCancellationResponse(
        SemanticStatus status,
        String message,
        String userId,
        String merchant,
        String idempotencyKey,
        String confirmationToken,
        String operationId,
        Instant executedAt,
        List<String> suggestions
) {}
