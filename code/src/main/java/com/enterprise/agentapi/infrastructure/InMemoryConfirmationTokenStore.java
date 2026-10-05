package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.agent.AgentProperties;
import com.enterprise.agentapi.application.port.ConfirmationTokenStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryConfirmationTokenStore implements ConfirmationTokenStore {
    private final AgentProperties properties;
    private final Map<String, PendingConfirmation> pendingByToken = new ConcurrentHashMap<>();

    public InMemoryConfirmationTokenStore(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public IssuedConfirmation issue(String userId, String merchant, String idempotencyKey, String argumentHash) {
        purgeExpired();
        var token = "confirm-" + UUID.randomUUID();
        var expiresAt = Instant.now().plusSeconds(properties.getConfirmation().getTokenTtlSeconds());
        pendingByToken.put(token, new PendingConfirmation(userId, merchant, idempotencyKey, argumentHash, expiresAt));
        return new IssuedConfirmation(token, expiresAt);
    }

    @Override
    public Optional<PendingConfirmation> consume(
            String token, String userId, String merchant, String idempotencyKey, String argumentHash) {
        purgeExpired();
        var pending = pendingByToken.get(token);
        if (pending == null) {
            return Optional.empty();
        }
        var matches = pending.userId().equals(userId)
                && pending.merchant().equalsIgnoreCase(merchant)
                && pending.idempotencyKey().equals(idempotencyKey)
                && Objects.equals(pending.argumentHash(), argumentHash)
                && !pending.expiresAt().isBefore(Instant.now());
        pendingByToken.remove(token);
        return matches ? Optional.of(pending) : Optional.empty();
    }

    private void purgeExpired() {
        var now = Instant.now();
        pendingByToken.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
