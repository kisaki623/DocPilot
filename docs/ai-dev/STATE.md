# DocPilot 当前状态

最后更新：2026-06-06

旧状态快照已保留在 `docs/ai-dev/archive/STATE_2026-04-18.md`。本文件只维护当前事实，不追加流水账。

## 1. 项目定位

DocPilot 是 Java Spring Boot + Next.js 的 AI 文档解析与问答工程化项目，覆盖上传、异步解析、文档问答、SSE、Agent 工具链和 Trace 展示。

## 2. 当前已实现能力

- 账号密码登录 / 注册；短信验证码登录保留兼容路径。
- 文件上传、分片上传、MinIO / local 存储模式。
- 文档创建、列表、详情。
- RocketMQ + Outbox 异步解析链路设计与实现，包括 outbox relay、scan job、producer 边界。
- Redisson 分布式锁、消费幂等、解析任务状态机和补偿思路。
- Redis 缓存、问答限流、登录 token / 上传会话等状态管理。
- 普通文档问答与 SSE 流式问答，支持 citations 和问答历史。
- Agent 工具选择、`AgentTask` / `AgentStep` 持久化、前端 Trace 展示。
- Agent 工具链包含文档状态、摘要、问答、旧 RAG showcase 工具和新 RAG QA 工具，能展示 routingReason、matchedKeywords、steps、citations。
- Agent 工具链已新增内部 `ToolSpec` / `ToolSpecRegistry` 元数据底座，并提供最小 ToolCall API 用于列出工具和调用安全子集工具；Agent 主流程已让 status 与新 RAG QA 工具复用 `ToolCallService`，summary / qa legacy 分支仍保持兼容；OpenAI-compatible Function Calling adapter 已有 mock tool_call 闭环。
- 当前 RAG 主线已有持久化 DocumentChunk、ChunkingService、EmbeddingProvider、VectorStoreClient、RagIndexingService、parse success 自动 indexing trigger、RagDocumentRetrievalService、单文档 RAG QA / SSE、KnowledgeBase 多文档 RAG retrieval / 非流式 QA、Agent RAG QA 接入和单文档 / 多文档离线 retrieval quality smoke；旧 Agent RAG showcase 链路仍保留为独立演示路径。
- KnowledgeBase RAG 已补充总结类问题质量治理：Markdown / 文本块合并式 chunking、摘要意图下的跨文档召回多样性、`documentHitCounts`、回答模型 provider / model / call count 观测字段，以及面向资料集总结的 prompt。
- 目标 KnowledgeBase `3` 的文档 `83/84/85/86` 已授权重建索引到稳定 Qdrant collection `docpilot_rag_v2`；chunk / vector 数为 `35/35`、`18/18`、`10/10`、`16/16`，总结资料集检索分布为 `{83:2,84:1,85:1,86:2}`。
- A1 / S 系列真实链路 smoke 已补齐：单文档 RAG、多文档 KnowledgeBase RAG、真实回答模型、MinIO active storage、RocketMQ + Outbox active parse、真实 embedding + Qdrant、ToolCall API 和权限越界失败案例均有记录。

## 3. 当前边界

- 当前默认不是生产级完整向量 RAG。
- fake embedding / in-memory vector store 仍属于测试、离线 eval 和稳定复现边界；真实 embedding + Qdrant 已在 smoke collection 验证，但不代表 rerank、hybrid search、线上治理或固定 SLA。
- Qdrant 已有 adapter、payload mapping、fake server 测试、preflight 参考和真实 tunnel smoke；普通测试不依赖远程 Qdrant，复现真实 Qdrant 仍需通过本地 `.env` 显式配置可用 endpoint / tunnel、`RAG_VECTOR_STORE_PROVIDER=qdrant` 和 `RAG_QDRANT_COLLECTION`。
- 持久化 RAG chunk 与 indexing workflow 已接入 parse success 自动触发；当前触发器为最小异步 service 调用，尚未独立 MQ / Outbox 化。
- Agent RAG QA 已接入新 RAG 查询链路，但仍是最小工具路由，不是复杂 LLM planner。
- ToolSpec / ToolCall API 目前是内部后端底座，Agent 只渐进复用 status 与 `rag_qa_tool` 调用；Function Calling 目前仅支持 OpenAI-compatible tools schema / mock tool_call adapter，不等于已接真实模型、MCP、KnowledgeBase Agent Tool 或完整通用工具编排。
- KnowledgeBase RAG v1 已提供后端管理、跨文档 retrieval、非流式 QA 和离线 eval / smoke；尚未接 SSE、前端页面、Agent / ToolSpec 或 reranker。
- 新 chunking 策略只影响后续 indexing；除已重建的 `83/84/85/86` 外，其他既有文档必须 rebuild / reindex 后，MySQL chunk 和 Qdrant 向量 payload 才会反映新的 chunk 质量。
- Agent 不是多智能体自主规划。
- `llm_execute` / real provider 等能力如果默认关闭，要视为待显式配置和 runtime 验证，不能写成默认生产能力；Function Calling 不能写成生产默认接管。
- 没有线上 SLA，不写 100% 可靠。

## 4. 当前求职优先级

1. 补求职级 RAG 闭环。
2. 保持 Agent Trace 展示。
3. 保持 README 和截图可展示。
4. README / docs 展示口径与最新 smoke 证据保持一致，不继续堆大文件。

## 5. 事实源规则

- 当前任务看 `docs/ai-dev/CURRENT_TASK.md`。
- RAG 总路线看 `docs/ai-dev/ROADMAP_RAG.md`。
- 历史记录看 archive 或旧大文件。
- 不要让旧 TODO 覆盖当前路线。
- 文档和代码冲突时，以代码、测试和可运行结果为准。

## 6. 最近安全加固

- T009 已补充 RAG scope guard：retrieval / QA / Agent `rag_qa_tool` / parse success indexing trigger 均以 userId、documentId、indexVersion 作为最小隔离边界。
- Vector search 仍依赖 metadata filter；service 层新增返回 hit 的二次校验，防止跨用户、跨文档或跨版本 citation 泄露。
