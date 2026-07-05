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

- Trace drill-down 仍偏浅：失败 case 还不能稳定跳到具体 trace / agent run 的定位视图。
- Failure triage 还只是桶列表，缺少稳定 taxonomy、筛选和“新失败 / 已修复失败”视角。
- Eval case 还不像完整评测资产：缺少 case catalog、case version、scoring rule 摘要和失败归因说明。
- Run comparison 还不明显：缺少修复前后质量差异展示。
- 成本和性能观测还偏弱：token usage 已有，但 model call count、tool call count、latency、retry 等摘要还不完整。
- 面试故事还需要闭环：最好能演示“case 失败 -> Console 定位 -> 修复 -> 新 run 对比通过”。

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

最小实现：

- README / showcase 中只写已验证事实，不夸大成商业 SaaS 或线上 SLA。
- 准备 3 个故事：RAG groundedness、Memory trace、Eval regression gate。
- 每个故事都能对应真实 smoke / audit marker、Console 页面和脱敏文档记录。

验收标准：

- 能现场演示 `/quality`。
- 能展示至少一个“发现问题 -> 定位 -> 修复 -> 回归通过”的闭环。
- 对外材料保持克制，不把小样本 smoke 写成大规模 benchmark。

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
