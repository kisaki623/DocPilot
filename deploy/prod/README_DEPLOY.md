# DocPilot 服务器 Docker 部署说明

本目录用于把 DocPilot **后端 Spring Boot 应用**和**前端 Next.js 应用**部署到服务器 Docker 中。当前模板刻意只管理应用容器，默认复用服务器现有的反向代理（Caddy / Nginx）、MySQL、Redis、RocketMQ、MinIO 和 Qdrant。

不要把这里当作“一键生产集群”。它是一个保守的上线骨架：先核实现有数据链路，再让前后端容器接入，避免因为新建空 Qdrant、改 RocketMQ group、替换现有反向代理或重置 volume 导致已有文档、索引和证书链路失效。

## 0. 本模板会做什么、不会做什么

会做：

- 构建 `backend` / `frontend` 两个镜像；
- 后端以 Java 17 非 root 用户运行；
- 前端以 Next.js standalone 方式运行；
- 后端连接现有中间件网络；
- 前端与后端通过应用网络互通；
- 提供现有 Caddy / Nginx 的合并片段。

不会做：

- 不新建或替换反向代理；
- 不新建 MySQL / Redis / RocketMQ / MinIO / Qdrant；
- 不清空或迁移任何 volume；
- 不自动执行数据库迁移；
- 不新建 Qdrant collection；
- 不默认开启 hybrid、multi-query 或 rerank。

## 1. 部署前只读核查

先在服务器只读确认现有事实：

```bash
docker ps
docker network ls
docker volume ls
```

至少记录以下信息，不要把密码、token、连接串贴到公开文档：

| 项目 | 要确认什么 |
| --- | --- |
| 反向代理 | 当前入口是 Caddy 还是 Nginx；是宿主机进程还是 Docker 容器；当前站点配置位置；证书和域名是否已存在 |
| MySQL | 容器名 / DNS 名、schema 名、当前表结构是否包含最新字段、是否已有备份 |
| Redis | 容器名 / DNS 名、database、密码是否存在 |
| RocketMQ | NameServer 地址、Broker 可达地址、topic、producer group、consumer group |
| MinIO | endpoint、bucket、base path、旧对象是否可读 |
| Qdrant | 容器名 / DNS 名、collection、vector size、distance、point count |
| Embedding | 当前模型名和维度，必须与 Qdrant collection 一致 |

当前开发事实曾记录为 `docpilot_rag_v2 / 1024`，但部署时必须以目标服务器实际值为准。

## 2. 准备环境变量

复制模板：

```bash
cp deploy/prod/.env.prod.example deploy/prod/.env.prod
```

编辑 `deploy/prod/.env.prod`，重点填：

- `DOCPILOT_MIDDLEWARE_NETWORK`
- MySQL / Redis / RocketMQ / MinIO / Qdrant 的容器 DNS 名；
- 现有 MySQL schema；
- 现有 RocketMQ topic / group；
- 现有 MinIO bucket / base path；
- 现有 Qdrant collection / dimension；
- 阿里云百炼 chat / embedding / rerank 的 API key。

安全边界：

- `.env.prod` 已被 `.gitignore` 忽略，不能提交；
- `RAG_QDRANT_COLLECTION_INIT_ENABLED=false` 默认不要改；
- `APP_QUALITY_CONSOLE_ENABLED=false` 默认不要改；
- `APP_RAG_RETRIEVAL_HYBRID_ENABLED=false`、`APP_RAG_RETRIEVAL_MULTI_QUERY_ENABLED=false`、`APP_RAG_RERANK_ENABLED=false` 默认不要改，除非你已经在目标环境验证成本、质量和 provider 可用性。

填完后先跑预检，确认没有占位符、错误 collection、维度不一致或误开启 collection init：

```bash
sh deploy/prod/preflight.sh deploy/prod/.env.prod
```

## 3. 准备 Docker 网络

应用网络作为 external network 使用，需要提前创建：

```bash
docker network create docpilot-app || true
```

中间件网络必须是服务器上现有网络。把它填进：

```env
DOCPILOT_MIDDLEWARE_NETWORK=你的现有中间件网络名
```

如果现有中间件不在同一个网络中，优先把 `backend` 连接到它们所在的稳定业务网络；不要为了省事把数据库、Redis、RocketMQ、MinIO 或 Qdrant 端口暴露到公网。

## 4. 接入现有反向代理（三选一）

当前目标域名为 `kisaki0.top`。如果目标服务器已经由宿主机 Nginx 管理 80/443，则优先使用“方案 C：Nginx 是宿主机进程”，不要再额外启动 Caddy 抢占端口。

### 方案 A：Caddy 也是 Docker 容器

把现有 Caddy 容器连接到应用网络：

```bash
docker network connect docpilot-app <existing-caddy-container> || true
```

然后把 [`caddy.container-snippet.caddy`](caddy.container-snippet.caddy) 合并到现有站点 block 中，放在前端 fallback 之前。

语义是：

- `/backend/api/*` 转发到 `backend:8081`，并去掉 `/backend` 前缀；
- `/backend/*` 的非 API 路径直接 404，避免公开 actuator 或未来内部端点；
- 其他路径转发到 `frontend:3000`。

### 方案 B：Caddy 是宿主机进程

启动应用时叠加 loopback override：

```bash
docker compose --env-file deploy/prod/.env.prod \
  -f deploy/prod/docker-compose.prod.yml \
  -f deploy/prod/docker-compose.host-proxy.override.yml \
  up -d --build
```

然后把 [`caddy.host-snippet.caddy`](caddy.host-snippet.caddy) 合并到现有站点 block 中。该方案只把 `127.0.0.1:3000` 和 `127.0.0.1:8081` 暴露给宿主机 Caddy，不应绑定 `0.0.0.0`。

兼容说明：旧文件 [`docker-compose.host-caddy.override.yml`](docker-compose.host-caddy.override.yml) 与 `docker-compose.host-proxy.override.yml` 内容等价，后续推荐使用命名更中性的 host-proxy override。

### 方案 C：Nginx 是宿主机进程

这是 `kisaki0.top` 当前更可能使用的接入方式。启动应用时同样叠加 loopback override：

```bash
docker compose --env-file deploy/prod/.env.prod \
  -f deploy/prod/docker-compose.prod.yml \
  -f deploy/prod/docker-compose.host-proxy.override.yml \
  up -d --build
```

然后把 [`nginx.host-snippet.conf`](nginx.host-snippet.conf) 合并到 `kisaki0.top` 对应的 Nginx `server { ... }` 中，而不是新建一份和现有站点冲突的 server block。合并后先执行：

```bash
nginx -t
```

通过后再 reload Nginx。该片段语义是：

- `/backend/api/*` 去掉 `/backend` 前缀后转发到 `127.0.0.1:8081/api/*`；
- `/backend/*` 的非 API 路径直接 404，避免公开 actuator 或内部端点；
- 其他路径转发到 `127.0.0.1:3000`；
- SSE / 长回答请求关闭 proxy buffering，避免流式输出被反代缓冲。

三种方案不要混用。尤其不要在宿主机 Nginx 已经占用 80/443 时再启动宿主机 Caddy。

## 5. 构建和启动应用容器

容器 Caddy 方案：

```bash
sh deploy/prod/preflight.sh deploy/prod/.env.prod
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml config --quiet
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml build
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml up -d
```

宿主机反向代理方案（Caddy / Nginx）：

```bash
sh deploy/prod/preflight.sh deploy/prod/.env.prod

docker compose --env-file deploy/prod/.env.prod \
  -f deploy/prod/docker-compose.prod.yml \
  -f deploy/prod/docker-compose.host-proxy.override.yml \
  config --quiet

docker compose --env-file deploy/prod/.env.prod \
  -f deploy/prod/docker-compose.prod.yml \
  -f deploy/prod/docker-compose.host-proxy.override.yml \
  up -d --build
```

注意：不要执行 `docker compose down -v`。

## 6. 运行验收

容器状态：

```bash
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml ps
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml logs -f backend
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml logs -f frontend
```

HTTP 验收：

```bash
curl -I https://kisaki0.top/
curl -I https://kisaki0.top/backend/api/quality/status
curl -I https://kisaki0.top/backend/actuator/health
```

预期：

- `/` 返回前端；
- `/backend/api/...` 能到后端；
- `/backend/actuator/health` 对公网为 `404`；
- 浏览器 Network 中 API 请求保持同源 `/backend/api/...`，不出现 CORS 错误；
- 公网只开放 `80/443`，不要开放 `3000/8081/3306/6379/9876/10909-10912/9000/9001/6333/6334`。如果宿主机层面已有中间件端口监听，也必须确认云安全组 / 防火墙没有对公网放行。

业务验收：

1. 既有用户可以登录；
2. 既有文档列表和知识库可以读取；
3. 旧文档仍能被 RAG 检索并返回 citation；
4. 新上传文档可以完成 ParseTask；
5. RocketMQ outbox 不持续失败；
6. MinIO 旧对象可读、新对象可写；
7. Qdrant point count 不异常减少，新文档索引后增加；
8. Conversation 绑定知识库后回答、引用来源、Context Inspector 正常；
9. 日志不出现密钥、Authorization、完整 Prompt、证据全文或连接串。

## 7. 升级和回滚

升级应用：

```bash
git pull
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml build backend frontend
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml up -d backend frontend
```

回滚应用：

```bash
git checkout <previous-commit>
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml build backend frontend
docker compose --env-file deploy/prod/.env.prod -f deploy/prod/docker-compose.prod.yml up -d backend frontend
```

回滚只替换 `backend` / `frontend`。不要删除 MySQL、MinIO、Qdrant、RocketMQ 或现有反向代理的容器、volume、证书和数据。

## 8. 如果服务器没有现成 Qdrant

那就已经超出“只新增前后端应用容器”的范围。你需要先做一个单独的 Qdrant 持久化部署方案，并明确：

- collection 名；
- vector size；
- distance；
- 是否需要迁移或重建旧文档索引；
- 如何备份和恢复；
- 如何确认 MySQL chunk 与 Qdrant point 一致。

不要在本应用 compose 里临时新建一个空 Qdrant 来冒充真实 RAG 可用。
