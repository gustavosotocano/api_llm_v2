package com.enterprise.agentapi.mcp;

import com.enterprise.agentapi.ai.BankingToolOperations;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class BankingMcpTools {
    private final BankingToolOperations operations;
    private final McpJsonEncoder jsonEncoder;

    public BankingMcpTools(BankingToolOperations operations, McpJsonEncoder jsonEncoder) {
        this.operations = operations;
        this.jsonEncoder = jsonEncoder;
    }

    @McpTool(
            name = "searchRecurringPayments",
            description = """
                    Search recurring bank payments by category or merchant.
                    Read-only. For streaming use category STREAMING.
                    For relative periods send period LAST_3_MONTHS (do not send dates).
                    Pass agentSessionId for rate limiting, budget, and audit.
                    """,
            generateOutputSchema = false,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public CallToolResult searchRecurringPayments(
            @McpToolParam(description = "Authenticated user id") String userId,
            @McpToolParam(description = "Category code such as STREAMING") String category,
            @McpToolParam(description = "Optional merchant filter", required = false) String merchant,
            @McpToolParam(description = "Period: LAST_30_DAYS, LAST_3_MONTHS, LAST_6_MONTHS, CURRENT_MONTH, PREVIOUS_MONTH") String period,
            @McpToolParam(description = "Max transactions to return", required = false) Integer limit,
            @McpToolParam(description = "Agent session id for rate limit, budget, and audit", required = false) String agentSessionId,
            McpMeta meta) {
        return invoke(agentSessionId, userId, meta,
                () -> operations.searchRecurringPayments(userId, category, merchant, period, limit));
    }

    @McpTool(
            name = "cancelRecurringSubscription",
            description = """
                    Cancel recurring payments for a merchant. Write operation.
                    Requires idempotencyKey. First call without confirmationToken for human confirmation.
                    Then call again with confirmationToken from the response.
                    Pass agentSessionId for rate limiting, budget, and audit.
                    """,
            generateOutputSchema = false,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true))
    public CallToolResult cancelRecurringSubscription(
            @McpToolParam(description = "Authenticated user id") String userId,
            @McpToolParam(description = "Merchant such as NETFLIX or SPOTIFY") String merchant,
            @McpToolParam(description = "Stable idempotency key for this cancellation attempt") String idempotencyKey,
            @McpToolParam(description = "Confirmation token from OPERATION_REQUIRES_CONFIRMATION", required = false) String confirmationToken,
            @McpToolParam(description = "Agent session id for rate limit, budget, and audit", required = false) String agentSessionId,
            McpMeta meta) {
        return invoke(agentSessionId, userId, meta,
                () -> operations.cancelRecurringSubscription(userId, merchant, idempotencyKey, confirmationToken));
    }

    @McpTool(
            name = "proposeCatalogChange",
            description = """
                    Propose a controlled catalog change for human review. Does NOT apply changes.
                    Use when category is unknown or new merchants are needed.
                    proposalType: NEW_CATEGORY | ADD_MERCHANTS.
                    merchants: comma-separated (HBO_MAX,APPLE_TV).
                    Read banking://governance/catalog-policy first.
                    """,
            generateOutputSchema = false,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public CallToolResult proposeCatalogChange(
            @McpToolParam(description = "NEW_CATEGORY or ADD_MERCHANTS") String proposalType,
            @McpToolParam(description = "Category code") String categoryCode,
            @McpToolParam(description = "Comma-separated merchant codes") String merchants,
            @McpToolParam(description = "Business justification") String reason,
            @McpToolParam(description = "User id") String userId,
            @McpToolParam(description = "Agent session id", required = false) String agentSessionId,
            McpMeta meta) {
        return invoke(agentSessionId, userId, meta,
                () -> operations.proposeCatalogChange(proposalType, categoryCode, merchants, reason, userId, agentSessionId));
    }

    @McpTool(
            name = "startCustomerReport",
            description = """
                    Start a long-running customer report. Do not wait for the result.
                    Returns ACCEPTED with jobId. Then poll getJobStatus and finally getJobResult.
                    """,
            generateOutputSchema = false,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public CallToolResult startCustomerReport(
            @McpToolParam(description = "Authenticated user id") String userId,
            @McpToolParam(description = "Period enum, default LAST_3_MONTHS", required = false) String period,
            @McpToolParam(description = "Agent session id", required = false) String agentSessionId,
            McpMeta meta) {
        return invoke(agentSessionId, userId, meta, () -> operations.startCustomerReport(userId, period));
    }

    @McpTool(
            name = "getJobStatus",
            description = "Poll a previously accepted job. Returns OPERATION_IN_PROGRESS, SUCCESS, or DEPENDENCY_UNAVAILABLE.",
            generateOutputSchema = false,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public CallToolResult getJobStatus(
            @McpToolParam(description = "Job id from startCustomerReport") String jobId,
            @McpToolParam(description = "Authenticated user id", required = false) String userId,
            @McpToolParam(description = "Agent session id", required = false) String agentSessionId,
            McpMeta meta) {
        return invoke(agentSessionId, userId, meta, () -> operations.getJobStatus(jobId));
    }

    @McpTool(
            name = "getJobResult",
            description = "Retrieve the result of a COMPLETED job. If still running, returns OPERATION_IN_PROGRESS.",
            generateOutputSchema = false,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public CallToolResult getJobResult(
            @McpToolParam(description = "Job id from startCustomerReport") String jobId,
            @McpToolParam(description = "Authenticated user id", required = false) String userId,
            @McpToolParam(description = "Agent session id", required = false) String agentSessionId,
            McpMeta meta) {
        return invoke(agentSessionId, userId, meta, () -> operations.getJobResult(jobId));
    }

    private CallToolResult invoke(String agentSessionId, String userId, McpMeta meta, java.util.function.Supplier<Object> action) {
        McpAgentContextBinder.bind(agentSessionId, userId, meta);
        try {
            return CallToolResult.builder().addTextContent(jsonEncoder.encode(action.get())).build();
        } catch (Exception ex) {
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
        } finally {
            McpAgentContextBinder.clear();
        }
    }
}
