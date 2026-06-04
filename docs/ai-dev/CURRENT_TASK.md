# Current Task

当前任务：T010b ToolCall API + 参数校验 + ToolCallResult 已完成；下一步待确认

## 目标

基于 T010a 的 `ToolSpec` / `ToolSpecRegistry`，新增最小统一工具调用入口，让后端可以列出可见工具并通过标准 `ToolCallResult` 调用安全子集工具。

## 范围

T010b 已完成：

- 新增 `GET /api/agent/tools`，返回 `ToolSpecRegistry` 中可见工具列表；
- 新增 `POST /api/agent/tools/call`，支持通过 `toolName + arguments` 调用工具；
- 新增 `ToolCallService`，统一做 tool spec 查询、callable allowlist、参数校验、typed input mapping 和结果标准化；
- 新增 `ToolArgumentValidator` / `ToolInputMapper`，将 `Map<String,Object>` 参数转换为现有 typed tool input；
- `ToolCallResult` 扩展 `durationMs`、`citations`、`retrievalHits`；
- T010b ToolCall API 只开放 `document_status_tool` 和 `rag_qa_tool`，旧 `document_rag_tool` showcase 不暴露为 callable。

下一步候选：

- T010c：逐步引入统一 `ToolExecutor` 执行路径；
- 或 T010d：OpenAI Function Calling adapter；
- 或前端小范围展示 RAG evidence / citations。

## 禁止事项

- 不迁移 `DocumentAgentServiceImpl` 主执行链；
- 不接 OpenAI Function Calling adapter；
- 不做 MCP；
- 不做多文档 RAG；
- 不把所有现有 Agent 工具强行开放到 ToolCall API；
- 不改前端；
- 不改根 README；
- 不调用真实 embedding / chat API；
- 不操作远程服务器；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- GET 工具列表不返回 legacy `document_rag_tool`；
- POST ToolCall API 仅允许 status 与 `rag_qa_tool`；
- 参数缺失、类型错误、userId mismatch、topK / indexVersion 非法均有明确错误；
- `rag_qa_tool` 仍走现有 `RagQaService` / `RagScopeGuard` 权限链路；
- 测试不依赖真实 embedding、真实大模型或远程 Qdrant。

## T010b 输出

- 新增 ToolCall API；
- 新增 ToolCallService、参数校验和 input mapper；
- 扩展 ToolCallResult；
- 补充 controller / service / validator 测试；
- 更新 ai-dev 简短进度记录。
