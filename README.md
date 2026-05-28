# DocPilot

> AI 文档解析与问答工程化平台。项目围绕“上传文档 -> 异步解析 -> 文档问答 -> SSE 流式输出 -> 引用证据 -> Agent 工具执行与 Trace 展示”这条链路展开，呈现一个从业务流程、后端工程到 AI 交互体验逐步闭环的全栈项目。

DocPilot 关注的不只是“能问答”，而是围绕文档型 AI 应用常见的工程问题做一套可演示、可追踪、可复盘的实现：异步任务投递、幂等消费、对象存储、缓存与限流、SSE 降级、引用证据、Agent 工具选择、执行步骤落库和脱敏调试信息。

## 项目定位

DocPilot 是一个面向文档上传、异步解析、文档问答和 Agent 工具编排的工程化展示项目。它适合作为 Java 后端实习、AI 应用开发、Agent 开发和 AI 全栈方向的作品入口，重点展示一条可运行、可观察、可复盘的 AI 文档处理链路。

| 方向 | README 前半部分重点展示 |
| --- | --- |
| 后端工程 | Spring Boot 分层、MyBatis-Plus、RocketMQ + Outbox、Redisson 幂等、Redis 缓存与限流、MinIO 上传链路 |
| AI 应用 | 文档问答、SSE 流式输出、引用证据、检索召回演示、问答历史与异常降级 |
| Agent 工作流 | ToolRegistry、ToolSelector、AgentTask / AgentStep trace、工具选择依据与执行轨迹 |
| 全栈联调 | Next.js 页面、文档状态轮询、问答流式事件解析、Agent 工作流可视化、错误降级与空状态文案 |

## 建议阅读顺序

- 先看 **演示链路** 和 **核心能力**，快速建立对项目形态的第一印象。
- 再看 **页面预览**、**当前实现状态** 和 **核心工程设计**，了解哪些链路已经落到代码和页面。
- 最后看 **验证方式**、**当前边界** 和 **演示建议**，确认复现方式与能力边界。

## 演示链路

推荐按下面这条链路演示：

```text
登录工作台
-> 上传 txt / md 文档
-> 观察异步解析状态
-> 进入文档详情页提问
-> 查看 SSE 输出、Markdown 渲染和引用证据
-> 进入 Agent 页面运行摘要 / 问答 / 检索召回任务
-> 查看工具选择、执行步骤、最终回答和 Agent Trace
```

## 核心能力

- **业务闭环**：账号登录、文件上传、文档创建、异步解析、文档列表 / 详情、普通问答、SSE 流式问答、引用证据和历史问答。
- **异步链路**：使用 Outbox + RocketMQ 思路拆分接口响应与耗时解析，配合补偿扫描、消费去重和 Redisson 锁降低重复任务与消息不一致风险。
- **AI 问答体验**：支持普通问答与 SSE 流式输出；流式异常时回退普通问答；回答展示 Markdown、代码块和引用片段。
- **Agent 工作流**：`/agent` 页面展示工具选择、执行步骤、持久化轨迹、最终回答、引用证据和检索召回结果。
- **可复盘的工程细节**：README、截图、smoke 脚本和本地验证记录共同保留实现证据，便于从页面演示追溯到后端链路。

## 当前实现状态

| 能力 | 当前状态 |
| --- | --- |
| 文档上传与创建 | 已实现普通上传、分片上传会话、文档创建与解析任务创建 |
| 异步解析任务 | 已实现 Outbox / RocketMQ 链路设计、解析任务状态追踪、补偿与幂等相关代码；完整运行依赖可用 MQ / consumer 环境 |
| 文档问答 | 已实现普通问答、历史问答、引用展示、Markdown 渲染 |
| SSE 流式问答 | 已实现流式事件解析、增量输出与失败降级 |
| Agent 工具链 | 已实现文档状态、摘要、问答、检索召回工具，以及 ToolRegistry / ToolSelector |
| Agent Trace | 已实现 AgentTask / AgentStep 持久化，前端可展示步骤、耗时、输入摘要和输出摘要 |
| 检索召回展示 | 已实现 chunking、scope isolation、召回片段、相关度、引用 metadata 和脱敏 trace summary |
| 观测与验证 | 保留 Actuator health、benchmark / eval 记录、smoke 脚本和 lint/build/test 验证方式 |

## 页面预览

以下截图来自本地 runtime 验证，使用已解析测试文档演示 Agent / RAG 页面。截图不包含 API Key、token、真实公网 IP 或环境变量。

| 截图 | 展示内容 |
| --- | --- |
| ![Agent Showcase Overview](docs/assets/screenshots/agent-showcase-overview.png) | Agent 工作流总览、文档选择和任务模板 |
| ![Agent RAG Retrieval Results](docs/assets/screenshots/agent-rag-retrieval-results.png) | 检索召回结果、相关片段和来源信息 |
| ![Agent Routing Explanation](docs/assets/screenshots/agent-routing-explanation.png) | 工具选择依据与命中关键词 |
| ![Agent Persisted Steps](docs/assets/screenshots/agent-persisted-steps.png) | AgentTask / AgentStep 持久化执行轨迹、工具步骤、状态和耗时 |
| ![Agent Citations](docs/assets/screenshots/agent-citations.png) | 普通问答路径的引用证据与轨迹视图 |

## 技术栈

- **Backend**: Java 17, Spring Boot 3.3.x, MyBatis-Plus, MySQL, Redis, Redisson, RocketMQ, MinIO, Actuator, Micrometer
- **Frontend**: Next.js 14 App Router, React 18, TypeScript, Tailwind CSS, ReactMarkdown
- **AI / Agent**: 文档问答、SSE streaming、引用证据、检索召回、ToolRegistry / ToolSelector、Agent Trace
- **Infra**: Docker Compose, MySQL, Redis, RocketMQ, MinIO, Prometheus demo config

## 系统主链路

1. 用户注册 / 登录，前端保存 token。
2. 用户上传 `txt / md / pdf` 文件，后端写入对象存储。
3. 用户创建文档，后端创建解析任务并进入异步解析链路。
4. 前端轮询文档状态，展示 `PENDING / PARSING / SUCCESS / FAILED` 等状态。
5. 用户进入文档详情，查看摘要、正文、解析状态和引用证据。
6. 用户发起普通问答或 SSE 流式问答。
7. 用户进入 `/agent`，选择已解析文档并运行摘要、问答或检索召回类任务。
8. 前端展示 Agent 工具决策、执行步骤、持久化 trace、citations 和最终回答。

> 说明：`pdf` 目前主要是占位 / 基础解析边界，真实文本解析能力以 `txt / md` 更稳定。

## 核心工程设计

### Outbox + RocketMQ 异步解析

文档创建后不会在请求线程里同步完成解析，而是创建解析任务并通过消息链路异步推进。项目中包含 Outbox、补偿扫描、消费记录、去重和任务状态追踪相关实现，用来展示异步链路的可靠性设计。

### Redisson 幂等与并发保护

解析任务创建侧使用分布式锁降低重复创建风险，消费侧通过记录与状态判断避免重复执行。这个部分适合在面试中讲“接口幂等、消息重复消费、并发任务保护”。

### MinIO 上传与对象存储

项目包含普通上传和分片上传会话，支持上传状态查询和合并完成。对象存储与文档业务记录分离，便于说明文件系统、数据库记录和解析任务之间的边界。

### AI 问答 + SSE 降级

文档详情页同时支持普通问答和 SSE 流式问答。前端按事件解析增量内容，展示引用证据；当流式链路失败时回退普通问答，避免用户只看到中断状态。

### Agent 工具执行与 Trace

Agent 目前聚焦文档业务场景，围绕状态查询、摘要、问答与 RAG 召回形成最小工具闭环。后端根据任务选择工具，执行过程写入 `AgentTask` / `AgentStep`，前端用 timeline 展示 step、耗时、输入摘要和输出摘要。

### 检索召回 Showcase

当前检索召回展示覆盖 chunking、topK 召回、相关度、citation metadata、scope isolation、index lifecycle 和脱敏 trace summary。它用于说明文档问答如何从“全文上下文”进一步演进到“召回片段 + 引用证据”的链路。

## 快速开始

### 1. 启动中间件

```bash
docker compose -f docker-compose.demo.yml up -d
docker compose -f docker-compose.demo.yml ps
```

### 2. 启动后端

Windows PowerShell:

```powershell
cd backend
Copy-Item .env.demo.example .env
mvn spring-boot:run
```

macOS / Linux:

```bash
cd backend
cp .env.demo.example .env
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8081/actuator/health
```

### 3. 启动前端

Windows PowerShell:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

macOS / Linux:

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

访问：

- Home: `http://localhost:3000/`
- Login: `http://localhost:3000/login`
- Dashboard: `http://localhost:3000/dashboard`
- Agent Showcase: `http://localhost:3000/agent`

> 若 3000 被占用，Next.js 会自动切到 3001/3002，请以终端输出端口为准。

## 常用验证命令

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

Smoke 脚本示例：

```powershell
powershell -ExecutionPolicy Bypass -File backend/scripts/demo/smoke-main-flow.ps1 -BaseUrl http://127.0.0.1:8081
powershell -ExecutionPolicy Bypass -File backend/scripts/demo/smoke-qa-stream.ps1 -BackendBaseUrl http://127.0.0.1:8081
powershell -ExecutionPolicy Bypass -File backend/scripts/agent/smoke-agent-min.ps1 -BackendBaseUrl http://127.0.0.1:8081
```

最近一次本地验证记录中，后端全量测试、前端 lint 和前端 build 均通过。公开 README 不依赖协作文档作为证明材料；如果你要复现，请以本机实际运行结果为准。

## 验证方式

仓库内保留过 eval / benchmark 记录，用于说明项目不是只靠主观展示。当前公开 README 不直接引用本地协作 artifact；如果用于正式展示，建议在目标环境重新运行测试并补充当次运行条件。

建议复现顺序：

```text
cd backend
mvn test -DskipITs

cd ../frontend
npm run lint
npm run build
```

说明：历史 eval 指标属于本地验证记录，不是线上服务承诺，也不代表固定 SLA。

## 项目结构

```text
DocPilot/
  backend/                 # Spring Boot 后端：认证、上传、文档、任务、MQ、AI 问答、Agent、eval / smoke
  frontend/                # Next.js 前端：首页、登录、dashboard、上传、文档列表、详情问答、Agent 页面
  deploy/                  # 本地演示依赖配置
  docs/                    # 协作文档、设计说明、验证记录和截图证据
  docker-compose.demo.yml  # 本地演示中间件编排
```

## 当前边界

- 项目定位为工程展示与面试演示环境，不按生产 SaaS 的 SLA 或运维标准承诺。
- 完整上传解析 runtime 依赖可用 RocketMQ NameServer / Broker / consumer；若关闭 MQ，会进入 no-op producer 路径，适合做接口联调但不会推进真实异步解析。
- AI 默认可使用 mock answer service；真实模型调用依赖本地环境变量和可用 OpenAI-compatible provider。
- PDF 解析能力有限，当前更适合展示 `txt / md` 文档链路。
- RAG Showcase 默认使用 fake embedding + in-memory vector store，适合展示检索增强链路；真实 embedding provider 和 Qdrant runtime 需要额外环境验证。
- Agent 当前围绕文档业务工具形成同步 API 闭环，MQ 异步 Agent 和多 Agent 编排属于后续演进方向。
- `llm_execute` 是默认关闭的 OpenAI-compatible chat completions JSON 选择方案，再由服务端 allowlist 执行已有工具；尚未切换到官方 tools/function_call 接口。
- selector Prometheus metrics 目前仍处于设计 / demo 边界，完整生产监控闭环留作后续扩展。
- 短信验证码接口保留为兼容联调能力，不代表已接入生产短信网关。

## 演示建议

建议优先展示这条 5 分钟链路：

```text
已解析文档
-> 文档详情页普通问答 / SSE 问答
-> 查看 citations 与 Markdown 渲染
-> Agent Showcase 运行摘要 / 问答 / RAG 召回任务
-> 展示 routingReason、matchedKeywords、retrieved chunks、score、trace/debug summary 和 persisted steps
-> 说明 llm_execute 默认关闭、allowlist + fallback 设计，以及 RAG 尚未接真实向量库生产链路
```

如果要展示“上传 -> 自动解析 -> 问答”的完整链路，请先确认 RocketMQ / consumer 环境可用。
