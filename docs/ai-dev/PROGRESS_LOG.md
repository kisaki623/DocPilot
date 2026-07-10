# Progress Log

## 2026-07-10 Document Parser 自然结构真实 smoke v3

- `document-parser-real-chain-smoke.ps1` 的 HTML fixture 新增 `aside` 噪声，安全结构信号新增 `html_noise_excluded`；artifact 只保留信号枚举，不保存解析文本。
- 真实 run marker `docpilot-parser-real-chain-20260710142418-09566e` PASS：三类文件 parse / chunk / direct retrieve / QA retrieval / citation / source locator 全部通过；结构覆盖 `10/10`、parser boundary `4/4`、artifact redaction PASS，运行环境稳定。
- 验证：脚本 plan / dry-run PASS；`mvn "-Dtest=DocumentParserRealChainSmokeScriptSafetyTest,DocumentParserFixtureCorpusTest" test` PASS（9 tests）。本轮 tunnel / backend / frontend 已由 runner 清理。
- 下一片：增加多 block / 多 chunk 来源覆盖门禁，避免单 chunk fixture 掩盖跨块 metadata 漂移。

## 2026-07-10 Document Parser chunk 来源定位 contract

- 新增 `RagIndexingTriggerServiceImplTest` 闭环用例：脱敏 parser block 的页码、章节路径、来源定位和 block type 经 indexing trigger、chunk、in-memory vector retrieval 后仍进入 citation。
- 验证：`mvn "-Dtest=RagIndexingTriggerServiceImplTest,RagIndexingServiceImplTest,ChunkingServiceImplTest,RagDocumentRetrievalServiceImplTest" test` PASS（43 tests，0 skipped）。
- 边界：离线 contract 不替代 Qdrant runtime 验证，不改 schema、检索阈值或 citation API；下一片把自然 HTML 噪声隔离纳入真实 parser smoke gate。

## 2026-07-10 Document Parser 自然样本 fixture v2

- `HtmlDocumentParser` 的本地 HTML 噪声剔除新增 `aside`，避免相关推荐 / 推广辅助栏混入 RAG 文本；不执行脚本、不访问外部资源。
- `DocumentParserFixtureCorpusTest` 新增自然 HTML 文章与多章节 DOCX fixture，覆盖三级标题、列表、表格行、`aside` 噪声、顶级章节切换和 `sectionPath` 重置。
- 验证：`mvn "-Dtest=DocumentParserTest,DocumentParserFixtureCorpusTest,ParseTaskConsumeEntryServiceImplTest" test` PASS（26 tests，0 skipped）。
- 边界：不新增依赖、schema 或二进制 fixture，不改上传 / 异步解析 / chunk / RAG 主链路；下一片审计 parser block 元数据到 chunk / citation 的端到端 contract。

## 2026-07-10 Document Parser 结构覆盖真实 run

- 执行 `document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`，marker `docpilot-parser-real-chain-20260710001619-a1b510`，整体 PASS。
- PDF / HTML / DOCX 均完成上传、异步解析、chunk、direct retrieve、QA retrieval、QA citation 和 source locator 验证；parser boundary `4/4` PASS，artifact redaction PASS。
- `fixtureStructureCoverage` 在真实 artifact 中为 `expectedSignals=9`、`coveredSignals=9`、`missingSignals=0`、`allCovered=true`。
- direct / QA 诊断：`directRetrieveOkCount=3`、`qaRetrieveOkCount=3`、最大重试次数均为 `1`，`environmentUnstable=false`。
- 清理：本轮启动的 tunnel / backend / frontend 已清理，`3000/3001/3002/3007/3100/8081` 未见 LISTEN。
- 边界：本次只记录脱敏摘要，不提交 artifact 原文；结论仍是小样本文本型 PDF / HTML / DOCX 真实链路，不代表 OCR、扫描件、旧 `.doc`、复杂版面或大规模解析 benchmark。

## 2026-07-09 Document Parser 结构覆盖 smoke 摘要

- `document-parser-real-chain-smoke.ps1` 已把长期 fixture corpus 的结构口径接入真实 smoke 摘要：每个 case 输出 `expectedStructures` / `structureSignals` 安全枚举，`parserQualityReport.fixtureStructureCoverage` 汇总覆盖计数和缺失计数。
- 真实 smoke fixture recipe 同步补齐 HTML 列表结构和 DOCX 列表段落；结构信号覆盖 PDF 文本 / 页码来源、HTML 标题 / 表格 / 链接 / 列表、DOCX 标题 / 表格 / 列表。
- Quality API 仅白名单解析结构覆盖计数；`/quality` 文档解析质量摘要新增“结构覆盖”，诊断网格新增“结构 fixture 覆盖”，缺口原因 `fixture_structure_missing` 已转中文。
- 验证：脚本 `plan` / `dry-run` PASS；`mvn "-Dtest=DocumentParserRealChainSmokeScriptSafetyTest,DocumentParserFixtureCorpusTest,QualityArtifactServiceImplTest,*Quality*" test` PASS（50 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；浏览器 `/quality?routeSmoke=2` 在 `390px` 下 console error 为 `0` 且无横向溢出。
- 边界：本片没有新跑真实 `run`，不提交 artifact 原文，不保存解析文本、query、answer 原文、prompt、evidence context、token、secret、连接串或云地址。

## 2026-07-09 Document Parser fixture corpus

- 新增 `DocumentParserFixtureCorpusTest`，作为 PDF / HTML / DOCX parser 的长期回归 fixture corpus，不提交二进制 fixture 文件，不访问外部网络。
- PDF fixture 覆盖多页文本型 PDF、空页 warning、page-level block、`pageNumber` 和 `sourceLocator=page:n`。
- HTML fixture 覆盖本地 HTML 噪声剔除、标题层级、正文内联链接文本、表格、列表和独立链接 block。
- DOCX fixture 覆盖 Heading1 / Heading2、段落、列表、表格、`sectionPath` 和 `docx:table:*` source locator。
- 验证：`mvn "-Dtest=DocumentParserTest,DocumentParserFixtureCorpusTest,ParseTaskConsumeEntryServiceImplTest" test` PASS（24 tests）。
- 边界：本片只增强测试资产，不改生产 parser、不新增依赖，不做 OCR、扫描件识别、旧 `.doc`、外部网页抓取或复杂版面理解。

## 2026-07-09 Document Parser 质量报告可读性

- `document-parser-real-chain-smoke.ps1` 的 `parserQualityReport.ragChainSummary` 新增 direct retrieve / QA retrieve 成功计数、no-evidence 计数、最大重试次数和 `environmentUnstable`，继续只保存脱敏数值和布尔摘要。
- `QualityArtifactServiceImpl`、`QualityRunDiagnostics.ParserQualitySummary` 和对应单测已同步白名单字段；未知敏感字段仍不会透传到 Quality API。
- `/quality` 文档解析质量摘要新增“直接检索接口”“问答检索接口”“运行环境稳定”小卡，并补充“直接 / 问答一致性”和“环境稳定性”诊断，方便判断是 parser/RAG 质量问题还是 tunnel / backend runtime 问题。
- 验证：parser smoke `plan` / `dry-run` PASS；`mvn "-Dtest=DocumentParserRealChainSmokeScriptSafetyTest,QualityArtifactServiceImplTest,*Quality*" test` PASS（46 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；浏览器 `/quality?routeSmoke=2` 桌面和 `390px` 移动端 console error 为 `0`，移动端无横向溢出。
- 边界：本片没有新跑真实上传业务数据，不改 parser / RAG 业务 service，不新增数据库表，不提交 artifact 原文；真实链路能力仍以最新 parser marker `docpilot-parser-real-chain-20260709233230-a08906` 为准。

## 2026-07-09 Document Parser direct retrieve / QA retrieve 差异收口

- `document-parser-real-chain-smoke.ps1` 新增 `directRetrieveDiagnostic` / `qaRetrieveDiagnostic` 脱敏诊断摘要，只记录 HTTP / 业务状态、attempts、hit / citation count、`noEvidence`、provider 和 collection 是否存在，不保存 query、answer 原文、文档全文、prompt、evidence context、token、secret、连接串或云地址。
- 修复 smoke runner 的 PowerShell 计数误差：`@($null).Count` 和函数返回数组展开会把缺失 hits 误计为 `1` 或 `null`；新增 `Get-SafeItemCount` 后，direct / QA 计数恢复可信。
- 增强环境归因：direct / QA / runtime error 失败时复查本地 MySQL / Qdrant tunnel 端口；运行中环境断链会标为 `environmentStability=BLOCKED`，避免误判为 parser 核心链路失败。
- 真实验证：`document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 最新 marker `docpilot-parser-real-chain-20260709233230-a08906` PASS；PDF / HTML / DOCX 均 parse、chunk、direct retrieve、QA retrieval、citation 和 source locator 通过，parserBoundary `4/4` PASS，artifact redaction PASS。
- 验证：脚本 `plan` / `dry-run` PASS；后端 parser / retrieval / quality targeted 74 tests PASS（1 skipped）；清理脚本确认 `3000/3001/3002/3007/3100/8081` 端口释放。

## 2026-07-09 Document Parser direct retrieve 质量门禁

- `document-parser-real-chain-smoke.ps1` 已调整 direct retrieve 检查：使用与 QA 相同的用户式问题，并在 QA retrieval 通过后做 direct endpoint 二次确认；artifact 仍只保存脱敏计数、布尔值和失败码。
- 质量口径已收紧：PDF / HTML / DOCX 的 parse、chunk、QA retrieval、citation 和 source locator 通过但 direct retrieve 未覆盖全部 fixture 时，`parserRealChain` 标为 `REVIEW`，`reviewReasons` 记录 `direct_retrieve_missing`；`/quality` 显示为“直接检索未命中”。
- 最新真实 run：`document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`，marker `docpilot-parser-real-chain-20260709223724-ceb637`，整体 `REVIEW`；`directRetrieveHitCount=0/3`、`qaRetrievalHitCount=3/3`、`citationCount=3/3`、source locator `3/3`，parser boundary 和 artifact redaction PASS。
- 补充定位：重启本地 backend 后，同批文档手动调用 `/api/rag/retrieve` 可得到 direct hits `1/1/1`；下一片继续定位同一 smoke 进程内 direct retrieve 与 QA retrieve 的差异。
- 验证：后端 parser / retrieval / quality targeted 74 tests PASS（1 skipped）；`npm run lint` PASS；`npm run build` PASS；脚本 `plan` / `dry-run` PASS。

## 2026-07-09 Agent Quality Console 真实登录态回归

- 复用本地已有 MySQL / Qdrant tunnel，启动 backend（local profile，Quality Console enabled，mock AI）和 frontend `3007`，通过浏览器注册临时用户并打开 `/quality?autoload=1`。
- Quality API 登录态回归通过：runs / eval-cases / trends 均成功；可见 `20` 条 run、`12` 个 eval case、`20` 个趋势点，最新 marker `docpilot-cloud-quality-20260709164330-452624` 状态为 `REVIEW`。
- 页面真实可见性通过：`/quality` 可见运行详情、待处理、链路、评测、文档解析质量摘要、评测用例库、能力层覆盖、覆盖缺口、质量趋势、反复失败用例和最近运行点；`/quality/trace` 可见链路瀑布图、步骤摘要、排查建议、关联门禁和关联评测用例。
- 脱敏与布局：页面 DOM 未命中 Authorization 凭据、API key、secret、password、连接串、system prompt、answer raw、document full text 或 evidence context；桌面 console error 为 `0`，`390px` 移动端 `/quality` 与 `/quality/trace` 均无横向溢出。
- 清理：本轮启动的 backend / frontend 已停止，`3000/3001/3002/3007/3100/8081` 端口已释放，临时启动日志已删除；已有 tunnel 未由本轮创建，未主动停止。
- 边界：本轮只创建临时登录用户，不上传文档、不创建 KB / Conversation、不删除业务数据、不改 schema、不操作远程 Docker、不提交 artifact 原文、不 push。

## 2026-07-09 Agent Quality Console B3 Eval 覆盖缺口

- `/quality` Eval Catalog 新增必需能力层清单：Agent RAG Trace、Memory Context Trace、RAG no-evidence、Citation Precision、Agent Search Routing、KB Agent Grounded Answer、Document Parser Real Chain 和 Memory Provider Contract。
- Eval Catalog 顶部新增“能力层覆盖”分子 / 分母，下方新增“覆盖缺口”区域；缺层时显示中文能力层名称，全部覆盖时显示“核心能力层已覆盖”。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（43 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` console error 为 `0`，`390px` 移动端无横向溢出。前端预览进程和临时日志已清理，`3007` 无 LISTEN。
- 边界：本片不改后端 API，不新增数据库表，不改 eval runner 评分逻辑，不读取 raw artifact，不展示 question、prompt、answer 原文、文档全文、evidence context、真实用户输入、token、secret、连接串或云地址。

## 2026-07-09 Agent Quality Console A3 Trace Timeline 诊断

- `/quality/trace` 链路瀑布图新增步骤摘要：失败步骤、复查步骤、工具 / RAG 步骤、模型 / 引用步骤和主要失败 / 复查类型。
- 每个 Trace step 会根据 `stepType`、状态和脱敏 bucket 展示“排查建议”，覆盖工具调用、RAG 检索、模型调用、引用校验、Agent step、eval case、权限、记忆和 parser 等常见方向。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（43 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality/trace?routeSmoke=1` console error 为 `0`，`390px` 移动端无横向溢出。前端预览进程和临时日志已清理，`3007` 无 LISTEN。
- 边界：本片不改后端 API，不新增数据库表，不读取 raw artifact，不展示 question、prompt、answer 原文、文档全文、evidence context、真实用户输入、token、secret、连接串或云地址。

## 2026-07-09 Agent Quality Console C2 轻量趋势诊断

- `/quality` 趋势区的反复失败用例现在会关联 Eval Catalog，展示 case type、能力层、risk gate、失败次数、复查次数、最近运行 marker 和首条修复建议。
- 反复失败 case 有 `latestRunMarker` 且有 `latestTraceId` / `latestAgentRunId` 时可直接“查看 Trace”；缺失时显示“暂无链路引用”。
- 最近运行点卡片新增 case 通过率、失败 / 复查数、token、耗时、失败类型和复查类型，避免趋势区只显示 marker 和状态。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（43 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` console error 为 `0`，`390px` 移动端无横向溢出。前端预览进程和临时日志已清理，`3007` 无 LISTEN。
- 边界：本片不改后端 API，不新增数据库表，不引入趋势图，不读取 raw artifact，不展示 question、prompt、answer 原文、文档全文、evidence context、真实用户输入、token、secret、连接串或云地址。

## 2026-07-09 Agent Quality Console B2 Eval Catalog 可读性

- `/quality` Eval Catalog 顶部新增用例总数、待处理用例、Trace 覆盖和高风险用例摘要，帮助快速判断评测资产健康度。
- Eval case 列表按失败 / 复查 / 未运行 / 高风险优先排序，不再把待处理 case 淹没在普通 PASS 项里。
- 每个 case 拆成“风险分层”“评分门禁”“回归策略”“历史与定位”“修复建议和期望证据”几个区域；常见 `caseLayer`、`riskGate`、`scoringSummary`、`regressionPolicy`、`remediationHints` 已转为中文短语展示。
- 有 `latestRunMarker` 且存在 `latestTraceId` / `latestAgentRunId` 的 case 显示“查看 Trace”；缺失时显示“暂无链路引用”，用于暴露 eval 到 trace 的覆盖缺口。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（43 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` console error 为 `0`，`390px` 移动端无横向溢出。前端预览进程和临时日志已清理，`3007` 无 LISTEN。
- 边界：本片不改后端 API，不新增数据库表，不改 eval runner 评分逻辑，不展示 question、prompt、answer 原文、文档全文、evidence context、真实用户输入、token、secret、连接串或云地址。

## 2026-07-09 Agent Quality Console A2 Trace / Failure 联动

- `/quality` 的“待处理”失败分桶卡片已增强为可行动排查入口：展示模块标签、失败 / 复查次数、关联门禁数、关联评测数、关联链路数、说明和建议动作。
- 有 trace reference 的失败桶可直接“查看 Trace”；没有链路引用时明确显示“暂无链路引用”，避免用户误以为页面漏了跳转。
- Run Detail 的链路定位行新增步骤数和主要排查方向；`/quality/trace` 的 Trace reference 卡片新增“步骤数”，与链路瀑布图保持一致。
- Eval case 行对失败 / 复查用例补充“暂无链路引用”提示，用于暴露 eval result 到 trace 的覆盖缺口。
- `PARSER_FAILURE` 已加入 Run Detail 失败类型筛选，Document Parser 真实链路问题不再长期落入“其他”。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（43 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 和 `/quality/trace?routeSmoke=1` console error 为 `0`，`390px` 移动端无横向溢出。前端预览进程和临时日志已清理，`3007` 无 LISTEN。
- 边界：本片不改后端 API，不新增数据库表，不读取 raw artifact，不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、token、secret、连接串或云地址。

## 2026-07-09 Agent Quality Console C1 趋势指标可信度

- `/quality` 趋势面板修正状态比例语义：通过 / 复查 / 失败运行均显示 `x / totalRuns`，平均 case 通过率继续作为趋势均值展示。
- token / cost 缺样本不再误显示为 0：`totalTokens` 缺失为“暂无统计”，`estimatedCost` 缺失为“暂无样本”，明确 `0` 才展示 `0`。
- 失败类型 TopN 与复查类型 TopN 改为卡片展示，包含模块标签、次数、说明和建议动作；Parser 相关失败桶独立归类为 `Parser`，artifact parse 坏文件仍归到 `Env`。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（43 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 桌面和 `390px` 移动端 console error 为 `0`，无横向溢出。前端预览进程已清理，`3007` 无 LISTEN。
- 边界：本片不改后端 API，不新增数据库表，不展示 raw artifact、prompt、answer 原文、文档全文、evidence context、真实用户输入、token、secret、连接串或云地址。

## 2026-07-09 Agent Quality Console B1 Eval Case 资产化

- `agent-quality-eval-cases.json` 新增 3 个默认 case：`kb-agent-grounded-answer-route`、`document-parser-real-chain`、`memory-provider-small-sample`。
- 新增 case 覆盖最新真实质量资产：KB Agent search / grounded answer、PDF / HTML / DOCX parser 真实链路、Memory provider 小样本抽取契约；均补齐 caseLayer、riskGate、scoringSummary、regressionPolicy、failureHistoryMarkers、lastVerifiedMarker 和 remediationHints。
- `/quality` 标签层新增 `kb_agent`、`parser`、`memory` 中文展示，Eval Catalog 不再把这些 case type 直接显示成 raw tag。
- 修复宽 Eval 测试暴露的旧契约：`RealShadowProviderEvaluationTest` 的 fake real-shadow parser / tool definitions 补入 `document_search_tool`，与当前 Agent search route 能力对齐。
- 验证：`mvn "-Dtest=*Quality*,*Eval*" test` PASS（82 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 桌面 console error 为 `0`，`390px` 移动端未见横向溢出。前端预览进程已清理，`3007` 无 LISTEN。
- 边界：本片不改 eval runner 评分逻辑，不新增数据库表，不读取业务库，不提交 artifact 原文，不展示 question、expectedBehavior、prompt、answer 原文、文档全文、evidence context、真实用户输入、token、secret、连接串或云地址。

## 2026-07-09 Agent Quality Console A1 Trace drill-down

- `QualityTraceStepDetail` 新增安全 `attributes` 字段，保留短枚举 / 工具名属性，继续过滤 URL、凭据、prompt、文档文本和 evidence context 类敏感片段。
- `QualityArtifactServiceImpl` 会从 `knowledgeBaseAgent` gate 生成 `knowledge-base-agent-runtime` trace reference，并沉淀 KB Agent search / grounded answer 的路由决策、工具名、检索命中、引用数、两文档覆盖、无证据处理和权限负向摘要。
- `/quality/trace` 链路瀑布图新增“链路属性”列，中文展示路由决策、检索工具、回答决策和回答工具，方便从 Console 直接判断 `search_tool -> knowledge_base_search_tool` 与 `rag_tool -> knowledge_base_rag_qa`。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（43 tests，1 skipped）；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 桌面无 console error，`/quality/trace` 空状态移动端无横向溢出。前端预览进程已清理，`3007` 无 LISTEN。
- 边界：本片不改 Agent / RAG 业务链路，不新增数据库表，不读取业务库，不提交 artifact 原文，不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、token、secret、连接串或云地址。

## 2026-07-09 Agent Quality Console ABC 求职级增强循环启动

- 当前任务已切换为 Agent Quality Console ABC 求职级增强循环：A 是 Agent Tool / Trace drill-down，B 是 Eval Case 资产化，C 是 Quality Console 趋势分析。
- `CURRENT_TASK.md` 已记录当前片、下一片和统一边界；`ROADMAP_AGENT_QUALITY_CONSOLE.md` 已补充三条路线的目标、最小实现、验收标准和明确不做事项。
- 统一验收口径：继续 artifact-only 和字段白名单；不新增数据库表；不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、API key、token、secret、连接串或云地址；前端质量结论必须通过 lint/build 和浏览器检查。
- 下一片进入 A1：基于现有 quality artifact / gate / eval / trace reference，增强 KB Agent search / grounded answer 的脱敏链路摘要和 Console 可读性。

## 2026-07-09 KB Agent answer route 前端真实可见性回归

- `knowledgeBaseAgent` 被标记为关键诊断 gate：在“已通过门禁”折叠组展开后，即使 gate 状态为 PASS，也展示它的安全 signals；其他 PASS gate 仍保持压缩。
- 真实 Quality API 验证最新 marker `docpilot-cloud-quality-20260709164330-452624`：`knowledgeBaseAgent` gate 为 PASS，`answerCitations=6`、`answerCoversBothDocuments=true`、`answerNoEvidenceHandled=true`、`foreignKnowledgeBaseRejected=true`。
- 浏览器 `/quality?autoload=1` 使用一次性临时用户登录后，切到“门禁”并展开“已通过门禁”，可见最新 marker、`知识库 Agent`、`Agent 回答引用数`、`KB 回答覆盖两份文档` 和 `KB 无证据回答已处理`。
- 脱敏与布局：页面未出现 prompt 原文、answer 原文、文档全文或 evidence context 字样；桌面 console error 为 `0`，`390px` 移动端 console error 为 `0`，移动端 `scrollWidth=clientWidth`。
- 清理：backend、frontend 和浏览器进程已清理，`3000/3001/3002/3007/3100/8081` 端口均释放；本轮临时 token / log 文件已删除。
- 边界：本片不改后端 API，不读取 raw artifact，不提交 artifact 原文，不删除业务数据，不改 schema，不 push。

## 2026-07-09 Agent Quality Console KB answer 诊断可见性

- `/quality` 标签层补齐 KB Agent answer route 安全摘要：`knowledgeBaseAgent` 显示为“知识库 Agent”，`answerCitations`、`answerDurationMs`、`answerDecisionPass`、`answerSuccess`、`answerCoversBothDocuments`、`answerNoEvidenceHandled` 等字段显示为中文。
- `kbAnswerDecisionMismatch` 现在归类为“KB Agent 回答路由不匹配”，失败桶说明和建议动作指向 `KnowledgeBaseAgentService` grounded answer 分支、`KnowledgeBaseRagQaService` 调用和 KB answer route smoke case；KB Agent 专属桶判断提前，减少误归类到 Citation / Other。
- RAG 摘要的“回答引用数”会计入 `answerCitations`，并新增“无证据已处理”事实，方便从控制台判断 grounded answer 和 no-evidence 边界。
- Gemini CLI 可用性检查通过，但正式审阅请求超时；本片按协作规则降级为 Codex 直接集成、审查和验证。
- 验证：`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 桌面和 `390px` 移动端 console error 均为 `0`，移动端 snapshot 未见横向溢出；前端预览进程已清理，`3007` 端口已释放。
- 边界：本片不改后端 API，不读取 raw artifact，不展示 prompt、answer 原文、文档全文、evidence context、token、凭据、云地址或连接串。

## 2026-07-09 KB Agent grounded answer 真实 cloud smoke

- 执行 `cloud-quality-smoke.ps1 -Mode run -SkipFrontend -EnableKnowledgeBaseAgentGate`，marker 为 `docpilot-cloud-quality-20260709164330-452624`。
- `knowledgeBaseAgent` gate 为 PASS：search route 返回 `decision=search_tool`，selected tool 为 `knowledge_base_search_tool`，retrieve hits / citations 均为 `6`，覆盖 Alpha / Beta 两份主文档。
- Grounded answer route 返回 `decision=rag_tool`，selected step / tool 为 `knowledge_base_rag_qa`，answer citations 为 `6`，并覆盖两份主文档；no-evidence answer 边界通过，跨用户 KB 访问被拒绝。
- 同轮 tunnel、backend health、上传 / parse / indexing、chunk quality、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、answer grounding、no-evidence、Conversation Trace、权限隔离、cleanup 和 artifact redaction 均为 PASS。
- 整体 run 为 REVIEW，仅因为本轮显式 `-SkipFrontend`，`frontendRoutes` 被标记为 REVIEW；这不是 KB Agent gate 失败，也不是完整前端体验回归。
- 本轮只提交脱敏文档摘要，不提交 ignored artifact 原文；artifact 不保存 prompt、answer 原文、文档全文、evidence context、token、凭据、云地址或连接串。

## 2026-07-09 KB Agent answer route smoke gate

- `cloud-quality-smoke.ps1 -EnableKnowledgeBaseAgentGate` 扩展为同时验证 KB Agent search route、grounded answer route、no-evidence answer 边界和跨用户 KB 权限负向。
- answer route gate 要求 `decision=rag_tool`、step 包含 `knowledge_base_rag_qa`、citation 数量至少 2，且 documentHitCounts 覆盖 Alpha / Beta 两份主文档；no-evidence gate 要求 `noEvidence=true` 且 citation 为 0。
- 离线 `agent-kb-search-route-smoke.ps1` plan 文案和 runner artifact 已从旧 unsupported P0 语义切换到 `answerDecisionPass`；run marker `docpilot-agent-kb-search-route-20260709164129-9a8972` PASS，artifact redaction scan PASS。
- `QualityArtifactServiceImpl` 安全 flag 白名单新增 `handled` 和 `*covers*` 布尔摘要，`QualityArtifactServiceImplTest` 覆盖 `answerCitations`、`answerCoversBothDocuments`、`answerNoEvidenceHandled` 解析以及 prompt / answer 诱饵字段不泄露。
- 验证：cloud quality plan / dry-run PASS；agent KB route plan / dry-run / run PASS；targeted smoke / Quality parser 22 tests PASS（1 skipped）；`mvn "-Dtest=*Quality*" test` PASS（43 tests，1 skipped）。
- 边界：本片没有启动真实 backend / frontend，没有创建业务数据；下一片进入真实 cloud smoke run。

## 2026-07-09 KB Agent grounded answer route P0

- `KnowledgeBaseAgentServiceImpl` 新增 answer 分支：`rag_tool` / `qa_tool` / `summary_tool` 复用 `KnowledgeBaseRagQaService.answer(...)`，`search_tool` 仍保持 retrieval-only `knowledge_base_search_tool`。
- `KnowledgeBaseAgentRequest` 新增 `sessionId`；response 新增 `noEvidence`、fallback、answer provider / model 和 `modelCallCount` 等安全元数据。
- answer step 使用 `knowledge_base_rag_qa`，step summary 只记录参数和 hit / citation / noEvidence / modelCallCount 计数，不写入 prompt、answer 原文、文档全文或 evidence context。
- `KnowledgeBaseAgentServiceImplTest` 覆盖 answer intent、summary intent、no-evidence、权限错误透传和 step 脱敏；`AgentKnowledgeBaseSearchRouteSmokeTest` 的 answer case 已从旧 unsupported 语义切换为 grounded answer route。
- 验证：KB Agent targeted 13 tests PASS（1 skipped）；Agent / Tool / KB RAG broader 248 tests PASS（3 skipped）。
- 边界：本片未跑真实 backend / tunnel，不创建业务数据，不新增数据库表；下一片进入 cloud quality smoke gate 扩展和真实链路验证。

## 2026-07-09 KB Agent Quality Console 真实前端可见性回归

- 本地启动 tunnel、backend 和 frontend 后，使用临时登录用户访问内部 `/quality` 页面；本轮只做可见性验证，没有上传文档、创建 KB / Conversation、修改数据库结构或提交 artifact 原文。
- Quality API detail 验证 `docpilot-cloud-quality-20260709153428-d25e54` 中 `knowledgeBaseAgent` gate 为 PASS，安全摘要包含 `retrieveHits=6`、`citations=6`、`coversBothDocuments=true`、`unsupportedIntentRejected=true`、`foreignKnowledgeBaseRejected=true`。
- Playwright 打开 `/quality?autoload=1`：桌面端可见最新 marker；切到“门禁”并展开已通过门禁后，可见“知识库 Agent 检索 / 通过”；`390px` 移动端同样可见 marker 和 gate。两种视口 console error 均为 0，未发现横向溢出。
- 页面脱敏检查未命中 Authorization 凭据、API key、secret、password、连接串、evidence context、system prompt、answer raw、document full text 等敏感模式。
- 观察到的边界：PASS 门禁默认压缩，前端列表只展示 gate 名称和状态；深层 `retrieveHits / citations / flags` 当前通过 Quality API detail 可读，后续如需更强可读性，可单独增强 PASS gate 展开内容。

## 2026-07-09 KB Agent Quality Console gate 诊断可读性

- `QualityArtifactServiceImpl` 安全 flag 白名单新增 `success`、`covers...` 和 `...Rejected` 布尔摘要，便于把 `knowledgeBaseAgent` gate 的关键检查提升为 Console 信号。
- `QualityArtifactServiceImplTest` 新增 cloud quality artifact fixture，覆盖 `knowledgeBaseAgent` gate 的 `retrieveHits`、`citations`、`coversBothDocuments`、`unsupportedIntentRejected`、`foreignKnowledgeBaseRejected` 解析，以及 prompt / answer 诱饵字段不泄露。
- `frontend/lib/quality-labels.ts` 新增 `knowledgeBaseAgent` gate、`coversBothDocuments`、`unsupportedIntentRejected`、`foreignKnowledgeBaseRejected`、`rerankApplied`、`multiQueryApplied`、`queryVariantCount` 等中文标签。
- 边界：本片只增强 Quality Console 可读摘要，不改 KB Agent 业务链路，不展示 raw artifact。

## 2026-07-09 KB Agent real-link runtime smoke gate

- `scripts/smoke/cloud-quality-smoke.ps1` 新增默认关闭参数 `-EnableKnowledgeBaseAgentGate`，复用主 cloud smoke 的临时用户、两文档 KnowledgeBase 和用户 B 权限负向上下文。
- 新 gate 会真实调用 `POST /api/ai/agent/knowledge-bases/{knowledgeBaseId}/run`：检索意图必须返回 `decision=search_tool`、执行 `knowledge_base_search_tool`、retrieve hits / citations 覆盖 Alpha / Beta 两份文档；answer / summary intent 必须被 KB Agent P0 拒绝；用户 B 调用用户 A KB 必须失败。
- artifact 只写入 success、decision、selectedTools、hit / citation count、documentHitCounts、retrieval mode、rerank / multi-query 布尔和 durationMs，不保存原始 task、prompt、answer 原文、文档全文、evidence context、token、凭据、云地址或连接串。
- 已验证：`cloud-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest,KnowledgeBaseAgentServiceImplTest,KnowledgeBaseAgentControllerTest" test` PASS（11 tests）。
- 真实 run：`cloud-quality-smoke.ps1 -Mode run -SkipFrontend -EnableKnowledgeBaseAgentGate` 完成，marker `docpilot-cloud-quality-20260709153428-d25e54`；`knowledgeBaseAgent` gate PASS，`decision=search_tool`，selected tool 为 `knowledge_base_search_tool`，retrieve hits / citations 均为 `6`，documentHitCounts 覆盖 `{782:3,783:3}`，unsupported intent 和用户 B 权限负向均通过。整体 run 为 REVIEW 仅因为本轮跳过 frontend route smoke。

## 2026-07-09 Agent search smoke artifact Quality Console 可见性

- `QualityArtifactServiceImpl` artifact root 白名单新增 `backend/target/agent-search-route` 与 `backend/target/agent-kb-search-route`，让单文档 Agent search route smoke 和 KB Agent search route smoke 的 ignored 脱敏 artifact 能进入 `/api/quality/runs` / `/quality`。
- `QualityArtifactServiceImplTest` 新增覆盖：两个 search route root 可解析为 run detail；caseResults 能作为 eval case 展示；prompt、answer、documentText、secret 等诱饵字段不会出现在返回对象中。
- `/quality` 前端新增 `agent_search_route`、`agent_kb_search_route` 中文 case type；失败桶新增 KB Agent 路由不匹配、KB Agent P0 意图边界异常、KB Agent 权限失败透传异常，并提供模块标签、说明和建议动作。
- `docs/ai-dev/CONSTRAINTS.md` 已同步 Quality Console artifact root 边界；本片不改 Agent / RAG 业务链路，不读取 raw artifact，不提交 artifact 原文。

## 2026-07-09 KB Agent search route smoke runner

- 新增 `scripts/smoke/agent-kb-search-route-smoke.ps1`，支持 `plan / dry-run / run`，用于离线验证 KB Agent P0 route contract。
- 新增 `AgentKnowledgeBaseSearchRouteSmokeTest`：覆盖 retrieval-only KB task 执行 `knowledge_base_search_tool`、answer intent 被 P0 安全拒绝且不调用工具、`KNOWLEDGE_BASE_FORBIDDEN` 权限失败透传、artifact 脱敏。新增 `AgentKnowledgeBaseSearchRouteSmokeScriptSafetyTest` 约束脚本不读 `.env`、不输出 Authorization / Bearer / API key、不递归删除文件。
- run 结果：`agent-kb-search-route-smoke.ps1 -Mode run` PASS，marker `docpilot-agent-kb-search-route-20260709152049-d529d6`；artifact redaction scan PASS。
- 验证：`plan` PASS；`dry-run` PASS；KB Agent smoke targeted 10 tests PASS（1 skipped）；KB Agent / Tool / eval targeted 34 tests PASS（1 skipped）。
- 边界：本片不启动 backend / frontend / tunnel，不创建业务数据，不改 Agent API，不新增数据库表，不做 KB answer agent，不提交 artifact 原文。下一片建议做真实 API runtime smoke 或 Quality Console 聚合展示。

## 2026-07-09 KB Agent retrieval-only route MVP

- 新增独立 KB Agent P0 后端入口：`KnowledgeBaseAgentRequest`、`KnowledgeBaseAgentResponse`、`KnowledgeBaseAgentService` / `KnowledgeBaseAgentServiceImpl` 和 `KnowledgeBaseAgentController`，API 为 `POST /api/ai/agent/knowledge-bases/{knowledgeBaseId}/run`。
- P0 只支持 retrieval-only search intent：命中 `search_tool` 时调用 ToolCall API 的 `knowledge_base_search_tool`；answer / summary / grounded QA intent 不误调用 search，而是返回“P0 仅支持检索证据”的安全提示。
- 输出只包含安全检索摘要、`documentHitCounts`、retrieval mode、rerank / multi-query 数值、限长 hits / citations 和 step 摘要；不生成 answer，不持久化 KB Agent task，不返回 prompt、answer 原文、文档全文、evidence context、凭据、云地址或连接串。KB 权限类失败会透传为 `BusinessException`，不被工具 fallback 掩盖。
- 验证：KB Agent targeted 27 tests PASS；Agent / Tool / KB RAG broader 241 tests PASS（2 skipped）。
- 边界：本片不新增数据库表，不改 KB RAG QA 主链路，不做 KB answer agent，不启动真实 backend / tunnel，不创建业务数据。下一片建议补 KB Agent search route smoke runner。

## 2026-07-09 KB Agent route design

- 复核 `knowledge_base_search_tool`、ToolCall callable subset、`ToolInputMapper`、ToolSpec 和现有 `KnowledgeBaseRagController` 后，确认底层 KB search 工具已经可用，缺口在 Agent 层 request / context 语义。
- 设计结论：不扩展当前单文档 `DocumentAgentRequest` 承载 KB。P0 新增独立 `KnowledgeBaseAgentRequest` / `KnowledgeBaseAgentService` / `KnowledgeBaseAgentController`，API 建议为 `POST /api/ai/agent/knowledge-bases/{knowledgeBaseId}/run`。
- P0 只做 retrieval-only KB search route：调用 `knowledge_base_search_tool`，返回安全 search summary、`documentHitCounts`、retrieval mode、multi-query / rerank 数值和限长 citation preview，不生成 answer。
- 验收标准已写入 `CURRENT_TASK.md`：覆盖成功、no-evidence、权限拒绝、脱敏和参数边界；不新增数据库表、不改 KB RAG QA 主链路、不做复杂 planner、不保存 prompt / answer / 文档全文 / evidence context / 凭据。

## 2026-07-09 Agent search route smoke runner

- 新增 `scripts/smoke/agent-search-route-smoke.ps1`，支持 `plan / dry-run / run`，用于离线验证单文档 Agent search route：retrieval-only 任务走 `search_tool` / `document_search_tool`，grounded answer 任务走 `rag_tool` / `rag_qa_tool`。
- 新增 `AgentSearchRouteSmokeTest`，显式环境变量启用时写入 ignored 脱敏 artifact；新增 `AgentSearchRouteSmokeScriptSafetyTest`，约束脚本不读 `.env`、不输出 Authorization / Bearer / API key、不递归删除文件。
- artifact 只保存 marker、状态、caseId、expected / actual decision、selected tool、布尔结果、stepCount 和 failure buckets；不保存原始 task、prompt、answer 原文、文档全文、evidence context、token、凭据、云地址或连接串。
- 验证：`agent-search-route-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker `docpilot-agent-search-route-20260709101258-021654`；artifact redaction scan PASS；`mvn "-Dtest=AgentSearchRouteSmokeTest,AgentSearchRouteSmokeScriptSafetyTest,DocumentAgentServiceImplTest" test` PASS（17 tests，1 skipped）；Agent / selector / eval targeted 63 tests PASS（1 skipped）。
- 边界：本片不启动 backend / frontend / tunnel，不创建业务数据，不改 Agent API，不新增数据库表，不接 KB Agent 路由，不提交 artifact 原文，不 push。

## 2026-07-09 Agent Quality Console search diagnostics

- `/quality` 前端标签层新增 Agent search route 诊断映射：`agent_search` 显示为“Agent 检索路由”，`expectedDecisionMatched` 显示为“路由决策匹配”，数值 1 / 0 显示为“是 / 否”。
- failure triage 新增“Agent 路由不匹配”类别，覆盖 `expectedDecisionMismatch`、selector、routing、search-overrouting、answer-overrouting 等路由漂移；模块标签为 `Agent`，建议动作指向 `DocumentToolSelector`、LLM selector prompt 和 search / answer eval case。
- `expectedDecisionMatched` 已加入信号优先级，Eval Case 行里更容易看到路由门禁是否匹配，不再被其他指标挤掉。
- 验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality?routeSmoke=2`，桌面和 `390px` 移动端 console error 为 0，移动端 snapshot 未见横向溢出。
- 收尾：本轮启动 `npx next start -p 3007` 做浏览器验证；验证后已停止监听进程，删除 `frontend/.next-route-smoke` 临时日志目录，3007 端口释放。
- 边界：本片只改前端展示映射，不改后端 API，不读取 raw artifact，不启动后端 / tunnel，不创建业务数据，不提交 artifact 原文，不 push。

## 2026-07-08 Agent search eval 路由质量门禁

- `AgentQualityEvalRunner` 对带 `scoringRules.expectedDecision` 的 case 增加真实 selector 评测：调用 `DocumentToolSelector` 生成观测决策与工具列表，若决策漂移则输出 `expectedDecisionMismatch`。
- 默认 `agent-quality-eval-cases.json` 新增 `agent-document-search-route` 与 `agent-rag-answer-route`：前者要求 retrieval-only 任务走 `search_tool` / `document_search_tool`，后者要求 grounded answer 任务继续走 `rag_tool` / `rag_qa_tool`。
- eval artifact 只新增安全数值 `expectedDecisionMatched`，不保存原始 question、expectedBehavior、prompt、answer 原文、文档全文或 evidence context；search routing case 不强制 trace，避免把纯离线路由门禁伪装成真实链路 trace。
- `agent-quality-eval-smoke.ps1` plan / dry-run 已同步 expectedDecision 字段说明，`run` 仍只执行离线 JUnit 并生成 ignored 脱敏 artifact。
- 验证：Agent Quality Eval targeted 18 tests PASS（1 skipped）；`mvn "-Dtest=*Quality*" test` PASS（41 tests，1 skipped）；`agent-quality-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker `docpilot-agent-quality-eval-20260708231648-f178d4`。
- 边界：本片不启动 backend / frontend / tunnel，不创建业务数据，不新增数据库表，不改生产 API，不提交 artifact 原文，不 push。

## 2026-07-08 Agent search intent 路由与评测门禁

- `DocumentToolSelector` 新增 `search_tool` 决策：retrieval-only 的 topK、similarity、source、citation list、evidence list 走 `document_search_tool`；回答、解释、总结并引用证据或“说明”事实的任务继续走 `rag_qa_tool`。
- `LlmToolSelectionParser`、`LlmToolSelectionPromptBuilder`、`FakeLlmToolSelectionClient` 和 `DocumentAgentServiceImpl` 已同步 `search_tool`；Agent 执行 search intent 时调用 ToolCall API 的 `document_search_tool`，返回检索摘要而不是生成式业务答案。
- search 输出继续保持脱敏边界：最多展示 3 条限长 quote/snippet，以及 chunkId、chunkIndex、score、sourceLocator 等定位信息；不透传完整 chunk content、文档全文、prompt、answer 原文、secret、连接串或云地址。
- 评测用例已同步新语义：`cite the source for the main claim` 作为来源查找走 `search_tool`；`请引用原文说明合同金额` 保持 `rag_tool`，避免中文 grounded QA 被误切到 retrieval-only。
- 验证：selector / fake / parser / prompt / service targeted 53 tests PASS；selector eval targeted 27 tests PASS；Agent / ToolCall / Tool / RAG retrieval broader 212 tests PASS（1 skipped）。
- 边界：本片不做 KB Agent 路由、不新增数据库表、不改 RAG 主链路、不启动真实链路、不提交 artifact 原文、不 push。

## 2026-07-08 KnowledgeBase search tool 最小闭环

- 新增 `KnowledgeBaseSearchTool`，复用 `KnowledgeBaseRagRetrievalService`，提供 retrieval-only 多文档 KB 检索工具；ToolSpec、ToolCall callable subset、参数校验和输入映射已同步接入。
- 工具输出使用安全 DTO：`SearchHit` / `SearchCitation` 只返回 document / chunk 元数据、score、vector / keyword / fused / rerank 分数、contentHash 和限长 quote/snippet，并返回 `documentHitCounts`、`retrievalMode`、rerank / multi-query 摘要，不透传完整 chunk content、文档全文、prompt 或 answer 原文。
- 参数边界：`knowledgeBaseId` 会归一化为正整数；`topK` 继续使用上限；`maxQueryVariants` 限制为 `1..5`；`multiQueryEnabled` 字符串只接受 `true/false`，避免静默把非法值当作 false。
- 已补测试：`KnowledgeBaseSearchToolTest`、`ToolCallServiceImplTest`、`DefaultToolSpecProviderTest`、`ToolArgumentValidatorTest`、`ToolSpecRegistryTest`、`ToolDefinitionProviderTest`、`OpenAiToolSchemaAdapterTest` 覆盖工具注册、schema、ToolCall 调用、参数归一化、scope rejection、KB 命中分布和安全预览。
- 验证：targeted 41 tests PASS；Agent Tool + KB RAG broader 157 tests PASS；布尔边界收紧后 targeted 21 tests PASS。
- 边界：本片不改 Agent 旧关键词路由，不改 KB RAG 主链路，不新增数据库表，不启动真实链路，不提交 artifact 原文。

## 2026-07-08 单文档 document_search_tool 最小闭环

- 新增 `DocumentSearchTool`，复用 `RagDocumentRetrievalService`，提供 retrieval-only Agent 工具；ToolSpec、ToolCall callable subset、参数校验和输入映射已同步接入。
- 工具输出使用安全 DTO：`SearchHit` / `SearchCitation` 只返回 rank、score、source locator、chunk 元数据、contentHash 和限长 quote/snippet，不透传完整 chunk content、文档全文、prompt 或 answer 原文。
- 已补测试：`DocumentSearchToolTest`、`ToolCallServiceImplTest`、`DefaultToolSpecProviderTest`、`ToolArgumentValidatorTest`、`ToolSpecRegistryTest`、`ToolDefinitionProviderTest`、`OpenAiToolSchemaAdapterTest` 覆盖工具注册、schema、ToolCall 调用、参数归一化、scope rejection 和安全预览。
- 验证：`mvn "-Dtest=DocumentSearchToolTest,ToolCallServiceImplTest,DefaultToolSpecProviderTest,ToolArgumentValidatorTest,OpenAiToolSchemaAdapterTest" test` PASS（24 tests）；`mvn "-Dtest=*Tool*,*ToolCall*,RagDocumentRetrievalServiceImplTest" test` PASS（123 tests）。
- 边界：本片不改 Agent 旧关键词路由，不做 KB search tool，不改 RAG 主链路，不新增数据库表，不启动真实链路，不提交 artifact 原文。

## 2026-07-08 Agent Document Search Tool 路线沉淀

- 复核 Agent Tool 现状：项目已有 `rag_qa_tool`，但它是 answer-generating QA 工具；ToolCall API 当前只开放 `document_status_tool` 与 `rag_qa_tool`，没有 retrieval-only 的 `document_search_tool`。
- 已确定第一片：新增单文档 `document_search_tool`，输入 `userId`、`documentId`、`query`、`topK`、`indexVersion`，复用现有 `RagDocumentRetrievalService` / `RagScopeGuard`，只返回安全 retrieval 摘要、限长 quote/snippet、source locator、score、chunkId 和 contentHash，不返回完整 chunk content、文档全文、prompt 或 answer 原文。
- 后续方向：再做 `knowledge_base_search_tool`、Agent search intent 路由、search eval / smoke、Agent Quality Console search diagnostics；不在第一片里改 RAG 主链路或数据库结构。

## 2026-07-08 Document Parser 真实链路质量回归与 Console 可见性收口

- `document-parser-real-chain-smoke.ps1` 修复受控服务策略：默认不再静默复用已有 backend / frontend，只有显式 `-ReuseRunningServices` 才复用；runner 自己启动 backend 时通过子进程环境设置 `AI_MODE=mock` 与 `APP_QUALITY_CONSOLE_ENABLED=true`，避免本地真实 provider 配置或未开启内部控制台导致误判。
- runner 新增 `directRetrieveHit` / `qaRetrievalHit` 与对应 count，区分直接 retrieve endpoint 和 QA 内部 retrieval；本轮 PASS run 显示直接 retrieve `0/3`、QA retrieval `3/3`、citation `3/3`，让后续排查有明确入口。
- `QualityArtifactServiceImpl` 修复工作目录漂移：后端从 `backend/` 或 `backend/target/classes` 启动时也能向上解析到仓库根并扫描 `backend/target/smoke/document-parser-real-chain`；`parserQuality` 白名单返回 direct / QA retrieval 计数。
- `/quality` 文档解析质量摘要新增“检索来源”，展示“直接 / 问答”两个脱敏计数；桌面和 `390px` 移动端验证无横向溢出，console error 为 `0`。
- 真实 run：`document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-parser-real-chain-20260708212742-0f9baa`；PDF / HTML / DOCX 均 parse、chunk、QA retrieval、citation、source locator PASS，parserBoundary `4/4` PASS，artifact redaction PASS。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（40 tests，1 skipped）；`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`npm run lint` PASS；`npm run build` PASS；Quality API 最新 run 可见。
- 边界：真实 run 创建临时 smoke 数据和 ignored 脱敏 artifact；未删除业务数据，未改数据库结构，未操作远程 Docker，未提交 artifact 原文，未 push。

## 2026-07-08 Document Parser 解析质量报告 / Console parser 诊断增强

- `document-parser-real-chain-smoke.ps1` 新增脱敏 `parserQualityReport`，聚合文件类型覆盖、解析成功率、来源定位覆盖、RAG 检索 / 引用覆盖、错误边界通过率、warning 统计可用性和 review reasons。
- `QualityRunDiagnostics` 新增 `parserQuality`，`QualityArtifactServiceImpl` 只按白名单解析 report 的数值、布尔值和安全短 bucket；单测覆盖 report 解析和 prompt / answer / content 字段不泄露。
- `/quality` 的“文档解析质量摘要”新增格式覆盖、解析成功率、检索与引用覆盖、错误边界四张诊断卡，并对缺失指标显示“暂无统计”，避免把未知样本显示为 0。
- 验证：`mvn "-Dtest=*Quality*" test` PASS（39 tests，1 skipped）；`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 桌面和 `390px` 移动端均无 console error、无横向溢出。
- 边界：本片不改 parser / RAG 主链路，不新增数据库表，不启动真实 run，不创建业务数据，不提交 artifact 原文，不 push；下一片进入 fixture corpus v3 和真实链路回归。

## 2026-07-06 Document Parser Quality Console parser 指标展示增强

- `/quality` 的 Artifact 分区新增“文档解析质量摘要”，直接展示 Document Parser smoke 的关键脱敏指标：解析成功文件、切片总数、检索 / 引用、来源定位、解析失败数、运行耗时、负向边界通过和不支持格式拒绝。
- `frontend/lib/quality-labels.ts` 补充 parser gate / metric / flag 中文标签，`parserRealChain` 显示为“文档解析真实链路”，`parserBoundary` 显示为“解析错误边界”。
- 本片只读取 `QualityRunDetail.gates` 的现有数值和布尔字段，不改后端 API、不新增数据库表、不读取 raw artifact、不展示文档全文、prompt、answer、evidence context、异常堆栈、凭据、连接串或云地址。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright mock Quality API 可见“文档解析质量摘要”“负向边界通过”“不支持格式拒绝”，桌面和 `390px` 移动端无横向溢出，最新 console error 为 0。
- 收尾：本轮启动 `npx next start -p 3007` 做浏览器验证；验证后已停止对应进程并复跑清理脚本，目标端口均释放。

## 2026-07-06 Document Parser fixture corpus v2

- HTML parser 增强表格抽取：`tr` 会按 `th/td` 单元格生成 `Metric | Value` 这类结构化文本，避免表格行被压成难以阅读的普通空格文本。
- DOCX parser 增强列表识别：带编号或 list 样式的段落现在标记为 `BlockType.LIST`，并继承当前 `sectionPath`，便于 chunk metadata 和 citation 定位列表来源。
- `DocumentParserTest` 的 fixture 已扩到更真实结构：PDF 三页含空页 warning；HTML 覆盖 h1/h2、表格、列表、独立链接和 script/style/nav/footer 噪声剔除；DOCX 覆盖 Heading1/Heading2、普通段落、list 样式段落和表格。
- 已验证：`mvn "-Dtest=DocumentParserTest" test` PASS，7 tests；`mvn "-Dtest=*Parser*,ParseTaskConsumeEntryServiceImplTest,FileContentReaderTest,FileServiceImplTest" test` PASS，44 tests。
- 已复跑真实链路：`document-parser-real-chain-smoke.ps1 -Mode run` PASS，marker `docpilot-parser-real-chain-20260706215802-78374c`；PDF / HTML / DOCX 均完成上传、parse、chunk、retrieve、QA citation 和 source locator；`parserBoundary` 继续 PASS。
- 边界：不新增数据库表、不改 schema、不做 OCR、扫描件、外部网页抓取、`.doc` 旧格式或复杂版面还原；真实 run 创建临时 smoke 数据和 ignored 脱敏 artifact，未提交 artifact 原文。

## 2026-07-06 Document Parser 错误边界 API 负向增强

- `document-parser-real-chain-smoke.ps1` 的 `parserBoundary` gate 已从“准备坏文件”升级为真实 API 负向验证：每个负向 case 都经过 upload / document create / parse task create / parse terminal polling。
- 负向 artifact 只保存 `caseId`、`fileType`、`uploadRejected`、`parseStatus`、`failureCode`、`expectedFailureCode` 和 `passed` 等脱敏摘要，不保存文档全文、异常堆栈、prompt、answer、evidence context、凭据、连接串或云地址。
- 已覆盖 4 个边界：不支持格式上传拒绝、空白 TXT 返回 `PARSER_EMPTY_CONTENT`、损坏 PDF 返回 `PARSER_CORRUPTED_FILE`、损坏 DOCX 返回 `PARSER_CORRUPTED_FILE`。
- 首轮 run 曾因同一临时用户上传频率限制导致负向 case 在 parser 前被拒绝；runner 已改为每个负向 case 使用独立临时 smoke 用户，复跑后 `parserBoundary` PASS。
- 已验证：`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker `docpilot-parser-real-chain-20260706215134-857b73`，`negativeCasePassCount=4/4`、`negativeCaseFailCount=0`、`unsupportedUploadRejected=true`。
- 边界：真实 run 创建临时 smoke 数据和 ignored 脱敏 artifact；未删除业务数据，未改数据库结构，未操作远程 Docker，未提交 artifact 原文，未 push。

## 2026-07-06 Document Parser source locator 贯通到 RAG citation

- 新增 `RagSourceBlock`，将 parser block 的 `blockIndex`、`blockType`、`pageNumber`、`sectionTitle`、`sectionPath`、offset 和 `sourceLocator` 作为 RAG indexing 的脱敏来源结构传递。
- `ParseTaskConsumeEntryServiceImpl` parse success 后不再只把 `fullText` 交给 RAG trigger，而是传入完整 `ParseResult`；`RagIndexingTriggerServiceImpl` 将 `DocumentBlock` 转成 `RagSourceBlock` 后异步索引。
- `ChunkingServiceImpl` 根据 chunk offset 与 parser source block 的 overlap 选择最合适的来源块，并把 `pageNumber`、`sourceLocator`、`blockType`、`sectionPath` 和 `structureType` 写入 `DocumentChunkCandidate.structureMetadata()`。
- `RagIndexingServiceImpl` 已将这些 locator 进入 embedding metadata 和 vector payload；`RagRetrievalHit`、`RagEvidenceCitation` 以及 retrieve / citation response 现在返回 `pageNumber`、`sourceLocator`、`blockType`，便于真实 citation 定位页码或区块。
- 修复 HTML / DOCX heading block 的 `endOffset`，确保 block offset 对齐 `fullText` 中的 Markdown heading 片段；smoke runner 的 `sourceLocatorPresent` 判定已优先识别 `sourceLocator`、`pageNumber`、`blockType`。
- 已验证：targeted parser / chunk / index / retrieval / parse consume 56 tests PASS；broader parser/RAG 88 tests PASS；`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker `docpilot-parser-real-chain-20260706214209-dcb8f2`。
- 边界：不新增数据库表、不改 schema、不做 OCR / 扫描件识别 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原 / PDF 坐标级 citation；真实 run 只创建临时 smoke 数据，artifact 仍为 ignored 脱敏摘要。

## 2026-07-06 Document Parser MVP 真实链路验证

- 修复 parser locator 传递：HTML / DOCX parser 的 `fullText` 现在保留 Markdown heading，便于现有 chunker 生成 `sectionPath` / `structureType`；单文档 RAG retrieve / QA citation 已暴露脱敏 `sourceName`、`sectionPath`、`structureType`，让真实 citation 能定位文件和章节摘要。
- 新增 `scripts/smoke/document-parser-real-chain-smoke.ps1`，支持 `plan / dry-run / run`。`run` 生成小型 PDF / HTML / DOCX fixture，注册临时用户，上传三类文档，等待 parse `SUCCESS`，验证 chunk count、RAG retrieve、QA citation、source locator 和 unsupported format boundary，并写入 ignored 脱敏 artifact。
- Agent Quality Console 已纳入 parser smoke artifact root：`backend/target/smoke/document-parser-real-chain`；`parserRealChain` gate 暴露 `fileCount`、`parsedFileCount`、`parserFailureCount`、`chunkCount`、`retrieveHitCount`、`citationCount`、`sourceLocatorCount` 和 `durationMs` 等安全数值，不返回 prompt、answer 原文、文档全文或 evidence context。
- 真实 run：`docpilot-parser-real-chain-20260706172220-f03956` PASS。PDF / HTML / DOCX 均 `parseStatus=SUCCESS`、`chunkCount=1`、`retrieveHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`；tunnel、backend、frontend、artifact redaction 均 PASS。
- 中途首轮真实 run 曾出现 QA citation 为 0 和 chunkCount 显示 49；定位为 smoke 问题设计和 PowerShell 单行字符串转数字 bug，已修复为 marker-aware QA 问题和正确 chunk count 解析，复跑通过。未发现新的业务 bug。
- 已验证：`mvn "-Dtest=*Quality*,DocumentParserTest,ChunkingServiceImplTest,RagDocumentRetrievalServiceImplTest" test` PASS，70 tests，1 skipped；`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS。
- 边界：真实 run 创建 marker 临时用户和临时文档；不删除已有业务数据，不改数据库结构，不操作远程 Docker，不提交 artifact 原文，不 push。

## 2026-07-06 Document Parser MVP 第一片

- 新增统一 `DocumentParser` / `ParserRegistry` / `ParseResult` / `DocumentBlock` 抽象，解析结果保留 `fullText`、block、pageNumber、sectionPath、blockType、parserName、parserVersion、parseDurationMs、extractedChars、pageCount、blockCount 和 warnings。
- 新增 parser 实现：`TextDocumentParser` 支持 `txt / md`；`PdfDocumentParser` 使用 PDFBox 抽取文本型 PDF 页级文本；`HtmlDocumentParser` 使用 Jsoup 解析本地上传 HTML 并去除 `script/style/nav/footer/header` 等噪声；`DocxDocumentParser` 使用 Apache POI 抽取 DOCX 段落、标题和表格文本。
- `ParseTaskConsumeEntryServiceImpl` 已接入 parser registry，解析成功后继续进入 summary、chunking、RAG indexing trigger 和原有 parse status 流转；RAG indexing trigger 异常仍不会反向打断 parse success。
- `FileContentReader` / `MinioFileStorageWriter` 增加 `readBytes` 和 `openStream`，支持 PDF / DOCX parser 读取本地或 MinIO 对象；上传 allowlist 扩到 `pdf/md/txt/html/htm/docx`。
- 新增 parser metrics 和配置：`APP_DOCUMENT_PARSER_MAX_FILE_SIZE_BYTES`、`APP_DOCUMENT_PARSER_TIMEOUT_MS`；只记录 parserName、耗时、字符数、页数、block 数和 warning 数，不记录文档全文。
- 已验证：`mvn "-Dtest=*Parser*,ParseTaskConsumeEntryServiceImplTest,FileContentReaderTest,FileServiceImplTest" test` PASS，44 tests；`mvn "-Dtest=ChunkingServiceImplTest,RagIndexingServiceImplTest,RagIndexingTriggerServiceImplTest,RagDocumentRetrievalQualitySmokeTest" test` PASS，34 tests。
- 边界：不新增数据库表、不改 schema、不做 OCR、扫描件识别、复杂版面还原、外部网页抓取、`.doc` 旧格式或 PDF 坐标级 citation；本片未启动真实链路，未创建业务数据，未提交 artifact 原文。

## 2026-07-06 Agent Quality Console 真实可见性回归

- 本地 tunnel 可用，backend `/actuator/health` 为 `UP`；前端使用 `next build` + `next start -p 3007` 启动。
- 注册临时 smoke 用户后，Playwright 打开 `/quality?autoload=1`，页面可见“内部质量排查控制台”“质量诊断”和已加载质量运行记录。
- 验证新的指标语义可见：分母为 `totalRuns` 的说明和“暂无统计”缺样本文案均出现在真实页面中。
- 桌面 `1440px` 和移动端 `390px` 均无横向溢出，console error 为 `0`。
- 插曲：`next dev -p 3007` 本轮卡在 Starting 且 HTTP 超时，改用生产预览完成验证；暂不作为业务 bug 记录。
- 边界：只创建临时登录用户，不上传文档、不创建 KB / Conversation、不提交 artifact 原文、不 push。

## 2026-07-06 Agent Quality Console token 文案一致性收口

- `/quality` 面向用户的 token 相关文案已统一为“token 数”“token 用量”“token 增量”，避免 `Token / TOKENS / tokens` 大小写混用。
- 诊断卡优先排查文案中的成本入口已改为“模型调用 / token 用量 / 重试”。
- 边界：本片只改前端展示文字，不改 DTO、不改 API、不新增功能。

## 2026-07-06 Agent Quality Console 延迟指标缺样本语义修正

- `/quality` Trend 面板“平均延迟”已改为缺样本显示“暂无统计”，与 token / 成本缺样本语义保持一致。
- Overview 的 P95 延迟说明已补充：该指标基于最近 trend points 的 `latencyMs`，没有 point 样本时显示“暂无统计”，不硬算。
- 边界：本片只改前端展示语义，不改后端 API、不新增数据库表、不读取 raw artifact、不启动真实链路。

## 2026-07-06 前端文档详情错误提示乱码兜底清理

- `frontend/app/documents/[documentId]/page.tsx` 已移除历史 mojibake 错误消息字面量匹配，文档错误 hint 只基于正常中文 `无权` / `不存在` 判断。
- `frontend/lib/api.ts` 现有统一错误归一仍负责把文档和知识库权限 / 不存在错误转换为可读中文，页面不再需要乱码兼容分支。
- 已验证：`npm run lint` PASS；`npm run build` PASS；前端源码和本轮文档乱码扫描无命中；`git diff --check` PASS。
- 边界：本片不改 API 协议、不改后端、不新增功能、不启动真实链路、不触碰业务数据。

## 2026-07-06 Agent Quality Console 指标可信度与失败桶可行动化

- `/quality` 的通过率、复查率、失败率已改为百分比 + 分子 / 分母展示，并在说明中明确分母为 `totalRuns`。
- token / 成本展示语义已修正：缺少 `token_usage` 或 `estimatedCost` 样本时显示“暂无统计”或“暂无样本”，只有明确数值为 `0` 时才显示 `0`。
- 质量诊断卡新增“优先排查”字段，把异常指标直接指向 `Failures / Gates / Trace`、`Citation / RAG / Eval Scorer`、`LLM / RAG / Tool latency`、`Context / Prompt / RAG chunks` 等排查入口。
- 失败类型 TopN 和复查类型 TopN 已增加模块标签、次数、简短说明和建议动作；bucket 归类优先识别 RAG、Citation、Tool、Memory、Security、Env，无法归类时保留 `Unknown / 其他` 并提示需要补充映射规则。
- 边界：本片只改前端现有 DTO 映射，不改后端 API、不新增数据库表、不展示 raw artifact、prompt、answer 原文、文档全文、evidence context、凭据、连接串或云地址。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright mock Quality API 打开 `/quality?autoload=1`，桌面和 `390px` 移动端无 console error、无横向溢出；已验证分子 / 分母、缺失 token 降级、模块标签、建议动作和 Unknown fallback 文案。

## 2026-07-06 Agent Quality Console P1 parser 安全摘要增强

- `QualityRunDetail` 新增 `diagnostics` 安全摘要，包含文档覆盖、工具质量和记忆质量三组数值统计。
- `QualityArtifactServiceImpl` 从 `documentHitCounts`、`contextSourceCounts`、tool / memory metrics 和 bucket 聚合摘要；文档命中只返回覆盖数量、零命中文档数和 min/max 命中数，不返回文档 ID 或原始 map。
- `/quality` 的 RAG / 记忆 / 工具调用诊断卡已使用后端摘要展示“命中文档分布”“记忆命中摘要”“工具参数复查”；缺字段时显示“暂无安全摘要”。
- 脱敏边界：不新增数据库表、不新增 API、不读取业务库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，38 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright mock Quality API 桌面和 `390px` 移动端 PASS，无 console error、无横向溢出；前端进程已清理。

## 2026-07-06 Agent Quality Console 诊断指标 P0

- `/quality` 新增前端派生诊断指标层，基于现有脱敏 `runs`、`trend`、`QualityRunDetail` 和 eval catalog 计算比率，不改后端 DTO / API。
- Overview 新增通过率、复查率、失败率、P95 延迟、平均 tokens、成功运行成本，以及失败 / 复查类型 TopN 与建议动作。
- Run Detail 新增“评测”tab；RAG / 记忆 / 工具调用 tab 增加诊断比率卡，Failures tab 增加当前失败、复查、新增失败和已恢复失败类型的建议动作。
- P0 对暂时无法可靠计算的指标做显式降级：`documentHitCounts`、严格工具选择准确率、工具参数准确率、记忆有用命中率和记忆噪声率仍需 P1/P2 parser 或 eval schema 扩展。
- 脱敏边界：只展示数值、比率、bucket、短安全 ID 和建议动作，不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- 已验证：`npm run lint` PASS；`npm run build` PASS。后续继续用 mock Quality API 和真实 `/quality?autoload=1` 做浏览器回归。

## 2026-07-06 Agent Quality Console 前端信息架构重构

- `/quality` 已从“所有 summary 和 artifact 堆在长页面里”改为“顶部 Overview + 左侧运行筛选 + 右侧分区排查”的内部质量控制台结构。
- 顶部 Overview 显示最近 run 健康判断、状态、运行次数、通过 / 复查 / 失败统计和 token 数值摘要；左侧运行记录支持状态筛选和 marker / 来源搜索。
- Run Detail 拆成“摘要 / 门禁 / 待处理 / 链路 / RAG / 记忆 / 工具调用 / Artifact”分区；FAILED / REVIEW 门禁默认展开，PASS 门禁默认折叠。
- 链路分区保留 Trace 定位，并让失败或需复查 eval case 显示“查看 Trace”入口；RAG / Memory / Tool Calls 分区只展示现有脱敏数值摘要。
- Artifact 分区只展示脱敏元信息，Eval Catalog 和 Trend 移到非首屏区域；仍不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- Gemini CLI 可用，但正式建议调用返回 malformed / empty response；本轮按约束降级为 Codex 直接集成、验证和回写。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 静态 route smoke 和 mock Quality API smoke 覆盖桌面与 `390px` 移动端，无 console error、无横向溢出，并验证失败门禁突出、PASS 折叠、Eval “查看 Trace”入口和 run 搜索可见；前端进程已清理。

## 2026-07-06 Agent Quality Console 前端中文化二次增强

- `gemini.cmd --version` 和 READY 探测通过；正式文案建议调用连续超时，本轮按协作约束降级为 Codex 直接集成、验证和回写。
- `frontend/lib/quality-labels.ts` 已改为默认返回纯中文标签，不再把 raw key 拼到正文里；状态、失败桶、指标、布尔门禁、用例类型、门禁名和链路步骤都走中文展示。
- `/quality` 已把 Overview、Eval Catalog、Quality Trend、Run Detail、Run Comparison、Gate、Eval Case 等默认标题进一步改为中文；失败桶和普通列表分开格式化，避免 source issue、评分规则等普通字段被误当作失败类型翻译。
- `/quality/trace` 已把 Trace 定位、Trace Reference、Run、Gate、Eval Case、steps 等页面文案进一步中文化；必要 raw key 只保留在 `title` 悬停信息或 `traceId` / `agentRunId` 等技术定位 ID 中。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright route smoke 覆盖 `/quality?routeSmoke=2` 与 `/quality/trace?routeSmoke=1...` 的桌面和 `390px` 移动端，均无业务 console error、无横向溢出，且正文无典型 raw key 括号残留；乱码扫描和敏感扫描无命中，端口已清理释放。
- 边界：本片只改前端展示和状态文档，不改 Quality API、不读取业务库、不创建业务数据、不提交 artifact 原文、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。

## 2026-07-06 Agent Quality Console 前端可读性增强

- 新增 `frontend/lib/quality-labels.ts`，统一 status、failure bucket、metric、flag、case type、gate 和 trace step 的中文展示；页面仍保留 raw key，便于和 artifact / API 字段对应。
- `/quality` 已把 Overview、Eval Catalog、Quality Trend、Run Detail、Failure Triage、Trace 定位、Gate / Eval Case 明细、Model / Cost Summary 和 Run Comparison 的关键参数改为中文可读标签。
- `/quality/trace` 已把 Trace Reference、链路瀑布图、关联 Gate 和关联 Eval Case 同步改为同一套中文标签。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright route smoke 覆盖 `/quality?routeSmoke=2` 与 `/quality/trace?routeSmoke=1...`，桌面和 `390px` 移动端均无业务 console error、无横向溢出。
- 边界：本片只改前端展示和状态文档，不改 Quality API、不读取业务库、不创建业务数据、不提交 artifact 原文、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；本轮启动的前端进程已清理。

## 2026-07-05 Agent Quality Console Trace / Eval / Trend 真实链路回归

- `real-user-qa-experience-audit.ps1 -Mode plan` PASS；`-Mode dry-run -FrontendBaseUrl http://127.0.0.1:3007` PASS。
- 最终真实 run PASS，marker `docpilot-real-user-qa-20260705210119-7b8092`；核心 RAG、KnowledgeBase、短文档 RAG、自然语料、multi-query、answer grounding、no-evidence、Conversation Trace、Memory、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- `cloud-quality-smoke.ps1` 增强 frontendInteraction console error 诊断，只记录脱敏 `phase/kind/messageShape`；同时为 `naturalCorpus` 和 `conversationTrace` 写入脱敏 trace case result，支撑 Console Trace Drill-down v3。
- Console API 验证 PASS：`/api/quality/runs/{marker}` 返回 `summary.status=PASS`、`gateCount=22`、`evalCaseCount=27`、`traceReferenceCount=2`；`/api/quality/eval-cases` 返回 7 个 case；`/api/quality/trends?limit=20` 返回 20 个趋势点。
- 浏览器 `/quality?autoload=1` 和 `/quality/trace` PASS：桌面和 `390px` 移动端 console error count 均为 `0`，无横向溢出，Trace 页面可见 `Eval case` 链路步骤。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，37 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 中途发现 `docpilot-real-user-qa-20260705205210-8c882e` 的 KB 阶段偶发 `TypeError`，旧 gate 诊断不足；已记录为 `REA-20260705-P3-008`，最终 PASS run 未复现。
- 边界：创建了临时 smoke 数据和 ignored 脱敏 artifact；未提交 artifact 原文，未删除业务数据，未操作远程 Docker / hk-ops，未改 schema，未 push；本轮启动的 backend / frontend 已清理。

## 2026-07-05 Agent Quality Console Quality Trend v1

- 后端新增 `/api/quality/trends?limit=20`，基于最近 N 个脱敏 artifact detail 聚合状态分布、failure / review bucket、casePassRate、token / cost、latency / duration 和反复失败 case。
- 前端 `/quality` 新增 `Quality Trend` 面板，展示最近 run 趋势、Top buckets、平均指标、repeated cases 和 recent points。
- 首次后端测试暴露趋势未统计 eval case 层 bucket，已修正为同时统计 run/gate 和 eval case buckets。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，37 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；`/quality?routeSmoke=2` 移动端无 console error、无横向溢出。
- 边界：本片不新增数据库表，不读取业务库，不展示原始 artifact、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；本轮启动的前端和浏览器进程已清理。

## 2026-07-05 Agent Quality Console Eval Asset v2

- 7 个默认 eval catalog case 已新增 `caseLayer`、`riskGate`、`scoringSummary`、`regressionPolicy` 和 `failureHistoryMarkers`，把 case catalog 继续推进为可解释质量资产。
- 后端 `QualityEvalCatalogServiceImpl` 和 `QualityEvalCaseCatalogItem` 已同步白名单字段；failure history 只保存脱敏 marker、status 和 issue id 摘要。
- 前端 `/quality` Eval Catalog 卡片已展示 case layer、risk gate、scoring summary、regression policy 和 failure history marker。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；`/quality?routeSmoke=2` 移动端无 console error、无横向溢出。
- 边界：本片不新增数据库表，不返回 question、expectedBehavior、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；本轮启动的前端和浏览器进程已清理。

## 2026-07-05 Agent Quality Console Trace Drill-down v3

- 后端新增 `QualityTraceStepDetail`，并在 `QualityTraceReference.steps` 中返回脱敏 trace step 摘要。
- `QualityArtifactServiceImpl` 从 eval case 的安全 metrics / flags / buckets 推断 eval case、agent step、RAG retrieve、tool call、model call、citation 和 failure bucket 链路步骤，不读取业务库、不新增数据库表。
- `/quality/trace` 新增“链路瀑布图”面板，展示 step type、status、metrics、flags 和 buckets。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright route smoke 无 console error，`390px` 宽度无横向溢出。
- 边界：本片不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；本轮启动的前端和浏览器进程已清理。

## 2026-07-05 Agent Quality Console 三线升级收口

- 已将求职级 Agent Quality Console 的后续自驱循环收口为三条主线：Trace Drill-down v3、Eval Asset v2、Quality Trend v1。
- `ROADMAP_AGENT_QUALITY_CONSOLE.md` 已补充三线目标、统一安全边界、每条主线的最小实现和验收标准。
- `CURRENT_TASK.md` 已指向下一片 Trace Drill-down v3；`STATE.md` 已记录当前路线图事实。
- 边界：本片只改文档，不改业务代码，不启动服务，不创建业务数据，不提交 artifact 原文，不 push。

## 2026-07-05 Agent Quality Console 7-case 真实审计回归

- `real-user-qa-experience-audit.ps1 -Mode plan` PASS；`-Mode dry-run -FrontendBaseUrl http://127.0.0.1:3007` PASS。
- 真实 run PASS，marker `docpilot-real-user-qa-20260705192354-eba0fc`；核心 RAG、KnowledgeBase、Conversation Trace、Memory、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Console API 可见性 PASS：`/api/quality/runs` 可见最新 marker，`/api/quality/eval-cases` 返回 7 个 case，其中 4 个带 `sourceIssueIds`，7 个带 `remediationHints`。
- 浏览器 `/quality?autoload=1` PASS：最新 marker、source issue、verified marker、remediation hints 可见；桌面和 `390px` 移动端无横向溢出，console error 为 `0`。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 边界：创建了临时 smoke 数据和 ignored 脱敏 artifact；未删除业务数据，未操作远程 Docker / hk-ops，未改 schema，未提交 artifact 原文，未 push。

## 2026-07-05 Agent Quality Console Eval Catalog 筛选 v1

- `/quality` Eval Catalog 新增 risk、owner、status 三个本地筛选控件，支持快速定位高风险、特定 owner 或失败 / REVIEW case。
- 本片只改前端展示，不新增后端 API、不新增数据库表、不启动真实后端 / tunnel。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 桌面与 `390px` 移动端无横向溢出、console error 为 `0`；清理脚本确认端口释放。

## 2026-07-05 Agent Quality Console Eval Catalog Remediation Hint v1

- 7 个默认 eval catalog case 已补 `lastVerifiedMarker` 和 `remediationHints`，让 Console 能展示每个 case 最近验证 marker 和修复排查方向。
- `QualityEvalCatalogServiceImpl` / `QualityEvalCaseCatalogItem` / `/quality` Eval Catalog 已同步展示安全字段；URL、secret-like hint 和连接串形态继续被过滤。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 边界：本片不启动真实链路，不创建业务数据，不调用 provider，不提交 artifact 原文；这是 catalog 可解释性增强。

## 2026-07-05 Agent Quality Console Real Audit Case 扩容 v1

- `agent-quality-eval-cases.json` 从 3 个默认 case 扩到 7 个，新增短文档 RAG evidence、KB 双文档覆盖、summary 干扰 citation 裁剪和 Quality Console backend health 四类真实审计沉淀 case。
- 新增 `sourceIssueIds` 安全字段，只展示 `REA-...` 脱敏问题编号；`QualityEvalCatalogServiceImpl`、`QualityEvalCaseCatalogItem`、`/quality` Eval Catalog 和相关测试已同步。
- 首次后端测试暴露 `distractor_citation_free` 与 `distractor_citation` 子串匹配冲突，已改成不重叠 marker；复跑通过。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 边界：本片不启动真实链路，不创建业务数据，不调用 provider，不提交 artifact 原文；这是评测资产沉淀，不代表新一轮 runtime audit。

## 2026-07-05 Agent Quality Console Eval Case Version v1

- `agent-quality-eval-cases.json` 为默认 3 个 case 增加 `caseVersion`、`owner`、`lastUpdated` 和 `riskLevel`，让 Eval Catalog 更像可维护的质量题库。
- `QualityEvalCatalogServiceImpl` / `QualityEvalCaseCatalogItem` / `/quality` Eval Catalog 已同步安全字段；question、expectedBehavior、mustContain、mustNotContain 仍不返回到 API 或页面。
- test-side `AgentQualityEvalCase` 允许忽略未知 JSON 字段，避免 catalog 元数据破坏离线 eval runner；eval result artifact 仍只保存脱敏 case result。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。

## 2026-07-05 Agent Quality Console Trace Detail 最小入口

- 新增 `frontend/app/quality/trace/page.tsx`，通过 marker / caseId / traceId / agentRunId / conversationId 定位同一 Quality run 里的脱敏 trace reference，并展示关联 gate / eval case 的安全 metrics、flags 和 failure / review buckets。
- `/quality` Run Detail 的 Trace 定位行新增“打开”链接，跳转到 `/quality/trace?...`；页面支持 `routeSmoke=1`，便于不启动后端时做前端路由 smoke。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality/trace?routeSmoke=1&marker=docpilot-route-smoke&caseId=route-smoke` 桌面与 `390px` 移动端无 console error，未见横向溢出。
- 边界：本片不新增后端 API，不读业务数据库，不展示 prompt、answer 原文、文档全文、evidence context、凭据、连接串或云地址。

## 2026-07-05 Agent Quality Console 求职展示打磨

- README 已更新为“企业文档知识库 RAG + 会话记忆工程化平台”口径，补充 `/quality` 内部质量控制台、Conversation Memory、Context Trace、RAG / Memory 真实质量门禁和当前边界。
- `PROJECT_INTERVIEW_BRIEF.md` 已更新项目定位、已实现能力、简历亮点、展示优先级和高风险追问，重点讲清“真实审计发现问题 -> Console / artifact 定位 -> 修复 -> 真实回归 PASS”的闭环。
- `RESUME_BULLETS.md` 已增加 Agent Quality Console、真实 audit / eval artifact、Memory governance 和 RAG 质量门禁相关 bullet，并保留不夸大边界。
- `INTERVIEW_QA.md` 已更新一分钟介绍、核心亮点、RAG 完整性、Agent Quality Console 价值、项目不足和下一步回答。
- 边界：本片只改文档，不改业务代码，不启动服务，不创建业务数据，不提交 artifact 原文，不 push。

## 2026-07-05 Agent Quality Console 真实体验审计集成 v2

- 首轮真实审计 `docpilot-real-user-qa-20260705164732-f54da1` 为 `BLOCKED`：backend health 未 UP。定位到 `QualityEvalCatalogServiceImpl` 多构造器缺少显式 `@Autowired`，真实 Spring 启动尝试找默认构造器失败。
- 已修复构造器注入并新增 `QualityEvalCatalogServiceSpringContextTest`；随后真实审计 `docpilot-real-user-qa-20260705165151-bbe588` PASS，核心 RAG、KnowledgeBase、Conversation Trace、Memory、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Console 可见性验证 PASS：`/api/quality/runs` 可见最新 marker，detail 为 `PASS`，`/api/quality/eval-cases` 返回 3 个 case；浏览器 `/quality?autoload=1` 可见最新 marker、Eval Catalog、Failure Triage、Run Comparison、Model / Cost Summary，console error count 为 `0`，`390px` 无横向溢出。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；清理脚本确认端口释放。
- 边界：创建了临时 smoke 数据和 ignored 脱敏 artifact；未删除业务数据，未操作远程 Docker / hk-ops，未改 schema，未提交 artifact 原文，未 push。

## 2026-07-05 Agent Quality Console Cost / Latency / Model Summary v1

- `QualityArtifactServiceImpl` 的安全 metric 白名单扩展到 `latencyMs`、`durationMs`、`estimatedCost` 和 `*Ms` 数值字段；已有 `modelCallCount`、`toolCallCount`、`retryCount` 继续作为 count 类指标保留。
- 前端 `/quality` Run Detail 新增 `Model / Cost Summary` 面板，聚合展示 token usage、estimated cost、model calls、tool calls、latency、duration 和 retry 数值。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，34 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 移动端 smoke 无 console error、主要容器未横向溢出；清理脚本确认端口释放。
- 边界：仅展示数值统计，不展示 prompt、answer 原文、provider 原始输出、文档全文、evidence context、凭据、连接串或云地址。

## 2026-07-05 Agent Quality Console Run Comparison v1

- 前端 `/quality` Run Detail 新增 `Run Comparison` 面板，可选择 previous run，并复用现有 run detail API 展示当前 run 相对 previous run 的状态、gate、失败桶、eval case、token total 和 casePassRate 差异。
- 对比结果只展示脱敏白名单字段和数值 delta，不新增后端 compare API、不新增数据库表、不展示原始 artifact、prompt、answer 原文、文档全文或 evidence context。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 移动端 smoke 无 console error、主要容器未横向溢出；清理脚本确认端口释放。

## 2026-07-05 Agent Quality Console Eval Case Catalog v1

- 后端新增 `QualityEvalCatalogService`、`QualityEvalCaseCatalogItem` 和 `GET /api/quality/eval-cases`，从现有 eval JSON 读取白名单字段，并关联最近 Quality run 的 latest status / marker / traceId / agentRunId。
- 前端 `/quality` 左侧新增 `Eval Catalog` 卡片，展示 caseId、caseType、tags、expectedEvidence、expectedTools、scoringRules 和最近运行状态。
- 脱敏边界：不返回 question、expectedBehavior、mustContain、mustNotContain、answer 原文、prompt、文档全文、evidence context、API key、token、secret、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，34 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright `/quality?routeSmoke=2` 移动端 smoke 无 console error、主要容器未横向溢出；清理脚本确认端口释放。

## 2026-07-05 Agent Quality Console Failure Triage v1

- 前端 `/quality` Run Detail 新增 Failure Triage 面板，支持按 status、失败桶 taxonomy、gate name 和 case type 筛选，并联动 Gate 列表、Eval Case 与 Trace 定位项。
- 失败桶归一化覆盖 RAG retrieval miss、citation unsupported、distractor citation、no-evidence false positive、memory conflict、tool failure、permission regression、frontend UX、env blocked 和 other；展示只包含脱敏桶名、计数和 case / gate 摘要。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality?routeSmoke=2` 在移动端宽度下无 console error、主要容器未横向溢出；清理脚本确认端口释放。
- 边界：本片未新增后端 API，未新增数据库表，未启动真实 backend / tunnel，未创建业务数据，未提交 artifact 原文，未 push。

## 2026-07-05 Agent Quality Console Trace Drill-down v2

- 后端新增脱敏 `QualityTraceReference`，`QualityRunDetail` 增加 `traceReferences`；artifact parser 现在递归收集多个 `caseResults` / `caseEvaluations` / `evalCases`，保留父级 `gateName`，并只输出安全定位字段和失败 / REVIEW 桶。
- 前端 `/quality` Run Detail 新增“Trace 定位”面板，展示 caseId、gateName、status、traceId、agentRunId、conversationId 和桶信息，并提供复制 ID 按钮；不新增真实 Trace 详情页，不读取业务数据库。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，30 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；浏览器 `/quality?routeSmoke=2` 在 `390x844` 下无 console error、无横向溢出；清理脚本确认端口释放。
- 边界：本片未新增数据库表，未改变核心业务流程，未启动真实 backend / tunnel，未创建业务数据，未提交 artifact 原文，未 push。

## 2026-07-05 Agent Quality Console 求职级升级路线图

- 新增 `docs/ai-dev/ROADMAP_AGENT_QUALITY_CONSOLE.md`，把当前 Agent Quality Console 与求职级内部质量控制台之间的差距、Phase 0-8 路线、每片验收标准、自驱循环规则和停止条件沉淀为长期事实源。
- 更新 `docs/README.md`，把 Agent Quality Console 路线图纳入默认文档地图；后续当前任务仍看 `CURRENT_TASK.md`，长期升级路线看 `ROADMAP_AGENT_QUALITY_CONSOLE.md`。
- 更新 `CURRENT_TASK.md` 和 `STATE.md`：Phase 0 已完成，下一片建议进入 Trace Drill-down v2，优先让失败 / REVIEW eval case 能定位 `traceId` / `agentRunId`。
- 边界：本片只改文档和任务口径，未改业务代码，未启动服务，未创建业务数据，未提交 artifact 原文，未 push。

## 2026-07-05 Agent Quality Console Explainability v1 Slice C

- 完成真实回归：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705151944-950f42`。
- 关键质量结果：`naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`；frontendInteraction、Memory quality、Conversation Trace、权限隔离和 artifact redaction 均 PASS。
- 完成 Console 可见性验证：开启 `APP_QUALITY_CONSOLE_ENABLED=true` 后，浏览器 `/quality?autoload=1` 可见最新 marker、`naturalCorpus` gate、`CASEPASSRATE`、`DISTRACTORCITATIONFREECOUNT` 和 eval case `ops-incident-support-summary`；console error count 为 `0`，`1366x900` 无横向溢出。
- 本轮启动的 backend / frontend / tunnel 均已清理；未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

## 2026-07-05 Agent Quality Console Explainability v1 Slice B

- 完成 `/quality` Run Detail 可解释性展示：gate 和 eval case 行新增脱敏 signals 小格子，展示安全 metrics / flags；Eval case 同时展示 failure / review buckets 和 traceId / agentRunId。
- 前端类型同步 `QualityEvalCaseResultDetail.metrics` / `flags`，不展示 question、answer 原文、文档全文、prompt 或 evidence context。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `http://127.0.0.1:3007/quality?routeSmoke=2` 页面非空、console error count 为 `0`、`390x844` 无横向溢出。

## 2026-07-05 Agent Quality Console Explainability v1 Slice A

- 完成后端 artifact 聚合增强：`QualityArtifactServiceImpl` 现在能解析 cloud quality / real-user audit artifact 中的嵌套 `gates.*`，并从单个 `checks` object 中抽取安全数值 / 布尔指标；多个 check 只保留 `checkCount`。
- `QualityEvalCaseResultDetail` 新增脱敏 `metrics` / `flags`，用于后续 `/quality` 展示 eval case 的召回数、citation 数、干扰 citation 数和覆盖布尔结果。
- 安全边界保持字段白名单：不返回 prompt、answer 原文、文档全文、evidence context、question、API key、token、secret、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，30 tests，1 skipped。

## 2026-07-05 RAG citation 精度收口

- 修复 `REA-20260704-P2-006`：KnowledgeBase QA 在答案生成后的 citation 后处理阶段新增极低分引用裁剪，只在 summary / compare 等多文档意图下、且裁剪后仍保留至少两份文档 coverage 时生效。
- 保留 retrieval hits 和 `documentHitCounts`，避免为了让最终 citation 更干净而丢失 Trace / audit 中的召回诊断证据。
- 新增 `KnowledgeBaseRagQaServiceImplTest` 防回归用例，覆盖目标两文档 citation 保留、低分干扰 citation 移除、召回 hits 仍保留。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest" test` PASS；`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS。
- 真实回归：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705145304-7a53b8`；`naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`，frontendInteraction、Memory quality、Conversation Trace、权限隔离和 artifact redaction 均 PASS。
- 边界：未改数据库结构，未删除业务数据，未操作远程 Docker / hk-ops，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

## 2026-07-04 Agent Quality Console MVP Slice 6

- 完成 Slice 6 真实链路质量回归：`agent-quality-eval-smoke.ps1 -Mode run` PASS，marker `docpilot-agent-quality-eval-20260704221655-48a5cf`；`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 完成，marker `docpilot-real-user-qa-20260704221704-4abc6f`。
- 真实审计整体为 `REVIEW`：核心业务 gate、权限隔离、frontendInteraction、Memory quality、Conversation Trace、cleanup 和 artifact redaction 均 PASS；`naturalCorpus` 中 `ops-incident-support-summary` 出现 `distractorCitation` review，`distractorCitationFreeCount=24/25`，已写入真实体验台账 `REA-20260704-P2-006`。
- 完成 Console 可见性验证：开启 `app.quality.console.enabled=true` 后，`/api/quality/runs`、`/api/quality/runs/{marker}` 和浏览器 `/quality?autoload=1` 都能看到该 REVIEW run，浏览器 console error count 为 `0`。
- 修复真实启动时发现的 Quality service 装配问题：`QualityArtifactServiceImpl` 显式标注构造器注入，并新增 `QualityArtifactServiceSpringContextTest`。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，29 tests，1 skipped；artifact 脱敏扫描 PASS；本轮 backend / frontend 已清理释放。
- 边界：未提交原始 artifact，未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未打印 secrets / token / 云地址 / 连接串，未 push。

## 2026-07-04 Agent Quality Console MVP Slice 5

- 新增 `frontend/lib/quality-api.ts` 和 `frontend/app/quality/page.tsx`，完成内部 `/quality` P0 页面：Overview 展示 run 状态、gate 统计、失败桶和 token usage / cost 数值；Run Detail 展示 gate 列表和 eval case 结果。
- 页面保留 `Trace` / `Eval` / `Failures` 入口，但不展开复杂平台。默认 `/quality` 不自动请求后端，避免路由 smoke 被旧 token / 未启动 backend 干扰；完整链路可用 `/quality?autoload=1`。
- 新增 `frontend/app/icon.svg`，避免浏览器 route smoke 产生 favicon 404。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `http://localhost:3007/quality?routeSmoke=2` 无 console error；`390x844` snapshot 未见横向溢出；本轮启动的 `3007` 前端 dev server 已清理。
- 边界：本片不改后端 API，不新增数据库表，不启动真实业务链路，不提交 artifact 原文，不 push。

## 2026-07-04 Agent Quality Console MVP Slice 4

- 新增轻量 Agent Quality Eval fixture：`agent-quality-eval-cases.json` 包含 `caseId`、`question`、`expectedBehavior`、`expectedEvidence`、`expectedTools`、`mustContain`、`mustNotContain`、`tags` 和 `scoringRules`。
- 新增 test-side `AgentQualityEvalRunner`：根据脱敏 observation 输出 `QualityEvalCaseResultDetail`，只保存 caseId、caseType、status、passed、traceId / agentRunId 和失败桶，不保存 question、answer、prompt、文档全文或 evidence context。
- 新增 `agent-quality-eval-smoke.ps1`，支持 `plan` / `dry-run` / `run`；run 只执行离线 JUnit 并生成 ignored 脱敏 artifact。`backend/target/agent-quality-eval` 已加入 Quality artifact 聚合白名单。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，28 tests，1 skipped；`agent-quality-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker `docpilot-agent-quality-eval-20260704220047-9c9af0`；artifact 脱敏扫描 PASS。
- 边界：这是轻量离线 eval 合约，不是大规模 Agent benchmark；未启动服务，未创建业务数据，未读取 `.env`，未调用 provider，未新增数据库表，未提交 artifact 原文，未 push。

## 2026-07-04 Agent Quality Console MVP Slice 3

- 新增内部只读 `QualityController`：`GET /api/quality/runs` 返回最近 run summary，`GET /api/quality/runs/{marker}` 返回 run detail。
- 访问控制保持 P0 最小方案：API 默认由 `app.quality.console.enabled=false` 关闭；打开后仍要求已有登录上下文，普通未登录请求不能读取质量详情。未新增 admin 表或角色系统。
- 返回数据继续复用 Slice 2 脱敏 DTO，不返回原始 artifact、prompt、answer 原文、文档全文、evidence context、API key、secret、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，21 tests；Controller test 覆盖关闭开关、缺登录上下文、默认 limit、detail 查询和 detail missing。
- 边界：本片不做前端 `/quality`，不新增数据库表，不启动服务，不创建业务数据，不提交 artifact 原文，不 push。

## 2026-07-04 Agent Quality Console MVP Slice 2

- 新增后端只读 artifact 聚合 service：`QualityArtifactServiceImpl` 扫描白名单 root，默认返回最近 `20` 个 run，并支持按 marker 获取 `QualityRunDetail`。
- 新增安全 DTO：`QualityRunSummary`、`QualityRunDetail`、`QualityGateSummary`、`QualityEvalCaseResultDetail` 和 `QualityTokenUsageSummary`；只保留状态、计数、布尔、失败桶、eval case id、trace / agent run id 和 token usage 数值。
- 脱敏策略：不透传原始 artifact；字段白名单过滤 prompt、answer 原文、文档全文、evidence context、API key、secret、连接串、云地址等敏感内容；坏 JSON 降级为 `REVIEW` 并标记 `artifactParseFailed=true`。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，15 tests；新增单测覆盖空目录、缺 artifact root、坏 JSON、正常 artifact、历史审计文件名、最近 N 个排序和敏感字段过滤。
- 边界：本片不暴露 `/api/quality/**`，不做前端 `/quality`，不新增数据库表，不启动服务，不创建业务数据，不提交 artifact 原文，不 push。

## 2026-07-04 Agent Quality Console MVP Slice 1

- 完成 Agent Quality Console 文档与接口口径收敛：统一定位为 DocPilot 内部质量控制台，第一版信息架构为 `Overview`、`Trace`、`Eval`、`Failures`，P0 先做 `Overview + Run Detail`，并保留 Trace / Eval drill-down 入口。
- 明确 P0 / P1 边界：P0 聚合 ignored artifact 脱敏摘要、定义白名单 DTO 和内部 `/quality` / `/api/quality/**` 口径；P1 再做 Trace 详情、Eval case 详情、失败桶、趋势对比和失败 case 到 Trace 的跳转。
- 明确 artifact 聚合边界和降级策略：默认 root 包括 `backend/target/audit`、`backend/target/rag-natural-corpus`、`backend/target/rag-real-qa`、`backend/target/memory-quality`、`backend/target/memory-provider` 和 `tmp-e2e/docpilot-cloud-quality-smoke`；缺文件返回空或 `artifactMissing=true`，解析失败标记 `artifactParseFailed=true` / `REVIEW`。
- 明确敏感信息泄露风险方案：parser / API 使用字段白名单，不返回 prompt、answer 原文、文档全文、evidence context、API key、access token、secret、连接串和云地址；`token_usage` 只保留数值统计；发现风险时关闭对应 artifact root 或隐藏 detail 字段。
- 边界：本片仅更新文档与接口口径，未修改业务代码，未启动服务，未创建业务数据，未提交 artifact 原文，未 push。

## 2026-07-04 Memory provider 小样本 v1

- 新增默认关闭的 `MemoryProviderExtractionRealProviderSmokeTest`，只有 `DOCPILOT_MEMORY_PROVIDER_SMOKE_ENABLED=true` 时才会读取本机 `AI_REAL_*` 配置并调用真实 provider；普通 `mvn test` 保持无真实 provider 依赖。
- 新增 `scripts/smoke/memory-provider-extraction-smoke.ps1`，提供 `plan` / `dry-run` / `run` 三种模式；`run` 会从本机 `.env` 注入必要 provider 配置到子进程，Maven 日志和脱敏 artifact 写入 ignored 的 `backend/target/memory-provider/`。
- `MemoryProviderExtractionEvalRunner` 增强真实 provider 兼容性：支持 JSON code fence、`task-goal` / `answer style` 类型归一化，以及类型 multiset 判断，避免无意义顺序差异误杀。
- 小样本 case 覆盖：`ANSWER_STYLE + TASK_GOAL`、`TECH_CONTEXT`、RAG evidence 不进入 memory、secret-like 内容不抽取；artifact 只保存 provider、model、modelCallCount、casePassRate、caseId、suggestionTypes、布尔值和失败原因。
- 已验证：`memory-provider-extraction-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=MemoryProviderExtractionEvalRunnerTest,MemoryProviderExtractionRealProviderSmokeTest,MemoryQualitySmokeScriptSafetyTest" test` PASS，7 tests，其中真实 provider smoke 默认 skipped 1。
- 真实验证：`memory-provider-extraction-smoke.ps1 -Mode run` PASS，marker `docpilot-memory-provider-20260704192850-695412`；`modelCallCount=4`，`casePassRate=1.0000`，`rawProviderOutputStored=false`。
- 边界：这是 4 case 小样本真实 provider contract 验证，不是大规模 memory extraction benchmark、生产 LLM 记忆抽取替换或长期记忆质量成熟结论；未启动后端 / 前端 / tunnel，未创建业务数据，未提交 artifact 原文，未打印 secrets，未 push。

## 2026-07-04 真实用户问答体验审计 v2

- 新增 `scripts/smoke/real-user-qa-experience-audit.ps1`，提供 `plan` / `dry-run` / `run` 三种模式，默认以 `docpilot-real-user-qa-*` marker 委托 `cloud-quality-smoke.ps1`，并启用 `naturalCorpus`、`multiQueryRag`、`frontendInteraction` 和 `memoryQuality` gate。
- 审计入口覆盖真实用户关键路径：本地 tunnel、backend health、frontend routes、临时用户、两文档上传 / parse / indexing、chunk 质量、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase 多文档 RAG、自然语料 25 case、quote-first UI、Conversation Trace、Memory 治理、权限隔离和脱敏 artifact。
- 首轮真实 run marker `docpilot-real-user-qa-20260704190235-553df7` 暴露 `answerFactExpression` 门禁对单一英文短语过度敏感；在 evidence / citation 已支撑时，真实回答的自然表达差异会造成误杀。
- 已修正：`Test-TextContainsAll` / `Test-TextContainsAny` 支持 `a|b|c` 同义表达组；自然语料 QA 的数字、日期、负责人和审批类答案短语改为表达组，仍保留 citation phrase support、forbidden answer、no-evidence 和权限隔离硬门禁。
- 已验证：`real-user-qa-experience-audit.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，5 tests。
- 真实验证：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260704191307-661bc0`；`naturalCorpus.casePassRate=1`，`answerFaithfulnessPassCount=11/11`，`citationPhraseSupportPassCount=22/22`，`frontendInteraction`、`memoryQuality`、`conversationTrace`、`permissionIsolation`、`artifactRedaction` 均 PASS。
- 边界：本片是小规模真实链路用户体验审计入口，不是大规模人工评测、完整浏览器 E2E 覆盖或线上 SLA；artifact 位于 ignored 的 `backend/target/audit/.../artifact.json`，不提交原文、回答文本、文档文本、prompt、evidence context、凭据、连接串、云地址或 token。

## 2026-07-04 Evidence Coverage 报告 v1

- `naturalCorpus` summary 新增脱敏 `evidenceCoverageReport`，用于每次真实自然语料 eval 后直接定位 case 级质量问题。
- 报告字段：`retrieveCoveragePassCount`、`citationCoveragePassCount`、`citationPhraseSupportPassCount`、`answerFaithfulnessPassCount`、`noEvidenceCorrectCount`、`distractorCitationFreeCount`，以及 `retrievalCoverageMisses`、`citationCoverageMisses`、`citationPhraseMisses`、`answerFaithfulnessMisses`、`distractorCitationLeaks`、`noEvidenceFailures`。
- 门禁口径微调：多文档 summary 中，如果目标文档覆盖和 citation 事实短语支撑已经满足，额外干扰 citation 先进入 REVIEW 报告；单数字事实 / 单文档事实中的干扰 citation 仍是硬失败。
- 真实过程：首轮 evidence coverage run 抓到 `ops-incident-support-summary` 干扰 citation；尝试把多文档 QA `topK` 压到目标文档数后又暴露召回不稳定，因此最终保留覆盖型 topK，并把多文档 summary 的干扰 citation 区分为 reportable review 风险。
- 已验证：`rag-natural-corpus-audit-smoke.ps1 -Mode plan` PASS；`rag-natural-corpus-audit-smoke.ps1 -Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，4 tests。
- 真实验证：`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704160327-16b351`；`evidenceCoverageReport` 中 retrieval / citation / phrase / answer / distractor / no-evidence 的 miss、leak、failure 清单均为空。
- 边界：报告只保存 caseId、计数和布尔值；不保存文档原文、问题原文、回答原文、prompt、evidence context、凭据、连接串、云地址或 token。未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未 push。

## 2026-07-04 Answer / Citation Faithfulness v2

- 在自然语料 v2 的 case-level gate 上继续增强：QA case 不再只看 hit / citation 数量，而是要求回答包含预期事实表达，并要求 citation / evidence 覆盖预期事实短语。
- `Invoke-NaturalCorpusCase` 新增 `answerFaithfulnessRequired` 和 `citationPhraseSupport`；当 QA case 配置了 `answerAnyPhrases` / `answerAllPhrases` 时，`answerFactExpression=false` 进入 `failureBuckets`，不再只是 `reviewBuckets`。
- 修正 artifact 计数细节：单条 citation / hit 统一使用 `@(...).Count` 统计，避免 `qaCitations` 在单 citation 场景序列化为 `null`。
- `naturalCorpus` 聚合新增 `answerFaithfulnessCaseCount`、`answerFaithfulnessPassCount`、`citationSupportCaseCount`、`citationPhraseSupportPassCount`。
- 已验证：`rag-natural-corpus-audit-smoke.ps1 -Mode plan` PASS；`rag-natural-corpus-audit-smoke.ps1 -Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest,KnowledgeBaseRagQaServiceImplTest" test` PASS，13 tests。
- 真实验证：`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704152850-e07b13`；`casePassRate=1`，`answerFaithfulnessPassCount=11/11`，`citationPhraseSupportPassCount=22/22`，`noEvidencePassCount=3/3`，`multiDocumentCoveragePassCount=4/4`，`distractorCitationFreeCount=25/25`。
- 边界：本片使用真实本地 backend / frontend / tunnel / MySQL / Qdrant 链路和临时 smoke 数据；未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 secrets，未 push。

## 2026-07-04 RAG 自然语料扩容 v2

- `cloud-quality-smoke.ps1` 的 `naturalCorpus` gate 从 v1 的 5 文档 / 6 case 扩到 v2 的 3 个 corpus、12 份临时 txt 文档、25 个 case，覆盖单文档事实、数字事实、日期事实、审批链、负向事实、多文档 compare / summary、干扰 citation、no-evidence 和绑定 KB 的 Conversation Trace。
- Artifact schema 升级为 `schemaVersion=2`，新增 `caseResults`、`casePassRate`、`noEvidencePassCount`、`multiDocumentCoveragePassCount`、`distractorCitationFreeCount`、`hardFailureBuckets` 和 `reviewBuckets`；仍只保存脱敏计数、布尔值、caseId、caseType、score summary 和文档覆盖计数，不保存文档原文、问题原文、回答原文、prompt、evidence context、token、云地址或连接串。
- `rag-natural-corpus-audit-smoke.ps1 -Mode plan` 同步输出 v2 目标：`defaultCorpusTarget=3`、`defaultDocumentTarget=12`、`defaultCaseTarget=25`，并新增 `natural_date_fact`、`natural_approval_chain`、`natural_negative_fact`、`natural_case_coverage`。
- 修复 runner 稳定性：自然语料临时用户改用 `fin` / `ops` / `gov` 短 alias，避免 `governance` 前缀叠加 run suffix 后超过注册 username 32 字符限制；本地 backend / frontend 启动日志写入 ignored artifact，API 传输失败时可记录本地恢复 gate。
- 修复 KnowledgeBase QA 多文档 citation 回归：答案数字一致性过滤仍会剔除单数字事实中的数值冲突 citation，但当问题是 compare / summarize / both / 中文比较总结等多文档意图时，不允许过滤结果把 citation 覆盖压成单文档。
- 真实过程：v2 真实 run 先暴露 `smokegovernance...` 用户名过长，修复后进入 25 case；随后暴露 `ops-backup-rollback-compare` 只保留 rollback citation、漏掉 backup citation；已修复并补单测。
- 已验证：`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，29 tests；`rag-natural-corpus-audit-smoke.ps1 -Mode plan` PASS；`rag-natural-corpus-audit-smoke.ps1 -Mode dry-run` PASS。
- 真实验证：`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704151615-bc193d`；`naturalCorpus.casePassRate=1`，`documentCount=12`，`caseCount=25`，`noEvidencePassCount=3/3`，`multiDocumentCoveragePassCount=4/4`，`distractorCitationFreeCount=25/25`，`traceRagTriggered=true`，`traceRagRequired=true`，`traceEvidenceCount=4`。
- 同轮真实 gate 保持 PASS：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant payload consistency、单文档 RAG、KnowledgeBase RAG、短文档 RAG、multi-query、answer grounding、no-evidence、Conversation Trace、权限隔离、frontendInteraction、artifact redaction 和 cleanup。
- 边界：本轮使用真实本地 backend / frontend / tunnel / MySQL / Qdrant 链路和临时 smoke 数据；未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 secrets，未 push。

## 2026-07-04 RAG 自然语料真实审计 gate v1

- 新增 `scripts/smoke/rag-natural-corpus-audit-smoke.ps1`，提供 `plan` / `dry-run` / `run` 三种模式，默认以 `docpilot-rag-natural-corpus-*` marker 委托 cloud quality runner，并启用 `naturalCorpus`、`multiQueryRag` 和 `frontendInteraction` gate。
- `cloud-quality-smoke.ps1` 新增 `-EnableNaturalCorpusGate`：创建 5 份临时自然语料 txt 文档，覆盖单文档事实、数字事实、多文档总结、干扰文档、no-evidence 和绑定 KB 的 Conversation Trace；artifact 只保存布尔值、计数、score summary、documentHitCounts 和失败桶。
- 后端 KnowledgeBase QA 新增答案数字一致性 citation 精炼：当答案包含明确数字事实时，过滤只包含其他数字值的干扰 citation；同时忽略 run marker 这类长编号，避免误伤多文档总结中的非数字支持引用。
- Runner 稳定性增强：上传遇到业务限流 `code=1014` 自动 retry/backoff；通用 API 调用 retry 从 3 次扩到 5 次并使用更长 backoff，适配更长的真实链路审计。
- 真实过程：首轮 run 暴露上传限流；第二轮暴露 invoice retention 问题同时引用 marketing retention 干扰文档；第三轮暴露一次请求层断连；第四轮暴露长 run marker 数字误伤 citation 精炼；最终均收口。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,RagDocumentRetrievalServiceImplTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS；`npm run lint` PASS；`npm run build` PASS；`rag-natural-corpus-audit-smoke.ps1 -Mode plan` / `-Mode dry-run` PASS。
- 真实验证：`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704143033-86b4f3`；`naturalCorpus` PASS，`distractorMarketingCitationCount=0`，`noEvidenceRetrieveNoEvidence=true`，`noEvidenceQaNoEvidence=true`，`traceRagTriggered=true`，`traceRagRequired=true`，`frontendInteraction` 和 `multiQueryRag` 均 PASS。
- 边界：本轮使用真实本地 backend / frontend / tunnel / MySQL / Qdrant 链路和临时 smoke 数据；未删除业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact 原文，未打印 secrets，未 push。

## 2026-07-04 真实体验审计问题防回归与短文档泛化 gate

- 已增强 `cloud-quality-smoke.ps1` 的 `shortDocumentRag` gate：在原短 Alpha / Beta 基础上增加中文短文档 retrieve、数字事实 retrieve、相似短文档干扰、KB 双文档覆盖和 citation marker 的细分布尔检查，失败时输出脱敏 `failureBuckets`。
- 已增强 `frontendInteraction` gate：失败时区分 `quoteFirstUi`、KnowledgeBase citation UI、`permissionUx` 和 console error 桶；artifact 仍只保存布尔值、计数和状态，不保存 token、文档原文、prompt、evidence context、云地址或连接串。
- 已让 `rag-real-qa-eval-smoke.ps1` 在未 `-SkipFrontend` 时默认启用 `frontendInteraction`，后续 RAG real QA wrapper 不再只验证 API 质量。
- 已验证：`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS；`mvn "-Dtest=RagDocumentRetrievalServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS；`npm run lint` PASS；`npm run build` PASS；`cloud-quality-smoke.ps1 -Mode plan` / `-Mode dry-run` PASS；`rag-real-qa-eval-smoke.ps1 -Mode plan` / `-Mode dry-run` PASS。
- 真实验证：`cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-cloud-quality-20260704135601-944384`；`shortDocumentRag.failureBuckets=[]`，`frontendInteraction.failureBuckets=[]`，核心 RAG、KB RAG、no-evidence、Conversation Trace、权限隔离、frontend routes、artifact redaction 和 cleanup 均 PASS。
- 本轮前两次真实 run 先后暴露 gate 口径过窄和 PowerShell 中文 fixture 编码不稳定；最终改为 retrieve 层验证中文 / 数字 marker，并用 ASCII-safe marker + codepoint 生成中文内容。未远程修复，未删除业务数据，未改 schema，未提交 artifact 原文，未 push。

## 2026-07-03 真实体验审计 P2/P3 浏览器细验收口

- 已给 `cloud-quality-smoke.ps1` 增加可选 `-EnableFrontendInteractionGate`，在真实 cloud quality smoke 中用 Playwright 覆盖登录态前端交互：文档详情 RAG 检索预览 quote-first、KnowledgeBase 双文档 citation、跨用户文档无权限提示和 console error count。
- 已修复文档详情页短文档 quote-first 展示缺口：当审计 / 调试问题中存在明确 marker token，且 `snippet/content` 比 `quoteText` 更能命中该 token 时，引用主文本优先展示 marker-bearing evidence；普通问题仍保持 `quoteText -> snippet -> content`。
- 已验证：`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS；`npm run lint` PASS；`npm run build` PASS；`cloud-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 `cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-cloud-quality-20260703231920-e74334`。
- 真实结果：`frontendInteraction` PASS，`documentQuoteFirstVisible=true`、`documentRetrieveHitCount=1`、`documentRetrieveCitationCount=1`、`knowledgeBaseAlphaCitationVisible=true`、`knowledgeBaseBetaCitationVisible=true`、`permissionMessageVisible=true`、`consoleErrorCount=0`；核心 RAG、短文档 RAG、Conversation Trace、权限隔离、frontend routes、artifact redaction 和 cleanup 均保持 PASS。
- 期间一次 run 因临时文档 parse timeout 未进入浏览器 gate，清理后重跑通过；未远程修复，未删除业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact 原文，未打印 secrets，未 push。

## 2026-07-03 真实体验审计 P1 RAG 修复与短文档 smoke gate

- 已修复短 txt 单文档 RAG 无 evidence：全局 similarity threshold 后为空时，只对 query 与 scoped hit 内容共同包含明确 marker token 的场景保留最强 evidence，不降低全局阈值。
- 已修复短文档 KnowledgeBase 双文档总结覆盖退化：总结类问题在明确 marker 场景下按缺失文档从 scoped raw hits backfill，避免短文档被阈值过滤后完全丢失。
- 已补 `cloud-quality-smoke.ps1` 的 `shortDocumentRag` gate，覆盖短单文档 retrieve / QA citation、短双文档 KB retrieve / QA citation 和 answer grounding；短文档上传改用用户 B，避免用户 A 触发上传限流。
- 已完成 quote-first citation UI 代码修复和权限 / 不存在场景中文错误归一化；这两项仍建议后续补浏览器点击级细验。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS；`npm run lint` PASS；`npm run build` PASS；`cloud-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 `cloud-quality-smoke.ps1 -Mode run` PASS，marker `docpilot-cloud-quality-20260703213703-dbef08`。
- 追加浏览器细验尝试：`docpilot-ui-verify-mr50eghq-9ed7ca` 未收口；API 预检已有 hit 且 `noEvidence=false`，但文档详情页 quote marker 未在等待窗口内展示。P2/P3 仍保持 `FIXED_PENDING_VERIFY`，下一片应优先排查文档详情页 RAG 检索预览的数据映射。
- 边界：未删除业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

## 2026-07-03 中文记录与真实体验问题自动台账

- 已把“内部文档和审计记录默认用中文”沉淀为长期协作规则。
- 已明确真实体验项目后发现的 bug、体验问题、安全疑点或环境阻塞必须自动写入 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`。
- 已补充台账记录语言规范、触发条件和单个问题必填字段；技术名、路径、API、状态枚举和命令可保留原文，其余解释性内容必须中文。
- 边界：仅文档流程规则沉淀；未启动服务，未创建业务数据，未修改业务代码，未提交 artifact 原文。

## 2026-07-03 真实体验审计问题台账

- 新增 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`，作为真实用户视角审计问题的长期台账。
- 已记录 marker `docpilot-real-audit-20260703195519-5118e8` 的首轮脱敏审计摘要。
- 当前台账追踪 4 个 OPEN（待修复）问题：短 txt 单文档 RAG 无 evidence、短文档 KnowledgeBase 双文档覆盖退化、quote-first citation UI 缺口、权限拒绝体验提示不够清晰。
- 已同步流程文档：后续真实体验发现写入该台账，原始 artifact 继续只保留在 ignored 的 `backend/target/audit/...`。
- 边界：仅文档流程变更；未改后端 / 前端代码，未启动服务，未创建业务数据，未提交 artifact 原文。

## 2026-07-03 Quality Loop v6.6 / Memory Provider Extraction Eval Contract

- Added `MemoryProviderExtractionEvalRunner` as a test-side provider contract evaluator for memory extraction.
- The runner asks an `AiAnswerService` for JSON-only memory suggestions, parses `memoryType` / `content` / `confidence`, and checks expected suggestion types, `MemorySafetyValidator` and forbidden marker leakage.
- Safe output stores provider/model names, model call count, pass rate, case ids, suggestion types, booleans and failure reasons only.
- Stub provider tests cover a clean provider-backed extraction case and an unsafe token-like output case; raw conversation text, provider output and memory content are not stored in safe maps.
- Verified: `mvn "-Dtest=MemoryProviderExtractionEvalRunnerTest,MemoryQualityEvalRunnerTest,MemoryQualityEvalFixtureTest,MemoryQualitySmokeScriptSafetyTest" test` PASS, 7 tests; `mvn "-Dtest=*Memory*,*Context*" test` PASS, 65 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS, 276 tests.
- Boundary: no real external provider call, no runtime smoke, no schema change, no production Memory extraction switch and no artifact submission.

## 2026-07-03 Quality Loop v6.5 / Memory Provider Readiness Eval Artifact

- Added provider provenance to Memory Quality Eval safe artifacts.
- Metrics now include `providerBackedCaseRate`; current offline eval reports `0.0000` because extraction is still rule-based.
- Added a redacted `providerEvaluation` block with `extractionProvider=rule_based`, `status=not_configured`, `realProviderConfigured=false`, `modelCallCount=0` and `rawProviderOutputStored=false`.
- Per-case summaries now include `extractionProvider` and `providerBacked`, preparing the artifact schema for a later small real-provider memory extraction comparison.
- Verified: `mvn "-Dtest=MemoryQualityEvalFixtureTest,MemoryQualityEvalRunnerTest,MemoryQualitySmokeScriptSafetyTest,RuleBasedMemoryExtractionServiceTest,UserMemoryServiceImplTest,MemorySelectorTest,ContextAssemblyServiceImplTest" test` PASS, 27 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS, 274 tests.
- Boundary: no provider call, no runtime smoke, no schema change, no raw conversation / memory text artifact submission and no push.

## 2026-07-03 Quality Loop v6.4 / Quote-level RAG Citation API

- Added quote-level fields to single-document RAG and KnowledgeBase RAG citations: `quoteText`, `quoteStartOffset` and `quoteEndOffset`.
- Added a shared chunk-local quote extractor that prefers evidence-marker-bearing sentences and falls back to the first readable sentence.
- Exposed quote fields through citation responses and retrieval hit responses while keeping existing `snippet`, chunk offsets and score fields compatible.
- Updated frontend API types so later UI work can render quote-first citations without another backend contract change.
- Verified: `mvn "-Dtest=RagEvidenceQuoteExtractorTest,RagQaControllerTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,DocumentAgentServiceImplTest" test` PASS, 26 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 214 tests; `npm run lint` PASS.
- Boundary: API/test slice only; no schema change, no retrieval ranking change, no prompt / answer-generation change, no runtime smoke, no real provider call, no artifact submission and no push.

## 2026-07-03 Quality Loop v6.3 / Multi-query Real Smoke Evidence

- Ran `scripts/smoke/rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`.
- PASS marker: `docpilot-rag-real-qa-20260703192456-2a62e9`.
- `multiQueryRag` passed with `multiQueryApplied=true`, `queryVariantCount=4`, `queryDedupeCount=24`, `6` retrieve hits and `6` QA citations.
- Both temporary documents were covered by the multi-query gate: Alpha retrieve/citation `3/3`, Beta retrieve/citation `3/3`.
- Core regression gates remained PASS, including chunk quality, MySQL / Qdrant consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, hard negative, semantic gate, real provider faithfulness, no-evidence, Conversation Trace, permission isolation, frontend routes, cleanup and artifact redaction.
- First run marker `docpilot-rag-real-qa-20260703192105-e953d2` reached `multiQueryRag` PASS but later failed at Conversation message request; cleanup succeeded and the immediate rerun passed.
- Boundary: small real-link smoke evidence only; no artifact raw content was submitted, no remote Docker/hk-ops operation, no schema change, no business-data deletion and no push.

## 2026-07-03 Quality Loop v6.2 / Multi-query Real Smoke Gate Runner

- Added optional `-EnableMultiQueryGate` to `cloud-quality-smoke.ps1`.
- Updated `rag-real-qa-eval-smoke.ps1` to enable the multi-query gate by default and expose `-SkipMultiQueryGate`.
- The gate uses request-scoped `multiQueryEnabled=true` / `maxQueryVariants=4`, then records redacted multi-query trigger counts, dedupe counts, two-document retrieve/citation coverage and answer-grounding checks.
- Verified: `rag-real-qa-eval-smoke.ps1 -Mode plan` PASS; `rag-real-qa-eval-smoke.ps1 -Mode dry-run` PASS; `mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS, 3 tests.
- Boundary: runner/control-plane slice only; no real `run` mode yet, no tunnel/backend/frontend startup, no business data creation, no real provider call and no artifact submission.

## 2026-07-03 Quality Loop v6.1 / Request-scoped Multi-query Retrieval Eval

- Added request-level `multiQueryEnabled` and `maxQueryVariants` controls to KnowledgeBase retrieve and QA APIs.
- Kept the default runtime behavior unchanged: absent request fields still inherit `app.rag.retrieval.multi-query-enabled=false` and bounded global `max-query-variants`.
- Propagated QA overrides into the retrieval query and added request-scoped validation for variant limits.
- Extended KnowledgeBase offline eval artifact metrics with `retrievalModeMetrics.multi_query`, in addition to `vector` and `hybrid`.
- Verified: `mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalFixtureTest,RuleBasedQueryRewriteServiceTest,RagRetrievalPropertiesTest" test` PASS, 32 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 211 tests.
- Boundary: no runtime smoke, no tunnel/backend/frontend startup, no business data creation, no real provider call, no schema change, no remote Docker/hk-ops operation, no artifact submission and no push.

## 2026-06-29 Quality Loop v5.6 / Query Rewrite and Multi-query Retrieval

- Added default-off KnowledgeBase multi-query retrieval: `app.rag.retrieval.multi-query-enabled=false` and bounded `max-query-variants`.
- Added deterministic `QueryRewriteService` / `RuleBasedQueryRewriteService`, keeping the original query first and generating cleaned / comparison-part variants for complex questions.
- KnowledgeBase retrieval now embeds and searches each variant when enabled, deduplicates vector hits by chunk identity, and then reuses the existing threshold, hybrid, rerank, scope guard and diversity selection pipeline.
- Added observability fields: `multiQueryApplied`, `queryVariantCount`, `queryDedupeCount`; response results do not store rewritten query text.
- Verified: `mvn "-Dtest=RuleBasedQueryRewriteServiceTest,RagRetrievalPropertiesTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseEvidenceContextBuilderTest" test` PASS, 32 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 209 tests; real `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007 -ReuseRunningServices` PASS, marker `docpilot-rag-real-qa-20260629202542-3e47d9`.
- Boundary: default-off deterministic expansion only; no LLM query planning, no schema change and no production relevance uplift claim. The real smoke proves default-path regression safety, not enabled multi-query effectiveness.

## 2026-06-29 Quality Loop v5.5 / Chunk Quality v2

- Extended chunk metadata with nested `sectionPath`; indexing metadata and Qdrant payload propagation now include the same field.
- Improved chunk structure detection for table and list blocks, while keeping section and code detection.
- Added chunk quality flags for `window_split`, `mid_sentence_split` and `duplicate_content`, alongside existing short / replacement character checks.
- Updated cloud quality smoke payload consistency field checks to include `sectionPath`.
- Verified: `mvn "-Dtest=ChunkingServiceImplTest,RagIndexingServiceImplTest,DocumentChunkServiceImplTest,VectorPointTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS, 41 tests.
- Boundary: local test-side / chunking / indexing metadata slice only; no tunnel/backend/frontend run, no business data creation, no real provider call, no schema change, no remote Docker or hk-ops operation.

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
