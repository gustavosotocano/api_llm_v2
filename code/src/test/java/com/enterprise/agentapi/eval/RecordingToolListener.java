package com.enterprise.agentapi.eval;

import com.enterprise.agentapi.ai.ToolInvocation;
import com.enterprise.agentapi.ai.ToolInvocationListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordingToolListener implements ToolInvocationListener {
    private final List<ToolInvocation> invocations = new ArrayList<>();

    @Override
    public void onInvocation(ToolInvocation invocation) {
        invocations.add(invocation);
    }

    public List<ToolInvocation> invocations() {
        return Collections.unmodifiableList(invocations);
    }

    public void clear() {
        invocations.clear();
    }
}
