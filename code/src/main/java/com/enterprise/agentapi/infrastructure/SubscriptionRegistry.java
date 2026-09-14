package com.enterprise.agentapi.infrastructure;

import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SubscriptionRegistry {
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    public void cancel(String userId, String merchant) {
        cancelled.add(key(userId, merchant));
    }

    public boolean isCancelled(String userId, String merchant) {
        return cancelled.contains(key(userId, merchant));
    }

    private String key(String userId, String merchant) {
        return userId + "::" + merchant.toUpperCase();
    }
}
