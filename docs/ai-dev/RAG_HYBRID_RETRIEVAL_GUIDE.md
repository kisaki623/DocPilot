# RAG Hybrid Retrieval Guide

最后更新：2026-06-26

本文记录 KnowledgeBase RAG 的可选混合检索与 rerank 增强。当前能力适合作为求职级工程增强链路展示，不代表生产级完整 RAG 平台。

## 当前实现

- BM25 keyword retrieval：从 MySQL `tb_document_chunk` 读取当前用户、当前 KnowledgeBase 文档、指定 `indexVersion` 且 `INDEXED` 的 chunk，在内存中计算 BM25 分数。
- Hybrid retrieval：向量候选与 keyword 候选通过 Reciprocal Rank Fusion 融合，`rrfK` 读取 `APP_RAG_RETRIEVAL_RRF_K`，默认 `60`。
- Scope guard：向量命中和融合后的最终候选都会经过 KnowledgeBase scope / `indexVersion` 校验，防止跨用户、跨文档、跨版本 evidence。
- Optional rerank：`APP_RAG_RERANK_ENABLED=true` 时，KnowledgeBase RAG 会在候选融合后、最终多样性选择前调用 `RerankService`。provider disabled、调用失败或返回 identity 时自动保留原候选顺序。
- Observability：retrieval response 暴露 `retrievalMode`、`rerankApplied`、`rerankModel`，hit / citation 暴露 `vectorScore`、`keywordScore`、`fusedScore`、`rerankScore`。

## 配置

默认配置保持保守：

```bash
APP_RAG_RETRIEVAL_HYBRID_ENABLED=false
APP_RAG_RETRIEVAL_MIN_SIMILARITY_THRESHOLD=0.0
APP_RAG_RETRIEVAL_RRF_K=60

APP_RAG_RERANK_ENABLED=false
APP_RAG_RERANK_PROVIDER=disabled
APP_RAG_RERANK_CONNECT_TIMEOUT_MS=5000
APP_RAG_RERANK_REQUEST_TIMEOUT_MS=30000
```

仅启用 hybrid：

```bash
APP_RAG_RETRIEVAL_HYBRID_ENABLED=true
APP_RAG_RETRIEVAL_RRF_K=60
```

启用 OpenAI-compatible rerank：

```bash
APP_RAG_RERANK_ENABLED=true
APP_RAG_RERANK_PROVIDER=openai_compatible
APP_RAG_RERANK_BASE_URL=<local-or-compatible-endpoint>
APP_RAG_RERANK_MODEL=<rerank-model>
APP_RAG_RERANK_API_KEY=<local-secret>
```

不要把真实 endpoint、API key 或云服务器地址写入文档、README 或 git commit。

## 验证命令

```powershell
cd backend
mvn "-Dtest=*Rag*,*KnowledgeBase*,*Rerank*" test

cd frontend
npm run lint
npm run build
```

已覆盖的关键回归：

- Hybrid keyword-only hit 保留 `indexVersion`、`contentHash` 和 chunk metadata。
- Hybrid 最终候选会再次经过 KnowledgeBase scope guard。
- rerank enabled 时会改变最终候选顺序，并输出 `rerankScore` / `rerankModel`。
- rerank disabled / provider failed 时回退 identity，不破坏默认检索链路。
