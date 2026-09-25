package com.enterprise.agentapi.enterprise;

import java.util.List;

/**
 * V2 §2. MCP is the interaction boundary. It does not replace enterprise APIs
 * and it does not own agent-runtime concerns.
 */
public final class AgentBoundary {
    public static final List<String> MCP_DOES_NOT_OWN = List.of(
            "persistent agent memory",
            "workflow state",
            "retry policies",
            "orchestration");

    public static final List<String> ENTERPRISE_APIS = List.of(
            "CustomerProfileApi",
            "TransactionQueryApi",
            "SubscriptionCommandApi");

    private AgentBoundary() {}
}
