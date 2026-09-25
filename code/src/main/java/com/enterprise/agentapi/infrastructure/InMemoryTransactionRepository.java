package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.application.port.TransactionRepository;
import com.enterprise.agentapi.domain.Transaction;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {
    private final List<Transaction> transactions = List.of(
            new Transaction("t-001", "user-123", "acc-001", "NFX.COM", "NETFLIX", "STREAMING", new BigDecimal("39900"), "COP", LocalDate.of(2026, 3, 2)),
            new Transaction("t-002", "user-123", "acc-001", "NFX.COM", "NETFLIX", "STREAMING", new BigDecimal("39900"), "COP", LocalDate.of(2026, 4, 2)),
            new Transaction("t-003", "user-123", "acc-001", "NFX.COM", "NETFLIX", "STREAMING", new BigDecimal("39900"), "COP", LocalDate.of(2026, 5, 2)),
            new Transaction("t-004", "user-123", "acc-001", "AMZN Mktp", "AMAZON", "SHOPPING", new BigDecimal("82000"), "COP", LocalDate.of(2026, 4, 18)),
            new Transaction("t-005", "user-123", "acc-001", "SPOTIFY P073", "SPOTIFY", "STREAMING", new BigDecimal("16900"), "COP", LocalDate.of(2026, 3, 12)),
            new Transaction("t-006", "user-123", "acc-001", "SPOTIFY P073", "SPOTIFY", "STREAMING", new BigDecimal("16900"), "COP", LocalDate.of(2026, 4, 12)),
            new Transaction("t-007", "user-123", "acc-001", "SPOTIFY P073", "SPOTIFY", "STREAMING", new BigDecimal("16900"), "COP", LocalDate.of(2026, 5, 12)),
            new Transaction("t-008", "user-456", "acc-999", "NFX.COM", "NETFLIX", "STREAMING", new BigDecimal("39900"), "COP", LocalDate.of(2026, 5, 2))
    );

    @Override
    public List<Transaction> search(String userId, Set<String> normalizedMerchants, String merchant,
                                    LocalDate from, LocalDate to, int limit) {
        OperationTrace.recordDownstream();
        return transactions.stream()
                .filter(tx -> tx.userId().equals(userId))
                .filter(tx -> !tx.transactionDate().isBefore(from) && !tx.transactionDate().isAfter(to))
                .filter(tx -> merchant == null || merchant.isBlank() || tx.normalizedMerchant().equalsIgnoreCase(merchant))
                .filter(tx -> normalizedMerchants.isEmpty() || normalizedMerchants.contains(tx.normalizedMerchant()))
                .limit(limit)
                .toList();
    }
}
