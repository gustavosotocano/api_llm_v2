package com.enterprise.agentapi.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Narrow credential scopes from V2 §4. Tool visibility is not the same as these scopes.
 */
public enum CapabilityScope {
    TRANSACTIONS_READ("transactions:read"),
    SUBSCRIPTIONS_WRITE("subscriptions:write"),
    CATALOG_PROPOSE("catalog:propose"),
    JOBS_RUN("jobs:run"),
    GOVERNANCE_REVIEW("governance:review");

    private final String value;

    CapabilityScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CapabilityScope fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("scope is required");
        }
        var normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(scope -> scope.value.equals(normalized) || scope.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scope: " + raw));
    }
}
