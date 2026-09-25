package com.enterprise.agentapi.application.port;

import com.enterprise.agentapi.domain.AsyncJob;
import com.enterprise.agentapi.domain.JobStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AsyncJobStore {
    void save(AsyncJob job);

    Optional<AsyncJob> find(String jobId);

    List<AsyncJob> inFlightForSession(String agentSessionId);

    boolean compareAndSet(String jobId, Set<JobStatus> expected, AsyncJob next);
}
