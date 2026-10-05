package com.enterprise.agentapi.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Binds a confirmation token to one tool call. A different user or merchant
 * produces a different hash, so the token cannot be replayed against another operation.
 */
public final class ConfirmationBinding {
    public static final String CANCEL_TOOL = "cancelRecurringSubscription";

    private ConfirmationBinding() {}

    public static String argumentHash(String userId, String merchant) {
        return sha256(CANCEL_TOOL + "\n" + userId + "\n" + merchant);
    }

    public static String preview(String merchant) {
        return "Cancel the recurring subscription for " + merchant
                + ". Future charges from this merchant will be blocked. Past payments are not refunded.";
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
