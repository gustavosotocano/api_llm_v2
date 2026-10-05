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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
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
    void writeToolsDoNotAskTheModelForAnIdempotencyKey() {
        for (var toolName : List.of("cancelRecurringSubscription", "proposeCatalogChange", "startCustomerReport")) {
            var method = Arrays.stream(BankingMcpTools.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(toolName))
                    .findFirst()
                    .orElseThrow();
            var params = Arrays.stream(method.getParameters())
                    .filter(parameter -> parameter.isAnnotationPresent(McpToolParam.class))
                    .collect(Collectors.toMap(
                            parameter -> parameter.getName(),
                            parameter -> parameter.getAnnotation(McpToolParam.class).required()));
            assertThat(params).doesNotContainKey("idempotencyKey");
        }
        var cancel = Arrays.stream(BankingMcpTools.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("cancelRecurringSubscription"))
                .findFirst()
                .orElseThrow();
        var confirmation = Arrays.stream(cancel.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(McpToolParam.class))
                .filter(parameter -> parameter.getName().equals("confirmationToken"))
                .findFirst()
                .orElseThrow();
        assertThat(confirmation.getAnnotation(McpToolParam.class).required()).isFalse();
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
    void modelSuppliedKeyCannotCollideTwoOperations() {
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
                "contract-derived-idempotency",
                "Cancel Spotify with a model-supplied key",
                "user-123",
                "contract-cancel-1",
                List.of(first, second),
                EvalExpectation.builder()
                        .requiredTools("cancelRecurringSubscription")
                        .expectedFinalStatus(SemanticStatus.OPERATION_REQUIRES_CONFIRMATION)
                        .expectedSemanticStatus(SemanticStatus.OPERATION_REQUIRES_CONFIRMATION)
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
        assertThat(searchFields).containsExactlyInAnyOrder(
                "userId", "category", "merchant", "period", "limit", "cursor");
        assertThat(searchFields).doesNotContainAnyElementsOf(BehavioralEvaluator.UNSUPPORTED_PARAMETERS);

        var cancelFields = Arrays.stream(SubscriptionCancellationRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        assertThat(cancelFields).contains("idempotencyKey", "confirmationToken");
        assertThat(Set.of(PeriodOption.values())).contains(PeriodOption.LAST_3_MONTHS);
        assertThat(CatalogProposalType.values()).contains(CatalogProposalType.NEW_CATEGORY, CatalogProposalType.ADD_MERCHANTS);
    }

    /**
     * Pin of tool name, description, hints, and parameter schema.
     * A change fails until this digest is reviewed and updated on purpose.
     */
    @Test
    void toolContractDigestIsPinned() throws Exception {
        var canonical = canonicalToolContract();
        var digest = sha256(canonical);
        assertThat(digest)
                .as("Tool contract changed. Review the schema, then update this pin.%n%s", canonical)
                .isEqualTo("b2c55a2c2be184a5e700550ed814b28fcc3cf5d6716f760e0a3ce44ba6590859");
    }

    private static String canonicalToolContract() {
        var methods = Arrays.stream(BankingMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .sorted(Comparator.comparing(method -> method.getAnnotation(McpTool.class).name()))
                .toList();
        var document = new StringBuilder();
        for (var method : methods) {
            var tool = method.getAnnotation(McpTool.class);
            var hints = tool.annotations();
            document.append("tool:").append(tool.name()).append('\n');
            document.append("description:").append(tool.description().strip()).append('\n');
            document.append("hints:readOnly=").append(hints.readOnlyHint())
                    .append(",destructive=").append(hints.destructiveHint())
                    .append(",idempotent=").append(hints.idempotentHint())
                    .append('\n');
            for (var parameter : method.getParameters()) {
                if (!parameter.isAnnotationPresent(McpToolParam.class)) {
                    continue;
                }
                var param = parameter.getAnnotation(McpToolParam.class);
                document.append("param:").append(parameter.getName())
                        .append("|required=").append(param.required())
                        .append("|description:").append(param.description().strip())
                        .append('\n');
            }
        }
        return document.toString();
    }

    private static String sha256(String canonical) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        var hex = new StringBuilder(digest.length * 2);
        for (var b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
