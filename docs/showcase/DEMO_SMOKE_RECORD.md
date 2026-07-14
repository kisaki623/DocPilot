# DocPilot Demo Smoke Record

> Last updated: 2026-07-14

本文件记录用于面试 / 展示准备的 demo smoke 证据摘要，并明确每次验证的能力边界。

## 2026-07-14 Agent Quality Console DB-backed Internal Console

状态：PASS（DB migration + internal admin + persisted QualityRun + import hygiene + DB-backed domain trends + UI/API）

Runner / 验证：

- `backend/src/main/resources/sql/011_init_quality_console_persistence.sql`
- `scripts/smoke/agent-quality-eval-smoke.ps1 -Mode run`
- 临时后端 `18081` + 临时前端 `3007` 管理员 / 普通用户浏览器验证

Marker:

- `docpilot-agent-quality-eval-20260714151238-756d91`
- closeout：`quality-console-closeout-20260714160116`

已验证：

- 真实开发库已执行 011，并二次执行确认幂等；`tb_quality_run`、`tb_quality_run_gate`、`tb_quality_run_case`、`tb_quality_import_event` 与 `tb_user.is_internal_admin` 存在。
- `zeus` 已作为唯一 ACTIVE 用户被标记为内部管理员；临时 smoke 管理员完成自动化验证。
- 未登录 Quality API 返回业务 `401`，普通用户返回业务 `403`，内部管理员可读取 runs/detail/trends 并导入 artifact。
- 真实 agent quality eval marker 导入后成为最新 DB-backed run：`status=PASS`、`dataSource=artifact_import`、`gateCount=1`、`evalCaseCount=19`。
- 导入器已在 `limit` 截断前过滤 `docpilot-import-*` 测试 marker，单测改用 `@TempDir` 隔离；真实 API 验证 `limit=1` 下 `firstRunIsTestMarker=false`。
- DB-backed `/api/quality/trends?limit=50` 已恢复领域趋势：`domainTrends.memoryQuality` 覆盖 4 个 run，`domainTrends.ragRepresentativeEval` 覆盖 12 个 run。
- `/quality?autoload=1` 可见最新 run、数据来源和导入信息；普通用户导航隐藏“质量”，直接访问显示无权限；桌面和 `390px` 移动端无横向溢出，console error 为 `0`。
- `/quality?autoload=1` 点击“趋势”后可见 `Memory quality smoke` 与 `RAG representative eval` 两张领域卡；桌面和 `390px` 移动端无横向溢出，console error 为 `0`。

边界：Quality Console 仍是内部控制台，默认配置不开启；本次验证使用临时本地端口和开发库，不是生产运维系统。artifact 位于 ignored 目录，只保留 marker、状态、计数和脱敏 id，不提交 token、密码、raw prompt、answer、evidence context、连接串或云地址。历史残留的 `docpilot-import-*` 测试 marker 不做破坏性删除，但默认导入、runs/detail/trends 已隐藏或跳过，避免影响真实质量控制台验收。

## 2026-07-14 Conversation Citation Source UI

状态：PASS（Conversation 回答卡片引用来源交互）

验证：

- 临时后端 `18081` + 临时前端 `3007`
- Playwright 浏览器验证

Marker:

- `conversation-citation-expand-20260714172419`

已验证：

- 受控 Conversation 生成 `3` 条返回 evidence / citations，回答正文实际只出现 `[1]` / `[2]`，用于验证“实际引用少于召回证据”的常见场景。
- 回答卡片来源摘要区分 `2` 实际引用、`3` 召回证据、`3` 命中文档。
- 默认只展示 2 张正文实际引用卡；点击“查看全部返回证据（3）”后展示 3 张完整证据卡。
- 点击正文 `[1]` 后聚焦并高亮对应来源卡片 `citation-567-1`。
- 桌面、`390px`、`320px` 视口横向溢出均为 `0`，console error 为 `0`。

边界：本次只验证 Conversation 回答卡片 citation UI，不覆盖 KnowledgeBase 页、文档详情页或 Agent 页的视觉改造；artifact 位于 ignored `backend/target/conversation-citation-expand-20260714172419/ui-citation-browser-check.json`，只保留 marker、id 和计数，不提交 token、密码、prompt、answer、evidence 全文、连接串或云地址。

## 2026-07-12 High Intensity Acceptance Layer 1

状态：PASS

Runner:

- Parser：`scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run`
- Grounding：`scripts/smoke/conversation-grounding-smoke.ps1 -Mode run`
- Cloud quality：`scripts/smoke/cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate -EnableKnowledgeBaseAgentGate`

Marker:

- parser real-chain：`docpilot-parser-real-chain-20260712212339-021ca3`
- conversation grounding：`docpilot-conversation-grounding-20260712212500-d26151`
- cloud quality：`docpilot-cloud-quality-20260712212603-173e7d`

已验证：

- PDF / HTML / DOCX 上传解析、chunk 可见、Qdrant 检索、QA citation 均 PASS。
- unsupported、empty、corrupted 负向文件边界按预期失败并返回脱敏错误码。
- GroundingPolicy 6 个路由 case PASS。
- 综合 cloud quality 覆盖上传解析索引、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、no-evidence、Conversation Trace、KnowledgeBase Agent、跨用户权限隔离、前端关键路由和前端交互 gate。

边界：这是高强度验收的第一层真实链路门禁，不是完整 T01-T47 全量通过。固定业务语料矩阵和 KnowledgeBase 生命周期 T22-T26 已在后续 gate 中单独通过；长会话摘要、弱网并发、多标签页和浏览器缩放 UI 仍待执行。artifact 位于 ignored 目录，只保留 marker、状态、计数和脱敏 id，不提交 token、密码、raw prompt、answer、evidence context、连接串或云地址。

## 2026-07-13 High Intensity Fixed Corpus And KB Lifecycle Matrix

状态：PASS（fixedBusinessCorpus + knowledgeBaseLifecycle gates）

Runner:

- `scripts/smoke/high-intensity-fixed-corpus-smoke.ps1 -Mode run -SkipFrontend`

Marker:

- `docpilot-high-intensity-fixed-corpus-20260713004622-113df1`

已验证：

- T02 串行重复上传 PASS。
- T06-T15 固定业务语料 RAG 质量矩阵全部 PASS，覆盖准确数字召回、近义表达、废弃草案冲突、跨文档计算、多文档总结、多跳审批、hard negative、strict no-evidence 和 prompt injection。
- T22-T26 KnowledgeBase 生命周期 API/RAG scope gate PASS：加入后立即可查、移出后 no-evidence / 0 citation、重新加入恢复、同一文档跨两个 KB 时移出 KB-A 不影响 KB-B；T26 disposable 文档删除后 KB detail 0 文档、retrieve / QA no-evidence 且 0 citation，文档详情不可读。
- 该 gate 同时验证 membership 变化不改变 MySQL chunk 数或 Qdrant point 数，并且不修改 `KB_CORE` / `KB_NOISY` 的固定质量矩阵；T26 的 MySQL chunk / Qdrant point 残留只记录为观测计数，不声明物理删除。
- `REA-20260712-P1-030` 与 `REA-20260713-P1-031` 均已通过真实 fixed corpus gate 验证：T08 / T11 / T12 的回答完整性、citation 文档覆盖和 citation support 均恢复。

边界：本次命令显式 `-SkipFrontend`，因此 run overallStatus 为 `REVIEW`；该证据只证明 fixed corpus API/RAG gate 与 T22-T26 KB lifecycle scope gate 已通过，不代表完整 T01-T47 验收完成。artifact 位于 ignored `backend/target/high-intensity-acceptance/.../artifact.json`，只保留脱敏摘要，不提交 raw prompt、answer、evidence context、日志、凭据或源文件。

## 2026-07-13 Conversation Recent Turns Context Gate

状态：PASS（API / Trace gate，frontend skipped）

Runner:

- `scripts/smoke/conversation-grounding-smoke.ps1 -Mode run -SkipFrontend`

Marker:

- `docpilot-conversation-grounding-20260713010452-f8e612`

已验证：

- Conversation grounding runner 已从 6 个路由 case 扩展为 8 个 case。
- T27 `RECENT_TURNS` 同会话上下文 PASS：先记录项目代号“蓝桥”，隔轮追问时 trace 为 `MODEL_ONLY`、`recentMessageCount>=2`、`citationCount=0`，回答包含该代号。
- T28 跨会话隔离 PASS：新建另一 `RECENT_TURNS` 会话直接追问项目代号时 trace 为 `MODEL_ONLY`、`recentMessageCount=0`、`citationCount=0`，回答不包含前一会话代号。
- 原有 no-KB、AUTO_RAG、STRICT_KB 和 evidence citation 路由 case 同轮继续 PASS；artifact redaction scan PASS，常用端口无 LISTEN 残留。

边界：本次是 T27/T28 的 API / Trace 自动化证据，命令显式 `-SkipFrontend`；不代表前端会话 UI、长会话摘要、Memory 生命周期、Agent ToolCall 或弱网并发已通过。artifact 位于 ignored `backend/target/conversation-grounding/.../artifact.json`，不提交 raw prompt、raw answer、evidence context、token、连接串或云地址。

## 2026-07-13 Agent Memory Candidate, Sensitive Rejection And Lifecycle Gate

状态：REVIEW（memoryQuality gate；frontend skipped；strict per-memory disable not implemented）

Runner:

- `scripts/smoke/memory-quality-smoke.ps1 -Mode run -SkipFrontend`

Marker:

- `docpilot-memory-quality-20260713015241-320bed`

已验证：

- T31 删除 / 会话级禁用自动化证据成立：ACTIVE `PREFERENCE` memory 在 `AGENT_MEMORY` Trace 中 `memoryCount=1` 且目标 `use_count` 增加 1；`RECENT_TURNS` 会话 `memoryEnabled=false`、Trace `memoryCount=0` 且 `use_count` 不变；delete 后 API / DB 状态均为 `DELETED`，ACTIVE 计数归零，新的 `AGENT_MEMORY` Trace 不再选入该 memory。
- T29 Agent Memory 候选确认 PASS：项目实现偏好先生成 `PREFERENCE` / `SUGGESTED` 候选；accept 前不在 ACTIVE list，accept 后同一 `memoryId` 进入 ACTIVE list；新会话 Trace 中 `memoryCount=2`、`contextSourceCounts.userMemory=2`、`memoryTypes=[PREFERENCE, TECH_CONTEXT]`。
- T30 敏感记忆拒绝 PASS：无敏感片段的 Java 后端偏好正对照可生成候选；带 `api key` 标签和运行时拼接 `sk-...` 假凭据形状的偏好文本不生成候选，不进入 ACTIVE / SUGGESTED list，`tb_user_memory` 按 source conversation 计数为 0。
- 原有 Memory governance 同轮继续 PASS：answer-style / task-goal 候选、accept / ignore 分层、冲突治理、冲突 accept 阻断、keep / replace / merge、敏感 edit 拦截和 ACTIVE edit 均通过。
- artifact redaction scan PASS，常用端口无 LISTEN 残留。

边界：本次 run 显式 `-SkipFrontend`，且当前产品 / 后端没有 per-memory `DISABLED` 状态、禁用 / 恢复 API 或用户全局长期记忆开关，因此严格 T31“禁用某条记忆”仍为 REVIEW；该证据证明的是 `RECENT_TURNS` 会话级禁用与 delete lifecycle，不代表前端 Memory 管理页、T32 长会话摘要、Agent ToolCall 或弱网并发 UI 已通过。artifact 位于 ignored `backend/target/memory-quality/.../artifact.json`，不提交用户消息、memory content、fake key、prompt、answer、evidence context、token、连接串或云地址。

## 2026-07-12 Quality Console Memory / RAG Trend View

状态：PASS

Runner:

- 后端：`mvn "-Dtest=QualityArtifactServiceImplTest,QualityControllerTest,RerankRepresentativeEvalSmokeScriptSafetyTest" test`
- 前端：`npm run lint`、`npm run build`
- API：临时本地后端 `SERVER_PORT=18081`、`APP_QUALITY_CONSOLE_ENABLED=true`、`AI_MODE=mock`，调用 `/api/quality/trends?limit=20` 与 `/api/quality/runs?limit=20`
- 浏览器：临时 frontend `3007`，Playwright 打开 `/quality?autoload=1` 并点击“趋势”

Marker:

- memory quality：`docpilot-memory-quality-20260712155609-7ba60d`
- rerank representative candidate：`docpilot-rerank-representative-representative-rerank-20260712152212-2e0f81`

已验证：

- `/api/quality/trends` 返回 `domainTrends.memoryQuality` 与 `domainTrends.ragRepresentativeEval`。
- Memory 趋势最新状态 `PASS`，runCount `1`。
- RAG representative 趋势最新状态 `PASS`，runCount `12`，`caseCount=12`、`upliftCaseCount=10`、`strictImprovementCaseCount=2`、`targetCoverageRegressionCount=0`。
- `/api/quality/runs?limit=20` 最近 20 条 marker 全唯一，避免 `latest-summary.json` 与 run artifact 同 marker 重复计数。
- 页面可见“质量趋势 / 领域趋势 / Memory quality smoke / RAG representative eval”；console error 为 `0`，`390px` 移动端横向溢出为 `0px`。

边界：这是内部 Quality Console 趋势展示和小样本 smoke / eval 证据，不是线上 SLA、跨机器质量仓库或大规模 benchmark。输出只保留 marker、状态和计数摘要，不提交 token、注册密码、raw artifact、prompt、answer、evidence context、日志原文、连接串或云地址。临时 18081 后端和 3007 前端已清理，无端口残留。

## 2026-07-12 Quality Console Conversation Grounding API Visibility

状态：PASS

Runner:

- 临时本地后端 `SERVER_PORT=18081`、`APP_QUALITY_CONSOLE_ENABLED=true`、`AI_MODE=mock`
- API：`/api/quality/runs`、`/api/quality/runs/{marker}`、`/api/quality/eval-cases`
- 浏览器：临时 frontend `3007`，Playwright 打开 `/quality?autoload=1`

Marker:

- conversation grounding 可见性历史 marker：`docpilot-conversation-grounding-20260712183609-a15fef`
- conversation grounding 最新 route marker：`docpilot-conversation-grounding-20260713212058-5915ed`

已验证：

- Quality runs 可见历史 marker，source 为 `backend/target/conversation-grounding`。
- 历史 Run detail 返回 `conversationGrounding` gate，`caseCount=6`、`evalCaseCount=6`；2026-07-13 最新 route smoke 已扩展为 9/9 case PASS。
- Eval Catalog 中 Conversation grounding case 可关联 smoke marker，latest status 为 `PASS`。
- 页面可见 marker、source root 和 Artifact 分区 catalog case；console error 为 `0`，`390px` 移动端无横向溢出。

边界：本次只验证 Quality API / 页面读取 ignored artifact 和 catalog 关联；不上传文档、不调用 provider，不证明完整浏览器 E2E。输出只保留 marker、状态和计数摘要，不提交 token、注册密码、raw artifact、prompt、answer、evidence context、日志原文、连接串或云地址。临时 18081 后端和 3007 前端已清理，无端口残留。

## 2026-07-12 Conversation Grounding Route Smoke

状态：PASS

Runner:

- `scripts/smoke/conversation-grounding-smoke.ps1 -Mode run`
- `mvn "-Dtest=ConversationGroundingSmokeScriptSafetyTest" test`

Marker:

- conversation grounding：`docpilot-conversation-grounding-20260712183609-a15fef`

已验证：

- 未绑定 KnowledgeBase 的普通问题走 `MODEL_ONLY`：`ragTriggered=false`、`ragRequired=false`、`evidenceCount=0`、`llmCalled=true`、`modelSkipped=false`。
- 未绑定 KB 即使请求 `STRICT_KB` 也归一为 `MODEL_ONLY`，不触发资料不足拒答。
- 绑定 KB 的 `AUTO_RAG` 明显闲聊不触发 RAG；非显式资料问题无 evidence 时 fallback 到模型，`routeDecision=AUTO_NO_EVIDENCE_MODEL`。
- 绑定 KB 的 `AUTO_RAG` 显式资料问题无 evidence 时安全拒答，`routeDecision=AUTO_REQUIRED_NO_EVIDENCE_FALLBACK`、`llmCalled=false`、`modelSkipped=true`。
- `STRICT_KB` 无 evidence 时安全拒答，`llmCalled=false`、`modelSkipped=true`、`routeDecision=STRICT_NO_EVIDENCE_FALLBACK`。
- `AUTO_RAG` 命中 evidence 时返回 citation，`routeDecision=AUTO_RAG_EVIDENCE`。
- 本轮 runner 为 9/9 case PASS，包含未绑定 KB、STRICT 归一、RECENT_TURNS 同会话 / 跨会话、AUTO smalltalk、AUTO optional no-evidence、AUTO required no-evidence、STRICT no-evidence 和 AUTO evidence citation。

边界：这是 Conversation grounding policy 的小规模真实链路防回归 smoke，不是大规模对话质量 benchmark。artifact 位于 ignored 的 `backend/target/conversation-grounding/.../artifact.json`，只保存路由枚举、布尔值、计数和脱敏 id，不提交 token、密码、raw prompt、raw answer、raw evidence、provider output、连接串或云地址。

## 2026-07-12 Memory Governance Smoke

状态：PASS

Runner:

- `mvn "-Dtest=UserMemoryServiceImplTest,RuleBasedMemoryExtractionServiceTest,MemoryQualitySmokeScriptSafetyTest" test`
- `scripts/smoke/memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- memory quality：`docpilot-memory-quality-20260712155609-7ba60d`

已验证：

- 离线测试覆盖相似 / 重复 / 冲突治理、跨用户 resolve 隔离、敏感内容手动创建 / merge / 系统抽取拦截、一次性指令抑制、assistant RAG evidence 不沉淀。
- 真实 memory smoke 的 `memoryQuality` gate PASS：候选抽取、accept / ignore 分层、冲突提示、冲突 accept 阻断、`KEEP_ACTIVE` / `REPLACE_ACTIVE` / `MERGE_WITH_ACTIVE`、敏感 edit 拦截、ACTIVE edit 均通过。
- Conversation Trace 同时验证 `contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6`，Memory 与 RAG evidence 不互相污染；权限隔离、frontend routes、cleanup 和 artifact redaction 均 PASS。

边界：这是规则式 Memory Governance 和真实链路 smoke，不代表真实模型长期记忆质量成熟，也不是大规模 memory extraction benchmark。artifact 位于 ignored 的 `backend/target/memory-quality/.../artifact.json`，不提交用户文本全文、prompt、evidence context、token、连接串或云地址。

## 2026-07-12 Citation Locator UI Refresh

状态：PASS

Runner:

- `npm run lint`
- `npm run build`
- `scripts/smoke/cloud-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- cloud quality：`docpilot-cloud-quality-20260712154804-0540c6`

已验证：

- 文档详情、KnowledgeBase、Conversation 和 Agent 页面均已展示 citation locator / metadata：文档名、`sourceLocator` / 页码 / section path、chunk/version、block / structure。
- 完整真实 cloud quality PASS：单文档 RAG、KnowledgeBase RAG、短文档 RAG、answer grounding、no-evidence、Conversation Trace、权限隔离、frontend routes、cleanup 和 artifact redaction 无回退。

边界：这是 locator 可见性和真实链路回归，不是 PDF 坐标级引用、OCR 版面理解或大规模 citation benchmark。artifact 位于 ignored 的 `tmp-e2e/docpilot-cloud-quality-smoke/.../artifact.json`，不提交文档全文、prompt、evidence context、token、连接串或云地址。

## 2026-07-12 Rerank Representative Eval Smoke

状态：PASS

Runner:

- `scripts/smoke/rerank-representative-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- hybrid-only baseline：`docpilot-rerank-representative-representative-hybrid-20260712151858-5543fd`
- hybrid + rerank candidate：`docpilot-rerank-representative-representative-rerank-20260712152212-2e0f81`

已验证：

- 代表语料 gate 覆盖 6 份临时文档、12 个脱敏 case：合规、审计、财务、安全、中文问法、干扰文档和 2 个 no-evidence case。
- baseline 与 candidate 整体均 PASS；candidate `rerankApplied=true`、`rerankModel=qwen3-rerank`、`rerankFailureReasons=[]`。
- 10/10 target case 保持覆盖，2/2 no-evidence case 保持拒答；`targetCoverageRegressionCount=0`、`citationLeakageCount=0`、`noEvidenceRegressionCount=0`。
- 排序 / 质量对比：`targetRerankAppliedCaseCount=10`、`strictImprovementCaseCount=2`、`upliftCaseCount=10`；中文 case 通过 request-scoped multi-query 与受控领域词 rewrite 获得目标覆盖。
- artifact redaction PASS；本轮还修复了 summary intent 泛词绕过 no-evidence gate、PowerShell 5.1 中文 JSON 编码和 caseId 误触发 token 脱敏正则的问题。

边界：这是小样本真实链路代表 eval，不是大规模 ranking benchmark、线上 SLA 或通用语义 reranker 评测。artifact 位于 ignored 的 `backend/target/rag-quality/rerank-representative-eval/latest-summary.json`，不提交 query 原文、文档全文、回答文本、prompt、evidence context、token、连接串或云地址。

## 2026-07-12 ParseTask Recovery / Bailian Rerank Uplift Refresh

状态：PASS

Runner:

- `scripts/smoke/rerank-effect-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- hybrid-only baseline：`docpilot-rerank-effect-hybrid-20260712015151-46c631`
- hybrid + rerank candidate：`docpilot-rerank-effect-rerank-20260712015353-cc21a9`
- parser real chain：`docpilot-parser-real-chain-20260712015555-91d1fd`
- parser/status refresh：`docpilot-parser-real-chain-20260712154120-5bb049`

已验证：

- 阿里云百炼 `qwen3-rerank` 真实 provider 调用成功，candidate `rerankApplied=true`、`rerankModel=qwen3-rerank`、`rerankFailureReason=""`。
- hard rerank fixture 使用独立 target / support / distractor 三文档：baseline 中 distractor 排第 1、target 排第 2；启用 rerank 后 target 排第 1、support 排第 2、distractor 排第 3，`hardUpliftObserved=true`。
- KB retrieve / QA citation 覆盖未下降，core RAG、no-evidence 与权限隔离无回退；artifact 只保存 id、rank、count、score summary 等脱敏摘要。
- ParseTask / parser 真实链路回归通过：PDF / HTML / DOCX 均 parse、retrieve、QA citation 和 source locator 通过，`sourceLocatorCount=3/3`，parser boundary `4/4`。
- 前端文档详情页已展示 ParseTask status 恢复卡：当前阶段、恢复建议、stale、retry/reparse、consume/outbox 摘要和“禁止纯 Document.content 重建索引”的安全边界可见；前端 lint / build 均通过。
- 定向 ParseTask / rerank 测试 40 tests PASS；后端全量默认测试 `mvn test -DskipITs` PASS（920 tests，0 failures，5 skipped）。

边界：rerank 结论仍是“小样本 hard fixture uplift 证据”，不是大规模 ranking benchmark；ParseTask 恢复链路本轮验证了 fail-closed 策略、状态可观测和正常真实解析链路未回退，不代表已完成自动重放、复杂迁移或线上 SLA。artifact 位于 ignored 的 `backend/target/...`，不提交文档全文、模型原始输出、prompt、evidence context、token、连接串或云地址。

## 2026-07-11 Max Stress Real Chain Audit

状态：PASS / REVIEW

Runner:

- `scripts/smoke/real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- `scripts/smoke/rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- `scripts/smoke/rerank-effect-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- `scripts/smoke/memory-provider-extraction-smoke.ps1 -Mode run`
- `scripts/smoke/agent-quality-eval-smoke.ps1 -Mode run`
- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- 完整真实用户审计：`docpilot-real-user-qa-20260711170544-dff948`
- 代表语料 / 真实 QA：`docpilot-rag-real-qa-20260711171137-ed38a0`
- rerank 对照 baseline / candidate：`docpilot-rerank-effect-hybrid-20260711171329-bda3dc` / `docpilot-rerank-effect-rerank-20260711171449-522a4c`
- Memory provider：`docpilot-memory-provider-20260711172435-14083e`
- Agent quality eval：`docpilot-agent-quality-eval-20260711171903-fae364`
- Parser real chain：`docpilot-parser-real-chain-20260711171912-a8e65c`

已验证：

- 真实用户全链路、自然语料 25 case、multi-query、answer grounding、no-evidence、Memory quality、Conversation Trace、权限隔离、frontendInteraction、artifact redaction 均 PASS。
- RAG Real QA Eval 覆盖 representative corpus、real QA hard gate、semantic gate、real provider faithfulness；真实回答 provider 为 openai-compatible / qwen-plus，关键 scope 均有 model call。
- Memory provider 小样本固定 6-call PASS，`casePassRate=1.0000`，`rawProviderOutputStored=false`；Agent Quality eval offline runner PASS。
- Parser 真实链路中 PDF / HTML / DOCX 均 parse、retrieve、QA citation 和 source locator 通过，`sourceLocatorCount=3/3`，parser boundary `4/4`。

边界：rerank 对照本轮为 REVIEW；核心 RAG、安全与 no-evidence 无回退，但当前本机 rerank model 返回 `NotFound`，candidate 降级为 `identity`，`rerankApplied=false`，不能宣称真实 rerank provider 实效已验证。Next 16 major 依赖升级未纳入本轮。artifact 位于 ignored 的 `backend/target/...`，不提交文档全文、模型原始输出、prompt、evidence context、token、连接串或云地址。

## 2026-07-11 Real User QA Full Chain Refresh

状态：PASS

Runner:

- `scripts/smoke/real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- `scripts/smoke/rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- 首轮失败诊断：`docpilot-real-user-qa-20260711155558-573a81`
- 自然语料修复验证：`docpilot-rag-natural-corpus-20260711160322-1cbcbc`
- 完整真实用户审计：`docpilot-real-user-qa-20260711160913-98a440`
- parser 专项：`docpilot-parser-real-chain-20260711161514-6c4786`

已验证：

- 完整真实用户审计覆盖 tunnel、backend health、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、短文档 RAG、自然语料、answer grounding、no-evidence、Conversation Trace、Memory quality、权限隔离、frontend routes / interaction、cleanup 和 artifact redaction。
- 自然语料首轮暴露 `finance-expense-invoice-compare` 的 `answerFactExpression` 误杀；修复后 `casePassRate=1`、`answerFaithfulnessPassCount=11/11`、`citationPhraseSupportPassCount=22/22`、`distractorCitationFreeCount=25/25`。
- ParseTask status 真实 API smoke 验证成功解析后仍返回 `safeReindexAllowed=false`、`contentOnlyReindexAllowed=false`，未暴露纯 `Document.content` 重建索引入口。
- parser 专项中 PDF / HTML / DOCX 均 parse、direct retrieve、QA retrieval、citation 和 source locator 通过；parser boundary `4/4`。

边界：本轮是真实 provider / Qdrant / MySQL / 前后端本地运行的小规模 smoke，不代表大规模 relevance benchmark、线上 SLA、OCR / 扫描件能力或商业 SaaS 完整验收。artifact 均位于 ignored 的 `backend/target/...` 或 `tmp-e2e/...`，不提交文档全文、回答文本、prompt、evidence context、token、连接串或云地址。

## 2026-07-11 Document Parser Real Chain Refresh

状态：PASS

Runner:

- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- `docpilot-parser-real-chain-20260711152944-1db28d`

已验证：

- tunnel、backend、frontend、临时用户、PDF / HTML / DOCX fixture、异步解析、Qdrant direct retrieve、QA retrieval、citation、source locator、parser boundary 和 artifact redaction 均通过。
- 三类文件均 `parseStatus=SUCCESS`；累计 `chunkCount=7`，其中 HTML fixture 触发多 chunk，`expectedMinChunks=2`、`multiChunkVerified=true`。
- `directRetrieveHitCount=3/3`、`qaRetrievalHitCount=3/3`、`citationCount=3/3`、`sourceLocatorCount=3/3`。
- parser boundary 负向检查 `4/4` 通过；unsupported / empty / corrupted parser case 均返回预期脱敏失败码。
- 本轮启动的 local tunnel、backend、frontend 已清理；`3007` / `8081` 等常用端口复查为空。

边界：这是小规模真实链路 parser / RAG smoke，证明文本型 PDF、本地 HTML 和 DOCX 能走通上传、异步解析、chunk、Qdrant 检索、QA citation 与来源定位；不代表 OCR、扫描件识别、复杂版面还原、旧 `.doc` 支持、外部网页抓取、大规模解析 benchmark 或线上 SLA。artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/.../artifact.json`，不提交文档全文、回答文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-10 Cloud Quality RAG Main Flow Recovery

状态：PASS

Runner:

- `scripts/smoke/cloud-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007 -EnableFrontendInteractionGate -EnableKnowledgeBaseAgentGate`

Marker:

- `docpilot-cloud-quality-20260710200547-6dec4e`

已验证：

- tunnel、backend health、认证、双文档上传 / 异步解析、chunk 质量和 MySQL / Qdrant 一致性均通过。
- 单文档、KnowledgeBase 与短文档 RAG 均返回 grounded evidence / citation；Conversation / Memory、KnowledgeBase Agent、权限隔离、浏览器 quote-first 与前端 console error 门禁均通过。
- 先前的真实模型失败已定位为当前本机 provider/model 的非流式读取窗口不足；RAG 服务已具备受限重试与脱敏诊断，当前本机运行调优后本次完整 gate 通过。
- Next dev 的 loopback 访问源兼容已验证：保持既有 `127.0.0.1:3007` smoke 命令，KnowledgeBase RAG 双 citation 可见且浏览器 console error 为 `0`。
- artifact redaction 与 cleanup PASS；本轮启动的 local tunnel、backend、frontend 已清理。

边界：这是当前本机 provider/model 与临时脱敏 smoke 数据上的真实闭环证据，不代表所有模型供应商、持续高并发或线上 SLA。读取窗口调优仅在 ignored 本机 `.env` 中生效，未改项目默认值、示例配置、数据库或云端服务；artifact 位于 ignored 的 `tmp-e2e/docpilot-cloud-quality-smoke/.../artifact.json`，不提交原文、prompt、答案、凭据、连接串或云地址。

## 2026-07-10 Document Parser Multi-Chunk Real Chain

状态：PASS

Runner:

- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- `docpilot-parser-real-chain-20260710143019-38705a`

已验证：

- HTML fixture 以脱敏长正文触发默认 `800/120` chunk 策略，实际 `chunkCount=5`、`expectedMinChunks=2`、`multiChunkVerified=true`。
- HTML 的 Qdrant direct retrieve 与 QA retrieval 均返回 `5` 个 hits / citations；PDF / HTML / DOCX 三类文件累计 `7` 个 chunk，上传、异步解析、citation、source locator、parser boundary 和 artifact redaction 均通过。
- `fixtureStructureCoverage.expectedSignals=11`、`coveredSignals=11`、`missingSignals=0`、`allCovered=true`；环境稳定，runner 已清理本轮 local tunnel、backend、frontend。

边界：此证据证明 parser 真实链路不再只依赖单 chunk fixture，并且多 chunk HTML 仍可检索和引用；当前只验收至少一条 citation 来源定位，未主张所有跨 block chunk 都有精确 locator，也不代表 OCR、扫描件、旧 `.doc`、复杂版面、外部网页抓取或大规模解析 benchmark。artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/.../artifact.json`，不提交文档全文、回答文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-10 Document Parser Natural HTML Noise Isolation

状态：PASS

Runner:

- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- `docpilot-parser-real-chain-20260710142418-09566e`

已验证：

- tunnel、backend health、frontend root route、临时用户注册、PDF / HTML / DOCX 上传、异步解析、chunk、Qdrant direct retrieve、QA retrieval、citation、source locator、parser boundary 和 artifact redaction 均通过。
- HTML fixture 额外包含本地 `aside` 辅助栏、`nav` 和脚本噪声；artifact 只记录 `html_noise_excluded=true` 对应的安全结构信号，不保存解析文本或噪声原文。
- `fixtureStructureCoverage.expectedSignals=10`、`coveredSignals=10`、`missingSignals=0`、`allCovered=true`；三类文件均 `chunkCount=1`、direct / QA retrieval hit、citation 与 source locator 为真。
- parser boundary 负向检查 `4/4` 通过，`environmentUnstable=false`；本轮启动的 local tunnel、backend、frontend 已清理。

边界：这是文本型 PDF / HTML / DOCX 的单 chunk 小样本真实链路证据，证明本地 HTML 噪声隔离能进入上传、解析、索引和 RAG quality gate；不代表 OCR、扫描件、旧 `.doc`、复杂版面理解、外部网页抓取或大规模解析 benchmark。artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/.../artifact.json`，不提交文档全文、回答文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-10 Document Parser Structure Coverage Real Chain

状态：PASS

Runner:

- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- `docpilot-parser-real-chain-20260710001619-a1b510`

已验证：

- tunnel、backend health、frontend root route、临时用户注册、PDF / HTML / DOCX fixture 上传、异步解析、chunk、direct retrieve、QA retrieval、QA citation、source locator、parser boundary 和 artifact redaction 均通过。
- 三类文件均为 `parseStatus=SUCCESS`、`chunkCount=1`、`directRetrieveHit=true`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`。
- 新增结构覆盖 artifact 摘要：`fixtureStructureCoverage.expectedSignals=9`、`coveredSignals=9`、`missingSignals=0`、`allCovered=true`，覆盖 PDF 文本 / 页码来源、HTML 标题 / 表格 / 链接 / 列表、DOCX 标题 / 表格 / 列表。
- direct / QA 诊断摘要：`directRetrieveOkCount=3`、`qaRetrieveOkCount=3`、最大重试次数均为 `1`，`environmentUnstable=false`。
- parser boundary 负向检查通过：不支持格式上传拒绝、空白 TXT、损坏 PDF 和损坏 DOCX 均返回预期脱敏失败码，`negativeCasePassCount=4/4`。
- 本轮启动的本地 tunnel、backend、frontend 已清理，`3000/3001/3002/3007/3100/8081` 未见 LISTEN。

边界：这是小规模真实链路 parser smoke，证明文本型 PDF / HTML / DOCX 能走通上传、异步解析、结构信号摘要、chunk、Qdrant 检索和 QA citation；不代表 OCR、扫描件识别、复杂版面理解、旧 `.doc` 支持、外部网页抓取或大规模解析 benchmark。artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/.../artifact.json`，不提交文档全文、回答文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-09 Document Parser Real Chain Direct Retrieve Regression

状态：PASS

Runner:

- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- `docpilot-parser-real-chain-20260709233230-a08906`

已验证：

- tunnel、backend health、frontend root route、临时用户注册、PDF / HTML / DOCX fixture 上传、异步解析、chunk、direct retrieve、QA retrieval、QA citation、source locator、parser boundary 和 artifact redaction 均通过。
- 三类文件均为 `parseStatus=SUCCESS`、`chunkCount=1`、`directRetrieveHit=true`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`。
- direct retrieve 与 QA retrieval 诊断摘要均显示 `code=0`、`hitCount=1`、`citationCount=1`、`noEvidence=false`、provider 为 Qdrant 摘要且 collection 存在；诊断字段只保存计数和状态，不保存 query、answer 原文、文档全文、prompt 或 evidence context。
- parser boundary 负向检查通过：不支持格式上传拒绝、空白 TXT、损坏 PDF 和损坏 DOCX 均返回预期脱敏失败码，`negativeCasePassCount=4/4`。
- 本轮同时修正 runner 质量归因：运行中 MySQL / Qdrant tunnel 断链会标为 `environmentStability=BLOCKED`，避免把环境问题误判为 parser 核心链路失败。

边界：这是小规模真实链路 parser smoke，证明 PDF / HTML / DOCX 文本型文件能走通上传、异步解析、chunk、Qdrant 检索和 QA citation；不代表 OCR、扫描件识别、复杂版面理解、旧 `.doc` 支持、外部网页抓取或大规模解析 benchmark。artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/.../artifact.json`，不提交文档全文、回答文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-09 Agent Quality Console Real Login Regression

状态：PASS

验证方式：

- 复用本地已有 MySQL / Qdrant tunnel。
- 本地启动 backend（local profile，Quality Console enabled，mock AI）和 frontend `3007`。
- 浏览器注册临时用户并打开 `/quality?autoload=1`。
- 从真实登录态页面打开一个 `/quality/trace` 链接。

已验证：

- Quality API 登录态可用：runs / eval-cases / trends 均返回成功。
- Console 可见 `20` 条 run、`12` 个 eval case、`20` 个趋势点。
- 最新 marker 为 `docpilot-cloud-quality-20260709164330-452624`，状态为 `REVIEW`；该 REVIEW 来自前序 KB Agent smoke 有意跳过 frontend route，不是本轮 Console 页面失败。
- `/quality` 可见运行详情、待处理、链路、评测、文档解析质量摘要、评测用例库、能力层覆盖、覆盖缺口、质量趋势、反复失败用例和最近运行点。
- `/quality/trace` 可见链路瀑布图、步骤摘要、排查建议、关联门禁和关联评测用例。
- 桌面 console error 为 `0`；`390px` 移动端 `/quality` 和 `/quality/trace` 均无横向溢出。
- 页面 DOM 未命中 Authorization 凭据、API key、secret、password、连接串、system prompt、answer raw、document full text 或 evidence context。

边界：本轮只创建临时登录用户，不上传文档、不创建 KnowledgeBase / Conversation、不删除业务数据、不改数据库结构、不操作远程 Docker、不提交 artifact 原文、不 push。该结果证明 Agent Quality Console ABC 增强在真实登录态下可读取和展示脱敏质量摘要，不等于企业级 APM、长期数据库化质量平台或线上 SLA。

## 2026-07-09 KB Agent Real-link Runtime Smoke

状态：KB Agent grounded answer gate PASS；本次整体 run 为 REVIEW（有意跳过前端路由）

Runner:

- `scripts/smoke/cloud-quality-smoke.ps1 -Mode run -SkipFrontend -EnableKnowledgeBaseAgentGate`

Marker:

- `docpilot-cloud-quality-20260709164330-452624`

已验证：

- tunnel、backend health、临时用户 A/B、两文档上传 / parse / indexing、chunk quality、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、answer grounding、no-evidence、Conversation Trace、权限隔离、cleanup 和 artifact redaction 均通过。
- 新增 `knowledgeBaseAgent` gate PASS：真实调用 `POST /api/ai/agent/knowledge-bases/{knowledgeBaseId}/run`。
- retrieval-only 任务返回 `decision=search_tool`，selected tool 为 `knowledge_base_search_tool`。
- KB Agent retrieve hits / citations 均为 `6`，documentHitCounts 覆盖两份主文档。
- grounded answer 任务返回 `decision=rag_tool`，执行 `knowledge_base_rag_qa`，answer citations 为 `6`，并覆盖两份主文档。
- no-evidence answer 边界通过：无证据问题不生成 citation；用户 B 访问用户 A KB 被拒绝。
- Agent Quality Console 可见性已有上一轮补验：浏览器打开 `/quality?autoload=1` 可见 KB Agent gate；门禁页展开已通过门禁后可见“知识库 Agent 检索 / 通过”。当前最新 answer route 深层指标已在 Quality API detail 中可见，前端 PASS gate 逐项展示仍可作为后续可读性增强。

边界：本轮原始 smoke 使用 `-SkipFrontend` 聚焦 KB Agent API 链路，因此 `frontendRoutes` 记为 REVIEW，整体 run 不是完整前端体验回归。artifact 位于 ignored 的 `tmp-e2e/docpilot-cloud-quality-smoke/.../artifact.json`，只保存计数、决策、工具名和布尔摘要，不提交原始 task、answer、文档全文、prompt、evidence context、凭据、连接串、云地址或 token。本次证明的是 KB Agent P0 grounded answer 小样本真实链路可用，不代表复杂 planner、多 Agent 编排或大规模 answer faithfulness benchmark。

## 2026-07-09 Document Parser Direct Retrieve Gate

状态：REVIEW（主 parser -> QA citation 链路通过，direct retrieve 质量缺口被显性化）

Runner:

- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`

Marker:

- `docpilot-parser-real-chain-20260709223724-ceb637`

已验证：

- tunnel、backend health、frontend root route、临时用户注册、PDF / HTML / DOCX fixture 上传、异步解析、chunk、QA retrieval、QA citation、source locator、parser boundary 和 artifact redaction 均通过。
- 三类文件均为 `parseStatus=SUCCESS`、`chunkCount=1`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`。
- parser boundary 负向检查通过：不支持格式上传拒绝、空白 TXT、损坏 PDF 和损坏 DOCX 均返回预期脱敏失败码。
- 新质量口径：`parserRealChain` 不再把 direct retrieve 缺口藏在 PASS 中；当 `directRetrieveHitCount < fileCount` 时标记为 `REVIEW`，并在 `parserQualityReport.reviewReasons` 中记录 `direct_retrieve_missing`。
- 本次结果：`directRetrieveHitCount=0/3`、`qaRetrievalHitCount=3/3`、`citationCount=3/3`、`sourceLocatorCount=3/3`。
- 补充定位：重启本地 backend 后，同批文档手动调用 `/api/rag/retrieve` 可命中 `1/1/1`，说明索引最终可用；仍需下一片定位同一 smoke 进程内 direct retrieve 与 QA retrieve 的差异。

边界：本次 REVIEW 是质量诊断结果，不是 PDF / HTML / DOCX 解析主链路失败。artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/.../artifact.json`，不提交原始文件、文档全文、回答文本、prompt、evidence context、token、凭据、连接串或云地址。本次不代表 OCR、扫描件识别、复杂版面理解、外部网页抓取、旧 `.doc` 支持或大规模解析 benchmark。

## 2026-07-08 Document Parser Real Chain Smoke

状态：PASS

Runner:

- `scripts/smoke/document-parser-real-chain-smoke.ps1 -Mode run`

Marker:

- `docpilot-parser-real-chain-20260708212742-0f9baa`

已验证：

- PDF / HTML / DOCX 三类临时 fixture 均完成上传、异步解析、chunk、embedding / index、RAG retrieve 和 QA citation。
- 三类文件均为 `parseStatus=SUCCESS`、`chunkCount=1`、`retrieveHit=true`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`。
- 本轮 source locator 回归已验证 parser block 的 `pageNumber` / `sourceLocator` / `blockType` 可进入 chunk metadata、vector payload、retrieve hit 和 QA citation response；artifact 只保留 `sourceLocatorPresent` 等脱敏布尔和计数结论。
- parser fixture corpus v2 已通过离线门禁：PDF 覆盖多页和空页 warning；HTML 覆盖标题层级、表格单元格分隔、列表、独立链接和噪声剔除；DOCX 覆盖标题层级、列表 block 和表格文本。
- tunnel、backend health、frontend root route、临时用户注册、parser boundary 和 artifact redaction 均 PASS。
- `parserBoundary` 真实 API 负向验证 PASS：不支持格式上传拒绝、空白 TXT 返回 `PARSER_EMPTY_CONTENT`、损坏 PDF / DOCX 返回 `PARSER_CORRUPTED_FILE`，`negativeCasePassCount=4/4`、`negativeCaseFailCount=0`、`unsupportedUploadRejected=true`。
- Agent Quality Console artifact root 已纳入 `backend/target/smoke/document-parser-real-chain`，`parserRealChain` gate 可展示 `fileCount=3`、`parsedFileCount=3`、`parserFailureCount=0`、`chunkCount=3`、`retrieveHitCount=3`、`directRetrieveHitCount=0`、`qaRetrievalHitCount=3`、`citationCount=3`、`sourceLocatorCount=3` 和 `durationMs`。
- `/quality?autoload=1` 可见最新 parser run 与“文档解析质量摘要”；Artifact 分区显示“检索来源：直接 0 / 问答 3”。桌面和 `390px` 移动端均无横向溢出，console error 为 `0`。

边界：本次 run 是小规模真实链路 parser smoke，不是 OCR、扫描件识别、复杂版面理解、外部网页抓取、`.doc` 旧格式支持或大规模解析质量 benchmark。`directRetrieveHitCount=0` 表明直接 retrieve endpoint / query 语义仍值得后续单独排查；本次 PASS 证明的是 parser 到 QA retrieval / citation 的主链路闭环。boundary artifact 只保存脱敏失败码和计数，不保存文件内容、异常堆栈或原始错误上下文。artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/.../artifact.json`，不提交文档全文、回答文本、prompt、evidence context、凭据、连接串、云地址或 token；本轮未删除已有业务数据、未改 schema、未 push。

## 2026-07-05 Agent Quality Console Trace Eval Trend Regression

状态：PASS

Runner:

- `scripts/smoke/real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- 浏览器打开 `/quality?autoload=1`
- 浏览器打开 `/quality/trace?...`

Marker:

- Audit：`docpilot-real-user-qa-20260705210119-7b8092`

已验证：

- 真实审计 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、naturalCorpus、multiQueryRag、answer grounding、no-evidence、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Console API 可见性 PASS：`/api/quality/runs/{marker}` 返回 `summary.status=PASS`、`gateCount=22`、`evalCaseCount=27`、`traceReferenceCount=2`；`/api/quality/eval-cases` 返回 7 个 case；`/api/quality/trends?limit=20` 返回 20 个趋势点。
- 浏览器 `/quality?autoload=1` 可见最新 marker、Eval Catalog、Quality Trend 和 trace reference；`/quality/trace` 可见脱敏链路步骤。桌面和 `390px` 移动端 console error count 均为 `0`，无横向溢出。
- Smoke runner 已把真实审计的 Conversation Trace 沉淀为脱敏 trace case result，只记录 `caseId/status/traceId/conversationId` 与数值 / 布尔指标，不提交或展示用户消息、answer 原文、文档全文或 evidence context。

边界：本次 run 是小规模真实链路质量回归，不是线上 SLA 或大规模 benchmark；artifact 位于 ignored 的 `backend/target/audit/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-05 Agent Quality Console 7-case Audit Regression

状态：PASS

Runner:

- `scripts/smoke/real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- 浏览器打开 `/quality?autoload=1`

Marker:

- Audit：`docpilot-real-user-qa-20260705192354-eba0fc`

已验证：

- 真实审计 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、naturalCorpus、multiQueryRag、answer grounding、no-evidence、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Console API 可见性 PASS：`/api/quality/runs` 可见最新 marker，detail 状态为 `PASS`；`/api/quality/eval-cases` 返回 7 个 case，其中 4 个带 `sourceIssueIds`，7 个带 `remediationHints`。
- 浏览器 `/quality?autoload=1` 可见最新 marker、Eval Catalog、source issue、verified marker、remediation hints、Failure Triage、Run Comparison 和 Model / Cost Summary；桌面和 `390px` 移动端 console error count 为 `0`，无横向溢出。

边界：本次 run 是小规模真实链路质量回归，不是线上 SLA 或大规模 benchmark；artifact 位于 ignored 的 `backend/target/audit/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-05 Agent Quality Console Phase 6 Real Audit

状态：PASS

Runner:

- `scripts/smoke/real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- 浏览器打开 `/quality?autoload=1`

Marker:

- 首轮 BLOCKED：`docpilot-real-user-qa-20260705164732-f54da1`
- 修复后 PASS：`docpilot-real-user-qa-20260705165151-bbe588`

已验证：

- 首轮真实审计暴露 Agent Quality Console 的 backend startup 回归：`QualityEvalCatalogServiceImpl` 构造器注入缺失导致 backend health BLOCKED。
- 修复后真实审计 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、naturalCorpus、answer grounding、no-evidence、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Console 可见性 PASS：`/api/quality/runs` 可见最新 marker，detail 状态为 `PASS`，`/api/quality/eval-cases` 返回 3 个 case。
- 浏览器 `/quality?autoload=1` 可见最新 marker、Eval Catalog、Failure Triage、Run Comparison 和 Model / Cost Summary；console error count 为 `0`，`390px` 宽度无横向溢出。

边界：本次 run 是小规模真实链路质量回归，不是线上 SLA 或大规模 benchmark；artifact 位于 ignored 的 `backend/target/audit/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-05 Agent Quality Console Regression Smoke

状态：PASS

Runner:

- `scripts/smoke/agent-quality-eval-smoke.ps1 -Mode run`
- `scripts/smoke/real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`
- 浏览器打开 `/quality?autoload=1`

Marker:

- Eval：`docpilot-agent-quality-eval-20260704221655-48a5cf`
- Audit：`docpilot-real-user-qa-20260705151944-950f42`

已验证：

- Agent Quality Console MVP 能聚合脱敏 eval / audit artifact，并通过内部 API 和 `/quality` 页面展示 run 状态。
- 真实 audit 中，tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、multi-query、answer grounding、no-evidence、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- `naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`；此前 `ops-incident-support-summary` 的 `distractorCitation` REVIEW 已通过本轮修复回归收口。
- Console Run Detail 已能展示嵌套 gate 和 eval case 的脱敏 signals；浏览器 `/quality?autoload=1` 可见 `naturalCorpus`、`CASEPASSRATE`、`DISTRACTORCITATIONFREECOUNT` 和 eval case `ops-incident-support-summary`，console error count 为 `0`。
- 浏览器交互 gate 保持 PASS：quote-first citation 可见、KnowledgeBase 双 marker citation 可见、权限提示可见，console error count 为 `0`。

边界：本次记录证明内部质量控制台与真实用户 QA 审计能发现并验证小样本 citation 精度问题，不代表大规模生产 relevance benchmark。artifact 位于 ignored 的 `backend/target/...`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-04 Memory Provider Extraction Smoke

状态：PASS

Runner:

- `scripts/smoke/memory-provider-extraction-smoke.ps1 -Mode run`

Marker: `docpilot-memory-provider-20260704192850-695412`

已验证：

- 使用真实 answer provider 做 4 case 小样本 memory extraction contract 验证。
- 覆盖 `ANSWER_STYLE + TASK_GOAL`、`TECH_CONTEXT`、RAG evidence 不进入 memory、secret-like 内容不抽取。
- 结果：`modelCallCount=4`，`casePassRate=1.0000`，`rawProviderOutputStored=false`。
- Artifact 只保存 provider、model、调用次数、caseId、suggestionTypes、布尔值和失败原因；不保存对话原文、provider 原始输出、memory 内容、prompt、token、凭据、云地址或连接串。

边界：这是小规模真实 provider contract smoke，不是大规模 memory extraction benchmark、生产 LLM 记忆抽取替换或长期记忆质量成熟结论；普通离线测试仍默认跳过真实 provider 调用。

## 2026-07-04 Real User QA Experience Audit Smoke

状态：PASS

Runner:

- `scripts/smoke/real-user-qa-experience-audit.ps1 -Mode run`

Marker: `docpilot-real-user-qa-20260704191307-661bc0`

已验证：

- 新增真实用户 QA 体验审计入口，组合 `naturalCorpus`、`multiQueryRag`、`frontendInteraction` 和 `memoryQuality` gate。
- 覆盖真实链路：tunnel、backend health、frontend routes、临时用户、上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、Conversation Trace、Memory 质量、权限隔离和 artifact 脱敏。
- 自然语料结果：3 个 corpus、12 份临时 txt 文档、25 个 case，`casePassRate=1`；`answerFaithfulnessPassCount=11/11`，`citationPhraseSupportPassCount=22/22`，`noEvidencePassCount=3/3`，`multiDocumentCoveragePassCount=4/4`。
- 前端与 Trace：quote-first citation UI 可见，KnowledgeBase 双 citation marker 可见，跨用户无权限提示可见，console error count 为 `0`；绑定 KB 的 Conversation Trace 中 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=4`、`memoryCount=1`。

本轮真实审计先发现 answer fact expression 门禁对单一英文短语过度敏感；已改为同义表达组后重跑 PASS。该问题记录在 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`。

边界：本次 run 是小规模真实链路用户体验审计，不是大规模人工评测、完整浏览器 E2E 覆盖或线上 SLA；artifact 位于 ignored `backend/target/audit/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-04 Evidence Coverage 报告 Smoke

状态：PASS

Runner:

- `scripts/smoke/rag-natural-corpus-audit-smoke.ps1 -Mode run`

Marker: `docpilot-rag-natural-corpus-20260704160327-16b351`

已验证：

- 自然语料 artifact 新增 `evidenceCoverageReport`，可以直接定位漏召回、漏 citation、citation 事实短语不支持、回答事实不满足、干扰 citation 泄漏和 no-evidence 失败。
- 本次结果：`retrieveCoveragePassCount=22`、`citationCoveragePassCount=22`、`citationPhraseSupportPassCount=22`、`answerFaithfulnessPassCount=11`、`noEvidenceCorrectCount=3`、`distractorCitationFreeCount=25`。
- 本次 miss / leak / failure 清单均为空：`retrievalCoverageMisses=[]`、`citationCoverageMisses=[]`、`citationPhraseMisses=[]`、`answerFaithfulnessMisses=[]`、`distractorCitationLeaks=[]`、`noEvidenceFailures=[]`。
- 同轮核心 gate 保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、短文档 RAG、multi-query、answer grounding、Conversation Trace、权限隔离、frontendInteraction、artifact redaction 和 cleanup。

边界：本次 run 是小规模真实链路 evidence coverage 报告，不是大规模 relevance benchmark、完整人工评测或线上 SLA；artifact 位于 ignored `backend/target/rag-natural-corpus/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-04 Answer / Citation Faithfulness v2 Smoke

状态：PASS

Runner:

- `scripts/smoke/rag-natural-corpus-audit-smoke.ps1 -Mode run`

Marker: `docpilot-rag-natural-corpus-20260704152850-e07b13`

已验证：

- 自然语料 QA case 增加回答事实表达硬门禁：`answerFaithfulnessPassCount=11/11`。
- 非 no-evidence case 增加 citation / evidence 事实短语支撑门禁：`citationPhraseSupportPassCount=22/22`。
- 同轮 `naturalCorpus` 继续保持 `casePassRate=1`，`noEvidencePassCount=3/3`，`multiDocumentCoveragePassCount=4/4`，`distractorCitationFreeCount=25/25`。
- 同轮核心 gate 保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、短文档 RAG、multi-query、answer grounding、Conversation Trace、权限隔离、frontendInteraction、artifact redaction 和 cleanup。

边界：本次 run 是小规模真实链路 answer / citation faithfulness smoke，不是大规模人工评测、NLI 模型评测或线上 SLA；artifact 位于 ignored `backend/target/rag-natural-corpus/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-04 RAG 自然语料扩容 v2 Smoke

状态：PASS

Runner:

- `scripts/smoke/rag-natural-corpus-audit-smoke.ps1 -Mode run`

Marker: `docpilot-rag-natural-corpus-20260704151615-bc193d`

已验证：

- `naturalCorpus` gate 升级为 `schemaVersion=2`：3 个 corpus、12 份临时 txt 文档、25 个自然语料 case。
- Case 覆盖：单文档事实、数字事实、日期事实、审批链、负向事实、多文档 compare / summary、干扰 citation、populated-KB no-evidence 和绑定 KnowledgeBase 的 Conversation Trace。
- 质量结果：`casePassRate=1`，`noEvidencePassCount=3/3`，`multiDocumentCoveragePassCount=4/4`，`distractorCitationFreeCount=25/25`。
- Trace 结果：自然语料 Conversation Trace 中 `ragTriggered=true`、`ragRequired=true`、`traceEvidenceCount=4`，并记录脱敏 `documentHitCounts`。
- 同轮核心 gate 保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、短文档 RAG、multi-query、answer grounding、权限隔离、frontendInteraction、artifact redaction 和 cleanup。

本轮真实 gate 先发现并修复了两类质量问题：自然语料 runner 的临时用户名长度超过注册约束；KnowledgeBase QA 数字 citation 精炼在多文档 compare 问题中误删一份目标文档 citation。最终均已回归通过。

边界：本次 run 是小规模真实链路自然语料质量门禁，不是大规模真实语料 relevance benchmark、线上 SLA、完整人工评测或通用语义蕴含模型。artifact 位于 ignored `backend/target/rag-natural-corpus/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-04 RAG 自然语料审计 Smoke

状态：PASS

Runner:

- `scripts/smoke/rag-natural-corpus-audit-smoke.ps1 -Mode run`

Marker: `docpilot-rag-natural-corpus-20260704143033-86b4f3`

已验证：

- 新增 `naturalCorpus` gate：使用 5 份临时自然语料 txt 文档，覆盖单文档事实、数字事实、多文档总结、干扰文档、no-evidence 和绑定 KnowledgeBase 的 Conversation Trace。
- 数字事实与干扰控制：invoice retention 问题最终 `numericQaCitations=1`，`distractorInvoiceCitationCount=1`，`distractorMarketingCitationCount=0`，避免把 marketing retention 干扰文档作为答案引用。
- 多文档自然问题：checkout incident 与 support SLA 总结同时覆盖 incident / support 文档，retrieve 与 citation 均命中两份目标文档。
- no-evidence：contractor payroll payment date 问题在 populated KB 中 `retrieveNoEvidence=true` 且 `qaNoEvidence=true`。
- Conversation Trace：绑定自然语料 KB 后 `ragTriggered=true`、`ragRequired=true`、`evidenceCount>0`，并记录脱敏 `documentHitCounts`。
- 同轮核心 gate 保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、短文档 RAG、multi-query、answer grounding、权限隔离、frontendInteraction、artifact redaction 和 cleanup。

边界：本次 run 是小规模真实链路自然语料 smoke，用于证明当前 RAG 质量门禁能发现并回归一类 citation 干扰问题；它不是大规模真实语料 relevance benchmark、线上 SLA、完整人工评测或通用语义蕴含模型。artifact 位于 ignored `backend/target/rag-natural-corpus/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-04 真实体验审计防回归增强 Smoke

状态：PASS

Runner:

- `scripts/smoke/cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate`

Marker: `docpilot-cloud-quality-20260704135601-944384`

已验证：

- `shortDocumentRag` gate 继续通过短 Alpha / Beta 单文档与 KnowledgeBase RAG，并新增中文短文档 retrieve、数字事实 retrieve、相似短文档干扰和细分 `failureBuckets`。
- 短文档 gate 结果：短 Alpha / Beta 各 `1` 个 chunk；单文档 retrieve / QA citation 为 `1/1`；短 KB retrieve / QA citation 为 `2/2`；`documentHitCounts` 覆盖两份短文档；`failureBuckets=[]`。
- `frontendInteraction` gate 继续覆盖文档详情 quote-first、KnowledgeBase 双 citation、跨用户无权限提示和 console error；`failureBuckets=[]`。
- 核心 gate 同步保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、answer grounding、no-evidence、Conversation Trace、权限隔离、artifact redaction 和 cleanup。

边界：本次 run 是小规模真实链路防回归 smoke，不是大规模 relevance benchmark、端到端 UI 自动化全覆盖或线上 SLA；artifact 位于 ignored `tmp-e2e/docpilot-cloud-quality-smoke/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-03 真实体验审计 P2/P3 浏览器交互 Smoke

状态：PASS

Runner:

- `scripts/smoke/cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate`

Marker: `docpilot-cloud-quality-20260703231920-e74334`

已验证：

- 新增 `frontendInteraction` gate：文档详情页登录态 RAG 检索预览中，quote-first 引用主文本可见 `ALPHA-SHORT-GATE`。
- KnowledgeBase 页面选择本轮短文档知识库后，回答引用区域可见 `ALPHA-SHORT-GATE` 和 `BETA-SHORT-GATE`。
- 用户 B 打开用户 A 文档详情时，无权限 / 不存在提示可见。
- 浏览器 console error count 为 `0`。
- 核心 gate 同步保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、短文档 RAG、answer grounding、no-evidence、Conversation Trace、权限隔离、artifact redaction 和 cleanup。

边界：本次 run 创建临时 smoke 用户、短 txt 文档、KnowledgeBase 和 Conversation；artifact 位于 ignored `tmp-e2e/docpilot-cloud-quality-smoke/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。该结果是小规模真实链路和浏览器交互回归 smoke，不是大规模 relevance benchmark、端到端 UI 自动化全覆盖或线上 SLA。

## 2026-07-03 真实体验审计 P1 修复验证 Smoke

状态：PASS

Runner:

- `scripts/smoke/cloud-quality-smoke.ps1`

Marker: `docpilot-cloud-quality-20260703213703-dbef08`

已验证：

- 新增 `shortDocumentRag` gate：短 Alpha txt 文档 parse / indexing 后，单文档 RAG 返回 `1` hit 和 `1` citation。
- 短 Alpha / Beta 两文档 KnowledgeBase RAG 返回 `2` hits 和 `2` citations，`documentHitCounts` 覆盖两份短文档。
- `answerGrounding` 同步覆盖短单文档和短 KnowledgeBase 回答：预期 marker 命中，forbidden marker 未命中，citation marker 存在。
- 核心 gate 保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、no-evidence、Conversation Trace、权限隔离、artifact redaction 和 cleanup。
- 本轮同时完成 quote-first citation UI 代码修复和权限错误中文提示归一化；这两项属于体验修复，仍建议后续补浏览器点击级细验。

边界：本次 run 创建临时 smoke 用户、短 txt 文档、KnowledgeBase 和 Conversation 数据；artifact 位于 ignored `tmp-e2e/docpilot-cloud-quality-smoke/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。该结果是小规模真实链路回归 smoke，不是大规模 relevance benchmark 或线上 SLA。

## 2026-07-03 真实体验审计问题发现

状态：REVIEW（需复查）

审计 marker：`docpilot-real-audit-20260703195519-5118e8`

关联 cloud quality marker：`docpilot-cloud-quality-20260703195356-1362ea`

已验证：

- 标准 cloud quality smoke 已通过：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、answer grounding、no-evidence、Conversation Trace、权限隔离、cleanup 和 artifact redaction。
- 浏览器审计中，`/`、`/dashboard`、`/documents`、`/documents/{documentId}`、`/knowledge-bases` 和 `/conversations` 均可渲染；桌面端和移动端未发现横向溢出、前端 console error 或用户可见 mojibake。
- 补充检查已通过：`npm run lint`；`mvn -DskipTests compile`。

问题已追踪在 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`：

- `REA-20260703-P1-001`：短 txt parse 成功但单文档 RAG 返回 no evidence。
- `REA-20260703-P1-002`：短文档 KnowledgeBase 双文档问题退化成单文档命中。
- `REA-20260703-P2-001`：quote-level citation API 已有，但仍需要 quote-first UI 展示。
- `REA-20260703-P3-001`：权限拒绝已生效，但前端需要把业务级无权限状态展示得更清晰。

边界：原始审计报告只保留在 ignored 的 `backend/target/audit/...`；不要提交 artifact 原文、日志、截图、临时文档文本、token、prompt、evidence context、云地址或连接串。本轮是用户视角 runtime audit，不是大规模生产 relevance benchmark 或线上 SLA。

## 2026-07-03 RAG Multi-query Enabled Quality Gate Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260703192456-2a62e9`

Verified gates:

- Request-scoped multi-query retrieval was enabled in the real cloud quality flow through `multiQueryEnabled=true` and `maxQueryVariants=4`.
- `multiQueryRag` passed: `multiQueryApplied=true`, `queryVariantCount=4`, `queryDedupeCount=24`, `6` retrieve hits and `6` QA citations.
- The multi-query gate covered both temporary documents: Alpha retrieve/citation count `3/3`, Beta retrieve/citation count `3/3`.
- Core gates also remained PASS: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase two-document RAG, representative corpus, answer grounding, hard negative gate, semantic gate, real provider faithfulness, ordinary no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Conversation Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=4`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=4`, and two-document `documentHitCounts`.

Boundary: this proves the request-scoped multi-query path can run in the current real smoke environment and preserve the existing quality gates. It is still a small smoke comparison, not a large-scale relevance uplift benchmark or online SLA. A first run with marker `docpilot-rag-real-qa-20260703192105-e953d2` reached `multiQueryRag` PASS but failed later at Conversation message request with `FAILED_CORE_FLOW`; cleanup succeeded and the immediate rerun above passed. Artifacts are stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens.

## 2026-06-29 RAG A4-A6 Quality Gate Regression Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629202542-3e47d9`

Verified gates:

- Real-link regression after A4 Retrieval Error Analysis Report, A5 Chunk Quality v2 and A6 default-off Multi-query Retrieval.
- `chunkQuality` passed for two temporary txt documents: each produced `4` MySQL chunks and `4` indexed vectors, with ordered offsets, matching token/content length and `0` duplicate hashes.
- `mysqlQdrantConsistency` passed: both temporary documents had matching MySQL chunk count and Qdrant point count, `0` missing vector IDs, `0` mismatched fields and `0` missing structure fields.
- Single-document RAG, KnowledgeBase two-document RAG, representative corpus, answer grounding, hard negative gate, semantic gate, real provider faithfulness, ordinary no-evidence, Conversation Trace, permission isolation, frontend routes, cleanup and artifact redaction all remained PASS.
- Conversation Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=4`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=4`, and two-document `documentHitCounts`.

Boundary: A6 multi-query retrieval remains disabled by default in this smoke; this run proves no regression in the default real-link path, not that multi-query improves relevance in production. Artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens.

## 2026-06-29 RAG Real Provider Faithfulness Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629191831-69d71e`

Verified gates:

- `realProviderFaithfulness` passed for `knowledgeBaseRag`, `answerFaithfulness`, `claimSupport`, and `numericFaithfulness`.
- Each checked scope observed a non-mock answer provider, `modelCallCount=1`, `noEvidence=false`, and a non-empty answer.
- Core real-link gates remained PASS: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, hard negative gate, semantic gate, ordinary no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.

Boundary: the first run exposed an unstable `answerFaithfulness` question that did not force the expected marker / citation marker; the runner question was narrowed and the second run passed. Artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a small real-provider smoke, not a large-scale answer faithfulness benchmark, general entailment model or online SLA.

## 2026-06-29 RAG Real QA Semantic Gate Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629183549-4aafc3`

Verified gates:

- `realQaSemanticGate` is now enabled by default in the RAG Real QA smoke wrapper and can be skipped with `-SkipRealQaSemanticGate`.
- `claimSupport` passed: retrieve hit count `1`, QA citation count `1`, target citation count `1`, forbidden citation count `0`, expected marker satisfied, forbidden marker absent and citation marker present.
- `numericFaithfulness` passed: retrieve hit count `1`, QA citation count `1`, target citation count `1`, forbidden citation count `0`, expected marker satisfied, forbidden marker absent and citation marker present.
- Core real-link gates remained PASS: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, hard negative gate, ordinary no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a small real-link semantic support gate, not a general entailment model, large-scale provider benchmark or online SLA.

## 2026-06-29 Memory Governance Edit / Resolve Smoke

Status: PASS

Runner:

- `scripts/smoke/memory-quality-smoke.ps1`

Marker: `docpilot-memory-quality-20260629140941-6668d9`

Verified gates:

- Memory governance now supports explicit user actions for conflicting suggestions: keep the active memory, replace the active memory with the suggestion, or merge with user-confirmed content.
- `memoryQuality` passed: conflicting suggestion direct accept was blocked, `KEEP_ACTIVE` moved the suggestion to `IGNORED`, `REPLACE_ACTIVE` updated the active memory, sensitive edit was rejected with code `1028`, normal edit persisted with priority `46`, and `MERGE_WITH_ACTIVE` updated the active memory.
- Core real-link gates remained PASS: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, answer grounding, no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Conversation Trace still separated memory and RAG evidence: `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=6`, `memoryCount=1`, `evidenceCount=6`.

Boundary: artifact is stored under ignored `backend/target/memory-quality/.../artifact.json`; do not commit artifact raw content, memory text, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a user-controlled memory governance smoke, not a large-scale personalization benchmark or real-model memory extraction evaluation.

## 2026-06-29 RAG Hard Negative Support Gate Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629130454-1d1d6c`

Verified gates:

- The v3.6 hard-negative REVIEW was addressed by a near-threshold evidence support gate in KnowledgeBase retrieval.
- `realQaHardGate` passed: hard negative returned `0` retrieve hits and `0` QA citations; answer faithfulness kept target citation count `1` and forbidden citation count `0`.
- Core real-link gates remained PASS: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, ordinary no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Representative Corpus KB returned `8` retrieve hits and `8` citations, with documentHitCounts covering Gamma `214:2`, Beta `213:3`, Alpha `212:3`.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. The support gate is a narrow near-threshold heuristic, not a general entailment model or large-scale benchmark.

## 2026-06-29 RAG Real QA Hard Gate Smoke

Status: REVIEW

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629125627-c0915e`

Verified gates:

- `cloud-quality-smoke.ps1` now includes optional `realQaHardGate`; `rag-real-qa-eval-smoke.ps1` enables it by default and can skip it with `-SkipRealQaHardGate`.
- Core real-link gates passed: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, ordinary no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- `answerFaithfulness` passed: target citation count `1`, forbidden citation count `0`, expected marker satisfied, forbidden marker absent and citation marker present.
- `hardNegative` remained REVIEW: a high lexical-overlap unsupported question returned `3` retrieve hits and `3` QA citations, with vector scores around `0.50-0.55`.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is small real-link evidence of a hard-negative quality gap, not a large-scale benchmark.

## 2026-06-29 RAG Answer Grounding Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260629003157-630db5`

Verified gates:

- `cloud-quality-smoke.ps1` now includes `answerGrounding` for single-document RAG, KnowledgeBase RAG and representative corpus QA answers.
- `rag-real-qa-eval-smoke.ps1` plan output includes `answer_grounding` and `answerGrounding`.
- Single-document, KnowledgeBase and representative corpus answer checks all passed: answer present, expected evidence markers satisfied, forbidden marker absent and citation marker present.
- Representative Corpus KB still returned `8` retrieve hits and `8` citations.
- documentHitCounts covered all three temporary documents: Gamma `203:2`, Beta `202:3`, Alpha `201:3`.
- Delegated cloud quality gates passed: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, representative corpus, populated-KB no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, answer text, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a small real-link answer grounding gate, not a large-scale answer faithfulness benchmark or online SLA.

## 2026-06-28 RAG Real Corpus Representative Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260628234235-5c1b94`

Verified gates:

- `rag-real-qa-eval-smoke.ps1` now defaults to the representative corpus gate and can skip it with `-SkipRepresentativeCorpusGate`.
- Delegated cloud quality gates passed: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, populated-KB no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Representative Corpus KB contained Alpha, Beta and Gamma temporary documents.
- Representative gate returned `8` retrieve hits and `8` citations.
- documentHitCounts covered all three documents: Gamma `196:2`, Beta `195:3`, Alpha `194:3`.
- Conversation Trace still showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=6`.
- Permission isolation negative checks and artifact redaction remained PASS.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a small real-link representative corpus gate, not a large-scale relevance benchmark or online SLA.

## 2026-06-28 RAG Real QA Eval Smoke

Status: PASS

Runner:

- `scripts/smoke/rag-real-qa-eval-smoke.ps1`

Marker: `docpilot-rag-real-qa-20260628164757-ac2a1d`

Verified gates:

- Local MySQL / Qdrant tunnel was started by the runner.
- Backend health and seven frontend routes passed.
- Temporary user A / user B, two txt documents, KnowledgeBase and Conversation were created by the runner.
- Two documents parsed and indexed successfully; each had `3` MySQL chunks and `3` matched Qdrant points.
- Chunk quality passed offset ordering, length/token checks, duplicate hash checks and indexed vector id checks.
- MySQL / Qdrant payload consistency passed with no missing vector IDs or structure payload fields.
- Single-document RAG returned `3` hits and `3` citations.
- KnowledgeBase RAG returned `6` hits and `6` citations, with document distribution covering both temporary documents.
- Populated-KB no-evidence gate returned `0` hits and `0` citations.
- Conversation Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=6`, and two-document hit distribution.
- Permission isolation negative checks passed for foreign KB detail, foreign KB retrieve, foreign document add and foreign trace access.
- Artifact redaction and cleanup gates passed.

Boundary: artifact is stored under ignored `backend/target/rag-real-qa/.../artifact.json`; do not commit artifact raw content, document text, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This is a small real-link smoke quality gate, not a large-scale relevance benchmark or online SLA.

## 2026-06-28 Memory Quality Smoke

Status: PASS

Runner:

- `scripts/smoke/memory-quality-smoke.ps1`

Marker: `docpilot-memory-quality-20260628193150-625bf6`

Verified gates:

- Local MySQL / Qdrant tunnel, backend health and seven frontend routes passed.
- Temporary user A / user B, two txt documents, KnowledgeBase, Conversation and memory records were created by the runner.
- Delegated cloud quality gates passed: upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase two-document RAG, populated-KB no-evidence, Conversation Trace, permission isolation and artifact redaction.
- Memory quality gate extracted `2` suggestions from a real temporary conversation.
- Accepted suggestion became `ACTIVE`; ignored suggestion became `IGNORED` and was absent from the ACTIVE memory list.
- Bound-KB trace showed `recentMessages=2`, `userMemory=1`, `ragEvidence=6`, `memoryCount=1`, `evidenceCount=6`, and documentHitCounts covering both temporary documents.

Boundary: artifact is stored under ignored `backend/target/memory-quality/.../artifact.json`; do not commit artifact raw content, conversation text, memory content, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This validates rule-based memory quality gates and trace separation; it does not claim real-model long-term memory extraction or large-scale personalization quality.

## 2026-06-28 Memory Governance Smoke

Status: PASS

Runner:

- `scripts/smoke/memory-quality-smoke.ps1`

Marker: `docpilot-memory-quality-20260628223255-0a06e6`

Verified gates:

- Delegated cloud quality gates passed: tunnel, backend health, frontend routes, two-document upload / parse / indexing, chunk quality, MySQL / Qdrant payload consistency, single-document RAG, KnowledgeBase RAG, populated-KB no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction.
- Memory quality gate extracted `2` suggestions; accepted suggestion became `ACTIVE`, ignored suggestion became `IGNORED`, and the ignored suggestion was absent from the ACTIVE memory list.
- Bound-KB trace kept user memory and RAG evidence separated, with `userMemory=1`, `ragEvidence=6`, `memoryCount=1`, `evidenceCount=6`, and two-document hit distribution.
- Memory governance gate created a temporary ACTIVE `ANSWER_STYLE` baseline, extracted a conflicting answer-style suggestion, and verified `governanceHint=conflict_active_memory` with non-empty `conflictWithId`.
- Direct accept of the conflicting suggestion was blocked before it could become ACTIVE, and the blocked reason matched the governance requirement.

Boundary: artifact is stored under ignored `backend/target/memory-quality/.../artifact.json`; do not commit artifact raw content, conversation text, memory content, prompts, evidence context, credentials, connection strings, cloud addresses or tokens. This validates the first accept-before-ACTIVE governance gate for rule-based memory suggestions; it does not claim automatic memory merge/edit workflows or real-model long-term memory extraction quality.

## 2026-06-28 Frontend UX Audit

Status: PASS

Marker: `docpilot-frontend-ux-2647184760`

Verified flow:

- Browser context created a temporary user, two txt documents, KnowledgeBase, ACTIVE memory and a KnowledgeBase-bound Conversation.
- Documents `175` and `176` parsed successfully.
- Conversation Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=2`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=2`, and document distribution `{175:1,176:1}`.
- `/conversations` displayed the assistant footer as `2 条来源`; Trace and Memory tabs were reachable through real clicks, and the ACTIVE memory was visible.
- `/knowledge-bases` displayed provider / collection fields, `来源不足: 否`, document distribution `#175: 1 / #176: 1`, retrieved snippets and citation cards containing both temporary document markers.
- Mobile `390x844` checks found no horizontal overflow on `/conversations` or `/knowledge-bases`.
- Follow-up `360x780` and `320x740` checks added a long ACTIVE memory and confirmed the Memory drawer and KnowledgeBase page still had no horizontal overflow.

Boundary: this is a real-browser user-experience audit over temporary smoke data. It did not change backend or frontend code, did not delete business data, did not alter schema, did not operate remote Docker, and did not commit artifacts, screenshots, raw logs, tokens, cloud addresses or connection strings.

## 2026-06-28 Memory Product UI Audit

Status: PASS

Marker: `docpilot-memory-ui-product-1782651263292`

Verified flow:

- Browser context created a temporary user, `3` ACTIVE memories, `2` suggested memories and a Conversation `41`.
- `/conversations` Memory drawer displayed active / suggested / duplicate KPI badges, memory type distribution, source labels, priority, confidence, updated time and duplicate warnings.
- Desktop check showed `cardCount=5`, `scrollWidth=clientWidth=1265`.
- Mobile `390x844` check showed `scrollWidth=clientWidth=375`, `metaCount=17`, `cardCount=5`.
- Mobile `320x740` check showed `scrollWidth=clientWidth=305`, `kpiCount=3`, `metaCount=17`, `cardCount=5`.

Boundary: this validates Memory management UX over temporary data. It does not claim real-model long-term memory extraction quality, large-scale personalization quality or conflict-resolution automation.

## 2026-06-28 Phase 2 Real Experience Audit

Status: PASS after follow-up fixes

Marker: `docpilot-phase2-ui-audit-1782628501578`

Verified flow:

- Local MySQL / Qdrant tunnel, backend and frontend were running locally.
- Browser UI registration on `localhost:3007` succeeded after the local CORS allowlist was extended for smoke ports.
- Two temporary txt documents parsed successfully: `150`, `151`.
- Single-document RAG returned `1` hit and `1` citation.
- KnowledgeBase API path returned `2` hits and `2` citations with document distribution `{150:1,151:1}`.
- Conversation bound to KB `26` returned an answer with two evidence references; Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=2`, `memoryCount=1`, `contextSourceCounts.userMemory=1`, `contextSourceCounts.ragEvidence=2`, and `documentHitCounts={150:1,151:1}`.

Experience findings:

- Fixed during audit: browser requests from frontend dev ports `3007` / `3100` were blocked by backend CORS before `WebMvcConfig` was updated.
- Single-document detail page can generate an answer with `[1]`, but the right-side citation panel still says no citation source.
- KnowledgeBase page exposes provider, collection, model, scores, citations and document distribution, but a manual two-document question only retrieved document `150`; `151` was missed even though the prompt requested both markers.
- Conversation answer text displayed `[1]` / `[2]` and Trace showed two RAG evidence items, but the chat bubble footer showed `0` citations.
- Mobile `/conversations` at `390x844` had horizontal overflow: the main chat area remained wider than the viewport while side panels were off-canvas.

Follow-up fix in the same Phase 2 cycle: `/conversations` now loads the latest assistant trace for historical messages and displays `2 条来源` from `contextTrace.evidenceCount` when citation details are not embedded in the message list response.

Follow-up fix in the same Phase 2 cycle: document detail RAG streaming now consumes `retrieval` and `citation` SSE events, so the citation panel updates during a streamed answer and shows hit count, citation score, chunk version and snippet.

Follow-up fix in the same Phase 2 cycle: mobile `/conversations` now constrains the chat main area, topbar, thread and composer to the viewport width; the long KB label is clipped instead of stretching the page.

Follow-up fix in the same Phase 2 cycle: KnowledgeBase hybrid retrieval now keeps keyword-supported summary-intent candidates long enough for scope guard, rerank and multi-document diversity selection. Real smoke `docpilot-rag-real-quality-20260628150434-2b7b39` passed with KnowledgeBase document distribution `{152:3,153:3}`, no-evidence threshold PASS, Conversation Trace PASS, permission isolation PASS and frontend route smoke PASS.

Boundary: no raw artifact, token, password, prompt, evidence context, cloud address or connection string is committed. Temporary data was created only for this real-link audit.

## 2026-06-28 RAG Quality Smoke

Status: PASS

Marker: `docpilot-rag-real-quality-20260628141419-fb7c21`

Verified gates:

- Reused local MySQL / Qdrant tunnel.
- Backend health and frontend routes passed.
- Temporary users, txt documents, KnowledgeBase and Conversation were created by smoke runner.
- Chunk quality and MySQL / Qdrant payload consistency passed.
- Single-document RAG, KnowledgeBase two-document RAG and populated-KB no-evidence gate passed.
- Conversation Trace showed `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, `memoryCount=1`, and separated `userMemory=1` / `ragEvidence=6`.
- Permission isolation negative checks and artifact redaction passed.

Boundary: artifact is stored under ignored `backend/target/rag-quality/.../artifact.json`; do not commit artifact raw content, prompts, evidence context, credentials, connection strings or cloud addresses.

## 2026-06-28 Rerank Effect Smoke

Status: PASS with small hard-fixture ranking uplift

Runner:

- `scripts/smoke/rerank-effect-smoke.ps1`

Validation performed:

| Check | Result |
| --- | --- |
| `-Mode plan` | PASS |
| `-Mode dry-run` | PASS |
| `-Mode run` overall status | PASS |
| hybrid-only baseline marker | `docpilot-rerank-effect-hybrid-20260628151134-170d38` |
| hybrid + rerank marker | `docpilot-rerank-effect-rerank-20260628151301-6b0060` |
| baseline KB gate | `6` retrieve hits, `6` QA citations, `2` covered documents |
| rerank KB gate | `6` retrieve hits, `6` QA citations, `2` covered documents |
| rerank provider evidence | `rerankApplied=true`, rerank score count `6`, score min `0.61774837970733643`, max `0.997183620929718` |
| no-evidence regression | `false` |
| security regression | `false` |
| hard fixture baseline marker | `docpilot-rerank-effect-hybrid-20260628204120-3e9f69` |
| hard fixture rerank marker | `docpilot-rerank-effect-rerank-20260628204339-7aac45` |
| hard fixture target rank | `2 -> 1` |
| hard fixture distractor rank | `3 -> 4` |
| hard fixture uplift observed | `true` |

Boundary: this proves the configured real rerank provider was called, did not regress the core RAG/security gates, and improved target/distractor ordering in a small hard smoke fixture. It does not prove broad relevance uplift or production-scale ranking quality.

## 1. Single Document Smoke

Status: PASS

Evidence source: live API smoke records from A1-3 and A1-4 agent/tool verification.

Verified flow:

- Registered and logged in with a demo user.
- Uploaded a non-sensitive txt document.
- Created a document record.
- Created a parse task.
- Document parsing reached `SUCCESS`.
- Parse success triggered RAG indexing.
- Single-document RAG retrieve returned hits.
- Single-document RAG QA returned an answer with citations.
- Single-document RAG SSE was verified in A1-3.
- Agent `rag_qa_tool` returned evidence-backed results.
- ToolCall API exposed and called `rag_qa_tool`.

Recorded IDs:

| Item | Value |
| --- | --- |
| A1-4 agent/tool userId | `78` |
| A1-4 agent/tool fileRecordId | `75` |
| A1-4 agent/tool documentId | `73` |
| A1-4 agent/tool parseTaskId | `70` |
| A1-4 agentTaskId | `26` |

Key results:

| Check | Result |
| --- | --- |
| Parse status | `SUCCESS` |
| Storage mode in this smoke | `local-path` |
| Single-document retrieve code | `0` |
| Single-document retrieve hit count | `1` |
| Agent run code | `0` |
| Agent success | `true` |
| Agent persisted step count | `2` |
| Agent RAG citation count | `1` |
| Tool list code | `0` |
| ToolCall `rag_qa_tool` status | `SUCCESS` |
| ToolCall hit count | `1` |
| ToolCall citation count | `1` |
| ToolCall userId violation | rejected with `403` |

Listed tools:

- `document_status_tool`
- `document_summary_tool`
- `document_qa_tool`
- `rag_qa_tool`

## 2. Multi-Document KnowledgeBase Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-a143-kb-smoke-summary.json`.

Verified flow:

- Created demo user A and demo user B.
- Uploaded two user A documents.
- Parsed both user A documents successfully.
- Verified both documents were individually indexed.
- Created a KnowledgeBase.
- Added both documents into the KnowledgeBase.
- Retrieved across multiple documents with Qdrant.
- Ran KnowledgeBase RAG QA.
- Verified citations covered both documents.
- Verified cross-user access failures.
- Verified cross-user document add failure.

Recorded IDs:

| Item | Value |
| --- | --- |
| user A id | `79` |
| user B id | `80` |
| user A document 1 | `74` |
| user A document 2 | `75` |
| user B document | `76` |
| KnowledgeBase id | `1` |
| Empty KnowledgeBase id | `2` |

Key results:

| Check | Result |
| --- | --- |
| Document 74 parse status | `SUCCESS` |
| Document 75 parse status | `SUCCESS` |
| Document 74 single retrieve hits | `1` |
| Document 75 single retrieve hits | `1` |
| KnowledgeBase create code | `0` |
| Add documents code | `0` |
| Active document count | `2` |
| KnowledgeBase detail document count | `2` |
| KnowledgeBase RAG provider | `qdrant` |
| KnowledgeBase retrieve code | `0` |
| KnowledgeBase retrieve hit count | `2` |
| KnowledgeBase retrieve citation count | `2` |
| Distinct hit document IDs | `74, 75` |
| KnowledgeBase QA code | `0` |
| KnowledgeBase QA citation count | `2` |
| KnowledgeBase QA fallback used | `false` |

No-evidence results:

| Case | Result |
| --- | --- |
| Populated KB with unrelated query | returned nearest hits; `noEvidence=false` |
| Empty KB retrieve | `noEvidence=true`, hit count `0` |
| Empty KB QA | `noEvidence=true`, `fallbackUsed=true`, `fallbackReason=no_evidence` |

Boundary: this older smoke predates the v3 evidence confidence gate. Populated KnowledgeBase no-evidence is now covered by the RAG real quality gate in section 11.

## 3. Real Model Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-real-model-smoke-summary.json`.

Verified flow:

- Confirmed `.env` contains non-empty real model settings without printing values.
- Started backend with `AI_MODE=real`.
- Uploaded a short non-sensitive txt document.
- Parsed the document successfully.
- Called `POST /api/ai/qa`.
- Verified the answer contained the smoke marker.

Recorded IDs:

| Item | Value |
| --- | --- |
| userId | `81` |
| fileRecordId | `79` |
| documentId | `77` |
| parseTaskId | `73` |

Key results:

| Check | Result |
| --- | --- |
| Parse status | `SUCCESS` |
| QA code | `0` |
| QA elapsed | about `6184ms` |
| Answer length | `113` |
| Answer contained marker | `true` |
| Citation count | `1` |

Boundary: this verifies the real answer generation model through `RealAiAnswerService`. It does not verify a real embedding provider.

## 4. MinIO Active Storage Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-s3-minio-smoke-summary.json`.

Verified flow:

- Started backend with `FILE_STORAGE_MODE=minio`.
- Uploaded a short non-sensitive txt document.
- Created a document record and parse task.
- Verified upload response used `minio://` storage prefix.
- Verified parser read the object back successfully and document parse reached `SUCCESS`.

Recorded IDs:

| Item | Value |
| --- | --- |
| userId | `83` |
| fileRecordId | `81` |
| documentId | `79` |
| parseTaskId | `75` |

Key results:

| Check | Result |
| --- | --- |
| Storage prefix | `minio://` |
| Parse status | `SUCCESS` |
| Direct bucket listing | not separately performed |

Boundary: this verifies MinIO object write/readback through the application path. It does not claim production object lifecycle governance.

## 5. RocketMQ + Outbox Active Parse Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-s4-mq-smoke-summary.json` and sanitized backend log lines.

Verified flow:

- Started backend with RocketMQ enabled.
- Registered and logged in with a demo user.
- Uploaded a non-sensitive txt document.
- Created a document and parse task.
- `POST /api/task/parse/create` returned `PENDING`.
- Producer sent the parse task message with `SEND_OK`.
- Consumer received the parse task message.
- Parse consume entry accepted the task.
- Document parsing reached `SUCCESS`.

Recorded IDs:

| Item | Value |
| --- | --- |
| userId | `85` |
| fileRecordId | `82` |
| documentId | `80` |
| parseTaskId | `76` |

Key results:

| Check | Result |
| --- | --- |
| Parse create status | `PENDING` |
| MQ send status | `SEND_OK` |
| Consume status | success log observed |
| Final parse status | `SUCCESS` |
| Parsed content marker present | `true` |

Boundary: DB row-level verification for `tb_parse_task_outbox` and `tb_parse_task_consume_record` was not performed because safe read-only DB access without credentials was blocked. API status and application logs prove the active producer / consumer path for this smoke.

## 6. Real Embedding + Qdrant Smoke

Status: PASS

Evidence source: `C:\Users\Lenovo\AppData\Local\Temp\docpilot-real-embedding-6333-smoke-summary.json`.

Verified flow:

- Confirmed required embedding configuration keys existed and were non-empty without printing values.
- Used a local Qdrant tunnel at `http://127.0.0.1:6333`.
- Started backend with `APP_RAG_EMBEDDING_PROVIDER=openai_compatible`, Qdrant vector store, and mock answer generation.
- Uploaded a short non-sensitive txt document.
- Created a document and parse task.
- Document parsing reached `SUCCESS`.
- Parse success triggered RAG indexing.
- Qdrant smoke collection existed after indexing.
- Single-document RAG retrieve returned a hit.
- Single-document RAG QA returned an answer with citation.

Recorded IDs:

| Item | Value |
| --- | --- |
| userId | `87` |
| fileRecordId | `84` |
| documentId | `82` |
| parseTaskId | `78` |

Key results:

| Check | Result |
| --- | --- |
| Embedding provider | `openai_compatible` |
| Embedding model | `Qwen/Qwen3-Embedding-0.6B` |
| Vector provider | `qdrant` |
| Vector dimension | `1024` |
| Qdrant collection | `docpilot_embedding_smoke_20260606_03` |
| Collection existed / created | `true` |
| Retrieve code | `0` |
| Retrieve hit count | `1` |
| QA code | `0` |
| QA answer length | `590` |
| QA citation count | `1` |
| Real answer model called | `false` |

Boundary: this verifies real embedding + Qdrant indexing / retrieval in a smoke collection. Answer generation stayed in mock mode, so this does not prove real answer model and real embedding in the same run.

## 7. Eval Artifact

Status: PASS

Artifact:

- `backend/target/rag-eval/knowledge-base-rag-eval-latest.json`

Test command used:

```powershell
cd backend
mvn "-Dtest=OpenAiFunctionCallingServiceImplTest,OpenAiToolCallParserTest,OpenAiToolResultAdapterTest,KnowledgeBaseRagEvalRunnerTest" test
```

Results:

| Metric | Value |
| --- | --- |
| Tests | `12` run, `0` failures, `0` errors, `0` skipped |
| Provider | `in_memory` |
| Embedding provider | `mock` |
| Case count | `5` |
| Model call count | `4` |
| No-evidence model call count | `0` |
| hitAtK | `1.0000` |
| documentHitRate | `1.0000` |
| citationHitRate | `1.0000` |
| answerHitRate | `1.0000` |
| citationCountRate | `1.0000` |
| multiDocumentCoverageRate | `1.0000` |
| forbiddenAnswerLeakRate | `0.0000` |
| noEvidenceRate | `1.0000` |
| scopeViolationRate | `0.0000` |

Boundary: this eval is offline/mock-oriented evidence, not a real external model eval. The v1 quality gate now checks retrieval markers, citation alignment, answer marker coverage, minimum citation count, multi-document coverage and forbidden answer leakage, but it still uses `MockEmbeddingProvider`, `InMemoryVectorStoreClient` and a synthetic answer service.

## 8. Conversation Context / Agent Memory Smoke

Status: PASS

Runtime setup:

- Local backend and frontend were started.
- Cloud MySQL and Qdrant were reached through the current local SSH tunnel entries.
- No real secrets, prompts, or evidence source text were copied into this record.

API smoke:

| Check | Result |
| --- | --- |
| Temporary user | `userId=94` |
| Uploaded / parsed document | `documentId=93`, parse `SUCCESS` |
| KnowledgeBase | `knowledgeBaseId=7` |
| KnowledgeBase retrieval | `1` hit, `noEvidence=false`, `documentHitCounts={93:1}` |
| Bound conversation | `conversationId=3`, mode `AGENT_MEMORY` |
| Conversation trace | `ragTriggered=true`, `ragRequired=true`, `evidenceCount=1` |
| Conversation citations | `1` |
| Fallback / model skipped | `false / false` |

Browser smoke:

| Check | Result |
| --- | --- |
| Page | `/conversations` |
| Bound KnowledgeBase | `#8` |
| Parsed document | `documentId=94` |
| User question | Chinese `根据知识库` intent |
| Assistant answer | Referenced `t013-ui-kb-0613093939.txt` |
| Trace evidence | `Evidence=1` |
| Trace RAG flags | `RAG triggered=yes`, `RAG required=yes`, `No Evidence=no` |
| Document hit distribution | `#94: 1` |

Boundary: this verifies Conversation Context / Agent Memory MVP with KnowledgeBase-bound evidence in a non-streaming conversation path. It does not mean the existing Agent main chain has been replaced, and it does not add background automatic summaries or real-model memory extraction.

## 9. Permission Boundary Cases

Verified failures:

| Case | Result |
| --- | --- |
| ToolCall with mismatched `userId` | rejected with `403` |
| User B retrieves user A KnowledgeBase | rejected with code `1022` |
| User B reads user A KnowledgeBase detail | rejected with code `1022` |
| User A adds user B document to user A KnowledgeBase | rejected with code `1010` |

Boundary: before S2, some Chinese error messages were garbled in API output. Error codes were valid, but display text needed cleanup.

## 10. Cloud Quality Gate Smoke Runner

Status: PASS

Evidence source: `backend/target/smoke/docpilot-cloud-quality-20260627022219-37efd4/artifact.json`.

Runner:

- `scripts/smoke/cloud-quality-smoke.ps1`

Implemented modes:

| Mode | Behavior |
| --- | --- |
| `plan` | Prints the gate list and artifact target only; does not read `.env`, start services, or create data. |
| `dry-run` | Checks local prerequisites, ports and ignored artifact path; does not start services or create data. |
| `run` | Executes the full cloud quality smoke and writes a redacted ignored artifact. |

Implemented gates:

| Gate | Coverage |
| --- | --- |
| Tunnel / health | MySQL and Qdrant local tunnel ports, backend `/actuator/health`, frontend route smoke |
| Business flow | Temporary user A / B, two txt uploads, document create, parse task create, parse polling, indexing |
| Chunk quality | MySQL `tb_document_chunk` count, contiguous indexes, positive lengths, hashes, `INDEXED` status, vector ids |
| MySQL / Qdrant consistency | Qdrant scroll filtered by user / document / indexVersion and payload comparison against MySQL chunks |
| RAG | Single-document retrieve / QA citation and KnowledgeBase two-document retrieve / QA citation |
| Conversation Trace | Bound KB conversation requires `ragTriggered=true`, `ragRequired=true`, `evidenceCount>0`, and document hit counts |
| Security | Foreign KB detail, foreign KB retrieve, cross-user document add, and foreign trace access must fail |
| Artifact | Redacted JSON artifact, no tokens / API keys / cloud addresses / connection strings / chunk content |

Validation performed:

| Check | Result |
| --- | --- |
| Windows PowerShell parser | PASS |
| `-Mode plan` | PASS |
| `-Mode dry-run` | PASS |
| `-Mode run` overall status | PASS |
| smoke marker | `docpilot-cloud-quality-20260627022219-37efd4` |
| Temporary users | user A `102`, user B `103` |
| User A documents | `102`, `103` |
| User B document | `104` |
| KnowledgeBase | `10` |
| Conversation / message | `9` / `18` |
| Chunk quality | document `102`: `3/3` indexed chunks; document `103`: `3/3` indexed chunks |
| MySQL / Qdrant consistency | both documents matched `3/3` points, `0` missing vector ids |
| Single-document RAG | `3` retrieve hits, `3` QA citations |
| KnowledgeBase RAG | `6` retrieve hits, `6` QA citations, hit distribution `{102:3,103:3}` |
| Conversation Trace | `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, hit distribution `{102:3,103:3}` |
| Permission isolation | foreign KB detail, foreign KB retrieve, cross-user document add, and foreign trace access all rejected |
| Frontend route smoke | `/`, `/login`, `/dashboard`, `/upload`, `/documents`, `/knowledge-bases`, `/conversations` all HTTP 200 and non-blank |
| Artifact redaction | PASS, `0` local redaction-pattern matches in post-run scan |

Boundary: this run created temporary smoke business data and a local redacted artifact under `backend/target/smoke`. The artifact is not intended to be committed. The run did not operate remote Docker, did not use `hk-ops`, did not delete business data, did not change database schema, and did not push.

## 11. RAG Real Quality Gate Smoke

Status: PASS

Evidence source: `backend/target/rag-quality/docpilot-rag-real-quality-20260627213040-4038e1/artifact.json`.

Runner:

- `scripts/smoke/rag-real-quality-smoke.ps1`

Validation performed:

| Check | Result |
| --- | --- |
| `-Mode plan` | PASS |
| `-Mode dry-run` | PASS |
| `-Mode run` overall status | PASS |
| smoke marker | `docpilot-rag-real-quality-20260628150434-2b7b39` |
| Quality min similarity threshold | `0.50` |
| Chunk quality | document `152`: `3/3` indexed chunks; document `153`: `3/3` indexed chunks; duplicate hash count `0`; offset order and token/content length checks passed |
| MySQL / Qdrant consistency | both documents matched `3/3` points, `0` missing vector ids, `0` mismatched fields, `0` missing structure fields |
| Single-document RAG | `3` retrieve hits, `3` QA citations |
| KnowledgeBase RAG | `6` retrieve hits, `6` QA citations, hit distribution `{152:3,153:3}` |
| KnowledgeBase vector score summary | retrieve min `0.65310615`, citation min `0.6255937` |
| No-evidence threshold | PASS: unrelated populated-KB query returned `noEvidence=true`, `0` retrieve hits and `0` QA citations |
| Conversation Trace | `ragTriggered=true`, `ragRequired=true`, `evidenceCount=6`, `memoryCount=1`, `contextSourceCounts={userMemory:1, ragEvidence:6}`, hit distribution `{152:3,153:3}` |
| Permission isolation | foreign KB detail, foreign KB retrieve, cross-user document add, and foreign trace access all rejected |
| Frontend route smoke | `/`, `/login`, `/dashboard`, `/upload`, `/documents`, `/knowledge-bases`, `/conversations` all HTTP 200 and non-blank |
| Artifact redaction | PASS, local redaction-pattern scan had `0` matches |

Boundary: this is a stronger real-link quality gate than the offline eval because it uses the application upload / parse / indexing / Qdrant path. The v3 gate rejects the specific unrelated populated-KB query used by the smoke, v4 adds answer-audit fields plus offline grounding metrics, v5 verifies chunk structure metadata enters Qdrant payload without a schema migration, v6 keeps rerank external calls behind complete explicit provider configuration, and v7 verifies active user memory and KB RAG evidence remain separately visible in Conversation Trace. It is still not a broad production relevance benchmark across large corpora or many domains.

## 12. Current Boundaries

What can be safely claimed:

- Single-document upload, parse, indexing, RAG retrieve, QA, SSE, Agent `rag_qa_tool`, and ToolCall API have been smoke tested.
- Multi-document KnowledgeBase create/add/retrieve/QA with Qdrant has been smoke tested.
- Scope isolation has been smoke tested for ToolCall, KnowledgeBase access, and cross-user document add.
- Real answer generation model has been smoke tested.
- Real embedding provider + Qdrant indexing / retrieval has been smoke tested.
- Conversation Context / Agent Memory with accepted user memory and KnowledgeBase-bound evidence has been smoke tested.
- Unified cloud quality gate smoke has passed once, covering two-document upload / parse / indexing, chunk quality, MySQL / Qdrant consistency, single-document RAG, two-document KnowledgeBase RAG, Conversation Trace, permission isolation, frontend routes, and redacted artifact output.
- RAG real quality gate now passes with evidence confidence, answer audit, chunk structure payload checks, and rejects the smoke unrelated populated-KB query as no-evidence.
- Small real rerank effect smoke confirms the configured rerank provider can be called and returns rerank scores without regressing KB coverage, no-evidence or security gates; the 2026-07-12 representative eval adds 12-case bounded evidence with target coverage preserved, no-evidence preserved, no citation leakage and limited uplift signals.
- MinIO active storage has been smoke tested through upload and parse readback.
- RocketMQ + Outbox active parse flow has been smoke tested through producer, consumer and final parse status.
- Offline Function Calling adapter tests and multi-document eval artifact have passed.
- KnowledgeBase Hybrid / Rerank optional enhancement has local unit/build/eval evidence and remains disabled by default; v6 verifies incomplete rerank provider config falls back without external HTTP, and the 2026-06-28 rerank effect smoke verifies a configured real provider can be called without core-gate regression.

What should be described with caveats:

- Offline eval still uses mock embedding + in-memory vector store.
- Function Calling is currently an OpenAI-compatible mock/offline adapter flow, not a live external model tool-call loop.
- Real answer model and real embedding were verified in separate smoke runs, not in one combined run.
- Populated KnowledgeBase no-evidence has a calibrated smoke threshold, but broader no-evidence precision still needs more eval cases and domain coverage.
- The current RAG real quality gate result is PASS, but broader no-evidence robustness still requires more eval coverage beyond this smoke fixture.
- KnowledgeBase Hybrid / Rerank now has configured-provider smoke, hard-fixture uplift evidence and a 12-case representative eval. It is still bounded smoke / eval evidence, not a broad production relevance benchmark or stable online uplift guarantee.
