package com.enterprise.agentapi.api;

import com.enterprise.agentapi.agent.AgentRateLimitExceededException;
import com.enterprise.agentapi.agent.AgentRateLimiter;
import com.enterprise.agentapi.application.CustomerReportJobService;
import com.enterprise.agentapi.application.SubscriptionCancellationService;
import com.enterprise.agentapi.application.TransactionSearchService;
import com.enterprise.agentapi.domain.JobResponse;
import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.RecurringPaymentSearchResponse;
import com.enterprise.agentapi.domain.SubscriptionCancellationRequest;
import com.enterprise.agentapi.domain.SubscriptionCancellationResponse;
import com.enterprise.agentapi.observability.AgentAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/debug")
public class DebugController {
    private final TransactionSearchService searchService;
    private final SubscriptionCancellationService cancellationService;
    private final CustomerReportJobService reportJobService;
    private final AgentRateLimiter rateLimiter;
    private final AgentAuditService auditService;

    public DebugController(TransactionSearchService searchService,
                           SubscriptionCancellationService cancellationService,
                           CustomerReportJobService reportJobService,
                           AgentRateLimiter rateLimiter,
                           AgentAuditService auditService) {
        this.searchService = searchService;
        this.cancellationService = cancellationService;
        this.reportJobService = reportJobService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @PostMapping("/transactions/search-recurring")
    public RecurringPaymentSearchResponse searchRecurring(@RequestBody RecurringPaymentSearchRequest request,
                                                          @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        var sessionId = AgentSessionSupport.resolveSessionId(agentSessionId);
        var userId = request.userId() == null || request.userId().isBlank() ? "user-123" : request.userId();
        AgentSessionSupport.bind(sessionId, userId, "DEBUG_API");
        try {
            rateLimiter.checkAllowed(sessionId, "searchRecurringPayments", userId, "DEBUG_API");
            var startedAt = System.nanoTime();
            var response = searchService.searchRecurringPayments(request);
            auditService.technical(sessionId, userId, "DEBUG_API", "SEARCH_COMPLETED", Map.of(
                    "durationMs", (System.nanoTime() - startedAt) / 1_000_000,
                    "status", response.status().name()));
            auditService.business(sessionId, userId, "DEBUG_API", "SEARCH_RECURRING_PAYMENTS", Map.of(
                    "status", response.status().name(),
                    "resultCount", response.resultCount()));
            return response;
        } catch (AgentRateLimitExceededException ex) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        } finally {
            AgentSessionSupport.clear();
        }
    }

    @PostMapping("/subscriptions/cancel")
    public SubscriptionCancellationResponse cancelSubscription(
            @RequestBody SubscriptionCancellationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationTokenHeader) {
        var sessionId = AgentSessionSupport.resolveSessionId(agentSessionId);
        var userId = request.userId() == null || request.userId().isBlank() ? "user-123" : request.userId();
        AgentSessionSupport.bind(sessionId, userId, "DEBUG_API");

        var idempotencyKey = idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()
                ? idempotencyKeyHeader
                : request.idempotencyKey();
        var confirmationToken = confirmationTokenHeader != null && !confirmationTokenHeader.isBlank()
                ? confirmationTokenHeader
                : request.confirmationToken();

        try {
            rateLimiter.checkAllowed(sessionId, "cancelRecurringSubscription", userId, "DEBUG_API");
            var startedAt = System.nanoTime();
            auditService.ai(sessionId, userId, "DEBUG_API", "CANCEL_REQUEST", Map.of(
                    "merchant", request.merchant() == null ? "none" : request.merchant(),
                    "idempotencyKey", idempotencyKey == null ? "none" : idempotencyKey,
                    "hasConfirmationToken", confirmationToken != null && !confirmationToken.isBlank()));

            var response = cancellationService.cancel(new SubscriptionCancellationRequest(
                    userId, request.merchant(), idempotencyKey, confirmationToken));

            auditService.technical(sessionId, userId, "DEBUG_API", "CANCEL_COMPLETED", Map.of(
                    "durationMs", (System.nanoTime() - startedAt) / 1_000_000,
                    "status", response.status().name()));
            auditService.business(sessionId, userId, "DEBUG_API", "CANCEL_RECURRING_SUBSCRIPTION", Map.of(
                    "status", response.status().name(),
                    "merchant", response.merchant() == null ? "none" : response.merchant(),
                    "operationId", response.operationId() == null ? "none" : response.operationId()));
            return response;
        } catch (AgentRateLimitExceededException ex) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        } finally {
            AgentSessionSupport.clear();
        }
    }

    @PostMapping("/jobs/reports")
    public JobResponse startReport(@RequestParam String userId,
                                   @RequestParam(defaultValue = "LAST_3_MONTHS") String period,
                                   @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        var sessionId = AgentSessionSupport.resolveSessionId(agentSessionId);
        AgentSessionSupport.bind(sessionId, userId, "DEBUG_API");
        try {
            return reportJobService.startReport(userId, period);
        } finally {
            AgentSessionSupport.clear();
        }
    }

    @GetMapping("/jobs/{jobId}")
    public JobResponse jobStatus(@PathVariable String jobId,
                                 @RequestParam(defaultValue = "user-123") String userId,
                                 @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        var sessionId = AgentSessionSupport.resolveSessionId(agentSessionId);
        AgentSessionSupport.bind(sessionId, userId, "DEBUG_API");
        try {
            return reportJobService.status(jobId);
        } finally {
            AgentSessionSupport.clear();
        }
    }

    @GetMapping("/jobs/{jobId}/result")
    public JobResponse jobResult(@PathVariable String jobId,
                                 @RequestParam(defaultValue = "user-123") String userId,
                                 @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        var sessionId = AgentSessionSupport.resolveSessionId(agentSessionId);
        AgentSessionSupport.bind(sessionId, userId, "DEBUG_API");
        try {
            return reportJobService.result(jobId);
        } finally {
            AgentSessionSupport.clear();
        }
    }
}
