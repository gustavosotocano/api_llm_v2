package com.enterprise.agentapi.domain;

import java.time.Instant;
import java.util.List;

public record CatalogChangeProposal(
        String proposalId,
        CatalogProposalType proposalType,
        CatalogProposalStatus status,
        String categoryCode,
        List<String> merchants,
        String reason,
        String proposedByAgentSessionId,
        String proposedByUserId,
        Instant proposedAt,
        String approvalToken,
        String reviewedBy,
        Instant reviewedAt,
        String reviewComment
) {}
