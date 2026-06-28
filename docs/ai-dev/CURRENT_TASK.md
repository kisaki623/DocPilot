# Current Task

当前任务：DocPilot Quality Loop v2：Frontend UX Audit v1（DONE）

## 2026-06-28 追加任务：Frontend UX Audit v1 真实浏览器审计

- 目标：从真实用户视角检查 RAG、Memory、Trace、citation、KnowledgeBase evidence 和移动端布局，确认质量门禁的 API 结果能在前端关键路径上被用户看见、点到、读懂。
- 已完成：复用本地 tunnel / backend / frontend，使用浏览器上下文创建临时用户、两份 txt 文档、KnowledgeBase、ACTIVE memory 和绑定 KB 的 Conversation；marker 为 `docpilot-frontend-ux-2647184760`，文档为 `175/176`，KnowledgeBase 为 `36`，Conversation 为 `35`。
- 已验证：两文档 parse `SUCCESS`；Conversation Trace 为 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=2`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=2`、`documentHitCounts={175:1,176:1}`；会话气泡 footer 显示 `2 条来源`；Trace 面板和 Memory 面板均可通过真实点击打开，ACTIVE memory 可见。
- KnowledgeBase 页面验证：页面内点击“查看引用来源”后展示 provider / 索引集合、`来源不足: 否`、来源文档分布 `#175: 1 / #176: 1`、召回片段和引用来源卡片；两份临时文档 marker 均可见。桌面 `/conversations`、`/knowledge-bases` 均无横向溢出。
- 移动端验证：`390x844` 下 `/conversations` 的 `.dp-chat-shell`、`.dp-chat-main`、`.dp-chat-topbar`、`.dp-chat-thread`、`.dp-chat-composer-wrap` 均约束在 `346px`，页面 `scrollWidth=clientWidth=375`；`/knowledge-bases` 同样 `scrollWidth=clientWidth=375`。
- Gemini 轻量 UX sanity review 提醒：继续关注技术观测字段对非技术用户的认知负担、Trace / Memory 数据量增长后的可读性，以及 `390px` 以下更窄移动端视口。
- 追加 v1.1 验证：同一临时用户下新增一条包含长标识符的 ACTIVE memory，检查 `360x780` 与 `320x740` 极窄移动端。`/conversations` Memory 抽屉可打开，长 memory 未撑破页面；`360px` 下页面 `scrollWidth=clientWidth=345`，`320px` 下页面 `scrollWidth=clientWidth=305`；`/knowledge-bases` 同样无横向溢出。
- 边界：本片创建了临时审计数据，但未改后端 / 前端业务代码，未删除业务数据，未改数据库结构，未操作远程 Docker，未提交 artifact / 截图 / 日志原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。
- 下一步候选：Quality Loop v2 三条主线已完成一轮闭环。若继续自驱推进，优先进入 KnowledgeBase 技术字段产品化降噪，保留面向工程审计的 provider / collection / score 信息，但避免普通用户第一眼被底层名词淹没。

## 2026-06-28 追加任务：Memory Quality Eval v1 真实链路 smoke 第二片

- 目标：把 Memory Quality Eval 从离线 test-side 门禁推进到真实链路 smoke，验证临时会话中候选记忆抽取、接受 / 忽略状态分层、ACTIVE 列表隔离，以及绑定 KnowledgeBase 后 trace 同时包含 userMemory 和 ragEvidence。
- 已完成：新增 `scripts/smoke/memory-quality-smoke.ps1`，并给 `cloud-quality-smoke.ps1` 增加默认关闭的 `-EnableMemoryQualityGate`；Memory 专项 wrapper 默认使用 `SmokePrefix=docpilot-memory-quality`、artifact root `backend/target/memory-quality` 并打开 memory gate。`RuleBasedMemoryExtractionService` 同步补充英文 smoke 消息关键词识别，避免真实 smoke 必须依赖中文脚本字符串。
- 已验证：`memory-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=MemoryQualitySmokeScriptSafetyTest,RuleBasedMemoryExtractionServiceTest" test` PASS，5 tests；真实 `memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-memory-quality-20260628193150-625bf6`；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，252 tests。
- 关键结果：Memory 专项 gate 抽取候选 `2` 条，accepted suggestion 变为 `ACTIVE`，ignored suggestion 变为 `IGNORED` 且不出现在 ACTIVE 列表；trace 显示 `contextSourceCounts.recentMessages=2`、`userMemory=1`、`ragEvidence=6`、`memoryCount=1`、`evidenceCount=6`，documentHitCounts 覆盖两份临时文档。完整 delegated gates 中 tunnel、backend health、frontend routes、两文档 parse/index、chunk 质量、MySQL/Qdrant 一致性、单文档 RAG、KB RAG、no-evidence、Conversation Trace、权限隔离和 artifact 脱敏均 PASS。
- 边界：本片创建了临时 smoke 用户、文档、KnowledgeBase、Conversation 和 memory 数据，但未操作远程 Docker、未走 `hk-ops`、未删除业务数据、未改数据库结构、未提交 artifact 原文、未打印 `.env` / token / API key / 云地址 / 连接串、未 push。早期失败 run 只留下 ignored artifact，用于本地诊断，不提交。
- 下一步：进入 Frontend UX Audit v1，使用真实前后端链路和浏览器检查 `/conversations` memory / trace / citation 展示、KnowledgeBase evidence 可读性、移动端布局和关键路径，不只看 API gate。

## 2026-06-28 追加任务：Memory Quality Eval v1 离线基线第一片

- 目标：把 Conversation Memory 从零散单测推进为可重复质量门禁，先离线覆盖候选抽取、敏感内容拦截、ACTIVE / SUGGESTED / IGNORED 状态分层、RAG evidence 不进入 memory、Context Trace source counts。
- 已完成：新增 `backend/src/test/resources/memory/memory-quality-eval-cases.json`，以及 `MemoryQualityEvalCase` / `MemoryQualityEvalRunner` / `MemoryQualityEvalResult` / `MemoryQualityEvalMetrics` 和 fixture / runner 测试；runner 复用真实 `RuleBasedMemoryExtractionService`、`MemorySelector`、`ContextAssemblyServiceImpl` 和 `MemorySafetyValidator`，用 test double 提供会话消息、记忆和 RAG evidence。
- 已验证：`mvn "-Dtest=*MemoryQualityEval*,*Memory*,*Context*" test` PASS，48 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，249 tests。
- 边界：本片只做离线 test-side eval，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不 push。artifact 只保存 case id、计数、布尔指标和失败原因，不保存对话全文、memory content、prompt、evidence context、token 或密钥。
- 下一步：已由第二片真实 `memory-quality-smoke.ps1 -Mode run` 收口为 PASS。

## 2026-06-28 追加任务：RAG Real QA Eval v1 真实链路 run 第三片

- 目标：把 RAG Real QA Eval 从离线基线和 wrapper 验证推进到真实链路证据，确认临时用户、两文档、KnowledgeBase、Conversation Trace、权限隔离和脱敏 artifact 在完整 cloud quality gate 中保持通过。
- 已完成：执行 `scripts/smoke/rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`，marker 为 `docpilot-rag-real-qa-20260628164757-ac2a1d`；runner 启动本地 tunnel / backend / frontend，创建临时 smoke 用户、两份 txt 文档、KnowledgeBase、Conversation，并生成 ignored 脱敏 artifact。
- 已验证：整体 `PASS`；覆盖配置一致性、tunnel、backend health、frontend route、注册、上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、populated-KB no-evidence、Conversation Trace、四个权限隔离负向检查、artifact 脱敏扫描和 cleanup。
- 关键结果：两份文档均为 `3/3` chunks indexed 且 MySQL / Qdrant matched；单文档 RAG `3` hits / `3` citations；KnowledgeBase RAG `6` hits / `6` citations，documentHitCounts 覆盖两文档；no-evidence gate 返回 `0` hits / `0` citations；Conversation Trace 显示 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=6`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6`。
- 边界：本片创建了临时 smoke 数据，但未操作远程 Docker、未走 `hk-ops`、未删除业务数据、未改数据库结构、未提交 artifact 原文、未打印 `.env` / token / API key / 云地址 / 连接串、未 push。
- 下一步：进入 Memory Quality Eval v1，先做离线 / runtime 小闭环，验证长期记忆候选、ACTIVE / SUGGESTED / IGNORED 分层、RAG evidence 不污染 memory、trace source counts 和真实会话体验；随后继续 Frontend UX Audit。

## 2026-06-28 追加任务：RAG Real QA Eval v1 离线基线第一片

- 目标：把下一轮质量循环从单纯 smoke / 少量 synthetic case，推进到更贴近真实问答类型的离线 RAG QA eval 基线，先覆盖事实查找、跨文档总结、比较、多跳式证据、no-evidence、语义干扰、hybrid keyword 噪声和 rerank uplift shaped case。
- 已完成：新增 `real-qa-eval-cases.json`，以及 `RagRealQaEvalCase` / `RagRealQaEvalRunner` / `RagRealQaEvalResult` / `RagRealQaEvalMetrics` 和对应 fixture / runner 测试；runner 复用既有 KnowledgeBase RAG eval harness，继续使用 `MockEmbeddingProvider` + `InMemoryVectorStoreClient`，artifact 只输出脱敏 summary，不保存文档原文、query、模型输入、evidence context 或模型输出。
- 已验证：`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，7 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，202 tests。
- 边界：本片只做离线质量基线，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不 push。
- 下一步：进入真实链路 RAG Real QA smoke runner，把同类 case 小规模迁移到临时用户 / 文档 / KB / Conversation 的真实链路验证；随后继续 Memory Quality Eval 和 Frontend UX Audit。

## 2026-06-28 追加任务：RAG Real QA Eval v1 真实链路入口第二片

- 目标：为 RAG Real QA Eval 建立独立真实链路 smoke 入口，后续可以用专属 marker / artifact 路径收口真实临时用户、文档、KnowledgeBase、Conversation Trace 和权限门禁，而不是把所有证据混在通用 cloud quality smoke 下。
- 已完成：新增 `scripts/smoke/rag-real-qa-eval-smoke.ps1`，支持 `plan` / `dry-run` / `run`，默认 `SmokePrefix=docpilot-rag-real-qa`、artifact 根目录 `backend/target/rag-real-qa`；当前 wrapper 委托 `cloud-quality-smoke.ps1` 执行完整业务质量门禁，并在 plan 输出 real-QA case 类型清单。
- 已验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS，当前记录 MySQL / Qdrant local ports 未监听但 dry-run 本身 PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，2 tests。
- 边界：本片只新增真实链路入口和脚本安全测试；未执行 `run`，未启动 tunnel / backend / frontend，未创建业务数据，未调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不 push。
- 下一步：已由第三片执行 `rag-real-qa-eval-smoke.ps1 -Mode run` 并收口为 PASS。

## 2026-06-28 追加任务：Phase 3 small real rerank provider validation 收口

- 目标：做小规模真实 rerank provider 实效验证，判断 rerank / hybrid 是否实际改善召回与 citation，同时不默认扩大能力范围。
- 已完成：新增 `scripts/smoke/rerank-effect-smoke.ps1`，支持 `plan` / `dry-run` / `run`；`run` 通过环境变量覆盖执行两轮 cloud quality smoke：hybrid-only baseline 与 hybrid+real-rerank candidate，并输出脱敏对比 artifact。`cloud-quality-smoke.ps1` 的 KnowledgeBase gate 同步输出 `retrievalMode`、`rerankApplied`、`rerankModel`、rerank score summary。
- 已验证：PowerShell parser PASS；`rerank-effect-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 `-Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS。baseline marker 为 `docpilot-rerank-effect-hybrid-20260628151134-170d38`，rerank marker 为 `docpilot-rerank-effect-rerank-20260628151301-6b0060`；rerank run 显示 `rerankApplied=true`、rerank score count `6`，KB hit / citation / coveredDocumentCount 与 baseline 均持平，no-evidence 和权限隔离无回退。
- 结论：当前 smoke fixture 下 baseline 已达到两文档覆盖和 6 citations，真实 rerank provider 没有带来可量化覆盖提升，但证明了 provider 可用、rerank score 进入 response / artifact、且核心 RAG / security gate 无回退；不能把该结果写成大规模 relevance uplift。
- 边界：本片只新增 smoke runner 和脱敏观测字段；不改数据库结构、不删除业务数据、不提交 artifact / 日志 / 截图、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 下一步候选：若继续提升真实效果，优先设计更难的 rerank eval / smoke case（baseline 会排序错误或被干扰文档诱导），再判断 rerank 是否带来正向 uplift；不要仅靠当前满分 fixture 宣称 rerank 显著提升。

## 2026-06-28 追加任务：Phase 2 KB multi-document coverage 收口

- 目标：修复真实体验审计中 KnowledgeBase 手动两文档总结问题只召回 Alpha 文档、漏掉 Beta 文档的问题，同时不破坏 populated-KB no-evidence 门禁。
- 已完成：KnowledgeBase hybrid retrieval 的二次 confidence gate 改为 summary intent 感知；summary / all-documents 类问题中，`keywordScore>0` 的 hybrid 候选可进入后续 scope guard、rerank 和多文档 diversity selection，普通非 summary 问法仍按 `vectorScore` / similarity threshold 阻断低置信 keyword 噪声。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，11 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，199 tests；真实 `scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007 -ReuseRunningServices` PASS，marker 为 `docpilot-rag-real-quality-20260628150434-2b7b39`，KnowledgeBase 两文档 gate 命中分布 `{152:3,153:3}`，no-evidence gate、Conversation Trace、权限隔离和前端 route smoke 均 PASS。
- 边界：本片只改 KnowledgeBase retrieval gate 和对应单测；不改数据库结构、不删除业务数据、不提交 artifact / 日志 / 截图、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 下一步：进入 Phase 3 小规模真实 rerank provider 实效验证；只验证 rerank / hybrid 是否改善召回与 citation，不默认扩大能力范围，不做大规模付费 eval。

## 2026-06-28 追加任务：Phase 2 mobile conversation layout 第一片

- 目标：修复移动端 `/conversations` 在 `390x844` 视口下横向溢出，主聊天区被长标题 / KB label / composer 控件撑到视口外的问题。
- 已完成：移动端 `.dp-chat-main` 增加 inline-size containment、overflow 和宽度约束；主区直接子项强制 `min-width:0` / `max-width:100%` / stretch；topbar pill 支持单行截断；thread 与 composer wrapper 限制在主区宽度内。
- 已验证：`npm run lint` PASS；Playwright 在 `390x844` 下测量 `.dp-chat-shell`、`.dp-chat-main`、`.dp-chat-topbar`、`.dp-chat-thread`、`.dp-chat-composer-wrap` 均为 `346px` 宽，页面整体 `scrollWidth` 不再超过 viewport；composer meta 内部保留横向滚动但不撑开页面。
- 边界：本片只修移动端会话页响应式 CSS，不改 API、不改业务逻辑、不改数据库结构、不提交截图 / 日志 / artifact、不 push。
- 下一步：处理 KnowledgeBase 页面手动两文档问题只召回单文档的问题，优先分析 retrieval 参数、问题改写、多文档覆盖策略与 rerank/hybrid 交互。

## 2026-06-28 追加任务：Phase 2 document detail citation 第一片

- 目标：修复文档详情页流式 RAG 回答出现 `[1]`，但右侧“引用来源”仍显示暂无引用的问题。
- 已完成：`rag-api.ts` 支持 RAG SSE 的 `retrieval` 与逐条 `citation` 事件；文档详情页在流式回答期间实时更新 `ragRetrieval` 与 `ragCitations`；SSE retrieval 摘要只有 `hitCount` 时，引用面板用 `hitCount` 兜底显示命中数量。
- 已验证：`npm run lint` PASS；Playwright 在 `http://localhost:3007/documents/150` 发送流式 RAG 问题后，右侧“引用来源”显示 `检索命中 1 条`、`引用 1`、score、chunk version 与 snippet。
- 边界：本片只修前端 RAG SSE 事件消费和展示，不改后端 API、不改数据库结构、不保存 prompt / evidence 原文、不提交 artifact / 日志 / 截图、不 push。
- 下一步：修移动端 `/conversations` 横向溢出；随后处理 KnowledgeBase 手动两文档问法只召回单文档的问题。

## 2026-06-28 追加任务：Phase 2 citation display 第一片

- 目标：修复 `/conversations` 历史消息重新加载后，回答正文含 `[1]` / `[2]`、Trace 有 RAG evidence，但气泡 footer 显示 `0` 条引用的真实体验问题。
- 已完成：前端加载会话历史时会 best-effort 拉取最新助手消息的 `ContextTrace`，并把 `message.citations.length` 或 `contextTrace.evidenceCount` 作为来源数量展示；无 trace 时仍保持聊天消息可渲染。
- 已验证：`npm run lint` PASS；Playwright 刷新 `http://localhost:3007/conversations` 后，临时会话 `docpilot-phase2-ui-audit-1782628501578` 的助手消息 footer 从 `0 条引用` 修正为 `2 条来源`，右侧 Trace 同时显示 `RAG 证据=2`、`长期记忆=1`。
- 边界：本片只修 `/conversations` 前端展示一致性，不改后端 API、不改数据库结构、不保存 prompt / evidence 原文、不提交 artifact / 日志 / 截图、不 push。
- 下一步：继续修文档详情页 citation 面板不同步，随后处理移动端 `/conversations` 横向溢出；KnowledgeBase 手动两文档覆盖不稳作为 Phase 2 后续检索质量切片。

## 2026-06-28 追加任务：Phase 2 真实体验审计第一片

- 目标：用真实浏览器和真实 backend / frontend / tunnel 链路，从用户视角检查 RAG、Memory、Trace 和前端关键路径，而不是只看 smoke runner 的 API gate。
- 已完成：启动本地 tunnel、backend、frontend；修复 `WebMvcConfig` 中本地 smoke 端口 `3007` / `3100` 未被 CORS allowlist 覆盖导致浏览器登录 / 注册 403 的问题；使用 marker `docpilot-phase2-ui-audit-1782628501578` 创建两份临时 txt 文档、KnowledgeBase、Conversation 和 ACTIVE memory。
- 已验证：浏览器 UI 注册成功；两文档 `150/151` parse `SUCCESS`；单文档 RAG `1` hit / `1` citation；KnowledgeBase API 多文档 RAG `2` hits / `2` citations 且 documentHitCounts 为 `{150:1,151:1}`；Conversation Trace 显示 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=2`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=2`。
- 发现问题：文档详情实时 RAG 回答有 `[1]` 但右侧引用来源仍为空；KnowledgeBase 页面手动两文档问题只召回 `150`、漏掉 `151`；Conversation 回答正文有 `[1]` / `[2]` 且 Trace evidence 为 `2`，但气泡 footer 显示 `0` 条引用；移动端 `/conversations` 在 `390x844` 下横向溢出。
- 边界：本片创建了临时审计数据，但不删除业务数据、不改数据库结构、不提交 artifact / 截图 / 日志原文、不打印或提交 `.env` / token / API key / 云地址 / 连接串、不 push。
- 下一步：优先修复前端 citation / Trace 展示一致性和移动端会话页布局，再回到 KnowledgeBase 多文档召回稳定性；这些完成后再进入 Phase 3 小规模真实 rerank provider 效果验证。

## 2026-06-28 追加任务：RAG Quality Upgrade v8 第二片

- 目标：补齐单文档 RAG 离线 smoke case，让单文档链路也覆盖 populated-document no-evidence、grounding citation marker 和单文档 distractor 抑制，而不是只靠空文档 no-evidence。
- 已完成：`rag-document-retrieval-smoke-cases.json` 从 4 个 case 扩到 7 个 case；`RagDocumentRetrievalQualitySmokeTest` 新增 case 级 `minSimilarityThreshold` 和可选 `forbiddenMarker` 检查，验证低置信结果过滤与 distractor 不进入 hit / citation。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalQualitySmokeTest" test` PASS，2 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 边界：本片只增强离线 smoke harness / case，不改生产 API、不改数据库结构、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 结论：v8 eval corpus expansion 当前两片已完成，KnowledgeBase 与单文档 RAG 离线质量门禁均已扩容；下一阶段进入 Phase 2 真实体验审计，从用户视角跑完整业务链路并检查 RAG、Memory、前端路径和 trace 展示效果。

## 2026-06-28 追加任务：RAG Quality Upgrade v8 第一片

- 目标：先把 KnowledgeBase RAG 离线 eval corpus 从“少量链路样例”扩展为更像质量门禁的 case 集，覆盖 no-evidence、grounding、多文档干扰、hybrid keyword 噪声和 scope 干扰。
- 已完成：`knowledge-base-rag-eval-cases.json` 从 5 个 case 扩到 11 个 case；新增 case 级 `minSimilarityThreshold`，用于稳定模拟真实链路 confidence gate；补充 `semantic-no-evidence-populated-kb`、`hybrid-keyword-noise-no-evidence`、`multi-document-three-way-summary`、`grounded-answer-distractor-suppression`、`cross-topic-distractor-routing` 和 `out-of-scope-semantic-distractor`。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagEvalFixtureTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagEvalRunnerTest" test` PASS，5 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests；真实 `scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260628141419-fb7c21`。
- 边界：本片不改生产 API、不改数据库结构、不强制真实 rerank provider、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 下一步：继续 v8 第二片，检查并扩展单文档 RAG eval / smoke case，补 no-evidence、grounding 和 distractor 覆盖，然后再进入 Phase 2 真实体验审计。

## 2026-06-27 追加任务：真实链路优先自驱协议

- 目标：把用户长期授权后的自驱模式从“最小 / mock 验证优先”调整为“真实链路优先验证”，让后续 RAG、KnowledgeBase、Conversation Memory、Context Trace、权限隔离和前端关键路径改动尽量以真实用户体验链路收口。
- 已完成：`AGENTS.md` 与 `docs/ai-dev/CONSTRAINTS.md` 已补充真实链路优先规则；自驱模式下默认允许启动本地 tunnel / backend / frontend、运行真实 smoke、创建带统一 marker 的临时 smoke 数据、使用本机已有真实配置并生成 ignored 脱敏 artifact；`docs/ai-dev/ROADMAP_RAG.md` 已同步 RAG 质量门禁口径。
- 边界：仍禁止 push、提交 `.env` / secrets / artifact 原文、打印 token / API key / 云地址 / 连接串、删除业务数据、改数据库结构、清空 collection、远程 Docker 启停 / 重启 / 迁移或大规模高成本 provider eval；这些高风险操作仍需单独确认。
- 下一步：进入 `RAG Quality Upgrade v8: eval corpus expansion`。后续每个 RAG / Memory 质量切片先跑离线门禁；环境可达时继续跑真实链路 smoke，否则只能记录 `REVIEW` 或 `BLOCKED`，不能把用户体验质量写成已真实验证。

## 2026-06-27 追加任务：自驱迭代推进协议文档化

- 目标：把用户授权后的“连续自驱迭代推进”写入项目协作规则，让后续协作代理能理解：在明确授权后，应自行拆小片、实现、验证、自审、提交、回写文档，并继续推进下一片，直到大目标完成或遇到阻塞。
- 已完成：`AGENTS.md` 新增自驱迭代模式入口、循环步骤、停止条件和提交规则；`docs/ai-dev/CONSTRAINTS.md` 新增自驱迭代安全边界。
- 边界：该模式不允许绕过安全限制；仍禁止 push、远程 Docker / `hk-ops` 未授权操作、删除业务数据、提交真实密钥或 artifact 原文。
- 下一步：进入 `RAG Quality Upgrade v8: eval corpus expansion`，优先扩大 no-evidence / grounding / multi-document 干扰 eval case。

## 2026-06-27 追加任务：RAG Quality Upgrade v7

- 目标：把 Conversation Context / User Memory / RAG Evidence / Context Trace 的边界做清楚，避免把 RAG evidence 当作长期记忆，也避免长期记忆污染知识库证据。
- 成功标准：Conversation Trace 能区分 `conversationContext`、`userMemory`、`ragEvidence` 和 fallback；RAG evidence 不自动写入长期记忆；长期记忆候选仍需要用户接受后才进入上下文；相关离线测试和真实 smoke 能证明 KB evidence 与 memory 同时存在时上下文可解释。
- 明确不做：本轮不引入复杂多 Agent 编排、不改数据库结构、不操作远程 Docker、不删除业务数据、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 已完成 v7 第一片：`ContextTrace` API 新增计算型 `contextSourceCounts` / `contextSourceFlags`，基于既有 summary / recent / memory / RAG evidence 字段生成，不改表结构、不持久化 prompt 或 evidence 原文；`/conversations` 右侧 Trace 面板展示会话摘要、最近消息、长期记忆和 RAG 证据拆分。
- 已验证：`mvn "-Dtest=*Context*,*Conversation*,*Memory*" test` PASS，56 tests；`npm run lint` PASS。
- 已完成 v7 第二片：新增 memory-aware RAG 负向测试，证明 assistant / RAG evidence 文本不会被长期记忆抽取为候选，且 `MemorySelector` 只把 `ACTIVE` user memory 放进上下文，`SUGGESTED` / `IGNORED` 不进入 prompt。
- 已验证：`mvn "-Dtest=RuleBasedMemoryExtractionServiceTest,MemorySelectorTest,ContextAssemblyServiceImplTest" test` PASS，6 tests。
- 已完成 v7 第三片：真实 `rag-real-quality-smoke.ps1 -Mode run` 已把 Conversation Trace gate 扩展为同时要求 KB RAG evidence 与 `ACTIVE` user memory，artifact 只保存脱敏 source counts，不保存 prompt 或 evidence 原文。
- 已验证：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260627220736-8f03b9`，其中 `conversationTrace` 显示 `evidenceCount=6`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6`。
- v7 结论：DONE。下一步建议是 v8 eval corpus expansion：扩大 no-evidence / grounding / multi-document 干扰 case，而不是继续堆功能。

## 2026-06-27 追加任务：RAG Quality Upgrade v5

- 已完成 chunk structure quality 小步落地：`DocumentChunkCandidate` 新增 section title / ordinal / source block ordinal / structure type / quality flags；`ChunkingServiceImpl` 从 Markdown heading、文本块和基础异常信号生成结构元数据。
- `RagIndexingServiceImpl` 将结构 metadata 透传到 embedding metadata 与 Qdrant `VectorPoint` payload；不改数据库结构，不保存 prompt、token、密钥、连接串或云地址。
- `scripts/smoke/cloud-quality-smoke.ps1` 的 `chunkQuality` gate 已增强 MySQL 侧 offset order、token/content length、duplicate hash 检查；`mysqlQdrantConsistency` gate 已校验 Qdrant payload 中的结构字段。
- 已验证：`mvn "-Dtest=ChunkingServiceImplTest,RagIndexingServiceImplTest,VectorPointTest" test` PASS，28 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已执行真实链路默认 run：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260627213040-4038e1`；chunkQuality、MySQL / Qdrant payload 结构字段、单文档 RAG、KB 两文档 RAG、no-evidence、Conversation Trace、权限隔离、前端 route 和 artifact redaction 均 PASS。

## 下一步代码任务：RAG Quality Upgrade v6

- 目标：把 hybrid / rerank 从“可选增强”推进到有明确默认边界、质量对比和失败降级策略的 production gate。
- 成功标准：默认离线测试不依赖真实 rerank provider；eval / smoke 能区分 vector-only、hybrid 和 rerank 的 hit / citation / multi-document coverage / no-evidence 指标；真实 rerank provider 仍必须显式配置。
- 明确不做：本轮不强制真实 rerank provider、不操作远程 Docker、不改数据库结构、不删除业务数据、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。

## 2026-06-27 追加任务：RAG Quality Upgrade v6

- 已完成 v6 第一片：KnowledgeBase RAG 离线 eval 现在同一批 case 同时跑 `vector` 与 `hybrid` 两种 retrieval mode，并在脱敏 artifact 中输出 `retrievalModeMetrics.vector` / `retrievalModeMetrics.hybrid`。
- 默认 eval 仍使用 `MockEmbeddingProvider` + `InMemoryVectorStoreClient`，不依赖真实 rerank provider；hybrid eval 使用内存 keyword retriever，只服务质量门禁，不改变线上默认 `hybridEnabled=false`。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，4 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已完成 v6 第二片：rerank provider 现在必须外部配置完整才发 HTTP；`enabled=true` 但缺少 provider 所需字段时直接 identity fallback，避免半配置状态误触发外部调用。
- 已验证：`mvn "-Dtest=*Rerank*,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，14 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests；`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260627214532-e1fb65`。
- v6 结论：DONE。真实 rerank provider 效果仍未强制验证，后续必须在用户显式提供配置后单独 smoke。

## 2026-06-27 追加任务：RAG Quality Upgrade v4

- 已完成 KnowledgeBase QA answer audit 小步落地：`KnowledgeBaseRagQaAnswer` 新增脱敏 `audit`，记录 `grounded`、evidence / citation count、documentHitCounts、score / vectorScore / fusedScore / rerankScore summary、retrievalMode、rerank 信息、fallbackReason 和 modelCallCount。
- `KnowledgeBaseRagQaResponse` 已暴露 audit；不保存 prompt、evidence 原文、模型输入输出、token、密钥或连接串。
- 离线 KnowledgeBase RAG eval 新增 `groundedAnswerRate` 和 `noEvidenceCitationFreeRate`，并把 grounded answer miss / no-evidence citation leak 纳入 case failure reasons。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，21 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已执行真实链路默认 run：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260627211711-383cda`；未提交 artifact 原文，未 push。

## 2026-06-27 追加任务：RAG Quality Upgrade v3

- 已完成 no-evidence threshold / grounded refusal 小步落地：KnowledgeBase hybrid retrieval 在融合后继续执行 evidence confidence gate，hybrid hit 带 `vectorScore` 时使用原始向量相似度判断阈值，不把 RRF `fusedScore` 当作 similarity。
- 默认质量阈值已校准为 `APP_RAG_RETRIEVAL_MIN_SIMILARITY_THRESHOLD=0.50`，同步到 `application.yml`、`.env*.example`、smoke runner 和 RAG hybrid guide；`RagRetrievalProperties` 程序化默认仍保持 `0.0`，避免破坏离线 harness。
- 已补测试：programmatic default / threshold validation、KnowledgeBase vector threshold no-evidence、hybrid fused hit 低置信 no-evidence、hybrid 使用 vectorScore 而非 fusedScore 做门禁。
- 已验证：`mvn "-Dtest=RagRetrievalPropertiesTest,RagDocumentRetrievalServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseEvidenceContextBuilderTest,ContextAssemblyServiceImplTest" test` PASS，33 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已执行真实链路默认 run：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260627210458-9d0321`；`noEvidenceThreshold` 返回 `noEvidence=true`、`0` retrieve hits、`0` QA citations。
- 本轮创建了临时 smoke 用户、文档、KnowledgeBase 和 Conversation，并写入 ignored artifact；未操作远程 Docker，未走 `hk-ops`，未删除业务数据，未改数据库结构，未提交 artifact 原文，未 push。

## 已完成代码任务：RAG Quality Upgrade v4

- 目标：强化 citation grounding 与 answer audit，确保回答层只使用通过 evidence confidence gate 的 citations，并把 no-evidence / fallback / score summary / documentHitCounts 以脱敏方式进入 response、trace 或 artifact。
- 成功标准：离线 eval 增加 citation grounding / no-evidence precision / forbidden answer leak case；真实 smoke 保持 `noEvidenceThreshold=PASS`、单文档 RAG、KB 两文档 RAG、Conversation Trace、权限隔离和 artifact redaction 不回退。该标准已由 marker `docpilot-rag-real-quality-20260627211711-383cda` 验证。
- 明确不做：不改数据库结构，不操作远程 Docker，不走 `hk-ops`，不删除业务数据，不强制真实 rerank provider，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。

## 2026-06-27 追加任务：RAG / Memory 生产化路线定线

- 本轮目标是先把关键事实源从“求职级展示收口”调整为“生产化知识库 RAG + 会话记忆核心闭环”，避免后续 agent 继续沿旧路线只做展示包装。
- 文档定线只更新 `AGENTS.md`、`docs/README.md`、`STATE.md`、`CURRENT_TASK.md`、`ROADMAP_RAG.md`、`DECISIONS.md` 和 `PROGRESS_LOG.md`；不启动 tunnel / backend / frontend，不创建业务数据，不改数据库结构，不 push。
- 当前真实证据以 v3 smoke 为准：`rag-real-quality-smoke.ps1 -Mode run` 已在默认阈值 `0.50` 下验证真实 embedding + Qdrant 链路整体 `PASS`，包括 populated-KB no-evidence。
- 下一轮代码任务固定为 v4：强化 citation grounding 与 answer audit，把通过门禁的 evidence、fallback 和 score summary 做成更清晰的质量证据。
- 本轮不得把项目写成完整商业 SaaS、线上 SLA、大规模多租户计费、高可用运维、成熟多 Agent 或已完成生产级 no-evidence。

## 已完成代码任务：RAG Quality Upgrade v3

- 目标：统一单文档 RAG、KnowledgeBase RAG 和 Conversation KnowledgeBase evidence 的 no-evidence 判定，让没有达到置信阈值的检索结果不进入 grounded QA。
- 成功标准：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run` 中 `noEvidenceThreshold` gate 变为 `PASS`；单文档 RAG、KB 两文档 RAG、Conversation Trace、权限隔离和 artifact redaction 不回退。该标准已由 marker `docpilot-rag-real-quality-20260627210458-9d0321` 验证。
- 实现结果：基于已有 `app.rag.retrieval.min-similarity-threshold` 补齐 KnowledgeBase hybrid 路径，并增加安全默认值、score 观测和离线测试；未强制真实 rerank provider。
- 明确不做：不改数据库结构，不操作远程 Docker，不走 `hk-ops`，不删除业务数据，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。

## 2026-06-27 追加任务：RAG Quality Upgrade v2

- 已新增 `scripts/smoke/rag-real-quality-smoke.ps1`，作为真实 embedding + Qdrant 检索质量门禁入口；脚本支持 `-Mode plan`、`-Mode dry-run`、`-Mode run`，默认 artifact 写入 ignored 路径 `backend/target/rag-quality/<smokeMarker>/artifact.json`。
- v2 复用并增强 `scripts/smoke/cloud-quality-smoke.ps1`：新增 `-SmokePrefix` 参数，并增加 `noEvidenceThreshold` gate；无关 populated-KB query 如果仍返回最近证据，状态标记为 `REVIEW`，不伪装成 PASS。
- 已执行 `-Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007`，marker 为 `docpilot-rag-real-quality-20260627195744-d5b6e2`，overallStatus 为 `REVIEW`。
- 本次真实链路 PASS 项：tunnel、backend health、frontend route、注册、两文档上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、Conversation Trace、权限隔离、artifact 脱敏。
- 本次 REVIEW 项：`noEvidenceThreshold`，无关 populated-KB query 仍返回 `3` 个 retrieve hits / `3` 个 QA citations，说明后续需要调 `minSimilarityThreshold`、rerank 或 no-evidence 策略。
- 本轮同步修复 `scripts/dev/cleanup-agent-processes.ps1` 的进程匹配规则，覆盖 `DocPilotApplication`、`npm run dev` 和带引号的 `next dev` 形态；本轮收尾时已手动确认 8081 / 3007 / 13306 / 6333 均未监听。

## 2026-06-27 追加任务：RAG Quality Upgrade v1

- 已完成第一版“从玩具 RAG 到求职级真实效果 RAG”的小步落地：单文档 retrieval 接入统一 `app.rag.retrieval.min-similarity-threshold`，避免配置只对 KnowledgeBase 生效、单文档链路仍对无关问题返回最近 chunk。
- 已增强 KnowledgeBase RAG 离线 eval：case fixture 可声明 `expectedAnswerMarkers`、`forbiddenAnswerMarkers`、`minCitationCount` 和 `requiresMultiDocumentCoverage`；eval result / artifact 新增 `answerHitRate`、`citationCountRate`、`multiDocumentCoverageRate`、`forbiddenAnswerLeakRate`。
- 当前离线 artifact 仍写入 ignored 路径 `backend/target/rag-eval/knowledge-base-rag-eval-latest.json`，只保存 case summary、计数和布尔指标，不保存文档原文、prompt、evidence context、模型输出、token 或密钥。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalServiceImplTest,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagEvalFixtureTest" test` PASS，12 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，192 tests；`mvn -DskipTests compile` PASS；`mvn test -DskipITs` PASS，729 tests，0 failures，0 errors，1 skipped。
- 当前边界：本轮不调用真实 embedding / rerank / answer provider，不启动 tunnel，不创建业务数据，不改数据库结构，不改前端；该 v1 是离线质量门禁增强，不等于真实 provider 效果评测已完成。

## 2026-06-27 追加任务：云端完整业务 Smoke 质量门禁增强

- 已新增 `scripts/smoke/cloud-quality-smoke.ps1`，把 cloud smoke 从“链路能跑通”扩展为可执行质量门禁 runner。
- runner 支持 `-Mode plan`、`-Mode dry-run`、`-Mode run`：`plan` 只输出门禁清单，`dry-run` 只做本地前置检查，`run` 才会启动 / 复用 tunnel、backend、frontend 并创建临时业务数据。
- 统一 `smokeMarker` 贯穿临时用户、两份 txt 文档、KnowledgeBase、Conversation、问题文本和 artifact；artifact 默认写入 ignored 路径 `tmp-e2e/docpilot-cloud-quality-smoke/<smokeMarker>/artifact.json`。
- `run` 模式门禁覆盖 tunnel 连通、backend health、frontend route、注册 / 登录、两文档上传 / parse / indexing、MySQL chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、Conversation Trace、至少四个权限隔离负向检查、artifact 脱敏扫描、清理和最终 `git status`。
- 当前已验证：Windows PowerShell 5 parser PASS；`-Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run -ArtifactRoot backend/target/smoke -FrontendBaseUrl http://127.0.0.1:3007` PASS。
- 本次 `run` marker 为 `docpilot-cloud-quality-20260627022219-37efd4`，脱敏 artifact 位于 `backend/target/smoke/docpilot-cloud-quality-20260627022219-37efd4/artifact.json`，artifact 不提交。
- 本次 `run` 生成临时用户、文档、KnowledgeBase 和 Conversation；没有操作远程 Docker，没有使用 `hk-ops`，没有删除业务数据，没有改数据库结构，没有 push。

## T013 本轮已完成

- 新增会话级上下文 MVP 后端底座：`tb_conversation`、`tb_conversation_message`、`tb_conversation_summary`、`tb_context_trace`、`tb_user_memory` 五张新表迁移脚本；未改动既有 `qa_history`、`agent_task`、`agent_step`、RAG 或 KnowledgeBase 表结构。
- 新增 `conversation` / `memory` / `ai.context` 后端 package，提供会话创建、列表、知识库绑定、非流式消息发送、会话摘要读取 / 删除、用户长期记忆手动维护接口。
- 新增 `ContextAssemblyService`，支持 `RECENT_TURNS` 和 `AGENT_MEMORY` 两种模式；上下文来源包含系统提示、长期记忆、会话摘要、最近轮次和可选 KnowledgeBase evidence。
- KnowledgeBase evidence 只复用现有 `KnowledgeBaseRagRetrievalService` 与 `KnowledgeBaseScopeGuard`，不直连 Qdrant，不改现有 RAG / Agent / ToolCall 主链路。
- 新增 token 预算、优先级裁剪、prompt rendering、trace response、记忆敏感内容拦截和权限过滤单元测试。
- 新增摘要级 `ContextTrace` 持久化与按消息查询 API：只保存 mode、计数、token 估算、RAG hit 分布、fallback / truncated 摘要字段，不保存完整 prompt 或 evidence 原文；trace 写入失败不影响主回答链路。
- 新增显式 `POST /api/conversations/{conversationId}/summary/refresh`，使用本地 extractive 摘要压缩最近消息；不调用真实外部模型，不做后台自动摘要。
- 新增长期记忆候选机制：规则式 `MemoryExtractionService` 从会话用户消息中提取 `SUGGESTED` 记忆，提供候选列表、提取、接受、忽略 API；候选记忆默认不进入 prompt，只有接受后才转为 `ACTIVE`。
- 新增 `KnowledgeBaseEvidenceContextBuilderTest`，覆盖 Agent Memory 绑定 KnowledgeBase 后的中文必需 RAG 触发、英文可选触发、no-evidence 中文 fallback、禁用 RAG 时不检索、长 evidence 截断。
- 新增前端会话工作台 MVP：`/conversations` 页面、会话 / 记忆 API wrapper、顶部导航入口；支持创建会话、发送非流式消息、绑定 / 解绑 KnowledgeBase、查看 summary / trace、手动维护 ACTIVE 记忆、提取 / 接受 / 忽略候选记忆。
- 完成求职展示级前端收口：`/` 首页改为工程链路总览与 smoke 边界入口，`/dashboard` 增加推荐演示路径和会话上下文入口，`/knowledge-bases` 增加 retrieval / evidence / answer model / model call / 命中文档分布可观测卡片，`/conversations` 增强非流式 MVP、摘要级 Trace、Memory 与 KnowledgeBase evidence 的展示口径。
- 完成前端 AI 产品感重点页二次精修：`/` 首页新增系统流程面板和产品级 CTA，`/dashboard` 收敛为 Demo Command Center，`/knowledge-bases` 和 `/conversations` 未登录态不再暴露完整空工作台，并强化 KnowledgeBase evidence / Agent Memory Trace 的观测层级。
- 完成 `/conversations` 核心页 GPT / DeepSeek 风格重做：按 Gemini CLI headless 建议，将页面从三栏工程控制台收敛为左侧会话历史、居中聊天流、底部悬浮 composer 和右侧 Context Inspector 抽屉；Trace / Memory / Summary / KnowledgeBase evidence 继续保留，但退为聊天辅助信息，不再抢占主聊天区。
- 完成前端 UI 文案成熟化收口：按 Gemini CLI 文案建议去掉页面上的“求职 / 面试 / MVP / 演示 / 生产级 / smoke”等内部口径，把首页、Dashboard、KnowledgeBase、Conversations、Agent、登录、上传和文档详情页的说明改为更克制的产品表达；不改后端 API，不新增依赖。
- 完成近期新增代码质量修复：KnowledgeBase Hybrid 检索按 `indexVersion` 过滤并保留 keyword-only hit 元数据；RRF `rrfK` 配置生效；BM25 scorer 改为请求内局部状态；rerank 真正接入 KnowledgeBase RAG 主链路并输出观测字段；会话消息发送改为先生成答案、再用 conversation 行锁连续写入 user / assistant 消息；前端记忆类型改为后端合法枚举并展示 score breakdown。
- 完成当前收口修复：README / showcase 面试材料已同步 Hybrid / Rerank “默认关闭可选增强”的口径；`.env.example` / `.env.demo.example` / `.env.cloud.example` 已补充安全占位配置；`DEMO_SMOKE_RECORD.md` 明确真实 rerank provider 尚未 smoke。
- 完成 tunnel 协作入口修复：MySQL / Qdrant tunnel 详细说明原本已在 `backend/README.md`；本轮已在 `AGENTS.md` 增加一线提醒，明确云 MySQL / Qdrant runtime smoke 前必须启动 `scripts/dev/start-cloud-tunnels.ps1`，普通离线测试和前端未登录态 smoke 不要求 tunnel。
- 完成交付前审查收口修复：`.claude/` 与 `test-hybrid-rag.sh` 已加入 `.gitignore`，保留本地文件但不作为交付内容；会话发送事务已收窄到最终落库阶段，模型调用和 trace best-effort 不再包在同一个长事务内。

## T013 已验证

```powershell
cd backend
mvn -DskipTests compile
mvn "-Dtest=*Context*,*Conversation*,*Memory*" test
mvn "-Dtest=*Rag*,*KnowledgeBase*" test
mvn "-Dtest=*Agent*,*Tool*,*ToolCall*,OpenAi*" test

cd frontend
npm run lint
npm run build
```

验证结果：

- backend compile：PASS。
- Context / Conversation / Memory tests：54 tests，0 failures，0 errors。
- RAG / KnowledgeBase 回归：189 tests，0 failures，0 errors。
- 2026-06-26 Hybrid / Rerank / Conversation 修复回归：`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`npm run lint` PASS；`npm run build` PASS。
- 2026-06-26 收口验证：`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS；`git diff --check` 仅有 CRLF 工作区提示；中文乱码扫描仅命中既有测试正则 / 归档历史 / AGENTS 规则文本；敏感配置扫描确认真实配置只在本地 `.env` 类文件中，未复制密钥值。
- 2026-06-26 Playwright 收口验证：本地启动 `frontend` dev server 于 `http://localhost:3007`，打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations`、`/agent` 桌面页面，以及移动端 `/`、`/conversations`，页面均可渲染；console 主要为既有 `favicon.ico` 404 和 dev Fast Refresh / RSC fallback 日志。
- 2026-06-26 tunnel 文档收口验证：已确认 `backend/README.md` 存在 `scripts/dev/start-cloud-tunnels.ps1`、`13306`、`6333` 说明；本轮只做文档 / 脚本收口，未启动 SSH tunnel，未做云 MySQL / Qdrant runtime smoke。
- 2026-06-26 交付前审查收口验证：`mvn "-Dtest=ConversationMessageServiceImplTest" test` PASS，5 tests，0 failures，0 errors；`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS；`git diff --check` 仅有 CRLF 工作区提示；乱码扫描仅命中 AGENTS 规则文本；脱敏敏感配置扫描确认真实密钥命中位于未跟踪 `backend/.env`，tracked 示例 / yml 为占位、默认本地值或环境变量引用。
- Agent / Tool / ToolCall / OpenAI adapter 回归：186 tests，0 failures，0 errors。
- 2026-06-12 本轮补充连接点测试后：默认离线全量 `mvn test` 已通过，结果为 707 tests，0 failures，0 errors，1 skipped；测试结束阶段出现 scheduled task 访问云端 MySQL 的本机 SSH tunnel 入口被拒日志，说明当时 tunnel / 转发端口未连通，但 Surefire 最终 BUILD SUCCESS。
- 2026-06-12 追加候选记忆后：默认离线全量 `mvn test` 已通过，结果为 702 tests，0 failures，0 errors，1 skipped。
- 2026-06-12 追加实现后：默认离线全量 `mvn test` 已通过，结果为 693 tests，0 failures，0 errors，1 skipped。
- 2026-06-12 此前修复记录：默认离线全量 `mvn test` 曾通过，结果为 683 tests，0 failures，0 errors，1 skipped。
- `DocumentChunkServiceImplTest.shouldReplaceChunksByDeletingVersionBeforeInsert` 已按当前 chunking policy 更新断言：短文本块会先合并，再按默认 `800/120` 切分，因此示例文本应保存为 1 个 chunk，并继续校验 delete-before-insert、version、chunk index、content、hash、offset、token count 和 status。
- 先前报告中的 `DocumentAgentRealProviderRuntimeHarnessTest` 与 `ManualKnowledgeBaseRagProbeTest` 在当前源码树和 git 索引中不存在；clean 前 surefire 目录残留了旧失败报告。执行 `mvn clean test` 后旧报告已清除，默认测试未运行真实 provider 或远程 Qdrant probe。
- frontend lint：PASS。
- frontend build：PASS；`/conversations` 已进入 Next.js route 输出。
- Playwright 打开 `http://localhost:3007/conversations`：PASS；未登录态页面正常渲染，console 仅有既有 `favicon.ico` 404。
- 2026-06-13 前端展示收口验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations` PASS，桌面和移动端首页 / 会话页无明显重叠或空白；console 仅观察到既有 `favicon.ico` 404。
- 2026-06-13 前端 AI 产品感精修验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations` PASS，桌面截图检查重点页面无明显重叠，移动端首页按钮和流程面板可用；console 仅有既有 `favicon.ico` 404。
- 2026-06-13 `/conversations` 核心页精修验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/conversations` 桌面 / 移动端 PASS，未登录态不暴露空工作台；首页 / Dashboard / KnowledgeBase HTTP 200。构建期间重启 dev server 后，Next 静态 chunk 404 消失，未观察到新增页面错误。
- 2026-06-14 `/conversations` 聊天产品页重做验证：Gemini CLI 通过 `-p` headless 模式输出 GPT / DeepSeek 风格建议；`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/conversations` 桌面 / 移动端 PASS，未登录态为居中聊天产品入口，登录态布局烟测确认左侧会话栏、中间聊天流、底部 composer 和右侧 Context Inspector 抽屉可渲染；console 仅有既有 `favicon.ico` 404。
- 2026-06-14 前端 UI 文案成熟化验证：Gemini CLI 通过 stdin + `-p` headless 模式输出文案方向；Codex 落地并拦截过度营销表达。`npm run lint` PASS，`npm run build` PASS；前端高暴露词扫描未命中“求职 / 面试 / MVP / smoke / 生产级 / 演示”等 UI 文案残留，中文乱码扫描未命中。Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations`、`/agent`、`/agent/tools` PASS，并检查移动端 `/`、`/conversations` 无明显溢出；console 仅有既有 `favicon.ico` 404 和一次 dev hot reload RSC fallback，页面已正常渲染。
- 2026-06-13 已按用户授权，通过当前本机 SSH tunnel 入口对云服务器 Docker MySQL 执行 `backend/src/main/resources/sql/007_init_conversation_context.sql`；已确认 `tb_conversation`、`tb_conversation_message`、`tb_conversation_summary`、`tb_context_trace`、`tb_user_memory` 五张表存在。
- 2026-06-13 迁移后 runtime smoke：本机 SSH tunnel 入口到云服务器 Docker MySQL / Qdrant 检查 PASS，backend `/actuator/health` 为 `UP`，frontend `/conversations` 为 HTTP 200；登录态完成创建会话、发送消息、查看 trace、刷新摘要、提取候选记忆、接受候选记忆、再次发送消息验证 ACTIVE 记忆进入 Agent Memory 上下文。第二轮 trace 显示 `Memory=1`、`summaryUsed=是`、最近消息 `2` 条 / `1` 轮、无截断、无 fallback、未跳过模型。
- 2026-06-13 T013 KnowledgeBase-bound evidence runtime smoke：新建临时用户、上传 txt、创建文档、触发解析并等待 `SUCCESS`，创建 KnowledgeBase 并添加文档；KnowledgeBase retrieval 命中 1 条 evidence。随后创建绑定该 KB 的 Agent Memory 会话并发送知识库问题，API trace 显示 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=1`、`documentHitCounts={93:1}`、citation `1`、无 fallback、未跳过模型。
- 2026-06-13 T013 浏览器端到端验证：在 `/conversations` 页面使用绑定 KnowledgeBase `#8` 的会话发送中文“根据知识库”问题，助手回答引用 `t013-ui-kb-0613093939.txt`，Context Trace 显示 `Evidence=1`、`RAG 触发=是`、`RAG 必需=是`、`No Evidence=否`、`Fallback=否`、`模型跳过=否`，展开命中文档分布显示 `#94: 1`。

## T013 当前边界

- 本轮仅小范围修正文档入口、配置示例和本地清理脚本；未调用真实外部服务、未操作远程服务器、未读取或提交 `.env` / secrets / API key。
- `.claude/` 和 `test-hybrid-rag.sh` 已按 local-only 处理并加入 `.gitignore`；未删除本地文件，未执行 `git add` / `git commit` / `git push`。
- 不做后台自动摘要生成、不做真实模型记忆抽取、不持久化完整 prompt / evidence 原文、不接管现有 Agent 主链路、不新增 KnowledgeBase Agent Tool。
- 新 API 与前端工作台先提供非流式 MVP；SSE、Agent 主链路集成和真实模型记忆抽取留到后续阶段。
- 登录态 runtime smoke 已覆盖 Conversation API、summary、trace、candidate memory -> ACTIVE memory -> Agent Memory 上下文选择，以及绑定 KnowledgeBase 后 evidence 进入 Context Trace 的浏览器端到端验证。

## 建议提交切片

- `feat(conversation)`: `ai.context`、`conversation`、`memory` 后端包，`007_init_conversation_context.sql`，对应 controller / service / mapper / schema / unit tests，前端 `/conversations`、`conversation-api.ts`、`memory-api.ts`。
- `feat(rag)`: KnowledgeBase Hybrid / Rerank 增强、BM25 / RRF / rerank 包、retrieval response 观测字段、RAG 配置示例、RAG / KnowledgeBase 相关测试和 `RAG_HYBRID_*` 参考文档。
- `feat(frontend)`: 首页、Dashboard、KnowledgeBase、Agent、登录、上传、文档页等产品化展示和全局样式改动；注意继续保持页面文案不直接使用“求职 / 面试 / MVP / smoke / 生产级”等内部口径。
- `docs(workflow)`: `AGENTS.md`、`backend/README.md`、`docs/ai-dev/CONSTRAINTS.md`、`scripts/dev/start-cloud-tunnels.ps1`、`scripts/dev/cleanup-agent-processes.ps1`，聚焦 tunnel / Gemini / agent 协作和清理规则。
- `docs(showcase)`: 根 `README.md`、`docs/showcase/*`、`STATE.md`、`CURRENT_TASK.md`、`PROGRESS_LOG.md`，用于对外展示口径和当前事实源收口。

## 当前交付状态

- 已按切片完成本地提交：`feat(conversation): add context memory workspace`、`feat(rag): add hybrid retrieval and rerank controls`、`feat(frontend): polish AI workspace presentation`、`docs(workflow): document cloud tunnel workflow`。
- 最终 `docs(showcase)` 切片包含根 `README.md`、`docs/README.md`、`STATE.md`、`CURRENT_TASK.md`、`PROGRESS_LOG.md`、`docs/showcase/*` 和 T013 设计参考资料，用于对外展示口径和当前事实源收口。
- 当前交付整理验证通过：staged diff whitespace check、全仓 diff check、敏感配置扫描、中文乱码扫描、后端 compile、后端重点测试、后端全量单测、前端 lint / build 均通过。
- 本轮仍未做云 MySQL / Qdrant runtime smoke；全量后端测试中的 scheduled outbox tunnel 连接失败日志只说明未连 runtime 环境，当前云链路仍以既有 smoke 文档为准。

## 已提交切片归属

- `feat(rag)` 文件范围：`backend/src/main/java/com/docpilot/backend/ai/rag/**` 中 Hybrid / BM25 / RRF / Rerank 相关新增和响应字段改动，`KnowledgeBaseRagQaServiceImpl` / `KnowledgeBaseRagRetrievalServiceImpl`，`KnowledgeBaseRag*Response`，`application.yml` 中 retrieval / rerank 配置，`backend/.env*.example` 安全占位，RAG / KnowledgeBase 相关测试，以及 `docs/ai-dev/RAG_HYBRID_*`。
- `feat(frontend)` 文件范围：`frontend/app/{page,dashboard,knowledge-bases,agent,agent/tools,documents,login,upload,layout}.tsx`、`frontend/app/globals.css`、`frontend/lib/knowledge-base-api.ts`；`globals.css` 同时支撑 `/conversations` 视觉，第一包单独提交后会话页可编译但完整样式依赖本切片。
- `docs(workflow)` 文件范围：`.gitignore`、`AGENTS.md`、`backend/README.md`、`docs/ai-dev/CONSTRAINTS.md`、`scripts/dev/start-cloud-tunnels.ps1`、`scripts/dev/cleanup-agent-processes.ps1`。
- `docs(showcase)` 文件范围：根 `README.md`、`docs/README.md`、`docs/ai-dev/STATE.md`、`docs/ai-dev/CURRENT_TASK.md`、`docs/ai-dev/PROGRESS_LOG.md`、`docs/showcase/*`，以及 `docs/ai-dev/会话级上下文管理/` / `docs/ai-dev/上下文会话系统设计路线.md` 作为 T013 设计参考资料。
- 跨切片注意：`application.yml` 同时包含 `.env` import 上移和 RAG retrieval / rerank 配置，已随 `feat(rag)` 提交；workflow 文档只解释行为，不重复实现配置。

## 设计文档归属

- `docs/ai-dev/会话级上下文管理/` 和 `docs/ai-dev/上下文会话系统设计路线.md` 当前应作为 T013 设计参考资料保留，适合随 `feat(conversation)` 或单独 `docs(conversation)` 提交。
- 这些设计文档不作为当前任务源；后续 agent 仍以 `STATE.md`、`CURRENT_TASK.md`、`PROGRESS_LOG.md` 和代码 / 测试为准。
- 如后续要压缩文档体量，优先在单独任务中归档或提炼，不在当前交付收口中删除。

## 剩余真实风险

- 全仓状态需以最终 `docs(showcase)` 提交后的 `git status --short` 为准。
- 本轮未启动 SSH tunnel，未执行云 MySQL / Qdrant runtime smoke；`mvn test -DskipITs` 中 scheduled outbox job 的 tunnel 连接失败日志只能说明未连 runtime 环境，不代表云链路验证通过。
- KnowledgeBase Hybrid / Rerank 仍是默认关闭的可选增强；真实 rerank provider 尚未 smoke。
- T013 Conversation / Memory 仍是非流式 MVP，不接管现有 Agent 主链路。

## 上一任务记录：KnowledgeBase RAG 问答质量修复

## 目标

修复“总结整个资料集”类问题中，多文档知识库虽然有 4 个成员文档和 6 条 evidence，但召回几乎被单一文档垄断、chunk 过短、回答模型无法总结整个资料集的问题。

## 本轮已完成

- 后端 chunking 从“短段落直接成 chunk”改为先合并 Markdown / 文本块，再按窗口切分，默认 chunk size 调整为 `800`、overlap 调整为 `120`。
- KnowledgeBase retrieval 扩大向量候选池，对外仍保留请求 `topK`；摘要 / 资料集 / 知识库类问题优先覆盖每个文档，并限制单文档命中数。
- KnowledgeBase retrieval response 新增 `documentHitCounts`，用于观察每个文档的最终命中数量。
- KnowledgeBase QA response 新增 `answerProvider`、`answerModel`、`modelCallCount`，用于确认是否真实调用回答模型。
- KnowledgeBase summary prompt 增加“整体总结 + 按文档标题总结 + 缺失文档证据需说明”的提示。
- RAG vector store 配置兼容 `RAG_VECTOR_PROVIDER` / `RAG_VECTOR_DIMENSION` 别名；未把误用的 `RAG_VECTOR_COLLECTION=http://...` 当 endpoint。
- `.env` 导入职责已从 `application-local.yml` 上移到 `application.yml`，并保留 `SPRING_CONFIG_IMPORT` 覆盖能力；`application-local.yml` 只保留 local profile 的端口、目录和中间件默认值覆盖。
- 前端 KnowledgeBase API 类型已同步新增 response 字段。
- 已按用户授权对目标 KnowledgeBase 文档 `83/84/85/86` 执行 rebuild / reindex：先写入临时验证 collection `docpilot_kb_quality_20260606`，随后将本地 `backend/.env` 切到稳定 collection `docpilot_rag_v2` 并完成重建；KnowledgeBase id 为 `3`，userId 为 `21`。

## 已验证

```powershell
cd backend
mvn "-Dtest=ChunkingServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagPromptBuilderTest,RagVectorStorePropertiesTest,KnowledgeBaseRagControllerTest" test
mvn -DskipTests compile
mvn "-Dtest=*Rag*" test

cd frontend
npm run lint
```

授权后的运行时 reindex 验证：

```powershell
cd backend
mvn "-Dtest=ManualKnowledgeBaseRagReindexTest" "-Dspring.profiles.active=local" test
```

配置整理验证：

```powershell
cd backend
mvn "-Dtest=RagVectorStorePropertiesTest" test
mvn "-Dtest=DocPilotApplicationTests" test
mvn -DskipTests compile
```

验证结果：

- targeted backend tests：36 tests，0 failures，0 errors。
- backend `*Rag*` tests：164 tests，0 failures，0 errors。
- backend compile：PASS。
- frontend lint：PASS。
- config import tests：`RagVectorStorePropertiesTest` 9/9 pass，`DocPilotApplicationTests` 1/1 pass。
- runtime reindex：document `83/84/85/86` rebuild 成功，稳定 collection 为 `docpilot_rag_v2`，chunk / vector 数分别为 `35/35`、`18/18`、`10/10`、`16/16`；“总结资料集”检索 hit 数为 `6`，`documentHitCounts={83:2,84:1,85:1,86:2}`。

## 当前边界

- 本轮没有操作远程 / 云端 MySQL、Qdrant 或服务进程。
- 已通过 Spring service 正式执行 rebuild / reindex，没有直接手写 SQL 或直接改 Qdrant payload。
- 当前本地 `backend/.env` 已配置为 `RAG_VECTOR_STORE_PROVIDER=qdrant`、`RAG_QDRANT_COLLECTION=docpilot_rag_v2`、`RAG_QDRANT_DIMENSION=1024`，并继续使用本机 `.env` 中的真实 endpoint / key；真实 `.env` 不提交。
- 如果当前环境仍使用 mock / fake embedding，语义召回质量仍会受限；本轮代码只让 provider/model/call count 更可观测。

## 下一步候选

- 前端展示 `documentHitCounts`、`answerProvider`、`answerModel`、`modelCallCount`，便于演示时解释检索和模型调用。
- 为 KnowledgeBase QA 补 SSE 流式路径，并保持与非流式 response 字段一致。

## 2026-06-08 环境恢复插曲（DONE）

- 目标：让本地后端通过 SSH tunnel 连接云端 MySQL / Qdrant，并恢复 `/actuator/health`。
- 已确认：`backend/.env` 目标配置为 `MYSQL_HOST=127.0.0.1`、`MYSQL_PORT=13306`、`RAG_QDRANT_ENDPOINT=http://127.0.0.1:6333`。
- 已验证：通过本地 `13306` tunnel 使用 `docpilot_app` 登录 `docpilot` 仍失败，错误来源为 `docpilot_app@172.20.0.1`，说明 Spring 配置解析生效，但远程 MySQL 用户认证 / host 授权仍不匹配。
- 已由 `hk-ops` 确认远程 MySQL 数据目录备份有效：`/data/docpilot/backups/mysql-datadir-20260607-010918.tar`，基础 tar 完整性校验通过。
- 已由 `hk-ops` 修复 `docpilot_app` 认证 / 授权，保留 `docpilot_app` 对 `docpilot` schema 的访问能力；未修改业务表结构或业务数据。
- 已由 `hk-ops` 将 Docker MySQL host 端口从公网监听收口为远程本机 `127.0.0.1:13306` 监听，`docpilot-mysql` 仍为 healthy。
- 本地已恢复 SSH tunnel：`127.0.0.1:13306` 连接远程 MySQL，`127.0.0.1:6333` 连接远程 Qdrant；MySQL CLI `SELECT 1` 成功，Qdrant `/collections` 可达。
- 后端已用 local profile 启动，HikariPool 初始化成功，未再出现 MySQL `Access denied` 或 Hikari timeout；`GET http://localhost:8081/actuator/health` 返回 `UP`。
- 已完成最小业务 smoke：注册临时用户、上传 txt、创建文档、创建解析任务、解析达到 `SUCCESS`、RAG retrieve 命中、RAG QA 返回 citation 且回答包含本次 smoke marker；记录 ID 为 user `88`、file `89`、document `87`、parseTask `83`。
- 已新增 `scripts/dev/start-cloud-tunnels.ps1` 固化本地 MySQL / Qdrant tunnel 启动与连通性检查；`backend/README.md` 已同步说明云 MySQL / Qdrant 不再走公网直连。
- 已定位并修复前端多文档问答报 `knowledge base RAG answer generation failed`：复现确认 KnowledgeBase retrieve 成功但真实回答模型在约 12 秒 read timeout 后失败；后端已为 KnowledgeBase QA 增加 answer 生成失败兜底，保留 retrieval / citations 返回，并将本机 `backend/.env` 的 `AI_REAL_READ_TIMEOUT_MS` 调整为 `30000`。复验 KnowledgeBase QA code `0`、citation `2`、modelCallCount `1`，记录 ID 为 user `90`、KB `5`、documents `90/91`。
