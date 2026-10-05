package com.enterprise.agentapi.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Idempotency keys for MCP tools. The model does not invent them.
 * The same session and the same operation always produce the same key.
 */
public final class IdempotencyKeys {
    private IdempotencyKeys() {}

    public static String derive(String toolName, String... parts) {
        var canonical = new StringBuilder(toolName).append('\n').append(sessionId());
        if (parts != null) {
            for (var part : parts) {
                canonical.append('\n').append(normalize(part));
            }
        }
        return "idem-" + sha256(canonical.toString()).substring(0, 32);
    }

    private static String sessionId() {
        var context = AgentContextHolder.get();
        if (context == null || context.agentSessionId() == null || context.agentSessionId().isBlank()) {
            return "none";
        }
        return context.agentSessionId().trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String sha256(String canonical) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(digest.length * 2);
            for (var b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
