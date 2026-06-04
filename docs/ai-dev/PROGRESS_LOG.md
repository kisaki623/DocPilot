# Progress Log

## 2026-06-04 T010c

- T010c 现有 Agent 工具迁移已完成：`DocumentAgentServiceImpl` 的 `document_status_tool` 与 `rag_qa_tool` 调用改为复用 T010b `ToolCallService` / `ToolCallResult`。
- Summary / QA legacy 分支和旧 `document_rag_tool` showcase 链路保持原执行路径；`DocumentToolSelector` 决策逻辑未做大改。
- RAG ToolCallResult 继续进入 Agent response / trace，包含 retrieval hits、citations、no-evidence/fallback 摘要；普通工具失败记录 FAILED step 和安全 fallback，权限 / 文档归属错误不被 fallback 掩盖。
- 已验证：`mvn "-Dtest=DocumentAgentServiceImplTest,DocumentAgentLlmExecuteModeTest,DocumentAgentRealShadowPathTest,ToolCallServiceImplTest,DocumentRagQaToolTest,DocumentToolSelectorTest" test`、`mvn "-Dtest=*Agent*,*ToolSelector*,*ToolSelection*,*Rag*" test`、`mvn test`（599 tests，0 failures，0 errors，1 skipped）。
- 下一步待确认：T010d OpenAI-compatible Function Calling adapter；不做 MCP、不做多文档 RAG、不改前端、不改根 README。

## 2026-06-04 T010b

- T010b ToolCall API + 参数校验 + ToolCallResult 已完成：新增 `GET /api/agent/tools` 和 `POST /api/agent/tools/call`，基于 T010a `ToolSpecRegistry` 暴露可见工具并调用安全子集工具。
- 新增 `ToolCallService`、`ToolArgumentValidator` 和 `ToolInputMapper`；ToolCall API 仅开放 `document_status_tool` 与 `rag_qa_tool`，不迁移现有 `DocumentAgentServiceImpl` 主执行链。
- `ToolCallResult` 扩展 `durationMs`、`citations`、`retrievalHits`；`rag_qa_tool` 继续复用现有 `RagQaService` / `RagScopeGuard` 权限边界。
- 已验证：`mvn "-Dtest=AgentToolControllerTest,ToolCallServiceImplTest,ToolArgumentValidatorTest,ToolCallResultTest,ToolSpecRegistryTest,DefaultToolSpecProviderTest,ToolDefinitionProviderTest" test`、`mvn "-Dtest=*Agent*,*ToolSelector*,*ToolSelection*,*Rag*" test`、`mvn test`（598 tests，0 failures，0 errors，1 skipped）。
- 下一步待确认：T010c 统一 `ToolExecutor` 执行路径，或 T010d OpenAI Function Calling adapter；不做 MCP、不做多文档 RAG、不改前端、不改根 README。

## 2026-06-04 T010a

- T010a ToolSpec / ToolRegistry 已完成：新增内部 `ai.agent.tool.spec` package，包含 `ToolSpec`、参数 / 结果 schema、risk level、`ToolExecutionContext`、`ToolCallResult` 和 `ToolExecutor` contract。
- 新增 `DefaultToolSpecProvider`、`ToolSpecRegistry` 和 `ToolDefinitionAdapter`；`ToolDefinitionProvider` 现在从 spec registry 输出现有 selector 所需 `ToolDefinition`，现有 Agent typed 执行链不迁移。
- 旧 `document_rag_tool` showcase spec 保留但不作为 LLM selectable 暴露；新 `rag_qa_tool` spec 明确基于 `EmbeddingProvider`、`VectorStoreClient` 和 `RagScopeGuard`。
- 已验证：`mvn "-Dtest=ToolSpecRegistryTest,DefaultToolSpecProviderTest,ToolCallResultTest,ToolDefinitionProviderTest" test`、`mvn "-Dtest=*Agent*,*ToolSelector*,*ToolSelection*,*Rag*" test`、`mvn test`（585 tests，0 failures，0 errors，1 skipped）。
- 下一步待确认：T010b OpenAI Function Calling adapter、T010c 统一 ToolExecutor 执行路径，或前端小范围展示 RAG evidence / citations；不做 MCP、不做多文档 RAG、不改前端、不改根 README。

## 2026-06-04 T009

- T009 RAG Scope & Permission Guard 已完成：新增 `RagScopeGuard`，统一 RAG 主链路 document owner 校验，并在 retrieval 返回 hits 后追加 userId / documentId / indexVersion 二次校验，防止跨 scope citation 泄露。
- RAG QA 权限类错误不再被 retrieval fallback 掩盖；`rag_qa_tool` 保持透传权限拒绝；parse success indexing trigger 在执行 indexing 前校验 document scope。
- 已验证：`mvn "-Dtest=RagScopeGuardTest,RagDocumentRetrievalServiceImplTest,RagQaServiceImplTest,DocumentRagQaToolTest,RagIndexingTriggerServiceImplTest" test`、`mvn "-Dtest=*Rag*" test`、`mvn "-Dtest=*Agent*,*ToolSelector*,*ToolSelection*" test`。
- 下一步待确认：前端小范围展示 RAG evidence / citations，或 RAG indexing trigger MQ / Outbox 化；不做多文档 RAG、不改前端、不改根 README、不调用真实外部服务。

## 2026-06-04 T008

- T008 parse success 自动触发 RAG indexing 已完成：在解析任务成功落库后，通过独立 `RagIndexingTriggerService` 异步触发 T004 `RagIndexingService`，形成 parse -> indexing -> retrieval 的后端闭环。
- RAG indexing 失败与 parse success 隔离：trigger 和 parse consumer 都做异常保护，parse task / document 保持 SUCCESS；indexVersion 继续默认使用 1，后续可再演进为 RAG indexing Outbox / MQ。
- 已验证：`mvn "-Dtest=ParseTaskConsumeEntryServiceImplTest,RagIndexingTriggerServiceImplTest,RagDocumentRetrievalQualitySmokeTest" test`、`mvn "-Dtest=*Rag*" test`、`mvn "-Dtest=*ParseTask*" test`、`mvn test`。
- 下一步待确认：RAG indexing trigger MQ / Outbox 化，或前端小范围展示 RAG evidence / citations；不做前端大改、不改根 README、不调用真实外部服务。

## 2026-06-04 T007

- T007 Eval / Retrieval Quality Smoke 已完成：新增基于 T003-T006 新 RAG 主链路的离线 smoke fixture 和测试，覆盖 indexing -> retrieval -> QA citations -> Agent `rag_qa_tool` trace。
- Smoke 指标覆盖 hit@k、citationHitRate、noEvidenceRate 和 userId / documentId / indexVersion metadata isolation；测试只使用 `MockEmbeddingProvider`、`InMemoryVectorStoreClient` 和 mock answer service。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalQualitySmokeTest,DocumentAgentRagQaQualitySmokeTest" test`。后续建议补跑 `*Rag*`、Agent selector 相关测试和 `mvn test` 后再提交。
- 下一步待确认：parse success 自动触发 RAG indexing，或前端小范围展示 RAG evidence / citations；不做前端大改、不改根 README、不调用真实外部服务。

## 2026-06-03 T006

- T006 Agent Integration 已完成：新增 `rag_qa_tool`，将 Agent 的 `rag_tool` 决策接入 T005 `RagQaService`，旧 `DocumentRagTool` showcase 链路保持独立。
- Agent RAG step / response 现在能返回 retrieval hits、RAG citations、no-evidence / fallback 摘要，并在工具异常时记录 FAILED step 和安全错误类型。
- 已验证：`mvn -DskipTests compile`、`mvn "-Dtest=DocumentRagQaToolTest,DocumentToolSelectorTest,DocumentAgentServiceImplTest,DocumentAgentLlmExecuteModeTest,ToolDefinitionProviderTest,LlmToolSelectionPromptBuilderTest,FakeLlmToolSelectionClientTest,FakeLlmToolSelectorTest" test`、`mvn "-Dtest=*Rag*" test`、`mvn "-Dtest=*Agent*,*ToolSelector*,*ToolSelection*,ToolDefinitionProviderTest" test`、`mvn test`。
- 下一步进入 T007 Eval / Retrieval Quality Smoke，不做前端大改、不改根 README、不调用真实外部服务。

## 2026-06-03 T005

- T005 Retrieval + QA + SSE 已完成：新增基于 T003 VectorStoreClient / T004 indexing workflow 的 RagDocumentRetrievalService、RagPromptBuilder、RagQaService 和独立 RAG API / SSE，旧 Agent showcase RAG 链路保持隔离。
- RAG retrieval 强制使用 userId / documentId / indexVersion metadata filter，indexVersion 默认 1，topK 上限 10；no-evidence 和 retrieval-unavailable fallback 不调用大模型，retrieval-only API 不写 QA history。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalServiceImplTest,RagPromptBuilderTest,RagQaServiceImplTest,RagQaControllerTest,*VectorStoreClient*" test`、`mvn "-Dtest=*Rag*" test`、`mvn test`。
- 下一步进入 T006 Agent Integration，不做前端大改、不改根 README、不调用真实外部服务。

## 2026-06-03 T004

- T004 RAG Indexing Workflow 已完成：新增 RagIndexingService service 层闭环，串联 ChunkingService、DocumentChunkService、EmbeddingProvider 和 VectorStoreClient；index / rebuild / retry 在 MVP 阶段统一采用同版本 replace semantics。
- 普通测试不依赖真实 embedding API 或远程 Qdrant；已覆盖成功 indexing、blank skip、默认 indexVersion、metadata payload、embedding 失败不删除旧索引、维度不一致、Qdrant dimension mismatch、upsert 失败标记 FAILED 和 best-effort cleanup。
- 下一步进入 T005 Retrieval + QA + SSE；T004 未新增 Controller、未接 parse success 自动触发、未改前端。

## 2026-06-02 T003b

- T003b 远程 Qdrant 轻量部署和本地 QdrantVectorStoreClient smoke 已完成：通过 SSH tunnel 连接 `http://127.0.0.1:6333`，完成 smoke collection 创建、upsert、metadata filter search、deleteByDocumentId 和清理；下一步进入 T004 RAG Indexing Workflow。

## 2026-06-02 T003a

- T003a Qdrant VectorStore adapter 已完成：新增 VectorStoreClient 抽象、InMemory fallback、Qdrant HTTP adapter、metadata filter、deleteByDocumentId 与本地 stub 测试；不操作远程服务器，不部署 Qdrant。

## 2026-06-02 T002

- T002 EmbeddingProvider 抽象已完成：新增 provider/request/result、deterministic mock、OpenAI-compatible provider、配置适配和兼容层测试。
- `RagEmbeddingProperties` 仍是唯一 Spring `app.rag.embedding` 配置入口；未接 Qdrant，未调用真实外部 embedding API。
- 下一步建议进入 T003 Qdrant VectorStore adapter。

## 2026-06-02 T001b-confirmed

- T001b-confirmed：远程 MySQL docpilot 数据库已创建 tb_document_chunk 表。
- MySQL 容器：docpilot-mysql。
- 表字段覆盖 document_id、user_id、chunk_index、content、content_hash、offset、token_count、index_status、index_version、embedding_model、vector_id、create_time、update_time。
- 索引包含 idx_document_chunk_document_id、idx_document_chunk_user_document、idx_document_chunk_status。
- 唯一约束为 uk_document_version_chunk(document_id, index_version, chunk_index)。
- 未重启服务，未修改其他表。

## 2026-06-02 T001

- T001 RAG 数据模型和 ChunkingService 已完成。
- 新增 DocumentChunkEntity / DocumentChunkMapper / DocumentChunkService / ChunkingService / tb_document_chunk SQL 脚本 / 单元测试。
- 下一步建议进入 T002 EmbeddingProvider 抽象。

## 2026-06-02

- 完成 docs 文档审计和索引整理。
- 将 `docs/README.md` 整理为中文文档地图，明确当前推进优先级、文档分类和大文件读取规则。
- 将根层 RAG、Agent、showcase、archive 文档移动到分类目录，并清理根层重复 stub。
- 明确 RAG 求职级路线：从 fake embedding / in-memory showcase 升级到 embedding provider + Qdrant + chunk 持久化 + citations + SSE + Agent Trace。
- 当前任务切换为 T001 RAG 数据模型和 ChunkingService。
