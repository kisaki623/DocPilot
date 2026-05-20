# RAG Vector Store Adapter Boundary

本文记录 DocPilot 接入真实向量库的最小边界。T071 只做设计；T075 已新增 Qdrant disabled skeleton 和配置边界，但仍不是真实 Qdrant runtime，不新增依赖，不修改 docker-compose，不新增数据库表。

## 1. 当前状态

- Embedding：已有 `EmbeddingModel` 接口，当前支持 `fake`、`disabled`、`openai_compatible`。默认仍为 `fake`。
- 真实 embedding runtime：T068 preflight 仍为 BLOCKED，因为 `APP_RAG_EMBEDDING_PROVIDER=False`、`APP_RAG_EMBEDDING_BASE_URL=False`、`APP_RAG_EMBEDDING_MODEL=False`、`APP_RAG_EMBEDDING_API_KEY=False`。
- Vector store：当前是 `InMemoryVectorStore`，仅适合测试、demo 和单进程 smoke，不是持久化向量库。
- QA RAG：`app.rag.qa.enabled=false` 默认关闭；开启后可用 fake embedding + in-memory vector store 注入受限 RAG context。
- Trace：已有内部 `RagQaTrace` / `RagQaTraceFormatter`，可脱敏展示 retrievedCount、contextHash、fallback、indexReused 等摘要。
- Demo / lifecycle：T072 已新增脱敏 RAG QA demo 脚本；T073 已让 Agent step 输出 RAG trace 摘要；T074 已新增 in-memory index lifecycle tracking。
- Vector store skeleton：T075 已新增 `app.rag.vector-store.provider=in_memory|qdrant_disabled`、`RagVectorStoreProperties`、`VectorStoreFactory` 和 `DisabledQdrantVectorStore`。默认仍为 `in_memory`；`qdrant_disabled` 只返回本地 disabled 行为，不发 HTTP。

当前仍未真实接 Qdrant / Redis Vector、LangChain4j / Spring AI、数据库表或 docker-compose 服务。

## 2. 下一步推荐

求职冲刺如果要接真实向量库，推荐优先 Qdrant，而不是 Redis Vector。

原因：

- Qdrant 是专用向量数据库，collection、payload filter、score、point id 和 metadata 语义直接，对 RAG 面试解释最清晰。
- HTTP API 边界相对简单，Java 侧可以先写轻量 adapter，不必引入重框架。
- Redis Vector 依赖 Redis Stack / RediSearch，不能假设当前普通 Redis 已支持；如果环境不是 Redis Stack，接入成本和版本风险会反而更高。
- 当前项目已有普通 Redis 用于缓存、限流和 session，不应把普通 Redis 误写成向量检索能力。

Redis Vector 适合作为备选：只有在用户确认本地 / 远程 Redis Stack 可用、并且希望减少中间件数量时再评估。

## 3. 最小接口边界

当前 `VectorStore` 接口只覆盖测试用 `add/searchTopK/clear`。真实 adapter 建议扩展为内部接口，不新增公开 API：

```java
interface RagVectorStore {
    void upsert(RagVectorPoint point);
    List<RagVectorHit> search(RagVectorQuery query);
    void deleteByDocument(Long userId, Long documentId, String documentVersion);
}
```

建议值对象：

- `RagVectorPoint`：pointId、userId、documentId、documentVersion、chunkIndex、contentHash、embedding、metadata。
- `RagVectorQuery`：userId、documentId、documentVersion、queryEmbedding、topK、minScore。
- `RagVectorHit`：documentId、documentVersion、chunkIndex、score、contentHash、metadata。

公开 controller、前端和 QA response 暂不需要变化；先通过 service/test 验证 adapter。

## 4. Collection / Index / Metadata

Qdrant collection 建议：

- collection：`docpilot_chunks_v1`
- distance：cosine
- vector size：由 embedding model dimension 决定，必须与 provider 配置一致。
- point id：可使用稳定 hash，例如 `userId:documentId:documentVersion:chunkIndex:contentHash` 的 hash 值。

payload 至少包含：

- `userId`
- `documentId`
- `documentVersion`
- `chunkIndex`
- `contentHash`
- `chunkVersion`
- `charStart`
- `charEnd`
- `source`
- `createdAt`

metadata 中不建议保存大段 chunk 正文。正文仍应由 MySQL / document chunk metadata 层管理；向量库 payload 只保存过滤和 citation 所需字段。

## 5. 隔离策略

检索必须至少按 `userId + documentId + documentVersion` 过滤。

- `userId`：避免跨用户召回。
- `documentId`：避免跨文档混召回。
- `documentVersion`：避免文档更新后旧 chunk 继续参与召回。
- `chunkVersion`：chunk 策略调整后可重建索引并区分旧数据。
- `contentHash`：用于幂等 upsert 和 citation 对齐。

如果未来支持跨文档知识库，再单独设计 workspace / collection / permission scope，不在当前最小 QA RAG 链路里混入。

## 6. TopK / Score / Citation

adapter 返回的 hit 应保持：

- `topK` 由 `app.rag.qa.top-k` 或调用方传入控制。
- `score` 原样记录向量库相似度；UI / trace 中只展示摘要，不输出 chunk 全文。
- citation metadata 至少包含 chunkIndex、charStart、charEnd、contentHash 和 chunkVersion。
- answer context builder 根据 hit 回查受限文本，并受 `maxContextChars` 限制。

后续如果加 rerank，应作为 `RagRetrievalService` 的独立步骤，不塞进底层 vector store adapter。

## 7. Fallback

推荐 fail-open 到当前普通 QA / fake RAG 路径：

- embedding provider 缺配置：明确 BLOCKED / disabled，不发 HTTP。
- embedding provider timeout / 4xx / 5xx：记录安全错误类型，fallback 普通 QA。
- vector store 不可用：fallback 普通 QA 或测试环境 fake store。
- collection 不存在：启动 / preflight 标记未就绪，不在用户请求路径里创建生产 collection。
- 空召回：fallback 普通 QA，trace 标记 retrievedCount=0。

日志和 trace 只记录 provider 类型、vectorStoreType、retrievedCount、fallbackReason 等摘要，不输出 API Key、baseUrl、Authorization、prompt、文档正文、chunk 全文或 provider response。

## 8. 测试策略

最小测试分层：

- adapter contract test：fake adapter 与未来真实 Qdrant adapter 共享用例，验证 upsert、search、deleteByDocument、filter、topK order；当前 T075 只覆盖 disabled skeleton 的配置选择与明确失败行为。
- request builder / parser test：如 Qdrant HTTP adapter，先测 request payload 和 response parser，不真实联网。
- service test：`RagIndexService` / `RagRetrievalService` 使用 fake adapter 验证 citation metadata。
- QA test：`app.rag.qa.enabled=true` 时验证 context 注入、maxContextChars、fallback、cache key。
- preflight smoke：只有环境变量齐全时才做一次真实健康检查；缺环境标记 BLOCKED，不硬刷。

## 9. 本轮明确不做

- 不真实接 Qdrant runtime；T075 仅有 `qdrant_disabled` skeleton。
- 不接 Redis Vector / Redis Stack。
- 不新增 Maven 依赖。
- 不修改 docker-compose。
- 不新增数据库表。
- 不新增公开 REST API。
- 不接 LangChain4j / Spring AI。
- 不处理 T010 / MQ blocked。
- 不把当前 fake embedding + in-memory vector store 写成生产完整 RAG。
