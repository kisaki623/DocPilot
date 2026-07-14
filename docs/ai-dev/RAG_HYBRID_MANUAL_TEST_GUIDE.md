# Hybrid Retrieval Manual Check

最后更新：2026-06-26

本文件只记录手动验证路径，不记录本机 `.env`、远程 IP、隧道 PID、API key 或具体账号。

## 前置条件

- 后端和前端已本地启动。
- 测试账号已登录。
- KnowledgeBase 中至少有 2 个已解析并完成 RAG indexing 的文档。
- 如需验证真实 rerank，必须在本机私有 `.env` 显式配置 provider、base URL、model 和 API key；自驱真实链路验证模式下，可优先使用 `scripts/smoke/rerank-effect-smoke.ps1` 做小规模脱敏对比。

## 建议步骤

1. 打开 KnowledgeBase 页面，选择已有知识库。
2. 提问包含精确关键词的问题，例如配置键、组件名、接口名，观察 keyword / fused score 是否出现。
3. 提问语义问题，观察向量分数和引用来源是否合理。
4. 启用 rerank 后再次提问，观察 response 中：
   - `retrievalMode`
   - `rerankApplied`
   - `rerankModel`
   - `hits[].rerankScore`
   - `citations[].rerankScore`
5. 若 rerank provider 不可用，预期行为是保留原候选顺序并继续返回答案，不应导致 KnowledgeBase QA 失败。
6. 自动对比路径可运行 `scripts/smoke/rerank-effect-smoke.ps1 -Mode run`，观察 hybrid-only 与 hybrid+rerank 的 covered document count、citation count、`rerankApplied` 和 rerank score summary。

## 推荐本地验证命令

```powershell
cd backend
mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest,HttpRerankServiceTest,ReciprocalRankFusionTest,BM25ScorerTest" test

cd frontend
npm run lint
npm run build
```

## 边界

- 当前不是生产级完整 RAG 平台。
- Hybrid / rerank 只接入 KnowledgeBase RAG retrieval / QA 结果链路；单文档 RAG 和 Agent 主链路不在本次改动范围内。
- 当前小规模真实 provider smoke 只能证明 provider 可用和核心 gate 无回退；是否带来 relevance uplift 需要更难的 eval / smoke case。
