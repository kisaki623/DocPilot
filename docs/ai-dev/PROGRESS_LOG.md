# Progress Log

## 2026-06-06 KnowledgeBase RAG 质量修复

- 修复“总结资料集”类问题的后端质量瓶颈：chunking 改为合并 Markdown / 文本块后切分，默认窗口调整为 `800/120`，避免大量短泛 chunk。
- KnowledgeBase retrieval 增加候选池扩大和跨文档多样性选择，摘要意图优先覆盖各成员文档，并输出 `documentHitCounts`。
- KnowledgeBase QA 输出 `answerProvider`、`answerModel`、`modelCallCount`，summary prompt 增加整体总结、按文档标题总结和缺失证据说明；前端 API 类型已同步。
- 配置兼容 `RAG_VECTOR_PROVIDER` / `RAG_VECTOR_DIMENSION`；授权后已对 KnowledgeBase `3` 的文档 `83/84/85/86` 执行 rebuild / reindex，写入 collection `docpilot_kb_quality_20260606`。
- Reindex 验证：chunk / vector 数分别为 `35/35`、`18/18`、`10/10`、`16/16`；“总结资料集”检索 hit 数为 `6`，`documentHitCounts={83:2,84:1,85:1,86:2}`。
- 后续已将本地运行 `.env` 切到稳定 collection `docpilot_rag_v2` 并再次 rebuild / reindex；Spring local profile 实际读取到 `qdrant` / `docpilot_rag_v2` / `1024`，四文档 chunk / vector 和检索分布保持一致。临时 collection `docpilot_kb_quality_20260606` 不再作为运行目标。
- 已验证：targeted backend tests 36/36 pass，`mvn "-Dtest=*Rag*" test` 164/164 pass，`mvn -DskipTests compile` pass，`frontend npm run lint` pass。

## 2026-06-06 AGENTS 协作入口修正

- 根 `AGENTS.md` 已同步 `docs/README.md` 的新文档地图：当前事实源改为 `docs/ai-dev/STATE.md`、`docs/ai-dev/CURRENT_TASK.md`、`docs/ai-dev/PROGRESS_LOG.md` 等文件，旧三件套仅保留在 `docs/archive/` 供历史追溯。
- 明确默认开发中间件在云服务器 Docker 中运行，远程 MySQL / Redis / RocketMQ / MinIO / Prometheus / Qdrant 操作必须通过 `hk-ops` 子代理并等待用户授权。

## 2026-06-06 README / showcase 收口

- README / docs 展示口径已统一到 A1 / S 系列真实 smoke 之后的状态：单文档 RAG、多文档 KnowledgeBase RAG、真实回答模型、MinIO active storage、RocketMQ + Outbox active parse、真实 embedding + Qdrant、ToolCall API 和权限越界失败案例均有记录。
- 展示口径采用“更突出成果但保留边界”：可以写真实 embedding + Qdrant smoke、MinIO / MQ active smoke，但不写生产级完整向量 RAG、MCP、多 Agent、线上 SLA 或生产默认 Function Calling。
- 同步更新 `README.md`、`docs/showcase/DEMO_SMOKE_RECORD.md`、`docs/showcase/RESUME_BULLETS.md`、`docs/showcase/PROJECT_INTERVIEW_BRIEF.md`、`docs/ai-dev/STATE.md`、`docs/ai-dev/CURRENT_TASK.md` 和 `docs/README.md`。

## 2026-06-05 T012

- T012 多文档 RAG eval 已完成：新增 KnowledgeBase RAG 离线 fixture、测试侧 eval runner / metrics / result 模型，复用 T011 retrieval / QA service。
- 指标覆盖 `hitAtK`、`documentHitRate`、`citationHitRate`、`noEvidenceRate` 和 `scopeViolationRate`；测试只使用 `MockEmbeddingProvider`、`InMemoryVectorStoreClient` 和 mock answer service。
- Eval artifact 可写入 `backend/target/rag-eval/knowledge-base-rag-eval-latest.json`，不纳入 git，且不保存文档原文、模型输入、evidence context、模型输出或密钥信息。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagEvalFixtureTest" test`。
- 下一步待确认：T013 KnowledgeBase Agent Tool / ToolSpec 接入，或 KnowledgeBase RAG SSE / 前端小范围展示；不做 MCP、不做 reranker、不改前端、不改根 README。

## 2026-06-05 T011

- T011a KnowledgeBase 管理底座已完成：新增 `tb_knowledge_base` / `tb_knowledge_base_document`，实现 KnowledgeBase entity / mapper / service / controller 和 `KnowledgeBaseScopeGuard`。
- 关系表采用 `ACTIVE / REMOVED` 软状态；removeDocument 软删除，addDocuments 可恢复 REMOVED 关系，重复 ACTIVE 添加保持幂等。
- T011b 多文档 RAG 已完成：`VectorSearchRequest` 兼容扩展 documentIds，InMemory / Qdrant filter 支持多文档 IN，新增 KnowledgeBase retrieval / 非流式 QA / prompt builder / citation response。
- 已验证：`mvn "-Dtest=KnowledgeBaseServiceImplTest,KnowledgeBaseScopeGuardTest,KnowledgeBaseControllerTest,KnowledgeBaseSchemaTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagPromptBuilderTest,KnowledgeBaseRagControllerTest,*VectorStoreClient*" test`；`mvn "-Dtest=*Rag*" test`；`mvn "-Dtest=*Agent*,*ToolSelector*,*ToolSelection*,*ToolCall*,OpenAi*" test`；`mvn test`（644 tests，0 failures，0 errors，1 skipped）。
- 下一步待确认：T012 KnowledgeBase Agent Tool / ToolSpec 接入，或前端小范围展示知识库 RAG citations；T011 未做 SSE、Agent / ToolSpec、多文档 eval、前端或根 README。

## 2026-06-04 T010d

- T010d OpenAI-compatible Function Calling adapter 已完成：新增内部 `ai.agent.tool.openai` package，将 `ToolSpec` 转成 OpenAI `type=function` tools schema。
- 新增 tool_call parser 和 tool result adapter；支持解析 mock model response 的 `tool_calls`、调用 T010b `ToolCallService`，并生成 OpenAI-compatible tool message。
- 新增 mock function calling service，覆盖单个 / 多个 tool_calls、invalid JSON、unknown tool / invalid args、tool failed 和失败消息脱敏；不调用真实 OpenAI-compatible provider，不替换现有 Agent 主流程。
- 已验证：`mvn "-Dtest=OpenAiToolSchemaAdapterTest,OpenAiToolCallParserTest,OpenAiToolResultAdapterTest,OpenAiFunctionCallingServiceImplTest,ToolCallServiceImplTest" test`；`mvn "-Dtest=*Agent*,*ToolSelector*,*ToolSelection*,*Rag*,OpenAi*" test`；`mvn test`（611 tests，0 failures，0 errors，1 skipped）。
- 下一步待确认：真实 provider adapter disabled-by-default preflight，或前端小范围展示 RAG evidence / citations；不做 MCP、不做多文档 RAG、不改前端、不改根 README。

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
