# CHANGELOG_CODING.md

记录 Codex 协作过程中的关键变更。不要把它写成业务功能宣传页；每条记录都应说明目标、范围、验证和遗留问题。

## 2026-05-12 - 初始化 Codex 协作文档与 TODO 看板

### 本轮目标

只做项目体检、文档初始化和 TODO 协同机制建立，为后续 Codex 接手提供固定入口。本轮不修改业务代码、不改接口逻辑、不改依赖版本、不改数据库结构、不改配置逻辑。

### 阅读过的关键文件

- `README.md`
- `backend/README.md`
- `frontend/README.md`
- `docs/ai-dev/STATE.md`
- `docs/ai-dev/PROJECT.md`
- `docs/ai-dev/TASKS.md`
- `docs/ai-dev/CONSTRAINTS.md`
- `docs/ai-dev/HANDOFF.md`
- `docs/ai-dev/SHOWCASE.md`
- `backend/pom.xml`
- `frontend/package.json`
- `docker-compose.demo.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/sql/`
- `deploy/`
- `git status --short`
- `git log --oneline -5`
- `git diff --stat`

### 新增 / 更新文档

- 新增 `AGENTS.md`：后续 agent 的项目规则、启动验证命令、协作约束和面试口径。
- 新增 `docs/CODEX_HANDOFF.md`：当前真实状态、功能边界、风险和接手路径。
- 新增 `docs/TODO_NEXT.md`：Codex 协作看板，初始化 8 个最小可执行任务。
- 新增 `docs/CHANGELOG_CODING.md`：记录本轮文档初始化。

### 本轮没有修改业务代码

本轮只创建协作文档，不修改后端源码、前端源码、接口逻辑、依赖版本、数据库脚本或配置逻辑。当前工作区已有的业务文件改动来自本轮之前，未在本轮处理或回滚。

### 当前发现的主要问题

- 工作区存在大量未提交改动和未跟踪文件，后续每轮必须先看 `git status` / `git diff`。
- `docs/ai-dev/HANDOFF.md` 等历史文档存在乱码和阶段漂移风险。
- README / SHOWCASE / STATE / HANDOFF 中的 eval 指标存在明确冲突，不只是“可能漂移”：README 记录 `answerSuccessRate=57.143%`、`citationHitRate=46.154%`；STATE 记录 `answerSuccessRate=50%`、`citationHitRate=50%`；最新 artifact JSON 记录 `answerSuccessRate=90%`、`citationHitRate=100%`。需要通过 T001a/T001b 定位权威 artifact 后统一引用。
- 项目功能已较多，但必须保持克制口径：轻量检索增强不是完整向量 RAG，最小 Agent 不是成熟多 Agent 平台，PDF 解析不是主能力。
- 本地 / 云中间件、真实模型密钥、端口占用仍是后续验证的主要不确定项。

### 下一步建议

优先执行 `T000：审计当前工作区未提交改动 + 敏感信息检查`。完成后再执行 T001a/T001b 收敛 eval 指标证据链，随后执行最小 smoke 验证，确认当前仓库在本地仍能构建、测试和演示。

## 2026-05-12 - T000b 敏感信息脱敏与协作文档入库策略修正

### 本轮目标

只做安全脱敏和 `.gitignore` 协作文档规则修正，不做业务功能开发。

### 修改文件

- `.gitignore`
- `README.md`
- `backend/README.md`
- `backend/.env.cloud.example`
- `backend/src/main/resources/application-local.yml`
- `docs/ai-dev/CONSTRAINTS.md`
- `docs/TODO_NEXT.md`
- `docs/CHANGELOG_CODING.md`

### 脱敏信息类型

- 将公开文档中的真实公网 IP 替换为 `<CLOUD_HOST>`。
- 将 cloud example 文件中的真实公网 IP 替换为 `<YOUR_CLOUD_HOST>`。
- 将本地 Spring 配置中的真实公网 IP 替换为 `${CLOUD_HOST:localhost}`。

### 协作文档入库策略

- 保留 `docs/ai-dev/**` 既有放行规则。
- 新增 `docs/CODEX_HANDOFF.md`、`docs/TODO_NEXT.md`、`docs/CHANGELOG_CODING.md` 的 `.gitignore` 例外规则。
- 暂不放行 `docs/agent-upgrade-roadmap.md`，等待用户确认其是否属于正式规划文档。

### 明确未做事项

- 未修改后端业务代码。
- 未修改前端业务代码。
- 未修改 `backend/.env`。
- 未执行 `git add` / `git commit` / `git push`。

## 2026-05-13 - T002a AI 问答 / SSE 后端验证结果记录

### 本轮目标

只记录 T002a-verify 的真实验证结果，不修改业务代码、测试代码或配置文件，不执行 Git 暂存、提交或推送。

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过，Maven 输出 `BUILD SUCCESS`。
- `cd backend; mvn test -DskipITs`：通过，Maven 输出 `BUILD SUCCESS`。
- 测试统计：`Tests run: 141, Failures: 0, Errors: 0, Skipped: 0`。
- 测试日志中出现 SSE 兜底、限流、Redis 降级、解析失败等异常路径日志，但均属于测试覆盖场景，未造成测试失败。

### 结论

- 当前 AI 问答 / SSE 后端改进包没有编译或测试阻塞。
- 建议下一步进入 Claude Code 只读提交前审查。
- 该改进包适合作为独立提交候选，但提交前仍需审查 diff 边界和敏感信息风险。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 明确未做事项

- 未修改后端业务代码。
- 未修改前端业务代码。
- 未修改测试代码。
- 未修改配置文件。
- 未执行 `git add` / `git commit` / `git push`。

## 2026-05-13 - T002b 前端 QA / SSE 展示改进验证记录

### 本轮目标

自审、验证并提交前端 QA / SSE 展示改进包；保持与后端 `91421a9 feat(ai): improve document QA SSE robustness` 拆分提交，不混入 Agent Demo、benchmark、`.run` 或根 README / AGENTS。

### 验证结果

- `cd frontend; npm run lint`：通过，`next lint` 输出无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建、类型检查和静态页面生成成功。
- 当前 `package.json` 无 `test` 脚本，因此本轮未执行前端 test。

### 自审结论

- 白名单文件未发现真实 API Key、token、password、secret 或真实公网 IP。
- `frontend/lib/qa-api.ts` 已适配后端 SSE `meta/chunk/done/error` 事件，并能解析结构化 `done/error` payload。
- 文档详情页保留流式输出、首字延迟展示、引用更新、失败自动降级普通问答和历史回答 Markdown 渲染。
- `MarkdownViewer` 新增 inline mode，不改变默认 card 模式，降低破坏普通 Markdown 展示的风险。
- `dashboard/layout/frontend README` 中存在 Agent 入口或说明改动，不属于本轮 QA/SSE 提交范围，本轮不提交。

### 修改文件

- `frontend/app/documents/[documentId]/page.tsx`
- `frontend/components/markdown-viewer.tsx`
- `frontend/components/markdown-viewer.module.css`
- `frontend/lib/qa-api.ts`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 明确未做事项

- 未提交 Agent Demo。
- 未提交 benchmark。
- 未提交 `.run` 配置。
- 未提交根目录 `README.md` 或 `AGENTS.md`。
- 未执行 `git push`。

## 2026-05-13 - 固化 commit message 协作规则

### 本轮目标

只将 commit message 格式规则写入协作文档（AGENTS.md、CODEX_TOOLING.md），使后续 Claude Code / Codex CLI 生成 commit message 时有统一约束，避免出现 Co-Authored-By、多行 body、工具/模型签名等问题。

### 修改文件

- `AGENTS.md`
- `docs/CODEX_TOOLING.md`
- `docs/CHANGELOG_CODING.md`

### 规则摘要

- 单行 conventional commits 格式（`type(scope): description`）。
- 不生成多行 body、不添加 Co-Authored-By、不出现工具/模型名称（Claude、Anthropic、Opus、AI assistant、Codex）。
- 详细实现说明写入 CHANGELOG / HANDOFF / TODO_NEXT，不写入 commit message。
- commit message 要像正常开发者提交，不是 AI 生成说明。

### 明确未做事项

- 未修改业务代码。
- 未修改前端代码。
- 未修改配置文件。
- 未执行 `git add` / `git commit` / `git push`。

### 后续约束

所有 Claude Code / Codex CLI 在本仓库生成 commit message 时必须遵守 AGENTS.md 和 CODEX_TOOLING.md 中的 Commit Message 规则。

## 2026-05-13 - T003 Agent Demo 提交

### 本轮目标

自审、验证并提交 Agent Demo 最小工具编排闭环。

### 提交文件

- 后端 Agent 模块（9 个）：controller / dto / service / impl / tool x4 / vo。
- 后端测试（1 个）：`DocumentAgentServiceImplTest.java`。
- 后端 smoke 脚本（1 个）：`smoke-agent-min.ps1`。
- 前端 Agent 页面/API（2 个）：`agent/page.tsx` / `agent-api.ts`。
- 前端导航入口（2 个）：`layout.tsx` / `dashboard/page.tsx`。

### 验证结果

- `mvn -DskipTests compile`：通过。
- `mvn test -DskipITs`：通过（141 tests, 0 failures）。
- `npm run lint`：通过。
- `npm run build`：通过。
- smoke 脚本语法已确认，未实跑。

### 明确未提交

- `.run` 配置、benchmark、README、AGENTS、`docs/ai-dev`。

## 2026-05-13 - T004a Agent 执行痕迹持久化骨架

### 本轮目标

新增 AgentTask / AgentStep 持久化骨架，为后续 Agent 执行痕迹、工具步骤 trace 和失败诊断落库做准备。

### 修改文件

- `deploy/mysql/init/01_add_agent_tables.sql`
- `backend/src/main/java/com/docpilot/backend/ai/agent/entity/AgentTask.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/entity/AgentStep.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/mapper/AgentTaskMapper.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/mapper/AgentStepMapper.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/AgentTaskPersistenceService.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/AgentTaskPersistenceServiceImpl.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 结果摘要

- 新增 `tb_agent_task` 和 `tb_agent_step` DDL。
- 新增 MyBatis-Plus Entity / Mapper。
- 新增 `AgentTaskPersistenceService` 骨架，支持创建 task、标记成功/失败、创建 step。

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未接入 `DocumentAgentServiceImpl`。
- 未执行 DDL。
- 未修改前端。
- 未接 RocketMQ。
- 未引入 Spring AI / LangChain4j / MCP。

## 2026-05-13 - T004b Agent 执行痕迹持久化接入

### 本轮目标

将 T004a 的 AgentTask / AgentStep 持久化服务接入 `DocumentAgentServiceImpl`，让每次 Agent run best-effort 记录 task、tool step、成功或失败状态。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/vo/DocumentAgentResponse.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未新增 Agent task 查询 API。
- 未执行 DDL。
- 未修改前端。
- 未接 RocketMQ。

## 2026-05-13 - T004c Agent 执行轨迹查询 API

### 本轮目标

提供 Agent task / step 查询接口，可按 `taskId` 查询当前用户的单次 Agent 执行记录和步骤列表。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/controller/DocumentAgentController.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/mapper/AgentTaskMapper.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/mapper/AgentStepMapper.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/AgentTaskPersistenceService.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/AgentTaskPersistenceServiceImpl.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未修改前端 Agent 页面。
- 未执行 DDL。
- 未新增 MQ / Outbox。

## 2026-05-13 - T004d Agent smoke 与协作文档收尾

### 本轮目标

补充 Agent smoke 对 `taskId` 的断言，并将 T004b/T004c/T004d 的完成状态同步到协作文档。

### 修改文件

- `backend/scripts/agent/smoke-agent-min.ps1`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- `cd backend; mvn test -DskipITs`：通过。
- `powershell -NoProfile -Command "Get-Command .\backend\scripts\agent\smoke-agent-min.ps1 -ErrorAction Stop"`：通过。

### 明确未做事项

- 未实跑 Agent smoke。
- 未启动后端服务。
- 未执行 DDL。

## 2026-05-13 - T004e Agent 持久化运行时 smoke

### 本轮目标

连续完成 AgentTask / AgentStep 远程表检查、授权建表、运行时 smoke、接口查询和远程数据库只读核验。

### 修改文件

- `backend/scripts/agent/smoke-agent-min.ps1`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- T004e-1：hk-ops 只读检查确认远程 `docpilot` 数据库存在，目标表起初不存在。
- T004e-2：经用户确认后，hk-ops 仅执行两条授权的 `CREATE TABLE IF NOT EXISTS`，随后确认 `tb_agent_task` / `tb_agent_step` 存在且结构与 DDL 大致一致。
- T004e-3：本地后端启动后，`scripts/agent/smoke-agent-min.ps1` 通过；Agent run 返回 `taskId`；task 查询接口与 step 查询接口通过。
- hk-ops 远程只读 SELECT 确认本次 smoke 的 task 与 steps 已写入远程 `tb_agent_task` / `tb_agent_step`。

### 明确未做事项

- 未修改 Java 业务代码。
- 未修改前端业务代码。
- 未修改 DDL。
- 未读取或输出 `backend/.env`。
- 未执行远程 `ALTER / DROP / DELETE / UPDATE / INSERT / TRUNCATE`。
- 未执行 `git push`。

## 2026-05-13 - T005b DocumentToolSelector 独立规则测试

### 本轮目标

补充 `DocumentToolSelector` 的独立单元测试，锁定 status / summary / evidence / 默认 QA 路由逻辑，避免后续工具选择规则漂移。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/DocumentToolSelectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 覆盖范围

- `status_only`：状态 / 解析完成类任务仅选择 `document_status_tool`。
- `summary_tool`：总结 / 摘要类任务选择 `document_status_tool` + `document_summary_tool`。
- `qa_tool`：证据 / 引用类任务选择 `document_status_tool` + `document_qa_tool`。
- 默认 QA：普通问题默认进入 QA 工具链。
- summary + evidence 冲突：证据需求优先进入 QA，避免总结工具丢失引用诉求。
- 空字符串 / 空白输入：默认进入 QA 工具链。

### 验证结果

- `cd backend; mvn -Dtest=DocumentToolSelectorTest test`：通过（6 tests, 0 failures）。
- `cd backend; mvn test -DskipITs`：通过（147 tests, 0 failures）。

### 明确未做事项

- 未修改生产代码。
- 未修改 `DocumentAgentServiceImpl`。
- 未修改前端。
- 未修改 DDL。
- 未接 MQ / RAG / MCP / Spring AI / LangChain4j。

## 2026-05-13 - T005c Agent ToolSelector runtime smoke

### 本轮目标

运行 Agent runtime smoke，确认 T005a 接入 `ToolRegistry` / `ToolSelector` 后，真实接口链路的 summary / QA 路由、`taskId` 返回和 task / step 查询仍然正常。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- 本地后端以 `local` profile 启动，`/actuator/health` 可访问。
- `cd backend; powershell -ExecutionPolicy Bypass -File scripts/agent/smoke-agent-min.ps1`：通过。
- summary run 返回 `summary_tool`，包含 2 个步骤和有效 `taskId`。
- QA run 返回 `qa_tool`，包含 2 个步骤、有效 `taskId` 和引用结果。
- Agent task 查询接口返回对应 task 与 steps。
- Agent step 查询接口返回对应 steps。

### 明确未做事项

- 未修改生产代码。
- 未修改前端。
- 未修改 DDL。
- 未修改 smoke 脚本。
- 未读取 `backend/.env`。
- 未执行 `git add` / `git commit` / `git push`。
- 本轮结束前已停止本地后端，确认 8081 端口释放。

## 2026-05-13 - T006a 前端 Agent 持久化执行轨迹展示

### 本轮目标

让前端 Agent 页面在 `/api/ai/agent/run` 返回 `taskId` 后，调用后端 task / step 查询接口并展示持久化后的执行轨迹。

### 修改文件

- `frontend/lib/agent-api.ts`
- `frontend/app/agent/page.tsx`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `frontend/lib/agent-api.ts` 新增 `getAgentTask(taskId)` 和 `getAgentTaskSteps(taskId)`，路径来自 `DocumentAgentController`。
- `frontend/app/agent/page.tsx` 在 Agent run 成功且存在 `taskId` 时自动查询持久化 task / steps。
- 页面新增“持久化执行轨迹”区域，展示 taskId、status、decision、totalDurationMs、step count，以及每个 step 的 stepIndex、toolName、status、durationMs、inputSummary、outputSummary。
- task / step 查询失败时只显示友好提示，不影响原始 Agent 回答、内存 trace 和引用展示。

### 验证结果

- `cd frontend; npm run lint`：通过，无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建、类型检查和静态页面生成完成。

### 明确未做事项

- 未修改后端 Java。
- 未修改 DDL。
- 未修改 README / frontend README。
- 未修改 `.run`。
- 未修改 benchmark / docs-ai-dev。
- 未新增依赖或修改 package / lock 文件。

## 2026-05-13 - T006b 前端 Agent trace 运行时验证

### 本轮目标

真实运行前端 Agent 页面，确认 T006a 新增的持久化 task / step trace 展示在浏览器中可用。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- 本地后端 8081 启动成功，`/actuator/health` 可访问。
- 本地前端 3000 启动成功，`/agent` 可访问。
- `cd backend; powershell -ExecutionPolicy Bypass -File scripts/agent/smoke-agent-min.ps1`：通过，summary / QA run 均返回有效 `taskId`，task / step 查询接口通过。
- Playwright 打开 `/agent`，通过合法注册、上传、建文档、解析和 Agent run 验证页面展示。
- 页面展示“持久化执行轨迹”，包含 taskId、`SUCCESS` 状态、`qa_tool` 决策、2 条 step、`document_status_tool` / `document_qa_tool`、durationMs、inputSummary、outputSummary。
- `cd frontend; npm run lint`：通过，无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建和类型检查完成。

### 明确未做事项

- 未修改业务代码。
- 未修改 DDL。
- 未读取 `backend/.env`。
- 未输出真实 token、密码、API Key。
- 未执行 `git push`。
- 本轮启动的后端 / 前端进程已停止，8081 / 3000 / 3001 端口已释放。

## 2026-05-12 - T001b 统一 eval 指标引用

### 本轮目标

统一 README / STATE / 协作文档中的 Stage C eval 指标引用，消除 README、STATE、artifact 三套指标冲突。

### 修改文件

- `README.md`
- `docs/ai-dev/STATE.md`
- `docs/CODEX_HANDOFF.md`
- `docs/TODO_NEXT.md`
- `docs/CHANGELOG_CODING.md`

### 当前权威指标来源

- Artifact：`docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`
- `generatedAt=2026-04-18T18:58:42.2763129+00:00`
- `datasetName=stagec-core-qa-eval`
- `datasetVersion=2026-04-19-r2`
- `caseCount/streamPairs=20/8`
- `answerSuccessRate=90%`
- `citationHitRate=100%`

### 明确未做事项

- 未修改后端业务代码。
- 未修改前端业务代码。
- 未重跑 eval。
- 未修改 artifact JSON。
- 未执行 `git add` / `git commit` / `git push`。

### 不确定项

- `stagec_eval_latest.json` 未记录实际运行时 `AI_MODE`、模型名或 provider。
- 当前指标是仓库内 artifact 证据，不代表线上 SLA。
- 后续 T005a/T005b 需要补充运行时配置记录并重跑 eval。

## 2026-05-12 - T000d Codex subagents 与 MCP 工具边界记录

### 本轮目标

新增工具能力边界文档，让后续 Claude Code、Codex、ChatGPT 接手时能明确 subagents、context7 MCP、playwright MCP 的用途、授权条件和禁止事项。

### 修改文件

- `AGENTS.md`
- `docs/CODEX_TOOLING.md`
- `docs/CODEX_HANDOFF.md`
- `docs/TODO_NEXT.md`
- `docs/CHANGELOG_CODING.md`

### 记录内容

- `code-map`、`docs-research`、`hk-ops`、`risk-review`、`test-audit`、`ui-check` 的用途和边界。
- `context7 MCP` 用于查询官方/库文档，避免凭空猜测。
- `playwright MCP` 用于浏览器自动化、前端 smoke test、UI/E2E 检查。
- `hk-ops` 远程访问前必须说明目的、命令类别、是否只读，并等待用户确认。
- `playwright` 不应未经用户确认启动长期驻留 dev server。

### 明确未做事项

- 未记录任何真实 IP、账号、密码、token、API Key 或 `.env` 内容。
- 未修改后端业务代码。
- 未修改前端业务代码。
- 未执行 `git add` / `git commit` / `git push`。

## 2026-05-12 - T000c 剩余敏感信息复核与 `.run` 配置检查

### 本轮目标

只做剩余敏感信息复核，重点检查 IDEA `.run/*.xml` 配置文件；如发现真实公网 IP 或敏感值，仅对 `.run/*.xml` 做必要脱敏。

### 检查范围

- `.run/*.xml`
- `README.md`
- `backend/README.md`
- `backend/.env.cloud.example`
- `backend/.env.demo.example`
- `backend/src/main/resources/application-*.yml`
- `docs/**/*.md`
- `AGENTS.md`

### 结果摘要

- `.run/*.xml` 未命中真实公网 IP。
- `.run/*.xml` 未命中密码、token、secret、API Key 等敏感关键词。
- T000b 已处理的目标文件未再次命中真实公网 IP。
- `backend/.env` 仍未被 Git 跟踪，且本轮未读取或修改其内容。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CHANGELOG_CODING.md`

### 明确未做事项

- 未修改 `.run/*.xml`，因为未发现需要脱敏的真实敏感值。
- 未修改后端业务代码。
- 未修改前端业务代码。
- 未修改 `backend/.env`。
- 未执行 `git add` / `git commit` / `git push`。
