# AGENTS.md

本文件是 DocPilot 仓库给后续 Codex / API agent / Claude Code 等协作代理读取的项目规则。每轮开始先读本文件，再读 `docs/TODO_NEXT.md`、`docs/CODEX_HANDOFF.md`、`docs/CHANGELOG_CODING.md`，并检查 `git status` 与 `git diff`。本地 subagents 与 MCP 工具能力边界见 `docs/CODEX_TOOLING.md`。

## 项目定位

DocPilot 是一个面向文档上传、异步解析、检索增强问答与最小 Agent 演示的 AI 文档平台。当前项目适合展示 Java 后端工程能力、AI 应用工程链路、前后端联调能力和工程化验证意识。

当前口径要克制：项目是轻量检索增强问答，不是完整向量 RAG 平台；Agent 是基于现有文档业务工具的最小闭环，不是成熟多 Agent 编排系统。

## 技术栈

- 后端：Java 17、Spring Boot 3.3.x、Maven、MyBatis-Plus、MySQL、Redis、Redisson、RocketMQ、MinIO、Actuator、Micrometer / Prometheus。
- 前端：Next.js 14 App Router、React 18、TypeScript、Tailwind CSS、ReactMarkdown、remark-gfm、rehype-highlight、rehype-sanitize。
- 中间件：MySQL、Redis、RocketMQ NameServer / Broker / Dashboard、MinIO、Prometheus。
- AI 接入：mock answer service 与 OpenAI-compatible / SiliconFlow 风格真实模型接入口径并存，真实模型依赖本地环境变量。

## 目录结构

- `backend/`：Spring Boot 后端，包含认证、上传、文档、任务、MQ、AI 问答、Agent、benchmark / eval 脚本等。
- `frontend/`：Next.js 前端，包含首页、登录、dashboard、上传、文档列表、文档详情、Agent 页面等。
- `deploy/`：演示环境相关初始化脚本和中间件配置。
- `.run/`：IDEA Run Configuration，供本地 / 云中间件模式启动参考。
- `docs/`：协作、交接、TODO、展示证据和阶段文档。不要把这里变成无限流水账。
- `docker-compose.demo.yml`：本地演示中间件编排。
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
npm install
npm run dev
```

本地中间件演示环境：

```powershell
docker compose -f docker-compose.demo.yml up -d
```

实际运行前请根据 `backend/README.md`、`frontend/README.md` 和 `.env.example` / `.env.demo.example` 检查环境变量。不要提交真实 `.env`、密钥、密码或云服务凭据。

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
2. 每轮开始必须先读：`AGENTS.md`、`docs/TODO_NEXT.md`、`docs/CODEX_HANDOFF.md`、`docs/CHANGELOG_CODING.md`、`git status`、`git diff`。
3. 每轮只允许执行一个 TODO 任务；如果任务过大，先拆小再做。
4. 动代码前必须先说明本轮计划、涉及文件、验证方式。
5. 不允许伪造测试结果；没有真实验证结果的任务不能标记为 `DONE`。
6. 代码已改但验证不完整，只能把任务标记为 `REVIEW`。
7. 缺环境、账号、密钥、数据库、中间件或用户确认时，任务标记为 `BLOCKED`，并说明阻塞原因。
8. 每轮结束后必须更新 `docs/TODO_NEXT.md`、`docs/CODEX_HANDOFF.md`、`docs/CHANGELOG_CODING.md`。
9. 不要自动执行 `git commit` / `git push`，除非用户明确要求。
10. 不要留下本地服务进程或端口占用；如启动了后端、前端或脚本服务，结束前要清理并说明端口状态。
11. 如果发现工作区有未提交改动或未跟踪文件，必须先向用户汇报，不要直接执行 `git add` / `git commit` / `git push`。
12. 每轮开始必须检查 `.env`、`.env.local`、`.env.*`、`application-*.yml`、`*.example` 中是否存在硬编码密钥、密码、token、真实云服务 IP；如有必须先告警，不能把敏感值复制到回复里。
13. 使用 subagents、context7 MCP、playwright MCP 或远程 hk-ops 前，先遵守 `docs/CODEX_TOOLING.md` 中的用途、授权和禁止事项。

## 编码规范

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
- 不要删除用户本地文件、日志、截图或临时产物，除非用户明确要求。
- 不要提交 `.env`、`.env.local`、日志、截图、Playwright 临时目录、测试产物或 IDE 状态文件。

## 面向 Java 后端实习面试的工程化亮点

可重点讲：

- RocketMQ + Outbox 的异步解析任务链路。
- Redisson 分布式锁、幂等消费、去重与并发保护。
- MinIO 对象存储、分片上传与断点续传思路。
- Redis 缓存、会话上下文、令牌桶限流与热点链路优化。
- Spring Boot 分层架构、MyBatis-Plus、Actuator、Prometheus 指标暴露。
- AI 问答的轻量检索增强闭环：检索、上下文组装、生成、引用展示。
- SSE 流式输出与普通问答一致性治理。
- 可复现 eval / benchmark artifact 与质量门禁意识。

不能硬吹：完整向量数据库 RAG、复杂 PDF 智能解析、生产级多 Agent、生产短信网关、线上 SLA。
