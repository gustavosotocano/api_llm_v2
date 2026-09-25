package com.enterprise.agentapi.agent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Request-scoped correlation for one inbound call and the async work it starts.
 * Downstream counts stay on the thread that opened the trace.
 */
public final class OperationTrace {
    public static final String HEADER = "X-Enterprise-Request-Id";

    private record State(
            String enterpriseRequestId,
            AtomicInteger downstream,
            int baseline,
            int retryCount,
            int estimatedCostUnits,
            int actualCostUnits
    ) {
        private State(String enterpriseRequestId) {
            this(enterpriseRequestId, new AtomicInteger(), 0, 0, 0, 0);
        }
    }

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private OperationTrace() {}

    /**
     * Opens a trace when this thread has none. A non-blank id replaces the current one
     * so a chat turn can be adopted by the MCP call it triggers.
     */
    public static void begin(String supplied) {
        var incoming = supplied == null || supplied.isBlank() ? null : supplied.trim();
        var current = CURRENT.get();
        if (current != null && incoming == null) {
            return;
        }
        if (current != null && incoming.equals(current.enterpriseRequestId)) {
            return;
        }
        var id = incoming == null ? "req-" + UUID.randomUUID() : incoming;
        CURRENT.set(new State(id));
    }

    public static String enterpriseRequestId() {
        var state = CURRENT.get();
        return state == null ? null : state.enterpriseRequestId;
    }

    public static void recordDownstream() {
        var state = CURRENT.get();
        if (state != null) {
            state.downstream.incrementAndGet();
        }
    }

    public static int downstreamCallCount() {
        var state = CURRENT.get();
        return state == null ? 0 : state.downstream.get() - state.baseline;
    }

    /** Counts only calls made after this point, so one request does not inherit another. */
    public static void markDownstreamBaseline() {
        var state = CURRENT.get();
        if (state == null) {
            return;
        }
        CURRENT.set(new State(
                state.enterpriseRequestId,
                state.downstream,
                state.downstream.get(),
                state.retryCount,
                state.estimatedCostUnits,
                state.actualCostUnits));
    }

    public static void noteAttempt(int retryCount, int estimatedCostUnits, int actualCostUnits) {
        var state = CURRENT.get();
        if (state == null) {
            return;
        }
        CURRENT.set(new State(
                state.enterpriseRequestId,
                state.downstream,
                state.baseline,
                retryCount,
                estimatedCostUnits,
                actualCostUnits));
    }

    public static int retryCount() {
        var state = CURRENT.get();
        return state == null ? 0 : state.retryCount;
    }

    public static int estimatedCostUnits() {
        var state = CURRENT.get();
        return state == null ? 0 : state.estimatedCostUnits;
    }

    public static int actualCostUnits() {
        var state = CURRENT.get();
        return state == null ? 0 : state.actualCostUnits;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
