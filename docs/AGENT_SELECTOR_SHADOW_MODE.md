# Agent Selector Shadow Mode

本文记录 DocPilot 当前 Agent selector shadow mode 的真实状态。它是 P3 LLM Tool Selection 的安全过渡基础设施，不代表 LLM 已经接管生产路由。

## 1. 当前 Selector 架构

- Primary selector：`DocumentToolSelector`。
- Shadow selector：`FakeLlmToolSelector`。
- Tool metadata：`ToolDefinitionProvider` 为 `document_status_tool`、`document_summary_tool`、`document_qa_tool` 提供稳定工具定义。
- Prompt builder：`LlmToolSelectionPromptBuilder` 只构建未来 LLM Tool Selection prompt，不调用模型。
- Parser：`LlmToolSelectionParser` 只解析未来 LLM 输出 JSON，不调用模型。
- 当前没有真实 LLM 调用，没有 function calling，也没有让 shadow decision 接管生产 routing。

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
- `OpenAiCompatibleLlmToolSelectionClient`：只有 request / response 和 dry-run skeleton，`completeSelectionPrompt` 返回 disabled，不发 HTTP 请求。
- `LlmToolSelectionClientFactory`：可按 provider 返回 disabled / fake / openai-compatible client。
- `RealLlmToolSelectorFactory`：把 `AgentSelectorProperties`、client factory、prompt builder 和 parser 串起来创建 `RealLlmToolSelector`。
- `RealLlmSelectorShadowRunner` 支持 factory-backed selector；`DocumentAgentServiceImpl` 的 real shadow 分支已使用该路径，但默认 `realShadowEnabled=false` 且默认 provider 为 `disabled`。
- provider=fake 已完成离线 shadow evaluation；provider=openai_compatible 仍 dry-run disabled，不联网。

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

- `totalComparisons`
- `matchedCount`
- `mismatchCount`
- `matchRate`
- `lastUpdatedTime`

当前边界：

- 仅内存态。
- 未落库。
- 未接 Micrometer。
- 未接 Prometheus。
- 未新增对外 API。
- fake shadow metrics 已可记录。
- real shadow metrics 默认不记录；只有 `realShadowRecordMetrics=true` 且 real shadow success 时才允许记录。

## 6. 当前已验证内容

- 后端 229 tests 通过。
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
- T010-lite-run 已通过，浏览器端已验证 `/agent` 页面展示 `routingReason`、`matchedKeywords`、持久化 trace 和 citations。
- 完整 T010 仍为 BLOCKED，原因是 MQ disabled / `NoopParseTaskMessageProducer` 导致上传解析链路不推进；该 blocker 与 selector shadow mode 无关。

## 7. 不能硬吹的边界

当前没有：

- 真实调用 LLM。
- DeepSeek 真实调用。
- OpenAI 真实调用。
- 硅基流动真实调用。
- API Key 读取。
- `backend/.env` 读取。
- 真实 HTTP 请求。
- function calling。
- LLM 接管生产 routing。
- Real LLM selector 接管生产 service 的真实决策。
- LangChain4j / Spring AI。
- MCP。
- 完整向量 RAG。
- 对外暴露 metrics API。
- 完整上传 -> 解析 -> Agent run 链路验证。

当前能力应表述为：已建立 selector shadow mode 基础设施，可在不改变生产 decision 的前提下记录 primary / shadow 对比结果。

## 8. 后续路线

建议后续拆小推进：

1. T019-real-shadow-only：用户确认 API Key、费用、provider 和日志脱敏策略后，才考虑真实 provider shadow-only 调用，仍不接管生产。
2. T020：记录真实 provider shadow mismatch，补充人工审核 eval。
3. 后续达到稳定阈值后再考虑小流量接管。
4. 完整 T010 需要等待可用 MQ / `ParseTaskMessageConsumer` 环境后再验证。
