# DocPilot RAG Roadmap

## 1. 目标

DocPilot 的 RAG 目标不是停留在 fake embedding / in-memory showcase，而是升级为可运行、可截图、可讲源码链路的求职级 RAG 闭环。

目标链路：

```text
文档上传
-> 文档解析
-> 文本清洗
-> chunk 切分
-> chunk 元数据落库
-> embedding 向量化
-> Qdrant 向量写入
-> query embedding
-> metadata filter
-> topK 召回
-> prompt 上下文构造
-> LLM 生成回答
-> SSE 流式返回
-> citation 引用证据
-> Agent Trace / eval 记录
```

## 2. 当前已有基础

- 上传和解析链路：已有文档创建、上传、分片上传、解析任务和状态流转基础。
- RocketMQ + Outbox：已有异步解析、outbox relay、scan job、幂等和补偿设计基础。
- Redis：已有缓存、限流、登录 token、上传会话和问答相关状态管理。
- MinIO：已有对象存储模式，并保留 local 存储。
- SSE 问答：已有普通问答与流式问答，支持 citations 和失败降级。
- Agent / Trace：已有工具选择、AgentTask / AgentStep、routingReason、matchedKeywords、前端 trace 展示。
- 轻量 RAG 基础：已有 fake embedding、in-memory vector store、chunking policy、retrieval scope isolation、citations、RAG trace/debug snapshot、offline eval 和 Agent RAG showcase。
- Qdrant / vector store adapter 边界：已有默认关闭的 Qdrant HTTP adapter、payload mapping、fake server 测试、preflight 和 fallback 测试；默认路径仍不是生产 Qdrant runtime，真实 Qdrant 需要显式配置和环境验证。

## 3. 选型决策

- MySQL：存 document、chunk、index status、QA history。
- Qdrant：存 chunk embedding 和 payload。
- Redis：缓存、限流、上传会话。
- RocketMQ + Outbox：解析后异步触发 RAG indexing。
- MinIO：存原始文件。
- OpenAI-compatible embedding provider：真实 embedding。
- MockEmbeddingProvider：测试和本地 fallback。
- 不优先接 LangChain4j 全家桶，优先自研主链路，后续预留 adapter。

## 4. 数据模型建议

### tb_document_chunk

| 字段 | 说明 |
| --- | --- |
| id | chunk 主键 |
| document_id | 文档 ID |
| user_id | 用户 ID |
| chunk_index | 文档内 chunk 序号，从 0 连续递增 |
| content | chunk 原文 |
| content_hash | chunk 内容 hash |
| start_offset | 原文起始 offset |
| end_offset | 原文结束 offset |
| token_count | 估算或实际 token 数 |
| index_status | pending / indexing / indexed / failed |
| index_version | indexing 策略版本 |
| embedding_model | embedding 模型名 |
| vector_id | Qdrant point id |
| create_time | 创建时间 |
| update_time | 更新时间 |

## 5. 任务拆分

### T001 RAG 数据模型和 ChunkingService

- 新增 DocumentChunk 实体 / Mapper / Service。
- 新增 ChunkingService。
- 单测覆盖短文本、长文本、overlap、hash、空文本。

### T002 EmbeddingProvider 抽象

- EmbeddingProvider。
- MockEmbeddingProvider。
- OpenAICompatibleEmbeddingProvider。
- 配置隔离，不提交 key。

### T003 Qdrant VectorStore adapter

- docker-compose 增加 qdrant。
- VectorStoreClient 接口。
- QdrantVectorStoreClient。
- InMemoryVectorStoreClient。
- upsert / search / deleteByDocumentId。

### T004 RAG Indexing Workflow

- parse success 后触发 indexing。
- chunk 落库。
- batch embedding。
- qdrant upsert。
- index status。
- retry / rebuild API。

### T005 RAG Retrieval + QA + SSE

- `POST /api/rag/retrieve`。
- `POST /api/documents/{documentId}/qa/rag`。
- `POST /api/documents/{documentId}/qa/rag/stream`。
- citations。
- no-evidence fallback。
- 前端展示召回片段和引用证据。

### T006 Agent Integration

- rag_retrieval_tool 接入 RetrievalService。
- Agent Step 记录 retrieval hits。
- Trace 展示 toolName、routingReason、citations。

### T007 Eval

- 离线检索评测集。
- hit@k / citationHitRate。
- smoke demo case。
- README 可复现说明。

## 6. 简历边界

可以写：

- RAG 文档问答链路。
- 文档切分。
- embedding provider 抽象。
- Qdrant 向量检索。
- metadata filter。
- 引用证据。
- SSE 流式回答。
- Agent Trace。

未实现前不要写：

- 生产级 RAG。
- 多智能体自主规划。
- 企业级观测平台。
- 完整 OpenAI tools 标准接入。
- 线上 SLA。
- 大规模压测。
