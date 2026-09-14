package com.enterprise.agentapi.ai;

import com.enterprise.agentapi.agent.AgentRateLimitExceededException;
import com.enterprise.agentapi.agent.AgentRateLimitSupport;
import com.enterprise.agentapi.agent.BudgetExceededException;
import com.enterprise.agentapi.agent.RateLimitScope;
import com.enterprise.agentapi.application.CatalogGovernanceService;
import com.enterprise.agentapi.application.CustomerReportJobService;
import com.enterprise.agentapi.application.SubscriptionCancellationService;
import com.enterprise.agentapi.application.TransactionSearchService;
import com.enterprise.agentapi.domain.CatalogChangeResponse;
import com.enterprise.agentapi.domain.CatalogProposalType;
import com.enterprise.agentapi.domain.JobResponse;
import com.enterprise.agentapi.domain.PeriodOption;
import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.RecurringPaymentSearchResponse;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.domain.SubscriptionCancellationRequest;
import com.enterprise.agentapi.domain.SubscriptionCancellationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BankingToolOperations {
    private static final Logger log = LoggerFactory.getLogger(BankingToolOperations.class);

    private final TransactionSearchService searchService;
    private final SubscriptionCancellationService cancellationService;
    private final CatalogGovernanceService catalogGovernanceService;
    private final CustomerReportJobService reportJobService;
    private final AgentToolSupport agentToolSupport;

    public BankingToolOperations(TransactionSearchService searchService,
                                 SubscriptionCancellationService cancellationService,
                                 CatalogGovernanceService catalogGovernanceService,
                                 CustomerReportJobService reportJobService,
                                 AgentToolSupport agentToolSupport) {
        this.searchService = searchService;
        this.cancellationService = cancellationService;
        this.catalogGovernanceService = catalogGovernanceService;
        this.reportJobService = reportJobService;
        this.agentToolSupport = agentToolSupport;
    }

    public RecurringPaymentSearchResponse searchRecurringPayments(
            String userId, String category, String merchant, String period, Integer limit) {
        try {
            return agentToolSupport.execute("searchRecurringPayments", toolParams(
                    "userId", userId,
                    "category", category,
                    "merchant", merchant,
                    "period", period,
                    "limit", limit), () -> {
                PeriodOption periodOption;
                try {
                    periodOption = PeriodOption.valueOf(period);
                } catch (RuntimeException ex) {
                    return new RecurringPaymentSearchResponse(
                            SemanticStatus.INVALID_PERIOD,
                            "Unsupported period. Send a semantic period enum, not raw dates.",
                            category, List.of(),
                            List.of("LAST_30_DAYS", "LAST_3_MONTHS", "LAST_6_MONTHS", "CURRENT_MONTH", "PREVIOUS_MONTH"),
                            null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0);
                }
                var request = new RecurringPaymentSearchRequest(
                        userId, category, merchant, periodOption, limit == null ? 100 : limit);
                log.info("Tool searchRecurringPayments: userId={}, category={}, merchant={}, period={}, limit={}",
                        request.userId(), request.category(), request.merchant(), request.period(), request.limit());
                var response = searchService.searchRecurringPayments(request);
                agentToolSupport.logBusinessAction("SEARCH_RECURRING_PAYMENTS", response.status(), Map.of(
                        "resultCount", response.resultCount()));
                return response;
            });
        } catch (AgentRateLimitExceededException ex) {
            return rateLimitedSearch(ex);
        } catch (BudgetExceededException ex) {
            return budgetExceededSearch(ex);
        }
    }

    public SubscriptionCancellationResponse cancelRecurringSubscription(
            String userId, String merchant, String idempotencyKey, String confirmationToken) {
        try {
            return agentToolSupport.execute("cancelRecurringSubscription", toolParams(
                    "userId", userId,
                    "merchant", merchant,
                    "idempotencyKey", idempotencyKey,
                    "confirmationToken", confirmationToken), () -> {
                var response = cancellationService.cancel(new SubscriptionCancellationRequest(
                        userId, merchant, idempotencyKey, confirmationToken));
                agentToolSupport.logBusinessAction("CANCEL_RECURRING_SUBSCRIPTION", response.status(), Map.of(
                        "merchant", response.merchant() == null ? "unknown" : response.merchant(),
                        "idempotencyKey", response.idempotencyKey() == null ? "none" : response.idempotencyKey()));
                return response;
            });
        } catch (AgentRateLimitExceededException ex) {
            return rateLimitedCancel(userId, merchant, idempotencyKey, ex);
        } catch (BudgetExceededException ex) {
            return budgetExceededCancel(userId, merchant, idempotencyKey, ex);
        }
    }

    public CatalogChangeResponse proposeCatalogChange(
            String proposalType, String categoryCode, String merchantsCsv, String reason,
            String userId, String agentSessionId) {
        try {
            return agentToolSupport.execute("proposeCatalogChange", toolParams(
                    "proposalType", proposalType,
                    "categoryCode", categoryCode,
                    "merchants", merchantsCsv,
                    "reason", reason,
                    "userId", userId,
                    "agentSessionId", agentSessionId), () -> {
                var type = CatalogProposalType.valueOf(proposalType.trim().toUpperCase());
                var merchants = parseMerchants(merchantsCsv);
                var response = catalogGovernanceService.propose(
                        type, categoryCode, merchants, reason, agentSessionId, userId);
                agentToolSupport.logBusinessAction("PROPOSE_CATALOG_CHANGE", response.status(), Map.of(
                        "proposalId", response.proposalId() == null ? "none" : response.proposalId(),
                        "categoryCode", response.categoryCode() == null ? "none" : response.categoryCode()));
                return response;
            });
        } catch (AgentRateLimitExceededException ex) {
            return rateLimitedCatalogChange(ex);
        } catch (BudgetExceededException ex) {
            return budgetExceededCatalogChange(ex);
        }
    }

    public JobResponse startCustomerReport(String userId, String period) {
        try {
            return agentToolSupport.execute("startCustomerReport", toolParams(
                    "userId", userId, "period", period), () -> {
                var response = reportJobService.startReport(userId, period);
                agentToolSupport.logBusinessAction("START_CUSTOMER_REPORT", response.status(), Map.of(
                        "jobId", response.jobId() == null ? "none" : response.jobId()));
                return response;
            });
        } catch (AgentRateLimitExceededException | BudgetExceededException ex) {
            return operationalFailure(ex);
        }
    }

    public JobResponse getJobStatus(String jobId) {
        try {
            return agentToolSupport.execute("getJobStatus", toolParams("jobId", jobId), () -> {
                var response = reportJobService.status(jobId);
                agentToolSupport.logBusinessAction("GET_JOB_STATUS", response.status(), Map.of(
                        "jobId", jobId,
                        "jobStatus", response.jobStatus() == null ? "unknown" : response.jobStatus().name()));
                return response;
            });
        } catch (AgentRateLimitExceededException | BudgetExceededException ex) {
            return operationalFailure(ex);
        }
    }

    public JobResponse getJobResult(String jobId) {
        try {
            return agentToolSupport.execute("getJobResult", toolParams("jobId", jobId), () -> {
                var response = reportJobService.result(jobId);
                agentToolSupport.logBusinessAction("GET_JOB_RESULT", response.status(), Map.of("jobId", jobId));
                return response;
            });
        } catch (AgentRateLimitExceededException | BudgetExceededException ex) {
            return operationalFailure(ex);
        }
    }

    private RecurringPaymentSearchResponse rateLimitedSearch(AgentRateLimitExceededException ex) {
        return new RecurringPaymentSearchResponse(
                AgentRateLimitSupport.semanticStatus(ex),
                ex.getMessage(), null, List.of(),
                List.of("Retry after " + ex.retryAfterSeconds() + " seconds",
                        ex.scope() == RateLimitScope.LOOP
                                ? "Avoid chaining the same tool in a loop"
                                : "Reduce request frequency"),
                null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0);
    }

    private RecurringPaymentSearchResponse budgetExceededSearch(BudgetExceededException ex) {
        return new RecurringPaymentSearchResponse(
                SemanticStatus.BUDGET_EXCEEDED, ex.getMessage(), null, List.of(),
                List.of("Wait for a new session or reduce high-cost tool use"),
                null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0);
    }

    private CatalogChangeResponse rateLimitedCatalogChange(AgentRateLimitExceededException ex) {
        return new CatalogChangeResponse(
                AgentRateLimitSupport.semanticStatus(ex), ex.getMessage(),
                null, null, null, List.of(), null, List.of(),
                List.of("Retry after " + ex.retryAfterSeconds() + " seconds"));
    }

    private CatalogChangeResponse budgetExceededCatalogChange(BudgetExceededException ex) {
        return new CatalogChangeResponse(
                SemanticStatus.BUDGET_EXCEEDED, ex.getMessage(),
                null, null, null, List.of(), null, List.of(),
                List.of("Execution budget exceeded for this session"));
    }

    private SubscriptionCancellationResponse rateLimitedCancel(
            String userId, String merchant, String idempotencyKey, AgentRateLimitExceededException ex) {
        return new SubscriptionCancellationResponse(
                AgentRateLimitSupport.semanticStatus(ex), ex.getMessage(),
                userId, merchant, idempotencyKey, null, null, null,
                List.of("Wait and retry with the same idempotencyKey",
                        "Retry after " + ex.retryAfterSeconds() + " seconds"));
    }

    private SubscriptionCancellationResponse budgetExceededCancel(
            String userId, String merchant, String idempotencyKey, BudgetExceededException ex) {
        return new SubscriptionCancellationResponse(
                SemanticStatus.BUDGET_EXCEEDED, ex.getMessage(),
                userId, merchant, idempotencyKey, null, null, null,
                List.of("Wait and retry with the same idempotencyKey after budget resets"));
    }

    private JobResponse operationalFailure(RuntimeException ex) {
        var status = ex instanceof BudgetExceededException
                ? SemanticStatus.BUDGET_EXCEEDED
                : AgentRateLimitSupport.semanticStatus((AgentRateLimitExceededException) ex);
        return new JobResponse(status, ex.getMessage(), null, null, null, null,
                List.of("Respect rate limits and execution budgets before retrying"));
    }

    private static List<String> parseMerchants(String merchantsCsv) {
        if (merchantsCsv == null || merchantsCsv.isBlank()) {
            throw new IllegalArgumentException("merchants is required (comma-separated, e.g. HBO_MAX,APPLE_TV)");
        }
        return Arrays.stream(merchantsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .toList();
    }

    private static Map<String, Object> toolParams(Object... keyValues) {
        var params = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            var value = keyValues[i + 1];
            if (value != null) {
                params.put((String) keyValues[i], value);
            }
        }
        return params;
    }
}
