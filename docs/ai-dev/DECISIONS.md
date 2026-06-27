# 关键技术决策（ADR 简版）

## D01 认证主口径
- 决策：账号密码注册/登录为主；短信验证码保留兼容接口。
- 原因：更适合公开演示与可复现，不依赖短信网关。
- 影响：登录页默认注册模式；后端保留 `/api/auth/code` 与 `/api/auth/login`。

## D02 解析异步化
- 决策：`task/parse/create` 采用 RocketMQ 异步消费。
- 原因：降低接口阻塞，便于失败重试与状态机扩展。
- 影响：需要维护 Outbox 与消费幂等。

## D03 一致性与幂等
- 决策：Outbox 补偿 + 消费记录去重 + Redisson 锁（WatchDog）。
- 原因：应对“落库成功/发消息失败”和重复消费、并发重复创建。
- 影响：增加补偿扫描与锁治理复杂度，但可靠性显著提升。

## D04 存储策略
- 决策：保留 `local/minio` 双模式，并支持分片上传会话。
- 原因：兼顾本地开发与演示环境可用性。
- 影响：需维护存储模式切换配置与上传会话校验。

## D05 AI 模式策略
- 决策：默认 `AI_MODE=mock`，`real` 模式通过 OpenAI 兼容协议接入。
- 原因：保证默认可跑，避免外部依赖阻塞主链路。
- 影响：对外文案必须明确“real 模式需额外配置”。

## D06 问答治理
- 决策：问答使用 Redis 令牌桶限流，叠加缓存与会话上下文。
- 原因：保护高成本接口并稳定体验。
- 影响：需维护限流参数和缓存命中边界。

## D07 量化口径
- 决策：benchmark 仅用于本地相对对比，不作为线上 SLA。
- 原因：避免夸大；保留可复现实证价值。
- 影响：README/面试话术必须写明边界。

## D-RAG-01 RAG 主链路自研
- 决策：RAG 主链路自研，不优先接 LangChain4j 全家桶。
- 原因：当前目标是展示 Java 后端工程能力，包括 chunk、embedding、retrieval、citation、fallback、trace 和测试边界。
- 影响：后续可以预留 adapter，但不要让框架掩盖核心链路。

## D-RAG-02 向量库选择 Qdrant
- 决策：向量库选择 Qdrant。
- 原因：Qdrant 的 collection、payload filter、score、point id 和 HTTP API 语义清晰，适合讲 RAG 工程化链路。
- 影响：Redis Vector 仅作为 Redis Stack 可用时的备选，不把普通 Redis 写成向量检索能力。

## D-RAG-03 MySQL 与 Qdrant 分工
- 决策：MySQL 存 chunk 原文、状态和业务元数据；Qdrant 存向量和 payload。
- 原因：MySQL 适合审计、重建索引、citation 回查和业务状态；Qdrant 负责向量相似度检索。
- 影响：RAG indexing 需要保持 `document_chunk` 和 Qdrant point 的版本、hash、状态一致。

## D-RAG-04 EmbeddingProvider 抽象
- 决策：EmbeddingProvider 使用接口抽象，支持 mock 和 OpenAI-compatible provider。
- 原因：测试和本地开发需要稳定 fallback，真实 provider 需要配置隔离且不能提交 key。
- 影响：mock provider 只能作为测试和展示边界，不能写成最终求职级 RAG 目标。

## D-RAG-05 RAG indexing 异步化
- 决策：RAG indexing 走异步任务，避免阻塞上传、解析和问答主链路。
- 原因：chunking、batch embedding、Qdrant upsert 可能耗时且有外部依赖，应该支持 retry 和 rebuild。
- 影响：parse success 后触发 indexing，并维护 index status。

## D-RAG-06 fake / in-memory 边界
- 决策：当前 fake embedding / in-memory vector store 只作为测试和展示边界，不作为最终求职级 RAG 目标。
- 原因：它们不能代表真实 embedding、持久化向量库、跨进程索引或生产检索能力。
- 影响：对外材料前半部分不主动强调这些边界；内部 ai-dev 文档必须诚实记录。

## D-RAG-07 RAG evidence confidence gate
- 决策：RAG 回答必须经过 evidence confidence gate；低于阈值或判定无关的检索结果不能进入 grounded QA，也不能生成带 citation 的伪证据答案。
- 原因：生产化知识库 RAG 的核心不是“总能搜到最近 chunk”，而是能判断证据是否足够支撑回答；无关问题必须拒答或降级。
- 影响：单文档 RAG、KnowledgeBase RAG 和 Conversation KB evidence 应共享 no-evidence / threshold 语义；`rag-real-quality-smoke.ps1` 的 `noEvidenceThreshold` 是后续是否通过的真实链路门禁。
