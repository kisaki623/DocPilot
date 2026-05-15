# Real Provider Shadow Preflight

本文记录 T019-real-shadow-only 前置检查与安全方案。当前阶段只允许制定方案和检查清单，不代表真实 provider 已启用。

## 1. T019 目标

T019 只允许做真实 provider 的 shadow-only 调用。

- 不允许接管 production routing。
- 真实执行工具仍由 `DocumentToolSelector` 决定。
- real provider 只用于生成 `shadowDecision`。
- real provider 失败必须 fail-open，不影响 primary 主流程。
- 不允许影响 Agent API 返回协议。
- 不允许把 shadow decision 写成最终生产 decision。

当前代码边界：

- `OpenAiCompatibleLlmToolSelectionClient` 当前仍是 disabled / dry-run skeleton，调用 `completeSelectionPrompt` 不发 HTTP。
- `LlmToolSelectionClientFactory` 默认 provider 为 `disabled`，默认返回 `DisabledLlmToolSelectionClient`。
- `RealLlmToolSelector` 只负责 prompt -> client -> parser 串联，client disabled 或返回空文本时会失败，不会 fallback 为 keyword selector。
- `RealLlmSelectorShadowRunner` 捕获 selector 失败并返回 `success=false` / `shouldRecordMetrics=false`。
- `DocumentAgentServiceImpl` 的真实执行仍以 primary `DocumentToolSelector` decision 为准。

## 2. 配置原则

- API Key 不写入代码。
- API Key 不写入 `application.yml`。
- API Key 不写入 README。
- API Key 不写入日志。
- API Key 不提交 Git。
- 后续真实运行时只允许通过临时环境变量或用户本地安全配置注入。
- Codex 不允许读取 `backend/.env`。
- Codex 不允许输出真实 API Key。
- `app.agent.selector.real-shadow-enabled` 默认必须保持 `false`。
- `app.agent.selector.llm-provider` 默认必须保持 `disabled`。
- 真实 provider 配置不得改变默认 Agent 行为。

## 3. 日志脱敏原则

禁止日志输出：

- API Key。
- Authorization header。
- prompt 全文。
- 用户原始 task。
- 文档内容。
- 模型完整返回原文。
- token。
- password。
- 真实 IP。
- `backend/.env` 内容。

允许日志输出：

- provider。
- model。
- success。
- disabled。
- matched。
- primaryDecision。
- shadowDecision。
- latencyMs。
- errorType。
- errorCode。
- 脱敏后的错误摘要。

错误摘要必须避免包含请求头、密钥、完整 prompt、文档片段或模型完整原文。

## 4. 真实 HTTP 调用边界

后续 T019-real-shadow-only 如果实现真实 HTTP 调用，必须遵守：

- 必须设置 timeout。
- 必须限制 `maxTokens`。
- 必须使用 `temperature=0` 或低温。
- 必须只请求 tool selection JSON。
- 必须通过 `LlmToolSelectionParser` 校验。
- parser 失败时 shadow 失败。
- shadow 失败不影响 primary。
- 不允许 fallback 成生产 decision。
- 不允许重试过多。
- 不允许无限等待。
- 不允许把 provider 原始响应全文写入业务日志。
- 不允许新增 Agent API 字段暴露 shadow 原始响应。

建议真实 HTTP 调用只返回以下 JSON 协议：

```json
{
  "decision": "summary_tool",
  "toolNames": ["document_summary_tool"],
  "routingReason": "short safe reason",
  "matchedKeywords": ["summary"],
  "confidence": 0.8
}
```

## 5. 验证方案

后续 T019-real-shadow-only 必须验证：

- provider shadow-only summary。
- provider shadow-only QA。
- primary decision 仍正确。
- shadow decision 可解析。
- 失败 fail-open。
- 后端 compile/test 通过。
- 前端 lint/build 通过。
- `git status --short` 干净。
- 不泄露 API Key。
- 不改变 API。
- 不改变前端。
- 不改变 production routing。
- 不修改 `application.yml` / `application-local.yml`。
- 不读取 `backend/.env`。

建议 runtime 验证仍优先使用已解析文档 `documentId=61`，并明确它只验证已解析文档上的 Agent runtime，不验证上传 / 解析 / MQ 链路。

## 6. 停止条件

后续真实 provider 任务必须在以下情况停止：

- 需要读取 `backend/.env`。
- 需要用户粘贴 API Key 到聊天。
- 需要提交 API Key。
- 需要修改 `application.yml`。
- 需要新增 API。
- 需要改前端。
- 需要改数据库。
- 真实 provider 返回不可控内容。
- parser 无法稳定解析。
- 日志可能泄露敏感信息。
- 真实调用失败超过 3 次。
- 费用或额度不明确。
- 需要把 shadow decision 作为 production decision。

## 7. 用户确认项

进入 T019-real-shadow-only 前需要用户确认：

- 使用哪个 provider：DeepSeek / OpenAI-compatible / 硅基流动 / 其他。
- baseUrl 是否由用户本地临时环境变量提供。
- model 名称。
- API Key 注入方式。
- 是否允许真实 HTTP。
- 是否允许产生少量费用。
- 是否只验证 `documentId=61`。
- 是否允许本地后端连接远程中间件。
- 是否允许记录脱敏后的 provider / model / latency / decision / errorCode。

未完成上述确认前，真实 provider shadow-only 任务保持 BLOCKED。
