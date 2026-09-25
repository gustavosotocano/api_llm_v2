package com.enterprise.agentapi.api;

import com.enterprise.agentapi.domain.AgentWorkflow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatWorkflowResolverTest {

    @Test
    void searchStaysReadOnly() {
        assertThat(ChatWorkflowResolver.resolve(null, "Show me my recurring streaming payments"))
                .isEqualTo(AgentWorkflow.READ);
    }

    @Test
    void cancelMessageOpensOnlyCancellation() {
        assertThat(ChatWorkflowResolver.resolve(null, "Cancel my Netflix recurring subscription"))
                .isEqualTo(AgentWorkflow.CANCELLATION);
    }

    @Test
    void reportMessageOpensOnlyReport() {
        assertThat(ChatWorkflowResolver.resolve(null, "Build a customer report for the last 3 months"))
                .isEqualTo(AgentWorkflow.REPORT);
    }

    @Test
    void explicitWorkflowWins() {
        assertThat(ChatWorkflowResolver.resolve("READ", "Cancel my Netflix subscription"))
                .isEqualTo(AgentWorkflow.READ);
        assertThat(ChatWorkflowResolver.resolve("FULL", "Show streaming payments"))
                .isEqualTo(AgentWorkflow.FULL);
    }

    @Test
    void twoWriteIntentsUseFull() {
        assertThat(ChatWorkflowResolver.resolve(null, "Cancel Netflix and generate a report"))
                .isEqualTo(AgentWorkflow.FULL);
    }
}
