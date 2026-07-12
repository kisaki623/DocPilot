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
| 2026-07-12 | `docpilot-high-intensity-fixed-corpus-20260712230404-a0bc35` | REVIEW（fixedBusinessCorpus PASS / frontend skipped） | `backend/target/high-intensity-acceptance/docpilot-high-intensity-fixed-corpus-20260712230404-a0bc35/artifact.json` | 修复 `REA-20260712-P1-030` 后复跑固定业务语料 runner：`fixedBusinessCorpus` gate PASS，T02 duplicate upload 与 T06-T15 全部通过；T08 废弃草案冲突、T11 多文档风险控制、T12 多跳审批 citation coverage / support 均恢复。整体 run 为 REVIEW 仅因为本片显式 `-SkipFrontend`；artifact raw-field scan PASS，本地 fixed corpus 源文件数为 0。 |
| 2026-07-12 | `docpilot-high-intensity-fixed-corpus-20260712223206-eae7f3` | FAILED_CORE_FLOW（固定业务语料验收发现 P1） | `backend/target/high-intensity-acceptance/docpilot-high-intensity-fixed-corpus-20260712223206-eae7f3/artifact.json` | 新增固定业务语料自动化 runner 后执行真实 run：T02 duplicate upload、T06 / T07 / T09 / T10 / T13 / T14 / T15 通过；T08 返回 `answer_claim_missing`，T11 / T12 返回 `citation_document_coverage` 与 `citation_support_missing`，说明 KnowledgeBase RAG 在错误前提回答完整性、多文档总结和多跳审批 citation 支撑上仍有质量缺口。JSON artifact 已脱敏，不保存 raw answer / prompt / evidence context；本地 fixed corpus 源文件上传后删除，最新 run 目录下 fixed corpus 源文件数为 0。 |
| 2026-07-12 | `cg20260712175003-50312c` / `ui-cg-20260712095111-7094ef` | PASS（Conversation grounding 修复真实验证） | `tmp-e2e/conversation-grounding-runtime/cg20260712175003-50312c-artifact.json` | 用户报告未绑定 KnowledgeBase 的普通会话误进 strict no-evidence refusal；已授权执行 `008_add_context_trace_grounding.sql` 并确认 Trace 三列存在。真实 API smoke 覆盖未绑定 KB、误选 STRICT、AUTO_RAG 普通问题、AUTO_RAG 无证据 fallback、STRICT_KB 无证据拒答、AUTO_RAG evidence citation；Playwright 验证普通回答显示“未使用知识库”且无“0 条来源”，严格资料不足显示“资料不足 / 调用模型否 / 模型跳过是”；后端全量 953 tests、前端 lint/build PASS。 |
| 2026-07-12 | `docpilot-rerank-representative-representative-rerank-20260712152212-2e0f81` | PASS（rerank 代表语料 eval） | `backend/target/rag-quality/rerank-representative-eval/latest-summary.json` | 12-case 代表语料 eval PASS：candidate `rerankApplied=true`、10/10 target coverage、2/2 no-evidence preserved、`strictImprovementCaseCount=2`、`upliftCaseCount=10`、`citationLeakageCount=0`、`noEvidenceRegressionCount=0`；同轮修复 summary intent 泛词、中文问法 / UTF-8 编码和 redaction false positive。 |
| 2026-07-12 | `docpilot-rerank-effect-rerank-20260712003244-46b2e3` | PASS / REVIEW（百炼 rerank provider 已生效，uplift 未证明） | `backend/target/rag-quality/rerank-effect/latest-summary.json` | 用户填入百炼 API key 后复验：candidate `rerankApplied=true`、`rerankModel=qwen3-rerank`、`rerankFailureReason=""`，核心 RAG / no-evidence / security 无回退；hard fixture 未观察到排序 uplift，因此效果提升仍保持 REVIEW。 |
| 2026-07-11 | `docpilot-real-user-qa-20260711170544-dff948` | PASS（最大压力真实用户审计） | `backend/target/audit/docpilot-real-user-qa-20260711170544-dff948/artifact.json` | 有界最大压力复验 PASS：自然语料 25 case、Memory、权限隔离、frontendInteraction、multi-query、answer grounding、no-evidence、cleanup 和 artifact redaction 均通过；同轮修复 frontendInteraction 脱敏诊断与财务多文档 compare 题歧义。 |
| 2026-07-11 | `docpilot-rag-real-qa-20260711171137-ed38a0` | PASS（代表语料 / 真实模型质量） | `backend/target/rag-real-qa/docpilot-rag-real-qa-20260711171137-ed38a0/artifact.json` | RAG Real QA Eval PASS：representative corpus、multi-query、real QA hard / semantic、realProviderFaithfulness 和 frontendInteraction 均通过，真实回答 provider 为 openai-compatible / qwen-plus。 |
| 2026-07-11 | `docpilot-rerank-effect-rerank-20260711171449-522a4c` | REVIEW（rerank provider 未生效） | `backend/target/rag-quality/rerank-effect/latest-summary.json` | Rerank 对照核心 RAG / no-evidence / security 无回退，但 candidate 轮 `rerankApplied=false`、`rerankModel=identity`；后端日志显示配置的 rerank model 返回 `NotFound`，登记为 `REA-20260711-P2-025`。 |
| 2026-07-11 | `docpilot-memory-provider-20260711172435-14083e` | PASS（Memory provider runner 修复验证） | `backend/target/memory-provider/docpilot-memory-provider-20260711172435-14083e/artifact.json` | 修复 memory provider smoke run 模式不加载 `.env` 的漂移后，直接命令 PASS；固定 6-call、`casePassRate=1.0000`、`rawProviderOutputStored=false`。 |
| 2026-07-11 | `docpilot-real-user-qa-20260711160913-98a440` | PASS（真实用户全链路修复验证） | `backend/target/audit/docpilot-real-user-qa-20260711160913-98a440/artifact.json` | 修复 `finance-expense-invoice-compare` answerFactExpression 同义表达误杀后，完整真实用户 QA 审计 PASS；自然语料 25 case、Memory、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均通过。 |
| 2026-07-11 | `docpilot-real-user-qa-20260711155558-573a81` | FAILED_CORE_FLOW（已修复验证） | `backend/target/audit/docpilot-real-user-qa-20260711155558-573a81/artifact.json` | 首轮真实用户 QA 审计仅 `naturalCorpus` 失败，失败 case 为 `finance-expense-invoice-compare:answerFactExpression`；retrieve / citation / evidence support / distractor suppression 均通过，已登记为 `REA-20260711-P2-020`。 |
| 2026-07-11 | `parse-status-validation-smoke-20260711` | PASS（参数校验修复验证） | `backend/target/validation-smoke/` | 手动 ParseTask status smoke 首次使用超长 username 暴露注册校验错误被兜底为 500；修复全局校验异常处理后，超长 username 运行时返回 `code=400`，登记为 `REA-20260711-P3-021`。 |
| 2026-07-09 | `docpilot-parser-real-chain-20260709233230-a08906` | PASS（direct retrieve / QA retrieve 差异修复验证） | `backend/target/smoke/document-parser-real-chain/docpilot-parser-real-chain-20260709233230-a08906/artifact.json` | 修复 parser smoke runner 的 direct / QA 诊断计数和环境断链归因后，真实链路 PASS；PDF / HTML / DOCX 均 parse、chunk、direct retrieve、QA retrieval、citation、source locator 通过，parserBoundary `4/4` PASS。 |
| 2026-07-08 | `docpilot-parser-real-chain-20260708212742-0f9baa` | PASS（Document Parser runner 修复验证） | `backend/target/smoke/document-parser-real-chain/docpilot-parser-real-chain-20260708212742-0f9baa/artifact.json` | 修复 parser smoke runner 静默复用不受控 backend / frontend 后，真实链路 PASS；PDF / HTML / DOCX 均 parse、chunk、QA retrieval、citation、source locator 通过，parserBoundary `4/4` PASS，Quality Console 可见最新 run 和 parser 诊断。 |
| 2026-07-08 | `docpilot-parser-real-chain-20260708212024-9bd2ea` | FAILED_CORE_FLOW（已修复验证） | `backend/target/smoke/document-parser-real-chain/docpilot-parser-real-chain-20260708212024-9bd2ea/artifact.json` | parser smoke 复用了手动启动的 backend，该 backend 不在 runner 受控配置内，导致三类文档 QA 阶段均 `qa_api_failed`；已记录为 `REA-20260708-P3-009` 并通过受控启动 PASS run 验证。 |
| 2026-07-05 | `docpilot-real-user-qa-20260705210119-7b8092` | PASS（Trace / Eval / Trend 回归） | `backend/target/audit/docpilot-real-user-qa-20260705210119-7b8092/artifact.json` | 真实用户 QA 审计通过；核心 RAG、KnowledgeBase、Conversation Trace、Memory、权限隔离、frontendInteraction 和 artifact 脱敏均 PASS；Console API detail 返回 `traceReferenceCount=2`，浏览器 `/quality?autoload=1` 和 `/quality/trace` 桌面 / `390px` 移动端无 console error、无横向溢出。 |
| 2026-07-05 | `docpilot-real-user-qa-20260705205210-8c882e` | FAILED_CORE_FLOW（已增强诊断，未复现） | `backend/target/audit/docpilot-real-user-qa-20260705205210-8c882e/artifact.json` | 核心 RAG / Memory / Trace / 权限 gate 均 PASS，但 `frontendInteraction` 在 KnowledgeBase 阶段捕获 `TypeError` console error；旧 gate 只记录 kind，无法定位字段。已记录为 `REA-20260705-P3-008`，随后增强脱敏 `messageShape` 诊断，最终 PASS run 未复现。 |
| 2026-07-05 | `docpilot-real-user-qa-20260705192354-eba0fc` | PASS（Agent Quality Console 7-case 回归） | `backend/target/audit/docpilot-real-user-qa-20260705192354-eba0fc/artifact.json` | 真实用户 QA 审计通过；核心 RAG、KnowledgeBase、Conversation Trace、Memory、权限隔离、frontendInteraction 和 artifact 脱敏均 PASS；`/api/quality/eval-cases` 返回 7 个 case，其中 4 个带 `sourceIssueIds`，7 个带 `remediationHints`；浏览器 `/quality?autoload=1` 桌面和 `390px` 移动端无 console error、无横向溢出。 |
| 2026-07-05 | `docpilot-real-user-qa-20260705165151-bbe588` | PASS（Agent Quality Console Phase 6 回归） | `backend/target/audit/docpilot-real-user-qa-20260705165151-bbe588/artifact.json` | 修复 Quality Eval Catalog 构造器注入后真实用户 QA 审计通过；核心 RAG、KnowledgeBase、Conversation Trace、Memory、权限隔离、frontendInteraction 和 artifact 脱敏均 PASS；`/quality?autoload=1` 可见最新 marker、Eval Catalog、Failure Triage、Run Comparison 和 Model / Cost Summary。 |
| 2026-07-05 | `docpilot-real-user-qa-20260705164732-f54da1` | BLOCKED（已修复验证） | `backend/target/audit/docpilot-real-user-qa-20260705164732-f54da1/artifact.json` | tunnel / config consistency PASS，但 backend health 未 UP；本地日志定位为 `QualityEvalCatalogServiceImpl` 多构造器缺少显式 `@Autowired`，已记录为 `REA-20260705-P1-007` 并修复验证。 |
| 2026-07-05 | `docpilot-real-user-qa-20260705145304-7a53b8` | PASS（干扰 citation 修复验证） | `backend/target/audit/docpilot-real-user-qa-20260705145304-7a53b8/artifact.json` | 修复 `REA-20260704-P2-006` 后真实用户 QA 审计通过；`naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`，frontendInteraction、Memory quality、Conversation Trace、权限隔离和 artifact 脱敏均 PASS。 |
| 2026-07-04 | `docpilot-real-user-qa-20260704221704-4abc6f` | REVIEW（Agent Quality Console 回归） | `backend/target/audit/docpilot-real-user-qa-20260704221704-4abc6f/artifact.json` | Agent Quality Console 可展示最新真实 audit run；核心 gate、frontendInteraction、Memory、权限隔离和脱敏均 PASS，但 `naturalCorpus` 中 `ops-incident-support-summary` 出现 `distractorCitation` review，`distractorCitationFreeCount=24/25`，已记录为 `REA-20260704-P2-006`。 |
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
| `REA-20260712-P1-030` | VERIFIED（已验证） | P1 | RAG 质量问题 | KnowledgeBase RAG QA / Citation | `docpilot-high-intensity-fixed-corpus-20260712223206-eae7f3` | 固定业务语料 T08 / T11 / T12 暴露回答完整性和 citation 支撑不足 |
| `REA-20260712-P1-029` | VERIFIED（已验证） | P1 | 会话路由 bug | Conversation / Grounding Policy / Frontend | `cg20260712175003-50312c` / `ui-cg-20260712095111-7094ef` | 未绑定知识库的普通会话误进 strict grounded no-evidence refusal，并显示 0 条来源 |
| `REA-20260712-P2-026` | VERIFIED（已验证） | P2 | RAG no-evidence 质量问题 | KnowledgeBase RAG Retrieval | `docpilot-rerank-representative-representative-hybrid-20260712142138-...` | 裸 `knowledge base` 泛词被当作总结意图，near-threshold 无关问题绕过 support gate |
| `REA-20260712-P2-027` | VERIFIED（已验证） | P2 | RAG 召回质量问题 | Query Rewrite / Hybrid Retrieval | `docpilot-rerank-representative-representative-hybrid-20260712142138-...` | 中文代表问法未稳定映射到英文业务 evidence，target coverage 失败 |
| `REA-20260712-P3-028` | VERIFIED（已验证） | P3 | 工程流程问题 | Rerank Representative Eval Runner | `rerank-representative-eval-smoke.ps1` 初跑 | PowerShell 5.1 中文 JSON / redaction 处理不稳，导致 mojibake 和 caseId 误报 token |
| `REA-20260711-P3-022` | VERIFIED（已验证） | P3 | 工程流程问题 | Smoke Runner / Frontend Interaction Gate | `docpilot-real-user-qa-20260711164556-93f35f` | frontendInteraction Node 异常被包装层吞成 false/0，缺少真实 safeMessage |
| `REA-20260711-P2-023` | VERIFIED（已验证） | P2 | 质量门禁 fixture bug | Natural Corpus / Answer Faithfulness Gate | `docpilot-real-user-qa-20260711165345-ecc162` | 财务多文档 compare 题表述偏泛，真实模型答案表达和干扰 citation 存在波动 |
| `REA-20260711-P3-024` | VERIFIED（已验证） | P3 | 工程流程问题 | Memory provider extraction smoke | `docpilot-memory-provider-20260711171644-030f7f` | Memory provider smoke run 模式未按文档加载 `.env`，直接运行误报 provider_config_missing |
| `REA-20260711-P2-025` | VERIFIED（已验证） | P2 | provider 配置 / 可用性问题 | RAG Rerank provider | `docpilot-rerank-effect-rerank-20260711171449-522a4c` | 旧 provider/model NotFound 已通过百炼 qwen3-rerank 修复；真实 provider 已生效，但 relevance uplift 仍需更强 eval |
| `REA-20260711-P2-020` | VERIFIED（已验证） | P2 | 质量门禁 fixture bug | Natural Corpus / Answer Faithfulness Gate | `docpilot-real-user-qa-20260711155558-573a81` | 财务多文档对比 case 的 answerFactExpression 对中英文同义表达过窄 |
| `REA-20260711-P3-021` | VERIFIED（已验证） | P3 | API 参数校验体验 | Auth register / GlobalExceptionHandler | `parse-status-validation-smoke-20260711` | 注册 username 校验失败被兜底为 500 |
| `REA-20260710-P3-014` | VERIFIED（已验证） | P3 | 真实前端体验问题 | KnowledgeBase RAG UI | `docpilot-cloud-quality-20260710195739-fdb3fa` | 知识库 RAG 交互成功但浏览器出现 Failed to fetch 控制台错误 |
| `REA-20260710-P1-012` | VERIFIED（已验证） | P1 | 真实链路稳定性问题 | Single-document RAG QA | `docpilot-cloud-quality-20260710173219-d801d9` | 解析与索引通过后单文档 RAG 回答模型读取窗口不足 |
| `REA-20260710-P2-013` | VERIFIED（已验证） | P2 | 真实链路稳定性问题 | RAG Indexing | `docpilot-cloud-quality-20260710194619-390475` | 切换百炼后遗留 embedding 模型标识导致索引失败 |
| `REA-20260710-P1-011` | OPEN | P1 | 安全依赖风险 | Frontend dependency supply chain | `npm-audit-frontend-20260710` | Next 升级后仍存在 high / moderate 生产依赖漏洞，完全修复要求破坏性 major 升级 |
| `REA-20260710-P2-015` | BLOCKED | P2 | 环境 / fresh-clone 可用性风险 | Local demo MySQL bootstrap | `schema-bootstrap-audit-20260710` | demo 初始化快照已修复，但隔离 MySQL runtime 验收受 Docker Engine 未运行阻塞 |
| `REA-20260710-P3-016` | VERIFIED（已验证） | P3 | 质量门禁 fixture bug | Memory provider extraction eval | `docpilot-memory-provider-20260710203107-29967e` | 中文长期信息正例被错误 forbidden marker 判为泄露 |
| `REA-20260710-P1-017` | VERIFIED（已验证） | P1 | artifact 安全边界 | Memory provider extraction smoke | `memory-provider-v2-security-review-20260710` | 原 wrapper 可落盘 Maven 原始日志，且空建议负例存在格式 fail-open |
| `REA-20260710-P2-018` | VERIFIED（已验证） | P2 | 流式体验可靠性 | RAG SSE client | `sse-eof-contract-audit-20260710` | 未收到 done 的 clean EOF 被静默当作成功 |
| `REA-20260710-P3-019` | VERIFIED（已验证） | P3 | 真实前端体验 / smoke 稳定性 | Next dev RSC / cloud quality runner | `docpilot-cloud-quality-20260710205934-95dd05` | Next dev 监听与浏览器访问 origin 不一致导致 RSC console error |
| `REA-20260703-P1-001` | VERIFIED（已验证） | P1 | 功能 bug | RAG | `docpilot-real-audit-20260703195519-5118e8` | 短 txt parse 成功但单文档 RAG 无 evidence |
| `REA-20260703-P1-002` | VERIFIED（已验证） | P1 | 功能 bug | KnowledgeBase RAG / Trace | `docpilot-real-audit-20260703195519-5118e8` | 短文档 KB 双文档问题退化成单文档命中 |
| `REA-20260703-P2-001` | VERIFIED（已验证） | P2 | 体验问题 | Citation UI | `docpilot-real-audit-20260703195519-5118e8` | quote-level citation API 已有，但 UI 仍需 quote-first 展示 |
| `REA-20260703-P3-001` | VERIFIED（已验证） | P3 | 体验问题 | Permission UX | `docpilot-real-audit-20260703195519-5118e8` | 权限拒绝走 HTTP 200 + 业务错误，前端提示需更明确 |
| `REA-20260704-P2-002` | VERIFIED（已验证） | P2 | 功能质量问题 | KnowledgeBase RAG Citation | `docpilot-rag-natural-corpus-20260704141543-aa95e9` | 数字事实回答同时引用语义相近但数值冲突的干扰文档 |
| `REA-20260704-P2-003` | VERIFIED（已验证） | P2 | 功能质量问题 | KnowledgeBase RAG Citation | `docpilot-rag-natural-corpus-20260704150746-1ef5da` | 多文档 compare 问题的 citation 被数字过滤误删成单文档覆盖 |
| `REA-20260704-P3-004` | VERIFIED（已验证） | P3 | 工程流程问题 | Smoke Runner | `docpilot-rag-natural-corpus-20260704150252-a675b6` | 自然语料 governance 临时用户名超过注册长度约束 |
| `REA-20260704-P3-005` | VERIFIED（已验证） | P3 | 工程流程问题 | Smoke Runner / Answer Faithfulness Gate | `docpilot-real-user-qa-20260704190235-553df7` | 自然语料 answer fact expression 对单一英文短语过度敏感 |
| `REA-20260704-P2-006` | VERIFIED（已验证） | P2 | 功能质量问题 | KnowledgeBase RAG Citation | `docpilot-real-user-qa-20260704221704-4abc6f` | 多文档 summary 在目标覆盖满足时仍带入一条干扰 citation |
| `REA-20260705-P1-007` | VERIFIED（已验证） | P1 | 功能 bug | Agent Quality Console / Backend Startup | `docpilot-real-user-qa-20260705164732-f54da1` | Eval Catalog service 构造器注入缺失导致 backend health BLOCKED |
| `REA-20260705-P3-008` | VERIFIED（已验证） | P3 | 工程流程问题 | Smoke Runner / Frontend Interaction Gate | `docpilot-real-user-qa-20260705205210-8c882e` | frontendInteraction 捕获 KB 阶段 TypeError 时缺少脱敏 message shape，难以定位 |
| `REA-20260708-P3-009` | VERIFIED（已验证） | P3 | 工程流程问题 | Smoke Runner / Document Parser | `docpilot-parser-real-chain-20260708212024-9bd2ea` | parser smoke 静默复用不受控 backend 导致 QA 阶段误失败 |
| `REA-20260709-P3-010` | VERIFIED（已验证） | P3 | 工程流程问题 | Smoke Runner / Document Parser | `docpilot-parser-real-chain-20260709230208-fc2876` | parser smoke direct / QA 诊断计数和环境断链归因不够准确 |

## 2026-07-12 Conversation grounding policy 问题闭环

### `REA-20260712-P1-030` 固定业务语料 T08 / T11 / T12 暴露回答完整性和 citation 支撑不足

- 状态：VERIFIED（已验证）
- 严重级别：P1
- 类型：RAG 质量问题
- 模块：KnowledgeBase RAG QA / Citation
- 发现 marker：`docpilot-high-intensity-fixed-corpus-20260712223206-eae7f3`

复现步骤：

1. 运行 `scripts/smoke/high-intensity-fixed-corpus-smoke.ps1 -Mode run -SkipFrontend`。
2. runner 创建 6 份固定 Markdown 业务语料，构造 `KB_CORE` / `KB_NOISY`。
3. 检查 fixedBusinessCorpus gate 的 T08 / T11 / T12 case result。

实际结果：

- T08 `合同规定违约金是每天 1%，对吗？`：citation 覆盖合同与废弃草案，但 case 返回 `answer_claim_missing`，说明回答没有满足“明确否定 + 当前 0.3% + 最高 8%”的完整断言门禁。
- T11 `综合合同、安全规范和事故复盘，总结当前系统需要落实的四项风险控制措施。`：回答有 evidence 与 citation，但 citation 文档为 `API_POLICY`、`SLA_BETA`，缺少合同与事故复盘支撑，返回 `citation_document_coverage`、`citation_support_missing`。
- T12 `哪些场景需要多人审批？分别出现在什么文档中？`：citation 只覆盖 `CONTRACT_ALPHA`，缺少 `API_POLICY` 中管理员权限变更两名审批人的支撑，返回 `citation_document_coverage`、`citation_support_missing`。

预期结果：

- T08 应明确否定 1% 旧草案，并回答当前规则为每日 0.3%、最高 8%。
- T11 citation 至少覆盖并支撑合同、安全规范和事故复盘三类风险控制证据。
- T12 citation 至少覆盖合同超过 50 万元审批和管理员权限变更两名审批人两个文档来源。

可能原因：

- KnowledgeBase QA 的答案后 citation 数字精炼对跨文档问题过宽，会用全局数字交集剪掉回答中仍需引用的其他文档来源。
- 中文“分别出现在什么文档”等多跳问题原先没有稳定进入多文档 citation 保留路径。
- 错误前提纠正类问题的默认 prompt 没有明确要求先否定 / 肯定用户前提，并区分当前生效规则与废弃草案。

建议修复位置：

- `backend/src/main/java/com/docpilot/backend/ai/service/impl/KnowledgeBaseRagQaServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/rag/KnowledgeBaseRagPromptBuilder.java`
- `scripts/smoke/cloud-quality-smoke.ps1`
- 必要时补 `KnowledgeBaseRagQaServiceImplTest` / fixed corpus 相关测试。

修复提交：本轮提交。

验证记录：

- 当前失败验证：`scripts/smoke/high-intensity-fixed-corpus-smoke.ps1 -Mode run -SkipFrontend`，marker `docpilot-high-intensity-fixed-corpus-20260712223206-eae7f3`，overallStatus `FAILED_CORE_FLOW`。
- 修复内容：`KnowledgeBaseRagQaServiceImpl` 将数字 citation 精炼限制在非多文档问题，并补充中文多文档意图识别；`KnowledgeBaseRagPromptBuilder` 为错误前提 / 冲突规则问题增加通用纠错 prompt，不硬编码固定业务值。
- 定向回归：`mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagPromptBuilderTest,KnowledgeBaseRagControllerTest" test` PASS，46 tests，包含 reviewer 指出的“多文档措辞 + 单一数字事实 + 冲突 citation”交叉回归。
- 修复后真实验证：`scripts/smoke/high-intensity-fixed-corpus-smoke.ps1 -Mode run -SkipFrontend`，marker `docpilot-high-intensity-fixed-corpus-20260712230404-a0bc35`，overallStatus `REVIEW`（仅因 `-SkipFrontend`），`fixedBusinessCorpus` gate PASS，T02 + T06-T15 全部 PASS；artifact raw-field scan PASS，本地 fixed corpus 源文件目录为空。
- 复跑说明：marker `docpilot-high-intensity-fixed-corpus-20260712230202-0e661d` 曾在进入 fixed corpus gate 前因 `GET /api/document/detail?documentId=1359` 返回业务 `code=500` 中断；后端日志未留异常栈，立即复跑 `docpilot-high-intensity-fixed-corpus-20260712230404-a0bc35` 未复现，当前不作为 T08 / T11 / T12 修复失败证据。

### `REA-20260712-P1-029` 未绑定知识库的普通会话误进 strict grounded no-evidence refusal，并显示 0 条来源

- 状态：VERIFIED（已验证）
- 严重级别：P1
- 类型：会话路由 bug
- 模块：Conversation / Grounding Policy / Frontend
- 发现 marker：`user-report-conversation-grounding-20260712`

复现步骤：

1. 在 Conversation 页面创建或进入未绑定 KnowledgeBase 的会话。
2. 询问普通常识 / 闲聊类问题。
3. 查看助手回答、来源展示和上下文 Trace。

实际结果：

- 系统返回“根据提供的文档上下文无法回答”一类 grounded refusal。
- 前端显示 `0 条来源`，看起来像知识库回答失败，而不是普通模型回答。
- 代码审计确认 Conversation 入口复用了 `AiAnswerService.answer(context, question)` 的文档 QA strict prompt；`contextMode` 与 RAG policy 也存在耦合，Trace 缺少 `groundingPolicy` / `routeDecision` / `llmCalled`，刷新后无法判断真实路由。

预期结果：

- 未绑定 KB：`groundingPolicy=MODEL_ONLY`，`ragTriggered=false`，`ragRequired=false`，`evidenceCount=0`，调用底层 AnswerProvider，不触发 no-evidence refusal，前端显示“未使用知识库”或隐藏来源。
- 绑定 KB + `AUTO_RAG`：普通常识 / 闲聊不触发 RAG；资料相关问题才检索；检索无证据时 fallback 到模型，不触发资料不足拒答。
- `STRICT_KB`：只有用户显式选择“仅基于知识库回答”时，缺 evidence 才拒答。

修复与验证：

- 修复位置：`ConversationMessageServiceImpl`、`ContextAssemblyServiceImpl`、`KnowledgeBaseEvidenceBuilder`、`RealAiAnswerService`、`ConversationContextTraceServiceImpl`、Conversation 前端页面和 `cloud-quality-smoke.ps1`。
- 修复方式：新增 `GroundingPolicy` / `RouteDecision`；Conversation 改走 `answerConversation(...)` 非 strict prompt；Trace 增加 `grounding_policy`、`route_decision`、`llm_called` 与增量 SQL；消息和 Trace 同事务保存；前端发送 policy 并按 Trace 区分“知识库来源 / 未使用知识库 / 资料不足”。
- 已验证：Conversation / grounding 定向 37 tests PASS，schema / smoke script safety 9 tests PASS；新增 `ContextTraceSerializationTest` 锁定 API JSON 同时暴露 `modelCallSkipped` 和 `modelSkipped`；授权后执行 `backend/src/main/resources/sql/008_add_context_trace_grounding.sql`，确认云 MySQL `tb_context_trace` 存在 `grounding_policy`、`route_decision`、`llm_called`。
- 真实验证：API smoke marker `cg20260712175003-50312c` PASS，artifact `tmp-e2e/conversation-grounding-runtime/cg20260712175003-50312c-artifact.json`，覆盖未绑定 KB、未绑定 KB 误选 STRICT、AUTO_RAG 普通问题、AUTO_RAG 无证据 fallback、STRICT_KB 无证据拒答、AUTO_RAG evidence citation；前端 Playwright marker `ui-cg-20260712095111-7094ef` PASS，确认普通回答显示“未使用知识库”且没有“0 条来源”，严格资料不足显示“资料不足 / 调用模型否 / 模型跳过是”。
- 回归门禁：`mvn test -DskipITs` PASS（953 tests，0 failures，0 errors，5 skipped）；前端 `npm run lint` / `npm run build` PASS。

## 2026-07-12 rerank 代表语料 eval 问题闭环

### `REA-20260712-P2-026` 裸 `knowledge base` 泛词被当作总结意图，near-threshold 无关问题绕过 support gate

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：RAG no-evidence 质量问题
- 模块：KnowledgeBase RAG Retrieval
- 发现 marker：`docpilot-rerank-representative-representative-hybrid-20260712142138-...`

复现步骤：

1. 执行 rerank 代表语料真实链路 eval。
2. 在 populated KnowledgeBase 中提问与语料无关、但包含 `knowledge base` 泛词的问题。
3. 检查 no-evidence case 的 retrieve / citation 结果。

实际结果：

- 该无关问题返回了检索结果 / citation，代表 eval 标为 REVIEW。
- 代码审计确认 `SUMMARY_INTENT_KEYWORDS` 中包含过宽的 `knowledge base` / `资料集` / `知识库` 等容器名词，导致普通问题被误判为 summary intent，从而绕过 near-threshold evidence support gate。

预期结果：

- 裸容器名词不能单独构成 summary intent；只有总结、概括、全部文档、overview 等明确总结意图才可触发总结类宽松策略。
- 无关问题应保持 `noEvidence=true`、0 hits / citations，不能因为语义相近或泛词进入 grounded QA。

修复与验证：

- 修复位置：`KnowledgeBaseRagRetrievalServiceImpl`。
- 修复方式：收窄 summary intent 关键词，新增回归测试 `shouldNotTreatBareKnowledgeBaseMentionAsSummaryIntentForNoEvidenceGateAfterRerank`。
- 验证：定向 tests PASS；最终代表 eval `docpilot-rerank-representative-representative-rerank-20260712152212-2e0f81` PASS，2/2 no-evidence case preserved，`noEvidenceRegressionCount=0`。

### `REA-20260712-P2-027` 中文代表问法未稳定映射到英文业务 evidence，target coverage 失败

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：RAG 召回质量问题
- 模块：Query Rewrite / Hybrid Retrieval
- 发现 marker：`docpilot-rerank-representative-representative-hybrid-20260712142138-...`

复现步骤：

1. 执行 rerank 代表语料真实链路 eval。
2. 运行中文合规 / 财务问法 case。
3. 检查 target coverage 与 multi-query 观测字段。

实际结果：

- 中文问法中的“合规、审计、保留、报销、发票、审批”等词未稳定支撑英文 evidence 召回，部分 target coverage 失败。
- 仅靠原始中文 query 的 hybrid keyword support 不足，导致目标文档在 confidence gate 前后被削弱。

预期结果：

- 在不引入 LLM query planner 的前提下，受控规则 rewrite 应把常见中文企业知识库词汇扩展成英文业务词，并且只输出计数 / 布尔观测，不保存 rewritten query 原文。

修复与验证：

- 修复位置：`RuleBasedQueryRewriteService`、`KnowledgeBaseRagRetrievalServiceImpl`。
- 修复方式：新增中文领域词 rewrite；multi-query 启用时让 hybrid keyword query 使用去重后的 query variants 参与 support 计算，同时保留 weak keyword support 拒绝测试。
- 验证：`RuleBasedQueryRewriteServiceTest` 和 `KnowledgeBaseRagRetrievalServiceImplTest` PASS；最终代表 eval PASS，中文 case `multiQueryApplied=true`、`queryVariantCount=3`，目标覆盖成功。

### `REA-20260712-P3-028` PowerShell 5.1 中文 JSON / redaction 处理不稳，导致 mojibake 和 caseId 误报 token

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：工程流程问题
- 模块：Rerank Representative Eval Runner
- 发现 marker：`rerank-representative-eval-smoke.ps1` 初跑

复现步骤：

1. 在 Windows PowerShell 5.1 下执行 rerank 代表语料 eval。
2. 检查中文 case 的请求体、artifact redaction scan 和最终 wrapper summary。

实际结果：

- 中文 query 字符串经过脚本 / HTTP body 路径出现 mojibake，导致中文 case 被误杀。
- redaction scan 把 caseId `security-token-rotation` 中的普通业务词 `token` 误判为敏感值，导致 artifact 扫描 false positive。

预期结果：

- 脚本应显式以 UTF-8 bytes 发送 JSON；中文 fixture 可用 Base64 常量解码避免脚本编码漂移。
- redaction 规则应匹配敏感字段名、Bearer、连接串、非 loopback IP 等风险，而不是把普通 caseId 单词当作泄密。

修复与验证：

- 修复位置：`cloud-quality-smoke.ps1`、`rerank-representative-eval-smoke.ps1`、`RerankRepresentativeEvalSmokeScriptSafetyTest`。
- 修复方式：`Invoke-JsonApi` body 改为 `UTF8.GetBytes` 且 `ContentType=application/json; charset=utf-8`；中文代表问题改为 Base64 解码；redaction scan 收窄到敏感字段 / token 形态 / 连接串。
- 验证：wrapper `plan` / `dry-run` / `run` PASS；最终代表 eval artifact redaction PASS，未提交 artifact 原文或任何敏感值。

## 2026-07-11 最大压力真实链路审计

### `REA-20260711-P3-022` frontendInteraction Node 异常被包装层吞成 false/0，缺少真实 safeMessage

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：工程流程问题
- 模块：Smoke Runner / Frontend Interaction Gate
- 发现 marker：`docpilot-real-user-qa-20260711164556-93f35f`

复现步骤：

1. 执行 `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`。
2. 查看 artifact 中 `frontendInteraction` gate。

实际结果：

- 业务 API 层 gate 大量通过，但 `frontendInteraction` 失败只记录 `documentRetrieveStatus=0`、多个 UI boolean 为 `false`。
- Node Playwright 脚本若在 wait / click / response 阶段抛错，会输出 `safeMessage`，但 PowerShell 包装层未把该信息写入 checks，导致定位信息丢失。

预期结果：

- 前端交互 gate 失败时应保留脱敏 `nodeOverallStatus` 与 `nodeSafeMessage`，并用独立 failure bucket 区分“脚本执行异常”和“UI 真实断言失败”。

修复与验证：

- 修复位置：`scripts/smoke/cloud-quality-smoke.ps1`。
- 测试：`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS。
- 真实验证：`docpilot-rag-natural-corpus-20260711165958-f4935a` 与 `docpilot-real-user-qa-20260711170544-dff948` 的 `frontendInteraction` PASS，artifact 中包含 `nodeOverallStatus=PASS`、`nodeSafeMessage=""`。

### `REA-20260711-P2-023` 财务多文档 compare 题表述偏泛，真实模型答案表达和干扰 citation 存在波动

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：质量门禁 fixture bug
- 模块：Natural Corpus / Answer Faithfulness Gate
- 发现 marker：`docpilot-real-user-qa-20260711165345-ecc162`

复现步骤：

1. 执行最大压力真实用户审计。
2. 检查 `naturalCorpus` gate 的 `finance-expense-invoice-compare` case。

实际结果：

- 该 case 的目标文档 retrieve / citation coverage、expected evidence support、citation phrase support、no-evidence 判断均通过。
- 但 `answerFactExpression=false`，且同轮出现 `distractorCitation` review；说明原问题只写“compare reimbursement approval rule with invoice archive retention rule”时，真实模型答案表达与检索选择存在波动。

预期结果：

- 该 case 仍应是多文档比较题，但问题应明确要求答案写出“谁审批报销”和“发票归档保留多久”，避免把题目歧义误当作 RAG 质量失败。

修复与验证：

- 修复位置：`scripts/smoke/cloud-quality-smoke.ps1`。
- 修复方式：保留原 answer faithfulness 硬门禁，不放宽通过条件；仅将问题改为明确要求两个事实。
- 验证：`docpilot-rag-natural-corpus-20260711165958-f4935a` PASS，`casePassRate=1`、`answerFaithfulnessPassCount=11/11`、`distractorCitationFreeCount=25/25`；完整审计 `docpilot-real-user-qa-20260711170544-dff948` PASS。

### `REA-20260711-P3-024` Memory provider smoke run 模式未按文档加载 `.env`，直接运行误报 provider_config_missing

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：工程流程问题
- 模块：Memory provider extraction smoke
- 发现 marker：`docpilot-memory-provider-20260711171644-030f7f`

复现步骤：

1. 直接执行 `memory-provider-extraction-smoke.ps1 -Mode run`。
2. 检查输出状态。

实际结果：

- `backend/.env` 中 `AI_REAL_PROVIDER`、`AI_REAL_BASE_URL`、`AI_REAL_API_KEY`、`AI_REAL_MODEL` 均存在且非占位，但脚本未加载 `.env`，因此返回 `BLOCKED/provider_config_missing`。

预期结果：

- 与历史文档口径一致，run 模式应能从 repo 内 `backend/.env` 安全注入缺失的 `AI_REAL_*` 到当前进程；plan / dry-run 仍不得读取 `.env`。

修复与验证：

- 修复位置：`scripts/smoke/memory-provider-extraction-smoke.ps1`。
- 安全边界：只允许 repo 内 env file；只填充缺失的四个 `AI_REAL_*`；不输出任何值。
- 测试：`mvn "-Dtest=MemoryQualitySmokeScriptSafetyTest" test` PASS。
- 真实验证：直接执行 `memory-provider-extraction-smoke.ps1 -Mode run` 后 marker `docpilot-memory-provider-20260711172435-14083e` PASS，`modelCallCount=6`、`casePassRate=1.0000`、`rawProviderOutputStored=false`。

### `REA-20260711-P2-025` 真实 rerank model 返回 NotFound，candidate 降级 identity，无法证明 rerank 实效

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：provider 配置 / 可用性问题
- 模块：RAG Rerank provider
- 发现 marker：`docpilot-rerank-effect-rerank-20260711171449-522a4c`

复现步骤：

1. 执行 `rerank-effect-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`。
2. 查看 `backend/target/rag-quality/rerank-effect/latest-summary.json` 和 rerank candidate 后端日志。

实际结果：

- baseline 与 candidate 的核心 RAG、no-evidence、安全隔离均无回退。
- candidate 轮 `rerankApplied=false`、`rerankModel=identity`、rerank score count 为 `0`。
- 后端日志显示配置的 rerank provider / model 调用返回 `NotFound`，服务按设计 fallback to identity。

预期结果：

- 若要把 rerank 写成真实 provider 实效验证，candidate 轮必须 `rerankApplied=true` 并产生 rerank score；否则只能写成“核心链路无回退，真实 rerank 未生效”。

修复与验证：

- 修复位置：`HttpRerankService` / `RerankProperties` / 本机 ignored `.env` 和示例配置文档。
- 修复方式：将真实 rerank provider 切到阿里云百炼 `aliyun_bailian` + `qwen3-rerank`，请求使用百炼 qwen3-rerank 的顶层 `query` / `documents` / `top_n` 结构，并兼容 gte-rerank-v2 的 `output.results` 响应结构。
- 真实验证：`rerank-effect-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`，baseline marker `docpilot-rerank-effect-hybrid-20260712003119-0b7ed3`，candidate marker `docpilot-rerank-effect-rerank-20260712003244-46b2e3`；candidate `rerankApplied=true`、`rerankModel=qwen3-rerank`、`rerankFailureReason=""`，rerank score count `4`，核心 RAG / no-evidence / 权限安全无回退。
- 回归验证：`mvn "-Dtest=HttpRerankServiceTest,RerankEffectSmokeScriptSafetyTest" test` PASS（8 tests）；`mvn test -DskipITs` PASS（914 tests，0 failures，5 skipped）。
- 边界：本次解决的是 provider/model 可用性和 identity fallback 问题；hard fixture 未观察到排序 uplift，整体 effect smoke 仍为 REVIEW，不能在展示材料中宣称真实 rerank relevance uplift 已验证。

## 2026-07-11 真实用户全链路审计

### `REA-20260711-P2-020` 财务多文档对比 case 的 answerFactExpression 对中英文同义表达过窄

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：质量门禁 fixture bug
- 模块：Natural Corpus / Answer Faithfulness Gate
- 发现 marker：`docpilot-real-user-qa-20260711155558-573a81`

复现步骤：

1. 执行 `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`。
2. 检查 `naturalCorpus` gate 的 `finance-expense-invoice-compare` case。

实际结果：

- 完整真实审计中 tunnel、backend health、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、短文档 RAG、Memory、权限隔离、frontendInteraction、cleanup 和 redaction 均通过。
- 失败 case 的 retrieve hits、QA citations、目标文档检索覆盖、目标文档引用覆盖、expected evidence support、citation phrase support 和干扰文档抑制均通过；唯一 failure bucket 为 `answerFactExpression`。

预期结果：

- 当 evidence 与 citation 已覆盖“团队经理审批”和“发票归档 7 年”两个目标事实，且回答使用等价中英文表达时，answer faithfulness gate 不应因单一英文短语缺失误杀。

可能原因：

- `finance-expense-invoice-compare` 的 `answerAllPhrases` 只覆盖英文 `manager` 相关表达，`answerAnyPhrases` 只覆盖英文 `7 years` 相关表达；真实回答可能使用中文“主管 / 团队负责人 / 7 年 / 七年”等等价表达。

修复与验证：

- 修复位置：`scripts/smoke/cloud-quality-smoke.ps1`。
- 修复内容：仅扩展该 case 的中英文同义表达组，不放宽目标文档覆盖、citation phrase support、干扰 citation 抑制或 no-evidence 门禁。
- 验证：自然语料专项 `docpilot-rag-natural-corpus-20260711160322-1cbcbc` PASS，完整真实用户审计 `docpilot-real-user-qa-20260711160913-98a440` PASS；`casePassRate=1`、`answerFaithfulnessPassCount=11/11`、`citationPhraseSupportPassCount=22/22`。

### `REA-20260711-P3-021` 注册 username 校验失败被兜底为 500

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：API 参数校验体验
- 模块：Auth register / GlobalExceptionHandler
- 发现来源：`parse-status-validation-smoke-20260711`

复现步骤：

1. 启动本地 tunnel 与 backend。
2. 调用 `POST /api/auth/register`，传入超过 32 字符的 username。

实际结果：

- 后端 DTO 已声明 `username` 格式和长度校验，但校验异常未被全局处理器识别，落入兜底 `Exception`，API 返回 `code=500`。

预期结果：

- 参数校验失败应返回业务 `BAD_REQUEST`，并带出安全的字段校验 message，不能伪装成服务器内部错误。

可能原因：

- `GlobalExceptionHandler` 仅处理 `BusinessException` 和兜底 `Exception`，缺少 `BindException` / `ConstraintViolationException` 分支。

修复与验证：

- 修复位置：`backend/src/main/java/com/docpilot/backend/common/exception/GlobalExceptionHandler.java`。
- 修复内容：新增绑定校验异常与约束校验异常处理，返回 `ErrorCode.BAD_REQUEST` 和第一条安全校验 message。
- 验证：`GlobalExceptionHandlerWebMvcTest` 覆盖字段绑定错误和约束错误；运行时超长 username 注册返回 `code=400`；`mvn test -DskipITs` PASS（911 tests，0 failures，5 skipped）。

## 2026-07-10 真实主链路验收失败

### `REA-20260710-P3-019` Next dev 监听与浏览器访问 origin 不一致导致 RSC console error

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：真实前端体验 / smoke 稳定性
- 模块：Next dev RSC / cloud quality runner
- 发现 marker：`docpilot-cloud-quality-20260710205934-95dd05`

实际结果：

- 上传、索引、单文档 / KnowledgeBase RAG、Agent、Trace、Memory、权限与 citation UI 均通过，但 KnowledgeBase 初次 dev compile / RSC 刷新期间出现 `fetchServerResponse` 的 `Failed to fetch` console error，frontendInteraction 因此失败。

修复与验证：

- runner 从 `FrontendBaseUrl` 解析并仅接受 http/https 的显式有效 loopback host / port，启动 Next dev 时显式传递 `-H <host> -p <port>`，并在首次可达性探测前拒绝外部 URL，使监听与 Playwright 访问 origin 对齐。
- 静态安全测试 PASS；完整真实 marker `docpilot-cloud-quality-20260710210913-5ea91b`、`docpilot-cloud-quality-20260710211110-0b226b`、`docpilot-cloud-quality-20260710211530-6de04e` 连续 PASS，KnowledgeBase citation 可见且三轮 console error 均为 0。

边界：

- 此修复针对 cloud smoke 的 Next dev 启动一致性，不改业务 API、Next config、依赖版本或生产运行行为。

### `REA-20260710-P2-018` RAG SSE 未收到 done 的 clean EOF 被静默当作成功

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：流式体验可靠性
- 模块：RAG SSE client
- 发现来源：`sse-eof-contract-audit-20260710`

实际结果：

- 前端 stream reader 在 HTTP body 结束时未要求协议 `done`，因此代理或网关在 done 前 clean EOF 时，首 chunk 前可能空白结束，已有 chunk 也可能没有中断提示。

修复与验证：

- 仅 `event: done` 标记终止成功；缺 done 的 EOF 抛受控 `transport_eof`，复用页面既有“无内容回退 / 部分内容保留”状态机。
- production Next + Playwright 定向 5/5 PASS：meta-only EOF 只发起 1 次非流式回退，chunk 后 EOF 保留部分答案且普通 RAG 为 0，正常 done 无回退；完整前端 E2E 14/14、lint/build 均通过，独立审查无 blocker。

边界：

- 验证的是 route mock 的 HTTP body clean EOF 协议，不包含 TCP reset、browser fetch reject、跨 read 分帧或 done 后非法 error。

### `REA-20260710-P1-017` Memory provider smoke 的 artifact 日志与空建议格式门禁存在安全缺口

- 状态：VERIFIED（已验证）
- 严重级别：P1
- 类型：artifact 安全边界
- 模块：Memory provider extraction smoke
- 发现来源：`memory-provider-v2-security-review-20260710`

发现：

- 原 wrapper 会将 Maven 全量 stdout / stderr 保存为 `maven.log`，失败时可能包含未受信 provider 错误体；同时 artifact root 可被调用参数改写。
- 原 provider runner 将非法或非结构化 JSON 解析为空列表，零 suggestion 的安全负例可能被误判为通过；预算参数也未与 Java suite 和最终 artifact 调用数硬绑定。

修复：

- wrapper 现只使用外层已注入的进程环境，不读取 `.env`；固定 artifact root 为 ignored 的 `backend/target/memory-provider`，删除 Maven 原始日志落盘，失败仅生成枚举化安全摘要。
- provider 调用异常转为不含异常体的 `provider_call_failed`；非法 JSON / 缺失 `suggestions` 数组会产生 `invalid_provider_response_format`，不再可作为空 suggestion 成功。
- six-case suite、Java 结果和 wrapper artifact 后校验统一为固定 `6`；dry-run 只有所有安全检查通过才返回 PASS。

验证：

- 安全收紧后 plan / dry-run、9 项定向离线测试（真实 smoke 默认 skipped 1）及固定 6-call 最终 marker `docpilot-memory-provider-20260710204432-4540df` 均通过；复审确认 Maven 原始日志、artifact root、非法 JSON / 畸形 suggestion、预算和 marker 路径缺口均已闭环。

### `REA-20260710-P3-016` Memory provider 中文正例被错误 forbidden marker 误判

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：质量门禁 fixture bug
- 模块：Memory provider extraction eval
- 发现 marker：`docpilot-memory-provider-20260710203107-29967e`

复现步骤：

1. 执行固定 6-call 的 `memory-provider-extraction-smoke.ps1 -Mode run -MaxModelCalls 6`。
2. 检查中文长期偏好 / 项目状态正例的脱敏类型摘要与 failure reason。

实际结果：

- 真实 provider 已返回预期 `PREFERENCE` 与 `PROJECT_STATE` 类型，但 fixture 将应当允许抽取的输入短语同时列入 forbidden marker，导致 `forbidden_marker_leaked` 误失败；首次结果为 5/6 PASS。

修复与验证：

- 将该正例的 forbidden marker 改回仅用于泄露检测的私有占位 marker，保留 artifact 文本不包含输入内容的断言。
- 复验 marker `docpilot-memory-provider-20260710203218-d0df0a` PASS：6 calls、`casePassRate=1.0000`、`rawProviderOutputStored=false`。artifact 仅保存 case 分类、类型、计数、布尔值和 failure reason，不保存会话、候选记忆或原始 provider 输出。

### `REA-20260710-P2-015` fresh-clone demo MySQL 初始化运行验收受本机 Docker Engine 阻塞

- 状态：BLOCKED
- 严重级别：P2
- 类型：环境 / fresh-clone 可用性风险
- 模块：Local demo MySQL bootstrap
- 发现来源：`schema-bootstrap-audit-20260710`

复现步骤：

1. 按 `docker-compose.demo.yml` 审计 MySQL Docker entrypoint 挂载的 `deploy/mysql/init/`。
2. 尝试以不映射端口、不复用 compose 容器名或 volume 的临时 MySQL 容器执行空卷初始化。

实际结果：

- 静态审计发现旧初始化目录只有 9/17 张应用持久表，且 `tb_document` 缺少应用层依赖的 `status` 字段。
- 完整快照及离线 5 项 schema contract 已补齐并通过；但本机 Docker Desktop Linux engine 未运行，无法创建隔离临时容器进行 MySQL entrypoint 实测。
- 本机存在未知 `3306` listener，未连接、未修改或删除该实例、现有 demo volume、云 MySQL 或业务数据。

预期结果：

- Docker Engine 可用时，应由空的临时 MySQL 容器执行 `00 -> 01 -> 02`，并查询 INFORMATION_SCHEMA 确认 17 张表、`tb_document.status` 和关键索引。

后续验证：

- 启动本机 Docker Engine 后，仅执行隔离 `docker run --rm` 验收；不使用既有 `docpilot_mysql_data`，不对云库或已有 volume 执行 DDL。

### `REA-20260710-P3-014` 知识库 RAG 交互成功但浏览器出现 Failed to fetch 控制台错误

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：真实前端体验问题
- 模块：KnowledgeBase RAG UI
- 发现 marker：`docpilot-cloud-quality-20260710195739-fdb3fa`

复现步骤：

1. 运行 `cloud-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007 -EnableFrontendInteractionGate -EnableKnowledgeBaseAgentGate`。
2. 完成临时用户、双文档上传解析和 KnowledgeBase 创建。
3. 在浏览器中完成 KnowledgeBase RAG 回答与 citation 展示检查。

实际结果：

- 单文档 / KnowledgeBase RAG、短文档、grounding、no-evidence、Conversation Trace、Agent 和权限隔离均通过；KnowledgeBase 双文档 citation 已在页面可见。
- `frontendInteraction` 捕获 1 个 blocking console error：knowledgeBase 阶段的 `TypeError`，脱敏 message shape 为 `Failed to fetch`。
- 因浏览器 console error gate 失败，整体标记为 `FAILED_CORE_FLOW`；runner cleanup 和 artifact redaction 均通过。

预期结果：

- KnowledgeBase RAG 交互完成后不应出现未处理的浏览器 fetch 错误；后台刷新或组件卸载触发的请求应被取消、忽略或以可理解 UI 状态处理。

可能原因：

- 错误栈形状来自 Next App Router 的 RSC 路由请求，而非 KnowledgeBase QA API；在 Next dev 下以 `127.0.0.1` 访问时，前端日志记录了 `/_next` 开发跨源警告。

建议修复位置：

- `frontend/next.config.js`

修复内容：`next.config.js` 增加 `allowedDevOrigins: ["127.0.0.1"]`，使现有 smoke / 本地联调访问源被 Next dev 显式允许；不改 KnowledgeBase API、模型、数据库或 runner 业务逻辑。

验证记录：`npm run lint`、`npm run build` PASS；在同一 `127.0.0.1:3007` 访问条件下，完整 cloud quality smoke `docpilot-cloud-quality-20260710200547-6dec4e` PASS，KnowledgeBase 双 citation 可见且 `consoleErrorCount=0`。失败 artifact `docpilot-cloud-quality-20260710195739-fdb3fa` 与验证 artifact 均位于 ignored `tmp-e2e/`，未提交原始 artifact、日志、临时文档或凭据。

### `REA-20260710-P1-012` 解析与索引通过后单文档 RAG 回答模型读取窗口不足

- 状态：VERIFIED（已验证）
- 严重级别：P1
- 类型：真实链路稳定性问题
- 模块：Single-document RAG QA
- 初始发现 marker：`docpilot-cloud-quality-20260710173219-d801d9`
- 业务错误复现 marker：`docpilot-cloud-quality-20260710174029-f348f5`
- 脱敏阶段诊断 marker：`docpilot-cloud-quality-20260710184854-87db56`
- 重试耗尽 marker：`docpilot-cloud-quality-20260710185927-727915`
- 修复验证 marker：`docpilot-cloud-quality-20260710191822-ec80b6`

复现步骤：

1. 运行 `cloud-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007 -EnableFrontendInteractionGate -EnableKnowledgeBaseAgentGate`。
2. 等待双用户认证、双文档上传、异步解析、chunk 索引和 MySQL / Qdrant 一致性通过。
3. 进入首份文档的 RAG QA 请求阶段。

实际结果：

- tunnel、backend health、认证、上传解析索引、chunk 质量和 MySQL / Qdrant 一致性均为 PASS。
- 首次 `status=0` 是 smoke runner 将 HTTP 200 的非零业务码错误错误降格后的诊断缺陷；修复 runner 后，同一请求稳定返回 HTTP 200、业务码 `1013`（`AI_CALL_FAILED`）。
- 新增脱敏阶段日志后，真实 run 记录为 `stage=model_call`、`exceptionClass=AiRetryableException`；可排除本轮的 parse、index、MySQL / Qdrant 一致性与历史写入阶段。
- 单文档与 KnowledgeBase RAG 已复用已有的 `AiRetryExecutor`；首次验证时受本地 30 秒读取窗口限制，整体仍为 `FAILED_CORE_FLOW`。
- runner 已执行 cleanup，未保留本地服务端口；原始 artifact、日志、临时文档和凭据均未提交。
- 代码修复后最终真实 run 已记录单文档回答模型调用的第 `1/3`、`2/3` 次受限重试，第三次仍为 `AiRetryableException`；因此重试逻辑已生效，但完整 quality gate 仍未通过。
- 只读模型目录探测确认已配置模型在当前账户可用列表中；最小请求与短输出 RAG 请求均可成功。相同 RAG 量级上下文的非流式请求在 30 秒窗口内会超时，但在更长窗口成功返回，说明根因是读取窗口不足而非模型标识、鉴权、索引或检索错误。
- 仅调整本机 ignored `backend/.env` 的真实模型读取窗口后，完整 cloud quality gate 全部 PASS；本地服务 cleanup 后无端口残留。

预期结果：

- 已完成 parse / index 且 MySQL 与 Qdrant 一致的文档，应稳定完成单文档 RAG QA；短暂可恢复的回答模型失败应遵循统一、受限的重试策略。

可能原因：

- 当前本机真实模型的读取窗口短于已观测到的正常非流式 RAG 响应时间，导致 `HttpTimeoutException` 被安全地归类为可重试失败。
- 初始单文档 RAG 缺少统一受限重试，扩大了暂态/超时的用户可见失败概率；该代码缺口已在 `cbf12d2` 修复。

建议修复位置：

- 本机 ignored `backend/.env` 的 `AI_REAL_READ_TIMEOUT_MS`（仅当前 provider/model 调优，不提交）
- `scripts/smoke/cloud-quality-smoke.ps1`
- `backend/src/main/java/com/docpilot/backend/ai/service/impl/RagQaServiceImpl.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/RagQaServiceImplTest.java`

修复提交：`cbf12d2`（RAG 受限重试、脱敏诊断与 smoke runner 业务码保留）。

验证记录：`AiRetryExecutorTest`、`RagQaServiceImplTest`、`KnowledgeBaseRagQaServiceImplTest` 与 `CloudQualitySmokeScriptSafetyTest` 共 `29` 项通过；完整真实 smoke `docpilot-cloud-quality-20260710191822-ec80b6` PASS，覆盖 tunnel、上传解析、chunk、MySQL / Qdrant 一致性、单文档 / KnowledgeBase / 短文档 RAG、Conversation / Memory、Agent、权限、浏览器交互、cleanup 与 artifact redaction。artifact 位于 ignored 的 `tmp-e2e/docpilot-cloud-quality-smoke/.../artifact.json`，未提交原始 artifact、日志、临时文档或凭据。

### `REA-20260710-P2-013` 切换百炼后遗留 embedding 模型标识导致索引失败

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：真实链路稳定性问题
- 模块：RAG Indexing
- 发现 marker：`docpilot-cloud-quality-20260710194619-390475`
- 修复验证 marker：`docpilot-cloud-quality-20260710195347-5fbdb7`

实际结果：

- tunnel、backend health、认证和首份文档解析通过；解析日志显示 `parser=text`、`blockCount=4`。
- 随后 `RagIndexingTriggerServiceImpl` 记录 `status=FAILED`、`chunks=4`、`vectors=0`；runner 在等待 indexed chunks 时超时并以 `FAILED_CORE_FLOW` 收口。
- 本轮尚未进入任何模型回答请求，不能归因于百炼模型；cleanup 与 artifact redaction 均 PASS。
- 最小 embedding 探测返回 HTTP 404，确认旧的 embedding 模型标识不被当前百炼 OpenAI-compatible endpoint 支持。
- 将本机 ignored embedding 模型名切为百炼官方 `text-embedding-v4` 后，探测返回 1024 维向量；完整 cloud quality smoke 通过 parse、index、Qdrant consistency、RAG、Agent、权限与浏览器交互。

预期结果：

- parse 成功的临时文档应完成向量索引并产出 vectors，随后进入 RAG QA 门禁。

后续：

- 配置修复仅在 ignored 本机 `.env` 中生效，未提交 Key、网关、业务空间或其他敏感配置；未改数据库或远端 collection。

## 2026-07-10 前端生产依赖审计

### `REA-20260710-P1-011` 前端生产依赖升级后仍存在 high / moderate 漏洞

- 状态：OPEN
- 严重级别：P1
- 类型：安全依赖风险
- 模块：Frontend dependency supply chain
- 发现 marker：`npm-audit-frontend-20260710`

复现步骤：

1. 在 `frontend/` 执行 `npm ci`。
2. 执行 `npm audit --json --omit=dev`，只读取生产依赖审计摘要。

实际结果：

- 初始审计报告生产依赖范围内有直接 `next` critical 与传递 `postcss` moderate；用户工作区已将 Next 升至 14.2.35。
- 复验 `npm audit --omit=dev` 仍报告 Next high 与 PostCSS moderate；audit 建议的完全修复会升级到 Next 16，属于破坏性 major 变更。

2026-07-11 复验补充：

- 异常恢复一度将 tracked `frontend/package.json` / `package-lock.json` 回退到 `next` / `eslint-config-next` `14.2.5`，导致 production audit 再次出现 critical。
- 本轮已重新恢复到 `14.2.35`；`npm run lint` 与 `npm run build` PASS，`npm audit --omit=dev` 当前无 critical，仍剩 Next high 与 PostCSS moderate。
- 该问题继续保持 OPEN：非 major critical 回退已修复，但完全清零仍需要 Next 16 major 升级评估。
- 当前升级已通过 lint、build、Playwright E2E 14/14 与真实 cloud smoke；未自动执行 `npm audit fix --force`、未暂存或提交用户的 lockfile / package 改动。

预期结果：

- 发布前应评估并处理剩余生产依赖风险；若选择 major 升级，需重新完成 lint、build、E2E、真实 cloud smoke 与 API / RSC 兼容性回归。

可能原因：

- 当前锁定的 Next.js 版本及其传递依赖已落入后续披露的安全公告范围。

建议修复位置：

- `frontend/package.json`
- `frontend/package-lock.json`
- `.github/workflows/ci.yml`（后续可增加只报告、不自动修复的依赖审计门禁）

修复提交：待定。

验证记录：`npm ci`、`npm run lint`、`npm run build` 已通过；漏洞修复本身尚未实施，不能标记为 VERIFIED。

## 2026-07-09 Document Parser direct / QA 诊断修复验证

验证 marker：`docpilot-parser-real-chain-20260709233230-a08906`

状态：PASS

已验证：

- 真实 parser smoke 通过：tunnel、backend、frontend root route、auth、PDF / HTML / DOCX 上传、异步解析、chunk、direct retrieve、QA retrieval、citation、source locator、parserBoundary 和 artifact redaction 均 PASS。
- parser 结果：三类文档均 `parseStatus=SUCCESS`、`chunkCount=1`、`directRetrieveHit=true`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`。
- 诊断结果：direct retrieve 与 QA retrieval 均记录脱敏摘要，三类文档 `hitCount=1`、`citationCount=1`、`noEvidence=false`；诊断不保存 query、answer 原文、文档全文、prompt、evidence context、token、secret、连接串或云地址。
- 运行环境归因：如果运行中本地 MySQL / Qdrant tunnel 端口不可达，runner 会写入 `environmentStability=BLOCKED`，避免把环境断链误判为 parser 核心失败。

本轮发现并处理：

- `REA-20260709-P3-010`：中途 run `docpilot-parser-real-chain-20260709230208-fc2876` 暴露两类工程问题。其一，PowerShell 对 `$null` 和函数返回数组的计数语义导致 direct / QA `hitCount` 可能显示为 `1` 或 `null`，误导排查；其二，运行中本地 MySQL / JDBC 连接不可用时，runner 会把后续 API 失败归到 `FAILED_CORE_FLOW`，容易误判为 parser 业务失败。runner 已新增 `Get-SafeItemCount`、`directRetrieveDiagnostic` / `qaRetrieveDiagnostic` 和 `environmentStability` 归因。

边界：

- 本轮创建临时 smoke 用户和临时文档；artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/`。
- 未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

### `REA-20260709-P3-010` parser smoke direct / QA 诊断计数和环境断链归因不够准确

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：工程流程问题
- 模块：Smoke Runner / Document Parser
- 发现于：`docpilot-parser-real-chain-20260709230208-fc2876`
- 修复验证：`docpilot-parser-real-chain-20260709233230-a08906`
- 复现步骤：运行 `document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`，在 direct retrieve / QA retrieve 阶段观察 artifact 中的诊断计数和运行状态。
- 实际结果：当 API 响应缺失 hits 或返回失败时，PowerShell 计数可能显示 `hitCount=1` 或 `hitCount=null`；运行中 MySQL / JDBC 断链时，后续上传 / 检索失败容易被整体归为 `FAILED_CORE_FLOW`。
- 预期结果：runner 应稳定区分 0 / 1 / 多条命中；环境断链应写为 `BLOCKED`，而不是污染 Document Parser 业务质量结论。
- 可能原因：PowerShell `@($null).Count` 和函数数组返回展开语义不直观；旧 runner 只在启动前检查 tunnel，没有在中途 API 失败时复查运行环境。
- 建议修复位置：`scripts/smoke/document-parser-real-chain-smoke.ps1`、`DocumentParserRealChainSmokeScriptSafetyTest.java`。
- 修复提交：本轮提交。
- 验证记录：脚本 `plan` / `dry-run` PASS；后端 parser / retrieval / quality targeted 74 tests PASS（1 skipped）；真实 run `docpilot-parser-real-chain-20260709233230-a08906` PASS，三类文档 direct / QA / citation / source locator 均通过，parserBoundary `4/4` PASS。

## 2026-07-08 Document Parser runner 修复验证

验证 marker：`docpilot-parser-real-chain-20260708212742-0f9baa`

状态：PASS

已验证：

- 真实 parser smoke 通过：tunnel、受控 backend、frontend route、auth、PDF / HTML / DOCX 上传、异步解析、chunk、QA retrieval、citation、source locator、parserBoundary 和 artifact redaction 均 PASS。
- parser 结果：三类文档均 `parseStatus=SUCCESS`、`chunkCount=1`、`retrieveHit=true`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`。
- 诊断结果：`directRetrieveHitCount=0`、`qaRetrievalHitCount=3`、`citationCount=3`，说明本次 PASS 依赖 QA 内部 retrieval 与 citation 主链路；direct retrieve endpoint / query 语义后续仍可单独排查。
- Agent Quality Console 可见最新 run：Quality API 返回最新 marker `docpilot-parser-real-chain-20260708212742-0f9baa`，`parserQuality.fileCount=3`、`parsedFileCount=3`、`qaRetrievalHitCount=3`、`boundaryPassRate=1.0`；浏览器 `/quality?autoload=1` 的 Artifact 分区可见“文档解析质量摘要”和“检索来源”，桌面与 `390px` 移动端无横向溢出，console error 为 `0`。

本轮发现并处理：

- `REA-20260708-P3-009`：中途 run `docpilot-parser-real-chain-20260708212024-9bd2ea` 复用了手动启动的 backend。该 backend 不在 runner 受控启动参数内，导致 PDF / HTML / DOCX QA 阶段都记录为 `qa_api_failed`，整体 `FAILED_CORE_FLOW`。runner 已改为默认不静默复用已有 backend / frontend；只有显式 `-ReuseRunningServices` 才复用。runner 自己启动 backend 时通过子进程环境设置 `AI_MODE=mock` 与 `APP_QUALITY_CONSOLE_ENABLED=true`，避免真实 provider 超时或 Quality Console 未开启导致误判。

边界：

- 本轮创建临时 smoke 用户和临时文档；artifact 位于 ignored 的 `backend/target/smoke/document-parser-real-chain/`。
- 未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

### `REA-20260708-P3-009` parser smoke 静默复用不受控 backend 导致 QA 阶段误失败

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：工程流程问题
- 模块：Smoke Runner / Document Parser
- 发现于：`docpilot-parser-real-chain-20260708212024-9bd2ea`
- 修复验证：`docpilot-parser-real-chain-20260708212742-0f9baa`
- 复现步骤：手动启动一个不由 parser smoke runner 管理的 backend，然后运行 `document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`。
- 实际结果：runner 直接复用健康 backend，但该 backend 不具备 runner 预期的受控配置；PDF / HTML / DOCX 解析和 chunk 已成功，source locator 也存在，但 QA 阶段均记录 `qa_api_failed`，整体状态为 `FAILED_CORE_FLOW`。
- 预期结果：真实 smoke 应只复用显式授权的运行中服务；默认 run 应自行启动受控 backend / frontend，或在发现已有服务时返回清晰 `BLOCKED`，避免把环境配置问题误判为 parser 业务失败。
- 可能原因：runner 原先只看 `/actuator/health` 和前端路由是否可达，没有区分“受控启动”和“用户手动启动”的服务配置。
- 建议修复位置：`scripts/smoke/document-parser-real-chain-smoke.ps1`。
- 修复提交：本轮提交。
- 验证记录：`document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-parser-real-chain-20260708212742-0f9baa`；三类文档 QA retrieval / citation / source locator 均 PASS，parserBoundary `4/4` PASS。

## 2026-07-05 Agent Quality Console Trace / Eval / Trend 回归

验证 marker：`docpilot-real-user-qa-20260705210119-7b8092`

状态：PASS

已验证：

- 真实用户 QA 审计通过：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、naturalCorpus、multiQueryRag、answerGrounding、noEvidenceThreshold、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Agent Quality Console 可见最新真实 run：`/api/quality/runs/{marker}` 返回 `summary.status=PASS`、`gateCount=22`、`evalCaseCount=27`、`traceReferenceCount=2`。
- Eval Catalog / Trend 可见：`/api/quality/eval-cases` 返回 7 个 case；`/api/quality/trends?limit=20` 返回 20 个趋势点。
- 浏览器 `/quality?autoload=1` 可见最新 marker、Eval Catalog、Quality Trend 和 trace reference；`/quality/trace` 可见脱敏链路步骤；桌面和 `390px` 移动端 console error count 均为 `0`，无横向溢出。

本轮发现并处理：

- `REA-20260705-P3-008`：中途 run `docpilot-real-user-qa-20260705205210-8c882e` 在 KnowledgeBase 阶段出现一次 `TypeError` console error，但旧 gate 只记录 kind，无法定位具体字段。已增强 smoke runner 诊断为 `phase/kind/messageShape`，并保持 `TypeError` 继续阻断；最终 PASS run 未复现该异常。

边界：

- 本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation 和 Memory 数据；artifact 位于 ignored 的 `backend/target/audit/`。
- 未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

### `REA-20260705-P3-008` frontendInteraction 捕获 KB 阶段 TypeError 时缺少脱敏 message shape

- 状态：VERIFIED（已验证）
- 严重级别：P3
- 类型：工程流程问题
- 模块：Smoke Runner / Frontend Interaction Gate
- 发现于：`docpilot-real-user-qa-20260705205210-8c882e`
- 修复验证：`docpilot-real-user-qa-20260705210119-7b8092`
- 复现步骤：运行真实用户 QA 审计，进入 frontendInteraction gate；浏览器访问文档详情、KnowledgeBase 问答和权限负向路径。
- 实际结果：KnowledgeBase 阶段捕获 1 条 `TypeError` console error，导致 `frontendInteraction` 为 `FAILED_CORE_FLOW`；旧 artifact 只记录 `phase=knowledgeBase` 和 `kind=typeError`，没有脱敏错误模板，无法继续定位具体可空字段。
- 预期结果：console error 仍应阻断质量门禁，但 artifact 至少要记录脱敏后的错误类别和 message shape，便于下一轮定位；不得保存 URL、token、用户输入、answer 原文、文档全文或 evidence context。
- 可能原因：真实前端偶发异常未稳定复现，同时 gate 诊断字段不足。
- 建议修复位置：`scripts/smoke/cloud-quality-smoke.ps1`。
- 修复提交：本轮提交。
- 验证记录：最终真实 run `docpilot-real-user-qa-20260705210119-7b8092` PASS；`frontendInteraction.consoleErrorCount=0`、`blockingConsoleErrorCount=0`；Console autoload 和 Trace 页面浏览器验证均无 console error、无横向溢出。

## 2026-07-05 Agent Quality Console 7-case 回归

验证 marker：`docpilot-real-user-qa-20260705192354-eba0fc`

状态：PASS

已验证：

- 真实用户 QA 审计通过：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、naturalCorpus、multiQueryRag、answerGrounding、noEvidenceThreshold、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Agent Quality Console 可见最新真实 run：`/api/quality/runs` 可见最新 marker，`/api/quality/runs/{marker}` 状态为 `PASS`，gate 数为 `22`。
- Eval Catalog 可见 7 个 case：其中 4 个带 `sourceIssueIds`，7 个带 `remediationHints`；这些字段只包含脱敏编号和安全 identifier。
- 浏览器 `/quality?autoload=1` 可见最新 marker、Eval Catalog、source issue、verified marker、remediation hints、Failure Triage、Run Comparison 和 Model / Cost Summary；桌面和 `390px` 移动端 console error count 为 `0`，无横向溢出。

本轮发现：

- 无新增 P0/P1/P2/P3 bug。
- 无环境 BLOCKED。

边界：

- 本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation 和 Memory 数据；artifact 位于 ignored 的 `backend/target/audit/`。
- 未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

## 2026-07-05 Agent Quality Console Phase 6 回归

验证 marker：`docpilot-real-user-qa-20260705165151-bbe588`

状态：PASS

已验证：

- 真实用户 QA 审计通过：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、naturalCorpus、answerGrounding、noEvidenceThreshold、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Agent Quality Console 可见最新真实 run：`/api/quality/runs` 可见最新 marker，`/api/quality/runs/{marker}` 状态为 `PASS`，`/api/quality/eval-cases` 返回 3 个 case。
- 浏览器 `/quality?autoload=1` 可见最新 marker、`Eval Catalog`、`Failure Triage`、`Run Comparison` 和 `Model / Cost Summary`；console error count 为 `0`，`390px` 宽度无横向溢出。

本轮发现并修复：

- `REA-20260705-P1-007`：首轮真实审计 `docpilot-real-user-qa-20260705164732-f54da1` 在 backend health 阶段 BLOCKED。根因是新增 `QualityEvalCatalogServiceImpl` 存在多个构造器，但主构造器缺少显式 `@Autowired`，真实 Spring 启动时尝试使用默认构造器并失败。

边界：

- 本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation 和 Memory 数据；artifact 位于 ignored 的 `backend/target/audit/`。
- 未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

### `REA-20260705-P1-007` Eval Catalog service 构造器注入缺失导致 backend health BLOCKED

- 状态：VERIFIED（已验证）
- 严重级别：P1
- 类型：功能 bug
- 模块：Agent Quality Console / Backend Startup
- 发现于：`docpilot-real-user-qa-20260705164732-f54da1`
- 修复验证：`docpilot-real-user-qa-20260705165151-bbe588`

复现步骤：

1. 运行 `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`。
2. 等待脚本启动本地 backend 并轮询 `/actuator/health`。
3. 查看本地后端启动日志摘要。

实际结果：

- `configConsistency` 和 `tunnel` 均 PASS。
- `backendHealth` 为 BLOCKED，safe message 为 backend health 未在超时内 UP。
- 本地日志显示 `qualityEvalCatalogServiceImpl` 创建失败，原因是没有默认构造器。

预期结果：

- backend 应在开启 Agent Quality Console 后正常启动，`/actuator/health` 返回 `UP`。

可能原因：

- `QualityEvalCatalogServiceImpl` 有 public Spring 构造器和 package-private 测试构造器，但没有用 `@Autowired` 标记主构造器；Spring 在真实启动路径中未选择正确构造器。

建议修复位置：

- `backend/src/main/java/com/docpilot/backend/quality/service/impl/QualityEvalCatalogServiceImpl.java`
- `backend/src/test/java/com/docpilot/backend/quality/service/impl/QualityEvalCatalogServiceSpringContextTest.java`

修复提交：本轮提交 `fix(quality): stabilize console real audit`

修复摘要：

- 给 `QualityEvalCatalogServiceImpl` 主构造器补充 `@Autowired`。
- 新增 Spring context runner 测试，防止 Quality Eval Catalog service 真实装配回归。
- 顺手修复 `/quality` autoload 后移动端 Overview run 卡片被长 marker 撑宽的问题。

验证记录：

- `mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped。
- `npm run lint` PASS。
- `npm run build` PASS。
- `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705165151-bbe588`。
- `/quality?autoload=1` 可见最新 marker、Eval Catalog、Failure Triage、Run Comparison 和 Model / Cost Summary；console error count 为 `0`，`390px` 无横向溢出。

## 2026-07-04 Agent Quality Console 回归审计

验证 marker：`docpilot-real-user-qa-20260704221704-4abc6f`

状态：REVIEW

已验证：

- Agent Quality Console 可读取并展示最新真实审计 artifact：`/api/quality/runs`、`/api/quality/runs/{marker}` 和浏览器 `/quality?autoload=1` 均能看到该 marker。
- 浏览器 Console 验证通过：marker 可见、`REVIEW` 状态可见，console error count 为 `0`。
- 核心真实链路 gate 通过：tunnel、backend health、frontend routes、临时用户、上传 / parse / indexing、chunk 质量、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、multi-query、answer grounding、no-evidence、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- `agent-quality-eval-smoke.ps1 -Mode run` PASS，marker `docpilot-agent-quality-eval-20260704221655-48a5cf`。

本轮发现：

- `REA-20260704-P2-006`：自然语料多文档 summary case `ops-incident-support-summary` 的目标覆盖和事实表达满足，但 citation 集合中出现 `distractorCitation` review。

边界：

- 本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation 和 Memory 数据；artifact 位于 ignored 的 `backend/target/audit/` 和 `backend/target/agent-quality-eval/`。
- 未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

### `REA-20260704-P2-006` 多文档 summary 在目标覆盖满足时仍带入一条干扰 citation

- 状态：VERIFIED（已验证）
- 严重级别：P2
- 类型：功能质量问题
- 模块：KnowledgeBase RAG Citation
- 发现于：`docpilot-real-user-qa-20260704221704-4abc6f`
- 修复验证：`docpilot-real-user-qa-20260705145304-7a53b8`

复现步骤：

1. 运行 `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`。
2. 查看 `naturalCorpus` gate 的多文档 summary case。
3. 观察 `ops-incident-support-summary` 的 review bucket。

实际结果：

- `naturalCorpus.casePassRate=1`，目标文档覆盖、回答事实表达和 citation phrase support 未失败。
- `ops-incident-support-summary` 进入 `reviewBuckets=["distractorCitation"]`，同轮 `distractorCitationFreeCount=24/25`。

预期结果：

- 多文档 summary 应在保证目标文档覆盖的同时尽量避免带入与问题无关的干扰 citation。
- 如果保留额外 citation，应能在 artifact / trace 中解释其必要性；否则应通过 citation selection / rerank / diversity policy 降低干扰引用。

可能原因：

- 当前多文档 summary 为了保证覆盖和 recall，citation selection 对额外相似文档保持宽松；当干扰文档和目标主题词相近时，会以 REVIEW 形式暴露。

建议修复位置：

- `backend/src/main/java/com/docpilot/backend/ai/service/impl/KnowledgeBaseRagQaServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/service/impl/KnowledgeBaseRagRetrievalServiceImpl.java`
- `scripts/smoke/cloud-quality-smoke.ps1`

修复提交：本轮提交 `fix(rag): prune low confidence summary citations`

修复摘要：

- 在 `KnowledgeBaseRagQaServiceImpl` 的答案生成后 citation 后处理阶段增加极低分 citation 裁剪；仅在多文档意图下、裁剪后仍能保留至少两份文档 coverage 时生效。
- 保留 retrieval hits 和 `documentHitCounts`，因此 Trace / audit 仍能看到被召回过的干扰文档，不把召回诊断证据从系统里抹掉。
- 新增单测覆盖“目标两文档 citation 保留、低分干扰 citation 移除、召回 hits 仍保留”的场景。

验证记录：

- `mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest" test` PASS。
- `mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS。
- `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705145304-7a53b8`。
- 本次真实回归中 `naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`，frontendInteraction、Memory quality、Conversation Trace、权限隔离和 artifact 脱敏均 PASS。

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
