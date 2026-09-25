package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.AgentWorkflow;
import com.enterprise.agentapi.domain.CapabilityScope;
import com.enterprise.agentapi.domain.IdentityType;

import java.time.Instant;
import java.util.Set;

public record AgentContext(
        String agentSessionId,
        String userId,
        String channel,
        IdentityType identityType,
        AgentWorkflow workflow,
        Set<CapabilityScope> scopes,
        String onBehalfOfUserId,
        Instant credentialExpiresAt
) {
    public AgentContext {
        identityType = identityType == null ? IdentityType.USER_DELEGATED : identityType;
        workflow = workflow == null ? AgentWorkflow.READ : workflow;
        scopes = scopes == null
                ? DelegationScopes.defaultsFor(identityType)
                : Set.copyOf(scopes);
        if (identityType == IdentityType.USER_DELEGATED) {
            onBehalfOfUserId = null;
        } else if (onBehalfOfUserId != null && onBehalfOfUserId.isBlank()) {
            onBehalfOfUserId = null;
        }
    }

    public AgentContext(String agentSessionId, String userId, String channel) {
        this(agentSessionId, userId, channel, IdentityType.USER_DELEGATED, AgentWorkflow.READ, null, null, null);
    }

    public AgentContext(String agentSessionId, String userId, String channel, IdentityType identityType) {
        this(agentSessionId, userId, channel, identityType, AgentWorkflow.READ, null, null, null);
    }

    public AgentContext(String agentSessionId, String userId, String channel,
                        IdentityType identityType, AgentWorkflow workflow) {
        this(agentSessionId, userId, channel, identityType, workflow, null, null, null);
    }

    public boolean credentialExpired(Instant now) {
        return credentialExpiresAt != null && now != null && !now.isBefore(credentialExpiresAt);
    }
}
