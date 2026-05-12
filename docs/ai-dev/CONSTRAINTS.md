# 协作约束

## 1. 基础原则

1. 以代码、配置和可执行脚本为准，文档只回写“当前事实”。
2. 优先做闭环交付，不做无关扩展，不把简单问题复杂化。
3. 先修真实问题，再做展示包装；文档不能掩盖实现边界。
4. 文档优先短、准、可执行，避免再次膨胀成流水账。

## 2. 文档与仓库约束

1. 根目录 `README.md` 面向 GitHub 访客、HR、面试官，只保留项目主页所需信息。
2. `backend/README.md` 只写后端启动、配置、联调与排障，不复述项目亮点。
3. `frontend/README.md` 只写前端启动、代理、SSE、登录态与页面联调信息。
4. `docs/ai-dev/STATE.md` 必须保持单一事实源，只维护当前态，不追记历史流水。
5. `docs/ai-dev/HANDOFF.md` 只保留最近 3~5 轮摘要；更早内容统一归档到 `archive/`。
6. `docs/ai-dev/TASKS.md` 只保留仍有价值的滚动待办；已完成事项不在此重复记账。
7. 新增文档前必须先回答两个问题：是否已有文档能承接、是否值得持续维护。

## 3. 后端实现约束

1. 当前主系统定位仍是单体 Spring Boot 工程，不主动拆微服务。
2. 认证主口径固定为账号密码：
   - `POST /api/auth/register`
   - `POST /api/auth/password/login`
   短信验证码接口只保留兼容联调，不再作为 README 主入口。
3. 上传主链路保持“自动串联”：
   - `file/upload -> document/create -> task/parse/create`
   若后续改动破坏该闭环，必须同步更新 README 与 smoke 脚本。
4. 异步解析默认口径保持：
   - `task/parse/create` 只保证 create API 快速返回
   - 解析结果通过 MQ 异步推进到终态
   - 不得把“毫秒级返回”描述为“端到端解析毫秒级完成”
5. AI 主口径固定为：
   - 默认 `AI_MODE=mock`
   - `real` 为可选增强，需显式配置 `AI_REAL_*`
   - “硅基流动”只能表述为 OpenAI 兼容协议接入，不夸大为专属 SDK 深度适配
6. SSE 与普通问答必须共享同一业务语义：
   - 相同鉴权口径
   - 相同文档归属校验
   - 流式异常允许降级普通问答
7. 文档解析边界必须明确：
   - `txt/md` 为当前主覆盖
   - `pdf` 仍是占位能力，不能写成完整 PDF 智能解析
8. benchmark 与指标必须标注边界：
   - 本地环境
   - 相对对比
   - 非线上 SLA

## 4. 前端实现约束

1. 当前前端技术路线固定为 Next.js App Router，不在后续协作中随意切换框架。
2. 登录页默认模式固定为“注册”，并支持切换到“登录”；这是公开演示主入口。
3. 登录态约定保持稳定：
   - token key：`docpilot_token`
   - 问答会话 key：`docpilot:qa:session:d:{documentId}`
4. 非 SSE 请求继续走 `/backend/*` 代理；SSE 优先直连 `NEXT_PUBLIC_BACKEND_BASE_URL`。
5. 前端若回退直连 `http://localhost:8081`，仅适用于本地开发端口 `3000/3001/3002` 场景；对外文档必须写清楚。
6. 上传页默认演示行为必须保持“上传后自动建文档、自动建解析任务”，不要退回手工逐接口调用。
7. 对外演示文案、状态文案、错误提示默认使用简体中文；发现乱码必须优先修复。

## 5. 环境与运行约束

1. 本地 demo 默认基线：
   - backend：`http://localhost:8081`
   - frontend：`http://localhost:3000`
2. 默认开发环境是：
   - 前后端本地运行
   - MySQL / Redis / RocketMQ / MinIO 位于香港云服务器 `<CLOUD_HOST>`
   - 通过 `backend/.env` 或 `.run/*-HK-Cloud` 接入
3. 纯本地 demo 才使用：
   - 根目录 `docker-compose.demo.yml`
   - `backend/.env.demo.example`
   - `frontend/.env.example`
4. `application-local.yml` 的兜底默认指向香港云中间件；若不希望连云，必须显式使用 `.env.demo.example` / `.env` 覆盖。
5. 根目录 `.run/*-HK-Cloud` 是当前默认开发运行配置；`*-Local` 主要用于纯本地 demo。
6. 所有真实密钥、云地址、账号密码只能出现在本机 `.env` 类文件中，不能写入可提交文档、示例配置或运行配置。
7. 若修改端口、环境变量名、云中间件地址或 compose 服务名，必须同步更新：
   - 根 README
   - backend/README
   - frontend/README
   - `.run/` 配置
   - smoke / check 脚本

## 6. 验证与收尾约束

1. 文档或配置变更后，至少确认对应启动路径仍可解释且命令未失效。
2. 若本轮启动过本地服务、Playwright 或 MCP 相关进程，最终回复前必须执行：

```powershell
powershell -File scripts/dev/cleanup-agent-processes.ps1
```

3. 汇报中必须说明：
   - 是否做了启动验证
   - 是否执行了进程清理
   - 还剩哪些真实边界或风险
