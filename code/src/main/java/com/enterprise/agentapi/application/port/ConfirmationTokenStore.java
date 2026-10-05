package com.enterprise.agentapi.application.port;

import java.time.Instant;
import java.util.Optional;

public interface ConfirmationTokenStore {
    record PendingConfirmation(
            String userId,
            String merchant,
            String idempotencyKey,
            String argumentHash,
            Instant expiresAt) {}

    record IssuedConfirmation(String token, Instant expiresAt) {}

    IssuedConfirmation issue(String userId, String merchant, String idempotencyKey, String argumentHash);

    Optional<PendingConfirmation> consume(
            String token, String userId, String merchant, String idempotencyKey, String argumentHash);
}
