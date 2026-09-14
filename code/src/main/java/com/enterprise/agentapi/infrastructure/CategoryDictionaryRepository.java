package com.enterprise.agentapi.infrastructure;

import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class CategoryDictionaryRepository {
    private final ConcurrentHashMap<String, Set<String>> categoryToMerchants = new ConcurrentHashMap<>();

    public CategoryDictionaryRepository() {
        applyCategory("STREAMING", Set.of("NETFLIX", "SPOTIFY", "PRIME_VIDEO", "DISNEY_PLUS"));
        applyCategory("TELECOMMUNICATIONS", Set.of("CLARO", "MOVISTAR", "TIGO"));
    }

    public boolean exists(String category) {
        return category != null && categoryToMerchants.containsKey(normalize(category));
    }

    public Set<String> merchantsFor(String category) {
        return Set.copyOf(categoryToMerchants.getOrDefault(normalize(category), Set.of()));
    }

    public List<String> knownCategories() {
        return categoryToMerchants.keySet().stream().sorted().toList();
    }

    public synchronized void applyCategory(String category, Set<String> merchants) {
        var code = normalize(category);
        categoryToMerchants.put(code, new LinkedHashSet<>(normalizeMerchants(merchants)));
    }

    public synchronized void addMerchants(String category, Set<String> merchants) {
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
