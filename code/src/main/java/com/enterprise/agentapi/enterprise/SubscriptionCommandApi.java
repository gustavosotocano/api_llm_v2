package com.enterprise.agentapi.enterprise;

import com.enterprise.agentapi.domain.SubscriptionCancellationRequest;
import com.enterprise.agentapi.domain.SubscriptionCancellationResponse;
import com.enterprise.agentapi.domain.SubscriptionSnapshot;

public interface SubscriptionCommandApi {
    SubscriptionCancellationResponse cancel(SubscriptionCancellationRequest request);

    SubscriptionSnapshot snapshot(String userId);
}
