# Progress Log

## 2026-06-29 Quality Loop v5.4 / RAG Retrieval Error Analysis Report

- Added a redacted `RagRetrievalErrorAnalysis` summary to KnowledgeBase RAG eval and RAG Real QA eval artifacts.
- The summary groups missed retrieval, wrong retrieval, no-evidence refusal, unsupported citation, unsupported answer, forbidden leak, scope violation and ranking candidate pass counts without storing raw query, document, model instruction/evidence or answer content.
- Verified: `mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest,KnowledgeBaseRagEvalRunnerTest" test` PASS, 5 tests.
- Boundary: offline eval/reporting gate only; no tunnel/backend/frontend run, no business data creation and no real provider call in this slice.

## 2026-06-29 Quality Loop v5.3 / RAG Real Provider Faithfulness Smoke

- 已给 `cloud-quality-smoke.ps1` 增加 `-EnableRealProviderFaithfulnessGate`，并让 `rag-real-qa-eval-smoke.ps1` 默认开启该 gate、支持 `-SkipRealProviderFaithfulnessGate`。
- Gate 只保存 provider / model / modelCallCount / answerLength / noEvidence / passed 等脱敏摘要；不保存回答原文、文档原文、prompt、evidence context、token、云地址或连接串。
- 首次真实 run 暴露 `answerFaithfulness` 问法不稳，真实回答未带出 `ALPHA-CLOUD-GATE` 与 citation marker；已把问题收窄为直接询问 `ALPHA-CLOUD-GATE`，随后重跑 PASS。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629191831-69d71e`。
- 真实结果：`realProviderFaithfulness` PASS，`knowledgeBaseRag`、`answerFaithfulness`、`claimSupport`、`numericFaithfulness` 四个 scope 均观察到非 mock provider、`modelCallCount=1`、`noEvidence=false`、answer length 大于 `0`；hard gate、semantic gate、representative corpus、answer grounding、no-evidence、Conversation Trace、权限隔离、frontend routes、cleanup 和 artifact redaction 均保持 PASS。
- 边界：这是小规模真实 provider smoke，不是大规模 answer faithfulness benchmark、通用 entailment scorer 或线上 SLA；artifact 位于 ignored `backend/target/rag-real-qa/.../artifact.json`，不提交原文。

## 2026-06-29 Quality Loop v5.2 / RAG Claim Support Evidence Scorer

- 已新增 test-side `RagClaimSupportScorer` / `RagClaimSupportScore`：Real QA Eval case 可声明 `expectedClaims`，每个 claim 只包含脱敏 claim id、answer marker、evidence marker 和 forbidden marker。
- `RagRealQaEvalResult` 的 case summary 现在输出 `claimSupportRequired`、`claimCount`、`supportedClaimCount`、`unsupportedClaimCount`、`claimSupportHit`、`forbiddenClaimHit`；artifact 仍不保存回答原文、文档原文、query 原文、prompt、evidence context 或模型输出。
- `RagRealQaEvalMetrics` 新增 `claimSupportScorerPassRate`、`supportedClaimRate`、`unsupportedClaimRate`、`forbiddenClaimRate`；访问审批链、四小时 SLA、root cause、报销限额和 vendor risk citation grounding 等代表 case 已接入 `expectedClaims`。
- 已验证：`mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，10 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，207 tests。
- 边界：本片只增强离线 test-side scorer / fixture / metrics / artifact schema 和事实源文档；未启动 tunnel / backend / frontend，未创建业务数据，未调用真实 provider / Qdrant / MySQL，未改生产 API 或数据库结构，不能写成通用 entailment scorer 或大规模真实 provider benchmark。

## 2026-06-29 Quality Loop v5.1 / RAG Real Corpus Expansion to 40 Cases

- 已进入用户选择的 A1 + A2 + A3 路线，先完成 A3 的离线语料扩容闭环：`real-qa-eval-cases.json` 从 `26` 个 case 扩展到 `40` 个 case。
- 新增 `14` 个脱敏企业知识库样例，覆盖合同续约、访问变更审批链、SLA 数字忠实度、审计交接、多文档客户事故沟通、hard negative、near-miss no-evidence、answer faithfulness、SSO / MFA 比较、报销限额、scope isolation、长备份 runbook、hybrid keyword 噪声和 citation grounding。
- Fixture 门禁同步增强：总 case 数下限提高到 `40`，并提高 hard negative、answer faithfulness、claim support、numeric faithfulness、multi-doc summary 和 scope isolation 等类别覆盖要求。
- 已验证：`mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，10 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，207 tests。
- 边界：本片只增强离线 test-side eval / fixture / 测试和事实源文档；未启动 tunnel / backend / frontend，未创建业务数据，未调用真实 provider / Qdrant / MySQL，未改生产 API 或数据库结构，未提交 artifact 原文，未打印 secrets，不能写成大规模真实 provider benchmark。

## 2026-06-29 Quality Loop v4.3 / RAG Real QA Semantic Gate Smoke

- 已把 v4.2 离线 `claim_support` / `numeric_faithfulness` 迁移进真实 RAG Real QA smoke：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRealQaSemanticGate`，`rag-real-qa-eval-smoke.ps1` 默认开启并支持 `-SkipRealQaSemanticGate`。
- 临时 Alpha / Beta KnowledgeBase 现在包含目标 evidence marker 与语义 / 数字干扰 marker；真实 gate 检查 retrieve / QA no-evidence、hit / citation 数、target / forbidden citation count、score summary、marker 命中、citation marker 和 answer length。
- Artifact 仍只保存脱敏 summary、计数、分数和布尔值；不保存回答原文、prompt、文档原文、evidence context、token、云地址或连接串。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629183549-4aafc3`。
- 真实结果：`realQaSemanticGate` PASS；`claimSupport` 与 `numericFaithfulness` 均为 `1` retrieve hit、`1` QA citation、target citation `1`、forbidden citation `0`、expected marker satisfied、forbidden marker absent、citation marker present。hard negative、representative corpus、answer grounding、普通 no-evidence、Conversation Trace、权限隔离、frontend routes、cleanup 和 artifact redaction 均保持 PASS。
- 边界：这是小规模真实链路语义支持门禁，不是通用语义蕴含模型、大规模真实 provider benchmark 或线上 SLA；本片不改生产 API、不改 schema、不删数据、不提交 artifact 原文、不打印 secrets、不 push。

## 2026-06-29 Quality Loop v4.2 / RAG Claim Support and Numeric Faithfulness Eval

- 已扩展 RAG Real QA Eval：新增 `claim_support` 和 `numeric_faithfulness` 两类脱敏 case，分别验证目标 evidence 支持的 manager approval 结论，以及 seven-year retention 不被 three-year 干扰文档污染。
- 已增强指标：`RagRealQaEvalMetrics` 新增 `claimSupportPassRate` 与 `numericFaithfulnessPassRate`，safe artifact 同步输出两个指标；仍只保存 case summary、类别、计数、文档 ID 和布尔结果，不保存文档全文、query、prompt、evidence context 或模型输出。
- 已验证：`mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，10 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，207 tests。
- 边界：本片只增强离线 test-side eval，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API 或数据库结构，不提交 artifact 原文，不打印 secrets，不 push；结论是离线语义支持门禁增强，不是大规模真实 answer faithfulness benchmark。

## 2026-06-29 Quality Loop v4.1 / Memory Extraction Quality Eval

- 已扩展 Memory Quality Eval：新增多信号抽取、assistant 指令污染、低价值寒暄、一次性回答风格、敏感 token/API key 指令五类脱敏离线 case。
- 已增强规则式候选抽取：`RuleBasedMemoryExtractionService` 在生成 memory suggestion 前过滤敏感内容和一次性 / 临时指令，避免把 token/API key 占位指令或“这一次回答详细一点，后面不用记住”沉淀为长期记忆。
- 已增强 eval 指标和脱敏 artifact：新增 `suggestionSafetyRate`、`userSignalExtractionRate`、`noiseSuppressionRate`、`temporaryInstructionSuppressionRate`，并对每个候选执行 `MemorySafetyValidator` 检查；artifact 仍不保存会话正文、memory 正文、prompt、token 或连接信息。
- 已验证：`mvn "-Dtest=MemoryQualityEvalFixtureTest,MemoryQualityEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*Memory*,*Context*" test` PASS，63 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，267 tests。
- 边界：本片不启动真实 tunnel / backend / frontend，不创建业务数据，不调用真实 LLM memory extraction，不改数据库结构，不删除业务数据，不提交 artifact 原文，不打印 secrets，不 push；结论是规则式离线质量门禁增强，不是长期记忆真实 provider 效果评测。

## 2026-06-29 Quality Loop v3.9 / Memory Governance Edit and Resolve

- 已新增用户可控 Memory 治理闭环：`PATCH /api/memories/{memoryId}` 支持编辑 ACTIVE memory；`POST /api/memories/suggestions/{memoryId}/resolve` 支持 `KEEP_ACTIVE`、`REPLACE_ACTIVE`、`MERGE_WITH_ACTIVE`。
- 后端治理边界：所有 edit / resolve 路径校验当前用户、ACTIVE / SUGGESTED 状态、同类型、敏感内容和重复 / 冲突治理；不改表结构、不 hard delete、不做自动 LLM merge。
- `/conversations` Memory 抽屉已支持 ACTIVE 记忆编辑，以及冲突 / 重复候选的保留、替换、手动合并；合并文本由用户确认。
- Smoke gate 已扩展：Memory 专项真实链路覆盖冲突 accept 被拦、keep -> `IGNORED`、replace 更新 ACTIVE、敏感 edit 被拒、普通 edit 成功、merge 更新 ACTIVE；artifact 只保存状态 / code / count / length，不保存记忆正文。
- 已验证：Memory / Context targeted tests 63/63 PASS；RAG / KnowledgeBase / Conversation / Memory 回归 267/267 PASS；脚本安全测试 5/5 PASS；`npm run lint` PASS；`npm run build` PASS；`memory-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 run PASS，marker 为 `docpilot-memory-quality-20260629140941-6668d9`。
- 首次真实 run 暴露 KB answer grounding 问题：KB QA 问题没有明确要求逐字包含 evidence marker，真实回答未命中 marker；已把 KB gate 问法对齐到代表性 corpus 的“include exact evidence markers verbatim”标准，并在随后真实 run 中 PASS。
- 边界：本片不做真实模型长期记忆抽取、不做自动合并、不新增 memory 审计表、不改 schema、不删业务数据、不提交 artifact 原文、不打印 secrets、不 push。

## 2026-06-29 Quality Loop v3.8 / RAG Quality Interview Docs Sync

- 已同步 `README.md`：补充 RAG no-evidence、answer grounding、hard negative、answer faithfulness、Conversation Trace、MySQL / Qdrant 一致性、权限隔离和脱敏 artifact 质量门禁口径。
- 已同步 `docs/showcase/PROJECT_INTERVIEW_BRIEF.md`、`RESUME_BULLETS.md`、`INTERVIEW_QA.md`：面试材料现在能讲“真实 smoke 暴露 hard-negative REVIEW，再通过近阈值 evidence support gate 修到 PASS”的闭环。
- 边界保持克制：hard-negative 支持度门禁写成近阈值启发式，不写成通用语义蕴含模型、大规模 relevance benchmark、线上 SLA 或生产级完整向量 RAG。

## 2026-06-29 Quality Loop v3.7 / Hard Negative Near-threshold Support Gate

- 已针对 v3.6 真实 smoke 暴露的 hard negative REVIEW 做最小治理：KnowledgeBase retrieval 在 similarity threshold、hybrid confidence gate、rerank 和 diversity selection 后增加近阈值低支持度拒答门。
- 触发条件保持收窄：仅非总结类问题、最高 threshold score 只略高于阈值、query 关键英文业务词数量足够、且候选 evidence 文本覆盖率低时才清空 hits；summary intent 和高置信 evidence 不受影响。
- 已补测试：hard negative 近阈值低支持度返回 no-evidence；近阈值但 evidence 覆盖 payroll / tax / remittance / approval / delegated / owner 等关键词时保持命中。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，13 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，207 tests。
- 真实链路验证：`rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629130454-1d1d6c`。`realQaHardGate` PASS，其中 `hardNegative` 为 `retrieveNoEvidence=true`、`qaNoEvidence=true`、`0` hits、`0` citations；`answerFaithfulness` target citation `1`、forbidden citation `0`，expected marker satisfied；代表性三文档 corpus、answer grounding、普通 no-evidence、Conversation Trace、权限隔离、frontend routes、cleanup 和 artifact redaction 均 PASS。
- 边界：本片不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push；该方案是近阈值支持度启发式，不是通用语义蕴含模型。

## 2026-06-29 Quality Loop v3.6 / RAG Real QA Hard Gate Smoke

- 已给 `cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRealQaHardGate`，复用已有 Alpha / Beta 临时 KB，不额外上传文件，检查 `hardNegative` 与 `answerFaithfulness` 两个真实链路 scope。
- `rag-real-qa-eval-smoke.ps1` 默认开启该 gate，并提供 `-SkipRealQaHardGate`；plan 输出新增 `hard_negative`、`answer_faithfulness`、`realQaHardGate` 和 `realQaHardGateEnabledByDefault`。
- Artifact 只记录 no-evidence 布尔值、hit / citation 数、score summary、marker 命中布尔值和 answer length；不保存回答原文、prompt、文档原文、evidence context、token、云地址或连接串。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests。
- 真实链路验证：首次 run 将 hard negative 作为 core failure 暴露，随后调整为 optional quality REVIEW gate；再次执行 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 完成，marker 为 `docpilot-rag-real-qa-20260629125627-c0915e`，整体 `REVIEW`。核心链路、representative corpus、answer grounding、普通 no-evidence、Conversation Trace、权限隔离、frontend routes、cleanup 和 artifact redaction 均 PASS；`answerFaithfulness` PASS；`hardNegative` 仍返回 `3` hits / `3` citations，vector score 约 `0.50-0.55`。
- 边界：本片不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push；结论是小规模真实链路 hard negative 质量缺口证据，下一步应做 evidence support / grounding policy，而不是简单硬调全局阈值。

## 2026-06-29 Quality Loop v3.5 / RAG Hard Negative and Answer Faithfulness Eval

- 已给 RAG Real QA Eval 追加两类离线质量门禁：`hard_negative` 用强词面相似但缺少目标结论的 payroll / tax / vendor / owner 场景验证 no-evidence；`answer_faithfulness` 用目标 policy exception evidence 与相近 SLA 干扰文档验证回答只落在目标 marker 上。
- `RagRealQaEvalMetrics` 新增 `hardNegativePassRate` 与 `answerFaithfulnessPassRate`，artifact safe map 同步输出两个脱敏指标；`hard_negative` 同时计入 distractor suppression 聚合。
- 已验证：`mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，10 tests。
- 边界：本片只改离线 test-side eval / fixture / metrics 和事实源文档；未启动 tunnel / backend / frontend，未创建业务数据，未调用真实 provider / Qdrant / MySQL，未改生产 API 或数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，不写成大规模真实 answer faithfulness benchmark。

## 2026-06-29 Quality Loop v3.4 / RAG Answer Grounding Gate v1

- 已给 `cloud-quality-smoke.ps1` 新增 `Test-AnswerGrounding` 与 `answerGrounding` gate：对单文档 RAG、KnowledgeBase 两文档 RAG、representative corpus 三文档 RAG 的最终回答检查 answer present、预期 evidence marker 命中、forbidden marker 未泄漏和 citation marker 存在。
- Artifact 只记录回答长度、marker 数量、命中计数和布尔结果；不保存回答原文、prompt、evidence context、response 原文、token、云地址或连接串。
- 已增强 `scripts/smoke/rag-real-qa-eval-smoke.ps1`：plan 输出新增 `answer_grounding` case type 和 `answerGrounding` gate；Representative Corpus 问题文本明确要求逐字包含 `ALPHA-CLOUD-GATE`、`BETA-CONTEXT-GATE`、`real-incident-detection-marker`，让真实回答门禁检查更公平。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests。
- 真实链路验证：首次 run 在 `answerGrounding` 暴露 representative answer 只命中 `2/3` 个预期 marker，说明门禁有效；调整问题后再次执行 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629003157-630db5`。三个 scope 均 `expectedMarkersSatisfied=true`、`forbiddenMarkerHit=false`、`citationMarkerPresent=true`；representative gate 返回 `8` hits / `8` citations，documentHitCounts 覆盖 Gamma `203:2`、Beta `202:3`、Alpha `201:3`。
- 边界：本片不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push；结论是小规模真实链路回答落证门禁，不写成大规模 answer faithfulness benchmark 或线上 SLA。

## 2026-06-28 Quality Loop v3.3 / RAG Real Corpus 真实链路代表性三文档门禁

- 已给 `cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRepresentativeCorpusGate`：真实链路中额外上传 incident review Gamma 文档，并与既有 Alpha / Beta 两文档组成 Representative Corpus KB。
- Representative corpus gate 要求 KnowledgeBase retrieve 和 QA citation 都覆盖 Alpha / Beta / Gamma 三份文档；artifact 只记录 ids、count、documentHitCounts 和 score summary，不保存文档全文、prompt、evidence context 或凭据。
- 已增强 `scripts/smoke/rag-real-qa-eval-smoke.ps1`：RAG Real QA 专项 smoke 默认打开 representative corpus gate，并提供 `-SkipRepresentativeCorpusGate` 跳过开关；plan 输出包含 `representative_corpus` 和 `representativeCorpusEnabledByDefault=true`。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，2 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS，9 tests。
- 真实链路验证：`rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260628234235-5c1b94`；representative gate 返回 `8` hits / `8` citations，documentHitCounts 覆盖 Gamma `196:2`、Beta `195:3`、Alpha `194:3`，no-evidence、Conversation Trace、权限隔离、前端 routes、cleanup 和 artifact 脱敏均保持 PASS。
- 边界：本片不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push；结论是小规模真实链路代表性门禁，不写成大规模 relevance benchmark。

## 2026-06-28 Quality Loop v3.2 / Memory Governance 第一片

- 已新增 Memory 治理响应字段：`duplicateOfId`、`conflictWithId`、`governanceHint`、`similarityScore`，用于让候选记忆在进入 ACTIVE 前说明疑似重复、近似重复或明确偏好冲突。
- 后端在手动创建 memory 时拒绝同类型 ACTIVE 精确重复；接受候选前检查同类型 ACTIVE memory 的精确重复、近似重复和少量明确冲突词，冲突候选不会直接变为 ACTIVE。
- `/conversations` Memory 抽屉已读取治理字段，展示冲突 / 重复的 memory id 与相似度提示；Gemini CLI 提供轻量 UX sanity 建议，Codex 只落地紧凑提示，不新增未实现的合并按钮。
- 已补真实链路治理 smoke：`memory-quality-smoke.ps1` 委托 `cloud-quality-smoke.ps1 -EnableMemoryQualityGate`，创建临时 ACTIVE `ANSWER_STYLE` 基线和冲突 answer-style suggestion，要求 `governanceHint=conflict_active_memory`、`conflictWithId` 非空，并验证直接 accept 被治理门禁阻止。
- 已验证：`memory-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 `memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-memory-quality-20260628223255-0a06e6`；`mvn "-Dtest=*MemoryQualityEval*,*Memory*,*Context*" test` PASS，54 tests；此前 `mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，255 tests；`npm run lint` PASS；`npm run build` PASS。
- 边界：本片不改数据库结构，不做真实 LLM memory extraction，不做自动合并 / 自动删除，不删除业务数据，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push；真实 smoke 创建了临时 smoke 用户、文档、KnowledgeBase、Conversation 和 memory 数据，artifact 位于 ignored `backend/target/memory-quality/.../artifact.json`。下一片可进入 Memory 编辑 / 合并交互，或把 RAG Real Corpus 代表 case 迁移进真实链路 smoke。

## 2026-06-28 Quality Loop v3.1 / RAG Real Corpus Eval 第一片

- 已将 `real-qa-eval-cases.json` 从 9 个 case 扩到 22 个 case，新增 security policy、runbook、onboarding、expense policy、contract clause、incident review、API policy、access audit 等脱敏企业知识库样例。
- 新增 case 类型覆盖 `long_document`、`near_miss_no_evidence`、`multi_doc_summary`、`citation_grounding`、`scope_isolation`，并继续保留 factual lookup、comparison、multi-hop、semantic distractor、hybrid keyword noise 和 rerank uplift candidate。
- 已增强 `RagRealQaEvalMetrics`：新增 `longDocumentCasePassRate`、`nearMissNoEvidenceRate`、`multiDocSummaryPassRate`、`distractorSuppressionRate`；artifact 继续只保存脱敏 summary、计数、文档 ID、类别和失败原因，不保存文档全文、query 原文、prompt、evidence context、模型输出或 secrets。
- 已验证：`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，9 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，204 tests。
- 边界：本片只做离线 test-side eval，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文、不 push；结论是 RAG 质量门禁覆盖度提升，不是大规模真实 provider benchmark。下一片进入 Memory Governance v1。

## 2026-06-28 Quality Loop v2.3 / Memory 产品化第一片

- 已增强 `/conversations` Memory 抽屉：新增生效 / 候选 / 重复提示 KPI、类型分布、来源说明（手动添加 / 系统候选 + 会话 / 消息来源）、priority、confidence、更新时间、ACTIVE 重复提示和候选已存在提示；生效记忆按 priority / 更新时间排序，候选按 priority / confidence 排序。
- Gemini CLI 参与轻量 UX sanity 建议，Codex 落地低风险子集：不做深链接、不做内联编辑、不做乐观状态更新，先补用户接受 / 忽略前最需要的 provenance 与重复风险信息。
- 已验证：`npm run lint` PASS；`npm run build` PASS；真实浏览器创建临时用户、3 条 ACTIVE memory、2 条 suggestion，marker 为 `docpilot-memory-ui-product-1782651263292`，Conversation `41`。桌面 Memory 面板显示 KPI、来源、confidence 和重复提示，`cardCount=5`，`scrollWidth=clientWidth=1265`；`390x844` 下 `scrollWidth=clientWidth=375`，`metaCount=17`，`cardCount=5`；`320x740` 下 `scrollWidth=clientWidth=305`，`kpiCount=3`，`metaCount=17`，`cardCount=5`。
- 边界：本片只改前端 Memory 展示和 CSS，不改后端 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交截图 / artifact / 日志原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push；结论是 Memory 管理体验更可解释，不代表真实模型长期记忆抽取能力提升。

## 2026-06-28 Quality Loop v2.2 / Rerank Hard Smoke

- 已将 `scripts/smoke/cloud-quality-smoke.ps1` 增加默认关闭的 `-EnableRerankHardGate`：真实链路 hard gate 复用 Alpha / Beta 临时文档作为目标 / 支撑文档，只额外上传 1 份关键词干扰文档，避免触发 `60s / 3 uploads / user` 文件上传限流；artifact 只记录 doc id、rank、count 和 score summary，不保存文档原文、prompt、evidence context 或凭据。
- 已增强 `scripts/smoke/rerank-effect-smoke.ps1`：两轮 cloud quality smoke 都打开 hard gate，最终根据 `rerankApplied`、target rank、distractor rank、citation delta、no-evidence regression 和 security regression 输出 PASS / REVIEW / FAILED；新增 `RerankEffectSmokeScriptSafetyTest` 覆盖 plan 输出、委托关系、hard gate 标记和敏感输出边界。
- 已验证：`rerank-effect-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RerankEffectSmokeScriptSafetyTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS，4 tests；真实 `rerank-effect-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS。baseline marker 为 `docpilot-rerank-effect-hybrid-20260628204120-3e9f69`，rerank marker 为 `docpilot-rerank-effect-rerank-20260628204339-7aac45`；hard fixture 中 target rank `2 -> 1`，distractor rank `3 -> 4`，`hardUpliftObserved=true`，no-evidence 和权限隔离无回退。
- 回归：`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，204 tests。边界：本片不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push；结论只写成小规模 hard smoke uplift 证据，不写成大规模 relevance benchmark。下一片进入 Memory 产品化。

## 2026-06-28 Quality Loop v2 / Frontend UX Audit v1

- 已完成真实浏览器前端体验审计：本地 backend / frontend 可达，使用浏览器上下文创建临时用户、两份 txt 文档、KnowledgeBase、ACTIVE memory 和绑定 KB 的 Conversation，marker 为 `docpilot-frontend-ux-2647184760`。
- API 侧证据：两文档 parse `SUCCESS`；Conversation Trace 为 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=2`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=2`、`documentHitCounts={175:1,176:1}`；KnowledgeBase QA citation `2` 且 `documentHitCounts={175:1,176:1}`。
- 页面侧证据：`/conversations` 显示 `2 条来源`，Trace 面板和 Memory 面板均可通过真实点击打开，ACTIVE memory 内容可见；`/knowledge-bases` 点击“查看引用来源”后展示 provider / 索引集合、来源文档分布、召回片段和两条引用来源。
- 移动端证据：`390x844` 下 `/conversations` 关键容器均约束在 `346px`，页面 `scrollWidth=clientWidth=375`；`/knowledge-bases` 同样无横向溢出。
- Gemini 轻量 UX sanity review 提醒继续关注：技术观测字段对普通用户的认知负担、Trace / Memory 数据量增长后的可读性，以及 `390px` 以下更窄移动端视口。
- 已补 v1.1 极窄移动端和长 memory 检查：新增一条包含长标识符的 ACTIVE memory，`360x780` 下 `/conversations` Memory 抽屉可打开且 `scrollWidth=clientWidth=345`，`320x740` 下 `scrollWidth=clientWidth=305`；`/knowledge-bases` 在两种窄视口同样无横向溢出。
- 已完成 KnowledgeBase 技术观测字段产品化降噪：问答结果区默认展示来源覆盖、引用来源、回答状态和生成次数；provider、collection、retrieval mode、rerank、answer provider / model 收进“工程观测”折叠区，展开后仍可审计。
- 已验证：`npm run lint` PASS；真实浏览器检索后默认态不显示 Provider / Collection，展开后工程字段可见；`360px` 移动端无横向溢出；`npm run build` PASS。
- 已完成更难 rerank uplift fixture 第一片：`real-qa-eval-cases.json` 新增 `real-rerank-distractor-ordering`，检验 export / audit / retention 词面干扰下仍命中 compliance export 与 audit retention 两份 evidence；`RagRealQaEvalMetrics` 新增 `rerankUpliftCandidatePassRate`。
- 已验证：`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，9 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，204 tests。
- 已完成 Memory 长列表交互审计：新临时用户下创建 `16` 条 ACTIVE memory，marker 为 `docpilot-memory-ui-1782649237433`，Conversation `37`；`390x844` 下 Memory 抽屉可打开且列表可滚动，`memoryItemCount=17`、`deleteButtonCount=16`、`scrollWidth=clientWidth=390`，桌面 `1036x850` 同样无横向溢出。中途本地 Next dev 与 `npm run build` 混用导致 `.next` chunk 缓存失效，已清理生成目录并重启本地 frontend，未改业务代码。
- 结论：Frontend UX Audit v1 / v1.1 PASS，KnowledgeBase 结果区已完成一轮产品化降噪，RAG Real QA Eval 对 rerank 候选的离线门禁更清晰，Memory 长列表 UI 已完成一轮真实压力审计；下一片建议进入真实 rerank smoke harder fixture或 README / showcase 口径同步。

## 2026-06-28 Quality Loop v2 / Memory Quality Eval v1

- 已启动 Memory Quality Eval v1 离线基线：新增 `memory-quality-eval-cases.json` 和 `MemoryQualityEvalRunner` / `MemoryQualityEvalMetrics` / `MemoryQualityEvalResult` / `MemoryQualityEvalCase`。
- fixture 覆盖用户回答风格偏好抽取、assistant RAG evidence 不进入 memory、ACTIVE / SUGGESTED / IGNORED 状态分层、敏感内容拦截，以及 summary / recent messages / user memory / RAG evidence 的 trace source counts。
- runner 复用现有 `RuleBasedMemoryExtractionService`、`MemorySelector`、`ContextAssemblyServiceImpl` 和 `MemorySafetyValidator`，只用 test double 提供会话消息、记忆和 RAG evidence；artifact 只保存脱敏 summary，不保存对话全文、memory content、prompt、evidence context 或敏感配置。
- 已验证：`mvn "-Dtest=*MemoryQualityEval*,*Memory*,*Context*" test` PASS，48 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，249 tests。
- 边界：离线第一片未启动 tunnel / backend / frontend，未创建业务数据，未调用真实 provider / Qdrant / MySQL。
- 已完成真实链路第二片：新增 `scripts/smoke/memory-quality-smoke.ps1`，并给 `cloud-quality-smoke.ps1` 增加默认关闭的 `-EnableMemoryQualityGate`；Memory 专项 wrapper 默认打开该 gate，覆盖真实候选抽取、接受 / 忽略、ACTIVE 列表隔离和绑定 KB 后 trace source counts。
- 为支撑 ASCII smoke 文本，`RuleBasedMemoryExtractionService` 补充英文关键词识别；同时 smoke runner API timeout 从 `60s` 提高到 `180s`，避免真实回答 provider 偶发慢响应误伤质量门禁，并在 API 失败时输出脱敏 status / code / message。
- 已验证：`memory-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=MemoryQualitySmokeScriptSafetyTest,RuleBasedMemoryExtractionServiceTest" test` PASS，5 tests；真实 `memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-memory-quality-20260628193150-625bf6`；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，252 tests。
- 真实 run 结果：memoryQuality gate 抽取候选 `2` 条，accepted suggestion 为 `ACTIVE`，ignored suggestion 为 `IGNORED` 且不进入 ACTIVE memory list；trace source counts 为 `recentMessages=2`、`userMemory=1`、`ragEvidence=6`，documentHitCounts 覆盖两份临时文档。下一片进入 Frontend UX Audit。

## 2026-06-28 Quality Loop v2 / RAG Real QA Eval v1

- 已启动下一轮自驱质量循环第一片：新增 RAG Real QA Eval v1 离线基线，覆盖事实查找、跨文档总结、比较、多跳式证据、no-evidence、语义干扰、hybrid keyword 噪声和 rerank uplift shaped case。
- 新增测试侧 `RagRealQaEvalRunner` / `RagRealQaEvalMetrics` / `RagRealQaEvalResult` / `RagRealQaEvalCase`，复用既有 KnowledgeBase eval harness，artifact 只输出脱敏 summary，不保存文档原文、query、模型输入、evidence context 或模型输出。
- 已验证：`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，7 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，202 tests。
- 边界：本片未启动 tunnel / backend / frontend，未创建业务数据，未调用真实 provider / Qdrant / MySQL；下一片应进入真实链路 RAG Real QA smoke runner，然后继续 Memory Quality Eval 和 Frontend UX Audit。
- 已完成第二片真实链路入口：新增 `scripts/smoke/rag-real-qa-eval-smoke.ps1`，默认 `SmokePrefix=docpilot-rag-real-qa`、artifact root `backend/target/rag-real-qa`，plan 输出 real-QA case 类型，dry-run 只检查本地前置条件。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS（当前 MySQL / Qdrant local ports 未监听，仅记录可达性）；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，2 tests。
- 已完成第三片真实链路 run：`rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260628164757-ac2a1d`；本次启动本地 tunnel / backend / frontend，创建临时 smoke 用户、两份 txt 文档、KnowledgeBase、Conversation 和 ignored 脱敏 artifact。
- 本次真实 run 覆盖：配置一致性、tunnel、backend health、frontend route、注册、两文档上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、populated-KB no-evidence、Conversation Trace、四个权限隔离负向检查、artifact 脱敏和清理。结果为整体 PASS，artifact 路径位于 ignored `backend/target/rag-real-qa/.../artifact.json`，未提交原文。
- 结论：RAG Real QA Eval v1 已从离线基线推进到真实链路 smoke 证据；下一片进入 Memory Quality Eval，重点验证长期记忆候选 / ACTIVE / IGNORED 分层、RAG evidence 不污染 memory、trace source counts 和真实会话体验。

## 2026-06-28 Phase 2 真实体验审计

- 已启动真实链路：本地 SSH tunnel、backend、frontend，并通过浏览器执行登录态审计。
- 已修复本地真实体验阻断点：`WebMvcConfig` 增加 `localhost/127.0.0.1:3007` 与 `:3100`，避免 smoke / Playwright 常用前端端口因 CORS 被后端 403。
- 已创建临时审计数据，marker 为 `docpilot-phase2-ui-audit-1782628501578`：两文档 parse `SUCCESS`，单文档 RAG `1` hit / `1` citation，KnowledgeBase API 多文档 RAG `2` hits / `2` citations，Conversation Trace 显示 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=2`、`memoryCount=1`、`userMemory=1`、`ragEvidence=2`。
- 真实体验结论为 `REVIEW`：文档详情 citation 面板未同步、KnowledgeBase 手动问法漏召回 Beta 文档、Conversation 气泡显示 `0` 条引用但 Trace 有 2 条 evidence、移动端 `/conversations` 横向溢出。
- 已修复 Conversation citation 展示第一片：历史消息加载时 best-effort 拉取最新助手消息 trace，并用 `contextTrace.evidenceCount` 作为来源数量兜底；`npm run lint` PASS，Playwright 刷新 `/conversations` 后 footer 显示 `2 条来源`，与 Trace `RAG 证据=2` 一致。
- 已修复文档详情 citation 展示第一片：前端 RAG SSE 客户端消费 `retrieval` / `citation` 事件，文档详情页流式回答后右侧引用来源显示 `检索命中 1 条`、`引用 1`、score、chunk version 和 snippet；`npm run lint` PASS。
- 已修复移动端 `/conversations` 横向溢出：移动端主聊天区、topbar、thread 与 composer wrapper 均被约束到主区宽度；Playwright `390x844` 测量 shell/main/topbar/thread/composer 均为 `346px` 宽，页面不再被长 KB label 撑出 viewport；`npm run lint` PASS。
- 已修复 KnowledgeBase 手动两文档总结问法漏召回 Beta 文档：hybrid retrieval 的 confidence gate 对 summary intent 保留 `keywordScore>0` 的候选进入 scope guard、rerank 和多文档 diversity selection，同时普通非 summary keyword 噪声仍被 similarity threshold 阻断。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，11 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，199 tests；真实 `rag-real-quality-smoke.ps1 -Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260628150434-2b7b39`，KnowledgeBase 两文档 gate `{152:3,153:3}`，no-evidence、Conversation Trace、权限隔离和前端 route smoke 均 PASS。Phase 2 当前收口为 PASS，下一阶段进入 Phase 3 小规模真实 rerank provider 实效验证。
- 已完成 Phase 3 小规模真实 rerank provider 验证：新增 `scripts/smoke/rerank-effect-smoke.ps1`，两轮运行 cloud quality smoke 对比 hybrid-only baseline 与 hybrid+real-rerank candidate；`cloud-quality-smoke.ps1` 同步输出 rerankApplied / rerankModel / rerank score summary。
- 已验证：PowerShell parser PASS；`rerank-effect-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 `-Mode run` PASS。baseline marker 为 `docpilot-rerank-effect-hybrid-20260628151134-170d38`，rerank marker 为 `docpilot-rerank-effect-rerank-20260628151301-6b0060`；rerank run `rerankApplied=true`、rerank score count `6`，KB hit / citation / coveredDocumentCount 与 baseline 持平，no-evidence 和权限隔离无回退。结论：provider 可用且无回退，但当前满分 fixture 没有证明覆盖率 uplift。

## 2026-06-28 RAG Quality Upgrade v8

- 已完成 v8 第一片：KnowledgeBase RAG eval corpus 从 5 个 case 扩到 11 个 case，新增 case 级 `minSimilarityThreshold`，覆盖 populated-KB no-evidence、hybrid keyword 噪声、多文档总结、grounding 干扰、跨主题路由和 scope 干扰。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagEvalFixtureTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagEvalRunnerTest" test` PASS，5 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已补真实链路验证：`rag-real-quality-smoke.ps1 -Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260628141419-fb7c21`；artifact 写入 ignored `backend/target/rag-quality/.../artifact.json`，不提交原文。
- 已完成 v8 第二片：单文档 RAG smoke case 从 4 个扩到 7 个 case，新增 case 级 confidence gate 和 forbidden marker 检查，覆盖 populated-document no-evidence、grounding citation marker 和 distractor 抑制。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalQualitySmokeTest" test` PASS，2 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。下一阶段进入 Phase 2 真实体验审计。

## 2026-06-27 真实链路优先自驱协议

- 已将自驱迭代模式升级为真实链路优先验证：后续 RAG、KnowledgeBase、Conversation Memory、Context Trace、权限隔离和前端关键路径改动，不能只靠 mock / 最小测试证明用户体验质量。
- `AGENTS.md`、`docs/ai-dev/CONSTRAINTS.md` 和 `docs/ai-dev/ROADMAP_RAG.md` 已同步受控放权规则：默认允许本地 tunnel / backend / frontend、真实 smoke、临时 smoke 数据、本机已有真实配置和 ignored 脱敏 artifact；远程破坏性操作、删数据、改 schema、push、大规模高成本 provider eval 仍需单独确认。

## 2026-06-27 自驱迭代推进协议

- 已将用户授权后的自驱迭代模式写入 `AGENTS.md` 和 `docs/ai-dev/CONSTRAINTS.md`：明确触发语、每片循环、自动提交条件、停止条件和安全边界。
- `CURRENT_TASK.md` 已切换下一步为 `RAG Quality Upgrade v8: eval corpus expansion（NEXT）`，后续可按自驱模式继续拆片推进。

## 2026-06-27 RAG Quality Upgrade v7

- 已完成 v7 第一片：`ContextTrace` API 暴露计算型 `contextSourceCounts` / `contextSourceFlags`，把会话摘要、最近消息、长期记忆和 RAG evidence 拆开展示；不改表结构，不持久化 prompt / evidence 原文。
- `/conversations` Trace 面板新增来源拆分计数，用户能直接看到长期记忆与 RAG 证据是两类上下文来源。
- 已验证：`mvn "-Dtest=*Context*,*Conversation*,*Memory*" test` PASS，56 tests；`npm run lint` PASS。
- 已完成 v7 第二片：补充 memory-aware RAG 负向门禁，assistant / RAG evidence 不会被抽取为长期记忆，只有 `ACTIVE` user memory 会进入上下文，`SUGGESTED` / `IGNORED` 不进入 prompt。
- 已验证：`mvn "-Dtest=RuleBasedMemoryExtractionServiceTest,MemorySelectorTest,ContextAssemblyServiceImplTest" test` PASS，6 tests。
- 已完成 v7 第三片：`cloud-quality-smoke.ps1` / `rag-real-quality-smoke.ps1` 的 Conversation Trace gate 现在要求 KB RAG evidence 与 ACTIVE user memory 同时进入 trace，并输出脱敏 `contextSourceCounts`。
- 已验证：`rag-real-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；默认 `-Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260627220736-8f03b9`；`conversationTrace` 显示 `evidenceCount=6`、`memoryCount=1`、`userMemory=1`、`ragEvidence=6`。
- 结论：v7 DONE。

## 2026-06-27 RAG Quality Upgrade v6

- 已完成 v6 第一片：KnowledgeBase RAG 离线 eval 现在同一批 case 同时跑 `vector` 与 `hybrid` 两种 retrieval mode，并在脱敏 artifact 中输出 `retrievalModeMetrics.vector` / `retrievalModeMetrics.hybrid`。
- eval harness 新增 in-memory keyword retriever，只用于质量门禁；默认线上 `hybridEnabled=false` 不变，真实 rerank provider 仍不强制。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，4 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已完成 v6 第二片：rerank provider 必须 `enabled=true` 且外部配置完整才发 HTTP；半配置状态直接 identity fallback，不破坏默认检索链路。
- 已验证：`mvn "-Dtest=*Rerank*,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，14 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests；默认真实 `rag-real-quality-smoke.ps1 -Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260627214532-e1fb65`。
- 结论：v6 DONE；真实 rerank provider 效果仍不强制，后续只在用户显式配置后单独 smoke。

## 2026-06-27 RAG Quality Upgrade v5

- 已落地 chunk structure quality：`DocumentChunkCandidate` 记录 section title / ordinal / source block ordinal / structure type / quality flags；`ChunkingServiceImpl` 基于 Markdown heading、文本块和基础异常信号生成结构 metadata。
- 索引链路已把结构 metadata 透传到 embedding metadata 与 Qdrant payload；未改数据库结构，MySQL 仍保存现有 chunk 字段。
- 已增强真实 smoke：`chunkQuality` gate 检查 MySQL offset order、token/content length 和 duplicate hash；`mysqlQdrantConsistency` gate 校验 Qdrant payload 结构字段。
- 已验证：targeted tests 28/28 pass；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` 198/198 pass；`rag-real-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；默认 `-Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260627213040-4038e1`。
- 边界：这不是复杂 PDF 智能解析，也不是大规模 chunk relevance benchmark；当前证明的是标题 / 段落结构信号已进入 chunk / Qdrant payload，并被 smoke 质量门禁覆盖。

## 2026-06-27 RAG Quality Upgrade v4

- 新增 KnowledgeBase QA answer audit：response 暴露脱敏 `audit`，记录 grounded、evidence / citation count、documentHitCounts、score / vectorScore / fusedScore / rerankScore summary、retrievalMode、rerank 信息、fallbackReason 和 modelCallCount；不保存 prompt、evidence 原文或模型输入输出。
- 增强离线 KnowledgeBase RAG eval：新增 `groundedAnswerRate` 和 `noEvidenceCitationFreeRate`，正例必须有 grounded answer 与 citation marker，no-evidence 例子必须无 citation 且不调用模型。
- 已验证：v4 targeted tests 21/21 pass；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` 198/198 pass；默认 `rag-real-quality-smoke.ps1 -Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260627211711-383cda`。
- 本轮真实 run 创建临时 smoke 数据和 ignored artifact，未操作远程 Docker，未走 `hk-ops`，未删除业务数据，未改数据库结构，未提交 artifact 原文，未 push。

## 2026-06-27 RAG Quality Upgrade v3

- 完成 evidence confidence gate 收口：KnowledgeBase hybrid retrieval 在融合后继续执行阈值过滤，并对带 `vectorScore` 的 hybrid hit 使用原始向量相似度做门禁，避免把 RRF `fusedScore` 当作 similarity；低置信 keyword / fused 结果不进入 grounded QA。
- 默认质量阈值校准为 `0.50`，已同步 `application.yml`、`.env*.example`、`scripts/smoke/cloud-quality-smoke.ps1`、`scripts/smoke/rag-real-quality-smoke.ps1` 和 `RAG_HYBRID_RETRIEVAL_GUIDE.md`；`RagRetrievalProperties` 程序化默认仍保持 `0.0` 以兼容离线 harness。
- smoke artifact 新增 KB `vectorScoreSummary`，并修复失败时覆盖已有 gate checks 的问题，确保失败也能保留 score / citation / hit 分布用于诊断。
- 已验证：targeted RAG / KB / Conversation tests 33/33 pass；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` 198/198 pass；`rag-real-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；默认 `-Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260627210458-9d0321`。
- 本轮真实 run 创建临时 smoke 数据和 ignored artifact，未操作远程 Docker，未走 `hk-ops`，未删除业务数据，未改数据库结构，未提交 artifact 原文，未 push。

## 2026-06-27 RAG / Memory 生产化路线定线

- 已将关键事实源从“求职级展示收口”调整为“生产化知识库 RAG + 会话记忆核心闭环”方向；RAG 和 Conversation Memory 成为主线，Agent 保持为工具调用与 Trace 辅助层。
- 已更新 `AGENTS.md`、`docs/README.md`、`STATE.md`、`CURRENT_TASK.md`、`ROADMAP_RAG.md` 和 `DECISIONS.md`，明确下一步代码任务为 `RAG Quality Upgrade v3: no-evidence threshold and grounded refusal`。
- 保留 v2 真实链路 smoke 的 `REVIEW` 事实：核心链路、chunk、MySQL / Qdrant 一致性、单文档 RAG、KB 两文档 RAG、Conversation Trace、权限隔离和 artifact 脱敏通过；populated-KB 无关问题仍返回 nearest evidence。
- 本轮只做文档定线，未启动 tunnel / backend / frontend，未创建业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact，未 push。

## 2026-06-27 RAG Quality Upgrade v2

- 新增 `scripts/smoke/rag-real-quality-smoke.ps1` 作为真实 embedding + Qdrant RAG 质量门禁入口，支持 `plan` / `dry-run` / `run`，默认 artifact 位于 ignored 的 `backend/target/rag-quality`。
- 增强 `scripts/smoke/cloud-quality-smoke.ps1`：新增 `SmokePrefix`，并加入 `noEvidenceThreshold` gate；无关 populated-KB query 若仍返回最近证据，标记 `REVIEW`。
- 已验证：`rag-real-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` 完成，marker 为 `docpilot-rag-real-quality-20260627195744-d5b6e2`，overallStatus 为 `REVIEW`。
- 本次 run 的 PASS 项包括 tunnel、backend health、frontend routes、注册、两文档上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、Conversation Trace、权限隔离和 artifact redaction；artifact 脱敏扫描无命中，artifact 不提交。
- REVIEW 原因：无关 populated-KB query 仍返回 `3` 个 retrieve hits / `3` 个 QA citations，说明后续需要调 `minSimilarityThreshold`、rerank 或 no-evidence 策略。
- 本轮发现 `cleanup-agent-processes.ps1` 对 Spring Boot argfile 和部分 Next dev 进程匹配不足，已补充 `DocPilotApplication`、`npm run dev`、`next*dev` 匹配；最终手动确认 8081 / 3007 / 13306 / 6333 均未监听。

## 2026-06-27 RAG Quality Upgrade v1

- 完成 RAG 质量门禁第一版小步落地：`RagDocumentRetrievalServiceImpl` 接入统一 `RagRetrievalProperties.minSimilarityThreshold`，补齐此前单文档 retrieval 未使用相似度阈值的问题，默认阈值仍为 `0.0`，不改变未配置场景行为。
- 增强 KnowledgeBase RAG 离线 eval：fixture 新增答案事实标记、禁止泄漏标记、最少 citation 数和多文档覆盖要求；metrics / artifact 新增 `answerHitRate`、`citationCountRate`、`multiDocumentCoverageRate`、`forbiddenAnswerLeakRate`，并继续只输出脱敏 summary。
- 当前生成的 `backend/target/rag-eval/knowledge-base-rag-eval-latest.json` 显示 caseCount `5`、`answerHitRate=1.0000`、`citationCountRate=1.0000`、`multiDocumentCoverageRate=1.0000`、`forbiddenAnswerLeakRate=0.0000`、`noEvidenceModelCallCount=0`；artifact 不提交。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalServiceImplTest,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagEvalFixtureTest" test` PASS，12 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，192 tests；`mvn -DskipTests compile` PASS；`mvn test -DskipITs` PASS，729 tests，0 failures，0 errors，1 skipped。
- 本轮未启动 tunnel，未调用真实 provider，未创建业务数据，未改数据库结构，未改前端；真实 embedding / rerank / answer provider 效果评测仍是后续任务。

## 2026-06-27 云端完整业务 Smoke 质量门禁 runner

- 已新增 `scripts/smoke/cloud-quality-smoke.ps1`，支持 `plan` / `dry-run` / `run` 三种执行模式，并统一使用 `smokeMarker` 串联临时用户、两份 txt 文档、KnowledgeBase、Conversation、问题文本和 ignored artifact。
- `run` 模式设计为完整质量门禁：tunnel、backend health、frontend route、注册 / 登录、两文档上传 / parse / indexing、MySQL chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、Conversation Trace、权限隔离负向检查、脱敏 artifact、清理和最终 `git status`。
- 已验证：Windows PowerShell 5 parser PASS；`powershell -File scripts/smoke/cloud-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run -ArtifactRoot backend/target/smoke -FrontendBaseUrl http://127.0.0.1:3007` PASS。
- 本次 run marker 为 `docpilot-cloud-quality-20260627022219-37efd4`，artifact 为 `backend/target/smoke/docpilot-cloud-quality-20260627022219-37efd4/artifact.json`；post-run redaction scan 为 `0` 命中。质量门禁覆盖两文档 chunk `3/3 + 3/3`、MySQL / Qdrant `3/3 + 3/3`、单文档 RAG `3` hits / `3` citations、KB RAG `6` hits / `6` citations、Conversation Trace `ragTriggered=true` / `ragRequired=true` / `evidenceCount=6`、四个权限隔离负向检查和七个前端 route。
- 执行过程未操作远程 Docker、未使用 `hk-ops`、未删除业务数据、未改数据库结构、未 push；本地 backend / frontend / tunnel 已在收尾时清理。为适配 Windows PowerShell，本轮追加修复 runner 的 backend 启动参数、顺序 indexing、chunk length 统计和 frontend `npm.cmd` 启动。

## 2026-06-26 交付切片本地提交

- 已按审查后的切片完成本地提交：`feat(conversation): add context memory workspace`、`feat(rag): add hybrid retrieval and rerank controls`、`feat(frontend): polish AI workspace presentation`、`docs(workflow): document cloud tunnel workflow`。
- 剩余展示口径和当前事实源文档将作为最终 `docs(showcase)` 切片提交；本轮仍未执行 `git push`。
- 最终验证通过：`git diff --check` 仅有 CRLF 提示；中文乱码扫描只命中 AGENTS 规则文字本身；配置敏感词扫描未输出任何真实值；`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 本轮未启动 tunnel，未操作远程服务器，未做新的云 MySQL / Qdrant runtime smoke；云链路仍以既有 smoke 记录为准。

## 2026-06-26 剩余切片归属确认

- 保持当前 `feat(conversation)` staged 第一包不变；未执行 `git commit` / `git push`。
- 已确认剩余四包归属：`feat(rag)` 覆盖 Hybrid / Rerank 后端、配置占位、RAG 测试和 `RAG_HYBRID_*`；`feat(frontend)` 覆盖全站产品化 UI、`globals.css` 和 KnowledgeBase 前端类型；`docs(workflow)` 覆盖 agent / tunnel / cleanup / ignore 规则；`docs(showcase)` 覆盖 README、showcase、STATE / CURRENT_TASK / PROGRESS_LOG 和 T013 设计参考资料。
- 已标注跨切片风险：`frontend/app/globals.css` 支撑 `/conversations` 样式但归入 `feat(frontend)`；`application.yml` 同时承担 `.env` import 上移和 RAG 配置，建议随 `feat(rag)` 提交、由 workflow docs 解释。
- 已验证：`git diff --cached --check` PASS；`git diff --check` PASS，仅有 CRLF 提示；乱码扫描只命中 AGENTS 规则文本和 archive 历史说明；敏感扫描确认真实配置仍只在 ignored `backend/.env`；`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 本轮仍未启动 tunnel 或操作远程服务器；全量后端测试中的 scheduled outbox MySQL tunnel refused 和 Surefire fork kill 日志只作为未连 runtime 环境边界记录，最终 Surefire / Maven 为 BUILD SUCCESS。

## 2026-06-26 交付切片执行

- 已执行交付切片第一步：用 `git reset` 清空原混合暂存区，未回滚工作树内容，未删除用户文件，未执行 `git commit` / `git push`。
- 已暂存第一包 `feat(conversation)`：Conversation Context / Agent Memory 后端包、`007_init_conversation_context.sql`、对应单测、`/conversations` 页面、`conversation-api.ts`、`memory-api.ts` 和必要 `ErrorCode`。
- RAG、全站前端 UI、workflow docs、showcase docs 仍保留为未暂存 / 未跟踪改动，等待后续按切片继续 review / stage；`frontend/app/globals.css` 因混合了全站和会话样式，暂未放入第一包。
- 已验证：`git diff --cached --check` 通过；中文 Markdown 乱码扫描只命中 AGENTS 规则文本和 archive 历史说明；`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 本轮未启动 tunnel，未执行云端 runtime smoke；全量后端测试中的 scheduled outbox 本机 MySQL tunnel 连接失败日志只作为未连 runtime 环境边界记录，不代表云链路验证。

## 2026-06-26 交付切片地图

- 已将当前混合工作区整理为建议提交切片：`feat(conversation)`、`feat(rag)`、`feat(frontend)`、`docs(workflow)`、`docs(showcase)`，用于后续人工 review / 分批提交；本轮未执行 `git add` / `git commit` / `git push`。
- 已审查暂存的 `docs/ai-dev/会话级上下文管理/` 设计文档：体量较大，适合作为 T013 设计参考资料保留，但不作为当前事实源；后续仍以 `STATE.md`、`CURRENT_TASK.md`、`PROGRESS_LOG.md` 和代码 / 测试为准。
- 已在 `docs/README.md` 补充 T013 设计资料目录说明，并在 `CURRENT_TASK.md` 写入提交切片、设计文档归属和剩余真实风险。

## 2026-06-26 交付前审查收口

- 继续收束近期新增代码审查结果：当前审查已覆盖 Hybrid / Rerank / Conversation / Memory / 前端 conversations 和 tunnel 文档入口，核心功能问题已修复，但工作区仍是混合 staged / unstaged / untracked 状态，尚未进入 commit-ready 切片。
- 已将 `.claude/` 和 `test-hybrid-rag.sh` 作为 local-only 产物加入 `.gitignore`；保留本地文件，不删除用户产物，不执行 `git add` / `git commit` / `git push`。
- 已收窄 `ConversationMessageServiceImpl.send` 的事务边界：上下文装配和模型调用在事务外执行，仅最终 conversation 行锁、连续写入 user / assistant message、更新时间包在事务内；trace best-effort 继续不影响用户回答。
- 已验证：`mvn "-Dtest=ConversationMessageServiceImplTest" test` PASS，5 tests，0 failures，0 errors；`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 仓库卫生检查：`git diff --check` 仅有 CRLF 工作区提示；乱码扫描仅命中 AGENTS 规则文本；脱敏敏感配置扫描确认真实密钥命中位于未跟踪 `backend/.env`，tracked 示例 / yml 为占位、默认本地值或环境变量引用；本轮未启动 SSH tunnel，未做云 MySQL / Qdrant runtime smoke。

## 2026-06-26 Tunnel 入口收口

- 确认 MySQL / Qdrant tunnel 启动说明原本已在 `backend/README.md`，但 `AGENTS.md` 未把它提升到 agent 首屏规则，后续 agent 容易漏。
- 已在 `AGENTS.md` 增加硬提醒：云 MySQL / Qdrant runtime smoke、后端 health 联调、真实 Qdrant indexing / retrieval 验证前，必须先运行 `scripts/dev/start-cloud-tunnels.ps1`；离线单测、compile、前端 lint/build 和未登录态 Playwright smoke 不要求 tunnel。
- 已整理 `backend/README.md` 的 tunnel 使用条件、云模式联调顺序、`mvn test -DskipITs` 中 scheduled outbox job 连接失败日志的解释边界。
- 已将 `scripts/dev/cleanup-agent-processes.ps1` 的端口检查列表补充 `3007`，覆盖本轮前端临时 smoke 端口。
- 本轮未启动 SSH tunnel、未操作远程服务器、未执行云 MySQL / Qdrant runtime smoke。

## 2026-06-26 收口验证与展示口径修正

- 修正 README / showcase 中过期的 Hybrid / Rerank 表述：当前口径为 KnowledgeBase RAG 默认关闭的可选增强，不写成生产默认能力或真实 provider smoke。
- 补齐 `backend/.env.example`、`backend/.env.demo.example`、`backend/.env.cloud.example` 中的 `APP_RAG_RETRIEVAL_*` 与 `APP_RAG_RERANK_*` 安全占位配置，默认保持关闭且不包含真实密钥。
- 已验证：`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- Playwright 已打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations`、`/agent` 桌面页面和移动端 `/`、`/conversations`；页面可渲染，console 仅见既有 favicon / dev Fast Refresh 类日志。全量后端测试结束阶段仍有 scheduled outbox job 访问本机 MySQL tunnel 被拒日志，但 Surefire 最终 BUILD SUCCESS。
- 本轮未操作远程服务器、未调用真实 provider、未提交代码；`.claude/` 和未归类临时产物仍不作为交付内容。

## 2026-06-26 Hybrid / Rerank / Conversation 修复

- 修复近期新增 Hybrid RAG 质量问题：keyword 检索按 `indexVersion` 过滤，fused hit 不再硬编码版本，keyword-only hit 保留 citation 元数据，最终候选再次经过 KnowledgeBase scope guard。
- 将 rerank 接入 KnowledgeBase RAG 主链路，默认仍关闭；provider 失败时 identity fallback，不泄露请求正文、密钥或 endpoint。
- 修复会话消息发送一致性：模型失败不落库，落库阶段通过 conversation 行锁连续写入 user / assistant 消息，避免 `MAX(sequence_no)+1` 并发撞号。
- 修复前端 memory type 的 `GOAL` / `TASK_GOAL` 不一致，并在 KnowledgeBase 页面补充 retrieval mode、rerank model 和 score breakdown 展示。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest,ConversationMessageServiceImplTest,ReciprocalRankFusionTest,BM25ScorerTest" test` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`npm run lint` PASS；`npm run build` PASS。

## 2026-06-14 前端 UI 文案成熟化

- 按用户反馈继续优化前端文案：页面文字从“求职项目自述 / 工程实现说明”收敛为“成熟 AI 产品界面表达”。
- 已使用 Gemini CLI headless 作为文案方向主导；Codex 负责拦截过度营销和夸大生产能力的表达，并完成代码落地。
- 首页、Dashboard、KnowledgeBase、Conversations、Agent、工具箱、登录、上传和文档详情页已替换高暴露文案：去掉页面上的“求职 / 面试 / MVP / 演示 / 生产级 / smoke”等内部口径，改为工作空间、引用来源、上下文溯源、会话记忆、工具链等更克制的产品表达。
- 本轮只改前端 UI 文案和 ai-dev 事实源，不改根 README、不改后端 API、不新增依赖、不操作远程环境。
- 已验证：`npm run lint` PASS，`npm run build` PASS；前端高暴露词扫描和中文乱码扫描均未命中。Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations`、`/agent`、`/agent/tools` PASS，并检查移动端 `/`、`/conversations` 无明显溢出；console 仅有既有 favicon 404 和一次 dev hot reload RSC fallback，页面已正常渲染。

## 2026-06-14 会话页聊天产品化重做

- 按用户反馈继续专项重做 `/conversations`，目标从“上下文系统控制台”切换为类似 GPT / DeepSeek 的聊天产品页。
- 已使用 Gemini CLI 正确的 `-p` headless 模式获取设计建议；Gemini 负责页面方向，Codex 负责代码落地、安全审查和验证。`frontend_showcase` 本轮不再使用。
- 会话页改为左侧深色历史栏、中间居中聊天流、底部悬浮 composer、右侧 Context Inspector 抽屉；Trace / Memory / Summary / KnowledgeBase evidence 保留为辅助信息，不再常驻抢占主聊天区。
- 新增非流式 pending 体验：发送后展示“检索知识库 / 召回记忆摘要 / 装配上下文预算 / 撰写回答”的前端等待状态，但不伪装成真实 SSE。
- 已验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/conversations` 桌面 / 移动端 PASS，未登录态为居中聊天产品入口，登录态布局烟测确认左侧会话栏、聊天主区、底部 composer 和右侧 Inspector 抽屉可渲染；console 仅有既有 `favicon.ico` 404。

## 2026-06-13 会话页核心展示精修

- 按用户反馈将 `/conversations` 作为前端核心页二次精修，并按已沉淀规则使用 Gemini CLI 做只读专项审阅；Gemini 识别出会话管理、对话、KnowledgeBase 绑定、Agent Memory、Summary / Context Trace 五个核心模块。
- 会话页首屏重构为 Agent Memory Workspace：新增 `Conversation -> Summary -> Memory -> KB Evidence -> Trace` 上下文装配流程，强调这是 DocPilot 前端最核心的 AI 产品页。
- 未登录态改为产品预览 + 登录 CTA：展示核心能力卡片和登录入口，但不暴露空工作台表单。
- 登录态工作区调整为三栏：左侧会话控制，中间消息主舞台，右侧可观测性控制台；助手消息直接展示 citation 数量和 Trace 入口，右侧优先展示 Context Trace，再展示 Summary / Memory / Suggestion。
- 已验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/conversations` 桌面 / 移动端 PASS，首页 / Dashboard / KnowledgeBase HTTP 200；dev server 重启后 console 无新增页面错误，仅有既有 favicon 类资源问题。

## 2026-06-13 Gemini CLI 协作规则沉淀

- 已将 Gemini CLI 协作方法沉淀为“短入口 + 详细规程”：`AGENTS.md` 写入前端协作入口，`docs/ai-dev/CONSTRAINTS.md` 写入启动检查、环境变量注入、stdin 长 prompt、auto-edit 失败降级、安全边界和验证归属。
- 本次规则明确：Gemini CLI 负责创意、方案和代码建议，Codex 负责安全审查、落地、验证和文档回写；Gemini 不接触 `.env`、secrets、远程服务器操作、数据库迁移或不相关文件。
- 本次经验记录：Gemini CLI 可作为协作参考，但 auto-edit 可能出现 503、`INVALID_ARGUMENT`、malformed tool call 或空响应；推荐默认采用 stdin + Codex 集成模式。

## 2026-06-13 前端 AI 产品感重点页精修

- 按“重点页面精修 + AI 产品感”方向二次收口前端展示，由 Codex 直接实现，不依赖 Gemini / frontend_showcase 自动改代码。
- 首页改为产品级演示入口：首屏展示 DocPilot、CTA 和 `Upload -> Parse -> Index -> Retrieve -> Answer -> Memory` 系统流程面板，并保留求职级 / 非生产级边界口径。
- Dashboard 改为 Demo Command Center：顶部加入演示链路步骤，状态卡统一为紧凑 KPI，未登录时不再显示“退出登录”按钮。
- KnowledgeBase 页面强化多文档 RAG 观测：未登录态收敛为登录说明卡，登录后结果区优先展示 provider、collection、evidence、citation、model call 和命中文档分布。
- Conversations 页面强化 Agent Memory 展示：未登录态只展示说明和登录入口，登录后显示 Bound KB / Summary / Memory 状态卡，并在 Trace 区说明只展示摘要级字段。
- 已验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations` PASS，桌面和移动端首页无明显重叠；console 仅有既有 `favicon.ico` 404。

## 2026-06-13 前端求职展示收口

- 首页改为 DocPilot 工程链路总览，突出上传 / 解析 / 索引、单文档 RAG + SSE、KnowledgeBase RAG、Agent Memory Context，并保留“求职级工程闭环、非生产级 SLA”的边界口径。
- Dashboard 增加“会话上下文”入口、推荐演示路径和面试展示检查点，方便按上传解析、单文档 RAG、多文档 KnowledgeBase、会话上下文、Agent / ToolCall 顺序演示。
- KnowledgeBase 页面增加 retrieval provider / collection、召回 / citation、answer provider / model、modelCallCount、no-evidence 和命中文档分布可观测卡片，不新增后端 API。
- Conversations 页面增强默认提问模板、非流式 MVP 边界说明、Memory / RAG / Trace 状态条和 Trace 上下文来源展示；仍不接管既有 Agent 主链路。
- 已验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations` PASS，桌面和移动端首页 / 会话页无明显空白或重叠；console 仅有既有 `favicon.ico` 404。
- 本轮未改根 README、未调用真实外部服务、未操作远程服务器、未读取或提交 `.env` / secrets / API key。

## 2026-06-13 Prometheus 9090 公网暴露修复

- 已由 `hk-ops` 按用户授权修复腾讯云服务器 Prometheus 9090 暴露：远程 `/opt/docpilot/docker-compose.yml` 中 `docpilot-prometheus` 端口从 `9090:9090` 收口为 `127.0.0.1:9090:9090`，并执行 `docker compose up -d docpilot-prometheus`。
- 已移除远程 `firewalld` public zone 的 `9090/tcp` 放行并 reload；Prometheus 容器仍保持 ready，本机 `127.0.0.1:9090/-/ready` 可用。
- 已验证远程监听只剩 `127.0.0.1:9090`，Docker 映射为 `127.0.0.1:9090->9090/tcp`，服务器访问公网 IP `62.234.3.22:9090` 超时；本机 `Test-NetConnection 62.234.3.22 -Port 9090` 返回 `False`。
- 远程有效备份为 `/opt/docpilot/docker-compose.yml.bak.20260613-112819`；另有一次服务名定位失败前创建的未修改备份 `/opt/docpilot/docker-compose.yml.bak.20260613-112738`。
- 本轮未修改腾讯云安全组；建议在腾讯云控制台重新检测，并删除或收口仍可能存在的云侧 9090 入站规则作为第二道防线。

## 2026-06-12 T013 Conversation Context Management / Agent Memory Mode

- 完成后端 MVP：新增 conversation / memory / ai.context package，落地会话、消息、摘要、用户记忆实体 / mapper / service / controller，并新增 `007_init_conversation_context.sql`。
- `ContextAssemblyService` 支持 `RECENT_TURNS` 与 `AGENT_MEMORY` 两种模式，按系统提示、长期记忆、会话摘要、最近轮次、KnowledgeBase evidence 拼接上下文，并输出 response-only trace。
- KnowledgeBase evidence 复用既有 `KnowledgeBaseRagRetrievalService` / `KnowledgeBaseScopeGuard`，未改现有单文档 RAG、多文档 KnowledgeBase RAG、ToolCallService、Function Calling adapter 或 Agent 主链路。
- 用户长期记忆仅支持手动创建 / 列表 / 软删除，带敏感内容拦截；未做自动抽取、后台自动摘要、前端页面或 SSE。
- 已验证：`mvn -DskipTests compile`；`mvn "-Dtest=*Context*,*Conversation*,*Memory*" test`（30/30 pass）；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test`（184/184 pass）；`mvn "-Dtest=*Agent*,*Tool*,*ToolCall*,OpenAi*" test`（186/186 pass）。
- 此前全量 `mvn test` 记录过 3 个失败：真实 provider harness、需要 Qdrant endpoint 的 manual probe、以及既有 DocumentChunk replacement 单测期望不一致；当时未把全量测试标为通过。
- 后续已处理默认离线全量测试：当前源码树和 git 索引未包含 `DocumentAgentRealProviderRuntimeHarnessTest` / `ManualKnowledgeBaseRagProbeTest`，clean 前失败来自旧 surefire report 残留；`DocumentChunkServiceImplTest` 已按短块合并式 chunking 策略更新核心断言。已验证 `mvn "-Dtest=DocumentChunkServiceImplTest" test` 8/8 pass、`mvn test` 683 tests / 0 failures / 0 errors / 1 skipped、`mvn clean test` 同样 683 tests / 0 failures / 0 errors / 1 skipped。
- 继续推进 Phase 2 / Phase 3 后端小闭环：新增 `tb_context_trace`、trace entity / mapper / service、`GET /api/conversations/{conversationId}/messages/{messageId}/trace`，消息发送后 best-effort 持久化摘要级 trace；trace 写入失败不影响回答，不保存完整 prompt 或 evidence 原文。
- 新增显式 `POST /api/conversations/{conversationId}/summary/refresh`，使用本地 extractive 摘要压缩最近消息；这是手动触发的安全起步版本，不调用真实外部模型，也不做后台自动摘要。
- 已验证追加实现：`mvn "-Dtest=*Context*,*Conversation*,*Memory*" test`（40/40 pass）；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test`（184/184 pass）；`mvn "-Dtest=*Agent*,*Tool*,*ToolCall*,OpenAi*" test`（186/186 pass）；`mvn test`（693 tests / 0 failures / 0 errors / 1 skipped）。
- 继续推进 Phase 2 长期记忆候选机制：新增 `SUGGESTED` / `IGNORED` 状态、规则式 `MemoryExtractionService`、候选列表 / 提取 / 接受 / 忽略 API；候选记忆默认不进入 prompt，用户接受后才转为 `ACTIVE`。
- 已验证候选记忆实现：`mvn "-Dtest=*Context*,*Conversation*,*Memory*" test`（49/49 pass）；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test`（184/184 pass）；`mvn "-Dtest=*Agent*,*Tool*,*ToolCall*,OpenAi*" test`（186/186 pass）；`mvn test`（702 tests / 0 failures / 0 errors / 1 skipped）。
- 本轮补充 Agent Memory + KnowledgeBase RAG 连接点防回归：新增 `KnowledgeBaseEvidenceContextBuilderTest`，覆盖中文必需触发、英文可选触发、no-evidence fallback、禁用 RAG 不检索和长 evidence 截断。已验证 `mvn "-Dtest=*Context*,*Conversation*,*Memory*" test`（54/54 pass）、`mvn "-Dtest=*Rag*,*KnowledgeBase*" test`（189/189 pass）、`mvn "-Dtest=*Agent*,*Tool*,*ToolCall*,OpenAi*" test`（186/186 pass）、`mvn test`（707 tests / 0 failures / 0 errors / 1 skipped；结束阶段有 scheduled task 访问云端 MySQL 的本机 SSH tunnel 入口被拒日志，说明当时 tunnel / 转发端口未连通，但构建通过）。
- 补齐前端会话工作台 MVP：新增 `frontend/lib/conversation-api.ts`、`frontend/lib/memory-api.ts`、`frontend/app/conversations/page.tsx`，并在顶部导航加入“会话”；页面支持会话创建、非流式消息、KnowledgeBase 绑定、summary / trace 查看、ACTIVE 记忆维护和候选记忆接受 / 忽略。
- 已验证前端：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `http://localhost:3007/conversations` PASS，未登录态页面正常渲染，console 仅有既有 `favicon.ico` 404。
- 2026-06-13 已按用户授权，通过当前本机 SSH tunnel 入口对云服务器 Docker MySQL 执行 `backend/src/main/resources/sql/007_init_conversation_context.sql`，并确认 `tb_conversation` / `tb_conversation_message` / `tb_conversation_summary` / `tb_context_trace` / `tb_user_memory` 五张表存在。
- 2026-06-13 完成迁移后登录态 runtime smoke：backend health `UP`，frontend `/conversations` HTTP 200；页面完成创建会话、发送消息、查看 Context Trace、刷新摘要、提取候选记忆、接受候选记忆，并通过第二轮消息验证 ACTIVE 记忆进入 Agent Memory 上下文（trace 显示 `Memory=1`、`summaryUsed=是`、最近消息 `2` 条 / `1` 轮、无截断 / fallback / model skipped）。KnowledgeBase 绑定 UI 已渲染，但本次 smoke 用户无可绑定知识库，未覆盖带真实 KB 文档 evidence 的浏览器端到端验证。
- 2026-06-13 继续完成 T013 KnowledgeBase-bound evidence 收口：新建临时用户、上传 txt、创建文档、解析到 `SUCCESS`、创建 KnowledgeBase 并添加文档；KnowledgeBase retrieval 命中 1 条 evidence。绑定该 KB 的 Agent Memory 会话发送知识库问题后，API trace 显示 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=1`、`documentHitCounts={93:1}`、citation `1`、无 fallback / model skipped。浏览器 `/conversations` 端到端复验中文“根据知识库”问题：助手回答引用 `t013-ui-kb-0613093939.txt`，页面 Trace 显示 `Evidence=1`、`RAG 触发=是`、`RAG 必需=是`、`No Evidence=否`，命中文档分布为 `#94: 1`。

## 2026-06-08 环境恢复诊断

- 尝试恢复本地后端通过 SSH tunnel 连接云端 MySQL / Qdrant：本地 `.env` 已指向 `127.0.0.1:13306` 和 `127.0.0.1:6333`，临时 tunnel TCP 可达。
- MySQL 登录探测仍失败：`docpilot_app` 经 tunnel 连接 `docpilot` 时被远程 MySQL 拒绝，来源显示为 Docker 网关侧地址，说明问题在远程 MySQL 用户认证 / host 授权，不是 Spring datasource 地址解析。
- `hk-ops` 停在安全点：确认 `docpilot-mysql` running/healthy、数据目录为 `/data/docpilot/mysql` bind mount，但备份未确认完成；未修改账号、权限、密码、compose/env 或容器状态。
- 本轮未启动后端做健康验证；已清理本轮新建的 `13306` / `6333` tunnel，保留用户已有 `23306` / `26379` tunnel。
- 后续已确认有效 MySQL datadir 备份 `/data/docpilot/backups/mysql-datadir-20260607-010918.tar`，基础 tar 完整性校验通过。
- 已修复远程 `docpilot_app` 认证 / 授权，并将 Docker MySQL host 端口收口到远程本机 `127.0.0.1:13306`；`docpilot-mysql` 保持 healthy，未修改业务表结构或业务数据。
- 本地重新建立 `13306` / `6333` SSH tunnel 后，MySQL CLI `SELECT 1` 成功，Qdrant `/collections` 可达；后端 local profile 启动成功，HikariPool 初始化完成，`/actuator/health` 返回 `UP`。
- 继续完成最小业务 smoke：临时用户注册、txt 上传、文档创建、RocketMQ parse create、解析 `SUCCESS`、RAG retrieve 命中 1 条、RAG QA 返回 1 条 citation 且命中本次 marker；记录 ID 为 user `88`、file `89`、document `87`、parseTask `83`。
- 新增 `scripts/dev/start-cloud-tunnels.ps1`，将 MySQL / Qdrant tunnel 启动与基础连通性检查固化；`backend/README.md` 已同步启动顺序和“不要公网直连 MySQL 13306”的排障说明。
- 定位前端多文档问答 `knowledge base RAG answer generation failed`：KnowledgeBase retrieve 成功，QA 失败耗时贴近 `AI_REAL_READ_TIMEOUT_MS=12000`，根因是真实回答模型生成超时被统一错误包装。已为 KnowledgeBase QA 增加 answer 生成失败兜底，保留 citations，并将本机 real model read timeout 调整为 `30000`；复验 KB `5` 问答 code `0`、citation `2`、modelCallCount `1`。

## 2026-06-06 KnowledgeBase RAG 质量修复

- 修复“总结资料集”类问题的后端质量瓶颈：chunking 改为合并 Markdown / 文本块后切分，默认窗口调整为 `800/120`，避免大量短泛 chunk。
- KnowledgeBase retrieval 增加候选池扩大和跨文档多样性选择，摘要意图优先覆盖各成员文档，并输出 `documentHitCounts`。
- KnowledgeBase QA 输出 `answerProvider`、`answerModel`、`modelCallCount`，summary prompt 增加整体总结、按文档标题总结和缺失证据说明；前端 API 类型已同步。
- 配置兼容 `RAG_VECTOR_PROVIDER` / `RAG_VECTOR_DIMENSION`；授权后已对 KnowledgeBase `3` 的文档 `83/84/85/86` 执行 rebuild / reindex，写入 collection `docpilot_kb_quality_20260606`。
- Reindex 验证：chunk / vector 数分别为 `35/35`、`18/18`、`10/10`、`16/16`；“总结资料集”检索 hit 数为 `6`，`documentHitCounts={83:2,84:1,85:1,86:2}`。
- 后续已将本地运行 `.env` 切到稳定 collection `docpilot_rag_v2` 并再次 rebuild / reindex；Spring local profile 实际读取到 `qdrant` / `docpilot_rag_v2` / `1024`，四文档 chunk / vector 和检索分布保持一致。临时 collection `docpilot_kb_quality_20260606` 不再作为运行目标。
- 已整理后端配置读取职责：`application.yml` 默认导入 `backend/.env` 并允许 `SPRING_CONFIG_IMPORT` 覆盖；`application-local.yml` 不再负责 `.env` 导入，只保留 local profile 差异。
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
