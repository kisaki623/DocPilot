# Current Task

当前任务：T010d OpenAI-compatible Function Calling Adapter 已完成；下一步待确认

## 目标

基于 T010a/T010b/T010c 的内部工具系统，新增 OpenAI-compatible Function Calling adapter，让 `ToolSpec` / `ToolCallService` / `ToolCallResult` 能映射到 OpenAI tools、tool_calls 和 tool result message 形态。

## 范围

T010d 已完成：

- 新增 OpenAI tools schema adapter，将内部 `ToolSpec` 转成 `type=function` 的 tools schema；
- 新增 tool_call parser，解析 mock model response 中的 `choices[0].message.tool_calls[]`；
- 新增 tool result adapter，将 `ToolCallResult` 转成 OpenAI-compatible tool message，并对失败消息做脱敏；
- 新增 mock function calling service，串联 tools schema、mock tool_call、`ToolCallService` 和 tool result message；
- 支持多个 tool_calls 按顺序执行；
- 测试只使用 mock model response，不调用真实 OpenAI-compatible provider。

下一步候选：

- T010e：真实 provider adapter 的 disabled-by-default preflight；
- 或前端小范围展示 RAG evidence / citations。

## 禁止事项

- 不改现有 `DocumentAgentServiceImpl` 主流程；
- 不接真实 OpenAI / 硅基流动 / 中转站模型调用；
- 不接 MCP；
- 不做多文档 RAG；
- 不改前端；
- 不改根 README；
- 不读取或提交 `.env` / key / secret；
- 不操作远程服务器；
- 不 push。

## 验收标准

- OpenAI tools schema 包含 function name、description、parameters 和 required fields；
- tool_call parser 能解析 function.name 和 function.arguments JSON string；
- invalid provider JSON / invalid arguments JSON 能安全失败；
- ToolCallResult 能转成 tool message，失败消息不暴露 key、连接串或异常堆栈；
- mock orchestration 能调用 `ToolCallService` 并返回 tool messages；
- 测试不依赖真实 embedding、真实大模型或远程 Qdrant。

## T010d 输出

- OpenAI-compatible Function Calling adapter 内部底座；
- mock tool_call orchestration service；
- adapter / parser / result / orchestration 单元测试；
- 更新 ai-dev 简短进度记录。
