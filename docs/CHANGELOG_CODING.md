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
