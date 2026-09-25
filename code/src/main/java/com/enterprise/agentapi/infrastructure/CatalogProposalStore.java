package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.domain.CatalogChangeProposal;
import com.enterprise.agentapi.domain.CatalogProposalStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CatalogProposalStore {
    private final Map<String, CatalogChangeProposal> proposals = new ConcurrentHashMap<>();

    public void save(CatalogChangeProposal proposal) {
        OperationTrace.recordDownstream();
        proposals.put(proposal.proposalId(), proposal);
    }

    public Optional<CatalogChangeProposal> find(String proposalId) {
        OperationTrace.recordDownstream();
        return Optional.ofNullable(proposals.get(proposalId));
    }

    public Optional<CatalogChangeProposal> findPendingForCategory(String categoryCode) {
        OperationTrace.recordDownstream();
        return proposals.values().stream()
                .filter(p -> p.status() == CatalogProposalStatus.PENDING_REVIEW)
                .filter(p -> p.categoryCode().equalsIgnoreCase(categoryCode))
                .findFirst();
    }

    public List<CatalogChangeProposal> listByStatus(CatalogProposalStatus status) {
        OperationTrace.recordDownstream();
        return proposals.values().stream()
                .filter(p -> p.status() == status)
                .toList();
    }
}
