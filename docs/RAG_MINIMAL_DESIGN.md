# RAG Minimal Design

本文档记录 DocPilot 从当前轻量文档问答升级到最小 RAG 的最短路径。T052 只做设计，不实现代码、不新增 API、不接 embedding provider、不接向量库。

## 1. 目标与边界

目标是把现有“文档内容切分 + 关键词检索 + citations”的问答链路，升级为可解释的最小 Retrieval Augmented Generation 闭环：

```text
parsed text -> chunk -> embedding -> vector store -> retrieve topK -> prompt assemble -> answer -> citations / score display
```

当前边界：

- 已有 Agent QA tool、普通问答、SSE 问答、citations 和前端引用展示。
- 当前不是完整生产向量 RAG；T054 / T055 已有 fake embedding + in-memory vector store + Agent Showcase 召回展示，T063 已新增真实 embedding adapter 代码路径但真实 runtime 尚未验证，T067 已新增默认关闭的 QA RAG context feature flag，T072-T092 已补脱敏 demo 脚本、Agent step trace 摘要、in-memory index lifecycle、Qdrant payload mapping、默认关闭的 Qdrant HTTP adapter、本地 fake server 链路测试、故障 fallback、可配置 chunking policy、检索 scope 隔离、脱敏 debug snapshot、collection preflight 边界和离线 retrieval eval；仍未启动真实 Qdrant / Redis Vector、chunk 持久化、rerank 或默认生产级 RAG routing。
- 完整上传 -> 解析 -> Agent run 的 T010 runtime 仍因 MQ disabled / NoopParseTaskMessageProducer 保持 BLOCKED。
- 本设计不修改代码，不修改配置，不新增公开 API。

## 2. 当前已有链路

当前可复用能力：

- 文档上传 / 创建 / 状态查询：已有文件和文档记录，解析成功后可读取文档正文。
- 文档问答：`DocumentQaServiceImpl` 已负责读取文档内容、切分文本、选择上下文、调用 mock 或 real answer service。
- citations：问答结果可返回 chunkIndex、charStart、charEnd、snippet、score 等引用信息，前端文档详情页和 Agent 页面可展示引用证据。
- Agent QA tool：Agent 路由到 `document_qa_tool` 后复用文档问答能力，并记录 AgentTask / AgentStep trace。

这意味着最小 RAG 不需要重做上传、鉴权、Agent trace 或 citations UI，优先补齐 chunk 持久化、embedding 与 retrieve 服务即可。

## 3. 最小 RAG 目标链路

建议最小闭环拆为 6 步：

1. `parsed text`：从已解析文档正文生成稳定文本版本。
2. `chunk`：按固定窗口和 overlap 生成 chunks，记录 chunkIndex、hash、metadata。
3. `embedding`：通过 fake embedding 或真实 embedding provider 生成向量。
4. `vector store`：写入向量库或测试用 in-memory store，并保留 documentId / userId payload。
5. `retrieve topK`：按问题向量检索 topK chunks，返回 score 与 citation mapping。
6. `answer`：把 topK chunks 组装进 prompt，调用现有 answer service，返回答案与 citations。

优先实现内部 service 链路，等链路稳定后再考虑公开 API 或前端交互增强。

## 4. 数据模型草案

### document_chunk

建议用于保存 chunk 元数据和可读文本，便于重建索引、排障和 citations 对齐。

| 字段 | 说明 |
| --- | --- |
| id | chunk 主键 |
| document_id | 所属文档 |
| user_id | 所属用户，用于鉴权和隔离 |
| chunk_index | 文档内 chunk 顺序 |
| content | chunk 文本 |
| char_start | 原文起始字符位置 |
| char_end | 原文结束字符位置 |
| metadata_json | 页码、标题、段落路径、解析版本等扩展信息 |
| content_hash | chunk 内容 hash，用于幂等重建 |
| chunk_version | chunk 策略版本 |
| created_at / updated_at | 审计时间 |

### chunk_embedding / vector payload

如果使用数据库保存 embedding，可设计 `chunk_embedding`：

| 字段 | 说明 |
| --- | --- |
| id | embedding 主键 |
| chunk_id | 对应 document_chunk |
| document_id | 冗余字段，便于过滤 |
| user_id | 冗余字段，便于鉴权过滤 |
| provider | embedding provider |
| model | embedding model |
| dimension | 向量维度 |
| vector_ref | 外部向量库 point id 或本地引用 |
| embedding_hash | embedding 输入和模型版本 hash |
| created_at / updated_at | 审计时间 |

如果使用 Qdrant / Redis Vector，则向量库 payload 至少包含：

- `documentId`
- `userId`
- `chunkIndex`
- `contentHash`
- `chunkVersion`
- `charStart`
- `charEnd`
- `metadata`

## 5. 内部 API / Service 草案

短期不新增公开 API，优先新增内部 service：

```java
List<RagChunkHit> retrieveForQuestion(Long documentId, String question, int topK);

RagAnswerResult answerWithRetrievedContext(Long documentId, String question, List<RagChunkHit> hits);
```

建议模块：

- `DocumentChunkingService`：从解析文本生成 chunks。
- `EmbeddingService`：统一 fake / real embedding provider。
- `VectorStoreService`：统一 upsert / search / delete。
- `RagRetrieveService`：封装鉴权、embedding、topK 检索和 fallback。
- `RagAnswerService`：封装 prompt assemble、answer service 调用和 citations 映射。

Agent 接入时，`document_qa_tool` 可以先保持当前逻辑；T067 已先在 `DocumentQaServiceImpl` 中加入默认关闭的 RAG context feature flag，后续可再决定是否让 Agent QA tool 显式展示该开关状态。

## 6. Fallback 策略

- embedding provider 不可用：返回可解释错误，或在 demo 模式使用 fake embedding；不能静默把失败写成 RAG 成功。
- vector store 不可用：降级到当前关键词 / chunk 检索，并在响应或日志中标注 fallback 来源。
- 文档未解析：返回 status_only 或 parse-not-ready，不进入 retrieve。
- chunk 索引不存在：触发只读提示或后台重建任务；不要在用户请求路径里无限等待。
- topK 为空：回答“未找到足够依据”，并返回空 citations。

## 7. 测试策略

最小测试应先覆盖 deterministic 行为：

- fake embedding：同一输入返回稳定向量，不需要真实 provider。
- deterministic retrieve：固定 chunks 和 question，topK 顺序稳定。
- topK order：断言 score 排序和过滤条件。
- citation mapping：断言 chunkIndex / charStart / charEnd / snippet 能映射回原文。
- fallback：embedding 不可用、vector store 不可用、文档未解析、topK 为空。
- Agent QA tool 兼容：RAG context provider 开关关闭时，当前 production routing 和 QA 行为不变。

## 8. 面试说法

可以这样讲：

- “当前项目已经实现轻量检索增强问答和 citations，Agent QA tool 可以复用这条链路，并展示执行轨迹。”
- “当前已经先用 fake embedding 和 in-memory vector store 打通了最小 RAG demo，并新增了默认关闭的 QA RAG context 注入开关、脱敏 trace、index lifecycle、可配置 chunking policy、检索 scope 隔离、Qdrant payload mapping、默认关闭的 Qdrant HTTP adapter、本地 fake server 链路测试、故障 fallback 和离线 retrieval eval；真实 embedding runtime 和真实 Qdrant runtime 还没有完成。”
- “我没有直接上 LangChain / LangGraph，是因为这个项目重点展示 Java 后端工程能力：鉴权、异步解析、幂等、trace、service 边界、fallback 和测试可控性。”
- “RAG 尚未实现时，我会明确说当前是轻量检索增强，不会把它包装成完整向量 RAG。”

## 9. T054 落地建议

T054 建议先做最小可测实现：

1. 新增 chunk service 和 fake embedding。
2. 新增 in-memory fake vector store 测试替身。
3. 用固定文档样例验证 retrieve topK 和 citations。
4. 保持公开 API 和前端不变。
5. 再根据 T053 选型决定是否引入 Qdrant / Redis Vector / MySQL fallback。

进入 T054 前需要用户确认：是否允许新增表、是否允许新增 docker-compose 服务、embedding 使用 fake 还是真实 provider、是否允许连接远程中间件。

## 10. T053 向量库选型结论

T053 已新增 `docs/VECTOR_STORE_SELECTION.md`。当前推荐路线是：

- 求职冲刺优先：fake embedding + in-memory fake vector store，先打通 service、retrieve、citations 和测试闭环。
- 后续工程化：Qdrant 作为 primary vector store，MySQL 保存 chunk metadata，in-memory fake 保留为测试替身。
- Redis Vector / Redis Stack 作为备选，前提是用户确认运行环境确实支持 RediSearch / vector index。

进入 T054 前仍需用户确认是否允许新增 docker-compose 服务、是否允许新增表、embedding 使用 fake 还是真实 provider，以及是否继续保持公开 API 不变。

## 11. T054 内部闭环实现状态

T054 已按求职冲刺优先方案落地第一阶段内部闭环。T054x 先稳定了既有 benchmark timing 脆弱断言后，targeted test、compile 和后端全量测试均已通过。

- 新增 `backend/src/main/java/com/docpilot/backend/ai/rag/` 包。
- 使用 `FakeEmbeddingModel` 生成稳定、可重复 embedding。
- 使用 `InMemoryVectorStore` 支持 `add` 和 `searchTopK`。
- `RagIndexService` 支持把文档文本切成 `DocumentChunk`，并写入内存向量库。
- `RagRetrievalService` 支持按 question 检索指定 documentId 的 topK chunks。
- `RagAnswerContextBuilder` 支持组装可注入 prompt 的上下文，并保留 citation metadata。

当前仍未做：

- 已新增真实 embedding adapter 代码路径，但真实 embedding provider runtime 仍 BLOCKED，未验证。
- 未启动真实 Qdrant / Redis Vector / MySQL vector fallback；T079 只新增了默认关闭的 Qdrant HTTP adapter，测试使用 JDK 本地 fake server，不依赖真实 Qdrant 服务。
- 未新增数据库表。
- 未新增 docker-compose 服务。
- 未新增公开 REST API。
- 已通过默认关闭的 feature flag 接入 Document QA context provider；默认 QA 行为不变，Agent production routing 仍未改为 RAG。

这一步的价值是先把 RAG 的 Java 内部边界、测试替身和 citation mapping 打通，为后续 T055 前端展示召回片段和 T054 后续真实向量库接入打基础。

## 12. T055 Agent Showcase 接入状态

T055 已将 T054 的内部 RAG 能力接到 Agent Showcase demo 路径：

- 新增实验性 `document_rag_tool`，每次运行基于已解析文档正文临时构建 fake embedding + in-memory vector store。
- `DocumentToolSelector` 仅在明确 RAG / 检索 / 召回 / 相似度 / 片段 / 找依据类任务中路由到 `rag_tool`，原有 summary / QA / status 行为保持不变。
- `DocumentAgentResponse` 向后兼容扩展 `ragResults` 与 `ragAnswerContext`，用于前端展示 retrieved chunks、score / similarity 和 citation metadata。
- `/agent` 页面新增 RAG 召回模板和展示区，保留 decision、routingReason、matchedKeywords、taskId、persisted steps、citations 和 finalAnswer。

验证结果：

- `cd backend; mvn -Dtest=*Rag* test` 通过。
- `mvn -Dtest=DocumentAgentServiceImplTest test` 通过。
- `mvn -Dtest=DocumentToolSelectorTest test` 通过。
- `mvn -DskipTests compile` 通过。
- `mvn test -DskipITs` 通过。
- `cd frontend; npm run lint` 通过。
- `npm run build` 通过。

T055 仍明确未做：

- 未接真实 embedding provider。
- 未启动真实 Qdrant / Redis Vector；T079 仅完成默认关闭的 Qdrant HTTP adapter 和本地 fake server 测试。
- 未接 LangChain4j。
- 未新增数据库表或 docker-compose 服务。
- 未将 RAG 写成 production routing；当前只是求职展示用的 Agent/RAG demo 路径。

## 13. T063 / T067 最新状态

- T063 已新增 embedding provider adapter 架构：默认 fake，支持 disabled 和 OpenAI-compatible `/embeddings` adapter。真实 embedding provider preflight 因 `APP_RAG_EMBEDDING_*` 必要环境变量缺失而 BLOCKED，未发起真实 HTTP。
- T067 已把 RAG context 以 feature flag 接入 QA execute path：`app.rag.qa.enabled=false` 默认关闭；开启且召回成功时注入受限 RAG context；异常或空召回 fallback 普通 QA。
- QA answer cache 在 RAG context used 时加入 topK、maxContextChars 和 context hash，避免不同上下文复用错误缓存。
- 当前仍未真实接 Qdrant / Redis Vector、LangChain4j、Spring AI、数据库表或 docker-compose 服务；T010 / MQ blocked 未处理。

## 14. T068 真实 embedding preflight 状态

T068 已重新检查真实 embedding provider 必要环境变量，当前 `APP_RAG_EMBEDDING_PROVIDER=False`、`APP_RAG_EMBEDDING_BASE_URL=False`、`APP_RAG_EMBEDDING_MODEL=False`、`APP_RAG_EMBEDDING_API_KEY=False`。

结论：真实 embedding runtime 仍为 BLOCKED，未发起 `/embeddings` HTTP 调用。后续 QA RAG smoke 继续基于 fake embedding + in-memory vector store 验证 feature flag 链路；不能写成真实向量 RAG 已完成。

## 15. T069 / T070 / T071 最新状态

- T069 已新增 QA RAG fake smoke，使用 fake embedding + in-memory vector store 验证 `app.rag.qa.enabled=true` 时可注入受限 context、生成 citation metadata，并确认 flag=false 默认行为不变。
- T070 已新增内部脱敏 `RagQaTrace` / `RagQaTraceFormatter`，用于展示 ragEnabled、embeddingProvider、vectorStoreType、topK、retrievedCount、contextHashPresent、fallbackUsed、fallbackReason、citationCount、cacheKeyRagAware 等摘要；不输出正文、prompt、chunk 全文或 secret。
- T071 已新增 `docs/RAG_VECTOR_STORE_ADAPTER_DESIGN.md`，明确下一步真实向量库 adapter 边界；推荐优先 Qdrant，Redis Vector 仅在 Redis Stack 可用时作为备选。
- 当前仍未真实接 Qdrant / Redis Vector、LangChain4j、Spring AI、数据库表或 docker-compose 服务；T010 / MQ blocked 未处理。

## 16. T072-T075 demo / trace / lifecycle / skeleton 状态

- T072 已新增 `backend/scripts/rag/demo-rag-qa-fake.ps1`，用于在已启动且显式开启 `app.rag.qa.enabled=true` 的后端上输出脱敏 RAG trace 摘要；脚本不输出 answer、文档正文、prompt、provider response、Authorization 或 secret。
- T073 已让 Agent RAG step / smoke 输出脱敏 trace-style 摘要，覆盖 embeddingProvider、vectorStoreType、topK、retrievedCount、contextHashPresent、fallbackUsed、fallbackReason 和 citationCount。
- T074 已新增 in-memory index lifecycle tracking：同一 documentId / documentVersion / contentHash 可跳过重复 index，版本或内容变化会重建，不同 documentId 隔离；trace 可展示 `indexReused=true/false`。
- T075 已新增 `app.rag.vector-store.provider=in_memory|qdrant_disabled`、`RagVectorStoreProperties`、`VectorStoreFactory` 和 `DisabledQdrantVectorStore`。默认仍为 in-memory，`qdrant_disabled` 不发 HTTP，只用于 adapter 边界和配置选择测试。
- T077 已补 VectorStore contract tests，覆盖默认 provider、in-memory 检索隔离、`qdrant_disabled` 本地 disabled 语义和未知 provider fail-fast。
- T078 已新增 Qdrant payload mapping，覆盖 upsert / search JSON、userId + documentId filter 和 response parser，不发 HTTP。
- T079 已新增默认关闭的 `QdrantVectorStore` HTTP adapter；只有显式 `app.rag.vector-store.provider=qdrant` 且 endpoint 配置齐全时才会发请求，默认仍为 `in_memory`。测试只使用 JDK 本地 fake HTTP server。
- T080 已新增 `backend/scripts/rag/preflight-qdrant-vector-store.ps1`，用于脱敏检查 Qdrant 环境是否齐全；缺环境时 SKIPPED / BLOCKED，不读取 `.env`，不输出 endpoint / API key / response body。
- 当前没有新增公开 API、数据库表、Maven 依赖或 docker-compose 服务；未接 LangChain4j / Spring AI；未启动真实 Qdrant；真实 embedding runtime 仍因 `APP_RAG_EMBEDDING_*` 缺失保持 BLOCKED；T010 / MQ 仍 BLOCKED。

## 17. T082-T086 Qdrant adapter 链路验证状态

- T082 已统一 Qdrant 配置 / 环境变量命名，推荐变量为 `RAG_VECTOR_STORE_PROVIDER`、`RAG_QDRANT_ENDPOINT`、`RAG_QDRANT_API_KEY`、`RAG_QDRANT_COLLECTION`、`RAG_QDRANT_CONNECT_TIMEOUT_MS`、`RAG_QDRANT_REQUEST_TIMEOUT_MS`；默认 provider 仍是 `in_memory`。
- T083 已确认 RAG 主链路通过 `VectorStore` 抽象运行，`RagIndexService`、`RagRetrievalService`、`RagQaContextBuilder` 和 `DocumentRagTool` 可用注入的 store 完成 index / search。
- T084 已用 JDK 本地 fake HTTP server 验证 `QdrantVectorStore` 的 upsert / search 请求形态、payload metadata、userId + documentId filter 和 topK parser。
- T085 已用 fake Qdrant server 验证 QA RAG context 能在显式 `provider=qdrant` 时通过 Qdrant adapter 返回召回结果，trace 可展示 `vectorStoreType=qdrant`。
- T086 已补 Qdrant 故障 fallback：HTTP 500、timeout、disabled、空结果和 Agent rag_tool 失败均不会破坏默认 QA / Agent 体验；fallback reason 只保留脱敏摘要。
- 当前仍没有启动真实 Qdrant，没有新增 API / DB / Maven 依赖 / docker-compose，没有接 Redis Vector、LangChain4j 或 Spring AI；真实 Qdrant runtime 仍需要用户提供服务和环境变量后再验证。

## 18. T088-T092 retrieval hardening / eval 状态

- T088 已新增可配置 `RagChunkingPolicy`、`RagChunker` 和 `RagChunkMetadata`，支持 `maxChunkChars`、`overlapChars`、`maxChunksPerDocument`、稳定 chunkId、contentHash / chunkHash、documentVersion、chunkIndex、startOffset / endOffset 和 `indexTruncated` trace。
- T089 已强化检索隔离：`RagSearchScope` 要求 userId + documentId，in-memory store 按双条件过滤，Qdrant search payload 强制携带 userId + documentId filter，缺少 scope 时 fail fast。
- T090 已新增内部脱敏 `RagDebugSnapshot` / `RagDebugReporter`，只输出 enable、provider、topK、retrievedCount、chunkCount、index / context / fallback / citation / cache key 等白名单字段。
- T091 已补 Qdrant collection preflight 边界：新增 collection info / create payload builder，preflight 脚本默认只读，只有显式允许时才尝试 create。
- T092 已新增离线 retrieval eval cases：默认用 fake embedding + in-memory vector store 统计 total、hitCount、missCount、hitRate、averageRetrievedCount，并用本地 fake Qdrant server 覆盖一条 adapter eval。
- 当前默认 provider 仍为 `in_memory`，Qdrant 仍默认关闭；未启动真实 Qdrant，未修改 docker-compose，未新增公开 API / DB / Maven 依赖，未接 Redis Vector、LangChain4j 或 Spring AI。

## 19. T100 offline vector store demo 状态

- T100 新增 `backend/scripts/rag/run-rag-vector-store-offline-demo.ps1`，默认运行 JUnit 离线 demo 并打印 `target/rag-demo/rag-vector-store-offline-demo-summary.json`。
- demo 覆盖 fake embedding 稳定性、in-memory index / retrieve、本地 fake Qdrant server 的 upsert / search，以及 Qdrant HTTP error fallback reason。
- 输出只包含聚合布尔和计数字段，例如 embeddingStable、embeddingDimension、retrievedCount、qdrantUpsertObserved、qdrantSearchObserved、qdrantFallbackReason；不输出文档正文、prompt、endpoint 原文、Authorization、API key 或 provider response。
- 该 demo 不读取 `backend/.env`，不依赖真实 provider，不连接真实 Qdrant，不新增 API / DB / Maven 依赖 / docker-compose。

## 20. T101 offline retrieval evaluation artifact 状态

- T101 新增 `backend/scripts/rag/run-rag-evaluation-artifact.ps1` 和 `RagRetrievalEvaluationArtifactTest`，可生成 `docs/ai-dev/benchmarks/rag/offline-retrieval-evaluation.json` 与 `.md`。
- artifact 覆盖 fake embedding + in-memory positive hit rate、no-match query、empty document、多 documentId isolation、本地 fake Qdrant retrieval hit rate 和 Qdrant HTTP error fallback row。
- artifact 只保存 provider、case counts、positiveHitRate、averageRetrievedCount、noMatchPassed、emptyDocumentPassed、isolationPassed、fallbackReason、failedCaseIds 等摘要字段，不保存 synthetic 文档正文、prompt、endpoint 原文、Authorization、API key 或 provider response。
- 验证已通过 `mvn "-Dtest=*Rag*Evaluation*" test`、`mvn "-Dtest=*Rag*" test` 和 `mvn test -DskipITs`；当前仍未真实调用 provider，未真实连接 Qdrant。

## 21. T102 RAG QA trace interview summary 状态

- T102 在 `RagQaTraceFormatter` 上新增 `toInterviewSafeMap` / `formatInterviewSummary`，复用既有 `RagQaTrace` 字段输出更适合展示的脱敏摘要。
- interview summary 字段固定为 ragEnabled、embeddingProvider、vectorStoreType、topK、retrievedCount、contextHashPresent、contextTruncated、fallbackUsed、fallbackReason、citationCount、indexReused、cacheKeyRagAware。
- 该能力不新增公开 API，不改变 QA / Agent routing，只是内部 formatter 增强；测试确认不输出文档正文、prompt 或多余上下文字段。
- 验证已通过 `mvn "-Dtest=*Rag*" test`、`mvn "-Dtest=DocumentQaServiceImplTest" test` 和 `mvn test -DskipITs`。
