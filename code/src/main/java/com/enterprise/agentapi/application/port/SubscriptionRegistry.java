package com.enterprise.agentapi.application.port;

import java.util.List;

public interface SubscriptionRegistry {
    void cancel(String userId, String merchant);

    boolean isCancelled(String userId, String merchant);

    List<String> cancelledMerchants(String userId);
}
