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

## 七、未来任务拆分

以下任务是未来候选路线，不代表已经完成，也不代表 endpoint 已在 dev / prod 开启。完整 T010 仍为 BLOCKED，原因仍是 MQ disabled / `NoopParseTaskMessageProducer` 导致上传解析链路无法推进。

### T026-design-security-check

目标：对 T025 安全开启策略做 CC / 人工审查，确认是否允许进入实现。

范围：

- 审查默认关闭、字段白名单、黑名单字段、访问边界和审计策略。
- 确认是否允许后续修改配置示例或仅允许测试 properties。
- 明确是否允许改 `application-local.yml`，或者只允许文档 / example 记录。
- 确认仍不允许生产默认开启。

### T027-local-enable-test

状态：已完成。

目标：只在测试内显式开启 endpoint，验证开启后的最小访问行为。

范围：

- 使用测试 properties 显式开启 endpoint。
- 验证开启后返回 200。
- 验证响应只包含白名单字段。
- 验证响应不包含黑名单字段。
- 不修改生产配置。

T027 新增 `AgentSelectorShadowEndpointEnabledTest`，只在测试 properties 中显式开启 endpoint。显式开启后 `GET /actuator/agentSelectorShadow` 返回 200；白名单字段检查、黑名单字段检查、空 metrics 检查和 metrics 不变检查均通过。T027 未修改 `application.yml`、`application-local.yml`、生产代码、前端、Spring Security 或 Prometheus；未真实调用 provider，未读取或输出 secret。默认状态仍关闭，生产环境仍未开启。完整 T010 仍为 BLOCKED。

配置命名注意：

- `management.endpoint.agent-selector-shadow.enabled=true` 使用单数 `endpoint`；`agentSelectorShadow` 对应 relaxed binding 写法 `agent-selector-shadow`。
- `management.endpoints.web.exposure.include=agentSelectorShadow` 使用复数 `endpoints`；`exposure.include` 的值使用 endpoint id `agentSelectorShadow`，不要写成 `agent-selector-shadow`。
- 禁止使用 `management.endpoints.web.exposure.include=*`。

### T028-dev-profile-proposal

目标：设计 dev profile 或运维侧开启方式，但不提交真实开启配置。

范围：

- 设计 dev profile 开启方式。
- 默认不提交真实开启配置。
- 只写 example 或文档。
- 不影响 prod。
- 明确 dev 仍需要内网、鉴权或网关限制。

## 八、T028 local / dev 显式开启方案草案

T028 只提出 local / dev 显式开启方案，不修改 `application.yml`，不修改 `application-local.yml`，不新增 profile 配置文件，也不真正开启 endpoint。

### local 临时开启草案

local 可以使用临时环境变量开启，适合开发者在本机排查 selector shadow metrics dump。以下示例仅表示本地临时环境变量，不是仓库默认配置，也不应提交到任何配置文件：

```powershell
$env:MANAGEMENT_ENDPOINT_AGENT_SELECTOR_SHADOW_ENABLED="true"
$env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE="health,info,agentSelectorShadow"
```

local 使用约束：

- 只允许本机访问。
- 不建议把开启配置提交到 `application.yml`。
- 不建议把开启配置提交到 `application-local.yml`。
- 不允许 `management.endpoints.web.exposure.include=*`。
- 不允许公网匿名访问。
- 不允许前端页面直接调用。
- 不允许普通用户访问。
- 不允许把该 endpoint 当成业务 API。
- 只用于开发者 / 运维只读观测。

### dev 显式开启草案

dev 可以通过部署环境变量或运维侧配置显式开启，但不应提交真实开启配置。dev 开启只适用于内网开发环境的只读观测，不代表 prod 已开启，也不代表 endpoint 可以作为业务 API 使用。

dev 使用约束：

- 只允许开发者 / 运维只读观测。
- 不允许公网匿名访问。
- 不允许普通用户访问。
- 不允许前端页面直接调用。
- 不允许把该 endpoint 当成业务 API。
- 不允许使用 `management.endpoints.web.exposure.include=*`。
- 不改变 production routing。
- 不触发真实 provider。

### dev 开启前置条件

dev 环境开启前必须同时确认：

- 只能在内网 dev 环境开启。
- 必须经过人工确认。
- 必须具备网关、反向代理或基础鉴权中的至少一种访问保护。
- 必须限制访问来源。
- 必须记录谁开启、何时开启、为什么开启。
- 必须确认响应只包含聚合 metrics。
- 必须确认不输出 prompt、用户 task、document content 或 model raw response。
- 必须确认不输出 provider baseUrl、API Key 或 Authorization。
- 必须确认不会触发真实 provider。
- 必须确认不改变 production routing。
- 不允许暴露到公网。
- 不允许对普通用户开放。

如果上述任一条件无法确认，dev 环境不应开启 `agentSelectorShadow` endpoint。

### T029-security-integration

目标：研究 Spring Security / Actuator 安全保护，再决定是否真正开启。

范围：

- 研究 `EndpointRequest`、Actuator endpoint matcher 和专用 `SecurityFilterChain`。
- 明确运维角色。
- 明确匿名 / 未授权访问行为。
- 明确审计日志字段。
- 再决定是否真正开启 endpoint。

### T030-prometheus-design

目标：只做 Prometheus 数值指标设计，不替代 Actuator endpoint。

范围：

- 只暴露数值指标。
- 不输出高维 label。
- 不输出 prompt、用户 task、文档内容或模型完整返回。
- 不输出用户 ID、文档 ID、sessionId 或 provider raw error。
- 不直接替代 actuator endpoint。

T026-T030 均不能暗示线上 SLA，不能让 shadow decision 接管 production routing，也不能把完整 T010 写成通过。
