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

## 五、未来 T030 测试策略

T030 的目标应限制为测试内鉴权策略验证，不接真实生产配置，不开放 dev / prod endpoint。

### 默认关闭测试

- endpoint 默认关闭时返回 404。
- 不需要鉴权即可确认默认不暴露。
- 不修改 `application.yml`。

该测试应继续保护 T024 的默认关闭边界，确保新增安全配置设计不会让 endpoint 意外进入默认暴露状态。

### 显式开启但未认证测试

- 只在测试内 properties 显式开启 endpoint。
- 模拟未认证请求访问 `/actuator/agentSelectorShadow`。
- 期望返回 401 或 403，具体取决于 Spring Security 测试配置。
- 不使用真实用户数据。

该测试用于确认 endpoint 即使被测试环境显式暴露，也不会被匿名访问。

### 无权限角色测试

- 模拟普通业务用户。
- 访问 `/actuator/agentSelectorShadow`。
- 期望返回 403。
- 不返回 metrics body。

普通用户访问失败时，错误响应不应包含 selector metrics、threshold decision、provider 信息或内部实现类名。

### 有权限角色测试

- 模拟 `ROLE_OPS` 或 `ROLE_ACTUATOR_ADMIN`。
- 访问 `/actuator/agentSelectorShadow`。
- 期望返回 200。
- 响应仍需检查白名单和黑名单字段。
- 不触发 provider。
- 不访问数据库。
- 不读取 `backend/.env`。

有权限场景只验证安全字段和只读观测，不调用 Agent run，不改变 metrics 计数，不改变 production routing。

### 测试隔离策略

- 测试内 properties 开启 endpoint。
- 测试内 security 配置隔离，不影响生产配置。
- 不依赖真实 MySQL、Redis 或 RocketMQ。
- 不依赖远程中间件。
- 不启动前端。
- 不真实调用 provider。
- 不输出 secret。

如果未来 T030 需要 mock 用户或角色，应在测试上下文内完成，不应接入真实账号体系、真实数据库或真实网关。

### T030 停止条件

T030 如遇到以下条件，应停止并回到设计 / 审查：

- 如果必须修改生产 `application.yml`，停止。
- 如果必须接真实中间件，停止。
- 如果必须读取 `backend/.env`，停止。
- 如果必须输出 secret，停止。
- 如果必须改 production routing，停止。

## 六、dev / prod 开启前人工审查清单

### dev 开启前 checklist

dev 环境开启前应逐项确认：

- 是否确认 endpoint 默认关闭？
- 是否确认只在 dev 环境变量中开启？
- 是否确认 `exposure.include` 不使用 `*`？
- 是否确认访问来源限制？
- 是否确认只有开发者 / 运维可访问？
- 是否确认响应字段通过白名单 / 黑名单测试？
- 是否确认不输出 secret？
- 是否确认不触发 provider？
- 是否确认不影响 production routing？
- 是否确认开启和关闭都有记录？

dev 开启仍应视为临时观测动作，不应把开启配置提交为仓库默认配置，也不应暴露给普通业务用户。

### prod 开启前 checklist

prod 当前阶段不建议开启。如未来必须开启，应先完成以下人工审查：

- 是否真的需要 prod 开启？
- 是否有替代方案，例如本地 debug dump 或 Prometheus 数值指标？
- 是否完成 Spring Security 保护？
- 是否完成网关、VPN 或 IP allowlist？
- 是否完成访问审计？
- 是否完成未授权访问测试？
- 是否完成字段黑名单测试？
- 是否确认不返回 raw sample？
- 是否确认不返回 provider 错误原文？
- 是否经过 CC / 人工安全审查？
- 是否有回滚方案？
- 是否有关闭开关？
- 是否明确负责人？
- 是否明确观察窗口？

prod 开启必须保持最小暴露面，只允许受控网络、受控角色和只读聚合响应。若审查中任一项无法确认，应保持关闭。

### 回滚策略

如需关闭已开启的 endpoint，推荐回滚步骤如下：

- 删除相关环境变量或关闭 endpoint enabled 配置。
- 从 `exposure.include` 移除 `agentSelectorShadow`。
- 重启服务后确认 `/actuator/agentSelectorShadow` 返回 404。
- 保留开启、访问和关闭审计记录。
- 不需要回滚代码，因为 endpoint 默认关闭。
