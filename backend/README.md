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

MySQL / Qdrant 已收口为远程本机监听。凡是需要访问云 MySQL 或真实 Qdrant 的运行验证，都要先在仓库根目录启动本地 tunnel，包括：

- 后端 `/actuator/health` runtime smoke
- 使用云 MySQL 的登录、上传、解析、会话或 KnowledgeBase 联调
- 真实 Qdrant indexing / retrieval / KnowledgeBase RAG smoke

普通离线单测、`mvn -DskipTests compile`、前端 `lint/build` 和未登录态页面 smoke 不要求启动 tunnel。

仓库 CI 只运行 `mvn test -DskipITs`、`npm run lint` 与 `npm run build`，用于守护离线回归；它不读取 `.env`、不启动 tunnel、不访问云 MySQL / Qdrant，也不能替代后续本机 `cloud-quality-smoke.ps1` 的真实链路验证。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/start-cloud-tunnels.ps1
```

重要说明：

1. 你当前日常开发默认口径是“后端本地运行 + 香港云中间件”。
2. 命令行启动前，建议先准备 `backend/.env`；不要直接裸跑 `mvn spring-boot:run`。
3. `application.yml` 默认读取 `.env`，`application-local.yml` 只保留 local profile 的端口、目录和中间件默认值覆盖。
4. 云 MySQL / Qdrant 默认通过本地 tunnel 访问：`MYSQL_HOST=127.0.0.1`、`MYSQL_PORT=13306`、`RAG_QDRANT_ENDPOINT=http://127.0.0.1:6333`。
5. 本地 compose 只作为可选 demo 路径，不是默认开发路径。
6. `mvn test -DskipITs` 默认使用 test profile，Maven Surefire 会覆盖 `.env` import，不要求启动 tunnel，也不应初始化 scheduled outbox / RocketMQ / Redisson 真实链路；若要证明云 MySQL / Qdrant runtime 可用，仍需按第 6 节启动 tunnel 后单独做 smoke。

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
- Document Parser MVP 支持 `txt / md`、文本型 PDF、本地 HTML 和 DOCX 基础文本抽取；PDF 使用 PDFBox 页级文本，HTML 只解析本地上传内容且不访问外部网络，DOCX 使用 Apache POI 抽取段落 / 标题 / 表格文本。
- Parser 运行边界由 `APP_DOCUMENT_PARSER_MAX_FILE_SIZE_BYTES` 和 `APP_DOCUMENT_PARSER_TIMEOUT_MS` 控制；当前不做 OCR、扫描件识别、复杂版面还原、外部网页抓取或 `.doc` 旧格式。
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
3. 云模式和命令行默认都读取 `backend/.env`；`.run/*` 可通过 `SPRING_CONFIG_IMPORT` 覆盖或追加导入顺序。

## 6. 最小联调顺序

默认云中间件路径：

1. 准备 `backend/.env`
2. 启动 MySQL / Qdrant tunnel：`powershell -ExecutionPolicy Bypass -File scripts/dev/start-cloud-tunnels.ps1`
3. 确认本地 `13306` / `6333` 端口监听成功
4. 启动 backend
5. 访问 `http://localhost:8081/actuator/health`
6. 启动 frontend
7. 访问 `/login`，走“注册 -> 上传 -> 解析 -> 问答”主链路

纯本地 demo 路径：

1. 根目录启动中间件：`docker compose -f ../docker-compose.demo.yml up -d`
2. 使用 `.env.demo.example` 启动 backend
3. 启动 frontend
4. 访问 `/login`

## 7. 常见问题

1. `Port 8081 was already in use`：先执行 `scripts/demo/preflight-backend-port.ps1`。
2. SSE 跨域预检异常：确认前端 `NEXT_PUBLIC_BACKEND_BASE_URL` 与后端地址一致。
3. 云模式不可用：检查 `.env`、Redis 鉴权、RocketMQ NameServer 连通性。
4. MySQL / Qdrant 不可达：先确认 `scripts/dev/start-cloud-tunnels.ps1` 已启动，且本地 `13306` / `6333` 端口正在监听；不要再用公网 `MYSQL_HOST=<CLOUD_HOST>` + `MYSQL_PORT=13306` 直连 MySQL。
5. 香港云中间件不可达：检查 `<CLOUD_HOST>` 的 Redis / RocketMQ / MinIO 连通性；MySQL / Qdrant 优先检查 tunnel，必要时临时切到 `docker-compose.demo.yml`。
6. 全量测试意外出现 `.env`、scheduled outbox、RocketMQ、Redisson、MySQL tunnel 或 Qdrant 连接日志：先确认是否回归了默认测试隔离。普通 `mvn test -DskipITs` 不应依赖真实中间件；若要证明云链路可用，按第 6 节先启动 tunnel 再做 runtime smoke。
