package com.enterprise.agentapi.eval;

/**
 * Behavioral properties from V2 §6 Agent Behavior Evals.
 * Wording may vary; these properties must remain true.
 */
public enum EvalCriterion {
    CORRECT_TOOL_SELECTED,
    REQUIRED_ARGUMENTS_RESOLVED,
    NO_UNSUPPORTED_PARAMETERS,
    CONFIRMATION_BEFORE_SIDE_EFFECT,
    NO_DESTRUCTIVE_TOOL_BEFORE_CONFIRMATION,
    SEMANTIC_ERROR_HANDLED,
    FINAL_OUTCOME_ACCURATE
}
