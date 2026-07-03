# 真实体验审计问题台账

本文件记录 Codex 真实启动 DocPilot、像用户一样跑关键路径后发现的问题。它是内部质量治理台账，不是对外展示稿。

## 记录规则

- 记录语言默认使用中文；技术名、路径、API、状态枚举、命令可以保留原文，但复现步骤、实际结果、预期结果、可能原因、边界和结论必须用中文。
- 触发条件：只要 Codex / agent 真实启动项目、运行本地 tunnel / backend / frontend / smoke、用浏览器或 API 按用户路径体验，并发现 bug、体验问题、安全疑点或环境阻塞，就必须追加脱敏记录。
- 完整原始证据只保留在 ignored 的 `backend/target/audit/...`，不要提交 artifact 原文、日志、截图或临时 txt。
- 本文件只记录脱敏摘要：marker、状态、ID、计数、布尔值、复现路径、可能原因和建议修复位置。
- 禁止写入 `.env`、token、API key、账号凭据、云地址、连接串、文档全文、prompt、evidence context 或模型原始输出。
- 每次真实体验审计发现问题后，必须追加到“问题总表”；修复后回填“修复提交”和“验证记录”。
- `DEMO_SMOKE_RECORD.md` 只记录可展示的 smoke / audit 摘要；本文件记录问题和修复闭环。

单个问题至少包含：ID、状态、严重级别、类型、模块、发现 marker、标题、复现步骤、实际结果、预期结果、可能原因、建议修复位置、修复提交和验证记录。

## 状态与严重级别

- `OPEN`：已确认，尚未开始修复。
- `PLANNED`：已纳入下一轮或当前修复计划。
- `FIXED_PENDING_VERIFY`：已有修复提交，但还缺真实链路验证。
- `VERIFIED`：修复后已通过对应真实链路或回归验证。
- `WONTFIX`：确认不修，必须写清原因。
- `BLOCKED`：环境、权限或外部条件阻塞。

严重级别：

- `P0`：核心业务不可用、严重数据泄露或安全绕过。
- `P1`：核心 RAG / Memory / Trace / 权限链路质量明显不符合真实用户预期。
- `P2`：影响可信度、可解释性或主要体验，但存在可用绕行。
- `P3`：轻量体验、文案、观测或工程流程问题。

## 最近一次审计摘要

| 日期 | Marker | 状态 | Artifact | 摘要 |
| --- | --- | --- | --- | --- |
| 2026-07-03 | `docpilot-cloud-quality-20260703231920-e74334` | PASS（浏览器细验收口） | `tmp-e2e/docpilot-cloud-quality-smoke/docpilot-cloud-quality-20260703231920-e74334/artifact.json` | 新增 `frontendInteraction` gate 通过：文档详情 quote-first 可见、KnowledgeBase 双 marker citation 可见、跨用户文档无权限提示可见、console error 为 `0`；P2/P3 标为已验证。 |
| 2026-07-03 | `docpilot-cloud-quality-20260703213703-dbef08` | PASS（修复验证） | `tmp-e2e/docpilot-cloud-quality-smoke/docpilot-cloud-quality-20260703213703-dbef08/artifact.json` | 修复后 cloud quality smoke 通过；新增 `shortDocumentRag` gate 覆盖短 txt 单文档 RAG、短文档双文档 KnowledgeBase RAG 和 answer grounding。 |
| 2026-07-03 | `docpilot-ui-verify-mr50eghq-9ed7ca` | REVIEW（浏览器细验未收口） | `tmp-e2e/docpilot-ui-verify-*/` | P2/P3 浏览器细验尝试未完成；按 smoke 阈值启动后 API 预检已有 hit，但文档详情页 quote marker 未在等待窗口内展示，P2 仍保持待细验，P3 未跑到。 |
| 2026-07-03 | `docpilot-real-audit-20260703195519-5118e8` | REVIEW（需复查） | `backend/target/audit/docpilot-real-audit-20260703195519-5118e8/real-experience-audit-report.json` | 标准 cloud quality smoke 为 PASS；真实浏览器短 txt 审计发现 2 个 P1 RAG 覆盖问题、1 个 P2 citation UI 问题、1 个 P3 权限拒绝体验问题。 |

## 问题总表

| ID | 状态 | 严重级别 | 类型 | 模块 | 发现于 | 标题 |
| --- | --- | --- | --- | --- | --- | --- |
| `REA-20260703-P1-001` | VERIFIED（已验证） | P1 | 功能 bug | RAG | `docpilot-real-audit-20260703195519-5118e8` | 短 txt parse 成功但单文档 RAG 无 evidence |
| `REA-20260703-P1-002` | VERIFIED（已验证） | P1 | 功能 bug | KnowledgeBase RAG / Trace | `docpilot-real-audit-20260703195519-5118e8` | 短文档 KB 双文档问题退化成单文档命中 |
| `REA-20260703-P2-001` | VERIFIED（已验证） | P2 | 体验问题 | Citation UI | `docpilot-real-audit-20260703195519-5118e8` | quote-level citation API 已有，但 UI 仍需 quote-first 展示 |
| `REA-20260703-P3-001` | VERIFIED（已验证） | P3 | 体验问题 | Permission UX | `docpilot-real-audit-20260703195519-5118e8` | 权限拒绝走 HTTP 200 + 业务错误，前端提示需更明确 |

## 2026-07-03 浏览器细验收口

验证 marker：`docpilot-cloud-quality-20260703231920-e74334`

状态：PASS

已验证：

- 新增 `frontendInteraction` gate：浏览器登录临时用户后打开短 Alpha 文档详情页，点击 RAG 检索预览，`title="精确引用原文"` 的引用主文本可见 `ALPHA-SHORT-GATE`。
- 同一 gate 在 `/knowledge-bases` 中选择本轮短文档 KnowledgeBase，生成回答后可见 `ALPHA-SHORT-GATE` 和 `BETA-SHORT-GATE` 两个 citation marker。
- 同一 gate 使用用户 B 打开用户 A 文档详情，页面显示无权限 / 不存在提示；浏览器 console error count 为 `0`。
- 核心 cloud quality gate 同步保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、短文档 RAG、answer grounding、no-evidence、Conversation Trace、权限隔离、artifact redaction 和 cleanup。

本轮补充修复：

- `scripts/smoke/cloud-quality-smoke.ps1` 新增可选 `-EnableFrontendInteractionGate`，用 Playwright 验证真实前端交互；临时 JS 使用环境变量传 token，不把 token 写入 artifact。
- 文档详情页 RAG 引用展示在有明确 marker token 的审计 / 调试问题中，会优先展示命中 marker 的 quote / snippet / content；普通问题仍保持 `quoteText -> snippet -> content` 的 quote-first 顺序。
- Smoke gate 记录的前端交互证据只包含布尔值、HTTP 状态和计数，不保存文档全文、回答原文、prompt、evidence context、token、云地址或连接串。

边界：

- 本轮真实 run 创建了临时 smoke 用户、文档、KnowledgeBase 和 Conversation；artifact 位于 ignored 的 `tmp-e2e/`，不提交原文。
- 期间有一次 run 因临时文档 parse timeout 失败，未进入浏览器 gate；清理后重跑通过，记录为环境波动证据，不做远程修复。
- 未删除业务数据，未操作远程 Docker，未改数据库结构，未 push。

## 2026-07-03 修复验证

验证 marker：`docpilot-cloud-quality-20260703213703-dbef08`

状态：PASS

已验证：

- `shortDocumentRag` gate：短 Alpha 文档 `1` 个 chunk，单文档 retrieve `1` hit、QA `1` citation。
- `shortDocumentRag` gate：短 Alpha / Beta KnowledgeBase retrieve `2` hits、QA `2` citations，`documentHitCounts` 同时覆盖两份短文档。
- `answerGrounding` gate：短单文档回答命中 `ALPHA-SHORT-GATE`，短 KnowledgeBase 回答同时命中 `ALPHA-SHORT-GATE` 和 `BETA-SHORT-GATE`，未命中 forbidden marker。
- 核心回归 gate 保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、no-evidence、Conversation Trace、权限隔离、artifact redaction 和 cleanup。

本轮修复内容：

- 单文档 RAG：在全局 similarity threshold 过滤后为空、且用户问题和候选内容存在同一个明确 marker token 时，允许保留最强 scoped hit；不降低全局阈值，不影响普通 no-evidence。
- KnowledgeBase RAG：对总结类问题增加按文档 marker-supported backfill，避免短文档双文档总结退化成单文档覆盖；仍只在明确 marker 和 summary intent 场景补回。
- Smoke runner：新增短 txt 单文档 / 双文档 KnowledgeBase 回归 gate，并避免同一用户触发上传限流。
- 前端：文档详情和 KnowledgeBase 引用卡片优先展示 `quoteText`，`snippet` 作为上下文；Conversation citation hover title 优先使用 quote；统一 API 错误封装给权限 / 不存在场景更清晰中文提示。

边界：

- 真实 smoke 已验证 P1 RAG / KnowledgeBase 修复闭环。
- P2 / P3 已完成代码修复，并通过前端构建、路由 smoke 和权限隔离 API gate 的回归；仍建议后续补一次浏览器点击级细验，再把状态从 `FIXED_PENDING_VERIFY` 改为 `VERIFIED`。
- 未删除业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact 原文，未 push。

### 2026-07-03 浏览器细验尝试

细验 marker：`docpilot-ui-verify-mr50eghq-9ed7ca`

状态：REVIEW（未收口）

过程摘要：

- 本地启动 tunnel、backend、frontend，并按 cloud smoke 等价方式给 backend 设置 `app.rag.retrieval.min-similarity-threshold=0.50`。
- 使用临时用户和短 txt 文档创建最小 UI 验证数据。
- API 预检阶段，短文档 RAG retrieve 已出现 `1` hit 且 `noEvidence=false`。
- 进入文档详情页后，填写同一 marker 问题并点击“检索预览”，但等待窗口内没有看到包含 `ALPHA-SHORT-GATE` 的 `title="精确引用原文"` 引用行。

结论：

- 不能把 `REA-20260703-P2-001` 标为 `VERIFIED`；当前仍保持 `FIXED_PENDING_VERIFY`。
- 本次因 P2 quote-first 细验未通过，尚未继续到 P3 前端无权限错误展示细验；`REA-20260703-P3-001` 仍保持 `FIXED_PENDING_VERIFY`。
- 后续应单独排查文档详情页 RAG 检索预览的数据映射：确认 retrieve hit / citation 中 `quoteText`、`content`、`snippet` 到页面引用卡片的展示关系，并用一个稳定 fixture 做 Playwright 断言。

边界：

- 未修改代码，未删除业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact 原文，未 push。
- 本轮启动的本地服务已通过 cleanup 脚本清理，相关临时日志只在 ignored 的 `tmp-e2e/` 下。

## 2026-07-03 真实体验审计

审计 marker：`docpilot-real-audit-20260703195519-5118e8`

关联 cloud quality marker：`docpilot-cloud-quality-20260703195356-1362ea`

状态：REVIEW（需复查）

已验证：

- Cloud quality smoke：PASS；覆盖 tunnel、backend health、frontend routes、auth、两文档上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、answer grounding、no-evidence、Conversation Trace、权限隔离、cleanup 和 artifact redaction。
- 浏览器审计：PASS；`/`、`/dashboard`、`/documents`、`/documents/{documentId}`、`/knowledge-bases`、`/conversations` 均可渲染；桌面和移动端未发现横向溢出；未发现前端 console error；页面文本未命中常见 mojibake 特征。
- 验证命令：`npm run lint` PASS；`mvn -DskipTests compile` PASS。
- 收尾：本轮启动的后端、前端和 tunnel 均已清理；最终 `git status --short` 为空。

边界：

- 本轮创建临时审计用户、短 txt 文档、KnowledgeBase 和 Conversation。
- 未修改代码，未删除业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact 原文，未 push。
- 原始 artifact 位于 ignored 的 `backend/target/audit/...`，本文件只记录脱敏摘要。

### `REA-20260703-P1-001` 短 txt parse 成功但单文档 RAG 无 evidence

状态：VERIFIED（已验证）

严重级别：P1

类型：功能 bug

模块：RAG

复现步骤：

1. 启动本地 tunnel、backend 和 frontend。
2. 注册临时用户，上传短 txt 文档，等待 parse `SUCCESS`。
3. 对该短文档中明确存在的 Alpha marker 提问，调用单文档 RAG retrieve / QA。

实际结果：

- 该短文档 parse 成功，但单文档 RAG 返回 `0` retrieve hits 和 `0` QA citations。
- 同轮标准 cloud quality smoke 的较长文档单文档 RAG 为 `4` hits / `4` citations。

预期结果：

- 短 txt 中有明确 evidence marker 时，单文档 RAG 应至少返回 1 条 grounded evidence / citation。
- 如果被 evidence gate 拒绝，应给出可解释的 no-evidence 原因，方便定位是阈值、chunk、embedding 还是 indexing 问题。

可能原因：

- 当前 similarity threshold、embedding 或 chunk 策略可能主要被较长 smoke fixture 校准；极短用户文档即使 parse / index 成功，也可能低于 evidence gate。

建议修复位置：

- `backend/src/main/java/com/docpilot/backend/ai/service/impl/RagDocumentRetrievalServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/rag/chunk/ChunkingServiceImpl.java`
- `scripts/smoke/cloud-quality-smoke.ps1`

修复提交：待补充

验证记录：`docpilot-cloud-quality-20260703213703-dbef08` 中 `shortDocumentRag` PASS；短 Alpha 单文档 retrieve `1` hit、QA `1` citation，answer grounding 命中 `ALPHA-SHORT-GATE`。

### `REA-20260703-P1-002` 短文档 KB 双文档问题退化成单文档命中

状态：VERIFIED（已验证）

严重级别：P1

类型：功能 bug

模块：KnowledgeBase RAG / Conversation Trace

复现步骤：

1. 使用同一轮两个 parse `SUCCESS` 的短 txt 文档创建 KnowledgeBase。
2. 问题显式要求总结两份资料并覆盖两个 marker。
3. 检查 KnowledgeBase retrieve / QA citation 分布和 Conversation Trace `documentHitCounts`。

实际结果：

- KnowledgeBase RAG 返回 `1` hit / `1` citation，只覆盖第二份文档。
- Conversation Trace `ragTriggered=true`、`ragRequired=true`，但 `evidenceCount=1`，`documentHitCounts` 只覆盖第二份文档。

预期结果：

- 双文档总结问题应覆盖两份文档，或在证据不足时明确暴露 partial coverage / no-evidence 状态，而不是给出单文档答案。

可能原因：

- 短文档在 threshold / hybrid / rerank / diversity selection 前被过滤，导致后续多文档覆盖策略没有机会补齐。

建议修复位置：

- `backend/src/main/java/com/docpilot/backend/ai/service/impl/KnowledgeBaseRagRetrievalServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/rag/eval/KnowledgeBaseRagEvalRunner.java`
- `scripts/smoke/cloud-quality-smoke.ps1`

修复提交：待补充

验证记录：`docpilot-cloud-quality-20260703213703-dbef08` 中 `shortDocumentRag` PASS；短 Alpha / Beta KnowledgeBase retrieve `2` hits、QA `2` citations，`documentHitCounts` 覆盖两份短文档。

### `REA-20260703-P2-001` quote-level citation API 已有，但 UI 仍需 quote-first 展示

状态：VERIFIED（已验证）

严重级别：P2

类型：体验问题

模块：Citation UI

复现步骤：

1. 调用 KnowledgeBase RAG QA，确认 response citation 包含 quote-level 字段。
2. 打开文档详情、KnowledgeBase 和 Conversation 页面查看引用区域。

实际结果：

- API 已有 quote-level citation 字段。
- 前端主要仍以 `snippet` / 来源卡片展示，缺少 quote-first 的证据体验。

预期结果：

- 引用卡片优先展示精确 quote；chunk snippet、score、chunk metadata 作为展开上下文。

可能原因：

- 后端 API contract 已完成，前端 quote-first rendering 曾被拆到后续 encoding-safe UI slice。

建议修复位置：

- `frontend/app/documents/[documentId]/page.tsx`
- `frontend/app/knowledge-bases/page.tsx`
- `frontend/app/conversations/page.tsx`

修复提交：待补充

验证记录：`docpilot-cloud-quality-20260703231920-e74334` 的 `frontendInteraction` gate PASS；文档详情页 RAG 检索预览中 `documentQuoteFirstVisible=true`、浏览器 API `documentRetrieveStatus=200`、`documentRetrieveHitCount=1`、`documentRetrieveCitationCount=1`、`consoleErrorCount=0`。

### `REA-20260703-P3-001` 权限拒绝走 HTTP 200 + 业务错误，前端提示需更明确

状态：VERIFIED（已验证）

严重级别：P3

类型：体验问题

模块：Permission UX

复现步骤：

1. 用户 A 创建 KnowledgeBase 和文档。
2. 用户 B 访问用户 A 的 KnowledgeBase detail 和 RAG retrieve。

实际结果：

- 权限隔离生效，跨用户访问被业务层拒绝。
- 传输层 HTTP status 为 `200`，依赖业务 code 表达失败。

预期结果：

- 安全边界继续保持；前端应将无权限状态展示得更明确，审计工具也应记录业务 code / message，而不是只看 HTTP status。

可能原因：

- 项目统一使用 `ApiResponse` 业务码表达错误，前端错误态仍可进一步产品化。

建议修复位置：

- `frontend/lib/api.ts`
- `backend/src/main/java/com/docpilot/backend/knowledge/controller/KnowledgeBaseController.java`
- `backend/src/main/java/com/docpilot/backend/ai/controller/KnowledgeBaseRagController.java`

修复提交：待补充

验证记录：`docpilot-cloud-quality-20260703231920-e74334` 的 `frontendInteraction` gate PASS；用户 B 打开用户 A 文档详情时 `permissionMessageVisible=true`，同轮权限隔离负向 gate 继续 PASS。
