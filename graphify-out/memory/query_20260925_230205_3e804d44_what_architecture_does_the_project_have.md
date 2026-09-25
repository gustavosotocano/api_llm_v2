---
type: "query"
date: "2026-09-25T23:02:05.721587+00:00"
question: "what architecture does the project have"
contributor: "graphify"
source_nodes: ["BankingMcpTools", "AgentToolSupport", "BankingToolOperations", "AiChatController", "EnterpriseBankingController", "Agent Boundary", "The Backend Remains the Business Authority"]
---

# Q: what architecture does the project have

## Answer

Layered agentic API. Two entries: /ai/chat (AiChatController, Ollama, MCP client) and direct /api/mcp (BankingMcpTools, 7 tools). Agent-facing guards live in AgentToolSupport (ToolAccessPolicy, RetryBudgetService, AgentRateLimiter, ExecutionBudgetService, AgentAuditService). BankingToolOperations calls domain APIs: TransactionQueryApi, SubscriptionCommandApi, CatalogChangeApi, CustomerReportApi. The same ports are exposed at /api/banking by EnterpriseBankingController. Business systems are in-memory. Memory, workflow, retries, and orchestration stay outside MCP. The backend remains the business authority. Stack: Spring Boot 4.1.1, Spring AI 2.0, MCP server and client, Ollama.

## Source Nodes

- BankingMcpTools
- AgentToolSupport
- BankingToolOperations
- AiChatController
- EnterpriseBankingController
- Agent Boundary
- The Backend Remains the Business Authority