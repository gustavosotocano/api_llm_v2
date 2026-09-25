package com.enterprise.agentapi.api;

import com.enterprise.agentapi.domain.AgentWorkflow;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chat stays inside the workflow the request needs.
 * An explicit workflow wins. Otherwise the message selects one write capability, or READ.
 */
public final class ChatWorkflowResolver {
    private static final Pattern CANCEL = Pattern.compile("\\b(cancel|cancellation|unsubscribe)\\b");
    private static final Pattern REPORT = Pattern.compile("\\breport\\b");
    private static final Pattern CATALOG = Pattern.compile("\\b(catalog|propose)\\b|new category|add merchants?");

    private ChatWorkflowResolver() {}

    public static AgentWorkflow resolve(String explicitWorkflow, String message) {
        var explicit = parse(explicitWorkflow);
        if (explicit != null) {
            return explicit;
        }
        return infer(message);
    }

    private static AgentWorkflow parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AgentWorkflow.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static AgentWorkflow infer(String message) {
        var text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        var cancel = CANCEL.matcher(text).find();
        var report = REPORT.matcher(text).find();
        var catalog = CATALOG.matcher(text).find();
        var writes = (cancel ? 1 : 0) + (report ? 1 : 0) + (catalog ? 1 : 0);
        if (writes > 1) {
            return AgentWorkflow.FULL;
        }
        if (cancel) {
            return AgentWorkflow.CANCELLATION;
        }
        if (report) {
            return AgentWorkflow.REPORT;
        }
        if (catalog) {
            return AgentWorkflow.GOVERNANCE;
        }
        return AgentWorkflow.READ;
    }
}
