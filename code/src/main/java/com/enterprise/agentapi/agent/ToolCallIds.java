package com.enterprise.agentapi.agent;

import java.util.UUID;

/**
 * Correlates the technical, AI, and business audit events of one tool invocation.
 */
public final class ToolCallIds {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ToolCallIds() {}

    public static String open() {
        var id = "call-" + UUID.randomUUID();
        CURRENT.set(id);
        return id;
    }

    public static String current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
