package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.domain.AsyncJob;
import com.enterprise.agentapi.domain.JobResponse;
import com.enterprise.agentapi.domain.JobStatus;
import com.enterprise.agentapi.domain.PeriodExpressions;
import com.enterprise.agentapi.domain.PeriodOption;
import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.enterprise.AgentBoundary;
import com.enterprise.agentapi.enterprise.CustomerProfileApi;
import com.enterprise.agentapi.enterprise.CustomerReportApi;
import com.enterprise.agentapi.enterprise.SubscriptionCommandApi;
import com.enterprise.agentapi.enterprise.TransactionQueryApi;
import com.enterprise.agentapi.infrastructure.AsyncJobStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CustomerReportJobService implements CustomerReportApi {
    private static final int POLL_AFTER_SECONDS = 2;

    private final AsyncJobStore jobStore;
    private final CustomerProfileApi customerProfileApi;
    private final TransactionQueryApi transactionQueryApi;
    private final SubscriptionCommandApi subscriptionCommandApi;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CustomerReportJobService(AsyncJobStore jobStore,
                                    CustomerProfileApi customerProfileApi,
                                    TransactionQueryApi transactionQueryApi,
                                    SubscriptionCommandApi subscriptionCommandApi) {
        this.jobStore = jobStore;
        this.customerProfileApi = customerProfileApi;
        this.transactionQueryApi = transactionQueryApi;
        this.subscriptionCommandApi = subscriptionCommandApi;
    }

    @Override
    public JobResponse startReport(String userId, String period) {
        var denied = IdentityGuard.authorizeUserResource(userId);
        if (denied != null) {
            var message = userId == null || userId.isBlank()
                    ? "userId is required. The backend does not assume an account."
                    : "Current identity cannot start a report for another user.";
            return new JobResponse(denied, message,
                    null, null, null, null, List.of("Pass the authenticated userId"));
        }

        if (PeriodExpressions.looksLikeDateRange(period)) {
            return new JobResponse(SemanticStatus.INVALID_DATE_RANGE,
                    "Raw dates are not accepted. Send a semantic period. The backend calculates the range.",
                    null, null, null, null, PeriodExpressions.semanticPeriods());
        }

        PeriodOption periodOption;
        try {
            periodOption = period == null || period.isBlank()
                    ? PeriodOption.LAST_3_MONTHS
                    : PeriodOption.valueOf(period.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return new JobResponse(SemanticStatus.INVALID_PERIOD,
                    "Unsupported period. Use a semantic period enum.",
                    null, null, null, null, PeriodExpressions.semanticPeriods());
        }

        var context = AgentContextHolder.require();
        var jobId = "job-" + UUID.randomUUID();
        var now = Instant.now();
        var job = new AsyncJob(jobId, userId, context.agentSessionId(), "CUSTOMER_REPORT",
                JobStatus.PENDING, now, now, POLL_AFTER_SECONDS, null, null);
        jobStore.save(job);

        executor.submit(() -> runReport(jobId, userId, periodOption, context));

        return new JobResponse(SemanticStatus.ACCEPTED,
                "Report accepted. Poll getJobStatus until COMPLETED, then call getJobResult.",
                jobId, JobStatus.PENDING, POLL_AFTER_SECONDS, null,
                List.of("Call getJobStatus with jobId=" + jobId, "Do not retry startCustomerReport"));
    }

    @Override
    public JobResponse status(String jobId) {
        var job = jobStore.find(jobId).orElse(null);
        if (job == null) {
            return new JobResponse(SemanticStatus.CLARIFICATION_REQUIRED, "Unknown jobId.",
                    jobId, null, null, null, List.of("Call startCustomerReport first"));
        }
        var denied = IdentityGuard.authorizeUserResource(job.userId());
        if (denied != null) {
            return new JobResponse(denied, "Current identity cannot inspect another user's job.",
                    jobId, null, null, null, List.of());
        }
        if (job.status() == JobStatus.COMPLETED) {
            return new JobResponse(SemanticStatus.SUCCESS, "Job completed.",
                    job.jobId(), job.status(), 0, null, List.of("Call getJobResult"));
        }
        if (job.status() == JobStatus.CANCELLED) {
            return cancelledResponse(job, "Job was cancelled. This state is terminal.");
        }
        if (job.status() == JobStatus.FAILED) {
            return new JobResponse(SemanticStatus.DEPENDENCY_UNAVAILABLE,
                    job.errorMessage() == null ? "Job failed." : job.errorMessage(),
                    job.jobId(), job.status(), null, null, List.of("Start a new report if needed"));
        }
        return new JobResponse(SemanticStatus.OPERATION_IN_PROGRESS,
                "Job is still running.",
                job.jobId(), job.status(), job.pollAfterSeconds(), null,
                List.of("Retry getJobStatus after " + job.pollAfterSeconds() + " seconds"));
    }

    @Override
    public JobResponse result(String jobId) {
        var job = jobStore.find(jobId).orElse(null);
        if (job == null) {
            return new JobResponse(SemanticStatus.CLARIFICATION_REQUIRED, "Unknown jobId.",
                    jobId, null, null, null, List.of("Call startCustomerReport first"));
        }
        var denied = IdentityGuard.authorizeUserResource(job.userId());
        if (denied != null) {
            return new JobResponse(denied, "Current identity cannot read another user's job result.",
                    jobId, null, null, null, List.of());
        }
        if (job.status() == JobStatus.CANCELLED) {
            return cancelledResponse(job, "Result is unavailable because the job was cancelled.");
        }
        if (job.status() != JobStatus.COMPLETED) {
            return new JobResponse(SemanticStatus.OPERATION_IN_PROGRESS,
                    "Result is not available yet.",
                    job.jobId(), job.status(), job.pollAfterSeconds(), null,
                    List.of("Poll getJobStatus until COMPLETED"));
        }
        return new JobResponse(SemanticStatus.SUCCESS, "Report ready.",
                job.jobId(), job.status(), 0, job.result(), List.of());
    }

    @Override
    public JobResponse cancel(String jobId) {
        var job = jobStore.find(jobId).orElse(null);
        if (job == null) {
            return new JobResponse(SemanticStatus.CLARIFICATION_REQUIRED, "Unknown jobId.",
                    jobId, null, null, null, List.of("Call startCustomerReport first"));
        }
        var denied = IdentityGuard.authorizeUserResource(job.userId());
        if (denied != null) {
            return new JobResponse(denied, "Current identity cannot cancel another user's job.",
                    jobId, null, null, null, List.of());
        }
        if (job.status() == JobStatus.CANCELLED) {
            return cancelledResponse(job, "Job was already cancelled.");
        }
        if (job.status() == JobStatus.COMPLETED) {
            return new JobResponse(SemanticStatus.SUCCESS, "Job already completed. The result remains available.",
                    job.jobId(), job.status(), 0, null, List.of("Call getJobResult"));
        }
        if (job.status() == JobStatus.FAILED) {
            return new JobResponse(SemanticStatus.DEPENDENCY_UNAVAILABLE,
                    "Job already failed and cannot be cancelled.",
                    job.jobId(), job.status(), null, null, List.of("Start a new report if needed"));
        }
        var cancelled = job.withStatus(JobStatus.CANCELLED, Instant.now(), null, "Cancelled by caller");
        if (!jobStore.compareAndSet(jobId, Set.of(JobStatus.PENDING, JobStatus.RUNNING), cancelled)) {
            return status(jobId);
        }
        return cancelledResponse(cancelled, "Job cancelled.");
    }

    private static JobResponse cancelledResponse(AsyncJob job, String message) {
        return new JobResponse(SemanticStatus.CANCELLED, message,
                job.jobId(), JobStatus.CANCELLED, null, null,
                List.of("Do not poll this job", "Start a new report if the user still needs it"));
    }

    private void runReport(String jobId, String userId, PeriodOption period, AgentContext context) {
        var current = jobStore.find(jobId).orElseThrow();
        var running = current.withStatus(JobStatus.RUNNING, Instant.now(), null, null);
        if (!jobStore.compareAndSet(jobId, Set.of(JobStatus.PENDING), running)) {
            return;
        }
        AgentContextHolder.set(context);
        try {
            Thread.sleep(800);
            if (jobStore.find(jobId).orElseThrow().status() == JobStatus.CANCELLED) {
                return;
            }
            var profile = customerProfileApi.getProfile(userId);
            var search = transactionQueryApi.searchRecurringPayments(
                    new RecurringPaymentSearchRequest(userId, "STREAMING", null, period, 100));
            var subscriptions = subscriptionCommandApi.snapshot(userId);
            var customer = new LinkedHashMap<String, Object>();
            customer.put("userId", profile.userId());
            customer.put("displayName", profile.displayName());
            customer.put("accountStatus", profile.accountStatus());
            customer.put("segment", profile.segment());
            customer.put("status", profile.status().name());

            var result = new LinkedHashMap<String, Object>();
            result.put("composedFrom", AgentBoundary.ENTERPRISE_APIS);
            result.put("customer", customer);
            result.put("period", period.name());
            result.put("searchStatus", search.status().name());
            result.put("merchantCount", search.merchantSummaries().size());
            result.put("totalAmount", search.totalAmount());
            result.put("merchantSummaries", search.merchantSummaries());
            result.put("cancelledMerchants", subscriptions.cancelledMerchants());
            var completed = jobStore.find(jobId).orElseThrow()
                    .withStatus(JobStatus.COMPLETED, Instant.now(), Map.copyOf(result), null);
            jobStore.compareAndSet(jobId, Set.of(JobStatus.RUNNING), completed);
        } catch (Exception ex) {
            var failed = jobStore.find(jobId).orElse(null);
            if (failed != null) {
                jobStore.compareAndSet(jobId, Set.of(JobStatus.PENDING, JobStatus.RUNNING),
                        failed.withStatus(JobStatus.FAILED, Instant.now(), null, ex.getMessage()));
            }
        } finally {
            AgentContextHolder.clear();
        }
    }
}
