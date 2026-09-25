package com.enterprise.agentapi.mcp;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.agent.ServiceCredentialRegistry;
import com.enterprise.agentapi.api.AgentSessionSupport;
import com.enterprise.agentapi.domain.AgentWorkflow;
import com.enterprise.agentapi.domain.IdentityType;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.stereotype.Component;

/**
 * Client meta cannot self-elevate to SERVICE. That requires a short-lived issued credential.
 */
@Component
public class McpAgentContextBinder {
    private final ServiceCredentialRegistry credentials;

    public McpAgentContextBinder(ServiceCredentialRegistry credentials) {
        this.credentials = credentials;
    }

    public void bind(String agentSessionId, String userId, McpMeta meta) {
        var session = firstNonBlank(agentSessionId, stringMeta(meta, "agentSessionId"));
        if (session == null) {
            session = AgentSessionSupport.resolveSessionId(null);
        }
        var resolvedUserId = firstNonBlank(userId, stringMeta(meta, "userId"));
        var workflow = parseWorkflow(stringMeta(meta, "workflow"));
        OperationTrace.begin(stringMeta(meta, "enterpriseRequestId"));
        var grant = credentials.validate(stringMeta(meta, "serviceId"), stringMeta(meta, "serviceCredential"));
        if (grant.isPresent()) {
            var service = grant.get();
            var onBehalfOf = service.allows(resolvedUserId) ? resolvedUserId : null;
            AgentContextHolder.set(new AgentContext(
                    session,
                    service.serviceId(),
                    "MCP",
                    IdentityType.SERVICE,
                    workflow,
                    service.scopes(),
                    onBehalfOf,
                    service.expiresAt()));
            return;
        }
        AgentContextHolder.set(new AgentContext(
                session, resolvedUserId, "MCP", IdentityType.USER_DELEGATED, workflow));
    }

    public void clear() {
        AgentContextHolder.clear();
        OperationTrace.clear();
    }

    private static AgentWorkflow parseWorkflow(String raw) {
        if (raw == null) {
            return AgentWorkflow.READ;
        }
        try {
            return AgentWorkflow.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return AgentWorkflow.READ;
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
