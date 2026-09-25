package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.CapabilityScope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Short-lived service credentials. MCP meta cannot self-elevate to SERVICE.
 */
@Component
public class ServiceCredentialRegistry {
    private final ConcurrentHashMap<String, ServiceGrant> credentials = new ConcurrentHashMap<>();
    private final Clock clock;

    public ServiceCredentialRegistry() {
        this(Clock.systemUTC());
    }

    public ServiceCredentialRegistry(Clock clock) {
        this.clock = clock;
    }

    public ServiceGrant issue(String serviceId,
                              Set<CapabilityScope> scopes,
                              Set<String> allowedUserIds,
                              Duration ttl) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        var now = clock.instant();
        var lifetime = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(5) : ttl;
        var grant = new ServiceGrant(
                serviceId.trim(),
                "svc-" + UUID.randomUUID(),
                scopes,
                allowedUserIds == null ? Set.of() : allowedUserIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toSet()),
                now.plus(lifetime));
        credentials.put(key(grant.serviceId(), grant.credential()), grant);
        return grant;
    }

    public Optional<ServiceGrant> validate(String serviceId, String credential) {
        if (serviceId == null || serviceId.isBlank() || credential == null || credential.isBlank()) {
            return Optional.empty();
        }
        var grant = credentials.get(key(serviceId, credential));
        if (grant == null || grant.expired(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(grant);
    }

    private static String key(String serviceId, String credential) {
        return serviceId.trim().toLowerCase(Locale.ROOT) + "|" + credential.trim();
    }
}
