package com.enterprise.agentapi.api;

import com.enterprise.agentapi.observability.AgentAuditEvent;
import com.enterprise.agentapi.observability.AgentAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/debug/audit")
public class AuditController {
    private final AgentAuditService auditService;

    public AuditController(AgentAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AgentAuditEvent> audit(@RequestParam(required = false) String agentSessionId) {
        if (agentSessionId == null || agentSessionId.isBlank()) {
            return auditService.allEvents();
        }
        return auditService.eventsForSession(agentSessionId);
    }
}
