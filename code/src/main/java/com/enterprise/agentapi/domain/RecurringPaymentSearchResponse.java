package com.enterprise.agentapi.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecurringPaymentSearchResponse(
        SemanticStatus status,
        String message,
        String requestedCategory,
        List<String> knownCategories,
        List<String> suggestions,
        PeriodOption requestedPeriod,
        LocalDate resolvedFromDate,
        LocalDate resolvedToDate,
        List<Transaction> transactions,
        List<MerchantSummary> merchantSummaries,
        BigDecimal totalAmount,
        int resultCount
) {}
