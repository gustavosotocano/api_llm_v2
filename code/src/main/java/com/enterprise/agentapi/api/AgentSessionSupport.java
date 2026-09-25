package com.enterprise.agentapi.api;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.domain.AgentWorkflow;
import com.enterprise.agentapi.domain.IdentityType;

import java.util.UUID;

public final class AgentSessionSupport {
    private AgentSessionSupport() {}

    public static String resolveSessionId(String agentSessionId) {
        if (agentSessionId == null || agentSessionId.isBlank()) {
            return "session-" + UUID.randomUUID();
        }
        return agentSessionId.trim();
    }

    public static void bind(String agentSessionId, String userId, String channel) {
        bind(agentSessionId, userId, channel, IdentityType.USER_DELEGATED);
    }

    public static void bind(String agentSessionId, String userId, String channel, IdentityType identityType) {
        bind(agentSessionId, userId, channel, identityType, AgentWorkflow.READ);
    }

    public static void bind(
            String agentSessionId,
            String userId,
            String channel,
            IdentityType identityType,
            AgentWorkflow workflow) {
        AgentContextHolder.set(new AgentContext(
                agentSessionId, userId, channel, identityType,
                workflow == null ? AgentWorkflow.READ : workflow));
    }

    public static void bind(AgentContext context) {
        AgentContextHolder.set(context);
    }

    public static void clear() {
        AgentContextHolder.clear();
        OperationTrace.clear();
    }
}
