package com.enterprise.agentapi.infrastructure;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class IdempotencyStore {
    private final Map<String, StoredResult> results = new ConcurrentHashMap<>();

    public <T> Optional<T> find(String idempotencyKey, Class<T> type) {
        var stored = results.get(idempotencyKey);
        if (stored == null || !stored.typeName().equals(type.getName())) {
            return Optional.empty();
        }
        return Optional.of(type.cast(stored.payload()));
    }

    public boolean hasDifferentPayload(String idempotencyKey, String fingerprint) {
        var stored = results.get(idempotencyKey);
        return stored != null && !stored.fingerprint().equals(fingerprint);
    }

    public void save(String idempotencyKey, Object payload, String fingerprint) {
        results.put(idempotencyKey, new StoredResult(payload.getClass().getName(), payload, fingerprint));
    }

    private record StoredResult(String typeName, Object payload, String fingerprint) {}
}
