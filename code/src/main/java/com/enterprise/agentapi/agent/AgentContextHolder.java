package com.enterprise.agentapi.agent;

public final class AgentContextHolder {
    private static final ThreadLocal<AgentContext> CONTEXT = new ThreadLocal<>();

    private AgentContextHolder() {}

    public static void set(AgentContext context) {
        CONTEXT.set(context);
    }

    public static AgentContext get() {
        return CONTEXT.get();
    }

    public static AgentContext require() {
        var context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("Agent context is not bound for this invocation");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
