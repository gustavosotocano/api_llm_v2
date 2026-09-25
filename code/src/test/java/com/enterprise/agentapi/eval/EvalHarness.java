package com.enterprise.agentapi.eval;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentProperties;
import com.enterprise.agentapi.agent.AgentRateLimiter;
import com.enterprise.agentapi.agent.ExecutionBudgetService;
import com.enterprise.agentapi.agent.ToolAccessPolicy;
import com.enterprise.agentapi.ai.AgentToolSupport;
import com.enterprise.agentapi.ai.BankingToolOperations;
import com.enterprise.agentapi.ai.ToolInvocation;
import com.enterprise.agentapi.api.AgentSessionSupport;
import com.enterprise.agentapi.application.CatalogGovernanceService;
import com.enterprise.agentapi.application.CustomerProfileService;
import com.enterprise.agentapi.application.CustomerReportJobService;
import com.enterprise.agentapi.application.SubscriptionCancellationService;
import com.enterprise.agentapi.application.TransactionSearchService;
import com.enterprise.agentapi.domain.AgentWorkflow;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.JobResponse;
import com.enterprise.agentapi.domain.SubscriptionCancellationResponse;
import com.enterprise.agentapi.infrastructure.AsyncJobStore;
import com.enterprise.agentapi.infrastructure.CustomerProfileRepository;
import com.enterprise.agentapi.infrastructure.CatalogProposalStore;
import com.enterprise.agentapi.infrastructure.CategoryDictionaryRepository;
import com.enterprise.agentapi.infrastructure.ConfirmationTokenStore;
import com.enterprise.agentapi.infrastructure.IdempotencyStore;
import com.enterprise.agentapi.infrastructure.SubscriptionRegistry;
import com.enterprise.agentapi.infrastructure.TransactionRepository;
import com.enterprise.agentapi.observability.AgentAuditService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EvalHarness {
    private final BankingToolOperations operations;
    private final RecordingToolListener recorder;

    private EvalHarness(BankingToolOperations operations, RecordingToolListener recorder) {
        this.operations = operations;
        this.recorder = recorder;
    }

    public static EvalHarness create() {
        var clock = Clock.fixed(LocalDate.of(2026, 5, 20).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        var properties = new AgentProperties();
        var audit = new AgentAuditService(JsonMapper.builder().build());
        var recorder = new RecordingToolListener();
        var toolSupport = new AgentToolSupport(
                new AgentRateLimiter(properties, audit),
                new ExecutionBudgetService(properties, audit),
                audit,
                new ToolAccessPolicy(),
                List.of(recorder));
        var dictionary = new CategoryDictionaryRepository();
        var transactions = new TransactionRepository();
        var search = new TransactionSearchService(transactions, dictionary, clock);
        var subscriptions = new SubscriptionRegistry();
        var idempotency = new IdempotencyStore();
        var cancellation = new SubscriptionCancellationService(
                idempotency,
                new ConfirmationTokenStore(properties),
                subscriptions,
                transactions);
        var operations = new BankingToolOperations(
                search,
                cancellation,
                new CatalogGovernanceService(dictionary, new CatalogProposalStore(), idempotency, properties),
                new CustomerReportJobService(
                        new AsyncJobStore(),
                        idempotency,
                        new CustomerProfileService(new CustomerProfileRepository()),
                        search,
                        cancellation),
                toolSupport);
        return new EvalHarness(operations, recorder);
    }

    public List<ToolInvocation> run(GoldenScenario scenario) {
        return run(scenario, AgentWorkflow.FULL);
    }

    public List<ToolInvocation> run(GoldenScenario scenario, AgentWorkflow workflow) {
        return run(scenario, new AgentContext(
                scenario.agentSessionId(), scenario.userId(), "EVAL", IdentityType.USER_DELEGATED, workflow));
    }

    public List<ToolInvocation> run(GoldenScenario scenario, AgentContext context) {
        recorder.clear();
        AgentSessionSupport.bind(context);
        try {
            for (var turn : scenario.turns()) {
                execute(turn);
            }
            return recorder.invocations();
        } finally {
            AgentSessionSupport.clear();
        }
    }

    private void execute(ScriptedTurn turn) {
        var args = resolve(turn.arguments());
        switch (turn.toolName()) {
            case "searchRecurringPayments" -> operations.searchRecurringPayments(
                    str(args, "userId"),
                    str(args, "category"),
                    str(args, "merchant"),
                    str(args, "period"),
                    integer(args, "limit"));
            case "cancelRecurringSubscription" -> operations.cancelRecurringSubscription(
                    str(args, "userId"),
                    str(args, "merchant"),
                    str(args, "idempotencyKey"),
                    str(args, "confirmationToken"));
            case "proposeCatalogChange" -> operations.proposeCatalogChange(
                    str(args, "proposalType"),
                    str(args, "categoryCode"),
                    str(args, "merchants"),
                    str(args, "reason"),
                    str(args, "userId"),
                    str(args, "agentSessionId"),
                    str(args, "idempotencyKey"));
            case "startCustomerReport" -> operations.startCustomerReport(
                    str(args, "userId"),
                    str(args, "period"),
                    str(args, "idempotencyKey"));
            case "cancelCustomerReport" -> operations.cancelCustomerReport(str(args, "jobId"));
            case "getJobStatus" -> operations.getJobStatus(str(args, "jobId"));
            case "getJobResult" -> operations.getJobResult(str(args, "jobId"));
            case "wait" -> sleep(integer(args, "millis") == null ? 1200 : integer(args, "millis"));
            default -> throw new IllegalArgumentException("Unknown eval tool: " + turn.toolName());
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during eval wait", ex);
        }
    }

    private Map<String, Object> resolve(Map<String, Object> arguments) {
        var resolved = new LinkedHashMap<String, Object>();
        arguments.forEach((key, value) -> resolved.put(key, resolveValue(value)));
        return resolved;
    }

    private Object resolveValue(Object value) {
        if (!(value instanceof String placeholder) || !placeholder.startsWith("${")) {
            return value;
        }
        for (var invocation : recorder.invocations().reversed()) {
            if ("${confirmationToken}".equals(placeholder)
                    && invocation.result() instanceof SubscriptionCancellationResponse response
                    && response.confirmationToken() != null) {
                return response.confirmationToken();
            }
            if ("${jobId}".equals(placeholder)
                    && invocation.result() instanceof JobResponse response
                    && response.jobId() != null) {
                return response.jobId();
            }
        }
        throw new IllegalStateException("Could not resolve placeholder " + placeholder);
    }

    private static String str(Map<String, Object> args, String key) {
        var value = args.get(key);
        return value == null ? null : value.toString();
    }

    private static Integer integer(Map<String, Object> args, String key) {
        var value = args.get(key);
        return switch (value) {
            case null -> null;
            case Integer integer -> integer;
            default -> Integer.parseInt(value.toString());
        };
    }
}
