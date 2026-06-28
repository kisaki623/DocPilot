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
- 2026-06-27 v3 真实链路 smoke 结论：默认 `0.50` evidence confidence gate 下整体 PASS，populated KnowledgeBase 无关问题返回 `noEvidence=true`、`0` hits、`0` citations。

## 3. 当前关键缺口

- no-evidence 已有 smoke 级门禁，但 eval 覆盖还不够：仍需更多语义相近、跨主题、跨文档干扰和 hybrid keyword-only case。
- score / confidence 治理已有 `0.50` 默认阈值和 hybrid `vectorScore` 门禁，但不同 embedding 模型 / 语料域仍需要校准记录。
- grounded citation 仍需加强：回答层必须继续只使用通过检索门禁的 evidence，并输出更清晰的 answer audit。
- chunk 质量还偏长度检查：标题继承、结构边界、重复率、异常 chunk、表格 / 段落元数据仍需继续治理。
- Conversation Memory 与 RAG evidence 需要更清晰分层：长期记忆、短期上下文、知识库证据和 trace 不应互相污染。

## 4. 升级阶段

### v3 no-evidence threshold and grounded refusal（DONE）

- 已统一单文档 RAG、KnowledgeBase RAG、Conversation KB evidence 的 no-evidence 判定。
- 已基于 `app.rag.retrieval.min-similarity-threshold` 过滤低置信结果；默认阈值校准为 `0.50`。
- KnowledgeBase hybrid hit 使用原始 `vectorScore` 做门禁，不把 RRF `fusedScore` 当作 similarity；keyword-only 低置信结果不会进入 grounded QA。
- `rag-real-quality-smoke.ps1 -Mode run` 的 `noEvidenceThreshold` 已从 `REVIEW` 变为 `PASS`，marker 为 `docpilot-rag-real-quality-20260627210458-9d0321`。

### v4 citation grounding and answer audit（DONE）

- 回答生成只接收通过 gate 的 evidence。
- QA response 已输出脱敏 answer audit：grounded、evidence count、citation count、documentHitCounts、score summary、fallback reason、retrieval / rerank 信息和 modelCallCount。
- 离线 eval 已增加 `groundedAnswerRate` 和 `noEvidenceCitationFreeRate`，继续保留 forbidden answer leak、citation minimum、multi-document coverage 和 no-evidence precision。

### v5 chunk structure quality（DONE）

- chunk metadata 增强标题 / section / ordinal / source block 等结构字段，优先不改表结构时通过可复用 metadata 或 parser 输出承接。
- chunk 质量门禁覆盖空白、过短、重复、异常字符、标题覆盖率和长度分布。
- 新 chunk 策略必须能通过 MySQL chunk 检查和 Qdrant payload 一致性检查。
- 2026-06-27 已完成小步版本：section title / ordinal / source block ordinal / structure type / quality flags 已进入 chunk candidate、embedding metadata 和 Qdrant payload；真实 smoke `docpilot-rag-real-quality-20260627213040-4038e1` PASS，覆盖 MySQL offset / duplicate hash / token length 和 Qdrant 结构 payload 字段。

### v6 hybrid / rerank production gate（DONE）

- 保持 vector retrieval 为主链路，keyword / BM25-like retrieval 作为候选增强。
- RRF / rerank 默认可关闭，真实 rerank provider 必须显式配置，普通测试不依赖外部服务。
- eval 比较 vector-only 与 hybrid / rerank 的 hit、citation、multi-doc coverage 和 no-evidence 指标。
- 2026-06-27 已完成：KnowledgeBase 离线 eval artifact 输出 `retrievalModeMetrics.vector` / `retrievalModeMetrics.hybrid`，可比较两种 retrieval mode 的核心质量指标；rerank provider 必须外部配置完整才发 HTTP，半配置状态 identity fallback；默认真实 smoke `docpilot-rag-real-quality-20260627214532-e1fb65` PASS。真实 rerank provider 效果仍未强制、未 smoke。

### v7 memory-aware RAG（DONE）

- 明确区分 `conversationContext`、`userMemory`、`ragEvidence` 和 `contextTrace`。
- RAG evidence 不自动写入长期记忆；长期记忆候选需要用户接受后才进入上下文。
- Conversation Trace 继续记录 `ragTriggered`、`ragRequired`、`evidenceCount`、`documentHitCounts`、`memoryUsed` 和 fallback reason。
- 2026-06-27 已完成第一片：`ContextTrace` API 新增计算型 `contextSourceCounts` / `contextSourceFlags`，前端 Trace 面板展示会话摘要、最近消息、长期记忆和 RAG evidence 的拆分计数；不改表结构、不保存 prompt 或 evidence 原文。
- 2026-06-27 已完成第二片：测试门禁覆盖 assistant / RAG evidence 不会自动变成长期记忆，且只有 `ACTIVE` user memory 进入上下文。
- 2026-06-27 已完成第三片：真实 smoke `docpilot-rag-real-quality-20260627220736-8f03b9` PASS，Conversation Trace 同时验证 `evidenceCount=6`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6`。

### v8 eval corpus expansion（DONE）

- 第一片已完成：KnowledgeBase RAG eval corpus 从 5 个 case 扩到 11 个 case，并新增 case 级 `minSimilarityThreshold`，用于在离线 eval 中稳定覆盖 confidence gate。
- 新增覆盖：populated-KB no-evidence、hybrid keyword 噪声、多文档三文档总结、grounded answer 干扰抑制、跨主题 distractor 路由和 out-of-scope semantic distractor。
- 第二片已完成：单文档 RAG smoke case 从 4 个扩到 7 个 case，新增 case 级 confidence gate 和 forbidden marker 检查，覆盖 populated-document no-evidence、grounding citation marker 和 distractor 抑制。
- 2026-06-28 已验证：`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests；真实 smoke `docpilot-rag-real-quality-20260628141419-fb7c21` PASS。
- Phase 2 真实体验审计修复已完成：真实浏览器链路跑通注册、两文档 parse、单文档 RAG、KnowledgeBase API 多文档 RAG、Conversation Trace 和 ACTIVE memory；后续修复了 citation 展示不同步、手动 KB 两文档问法覆盖不稳、Conversation 气泡引用数错误和移动端会话页横向溢出。
- 2026-06-28 真实 smoke `docpilot-rag-real-quality-20260628150434-2b7b39` PASS，KnowledgeBase 两文档 gate 命中分布 `{152:3,153:3}`，no-evidence、Conversation Trace、权限隔离和前端 route smoke 均保持通过。
- Phase 3 小规模真实 rerank provider 验证已完成：`scripts/smoke/rerank-effect-smoke.ps1 -Mode run` 通过两轮真实 cloud quality smoke 对比 hybrid-only 与 hybrid+real-rerank。rerank run 显示 `rerankApplied=true`、rerank score count `6`，KB hit / citation / coveredDocumentCount 与 baseline 持平，no-evidence 和权限隔离无回退。结论是 provider 可用且无回退，但当前满分 fixture 未证明覆盖率 uplift。
- 结论：v8 三阶段已完成；下一阶段进入 DocPilot Quality Loop v2，把 RAG / Memory / Frontend UX 分别做成更接近真实用户体验的持续质量循环。

### Quality Loop v2 / RAG Real QA Eval（DONE）

- 第一片已完成：新增 RAG Real QA Eval v1 离线基线，覆盖事实查找、跨文档总结、比较、多跳式证据、no-evidence、语义干扰、hybrid keyword 噪声和 rerank uplift shaped case。
- 新增指标：`casePassRate`、`answerCorrectnessRate`、`citationGroundingRate`、`noEvidencePrecision`、`multiDocumentCoverageRate`、`forbiddenLeakRate`、`scopeViolationRate`、`rerankUpliftCandidateRate`。
- 当前边界：该基线仍使用 `MockEmbeddingProvider` + `InMemoryVectorStoreClient` 和 synthetic answer，只能证明离线回归门禁，不代表真实 provider / Qdrant / 浏览器体验；artifact 不保存文档原文、query、模型输入、evidence context 或模型输出。
- 第二片已完成：新增 `scripts/smoke/rag-real-qa-eval-smoke.ps1`，使用 `docpilot-rag-real-qa` marker 和 `backend/target/rag-real-qa` artifact root 承接真实链路质量证据；wrapper 复用 cloud quality gate 并已通过 plan / dry-run / 脚本安全测试。
- 第三片已完成：`rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260628164757-ac2a1d`；临时用户、两份临时文档、KnowledgeBase、Conversation Trace、权限隔离和脱敏 artifact 均通过完整 gate。

### Quality Loop v2 / Memory Quality Eval（DONE）

- 目标：把 Conversation Memory 从功能可用推进到质量可解释，验证长期记忆候选、ACTIVE / SUGGESTED / IGNORED 分层、summary / recent messages / user memory / RAG evidence 的 trace 计数，以及 RAG evidence 不污染长期记忆。
- 第一片已完成：补离线 memory quality eval / tests，覆盖用户偏好抽取、项目目标抽取、敏感内容拦截、assistant / RAG evidence 不抽取为 memory、只有 ACTIVE memory 进入 context，以及 trace source counts。2026-06-28 已验证 `mvn "-Dtest=*MemoryQualityEval*,*Memory*,*Context*" test` PASS，48 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，249 tests。
- 第二片已完成：补真实链路 memory smoke，创建临时会话并验证候选抽取、接受 / 忽略状态分层、ACTIVE memory list 隔离，绑定 KnowledgeBase 后 trace 同时包含 `contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6` 和两文档 documentHitCounts。2026-06-28 `memory-quality-smoke.ps1 -Mode run` PASS，marker 为 `docpilot-memory-quality-20260628193150-625bf6`。

### Quality Loop v2 / Frontend UX Audit（DONE）

- 目标：从真实用户视角检查 RAG、Memory、Trace 和 citation 展示效果，而不是只依赖 API gate。
- 第一片已完成：2026-06-28 使用真实 backend / frontend / tunnel 和浏览器创建临时数据，marker 为 `docpilot-frontend-ux-2647184760`。`/conversations` 显示 citation footer `2 条来源`，Trace 面板展示 `userMemory=1` / `ragEvidence=2`，Memory 面板展示 ACTIVE memory；`/knowledge-bases` 页面展示 provider / collection、来源文档分布 `#175:1 / #176:1`、召回片段和 citation 卡片。
- 移动端 `390x844`、`360x780`、`320x740` 检查 `/conversations` 与 `/knowledge-bases` 均无横向溢出；长 ACTIVE memory 未撑破 Memory 抽屉。本片未发现需要改代码的阻断问题。
- 后续 v2.1：继续关注 KnowledgeBase 技术观测字段对普通用户的认知负担，在保留工程审计信息的同时改善默认阅读层级。

## 5. 质量门禁

- 离线默认门禁：`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test`，不能依赖真实 provider 或远程 Qdrant；它用于快速回归，不单独证明真实用户体验。
- 真实链路门禁：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run`。自驱迭代模式下，只要本地 tunnel / backend / frontend 可用，默认允许创建带统一 marker 的临时 smoke 数据并执行；RAG / Memory / 前端体验质量结论优先以该类真实链路证据为准。
- Artifact 规则：只保存脱敏 summary、计数、score summary、门禁状态和安全错误摘要；不保存 `.env`、token、API key、连接串、云地址、文档全文、prompt 或 evidence context。
- 状态规则：核心链路失败为 `FAILED_CORE_FLOW`，权限隔离失败为 `FAILED_SECURITY_GATE`，质量阈值未达标为 `REVIEW`，环境不可达为 `BLOCKED`。

## 6. 对外边界

可以讲：

- 生产化知识库 RAG 核心闭环建设。
- 文档切片、embedding provider 抽象、Qdrant 向量检索、metadata filter、scope guard、citation、no-evidence、Conversation Trace 和质量门禁。
- 真实链路 smoke 能暴露质量问题，而不是只证明接口跑通。

不能硬吹：

- 完整商业 SaaS、线上 SLA、大规模压测、高可用运维、复杂 PDF 智能解析、成熟多 Agent 编排。
- 当前 v3 smoke PASS 不能写成线上 SLA 或跨大规模语料的完整 relevance benchmark。
