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

当时建议先执行 `T000：审计工作区改动 + 敏感信息检查`。完成后再执行 T001a/T001b 收敛 eval 指标证据链，随后执行最小 smoke 验证，确认当前仓库在本地仍能构建、测试和演示。

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

## 2026-05-13 - T007 项目文档收口

### 本轮目标

收口根 README 与 frontend README，让公开文档反映当前真实实现：AI 问答 / SSE、最小 Agent、AgentTask / AgentStep 执行轨迹、task / step 查询接口和前端持久化 trace 展示。

### 修改文件

- `README.md`
- `frontend/README.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- README 补充最小 Agent 工具链闭环、执行轨迹落库、查询接口、前端 trace 展示和当前验证记录。
- README 明确边界：当前不是成熟多 Agent 平台；ToolSelector 是规则 / 关键词选择，不是 LLM Tool Calling；未接 MQ 异步 Agent、完整 RAG、向量库、MCP、Spring AI 或 LangChain4j。
- frontend README 补充 dashboard、document QA、Agent 页面和持久化 task / step trace 展示能力。
- frontend README 保留启动、lint、build 和联调说明，不记录敏感信息。

### 验证结果

- `cd frontend; npm run lint`：通过，无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建和类型检查完成。

### 明确未做事项

- 未修改业务代码。
- 未修改后端 Java。
- 未修改 DDL。
- 未提交 `.run`。
- 未提交 benchmark / docs-ai-dev。
- 未新增依赖或修改 package / lock 文件。

## 2026-05-13 - T009a Agent ToolSelector 可解释性增强

### 本轮目标

让 Agent 工具路由结果从仅有 `decision` 扩展为同时返回 `routingReason` 和 `matchedKeywords`，便于前端展示和后续 smoke 验证。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/ToolSelector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/DocumentToolSelector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/vo/DocumentAgentResponse.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `ToolSelector.SelectResult` 新增 `reason` 和 `matchedKeywords`，并保留 `of(decision, toolNames)` 兼容便捷方法。
- `DocumentToolSelector` 改为收集命中关键词，summary + evidence 冲突时继续路由到 `qa_tool`。
- `DocumentAgentServiceImpl` 将 selector reason / matched keywords 写入 `DocumentAgentResponse`；parseReady=false 短路路径仍不调用 selector，并返回固定路由原因。
- `DocumentAgentServiceImplTest` 增加路由原因断言。

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过（147 tests, 0 failures）。

### 明确未做事项

- 未修改 DDL。
- 未修改 Controller。
- 未修改前端。
- 未修改 smoke 脚本。
- 未持久化 `routingReason` / `matchedKeywords` 到 AgentTask。
- 未接 MQ / RAG / MCP / Spring AI / LangChain4j / LLM Tool Calling。

## 2026-05-13 - T009b DocumentToolSelector 可解释性测试

### 本轮目标

补充 `DocumentToolSelector` 独立单元测试，锁定 `decision`、`reason`、`matchedKeywords` 的规则行为。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/DocumentToolSelectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 覆盖范围

- 状态查询路由到 `status_only`，并返回状态类命中关键词和 reason。
- 摘要任务路由到 `summary_tool`，并返回摘要类命中关键词和 reason。
- 证据 / 原文引用任务路由到 `qa_tool`，reason 体现证据或引用需求。
- 默认 QA 在无关键词命中时返回空 matchedKeywords 和非空 reason。
- summary + evidence 冲突时继续优先 `qa_tool`。
- 英文关键词大小写不敏感。
- 空字符串、空白字符串和 null 输入按当前实现默认进入 QA。

### 验证结果

- `cd backend; mvn -Dtest=DocumentToolSelectorTest test`：通过（8 tests, 0 failures）。
- `cd backend; mvn test -DskipITs`：通过（149 tests, 0 failures）。

### 明确未做事项

- 未修改生产代码。
- 未修改前端。
- 未修改 DDL。
- 未接 MQ / RAG / MCP / Spring AI / LangChain4j / LLM Tool Calling。

## 2026-05-13 - T009c 前端展示 Agent 路由解释

### 本轮目标

让 Agent 页面展示后端返回的 `routingReason` 和 `matchedKeywords`，使工具路由结果对用户可见。

### 修改文件

- `frontend/lib/agent-api.ts`
- `frontend/app/agent/page.tsx`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `DocumentAgentRunData` 增加 `routingReason?: string` 和 `matchedKeywords?: string[]`。
- Agent 运行结果区新增“路由决策”卡片，仅在 `routingReason` 存在时展示。
- 命中关键词以轻量 badge 展示；关键词为空或字段缺失时不渲染标签。
- 保留原有 decision、finalAnswer、citations、steps 和持久化 trace 展示。

### 验证结果

- `cd frontend; npm run lint`：通过，无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建和类型检查完成。

### 明确未做事项

- 未修改后端 Java。
- 未修改 DDL。
- 未新增依赖。
- 未修改 package / lock 文件。

## 2026-05-13 - T009d Agent 路由解释 smoke 与异步设计文档

### 本轮目标

验证 T009a-c 的路由解释全链路，并记录未来异步 Agent 演进方案。本轮不实现异步 Agent，不接 MQ。

### 修改文件

- `backend/scripts/agent/smoke-agent-min.ps1`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/AgentTool.java`
- `docs/AGENT_ASYNC_DESIGN.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- smoke 脚本增加 summary run 的 `routingReason`、`matchedKeywords` 和 `summary_tool` 断言。
- smoke 脚本增加 QA run 的 `routingReason`、`qa_tool` 和 citation 断言。
- smoke 输出增加 summary / QA 的 routing reason 与 matched keywords。
- `AgentTool` 补充 Javadoc，说明新增工具的最小接入路径。
- 新增 `docs/AGENT_ASYNC_DESIGN.md`，对比 RocketMQ、Spring `@Async` 和 DB polling，并推荐未来基于 `AgentTask` 状态机和 RocketMQ 的异步演进路线。

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过（149 tests, 0 failures）。
- `cd backend; powershell -ExecutionPolicy Bypass -File scripts/agent/smoke-agent-min.ps1`：通过，summary / QA 均返回 routing reason，summary 返回 matched keywords，QA 返回引用。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 未实现异步 Agent。
- 未新增 RocketMQ topic / consumer。
- 未修改 DDL。
- 未修改前端页面。
- 未接 RAG / MCP / Spring AI / LangChain4j / LLM Tool Calling。

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

## 2026-05-13 - T009e 协作文档事实状态同步

### 本轮目标

同步协作文档中的当前状态描述，让 `docs/CODEX_HANDOFF.md`、`docs/TODO_NEXT.md` 和 `docs/CHANGELOG_CODING.md` 与当前 git / 代码状态一致。

### 原因

新会话恢复上下文时发现协作文档仍停留在旧的工作区风险口径、推荐先做 T000，但实际 `git status --short` 为空，且 `git log --oneline -20` 已显示 T009a-d 完成。

### 修正内容

- `docs/CODEX_HANDOFF.md` 改为记录当前工作区干净、T009a-d 已完成、`ToolSelector` 已支持 `routingReason` / `matchedKeywords`、前端 Agent 页面已展示路由决策、smoke 已增强路由解释断言。
- `docs/TODO_NEXT.md` 新增 T009e 完成记录和 T010 runtime 验证任务，推荐下一步改为 T010，并补充 T011 面试向项目总结 / 架构图 / 简历亮点收口。
- 协作文档明确暂不直接进入 MQ / RAG / MCP / LLM Tool Calling。

### 明确未做事项

- 未修改业务代码。
- 未修改后端 Java。
- 未修改前端业务代码。
- 未修改 DDL、README、`.run`、benchmark 或 `docs/ai-dev`。
- 未执行 T010 runtime 验证。
- 未读取或修改 `backend/.env`。

### 下一步

进入 `T010-runtime-verify`，完整验证 Agent 路由可解释性在浏览器端真实可用。

## 2026-05-14 - T010z 记录 runtime 验证阻塞状态

### 本轮目标

记录 T010-runtime-verify 当前 BLOCKED 状态，避免后续新会话误以为 Agent 路由解释浏览器端完整验证已经通过。

### T010x 诊断结果

- T010x 已复现后端 smoke 在文档解析阶段超时。
- 原始失败信息：`Parse timeout after 120 seconds.`
- 后端日志摘要显示 `NoopParseTaskMessageProducer` 跳过解析消息发送。
- 当前 MQ disabled / no-op producer 模式下不会推进真实异步解析，worker 不会消费并更新 `parseStatus`。

### 结论

- T010 未通过。
- 失败原因不是 Agent `routingReason` / `matchedKeywords` 代码问题，而是完整 smoke 依赖上传后真实异步解析链路。
- 完整 T010 需要可用 MQ / 解析消费环境后再重跑；若用户不想接 MQ，只能单独定义 T010-lite，并明确它不是完整上传解析链路验证。

### 本轮未做事项

- 未修改业务代码。
- 未修改后端 Java。
- 未修改前端代码。
- 未修改 DDL、README、`.run`、benchmark 或 `docs/ai-dev`。
- 未使用 hk-ops。
- 未远程连接服务器。
- 未启动或修改 RocketMQ。
- 未把 T010 写成通过。

### 下一步

执行 `T010m-local-mq-readiness-check`，只读检查本地 MQ / parse 配置入口和完整 T010 所需环境条件。

## 2026-05-14 - T010-lite-ui 前端 Agent lite 验证入口

### 本轮目标

在完整 T010 仍被 MQ disabled / no-op parser queue 阻塞的前提下，只为 `/agent` 页面增加当前用户可访问 `documentId` 的选择 / 输入入口，方便后续执行 Agent-only lite 验证。

### 修改文件

- `frontend/app/agent/page.tsx`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 保留 `/agent` 页面已有当前用户文档列表下拉框。
- 新增手动输入 `documentId` 的输入框，不硬编码任何文档 ID。
- 页面展示 Lite 验证模式说明：仅验证已解析文档上的 Agent 运行，不验证上传和解析链路。
- 文档不存在或当前账号无权访问时，页面显示友好错误，不影响原有 Agent run、decision、routingReason、matchedKeywords、持久化 trace 和 citations 展示逻辑。

### 验证结果

- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 未修改后端 Java。
- 未修改 DDL。
- 未修改 `package.json` 或 lock 文件。
- 未修改 smoke 脚本。
- 未启动或修改 RocketMQ。
- 未使用 hk-ops 或远程连接。
- 未验证上传解析链路。
- 未把 T010-lite 写成完整 T010 通过。

## 2026-05-14 - T010-lite-run Agent-only runtime 验证

### 本轮目标

在完整 T010 仍被 MQ disabled / no-op parser queue 阻塞的前提下，只验证已解析文档上的 Agent routing explainability 运行链路：已解析文档 -> Agent run -> routingReason / matchedKeywords -> task / step trace -> 前端展示。

### 验证对象

- 使用 `documentId=61`，该文档来自当前浏览器登录账号的 `/agent` 文档下拉列表，页面显示为解析成功。
- 本轮不使用 `documentId=58`，不硬编码 documentId，不验证上传解析链路。

### 浏览器验证结果

- Playwright 打开 `/agent` 并使用 `documentId=61`。
- summary 任务“总结一下这篇文档”通过，页面显示 `decision=summary_tool`、路由决策、`routingReason`、matched keyword、持久化执行轨迹、`taskId`、`SUCCESS`、2 条 step、toolName、durationMs、inputSummary、outputSummary。
- QA 任务“根据原文证据回答这篇文档的核心内容是什么”通过，页面显示 `decision=qa_tool`、路由决策、`routingReason`、matched keyword、持久化执行轨迹、`taskId`、`SUCCESS`、2 条 step、toolName、durationMs、inputSummary、outputSummary，并展示 citations。

### CLI lite smoke

- 未执行。
- 原因：当前 smoke 脚本会注册新的临时账号，无法保证该账号有权访问浏览器当前账号下的 `documentId=61`；本轮不把 CLI 账号不一致误判为浏览器 lite 验证失败。

### 构建验证

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过，测试统计为 149 tests，0 failures，0 errors。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 未修改业务代码。
- 未修改后端 Java。
- 未修改前端业务代码。
- 未修改 DDL。
- 未启动或修改 RocketMQ。
- 未使用 hk-ops。
- 未远程连接服务器。
- 未读取或输出 `backend/.env`。
- 未执行 `git push`。
- 未把 T010-lite 写成完整 T010 通过；完整 T010 仍为 BLOCKED。

## 2026-05-14 - T011a Tool Schema / Tool Metadata

### 本轮目标

进入 P3 LLM Tool Selection 的基础设施阶段，先为当前 Agent 工具建立稳定 Tool Definition，不调用真实 LLM、不接 function calling、不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/ToolDefinition.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/ToolDefinitionProvider.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/ToolDefinitionProviderTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `ToolDefinition` record，包含 toolName、displayName、description、inputSchemaText、outputSchemaText 和 safeForLlmSelection。
- 新增 `ToolDefinitionProvider`，基于当前 `ToolRegistry` 注册工具返回 `document_status_tool`、`document_summary_tool`、`document_qa_tool` 三个工具定义。
- 单元测试覆盖 3 个工具定义存在、toolName 不重复、description / schema 非空，以及 QA 工具描述不声明危险能力。

### 验证结果

- `cd backend; mvn -Dtest=ToolDefinitionProviderTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T011d Tool Selector Evaluation Cases

### 本轮目标

新增 selector 评估样例集和离线测试，为后续比较关键词 selector 与未来 LLM selector 建立基线。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/ToolSelectorEvaluationTest.java`
- `backend/src/test/resources/agent/tool-selector-eval-cases.json`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 24 条 `tool-selector-eval-cases.json` 样例，覆盖状态查询、摘要查询、证据问答、英文大小写、中文表达、模糊表达、summary + evidence 冲突和空白输入。
- 新增 `ToolSelectorEvaluationTest`，读取 JSON 样例并调用当前 `DocumentToolSelector`，断言 decision 符合 expectedDecision。
- 测试输出 pass count / total count，当前基线为 24/24。

### 验证结果

- `cd backend; mvn -Dtest=ToolSelectorEvaluationTest test`：通过，24/24 cases。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T012a Shadow LLM Selector Adapter

### 本轮目标

进入 P3 Shadow LLM Selector 基础设施阶段，新增未来 LLM selector 的适配接口、fake shadow implementation 和 compare result。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/FakeLlmToolSelector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmSelectorShadowResult.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/FakeLlmToolSelectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `LlmToolSelector` 接口，定义 `selectWithPrompt` 输入 task、parseReady、hasSummary 和工具定义列表，返回 `LlmToolSelectionResult`。
- 新增 `FakeLlmToolSelector`，不联网、不调用真实 LLM，仅复用 `DocumentToolSelector` 或在 parseReady=false 时返回状态工具决策。
- 新增 `LlmSelectorShadowResult`，记录 primaryDecision、shadowDecision、matched、primaryReason 和 shadowReason。
- 单元测试覆盖 summary、QA、status、parseReady=false、matched=true 和 shadowDecision 非空。

### 验证结果

- `cd backend; mvn -Dtest=FakeLlmToolSelectorTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T012b Selector Feature Flags

### 本轮目标

新增 Agent selector feature flags，为后续 shadow compare 提供显式配置。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/config/AgentSelectorProperties.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/docpilot/backend/ai/agent/config/AgentSelectorPropertiesTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `AgentSelectorProperties`，绑定 `app.agent.selector` 配置。
- 新增 `app.agent.selector.mode`，允许 `keyword` / `shadow_llm`，默认 `keyword`。
- 新增 `app.agent.selector.shadow-enabled`，默认 `false`。
- 配置测试覆盖默认值、shadow 配置绑定和非法 mode 拒绝。

### 验证结果

- `cd backend; mvn -Dtest=AgentSelectorPropertiesTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T012c Selector Shadow Comparison

### 本轮目标

在 `DocumentAgentServiceImpl` 中接入 primary selector + shadow selector compare，但 primary decision 仍唯一生效。本轮不调用真实 LLM，不接 function calling，不改变 API 返回或默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmSelectorShadowResult.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `DocumentAgentServiceImpl` 在文档 parseReady 后仍先使用 primary `ToolSelector` 得到真实 decision。
- 当 `app.agent.selector.shadow-enabled=true` 时，旁路调用 `LlmToolSelector`，生成 `LlmSelectorShadowResult` 并记录 compare 日志。
- shadow selector 异常只记录 warn，primary decision 和真实工具执行不受影响。
- parseReady=false 时仍直接返回状态提示，不运行 primary selector 或 shadow compare。
- 单元测试覆盖 shadow compare 不影响真实工具执行、matched=true、开关关闭不运行、parseReady=false 不运行。

### 验证结果

- `cd backend; mvn -Dtest=DocumentAgentServiceImplTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过，175 tests，0 failures，0 errors。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 API 返回协议。
- 未修改 AgentTask / AgentStep schema。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T012d Selector Shadow Metrics

### 本轮目标

新增 selector compare metrics 的内存态 collector 和 snapshot，为后续 shadow compare 观测打基础。本轮不接 Micrometer / Prometheus，不新增 API，不落库。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsSnapshot.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsCollector.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsCollectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `SelectorMetricsSnapshot`，包含 totalComparisons、matchedCount、mismatchCount、matchRate 和 lastUpdatedTime。
- 新增 `SelectorMetricsCollector`，通过 `record(primaryDecision, shadowDecision)` 线程安全记录 match / mismatch。
- collector 仅内存态保存数据，不落库、不接外部指标系统、不暴露接口。
- 单元测试覆盖全 match、部分 mismatch、matchRate 正确、空 snapshot 和并发 record。

### 验证结果

- `cd backend; mvn -Dtest=SelectorMetricsCollectorTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过，179 tests，0 failures，0 errors。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未新增 API。
- 未落库。
- 未接 Micrometer / Prometheus。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T011c Tool Selection Prompt Builder

### 本轮目标

新增未来 LLM Tool Selection 的 prompt builder，为后续真实 LLM selector 提供稳定提示词骨架。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionPromptBuilder.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionPromptBuilderTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `LlmToolSelectionPromptBuilder`，输入 task、parseReady、hasSummary 和 `ToolDefinition` 列表，输出工具选择 prompt。
- prompt 包含当前任务、文档解析状态、是否已有 summary、可用工具列表、每个工具的输入输出 schema、JSON 输出协议和安全限制。
- prompt 明确 decision 只能从 `status_only`、`summary_tool`、`qa_tool` 中选择，并禁止生成 SQL、系统命令或调用未列出的工具。
- 单元测试覆盖 toolName、JSON 输出格式、安全限制、task、parseReady、hasSummary 和 decision 值。

### 验证结果

- `cd backend; mvn -Dtest=LlmToolSelectionPromptBuilderTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T011b LLM Tool Selection 输出协议和解析器

### 本轮目标

定义未来 LLM Tool Selection 的 JSON 输出协议，并实现离线 parser。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionResult.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionParser.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionParserTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `LlmToolSelectionResult`，包含 decision、toolNames、routingReason、matchedKeywords、confidence。
- 新增 `LlmToolSelectionParser`，可从原始文本中提取第一个 JSON object，并校验 decision、已注册工具名、confidence 范围和 toolNames 非空。
- 解析失败时抛出明确异常，不做静默 fallback。
- 单元测试覆盖标准 JSON、前后带自然语言、非法 decision、未知 toolName、confidence 越界、空输入和空 toolNames。

### 验证结果

- `cd backend; mvn -Dtest=LlmToolSelectionParserTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。
