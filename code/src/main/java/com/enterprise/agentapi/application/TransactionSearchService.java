package com.enterprise.agentapi.application;

import com.enterprise.agentapi.domain.DateRange;
import com.enterprise.agentapi.domain.MerchantSummary;
import com.enterprise.agentapi.domain.PaymentOccurrence;
import com.enterprise.agentapi.domain.PeriodOption;
import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.RecurringPaymentSearchResponse;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.domain.Transaction;
import com.enterprise.agentapi.enterprise.TransactionQueryApi;
import com.enterprise.agentapi.infrastructure.CategoryDictionaryRepository;
import com.enterprise.agentapi.infrastructure.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TransactionSearchService implements TransactionQueryApi {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 100;

    private final TransactionRepository transactionRepository;
    private final CategoryDictionaryRepository dictionaryRepository;
    private final Clock clock;

    public TransactionSearchService(TransactionRepository transactionRepository,
                                    CategoryDictionaryRepository dictionaryRepository,
                                    Clock clock) {
        this.transactionRepository = transactionRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.clock = clock;
    }

    @Override
    public RecurringPaymentSearchResponse searchRecurringPayments(RecurringPaymentSearchRequest request) {
        var userId = normalizeUserId(request.userId());
        var denied = IdentityGuard.authorizeUserResource(userId);
        if (denied != null) {
            return response(denied, "Current identity cannot access another user's resources.",
                    request.category(), request.period(), null, null,
                    List.of("Use the authenticated userId"), List.of());
        }

        var category = normalizeCategory(request.category());
        var period = request.period() == null ? PeriodOption.LAST_3_MONTHS : request.period();
        var limit = normalizeLimit(request.limit());

        if (category == null && isBlank(request.merchant())) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Please provide a category or merchant.", category, period, null, null,
                    List.of("Use category STREAMING", "Use a specific merchant like NETFLIX"), List.of());
        }

        if (category != null && !dictionaryRepository.exists(category)) {
            return response(SemanticStatus.UNKNOWN_CATEGORY,
                    "Unknown category. The agent cannot add categories directly — use proposeCatalogChange for human review.",
                    category, period, null, null,
                    List.of(
                            "Known categories: " + String.join(", ", dictionaryRepository.knownCategories()),
                            "Call proposeCatalogChange (NEW_CATEGORY) and wait for human approval"),
                    List.of());
        }

        var range = resolveDateRange(period);
        Set<String> merchants = category == null ? Set.of() : dictionaryRepository.merchantsFor(category);
        var transactions = transactionRepository.search(userId, merchants, request.merchant(),
                        range.fromDate(), range.toDate(), limit)
                .stream()
                .sorted(Comparator.comparing(Transaction::normalizedMerchant).thenComparing(Transaction::transactionDate))
                .toList();

        if (transactions.isEmpty()) {
            return response(SemanticStatus.NO_RESULTS_FOUND,
                    "No transactions found for the requested criteria.",
                    category, period, range.fromDate(), range.toDate(), List.of(), transactions);
        }

        return response(SemanticStatus.SUCCESS,
                "Transactions found. Use merchantSummaries as the source of truth.",
                category, period, range.fromDate(), range.toDate(), List.of(), transactions);
    }

    private RecurringPaymentSearchResponse response(SemanticStatus status, String message, String category,
                                                    PeriodOption period, LocalDate fromDate, LocalDate toDate,
                                                    List<String> suggestions, List<Transaction> transactions) {
        var summaries = buildSummaries(transactions);
        var total = transactions.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RecurringPaymentSearchResponse(
                status, message, category, dictionaryRepository.knownCategories(), suggestions,
                period, fromDate, toDate, transactions, summaries, total, transactions.size());
    }

    private List<MerchantSummary> buildSummaries(List<Transaction> transactions) {
        Map<String, List<Transaction>> grouped = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::normalizedMerchant));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    var txs = entry.getValue().stream()
                            .sorted(Comparator.comparing(Transaction::transactionDate))
                            .toList();
                    var total = txs.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    var occurrences = txs.stream()
                            .map(tx -> new PaymentOccurrence(tx.transactionDate(), tx.amount(), tx.currency()))
                            .toList();
                    return new MerchantSummary(
                            txs.getFirst().merchantName(),
                            entry.getKey(),
                            txs.size(),
                            total,
                            occurrences);
                })
                .toList();
    }

    DateRange resolveDateRange(PeriodOption period) {
        var today = LocalDate.now(clock);
        return switch (period) {
            case LAST_30_DAYS -> new DateRange(today.minusDays(30), today);
            case LAST_3_MONTHS -> new DateRange(today.minusMonths(3), today);
            case LAST_6_MONTHS -> new DateRange(today.minusMonths(6), today);
            case CURRENT_MONTH -> new DateRange(today.withDayOfMonth(1), today);
            case PREVIOUS_MONTH -> {
                var start = today.minusMonths(1).withDayOfMonth(1);
                var end = today.withDayOfMonth(1).minusDays(1);
                yield new DateRange(start, end);
            }
        };
    }

    private String normalizeUserId(String userId) {
        return isBlank(userId) ? "user-123" : userId;
    }

    private String normalizeCategory(String category) {
        return isBlank(category) ? null : category.trim().toUpperCase();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
