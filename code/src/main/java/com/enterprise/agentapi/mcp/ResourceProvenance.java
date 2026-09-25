package com.enterprise.agentapi.mcp;

import com.enterprise.agentapi.domain.TrustLevel;

import java.util.Map;

public final class ResourceProvenance {
    private ResourceProvenance() {}

    public static Map<String, Object> wrap(String resourceId, TrustLevel trustLevel, Object data) {
        return ContextProvenance.resource(resourceId, trustLevel, data);
    }
}
