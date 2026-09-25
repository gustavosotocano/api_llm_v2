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
}
