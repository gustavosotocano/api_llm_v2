package com.enterprise.agentapi.application;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.agent.ToolCallIds;
import com.enterprise.agentapi.observability.AgentAuditService;
import com.enterprise.agentapi.domain.AsyncJob;
import com.enterprise.agentapi.domain.JobResponse;
import com.enterprise.agentapi.domain.JobStatus;
import com.enterprise.agentapi.domain.OperationRetry;
import com.enterprise.agentapi.domain.PeriodExpressions;
import com.enterprise.agentapi.domain.PeriodOption;
import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.enterprise.AgentBoundary;
import com.enterprise.agentapi.enterprise.CustomerProfileApi;
import com.enterprise.agentapi.enterprise.CustomerReportApi;
import com.enterprise.agentapi.enterprise.SubscriptionCommandApi;
import com.enterprise.agentapi.enterprise.TransactionQueryApi;
import com.enterprise.agentapi.application.port.AsyncJobStore;
import com.enterprise.agentapi.application.port.IdempotencyStore;
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
    private static final int MAX_IN_FLIGHT_PER_SESSION = 1;

    private final AsyncJobStore jobStore;
    private final IdempotencyStore idempotencyStore;
    private final CustomerProfileApi customerProfileApi;
    private final TransactionQueryApi transactionQueryApi;
    private final SubscriptionCommandApi subscriptionCommandApi;
    private final AgentAuditService auditService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CustomerReportJobService(AsyncJobStore jobStore,
                                    IdempotencyStore idempotencyStore,
                                    CustomerProfileApi customerProfileApi,
                                    TransactionQueryApi transactionQueryApi,
                                    SubscriptionCommandApi subscriptionCommandApi,
                                    AgentAuditService auditService) {
        this.jobStore = jobStore;
        this.idempotencyStore = idempotencyStore;
        this.customerProfileApi = customerProfileApi;
        this.transactionQueryApi = transactionQueryApi;
        this.subscriptionCommandApi = subscriptionCommandApi;
        this.auditService = auditService;
    }

    @Override
    public JobResponse startReport(String userId, String period, String idempotencyKey) {
        var denied = IdentityGuard.authorizeUserResource(userId);
        if (denied != null) {
            var message = userId == null || userId.isBlank()
                    ? "userId is required. The backend does not assume an account."
                    : "Current identity cannot start a report for another user.";
            return respond(denied, message, null, null, null, null, List.of("Pass the authenticated userId"), null);
        }

        var key = normalizeKey(idempotencyKey);
        if (key == null) {
            return respond(SemanticStatus.CLARIFICATION_REQUIRED,
                    "idempotencyKey is required to start a report.",
                    null, null, null, null,
                    List.of("Send a stable idempotencyKey for this report"), null);
        }

        if (PeriodExpressions.looksLikeDateRange(period)) {
            return respond(SemanticStatus.INVALID_DATE_RANGE,
                    "Raw dates are not accepted. Send a semantic period. The backend calculates the range.",
                    null, null, null, null, PeriodExpressions.semanticPeriods(), null);
        }

        PeriodOption periodOption;
        try {
            periodOption = period == null || period.isBlank()
                    ? PeriodOption.LAST_3_MONTHS
                    : PeriodOption.valueOf(period.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return respond(SemanticStatus.INVALID_PERIOD,
                    "Unsupported period. Use a semantic period enum.",
                    null, null, null, null, PeriodExpressions.semanticPeriods(), null);
        }

        var context = AgentContextHolder.require();
        var fingerprint = userId.trim() + "|" + periodOption.name();
        if (idempotencyStore.hasDifferentPayload(key, fingerprint)) {
            return respond(SemanticStatus.IDEMPOTENCY_CONFLICT,
                    "The same Idempotency-Key was already used with different parameters.",
                    null, null, null, null,
                    List.of("Reuse the original parameters or generate a new idempotencyKey"), null);
        }
        var cached = idempotencyStore.find(key, JobResponse.class);
        if (cached.isPresent() && cached.get().jobId() != null) {
            return status(cached.get().jobId());
        }

        var inFlight = jobStore.inFlightForSession(context.agentSessionId());
        if (inFlight.size() >= MAX_IN_FLIGHT_PER_SESSION) {
            var active = inFlight.getFirst();
            return respond(SemanticStatus.OPERATION_IN_PROGRESS,
                    "A report is already running for this session.",
                    active.jobId(), active.status(), active.pollAfterSeconds(), null,
                    List.of("Poll getJobStatus for jobId=" + active.jobId(), "Do not start a second report"),
                    active.toolCallId());
        }

        var jobId = "job-" + UUID.randomUUID();
        var now = Instant.now();
        var toolCallId = ToolCallIds.current();
        var job = new AsyncJob(jobId, userId, context.agentSessionId(), "CUSTOMER_REPORT",
                JobStatus.PENDING, now, now, POLL_AFTER_SECONDS, null, null, toolCallId);
        jobStore.save(job);
        var enterpriseRequestId = OperationTrace.enterpriseRequestId();
        executor.submit(() -> runReport(jobId, userId, periodOption, context, toolCallId, enterpriseRequestId));

        var accepted = respond(SemanticStatus.ACCEPTED,
                "Report accepted. Poll getJobStatus until COMPLETED, then call getJobResult.",
                jobId, JobStatus.PENDING, POLL_AFTER_SECONDS, null,
                List.of("Call getJobStatus with jobId=" + jobId, "Do not retry startCustomerReport"),
                toolCallId);
        idempotencyStore.save(key, accepted, fingerprint);
        return accepted;
    }

    @Override
    public JobResponse status(String jobId) {
        var job = jobStore.find(jobId).orElse(null);
        if (job == null) {
            return respond(SemanticStatus.CLARIFICATION_REQUIRED, "Unknown jobId.",
                    jobId, null, null, null, List.of("Call startCustomerReport first"), null);
        }
        var denied = IdentityGuard.authorizeUserResource(job.userId());
        if (denied != null) {
            return respond(denied, "Current identity cannot inspect another user's job.",
                    jobId, null, null, null, List.of(), null);
        }
        if (job.status() == JobStatus.COMPLETED) {
            return respond(SemanticStatus.SUCCESS, "Job completed.",
                    job.jobId(), job.status(), 0, null, List.of("Call getJobResult"), job.toolCallId());
        }
        if (job.status() == JobStatus.CANCELLED) {
            return cancelledResponse(job, "Job was cancelled. This state is terminal.");
        }
        if (job.status() == JobStatus.FAILED) {
            return respond(SemanticStatus.DEPENDENCY_UNAVAILABLE,
                    job.errorMessage() == null ? "Job failed." : job.errorMessage(),
                    job.jobId(), job.status(), null, null, List.of("Start a new report if needed"), job.toolCallId());
        }
        return respond(SemanticStatus.OPERATION_IN_PROGRESS,
                "Job is still running.",
                job.jobId(), job.status(), job.pollAfterSeconds(), null,
                List.of("Retry getJobStatus after " + job.pollAfterSeconds() + " seconds"), job.toolCallId());
    }

    @Override
    public JobResponse result(String jobId) {
        var job = jobStore.find(jobId).orElse(null);
        if (job == null) {
            return respond(SemanticStatus.CLARIFICATION_REQUIRED, "Unknown jobId.",
                    jobId, null, null, null, List.of("Call startCustomerReport first"), null);
        }
        var denied = IdentityGuard.authorizeUserResource(job.userId());
        if (denied != null) {
            return respond(denied, "Current identity cannot read another user's job result.",
                    jobId, null, null, null, List.of(), null);
        }
        if (job.status() == JobStatus.CANCELLED) {
            return cancelledResponse(job, "Result is unavailable because the job was cancelled.");
        }
        if (job.status() != JobStatus.COMPLETED) {
            return respond(SemanticStatus.OPERATION_IN_PROGRESS,
                    "Result is not available yet.",
                    job.jobId(), job.status(), job.pollAfterSeconds(), null,
                    List.of("Poll getJobStatus until COMPLETED"), job.toolCallId());
        }
        return respond(SemanticStatus.SUCCESS, "Report ready.",
                job.jobId(), job.status(), 0, job.result(), List.of(), job.toolCallId());
    }

    @Override
    public JobResponse cancel(String jobId) {
        var job = jobStore.find(jobId).orElse(null);
        if (job == null) {
            return respond(SemanticStatus.CLARIFICATION_REQUIRED, "Unknown jobId.",
                    jobId, null, null, null, List.of("Call startCustomerReport first"), null);
        }
        var denied = IdentityGuard.authorizeUserResource(job.userId());
        if (denied != null) {
            return respond(denied, "Current identity cannot cancel another user's job.",
                    jobId, null, null, null, List.of(), null);
        }
        if (job.status() == JobStatus.CANCELLED) {
            return cancelledResponse(job, "Job was already cancelled.");
        }
        if (job.status() == JobStatus.COMPLETED) {
            return respond(SemanticStatus.SUCCESS, "Job already completed. The result remains available.",
                    job.jobId(), job.status(), 0, null, List.of("Call getJobResult"), job.toolCallId());
        }
        if (job.status() == JobStatus.FAILED) {
            return respond(SemanticStatus.DEPENDENCY_UNAVAILABLE,
                    "Job already failed and cannot be cancelled.",
                    job.jobId(), job.status(), null, null, List.of("Start a new report if needed"), job.toolCallId());
        }
        var cancelled = job.withStatus(JobStatus.CANCELLED, Instant.now(), null, "Cancelled by caller");
        if (!jobStore.compareAndSet(jobId, Set.of(JobStatus.PENDING, JobStatus.RUNNING), cancelled)) {
            return status(jobId);
        }
        return cancelledResponse(cancelled, "Job cancelled.");
    }

    private static JobResponse cancelledResponse(AsyncJob job, String message) {
        return respond(SemanticStatus.CANCELLED, message,
                job.jobId(), JobStatus.CANCELLED, null, null,
                List.of("Do not poll this job", "Start a new report if the user still needs it"),
                job.toolCallId());
    }

    private static String normalizeKey(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    }

    private static JobResponse respond(SemanticStatus status, String message, String jobId, JobStatus jobStatus,
                                       Integer pollAfterSeconds, Map<String, Object> result, List<String> suggestions,
                                       String toolCallId) {
        var delay = status == SemanticStatus.ACCEPTED || status == SemanticStatus.OPERATION_IN_PROGRESS
                ? pollAfterSeconds
                : null;
        return new JobResponse(status, message, jobId, jobStatus, pollAfterSeconds, result, suggestions,
                toolCallId, OperationRetry.forStatus(status, delay));
    }

    private void runReport(String jobId, String userId, PeriodOption period, AgentContext context,
                           String toolCallId, String enterpriseRequestId) {
        var current = jobStore.find(jobId).orElseThrow();
        var running = current.withStatus(JobStatus.RUNNING, Instant.now(), null, null);
        if (!jobStore.compareAndSet(jobId, Set.of(JobStatus.PENDING), running)) {
            return;
        }
        OperationTrace.begin(enterpriseRequestId);
        AgentContextHolder.set(context);
        var startedAt = System.nanoTime();
        var outcome = "FAILED";
        try {
            Thread.sleep(800);
            if (jobStore.find(jobId).orElseThrow().status() == JobStatus.CANCELLED) {
                outcome = "CANCELLED";
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
            var durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            result.put("enterpriseRequestId", OperationTrace.enterpriseRequestId() == null
                    ? "none" : OperationTrace.enterpriseRequestId());
            result.put("downstreamCallCount", OperationTrace.downstreamCallCount());
            result.put("executionDurationMs", durationMs);
            var completed = jobStore.find(jobId).orElseThrow()
                    .withStatus(JobStatus.COMPLETED, Instant.now(), Map.copyOf(result), null);
            if (jobStore.compareAndSet(jobId, Set.of(JobStatus.RUNNING), completed)) {
                outcome = "COMPLETED";
            }
        } catch (Exception ex) {
            var failed = jobStore.find(jobId).orElse(null);
            if (failed != null) {
                jobStore.compareAndSet(jobId, Set.of(JobStatus.PENDING, JobStatus.RUNNING),
                        failed.withStatus(JobStatus.FAILED, Instant.now(), null, ex.getMessage()));
            }
        } finally {
            var durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            var attributes = new LinkedHashMap<String, Object>();
            attributes.put("jobId", jobId);
            attributes.put("toolCallId", toolCallId == null ? "none" : toolCallId);
            attributes.put("enterpriseRequestId", OperationTrace.enterpriseRequestId() == null
                    ? "none" : OperationTrace.enterpriseRequestId());
            attributes.put("downstreamCallCount", OperationTrace.downstreamCallCount());
            attributes.put("executionDurationMs", durationMs);
            attributes.put("jobStatus", outcome);
            auditService.technical(context.agentSessionId(), userId, context.channel(), "JOB_EXECUTION", attributes);
            OperationTrace.clear();
            AgentContextHolder.clear();
        }
    }
}
