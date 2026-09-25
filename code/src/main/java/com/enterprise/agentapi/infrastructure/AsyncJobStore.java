package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.domain.AsyncJob;
import com.enterprise.agentapi.domain.JobStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class AsyncJobStore {
    private final Map<String, AsyncJob> jobs = new ConcurrentHashMap<>();

    public void save(AsyncJob job) {
        jobs.put(job.jobId(), job);
    }

    public Optional<AsyncJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public List<AsyncJob> inFlightForSession(String agentSessionId) {
        return jobs.values().stream()
                .filter(job -> agentSessionId != null && agentSessionId.equals(job.agentSessionId()))
                .filter(job -> job.status() == JobStatus.PENDING || job.status() == JobStatus.RUNNING)
                .toList();
    }

    public synchronized boolean compareAndSet(String jobId, Set<JobStatus> expected, AsyncJob next) {
        var current = jobs.get(jobId);
        if (current == null || !expected.contains(current.status())) {
            return false;
        }
        jobs.put(jobId, next);
        return true;
    }
}
