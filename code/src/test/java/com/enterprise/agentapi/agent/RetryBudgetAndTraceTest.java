package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.ai.AgentToolSupport;
import com.enterprise.agentapi.domain.AgentWorkflow;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.JobResponse;
import com.enterprise.agentapi.domain.OperationRetry;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.observability.AgentAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryBudgetAndTraceTest {
    @AfterEach
    void tearDown() {
        OperationTrace.clear();
        ToolCallIds.clear();
        AgentContextHolder.clear();
    }

    @Test
    void completedCallRecordsRequestCostDurationAndDownstreamCalls() {
        var properties = new AgentProperties();
        var audit = new AgentAuditService(JsonMapper.builder().build());
        var support = support(properties, audit);
        AgentContextHolder.set(new AgentContext(
                "trace-session", "user-123", "TEST", IdentityType.USER_DELEGATED, AgentWorkflow.READ));
        OperationTrace.begin("req-fixed");

        support.execute("searchRecurringPayments", Map.<String, Object>of("userId", "user-123", "category", "STREAMING"), () -> {
            OperationTrace.recordDownstream();
            return outcome(SemanticStatus.SUCCESS);
        });

        var completed = audit.eventsForSession("trace-session").stream()
                .filter(event -> "TOOL_COMPLETED".equals(event.eventType()))
                .findFirst()
                .orElseThrow();
        assertThat(completed.attributes())
                .containsEntry("enterpriseRequestId", "req-fixed")
                .containsEntry("downstreamCallCount", 1)
                .containsEntry("retryCount", 0)
                .containsEntry("estimatedCostUnits", 2)
                .containsEntry("actualCostUnits", 2);
        assertThat((Long) completed.attributes().get("executionDurationMs")).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void permanentFailureStopsTheNextAttempt() {
        var properties = new AgentProperties();
        var support = support(properties, new AgentAuditService(JsonMapper.builder().build()));
        AgentContextHolder.set(new AgentContext(
                "retry-session", "user-123", "TEST", IdentityType.USER_DELEGATED, AgentWorkflow.READ));
        var params = Map.<String, Object>of("userId", "user-123", "category", "STREAMING");

        var first = support.execute("searchRecurringPayments", params,
                () -> outcome(SemanticStatus.INSUFFICIENT_PERMISSIONS));
        assertThat(first.status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);

        assertThatThrownBy(() -> support.execute("searchRecurringPayments", params,
                () -> outcome(SemanticStatus.SUCCESS)))
                .isInstanceOf(RetryBudgetExceededException.class);
    }

    @Test
    void retryAfterStopsOnceTheConfiguredRetriesAreUsed() {
        var properties = new AgentProperties();
        properties.getRetryBudget().setMaxRetries(1);
        var support = support(properties, new AgentAuditService(JsonMapper.builder().build()));
        AgentContextHolder.set(new AgentContext(
                "retry-after-session", "user-123", "TEST", IdentityType.USER_DELEGATED, AgentWorkflow.READ));
        var params = Map.<String, Object>of("category", "STREAMING");

        support.execute("searchRecurringPayments", params, () -> outcome(SemanticStatus.RATE_LIMITED));
        support.execute("searchRecurringPayments", params, () -> outcome(SemanticStatus.RATE_LIMITED));
        assertThatThrownBy(() -> support.execute("searchRecurringPayments", params,
                () -> outcome(SemanticStatus.RATE_LIMITED)))
                .isInstanceOf(RetryBudgetExceededException.class);
    }

    @Test
    void jobPollingDoesNotConsumeTheRetryBudget() {
        var properties = new AgentProperties();
        properties.getRetryBudget().setMaxRetries(0);
        var support = support(properties, new AgentAuditService(JsonMapper.builder().build()));
        AgentContextHolder.set(new AgentContext(
                "poll-session", "user-123", "TEST", IdentityType.USER_DELEGATED, AgentWorkflow.READ));
        var params = Map.<String, Object>of("jobId", "job-1");

        for (var i = 0; i < 4; i++) {
            var polled = support.execute("getJobStatus", params, () -> outcome(SemanticStatus.RATE_LIMITED));
            assertThat(polled.status()).isEqualTo(SemanticStatus.RATE_LIMITED);
        }
    }

    private static AgentToolSupport support(AgentProperties properties, AgentAuditService audit) {
        return new AgentToolSupport(
                new AgentRateLimiter(properties, audit),
                new ExecutionBudgetService(properties, audit),
                audit,
                new ToolAccessPolicy(),
                List.of(),
                new RetryBudgetService(properties));
    }

    private static JobResponse outcome(SemanticStatus status) {
        return new JobResponse(status, "test", null, null, null, null, List.of(), null,
                OperationRetry.forStatus(status, null));
    }
}
