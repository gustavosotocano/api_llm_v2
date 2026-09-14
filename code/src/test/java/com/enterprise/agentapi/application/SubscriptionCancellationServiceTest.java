package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.AgentProperties;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.domain.SubscriptionCancellationRequest;
import com.enterprise.agentapi.infrastructure.ConfirmationTokenStore;
import com.enterprise.agentapi.infrastructure.IdempotencyStore;
import com.enterprise.agentapi.infrastructure.SubscriptionRegistry;
import com.enterprise.agentapi.infrastructure.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionCancellationServiceTest {
    private SubscriptionCancellationService service;

    @BeforeEach
    void setUp() {
        var properties = new AgentProperties();
        service = new SubscriptionCancellationService(
                new IdempotencyStore(),
                new ConfirmationTokenStore(properties),
                new SubscriptionRegistry(),
                new TransactionRepository());
        AgentContextHolder.set(new AgentContext("session-1", "user-123", "TEST", IdentityType.USER_DELEGATED));
    }

    @AfterEach
    void tearDown() {
        AgentContextHolder.clear();
    }

    @Test
    void firstCallRequiresHumanConfirmation() {
        var response = service.cancel(new SubscriptionCancellationRequest(
                "user-123", "NETFLIX", "cancel-netflix-1", null));

        assertThat(response.status()).isEqualTo(SemanticStatus.OPERATION_REQUIRES_CONFIRMATION);
        assertThat(response.confirmationToken()).startsWith("confirm-");
    }

    @Test
    void confirmedCallIsIdempotent() {
        var pending = service.cancel(new SubscriptionCancellationRequest(
                "user-123", "NETFLIX", "cancel-netflix-1", null));
        var executed = service.cancel(new SubscriptionCancellationRequest(
                "user-123", "NETFLIX", "cancel-netflix-1", pending.confirmationToken()));
        var replay = service.cancel(new SubscriptionCancellationRequest(
                "user-123", "NETFLIX", "cancel-netflix-1", pending.confirmationToken()));

        assertThat(executed.status()).isEqualTo(SemanticStatus.SUCCESS);
        assertThat(replay.status()).isEqualTo(SemanticStatus.SUCCESS);
        assertThat(replay.operationId()).isEqualTo(executed.operationId());
    }

    @Test
    void sameKeyDifferentMerchantIsConflict() {
        service.cancel(new SubscriptionCancellationRequest("user-123", "NETFLIX", "shared-key", null));
        var conflict = service.cancel(new SubscriptionCancellationRequest("user-123", "SPOTIFY", "shared-key", null));

        assertThat(conflict.status()).isEqualTo(SemanticStatus.IDEMPOTENCY_CONFLICT);
    }
}
