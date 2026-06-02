# Agent Selector Prometheus Metrics Design

本文档记录 Agent selector shadow metrics 未来接入 Prometheus 的设计方案。T032 只做设计，不修改代码，不新增依赖，不修改配置，也不真正接入 Prometheus。

## 一、背景

T020 已有 `SelectorMetricsCollector`，当前可记录 selector shadow 的 total / success / failure / matched / mismatch、matchRate、failureRate、provider 聚合和 decision pair 聚合。

T021 已有内部 debug dump / reporter，用于离线查看安全聚合数据。T024 已实现默认关闭的 `agentSelectorShadow` Actuator endpoint。T027 已验证在测试 properties 中显式开启 endpoint 可以返回 200，并通过字段白名单 / 黑名单检查。

T030 因当前项目缺少 Spring Security Web 鉴权体系而 BLOCKED；当前不建议为 T030 直接新增 Spring Security 依赖，也不应在 dev / prod 真正开启 endpoint。

Prometheus 是未来中期方案，目标是观察 selector shadow metrics 的数值趋势和告警信号。本轮只做设计，不接入 Micrometer，不接 Prometheus，不新增配置，不修改 production routing。

## 二、Prometheus 指标候选

未来只允许设计数值指标，候选指标如下：

- `docpilot_agent_selector_shadow_total`
- `docpilot_agent_selector_shadow_success_total`
- `docpilot_agent_selector_shadow_failure_total`
- `docpilot_agent_selector_shadow_matched_total`
- `docpilot_agent_selector_shadow_mismatch_total`
- `docpilot_agent_selector_shadow_match_rate`
- `docpilot_agent_selector_shadow_failure_rate`

这些指标只表达聚合计数或比例，不表达单个用户、单个文档、单次请求、原始输入或模型原文。

## 三、允许 label

只允许低风险、低基数字段作为 label：

- `provider`
- `primary_decision`
- `shadow_decision`
- `result`，值限定为 `matched` / `mismatch` / `failure`

label 值必须来自受控枚举或低基数 provider 名称，不允许接收任意文本输入。

## 四、禁止 label / 禁止字段

以下字段禁止进入 Prometheus 指标名、label、sample、description 或日志：

- `userId`
- `documentId`
- `sessionId`
- `task`
- `prompt`
- document content
- model raw response
- API Key
- `baseUrl`
- Authorization
- provider raw error
- final answer
- citation content

Prometheus metrics 不应携带 raw sample，也不应把 provider 异常原文作为 label 或 value 附加信息。

## 五、风险

Prometheus 适合趋势和告警，不适合调试 raw case。selector shadow 的具体样例、错误原文、输入内容或模型输出都不能进入 metrics。

高维 label 会导致 Prometheus cardinality 爆炸。`userId`、`documentId`、`sessionId` 不能作为 label；prompt、用户 task、document content 绝不能进入 metrics；provider raw error 也不能进入 metrics。

Actuator endpoint 和 Prometheus 目标不同，不互相替代。Actuator 更适合受控只读诊断视图，Prometheus 更适合长期数值趋势、聚合看板和阈值告警。二者都必须遵守字段白名单和访问边界。

## 六、未来任务拆分

以下任务仅为未来路线，不代表已经完成：

- T033：Prometheus metrics 设计审查。
- T034：Micrometer 接入方案评估。
- T035：测试内注册 `MeterRegistry` 验证。
- T036：Prometheus 指标文档和告警阈值设计。

未来实现时仍需遵守：

- 不把 Prometheus 写成已接入，除非已有真实代码和验证。
- 不修改 `pom.xml`，除非任务明确允许依赖变更。
- 不输出高维 label。
- 不输出 prompt、task、文档内容、模型完整返回、provider raw error 或敏感凭据。
- 不改变 production routing。
- 不让 shadow decision 接管 primary decision。
