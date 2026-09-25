package com.enterprise.agentapi.eval;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.domain.CatalogProposalType;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.PeriodOption;
import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.domain.SubscriptionCancellationRequest;
import com.enterprise.agentapi.mcp.BankingMcpTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V2 §6 tool contract tests")
class ToolContractEvalTest {
    private EvalHarness harness;

    @BeforeEach
    void setUp() {
        harness = EvalHarness.create();
        AgentContextHolder.set(new AgentContext("contract-session", "user-123", "EVAL", IdentityType.USER_DELEGATED));
    }

    @AfterEach
    void tearDown() {
        AgentContextHolder.clear();
    }

    @Test
    void mcpSurfaceExposesSemanticallyClearTools() {
        var tools = Arrays.stream(BankingMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> method.getAnnotation(McpTool.class))
                .toList();

        assertThat(tools).extracting(McpTool::name).containsExactlyInAnyOrder(
                "searchRecurringPayments",
                "cancelRecurringSubscription",
                "proposeCatalogChange",
                "startCustomerReport",
                "cancelCustomerReport",
                "getJobStatus",
                "getJobResult");
        assertThat(tools)
                .filteredOn(tool -> tool.name().equals("searchRecurringPayments"))
                .first()
                .extracting(tool -> tool.annotations().readOnlyHint())
                .isEqualTo(true);
        assertThat(tools)
                .filteredOn(tool -> tool.name().equals("cancelRecurringSubscription"))
                .first()
                .extracting(tool -> tool.annotations().destructiveHint() && tool.annotations().idempotentHint())
                .isEqualTo(true);
    }

    @Test
    void approveIsNotAnMcpTool() {
        var names = Arrays.stream(BankingMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> method.getAnnotation(McpTool.class).name())
                .collect(Collectors.toSet());
        assertThat(names).doesNotContain("approveCatalogChange", "rejectCatalogChange", "applyCatalogChange");
    }

    @Test
    void writeToolMarksConfirmationTokenOptionalAndIdempotencyRequired() {
        var cancel = Arrays.stream(BankingMcpTools.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("cancelRecurringSubscription"))
                .findFirst()
                .orElseThrow();
        var params = Arrays.stream(cancel.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(McpToolParam.class))
                .collect(Collectors.toMap(
                        parameter -> parameter.getName(),
                        parameter -> parameter.getAnnotation(McpToolParam.class).required()));

        assertThat(params.get("idempotencyKey")).isTrue();
        assertThat(params.get("confirmationToken")).isFalse();
    }

    @Test
    void searchMissingCategoryIsClarificationRequired() {
        var scenario = new GoldenScenario(
                "contract-clarification",
                "Show my payments",
                "user-123",
                "contract-search-1",
                List.of(ScriptedTurn.of("searchRecurringPayments", "userId", "user-123", "period", "LAST_3_MONTHS")),
                EvalExpectation.builder()
                        .requiredTools("searchRecurringPayments")
                        .expectedFinalStatus(SemanticStatus.CLARIFICATION_REQUIRED)
                        .expectedSemanticStatus(SemanticStatus.CLARIFICATION_REQUIRED)
                        .build());
        var verdicts = BehavioralEvaluator.evaluate(scenario.expectation(), harness.run(scenario));
        assertThat(verdicts).allMatch(EvalVerdict::passed);
    }

    @Test
    void cancelSameKeyDifferentMerchantIsIdempotencyConflict() {
        var first = ScriptedTurn.of(
                "cancelRecurringSubscription",
                "userId", "user-123",
                "merchant", "NETFLIX",
                "idempotencyKey", "shared-contract-key");
        var second = ScriptedTurn.of(
                "cancelRecurringSubscription",
                "userId", "user-123",
                "merchant", "SPOTIFY",
                "idempotencyKey", "shared-contract-key");
        var scenario = new GoldenScenario(
                "contract-idempotency",
                "Cancel Spotify with reused key",
                "user-123",
                "contract-cancel-1",
                List.of(first, second),
                EvalExpectation.builder()
                        .requiredTools("cancelRecurringSubscription")
                        .expectedFinalStatus(SemanticStatus.IDEMPOTENCY_CONFLICT)
                        .expectedSemanticStatus(SemanticStatus.IDEMPOTENCY_CONFLICT)
                        .build());
        var verdicts = BehavioralEvaluator.evaluate(scenario.expectation(), harness.run(scenario));
        assertThat(verdicts).allMatch(EvalVerdict::passed);
    }

    @Test
    void rawDatePeriodIsInvalidDateRange() {
        var scenario = new GoldenScenario(
                "contract-raw-date",
                "Search from 2026-01-01",
                "user-123",
                "contract-date-1",
                List.of(ScriptedTurn.of(
                        "searchRecurringPayments",
                        "userId", "user-123",
                        "category", "STREAMING",
                        "period", "2026-01-01")),
                EvalExpectation.builder()
                        .requiredTools("searchRecurringPayments")
                        .expectedFinalStatus(SemanticStatus.INVALID_DATE_RANGE)
                        .expectedSemanticStatus(SemanticStatus.INVALID_DATE_RANGE)
                        .build());
        var verdicts = BehavioralEvaluator.evaluate(scenario.expectation(), harness.run(scenario));
        assertThat(verdicts).allMatch(EvalVerdict::passed);
    }

    @Test
    void invalidCatalogProposalStaysInsideTheSemanticContract() {
        var scenario = new GoldenScenario(
                "contract-catalog-clarification",
                "Propose a category",
                "user-123",
                "contract-cat-1",
                List.of(ScriptedTurn.of(
                        "proposeCatalogChange",
                        "proposalType", "INVENT",
                        "categoryCode", "UTILITIES",
                        "merchants", "",
                        "reason", "needed",
                        "userId", "user-123",
                        "agentSessionId", "contract-cat-1")),
                EvalExpectation.builder()
                        .requiredTools("proposeCatalogChange")
                        .expectedFinalStatus(SemanticStatus.CLARIFICATION_REQUIRED)
                        .expectedSemanticStatus(SemanticStatus.CLARIFICATION_REQUIRED)
                        .build());
        var verdicts = BehavioralEvaluator.evaluate(scenario.expectation(), harness.run(scenario));
        assertThat(verdicts).allMatch(EvalVerdict::passed);
    }

    @Test
    void domainRequestRecordsStayAlignedWithToolParams() {
        var searchFields = Arrays.stream(RecurringPaymentSearchRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        assertThat(searchFields).containsExactlyInAnyOrder("userId", "category", "merchant", "period", "limit");
        assertThat(searchFields).doesNotContainAnyElementsOf(BehavioralEvaluator.UNSUPPORTED_PARAMETERS);

        var cancelFields = Arrays.stream(SubscriptionCancellationRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        assertThat(cancelFields).contains("idempotencyKey", "confirmationToken");
        assertThat(Set.of(PeriodOption.values())).contains(PeriodOption.LAST_3_MONTHS);
        assertThat(CatalogProposalType.values()).contains(CatalogProposalType.NEW_CATEGORY, CatalogProposalType.ADD_MERCHANTS);
    }
}
