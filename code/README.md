# Enterprise Agent API

API de empresa lista para agentes (Spring Boot **4.1.1** + Spring AI **2.0** + MCP).

Implementación de referencia de *Designing Enterprise APIs for the Age of AI Agents* (V2), portada desde [gustavosotocano/api_llm](https://github.com/gustavosotocano/api_llm).

> El agente interpreta intención y pide acciones. El backend sigue siendo la autoridad de negocio.

```text
User → Agent / LLM runtime → Agent-facing layer (MCP tools)
                           → Enterprise / domain APIs
                           → Business systems (source of truth)
```

## Qué implementa

| Patrón V2 | Dónde |
|---|---|
| Capa agent-facing + MCP | `POST /api/mcp` — 6 tools, 7 resources, 4 prompts |
| Contratos tipados y estados semánticos | `SemanticStatus` |
| Identidad delegada + autorización | `IdentityGuard` — un usuario no ve recursos de otro |
| Confirmación humana en writes | `OPERATION_REQUIRES_CONFIRMATION` + `confirmationToken` |
| Idempotencia | `Idempotency-Key` / `IdempotencyStore` |
| Catálogo gobernado | `proposeCatalogChange` + REST humana |
| Observabilidad de 3 capas | logger `AGENT_AUDIT`: TECHNICAL / AI / BUSINESS |
| Rate limit ≠ presupuesto | `RATE_LIMITED` / `AGENT_LOOP_DETECTED` vs `BUDGET_EXCEEDED` |
| Jobs asíncronos explícitos | `startCustomerReport` → `getJobStatus` → `getJobResult` |
| Contexto no confiable + procedencia | resources MCP con `provenance.trust_level` |
| Versionado de contrato de tools | `toolVersion=2.0.0` en auditoría |

## Requisitos

- Java 21
- Maven
- Ollama (solo para `/ai/chat`)

```bash
ollama pull llama3.2:latest
ollama serve
```

## Arranque

```bash
mvn test
mvn spring-boot:run
```

## Debug API (sin LLM)

```bash
# Búsqueda de lecturas
curl -X POST http://localhost:8080/debug/transactions/search-recurring \
  -H 'Content-Type: application/json' \
  -H 'X-Agent-Session-Id: agent-demo-001' \
  -d '{"userId":"user-123","category":"STREAMING","period":"LAST_3_MONTHS","limit":100}'

# Cancelación paso 1 — confirmación
curl -X POST http://localhost:8080/debug/subscriptions/cancel \
  -H 'Content-Type: application/json' \
  -H 'X-Agent-Session-Id: agent-demo-001' \
  -H 'Idempotency-Key: demo-netflix-cancel-1' \
  -d '{"userId":"user-123","merchant":"NETFLIX"}'

# Cancelación paso 2 — ejecutar
curl -X POST http://localhost:8080/debug/subscriptions/cancel \
  -H 'Content-Type: application/json' \
  -H 'X-Agent-Session-Id: agent-demo-001' \
  -H 'Idempotency-Key: demo-netflix-cancel-1' \
  -H 'X-Confirmation-Token: confirm-<token-del-paso-1>' \
  -d '{"userId":"user-123","merchant":"NETFLIX"}'

# Job asíncrono
curl -X POST 'http://localhost:8080/debug/jobs/reports?userId=user-123&period=LAST_3_MONTHS' \
  -H 'X-Agent-Session-Id: agent-demo-001'
```

## Chat (Ollama → MCP bridge)

`/ai/chat` no llama beans `@Tool` locales. Usa el mismo servidor MCP que Cursor (`/api/mcp`).

```bash
curl -X POST http://localhost:8080/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "user-123",
    "agentSessionId": "agent-demo-001",
    "message": "Show me my recurring streaming payments from the last 3 months"
  }'
```

## Gobernanza de catálogo

Los agentes **proponen**. Un humano **aprueba**.

```bash
curl "http://localhost:8080/debug/governance/catalog/proposals?status=PENDING_REVIEW"

curl -X POST "http://localhost:8080/debug/governance/catalog/proposals/<proposalId>/approve" \
  -H "X-Governance-Reviewer: catalog-admin" \
  -H "X-Governance-Approval-Token: <approvalToken>"
```

## Arquitectura

```text
User → /ai/chat → Ollama → MCP Client → /api/mcp → BankingMcpTools
                                              ↘
User/Cursor → /api/mcp (directo) ──────────────┘
                        ↓
              BankingToolOperations → Services → Stores (in-memory)
```

## Lección clave

Si un valor se puede calcular de forma determinista en el backend, no se lo pidas al LLM. Envía intención semántica (`LAST_3_MONTHS`) y deja que el backend posea fechas, catálogos, presupuestos y efectos de lado.
