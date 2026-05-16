# Agent Selector Actuator Security Plan

本文档记录 `agentSelectorShadow` Actuator endpoint 的安全开启策略。T025 只做设计，不真正开启 endpoint，不修改配置文件，不新增 Spring Security 配置，也不接 Prometheus。

## 一、背景

T024 已经实现 `AgentSelectorShadowEndpoint`，endpoint id 为 `agentSelectorShadow`，候选访问路径为 `/actuator/agentSelectorShadow`。

当前实现使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`，因此 endpoint 默认关闭。T024 没有修改 `application.yml` 或 `application-local.yml`，也没有加入 `management.endpoints.web.exposure.include`，所以当前默认不会暴露该 endpoint。

T025 的目标是设计未来如何安全开启该 endpoint，而不是在本轮真正开启它。当前仍不新增普通 REST API，不改前端，不接 Prometheus，不真实调用 provider，也不改变 production routing。

## 二、开启目标

未来开启该 endpoint 的目的，是给开发者或运维人员查看 Agent selector shadow metrics，辅助判断 shadow selector 的稳定性和 promotion candidate 风险。

该 endpoint 必须保持只读观测性质，只输出聚合指标和安全枚举字段，例如 total / success / failure / matched / mismatch、matchRate / failureRate、provider 聚合、decision pair 聚合和 threshold decision。

该 endpoint 禁止输出 prompt、用户 task、文档内容、模型完整返回、provider raw response、API Key、Authorization、baseUrl、用户 ID、文档 ID、sessionId 或最终回答。

该 endpoint 不能影响 production routing，不能让 shadow decision 接管 primary decision，也不能替代现有 `/api/ai/agent/run` 行为。它也不替代 Prometheus；Prometheus 数值指标仍应作为后续独立设计任务处理。

## 三、默认策略

默认策略必须保持关闭优先：

- 默认关闭。
- local / dev 可以在明确需要调试时显式开启。
- test 可以通过测试类 properties 显式开启，用于验证安全字段和访问行为。
- prod 默认关闭。
- prod 如需开启，必须先经过人工确认和安全审查。
- 不允许默认公网暴露。
- 不允许匿名访问。

任何开启动作都必须保留 endpoint 只读、字段白名单、黑名单字段不输出、production routing 不变的边界。

## 四、推荐开启方式设计

未来如需开启，应通过明确 profile、环境变量或运维侧配置完成，而不是提交默认开启配置。候选环境变量设计如下：

```powershell
MANAGEMENT_ENDPOINT_AGENT_SELECTOR_SHADOW_ENABLED=true
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,agentSelectorShadow
```

约束：

- 不能使用 `management.endpoints.web.exposure.include=*` 暴露所有 endpoints。
- 不能把 `agentSelectorShadow` 默认写进 `application.yml`。
- 不能把 `agentSelectorShadow` 默认写进 `application-local.yml`。
- 只能在明确 profile、环境变量或运维配置中开启。
- 开启前必须确认访问来源、鉴权边界、字段白名单、审计策略和回滚方式。
- 开启后仍不能真实调用 provider，也不能改变 selector primary routing。

## 五、分环境策略

### local 策略

local 环境可以手动开启，用于开发者临时查看 selector shadow metrics dump。开启时应仅允许本机访问，不允许提交真实开启配置，不允许把开启状态固化到默认配置文件。

local 推荐使用临时环境变量开启，并在调试结束后清理当前终端或运行配置中的临时值。该模式适合排查 metrics 聚合、threshold decision 和 endpoint 序列化字段，不适合作为演示公网入口。

### dev 策略

dev 环境可以在内网开发环境中显式开启，但必须限制访问来源。开启前应明确网段、网关、反向代理或基础鉴权方案。

dev 不允许通过公网域名直接暴露 `/actuator/agentSelectorShadow`，也不允许绕过鉴权把 endpoint 暴露给普通业务用户。若 dev 环境有统一网关，应由网关限制访问来源，并只允许开发 / 运维角色访问。

### test 策略

test 环境只在测试类 properties 中显式开启。测试目标应限制为验证 endpoint 可访问、返回字段属于白名单、黑名单字段不出现、默认关闭行为仍有效。

test 不真实调用 provider，不依赖真实中间件，不读取 `backend/.env`，不要求真实 API Key，也不输出 prompt、用户 task、文档内容或模型完整返回。

### prod 策略

prod 默认关闭，当前阶段不建议开启。

如果未来必须在 prod 开启，需要同时满足：

- 内网访问。
- Spring Security 鉴权。
- 运维角色授权。
- IP allowlist。
- 反向代理限制。
- 访问审计。
- 响应字段白名单。
- 禁止返回 raw sample。

prod 开启前必须先经过 CC / 人工安全审查，确认访问链路、鉴权行为、审计策略、回滚方案和字段脱敏边界。

## 六、禁止策略

以下策略明确禁止：

- 使用 `management.endpoints.web.exposure.include=*`。
- 公网匿名访问 actuator。
- 普通用户访问 `agentSelectorShadow`。
- 前端页面直接调用该 endpoint。
- 在日志中打印完整响应。
- 输出 provider raw error。
- 输出 prompt、document content 或 model response。
- 输出 API Key、Authorization、baseUrl、用户 ID、文档 ID、sessionId 或最终回答。
- 将 shadow decision 接管 primary decision。
