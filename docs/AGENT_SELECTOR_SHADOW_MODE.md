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
- `RealLlmToolSelector` 当前不是生产 Spring bean，未注入 `DocumentAgentServiceImpl`，未接管任何真实工具选择。
- `RealLlmSelectorShadowRunner` 已存在，用于未来把 real selector 接入 shadow compare；当前只在单元测试中验证 disabled / fake client 行为。
- runner 当前未接入 service，未记录 metrics，未改变 API 返回。

这些类的目标是先固定 prompt、client、parser、runner 的边界，防止后续接真实 provider 时把失败静默 fallback 成 keyword selector，或误让 LLM selector 接管生产。

## 3. Shadow Mode 的目的

Shadow mode 的目标是安全比较未来 LLM selector 与当前 keyword selector 的选择结果：

- 不改变生产决策。
- 不改变真实执行工具。
- 记录 primary decision 与 shadow decision 的 match / mismatch。
- 为后续真实 LLM selector 做离线评估、灰度观察和阈值治理。

当前真实执行仍只使用 `DocumentToolSelector` 的 primary decision。Shadow decision 只用于 compare、日志和内存态 metrics。

## 4. Feature Flag

当前配置入口：

```yaml
app:
  agent:
    selector:
      mode: keyword
      shadow-enabled: false
```

- `app.agent.selector.mode=keyword|shadow_llm`。
- `app.agent.selector.shadow-enabled=false` 默认关闭。
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

## 6. 当前已验证内容

- 后端 180 tests 通过。
- `ToolSelectorEvaluationTest` 已用 24 条离线样例验证当前 keyword selector 基线。
- `ShadowToolSelectorEvaluationTest` 已验证 primary `DocumentToolSelector` 与 fake shadow selector 的离线对比：24 cases，23 matched，1 mismatch，matchRate=0.9583。
- `DocumentAgentServiceImplTest` 已验证 shadow compare metrics 只在 shadow compare 成功执行时记录，且不影响真实 decision 和工具执行。
- `DisabledLlmToolSelectionClientTest`、`RealLlmToolSelectorTest` 和 `RealLlmSelectorShadowRunnerTest` 已验证 disabled client、adapter 串联、fake JSON 解析、disabled 失败和 runner 成功 / 失败边界。
- T010-lite-run 已通过，浏览器端已验证 `/agent` 页面展示 `routingReason`、`matchedKeywords`、持久化 trace 和 citations。
- 完整 T010 仍为 BLOCKED，原因是 MQ disabled / `NoopParseTaskMessageProducer` 导致上传解析链路不推进；该 blocker 与 selector shadow mode 无关。

## 7. 不能硬吹的边界

当前没有：

- 真实调用 LLM。
- function calling。
- LLM 接管生产 routing。
- Real LLM selector 接入生产 service。
- LangChain4j / Spring AI。
- MCP。
- 完整向量 RAG。
- 对外暴露 metrics API。
- 完整上传 -> 解析 -> Agent run 链路验证。

当前能力应表述为：已建立 selector shadow mode 基础设施，可在不改变生产 decision 的前提下记录 primary / shadow 对比结果。

## 8. 后续路线

建议后续拆小推进：

1. T015：在 feature flag 严格关闭的前提下，将 `RealLlmSelectorShadowRunner` 接入 service 的 shadow 路径；默认仍不启用，只允许 disabled / fake profile。
2. 真实 provider 调用必须另开任务，且只能先 shadow compare，不接管生产。
3. T016：增加人工审核 eval，记录 task、primary decision、shadow decision、reason 和人工判定。
4. T017：达到稳定阈值后再考虑小流量接管。
5. 完整 T010 需要等待可用 MQ / `ParseTaskMessageConsumer` 环境后再验证。
