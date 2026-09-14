package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.observability.AgentAuditService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRateLimiter {
    private final AgentProperties properties;
    private final AgentAuditService auditService;
    private final Map<String, Deque<Instant>> sessionTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> toolTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> loopTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastLoopAlertAt = new ConcurrentHashMap<>();

    public AgentRateLimiter(AgentProperties properties, AgentAuditService auditService) {
        this.properties = properties;
        this.auditService = auditService;
    }

    public void checkAllowed(String agentSessionId, String toolName, String userId, String channel) {
        var normalizedTool = normalizeTool(toolName);
        var now = Instant.now();

        checkWindow(sessionTimestamps, sessionKey(agentSessionId), now,
                properties.getRateLimit().getWindowSeconds(),
                properties.getRateLimit().getMaxRequestsPerWindow(),
                agentSessionId, normalizedTool, userId, channel, RateLimitScope.SESSION, null);

        var toolLimit = properties.getRateLimit().resolveToolLimit(normalizedTool);
        if (toolLimit != null) {
            checkWindow(toolTimestamps, toolKey(agentSessionId, normalizedTool), now,
                    toolLimit.getWindowSeconds(),
                    toolLimit.getMaxRequestsPerWindow(),
                    agentSessionId, normalizedTool, userId, channel, RateLimitScope.TOOL, toolLimit);
        }

        detectLoop(agentSessionId, normalizedTool, userId, channel, now);

        recordHit(sessionTimestamps, sessionKey(agentSessionId), now,
                properties.getRateLimit().getWindowSeconds());
        if (toolLimit != null) {
            recordHit(toolTimestamps, toolKey(agentSessionId, normalizedTool), now, toolLimit.getWindowSeconds());
        }
        recordLoopHit(agentSessionId, normalizedTool, now);
    }

    private void detectLoop(String agentSessionId, String toolName, String userId, String channel, Instant now) {
        var loop = properties.getRateLimit().getLoopDetection();
        if (!loop.isEnabled()) {
            return;
        }

        var key = toolKey(agentSessionId, toolName);
        var timestamps = loopTimestamps.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        var windowStart = now.minusSeconds(loop.getWindowSeconds());
        int recentCalls;
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.removeFirst();
            }
            recentCalls = timestamps.size();
        }

        if (recentCalls < loop.getMaxSameToolCalls()) {
            return;
        }

        emitLoopAlert(agentSessionId, toolName, userId, channel, recentCalls, loop.getWindowSeconds());

        if (loop.isBlockOnLoop()) {
            throw new AgentRateLimitExceededException(
                    agentSessionId, toolName, RateLimitScope.LOOP, loop.getWindowSeconds());
        }
    }

    private void emitLoopAlert(String agentSessionId, String toolName, String userId, String channel,
                               int recentCalls, int windowSeconds) {
        var alertKey = agentSessionId + "|" + toolName;
        var now = Instant.now();
        var lastAlert = lastLoopAlertAt.get(alertKey);
        if (lastAlert != null && lastAlert.isAfter(now.minusSeconds(windowSeconds))) {
            return;
        }
        lastLoopAlertAt.put(alertKey, now);
        auditService.technical(agentSessionId, userId, channel, "ANOMALY_LOOP_DETECTED", Map.of(
                "tool", toolName,
                "recentCallsInWindow", recentCalls,
                "windowSeconds", windowSeconds,
                "threshold", properties.getRateLimit().getLoopDetection().getMaxSameToolCalls(),
                "severity", "HIGH",
                "action", properties.getRateLimit().getLoopDetection().isBlockOnLoop() ? "BLOCKED" : "ALERT_ONLY"));
    }

    private void checkWindow(Map<String, Deque<Instant>> store, String key, Instant now,
                             int windowSeconds, int maxRequests,
                             String agentSessionId, String toolName, String userId, String channel,
                             RateLimitScope scope, AgentProperties.ToolLimit toolLimit) {
        var timestamps = store.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        var windowStart = now.minusSeconds(windowSeconds);
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxRequests) {
                var oldest = timestamps.peekFirst();
                var retryAfter = oldest == null
                        ? windowSeconds
                        : (int) Math.max(1, windowSeconds - (now.getEpochSecond() - oldest.getEpochSecond()));
                auditService.technical(agentSessionId, userId, channel, "RATE_LIMIT_EXCEEDED", Map.of(
                        "scope", scope.name(),
                        "tool", toolName,
                        "maxRequests", maxRequests,
                        "windowSeconds", windowSeconds,
                        "configuredToolLimit", toolLimit != null));
                throw new AgentRateLimitExceededException(agentSessionId, toolName, scope, retryAfter);
            }
        }
    }

    private void recordHit(Map<String, Deque<Instant>> store, String key, Instant now, int windowSeconds) {
        var timestamps = store.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        var windowStart = now.minusSeconds(windowSeconds);
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.removeFirst();
            }
            timestamps.addLast(now);
        }
    }

    private void recordLoopHit(String agentSessionId, String toolName, Instant now) {
        var loop = properties.getRateLimit().getLoopDetection();
        if (!loop.isEnabled()) {
            return;
        }
        recordHit(loopTimestamps, toolKey(agentSessionId, toolName), now, loop.getWindowSeconds());
    }

    private String sessionKey(String agentSessionId) {
        return agentSessionId;
    }

    private String toolKey(String agentSessionId, String toolName) {
        return agentSessionId + "|" + toolName;
    }

    private String normalizeTool(String toolName) {
        return toolName == null || toolName.isBlank() ? "UNKNOWN" : toolName.trim();
    }
}
