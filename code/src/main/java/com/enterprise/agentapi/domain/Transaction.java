package com.enterprise.agentapi.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(
        String id,
        String userId,
        String accountId,
        String merchantName,
        String normalizedMerchant,
        String category,
        BigDecimal amount,
        String currency,
        LocalDate transactionDate
) {}
