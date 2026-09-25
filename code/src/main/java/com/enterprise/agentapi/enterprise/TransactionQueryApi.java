package com.enterprise.agentapi.enterprise;

import com.enterprise.agentapi.domain.RecurringPaymentSearchRequest;
import com.enterprise.agentapi.domain.RecurringPaymentSearchResponse;

public interface TransactionQueryApi {
    RecurringPaymentSearchResponse searchRecurringPayments(RecurringPaymentSearchRequest request);
}
