package com.enterprise.agentapi.domain;

public record RecurringPaymentSearchRequest(
        String userId,
        String category,
        String merchant,
        PeriodOption period,
        Integer limit,
        String cursor
) {
    public RecurringPaymentSearchRequest(
            String userId, String category, String merchant, PeriodOption period, Integer limit) {
        this(userId, category, merchant, period, limit, null);
    }
}
