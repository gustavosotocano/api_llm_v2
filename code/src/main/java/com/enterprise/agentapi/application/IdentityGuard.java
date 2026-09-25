package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.DelegationScopes;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.SemanticStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Identity survives the agent boundary. SERVICE is never an implicit god account.
 */
public final class IdentityGuard {
    private IdentityGuard() {}

    public static SemanticStatus authorizeCapability(String toolName) {
        var context = AgentContextHolder.get();
        if (context == null) {
            return SemanticStatus.INSUFFICIENT_PERMISSIONS;
        }
        if (context.credentialExpired(Instant.now())) {
            return SemanticStatus.INSUFFICIENT_PERMISSIONS;
        }
        var required = DelegationScopes.requiredFor(toolName);
        if (required.isEmpty() || !context.scopes().contains(required.get())) {
            return SemanticStatus.INSUFFICIENT_PERMISSIONS;
        }
        return null;
    }

    public static SemanticStatus authorizeUserResource(String requestedUserId) {
        var context = AgentContextHolder.get();
        if (context == null) {
            return SemanticStatus.INSUFFICIENT_PERMISSIONS;
        }
        if (context.credentialExpired(Instant.now())) {
            return SemanticStatus.INSUFFICIENT_PERMISSIONS;
        }
        if (context.identityType() == IdentityType.SERVICE) {
            if (requestedUserId == null || requestedUserId.isBlank()) {
                return SemanticStatus.INSUFFICIENT_PERMISSIONS;
            }
            if (!Objects.equals(context.onBehalfOfUserId(), requestedUserId.trim())) {
                return SemanticStatus.INSUFFICIENT_PERMISSIONS;
            }
            return null;
        }
        if (requestedUserId != null && !requestedUserId.isBlank()
                && !Objects.equals(context.userId(), requestedUserId)) {
            return SemanticStatus.INSUFFICIENT_PERMISSIONS;
        }
        return null;
    }

    public static AgentContext context() {
        return AgentContextHolder.require();
    }
}
