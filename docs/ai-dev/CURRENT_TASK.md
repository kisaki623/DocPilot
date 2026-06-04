# Current Task

当前任务：T009 RAG Scope & Permission Guard 已完成；下一步待确认

## 目标

基于 T001-T008 已完成的 RAG indexing、retrieval、QA、SSE、Agent 接入、离线 smoke 和 parse success trigger 能力，强化 RAG 主链路的 userId / documentId / indexVersion scope isolation。

## 范围

T009 已完成：

- 新增 `RagScopeGuard`，统一校验 document owner；
- `RagDocumentRetrievalService` 在 metadata filter 之外，对 vector search hit 做 userId / documentId / indexVersion 二次校验；
- `RagQaService` 对权限类错误不做 retrieval fallback，不调用大模型，不保存 QA history；
- `RagIndexingTriggerService` 在自动 indexing 前校验 document scope，避免直接调用 trigger 时写入错误用户范围；
- `DocumentRagQaTool` 保持依赖 `RagQaService` 权限拒绝，不吞掉越权异常；
- 测试不依赖远程 Qdrant、真实 embedding API 或真实大模型。

下一步候选：

- T010 前端小范围展示 RAG evidence / citations；
- 或 RAG indexing trigger 进一步 MQ / Outbox 化。

## 禁止事项

- 不做多文档 RAG；
- 不做 ToolSpec；
- 不改前端；
- 不改根 README；
- 不调用真实 embedding / chat API；
- 不操作远程服务器；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- retrieval / QA / Agent rag_qa_tool 无法返回跨用户、跨文档、跨版本 hit 或 citation；
- 权限拒绝不会被 no-evidence / retrieval-unavailable fallback 掩盖；
- parse success trigger 不会为不匹配 user/document 的请求执行 indexing；
- 受影响后端测试通过。

## T009 输出

- 新增 guard；
- 修改 retrieval / QA SSE / indexing trigger；
- 补充越权与 scope isolation 测试；
- 更新 ai-dev 简短进度记录。
