package com.enterprise.agentapi.application.port;

import java.util.Optional;

public interface CustomerProfileRepository {
    record CustomerRecord(String userId, String displayName, String accountStatus, String segment) {}

    Optional<CustomerRecord> find(String userId);
}
