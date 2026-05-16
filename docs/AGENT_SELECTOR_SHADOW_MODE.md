# Agent Selector Shadow Mode

本文记录 DocPilot 当前 Agent selector shadow mode 的真实状态。它是 P3 LLM Tool Selection 的安全过渡基础设施，不代表 LLM 已经接管生产路由。

## 1. 当前 Selector 架构

- Primary selector：`DocumentToolSelector`。
- Shadow selector：`FakeLlmToolSelector`。
- Tool metadata：`ToolDefinitionProvider` 为 `document_status_tool`、`document_summary_tool`、`document_qa_tool` 提供稳定工具定义。
- Prompt builder：`LlmToolSelectionPromptBuilder` 只构建未来 LLM Tool Selection prompt，不调用模型。
- Parser：`LlmToolSelectionParser` 只解析未来 LLM 输出 JSON，不调用模型。
- 默认没有真实 LLM 调用；T019 已在用户明确授权下完成一次真实 provider shadow-only 验证。当前没有 function calling，也没有让 shadow decision 接管生产 routing。

## 2. Real LLM Selector Adapter 当前状态

T014 已补齐真实 LLM selector 的禁用态适配层，但它仍然不参与生产 routing：

- `LlmToolSelectionClient` 是未来调用模型的 client 抽象。
- `DisabledLlmToolSelectionClient` 是当前默认安全实现：不联网、不调用真实模型、不读取环境变量或 `backend/.env`，只返回 disabled response。
- `RealLlmToolSelector` 已能串联 `LlmToolSelectionPromptBuilder`、`LlmToolSelectionClient` 和 `LlmToolSelectionParser`。
- `RealLlmToolSelector` 当前不是生产 Spring bean，不直接接管任何真实工具选择。
- `RealLlmSelectorShadowRunner` 已接入 `DocumentAgentServiceImpl` 的 real shadow 分支，但默认 `realShadowEnabled=false`，因此默认不运行。
- 即使开启 real shadow，由于当前 client 是 `DisabledLlmToolSelectionClient`，也不会真实调用模型；失败按 fail-open 处理，不影响 primary routing、真实工具执行或 API 返回。

这些类的目标是先固定 prompt、client、parser、runner 的边界，防止后续接真实 provider 时把失败静默 fallback 成 keyword selector，或误让 LLM selector 接管生产。

T016 / T017 已新增 provider-specific client skeleton 和 factory-backed real shadow 路径：

- Provider settings：`llmProvider=disabled|fake|openai_compatible`，默认 `disabled`；`llmModel` / `llmBaseUrl` 默认空，`llmRequestTimeoutMs=3000`。
- `FakeLlmToolSelectionClient`：不联网，不读取密钥，只用于测试和未来 shadow-only smoke；T017x 已增强其本地规则，使其更贴近 `DocumentToolSelector` 当前 eval routing。
- `OpenAiCompatibleLlmToolSelectionClient`：已支持 OpenAI-compatible `/chat/completions`；缺少 apiKey / baseUrl / model 时返回 disabled，不发 HTTP 请求；配置齐全且 real shadow 被显式开启时可用于 shadow-only 调用。
- `LlmToolSelectionClientFactory`：可按 provider 返回 disabled / fake / openai-compatible client。
- `RealLlmToolSelectorFactory`：把 `AgentSelectorProperties`、client factory、prompt builder 和 parser 串起来创建 `RealLlmToolSelector`。
- `RealLlmSelectorShadowRunner` 支持 factory-backed selector；`DocumentAgentServiceImpl` 的 real shadow 分支已使用该路径，但默认 `realShadowEnabled=false` 且默认 provider 为 `disabled`。
- provider=fake 已完成离线 shadow evaluation 和 runtime 验证；provider=openai_compatible 已完成一次真实 provider shadow-only runtime 验证，但默认仍不启用。

## 3. Shadow Mode 的目的

Shadow mode 的目标是安全比较未来 LLM selector 与当前 keyword selector 的选择结果：

- 不改变生产决策。
- 不改变真实执行工具。
- 记录 primary decision 与 shadow decision 的 match / mismatch。
- 为后续真实 LLM selector 做离线评估、灰度观察和阈值治理。

当前 selector 决策顺序：

1. Primary：`DocumentToolSelector`，唯一决定真实执行工具。
2. Fake shadow：可选，用于 compare / metrics。
3. Real shadow：可选，默认关闭，用于未来真实 LLM selector shadow。
4. 真实执行：永远使用 primary decision。

Shadow decision 只用于 compare、日志和内存态 metrics，不会写入 API 返回。

## 4. Feature Flag

当前配置入口：

```yaml
app:
  agent:
    selector:
      mode: keyword
      shadow-enabled: false
      llm-provider: disabled
```

- `app.agent.selector.mode=keyword|shadow_llm`。
- `app.agent.selector.shadow-enabled=false` 默认关闭。
- `app.agent.selector.real-shadow-enabled=false` 默认关闭。
- `app.agent.selector.real-shadow-record-metrics=false` 默认关闭。
- `app.agent.selector.real-shadow-fail-open=true` 默认 fail-open。
- `app.agent.selector.llm-provider=disabled|fake|openai_compatible`，默认 `disabled`。
- `app.agent.selector.llm-model` 默认空。
- `app.agent.selector.llm-base-url` 默认空。
- `app.agent.selector.llm-request-timeout-ms=3000`。
- 即使配置为 `shadow_llm`，当前真实执行仍以 primary decision 为准。
- 不要把 `shadow_llm` 理解成 LLM 接管生产 routing。

## 5. Metrics

`SelectorMetricsCollector` 当前记录：

- `totalCount`
- `successCount`
- `failureCount`
- `matchedCount`
- `mismatchCount`
- `matchRate`
- `failureRate`
- `lastUpdatedTime`
- provider 维度聚合，例如 `disabled` / `fake` / `openai_compatible`
- primaryDecision / shadowDecision 的安全 decision pair 聚合

当前边界：

- 仅内存态。
- 未落库。
- 未接 Micrometer。
- 未接 Prometheus。
- 未新增对外 API。
- fake shadow metrics 已可记录。
- real shadow metrics 默认不记录；只有 `realShadowRecordMetrics=true` 且 real shadow success 时才允许记录。
- metrics 不记录 prompt、用户原始 task、文档内容、模型完整返回、API Key、完整 baseUrl 或 Authorization header。

## 6. Threshold Policy 与安全日志

`SelectorShadowThresholdPolicy` 当前只用于评估，不用于自动接管 routing：

- 默认 `minimumSamples=20`。
- 默认 `minMatchRate=0.95`。
- 默认 `maxFailureRate=0.05`。
- 输出 `allowPromotionCandidate` 和可读 `reason`。
- `allowPromotionCandidate=true` 只表示“候选”，不修改配置，不改变 production routing。

当前 runtime 安全日志已可观察：

- fake shadow compare：`primaryDecision`、`shadowDecision`、`matched`。
- real shadow compare：`provider`、`primaryDecision`、`shadowDecision`、`matched`、`metricsRecorded`。
- real shadow skip / failure：`provider`、`primaryDecision` 和脱敏错误摘要。

T021 已新增内部只读 debug dump / reporter：

- `SelectorMetricsDebugSnapshot` 只格式化安全字段：total / success / failure / matched / mismatch、matchRate、failureRate、lastUpdatedTime、provider 聚合、decision pair 聚合和 threshold decision。
- `SelectorMetricsDebugReporter` 只读组合 `SelectorMetricsCollector` 与 `SelectorShadowThresholdPolicy`，不会清空 metrics，也不会改变 runtime 状态。
- 当前没有新增 HTTP API，没有新增 Actuator endpoint，没有接 Prometheus，没有落库。
- 暂不开放 API / Actuator 的原因是 metrics 可能包含 provider 与 decision 运行信息，管理端鉴权、内网边界和脱敏策略尚未单独设计。
- T022 已补充观测入口设计决策：短期继续本地 debug dump；下一步优先做 Actuator endpoint 设计草案；中期再考虑 Prometheus；管理端 API 暂缓。
- 后续观测入口可选四种路线：A. 本地 CLI / debug dump；B. Actuator endpoint，仅限内网和认证；C. 管理端 API，需要鉴权和脱敏；D. Prometheus metrics，仅暴露数值指标。
- T023 推荐继续写 Actuator endpoint 设计草案，不直接实现接口。

禁止日志输出：

- API Key、Authorization header、完整 baseUrl。
- prompt 全文、用户原始 task、文档内容。
- 模型完整返回原文。
- 真实 IP、token、password 或 `backend/.env` 内容。

## 7. 当前已验证内容

- 后端 244 tests 通过。
- `ToolSelectorEvaluationTest` 已用 24 条离线样例验证当前 keyword selector 基线。
- `ShadowToolSelectorEvaluationTest` 已验证 primary `DocumentToolSelector` 与 fake shadow selector 的离线对比：24 cases，23 matched，1 mismatch，matchRate=0.9583。
- `RealShadowProviderEvaluationTest` 已验证 primary `DocumentToolSelector` 与 `RealLlmSelectorShadowRunner + provider=fake` 的离线对比：total=24，success=22，failures=2，matched=22，mismatch=0，matchRate=0.9167。两个 failure 来自 blank task 被 prompt builder 拒绝，非空样例均成功且无 mismatch。
- `DocumentAgentServiceImplTest` 已验证 shadow compare metrics 只在 shadow compare 成功执行时记录，且不影响真实 decision 和工具执行。
- `DocumentAgentRealShadowPathTest` 已验证 real shadow 默认关闭、fake shadow 不隐式启用 real shadow、disabled / exception fail-open、parseReady=false 跳过和 real metrics 开关边界。
- `DisabledLlmToolSelectionClientTest`、`RealLlmToolSelectorTest` 和 `RealLlmSelectorShadowRunnerTest` 已验证 disabled client、adapter 串联、fake JSON 解析、disabled 失败和 runner 成功 / 失败边界。
- `FakeLlmToolSelectionClientTest` 已验证 fake provider client 输出可被 parser 解析，并覆盖 status / summary / evidence、summary + evidence 冲突、中文关键词、英文大小写和空白 fallback。
- `OpenAiCompatibleLlmToolSelectionClientTest` 已验证 OpenAI-compatible skeleton 只构造 request 并返回 disabled response，不联网。
- `LlmToolSelectionClientFactoryTest` 已验证默认返回 disabled、fake 返回 fake、openai-compatible 返回 dry-run skeleton，unknown provider fallback disabled。
- T018 fake provider shadow-only runtime 已通过：本地后端连接用户授权的远程中间件，使用命令行参数开启 `shadowEnabled=true`、`realShadowEnabled=true`、`realShadowRecordMetrics=true`、`llmProvider=fake`，基于已解析文档 `documentId=61` 完成 summary / QA 浏览器验证。
- T018 runtime 验证中，summary primary decision 仍为 `summary_tool`，QA primary decision 仍为 `qa_tool`；后端安全日志可见 `provider=fake` 的 real shadow compare，shadow decision 与 primary matched，且只用于 shadow compare / metrics。
- T018 runtime 验证未修改 API、前端、production routing 或配置文件；未真实调用 LLM，未读取 API Key / `backend/.env`，未向模型 provider 发真实 HTTP；本轮未使用 hk-ops，未执行远程 DB 只读 SELECT。
- T019-preflight 已新增 `docs/REAL_PROVIDER_SHADOW_PREFLIGHT.md`，明确真实 provider shadow-only 前的配置原则、日志脱敏原则、HTTP 调用边界、验证方案、停止条件和用户确认项；该文档不代表真实 provider 已启用。
- T019-real-shadow-only 已在用户授权下完成：provider=`openai_compatible`，真实 HTTP 调用 2 次，基于 `documentId=61` 验证 summary / QA；summary primary / shadow 均为 `summary_tool`，QA primary / shadow 均为 `qa_tool`，shadow parse success=true，mismatch=false，QA citations 正常。
- T019 回归验证已通过：后端 `mvn -DskipTests compile`、`mvn test -DskipITs`，前端 `npm run lint`、`npm run build` 均通过；协作代理未读取或输出 API Key，未输出完整 baseUrl、prompt、文档内容或模型完整返回。
- T020 已完成 selector shadow metrics 与 threshold policy：metrics 支持 total / success / failure / matched / mismatch、matchRate、failureRate、provider 聚合和 decision pair 聚合；threshold policy 默认 `minimumSamples=20`、`minMatchRate=0.95`、`maxFailureRate=0.05`，只输出 `allowPromotionCandidate` 和 reason，不接管 routing。
- T020 测试已验证 threshold policy 与 offline eval 组合；即使 `allowPromotionCandidate=true`，`DocumentAgentServiceImpl` 的真实响应仍由 primary `DocumentToolSelector` decision 决定。
- T021a-c 已完成内部只读 debug dump / reporter 和离线 evaluation dump 测试；未新增 API / Actuator / Prometheus / 落库。
- T022 已创建 `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`，完成本地 debug dump、Actuator endpoint、管理端 API、Prometheus metrics 四种观测入口的方案对比和安全威胁模型；T022 只做设计文档，未实现接口。
- T023 已完成 Actuator endpoint 设计草案。
- T024 已新增默认关闭的 `AgentSelectorShadowEndpoint`：endpoint 使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`，只读返回 `SelectorMetricsDebugSnapshot`，默认未暴露，未修改 `application.yml` / `application-local.yml`，未加入 exposure include，未接 Prometheus，未新增普通 REST API。
- T025 已新增 `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`，只设计安全开启策略；endpoint 当前仍默认关闭，尚未修改配置，尚未接 Spring Security，尚未接 Prometheus，尚未开启 dev / prod 访问。
- T027 已新增 `AgentSelectorShadowEndpointEnabledTest`，只在测试 properties 中显式开启 endpoint；`GET /actuator/agentSelectorShadow` 返回 200，字段白名单 / 黑名单、空 metrics 和 metrics 不变检查均通过。T027 未修改生产代码、配置文件、前端、Spring Security 或 Prometheus，未真实调用 provider，未读取或输出 secret。
- T028 已完成 local / dev 显式开启方案草案和后续路线拆分；该任务只写文档，未真正开启 endpoint。
- T029 已新增 `docs/AGENT_ACTUATOR_SECURITY_INTEGRATION_DESIGN.md`，完成 Spring Security / Actuator 安全集成设计；该任务只写文档，没有实现 Spring Security，没有新增 `SecurityFilterChain`，没有修改配置，也没有真正开启 endpoint。
- T030 当前仍为 BLOCKED，原因是项目缺少 Spring Security Web 鉴权体系；不建议现在为了 T030 直接引入 Spring Security 依赖。
- T031 已完成 local / dev 临时开启文档示例；未修改配置文件，未真正开启 endpoint。
- T032 已新增 `docs/AGENT_SELECTOR_PROMETHEUS_METRICS_DESIGN.md`；只设计 Prometheus 数值指标和安全 label，未接 Prometheus。
- T010-lite-run 已通过，浏览器端已验证 `/agent` 页面展示 `routingReason`、`matchedKeywords`、持久化 trace 和 citations。
- 完整 T010 仍为 BLOCKED，原因是 MQ disabled / `NoopParseTaskMessageProducer` 导致上传解析链路不推进；该 blocker 与 selector shadow mode 无关。

## 8. 不能硬吹的边界

当前边界：

- 已完成一次真实 provider shadow-only HTTP 验证，但默认配置仍为 disabled，后续再次运行真实 provider 必须重新获得用户确认。
- 协作代理未读取 API Key；应用只通过用户环境变量在运行时使用。
- 未读取 `backend/.env`。
- 未输出 API Key、完整 baseUrl、Authorization header、prompt、文档内容或模型完整返回。
- 未接 function calling。
- 未让 LLM 接管生产 routing。
- 未让 Real LLM selector 接管生产 service 的真实决策。
- 未接 LangChain4j / Spring AI。
- 未接 MCP。
- 未实现完整向量 RAG。
- 未对外暴露 metrics API。
- 已新增默认关闭的 Actuator endpoint；未加入 exposure include，默认不可访问，未接 Prometheus。
- 已有 Actuator endpoint 安全开启策略设计和 Spring Security / Actuator 安全集成设计；但尚未执行开启，尚未加入 exposure include，尚未新增 Spring Security 配置，尚未开启 dev / prod 访问。
- 已完成测试内显式开启验证；该验证只使用测试 properties，不代表 local / dev / prod 已开启。
- 未接 Prometheus；T032 只是 Prometheus metrics 设计文档。
- 未将 selector metrics 落库。
- 未验证完整上传 -> 解析 -> Agent run 链路。

当前能力应表述为：已建立 selector shadow mode 基础设施，可在不改变生产 decision 的前提下记录 primary / shadow 对比结果。

## 9. 后续路线

建议后续拆小推进：

1. T033：Prometheus metrics 设计审查，或先回到 T030-design-review 重新收窄鉴权验证方案。
2. 后续如果实现开启策略，必须默认关闭、字段白名单、字段黑名单、鉴权边界和内网限制同时成立。
3. 后续再次真实 provider shadow-only：必须由用户重新确认 API Key 注入、费用、provider、日志脱敏策略和调用次数上限，仍不接管生产。
4. 后续达到稳定阈值后再考虑小流量接管。
5. 完整 T010 需要等待可用 MQ / `ParseTaskMessageConsumer` 环境后再验证。
