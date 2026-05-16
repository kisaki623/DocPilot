# Agent Actuator Security Integration Design

本文档记录 `agentSelectorShadow` Actuator endpoint 的 Spring Security / Actuator 安全集成设计。T029 只做设计，不实现 Spring Security，不修改配置文件，不真正开启 endpoint。

## 一、背景

T024 已实现默认关闭的 `AgentSelectorShadowEndpoint`，endpoint id 为 `agentSelectorShadow`，候选访问路径为 `/actuator/agentSelectorShadow`。

T025 / T028 已设计 local / dev / test / prod 的显式开启策略和 dev / local enablement proposal。T027 已验证在测试 properties 中显式开启后，`GET /actuator/agentSelectorShadow` 可以返回 200，并完成白名单字段、黑名单字段、空 metrics 和 metrics 不变检查。

当前 endpoint 仍默认关闭，仓库没有修改 `application.yml` 或 `application-local.yml`，也没有真正开启 `management.endpoints.web.exposure.include`。当前尚没有针对该 endpoint 的 Spring Security 保护方案。

T029 的目标是设计未来安全集成边界，不在本轮实现安全配置，不改变 Actuator 暴露状态，不影响 Agent selector 的 production routing。

## 二、安全目标

`/actuator/agentSelectorShadow` 未来如需开启，必须满足以下安全目标：

- endpoint 只允许开发者、运维或管理角色访问。
- 普通业务用户不能访问。
- 前端页面不能直接调用。
- 公网匿名不能访问。
- 不允许使用 `management.endpoints.web.exposure.include=*`。
- 不允许默认在 prod 开启。
- 响应只允许返回聚合 metrics 和安全枚举字段。
- 不返回 prompt、用户 task、documentContent 或 modelRawResponse。
- 不返回 provider baseUrl、API Key 或 Authorization。
- 不改变 production routing。
- 不触发真实 provider。
- 不影响正常业务 API。

安全集成必须保持该 endpoint 的只读观测定位：它只能帮助开发者 / 运维查看 selector shadow metrics，不能成为业务 API，也不能让 shadow decision 接管 primary decision。

## 三、非目标

T029 明确不做以下事项：

- 不实现 Spring Security。
- 不新增 `SecurityFilterChain`。
- 不修改 `application.yml`。
- 不修改 `application-local.yml`。
- 不接 Prometheus。
- 不新增普通 REST API。
- 不做生产开启。
- 不做权限系统改造。

后续如需进入实现，应先完成安全审查和测试内鉴权策略验证，再决定是否允许修改配置示例或新增安全配置。
