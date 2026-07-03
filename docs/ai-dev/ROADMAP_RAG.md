# DocPilot RAG / Memory Productionization Roadmap

## 2026-07-03 Roadmap Addendum

- R1 smoke runner slice is DONE: `cloud-quality-smoke.ps1 -EnableMultiQueryGate` can now validate request-scoped multi-query retrieval in the real cloud quality flow, and the RAG Real QA wrapper enables it by default while keeping a skip switch.
- Verification so far: wrapper `plan` / `dry-run` PASS and script safety tests PASS. Real-link enabled multi-query smoke is the next required evidence before claiming runtime quality uplift.
- R1 first slice is DONE: KnowledgeBase retrieve and QA APIs now support request-scoped `multiQueryEnabled` / `maxQueryVariants`, with absent fields preserving the default-off global behavior.
- Offline KnowledgeBase RAG eval artifacts now compare `retrievalModeMetrics.vector`, `retrievalModeMetrics.hybrid` and `retrievalModeMetrics.multi_query`, keeping the report redacted and query-text-free.
- Verification: targeted R1 tests PASS, 32 tests; broader `*Rag*,*KnowledgeBase*` regression PASS, 211 tests.
- Remaining R1 work: run a real-link enabled multi-query smoke comparison before claiming user-experience relevance uplift. This slice proves the control plane and offline eval path, not production effectiveness.

## 2026-06-29 Roadmap Addendum

- A6 Query Rewrite / Multi-query Retrieval v1 is DONE behind a default-off flag. KnowledgeBase retrieval can generate bounded deterministic query variants, merge / deduplicate vector hits and continue through the existing confidence, hybrid, rerank, scope and diversity gates; responses expose only counts / booleans, not rewritten query text.
- Real default-path regression smoke after A4-A6 PASS: `rag-real-qa-eval-smoke.ps1 -Mode run` marker `docpilot-rag-real-qa-20260629202542-3e47d9` covered chunk quality, MySQL / Qdrant consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, no-evidence, Conversation Trace, permission isolation, frontend routes and artifact redaction.
- This is a conservative retrieval engineering feature, not LLM query planning or a proven real-provider relevance uplift claim. Multi-query effectiveness still requires explicit enabled smoke / eval evidence.
- A5 Chunk Quality v2 is DONE for the chunking / indexing path: chunks now carry nested `sectionPath`, table / list detection, window / mid-sentence split flags and duplicate content flags; `sectionPath` is propagated to indexing metadata and Qdrant payload consistency checks.
- This improves diagnosis of retrieval failures caused by structural chunking, but existing indexed documents need rebuild / reindex before their stored MySQL chunks and Qdrant payloads reflect the new metadata.
- A4 Retrieval Error Analysis Report v1 is DONE for offline eval artifacts: KnowledgeBase RAG eval and RAG Real QA eval now output redacted error buckets for retrieval miss, wrong retrieval, no-evidence refusal, citation support, answer support, forbidden leakage, scope isolation and ranking candidate pass counts.
- This strengthens ML-system evaluation explainability, but remains an offline gate. Real-link quality claims still require cloud smoke / runtime evidence.

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
- Phase 3 小规模真实 rerank provider 验证已完成，并在 Quality Loop v2.2 补过真实 hard fixture：`scripts/smoke/rerank-effect-smoke.ps1 -Mode run` 先通过两轮真实 cloud quality smoke 对比 hybrid-only 与 hybrid+real-rerank，证明 provider 可调用且核心 gate 无回退；随后 `-EnableRerankHardGate` 用目标 / 支撑 / 干扰三文档场景观察排序差异。2026-06-28 hard run PASS，baseline marker `docpilot-rerank-effect-hybrid-20260628204120-3e9f69`，rerank marker `docpilot-rerank-effect-rerank-20260628204339-7aac45`，target rank `2 -> 1`，distractor rank `3 -> 4`，`hardUpliftObserved=true`。结论是小规模 hard smoke 下观察到排序 uplift，但仍不是大规模 relevance benchmark。
- 结论：v8 三阶段已完成；下一阶段进入 DocPilot Quality Loop v2，把 RAG / Memory / Frontend UX 分别做成更接近真实用户体验的持续质量循环。

### Quality Loop v2 / RAG Real QA Eval（DONE）

- 第一片已完成：新增 RAG Real QA Eval v1 离线基线，覆盖事实查找、跨文档总结、比较、多跳式证据、no-evidence、语义干扰、hybrid keyword 噪声和 rerank uplift shaped case。
- 新增指标：`casePassRate`、`answerCorrectnessRate`、`citationGroundingRate`、`noEvidencePrecision`、`multiDocumentCoverageRate`、`forbiddenLeakRate`、`scopeViolationRate`、`rerankUpliftCandidateRate`。
- 当前边界：该基线仍使用 `MockEmbeddingProvider` + `InMemoryVectorStoreClient` 和 synthetic answer，只能证明离线回归门禁，不代表真实 provider / Qdrant / 浏览器体验；artifact 不保存文档原文、query、模型输入、evidence context 或模型输出。
- 第二片已完成：新增 `scripts/smoke/rag-real-qa-eval-smoke.ps1`，使用 `docpilot-rag-real-qa` marker 和 `backend/target/rag-real-qa` artifact root 承接真实链路质量证据；wrapper 复用 cloud quality gate 并已通过 plan / dry-run / 脚本安全测试。
- 第三片已完成：`rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260628164757-ac2a1d`；临时用户、两份临时文档、KnowledgeBase、Conversation Trace、权限隔离和脱敏 artifact 均通过完整 gate。
- Quality Loop v3.1 第一片已完成：RAG Real Corpus Eval 从 9 个 case 扩到 22 个 case，新增长文档、近义 no-evidence、多文档总结、citation grounding、scope isolation、hybrid keyword noise 和 rerank distractor 场景；metrics 新增 `longDocumentCasePassRate`、`nearMissNoEvidenceRate`、`multiDocSummaryPassRate`、`distractorSuppressionRate`。2026-06-28 targeted eval 9/9 PASS，`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，204 tests。边界仍是脱敏离线门禁，不写成大规模真实 provider benchmark。
- Quality Loop v4.2 已完成 RAG Claim Support / Numeric Faithfulness Eval 第一片：Real QA eval 新增 `claim_support` 与 `numeric_faithfulness` 两类 case，覆盖目标 evidence 支持结论和数字 / 年限忠实度；metrics 新增 `claimSupportPassRate` 与 `numericFaithfulnessPassRate`。2026-06-29 targeted eval 3/3 PASS，Real QA / KnowledgeBase eval 10/10 PASS，`*Rag*,*KnowledgeBase*` 回归 207/207 PASS。该片仍是离线语义支持门禁，不代表真实 provider 大规模 benchmark。
- Quality Loop v5.1 / v5.2 已完成 A3 + A2 的离线门禁增强：Real QA eval corpus 从 `26` 个 case 扩到 `40` 个 case，并新增 test-side `RagClaimSupportScorer`，通过 `expectedClaims` 将关键 claim 绑定到 answer marker / evidence marker / forbidden marker；metrics 新增 claim support scorer 通过率、supported / unsupported claim rate 和 forbidden claim rate。2026-06-29 已验证 targeted Real QA eval 3/3 PASS，Real QA / KnowledgeBase eval 10/10 PASS，`*Rag*,*KnowledgeBase*` 回归 207/207 PASS。该片仍是 synthetic marker contract 下的脱敏离线质量门禁，不是通用 entailment scorer 或大规模真实 provider benchmark。
- Quality Loop v5.3 已完成 A1 小规模真实 provider faithfulness smoke：RAG Real QA smoke 新增 `realProviderFaithfulness` gate，只记录 provider / model / modelCallCount / answerLength / noEvidence / passed 等脱敏摘要；2026-06-29 真实 marker `docpilot-rag-real-qa-20260629191831-69d71e` PASS，四个 grounded QA scope 均观察到非 mock provider 和 `modelCallCount=1`。该片是小规模真实链路证据，不是大规模 answer faithfulness benchmark 或线上 SLA。

### Quality Loop v2 / Memory Quality Eval（DONE）

- 目标：把 Conversation Memory 从功能可用推进到质量可解释，验证长期记忆候选、ACTIVE / SUGGESTED / IGNORED 分层、summary / recent messages / user memory / RAG evidence 的 trace 计数，以及 RAG evidence 不污染长期记忆。
- 第一片已完成：补离线 memory quality eval / tests，覆盖用户偏好抽取、项目目标抽取、敏感内容拦截、assistant / RAG evidence 不抽取为 memory、只有 ACTIVE memory 进入 context，以及 trace source counts。2026-06-28 已验证 `mvn "-Dtest=*MemoryQualityEval*,*Memory*,*Context*" test` PASS，48 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，249 tests。
- 第二片已完成：补真实链路 memory smoke，创建临时会话并验证候选抽取、接受 / 忽略状态分层、ACTIVE memory list 隔离，绑定 KnowledgeBase 后 trace 同时包含 `contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6` 和两文档 documentHitCounts。2026-06-28 `memory-quality-smoke.ps1 -Mode run` PASS，marker 为 `docpilot-memory-quality-20260628193150-625bf6`。
- Quality Loop v3.2 第一片已完成：Memory Governance 新增 API 级重复 / 冲突提示和接受前门禁，`UserMemoryResponse` 暴露 `duplicateOfId`、`conflictWithId`、`governanceHint`、`similarityScore`，`/conversations` Memory 抽屉展示冲突 / 重复提示。2026-06-28 Memory / Context targeted tests 54/54 PASS，RAG / KB / Conversation / Memory 回归 255/255 PASS，前端 lint / build PASS。
- Quality Loop v3.9 已完成 Memory Governance v2：支持 ACTIVE memory 编辑，以及冲突候选的 `KEEP_ACTIVE` / `REPLACE_ACTIVE` / `MERGE_WITH_ACTIVE` 用户可控处理；`/conversations` Memory 抽屉可编辑、替换和手动合并。2026-06-29 Memory / Context targeted tests 63/63 PASS，RAG / KB / Conversation / Memory 回归 267/267 PASS，前端 lint / build PASS，真实 `memory-quality-smoke.ps1 -Mode run` PASS，marker 为 `docpilot-memory-quality-20260629140941-6668d9`。下一步可做 Memory 版本历史 / 审计或真实 provider 小样本抽取质量验证；仍不写成真实模型长期记忆质量已成熟。
- Quality Loop v4.1 已完成 Memory Extraction Quality Eval 第一片：规则式候选抽取增加敏感内容和一次性 / 临时指令的前置过滤；离线 eval case 扩展到多信号抽取、assistant contamination、低价值寒暄、临时回答风格、敏感 token/API key 指令抑制，并输出 `suggestionSafetyRate`、`userSignalExtractionRate`、`noiseSuppressionRate`、`temporaryInstructionSuppressionRate`。2026-06-29 targeted eval 3/3 PASS，Memory / Context 63/63 PASS，RAG / KB / Conversation / Memory 回归 267/267 PASS。该片仍是规则式离线门禁，不代表真实 provider 长期记忆抽取质量。

### Quality Loop v2 / Frontend UX Audit（DONE）

- 目标：从真实用户视角检查 RAG、Memory、Trace 和 citation 展示效果，而不是只依赖 API gate。
- 第一片已完成：2026-06-28 使用真实 backend / frontend / tunnel 和浏览器创建临时数据，marker 为 `docpilot-frontend-ux-2647184760`。`/conversations` 显示 citation footer `2 条来源`，Trace 面板展示 `userMemory=1` / `ragEvidence=2`，Memory 面板展示 ACTIVE memory；`/knowledge-bases` 页面展示 provider / collection、来源文档分布 `#175:1 / #176:1`、召回片段和 citation 卡片。
- 移动端 `390x844`、`360x780`、`320x740` 检查 `/conversations` 与 `/knowledge-bases` 均无横向溢出；长 ACTIVE memory 未撑破 Memory 抽屉。本片未发现需要改代码的阻断问题。
- 后续 v2.1 已补三片：KnowledgeBase 问答结果区默认展示用户语义 KPI，并把 provider / collection / retrieval / rerank / model 信息收进“工程观测”折叠区；RAG Real QA Eval 新增更难 rerank candidate fixture 和 `rerankUpliftCandidatePassRate`；Memory 长列表真实 UI 审计验证 `16` 条 ACTIVE memory 下抽屉可滚动且桌面 / 移动端无横向溢出。v2.2 已补真实 rerank hard smoke，观察到 target rank 提升和 distractor 降权。v2.3 已补 Memory 产品化第一片，让来源、priority、confidence、重复提示和候选状态在 Memory 抽屉中可见，并通过桌面 / `390px` / `320px` 浏览器验证。继续可选方向是 README / showcase 口径同步，或 Memory 冲突 / 合并 / 编辑能力设计。

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
