package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.CapabilityScope;

import java.time.Instant;
import java.util.Set;

public record ServiceGrant(
        String serviceId,
        String credential,
        Set<CapabilityScope> scopes,
        Set<String> allowedUserIds,
        Instant expiresAt
) {
    public ServiceGrant {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        allowedUserIds = allowedUserIds == null ? Set.of() : Set.copyOf(allowedUserIds);
    }

    public boolean allows(String userId) {
        return userId != null && !userId.isBlank() && allowedUserIds.contains(userId.trim());
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
