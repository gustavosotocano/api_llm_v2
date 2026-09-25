package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentProperties;
import com.enterprise.agentapi.domain.CatalogChangeProposal;
import com.enterprise.agentapi.domain.CatalogChangeResponse;
import com.enterprise.agentapi.domain.CatalogProposalStatus;
import com.enterprise.agentapi.domain.CatalogProposalType;
import com.enterprise.agentapi.domain.OperationRetry;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.enterprise.CatalogChangeApi;
import com.enterprise.agentapi.infrastructure.CatalogProposalStore;
import com.enterprise.agentapi.infrastructure.CategoryDictionaryRepository;
import com.enterprise.agentapi.infrastructure.IdempotencyStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CatalogGovernanceService implements CatalogChangeApi {
    private final CategoryDictionaryRepository dictionaryRepository;
    private final CatalogProposalStore proposalStore;
    private final IdempotencyStore idempotencyStore;
    private final AgentProperties agentProperties;

    public CatalogGovernanceService(CategoryDictionaryRepository dictionaryRepository,
                                    CatalogProposalStore proposalStore,
                                    IdempotencyStore idempotencyStore,
                                    AgentProperties agentProperties) {
        this.dictionaryRepository = dictionaryRepository;
        this.proposalStore = proposalStore;
        this.idempotencyStore = idempotencyStore;
        this.agentProperties = agentProperties;
    }

    @Override
    public CatalogChangeResponse propose(CatalogProposalType proposalType,
                                         String categoryCode,
                                         List<String> merchants,
                                         String reason,
                                         String agentSessionId,
                                         String userId,
                                         String idempotencyKey) {
        var key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key == null) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "idempotencyKey is required to propose a catalog change.",
                    null, proposalType, categoryCode, merchants, null,
                    List.of("Send a stable idempotencyKey for this proposal"));
        }
        var category = normalizeCategory(categoryCode);
        var merchantSet = normalizeMerchants(merchants);
        var normalizedReason = normalizeReason(reason);
        var fingerprint = userId + "|" + proposalType + "|" + category + "|" + merchantSet + "|" + normalizedReason;
        if (idempotencyStore.hasDifferentPayload(key, fingerprint)) {
            return response(SemanticStatus.IDEMPOTENCY_CONFLICT,
                    "The same Idempotency-Key was already used with different parameters.",
                    null, proposalType, category, List.copyOf(merchantSet), null,
                    List.of("Reuse the original parameters or generate a new idempotencyKey"));
        }
        var cached = idempotencyStore.find(key, CatalogChangeResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (proposalType == CatalogProposalType.NEW_CATEGORY) {
            if (dictionaryRepository.exists(category)) {
                return response(SemanticStatus.CLARIFICATION_REQUIRED,
                        "Category already exists in the approved catalog.",
                        null, proposalType, category, List.copyOf(merchantSet), null,
                        List.of("Use existing category " + category, "Or propose ADD_MERCHANTS to extend it"));
            }
        } else if (proposalType == CatalogProposalType.ADD_MERCHANTS) {
            if (!dictionaryRepository.exists(category)) {
                return response(SemanticStatus.UNKNOWN_CATEGORY,
                        "Cannot add merchants to unknown category. Propose NEW_CATEGORY first.",
                        null, proposalType, category, List.copyOf(merchantSet), null,
                        List.of("Propose NEW_CATEGORY for " + category, "See banking://governance/catalog-policy"));
            }
        }

        var existingPending = proposalStore.findPendingForCategory(category);
        if (existingPending.isPresent()) {
            var pending = existingPending.get();
            return response(SemanticStatus.CATALOG_CHANGE_PENDING_REVIEW,
                    "A proposal for this category is already pending human review.",
                    pending.proposalId(), pending.proposalType(), pending.categoryCode(), pending.merchants(),
                    pending.approvalToken(),
                    List.of("Wait for reviewer approval", "GET /debug/governance/catalog/proposals"));
        }

        var proposalId = "cat-prop-" + UUID.randomUUID();
        var approvalToken = "gov-approve-" + UUID.randomUUID();
        var proposal = new CatalogChangeProposal(
                proposalId, proposalType, CatalogProposalStatus.PENDING_REVIEW, category,
                List.copyOf(merchantSet), normalizedReason, agentSessionId, userId,
                Instant.now(), approvalToken, null, null, null);
        proposalStore.save(proposal);

        var submitted = response(SemanticStatus.CATALOG_CHANGE_PENDING_REVIEW,
                "Catalog change submitted for human review. The agent cannot apply it directly.",
                proposalId, proposalType, category, List.copyOf(merchantSet), approvalToken,
                List.of(
                        "A human reviewer must approve via POST /debug/governance/catalog/proposals/" + proposalId + "/approve",
                        "Use X-Governance-Reviewer and X-Governance-Approval-Token headers",
                        "Read resource banking://governance/pending-proposals"));
        idempotencyStore.save(key, submitted, fingerprint);
        return submitted;
    }

    public CatalogChangeResponse approve(String proposalId, String reviewerId, String approvalToken) {
        if (!isAuthorizedReviewer(reviewerId)) {
            return response(SemanticStatus.INSUFFICIENT_PERMISSIONS,
                    "Reviewer is not authorized to approve catalog changes.",
                    proposalId, null, null, List.of(), null,
                    List.of("Configure allowed reviewers in enterprise.agent.governance.allowed-reviewers"));
        }

        var proposal = proposalStore.find(proposalId).orElse(null);
        if (proposal == null) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Proposal not found.", proposalId, null, null, List.of(), null, List.of());
        }
        if (proposal.status() != CatalogProposalStatus.PENDING_REVIEW) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Proposal is not pending review (status=" + proposal.status() + ").",
                    proposalId, proposal.proposalType(), proposal.categoryCode(), proposal.merchants(), null, List.of());
        }
        if (!proposal.approvalToken().equals(approvalToken)) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Invalid governance approval token.",
                    proposalId, proposal.proposalType(), proposal.categoryCode(), proposal.merchants(), null, List.of());
        }

        applyApprovedChange(proposal);
        proposalStore.save(new CatalogChangeProposal(
                proposal.proposalId(), proposal.proposalType(), CatalogProposalStatus.APPROVED,
                proposal.categoryCode(), proposal.merchants(), proposal.reason(),
                proposal.proposedByAgentSessionId(), proposal.proposedByUserId(), proposal.proposedAt(),
                proposal.approvalToken(), reviewerId, Instant.now(), "Approved by " + reviewerId));

        return response(SemanticStatus.SUCCESS,
                "Catalog change approved and applied to the controlled dictionary.",
                proposalId, proposal.proposalType(), proposal.categoryCode(), proposal.merchants(), null,
                List.of("Category is now available for searchRecurringPayments"));
    }

    public CatalogChangeResponse reject(String proposalId, String reviewerId, String approvalToken, String comment) {
        if (!isAuthorizedReviewer(reviewerId)) {
            return response(SemanticStatus.INSUFFICIENT_PERMISSIONS,
                    "Reviewer is not authorized to reject catalog changes.",
                    proposalId, null, null, List.of(), null, List.of());
        }

        var proposal = proposalStore.find(proposalId).orElse(null);
        if (proposal == null) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Proposal not found.", proposalId, null, null, List.of(), null, List.of());
        }
        if (proposal.status() != CatalogProposalStatus.PENDING_REVIEW) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Proposal is not pending review.",
                    proposalId, proposal.proposalType(), proposal.categoryCode(), proposal.merchants(), null, List.of());
        }
        if (!proposal.approvalToken().equals(approvalToken)) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Invalid governance approval token.",
                    proposalId, proposal.proposalType(), proposal.categoryCode(), proposal.merchants(), null, List.of());
        }

        proposalStore.save(new CatalogChangeProposal(
                proposal.proposalId(), proposal.proposalType(), CatalogProposalStatus.REJECTED,
                proposal.categoryCode(), proposal.merchants(), proposal.reason(),
                proposal.proposedByAgentSessionId(), proposal.proposedByUserId(), proposal.proposedAt(),
                proposal.approvalToken(), reviewerId, Instant.now(),
                comment == null || comment.isBlank() ? "Rejected by " + reviewerId : comment));

        return response(SemanticStatus.CATALOG_CHANGE_REJECTED,
                "Catalog change proposal rejected.",
                proposalId, proposal.proposalType(), proposal.categoryCode(), proposal.merchants(), null,
                List.of("Inform the user and do not use the proposed category until a new proposal is approved"));
    }

    public List<CatalogChangeProposal> listProposals(CatalogProposalStatus status) {
        return proposalStore.listByStatus(status);
    }

    private void applyApprovedChange(CatalogChangeProposal proposal) {
        var merchants = new LinkedHashSet<>(proposal.merchants());
        if (proposal.proposalType() == CatalogProposalType.NEW_CATEGORY) {
            dictionaryRepository.applyCategory(proposal.categoryCode(), merchants);
        } else {
            dictionaryRepository.addMerchants(proposal.categoryCode(), merchants);
        }
    }

    private boolean isAuthorizedReviewer(String reviewerId) {
        return reviewerId != null && !reviewerId.isBlank()
                && agentProperties.getGovernance().getAllowedReviewers().contains(reviewerId.trim());
    }

    private CatalogChangeResponse response(SemanticStatus status, String message, String proposalId,
                                           CatalogProposalType type, String category, List<String> merchants,
                                           String approvalToken, List<String> suggestions) {
        return new CatalogChangeResponse(
                status, message, proposalId, type, category,
                merchants == null ? List.of() : merchants, approvalToken,
                dictionaryRepository.knownCategories(), suggestions,
                OperationRetry.forStatus(status, null));
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("categoryCode is required");
        }
        return category.trim().toUpperCase();
    }

    private Set<String> normalizeMerchants(List<String> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            throw new IllegalArgumentException("At least one merchant is required");
        }
        return merchants.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(m -> m.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required for catalog governance audit");
        }
        return reason.trim();
    }
}
