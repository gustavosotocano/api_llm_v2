package com.enterprise.agentapi.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AgentAuditService {
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AGENT_AUDIT");
    private static final Set<String> SECRET_FIELDS = Set.of(
            "confirmationtoken",
            "servicecredential",
            "credential",
            "accesstoken",
            "subjecttoken",
            "actortoken",
            "approvaltoken");

    private final JsonMapper jsonMapper;
    private final Map<String, List<AgentAuditEvent>> eventsBySession = new ConcurrentHashMap<>();

    public AgentAuditService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void record(AgentAuditEvent event) {
        var safe = new AgentAuditEvent(
                event.timestamp(), event.layer(), event.agentSessionId(), event.userId(),
                event.channel(), event.eventType(), redact(event.attributes()));
        eventsBySession
                .computeIfAbsent(safe.agentSessionId(), ignored -> new CopyOnWriteArrayList<>())
                .add(safe);
        try {
            AUDIT_LOG.info(jsonMapper.writeValueAsString(safe));
        } catch (RuntimeException ex) {
            AUDIT_LOG.info("layer={} session={} type={} attrs={}",
                    safe.layer(), safe.agentSessionId(), safe.eventType(), safe.attributes());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> redact(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        return (Map<String, Object>) redactValue(attributes);
    }

    private static Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((key, nested) -> {
                var name = String.valueOf(key);
                if (isSecret(name) && nested != null && !nested.toString().isBlank()) {
                    copy.put(name, "[redacted]");
                } else {
                    copy.put(name, redactValue(nested));
                }
            });
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AgentAuditService::redactValue).toList();
        }
        if (value instanceof String text && embedsSecret(text)) {
            return "[redacted]";
        }
        return value;
    }

    private static boolean isSecret(String name) {
        return SECRET_FIELDS.contains(name.toLowerCase(Locale.ROOT).replace("_", ""));
    }

    private static boolean embedsSecret(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("confirmationtoken=")
                || lower.contains("servicecredential=")
                || lower.contains("confirm-")
                || lower.contains("gov-approve-");
    }

    public void technical(String agentSessionId, String userId, String channel, String eventType, Map<String, Object> attributes) {
        record(new AgentAuditEvent(Instant.now(), AuditLayer.TECHNICAL, agentSessionId, userId, channel, eventType, attributes));
    }

    public void ai(String agentSessionId, String userId, String channel, String eventType, Map<String, Object> attributes) {
        record(new AgentAuditEvent(Instant.now(), AuditLayer.AI, agentSessionId, userId, channel, eventType, attributes));
    }

    public void business(String agentSessionId, String userId, String channel, String eventType, Map<String, Object> attributes) {
        record(new AgentAuditEvent(Instant.now(), AuditLayer.BUSINESS, agentSessionId, userId, channel, eventType, attributes));
    }

    public List<AgentAuditEvent> eventsForSession(String agentSessionId) {
        return List.copyOf(eventsBySession.getOrDefault(agentSessionId, List.of()));
    }

    public List<AgentAuditEvent> allEvents() {
        var all = new ArrayList<AgentAuditEvent>();
        eventsBySession.values().forEach(all::addAll);
        all.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return all;
    }
}
