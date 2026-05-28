# DocPilot

> AI 文档解析与问答工程化平台。项目围绕“上传文档 -> 异步解析 -> 文档问答 -> SSE 流式输出 -> 引用证据 -> Agent 工具执行与 Trace 展示”这条链路展开，呈现一个从业务流程、后端工程到 AI 交互体验逐步闭环的全栈项目。

DocPilot 关注的不只是“能问答”，而是围绕文档型 AI 应用常见的工程问题做一套可演示、可追踪、可复盘的实现：异步任务投递、幂等消费、对象存储、缓存与限流、SSE 降级、引用证据、Agent 工具选择、执行步骤落库和脱敏调试信息。

## 项目画像

| 方向 | 项目中对应的展示点 |
| --- | --- |
| 后端工程 | Spring Boot 分层、MyBatis-Plus、RocketMQ + Outbox、Redisson 幂等、Redis 缓存与限流、MinIO 上传链路 |
| AI 应用 | 文档问答、SSE 流式输出、引用证据、mock / real provider 边界、RAG context 与 eval artifact |
| Agent / RAG | ToolRegistry、ToolSelector、`llm_execute` 默认关闭模式、AgentTask / AgentStep trace、RAG retrieved chunks 与脱敏 debug summary |
| 全栈联调 | Next.js 页面、文档状态轮询、问答流式事件解析、Agent Showcase 可视化、错误降级与空状态文案 |

## 建议阅读顺序

- 先看 **页面预览** 和 **核心看点**，快速建立对项目形态的第一印象。
- 再看 **当前实现状态** 和 **核心工程点**，了解哪些链路已经落到代码和页面。
- 最后看 **量化与证据链**、**当前边界** 和 **演示建议**，确认验证方式与能力边界。

## 核心看点

- **业务闭环**：账号登录、文件上传、文档创建、异步解析、文档列表 / 详情、普通问答、SSE 流式问答、引用证据和历史问答。
- **异步链路**：使用 Outbox + RocketMQ 思路拆分接口响应与耗时解析，配合补偿扫描、消费去重和 Redisson 锁降低重复任务与消息不一致风险。
- **AI 问答体验**：支持普通问答与 SSE 流式输出；流式异常时回退普通问答；回答展示 Markdown、代码块和引用片段。
- **Agent Showcase**：`/agent` 页面展示工具选择、`routingReason`、`matchedKeywords`、`taskId`、持久化 steps、最终回答、citations，以及 RAG 召回片段、score 和 metadata。
- **可复盘的工程细节**：README、截图、smoke 脚本、eval artifact 和协作文档共同保留实现证据，便于从页面演示追溯到后端链路。

## 当前实现状态

| 能力 | 当前状态 |
| --- | --- |
| 文档上传与创建 | 已实现普通上传、分片上传会话、文档创建与解析任务创建 |
| 异步解析任务 | 已实现 Outbox / RocketMQ 链路设计、解析任务状态追踪、补偿与幂等相关代码；完整运行依赖可用 MQ / consumer 环境 |
| 文档问答 | 已实现普通问答、历史问答、引用展示、Markdown 渲染 |
| SSE 流式问答 | 已实现流式事件解析、增量输出与失败降级 |
| Agent 工具链 | 已实现文档状态、摘要、问答、RAG 召回工具，以及 ToolRegistry / ToolSelector |
| Agent Trace | 已实现 AgentTask / AgentStep 持久化，前端可展示步骤、耗时、输入摘要和输出摘要 |
| RAG Showcase | 已实现 fake embedding + in-memory vector store 的 demo 路径、chunking、scope isolation、trace/debug summary 和 offline eval |
| Qdrant / embedding adapter | 已有默认关闭的 adapter / preflight / fake server 测试；真实 Qdrant 与真实 embedding runtime 作为可扩展方向保留 |
| LLM 工具选择 | 已实现默认关闭的 `llm_execute` 模式，服务端只执行 allowlist 内已有工具；当前采用文本 JSON 选择协议 |
| 观测与验证 | 保留 Actuator health、selector debug dump、benchmark / eval artifact、smoke 脚本和 lint/build/test 记录 |

## 页面预览

以下截图来自本地 runtime 验证，使用已解析测试文档演示 Agent / RAG 页面。截图不包含 API Key、token、真实公网 IP 或环境变量。

| 截图 | 展示内容 |
| --- | --- |
| ![Agent Showcase Overview](docs/assets/screenshots/agent-showcase-overview.png) | Agent Showcase 总览、文档选择和任务模板 |
| ![Agent RAG Retrieval Results](docs/assets/screenshots/agent-rag-retrieval-results.png) | `rag_tool` 决策、RAG retrieved chunk、score / similarity 和 metadata |
| ![Agent Routing Explanation](docs/assets/screenshots/agent-routing-explanation.png) | ToolSelector 的 routingReason 与 matchedKeywords |
| ![Agent Persisted Steps](docs/assets/screenshots/agent-persisted-steps.png) | AgentTask / AgentStep 持久化执行轨迹、toolName、status 和 duration |
| ![Agent Citations](docs/assets/screenshots/agent-citations.png) | 普通 QA 路径的 citations 与 trace |

## 技术栈

- **Backend**: Java 17, Spring Boot 3.3.x, MyBatis-Plus, MySQL, Redis, Redisson, RocketMQ, MinIO, Actuator, Micrometer
- **Frontend**: Next.js 14 App Router, React 18, TypeScript, Tailwind CSS, ReactMarkdown
- **AI / RAG**: mock answer service, OpenAI-compatible real provider path, fake embedding, in-memory vector store, default-off Qdrant adapter
- **Infra**: Docker Compose, MySQL, Redis, RocketMQ, MinIO, Prometheus demo config

## 系统主链路

1. 用户注册 / 登录，前端保存 token。
2. 用户上传 `txt / md / pdf` 文件，后端写入对象存储。
3. 用户创建文档，后端创建解析任务并进入异步解析链路。
4. 前端轮询文档状态，展示 `PENDING / PARSING / SUCCESS / FAILED` 等状态。
5. 用户进入文档详情，查看摘要、正文、解析状态和引用证据。
6. 用户发起普通问答或 SSE 流式问答。
7. 用户进入 `/agent`，选择已解析文档并运行摘要、问答或 RAG 召回类任务。
8. 前端展示 Agent 工具决策、执行步骤、持久化 trace、citations 和最终回答。

> 说明：`pdf` 目前主要是占位 / 基础解析边界，真实文本解析能力以 `txt / md` 更稳定。

## 核心工程点

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

### 轻量 RAG Showcase

当前 RAG Showcase 使用 fake embedding + in-memory vector store 展示 chunking、topK 召回、score、citation metadata、scope isolation、index lifecycle 和脱敏 debug summary。Qdrant HTTP adapter 与真实 embedding adapter 已有默认关闭路径和测试边界，适合作为后续接入真实向量库的演进基础。

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

最近一次仓库协作记录中，后端全量测试、前端 lint 和前端 build 均通过；具体记录见 `docs/CHANGELOG_CODING.md`。如果你要复现，请以本机实际运行结果为准。

## 量化与证据链

仓库内保留了 eval / benchmark artifact，用于说明项目不是只靠主观展示。当前公开 README 只引用仓库已有 artifact，不把它写成线上 SLA。

当前权威基准来自：

```text
docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json
```

记录摘要：

- `datasetName`: `stagec-core-qa-eval`
- `datasetVersion`: `2026-04-19-r2`
- `caseCount / streamPairs`: `20 / 8`
- `answerSuccessRate`: `90%`
- `citationHitRate`: `100%`
- `casePassRate`: `85%`
- `streamVsNonStreamConsistency`: `87.5%`
- `Gate`: `passed=true`

边界：这是仓库内 artifact 记录，不是线上服务承诺；artifact 未记录完整运行时 provider / 模型配置。后续如果用于正式展示，建议重新运行 eval 并补充运行时配置说明。

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
