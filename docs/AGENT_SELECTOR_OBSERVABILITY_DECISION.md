# Agent Selector Observability Decision

本文档记录 Agent selector shadow metrics 的观测入口设计决策。它是设计文档，不代表已经新增 HTTP API、Actuator endpoint、Prometheus 指标或管理端页面。

## 背景

当前 Agent selector 升级路线已经完成以下基础设施：

1. T019 已完成真实 provider shadow-only 验证：`openai_compatible` provider 在用户授权下真实 HTTP 调用 2 次，summary / QA 的 primary decision 与 shadow decision 均一致，production routing 未改变。
2. T020 已完成 selector shadow metrics 与 threshold policy：可以统计 match / mismatch / failure，并用阈值判断是否达到 promotion candidate。
3. T021 已完成内部 debug dump：可以在 Java 内部把 metrics snapshot、provider 聚合、decision 聚合和 threshold decision 格式化为安全 view。

现在需要决定如何让开发者或运维观察这些 metrics，同时避免把内部 provider / decision 信息误暴露到公网或用户侧。

## 当前已有能力

- `SelectorMetricsCollector`：内存态记录 selector shadow compare 结果。
- `SelectorMetricsSnapshot`：提供 total / success / failure / matched / mismatch、matchRate、failureRate、provider 聚合、primaryDecision / shadowDecision 聚合。
- `SelectorShadowThresholdPolicy`：根据 metrics 判断是否达到候选阈值。
- `SelectorShadowThresholdDecision`：输出 `allowPromotionCandidate`、reason 和阈值参数。
- `SelectorMetricsDebugSnapshot`：将 snapshot / threshold decision 格式化为安全 view。
- `SelectorMetricsDebugReporter`：只读组合 collector 与 threshold policy，生成内部 debug dump。

## 当前边界

- 不接管 production routing。
- production routing 仍由 `DocumentToolSelector` 决定。
- 不新增 API。
- 不新增 Actuator endpoint。
- 不接 Prometheus。
- 不落库。
- 不暴露 secret。
- 不输出 prompt、用户 task、文档内容或模型完整返回。
- metrics 当前是内存态，只适合进程内观察和测试验证。

## 候选方案

### A. 继续使用本地 Debug Dump

适用场景：

- 本地开发。
- 单元测试 / 离线 evaluation。
- 调试 selector shadow metrics 和 threshold policy。

优点：

- 安全边界最小，不需要网络暴露。
- 不需要新增 API、Controller 或 Actuator endpoint。
- 不需要额外鉴权设计。
- 已有 T021 基础设施可直接复用。

缺点：

- 不适合线上运维实时查看。
- 不支持跨进程或历史趋势。
- 需要开发者通过测试或内部对象主动调用。

风险：

- 如果后续把 dump 文本直接写入日志，仍需遵守脱敏白名单。

### B. Actuator Endpoint

适用场景：

- 内部运维观测。
- 本地或内网环境的只读调试。
- 与 Spring Boot 运维体系保持一致。

优点：

- 比自定义业务 API 更接近运维语义。
- 可结合现有 Actuator 暴露策略、网络边界和认证机制。
- 适合只读健康 / metrics snapshot 类数据。

缺点：

- 仍然需要明确是否默认关闭、哪些 profile 可开启、如何鉴权。
- 如果错误配置导致公网暴露，风险较高。
- 需要后续单独实现和验证。

风险：

- 未鉴权 Actuator 可能泄露 provider / decision 分布。
- endpoint 返回字段必须严格白名单。

### C. 管理端 API

适用场景：

- 未来有明确管理员角色和后台页面时。
- 需要结合用户 / 租户 / 权限体系进行治理时。

优点：

- 可以与产品级管理界面集成。
- 可以做更细粒度鉴权、审计和展示控制。

缺点：

- 当前管理端权限体系不明确。
- 容易被误用为普通业务 API。
- 需要前后端、鉴权、脱敏和审计一起设计，改动面较大。

风险：

- 若暴露到公网或普通用户，可能泄露内部 provider 运行状态。
- 如果维度设计过细，可能反推出业务行为。

### D. Prometheus Metrics

适用场景：

- 中期线上趋势观察。
- 告警、看板和长期质量门禁。
- 与现有 Micrometer / Prometheus 体系融合。

优点：

- 适合数值指标、趋势和告警。
- 可观察 matchRate / failureRate 的长期变化。
- 运维体系成熟。

缺点：

- 需要仔细控制 label cardinality。
- 不适合暴露 threshold reason 这类文本。
- 需要设计指标命名、标签、采样和告警阈值。

风险：

- label 维度过高会导致 Prometheus 压力。
- label 不应包含 prompt、task、文档内容、错误原文或用户信息。

## 安全约束

无论选择哪种方案，都必须满足：

- 默认关闭或仅限本地 / 内网。
- 必须有明确鉴权或访问边界。
- 不暴露 API Key。
- 不暴露 provider baseUrl。
- 不暴露 Authorization header。
- 不暴露 prompt。
- 不暴露用户 task。
- 不暴露文档内容。
- 不暴露模型完整返回。
- 不暴露 provider raw error。
- 只输出聚合数据和安全枚举值。

## 安全威胁模型

### 可能泄露的信息

允许被谨慎观察的内部信息：

- provider 名称，例如 `disabled` / `fake` / `openai_compatible`。
- primaryDecision / shadowDecision 分布。
- failureRate。
- mismatchRate 或由 matched / mismatch 推导的差异率。
- provider 调用延迟的聚合值。
- 归类后的 errorType。
- threshold 的 `promotionCandidate` 和 reason。

不应该泄露的信息：

- prompt。
- 用户 task。
- 文档内容。
- 模型完整返回原文。
- API Key。
- provider baseUrl。
- Authorization header。
- provider raw error。
- 用户私有内容。

### 攻击面

- 公网暴露 API：如果把 metrics 暴露成普通业务 API，可能被非管理员访问。
- 未鉴权 Actuator：Actuator endpoint 若默认开启或公网可达，会泄露内部 selector 运行状态。
- 日志误输出：debug dump 或 provider error 如果直接写入日志，可能把内部信息扩散到日志系统。
- metrics label 维度过高：将 task、documentId、userId、errorMessage 等放入 label 会造成隐私和稳定性风险。
- provider error 原样透出：上游错误可能包含请求路径、模型名、额度信息或诊断文本。
- 用户文档内容进入观测数据：任何 raw sample 都可能包含用户私有内容。

### 防护策略

- 默认关闭网络观测入口。
- 仅允许本地或内网访问。
- 所有网络入口必须鉴权。
- 需要时增加 IP allowlist。
- 只输出聚合数据。
- 不输出 raw sample。
- 不输出 prompt。
- 不输出 model response。
- errorType 归类，不输出 provider raw error。
- metrics label 只使用低基数枚举值。
- Actuator / API 返回字段必须使用白名单。

### 最小安全字段白名单

- `totalCount`
- `successCount`
- `failureCount`
- `matchedCount`
- `mismatchCount`
- `matchRate`
- `failureRate`
- provider 枚举值
- primaryDecision / shadowDecision 枚举值
- `promotionCandidate`
- threshold reason

### 禁止字段黑名单

- API Key
- baseUrl
- Authorization
- prompt
- task
- document content
- model raw response
- real IP
- user private content

## 推荐路线

短期：

- 继续使用 T021 的本地 debug dump。
- 不新增 HTTP API。
- 不新增 Actuator endpoint。
- 不接 Prometheus。

下一步：

- T023 优先做 Actuator endpoint 设计草案，而不是马上实现。
- 设计草案必须明确默认关闭、profile 条件、鉴权、内网边界、字段白名单和测试策略。

中期：

- 在 Actuator 方案边界清楚后，再考虑 Prometheus 数值指标。
- Prometheus 只暴露数值指标，不暴露 prompt / task / 文档内容 / 模型输出。

暂缓：

- 管理端 API 暂缓，等后台权限体系、管理员角色和审计策略更明确后再设计。

## 面试表达

可以这样说明：

- 没有直接把 selector metrics 暴露成 API，是因为 metrics 虽然不含原文，但仍包含 provider 和 decision 分布，属于内部观测信息。
- 先做 shadow，再做 metrics，再做 threshold，再设计观测入口，是为了让 LLM selector 的演进有安全过渡和可量化基线。
- `promotionCandidate` 只是候选判断，不会自动改变 production routing，体现了“可观察先于接管”的工程策略。
- 短期保留 debug dump，避免未设计鉴权时误暴露；中期再考虑 Actuator / Prometheus，是更稳健的生产化路径。

## 决策矩阵

| 维度 | 本地 debug dump | Actuator endpoint | 管理端 API | Prometheus metrics |
| --- | --- | --- | --- | --- |
| 实现成本 | 低，T021 已具备 Java 内部对象 | 中，需要 endpoint、配置和测试 | 高，需要权限、审计和前端/后台协作 | 中，需要 Micrometer 指标、label 设计和部署验证 |
| 安全风险 | 低，不网络暴露 | 中，取决于 Actuator 暴露范围和鉴权 | 高，可能成为公网业务接口 | 中，风险集中在 label 和 scrape 暴露范围 |
| 是否适合本地开发 | 是 | 是 | 一般 | 一般 |
| 是否适合线上运维 | 否 | 是，适合只读诊断 | 是，但依赖管理端权限体系 | 是，适合趋势和告警 |
| 是否需要鉴权 | 不需要网络鉴权 | 需要，至少内网 / profile / 认证控制 | 必须需要 | 需要依赖 Prometheus 网络与访问控制 |
| 是否需要网络暴露 | 否 | 需要内部端点 | 需要 API 路由 | 需要 scrape endpoint |
| 是否适合面试展示 | 是，展示工程边界和测试证据 | 是，展示运维设计 | 一般，容易引出权限体系问题 | 是，展示生产化观测意识 |
| 是否适合生产环境 | 只适合辅助调试 | 可作为短期生产只读观测候选 | 暂不适合当前阶段 | 中期适合，需控制 label 和告警 |
| 是否支持趋势观察 | 否 | 有限，只看当前进程状态 | 有限，除非额外落库 | 是 |
| 是否支持告警 | 否 | 有限，可人工检查 | 有限，需额外告警系统 | 是 |

## 推荐结论

1. 当前阶段不直接新增 API。
2. 当前阶段不直接接 Prometheus。
3. T023 推荐做 Actuator endpoint 设计，而不是马上实现。
4. 如果后续实现 Actuator endpoint，必须默认关闭或仅在 local / internal profile 开启。
5. 如果后续接 Prometheus，只暴露数值指标和安全枚举 label，不暴露 prompt、task、文档内容或模型输出。

当前推荐路径是：

1. 保留 T021 本地 debug dump 作为短期观测方式。
2. T023 已完成 Actuator endpoint 设计草案，明确开关、鉴权、字段白名单和测试策略。
3. T024 已完成默认关闭的 Actuator endpoint 最小实现，使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`，不修改配置文件，也不加入 exposure include。
4. T025 已新增 Actuator endpoint 安全开启策略设计文档；该任务只做设计，不真正开启 endpoint。
5. 当前仍未修改 `application.yml` / `application-local.yml`，未加入 exposure include，未接 Spring Security，未接 Prometheus，也未开启 dev / prod 访问。
6. T027 已完成测试内显式开启验证：新增 `AgentSelectorShadowEndpointEnabledTest`，只在测试 properties 中开启 `agentSelectorShadow`，验证 200、字段白名单 / 黑名单、空 metrics 和 metrics 不变；未修改配置文件、生产代码、前端、Spring Security 或 Prometheus。
7. T027 配置命名已确认：开启开关使用单数 `management.endpoint.agent-selector-shadow.enabled=true`；web 暴露使用复数 `management.endpoints.web.exposure.include=agentSelectorShadow`，值使用 endpoint id，不能写成 `agent-selector-shadow`，也不能使用 `*`。
8. 默认状态仍关闭，生产环境仍未开启，完整 T010 仍为 BLOCKED。
9. Prometheus 放到中期，等 Actuator 和指标口径稳定后再接。
10. 管理端 API 暂缓，等后台权限体系和审计策略更清楚后再评估。
