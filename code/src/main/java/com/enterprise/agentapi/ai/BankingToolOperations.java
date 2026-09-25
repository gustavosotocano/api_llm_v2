package com.enterprise.agentapi.ai;

import com.enterprise.agentapi.agent.AgentRateLimitExceededException;
import com.enterprise.agentapi.agent.AgentRateLimitSupport;
import com.enterprise.agentapi.agent.BudgetExceededException;
import com.enterprise.agentapi.agent.RateLimitScope;
import com.enterprise.agentapi.agent.RetryBudgetExceededException;
import com.enterprise.agentapi.agent.ToolAccessDeniedException;
import com.enterprise.agentapi.enterprise.CatalogChangeApi;
import com.enterprise.agentapi.enterprise.CustomerReportApi;
import com.enterprise.agentapi.enterprise.SubscriptionCommandApi;
import com.enterprise.agentapi.enterprise.TransactionQueryApi;
import com.enterprise.agentapi.domain.CatalogChangeResponse;
import com.enterprise.agentapi.domain.CatalogProposalType;
import com.enterprise.agentapi.domain.JobResponse;
import com.enterprise.agentapi.domain.OperationRetry;
import com.enterprise.agentapi.domain.PeriodExpressions;
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

    private final TransactionQueryApi searchService;
    private final SubscriptionCommandApi cancellationService;
    private final CatalogChangeApi catalogGovernanceService;
    private final CustomerReportApi reportJobService;
    private final AgentToolSupport agentToolSupport;

    public BankingToolOperations(TransactionQueryApi searchService,
                                 SubscriptionCommandApi cancellationService,
                                 CatalogChangeApi catalogGovernanceService,
                                 CustomerReportApi reportJobService,
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
                if (PeriodExpressions.looksLikeDateRange(period)) {
                    return new RecurringPaymentSearchResponse(
                            SemanticStatus.INVALID_DATE_RANGE,
                            "Raw dates are not accepted. Send a semantic period. The backend calculates the range.",
                            category, List.of(), PeriodExpressions.semanticPeriods(),
                            null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0,
                            OperationRetry.forStatus(SemanticStatus.INVALID_DATE_RANGE, null));
                }
                PeriodOption periodOption;
                try {
                    periodOption = PeriodOption.valueOf(period);
                } catch (RuntimeException ex) {
                    return new RecurringPaymentSearchResponse(
                            SemanticStatus.INVALID_PERIOD,
                            "Unsupported period. Send a semantic period enum, not raw dates.",
                            category, List.of(), PeriodExpressions.semanticPeriods(),
                            null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0,
                            OperationRetry.forStatus(SemanticStatus.INVALID_PERIOD, null));
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
        } catch (ToolAccessDeniedException ex) {
            return accessDeniedSearch(ex);
        } catch (AgentRateLimitExceededException ex) {
            return rateLimitedSearch(ex);
        } catch (BudgetExceededException ex) {
            return budgetExceededSearch(ex);
        } catch (RetryBudgetExceededException ex) {
            return retryBudgetSearch(ex);
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
                        "idempotencyKey", response.idempotencyKey() == null ? "none" : response.idempotencyKey(),
                        "operationId", response.operationId() == null ? "none" : response.operationId()));
                return response;
            });
        } catch (ToolAccessDeniedException ex) {
            return accessDeniedCancel(userId, merchant, idempotencyKey, ex);
        } catch (AgentRateLimitExceededException ex) {
            return rateLimitedCancel(userId, merchant, idempotencyKey, ex);
        } catch (BudgetExceededException ex) {
            return budgetExceededCancel(userId, merchant, idempotencyKey, ex);
        } catch (RetryBudgetExceededException ex) {
            return retryBudgetCancel(userId, merchant, idempotencyKey, ex);
        }
    }

    public CatalogChangeResponse proposeCatalogChange(
            String proposalType, String categoryCode, String merchantsCsv, String reason,
            String userId, String agentSessionId, String idempotencyKey) {
        try {
            return agentToolSupport.execute("proposeCatalogChange", toolParams(
                    "proposalType", proposalType,
                    "categoryCode", categoryCode,
                    "merchants", merchantsCsv,
                    "reason", reason,
                    "userId", userId,
                    "agentSessionId", agentSessionId,
                    "idempotencyKey", idempotencyKey), () -> {
                if (proposalType == null || proposalType.isBlank()) {
                    return clarificationCatalog("proposalType is required.");
                }
                CatalogProposalType type;
                try {
                    type = CatalogProposalType.valueOf(proposalType.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    return clarificationCatalog("proposalType must be NEW_CATEGORY or ADD_MERCHANTS.");
                }
                if (merchantsCsv == null || merchantsCsv.isBlank()) {
                    return clarificationCatalog("merchants is required (comma-separated, e.g. HBO_MAX,APPLE_TV).");
                }
                var merchants = parseMerchants(merchantsCsv);
                if (merchants.isEmpty()) {
                    return clarificationCatalog("merchants is required (comma-separated, e.g. HBO_MAX,APPLE_TV).");
                }
                var response = catalogGovernanceService.propose(
                        type, categoryCode, merchants, reason, agentSessionId, userId, idempotencyKey);
                agentToolSupport.logBusinessAction("PROPOSE_CATALOG_CHANGE", response.status(), Map.of(
                        "proposalId", response.proposalId() == null ? "none" : response.proposalId(),
                        "categoryCode", response.categoryCode() == null ? "none" : response.categoryCode()));
                return response;
            });
        } catch (ToolAccessDeniedException ex) {
            return accessDeniedCatalogChange(ex);
        } catch (AgentRateLimitExceededException ex) {
            return rateLimitedCatalogChange(ex);
        } catch (BudgetExceededException ex) {
            return budgetExceededCatalogChange(ex);
        } catch (RetryBudgetExceededException ex) {
            return retryBudgetCatalogChange(ex);
        } catch (IllegalArgumentException ex) {
            return clarificationCatalog(ex.getMessage());
        }
    }

    public JobResponse startCustomerReport(String userId, String period, String idempotencyKey) {
        try {
            return agentToolSupport.execute("startCustomerReport", toolParams(
                    "userId", userId, "period", period, "idempotencyKey", idempotencyKey), () -> {
                var response = reportJobService.startReport(userId, period, idempotencyKey);
                agentToolSupport.logBusinessAction("START_CUSTOMER_REPORT", response.status(), Map.of(
                        "jobId", response.jobId() == null ? "none" : response.jobId()));
                return response;
            });
        } catch (ToolAccessDeniedException | AgentRateLimitExceededException | BudgetExceededException | RetryBudgetExceededException ex) {
            return operationalFailure(ex);
        }
    }

    public JobResponse getJobStatus(String jobId) {
        try {
            return agentToolSupport.execute("getJobStatus", toolParams("jobId", jobId), () -> {
                var response = reportJobService.status(jobId);
                agentToolSupport.logBusinessAction("GET_JOB_STATUS", response.status(), Map.of(
                        "jobId", jobId == null ? "none" : jobId,
                        "jobStatus", response.jobStatus() == null ? "unknown" : response.jobStatus().name(),
                        "originatingToolCallId", response.toolCallId() == null ? "none" : response.toolCallId()));
                return response;
            });
        } catch (ToolAccessDeniedException | AgentRateLimitExceededException | BudgetExceededException | RetryBudgetExceededException ex) {
            return operationalFailure(ex);
        }
    }

    public JobResponse cancelCustomerReport(String jobId) {
        try {
            return agentToolSupport.execute("cancelCustomerReport", toolParams("jobId", jobId), () -> {
                var response = reportJobService.cancel(jobId);
                agentToolSupport.logBusinessAction("CANCEL_CUSTOMER_REPORT", response.status(), Map.of(
                        "jobId", jobId == null ? "none" : jobId,
                        "jobStatus", response.jobStatus() == null ? "unknown" : response.jobStatus().name(),
                        "originatingToolCallId", response.toolCallId() == null ? "none" : response.toolCallId()));
                return response;
            });
        } catch (ToolAccessDeniedException | AgentRateLimitExceededException | BudgetExceededException | RetryBudgetExceededException ex) {
            return operationalFailure(ex);
        }
    }

    public JobResponse getJobResult(String jobId) {
        try {
            return agentToolSupport.execute("getJobResult", toolParams("jobId", jobId), () -> {
                var response = reportJobService.result(jobId);
                agentToolSupport.logBusinessAction("GET_JOB_RESULT", response.status(), Map.of(
                        "jobId", jobId == null ? "none" : jobId,
                        "originatingToolCallId", response.toolCallId() == null ? "none" : response.toolCallId()));
                return response;
            });
        } catch (ToolAccessDeniedException | AgentRateLimitExceededException | BudgetExceededException | RetryBudgetExceededException ex) {
            return operationalFailure(ex);
        }
    }

    private RecurringPaymentSearchResponse accessDeniedSearch(ToolAccessDeniedException ex) {
        return new RecurringPaymentSearchResponse(
                SemanticStatus.INSUFFICIENT_PERMISSIONS, ex.getMessage(), null, List.of(),
                List.of("Use a workflow that includes this tool, or elevate to FULL"),
                null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0,
                OperationRetry.forStatus(SemanticStatus.INSUFFICIENT_PERMISSIONS, null));
    }

    private RecurringPaymentSearchResponse rateLimitedSearch(AgentRateLimitExceededException ex) {
        return new RecurringPaymentSearchResponse(
                AgentRateLimitSupport.semanticStatus(ex),
                ex.getMessage(), null, List.of(),
                List.of("Retry after " + ex.retryAfterSeconds() + " seconds",
                        ex.scope() == RateLimitScope.LOOP
                                ? "Avoid chaining the same tool in a loop"
                                : "Reduce request frequency"),
                null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0,
                OperationRetry.forStatus(AgentRateLimitSupport.semanticStatus(ex), ex.retryAfterSeconds()));
    }

    private RecurringPaymentSearchResponse retryBudgetSearch(RetryBudgetExceededException ex) {
        return new RecurringPaymentSearchResponse(
                SemanticStatus.RETRY_BUDGET_EXCEEDED, ex.getMessage(), null, List.of(),
                List.of("Do not retry this operation. The retry budget is exhausted."),
                null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0,
                OperationRetry.forStatus(SemanticStatus.RETRY_BUDGET_EXCEEDED, null));
    }

    private RecurringPaymentSearchResponse budgetExceededSearch(BudgetExceededException ex) {
        return new RecurringPaymentSearchResponse(
                SemanticStatus.BUDGET_EXCEEDED, ex.getMessage(), null, List.of(),
                List.of("Wait for a new session or reduce high-cost tool use"),
                null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0,
                OperationRetry.forStatus(SemanticStatus.BUDGET_EXCEEDED, null));
    }

    private static CatalogChangeResponse clarificationCatalog(String message) {
        return new CatalogChangeResponse(
                SemanticStatus.CLARIFICATION_REQUIRED, message,
                null, null, null, List.of(), null, List.of(),
                List.of("proposalType: NEW_CATEGORY or ADD_MERCHANTS",
                        "merchants: comma-separated codes such as HBO_MAX,APPLE_TV"),
                OperationRetry.forStatus(SemanticStatus.CLARIFICATION_REQUIRED, null));
    }

    private CatalogChangeResponse accessDeniedCatalogChange(ToolAccessDeniedException ex) {
        return new CatalogChangeResponse(
                SemanticStatus.INSUFFICIENT_PERMISSIONS, ex.getMessage(),
                null, null, null, List.of(), null, List.of(),
                List.of("Use GOVERNANCE or FULL workflow to propose catalog changes"),
                OperationRetry.forStatus(SemanticStatus.INSUFFICIENT_PERMISSIONS, null));
    }

    private CatalogChangeResponse rateLimitedCatalogChange(AgentRateLimitExceededException ex) {
        return new CatalogChangeResponse(
                AgentRateLimitSupport.semanticStatus(ex), ex.getMessage(),
                null, null, null, List.of(), null, List.of(),
                List.of("Retry after " + ex.retryAfterSeconds() + " seconds"),
                OperationRetry.forStatus(AgentRateLimitSupport.semanticStatus(ex), ex.retryAfterSeconds()));
    }

    private CatalogChangeResponse retryBudgetCatalogChange(RetryBudgetExceededException ex) {
        return new CatalogChangeResponse(
                SemanticStatus.RETRY_BUDGET_EXCEEDED, ex.getMessage(),
                null, null, null, List.of(), null, List.of(),
                List.of("Do not retry this proposal. The retry budget is exhausted."),
                OperationRetry.forStatus(SemanticStatus.RETRY_BUDGET_EXCEEDED, null));
    }

    private CatalogChangeResponse budgetExceededCatalogChange(BudgetExceededException ex) {
        return new CatalogChangeResponse(
                SemanticStatus.BUDGET_EXCEEDED, ex.getMessage(),
                null, null, null, List.of(), null, List.of(),
                List.of("Execution budget exceeded for this session"),
                OperationRetry.forStatus(SemanticStatus.BUDGET_EXCEEDED, null));
    }

    private SubscriptionCancellationResponse accessDeniedCancel(
            String userId, String merchant, String idempotencyKey, ToolAccessDeniedException ex) {
        return new SubscriptionCancellationResponse(
                SemanticStatus.INSUFFICIENT_PERMISSIONS, ex.getMessage(),
                userId, merchant, idempotencyKey, null, null, null,
                List.of("Use CANCELLATION or FULL workflow for write tools"),
                OperationRetry.forStatus(SemanticStatus.INSUFFICIENT_PERMISSIONS, null));
    }

    private SubscriptionCancellationResponse rateLimitedCancel(
            String userId, String merchant, String idempotencyKey, AgentRateLimitExceededException ex) {
        return new SubscriptionCancellationResponse(
                AgentRateLimitSupport.semanticStatus(ex), ex.getMessage(),
                userId, merchant, idempotencyKey, null, null, null,
                List.of("Wait and retry with the same idempotencyKey",
                        "Retry after " + ex.retryAfterSeconds() + " seconds"),
                OperationRetry.forStatus(AgentRateLimitSupport.semanticStatus(ex), ex.retryAfterSeconds()));
    }

    private SubscriptionCancellationResponse retryBudgetCancel(
            String userId, String merchant, String idempotencyKey, RetryBudgetExceededException ex) {
        return new SubscriptionCancellationResponse(
                SemanticStatus.RETRY_BUDGET_EXCEEDED, ex.getMessage(),
                userId, merchant, idempotencyKey, null, null, null,
                List.of("Do not retry this cancellation. The retry budget is exhausted."),
                OperationRetry.forStatus(SemanticStatus.RETRY_BUDGET_EXCEEDED, null));
    }

    private SubscriptionCancellationResponse budgetExceededCancel(
            String userId, String merchant, String idempotencyKey, BudgetExceededException ex) {
        return new SubscriptionCancellationResponse(
                SemanticStatus.BUDGET_EXCEEDED, ex.getMessage(),
                userId, merchant, idempotencyKey, null, null, null,
                List.of("Wait and retry with the same idempotencyKey after budget resets"),
                OperationRetry.forStatus(SemanticStatus.BUDGET_EXCEEDED, null));
    }

    private JobResponse operationalFailure(RuntimeException ex) {
        var status = switch (ex) {
            case ToolAccessDeniedException ignored -> SemanticStatus.INSUFFICIENT_PERMISSIONS;
            case BudgetExceededException ignored -> SemanticStatus.BUDGET_EXCEEDED;
            case RetryBudgetExceededException ignored -> SemanticStatus.RETRY_BUDGET_EXCEEDED;
            case AgentRateLimitExceededException rateLimited -> AgentRateLimitSupport.semanticStatus(rateLimited);
            default -> throw ex;
        };
        Integer retryAfter = ex instanceof AgentRateLimitExceededException rateLimited
                ? rateLimited.retryAfterSeconds()
                : null;
        return new JobResponse(status, ex.getMessage(), null, null, retryAfter, null,
                List.of("Respect workflow, rate limits, and execution budgets before retrying"),
                com.enterprise.agentapi.agent.ToolCallIds.current(),
                OperationRetry.forStatus(status, retryAfter));
    }

    private static List<String> parseMerchants(String merchantsCsv) {
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
