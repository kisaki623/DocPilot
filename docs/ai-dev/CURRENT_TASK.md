# Current Task

当前任务：T010c 现有 Agent 工具迁移到 ToolCallService 已完成；下一步待确认

## 目标

在不大改 Agent 架构的前提下，让现有 Agent 工作流逐步复用 T010a/T010b 的 `ToolSpec` / `ToolCallService` / `ToolCallResult`，为后续 T010d OpenAI-compatible Function Calling adapter 做准备。

## 范围

T010c 已完成：

- `DocumentAgentServiceImpl` 的 `document_status_tool` 调用改为通过 `ToolCallService` 执行；
- `DocumentAgentServiceImpl` 的 `rag_qa_tool` 调用改为通过 `ToolCallService` 执行；
- `ToolCallResult` 统一进入 Agent step summary：记录 toolName、status、outputSummary、durationMs 和脱敏 errorType；
- RAG 成功结果继续填充 Agent response 的 retrieval hits、citations、legacy citations 和 RAG answer context；
- 普通 RAG 工具失败会记录 FAILED step 并返回安全 fallback；权限 / 文档归属类错误不被 fallback 掩盖；
- `document_summary_tool`、`document_qa_tool` 和旧 `document_rag_tool` showcase 链路保持原执行路径。

下一步候选：

- T010d：OpenAI-compatible Function Calling adapter；
- 或前端小范围展示 RAG evidence / citations。

## 禁止事项

- 不接 MCP；
- 不做多文档 RAG；
- 不把所有现有 Agent 工具强行迁移；
- 不改前端；
- 不改根 README；
- 不调用真实 embedding / chat API；
- 不操作远程服务器；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- status 与 `rag_qa_tool` 由 Agent 主流程通过 `ToolCallService` 调用；
- summary / qa legacy 分支行为保持兼容；
- `rag_qa_tool` 仍走现有 `RagQaService` / `RagScopeGuard` 权限链路；
- RAG citations / retrievalHits / noEvidence 继续进入 Agent response / trace；
- 测试不依赖真实 embedding、真实大模型或远程 Qdrant。

## T010c 输出

- Agent 主流程渐进接入 `ToolCallService`；
- ToolCallResult 到 AgentStep 的转换 helper；
- status / RAG 迁移回归测试；
- 更新 ai-dev 简短进度记录。
