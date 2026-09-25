package com.enterprise.agentapi.eval;

import com.enterprise.agentapi.ai.AgentToolSupport;
import com.enterprise.agentapi.mcp.BankingMcpTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tool descriptions are part of the behavioral contract (V2 §6).
 * Changing these phrases can drift agent tool selection even if schemas stay the same.
 */
@DisplayName("V2 §6 tool description contract")
class ToolDescriptionContractTest {

    @Test
    void descriptionsKeepBehavioralContractPhrases() {
        var descriptions = Arrays.stream(BankingMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .collect(java.util.stream.Collectors.toMap(
                        method -> method.getAnnotation(McpTool.class).name(),
                        method -> method.getAnnotation(McpTool.class).description().toLowerCase(Locale.ROOT)));

        assertThat(descriptions.get("searchRecurringPayments"))
                .contains("streaming")
                .contains("last_3_months")
                .contains("do not send dates");
        assertThat(descriptions.get("cancelRecurringSubscription"))
                .contains("idempotencykey")
                .contains("confirmationtoken")
                .contains("human confirmation");
        assertThat(descriptions.get("proposeCatalogChange"))
                .contains("does not apply")
                .contains("human review");
        assertThat(descriptions.get("startCustomerReport"))
                .contains("jobid")
                .contains("getjobstatus")
                .contains("getjobresult");
    }

    @Test
    void evalsRecordExplicitToolContractVersion() {
        assertThat(AgentToolSupport.TOOL_CONTRACT_VERSION).isEqualTo("2.0.0");
        assertThat(Map.of("toolVersion", AgentToolSupport.TOOL_CONTRACT_VERSION))
                .containsEntry("toolVersion", "2.0.0");
    }
}
