# DocPilot 面向 AI 文档问答的企业级全栈解决方案

> **DocPilot** 是一个面向 AI 文档问答场景的全栈工程项目，提供了从**大文件分片上传、异步文档解析、状态流转到大模型流式问答（SSE）**的完整业务闭环。
>
>💡 **设计初衷**：本项目不仅提供前卫的 AI 产品交互体验体验（Next.js 14），更深耕于后端工程的健壮性。重点攻坚高并发下的异步解耦、分布式事务一致性（Outbox 消息补偿）、请求幂等与高可用降级策略，是绝佳的“真实复杂业务场景”演示沙盒。

## 项目预览
<img width="2543" height="1401" alt="image" src="https://github.com/user-attachments/assets/928a2778-0668-4067-934c-3d2e25c86fc9" />
<img width="2558" height="1199" alt="image" src="https://github.com/user-attachments/assets/feaaaf26-ea95-432d-bd10-8b41f26d14fe" />
<img width="2558" height="1365" alt="image" src="https://github.com/user-attachments/assets/d9a1b0d7-8471-4e98-b6b2-8f4fed5cd1b9" />
<img width="2556" height="1396" alt="image" src="https://github.com/user-attachments/assets/b8a78695-a793-4f1e-a316-24467b17d1bc" />
<img width="2555" height="1399" alt="image" src="https://github.com/user-attachments/assets/d757393f-e667-4ef7-8598-0993f97023f1" />

## 🚀 核心亮点

- **高可靠异步解析链路**：采用 **Outbox 模式 + RocketMQ** 彻底解决本地事务与消息通知不一致的问题。配合定时后台补偿扫表，确保庞大的文档解析任务在任何极端宕机下 100% 可靠投递。
- **并发防抖与绝对幂等**：基于 **Redisson 分布式锁**和 DB 唯一索引拦截高频上传与任务重复创建；MQ 消费端引入消费记录去重表，保障哪怕发生网络抖动和重发，解析引擎也不会重复扣减性能。
- **大文件分片与断点合并**：基于 **MinIO** 封装了一套健壮的分片上传与合并调度流程，支持大文件的分块连续上传、进度感知和快速重试，大幅提升弱网环境下的上传成功率。
- **极致的 AI SSE 流式问答**：支持打字机效果的 **SSE（Server-Sent Events）** 流式时延极低输出；核心问答链路配备了**降级容灾机制**，当流式接口出错或超时，自动无缝降级为普通同步问答。
- **多级防线与可观测性**：基于 **Redis 令牌桶算法**对高昂的大模型调用接口做细粒度限流，严防雪崩；全局集成 **Spring Boot Actuator + Prometheus** 打通了核心业务指标（缓存命中率、解析成功率等）的埋点埋点。

## 🏗️ 核心架构与技术栈

- **后端底座**: `Java 17` + `Spring Boot 3` + `MyBatis-Plus`
- **核心中间件**: `MySQL 8` + `Redis` + `RocketMQ 5` + `MinIO`
- **前端架构**: `Next.js 14 (App Router)` + `React` + `TypeScript` + `Tailwind CSS`
- **高可用与监控**: `Redisson` + `Docker Compose` + `Prometheus`

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

## 量化结果（可复现边界）

仓库包含 `Task11_6BenchmarkTest` 基准测试与脚本，当前可稳定复现以下“门槛结论”（非线上 SLA）：

- 文档详情缓存命中延迟 **低于** 缓存未命中（同机基准对比）。
- 问答缓存命中延迟 **低于** 缓存未命中（同机基准对比）。
- `document/create` 与 `task/parse/create` 在测试阈值下要求成功率 `>= 99%`。
- 异步非阻塞证明要求：`task/parse/create` 在大多数请求中先返回，`responseBeforeParseFinishRate >= 95%`。

> 边界说明：以上来自本地基准 harness 与断言门槛，结果会受机器配置与运行环境影响。

## 已知限制

- AI 默认 `AI_MODE=mock`；切换 `real` 模式需配置 `AI_REAL_*` 参数与可用模型服务。
- RocketMQ 异步链路依赖 `ROCKETMQ_ENABLED=true` 与可用 NameServer；关闭后会走 Noop Producer。
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

