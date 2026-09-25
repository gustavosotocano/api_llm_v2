package com.enterprise.agentapi.application.port;

import com.enterprise.agentapi.domain.Transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface TransactionRepository {
    List<Transaction> search(String userId, Set<String> normalizedMerchants, String merchant,
                             LocalDate from, LocalDate to, int limit);
}
