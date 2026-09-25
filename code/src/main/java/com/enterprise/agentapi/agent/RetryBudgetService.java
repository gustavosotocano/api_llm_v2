package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.RetryDisposition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts repeats of the same operation and stops the ones the platform already
 * marked retryable-with-a-limit or permanently failed.
 * Polling a job is not a retry. Confirmation and idempotent replays stay allowed.
 */
@Component
public class RetryBudgetService {
    private static final Set<String> EXEMPT = Set.of("getJobStatus", "getJobResult");

    private final AgentProperties properties;
    private final Map<String, State> attempts = new ConcurrentHashMap<>();

    public RetryBudgetService(AgentProperties properties) {
        this.properties = properties;
    }

    public int begin(String agentSessionId, String toolName, Map<String, Object> parameters) {
        if (EXEMPT.contains(toolName)) {
            return 0;
        }
        var state = attempts.computeIfAbsent(key(agentSessionId, toolName, parameters), ignored -> new State());
        var maxRetries = properties.getRetryBudget().getMaxRetries();
        synchronized (state) {
            var retryCount = state.served;
            if (state.last == RetryDisposition.PERMANENT_FAILURE && retryCount > 0) {
                throw new RetryBudgetExceededException(toolName, retryCount, 0);
            }
            if (state.last == RetryDisposition.RETRY_AFTER && retryCount > maxRetries) {
                throw new RetryBudgetExceededException(toolName, retryCount, maxRetries);
            }
            state.served++;
            return retryCount;
        }
    }

    public void observe(String agentSessionId, String toolName, Map<String, Object> parameters, RetryDisposition disposition) {
        if (EXEMPT.contains(toolName) || disposition == null) {
            return;
        }
        var state = attempts.computeIfAbsent(key(agentSessionId, toolName, parameters), ignored -> new State());
        synchronized (state) {
            state.last = disposition;
        }
    }

    static String operationKey(String toolName, Map<String, Object> parameters) {
        if (parameters != null && parameters.get("idempotencyKey") != null) {
            return toolName + "|idem|" + parameters.get("idempotencyKey");
        }
        if (parameters == null || parameters.isEmpty()) {
            return toolName;
        }
        var names = new ArrayList<>(parameters.keySet());
        names.sort(String::compareTo);
        var key = new StringBuilder(toolName);
        for (var name : names) {
            if ("confirmationToken".equals(name)) {
                continue;
            }
            key.append('|').append(name).append('=').append(parameters.get(name));
        }
        return key.toString();
    }

    private static String key(String agentSessionId, String toolName, Map<String, Object> parameters) {
        return agentSessionId + "|" + operationKey(toolName, parameters);
    }

    private static final class State {
        private int served;
        private RetryDisposition last;
    }
}
