package com.enterprise.agentapi.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AgentAuditService {
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AGENT_AUDIT");

    private final JsonMapper jsonMapper;
    private final Map<String, List<AgentAuditEvent>> eventsBySession = new ConcurrentHashMap<>();

    public AgentAuditService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void record(AgentAuditEvent event) {
        eventsBySession
                .computeIfAbsent(event.agentSessionId(), ignored -> new CopyOnWriteArrayList<>())
                .add(event);
        try {
            AUDIT_LOG.info(jsonMapper.writeValueAsString(event));
        } catch (RuntimeException ex) {
            AUDIT_LOG.info("layer={} session={} type={} attrs={}",
                    event.layer(), event.agentSessionId(), event.eventType(), event.attributes());
        }
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
