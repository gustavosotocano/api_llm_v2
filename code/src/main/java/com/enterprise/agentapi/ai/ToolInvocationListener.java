package com.enterprise.agentapi.ai;

@FunctionalInterface
public interface ToolInvocationListener {
    void onInvocation(ToolInvocation invocation);
}
