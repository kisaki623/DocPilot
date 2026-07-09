# Agent Quality Console 求职级升级路线图

> 本文是 Agent Quality Console 后续自驱迭代的长期事实源。当前任务只看 `CURRENT_TASK.md`，已完成事实写入 `STATE.md`，简短进度写入 `PROGRESS_LOG.md`。

## 1. 定位

Agent Quality Console 是 DocPilot 的内部 AI 质量控制台，用来把 RAG、Memory、Agent、Eval、Trace 和真实链路 smoke 串成一个可观测、可复现、可回归的质量闭环。

它不是企业级 APM、告警系统、多租户运营后台或独立商业观测平台。求职级目标是让面试官能清楚看到：系统如何发现 AI 质量问题、如何定位到 trace / eval case / failure bucket、如何避免敏感信息泄露，以及如何用真实链路回归证明修复有效。

## 2. 当前基础

已完成能力：

- `/quality` 内部页面已有 Overview + Run Detail。
- `GET /api/quality/runs` 和 `GET /api/quality/runs/{marker}` 已返回脱敏 summary / detail。
- 后端 artifact 聚合已支持白名单 root、坏 JSON 降级、嵌套 `gates.*` 和安全 metrics / flags。
- 轻量 Eval Case JSON + Runner 已存在，结果可带 `traceId` / `agentRunId`。
- 真实链路 audit 已证明 Console 能看到最新 run、gate signal 和 eval case signal。
- parser / DTO / API 已采用字段白名单，不返回 prompt、answer 原文、文档全文、evidence context、API key、token、secret、连接串或云地址。
- token usage 可保留为数值统计，例如 prompt tokens、completion tokens、total tokens、estimated cost。

主要差距：

- Trace drill-down v2 已能定位 trace reference，但还不是完整链路瀑布图；下一轮需要展示用户请求、Agent step、RAG retrieve、tool call、model call、citation 和 failure bucket 的脱敏摘要关系。
- Eval catalog 已从 3 个 case 扩到 7 个，并已有 owner、risk、version、source issue、verified marker 和 remediation hint；下一轮要把它继续推进成长期评测资产，补齐 case 分层、评分规则解释、失败历史和按风险级别的回归策略。
- Run comparison 已能对比两次 run，但趋势分析还偏弱；下一轮要能观察最近 N 次 run 的 case pass rate、失败桶、token cost、latency 和反复失败 case。
- 面试故事已经能讲“小样本真实链路质量闭环”，但如果要更像求职级内部质量控制台，需要把 Trace 深度、Eval 资产化和 Trend 视角连成一条清晰演示路径。

## 2.1 2026-07-05 三线升级收口

本轮之后 Agent Quality Console 的后续自驱循环只围绕三条主线推进：

1. **Trace Drill-down v3**：从“能定位 ID”升级为“能看脱敏链路瀑布图”。
2. **Eval Asset v2**：从“有 case catalog”升级为“有长期维护语义的质量题库”。
3. **Quality Trend v1**：从“两次 run 对比”升级为“最近 N 次质量趋势”。

统一收口标准：

- 继续保持 artifact-only，不新增数据库表，除非用户单独确认 Phase 7 持久化。
- 继续使用字段白名单，不返回 prompt、answer 原文、文档全文、evidence context、真实用户输入、API key、token、secret、连接串或云地址。
- token usage、latency、cost、case pass rate、failure bucket count 只允许作为数值或枚举摘要展示。
- 每个切片都必须有对应后端单测、前端 lint/build 或浏览器 smoke；真实用户体验结论必须用真实链路 smoke / audit 收口。
- 每个切片完成后回写 `CURRENT_TASK.md`、`STATE.md`、`PROGRESS_LOG.md`，并做精确 commit，不 push。

## 2.2 2026-07-09 ABC 求职级增强循环

本轮在既有 Trace / Eval / Trend 基础上重新收口三条可连续推进的求职级路线，优先服务“真实排查能力”，不继续堆 raw artifact 字段。

### A：Agent Tool / Trace drill-down

目标：Run Detail 中能读懂一次 Agent 请求的脱敏链路，而不是只看到 gate 名称和若干计数。

2026-07-09 追加状态：A1 / A2 已完成。A1 已把 `knowledgeBaseAgent` gate 转成可打开的 `knowledge-base-agent-runtime` trace reference，并在 `/quality/trace` 链路瀑布图展示 KB Agent search / grounded answer 的脱敏属性。A2 已把 Run Detail 的失败分桶与 Gate / Eval / Trace 数量、建议动作和 Trace 入口联动起来；失败或 REVIEW eval case 如果没有 traceId / agentRunId，会明确显示“暂无链路引用”，用于暴露 trace 覆盖缺口。

最小实现：

- 从现有 quality artifact、gate metrics、eval case result 和 trace reference 中聚合安全链路摘要。
- 展示 selector decision、selected tool、tool status、RAG hit / citation count、documentHitCounts、noEvidence、permission negative 和 failure bucket。
- KB Agent search route 和 grounded answer route 必须能区分：`search_tool -> knowledge_base_search_tool` 与 `rag_tool -> knowledge_base_rag_qa`。

验收标准：

- `/quality` Run Detail 或 Trace 入口能定位 KB Agent search / answer 两类 step。
- citation 数、两文档覆盖、no-evidence 和跨用户拒绝能以安全摘要展示。
- 不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、API key、token、secret、连接串或云地址。
- `mvn "-Dtest=*Quality*" test` PASS；前端改动需 `npm run lint`、`npm run build` 和 Playwright `/quality` 桌面 / 移动端无 console error、无横向溢出。

### B：Eval Case 资产化

目标：让 eval case 成为长期质量资产，能说明“验什么、为什么验、失败后怎么修、如何回归”。

最小实现：

- 在现有 JSON catalog 和 Quality API 白名单基础上补充能力层、风险等级、评分摘要、失败桶、最近验证 marker、修复建议和回归策略。
- Eval result 与 traceId / agentRunId / marker 关联；无法关联时明确显示“暂无链路引用”。
- REVIEW 和 FAILED 分开展示：REVIEW 是质量风险，FAILED 是核心失败或安全门禁失败。

验收标准：

- 至少覆盖 Agent search、KB Agent answer、RAG no-evidence、citation grounding、Memory governance、Parser real-chain 这些已有资产。
- 每个失败 / REVIEW case 有 failure bucket、模块标签和建议动作。
- 失败 case 优先能跳到 Trace；无法跳转时不显示空白或误导链接。
- artifact 和 API 不保存或返回 question 原文、prompt、answer 原文、文档全文、evidence context 或真实用户输入。
- `mvn "-Dtest=*Quality*,*Eval*" test` PASS；相关 smoke 的 `plan / dry-run` PASS。

### C：Quality Console 趋势分析

目标：让 Console 能回答“最近质量是在变好、变差，还是同一类问题反复出现”。

2026-07-09 追加状态：C1 已完成。趋势面板已把通过 / 复查 / 失败运行展示为 `x / totalRuns`，token / cost 缺样本显示“暂无统计 / 暂无样本”，失败 / 复查 TopN 改为带模块标签、说明和建议动作的卡片；Parser 失败桶独立归类，artifact JSON 解析坏文件仍归到 Env。本阶段仍不改后端 API、不新增数据库表、不展示 raw artifact 或敏感原文。

最小实现：

- 基于最近 N 个 ignored 脱敏 artifact 聚合，不新增数据库表。
- 聚合 totalRuns、pass/review/fail 数量、case pass rate、top failure / review bucket、avg / p95 latency、token usage、estimated cost 和 cost per successful run。
- token / cost / latency 字段缺失显示“暂无统计”，只有明确数值为 0 时才展示 0。

验收标准：

- 所有比率展示分子 / 分母，例如 `17 / 20`。
- failure / review bucket 带模块标签：`RAG`、`Citation`、`Tool`、`Memory`、`Parser`、`Security`、`Env`、`Unknown`。
- 每个 TopN bucket 有简短说明和建议动作。
- 坏 JSON、缺 artifact、不同 schema 只能降级为 REVIEW 或空状态，不能让页面崩溃。
- `mvn "-Dtest=*Quality*" test` PASS；前端 lint/build 和 Playwright `/quality` 桌面 / 移动端检查 PASS。

统一不做：

- 不做企业级 APM、告警系统、多租户后台、复杂 BI 报表或复杂 planner。
- 不新增数据库表，除非 artifact 聚合已无法满足跨机器长期历史和权限审计，并由用户单独确认。
- 不读取 raw artifact 原文，不展示敏感原文，不提交 artifact、日志、截图或临时数据。

## 3. 分阶段路线

### Phase 0：路线图沉淀

目标：把求职级升级计划写进项目文档，形成后续自驱循环依据。

验收标准：

- 本文存在并说明目标、差距、阶段、验收标准、停止条件。
- `docs/README.md` 能路由到本文。
- `CURRENT_TASK.md` 指向下一片可执行任务。
- `git diff --check` 和中文乱码扫描通过。

### Phase 1：Trace Drill-down v2

目标：让失败 eval case 能定位到具体 Trace / Agent Run。

2026-07-05 追加状态：DONE。已在 `/quality` Trace 定位行增加“打开”链接，并新增 `/quality/trace` 最小详情页；该页复用现有 QualityRunDetail，只展示脱敏 trace reference、关联 gate / eval case、failure / review buckets 和安全 metrics / flags，不新增后端 API、不读业务库。

最小实现：

- 在 Quality detail 中补充安全 trace reference 摘要：`traceId`、`agentRunId`、`conversationId`、`caseId`、`gateName`、`failureBucket`、`status`。
- `/quality` Run Detail 中为失败 / REVIEW case 提供“复制 ID / 打开 Trace 入口”。
- 如果现有 trace detail API 不足，第一版先做 ID 定位和失败桶过滤，不新增数据库表。

验收标准：

- 任意失败或 REVIEW case 都能在 Run Detail 中定位 traceId 或 agentRunId。
- API 不返回 prompt、answer 原文、文档全文或 evidence context。
- `mvn "-Dtest=*Quality*" test` PASS。
- `/quality` 浏览器 smoke 无 console error、无移动端横向溢出。

### Phase 2：Failure Triage v1

目标：让 Console 从“展示结果”升级为“定位问题”。

失败桶 taxonomy：

- `RAG_RETRIEVAL_MISS`
- `CITATION_UNSUPPORTED`
- `DISTRACTOR_CITATION`
- `NO_EVIDENCE_FALSE_POSITIVE`
- `MEMORY_CONFLICT`
- `TOOL_FAILURE`
- `PERMISSION_REGRESSION`
- `FRONTEND_UX`
- `ENV_BLOCKED`

验收标准：

- Run Detail 能按 status、failure bucket、case tag、gate name 筛选。
- 能区分 FAILED、REVIEW、BLOCKED。
- 失败桶展示只包含脱敏原因、caseId、traceId / agentRunId 和计数。
- 前端 lint / build PASS。

### Phase 3：Eval Case Catalog v1

目标：让 eval 不只是 smoke 聚合，而是可解释的评测资产。

2026-07-05 追加状态：DONE。Eval Catalog 已补 `caseVersion`、`owner`、`lastUpdated` 和 `riskLevel` 安全元数据；`/api/quality/eval-cases` 与 `/quality` 卡片同步展示，离线 eval runner 对这些 catalog 元数据保持兼容。

2026-07-05 追加状态：DONE。Real Audit Case 扩容 v1 已把默认 case 从 3 个扩到 7 个，新增短文档单文档 RAG evidence、短文档 KB 双文档覆盖、summary 干扰 citation 裁剪和 Quality Console backend health 四类真实审计沉淀 case；`sourceIssueIds` 只暴露 `REA-...` 脱敏编号，仍不返回 question、expectedBehavior、prompt、answer 原文、文档全文或 evidence context。

2026-07-05 追加状态：DONE。Eval Catalog Remediation Hint v1 已为 7 个默认 case 增加 `lastVerifiedMarker` 和 `remediationHints`；Console 可以展示每个 case 最近验证 marker 和修复排查方向，字段继续使用白名单过滤，不展示真实 artifact、prompt、answer 原文、文档全文或 evidence context。

2026-07-05 追加状态：DONE。Eval Catalog 筛选 v1 已在 `/quality` 前端增加 risk、owner 和 latest status 本地筛选；不新增后端 API、不新增数据库表。浏览器 route smoke 已验证桌面和移动端无 console error、无横向溢出。

最小实现：

- 继续使用 JSON 文件，不新增数据库表。
- Console 展示 case catalog 的安全摘要：caseId、tags、case type、scoring rule 摘要、最后一次状态。
- Eval result 关联 run marker、traceId 或 agentRunId。
- question 只允许使用非敏感 synthetic / smoke 问题，不展示真实用户隐私或文档全文。

验收标准：

- Console 能说明当前有哪些 eval case、每类 case 验什么、最近一次是否通过。
- artifact 不保存 answer 原文、prompt、文档全文、evidence context。
- plan / dry-run 脚本 PASS，离线 Quality tests PASS。

### Phase 4：Run Comparison v1

目标：能证明修复前后质量是否变好。

最小实现：

- 支持 latest run 与 selected previous run 的对比。
- 展示 gate status 变化、case pass rate 变化、新增失败桶、已修复失败桶、关键 token / latency 数值变化。
- 不做复杂趋势图，第一版用表格和 delta 即可。

验收标准：

- 能展示最近两次 run 的差异。
- 至少能用一条真实历史问题演示从 REVIEW / FAIL 到 PASS 的变化。
- 对比 API 仍只返回白名单字段。

### Phase 5：Cost / Latency / Model Summary v1

目标：补齐 AI 系统成本和性能观测。

允许字段：

- `promptTokens`
- `completionTokens`
- `totalTokens`
- `estimatedCost`
- `modelCallCount`
- `toolCallCount`
- `latencyMs`
- `retryCount`

验收标准：

- Run Detail 能回答本次评测用了多少 token、成本、模型调用、工具调用和大致耗时。
- 不返回 system prompt、user prompt、answer 原文或 provider 原始输出。
- 单测覆盖敏感字段过滤和 token usage 数值保留。

### Phase 6：真实体验审计集成 v2

目标：把真实用户体验发现的问题纳入 Console 质量闭环。

最小实现：

- real audit 脱敏 artifact 能被 Console 自动识别。
- 真实体验问题仍写入 `REAL_EXPERIENCE_AUDIT_LOG.md`。
- Console 展示 real audit 的核心 gate：frontend route、RAG、KB RAG、Conversation Trace、Memory、permission isolation、artifact redaction。
- 浏览器验证 `/quality?autoload=1` 能看到最新真实 run。

验收标准：

- 跑一次真实 audit 后，Console 自动出现该 run。
- 若有失败，Run Detail 能进入对应失败桶并定位 traceId / agentRunId。
- 文档回写完整，不提交 artifact 原文。

### Phase 7：可选持久化

默认不做。

只有出现以下需求时，再单独请求用户确认是否新增 `quality_eval_run` / `quality_eval_gate` / `quality_eval_case_result`：

- artifact 扫描明显变慢。
- 需要跨机器长期保存质量历史。
- 需要多人协作标记 REVIEW 状态。
- 需要稳定 CI / release gate 查询历史趋势。

### Phase 8：求职展示打磨

目标：形成可讲清楚的面试故事。

2026-07-05 状态：DONE。README、Project Interview Brief、Resume Bullets 和 Interview QA 已同步 Agent Quality Console、RAG / Memory 质量闭环和真实审计回归故事；对外口径保持“内部质量控制台 + 小样本真实链路证据”，不写成企业级 APM、线上 SLA 或大规模 benchmark。

最小实现：

- README / showcase 中只写已验证事实，不夸大成商业 SaaS 或线上 SLA。
- 准备 3 个故事：RAG groundedness、Memory trace、Eval regression gate。
- 每个故事都能对应真实 smoke / audit marker、Console 页面和脱敏文档记录。

验收标准：

- 能现场演示 `/quality`。
- 能展示至少一个“发现问题 -> 定位 -> 修复 -> 回归通过”的闭环。
- 对外材料保持克制，不把小样本 smoke 写成大规模 benchmark。

### Phase 9：Trace Drill-down v3

目标：把 Trace 从“定位 reference”升级为脱敏链路瀑布图，让面试官能看到一次 Agent / RAG 请求内部发生了什么，但仍不暴露原文。

2026-07-05 状态：DONE。后端新增 `QualityTraceStepDetail` 并挂到 `QualityTraceReference.steps`；artifact 聚合 service 会从 eval case 的安全 metrics / flags / buckets 推断 `eval_case`、`agent_step`、`rag_retrieve`、`tool_call`、`model_call`、`citation` 和 `failure_bucket` 步骤摘要。前端 `/quality/trace` 已展示“链路瀑布图”面板。本阶段仍不读业务库、不新增 API、不新增数据库表、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入或凭据。

最小实现：

- 在现有 QualityRunDetail / traceReferences 基础上聚合安全 step 摘要，不读业务库、不新增数据库表。
- 展示链路顺序：run -> gate -> eval case -> trace reference -> agent step -> RAG retrieve -> tool call -> model call -> citation -> failure bucket。
- 每个 step 只展示白名单字段：stepType、status、durationMs、token usage 数值、toolName、modelName 安全标识、retrieval hit count、citation count、bucket、caseId、traceId / agentRunId。
- `/quality/trace` 第一版使用表格或 timeline 块，不做复杂图形编辑器。

验收标准：

- 一个失败或 REVIEW case 能打开 `/quality/trace`，看到对应脱敏链路摘要。
- Trace 页面不展示 prompt、answer 原文、文档全文、evidence context 或真实用户输入。
- `mvn "-Dtest=*Quality*" test` PASS；`npm run lint` PASS；`npm run build` PASS。
- Playwright 打开 `/quality/trace` 桌面和 `390px` 移动端无 console error、无横向溢出。

### Phase 10：Eval Asset v2

目标：把 Eval Catalog 从“case 列表”升级为长期质量资产，能解释每个 case 为什么存在、属于什么风险、失败后如何回归。

2026-07-05 状态：DONE。7 个默认 eval catalog case 已补 `caseLayer`、`riskGate`、`scoringSummary`、`regressionPolicy` 和 `failureHistoryMarkers`；后端 catalog parser / DTO / API 与前端 Eval Catalog 同步展示这些安全字段。failure history 只保存脱敏 marker、status 和 issue id 摘要，不保存问题原文、回答原文、文档原文或 evidence context。

最小实现：

- 继续使用 `agent-quality-eval-cases.json`，不建表。
- 增加安全元数据：caseLayer、riskGate、scoringSummary、regressionPolicy、failureHistoryMarkers。
- `failureHistoryMarkers` 只保存脱敏 marker、状态和 issue id，不保存问题原文、回答原文、文档原文或 evidence context。
- `/quality` Eval Catalog 展示 case 分层、风险门禁、评分摘要和最近失败 / 修复历史。

验收标准：

- 7 个默认 case 都能说明：验什么、风险级别、失败时归到哪个 bucket、需要如何回归。
- 后端 catalog parser 对新增字段继续使用白名单和安全 identifier 过滤。
- 离线 eval runner 与旧 artifact 兼容。
- `mvn "-Dtest=*Quality*" test` PASS；`npm run lint` PASS；`npm run build` PASS。

### Phase 11：Quality Trend v1

目标：基于最近 N 个脱敏 artifact 给出质量趋势，让 Console 不只看单次 run，也能回答“这个系统最近是在变好还是反复退化”。

2026-07-05 状态：DONE。后端新增 `GET /api/quality/trends?limit=20` 和趋势 DTO，基于最近 N 个 parsed Quality detail 聚合状态分布、failure / review bucket、平均 casePassRate、token / cost、latency / duration 和反复失败 case；前端 `/quality` 新增 `Quality Trend` 面板。该阶段仍保持 artifact-only、不新增数据库表、不展示原始 artifact 或敏感原文。

最小实现：

- 后端在现有 artifact 聚合 service 中增加最近 N 次趋势摘要，默认 N=20，不新增数据库表。
- 趋势指标：status distribution、case pass rate、failure bucket count、token total、estimated cost、latency / duration、repeated failing cases。
- 前端 `/quality` 增加轻量 Trend 面板，优先用折线 / 小表格 / badge 展示，不做复杂 BI。
- 趋势只使用现有 Quality DTO 的安全字段，不读取或展示原始 artifact。

验收标准：

- `/quality` 能展示最近 N 次 run 的状态趋势、失败桶趋势、case pass rate 趋势、成本 / 耗时趋势和反复失败 case。
- 坏 JSON、缺 artifact、不同 artifact schema 不会导致趋势面板崩溃，最多降级为 REVIEW 或空趋势。
- `mvn "-Dtest=*Quality*" test` PASS；`npm run lint` PASS；`npm run build` PASS。
- Playwright 打开 `/quality` 桌面和 `390px` 移动端无 console error、无横向溢出。

## 4. 自驱循环规则

当用户明确说“按 Agent Quality Console 路线图继续自驱循环”“连续做直到完成”或类似表达时，后续 agent 按本文和 `CURRENT_TASK.md` 推进：

1. 读取 `AGENTS.md`、`docs/README.md`、`STATE.md`、`CURRENT_TASK.md`、`CONSTRAINTS.md`、`PROGRESS_LOG.md` 和本文。
2. 检查 `git status --short`、`git diff` 和敏感配置边界。
3. 只选择当前最小可交付 Slice。
4. 说明目标、涉及文件、验证方式和不做事项。
5. 实现、测试、真实链路验证、自审、文档回写。
6. 精确 `git add`，一行 conventional commit，不使用 `git add .`，不 push。
7. 若无阻塞且大目标未完成，继续下一片。

必须停止并向用户确认的情况：

- 需要新增数据库表。
- 需要改变核心业务流程。
- 发现可能泄露 prompt、answer 原文、文档全文、evidence context、API key、secret、连接串或云地址。
- 测试大面积失败或连续真实链路验证失败。
- 需要远程 Docker 启停 / 重启 / 迁移、删除业务数据、改 schema、清空 collection、改云资源。
- 需要大规模或高成本真实 provider 调用。
- 需要 push。
- 需要产品取舍或 UI 范围明显超过 MVP。

## 5. 明确不做

- 不做企业级 APM。
- 不做告警系统。
- 不做多租户质量后台。
- 不做复杂权限矩阵。
- 不默认新增数据库表。
- 不展示 prompt、answer 原文、文档全文、evidence context。
- 不打印或提交 API key、token、secret、连接串、云地址。
- 不把 Agent Quality Console 写成独立大平台。
- 不把当前 smoke / eval 结果吹成线上 SLA 或大规模生产 benchmark。
