package com.enterprise.agentapi.application.port;

import java.time.Instant;
import java.util.Optional;

public interface ConfirmationTokenStore {
    record PendingConfirmation(String userId, String merchant, String idempotencyKey, Instant expiresAt) {}

    String issue(String userId, String merchant, String idempotencyKey);

    Optional<PendingConfirmation> consume(String token, String userId, String merchant, String idempotencyKey);
}
