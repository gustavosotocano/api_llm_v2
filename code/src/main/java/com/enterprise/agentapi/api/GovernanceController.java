package com.enterprise.agentapi.api;

import com.enterprise.agentapi.agent.AgentRateLimitExceededException;
import com.enterprise.agentapi.agent.AgentRateLimiter;
import com.enterprise.agentapi.application.CatalogGovernanceService;
import com.enterprise.agentapi.domain.CatalogChangeProposal;
import com.enterprise.agentapi.domain.CatalogChangeResponse;
import com.enterprise.agentapi.domain.CatalogProposalStatus;
import com.enterprise.agentapi.observability.AgentAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/debug/governance/catalog")
public class GovernanceController {
    private final CatalogGovernanceService governanceService;
    private final AgentRateLimiter rateLimiter;
    private final AgentAuditService auditService;

    public GovernanceController(CatalogGovernanceService governanceService,
                                AgentRateLimiter rateLimiter,
                                AgentAuditService auditService) {
        this.governanceService = governanceService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @GetMapping("/proposals")
    public List<CatalogChangeProposal> listProposals(
            @RequestParam(defaultValue = "PENDING_REVIEW") CatalogProposalStatus status) {
        return governanceService.listProposals(status);
    }

    @PostMapping("/proposals/{proposalId}/approve")
    public CatalogChangeResponse approve(
            @PathVariable String proposalId,
            @RequestHeader("X-Governance-Reviewer") String reviewerId,
            @RequestHeader("X-Governance-Approval-Token") String approvalToken,
            @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        return review(proposalId, reviewerId, approvalToken, agentSessionId, "GOVERNANCE_APPROVE",
                () -> governanceService.approve(proposalId, reviewerId, approvalToken));
    }

    @PostMapping("/proposals/{proposalId}/reject")
    public CatalogChangeResponse reject(
            @PathVariable String proposalId,
            @RequestHeader("X-Governance-Reviewer") String reviewerId,
            @RequestHeader("X-Governance-Approval-Token") String approvalToken,
            @RequestParam(required = false) String comment,
            @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        return review(proposalId, reviewerId, approvalToken, agentSessionId, "GOVERNANCE_REJECT",
                () -> governanceService.reject(proposalId, reviewerId, approvalToken, comment));
    }

    private CatalogChangeResponse review(String proposalId, String reviewerId, String approvalToken,
                                         String agentSessionId, String action,
                                         java.util.function.Supplier<CatalogChangeResponse> operation) {
        var sessionId = AgentSessionSupport.resolveSessionId(agentSessionId);
        AgentSessionSupport.bind(sessionId, reviewerId, "GOVERNANCE_API");
        try {
            rateLimiter.checkAllowed(sessionId, action, reviewerId, "GOVERNANCE_API");
            var startedAt = System.nanoTime();
            auditService.ai(sessionId, reviewerId, "GOVERNANCE_API", action, Map.of(
                    "proposalId", proposalId,
                    "reviewerId", reviewerId));

            var response = operation.get();

            auditService.technical(sessionId, reviewerId, "GOVERNANCE_API", action + "_DONE", Map.of(
                    "durationMs", (System.nanoTime() - startedAt) / 1_000_000,
                    "status", response.status().name(),
                    "proposalId", proposalId));
            auditService.business(sessionId, reviewerId, "GOVERNANCE_API", action, Map.of(
                    "status", response.status().name(),
                    "proposalId", proposalId,
                    "categoryCode", response.categoryCode() == null ? "none" : response.categoryCode()));
            return response;
        } catch (AgentRateLimitExceededException ex) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        } finally {
            AgentSessionSupport.clear();
        }
    }
}
