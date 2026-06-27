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

## 2.1 自驱迭代模式约束

1. 只有用户明确授权“连续做直到完成 / 自驱迭代推进 / 每完成一部分自审并提交”等意图时，才进入自驱迭代模式。
2. 自驱迭代模式允许协作代理在一个大目标内自行拆片、实现、验证、回写文档、精确暂存和提交；普通任务仍不得自动 commit。
3. 每个切片必须是小闭环：先说明本片目标和验证方式，再改动、验证、自审、回写事实源、提交。
4. 自动提交只允许使用精确路径；禁止 `git add .`，禁止 push，禁止提交 `.env`、artifact 原文、日志、截图、Playwright 临时目录或任何真实凭据。
5. 自驱模式默认采用真实链路优先验证：mock / unit test 是快速回归门禁，RAG、Memory、Conversation Trace、权限隔离和前端关键路径的质量结论优先以真实 smoke / runtime evidence 为准。
6. 自驱模式允许在当前大目标内自行启动本地 tunnel / backend / frontend，运行真实 smoke，创建带统一 marker 的临时 smoke 数据，使用本机已有 `.env` 中的真实 provider / Qdrant / MySQL 配置，并生成 ignored 脱敏 artifact；这些操作不再逐次等待用户确认。
7. 下列情况必须停止并汇报，不能继续硬做：需要用户产品取舍、数据库结构变更、删除业务数据、清空 collection、远程 Docker 启停 / 重启 / 迁移、改防火墙或云资源、大规模或高成本真实 provider 调用、无法脱敏的证据、连续验证失败、工作区出现影响当前切片的无关改动。
8. 自驱模式不改变状态口径：没有真实链路验证不能把用户体验质量写 `DONE`；验证不完整写 `REVIEW`；环境 / 权限 / 配置缺失写 `BLOCKED`。

## 2.2 真实链路优先验证权限

1. 默认允许：本地 SSH tunnel、后端、前端、Playwright、cloud / RAG smoke runner、临时 smoke 用户 / 文档 / KnowledgeBase / Conversation、ignored 脱敏 artifact、小规模真实 provider 调用。
2. 受控允许：通过 `hk-ops` 或等价路径做只读远程诊断，包括容器状态、健康检查、日志摘要、端口、网络、非敏感计数；执行前说明目的和命令类别，输出必须脱敏。
3. 仍需单独授权：远程 Docker 启动 / 停止 / 重启、数据库结构变更、业务数据删除、collection 清空、云资源或防火墙修改、大规模付费 eval、push。
4. 敏感值永远不能输出或提交：`.env`、token、API key、账号密码、云地址、连接串、文档全文、prompt、evidence context。真实配置只能由本地应用、脚本或环境变量读取。

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
4. `application.yml` 默认导入 `backend/.env`；`application-local.yml` 只保留 local profile 覆盖。若不希望连云，必须显式使用 `.env.demo.example` / `.env` 或 `.run/*-Local` 的 `SPRING_CONFIG_IMPORT` 覆盖。
5. 根目录 `.run/*-HK-Cloud` 是当前默认开发运行配置；`*-Local` 主要用于纯本地 demo。
6. 所有真实密钥、云地址、账号密码只能出现在本机 `.env` 类文件中，不能写入可提交文档、示例配置或运行配置。
7. 若修改端口、环境变量名、云中间件地址或 compose 服务名，必须同步更新：
   - 根 README
   - backend/README
   - frontend/README
   - `.run/` 配置
   - smoke / check 脚本

## 6. Gemini CLI 协作规则

1. Gemini CLI 是协作增强，不是关键路径；失败时由 Codex 继续负责落地、验证和回写。
2. 启动前只做可用性检查，不打印密钥或环境变量值：
   - `Get-Command gemini.cmd`
   - `gemini.cmd --version`
3. 如当前 shell 未继承 Gemini 环境变量，只允许从系统环境变量注入到当前子进程：
   - `$env:GOOGLE_GEMINI_BASE_URL = [Environment]::GetEnvironmentVariable('GOOGLE_GEMINI_BASE_URL','User')`
   - `$env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY','User')`
   - 禁止输出、复制或写入这些变量的真实值。
4. 可用性探测使用短 prompt 和显式模型，只确认 READY 类返回：
   - `gemini.cmd -m gemini-2.5-pro --prompt "Reply exactly: READY"`
   - `gemini.cmd -m gemini-2.5-flash --prompt "Reply exactly: READY"`
5. 长 prompt、源码和大上下文通过 stdin 传入 Gemini，不塞进命令行参数，避免 PowerShell 命令行长度限制。
6. 推荐协作模式：
   - Gemini 输出设计方向、patch 建议或单文件完整方案。
   - Codex 审查是否破坏现有 API、路由、类型、安全边界和展示口径。
   - 通过审查后由 Codex 使用 `apply_patch` 落地。
7. `--approval-mode auto_edit` 只允许在明确文件范围内尝试；如果出现 503、`INVALID_ARGUMENT`、malformed tool call、空响应或工具流异常，立即降级为“Gemini 提建议，Codex 集成”。
8. 安全边界：
   - 不把 `.env`、密钥、云 IP、连接串、远程命令、数据库凭据传给 Gemini。
   - 不让 Gemini 执行 `git commit` / `git push`。
   - 不让 Gemini 操作远程服务器、云服务器 Docker 中间件或数据库迁移。
   - 不让 Gemini 修改与当前任务无关的文件。
9. 验证归属固定为 Codex：最终 lint、build、Playwright、乱码扫描、进程清理和 ai-dev 文档回写都由 Codex 执行；Gemini 输出不能直接写成已验证结果。

## 7. 验证与收尾约束

1. 文档或配置变更后，至少确认对应启动路径仍可解释且命令未失效。
2. 若本轮启动过本地服务、Playwright 或 MCP 相关进程，最终回复前必须执行：

```powershell
powershell -File scripts/dev/cleanup-agent-processes.ps1
```

3. 汇报中必须说明：
   - 是否做了启动验证
   - 是否执行了进程清理
   - 还剩哪些真实边界或风险
