package com.enterprise.agentapi.domain;

import java.math.BigDecimal;
import java.util.List;

public record MerchantSummary(
        String merchantName,
        String normalizedMerchant,
        int occurrenceCount,
        BigDecimal totalAmount,
        List<PaymentOccurrence> occurrences
) {}
