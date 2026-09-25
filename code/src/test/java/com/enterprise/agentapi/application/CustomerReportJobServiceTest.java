package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.JobStatus;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.enterprise.AgentBoundary;
import com.enterprise.agentapi.domain.RetryDisposition;
import com.enterprise.agentapi.infrastructure.AsyncJobStore;
import com.enterprise.agentapi.infrastructure.CategoryDictionaryRepository;
import com.enterprise.agentapi.infrastructure.ConfirmationTokenStore;
import com.enterprise.agentapi.infrastructure.CustomerProfileRepository;
import com.enterprise.agentapi.infrastructure.IdempotencyStore;
import com.enterprise.agentapi.infrastructure.SubscriptionRegistry;
import com.enterprise.agentapi.infrastructure.TransactionRepository;
import com.enterprise.agentapi.agent.AgentProperties;
import com.enterprise.agentapi.observability.AgentAuditService;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

class CustomerReportJobServiceTest {
    private CustomerReportJobService service;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(LocalDate.of(2026, 5, 20).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        var transactions = new TransactionRepository();
        var search = new TransactionSearchService(transactions, new CategoryDictionaryRepository(), clock);
        var cancellation = new SubscriptionCancellationService(
                new IdempotencyStore(),
                new ConfirmationTokenStore(new AgentProperties()),
                new SubscriptionRegistry(),
                transactions);
        service = new CustomerReportJobService(
                new AsyncJobStore(),
                new IdempotencyStore(),
                new CustomerProfileService(new CustomerProfileRepository()),
                search,
                cancellation,
                new AgentAuditService(JsonMapper.builder().build()));
        AgentContextHolder.set(new AgentContext("session-1", "user-123", "TEST", IdentityType.USER_DELEGATED));
    }

    @AfterEach
    void tearDown() {
        AgentContextHolder.clear();
    }

    @Test
    void startPollAndResultUseExplicitJobStates() {
        var accepted = service.startReport("user-123", "LAST_3_MONTHS", "report-1");
        assertThat(accepted.status()).isEqualTo(SemanticStatus.ACCEPTED);
        assertThat(accepted.jobId()).startsWith("job-");
        assertThat(accepted.retry().disposition()).isEqualTo(RetryDisposition.IN_PROGRESS);
        assertThat(accepted.retry().retryAfterSeconds()).isEqualTo(2);

        await().pollInSameThread().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(service.status(accepted.jobId()).jobStatus()).isEqualTo(JobStatus.COMPLETED));

        var result = service.result(accepted.jobId());
        assertThat(result.status()).isEqualTo(SemanticStatus.SUCCESS);
        assertThat(result.result()).containsKey("merchantSummaries");
        assertThat(result.result()).containsEntry("composedFrom", AgentBoundary.ENTERPRISE_APIS);
        assertThat(result.result()).containsKey("customer");
        assertThat(result.result()).containsKey("enterpriseRequestId");
        assertThat((Integer) result.result().get("downstreamCallCount")).isGreaterThanOrEqualTo(3);
        assertThat((Long) result.result().get("executionDurationMs")).isGreaterThan(0L);
    }

    @Test
    void invalidPeriodIsDeterministic() {
        var response = service.startReport("user-123", "YESTERDAY", "report-invalid-period");
        assertThat(response.status()).isEqualTo(SemanticStatus.INVALID_PERIOD);
    }

    @Test
    void rawDateIsInvalidDateRange() {
        var response = service.startReport("user-123", "2026-01-01", "report-raw-date");
        assertThat(response.status()).isEqualTo(SemanticStatus.INVALID_DATE_RANGE);
        assertThat(response.jobId()).isNull();
    }

    @Test
    void cancelMovesAnActiveJobToCancelledAndTheWorkerDoesNotFinishIt() {
        var accepted = service.startReport("user-123", "LAST_3_MONTHS", "report-cancel");
        var cancelled = service.cancel(accepted.jobId());
        assertThat(cancelled.status()).isEqualTo(SemanticStatus.CANCELLED);
        assertThat(cancelled.jobStatus()).isEqualTo(JobStatus.CANCELLED);

        await().pollDelay(Duration.ofMillis(1100)).pollInSameThread().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(service.status(accepted.jobId()).jobStatus()).isEqualTo(JobStatus.CANCELLED));
        assertThat(service.result(accepted.jobId()).status()).isEqualTo(SemanticStatus.CANCELLED);
        assertThat(cancelled.retry().disposition()).isEqualTo(RetryDisposition.DO_NOT_RETRY);
    }

    @Test
    void sameIdempotencyKeyReturnsTheExistingJob() {
        var first = service.startReport("user-123", "LAST_3_MONTHS", "report-same-key");
        var second = service.startReport("user-123", "LAST_3_MONTHS", "report-same-key");
        assertThat(second.jobId()).isEqualTo(first.jobId());
    }

    @Test
    void aSecondInFlightReportPointsAtTheActiveJob() {
        var first = service.startReport("user-123", "LAST_3_MONTHS", "report-inflight-1");
        var second = service.startReport("user-123", "LAST_3_MONTHS", "report-inflight-2");
        assertThat(second.status()).isEqualTo(SemanticStatus.OPERATION_IN_PROGRESS);
        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.retry().disposition()).isEqualTo(RetryDisposition.IN_PROGRESS);
    }
}
