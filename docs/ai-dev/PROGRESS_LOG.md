# Progress Log

## 2026-06-02 T003a

- T003a Qdrant VectorStore adapter 已完成：新增 VectorStoreClient 抽象、InMemory fallback、Qdrant HTTP adapter、metadata filter、deleteByDocumentId 与本地 stub 测试；不操作远程服务器，不部署 Qdrant。

## 2026-06-02 T002

- T002 EmbeddingProvider 抽象已完成：新增 provider/request/result、deterministic mock、OpenAI-compatible provider、配置适配和兼容层测试。
- `RagEmbeddingProperties` 仍是唯一 Spring `app.rag.embedding` 配置入口；未接 Qdrant，未调用真实外部 embedding API。
- 下一步建议进入 T003 Qdrant VectorStore adapter。

## 2026-06-02 T001b-confirmed

- T001b-confirmed：远程 MySQL docpilot 数据库已创建 tb_document_chunk 表。
- MySQL 容器：docpilot-mysql。
- 表字段覆盖 document_id、user_id、chunk_index、content、content_hash、offset、token_count、index_status、index_version、embedding_model、vector_id、create_time、update_time。
- 索引包含 idx_document_chunk_document_id、idx_document_chunk_user_document、idx_document_chunk_status。
- 唯一约束为 uk_document_version_chunk(document_id, index_version, chunk_index)。
- 未重启服务，未修改其他表。

## 2026-06-02 T001

- T001 RAG 数据模型和 ChunkingService 已完成。
- 新增 DocumentChunkEntity / DocumentChunkMapper / DocumentChunkService / ChunkingService / tb_document_chunk SQL 脚本 / 单元测试。
- 下一步建议进入 T002 EmbeddingProvider 抽象。

## 2026-06-02

- 完成 docs 文档审计和索引整理。
- 将 `docs/README.md` 整理为中文文档地图，明确当前推进优先级、文档分类和大文件读取规则。
- 将根层 RAG、Agent、showcase、archive 文档移动到分类目录，并清理根层重复 stub。
- 明确 RAG 求职级路线：从 fake embedding / in-memory showcase 升级到 embedding provider + Qdrant + chunk 持久化 + citations + SSE + Agent Trace。
- 当前任务切换为 T001 RAG 数据模型和 ChunkingService。
