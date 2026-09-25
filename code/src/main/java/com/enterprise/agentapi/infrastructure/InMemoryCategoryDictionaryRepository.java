package com.enterprise.agentapi.infrastructure;

import com.enterprise.agentapi.agent.OperationTrace;
import com.enterprise.agentapi.application.port.CategoryDictionaryRepository;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryCategoryDictionaryRepository implements CategoryDictionaryRepository {
    private final ConcurrentHashMap<String, Set<String>> categoryToMerchants = new ConcurrentHashMap<>();

    public InMemoryCategoryDictionaryRepository() {
        applyCategory("STREAMING", Set.of("NETFLIX", "SPOTIFY", "PRIME_VIDEO", "DISNEY_PLUS"));
        applyCategory("TELECOMMUNICATIONS", Set.of("CLARO", "MOVISTAR", "TIGO"));
    }

    @Override
    public boolean exists(String category) {
        OperationTrace.recordDownstream();
        return category != null && categoryToMerchants.containsKey(normalize(category));
    }

    @Override
    public Set<String> merchantsFor(String category) {
        OperationTrace.recordDownstream();
        return Set.copyOf(categoryToMerchants.getOrDefault(normalize(category), Set.of()));
    }

    @Override
    public List<String> knownCategories() {
        OperationTrace.recordDownstream();
        return categoryToMerchants.keySet().stream().sorted().toList();
    }

    @Override
    public synchronized void applyCategory(String category, Set<String> merchants) {
        OperationTrace.recordDownstream();
        var code = normalize(category);
        categoryToMerchants.put(code, new LinkedHashSet<>(normalizeMerchants(merchants)));
    }

    @Override
    public synchronized void addMerchants(String category, Set<String> merchants) {
        OperationTrace.recordDownstream();
        var code = normalize(category);
        if (!exists(code)) {
            throw new IllegalArgumentException("Category does not exist: " + code);
        }
        var current = new LinkedHashSet<>(categoryToMerchants.get(code));
        current.addAll(normalizeMerchants(merchants));
        categoryToMerchants.put(code, current);
    }

    private String normalize(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category code is required");
        }
        return category.trim().toUpperCase();
    }

    private Set<String> normalizeMerchants(Set<String> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            throw new IllegalArgumentException("At least one merchant is required");
        }
        return merchants.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(m -> m.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
