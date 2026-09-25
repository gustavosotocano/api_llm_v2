package com.enterprise.agentapi.application.port;

import com.enterprise.agentapi.domain.CatalogChangeProposal;
import com.enterprise.agentapi.domain.CatalogProposalStatus;

import java.util.List;
import java.util.Optional;

public interface CatalogProposalStore {
    void save(CatalogChangeProposal proposal);

    Optional<CatalogChangeProposal> find(String proposalId);

    Optional<CatalogChangeProposal> findPendingForCategory(String categoryCode);

    List<CatalogChangeProposal> listByStatus(CatalogProposalStatus status);
}
