package com.enterprise.agentapi.eval;

import com.enterprise.agentapi.ai.ToolInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V2 §6 agent behavior evals")
class AgentEvalSuiteTest {

    static Stream<GoldenScenario> goldenScenarios() {
        return GoldenScenarios.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    void goldenScenarioHoldsBehavioralProperties(GoldenScenario scenario) {
        var harness = EvalHarness.create();
        var trace = harness.run(scenario);
        var verdicts = BehavioralEvaluator.evaluate(scenario.expectation(), trace);

        assertThat(verdicts)
                .as(failureReport(scenario, trace, verdicts))
                .allMatch(EvalVerdict::passed);
    }

    private static String failureReport(GoldenScenario scenario,
                                        List<ToolInvocation> trace,
                                        List<EvalVerdict> verdicts) {
        var tools = trace.stream()
                .map(call -> call.toolName() + " -> " + call.status())
                .collect(Collectors.joining(" | "));
        var failed = verdicts.stream()
                .filter(verdict -> !verdict.passed())
                .map(verdict -> verdict.criterion() + ": " + verdict.reason())
                .collect(Collectors.joining("; "));
        return """
                Scenario %s failed.
                User: %s
                Trace: %s
                Failures: %s
                """.formatted(scenario.id(), scenario.userMessage(), tools, failed);
    }
}
