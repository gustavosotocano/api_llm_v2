package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.domain.AsyncJob;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
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
}
