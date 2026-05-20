# RAG Vector Store Adapter Boundary

本文记录 DocPilot 接入真实向量库的最小边界。T071 只做设计；T075 已新增 Qdrant disabled skeleton 和配置边界；T079 已新增默认关闭的 Qdrant HTTP adapter 和本地 fake server 测试；T082-T086 已把 RAG 主链路推进到可通过 `VectorStore` 抽象替换，并用 JDK 本地 fake Qdrant server 验证 index / search / QA context / fallback；T088-T092 已补 chunking policy、retrieval scope isolation、脱敏 debug snapshot、collection preflight boundary 和离线 retrieval eval。当前仍未启动真实 Qdrant runtime，不新增依赖，不修改 docker-compose，不新增数据库表。

## 1. 当前状态

- Embedding：已有 `EmbeddingModel` 接口，当前支持 `fake`、`disabled`、`openai_compatible`。默认仍为 `fake`。
- 真实 embedding runtime：T068 preflight 仍为 BLOCKED，因为 `APP_RAG_EMBEDDING_PROVIDER=False`、`APP_RAG_EMBEDDING_BASE_URL=False`、`APP_RAG_EMBEDDING_MODEL=False`、`APP_RAG_EMBEDDING_API_KEY=False`。
- Vector store：当前是 `InMemoryVectorStore`，仅适合测试、demo 和单进程 smoke，不是持久化向量库。
- QA RAG：`app.rag.qa.enabled=false` 默认关闭；开启后可用 fake embedding + in-memory vector store 注入受限 RAG context。
- Trace：已有内部 `RagQaTrace` / `RagQaTraceFormatter`，可脱敏展示 retrievedCount、contextHash、fallback、indexReused 等摘要。
- Demo / lifecycle：T072 已新增脱敏 RAG QA demo 脚本；T073 已让 Agent step 输出 RAG trace 摘要；T074 已新增 in-memory index lifecycle tracking。
- Vector store adapter：T075 已新增 `app.rag.vector-store.provider=in_memory|qdrant_disabled`、`RagVectorStoreProperties`、`VectorStoreFactory` 和 `DisabledQdrantVectorStore`；T079 已新增显式 `provider=qdrant` 的 `QdrantVectorStore` HTTP adapter；T083 已确认 RAG 主链路通过 `VectorStore` 抽象运行。默认仍为 `in_memory`；`qdrant_disabled` 只返回本地 disabled 行为；`qdrant` 只有显式配置且 endpoint 齐全时才发 HTTP。
- Qdrant 测试与 preflight：T077 已补 VectorStore contract tests，T078 已补 payload builder / parser，T079 使用 JDK 本地 fake HTTP server 验证 adapter，T084-T085 进一步用本地 fake server 验证 index / search 和 QA context 链路；T080 已新增脱敏 preflight 脚本，缺环境时 SKIPPED / BLOCKED。
- 故障 fallback：T086 已补 Qdrant HTTP error、timeout、disabled、空召回和 Agent rag_tool 失败测试；fallback reason 仅保留 `qdrant_http_error`、`qdrant_timeout`、`qdrant_disabled`、`rag_retrieval_failed` 等脱敏摘要。
- Retrieval hardening / eval：T088 已新增可配置 chunking policy；T089 已强制 userId + documentId search scope；T090 已新增脱敏 debug snapshot / reporter；T091 已补 collection preflight 只读 / dry-run 边界；T092 已新增 fake embedding + in-memory 离线 retrieval eval，并用本地 fake Qdrant server 覆盖 adapter eval。

当前仍未启动真实 Qdrant / Redis Vector、LangChain4j / Spring AI、数据库表或 docker-compose 服务。

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

- adapter contract test：fake adapter 与 Qdrant adapter 共享用例，验证 upsert、search、deleteByDocument、filter、topK order；T077 已覆盖 in-memory / qdrant_disabled / factory 行为。
- request builder / parser test：T078 已覆盖 Qdrant request payload 和 response parser，不真实联网。
- HTTP adapter test：T079 已使用 JDK 本地 fake HTTP server 验证 path / method / body shape / parser topK；T084 已补同一 fake server 内的 add / search 双请求验证；不依赖真实 Qdrant。
- service test：T083 已确认 `RagIndexService` / `RagRetrievalService` / `RagQaContextBuilder` 通过 `VectorStore` 抽象运行；T085 已用 fake Qdrant server 验证 QA context 可从 Qdrant adapter 返回召回结果。
- QA / Agent fallback test：`app.rag.qa.enabled=true` 时验证 context 注入、maxContextChars、fallback、cache key；T086 已覆盖 Qdrant 500 / timeout / 空结果 / rag_tool 失败时的安全 fallback。
- preflight smoke：只有环境变量齐全时才做一次真实健康检查；缺环境标记 BLOCKED，不硬刷。

## 9. 本轮明确不做

- 不启动真实 Qdrant runtime；T079 仅实现默认关闭的 HTTP adapter 和本地 fake server 测试。
- 不接 Redis Vector / Redis Stack。
- 不新增 Maven 依赖。
- 不修改 docker-compose。
- 不新增数据库表。
- 不新增公开 REST API。
- 不接 LangChain4j / Spring AI。
- 不处理 T010 / MQ blocked。
- 不把当前 fake embedding + in-memory vector store 写成生产完整 RAG。

## 10. 2026-05-21 T082-T086 链路验证收口

- T082 已校准 Qdrant 配置 / 环境变量命名：`RAG_VECTOR_STORE_PROVIDER`、`RAG_QDRANT_ENDPOINT`、`RAG_QDRANT_API_KEY`、`RAG_QDRANT_COLLECTION`、`RAG_QDRANT_CONNECT_TIMEOUT_MS`、`RAG_QDRANT_REQUEST_TIMEOUT_MS`；默认 provider 仍为 `in_memory`。
- T083 已将 RAG 主链路推进到 `VectorStore` 抽象：`RagIndexService`、`RagRetrievalService`、`RagQaContextBuilder` 和 `DocumentRagTool` 不再把主路径硬编码为 `InMemoryVectorStore`。
- T084 已用 JDK 本地 fake HTTP server 验证 `QdrantVectorStore` 的 upsert / search path、method、payload metadata、userId + documentId filter 和 topK parser。
- T085 已用 fake Qdrant server 验证 RAG QA context 链路：显式 `provider=qdrant` 时，index / search 可通过 Qdrant adapter 返回召回结果，trace 能显示 `vectorStoreType=qdrant`。
- T086 已验证 Qdrant 故障 fallback：HTTP 500、timeout、disabled、空结果和 Agent rag_tool 失败不会破坏默认 QA / Agent 体验；失败 reason 只保留脱敏摘要。
- 当前仍未启动真实 Qdrant，未访问外部 Qdrant 服务，未修改 docker-compose，未新增 API / DB / Maven 依赖，未接 Redis Vector、LangChain4j 或 Spring AI。

## 11. 2026-05-21 T088-T092 Retrieval Hardening / Eval 收口

- T088 已补 `RagChunkingPolicy`、`RagChunker` 和 `RagChunkMetadata`，支持可配置 chunk 长度、overlap、最大 chunk 数、稳定 chunkId、offset、hash 和 truncation trace。
- T089 已补 `RagSearchScope`，要求 search scope 至少包含 userId + documentId；in-memory 与 Qdrant search 均按该 scope 过滤或构造 filter。
- T090 已补 `RagDebugSnapshot` / `RagDebugReporter`，只输出白名单摘要字段，不包含正文、prompt、endpoint、Authorization、API key 或 provider response。
- T091 已补 collection info / create request builder 和 response parser；preflight 脚本默认只读 / dry-run，只有显式允许时才会尝试 create。
- T092 已补离线 retrieval eval：默认 fake embedding + in-memory vector store，指标为 total、hitCount、missCount、hitRate、averageRetrievedCount；另用本地 fake Qdrant server 验证 adapter eval，不依赖真实 Qdrant。
- 当前边界不变：默认 provider 仍为 `in_memory`，Qdrant adapter 默认关闭；未启动真实 Qdrant，未新增公开 API / DB / Maven 依赖 / docker-compose，未接 Redis Vector、LangChain4j 或 Spring AI。

## 12. 2026-05-21 T095 Qdrant Adapter Safety Coverage

- T095 补强了 Qdrant adapter 只读安全测试：默认 provider 仍由既有 contract tests 锁定为 `in_memory`，`qdrant` 仍必须显式配置 endpoint 才会创建 HTTP adapter。
- `QdrantVectorStore` 新增测试覆盖显式 userId + documentId search scope，确认 search payload filter 不缺隔离条件。
- `QdrantPointPayload` 的 `metadata` 字段收敛为白名单，只保留 contentHash / chunkHash / charStart / charEnd / chunkVersion / source；不会把 prompt、provider response 或误塞入 metadata 的正文类字段复制进 metadata / citation。
- Qdrant HTTP 500 和缺 endpoint 的失败路径继续保持脱敏：异常信息不包含 endpoint 原文、collection、Authorization、API key 或 response body。
- 测试仍只使用 JDK 本地 fake HTTP server；未启动真实 Qdrant，未访问外部 Qdrant 服务，未新增 API / DB / Maven 依赖 / docker-compose。
