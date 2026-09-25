package com.enterprise.agentapi.eval;

import com.enterprise.agentapi.domain.SemanticStatus;

import java.util.List;
import java.util.Set;

public record EvalExpectation(
        List<String> requiredTools,
        List<String> forbiddenTools,
        Set<String> requiredArguments,
        SemanticStatus expectedFinalStatus,
        SemanticStatus expectedSemanticStatus,
        boolean requireConfirmationBeforeWrite,
        boolean forbidDestructiveTools
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<String> requiredTools = List.of();
        private List<String> forbiddenTools = List.of();
        private Set<String> requiredArguments = Set.of();
        private SemanticStatus expectedFinalStatus;
        private SemanticStatus expectedSemanticStatus;
        private boolean requireConfirmationBeforeWrite;
        private boolean forbidDestructiveTools;

        public Builder requiredTools(String... tools) {
            this.requiredTools = List.of(tools);
            return this;
        }

        public Builder forbiddenTools(String... tools) {
            this.forbiddenTools = List.of(tools);
            return this;
        }

        public Builder requiredArguments(String... args) {
            this.requiredArguments = Set.of(args);
            return this;
        }

        public Builder expectedFinalStatus(SemanticStatus status) {
            this.expectedFinalStatus = status;
            return this;
        }

        public Builder expectedSemanticStatus(SemanticStatus status) {
            this.expectedSemanticStatus = status;
            return this;
        }

        public Builder requireConfirmationBeforeWrite() {
            this.requireConfirmationBeforeWrite = true;
            return this;
        }

        public Builder forbidDestructiveTools() {
            this.forbidDestructiveTools = true;
            return this;
        }

        public EvalExpectation build() {
            return new EvalExpectation(
                    requiredTools, forbiddenTools, requiredArguments,
                    expectedFinalStatus, expectedSemanticStatus,
                    requireConfirmationBeforeWrite, forbidDestructiveTools);
        }
    }
}
