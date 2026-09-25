package com.enterprise.agentapi.domain;

/**
 * Limits which tools an agent session may invoke.
 * Read-only is the default blast-radius control from V2 §5.
 */
public enum AgentWorkflow {
    READ,
    CANCELLATION,
    GOVERNANCE,
    REPORT,
    FULL
}
