# DocPilot Frontend

面向前端开发与联调的运行说明。项目展示与整体架构请看根目录 `README.md`。

当前前端覆盖 dashboard、上传、文档列表、文档详情问答和 Agent 页面。Agent 页面已经支持运行最小工具链，并根据后端返回的 `taskId` 查询和展示持久化 task / step trace。

## 1. 本地启动

```bash
cd frontend
cp .env.example .env.local   # Windows 可手动复制
npm install
npm run dev
```

默认端口：`3000`（被占用时可能自动切到 `3001/3002`）。

启动前提：

1. 后端应先在 `http://localhost:8081` 可用。
2. 若本地端口发生漂移，请同时检查浏览器访问端口与 SSE 直连地址。

## 2. 环境变量

`.env.example`：

```bash
BACKEND_BASE_URL=http://localhost:8081
NEXT_PUBLIC_BACKEND_BASE_URL=
```

说明：

- 非 SSE 请求：通过 `/backend/*` 代理到后端
- SSE 请求：优先使用 `NEXT_PUBLIC_BACKEND_BASE_URL`；为空时本地回退 `http://localhost:8081`

约束：

1. 开发期不要把普通接口和 SSE 都改成浏览器直连，否则容易引入新的跨域口径分裂。
2. 若更改后端端口，必须同步更新：
   - `.env.local`
   - 根目录 `.run/DocPilot-Frontend-Local.run.xml`
   - README 中的访问说明

## 3. 认证页与登录态

- 路由：`/login`
- 默认模式：注册
- 可切换模式：登录
- 主认证接口：
  - `POST /api/auth/register`
  - `POST /api/auth/password/login`

token 约定：

- localStorage key: `docpilot_token`
- 请求头：`Authorization: Bearer <token>`

## 4. 页面主链路

`/` -> `/login` -> `/dashboard` -> `/upload` -> `/documents` -> `/documents/[documentId]` -> `/agent`

详情页支持：

- 普通问答
- SSE 流式问答
- SSE `meta/chunk/done/error` 事件解析
- 首字延迟展示与流式失败自动降级
- 引用片段展示
- 历史问答展示

`/agent` 页面支持：

- 任务输入（模板 + 自定义）
- Agent 决策结果展示（summary/status/qa）
- 工具步骤 trace 与耗时
- 根据 `taskId` 查询持久化 task / step trace
- 展示 task status、decision、totalDurationMs、step count、toolName、durationMs、inputSummary、outputSummary
- task / step 查询失败时保留原始 Agent 回答展示，并显示友好提示
- 引用片段展示

## 5. 质量检查

```bash
npm run lint
npm run build
```

## 6. 联调提示

1. 上传页会自动串联 `file/upload -> document/create -> task/parse/create`。
2. SSE 异常时前端会降级普通问答。
3. 问答会话默认按文档维度落在 localStorage：`docpilot:qa:session:d:{documentId}`。
4. 若出现中文乱码，优先检查源码文件编码、Git 提交编码与 IDE 文件编码设置是否一致。
