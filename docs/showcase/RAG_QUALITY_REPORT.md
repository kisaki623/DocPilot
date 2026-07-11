# DocPilot RAG 质量报告（求职展示版）

更新时间：2026-07-11

## 结论

DocPilot 当前已经达到“后端 / RAG 实习或初中级岗位求职展示级”的 RAG 工程闭环：它不是只把文档全文塞进 prompt 的 demo，而是覆盖了文档解析、结构化 chunk、embedding、Qdrant 检索、metadata 权限过滤、KnowledgeBase 多文档 RAG、no-evidence、grounded QA、citation、Conversation Trace / Memory 和真实链路质量门禁。

需要克制的边界也很明确：它不是完整商业 SaaS、不是线上 SLA 系统、不是 OCR / 复杂 PDF 版面理解系统，也不是大规模 relevance benchmark。rerank 当前已有代码链路和安全诊断，但最新真实 provider 对照仍是 REVIEW：核心 RAG 无回退，真实 rerank model 返回 NotFound，服务降级为 identity，不能宣称真实 rerank 效果已验证。

## 组件成熟度

| 组件 | 当前实现 | 求职级判断 |
| --- | --- | --- |
| Document Parser | 支持 txt / md、文本型 PDF、HTML、DOCX；`ParseResult.DocumentBlock` 保留 `pageNumber`、`sectionPath`、`sourceLocator`、`blockType`、offset。 | 求职级偏强；不覆盖 OCR、扫描件、旧 `.doc`、复杂版面。 |
| Chunking | `ChunkingServiceImpl` 按文本块 pack，支持 chunk size / overlap，生成 hash、offset、section path、source block、quality flags。 | 求职级强；不是简单固定字符切片。 |
| Embedding | `EmbeddingProvider` 抽象支持 mock 与 OpenAI-compatible provider；索引时做批量 embedding、维度和数量校验。 | 求职级够用偏强；成本治理和限流仍不是生产级。 |
| Vector DB | Qdrant client 支持 collection ensure、upsert、search、delete；payload 带 userId / documentId / indexVersion / chunk / locator metadata。 | 求职级强；metadata filter 和二次 scope check 是核心亮点。 |
| Hybrid / Multi-query | KnowledgeBase RAG 支持 rule-based query rewrite、多 query merge、BM25 + vector + RRF。 | 求职级偏强；默认可配置，不是完整 ES 生产检索平台。 |
| Rerank | 有 `RerankService` / `HttpRerankService`，支持 Cohere / OpenAI-compatible，失败 identity fallback；本轮新增 `rerankFailureReason` 安全诊断。 | 代码链路求职级，真实效果当前 REVIEW；需 provider/model 可用后再声称生效。 |
| Context Packing | `ContextAssemblyServiceImpl` 统一拼接 system、mode、summary、memory、recent turns、KB evidence、current message，并做权限过滤、token budget、trace。 | 求职级强；体现 RAG + 会话记忆融合。 |
| Citation | 单文档 citation 已有 quote、chunk、offset、section/page/source locator；本轮补齐 KnowledgeBase citation / hit / Agent tool 的 locator 字段。 | 求职级强；仍不是 PDF 坐标级引用。 |
| No-evidence / Grounding | KnowledgeBase QA 无证据时跳过模型，返回 no-evidence；有证据才构造 prompt 调模型，并做 answer-aware citation pruning。 | 求职级强；强调“不让模型凭记忆硬答”。 |
| Eval / Smoke | 有离线单测、脚本安全测试、真实链路 smoke、artifact redaction、Quality Console 聚合。 | 求职展示很加分；仍不是大规模 benchmark。 |

## 最新证据

- 全量后端基线：`mvn test -DskipITs` PASS（912 tests，0 failures，5 skipped，本轮复验）。
- 真实用户链路：`docpilot-real-user-qa-20260711170544-dff948` PASS。
- 真实 RAG QA：`docpilot-rag-real-qa-20260711171137-ed38a0` PASS。
- Parser 真实链路：`docpilot-parser-real-chain-20260711171912-a8e65c` PASS，source locator `3/3`、parser boundary `4/4`。
- Memory provider：`docpilot-memory-provider-20260711172435-14083e` PASS，6 calls、`casePassRate=1.0000`、`rawProviderOutputStored=false`。
- Agent quality：`docpilot-agent-quality-eval-20260711171903-fae364` PASS。
- Rerank 对照：`docpilot-rerank-effect-hybrid-20260711194601-2623f5` / `docpilot-rerank-effect-rerank-20260711194743-d98021` REVIEW，`rerankFailureReason=provider_not_found`，核心 RAG、no-evidence 和权限安全无回退，但真实 rerank provider 当前未生效。
- 本轮定向回归：`KnowledgeBaseRagRetrievalServiceImplTest`、`KnowledgeBaseSearchToolTest`、`KnowledgeBaseAgentServiceImplTest`、`HttpRerankServiceTest`、`RerankEffectSmokeScriptSafetyTest` 共 34 tests PASS。
- 本轮全量后端回归：`mvn test -DskipITs` PASS（912 tests，0 failures，5 skipped）。

## 面试讲法

可以这样讲：

> 我做的是一个企业文档知识库 RAG 核心闭环。文档上传后走异步解析，parser 会保留 page、block、section path 和 source locator；chunk 后持久化到 MySQL，并把 metadata 写入 Qdrant payload。检索时用 userId、documentId / documentIds、indexVersion 做 metadata filter 和 service 二次校验，KnowledgeBase RAG 支持多文档检索、multi-query、hybrid search、no-evidence gate、grounded QA 和 quote-level citation。会话模式下，RAG evidence 会和 summary、recent turns、memory 一起进入统一 context packing，并生成 trace，方便质量回归和问题定位。

不要这样讲：

- 不要说“完整商业 SaaS / 线上 SLA / 大规模多租户计费”。
- 不要说“支持复杂 PDF 智能解析、OCR、扫描件坐标级 citation”。
- 不要说“真实 rerank 效果已验证”，除非后续 provider/model 修复后跑出 `rerankApplied=true` 的真实 smoke。
- 不要把 smoke / eval 写成大规模 relevance benchmark；当前更准确的说法是“小样本真实链路质量门禁 + 离线回归”。

## 下一步优先级

1. 修正本机真实 rerank provider/model 配置，复跑 `rerank-effect-smoke.ps1 -Mode run`，目标是 `rerankApplied=true` 且 hard fixture 无核心回退。
2. 在前端 KnowledgeBase / Agent 展示中选择性呈现 page / section / source locator，避免用户只看到 chunk id。
3. 扩展代表语料质量报告，把 hit、citation、no-evidence、answer faithfulness 和 distractor suppression 形成一页可读趋势摘要。
