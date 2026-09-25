package com.enterprise.agentapi.mcp;

import com.enterprise.agentapi.domain.TrustLevel;
import org.springframework.stereotype.Component;

/**
 * Context may inform the agent; it never becomes the security boundary (V2 §5).
 */
@Component
public class ContextAuthority {

    public boolean mayGrantPermission(TrustLevel trustLevel) {
        return false;
    }

    public boolean maySkipConfirmation(TrustLevel trustLevel) {
        return false;
    }

    public boolean mayMutateCatalog(TrustLevel trustLevel) {
        return false;
    }

    public boolean isInstructionalPolicy(TrustLevel trustLevel) {
        return trustLevel == TrustLevel.AUTHORITATIVE_POLICY;
    }

    public boolean isUntrusted(TrustLevel trustLevel) {
        return trustLevel == null || trustLevel == TrustLevel.UNTRUSTED_CONTENT;
    }
}
