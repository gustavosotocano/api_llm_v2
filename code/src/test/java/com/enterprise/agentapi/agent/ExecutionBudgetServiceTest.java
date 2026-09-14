package com.enterprise.agentapi.agent;

import com.enterprise.agentapi.observability.AgentAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class ExecutionBudgetServiceTest {

    @Mock
    private AgentAuditService auditService;

    @Test
    void blocksWhenSessionBudgetIsExceeded() {
        var properties = new AgentProperties();
        properties.getBudget().setMaxUnitsPerSession(4);
        var budget = new ExecutionBudgetService(properties, auditService);

        assertDoesNotThrow(() -> budget.consume("s1", "searchRecurringPayments", "user-123", "TEST"));
        assertDoesNotThrow(() -> budget.consume("s1", "searchRecurringPayments", "user-123", "TEST"));
        assertThatThrownBy(() -> budget.consume("s1", "searchRecurringPayments", "user-123", "TEST"))
                .isInstanceOf(BudgetExceededException.class);
    }
}
