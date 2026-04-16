# DocPilot Backend

## 运行要求

- Java 17+
- Maven 3.9+

## 本地启动（推荐）

```bash
cd backend
cp .env.demo.example .env   # Windows 可手动复制
mvn spring-boot:run
```

默认端口：`8081`

## 环境变量模板

- `.env.demo.example`：推荐本地演示模板（配合 `docker-compose.demo.yml`）
- `.env.example`：通用模板
- `.env.cloud.example`：云中间件模板

请勿提交真实密钥文件：
- `backend/.env`
- `backend/.env.cloud`

## 认证接口

主入口（账号密码）：
- `POST /api/auth/register`
- `POST /api/auth/password/login`

兼容入口（短信验证码）：
- `POST /api/auth/code`
- `POST /api/auth/login`

## 健康检查

```bash
curl http://localhost:8081/actuator/health
```

## IDEA 运行配置（根目录 `.run/`）

- `DocPilot-Backend-Local`
- `DocPilot-Backend-App-Local`
- `DocPilot-Backend-HK-Cloud`
- `DocPilot-Backend-App-HK-Cloud`

`Local` 配置默认会读取：
- `SPRING_CONFIG_IMPORT=optional:file:./.env.demo.example[.properties],optional:file:./.env[.properties]`

## 演示联调顺序

1. 启动 `docker compose -f ../docker-compose.demo.yml up -d`
2. 启动 backend
3. 启动 frontend
4. 访问 `/login` 走注册/登录流程
