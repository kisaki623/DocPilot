# Current Task

当前任务：T004 RAG Indexing Workflow

## 目标

将 T001 的 DocumentChunk、T002 的 EmbeddingProvider、T003 的 VectorStoreClient 串成可复用的 RAG indexing workflow。

## 范围

下一轮优先做：

- parse success 后的 indexing 入口设计；
- chunk 落库与 indexVersion 管理；
- batch embedding；
- Qdrant upsert；
- indexStatus 更新；
- retry / rebuild API 的最小实现；
- service 层和本地 stub 测试。

## 禁止事项

- 不接前端；
- 不做 RAG QA API；
- 不做 SSE RAG 问答；
- 不写生产级 RAG 夸大文案；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- indexing workflow 能从文本生成 chunks；
- chunks 能落库；
- embedding provider 可 mock；
- vector store 可用 in-memory 或 Qdrant stub；
- indexStatus 状态明确；
- 普通测试不依赖远程 Qdrant；
- 受影响后端测试通过。

## 完成后输出

- 修改文件；
- 测试结果；
- 新增能力；
- 下一步是否进入 T005 Retrieval + QA + SSE；
- 可写进简历的一句话。
