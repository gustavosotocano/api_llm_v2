package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.SemanticStatus;

public final class AgentRateLimitSupport {
    private AgentRateLimitSupport() {}

    public static SemanticStatus semanticStatus(AgentRateLimitExceededException ex) {
        return ex.scope() == RateLimitScope.LOOP
                ? SemanticStatus.AGENT_LOOP_DETECTED
                : SemanticStatus.RATE_LIMITED;
    }
}
