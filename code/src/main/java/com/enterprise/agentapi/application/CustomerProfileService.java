package com.enterprise.agentapi.application;

import com.enterprise.agentapi.domain.CustomerProfileResponse;
import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.enterprise.CustomerProfileApi;
import com.enterprise.agentapi.application.port.CustomerProfileRepository;
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
        var normalized = userId == null || userId.isBlank() ? null : userId.trim();
        var denied = IdentityGuard.authorizeUserResource(normalized);
        if (denied != null) {
            var message = normalized == null
                    ? "userId is required. The backend does not assume an account."
                    : "Current identity cannot read another user's profile.";
            return new CustomerProfileResponse(denied, message,
                    normalized, null, null, null, List.of("Pass the authenticated userId"));
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
