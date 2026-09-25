package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.domain.AgentWorkflow;
import com.enterprise.agentapi.domain.CapabilityScope;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.PeriodOption;
import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.infrastructure.CategoryDictionaryRepository;
import com.enterprise.agentapi.infrastructure.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionSearchServiceTest {
    private TransactionSearchService service;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(LocalDate.of(2026, 5, 20).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new TransactionSearchService(new TransactionRepository(), new CategoryDictionaryRepository(), clock);
        AgentContextHolder.set(new AgentContext("session-1", "user-123", "TEST", IdentityType.USER_DELEGATED));
    }

    @AfterEach
    void tearDown() {
        AgentContextHolder.clear();
    }

    @Test
    void searchStreamingLastThreeMonthsReturnsMerchantSummaries() {
        var response = service.searchRecurringPayments(new RecurringPaymentSearchRequest(
                "user-123", "STREAMING", null, PeriodOption.LAST_3_MONTHS, 100));

        assertThat(response.status()).isEqualTo(SemanticStatus.SUCCESS);
        assertThat(response.resolvedFromDate()).isEqualTo(LocalDate.of(2026, 2, 20));
        assertThat(response.resolvedToDate()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(response.merchantSummaries()).extracting(summary -> summary.normalizedMerchant())
                .containsExactly("NETFLIX", "SPOTIFY");
        assertThat(response.resultCount()).isEqualTo(6);
    }

    @Test
    void unknownCategoryDoesNotInventSemantics() {
        var response = service.searchRecurringPayments(new RecurringPaymentSearchRequest(
                "user-123", "UTILITIES", null, PeriodOption.LAST_3_MONTHS, 100));

        assertThat(response.status()).isEqualTo(SemanticStatus.UNKNOWN_CATEGORY);
        assertThat(response.knownCategories()).contains("STREAMING", "TELECOMMUNICATIONS");
    }

    @Test
    void delegatedIdentityCannotReadAnotherUser() {
        var response = service.searchRecurringPayments(new RecurringPaymentSearchRequest(
                "user-456", "STREAMING", null, PeriodOption.LAST_3_MONTHS, 100));

        assertThat(response.status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);
    }

    @Test
    void serviceIdentityIsNotAGodAccount() {
        AgentContextHolder.set(new AgentContext("session-svc", "reconciliation-bot", "TEST", IdentityType.SERVICE));
        var response = service.searchRecurringPayments(new RecurringPaymentSearchRequest(
                "user-123", "STREAMING", null, PeriodOption.LAST_3_MONTHS, 100));
        assertThat(response.status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);
    }

    @Test
    void serviceMayReadOnlyTheGrantedUser() {
        AgentContextHolder.set(new AgentContext(
                "session-svc", "reconciliation-bot", "TEST", IdentityType.SERVICE, AgentWorkflow.READ,
                Set.of(CapabilityScope.TRANSACTIONS_READ), "user-123", null));
        var allowed = service.searchRecurringPayments(new RecurringPaymentSearchRequest(
                "user-123", "STREAMING", null, PeriodOption.LAST_3_MONTHS, 100));
        var denied = service.searchRecurringPayments(new RecurringPaymentSearchRequest(
                "user-456", "STREAMING", null, PeriodOption.LAST_3_MONTHS, 100));
        assertThat(allowed.status()).isEqualTo(SemanticStatus.SUCCESS);
        assertThat(denied.status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);
    }

    @Test
    void missingUserIdIsNotAssumed() {
        var response = service.searchRecurringPayments(new RecurringPaymentSearchRequest(
                null, "STREAMING", null, PeriodOption.LAST_3_MONTHS, 100));
        assertThat(response.status()).isEqualTo(SemanticStatus.INSUFFICIENT_PERMISSIONS);
        assertThat(response.message()).contains("userId is required");
    }

    @Test
    void missingCategoryAndMerchantRequiresClarification() {
        var response = service.searchRecurringPayments(new RecurringPaymentSearchRequest(
                "user-123", null, null, PeriodOption.LAST_3_MONTHS, 100));

        assertThat(response.status()).isEqualTo(SemanticStatus.CLARIFICATION_REQUIRED);
    }
}
