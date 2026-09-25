package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.application.port.IdempotencyStore;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryIdempotencyStore implements IdempotencyStore {
    private final Map<String, StoredResult> results = new ConcurrentHashMap<>();

    @Override
    public <T> Optional<T> find(String idempotencyKey, Class<T> type) {
        var stored = results.get(idempotencyKey);
        if (stored == null || !stored.typeName().equals(type.getName())) {
            return Optional.empty();
        }
        return Optional.of(type.cast(stored.payload()));
    }

    @Override
    public boolean hasDifferentPayload(String idempotencyKey, String fingerprint) {
        var stored = results.get(idempotencyKey);
        return stored != null && !stored.fingerprint().equals(fingerprint);
    }

    @Override
    public void save(String idempotencyKey, Object payload, String fingerprint) {
        results.put(idempotencyKey, new StoredResult(payload.getClass().getName(), payload, fingerprint));
    }

    private record StoredResult(String typeName, Object payload, String fingerprint) {}
}
