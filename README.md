# Enterprise Agent API

Enterprise API ready for agents (Spring Boot **4.1.1** + Spring AI **2.0** + MCP).

Reference implementation of *Designing Enterprise APIs for the Age of AI Agents* (V2), ported from [gustavosotocano/api_llm](https://github.com/gustavosotocano/api_llm).

> The agent interprets intent and requests actions. The backend remains the business authority.

```text
User → Agent / LLM runtime → Agent-facing layer (MCP tools)
                           → Enterprise / domain APIs
                           → Business systems (source of truth)
```

## What it implements

| V2 pattern | Where |
|---|---|
| Agent-facing layer + MCP | `POST /api/mcp` — 7 tools over enterprise APIs at `/api/banking` |
| Typed contracts and semantic statuses | `SemanticStatus` |
| Delegated identity and authorization | `IdentityGuard` + scopes; a `SERVICE` call needs an issued credential |
| Human confirmation on writes | `OPERATION_REQUIRES_CONFIRMATION` + `confirmationToken` |
| Idempotency | `Idempotency-Key` / `IdempotencyStore` |
| Governed catalog | `proposeCatalogChange` + human REST approval |
| Three observability layers | `AGENT_AUDIT` logger: TECHNICAL / AI / BUSINESS, with `enterpriseRequestId`, duration, downstream calls, and cost |
| Rate limit and budget are separate | `RATE_LIMITED` / `AGENT_LOOP_DETECTED` vs `BUDGET_EXCEEDED` |
| Capped retries | `retry` + `retryCount`. `RETRY_BUDGET_EXCEEDED` stops the operation |
| Explicit async jobs | `startCustomerReport` → `getJobStatus` → `getJobResult`; `cancelCustomerReport` moves the job to `CANCELLED` |
| Untrusted context and provenance | MCP resources and tool results carry `provenance`; `AgentWorkflow` limits tools |
| Tool contract versioning | `toolVersion=2.0.0` on audit events |

## Requirements

- Java 21
- Maven
- Ollama (only for `/ai/chat`)

```bash
ollama pull llama3.2:latest
ollama serve
```

## Run

```bash
mvn test
mvn spring-boot:run
```

## Debug API (no LLM)

```bash
# Read-only search
curl -X POST http://localhost:8080/debug/transactions/search-recurring \
  -H 'Content-Type: application/json' \
  -H 'X-Agent-Session-Id: agent-demo-001' \
  -d '{"userId":"user-123","category":"STREAMING","period":"LAST_3_MONTHS","limit":100}'

# Cancellation step 1 — confirmation
curl -X POST http://localhost:8080/debug/subscriptions/cancel \
  -H 'Content-Type: application/json' \
  -H 'X-Agent-Session-Id: agent-demo-001' \
  -H 'Idempotency-Key: demo-netflix-cancel-1' \
  -d '{"userId":"user-123","merchant":"NETFLIX"}'

# Cancellation step 2 — execute
curl -X POST http://localhost:8080/debug/subscriptions/cancel \
  -H 'Content-Type: application/json' \
  -H 'X-Agent-Session-Id: agent-demo-001' \
  -H 'Idempotency-Key: demo-netflix-cancel-1' \
  -H 'X-Confirmation-Token: confirm-<token-from-step-1>' \
  -d '{"userId":"user-123","merchant":"NETFLIX"}'

# Async job
curl -X POST 'http://localhost:8080/debug/jobs/reports?userId=user-123&period=LAST_3_MONTHS' \
  -H 'X-Agent-Session-Id: agent-demo-001' \
  -H 'Idempotency-Key: demo-report-1'
```

## Chat (Ollama → MCP bridge)

`/ai/chat` calls the same MCP server Cursor uses (`/api/mcp`).

```bash
curl -X POST http://localhost:8080/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "user-123",
    "agentSessionId": "agent-demo-001",
    "message": "Show me my recurring streaming payments from the last 3 months"
  }'
```

## Catalog governance

Agents propose. A human approves.

```bash
curl "http://localhost:8080/debug/governance/catalog/proposals?status=PENDING_REVIEW"

curl -X POST "http://localhost:8080/debug/governance/catalog/proposals/<proposalId>/approve" \
  -H "X-Governance-Reviewer: catalog-admin" \
  -H "X-Governance-Approval-Token: <approvalToken>"
```

## Architecture

```text
User → /ai/chat → Ollama → MCP Client → /api/mcp → BankingMcpTools
                                              ↘
User/Cursor → /api/mcp (direct) ───────────────┘
                        ↓ agent-facing (schemas, rate, budget, audit)
              BankingToolOperations
                        ↓ enterprise / domain APIs
              /api/banking  (Customer • Transactions • Subscriptions)
                        ↓
              Business systems (in-memory stores)
```

Memory, workflow state, retries, and orchestration stay outside MCP. `startCustomerReport` composes three domain APIs behind a single tool.

## Boundary (chapter 2)

Enterprise APIs stay domain-oriented:

```bash
curl http://localhost:8080/api/banking/customers/user-123 \
  -H 'X-Agent-Session-Id: agent-demo-001'
```

## Identity (chapter 4)

Identity survives the agent boundary. A tool grant covers only the resources that identity and scopes allow.

- `USER_DELEGATED` sees only the authenticated user. Confirming a cancellation authorizes that user alone.
- `SERVICE` calls need a short-lived credential (`POST /debug/identity/service-credentials`) and an explicit `onBehalfOf`.
- MCP meta `identityType=SERVICE` has no effect. Only a `serviceCredential` issued by the backend grants the service identity.
- Scopes (`transactions:read`, `subscriptions:write`, …) are independent of workflow: `FULL` still needs `subscriptions:write` to cancel.

## Context (chapter 5)

The backend is the security perimeter:

- Every MCP resource and tool result is wrapped in `provenance` (`source_type`, `trust_level`, `may_grant_permission=false`).
- Tool results are `untrusted_content` by default. The semantic `status` is the platform field the agent uses to choose the next step.
- The default workflow is `READ`. `CANCELLATION`, `GOVERNANCE`, `REPORT`, and `FULL` are explicit. MCP calls with no meta stay on `READ`. `/ai/chat` infers the workflow from the message, or uses the `workflow` field when the caller sends it.
- A merchant memo or a resource keeps `confirmationToken` and the catalog under backend control.

## Evals (chapter 6)

`mvn test` runs three layers aligned with the article:

| Layer | Where | What it checks |
|---|---|---|
| API / domain | `application/*Test`, `agent/*Test` | rules, auth, idempotency, jobs, budget |
| Tool contract | `eval/ToolContractEvalTest`, `eval/ToolDescriptionContractTest` | MCP schemas, hints, semantic statuses, behavioral-contract phrases |
| Boundary / layers | `eval/BoundaryEvalTest` | MCP keeps memory and retry outside itself; enterprise APIs stay independent; one tool orchestrates three APIs |
| Identity / scopes | `eval/IdentityEvalTest` | SERVICE without a grant, onBehalfOf, scopes, expired credential, no self-elevation |
| Context / blast radius | `eval/ContextTrustEvalTest` | untrusted provenance, READ workflow stays read-only, retrieved text still requires confirmation |
| Agent evals | `eval/AgentEvalSuiteTest` + `GoldenScenarios` | 9 golden scenarios: correct tool, args, no invented dates, confirmation before the write, semantic errors, final outcome |

The evaluated properties are the ones in the article: wording may change; behavior does not.

To add a scenario, add a `GoldenScenario` in `GoldenScenarios` with the `userMessage` (eval prompt) and the turns a well-behaved agent must execute.

When the backend can compute a value deterministically, keep it off the LLM. Send semantic intent (`LAST_3_MONTHS`) and let the backend own dates, catalogs, budgets, and side effects.
