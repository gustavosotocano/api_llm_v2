package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.SemanticStatus;

import java.util.Objects;

public final class IdentityGuard {
    private IdentityGuard() {}

    public static SemanticStatus authorizeUserResource(String requestedUserId) {
        var context = AgentContextHolder.get();
        if (context == null) {
            return SemanticStatus.INSUFFICIENT_PERMISSIONS;
        }
        if (context.identityType() == IdentityType.SERVICE) {
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
