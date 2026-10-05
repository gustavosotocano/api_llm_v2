package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.domain.IdentityType;

/**
 * Data-layer view of the authenticated subject. A repository uses this so a
 * direct store call cannot return another user's rows when an agent context is bound.
 */
public final class ResourceScope {
    private ResourceScope() {}

    public static boolean visibleToCaller(String requestedUserId) {
        if (requestedUserId == null || requestedUserId.isBlank()) {
            return false;
        }
        var context = AgentContextHolder.get();
        if (context == null) {
            return true;
        }
        if (context.identityType() == IdentityType.SERVICE) {
            return requestedUserId.equals(context.onBehalfOfUserId());
        }
        return requestedUserId.equals(context.userId());
    }
}
