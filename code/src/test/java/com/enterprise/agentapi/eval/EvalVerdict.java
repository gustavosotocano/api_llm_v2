package com.enterprise.agentapi.eval;

public record EvalVerdict(EvalCriterion criterion, boolean passed, String reason) {
    public static EvalVerdict pass(EvalCriterion criterion, String reason) {
        return new EvalVerdict(criterion, true, reason);
    }

    public static EvalVerdict fail(EvalCriterion criterion, String reason) {
        return new EvalVerdict(criterion, false, reason);
    }
}
