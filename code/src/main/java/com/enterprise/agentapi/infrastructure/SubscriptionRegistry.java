package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.agent.OperationTrace;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SubscriptionRegistry {
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    public void cancel(String userId, String merchant) {
        OperationTrace.recordDownstream();
        cancelled.add(key(userId, merchant));
    }

    public boolean isCancelled(String userId, String merchant) {
        OperationTrace.recordDownstream();
        return cancelled.contains(key(userId, merchant));
    }

    public List<String> cancelledMerchants(String userId) {
        OperationTrace.recordDownstream();
        var prefix = userId + "::";
        return cancelled.stream()
                .filter(entry -> entry.startsWith(prefix))
                .map(entry -> entry.substring(prefix.length()))
                .sorted()
                .toList();
    }

    private String key(String userId, String merchant) {
        return userId + "::" + merchant.toUpperCase();
    }
}
