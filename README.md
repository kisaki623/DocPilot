# DocPilot

DocPilot 是一个可运行、可演示的 AI 文档解析与问答项目。

主链路：
1. 账号注册/登录
2. 上传文档
3. 创建解析任务（RocketMQ 异步）
4. 查看文档列表/详情
5. 基于文档问答（普通 + SSE 流式）

## 项目结构

```text
DocPilot/
  backend/                 # Spring Boot 后端
  frontend/                # Next.js 前端
  deploy/                  # docker-compose 依赖配置（MySQL/Redis/RocketMQ/MinIO/Prometheus）
  .run/                    # IDEA 运行配置
  docker-compose.demo.yml  # 本地演示依赖编排
```

## 技术栈

- Backend: Java 17, Spring Boot 3, MyBatis-Plus, MySQL, Redis, RocketMQ, MinIO
- Frontend: Next.js 14, TypeScript, Tailwind CSS

## 快速开始（推荐演示路径）

### 1) 启动依赖

```bash
docker compose -f docker-compose.demo.yml up -d
```

### 2) 启动后端

```bash
cd backend
cp .env.demo.example .env   # Windows 可手动复制
mvn spring-boot:run
```

默认端口：`8081`

### 3) 启动前端

```bash
cd frontend
cp .env.example .env.local  # Windows 可手动复制
npm install
npm run dev
```

默认端口：`3000`

### 4) 访问页面

- Home: `http://localhost:3000/`
- Login: `http://localhost:3000/login`
- Dashboard: `http://localhost:3000/dashboard`

## 认证说明

默认认证方案为账号密码：
- `POST /api/auth/register`
- `POST /api/auth/password/login`

兼容保留短信链路（非主入口）：
- `POST /api/auth/code`
- `POST /api/auth/login`

## 开发环境变量

- 不要提交 `backend/.env`、`frontend/.env.local`
- 仓库中仅保留模板：
  - `backend/.env.example`
  - `backend/.env.demo.example`
  - `backend/.env.cloud.example`
  - `frontend/.env.example`

## Docker 演示环境

`docker-compose.demo.yml` 依赖以下文件：
- `deploy/mysql/init/00_init_docpilot.sql`
- `deploy/rocketmq/broker.conf`
- `deploy/prometheus/prometheus.yml`

请确保 `deploy/` 目录与 `docker-compose.demo.yml` 同时保留。

## 已知说明

- Prometheus 默认抓取 `host.docker.internal:8081`，Linux 环境下若不可达，请改成宿主机可访问地址。
- 本仓库为公开演示版本，不包含内部协作文档目录 `docs/`。
