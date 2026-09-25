package com.enterprise.agentapi.application.port;

import java.util.Optional;

public interface IdempotencyStore {
    <T> Optional<T> find(String idempotencyKey, Class<T> type);

    boolean hasDifferentPayload(String idempotencyKey, String fingerprint);

    void save(String idempotencyKey, Object payload, String fingerprint);
}
