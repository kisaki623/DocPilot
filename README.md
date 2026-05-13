# DocPilot

> 面向 AI 文档问答场景的全栈工程项目：覆盖账号认证、文件上传、异步解析、文档检索与问答（含 SSE 流式输出）和最小 Agent 工具链演示。
>
> 项目重点不在“堆功能页”，而在可验证的工程链路：Outbox + RocketMQ 异步可靠投递、Redis/Redisson 幂等与限流、MinIO 分片上传、Prometheus 指标可观测。

## Why This Project

DocPilot 适合作为后端工程 + 全栈联调能力的展示样本：
- 业务链路完整：注册/登录 -> 上传 -> 创建解析任务 -> 文档详情 -> AI 问答
- 关键中间件可切换：本地 demo 可一键拉起，云环境可按配置切换
- 面向真实约束：限流、幂等、异步补偿、可观测性、错误降级都在主链路内可见

## 核心亮点

- **Outbox + RocketMQ 异步解析链路**：`task/parse/create` 返回后，解析通过消息链路异步推进；含补偿扫描与重投，避免事务与消息不一致。
- **消费幂等 + 分布式锁**：解析消费端用消费记录去重，解析任务创建侧用 Redisson 锁防重复创建。
- **MinIO + 分片上传/断点续传**：支持普通上传与分片上传会话，含上传状态查询与合并完成。
- **AI 问答 + SSE 流式输出**：详情页支持普通问答与流式问答切换，流式失败自动降级普通问答。
- **最小 Agent 工具链闭环**：`/api/ai/agent/run` 先检查文档状态，再按规则选择 summary / QA 工具；前端 `/agent` 展示决策、步骤 trace、最终回答和引用。
- **Agent 执行轨迹落库**：`tb_agent_task` / `tb_agent_step` 记录每次 Agent run 和工具步骤，后端提供 task / step 查询接口，前端可按 `taskId` 展示持久化执行轨迹。
- **Redis 缓存 + 令牌桶限流 + 会话上下文**：文档详情缓存、问答答案缓存、问答限流、短期会话上下文全部可见。
- **可观测性与压测基线**：内置 Actuator/Prometheus 指标，并提供 benchmark harness 与 smoke 脚本用于复现。

## 系统主链路

1. **注册/登录**：前端 `/login` 默认注册模式，认证主入口为账号密码。
2. **上传文件**：上传页支持 `txt / md / pdf`，上传后自动进入文档创建与解析任务创建。
3. **异步解析**：解析任务入队后异步执行，前端轮询详情状态（`PENDING -> ... -> SUCCESS/FAILED`）。
4. **文档浏览**：列表页按状态查看文档，详情页查看摘要、正文、状态与引用证据。
5. **AI 问答**：在详情页进行普通/SSE 问答，查看引用片段与历史问答。
6. **Agent 演示**：在 `/agent` 选择文档并输入任务，查看工具选择、运行步骤、持久化 task / step trace 与最终回答。

> 说明：上传页已将 `file/upload -> document/create -> task/parse/create` 串为一条流程；无需手动逐接口触发。

## 技术栈

- **Backend**: Java 17, Spring Boot 3, MyBatis-Plus, MySQL, Redis, RocketMQ, MinIO, Redisson, Micrometer
- **Frontend**: Next.js 14 (App Router), React, TypeScript, Tailwind CSS
- **Infra / Middleware**: Docker Compose, MySQL, Redis, RocketMQ, MinIO
- **Observability**: Spring Boot Actuator, Prometheus

## 页面预览

当前仓库未提交可公开截图文件（避免误传本地调试产物）。

建议补充到 `assets/screenshots/` 后在此处引用（推荐顺序）：
1. `01-login-register-and-password-YYYYMMDD.png`（注册/登录双模式）
2. `02-upload-workflow-success-YYYYMMDD.png`（上传 + 自动建文档/建任务）
3. `03-documents-list-filter-status-YYYYMMDD.png`（搜索/筛选/状态）
4. `04-detail-qa-citations-YYYYMMDD.png`（详情 + 回答 + 引用）
5. `05-detail-sse-streaming-in-progress-YYYYMMDD.png`（流式输出过程态）
6. `06-agent-persisted-trace-YYYYMMDD.png`（Agent 决策 + 持久化执行轨迹）

## 快速开始（本地演示）

### 0) 前置依赖

- Docker Desktop（需确保 daemon 已启动）
- Java 17+
- Maven 3.9+
- Node.js 20+（建议 LTS）
- npm 10+

### 1) 启动中间件（MySQL/Redis/RocketMQ/MinIO/Prometheus）

```bash
docker compose -f docker-compose.demo.yml up -d
docker compose -f docker-compose.demo.yml ps
```

### 2) 启动后端（默认 8081）

Windows PowerShell:
```powershell
cd backend
Copy-Item .env.demo.example .env
mvn spring-boot:run
```

macOS/Linux:
```bash
cd backend
cp .env.demo.example .env
mvn spring-boot:run
```

健康检查：
```bash
curl http://localhost:8081/actuator/health
```

### 3) 启动前端（默认 3000）

Windows PowerShell:
```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

macOS/Linux:
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

> 若 3000 被占用，Next.js 会自动切到 3001/3002，请以终端输出端口为准。

### 4) 可选：运行最小链路 smoke

```powershell
powershell -ExecutionPolicy Bypass -File backend/scripts/demo/smoke-main-flow.ps1 -BaseUrl http://127.0.0.1:8081
powershell -ExecutionPolicy Bypass -File backend/scripts/demo/smoke-qa-stream.ps1 -BackendBaseUrl http://127.0.0.1:8081
powershell -ExecutionPolicy Bypass -File backend/scripts/agent/smoke-agent-min.ps1 -BackendBaseUrl http://127.0.0.1:8081
```

## 项目结构

```text
DocPilot/
  backend/                 # Spring Boot 后端
  frontend/                # Next.js 前端
  deploy/                  # compose 依赖配置（MySQL / RocketMQ / Prometheus）
  .run/                    # IDEA 运行配置（Backend/Frontend Local + HK Cloud）
  docker-compose.demo.yml  # 本地演示中间件编排
```

## 核心能力说明（解决了什么问题）

- **异步解耦（RocketMQ + Outbox）**
  将“接口响应”与“耗时解析”拆开，避免同步阻塞；通过 outbox 补偿降低消息丢失风险。

- **幂等与并发控制（Redisson + 消费去重）**
  解决并发重复创建任务、消息重复消费导致的重复执行问题。

- **对象存储与上传体验（MinIO + Chunk）**
  支持大文件分片上传、续传与合并，减少单次上传失败重试成本。

- **问答体验与稳态（SSE + 降级）**
  流式输出提升交互反馈速度；流式异常时自动降级普通问答，保证可用性。

- **性能与稳定性（Redis 缓存 + 限流）**
  热路径走缓存，问答入口做令牌桶限流，降低高并发下的抖动和雪崩风险。

- **可观测性（Actuator + Prometheus）**
  通过指标查看健康状态与关键业务计数，便于演示和定位问题。

- **最小 Agent 工程化闭环**
  当前 Agent 是基于已有文档业务工具的最小闭环：状态检查、摘要、问答三类工具由 `ToolRegistry` 注册，`DocumentToolSelector` 按关键词规则选择工具链。执行结果会写入 `tb_agent_task` / `tb_agent_step`，并通过查询接口和前端页面展示持久化 trace。

## 当前验证记录

- 后端 AI / SSE 改进提交已通过 Maven 测试基线：`mvn test -DskipITs` 通过，记录为 141 tests passed；后续加入 Agent selector 独立测试后，全量后端测试记录为 147 tests passed。
- Agent runtime smoke 已通过：`/api/ai/agent/run` 返回有效 `taskId`，summary / QA 决策正常，task / step 查询接口可用。
- 前端 Agent trace 页面已通过 Playwright 运行时验证：`/agent` 可展示 taskId、status、decision、step count、toolName、durationMs、inputSummary、outputSummary。
- 前端质量检查已通过：`npm run lint` 与 `npm run build` 均通过。
- 远程开发库中 `tb_agent_task` / `tb_agent_step` 已通过 hk-ops 只读核验，可查到 runtime smoke 产生的真实记录。

## 量化结果（可复现边界）

当前权威基准来自仓库内最新 artifact：`docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`。

- `generatedAt`: `2026-04-18T18:58:42.2763129+00:00`
- `datasetName`: `stagec-core-qa-eval`
- `datasetVersion`: `2026-04-19-r2`
- `caseCount / streamPairs`: `20 / 8`
- `answerSuccessRate`: `90%`
- `citationHitRate`: `100%`
- `casePassRate`: `85%`
- `streamVsNonStreamConsistency`: `87.5%`
- Gate: `passed=true`

> 边界说明：以上是仓库内当前 artifact 记录，不是本轮重新运行结果；artifact 未记录实际运行时 `AI_MODE`、模型名或 provider；该结果用于本地版本证据链，不代表线上 SLA。后续需通过 T005 重新运行 eval，并补充运行时配置记录。

## 已知限制

- `pdf` 解析目前为占位逻辑；真实文本解析能力主要针对 `txt/md`。
- AI 默认 `AI_MODE=mock`；切换 `real` 模式需配置 `AI_REAL_*` 参数与可用模型服务。
- RocketMQ 异步链路依赖 `ROCKETMQ_ENABLED=true` 与可用 NameServer；关闭后会走 Noop Producer。
- 当前 Agent 是规则 / 关键词 ToolSelector，不是 LLM Tool Calling，也不是成熟多 Agent 编排平台。
- 当前 Agent 执行仍是同步 API 链路，尚未接 MQ 异步 Agent、Outbox 或复杂任务调度。
- 当前未接向量库 / 完整 RAG / MCP / Spring AI / LangChain4j；文档问答仍是轻量检索增强。
- 短信验证码接口保留为兼容联调能力，不代表已接入生产短信网关。
- Prometheus 默认抓取 `host.docker.internal:8081`；Linux 环境需要改为宿主机可达地址。

## 运行与配置补充

- 环境变量模板：
  - `backend/.env.demo.example`
  - `backend/.env.example`
  - `backend/.env.cloud.example`
  - `frontend/.env.example`
- 请勿提交：
  - `backend/.env`
  - `backend/.env.cloud`
  - `frontend/.env.local`

---

如果你在准备面试演示，建议优先展示这条 5 分钟链路：
`注册/登录 -> 上传 -> 自动创建解析任务 -> 详情页 SSE 问答 -> Agent 页面查看工具决策与持久化执行轨迹`。
