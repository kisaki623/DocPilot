﻿﻿# AGENTS.md

本文件是 DocPilot 仓库给后续 Codex / API agent / Claude Code 等协作代理读取的项目规则。每轮开始先读本文件和 `docs/README.md`，再按文档地图读取 `docs/ai-dev/STATE.md`、`docs/ai-dev/CURRENT_TASK.md`、`docs/showcase/DEMO_SMOKE_RECORD.md`、`docs/ai-dev/ROADMAP_RAG.md`、`docs/ai-dev/DECISIONS.md`、`docs/ai-dev/CONSTRAINTS.md`、`docs/ai-dev/PROGRESS_LOG.md`，并检查 `git status` 与 `git diff`；涉及真实体验审计、用户视角 bug 或修复回归时，同时读取 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`。旧的 `TODO_NEXT.md`、`CODEX_HANDOFF.md`、`CHANGELOG_CODING.md` 已归档到 `docs/archive/`，只在追溯历史时读取，不作为当前任务源。

## 项目定位

DocPilot 是一个面向企业文档知识库场景的 RAG + 会话记忆平台，核心关注文档上传、异步解析、结构化切片、向量索引、多文档检索增强问答、可信引用、会话上下文追踪、用户记忆沉淀、权限隔离和质量门禁。当前目标是把项目从“可演示 AI 文档系统”推进为“生产化知识库 RAG 核心闭环”，同时继续保留 Java 后端工程、前后端联调和工程化验证能力。

当前口径要克制但不自降目标：项目已有单文档 RAG、多文档 KnowledgeBase RAG、真实回答模型、MinIO active storage、RocketMQ + Outbox active parse、真实 embedding + Qdrant smoke、Conversation Context / Memory、Agent `rag_qa_tool` / ToolCall API 和 cloud quality gate 记录；但仍不能写成完整商业 SaaS、线上 SLA、大规模多租户计费、高可用运维或成熟多 Agent 编排系统。RAG 和会话记忆是主线，Agent 是围绕现有文档业务工具、RAG evidence 和 Trace 的辅助执行与观测层。

## 技术栈

- 后端：Java 17、Spring Boot 3.3.x、Maven、MyBatis-Plus、MySQL、Redis、Redisson、RocketMQ、MinIO、Actuator、Micrometer / Prometheus。
- 前端：Next.js 14 App Router、React 18、TypeScript、Tailwind CSS、ReactMarkdown、remark-gfm、rehype-highlight、rehype-sanitize。
- 中间件：MySQL、Redis、RocketMQ NameServer / Broker / Dashboard、MinIO、Prometheus；默认开发中间件位于云服务器 Docker，纯本地 demo 才使用 `docker-compose.demo.yml`。
- AI 接入：mock answer service、OpenAI-compatible / SiliconFlow 风格真实回答模型、mock embedding 与 OpenAI-compatible embedding provider 并存；真实模型、真实 embedding、Qdrant endpoint 均依赖本地环境变量。

## 目录结构

- `backend/`：Spring Boot 后端，包含认证、上传、文档、任务、MQ、AI 问答、Agent、benchmark / eval 脚本等。
- `frontend/`：Next.js 前端，包含首页、登录、dashboard、上传、文档列表、文档详情、Agent 页面等。
- `deploy/`：演示环境相关初始化脚本和中间件配置。
- `.run/`：IDEA Run Configuration，供本地 / 云中间件模式启动参考。
- `docs/`：文档地图、当前事实源、RAG / Agent 设计参考、展示证据和历史归档。当前开发入口以 `docs/README.md` 和 `docs/ai-dev/` 为准，不要把这里变成无限流水账。
- `docker-compose.demo.yml`：纯本地 demo 中间件编排；当前默认开发中间件在云服务器 Docker 中运行。
- `README.md`：面向 GitHub / HR / 面试官的项目主页。

## 启动命令

后端本地启动常用命令：

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

前端本地启动常用命令：

```powershell
cd frontend
npm run dev
```

当前默认开发环境是前后端本地运行，中间件 MySQL、Redis、RocketMQ、MinIO等部署在云服务器 Docker 中，通过 `backend/.env`  接入。

如需做云 MySQL / Qdrant runtime smoke、后端 `/actuator/health` 联调、真实 Qdrant retrieval / indexing 验证，必须先按 `backend/README.md` 在仓库根目录启动本地 SSH tunnel：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/start-cloud-tunnels.ps1
```

普通离线单测、`mvn -DskipTests compile`、前端 `lint/build` 和未登录态 Playwright smoke 不要求启动 tunnel；如果测试日志里出现 scheduled outbox job 访问本机 MySQL tunnel 被拒，但 Surefire 最终 `BUILD SUCCESS`，只能记录为“未做 runtime smoke / tunnel 未连通边界”，不能写成云链路验证通过。



真实链路验证优先走本地 SSH tunnel、后端、前端和 smoke runner。用户明确进入自驱迭代模式后，视为已授权代理在当前大目标内自行启动本地 tunnel、后端、前端，运行真实 smoke，创建带统一 marker 的临时 smoke 用户 / 文档 / KnowledgeBase / Conversation，并生成 ignored 脱敏 artifact；这些操作不再需要逐次等待用户确认。涉及云服务器 Docker / `hk-ops` 时，只读诊断（状态、日志、端口、网络、健康检查、非敏感计数）可在说明目的和命令类别后执行；启动、停止、重启、删除、迁移、改防火墙、改云资源、清空数据或修改数据库结构仍必须单独获得用户明确授权。不得为了方便绕过本地证据直接做远程破坏性操作。

涉及前端开发或改善的工作请和 Gemini CLI 协作，Gemini CLI 负责创意、方案和代码建议，Codex 负责安全审查、代码落地、验证和文档回写；默认先用 `gemini-2.5-flash` 做短探测，正式协作优先用 `gemini-3.1-pro-preview`，外层超时建议 `180000ms`；Gemini CLI 不直接接触 `.env`、secrets、远程服务器操作、数据库迁移或不相关文件。详细规则见 `docs/ai-dev/CONSTRAINTS.md`。

实际运行前请根据 `backend/README.md`、`frontend/README.md`、`.run/` 配置和 `.env.example` /检查环境变量。不要提交真实 `.env`、密钥、密码、token、云服务地址或连接串。

## 构建与测试命令

后端：

```powershell
cd backend
mvn -DskipTests compile
mvn test -DskipITs
```

前端：

```powershell
cd frontend
npm run lint
npm run build
```

常用验证：

```powershell
git status --short
git diff --stat
curl http://localhost:8081/actuator/health
```

如果涉及浏览器行为，优先用 Playwright MCP 真实打开页面验证，不要只靠读代码判断。

## 协作流程规则

1. 默认使用中文回复。
   内部协作文档、状态回写、审计台账和问题记录默认使用中文；技术名、路径、API、状态枚举、命令可以保留原文，但解释、结论、复现步骤、实际结果和预期结果必须用中文，方便用户直接阅读和复盘。
2. 每轮开始必须先读：`AGENTS.md`、`docs/README.md`、`docs/ai-dev/STATE.md`、`docs/ai-dev/CURRENT_TASK.md`、`docs/ai-dev/CONSTRAINTS.md`、`docs/ai-dev/PROGRESS_LOG.md`、`git status`、`git diff`；涉及展示口径时同时读 `docs/showcase/DEMO_SMOKE_RECORD.md`，涉及 RAG / 技术决策时同时读 `docs/ai-dev/ROADMAP_RAG.md` 和 `docs/ai-dev/DECISIONS.md`。
3. 每轮只允许执行一个用户任务或 `docs/ai-dev/CURRENT_TASK.md` 中的一个任务切片；如果任务过大，先拆小再做。
4. 动代码前必须先说明本轮计划、涉及文件、验证方式。
5. 不允许伪造测试结果；没有真实验证结果的任务不能标记为 `DONE`。
6. 代码已改但验证不完整，只能把任务标记为 `REVIEW`。
7. 缺环境、账号、密钥、数据库、中间件或用户确认时，任务标记为 `BLOCKED`，并说明阻塞原因。
8. 每轮结束后按任务性质更新当前事实源：任务状态写入 `docs/ai-dev/CURRENT_TASK.md`，当前项目事实写入 `docs/ai-dev/STATE.md`，简短进度写入 `docs/ai-dev/PROGRESS_LOG.md`。旧的 `docs/archive/TODO_NEXT.md`、`docs/archive/CODEX_HANDOFF.md`、`docs/archive/CHANGELOG_CODING.md` 只用于历史追溯，不再作为默认回写目标。
   真实体验审计发现的 bug / 体验问题 / 环境阻塞必须写入 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`；修复后回填状态、提交和验证证据。`docs/showcase/DEMO_SMOKE_RECORD.md` 只记录可展示的 smoke / audit 摘要，不承接完整问题台账。
   只要 Codex / agent 真实启动项目、运行本地 tunnel / backend / frontend / smoke、用浏览器或 API 按用户路径体验，并发现 bug、体验问题、安全疑点或环境阻塞，就必须自动追加脱敏问题记录；不能只在对话里口头说明。
9. 不要自动执行 `git commit` / `git push`，除非用户明确要求；若用户明确激活“自驱迭代模式”，则按下方自驱迭代规则允许每个完成切片自动提交，但仍禁止 push。
10. 不要留下本地服务进程或端口占用；如启动了后端、前端或脚本服务，结束前要清理并说明端口状态。
11. 如果发现工作区有未提交改动或未跟踪文件，必须先向用户汇报，不要直接执行 `git add` / `git commit` / `git push`。
12. 每轮开始必须检查 `.env`、`.env.local`、`.env.*`、`application-*.yml`、`*.example` 中是否存在硬编码密钥、密码、token、真实云服务 IP；如有必须先告警，不能把敏感值复制到回复里。
13. 使用 subagents、context7 MCP、playwright MCP 或远程 `hk-ops` 前，先遵守本文件和 `docs/ai-dev/CONSTRAINTS.md`；旧工具边界可按需参考 `docs/archive/CODEX_TOOLING.md`。自驱迭代模式下允许为真实链路验证执行本地 tunnel / smoke / 只读远程诊断；远程破坏性操作仍必须单独授权，禁止泄露凭据或执行未经授权的破坏性操作。

## 自驱迭代模式

当用户明确表达“连续做直到完成”“自驱迭代推进”“按计划一直往下做”“每完成一部分自审并提交”等意图时，后续协作代理进入自驱迭代模式。该模式用于推进一个已定义的大目标，不用于绕过安全边界或无限扩大范围。

自驱迭代模式默认采用“真实链路优先验证”：mock / unit test 是快速回归门禁，但不能单独证明用户真实体验。涉及 RAG、KnowledgeBase、Conversation Memory、Context Trace、权限隔离、前端关键路径或 cloud smoke 的切片，只要当前本地环境可达，就应优先运行真实 backend / frontend / tunnel / Qdrant / MySQL / provider 链路上的 smoke 或 runtime 验证；没有真实链路验证时，不能把用户体验质量写成 `DONE`。

自驱迭代模式下，代理不需要等待用户逐片发送 `Implement the plan`；每个循环自行选择 `docs/ai-dev/CURRENT_TASK.md` / `docs/ai-dev/ROADMAP_RAG.md` 中最小可交付切片，按以下顺序推进：

1. 读取本文件、`docs/README.md`、`STATE.md`、`CURRENT_TASK.md`、`CONSTRAINTS.md`、`PROGRESS_LOG.md`，检查 `git status` / `git diff` 和敏感配置边界。
2. 简短说明本片目标、涉及文件、验证方式和不做事项。
3. 实现切片，优先复用现有模块和测试风格。
4. 运行与风险匹配的验证；RAG / Memory / 前端体验类任务优先跑真实链路 smoke，没有真实验证不能把用户体验质量标记为 `DONE`。
5. 自审 `git diff --check`、中文 Markdown 乱码、staged 敏感值、artifact / 端口 / 进程状态。
6. 更新 `CURRENT_TASK.md`、`STATE.md`、`PROGRESS_LOG.md`，必要时更新 `ROADMAP_RAG.md` 和 `DEMO_SMOKE_RECORD.md`。
7. 精确 `git add` 相关文件并提交一条一行 conventional commit；不得使用 `git add .`。
8. 再次检查 `git status --short`；若大目标未完成且无阻塞，继续下一片。

自驱迭代模式不需要因为启动本地 tunnel / backend / frontend、创建临时 smoke 数据、运行真实 smoke、使用本机已有 `.env` 中的真实 provider / Qdrant / MySQL 配置而停下来等确认；但任何敏感值只能由应用或脚本读取，禁止复制到回复、文档、commit message 或 artifact。

自驱迭代模式必须在以下情况停止并向用户汇报：大目标已完成；需要产品取舍；需要改数据库结构、删除业务数据、清空 collection、远程 Docker 启停 / 重启 / 迁移、改防火墙或云资源、大规模或高成本真实 provider 调用、push；无法脱敏的证据；连续验证失败且本地证据不足；发现影响当前切片的无关未提交改动；用户要求暂停、只读或进入 Plan 阶段。

自驱迭代提交规则：每个提交必须是小闭环；验证不完整时状态只能写 `REVIEW`；环境或权限缺失写 `BLOCKED`；不得提交 `.env`、artifact 原文、日志、截图、真实密钥、云地址或连接串。

## Commit Message 规则

14. 默认使用一行 conventional commits 格式（`type(scope): description`）。
15. 不生成多行 commit body，除非用户明确要求。
16. 不添加 `Co-Authored-By` 或任何形式的共同作者签名。
17. 不出现 Claude、Anthropic、Opus、AI assistant、Codex 等第三方工具或模型名称。
18. 不把详细功能列表写进 git commit message；详细实现说明写入 `docs/ai-dev/PROGRESS_LOG.md`、`docs/ai-dev/STATE.md` 或 `docs/ai-dev/CURRENT_TASK.md`，历史追溯资料才写入 `docs/archive/`。
19. commit message 要像正常开发者提交，而不是 AI 生成说明。
20. 推荐格式示例：
    - `feat(agent): add document agent demo with tool orchestration`
    - `feat(ai): improve document QA SSE robustness`
    - `feat(frontend): improve document QA streaming experience`
    - `docs: update collaboration workflow`
    - `test(agent): add document agent service tests`

## 编码规范

- 中文 Markdown 编码安全是硬性规则：修改 `README.md`、`docs/**/*.md`、`backend/README.md`、`frontend/README.md` 前后，必须检查乱码特征 `锛|鏂|銆|闈|�`。
- 不要用普通 `Get-Content -Raw` 读取中文 Markdown 后再整文件写回；Windows PowerShell 可能把无 BOM UTF-8 按本地编码解码，导致 mojibake。
- 如必须脚本读写中文文档，必须显式使用 UTF-8：`[System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)` 和 `WriteAllText` with `UTF8Encoding($false)`。
- 小范围修改中文文档优先使用 `apply_patch`；不要对中文文档做无必要的整文件重写、格式化或编码转换。
- 如果发现当前文件已有乱码，必须先停止并向用户汇报，不能继续在乱码内容上追加修改；需要从最近正常版本恢复后再合入必要变更。
- 保持小步修改，优先复用现有模块，不做无意义大重构。
- 后端优先保持 controller / service / mapper / DTO / VO 分层清晰。
- 前端优先复用现有视觉语言、MarkdownViewer、API 封装和页面布局。
- 业务链路变更必须同步测试或至少同步可复现验证路径。
- 配置变更必须同步 README 或 `.env.example`，但不能提交真实密钥。
- 文档必须区分“已实现 / 半实现 / 未实现 / 不确定”。

## 不要随意修改

- 不要随意修改数据库表结构、迁移脚本、依赖版本、运行配置逻辑。
- 不要把 mock / demo 能力写成生产能力。
- 不要把轻量检索增强写成向量 RAG。
- 不要把最小 Agent 写成复杂多 Agent 平台。
- 不要直接操作云服务器 Docker 中间件；涉及远程 MySQL、Redis、RocketMQ、MinIO、Prometheus、Qdrant 或服务器网络时，必须走 `hk-ops` 授权流程。
- 不要删除用户本地文件、日志、截图或临时产物，除非用户明确要求。
- 不要提交 `.env`、`.env.local`、日志、截图、Playwright 临时目录、测试产物或 IDE 状态文件。

## 面向 RAG / 后端实习面试的工程化亮点

可重点讲：

- RocketMQ + Outbox 的异步解析任务链路。
- Redisson 分布式锁、幂等消费、去重与并发保护。
- MinIO 对象存储、分片上传与断点续传思路。
- Redis 缓存、会话上下文、令牌桶限流与热点链路优化。
- Spring Boot 分层架构、MyBatis-Plus、Actuator、Prometheus 指标暴露。
- 知识库 RAG 主链路：chunk、embedding、Qdrant、metadata filter、retrieval、grounded QA、citation、no-evidence 和质量门禁。
- Conversation Context / Memory：短期上下文、摘要、长期记忆候选、KnowledgeBase evidence 和 Context Trace。
- SSE 流式输出与普通问答一致性治理。
- 可复现 eval / benchmark artifact 与质量门禁意识。

不能硬吹：完整商业 SaaS、复杂 PDF 智能解析、生产级多 Agent、生产短信网关、线上 SLA、大规模压测；v3 populated-KB no-evidence 仅是 smoke 级 PASS，不能写成大规模生产 relevance benchmark。
