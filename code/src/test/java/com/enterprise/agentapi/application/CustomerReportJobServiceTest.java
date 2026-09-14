package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.JobStatus;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.infrastructure.AsyncJobStore;
import com.enterprise.agentapi.infrastructure.CategoryDictionaryRepository;
import com.enterprise.agentapi.infrastructure.TransactionRepository;
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
        var search = new TransactionSearchService(new TransactionRepository(), new CategoryDictionaryRepository(), clock);
        service = new CustomerReportJobService(new AsyncJobStore(), search);
        AgentContextHolder.set(new AgentContext("session-1", "user-123", "TEST", IdentityType.USER_DELEGATED));
    }

    @AfterEach
    void tearDown() {
        AgentContextHolder.clear();
    }

    @Test
    void startPollAndResultUseExplicitJobStates() {
        var accepted = service.startReport("user-123", "LAST_3_MONTHS");
        assertThat(accepted.status()).isEqualTo(SemanticStatus.ACCEPTED);
        assertThat(accepted.jobId()).startsWith("job-");

        await().pollInSameThread().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(service.status(accepted.jobId()).jobStatus()).isEqualTo(JobStatus.COMPLETED));

        var result = service.result(accepted.jobId());
        assertThat(result.status()).isEqualTo(SemanticStatus.SUCCESS);
        assertThat(result.result()).containsKey("merchantSummaries");
    }

    @Test
    void invalidPeriodIsDeterministic() {
        var response = service.startReport("user-123", "YESTERDAY");
        assertThat(response.status()).isEqualTo(SemanticStatus.INVALID_PERIOD);
    }
}
