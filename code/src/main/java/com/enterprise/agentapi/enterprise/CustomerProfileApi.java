package com.enterprise.agentapi.enterprise;

import com.enterprise.agentapi.domain.CustomerProfileResponse;

public interface CustomerProfileApi {
    CustomerProfileResponse getProfile(String userId);
}
