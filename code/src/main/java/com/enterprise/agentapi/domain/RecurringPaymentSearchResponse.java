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
        int resultCount,
        boolean truncated,
        int totalMatching,
        String nextCursor,
        OperationRetry retry
) {
    public static RecurringPaymentSearchResponse empty(
            SemanticStatus status,
            String message,
            String requestedCategory,
            List<String> knownCategories,
            List<String> suggestions,
            OperationRetry retry) {
        return new RecurringPaymentSearchResponse(
                status, message, requestedCategory, knownCategories, suggestions,
                null, null, null, List.of(), List.of(), BigDecimal.ZERO, 0,
                false, 0, null, retry);
    }
}
