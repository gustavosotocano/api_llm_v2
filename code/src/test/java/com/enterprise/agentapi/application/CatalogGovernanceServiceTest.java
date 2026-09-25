package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentProperties;
import com.enterprise.agentapi.domain.CatalogProposalType;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.application.port.CategoryDictionaryRepository;
import com.enterprise.agentapi.infrastructure.InMemoryCatalogProposalStore;
import com.enterprise.agentapi.infrastructure.InMemoryCategoryDictionaryRepository;
import com.enterprise.agentapi.infrastructure.InMemoryIdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogGovernanceServiceTest {
    private CatalogGovernanceService service;
    private CategoryDictionaryRepository dictionary;

    @BeforeEach
    void setUp() {
        dictionary = new InMemoryCategoryDictionaryRepository();
        service = new CatalogGovernanceService(dictionary, new InMemoryCatalogProposalStore(), new InMemoryIdempotencyStore(), new AgentProperties());
    }

    @Test
    void agentCanOnlyProposeAndHumanMustApprove() {
        var proposed = service.propose(CatalogProposalType.NEW_CATEGORY, "UTILITIES",
                List.of("ENEL", "EPM"), "User asked for utilities", "session-1", "user-123", "catalog-propose-1");

        assertThat(proposed.status()).isEqualTo(SemanticStatus.CATALOG_CHANGE_PENDING_REVIEW);
        assertThat(dictionary.exists("UTILITIES")).isFalse();

        var rejectedReviewer = service.approve(proposed.proposalId(), "random-user", proposed.approvalToken());
        assertThat(rejectedReviewer.status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);

        var approved = service.approve(proposed.proposalId(), "catalog-admin", proposed.approvalToken());
        assertThat(approved.status()).isEqualTo(SemanticStatus.SUCCESS);
        assertThat(dictionary.exists("UTILITIES")).isTrue();
        assertThat(dictionary.merchantsFor("UTILITIES")).contains("ENEL", "EPM");
    }
}
