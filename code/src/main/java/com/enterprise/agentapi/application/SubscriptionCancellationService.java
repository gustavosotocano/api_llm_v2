package com.enterprise.agentapi.application;

import com.enterprise.agentapi.domain.SemanticStatus;
import com.enterprise.agentapi.domain.SubscriptionCancellationRequest;
import com.enterprise.agentapi.domain.SubscriptionCancellationResponse;
import com.enterprise.agentapi.domain.SubscriptionSnapshot;
import com.enterprise.agentapi.enterprise.SubscriptionCommandApi;
import com.enterprise.agentapi.infrastructure.ConfirmationTokenStore;
import com.enterprise.agentapi.infrastructure.IdempotencyStore;
import com.enterprise.agentapi.infrastructure.SubscriptionRegistry;
import com.enterprise.agentapi.infrastructure.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SubscriptionCancellationService implements SubscriptionCommandApi {
    private final IdempotencyStore idempotencyStore;
    private final ConfirmationTokenStore confirmationTokenStore;
    private final SubscriptionRegistry subscriptionRegistry;
    private final TransactionRepository transactionRepository;

    public SubscriptionCancellationService(IdempotencyStore idempotencyStore,
                                           ConfirmationTokenStore confirmationTokenStore,
                                           SubscriptionRegistry subscriptionRegistry,
                                           TransactionRepository transactionRepository) {
        this.idempotencyStore = idempotencyStore;
        this.confirmationTokenStore = confirmationTokenStore;
        this.subscriptionRegistry = subscriptionRegistry;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public SubscriptionSnapshot snapshot(String userId) {
        var normalized = normalizeUserId(userId);
        var denied = IdentityGuard.authorizeUserResource(normalized);
        if (denied != null) {
            return new SubscriptionSnapshot(denied, "Current identity cannot read another user's subscriptions.",
                    normalized, List.of(), List.of("Use the authenticated userId"));
        }
        return new SubscriptionSnapshot(SemanticStatus.SUCCESS, "Subscription snapshot.",
                normalized, subscriptionRegistry.cancelledMerchants(normalized), List.of());
    }

    @Override
    public SubscriptionCancellationResponse cancel(SubscriptionCancellationRequest request) {
        var userId = normalizeUserId(request.userId());
        var denied = IdentityGuard.authorizeUserResource(userId);
        if (denied != null) {
            var message = userId == null
                    ? "userId is required. The backend does not assume an account."
                    : "Current identity cannot cancel another user's subscription.";
            return response(denied, message,
                    userId, request.merchant(), request.idempotencyKey(), null, null, null,
                    List.of("Pass the authenticated userId"));
        }

        var merchant = normalizeMerchant(request.merchant());
        var idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        var fingerprint = userId + "|" + merchant;

        if (idempotencyKey == null) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "idempotencyKey is required for write operations.",
                    userId, merchant, null, null, null, null,
                    List.of("Provide a stable idempotencyKey per cancellation attempt"));
        }

        if (idempotencyStore.hasDifferentPayload(idempotencyKey, fingerprint)) {
            return response(SemanticStatus.IDEMPOTENCY_CONFLICT,
                    "The same Idempotency-Key was already used with different parameters.",
                    userId, merchant, idempotencyKey, null, null, null,
                    List.of("Reuse the original parameters or generate a new idempotencyKey"));
        }

        var cached = idempotencyStore.find(idempotencyKey, SubscriptionCancellationResponse.class);
        if (cached.isPresent()) {
            var previous = cached.get();
            if (previous.status() == SemanticStatus.SUCCESS) {
                return previous;
            }
            if (previous.status() == SemanticStatus.OPERATION_REQUIRES_CONFIRMATION
                    && isBlank(request.confirmationToken())) {
                return previous;
            }
        }

        if (!merchantExistsForUser(userId, merchant)) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Merchant not found for this user. Use a known merchant such as NETFLIX or SPOTIFY.",
                    userId, merchant, idempotencyKey, null, null, null,
                    List.of("NETFLIX", "SPOTIFY"));
        }

        if (isBlank(request.confirmationToken())) {
            var token = confirmationTokenStore.issue(userId, merchant, idempotencyKey);
            var operationId = "op-" + UUID.randomUUID();
            var pending = response(SemanticStatus.OPERATION_REQUIRES_CONFIRMATION,
                    "Cancellation requires explicit human confirmation. Ask the user to confirm, then call again with confirmationToken.",
                    userId, merchant, idempotencyKey, token, operationId, null,
                    List.of(
                            "Tell the user: cancelling recurring payments for " + merchant,
                            "After user confirms, call cancelRecurringSubscription with the same idempotencyKey and confirmationToken=" + token));
            idempotencyStore.save(idempotencyKey, pending, fingerprint);
            return pending;
        }

        var pendingConfirmation = confirmationTokenStore.consume(
                request.confirmationToken(), userId, merchant, idempotencyKey);
        if (pendingConfirmation.isEmpty()) {
            return response(SemanticStatus.CLARIFICATION_REQUIRED,
                    "Invalid or expired confirmationToken. Request confirmation again.",
                    userId, merchant, idempotencyKey, null, null, null,
                    List.of("Call cancelRecurringSubscription without confirmationToken to obtain a new token"));
        }

        if (subscriptionRegistry.isCancelled(userId, merchant)) {
            var alreadyDone = response(SemanticStatus.SUCCESS,
                    "Subscription was already cancelled for this merchant.",
                    userId, merchant, idempotencyKey, null, "op-existing", Instant.now(),
                    List.of());
            idempotencyStore.save(idempotencyKey, alreadyDone, fingerprint);
            return alreadyDone;
        }

        subscriptionRegistry.cancel(userId, merchant);
        var executed = response(SemanticStatus.SUCCESS,
                "Recurring subscription cancelled. Future charges for this merchant will be blocked.",
                userId, merchant, idempotencyKey, null, "op-" + UUID.randomUUID(), Instant.now(),
                List.of());
        idempotencyStore.save(idempotencyKey, executed, fingerprint);
        return executed;
    }

    private boolean merchantExistsForUser(String userId, String merchant) {
        return transactionRepository.search(userId, Set.of(), merchant,
                        java.time.LocalDate.of(2020, 1, 1), java.time.LocalDate.now(), 1)
                .stream()
                .anyMatch(tx -> tx.normalizedMerchant().equalsIgnoreCase(merchant));
    }

    private SubscriptionCancellationResponse response(SemanticStatus status, String message, String userId,
                                                      String merchant, String idempotencyKey, String confirmationToken,
                                                      String operationId, Instant executedAt, List<String> suggestions) {
        return new SubscriptionCancellationResponse(
                status, message, userId, merchant, idempotencyKey, confirmationToken, operationId, executedAt, suggestions);
    }

    private String normalizeUserId(String userId) {
        return isBlank(userId) ? null : userId.trim();
    }

    private String normalizeMerchant(String merchant) {
        return isBlank(merchant) ? null : merchant.trim().toUpperCase();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        return isBlank(idempotencyKey) ? null : idempotencyKey.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
