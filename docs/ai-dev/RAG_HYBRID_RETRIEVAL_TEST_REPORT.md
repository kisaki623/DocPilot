# Hybrid Retrieval Repair Report

日期：2026-06-26

## 本轮修复

- 修复 Hybrid keyword 检索未按 `indexVersion` 过滤的问题。
- 修复 fused hit 硬编码 `indexVersion=1` 的问题。
- 修复 keyword-only fused hit 缺失 `contentHash`、offset、tokenCount、embeddingModel 等 citation 元数据的问题。
- 修复 BM25 scorer 在 Spring singleton service 中共享可变 corpus 状态的问题。
- 修复 `APP_RAG_RETRIEVAL_RRF_K` 配置未生效的问题。
- 将 rerank 真正接入 KnowledgeBase RAG 主链路：候选召回 / hybrid fusion 后、最终多样性选择前执行。
- 为 rerank HTTP provider 补充 timeout 配置和无敏感信息 fallback 日志。
- 前端 KnowledgeBase 类型与页面展示已同步 `retrievalMode`、`rerankApplied`、`rerankModel` 和 score breakdown。

## 已验证

```powershell
cd backend
mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest,ConversationMessageServiceImplTest,ReciprocalRankFusionTest,BM25ScorerTest" test
mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test

cd frontend
npm run lint
npm run build
```

结果：

- 定向后端测试：27 tests，0 failures，0 errors。
- 扩展后端回归：233 tests，0 failures，0 errors。
- 前端 lint：PASS。
- 前端 build：PASS。

## 当前边界

- 默认配置仍关闭 hybrid 和 rerank。
- rerank provider 失败时降级为 identity，不保证真实 provider 一定可用。
- 本轮未操作远程服务器、远程 Qdrant、远程 MySQL 或真实外部模型。
- 本轮没有新增数据库迁移。
