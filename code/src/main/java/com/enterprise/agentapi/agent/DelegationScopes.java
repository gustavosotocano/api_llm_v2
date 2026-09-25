package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.CapabilityScope;
import com.enterprise.agentapi.domain.IdentityType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DelegationScopes {
    private static final Set<CapabilityScope> USER_DEFAULTS = Set.of(
            CapabilityScope.TRANSACTIONS_READ,
            CapabilityScope.SUBSCRIPTIONS_WRITE,
            CapabilityScope.CATALOG_PROPOSE,
            CapabilityScope.JOBS_RUN);

    private static final Map<String, CapabilityScope> TOOL_SCOPES = Map.of(
            "searchRecurringPayments", CapabilityScope.TRANSACTIONS_READ,
            "cancelRecurringSubscription", CapabilityScope.SUBSCRIPTIONS_WRITE,
            "proposeCatalogChange", CapabilityScope.CATALOG_PROPOSE,
            "startCustomerReport", CapabilityScope.JOBS_RUN,
            "getJobStatus", CapabilityScope.JOBS_RUN,
            "getJobResult", CapabilityScope.JOBS_RUN);

    private DelegationScopes() {}

    public static Set<CapabilityScope> defaultsFor(IdentityType identityType) {
        return identityType == IdentityType.USER_DELEGATED ? USER_DEFAULTS : Set.of();
    }

    public static Optional<CapabilityScope> requiredFor(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(TOOL_SCOPES.get(toolName.trim()));
    }
}
