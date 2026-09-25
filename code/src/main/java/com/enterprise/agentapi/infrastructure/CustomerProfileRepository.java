package com.enterprise.agentapi.infrastructure;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CustomerProfileRepository {
    public record CustomerRecord(String userId, String displayName, String accountStatus, String segment) {}

    private final Map<String, CustomerRecord> records = new ConcurrentHashMap<>();

    public CustomerProfileRepository() {
        records.put("user-123", new CustomerRecord("user-123", "Alex Rivera", "ACTIVE", "RETAIL"));
        records.put("user-456", new CustomerRecord("user-456", "Jordan Lee", "ACTIVE", "RETAIL"));
    }

    public Optional<CustomerRecord> find(String userId) {
        return Optional.ofNullable(records.get(userId));
    }
}
