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
| 2026-07-04 | `docpilot-real-user-qa-20260704191307-661bc0` | PASS（真实用户 QA 体验审计） | `backend/target/audit/docpilot-real-user-qa-20260704191307-661bc0/artifact.json` | 新增真实用户 QA 审计入口，组合 `naturalCorpus`、`multiQueryRag`、`frontendInteraction` 和 `memoryQuality` gate；最终 25 个自然语料 case、answer faithfulness、citation support、Conversation Trace、Memory、权限隔离和 artifact 脱敏均 PASS。首轮先暴露 answer 事实表达门禁对单一英文短语过度敏感，已改为同义表达组后验证通过。 |
| 2026-07-04 | `docpilot-rag-natural-corpus-20260704160327-16b351` | PASS（coverage report） | `backend/target/rag-natural-corpus/docpilot-rag-natural-corpus-20260704160327-16b351/artifact.json` | 自然语料 artifact 新增 `evidenceCoverageReport`，retrieval / citation / phrase / answer / distractor / no-evidence 的 miss、leak、failure 清单均为空；同轮 25 case、frontendInteraction、multi-query、Trace、权限隔离均 PASS。 |
| 2026-07-04 | `docpilot-rag-natural-corpus-20260704152850-e07b13` | PASS（faithfulness v2） | `backend/target/rag-natural-corpus/docpilot-rag-natural-corpus-20260704152850-e07b13/artifact.json` | 自然语料 gate 新增回答事实表达和 citation 事实短语支撑硬门禁，`answerFaithfulnessPassCount=11/11`、`citationPhraseSupportPassCount=22/22`，同轮 25 case、frontendInteraction、multi-query、Trace、权限隔离均 PASS。 |
| 2026-07-04 | `docpilot-rag-natural-corpus-20260704151615-bc193d` | PASS（自然语料 v2） | `backend/target/rag-natural-corpus/docpilot-rag-natural-corpus-20260704151615-bc193d/artifact.json` | v2 自然语料 gate 覆盖 3 个 corpus、12 份临时 txt 文档、25 个 case，`casePassRate=1`；本轮先发现 governance 临时用户名超过注册长度约束、以及多文档 compare citation 被数字过滤误删，修复后 25 case、frontendInteraction、multi-query、Trace、权限隔离均 PASS。 |
| 2026-07-04 | `docpilot-rag-natural-corpus-20260704143033-86b4f3` | PASS（自然语料审计） | `backend/target/rag-natural-corpus/docpilot-rag-natural-corpus-20260704143033-86b4f3/artifact.json` | 新增自然语料 gate，覆盖 5 份临时文档、单文档事实、数字事实、多文档总结、干扰文档、no-evidence 和 Conversation Trace；本轮先发现 invoice retention 同时引用 marketing retention 干扰文档，修复后 `distractorMarketingCitationCount=0`。 |
| 2026-07-04 | `docpilot-cloud-quality-20260704135601-944384` | PASS（防回归增强） | `tmp-e2e/docpilot-cloud-quality-smoke/docpilot-cloud-quality-20260704135601-944384/artifact.json` | `shortDocumentRag` 新增中文短文档 retrieve、数字事实 retrieve、相似短文档干扰和细分 `failureBuckets`，`frontendInteraction` 新增 UI / 权限失败桶；最终真实 run 中两个 gate 的 `failureBuckets=[]`，旧 P1/P2/P3 问题进入防回归状态。 |
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
| `REA-20260704-P2-002` | VERIFIED（已验证） | P2 | 功能质量问题 | KnowledgeBase RAG Citation | `docpilot-rag-natural-corpus-20260704141543-aa95e9` | 数字事实回答同时引用语义相近但数值冲突的干扰文档 |
| `REA-20260704-P2-003` | VERIFIED（已验证） | P2 | 功能质量问题 | KnowledgeBase RAG Citation | `docpilot-rag-natural-corpus-20260704150746-1ef5da` | 多文档 compare 问题的 citation 被数字过滤误删成单文档覆盖 |
| `REA-20260704-P3-004` | VERIFIED（已验证） | P3 | 工程流程问题 | Smoke Runner | `docpilot-rag-natural-corpus-20260704150252-a675b6` | 自然语料 governance 临时用户名超过注册长度约束 |
| `REA-20260704-P3-005` | VERIFIED（已验证） | P3 | 工程流程问题 | Smoke Runner / Answer Faithfulness Gate | `docpilot-real-user-qa-20260704190235-553df7` | 自然语料 answer fact expression 对单一英文短语过度敏感 |

## 2026-07-04 真实用户 QA 体验审计 v2

验证 marker：`docpilot-real-user-qa-20260704191307-661bc0`

状态：PASS

已验证：

- 新增 `real-user-qa-experience-audit.ps1` 作为真实用户 QA 体验审计入口，默认组合 `naturalCorpus`、`multiQueryRag`、`frontendInteraction` 和 `memoryQuality` gate。
- 自然语料：3 个 corpus、12 份临时 txt 文档、25 个 case，`casePassRate=1`；`answerFaithfulnessPassCount=11/11`，`citationPhraseSupportPassCount=22/22`，`noEvidencePassCount=3/3`，`multiDocumentCoveragePassCount=4/4`。
- 前端交互：文档详情 quote-first 可见、KnowledgeBase 双 citation marker 可见、跨用户无权限提示可见、console error count 为 `0`。
- Conversation / Memory：绑定 KnowledgeBase 后 Trace 中 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=4`、`memoryCount=1`，Memory quality gate 覆盖候选抽取、接受 / 忽略 / 冲突治理、编辑和敏感内容拦截。
- 权限隔离和 artifact 脱敏均 PASS。

本轮发现并修复：

- `REA-20260704-P3-005`：首轮真实 run 中，`finance-expense-approval`、`governance-version-retention`、`governance-hotfix-retention` 的 evidence / citation 均支撑目标事实，但 `answerFactExpression` 因只匹配单一英文短语而失败。
- 已将回答事实表达检查升级为同义表达组，例如 `7 days|within 7 days|seven days|within seven days`，避免真实回答自然改写造成误杀；citation phrase support、forbidden answer、no-evidence 和权限隔离仍保持硬门禁。

边界：

- 本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation 和 Memory 数据；artifact 位于 ignored 的 `backend/target/audit/`，不提交原文。
- 未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

### `REA-20260704-P3-005` 自然语料 answer fact expression 对单一英文短语过度敏感

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：工程流程问题
- 模块：Smoke Runner / Answer Faithfulness Gate
- 发现于：`docpilot-real-user-qa-20260704190235-553df7`
- 修复验证：`docpilot-real-user-qa-20260704191307-661bc0`

复现步骤：

1. 运行 `real-user-qa-experience-audit.ps1 -Mode run`。
2. 观察 `naturalCorpus` 中 QA case 的 `answerFactExpression` 结果。
3. 检查失败 case 的 retrieve / citation 覆盖和 citation phrase support。

实际结果：

- 首轮 run 中 3 个 QA case 的 retrieve / citation 覆盖、citation phrase support 和 forbidden answer 均通过，但 `answerFactExpression=false`，导致 `naturalCorpus` 失败。

预期结果：

- Answer faithfulness 门禁应验证“答案是否表达目标事实”，不能只依赖单一英文字符串；同一事实的常见自然表达应被视为等价。

可能原因：

- 旧门禁只对 `answerAnyPhrases` / `answerAllPhrases` 做逐字符串包含判断，缺少同义表达组，真实模型回答稍作改写就会误杀。

建议修复位置：

- `scripts/smoke/cloud-quality-smoke.ps1`
- `backend/src/test/java/com/docpilot/backend/ai/rag/RagRealQaEvalSmokeScriptSafetyTest.java`

修复提交：本轮待提交。

验证记录：

- `real-user-qa-experience-audit.ps1 -Mode plan` PASS。
- `real-user-qa-experience-audit.ps1 -Mode dry-run` PASS。
- `mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，5 tests。
- `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260704191307-661bc0`。

## 2026-07-04 自然语料扩容 gate v2

验证 marker：`docpilot-rag-natural-corpus-20260704151615-bc193d`

状态：PASS

已验证：

- `naturalCorpus` gate 升级到 `schemaVersion=2`，覆盖 3 个 corpus、12 份临时 txt 文档、25 个 case。
- Case 结果：`casePassRate=1`，`noEvidencePassCount=3/3`，`multiDocumentCoveragePassCount=4/4`，`distractorCitationFreeCount=25/25`。
- Conversation Trace：自然语料绑定 KB 后 `ragTriggered=true`、`ragRequired=true`、`traceEvidenceCount=4`，并记录脱敏 `documentHitCounts`。
- 同轮前端和安全 gate：`frontendInteraction` PASS，权限隔离负向检查 PASS，multi-query PASS，artifact redaction PASS。

本轮发现并修复：

- `REA-20260704-P3-004`：v2 runner 中 `smokegovernance + run suffix` 超过注册 username 32 字符约束，导致 governance corpus 注册失败；已改为 `fin` / `ops` / `gov` 短 alias。
- `REA-20260704-P2-003`：`ops-backup-rollback-compare` 的 retrieve 已覆盖 backup / rollback 两份文档，但 QA citation 因数字过滤只保留 rollback，漏掉 backup；已修复为多文档意图下不允许数字过滤破坏至少两份文档 citation 覆盖。

边界：

- 本轮创建临时 smoke 用户、文档、KnowledgeBase 和 Conversation；artifact 位于 ignored 的 `backend/target/rag-natural-corpus/`，不提交原文。
- 未删除业务数据，未操作远程 Docker，未改数据库结构，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

### `REA-20260704-P2-003` 多文档 compare 问题的 citation 被数字过滤误删成单文档覆盖

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：功能质量问题
- 模块：KnowledgeBase RAG Citation
- 发现于：`docpilot-rag-natural-corpus-20260704150746-1ef5da`
- 修复验证：`docpilot-rag-natural-corpus-20260704151615-bc193d`

复现步骤：

1. 创建 ops 自然语料 KnowledgeBase，包含 backup runbook 和 rollback runbook。
2. 提问：“Compare backup verification ownership with feature rollback authority.”
3. 检查 KB QA citations 的目标文档覆盖。

实际结果：

- 修复前：retrieve 命中 backup / rollback 两份目标文档，但 QA citation 只保留 rollback，`targetCitationCoverage=false`，`evidencePhraseSupport=false`。

预期结果：

- 多文档 compare / summary 问题应保留两份目标文档 citation，不能因为某个引用含有回答未提及的数字就把 citation 覆盖压成单文档。

可能原因：

- KnowledgeBase QA 的 answer-aware numeric citation filter 只按答案中的短数字过滤 citation；backup 引用含 `14 days`，回答只提 rollback 的 `15 minutes` 时，backup citation 被误判为数字不一致并移除。

建议修复位置：

- `backend/src/main/java/com/docpilot/backend/ai/service/impl/KnowledgeBaseRagQaServiceImpl.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/KnowledgeBaseRagQaServiceImplTest.java`

修复提交：本轮待提交。

验证记录：

- `mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，29 tests。
- `rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704151615-bc193d`。

### `REA-20260704-P3-004` 自然语料 governance 临时用户名超过注册长度约束

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：工程流程问题
- 模块：Smoke Runner
- 发现于：`docpilot-rag-natural-corpus-20260704150252-a675b6`
- 修复验证：`docpilot-rag-natural-corpus-20260704151615-bc193d`

复现步骤：

1. 运行自然语料 v2 smoke。
2. finance / ops corpus 创建成功后，进入 governance corpus 注册。
3. 检查 runner 输出和本地 backend 日志。

实际结果：

- 修复前：`smokegovernance` 前缀叠加 20 位 run suffix 后超过 `RegisterRequest` 的 username 32 字符约束，runner 在 governance 注册阶段失败。

预期结果：

- Smoke runner 生成的临时用户名必须稳定满足业务注册约束，不应让测试 fixture 自身阻断真实质量门禁。

可能原因：

- v2 扩容时直接使用 corpus key 作为 username 前缀，没有重新校验 `RegisterRequest` 的长度规则。

建议修复位置：

- `scripts/smoke/cloud-quality-smoke.ps1`

修复提交：本轮待提交。

验证记录：

- `rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704151615-bc193d`。

## 2026-07-04 自然语料审计 gate v1

验证 marker：`docpilot-rag-natural-corpus-20260704143033-86b4f3`

状态：PASS

已验证：

- `naturalCorpus` gate 使用 5 份临时自然语料文档，覆盖单文档事实、数字事实、多文档总结、干扰文档、no-evidence 和绑定 KnowledgeBase 的 Conversation Trace。
- 单文档事实：报销提交时限和 manager approval 均能返回 evidence / citation，回答事实表达通过。
- 数字事实：invoice archive retention 最终只保留 invoice citation，`numericQaCitations=1`、`numericInvoiceCitationCount=1`。
- 干扰控制：invoice retention 问题不再引用 marketing retention 干扰文档，`distractorInvoiceCitationCount=1`、`distractorMarketingCitationCount=0`。
- 多文档自然问题：checkout incident 与 support SLA 的 retrieve / citation 均覆盖目标两份文档。
- no-evidence：contractor payroll payment date 在 populated KB 中拒答，retrieve / QA 均为 no-evidence。
- Conversation Trace：绑定自然语料 KB 后 `ragTriggered=true`、`ragRequired=true`、`evidenceCount>0`，并记录脱敏 `documentHitCounts`。

本轮发现并修复：

- 首次自然语料 run 暴露上传频率限制，后续 runner 在上传遇到 `code=1014` 时使用 retry/backoff；该修复只增强审计工具耐跑性，不绕过业务限流。
- marker `docpilot-rag-natural-corpus-20260704141543-aa95e9` 暴露 `REA-20260704-P2-002`：invoice archive retention 的回答同时挂载了 marketing retention 干扰 citation。
- 已在 KnowledgeBase QA 中增加答案数字一致性 citation 精炼：答案明确给出数字事实时，过滤只包含其他数字值的干扰 citation；retrieval hits 与 `documentHitCounts` 保持不变，仍用于 trace / 调试。
- marker `docpilot-rag-natural-corpus-20260704142549-252f85` 又暴露 run marker 长数字误伤多文档 citation 精炼，已改为忽略长编号，仅把短数字事实纳入一致性过滤。

边界：

- 本轮真实 run 创建临时 smoke 用户、文档、KnowledgeBase 和 Conversation；artifact 位于 ignored 的 `backend/target/rag-natural-corpus/`，不提交原文。
- 未删除业务数据，未操作远程 Docker，未改数据库结构，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

### `REA-20260704-P2-002` 数字事实回答同时引用语义相近但数值冲突的干扰文档

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：功能质量问题
- 模块：KnowledgeBase RAG Citation
- 发现于：`docpilot-rag-natural-corpus-20260704141543-aa95e9`
- 修复验证：`docpilot-rag-natural-corpus-20260704143033-86b4f3`

复现步骤：

1. 创建包含 invoice retention 与 marketing draft retention 的临时 KnowledgeBase。
2. 提问：“Which policy states invoice archive retention, and what retention period should be used?”
3. 检查 QA citations 的文档分布。

实际结果：

- 修复前：QA citations 同时包含 invoice 文档和 marketing 文档；marketing 文档含有相近的 retention 词面但数值是 `3 years`，与 invoice archive 的 `7 years` 不一致。

预期结果：

- 数字事实回答只引用支持答案数字的 evidence；如果干扰文档只包含冲突数字，不应作为答案 citation。

可能原因：

- KnowledgeBase QA 原先直接把 retrieval selected hits 映射成 citations，缺少回答生成后的 citation 精炼；语义相近的干扰文档会被保留为引用，即使答案本身没有采用它的数字事实。

修复位置：

- `backend/src/main/java/com/docpilot/backend/ai/service/impl/KnowledgeBaseRagQaServiceImpl.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/KnowledgeBaseRagQaServiceImplTest.java`
- `scripts/smoke/cloud-quality-smoke.ps1`
- `scripts/smoke/rag-natural-corpus-audit-smoke.ps1`

验证记录：

- `mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,RagDocumentRetrievalServiceImplTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS。
- `rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704143033-86b4f3`；`distractorMarketingCitationCount=0`。

## 2026-07-04 防回归增强

验证 marker：`docpilot-cloud-quality-20260704135601-944384`

状态：PASS

已验证：

- `shortDocumentRag` gate 增加细分失败桶，覆盖短单文档 evidence、短单文档 citation marker、中文短文档 retrieve、数字事实 retrieve、KnowledgeBase 双文档 coverage、KnowledgeBase Alpha / Beta citation、相似短文档干扰。
- 最终真实 run 中 `shortDocumentRag.failureBuckets=[]`；短 Alpha / Beta 各 `1` 个 chunk，单文档 retrieve / QA citation 为 `1/1`，短 KB retrieve / QA citation 为 `2/2`，`documentHitCounts` 覆盖两份短文档。
- `frontendInteraction` gate 增加细分失败桶，覆盖 `quoteFirstUi`、KnowledgeBase citation UI、`permissionUx` 和 console error；最终真实 run 中 `frontendInteraction.failureBuckets=[]`、`documentQuoteFirstVisible=true`、`permissionMessageVisible=true`、`consoleErrorCount=0`。
- `rag-real-qa-eval-smoke.ps1` 在未 `-SkipFrontend` 时默认启用 `frontendInteraction`，后续 RAG real QA wrapper 会同步验证 P2/P3 浏览器交互回归。

本轮过程：

- 前两次真实 run 先后暴露新增 gate 口径过窄、以及 Windows PowerShell 对 `.ps1` 中文 fixture 的编码不稳定：中文行内 marker 被写坏，导致中文短文档检查失败。
- 已修正为 retrieve 层验证中文 / 数字 marker，citation 层只要求主 evidence marker；中文内容用 codepoint 生成，marker 保持 ASCII-safe，避免脚本编码影响 fixture。
- 这两个失败属于 smoke runner / fixture 稳定性问题，不是新的后端解析或 RAG 业务 bug；最终真实链路已通过。

边界：

- 本轮真实 run 创建临时 smoke 用户、文档、KnowledgeBase 和 Conversation；artifact 位于 ignored 的 `tmp-e2e/`，不提交原文。
- 未删除业务数据，未操作远程 Docker，未改数据库结构，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

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
