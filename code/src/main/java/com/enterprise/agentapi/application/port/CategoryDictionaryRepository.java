package com.enterprise.agentapi.application.port;

import java.util.List;
import java.util.Set;

public interface CategoryDictionaryRepository {
    boolean exists(String category);

    Set<String> merchantsFor(String category);

    List<String> knownCategories();

    void applyCategory(String category, Set<String> merchants);

    void addMerchants(String category, Set<String> merchants);
}
