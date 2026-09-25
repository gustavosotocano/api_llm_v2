package com.enterprise.agentapi.api;

import com.enterprise.agentapi.domain.AgentWorkflow;
import com.enterprise.agentapi.domain.CustomerProfileResponse;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.JobResponse;
import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.RecurringPaymentSearchResponse;
import com.enterprise.agentapi.domain.SubscriptionCancellationRequest;
import com.enterprise.agentapi.domain.SubscriptionCancellationResponse;
import com.enterprise.agentapi.domain.SubscriptionSnapshot;
import com.enterprise.agentapi.enterprise.CustomerProfileApi;
import com.enterprise.agentapi.enterprise.CustomerReportApi;
import com.enterprise.agentapi.enterprise.SubscriptionCommandApi;
import com.enterprise.agentapi.enterprise.TransactionQueryApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Domain-oriented enterprise API. Not LLM-shaped. MCP tools call the same ports.
 */
@RestController
@RequestMapping("/api/banking")
public class EnterpriseBankingController {
    private final CustomerProfileApi customerProfileApi;
    private final TransactionQueryApi transactionQueryApi;
    private final SubscriptionCommandApi subscriptionCommandApi;
    private final CustomerReportApi customerReportApi;

    public EnterpriseBankingController(CustomerProfileApi customerProfileApi,
                                       TransactionQueryApi transactionQueryApi,
                                       SubscriptionCommandApi subscriptionCommandApi,
                                       CustomerReportApi customerReportApi) {
        this.customerProfileApi = customerProfileApi;
        this.transactionQueryApi = transactionQueryApi;
        this.subscriptionCommandApi = subscriptionCommandApi;
        this.customerReportApi = customerReportApi;
    }

    @GetMapping("/customers/{userId}")
    public CustomerProfileResponse customer(@PathVariable String userId,
                                           @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        return withContext(agentSessionId, userId, AgentWorkflow.READ, () -> customerProfileApi.getProfile(userId));
    }

    @PostMapping("/transactions/recurring-search")
    public RecurringPaymentSearchResponse search(@RequestBody RecurringPaymentSearchRequest request,
                                                @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        var userId = blankToNull(request.userId());
        return withContext(agentSessionId, userId, AgentWorkflow.READ,
                () -> transactionQueryApi.searchRecurringPayments(request));
    }

    @GetMapping("/subscriptions/{userId}")
    public SubscriptionSnapshot subscriptions(@PathVariable String userId,
                                             @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        return withContext(agentSessionId, userId, AgentWorkflow.READ, () -> subscriptionCommandApi.snapshot(userId));
    }

    @PostMapping("/subscriptions/cancel")
    public SubscriptionCancellationResponse cancel(@RequestBody SubscriptionCancellationRequest request,
                                                   @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        var userId = blankToNull(request.userId());
        return withContext(agentSessionId, userId, AgentWorkflow.CANCELLATION,
                () -> subscriptionCommandApi.cancel(request));
    }

    @PostMapping("/jobs/reports")
    public JobResponse startReport(@RequestParam String userId,
                                   @RequestParam(defaultValue = "LAST_3_MONTHS") String period,
                                   @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        return withContext(agentSessionId, userId, AgentWorkflow.REPORT,
                () -> customerReportApi.startReport(userId, period));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public JobResponse cancelJob(@PathVariable String jobId,
                                 @RequestParam String userId,
                                 @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        return withContext(agentSessionId, userId, AgentWorkflow.REPORT, () -> customerReportApi.cancel(jobId));
    }

    @GetMapping("/jobs/{jobId}")
    public JobResponse jobStatus(@PathVariable String jobId,
                                 @RequestParam String userId,
                                 @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        return withContext(agentSessionId, userId, AgentWorkflow.REPORT, () -> customerReportApi.status(jobId));
    }

    @GetMapping("/jobs/{jobId}/result")
    public JobResponse jobResult(@PathVariable String jobId,
                                 @RequestParam String userId,
                                 @RequestHeader(value = "X-Agent-Session-Id", required = false) String agentSessionId) {
        return withContext(agentSessionId, userId, AgentWorkflow.REPORT, () -> customerReportApi.result(jobId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> T withContext(String agentSessionId, String userId, AgentWorkflow workflow, java.util.function.Supplier<T> action) {
        var sessionId = AgentSessionSupport.resolveSessionId(agentSessionId);
        AgentSessionSupport.bind(sessionId, userId, "ENTERPRISE_API", IdentityType.USER_DELEGATED, workflow);
        try {
            return action.get();
        } finally {
            AgentSessionSupport.clear();
        }
    }
}
