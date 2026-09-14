package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.IdentityType;

public record AgentContext(
        String agentSessionId,
        String userId,
        String channel,
        IdentityType identityType
) {
    public AgentContext(String agentSessionId, String userId, String channel) {
        this(agentSessionId, userId, channel, IdentityType.USER_DELEGATED);
    }
}
