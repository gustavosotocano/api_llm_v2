package com.enterprise.agentapi.enterprise;

import com.enterprise.agentapi.domain.JobResponse;

public interface CustomerReportApi {
    JobResponse startReport(String userId, String period);

    JobResponse status(String jobId);

    JobResponse result(String jobId);

    JobResponse cancel(String jobId);
}
