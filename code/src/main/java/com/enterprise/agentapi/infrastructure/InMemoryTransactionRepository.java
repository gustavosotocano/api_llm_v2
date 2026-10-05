package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.agent.ResourceScope;
import com.enterprise.agentapi.application.port.TransactionRepository;
import com.enterprise.agentapi.domain.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {
    private final List<Transaction> transactions;

    @Autowired
    public InMemoryTransactionRepository(Clock clock) {
        var today = LocalDate.now(clock);
        transactions = List.of(
                charge("t-001", "user-123", "acc-001", "NFX.COM", "NETFLIX", "STREAMING", "39900", today.minusMonths(2)),
                charge("t-002", "user-123", "acc-001", "NFX.COM", "NETFLIX", "STREAMING", "39900", today.minusMonths(1)),
                charge("t-003", "user-123", "acc-001", "NFX.COM", "NETFLIX", "STREAMING", "39900", today.minusDays(3)),
                charge("t-004", "user-123", "acc-001", "AMZN Mktp", "AMAZON", "SHOPPING", "82000", today.minusMonths(1).minusDays(4)),
                charge("t-005", "user-123", "acc-001", "SPOTIFY P073", "SPOTIFY", "STREAMING", "16900", today.minusMonths(2).plusDays(10)),
                charge("t-006", "user-123", "acc-001", "SPOTIFY P073", "SPOTIFY", "STREAMING", "16900", today.minusMonths(1).plusDays(10)),
                charge("t-007", "user-123", "acc-001", "SPOTIFY P073", "SPOTIFY", "STREAMING", "16900", today.minusDays(8)),
                charge("t-008", "user-456", "acc-999", "NFX.COM", "NETFLIX", "STREAMING", "39900", today.minusDays(3))
        );
    }

    public InMemoryTransactionRepository() {
        this(Clock.systemDefaultZone());
    }

    @Override
    public List<Transaction> search(String userId, Set<String> normalizedMerchants, String merchant,
                                    LocalDate from, LocalDate to) {
        OperationTrace.recordDownstream();
        if (!ResourceScope.visibleToCaller(userId)) {
            return List.of();
        }
        return transactions.stream()
                .filter(tx -> tx.userId().equals(userId))
                .filter(tx -> !tx.transactionDate().isBefore(from) && !tx.transactionDate().isAfter(to))
                .filter(tx -> merchant == null || merchant.isBlank() || tx.normalizedMerchant().equalsIgnoreCase(merchant))
                .filter(tx -> normalizedMerchants.isEmpty() || normalizedMerchants.contains(tx.normalizedMerchant()))
                .toList();
    }

    private static Transaction charge(String id, String userId, String accountId, String merchantName,
                                      String normalizedMerchant, String category, String amount, LocalDate date) {
        return new Transaction(id, userId, accountId, merchantName, normalizedMerchant, category,
                new BigDecimal(amount), "COP", date);
    }
}
