# Current Task

当前任务：T010a ToolSpec / ToolRegistry 已完成；下一步待确认

## 目标

在不接 OpenAI Function Calling、不做 MCP、不迁移现有 Agent 执行链的前提下，为后续工具调用演进补齐内部 ToolSpec / ToolRegistry 底座。

## 范围

T010a 已完成：

- 新增 `ai.agent.tool.spec` 内部 package，包含 `ToolSpec`、参数 / 结果 schema、risk level、tool execution context、tool call result 和 executor contract；
- 新增 `DefaultToolSpecProvider` 与 `ToolSpecRegistry`，统一管理当前 Agent 工具元数据；
- `ToolDefinitionProvider` 改为从 `ToolSpecRegistry` 输出现有 `ToolDefinition`，保持 selector prompt 调用面兼容；
- `document_rag_tool` 仍保留为旧 showcase 工具，但不再作为 LLM selectable spec 暴露；
- 现有 `DocumentAgentServiceImpl` typed 工具执行链保持不变。

下一步候选：

- T010b：OpenAI Function Calling adapter；
- 或 T010c：逐步引入统一 `ToolExecutor` 执行路径；
- 或前端小范围展示 RAG evidence / citations。

## 禁止事项

- 不做 OpenAI Function Calling adapter；
- 不做 MCP；
- 不做 ToolCall API；
- 不做多文档 RAG；
- 不把所有现有 Agent 工具强行迁移到 `ToolExecutor`；
- 不改前端；
- 不改根 README；
- 不调用真实 embedding / chat API；
- 不操作远程服务器；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- 当前 Agent status / summary / qa / rag_qa_tool 路由保持兼容；
- 工具 metadata 有统一 spec 来源；
- legacy `document_rag_tool` 不误入新 RAG 主链路或 LLM selector；
- 测试不依赖真实 embedding、真实大模型或远程 Qdrant。

## T010a 输出

- 新增 ToolSpec 内部抽象底座；
- 新增 ToolSpecRegistry 和 ToolDefinition adapter；
- 更新 ToolDefinitionProvider；
- 补充 spec registry / provider / call result 测试；
- 更新 ai-dev 简短进度记录。
