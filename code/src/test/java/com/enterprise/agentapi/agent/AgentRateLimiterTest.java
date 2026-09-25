package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.observability.AgentAuditService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRateLimiterTest {

    @Test
    void userLimitAppliesAcrossSessions() {
        var properties = new AgentProperties();
        properties.getRateLimit().setMaxRequestsPerUserWindow(2);
        properties.getRateLimit().setUserWindowSeconds(60);
        properties.getRateLimit().setMaxRequestsPerWindow(20);
        var limiter = new AgentRateLimiter(properties, new AgentAuditService(JsonMapper.builder().build()));

        limiter.checkAllowed("session-a", "searchRecurringPayments", "user-123", "TEST");
        limiter.checkAllowed("session-b", "searchRecurringPayments", "user-123", "TEST");
        assertThatThrownBy(() -> limiter.checkAllowed("session-c", "searchRecurringPayments", "user-123", "TEST"))
                .isInstanceOf(AgentRateLimitExceededException.class)
                .extracting(ex -> ((AgentRateLimitExceededException) ex).scope())
                .isEqualTo(RateLimitScope.USER);
    }
}
