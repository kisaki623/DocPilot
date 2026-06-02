# Prompt Engineering Notes

本文记录 DocPilot 当前 Tool Selection / Prompt Engineering 证据链。它只描述模板结构、输出协议、解析和 fallback，不包含真实用户文档正文、真实运行时 prompt、API Key、baseUrl 或 provider 原始返回。

## 1. 目标

DocPilot 的 Agent 工具选择不是让模型自由生成代码或自由调用接口，而是把模型输出限制在“选择一个已注册工具”的小范围内：

```text
task + document state + tool definitions
-> LLM tool selection prompt
-> JSON decision
-> parser validate
-> ToolRegistry allowlist validate
-> server-side tool execution
```

默认生产行为仍是 keyword selector。只有显式配置 `app.agent.selector.mode=llm_execute` 时，LLM selector 的合法 decision 才能进入实际工具执行路径。

## 2. Prompt 模板结构

`LlmToolSelectionPromptBuilder` 的模板只包含选择工具所需的最小结构：

- 当前任务文本。
- 文档状态：`parseReady`、`hasSummary`。
- 已注册工具定义：`toolName`、display name、description、输入 schema 文本、输出 schema 文本、是否允许 LLM 选择。
- 可选 decision 集合：`status_only`、`summary_tool`、`qa_tool`、`rag_tool`。
- 安全约束：只能从 available tools 选择 toolName；不能生成 SQL；不能生成系统命令；不能调用未列出的工具。
- 输出协议：只返回一个 JSON object。

文档正文不会被这份 selector prompt 作为完整内容输出到文档或日志；T059 也不记录真实运行时 prompt。

## 3. 输出协议

LLM selector 预期只返回如下结构的 JSON：

```json
{
  "decision": "summary_tool",
  "toolNames": ["document_summary_tool"],
  "routingReason": "short route reason",
  "matchedKeywords": ["summary"],
  "confidence": 0.8
}
```

字段含义：

- `decision`：工具选择结果，只能是固定枚举。
- `toolNames`：模型认为需要的工具名，必须属于 ToolRegistry allowlist。
- `routingReason`：短理由，用于解释路由，不作为权限或参数来源。
- `matchedKeywords`：辅助展示字段，不作为执行依据。
- `confidence`：0 到 1 的数值，仅供观察，不用于越过 allowlist。

## 4. 解析与校验

`LlmToolSelectionParser` 做离线结构校验：

- 从模型原始文本中提取第一个 JSON object。
- 校验 JSON 必须是 object。
- 校验 `decision` 必须属于固定枚举。
- 校验 `toolNames` 必须是非空字符串数组。
- 校验每个 toolName 都属于 `ToolRegistry#getToolNames()`。
- 校验 `matchedKeywords` 是字符串数组。
- 校验 `confidence` 是 0 到 1 的数值。

`DocumentAgentServiceImpl` 在 execute mode 下还会做服务端执行校验：

- 根据 decision 推导 required toolName。
- 校验 required toolName 已注册。
- 校验模型返回的 `toolNames` 包含 required toolName。
- 校验所有 toolName 都在 ToolRegistry allowlist 内。
- 工具输入仍由服务端使用 `userId / documentId / task / sessionId / content` 构造。

因此模型不能通过输出 JSON 来执行任意代码、传入任意参数或调用未注册工具。

## 5. Fallback 策略

`llm_execute` 是 fail-open 设计。以下情况都会回退 keyword selector：

- provider disabled。
- provider timeout 或 HTTP / client 异常。
- provider 返回空内容。
- 返回内容没有可解析 JSON。
- JSON 字段类型不符合协议。
- decision 不在枚举内。
- toolName 不在 allowlist 内。
- toolNames 缺少当前 decision 所需的 required toolName。

响应会通过 `fallbackUsed`、`fallbackReason`、`toolSelectionSource`、`primaryDecision`、`llmDecision` 和 `finalDecision` 说明最终选择来源。fallback reason 只记录异常类型摘要，不输出 prompt、文档内容、API Key、baseUrl、Authorization 或模型完整返回。

## 6. Shadow 到 Execute 的演进

当前演进路径是渐进式的：

1. Keyword selector：默认路径，可解释、可测试、可回归。
2. Fake shadow selector：旁路比较 primary / shadow decision，不影响真实执行。
3. Real provider shadow-only：真实 provider 只产生 shadow decision，不接管 production routing。
4. `llm_execute`：默认关闭；显式开启后，只有通过 allowlist 和 required tool 校验的 LLM decision 才能成为 final decision。

这个顺序的目的不是追求复杂度，而是控制 AI 决策接管风险：先观测，再测试 fallback，最后才提供显式执行开关。

## 7. Bad Cases

需要重点向面试官说明的 bad cases：

- 非法 JSON：parser 抛错，fallback keyword。
- 未知工具：allowlist 校验失败，fallback keyword。
- 工具冲突：decision 与 toolNames 不匹配，fallback keyword。
- provider disabled：不会执行 LLM decision，fallback keyword。
- provider timeout：不影响 Agent API 可用性，fallback keyword。
- 空任务：prompt builder 拒绝，业务入口也会校验 task。
- 文档未解析：不进入摘要 / QA / RAG 工具，返回 status_only。

## 8. 测试证据

当前测试覆盖：

- prompt builder 输出协议、工具名、安全限制。
- parser 合法 JSON、自然语言包裹 JSON、非法 decision、未知 toolName、confidence 越界和空输入。
- shadow selector evaluation。
- real provider shadow runner disabled / fake / failure path。
- `DocumentAgentLlmExecuteModeTest` 覆盖 fake provider 选择 summary / QA / RAG 后实际执行对应工具，以及 keyword mode 不变、非法 toolName、decision / toolNames 不匹配、解析失败、provider disabled、provider 异常时 fallback keyword。

T062c 真实 provider execute runtime 当前为 BLOCKED：当前 shell 未注入必要 provider 环境变量，未读取 `backend/.env`，未输出变量值，未执行真实 HTTP。

T059 / T062 文档记录不代表已接 OpenAI 官方 tools / function_call API；当前是文本 JSON 选择加服务端 allowlist 执行，不接 LangChain4j，不接 Qdrant，不接真实 embedding。
