# Current Task

当前任务：T012 多文档 RAG eval 已完成；下一步待确认

## 目标

为 T011 KnowledgeBase 多文档 RAG 增加轻量、离线、可复现的 retrieval quality smoke / eval，验证跨文档召回、citation 来源和 scope isolation。

## 范围

T012 已完成：

- 新增 `knowledge-base-rag-eval-cases.json` 多文档 eval fixture；
- 新增测试侧 `KnowledgeBaseRagEvalRunner` / case / result / metrics；
- 使用 `MockEmbeddingProvider` + `InMemoryVectorStoreClient` 离线执行 KnowledgeBase retrieval / QA；
- 指标覆盖 `hitAtK`、`documentHitRate`、`citationHitRate`、`noEvidenceRate`、`scopeViolationRate`；
- artifact 输出到 `backend/target/rag-eval/knowledge-base-rag-eval-latest.json`，不纳入 git；
- artifact 不保存文档原文、模型输入、evidence context、模型输出或密钥信息。

下一步候选：

- T013：KnowledgeBase Agent Tool / ToolSpec 接入；
- 或 KnowledgeBase RAG SSE；
- 或前端小范围展示知识库 RAG citations。

## 禁止事项

- 不做知识库 RAG SSE；
- 不接 Agent / ToolSpec；
- 不接 MCP；
- 不做 reranker；
- 不做 hybrid search；
- 不改前端；
- 不改根 README；
- 不读取或提交 `.env` / key / secret；
- 不调用真实外部 embedding / LLM / 远程 Qdrant；
- 不操作远程服务器；
- 不 push。

## 验收标准

- fixture 至少覆盖多文档命中、单文档命中、no-evidence、scope isolation 和 citation 来源校验；
- eval runner 复用 T011 KnowledgeBase RAG service，不混用旧 showcase RAG 链路；
- 指标全部稳定通过，`scopeViolationRate` 期望为 0；
- no-evidence case 不调用 mock 大模型；
- artifact 脱敏，不包含文档原文、模型输入、evidence context、模型输出或 secret 关键词；
- 测试不依赖真实 embedding、真实大模型或远程 Qdrant。

## T012 输出

- KnowledgeBase 多文档 RAG 离线 eval fixture；
- 测试侧 eval runner / metrics / result 模型；
- 可选写入 target 目录的安全 JSON artifact；
- fixture、metrics、runner 单元测试；
- 更新 ai-dev 简短进度记录。
