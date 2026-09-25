package com.enterprise.agentapi.api;

import com.enterprise.agentapi.agent.AgentProperties;
import com.enterprise.agentapi.agent.ServiceCredentialRegistry;
import com.enterprise.agentapi.agent.ServiceGrant;
import com.enterprise.agentapi.domain.CapabilityScope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/debug/identity")
public class IdentityDebugController {
    private final ServiceCredentialRegistry credentials;
    private final AgentProperties properties;

    public IdentityDebugController(ServiceCredentialRegistry credentials, AgentProperties properties) {
        this.credentials = credentials;
        this.properties = properties;
    }

    @PostMapping("/service-credentials")
    public ServiceGrant issue(@RequestBody ServiceCredentialRequest request) {
        var scopes = request.scopes() == null || request.scopes().isEmpty()
                ? Set.of(CapabilityScope.TRANSACTIONS_READ)
                : request.scopes().stream().map(CapabilityScope::fromValue).collect(Collectors.toSet());
        var allowed = request.allowedUserIds() == null ? Set.<String>of() : Set.copyOf(request.allowedUserIds());
        return credentials.issue(
                request.serviceId(),
                scopes,
                allowed,
                Duration.ofSeconds(properties.getIdentity().getCredentialTtlSeconds()));
    }

    public record ServiceCredentialRequest(String serviceId, List<String> scopes, List<String> allowedUserIds) {}
}
