# Agent Selector Actuator Endpoint Design

本文档是 Agent selector shadow metrics 的 Actuator endpoint 设计草案。当前不实现 endpoint，不新增 Controller，不修改 Java 生产代码，不修改配置文件。

## 背景

1. T019 已验证真实 provider shadow-only：`openai_compatible` provider 在用户授权下完成 summary / QA 两次真实 shadow 调用，primary / shadow decision 均一致，production routing 未改变。
2. T020 已完成 shadow metrics + threshold policy：支持 total / success / failure / matched / mismatch、matchRate、failureRate、provider 聚合、decision pair 聚合，并通过阈值计算 `promotionCandidate`。
3. T021 已完成内部 debug dump：`SelectorMetricsDebugSnapshot` / `SelectorMetricsDebugReporter` 可以在 Java 内部安全查看 metrics 与 threshold decision。
4. T022 已完成观测入口设计决策：短期不直接开放业务 API，下一步先做 Actuator endpoint 设计草案。
5. 当前仍不实现 endpoint；本文件只描述未来 T024 可能实现时的安全边界、字段白名单和测试策略。

## 设计目标

Actuator endpoint 的目标是提供一个内部只读观测入口：

1. 给开发者和运维查看 selector shadow metrics。
2. 只读，不修改任何状态。
3. 只输出聚合指标。
4. 不输出 prompt、用户 task、文档内容或模型返回。
5. 不影响 production routing。
6. 不让 shadow decision 接管 primary。
7. 不替代 Prometheus，只作为从本地 debug dump 走向正式监控之间的中间阶段运维观测入口。

## 非目标

本设计明确不做：

1. 不做管理端 API。
2. 不做前端页面。
3. 不做 Prometheus export。
4. 不做 metrics 落库。
5. 不做按用户 / 文档维度查询。
6. 不暴露 raw sample。
7. 不暴露模型原始响应。
8. 不暴露 provider baseUrl / API Key。
9. 不提供修改阈值接口。
10. 不提供切换 production routing 接口。

## 推荐 Endpoint 形态

候选 Actuator endpoint：

- id: `agentSelectorShadow`
- path: `/actuator/agentSelectorShadow`
- method: `GET`
- readOnly: `true`

说明：

- 这只是设计草案，当前未实现。
- 如果未来实现，endpoint 应只依赖 `SelectorMetricsDebugReporter`。
- T024 如实现，必须显式使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`。
- endpoint 不应调用真实 provider，不应触发工具选择，不应访问数据库。
- endpoint 不应返回任何用户维度、文档维度或请求样本维度数据。

## 返回字段白名单

只允许包含：

1. `totalCount`
2. `successCount`
3. `failureCount`
4. `matchedCount`
5. `mismatchCount`
6. `matchRate`
7. `failureRate`
8. `lastUpdatedTime`
9. provider aggregation
10. primaryDecision / shadowDecision aggregation
11. `promotionCandidate`
12. threshold reason
13. `minimumSamples`
14. `minMatchRate`
15. `maxFailureRate`

provider aggregation 只能包含 provider 枚举名和聚合计数 / rate。decision aggregation 只能包含 selector decision 枚举值和聚合计数。

## 禁止字段黑名单

禁止包含：

1. API Key
2. baseUrl
3. Authorization
4. prompt
5. user task
6. document content
7. model raw response
8. real IP
9. userId
10. documentId
11. sessionId
12. task_input
13. final_answer 全文
14. provider raw error

## 默认开关策略

设计建议：

1. 默认关闭。
2. 仅 local / dev profile 开启。
3. 生产环境默认不暴露。
4. 如果生产要开，必须依赖 management endpoint 暴露白名单。
5. 必须配合 Spring Security / 内网 / 反向代理访问控制。
6. 不允许默认公网暴露。

未来如果实现，不能通过默认 `application.yml` 把 endpoint 暴露出去；应由 profile、环境变量或部署侧配置显式开启。

T024 实现边界补充：

- 这是项目中第一个自定义 Actuator endpoint，没有既有项目内模式可复用，因此必须最小实现。
- T024 不修改 `application.yml`。
- T024 不修改 `application-local.yml`。
- T024 不加入 `management.endpoints.web.exposure.include`。
- T024 只做默认关闭 endpoint、单元测试和 context 默认 404 测试。
- T024 暂不测试“未授权访问被拒绝”，因为当前项目还没有专门的 Actuator Spring Security 配置；该项留到 T025 安全体系任务。
- T024 不接 Prometheus。
- 项目现有 Prometheus endpoint 和 selector-specific Prometheus metrics 是两回事；T024 不修改现有 Prometheus 配置。

## 安全与鉴权设计

### 访问控制

建议策略：

1. local / dev 默认可以显式开启，便于本地排障。
2. prod 默认关闭。
3. prod 如需开启，必须同时满足：
   - 仅内网访问；
   - Spring Security 鉴权；
   - 运维角色权限；
   - IP allowlist 或网关限制；
   - 不走公网域名。
4. 不允许匿名访问。
5. 不允许前端普通用户访问。

该 endpoint 面向内部运维与开发者，不是面向业务用户的功能。

### Actuator 暴露策略

设计说明：

1. `management.endpoints.web.exposure.include` 不应该默认包含该 endpoint。
2. 如果引入，应通过 profile 或环境变量显式开启。
3. 不能为了方便把所有 Actuator endpoints 暴露出去。
4. 本轮不修改 `application.yml`；这里只记录未来实现时的设计要求。
5. 如未来新增 endpoint，应在部署文档中说明暴露范围和访问边界。

### 脱敏策略

返回内容必须满足：

1. 只输出聚合指标。
2. provider 只能输出枚举名。
3. error 只能输出分类，不输出 provider raw error。
4. reason 只能是阈值判断原因，不能包含 task / prompt。
5. 不输出 sample。
6. 不输出文档相关字段。
7. 不输出用户相关字段。
8. 不输出请求上下文。

### 审计策略

如果未来实现 endpoint，应记录访问日志：

1. 记录访问时间、访问身份和 endpoint id。
2. 不记录响应 body。
3. 不记录用户文档内容。
4. 不记录 prompt / task / 模型响应。
5. 访问异常可以记录 warning，但不能输出 secret。

### 风险

需要重点防范：

1. 误暴露 Actuator 的风险。
2. metrics label 维度过高的风险。
3. provider 错误信息泄露风险。
4. 被用于推断系统行为的风险。
5. 与 Prometheus / 管理端 API 的边界混淆风险。
6. 为了调试而临时扩大 Actuator 暴露范围后忘记回收的风险。

## 未来实现草案

本节只描述未来 T024 可能的实现方式，不代表当前已经写代码。

### 候选实现类

未来如实现，可以新增：

1. `AgentSelectorShadowEndpoint`
2. 复用 `SelectorMetricsDebugReporter`
3. 复用 `SelectorMetricsDebugSnapshot`

候选注解形态可参考 Spring Boot Actuator 自定义 endpoint 方式，但 T023 不新增任何 Java 类。

### 依赖关系

设计依赖方向：

1. endpoint 只依赖 `SelectorMetricsDebugReporter`。
2. reporter 只读 `SelectorMetricsCollector`。
3. reporter 只读 `SelectorShadowThresholdPolicy`。
4. endpoint 不依赖 `DocumentAgentServiceImpl`。
5. endpoint 不调用真实 provider。
6. endpoint 不触发工具选择。
7. endpoint 不访问数据库。
8. endpoint 不读取环境变量。
9. endpoint 不读取 `backend/.env`。

### 测试策略

未来 T024 如实现，必须测试：

1. endpoint 默认不开启。
2. endpoint 开启后返回白名单字段。
3. endpoint 不包含黑名单字段。
4. endpoint 不触发真实 provider。
5. endpoint 不改变 metrics 计数。
6. endpoint 不改变 production routing。
7. 空 metrics 返回安全默认值。
8. 有 mismatch 时返回聚合统计。
9. 有 failure 时返回聚合统计。
10. threshold decision 正确返回。
11. 未授权访问被拒绝，如项目已有安全体系可测。
12. Actuator exposure 未配置时不可访问。

建议测试层次：

- 单元测试：直接调用 endpoint 对象，验证字段白名单和只读行为。
- Spring context 测试：验证默认配置下 endpoint 不暴露。
- 安全测试：若接入 Spring Security，验证匿名访问被拒绝。
- 回归测试：验证 `DocumentAgentServiceImpl` primary decision 不受 endpoint 调用影响。

### 验收标准

未来 T024 实现前必须满足：

1. 仍不改前端。
2. 仍不接 Prometheus。
3. 仍不落库。
4. 仍不暴露 raw sample。
5. 仍不输出 secret。
6. 后端全量 test 通过。
7. T010 完整上传解析链路是否通过不影响该 endpoint 设计，但文档里必须继续标记 T010 BLOCKED。
8. endpoint 默认关闭或仅在明确 profile / exposure 配置下可见。
9. 返回字段必须只来自 `SelectorMetricsDebugSnapshot` 安全 view。

## T024 实现结果

T024 已按本设计完成最小实现：

- 新增 `AgentSelectorShadowEndpoint`。
- endpoint 使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`。
- endpoint id 为 `agentSelectorShadow`，候选访问路径为 `/actuator/agentSelectorShadow`，只读 GET 语义由 `@ReadOperation` 提供。
- endpoint 默认关闭。
- endpoint 只依赖 `SelectorMetricsDebugReporter`，返回 `SelectorMetricsDebugSnapshot` 安全 view。
- 已新增直接单元测试，验证空 metrics、字段黑名单和只读行为。
- 已新增 Spring context 测试，验证未显式开启 / 未加入 exposure 时默认返回 404。

T024 明确未做：

- 未修改 `application.yml`。
- 未修改 `application-local.yml`。
- 未加入 `management.endpoints.web.exposure.include`。
- 未新增普通 REST API 或 Controller。
- 未接 Prometheus。
- 未落库。
- 未修改前端。
- 未真实调用 provider。
- 未读取或输出 secret。
- 未改变 production routing，真实工具执行仍由 `DocumentToolSelector` 决定。
- 未做未授权访问测试；当前项目没有专门的 Actuator Spring Security 配置，该项留到 T025。

## 当前状态

- 已新增默认关闭的自定义 Actuator endpoint。
- 未新增 Controller。
- 未修改 `application.yml` 或 `application-local.yml`。
- 未修改前端。
- 未接 Prometheus。
- 未落库。
- production routing 仍由 `DocumentToolSelector` 决定。
- 完整 T010 仍为 BLOCKED，等待 MQ / 解析消费链路。
