package com.enterprise.agentapi.observability;

import java.time.Instant;
import java.util.Map;

public record AgentAuditEvent(
        Instant timestamp,
        AuditLayer layer,
        String agentSessionId,
        String userId,
        String channel,
        String eventType,
        Map<String, Object> attributes
) {}
