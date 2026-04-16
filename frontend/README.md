# DocPilot Frontend

DocPilot 前端基于 Next.js App Router，覆盖完整演示链路：
`首页 -> 登录 -> 控制台 -> 上传 -> 文档列表 -> 文档详情 -> 问答`。

## 本地启动

```bash
cd frontend
cp .env.example .env.local   # Windows 可手动复制
npm install
npm run dev
```

默认端口：`3000`

## 环境变量

`.env.example` 关键配置：

```bash
BACKEND_BASE_URL=http://localhost:8081
NEXT_PUBLIC_BACKEND_BASE_URL=http://localhost:8081
```

说明：
- 非 SSE 请求统一通过 `/backend/*` 代理到后端
- SSE 优先使用 `NEXT_PUBLIC_BACKEND_BASE_URL` 直连

## 登录页

- 路由：`/login`
- 默认模式：注册
- 可切换模式：登录
- 认证主接口：
  - `POST /api/auth/register`
  - `POST /api/auth/password/login`

## 运行校验

```bash
npm run lint
npm run build
```

## token 约定

- localStorage key: `docpilot_token`
- 请求头：`Authorization: Bearer <token>`
