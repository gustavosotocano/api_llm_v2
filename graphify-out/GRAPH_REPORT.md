# Graph Report - api_llm_v2  (2026-09-25)

## Corpus Check
- 132 files · ~66,169 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 4 file(s) not represented in the graph (top: (none) 3, .log 1)

## Summary
- 1397 nodes · 3974 edges · 64 communities (51 shown, 13 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 395 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Session and boundary tests
- Rate limit exceptions
- Config audit and budget
- Catalog governance
- MCP ownership boundary
- Golden scenario evals
- Retry and operation trace
- Agentic architecture diagram
- Transaction search
- Customer report jobs
- Agent runtime wiring
- Test fixtures
- Read workflow blast radius
- Semantic statuses
- Tool trace and domain APIs
- Chat audit correlation
- Category catalog approval
- Trust and provenance
- Identity scopes
- Agent property groups
- Execution budget tariff
- Service credentials
- Per-user rate limits
- Loop detection
- Retry dispositions
- Rate limit status mapping
- MCP policy resources
- Credential issuance
- Catalog propose path
- Job status API
- MCP prompt class
- MCP resource class
- MCP prompt flows
- Confirmation tokens
- Loop detection settings
- Rate limit windows
- Tool execute entry
- Customer profile
- Clock configuration
- MCP client init
- Context authority
- Job tool business audit
- Tool access policy
- Chat to MCP meta
- Banking reference data
- Eval expectation builder
- Search retry budget
- Budget cost lookup
- Capability scope types
- Budget unit tests
- Rate and retry tests
- Identity property config
- Budget exceeded exception
- Application entrypoint
- MCP JSON encoder
- Semantic periods
- Eval harness execution
- Job and retry states
- Safe failure statuses
- Chat workflow inference
- Agent boundary constants
- Audit token redaction
- Tool contract version

## God Nodes (most connected - your core abstractions)
1. `SemanticStatus` - 87 edges
2. `AgentProperties` - 53 edges
3. `AgentAuditService` - 50 edges
4. `GoldenScenario` - 47 edges
5. `AgentWorkflow` - 45 edges
6. `BankingToolOperations` - 44 edges
7. `JobResponse` - 38 edges
8. `CatalogGovernanceService` - 33 edges
9. `AgentRateLimiter` - 30 edges
10. `BankingMcpTools` - 30 edges

## Surprising Connections (you probably didn't know these)
- `/ai/chat Ollama to MCP Bridge` --references--> `AiChatController`  [INFERRED]
  README.md → code/src/main/java/com/enterprise/agentapi/api/AiChatController.java
- `User-Delegated Identity` --references--> `IdentityType`  [INFERRED]
  code/docs/Designing_Enterprise_APIs_AI_Agents_V2_working.md → code/src/main/java/com/enterprise/agentapi/domain/IdentityType.java
- `V2 Pattern to Implementation Map` --references--> `RATE_LIMITED`  [EXTRACTED]
  README.md → code/src/main/java/com/enterprise/agentapi/domain/SemanticStatus.java
- `V2 Pattern to Implementation Map` --references--> `BUDGET_EXCEEDED`  [EXTRACTED]
  README.md → code/src/main/java/com/enterprise/agentapi/domain/SemanticStatus.java
- `V2 Pattern to Implementation Map` --references--> `RETRY_BUDGET_EXCEEDED`  [EXTRACTED]
  README.md → code/src/main/java/com/enterprise/agentapi/domain/SemanticStatus.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Tool execution guard pipeline: workflow policy, retry budget, rate limit, execution budget, trace accounting** — agenttoolsupport_executewithcallid, toolaccesspolicy_allows, retrybudgetservice_begin, agentratelimiter_checkallowed, executionbudgetservice_consume, operationtrace_noteattempt, retrybudgetservice_observe [EXTRACTED 1.00]
- **ThreadLocal request correlation (agent context, operation trace, tool call id)** — agentcontextholder_agentcontextholder, operationtrace_operationtrace, toolcallids_toolcallids, agenttoolsupport_execute [INFERRED 0.85]
- **Guard exceptions mapped to semantic tool responses** — bankingtooloperations_operationalfailure, code_src_main_java_com_enterprise_agentapi_agent_toolaccessdeniedexception_toolaccessdeniedexception, code_src_main_java_com_enterprise_agentapi_agent_agentratelimitexceededexception_agentratelimitexceededexception, code_src_main_java_com_enterprise_agentapi_agent_budgetexceededexception_budgetexceededexception, code_src_main_java_com_enterprise_agentapi_agent_retrybudgetexceededexception_retrybudgetexceededexception, agentratelimitsupport_semanticstatus [EXTRACTED 1.00]
- **IdentityGuard user-resource authorization across application services** — identityguard_authorizeuserresource, customerprofileservice_getprofile, customerreportjobservice_startreport, customerreportjobservice_status, customerreportjobservice_result, customerreportjobservice_cancel, subscriptioncancellationservice_snapshot, subscriptioncancellationservice_cancel, transactionsearchservice_searchrecurringpayments [EXTRACTED 1.00]
- **Customer report job composes profile, transaction search and subscription snapshot** — customerreportjobservice_runreport, customerprofileservice_getprofile, transactionsearchservice_searchrecurringpayments, subscriptioncancellationservice_snapshot, code_src_main_java_com_enterprise_agentapi_infrastructure_asyncjobstore [INFERRED 0.85]
- **UNKNOWN_CATEGORY search to human-approved catalog change loop** — transactionsearchservice_searchrecurringpayments, cataloggovernanceservice_propose, governancecontroller_approve, cataloggovernanceservice_approve, code_src_main_java_com_enterprise_agentapi_infrastructure_categorydictionaryrepository [INFERRED 0.85]
- **Response records carrying OperationRetry retry** — code_src_main_java_com_enterprise_agentapi_domain_catalogchangeresponse_catalogchangeresponse, code_src_main_java_com_enterprise_agentapi_domain_jobresponse_jobresponse, code_src_main_java_com_enterprise_agentapi_domain_recurringpaymentsearchresponse_recurringpaymentsearchresponse, code_src_main_java_com_enterprise_agentapi_domain_subscriptioncancellationresponse_subscriptioncancellationresponse, operationretry_operationretry [EXTRACTED 1.00]
- **SemanticStatus -> RetryDisposition mapping via OperationRetry.forStatus** — code_src_main_java_com_enterprise_agentapi_domain_semanticstatus_semanticstatus, code_src_main_java_com_enterprise_agentapi_domain_operationretry_forstatus, code_src_main_java_com_enterprise_agentapi_domain_retrydisposition_retrydisposition, operationretry_operationretry [EXTRACTED 1.00]
- **Async job lifecycle (AsyncJob, JobStatus, JobResponse)** — code_src_main_java_com_enterprise_agentapi_domain_asyncjob_asyncjob, asyncjob_withstatus, code_src_main_java_com_enterprise_agentapi_domain_jobstatus_jobstatus, code_src_main_java_com_enterprise_agentapi_domain_jobresponse_jobresponse [INFERRED 0.85]
- **MCP tool invocation: bind context, delegate to BankingToolOperations, wrap provenance** — bankingmcptools_invoke, mcpagentcontextbinder_bind, bankingtooloperations, contextprovenance_toolresult, mcpjsonencoder_encode, mcpagentcontextbinder_clear [EXTRACTED 1.00]
- **Repositories recording downstream calls in OperationTrace** — code_src_main_java_com_enterprise_agentapi_infrastructure_customerprofilerepository, code_src_main_java_com_enterprise_agentapi_infrastructure_transactionrepository, code_src_main_java_com_enterprise_agentapi_infrastructure_categorydictionaryrepository, code_src_main_java_com_enterprise_agentapi_infrastructure_catalogproposalstore, code_src_main_java_com_enterprise_agentapi_infrastructure_subscriptionregistry, operationtrace_recorddownstream [EXTRACTED 1.00]
- **Three-layer agent audit (technical, AI, business)** — code_src_main_java_com_enterprise_agentapi_observability_agentauditservice_technical, code_src_main_java_com_enterprise_agentapi_observability_agentauditservice_ai, code_src_main_java_com_enterprise_agentapi_observability_agentauditservice_business, agentauditservice_record, code_src_main_java_com_enterprise_agentapi_observability_auditlayer_auditlayer, code_src_main_java_com_enterprise_agentapi_observability_agentauditevent_agentauditevent [EXTRACTED 1.00]
- **Three observability layers implementation** — designing_enterprise_apis_ai_agents_v2_working_observability_layers, readme_agent_audit_logger, application_agent_audit_logging, code_src_main_java_com_enterprise_agentapi_observability_agentauditservice_agentauditservice, code_src_main_java_com_enterprise_agentapi_observability_auditlayer_auditlayer, code_src_main_java_com_enterprise_agentapi_observability_agentauditevent_agentauditevent [INFERRED 0.85]
- **Rate limiting separated from cost and retry budgets** — designing_enterprise_apis_ai_agents_v2_working_rate_limiting, designing_enterprise_apis_ai_agents_v2_working_cost_governance, designing_enterprise_apis_ai_agents_v2_working_retry_semantics, readme_rate_limit_vs_budget, application_rate_limit_config, application_execution_budget, application_retry_budget, code_src_main_java_com_enterprise_agentapi_agent_agentratelimiter_agentratelimiter, code_src_main_java_com_enterprise_agentapi_agent_executionbudgetservice_executionbudgetservice, code_src_main_java_com_enterprise_agentapi_agent_retrybudgetservice_retrybudgetservice [INFERRED 0.85]
- **Identity preserved across the agent boundary** — designing_enterprise_apis_ai_agents_v2_working_identity_across_boundary, designing_enterprise_apis_ai_agents_v2_working_user_delegated_identity, designing_enterprise_apis_ai_agents_v2_working_service_identity, readme_service_credential, application_identity_config, code_src_main_java_com_enterprise_agentapi_application_identityguard_identityguard, code_src_main_java_com_enterprise_agentapi_agent_servicecredentialregistry_servicecredentialregistry, code_src_main_java_com_enterprise_agentapi_domain_identitytype_identitytype, code_src_test_java_com_enterprise_agentapi_eval_identityevaltest_identityevaltest [INFERRED 0.85]
- **EvalHarness wires BankingToolOperations with real services and a recording listener** — evalharness_create, code_src_main_java_com_enterprise_agentapi_ai_bankingtooloperations_bankingtooloperations, code_src_main_java_com_enterprise_agentapi_ai_agenttoolsupport_agenttoolsupport, code_src_test_java_com_enterprise_agentapi_eval_recordingtoollistener_recordingtoollistener, code_src_main_java_com_enterprise_agentapi_application_transactionsearchservice_transactionsearchservice, code_src_main_java_com_enterprise_agentapi_application_subscriptioncancellationservice_subscriptioncancellationservice, code_src_main_java_com_enterprise_agentapi_application_cataloggovernanceservice_cataloggovernanceservice, code_src_main_java_com_enterprise_agentapi_application_customerreportjobservice_customerreportjobservice [EXTRACTED 1.00]
- **Golden scenarios replayed through EvalHarness and judged by BehavioralEvaluator** — agentevalsuitetest_goldenscenarioholdsbehavioralproperties, goldenscenarios_all, evalharness_run, recordingtoollistener_invocations, behavioralevaluator_evaluate, code_src_test_java_com_enterprise_agentapi_eval_evalverdict_evalverdict [EXTRACTED 1.00]
- **Eval tests covering identity, context trust, boundary and tool contracts** — code_src_test_java_com_enterprise_agentapi_eval_identityevaltest_identityevaltest, code_src_test_java_com_enterprise_agentapi_eval_contexttrustevaltest_contexttrustevaltest, code_src_test_java_com_enterprise_agentapi_eval_boundaryevaltest_boundaryevaltest, code_src_test_java_com_enterprise_agentapi_eval_toolcontractevaltest_toolcontractevaltest, code_src_test_java_com_enterprise_agentapi_eval_tooldescriptioncontracttest_tooldescriptioncontracttest, code_src_test_java_com_enterprise_agentapi_eval_evalharness_evalharness, code_src_main_java_com_enterprise_agentapi_mcp_bankingmcptools_bankingmcptools [INFERRED 0.85]
- **Semantic status codes returned to agents** — code_docs_picture3_clarification_required, code_docs_picture3_unknown_category, code_docs_picture3_invalid_date_range, code_docs_picture3_insufficient_permissions, code_docs_picture3_operation_requires_confirmation, code_docs_picture3_rate_limited, code_docs_picture3_budget_exceeded, code_docs_picture3_operation_in_progress, code_docs_picture3_dependency_unavailable [EXTRACTED 1.00]
- **Untrusted inputs merged into agent context** — code_docs_picture5_user_input, code_docs_picture5_retrieved_content, code_docs_picture5_tool_mcp_results, code_docs_picture5_agent_context [EXTRACTED 1.00]
- **Three observability layers** — code_docs_picture6_observability, code_docs_picture6_business_observability, code_docs_picture6_agent_observability, code_docs_picture6_technical_observability [EXTRACTED 1.00]

## Communities (64 total, 13 thin omitted)

### Community 0 - "Session and boundary tests"
Cohesion: 0.06
Nodes (45): AgentContext, AgentContextHolder, BoundaryEvalTest.oneToolOrchestratesMultipleEnterpriseApis(), ChatWorkflowResolverTest, BoundaryEvalTest, GoldenScenario, Override, GoldenScenarios (+37 more)

### Community 1 - "Rate limit exceptions"
Cohesion: 0.06
Nodes (27): AgentBoundary.ENTERPRISE_APIS, CatalogChangeResponse.retry, AgentRateLimitExceededException, RateLimitScope, LOOP, SESSION, TOOL, USER (+19 more)

### Community 2 - "Config audit and budget"
Cohesion: 0.06
Nodes (39): agentworkflow, AGENT_AUDIT Log Level, Execution Budget (max-units-per-session, per-tool-cost), enterprise.agent.rate-limit (global, per-user, per-tool), AuditController.audit(), BankingMcpResources.agentAuditTrail(), AgentRateLimiter, ExecutionBudgetService (+31 more)

### Community 3 - "Catalog governance"
Cohesion: 0.05
Nodes (25): Governance Allowed Reviewers (catalog-admin, compliance-officer), CatalogGovernanceService, Override, Override, SubscriptionCancellationService, CatalogChangeProposal, CatalogProposalStatus, APPROVED (+17 more)

### Community 4 - "MCP ownership boundary"
Cohesion: 0.06
Nodes (43): AgentBoundary.MCP_DOES_NOT_OWN, AgentContextHolder.get(), ollama-mcp-bridge MCP Client, enterprise-agent-api-banking MCP Server (SYNC, STREAMABLE, /api/mcp), Ollama llama3.2 Chat Model, BankingToolOperations, BoundaryEvalTest.mcpDoesNotOwnRuntimeConcerns(), BoundaryEvalTest.toolContextDoesNotSurviveTheMcpCall() (+35 more)

### Community 5 - "Golden scenario evals"
Cohesion: 0.07
Nodes (35): AgentEvalSuiteTest.goldenScenarioHoldsBehavioralProperties(), AgentEvalSuiteTest.goldenScenarios(), arraylist, BehavioralEvaluator.confirmationBeforeSideEffect(), BehavioralEvaluator.correctToolSelected(), BehavioralEvaluator.DESTRUCTIVE_TOOLS, BehavioralEvaluator.evaluate(), BehavioralEvaluator.finalOutcomeAccurate() (+27 more)

### Community 6 - "Retry and operation trace"
Cohesion: 0.06
Nodes (23): AgentRateLimiter, Retry Budget (max-retries 3), OperationTrace, State, RetryBudgetService, State, ToolCallIds, AgentToolSupport (+15 more)

### Community 7 - "Agentic architecture diagram"
Cohesion: 0.09
Nodes (51): Agent-facing layer (Tools / MCP), Agent / LLM runtime (Interprets intent), Business systems (Systems of record) - agentic, Agentic flow, Agentic zone, Business systems (Systems of record) - traditional, Enterprise API (Stable contract), Enterprise APIs (Deterministic validation) (+43 more)

### Community 8 - "Transaction search"
Cohesion: 0.09
Nodes (23): BehavioralEvaluator.UNSUPPORTED_PARAMETERS, bigdecimal, Override, TransactionSearchService, DateRange, MerchantSummary, PaymentOccurrence, PeriodOption (+15 more)

### Community 9 - "Customer report jobs"
Cohesion: 0.11
Nodes (16): AsyncJob.withStatus(), CustomerReportJobService, Override, AsyncJob, JobStatus, CANCELLED, COMPLETED, FAILED (+8 more)

### Community 10 - "Agent runtime wiring"
Cohesion: 0.13
Nodes (26): arraydeque, atomicinteger, AgentProperties, AgentRateLimiter, DelegationScopes, ExecutionBudgetService, OperationTrace, CatalogProposalStore (+18 more)

### Community 11 - "Test fixtures"
Cohesion: 0.18
Nodes (15): assertthat, await, clock, AgentContext, AgentContext, AgentContextHolder, AgentContextHolder, IdentityType (+7 more)

### Community 12 - "Read workflow blast radius"
Cohesion: 0.10
Nodes (16): Read-only default blast-radius control (V2 §5), tools.default-workflow READ, Tools, ToolAccessDeniedException, ChatWorkflowResolver, AgentWorkflow, CANCELLATION, FULL (+8 more)

### Community 13 - "Semantic statuses"
Cohesion: 0.09
Nodes (26): SemanticStatus, ACCEPTED, CANCELLED, CATALOG_CHANGE_PENDING_REVIEW, CATALOG_CHANGE_REJECTED, DEPENDENCY_UNAVAILABLE, IDEMPOTENCY_CONFLICT, INSUFFICIENT_PERMISSIONS (+18 more)

### Community 14 - "Tool trace and domain APIs"
Cohesion: 0.11
Nodes (25): AgentToolSupport.trace(), BoundaryEvalTest.enterpriseApisRemainCallableWithoutMcp(), AgentContextHolder.clear(), AgentContextHolder.set(), CustomerProfileApi.getProfile(), SubscriptionCommandApi.snapshot(), TransactionQueryApi.searchRecurringPayments(), SubscriptionRegistry.cancelledMerchants() (+17 more)

### Community 15 - "Chat audit correlation"
Cohesion: 0.16
Nodes (22): AgentAuditService.record(), AgentChatToolContextToMcpMetaConverter.convert(), AgentSessionSupport.bind(), AgentSessionSupport.clear(), AgentSessionSupport.resolveSessionId(), AiChatController.chat(), AuditLayer.AI, AuditLayer.BUSINESS (+14 more)

### Community 16 - "Category catalog approval"
Cohesion: 0.13
Nodes (24): BankingMcpResources.categoryCatalog(), BankingReferenceData.categoryCatalog(), CatalogGovernanceService.applyApprovedChange(), CatalogGovernanceService.approve(), CatalogGovernanceService.isAuthorizedReviewer(), CatalogGovernanceService.reject(), CatalogGovernanceService.response(), CategoryDictionaryRepository.normalize() (+16 more)

### Community 17 - "Trust and provenance"
Cohesion: 0.14
Nodes (10): TrustLevel, AUTHORITATIVE_CATALOG, AUTHORITATIVE_POLICY, INTERNAL_OPERATIONAL, UNTRUSTED_CONTENT, ContextAuthority, ContextProvenance, ResourceProvenance (+2 more)

### Community 18 - "Identity scopes"
Cohesion: 0.10
Nodes (14): Identity Config (credential TTL, default user scopes), Narrow credential scopes (V2 §4), distinct from tool visibility, DelegationScopes, IdentityGuard, CapabilityScope, CATALOG_PROPOSE, GOVERNANCE_REVIEW, JOBS_RUN (+6 more)

### Community 19 - "Agent property groups"
Cohesion: 0.12
Nodes (6): AgentProperties, Confirmation, Governance, RetryBudget, AgentRateLimiterTest, org.springframework.boot.context.properties.ConfigurationProperties

### Community 20 - "Execution budget tariff"
Cohesion: 0.13
Nodes (17): AgentProperties, AgentProperties.Budget, Budget.costOf(), AgentProperties.RetryBudget, AgentToolSupport.executeWithCallId(), AgentToolSupport.notifyListeners(), ExecutionBudgetService.consume(), ExecutionBudgetService.costOf() (+9 more)

### Community 21 - "Service credentials"
Cohesion: 0.21
Nodes (14): capabilityscope, ServiceCredentialRegistry, ToolCallIds, OperationRetry, OperationRetry, collectors, duration, executors (+6 more)

### Community 23 - "Loop detection"
Cohesion: 0.13
Nodes (18): Confirmation Token TTL, Loop Detection (max-same-tool-calls, block-on-loop), AGENT_LOOP_DETECTED, BUDGET_EXCEEDED, OPERATION_REQUIRES_CONFIRMATION, RATE_LIMITED, RETRY_BUDGET_EXCEEDED, Tool Descriptions Are Part of the Behavioral Contract (+10 more)

### Community 24 - "Retry dispositions"
Cohesion: 0.12
Nodes (16): RetryDisposition, ALREADY_COMPLETED, DO_NOT_RETRY, IN_PROGRESS, PERMANENT_FAILURE, RETRY_AFTER, JobStatus.COMPLETED, RetryDisposition.ALREADY_COMPLETED (+8 more)

### Community 25 - "Rate limit status mapping"
Cohesion: 0.17
Nodes (9): agentratelimitsupport, AgentRateLimitSupport, CatalogProposalType, ADD_MERCHANTS, NEW_CATEGORY, CatalogChangeApi, CatalogChangeApi, retrydisposition (+1 more)

### Community 26 - "MCP policy resources"
Cohesion: 0.13
Nodes (11): BankingMcpResources.cancellationPolicy(), BankingMcpResources.catalogGovernancePolicy(), BankingMcpResources.jsonResource(), BankingMcpResources.operationalPolicy(), BankingMcpResources.pendingCatalogProposals(), BankingMcpResources.supportedPeriods(), BankingReferenceData.periodsGuide(), BankingReferenceData.supportedPeriods() (+3 more)

### Community 27 - "Credential issuance"
Cohesion: 0.21
Nodes (5): ServiceCredentialRegistry, ServiceGrant, IdentityDebugController, ServiceCredentialRequest, ServiceCredentialRegistry.issue()

### Community 28 - "Catalog propose path"
Cohesion: 0.19
Nodes (10): AgentContextHolder.require(), CatalogGovernanceService.propose(), CustomerReportApi.startReport(), CatalogProposalStore.findPendingForCategory(), IdempotencyStore.find(), IdempotencyStore.hasDifferentPayload(), IdempotencyStore.save(), CustomerReportJobService.startReport() (+2 more)

### Community 29 - "Job status API"
Cohesion: 0.27
Nodes (13): CustomerReportApi.cancel(), CustomerReportApi.result(), CustomerReportApi.status(), AsyncJobStore.compareAndSet(), AsyncJobStore.find(), CustomerReportJobService.cancel(), CustomerReportJobService.cancelledResponse(), CustomerReportJobService.respond() (+5 more)

### Community 30 - "MCP prompt class"
Cohesion: 0.32
Nodes (7): BankingMcpPrompts, io.modelcontextprotocol.spec.McpSchema.GetPromptResult, mcparg, org.springframework.ai.mcp.annotation.McpPrompt, promptmessage, role, textcontent

### Community 31 - "MCP resource class"
Cohesion: 0.44
Nodes (4): BankingMcpResources, io.modelcontextprotocol.spec.McpSchema.ReadResourceResult, org.springframework.ai.mcp.annotation.McpResource, textresourcecontents

### Community 32 - "MCP prompt flows"
Cohesion: 0.21
Nodes (11): BankingMcpPrompts.cancelSubscriptionFlow(), BankingMcpPrompts.customerReportFlow(), BankingMcpPrompts.promptResult(), BankingMcpPrompts.searchStreamingPayments(), BankingMcpTools.cancelRecurringSubscription(), BankingMcpTools.getJobStatus(), BankingMcpTools.invoke(), BankingMcpTools.searchRecurringPayments() (+3 more)

### Community 33 - "Confirmation tokens"
Cohesion: 0.23
Nodes (12): SubscriptionCommandApi.cancel(), ConfirmationTokenStore.consume(), ConfirmationTokenStore.issue(), SubscriptionRegistry.cancel(), SubscriptionRegistry.isCancelled(), TransactionRepository.search(), ConfirmationTokenStore.PendingConfirmation, ConfirmationTokenStore.purgeExpired() (+4 more)

### Community 35 - "Rate limit windows"
Cohesion: 0.27
Nodes (9): AgentProperties.LoopDetection, AgentProperties.RateLimit, RateLimit.resolveToolLimit(), AgentProperties.ToolLimit, AgentRateLimiter.checkAllowed(), AgentRateLimiter.checkWindow(), AgentRateLimiter.detectLoop(), AgentRateLimiter.recordHit() (+1 more)

### Community 36 - "Tool execute entry"
Cohesion: 0.22
Nodes (8): AgentRateLimitSupport.semanticStatus(), AgentToolSupport.execute(), BankingMcpTools.proposeCatalogChange(), BankingToolOperations.cancelRecurringSubscription(), BankingToolOperations.proposeCatalogChange(), BankingToolOperations.rateLimitedCancel(), BankingToolOperations.rateLimitedCatalogChange(), BankingToolOperations.rateLimitedSearch()

### Community 37 - "Customer profile"
Cohesion: 0.33
Nodes (5): CustomerProfileService, Override, CustomerProfileRepository, CustomerRecord, org.springframework.stereotype.Service

### Community 38 - "Clock configuration"
Cohesion: 0.36
Nodes (6): AgentConfiguration, McpClientBridgeConfiguration, org.springframework.ai.mcp.McpToolNamePrefixGenerator, org.springframework.boot.context.properties.EnableConfigurationProperties, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration

### Community 39 - "MCP client init"
Cohesion: 0.38
Nodes (7): Override, McpClientInitializer, io.modelcontextprotocol.client.McpSyncClient, org.slf4j.Logger, org.springframework.ai.mcp.SyncMcpToolCallbackProvider, org.springframework.boot.context.event.ApplicationReadyEvent, org.springframework.context.ApplicationListener

### Community 40 - "Context authority"
Cohesion: 0.20
Nodes (5): ContextProvenance.resource(), ContextProvenance.toolResult(), ContextProvenance.wrap(), ContextTrustEvalTest.toolResultsAreUntrustedAndCannotAuthorize(), ResourceProvenance.wrap()

### Community 41 - "Job tool business audit"
Cohesion: 0.28
Nodes (7): AgentToolSupport.logBusinessAction(), BankingMcpTools.cancelCustomerReport(), BankingMcpTools.getJobResult(), BankingToolOperations.cancelCustomerReport(), BankingToolOperations.getJobResult(), BankingToolOperations.operationalFailure(), ToolCallIds.current()

### Community 42 - "Tool access policy"
Cohesion: 0.31
Nodes (4): ToolAccessPolicy, ContextTrustEvalTest, DelegationScopes, EvalHarness.create()

### Community 43 - "Chat to MCP meta"
Cohesion: 0.33
Nodes (6): AgentChatToolContextToMcpMetaConverter, Override, hashmap, org.springframework.ai.chat.model.ToolContext, org.springframework.ai.mcp.ToolContextToMcpMetaConverter, org.springframework.stereotype.Component

### Community 46 - "Search retry budget"
Cohesion: 0.25
Nodes (3): BankingToolOperations.searchRecurringPayments(), BankingToolOperations.toolParams(), RetryBudgetExceededException

### Community 48 - "Capability scope types"
Cohesion: 0.38
Nodes (5): agenttoolsupport, arrays, PeriodExpressions, locale, org.junit.jupiter.api.DisplayName

### Community 49 - "Budget unit tests"
Cohesion: 0.38
Nodes (5): assertdoesnotthrow, ExecutionBudgetServiceTest, mock, org.junit.jupiter.api.extension.ExtendWith, org.mockito.junit.jupiter.MockitoExtension

### Community 50 - "Rate and retry tests"
Cohesion: 0.33
Nodes (3): assertthatthrownby, AgentAuditServiceTest, jsonmapper

### Community 53 - "Application entrypoint"
Cohesion: 0.38
Nodes (4): EnterpriseAgentApiApplication, org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration, org.springframework.boot.autoconfigure.SpringBootApplication, springapplication

### Community 55 - "Semantic periods"
Cohesion: 0.33
Nodes (6): PeriodExpressions.looksLikeDateRange(), PeriodExpressions.semanticPeriods(), PeriodExpressions, Raw dates are a distinct semantic failure from unknown enum, SemanticStatus.INVALID_DATE_RANGE, SemanticStatus.INVALID_PERIOD

### Community 56 - "Eval harness execution"
Cohesion: 0.40
Nodes (4): EvalHarness.execute(), EvalHarness.resolve(), EvalHarness.resolveValue(), RecordingToolListener.invocations()

### Community 57 - "Job and retry states"
Cohesion: 0.40
Nodes (5): JobStatus.PENDING, JobStatus.RUNNING, RetryDisposition.IN_PROGRESS, SemanticStatus.ACCEPTED, SemanticStatus.OPERATION_IN_PROGRESS

### Community 58 - "Safe failure statuses"
Cohesion: 0.50
Nodes (4): CLARIFICATION_REQUIRED, OPERATION_IN_PROGRESS, Fail Safely Under Operational Pressure, Explicit Semantic States

## Ambiguous Edges - Review These
- `CapabilityScope` → `IdentityType`  [AMBIGUOUS]
  code/src/main/java/com/enterprise/agentapi/domain/IdentityType.java · relation: conceptually_related_to
- `CapabilityScope` → `TrustLevel`  [AMBIGUOUS]
  code/src/main/java/com/enterprise/agentapi/domain/TrustLevel.java · relation: conceptually_related_to
- `Business observability (Action, actor, outcome, impact)` → `Agent observability (Tool choice, parameters, workflow)`  [AMBIGUOUS]
  code/docs/Picture6.png · relation: conceptually_related_to
- `Agent observability (Tool choice, parameters, workflow)` → `Technical observability (Latency, errors, retries, dependency health)`  [AMBIGUOUS]
  code/docs/Picture6.png · relation: conceptually_related_to

## Knowledge Gaps
- **90 isolated node(s):** `com.enterprise:enterprise-agent-api`, `USER`, `SESSION`, `TOOL`, `LOOP` (+85 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 271 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **13 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `CapabilityScope` and `IdentityType`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `CapabilityScope` and `TrustLevel`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `Business observability (Action, actor, outcome, impact)` and `Agent observability (Tool choice, parameters, workflow)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `Agent observability (Tool choice, parameters, workflow)` and `Technical observability (Latency, errors, retries, dependency health)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `SemanticStatus` connect `Semantic statuses` to `Rate limit exceptions`, `Catalog governance`, `Golden scenario evals`, `Retry and operation trace`, `Transaction search`, `Customer report jobs`, `Agent runtime wiring`, `Test fixtures`, `Category catalog approval`, `Identity scopes`, `Service credentials`, `Loop detection`, `Retry dispositions`, `Rate limit status mapping`, `Customer profile`, `Tool access policy`, `Eval expectation builder`, `Rate and retry tests`, `Semantic periods`, `Job and retry states`, `Safe failure statuses`?**
  _High betweenness centrality (0.133) - this node is a cross-community bridge._
- **Why does `AgentProperties` connect `Agent property groups` to `Config audit and budget`, `Loop detection settings`, `Catalog governance`, `Retry and operation trace`, `Clock configuration`, `Agent runtime wiring`, `Test fixtures`, `Read workflow blast radius`, `Budget cost lookup`, `Budget unit tests`, `Identity property config`, `Service credentials`, `Per-user rate limits`, `Rate limit status mapping`, `Credential issuance`?**
  _High betweenness centrality (0.089) - this node is a cross-community bridge._
- **Why does `AgentAuditService` connect `Config audit and budget` to `Rate limit exceptions`, `Catalog governance`, `Customer profile`, `Retry and operation trace`, `MCP client init`, `Transaction search`, `Customer report jobs`, `Agent runtime wiring`, `Test fixtures`, `Budget unit tests`, `Rate and retry tests`, `Agent property groups`, `Service credentials`, `MCP JSON encoder`, `Rate limit status mapping`, `MCP resource class`?**
  _High betweenness centrality (0.070) - this node is a cross-community bridge._