package com.enterprise.agentapi.eval;

import java.util.List;

public record GoldenScenario(
        String id,
        String userMessage,
        String userId,
        String agentSessionId,
        List<ScriptedTurn> turns,
        EvalExpectation expectation
) {
    @Override
    public String toString() {
        return id;
    }
}
