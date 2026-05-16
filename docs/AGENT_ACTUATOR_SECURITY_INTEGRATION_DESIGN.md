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

## 四、鉴权边界和访问角色

### 推荐角色

未来如果接入 Spring Security，建议为 Actuator 观测入口区分独立角色：

- `ROLE_ACTUATOR_ADMIN`：权限最高，可访问管理类 actuator endpoint。
- `ROLE_OPS`：可访问只读观测 endpoint，包括候选的 `agentSelectorShadow`。
- `ROLE_DEVELOPER_DEBUG`：仅 local / dev 可用，不应用于 prod。

普通业务用户不允许访问 `/actuator/agentSelectorShadow`。该 endpoint 不应复用普通文档问答、Agent run 或 dashboard 页面权限，也不应对终端用户开放。

### 推荐路径规则

路径规则建议保持分层处理：

- `/actuator/health` 可以按现有策略处理。
- `/actuator/info` 可以按现有策略处理。
- `/actuator/agentSelectorShadow` 未来必须单独保护。
- 不建议把所有 `/actuator/**` 一刀切公网暴露。
- 不建议把 `agentSelectorShadow` 混入普通业务 API 权限体系。

如果未来管理端还需要其他 Actuator endpoint，应逐个评估暴露必要性、访问角色和响应字段，不应因为开启一个只读观测 endpoint 而扩大整个 Actuator 面。

### 未授权访问行为

未来推荐的未授权访问行为如下：

- 未登录：返回 401。
- 已登录但无访问角色：返回 403。
- endpoint 未开启：返回 404。
- prod 默认关闭时应保持 404。
- 错误响应不能泄露 endpoint 内部结构、metrics 内容或 provider 信息。

当 endpoint 未暴露或未启用时，404 是期望行为；这可以降低外部探测者判断内部观测入口存在性的概率。开启后的 401 / 403 行为应由安全测试固定下来，避免不同环境出现意外匿名访问。

### 访问来源限制

prod 如必须开启，应叠加内网、VPN、IP allowlist 或网关限制。仅依赖 Spring Security 不是充分条件，反向代理层也应限制来源。

任何环境都不允许公网匿名访问 `/actuator/agentSelectorShadow`。dev 环境也应限制来源，只允许开发者 / 运维在受控网络内访问。
