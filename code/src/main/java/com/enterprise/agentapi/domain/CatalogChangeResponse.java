package com.enterprise.agentapi.domain;

import java.util.List;

public record CatalogChangeResponse(
        SemanticStatus status,
        String message,
        String proposalId,
        CatalogProposalType proposalType,
        String categoryCode,
        List<String> merchants,
        String approvalToken,
        List<String> knownCategories,
        List<String> suggestions,
        OperationRetry retry
) {}
