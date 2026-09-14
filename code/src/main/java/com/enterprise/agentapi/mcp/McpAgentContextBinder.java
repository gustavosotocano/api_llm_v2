package com.enterprise.agentapi.mcp;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.api.AgentSessionSupport;
import com.enterprise.agentapi.domain.IdentityType;
import org.springframework.ai.mcp.annotation.McpMeta;

public final class McpAgentContextBinder {
    private McpAgentContextBinder() {}

    public static void bind(String agentSessionId, String userId, McpMeta meta) {
        var session = firstNonBlank(agentSessionId, stringMeta(meta, "agentSessionId"));
        if (session == null) {
            session = AgentSessionSupport.resolveSessionId(null);
        }
        var resolvedUserId = firstNonBlank(userId, stringMeta(meta, "userId"), "user-123");
        var identity = parseIdentity(stringMeta(meta, "identityType"));
        AgentContextHolder.set(new AgentContext(session, resolvedUserId, "MCP", identity));
    }

    public static void clear() {
        AgentContextHolder.clear();
    }

    private static IdentityType parseIdentity(String raw) {
        if (raw == null) {
            return IdentityType.USER_DELEGATED;
        }
        try {
            return IdentityType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return IdentityType.USER_DELEGATED;
        }
    }

    private static String stringMeta(McpMeta meta, String key) {
        if (meta == null) {
            return null;
        }
        var value = meta.get(key);
        if (value == null) {
            return null;
        }
        var text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
