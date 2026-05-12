# DocPilot Backend

面向后端开发与联调的运行说明。对外展示信息请看根目录 `README.md`。

## 1. 运行要求

- Java 17+
- Maven 3.9+
- Docker（用于本地中间件）

## 2. 推荐启动方式（默认：本地应用 + 香港云中间件）

```bash
cd backend
cp .env.cloud.example .env   # Windows 可手动复制
mvn spring-boot:run
```

默认后端端口：`8081`。

重要说明：

1. 你当前日常开发默认口径是“后端本地运行 + 香港云中间件”。
2. 命令行启动前，建议先准备 `backend/.env`；不要直接裸跑 `mvn spring-boot:run`。
3. `application-local.yml` 默认读取 `.env`，命令行与 IDEA 都应统一走这套本机云配置。
4. 本地 compose 只作为可选 demo 路径，不是默认开发路径。

健康检查：

```bash
curl http://localhost:8081/actuator/health
```

## 3. 环境变量模板与优先级

- `.env.cloud.example`：香港云中间件示例模板，可复制为 `.env`
- `.env.demo.example`：纯本地 demo（配合根目录 `docker-compose.demo.yml`）
- `.env.example`：通用模板

推荐优先级：

1. 日常开发 / IDEA 启动：复制 `.env.cloud.example` 为 `.env`
2. 纯本地 demo：复制 `.env.demo.example` 为 `.env`
3. 通用本地开发：参考 `.env.example`

请勿提交真实密钥文件：

- `backend/.env`

## 4. 认证与核心接口口径

主认证入口（账号密码）：

- `POST /api/auth/register`
- `POST /api/auth/password/login`

兼容入口（短信验证码）：

- `POST /api/auth/code`
- `POST /api/auth/login`

解析与问答主链路：

- `POST /api/file/upload`
- `POST /api/document/create`
- `POST /api/task/parse/create`
- `POST /api/ai/qa`
- `POST /api/ai/qa/stream`
- `POST /api/ai/agent/run`（阶段 D：最小 Agent 工具编排入口）

补充口径：

- 当前问答链路为“轻量检索增强”（分片检索 + 上下文组装 + 生成 + 引用）。
- SSE 事件使用 `meta/chunk/done/error`；`meta/done` 会携带 `sessionId/documentId/citations`。
- Agent 当前为单 Agent 最小闭环，包含工具步骤：文档状态查询 -> 摘要或问答。

## 4.1 阶段 C / D 脚本入口

- 阶段 C 评测：
  - `powershell -ExecutionPolicy Bypass -File scripts/benchmark/run-stage-c-eval.ps1 -BackendBaseUrl http://127.0.0.1:8081`
- 阶段 D Agent smoke：
  - `powershell -ExecutionPolicy Bypass -File scripts/agent/smoke-agent-min.ps1 -BackendBaseUrl http://127.0.0.1:8081 -FilePath ..\README.md`

## 5. IDEA 运行配置（根目录 `.run/`）

- `DocPilot-Backend-Local`
- `DocPilot-Backend-App-Local`
- `DocPilot-Backend-HK-Cloud`
- `DocPilot-Backend-App-HK-Cloud`

HK Cloud 配置默认读取：

- `SPRING_CONFIG_IMPORT=optional:file:./.env[.properties]`

本地 demo 配置默认读取：

- `SPRING_CONFIG_IMPORT=optional:file:./.env.demo.example[.properties],optional:file:./.env[.properties]`

含义：

1. `HK-Cloud` 是当前默认开发配置。
2. `Local` 更适合无云权限时的纯本地 demo。
3. 云模式和命令行默认都读取 `backend/.env`。

## 6. 最小联调顺序

默认云中间件路径：

1. 准备 `backend/.env`
2. 启动 backend
3. 启动 frontend
4. 访问 `/login`，走“注册 -> 上传 -> 解析 -> 问答”主链路

纯本地 demo 路径：

1. 根目录启动中间件：`docker compose -f ../docker-compose.demo.yml up -d`
2. 使用 `.env.demo.example` 启动 backend
3. 启动 frontend
4. 访问 `/login`

## 7. 常见问题

1. `Port 8081 was already in use`：先执行 `scripts/demo/preflight-backend-port.ps1`。
2. SSE 跨域预检异常：确认前端 `NEXT_PUBLIC_BACKEND_BASE_URL` 与后端地址一致。
3. 云模式不可用：检查 `.env`、Redis 鉴权、RocketMQ NameServer 连通性。
4. 香港云中间件不可达：检查 `<CLOUD_HOST>` 的 MySQL / Redis / RocketMQ / MinIO 连通性，必要时临时切到 `docker-compose.demo.yml`。
