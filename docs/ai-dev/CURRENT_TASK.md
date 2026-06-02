# Current Task

当前任务：T003a Qdrant VectorStore adapter

## 目标

为 DocPilot 增加 RAG VectorStore adapter 抽象和本地可测试实现，为后续 T004 RAG Indexing Workflow 做准备。

## 范围

本轮只做：

- VectorStoreClient 抽象；
- VectorPoint / VectorSearchRequest / VectorSearchResult / VectorSearchHit；
- InMemoryVectorStoreClient；
- QdrantVectorStoreClient；
- RagVectorStoreProperties 配置补充；
- 单元测试和本地 HttpServer stub 测试。

## 禁止事项

- 不接真实 embedding；
- 不把 DocumentChunk 自动写入 Qdrant；
- 不在 parse success 后触发 indexing；
- 不做 RAG QA API；
- 不做 SSE RAG 问答；
- 不改前端；
- 不改根 README；
- 不操作远程服务器；
- 不读取或提交 `.env` / key / secret。

## 验收标准

- upsert chunks 请求结构正确；
- search topK 可用；
- userId / documentId / indexVersion filter 生效；
- deleteByDocumentId 可用；
- InMemory client deterministic；
- Qdrant client 测试只打本地 stub；
- 现有 RAG 相关测试继续通过。

## 完成后输出

- 修改文件；
- 测试结果；
- 新增能力；
- 下一步是否进入 T003b 或 T004；
- 可写进简历的一句话。
