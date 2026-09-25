---
type: "query"
date: "2026-09-25T23:03:13.621356+00:00"
question: "could this project use a hexagonal architecture"
contributor: "graphify"
source_nodes: ["TransactionQueryApi", "TransactionSearchService", "BankingMcpTools", "EnterpriseBankingController", "CustomerProfileApi"]
---

# Q: could this project use a hexagonal architecture

## Answer

Yes. The inbound side is already hexagonal: enterprise APIs (TransactionQueryApi, SubscriptionCommandApi, CatalogChangeApi, CustomerReportApi, CustomerProfileApi) are driving ports implemented by application services. MCP tools and EnterpriseBankingController are driving adapters. Domain records do not import infrastructure. The outbound side is not: application services depend on concrete infrastructure stores (TransactionRepository, CategoryDictionaryRepository, IdempotencyStore, and the rest). Making those stores interfaces owned by the application, implemented in infrastructure, would complete the hexagon. Agent guards (rate, budget, audit, identity) can stay as a driving adapter in front of the ports.

## Source Nodes

- TransactionQueryApi
- TransactionSearchService
- BankingMcpTools
- EnterpriseBankingController
- CustomerProfileApi