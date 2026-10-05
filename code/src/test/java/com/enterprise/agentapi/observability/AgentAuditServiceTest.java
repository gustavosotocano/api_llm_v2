package com.enterprise.agentapi.observability;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAuditServiceTest {

    @Test
    void confirmationTokenIsRedactedBeforeItIsStoredOrLogged() {
        var audit = new AgentAuditService(JsonMapper.builder().build());
        audit.ai("session-1", "user-123", "MCP", "TOOL_INVOCATION", Map.of(
                "tool", "cancelRecurringSubscription",
                "inferredParameters", Map.of(
                        "merchant", "NETFLIX",
                        "confirmationToken", "confirm-secret")));

        var event = audit.eventsForSession("session-1").getFirst();
        @SuppressWarnings("unchecked")
        var parameters = (Map<String, Object>) event.attributes().get("inferredParameters");
        assertThat(parameters.get("merchant")).isEqualTo("NETFLIX");
        assertThat(parameters.get("confirmationToken")).isEqualTo("[redacted]");
        assertThat(event.attributes().toString()).doesNotContain("confirm-secret");
    }

    @Test
    void credentialsAndApprovalTokensAreRedacted() {
        var audit = new AgentAuditService(JsonMapper.builder().build());
        audit.ai("session-1", "user-123", "MCP", "TOOL_INVOCATION", Map.of(
                "userId", "user-123",
                "serviceCredential", "svc-secret-credential",
                "approvalToken", "gov-approve-secret",
                "note", "confirmationToken=confirm-embedded"));

        var stored = audit.eventsForSession("session-1").getFirst().attributes();
        assertThat(stored.get("userId")).isEqualTo("user-123");
        assertThat(stored.get("serviceCredential")).isEqualTo("[redacted]");
        assertThat(stored.get("approvalToken")).isEqualTo("[redacted]");
        assertThat(stored.get("note")).isEqualTo("[redacted]");
        assertThat(stored.toString()).doesNotContain("svc-secret-credential", "gov-approve-secret", "confirm-embedded");
    }
}
