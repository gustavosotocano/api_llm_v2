package com.enterprise.agentapi.domain;

public record RecurringPaymentSearchRequest(
        String userId,
        String category,
        String merchant,
        PeriodOption period,
        Integer limit
) {}
