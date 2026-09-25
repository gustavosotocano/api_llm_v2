package com.enterprise.agentapi.application;

import com.enterprise.agentapi.domain.CustomerProfileResponse;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.enterprise.CustomerProfileApi;
import com.enterprise.agentapi.infrastructure.CustomerProfileRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerProfileService implements CustomerProfileApi {
    private final CustomerProfileRepository profiles;

    public CustomerProfileService(CustomerProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public CustomerProfileResponse getProfile(String userId) {
        var normalized = userId == null || userId.isBlank() ? "user-123" : userId.trim();
        var denied = IdentityGuard.authorizeUserResource(normalized);
        if (denied != null) {
            return new CustomerProfileResponse(denied, "Current identity cannot read another user's profile.",
                    normalized, null, null, null, List.of("Use the authenticated userId"));
        }
        return profiles.find(normalized)
                .map(record -> new CustomerProfileResponse(
                        SemanticStatus.SUCCESS, "Customer profile.",
                        record.userId(), record.displayName(), record.accountStatus(), record.segment(), List.of()))
                .orElseGet(() -> new CustomerProfileResponse(
                        SemanticStatus.NO_RESULTS_FOUND, "Unknown customer.",
                        normalized, null, null, null, List.of()));
    }
}
