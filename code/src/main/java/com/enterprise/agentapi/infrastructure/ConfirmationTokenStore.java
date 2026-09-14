package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.agent.AgentProperties;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ConfirmationTokenStore {
    private final AgentProperties properties;
    private final Map<String, PendingConfirmation> pendingByToken = new ConcurrentHashMap<>();

    public ConfirmationTokenStore(AgentProperties properties) {
        this.properties = properties;
    }

    public String issue(String userId, String merchant, String idempotencyKey) {
        purgeExpired();
        var token = "confirm-" + UUID.randomUUID();
        var expiresAt = Instant.now().plusSeconds(properties.getConfirmation().getTokenTtlSeconds());
        pendingByToken.put(token, new PendingConfirmation(userId, merchant, idempotencyKey, expiresAt));
        return token;
    }

    public Optional<PendingConfirmation> consume(String token, String userId, String merchant, String idempotencyKey) {
        purgeExpired();
        var pending = pendingByToken.remove(token);
        if (pending == null) {
            return Optional.empty();
        }
        if (!pending.userId().equals(userId)
                || !pending.merchant().equalsIgnoreCase(merchant)
                || !pending.idempotencyKey().equals(idempotencyKey)
                || pending.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    private void purgeExpired() {
        var now = Instant.now();
        pendingByToken.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record PendingConfirmation(String userId, String merchant, String idempotencyKey, Instant expiresAt) {}
}
