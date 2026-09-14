package com.enterprise.agentapi.mcp;

import com.enterprise.agentapi.application.CatalogGovernanceService;
import com.enterprise.agentapi.domain.CatalogProposalStatus;
import com.enterprise.agentapi.domain.TrustLevel;
import com.enterprise.agentapi.observability.AgentAuditService;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BankingMcpResources {
    private final BankingReferenceData referenceData;
    private final CatalogGovernanceService governanceService;
    private final AgentAuditService auditService;
    private final McpJsonEncoder jsonEncoder;

    public BankingMcpResources(BankingReferenceData referenceData,
                               CatalogGovernanceService governanceService,
                               AgentAuditService auditService,
                               McpJsonEncoder jsonEncoder) {
        this.referenceData = referenceData;
        this.governanceService = governanceService;
        this.auditService = auditService;
        this.jsonEncoder = jsonEncoder;
    }

    @McpResource(
            uri = "banking://categories",
            name = "category-catalog",
            description = "Controlled category and merchant catalog. Authoritative for category codes only.",
            mimeType = "application/json")
    public ReadResourceResult categoryCatalog() {
        return jsonResource("banking://categories", TrustLevel.AUTHORITATIVE_CATALOG, referenceData.categoryCatalog());
    }

    @McpResource(
            uri = "banking://policies/cancellation",
            name = "cancellation-policy",
            description = "Human-in-the-loop and idempotency rules for cancelRecurringSubscription.",
            mimeType = "application/json")
    public ReadResourceResult cancellationPolicy() {
        return jsonResource("banking://policies/cancellation", TrustLevel.AUTHORITATIVE_POLICY, referenceData.cancellationPolicy());
    }

    @McpResource(
            uri = "banking://periods",
            name = "supported-periods",
            description = "Semantic period values accepted by the backend (do not send raw dates).",
            mimeType = "application/json")
    public ReadResourceResult supportedPeriods() {
        return jsonResource("banking://periods", TrustLevel.AUTHORITATIVE_CATALOG, Map.of(
                "guide", referenceData.periodsGuide(),
                "values", referenceData.supportedPeriods()));
    }

    @McpResource(
            uri = "banking://governance/catalog-policy",
            name = "catalog-governance-policy",
            description = "How catalog changes are proposed by agents and approved by humans.",
            mimeType = "application/json")
    public ReadResourceResult catalogGovernancePolicy() {
        return jsonResource("banking://governance/catalog-policy", TrustLevel.AUTHORITATIVE_POLICY,
                referenceData.catalogGovernancePolicy());
    }

    @McpResource(
            uri = "banking://policies/operations",
            name = "operational-policy",
            description = "Rate limits vs execution budgets, and the async job contract.",
            mimeType = "application/json")
    public ReadResourceResult operationalPolicy() {
        return jsonResource("banking://policies/operations", TrustLevel.AUTHORITATIVE_POLICY,
                referenceData.operationalPolicy());
    }

    @McpResource(
            uri = "banking://governance/pending-proposals",
            name = "pending-catalog-proposals",
            description = "Catalog change proposals awaiting human review (operational data, not policy).",
            mimeType = "application/json")
    public ReadResourceResult pendingCatalogProposals() {
        var pending = governanceService.listProposals(CatalogProposalStatus.PENDING_REVIEW);
        return jsonResource("banking://governance/pending-proposals", TrustLevel.INTERNAL_OPERATIONAL, Map.of(
                "status", "PENDING_REVIEW",
                "count", pending.size(),
                "proposals", pending));
    }

    @McpResource(
            uri = "banking://audit/{agentSessionId}",
            name = "agent-audit-trail",
            description = "Technical, AI, and business audit events for an agent session.",
            mimeType = "application/json")
    public ReadResourceResult agentAuditTrail(String agentSessionId) {
        var events = auditService.eventsForSession(agentSessionId);
        return jsonResource("banking://audit/" + agentSessionId, TrustLevel.INTERNAL_OPERATIONAL, Map.of(
                "agentSessionId", agentSessionId,
                "eventCount", events.size(),
                "events", events));
    }

    private ReadResourceResult jsonResource(String uri, TrustLevel trustLevel, Object body) {
        var payload = ResourceProvenance.wrap(uri, trustLevel, body);
        return new ReadResourceResult(List.of(new TextResourceContents(uri, "application/json", jsonEncoder.encode(payload))));
    }
}
