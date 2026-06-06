# Current Task

当前任务：KnowledgeBase RAG 问答质量修复

## 目标

修复“总结整个资料集”类问题中，多文档知识库虽然有 4 个成员文档和 6 条 evidence，但召回几乎被单一文档垄断、chunk 过短、回答模型无法总结整个资料集的问题。

## 本轮已完成

- 后端 chunking 从“短段落直接成 chunk”改为先合并 Markdown / 文本块，再按窗口切分，默认 chunk size 调整为 `800`、overlap 调整为 `120`。
- KnowledgeBase retrieval 扩大向量候选池，对外仍保留请求 `topK`；摘要 / 资料集 / 知识库类问题优先覆盖每个文档，并限制单文档命中数。
- KnowledgeBase retrieval response 新增 `documentHitCounts`，用于观察每个文档的最终命中数量。
- KnowledgeBase QA response 新增 `answerProvider`、`answerModel`、`modelCallCount`，用于确认是否真实调用回答模型。
- KnowledgeBase summary prompt 增加“整体总结 + 按文档标题总结 + 缺失文档证据需说明”的提示。
- RAG vector store 配置兼容 `RAG_VECTOR_PROVIDER` / `RAG_VECTOR_DIMENSION` 别名；未把误用的 `RAG_VECTOR_COLLECTION=http://...` 当 endpoint。
- 前端 KnowledgeBase API 类型已同步新增 response 字段。
- 已按用户授权对目标 KnowledgeBase 文档 `83/84/85/86` 执行 rebuild / reindex：写入 Qdrant collection `docpilot_kb_quality_20260606`，KnowledgeBase id 为 `3`，userId 为 `21`。

## 已验证

```powershell
cd backend
mvn "-Dtest=ChunkingServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagPromptBuilderTest,RagVectorStorePropertiesTest,KnowledgeBaseRagControllerTest" test
mvn -DskipTests compile
mvn "-Dtest=*Rag*" test

cd frontend
npm run lint
```

授权后的运行时 reindex 验证：

```powershell
cd backend
mvn "-Dtest=ManualKnowledgeBaseRagReindexTest" "-Dspring.profiles.active=local" test
```

验证结果：

- targeted backend tests：36 tests，0 failures，0 errors。
- backend `*Rag*` tests：164 tests，0 failures，0 errors。
- backend compile：PASS。
- frontend lint：PASS。
- runtime reindex：document `83/84/85/86` rebuild 成功，chunk / vector 数分别为 `35/35`、`18/18`、`10/10`、`16/16`；“总结资料集”检索 hit 数为 `6`，`documentHitCounts={83:2,84:1,85:1,86:2}`。

## 当前边界

- 本轮没有操作远程 / 云端 MySQL、Qdrant 或服务进程。
- 已通过 Spring service 正式执行 rebuild / reindex，没有直接手写 SQL 或直接改 Qdrant payload。
- 当前 `.env` 仍需要使用 `RAG_QDRANT_ENDPOINT` / `RAG_QDRANT_COLLECTION=docpilot_kb_quality_20260606` 这类新字段或等价进程环境，才能让后端运行时读取到本次重建后的 collection；本轮没有提交真实 `.env`。
- 如果当前环境仍使用 mock / fake embedding，语义召回质量仍会受限；本轮代码只让 provider/model/call count 更可观测。

## 下一步候选

- 前端展示 `documentHitCounts`、`answerProvider`、`answerModel`、`modelCallCount`，便于演示时解释检索和模型调用。
- 为 KnowledgeBase QA 补 SSE 流式路径，并保持与非流式 response 字段一致。
