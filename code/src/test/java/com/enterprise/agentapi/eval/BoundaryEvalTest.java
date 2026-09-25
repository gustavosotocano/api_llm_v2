package com.enterprise.agentapi.eval;

import com.enterprise.agentapi.agent.AgentContext;
import com.enterprise.agentapi.agent.AgentContextHolder;
import com.enterprise.agentapi.api.EnterpriseBankingController;
import com.enterprise.agentapi.application.CustomerProfileService;
import com.enterprise.agentapi.domain.IdentityType;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.enterprise.AgentBoundary;
import com.enterprise.agentapi.infrastructure.CustomerProfileRepository;
import com.enterprise.agentapi.mcp.BankingMcpTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V2 §2 agent boundary")
class BoundaryEvalTest {

    @AfterEach
    void tearDown() {
        AgentContextHolder.clear();
    }

    @Test
    void mcpDoesNotOwnRuntimeConcerns() {
        assertThat(AgentBoundary.MCP_DOES_NOT_OWN).containsExactly(
                "persistent agent memory",
                "workflow state",
                "retry policies",
                "orchestration");
        var fieldTypes = Arrays.stream(BankingMcpTools.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .collect(Collectors.toSet());
        assertThat(fieldTypes).isEqualTo(Set.of(
                "BankingToolOperations", "McpJsonEncoder", "McpAgentContextBinder"));
        assertThat(fieldTypes).doesNotContain("Map", "ConcurrentHashMap", "AsyncJobStore", "IdempotencyStore");
    }

    @Test
    void toolDescriptionsHideInternalTopology() {
        var text = Arrays.stream(BankingMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> method.getAnnotation(McpTool.class).description().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
        assertThat(text).doesNotContain("transactionrepository", "customerprofilerepository", "/api/banking");
        assertThat(text).contains("domain apis");
    }

    @Test
    void enterpriseApisRemainCallableWithoutMcp() {
        AgentContextHolder.set(new AgentContext("boundary-session", "user-123", "ENTERPRISE_API", IdentityType.USER_DELEGATED));
        var profile = new CustomerProfileService(new CustomerProfileRepository()).getProfile("user-123");
        assertThat(profile.status()).isEqualTo(SemanticStatus.SUCCESS);
        assertThat(profile.displayName()).isEqualTo("Alex Rivera");

        var mapping = EnterpriseBankingController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/api/banking");
    }

    @Test
    void oneToolOrchestratesMultipleEnterpriseApis() {
        var harness = EvalHarness.create();
        var scenario = GoldenScenarios.reportExplicitAsync();
        var trace = harness.run(scenario);
        var result = trace.stream()
                .filter(call -> "getJobResult".equals(call.toolName()))
                .findFirst()
                .orElseThrow();
        assertThat(result.status()).isEqualTo(SemanticStatus.SUCCESS);
        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) ((com.enterprise.agentapi.domain.JobResponse) result.result()).result();
        assertThat(payload.get("composedFrom")).isEqualTo(AgentBoundary.ENTERPRISE_APIS);
        assertThat(payload).containsKeys("customer", "merchantSummaries", "cancelledMerchants");
    }

    @Test
    void toolContextDoesNotSurviveTheMcpCall() {
        AgentContextHolder.set(new AgentContext("leak-session", "user-123", "MCP", IdentityType.USER_DELEGATED));
        var binder = new com.enterprise.agentapi.mcp.McpAgentContextBinder(
                new com.enterprise.agentapi.agent.ServiceCredentialRegistry());
        binder.bind("boundary-clear", "user-123", null);
        assertThat(AgentContextHolder.get()).isNotNull();
        binder.clear();
        assertThat(AgentContextHolder.get()).isNull();
    }
}
