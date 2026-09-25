package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.application.port.CatalogProposalStore;
import com.enterprise.agentapi.domain.CatalogChangeProposal;
import com.enterprise.agentapi.domain.CatalogProposalStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCatalogProposalStore implements CatalogProposalStore {
    private final Map<String, CatalogChangeProposal> proposals = new ConcurrentHashMap<>();

    @Override
    public void save(CatalogChangeProposal proposal) {
        OperationTrace.recordDownstream();
        proposals.put(proposal.proposalId(), proposal);
    }

    @Override
    public Optional<CatalogChangeProposal> find(String proposalId) {
        OperationTrace.recordDownstream();
        return Optional.ofNullable(proposals.get(proposalId));
    }

    @Override
    public Optional<CatalogChangeProposal> findPendingForCategory(String categoryCode) {
        OperationTrace.recordDownstream();
        return proposals.values().stream()
                .filter(p -> p.status() == CatalogProposalStatus.PENDING_REVIEW)
                .filter(p -> p.categoryCode().equalsIgnoreCase(categoryCode))
                .findFirst();
    }

    @Override
    public List<CatalogChangeProposal> listByStatus(CatalogProposalStatus status) {
        OperationTrace.recordDownstream();
        return proposals.values().stream()
                .filter(p -> p.status() == status)
                .toList();
    }
}
