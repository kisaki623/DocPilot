# DocPilot RAG / Memory Productionization Roadmap

## 1. 目标

DocPilot 的核心目标是建设面向企业文档知识库场景的 RAG + 会话记忆平台。RAG 不是附属 demo，而是主链路：系统必须能完成文档上传、解析、切片、索引、检索、回答、引用、拒答、上下文追踪和质量门禁。

目标链路：

```text
文档上传
-> 异步解析
-> 文本清洗
-> 结构化 chunk 切分
-> MySQL chunk / index 状态落库
-> embedding 向量化
-> Qdrant 向量与 payload 写入
-> query embedding / hybrid candidate
-> metadata filter / scope guard
-> score threshold / rerank / no-evidence gate
-> grounded context assembly
-> LLM 生成或拒答
-> citation 引用证据
-> Conversation Trace / Memory Trace
-> eval / smoke artifact 质量门禁
```

当前口径：目标是“生产化知识库 RAG 核心闭环”，不是完整商业 SaaS、线上 SLA、大规模多租户计费、高可用运维或成熟多 Agent 编排系统。

## 2. 当前基础

- 已完成 T001-T007 基础链路：`DocumentChunk`、`ChunkingService`、`EmbeddingProvider`、`VectorStoreClient`、Qdrant adapter、RAG indexing、单文档 retrieval / QA / SSE、Agent `rag_qa_tool` 和离线 eval。
- 已完成 KnowledgeBase 多文档 RAG：KnowledgeBase 管理、跨文档 retrieval、非流式 QA、citations、`documentHitCounts`、Hybrid / Rerank 可选增强和前端观测字段。
- 已完成 Conversation Context / Memory MVP：会话、摘要、长期记忆候选、ACTIVE memory、KnowledgeBase evidence 接入、Context Trace 和前端 `/conversations`。
- 已完成质量门禁基础：离线 RAG eval、cloud quality smoke、RAG real quality smoke、MySQL / Qdrant 一致性检查、权限隔离负向检查和脱敏 artifact。
- 2026-06-27 v2 真实链路 smoke 结论：核心链路 PASS，但 populated KnowledgeBase 无关问题仍返回 nearest evidence，`noEvidenceThreshold` 为 `REVIEW`。

## 3. 当前关键缺口

- no-evidence 不够硬：无关问题仍可能拿最近 chunk 生成带 citation 的答案。
- score / confidence 治理不足：真实 embedding + Qdrant 的 topK score 还没有成为统一 gate。
- grounded citation 仍需加强：回答层必须只使用通过检索门禁的 evidence，不能把低置信 nearest hit 包装成可靠引用。
- chunk 质量还偏长度检查：标题继承、结构边界、重复率、异常 chunk、表格 / 段落元数据仍需继续治理。
- Conversation Memory 与 RAG evidence 需要更清晰分层：长期记忆、短期上下文、知识库证据和 trace 不应互相污染。

## 4. 升级阶段

### v3 no-evidence threshold and grounded refusal

- 统一单文档 RAG、KnowledgeBase RAG、Conversation KB evidence 的 no-evidence 判定。
- 基于 `app.rag.retrieval.min-similarity-threshold` 或等价配置过滤低置信结果。
- QA 层在 no-evidence 时返回安全拒答 / fallback，不调用模型硬编带引用答案。
- `rag-real-quality-smoke.ps1 -Mode run` 的 `noEvidenceThreshold` 必须从 `REVIEW` 变为 `PASS`。

### v4 citation grounding and answer audit

- 回答生成只接收通过 gate 的 evidence。
- QA response / trace 输出可脱敏的 evidence count、documentHitCounts、score summary、fallback reason。
- 离线 eval 增加 forbidden answer leak、citation minimum、multi-document coverage 和 no-evidence precision。

### v5 chunk structure quality

- chunk metadata 增强标题 / section / ordinal / source block 等结构字段，优先不改表结构时通过可复用 metadata 或 parser 输出承接。
- chunk 质量门禁覆盖空白、过短、重复、异常字符、标题覆盖率和长度分布。
- 新 chunk 策略必须能通过 MySQL chunk 检查和 Qdrant payload 一致性检查。

### v6 hybrid / rerank production gate

- 保持 vector retrieval 为主链路，keyword / BM25-like retrieval 作为候选增强。
- RRF / rerank 默认可关闭，真实 rerank provider 必须显式配置，普通测试不依赖外部服务。
- eval 比较 vector-only 与 hybrid / rerank 的 hit、citation、multi-doc coverage 和 no-evidence 指标。

### v7 memory-aware RAG

- 明确区分 `conversationContext`、`userMemory`、`ragEvidence` 和 `contextTrace`。
- RAG evidence 不自动写入长期记忆；长期记忆候选需要用户接受后才进入上下文。
- Conversation Trace 继续记录 `ragTriggered`、`ragRequired`、`evidenceCount`、`documentHitCounts`、`memoryUsed` 和 fallback reason。

## 5. 质量门禁

- 离线默认门禁：`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test`，不能依赖真实 provider 或远程 Qdrant。
- 真实链路门禁：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run`，只在本地 tunnel / backend / frontend 可用且用户允许创建临时 smoke 数据时执行。
- Artifact 规则：只保存脱敏 summary、计数、score summary、门禁状态和安全错误摘要；不保存 `.env`、token、API key、连接串、云地址、文档全文、prompt 或 evidence context。
- 状态规则：核心链路失败为 `FAILED_CORE_FLOW`，权限隔离失败为 `FAILED_SECURITY_GATE`，质量阈值未达标为 `REVIEW`，环境不可达为 `BLOCKED`。

## 6. 对外边界

可以讲：

- 生产化知识库 RAG 核心闭环建设。
- 文档切片、embedding provider 抽象、Qdrant 向量检索、metadata filter、scope guard、citation、no-evidence、Conversation Trace 和质量门禁。
- 真实链路 smoke 能暴露质量问题，而不是只证明接口跑通。

不能硬吹：

- 完整商业 SaaS、线上 SLA、大规模压测、高可用运维、复杂 PDF 智能解析、成熟多 Agent 编排。
- 当前 `REVIEW` 的 populated-KB no-evidence 能力不能写成已可靠通过。
