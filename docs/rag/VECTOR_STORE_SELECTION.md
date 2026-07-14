# Vector Store Selection for Minimal RAG

本文档记录 DocPilot 最小 RAG 的向量库选型。T053 只做方案比较和落地建议，不修改 docker-compose、不修改配置、不实现代码。

## 1. 选型目标

DocPilot 当前已有轻量文档问答、citations、Agent QA tool 和执行轨迹。下一阶段最小 RAG 需要一个可测试、可解释、可展示的向量召回层。

选型目标按优先级排序：

1. 快速支撑求职展示，能在 30-90 分钟任务粒度内逐步落地。
2. Java / Spring Boot 接入简单，测试可控。
3. 不把 demo 能力误写成生产能力。
4. 后续可以自然演进到更完整的 RAG 工程化方案。

## 2. 方案对比

| 方案 | 接入成本 | 本地 / 远程中间件成本 | Java / Spring Boot 复杂度 | 快速求职展示 | 后续生产化 | 测试难度 | 面试解释难度 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Qdrant | 中等，需要新增服务和 client 封装 | 需要新增 Docker 服务或远程实例 | 中等，HTTP API 简洁但要写 client / DTO | 强，向量库故事清晰 | 强，适合专用向量检索 | 中等，可用 test container 或 fake client | 低，面试官容易理解 |
| Redis Vector / Redis Stack | 中等，前提是 Redis Stack 可用 | 如果当前 Redis 不是 Stack，需要替换或新增服务 | 中等，需要 RediSearch / vector index 语义 | 中等，复用 Redis 叙事好 | 中等，适合轻量场景 | 中等，环境差异要控制 | 中等，要解释 Redis 与 Redis Stack 差异 |
| MySQL fallback | 低到中等，不需要新中间件 | 无新增中间件 | 低，可先存 chunk 和 embedding metadata；真实向量相似度较弱 | 中等，适合先证明数据闭环 | 弱到中等，不适合作为最终向量检索核心 | 低，最容易写集成测试 | 中等，要诚实说明不是专业向量检索 |
| in-memory fake vector store | 低，无外部依赖 | 无中间件 | 低，纯 Java 接口和测试替身 | 强，最快证明链路和截图 | 弱，仅限测试 / demo | 低，最稳定 | 低，但必须说明是 fake，不是生产方案 |

## 3. 方案分析

### Qdrant

优点：

- 专用向量数据库，RAG 面试叙事最直接。
- HTTP API 清晰，payload 可以保存 documentId、userId、chunkIndex、score 所需信息。
- 后续可以扩展 collection、filter、payload schema 和 distance metric。

缺点：

- 需要新增 Docker / 远程服务和运行配置。
- 需要新增 client 封装、错误处理、健康检查和测试替身。
- 当前用户未确认是否允许改 docker-compose 或引入远程向量库。

适合：T054 后半段或 T055 前，用户确认可新增服务后接入。

### Redis Vector / Redis Stack

优点：

- DocPilot 已经使用 Redis / Redisson，概念上容易复用现有中间件叙事。
- 对轻量 demo 和小规模检索够用。
- 可以把缓存、限流和向量索引放在同一中间件族里讲。

缺点：

- Redis Vector 依赖 Redis Stack / RediSearch，不能假设当前普通 Redis 已支持。
- 本地、远程和 CI 环境容易出现版本差异。
- Java 侧 vector index 建模和查询语法比 in-memory fake 更重。

适合：如果用户确认远程或本地 Redis Stack 已可用，可作为求职展示加速方案。

### MySQL fallback

优点：

- 不新增中间件，最符合当前稳定性要求。
- 适合先持久化 `document_chunk`、metadata、hash、version 和 embedding 状态。
- 可以支撑“chunk 管理 + citation mapping + 重建索引”的工程化基础。

缺点：

- MySQL 不适合作为真正向量相似度检索核心。
- 如果只做关键词 / LIKE / 简单 score，不能写成完整向量 RAG。
- 后续接 Qdrant / Redis Vector 时仍需要迁移检索层。

适合：T054 先落 chunk 表和可解释 fallback 时使用。

### in-memory fake vector store

优点：

- 最快，纯 Java 实现即可。
- 不需要配置、不需要远程中间件、不需要真实 embedding。
- 非常适合单元测试、演示链路、前端截图前的稳定验证。

缺点：

- 不能跨进程持久化。
- 不能作为生产方案。
- 必须在文档和面试中标注 fake / demo / test boundary。

适合：T054 第一阶段，先把接口、测试、citation mapping 和 Agent QA tool 兼容性打通；T055 已将该方案用于 Agent Showcase 的 RAG 召回展示。

## 4. 推荐路线

### 求职冲刺优先方案

推荐先采用：

```text
fake embedding + in-memory fake vector store + document_chunk 设计草案
```

理由：

- 最快形成可测闭环，不依赖新中间件。
- 能证明 RAG 工程拆分：chunk、embedding、retrieve、answer、citations。
- 适合先让后端测试和前端展示稳定，再决定真实向量库。
- 不会因为 Qdrant / Redis Stack 环境问题拖慢投递节奏。

### 后续工程化方案

推荐后续采用：

```text
Qdrant as primary vector store + MySQL document_chunk metadata + fake store for tests
```

理由：

- Qdrant 是专用向量数据库，面试表达清晰。
- MySQL 继续负责 chunk 元数据、hash、版本和重建记录。
- in-memory fake 保留为单元测试替身，避免测试依赖真实服务。

Redis Vector 可作为备选：如果用户已经有 Redis Stack 环境并希望减少中间件数量，可优先评估 Redis Vector；否则不要把普通 Redis 误写成已支持向量检索。

## 5. T054 前置确认项

进入 T054 代码实现前需要用户确认：

1. 是否允许新增 docker-compose 服务，例如 Qdrant。
2. 是否允许使用远程中间件，还是只做本地 fake。
3. embedding provider 使用 fake 还是真实模型。
4. 是否允许新增 `document_chunk` / `chunk_embedding` 表或迁移脚本。
5. 是否允许新增后端内部 service 和测试资源。
6. 是否先不改公开 API，只在 service / test 层打通链路。

## 6. 面试口径

可以这样讲：

- “当前项目已经有轻量检索增强和 citations；下一步 RAG 我会先用 fake embedding 和 in-memory vector store 打通链路，再接 Qdrant。”
- “当前 Agent Showcase 已能展示 fake embedding + in-memory vector store 的 retrieved chunks、score 和 citation metadata，但我会明确说明这不是生产级向量库方案。”
- “我不会一上来引入复杂框架，因为先把 chunk、embedding、retrieve、citation mapping 和测试边界拆清楚更重要。”
- “MySQL 适合保存 chunk metadata，但不把它包装成专业向量库。”
- “Redis Vector 需要 Redis Stack 支持，不等同于普通 Redis。”
- “Qdrant 是后续工程化的推荐方案，但需要用户确认新增服务和部署方式。”

## 7. 本任务明确未做

- 未修改 docker-compose。
- 未修改配置文件。
- 未新增后端 API。
- T063 已实现 embedding adapter 代码路径，但真实 embedding runtime 尚未验证。
- 未实现向量库。
- 未修改数据库 DDL。
- 未修改 production routing。

## 8. 2026-05-20 更新

- 当前已完成 fake / disabled / OpenAI-compatible embedding adapter 架构，默认仍走 fake embedding，真实 embedding provider runtime 因环境变量缺失 BLOCKED。
- 当前已完成默认关闭的 QA RAG context feature flag，开启后可用 fake embedding + in-memory vector store 给 QA 注入受限 context；默认 QA 行为不变。
- 选型结论不变：仍未接 Qdrant / Redis Vector；如果后续进入真实向量库，应先确认是否允许新增服务、DDL 和部署配置。

## 9. T071 adapter 边界更新

T071 新增 `docs/RAG_VECTOR_STORE_ADAPTER_DESIGN.md`，仅设计下一步 adapter 边界，不实现真实向量库。

当前推荐保持：

- 求职冲刺若要接真实向量库，优先 Qdrant：专用向量库语义清晰，collection / payload filter / score / point id 更适合讲 RAG 工程化。
- Redis Vector 作为备选：只有确认 Redis Stack / RediSearch 可用时再评估；不能把当前普通 Redis 写成已支持向量检索。
- in-memory fake store 继续作为单元测试和 smoke 替身。

T071 未新增依赖、未改 docker-compose、未新增数据库表、未接 Qdrant / Redis Vector、未接 LangChain4j。

## 10. 2026-05-21 T072-T080 更新

- T072 已新增脱敏 RAG QA demo 脚本，便于在显式开启 `app.rag.qa.enabled=true` 的后端上展示 fake embedding + in-memory vector store 的 RAG QA trace 摘要。
- T073 已让 Agent RAG step / smoke 输出脱敏 trace 摘要，避免只看到 retrieved chunks 而看不到 RAG 过程证据。
- T074 已新增 in-memory index lifecycle tracking：同一 documentId / documentVersion / contentHash 可跳过重复 index，版本或内容变化会重建，不同 documentId 隔离。
- T075 已新增 Qdrant vector store disabled skeleton：`app.rag.vector-store.provider` 默认仍为 `in_memory`，可选 `qdrant_disabled`；该模式不发 HTTP，只用于配置边界和 factory 选择测试。
- T077 已补 VectorStore contract tests，锁定默认 `in_memory`、in-memory 检索隔离、`qdrant_disabled` 本地失败和未知 provider fail-fast。
- T078 已新增 Qdrant payload mapping，覆盖 upsert / search JSON、userId + documentId filter 和 response parser；该层不发 HTTP。
- T079 已新增默认关闭的 Qdrant HTTP adapter：显式 `provider=qdrant` 且 endpoint 配置齐全时才会发请求，默认仍为 `in_memory`；测试只使用 JDK 本地 fake HTTP server，未启动真实 Qdrant。
- T080 已新增脱敏 preflight 脚本，缺环境时 SKIPPED / BLOCKED，不输出 endpoint 原文、API key、Authorization 或响应体。
- 选型结论保持克制：当前可以讲“已有 fake embedding + in-memory vector store 的 RAG demo、QA RAG feature flag、RAG trace、index lifecycle、默认关闭的 Qdrant HTTP adapter 和 preflight”；不能写成真实 embedding / 真实 Qdrant runtime / 生产完整 RAG 已完成。

## 11. 2026-05-21 T082-T086 更新

- T082 已统一 Qdrant 配置 / 环境变量命名，默认 provider 仍为 `in_memory`，显式 `RAG_VECTOR_STORE_PROVIDER=qdrant` 才会进入 Qdrant HTTP adapter。
- T083 已确认 RAG 主链路通过 `VectorStore` 抽象运行，避免把 `RagIndexService` / `RagQaContextBuilder` / `DocumentRagTool` 主路径硬编码到 in-memory 实现。
- T084 已用 JDK 本地 fake HTTP server 验证 `QdrantVectorStore` 的 index / search 请求形态、payload metadata 和 userId + documentId filter。
- T085 已用 fake Qdrant server 验证 QA RAG context 在 `provider=qdrant` 时可通过 adapter 返回召回结果。
- T086 已补 Qdrant HTTP error、timeout、disabled、空结果和 Agent rag_tool 失败的 fallback 测试，确保默认 QA / Agent 体验不被向量库故障破坏。
- 选型结论不变：Qdrant 是后续真实向量库优先方案，但当前只完成默认关闭 adapter 和本地 fake server 链路验证；没有启动真实 Qdrant，没有改 docker-compose，没有新增 Maven 依赖、DB 表或公开 API。

## 12. 2026-05-21 T088-T092 更新

- T088 已把 chunk 切分收敛为可配置 policy：支持 `maxChunkChars`、`overlapChars`、`maxChunksPerDocument`、稳定 chunkId、offset 和 hash metadata。
- T089 已强化 retrieval isolation：所有主链路 search 都应携带 userId + documentId scope，in-memory 与 Qdrant payload 均有对应测试。
- T090 已新增脱敏 debug snapshot / reporter，只输出 RAG 链路摘要字段，不输出正文、prompt、endpoint、Authorization 或 provider response。
- T091 已补 Qdrant collection lifecycle 边界：可构造 info / create payload，preflight 默认只读，不自动创建 collection。
- T092 已新增离线 retrieval eval cases，用 fake embedding + in-memory 做默认评测，并用本地 fake Qdrant server 覆盖一条 adapter eval。
- 选型结论仍保持克制：默认 provider 是 `in_memory`，Qdrant adapter 仍默认关闭；当前没有启动真实 Qdrant、没有改 docker-compose、没有新增 API / DB / Maven 依赖，也没有接 Redis Vector、LangChain4j 或 Spring AI。

## 13. 2026-05-21 T095 更新

- Qdrant adapter 仍是默认关闭能力，只有显式 `provider=qdrant` 且 endpoint 齐全才会发 HTTP；默认 provider 仍为 `in_memory`。
- 本轮补强了 Qdrant payload / filter / fallback 测试：search payload 必须包含 userId + documentId filter，HTTP 500 / 缺 endpoint 错误不输出 endpoint、Authorization、API key 或 response body。
- Qdrant payload 的 metadata/citation 采用白名单字段，不把正文、prompt 或 provider response 类字段写入 metadata；正文仅作为受控 retrieval text 存在于 payload，用于 adapter 解析测试。
- 当前仍未启动真实 Qdrant，未改 docker-compose，未新增 API / DB / Maven 依赖，未接 Redis Vector、LangChain4j 或 Spring AI。
