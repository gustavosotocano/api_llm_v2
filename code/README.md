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
| Capa agent-facing + MCP | `POST /api/mcp` — 7 tools sobre APIs de empresa en `/api/banking` |
| Contratos tipados y estados semánticos | `SemanticStatus` |
| Identidad delegada + autorización | `IdentityGuard` + scopes; `SERVICE` no es cuenta privilegiada |
| Confirmación humana en writes | `OPERATION_REQUIRES_CONFIRMATION` + `confirmationToken` |
| Idempotencia | `Idempotency-Key` / `IdempotencyStore` |
| Catálogo gobernado | `proposeCatalogChange` + REST humana |
| Observabilidad de 3 capas | logger `AGENT_AUDIT`: TECHNICAL / AI / BUSINESS, con `enterpriseRequestId`, duración, llamadas downstream y costo |
| Rate limit ≠ presupuesto | `RATE_LIMITED` / `AGENT_LOOP_DETECTED` vs `BUDGET_EXCEEDED` |
| Reintentos con tope | `retry` + `retryCount`. `RETRY_BUDGET_EXCEEDED` corta la operación |
| Jobs asíncronos explícitos | `startCustomerReport` → `getJobStatus` → `getJobResult`; `cancelCustomerReport` pasa a `CANCELLED` |
| Contexto no confiable + procedencia | resources y tool results con `provenance`; `AgentWorkflow` limita tools |
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
  -H 'X-Agent-Session-Id: agent-demo-001' \
  -H 'Idempotency-Key: demo-report-1'
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
                        ↓ agent-facing (schemas, rate, budget, audit)
              BankingToolOperations
                        ↓ enterprise / domain APIs
              /api/banking  (Customer • Transactions • Subscriptions)
                        ↓
              Business systems (in-memory stores)
```

MCP no guarda memoria de agente, estado de workflow, retries ni orquestación. `startCustomerReport` compone tres APIs de dominio detrás de un solo tool.

## Boundary (capítulo 2)

Las APIs de empresa siguen siendo domain-oriented. El agente no las reemplaza:

```bash
curl http://localhost:8080/api/banking/customers/user-123 \
  -H 'X-Agent-Session-Id: agent-demo-001'
```

## Identidad (capítulo 4)

La identidad sobrevive el límite del agente. Acceder a un tool no implica acceder a todos los recursos detrás de ese tool.

- `USER_DELEGATED` solo ve al usuario autenticado. Confirmar una cancelación no autoriza a otro usuario.
- `SERVICE` no es una cuenta privilegiada implícita. Necesita credencial de corta vida (`POST /debug/identity/service-credentials`) y un `onBehalfOf` explícito.
- El meta MCP `identityType=SERVICE` no eleva privilegios. Solo un `serviceCredential` emitido por el backend.
- Los scopes (`transactions:read`, `subscriptions:write`, …) son independientes del workflow: `FULL` sin `subscriptions:write` no cancela.

## Contexto (capítulo 5)

El backend, no el texto recuperado, es el perímetro de seguridad:

- Cada resource MCP y cada tool result va envuelto en `provenance` (`source_type`, `trust_level`, `may_grant_permission=false`).
- Los resultados de tools son `untrusted_content` por defecto. El `status` semántico es el único campo de plataforma que el agente debe usar para decidir el siguiente paso.
- El workflow por defecto es `READ`. `CANCELLATION`, `GOVERNANCE`, `REPORT` y `FULL` se pasan explícitamente. MCP sin meta queda en `READ`. `/ai/chat` infiere el workflow del mensaje, o usa el campo `workflow` si el caller lo envía.
- Un memo de merchant o un resource no puede saltarse `confirmationToken` ni mutar el catálogo.

## Evals (capítulo 6)

`mvn test` corre tres capas alineadas al documento:

| Capa | Dónde | Qué comprueba |
|---|---|---|
| API / dominio | `application/*Test`, `agent/*Test` | reglas, auth, idempotencia, jobs, presupuesto |
| Contrato de tools | `eval/ToolContractEvalTest`, `eval/ToolDescriptionContractTest` | schemas MCP, hints, estados semánticos, frases del contrato conductual |
| Boundary / capas | `eval/BoundaryEvalTest` | MCP sin memoria/retry, APIs de empresa independientes, un tool orquesta tres APIs |
| Identidad / scopes | `eval/IdentityEvalTest` | SERVICE sin grant, onBehalfOf, scopes, credencial expirada, no auto-elevación |
| Contexto / blast radius | `eval/ContextTrustEvalTest` | procedencia untrusted, workflow READ no escribe, texto no salta confirmación |
| Evals de agente | `eval/AgentEvalSuiteTest` + `GoldenScenarios` | 9 escenarios dorados: tool correcto, args, sin fechas inventadas, confirmación antes del write, errores semánticos, outcome final |

Las propiedades evaluadas son las del documento: wording puede cambiar; el comportamiento no.

Para añadir un escenario, agrega un `GoldenScenario` en `GoldenScenarios` con el `userMessage` (prompt de eval) y los turns que un agente bien comportado debe ejecutar.

Si un valor se puede calcular de forma determinista en el backend, no se lo pidas al LLM. Envía intención semántica (`LAST_3_MONTHS`) y deja que el backend posea fechas, catálogos, presupuestos y efectos de lado.
