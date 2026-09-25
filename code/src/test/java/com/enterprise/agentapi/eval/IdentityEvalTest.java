package com.enterprise.agentapi.eval;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.agent.ServiceCredentialRegistry;
import com.enterprise.agentapi.domain.AgentWorkflow;
import com.enterprise.agentapi.domain.CapabilityScope;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.mcp.McpAgentContextBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V2 §4 identity and authorization")
class IdentityEvalTest {
    private final EvalHarness harness = EvalHarness.create();

    @AfterEach
    void tearDown() {
        AgentContextHolder.clear();
        OperationTrace.clear();
    }

    @Test
    void serviceWithoutGrantCannotReadAnyUser() {
        var scenario = search("identity-service-no-grant", "eval-id-svc-001");
        var trace = harness.run(scenario, new AgentContext(
                scenario.agentSessionId(), "reconciliation-bot", "EVAL",
                IdentityType.SERVICE, AgentWorkflow.FULL, Set.of(CapabilityScope.TRANSACTIONS_READ),
                null, null));
        assertThat(trace.getLast().status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);
    }

    @Test
    void serviceGrantIsScopedToOneUser() {
        var allowed = search("identity-service-allowed-user", "eval-id-svc-002");
        var allowedTrace = harness.run(allowed, new AgentContext(
                allowed.agentSessionId(), "reconciliation-bot", "EVAL",
                IdentityType.SERVICE, AgentWorkflow.FULL, Set.of(CapabilityScope.TRANSACTIONS_READ),
                "user-123", null));
        assertThat(allowedTrace.getLast().status()).isEqualTo(SemanticStatus.SUCCESS);

        var denied = new GoldenScenario(
                "identity-service-other-user",
                "Show user-456 streaming payments",
                "user-456",
                "eval-id-svc-003",
                List.of(ScriptedTurn.of(
                        "searchRecurringPayments",
                        "userId", "user-456",
                        "category", "STREAMING",
                        "period", "LAST_3_MONTHS")),
                EvalExpectation.builder().requiredTools("searchRecurringPayments").build());
        var deniedTrace = harness.run(denied, new AgentContext(
                denied.agentSessionId(), "reconciliation-bot", "EVAL",
                IdentityType.SERVICE, AgentWorkflow.FULL, Set.of(CapabilityScope.TRANSACTIONS_READ),
                "user-123", null));
        assertThat(deniedTrace.getLast().status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);
    }

    @Test
    void missingWriteScopeCannotCancelEvenWithFullWorkflow() {
        var scenario = new GoldenScenario(
                "identity-read-scope-cannot-cancel",
                "Cancel Netflix",
                "user-123",
                "eval-id-scope-001",
                List.of(ScriptedTurn.of(
                        "cancelRecurringSubscription",
                        "userId", "user-123",
                        "merchant", "NETFLIX",
                        "idempotencyKey", "eval-id-netflix-1")),
                EvalExpectation.builder().requiredTools("cancelRecurringSubscription").build());
        var trace = harness.run(scenario, new AgentContext(
                scenario.agentSessionId(), scenario.userId(), "EVAL",
                IdentityType.USER_DELEGATED, AgentWorkflow.FULL,
                Set.of(CapabilityScope.TRANSACTIONS_READ), null, null));
        assertThat(trace.getLast().status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);
        assertThat(trace.getLast().success()).isFalse();
    }

    @Test
    void expiredCredentialIsDenied() {
        var scenario = search("identity-expired-credential", "eval-id-exp-001");
        var trace = harness.run(scenario, new AgentContext(
                scenario.agentSessionId(), "reconciliation-bot", "EVAL",
                IdentityType.SERVICE, AgentWorkflow.FULL, Set.of(CapabilityScope.TRANSACTIONS_READ),
                "user-123", Instant.now().minusSeconds(5)));
        assertThat(trace.getLast().status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);
    }

    @Test
    void omittedUserIdDoesNotFallBackToADemoAccount() {
        var binder = new McpAgentContextBinder(new ServiceCredentialRegistry());
        binder.bind("eval-id-missing-user", "  ", null);
        var context = AgentContextHolder.get();
        assertThat(context.userId()).isNull();
        assertThat(context.identityType()).isEqualTo(IdentityType.USER_DELEGATED);
        assertThat(context.workflow()).isEqualTo(AgentWorkflow.READ);
    }

    @Test
    void clientMetaCannotSelfElevateToService() {
        var binder = new McpAgentContextBinder(new ServiceCredentialRegistry());
        binder.bind("eval-id-spoof-001", "user-123", new McpMeta(Map.of("identityType", "SERVICE")));
        var context = AgentContextHolder.get();
        assertThat(context.identityType()).isEqualTo(IdentityType.USER_DELEGATED);
        assertThat(context.userId()).isEqualTo("user-123");
        assertThat(context.onBehalfOfUserId()).isNull();
    }

    @Test
    void issuedServiceCredentialCanBindOnBehalfOfAllowedUser() {
        var registry = new ServiceCredentialRegistry();
        var grant = registry.issue(
                "reconciliation-bot",
                Set.of(CapabilityScope.TRANSACTIONS_READ),
                Set.of("user-123"),
                Duration.ofMinutes(5));
        var binder = new McpAgentContextBinder(registry);
        binder.bind("eval-id-bind-001", "user-123", new McpMeta(Map.of(
                "serviceId", grant.serviceId(),
                "serviceCredential", grant.credential(),
                "identityType", "SERVICE",
                "workflow", "READ")));

        var context = AgentContextHolder.get();
        assertThat(context.identityType()).isEqualTo(IdentityType.SERVICE);
        assertThat(context.userId()).isEqualTo("reconciliation-bot");
        assertThat(context.onBehalfOfUserId()).isEqualTo("user-123");
        assertThat(context.scopes()).containsExactly(CapabilityScope.TRANSACTIONS_READ);
    }

    private static GoldenScenario search(String id, String session) {
        return new GoldenScenario(
                id,
                "Show streaming payments",
                "user-123",
                session,
                List.of(ScriptedTurn.of(
                        "searchRecurringPayments",
                        "userId", "user-123",
                        "category", "STREAMING",
                        "period", "LAST_3_MONTHS")),
                EvalExpectation.builder().requiredTools("searchRecurringPayments").build());
    }
}
