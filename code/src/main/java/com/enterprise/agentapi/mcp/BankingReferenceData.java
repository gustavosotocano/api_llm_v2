package com.enterprise.agentapi.mcp;

import com.enterprise.agentapi.domain.PeriodOption;
import com.enterprise.agentapi.infrastructure.CategoryDictionaryRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BankingReferenceData {
    private final CategoryDictionaryRepository categoryDictionary;

    public BankingReferenceData(CategoryDictionaryRepository categoryDictionary) {
        this.categoryDictionary = categoryDictionary;
    }

    public Map<String, Object> categoryCatalog() {
        var categories = new LinkedHashMap<String, List<String>>();
        for (var category : categoryDictionary.knownCategories()) {
            categories.put(category, categoryDictionary.merchantsFor(category).stream().sorted().toList());
        }
        return Map.of(
                "knownCategories", categoryDictionary.knownCategories(),
                "categoryToMerchants", categories,
                "governance", "Catalog is read-only for agents. Changes require proposeCatalogChange + human approval.",
                "usage", "Send category codes exactly as listed. For streaming use STREAMING.");
    }

    public Map<String, Object> catalogGovernancePolicy() {
        return Map.of(
                "principle", "Dictionaries must not be modified by the LLM automatically",
                "agentCapability", "proposeCatalogChange only — creates PENDING_REVIEW proposal",
                "humanCapability", "POST /debug/governance/catalog/proposals/{id}/approve or /reject",
                "requiredHeaders", List.of("X-Governance-Reviewer", "X-Governance-Approval-Token"),
                "allowedReviewers", "Configured in enterprise.agent.governance.allowed-reviewers",
                "proposalTypes", List.of("NEW_CATEGORY", "ADD_MERCHANTS"),
                "workflow", List.of(
                        "1. Agent calls proposeCatalogChange with reason (audit trail).",
                        "2. Response CATALOG_CHANGE_PENDING_REVIEW includes proposalId and approvalToken.",
                        "3. Human reviewer approves via governance REST API (not exposed as MCP tool).",
                        "4. Only after SUCCESS approval may searchRecurringPayments use the new category."));
    }

    public Map<String, Object> cancellationPolicy() {
        return Map.of(
                "operation", "cancelRecurringSubscription",
                "humanInTheLoop", true,
                "steps", List.of(
                        "1. Call cancelRecurringSubscription WITHOUT confirmationToken.",
                        "2. If status is OPERATION_REQUIRES_CONFIRMATION, show confirmationToken to the user and ask explicit confirmation.",
                        "3. Call again with the SAME idempotencyKey and confirmationToken.",
                        "4. Only report success when status is SUCCESS."),
                "idempotency", "Always send a stable idempotencyKey per cancellation attempt.",
                "agentSessionId", "Pass agentSessionId on every tool call for rate limiting and audit.",
                "knownMerchants", List.of("NETFLIX", "SPOTIFY"));
    }

    public Map<String, Object> operationalPolicy() {
        return Map.of(
                "rateLimit", "Frequency of invocation. Status RATE_LIMITED or AGENT_LOOP_DETECTED.",
                "costGovernance", "Expense of a successful execution. Status BUDGET_EXCEEDED.",
                "longRunning", List.of(
                        "startCustomerReport returns ACCEPTED + jobId",
                        "Poll getJobStatus until COMPLETED",
                        "Then call getJobResult"),
                "jobStates", List.of("PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED"));
    }

    public List<String> supportedPeriods() {
        return Arrays.stream(PeriodOption.values()).map(Enum::name).toList();
    }

    public String periodsGuide() {
        return supportedPeriods().stream()
                .map(p -> "- " + p + periodHint(p))
                .collect(Collectors.joining("\n"));
    }

    private String periodHint(String period) {
        return switch (period) {
            case "LAST_3_MONTHS" -> " → use for 'last 3 months' (backend calculates dates)";
            case "LAST_30_DAYS" -> " → use for 'last 30 days'";
            case "LAST_6_MONTHS" -> " → use for 'last 6 months'";
            case "CURRENT_MONTH" -> " → use for current month";
            case "PREVIOUS_MONTH" -> " → use for previous month";
            default -> "";
        };
    }
}
