# DocPilot RAG 质量报告（求职展示版）

更新时间：2026-07-12

## 结论

DocPilot 当前已经达到“后端 / RAG 实习或初中级岗位求职展示级”的 RAG 工程闭环：它不是只把文档全文塞进 prompt 的 demo，而是覆盖了文档解析、结构化 chunk、embedding、Qdrant 检索、metadata 权限过滤、KnowledgeBase 多文档 RAG、GroundingPolicy 路由、no-evidence、grounded QA、citation、Conversation Trace / Memory 和真实链路质量门禁。

需要克制的边界也很明确：它不是完整商业 SaaS、不是线上 SLA 系统、不是 OCR / 复杂 PDF 版面理解系统，也不是大规模 relevance benchmark。rerank 当前已有代码链路、安全诊断、阿里云百炼真实 provider 调用证据、小样本 hard fixture 排序 uplift 证据，以及 12-case 代表语料真实链路 eval 证据；但仍不能把这些 bounded smoke / eval 写成稳定线上效果或大规模 ranking benchmark。

## 组件成熟度

| 组件 | 当前实现 | 求职级判断 |
| --- | --- | --- |
| Document Parser | 支持 txt / md、文本型 PDF、HTML、DOCX；`ParseResult.DocumentBlock` 保留 `pageNumber`、`sectionPath`、`sourceLocator`、`blockType`、offset。 | 求职级偏强；不覆盖 OCR、扫描件、旧 `.doc`、复杂版面。 |
| Chunking | `ChunkingServiceImpl` 按文本块 pack，支持 chunk size / overlap，生成 hash、offset、section path、source block、quality flags。 | 求职级强；不是简单固定字符切片。 |
| Embedding | `EmbeddingProvider` 抽象支持 mock 与 OpenAI-compatible provider；索引时做批量 embedding、维度和数量校验。 | 求职级够用偏强；成本治理和限流仍不是生产级。 |
| Vector DB | Qdrant client 支持 collection ensure、upsert、search、delete；payload 带 userId / documentId / indexVersion / chunk / locator metadata。 | 求职级强；metadata filter 和二次 scope check 是核心亮点。 |
| Hybrid / Multi-query | KnowledgeBase RAG 支持 rule-based query rewrite、多 query merge、BM25 + vector + RRF。 | 求职级偏强；默认可配置，不是完整 ES 生产检索平台。 |
| Rerank | 有 `RerankService` / `HttpRerankService`，支持 Cohere / OpenAI-compatible / 阿里云百炼，失败 identity fallback；已暴露 `rerankFailureReason` 安全诊断。 | 代码链路求职级；百炼 provider 已真实生效，hard fixture 小样本观察到排序 uplift，12-case 代表 eval 无 coverage / no-evidence 回退；仍不是大规模 ranking benchmark。 |
| Context Packing | `ContextAssemblyServiceImpl` 统一拼接 system、mode、summary、memory、recent turns、KB evidence、current message，并做权限过滤、token budget、trace。 | 求职级强；体现 RAG + 会话记忆融合。 |
| Citation | 单文档和 KnowledgeBase citation / hit 已有 quote、chunk、offset、section/page/source locator；文档详情、KnowledgeBase、Conversation 和 Agent 页面现在都可展示 locator / metadata。 | 求职级强；仍不是 PDF 坐标级引用。 |
| No-evidence / Grounding | KnowledgeBase QA 无证据时跳过模型，返回 no-evidence；Conversation 路由拆成 `MODEL_ONLY / AUTO_RAG / STRICT_KB`，未绑定 KB 不触发资料不足拒答，`AUTO_RAG` 无证据 fallback 到模型，显式 `STRICT_KB` 才安全拒答；有证据才构造 grounded prompt，并做 answer-aware citation pruning。 | 求职级强；强调“普通会话不误拒答、严格资料模式不让模型凭记忆硬答”。 |
| Eval / Smoke | 有离线单测、脚本安全测试、真实链路 smoke、artifact redaction、Quality Console 聚合。 | 求职展示很加分；仍不是大规模 benchmark。 |

## 最新证据

- 全量后端基线：`mvn test -DskipITs` PASS（953 tests，0 failures，5 skipped，本轮复验）。
- Conversation Grounding：`docpilot-conversation-grounding-20260712183609-a15fef` PASS，6/6 case 覆盖未绑定 KB、误选 `STRICT_KB`、`AUTO_RAG` 普通问题、`AUTO_RAG` 无证据 fallback、`STRICT_KB` 无证据拒答和 `AUTO_RAG` evidence citation；`ConversationGroundingSmokeScriptSafetyTest` 3 tests PASS。
- 真实用户链路：`docpilot-real-user-qa-20260711170544-dff948` PASS。
- 真实 RAG QA：`docpilot-rag-real-qa-20260711171137-ed38a0` PASS。
- Parser 真实链路：`docpilot-parser-real-chain-20260712015555-91d1fd` PASS，source locator `3/3`、parser boundary `4/4`。
- Memory provider：`docpilot-memory-provider-20260711172435-14083e` PASS，6 calls、`casePassRate=1.0000`、`rawProviderOutputStored=false`。
- Agent quality：`docpilot-agent-quality-eval-20260711171903-fae364` PASS。
- Rerank provider 对照：`docpilot-rerank-effect-hybrid-20260712015151-46c631` / `docpilot-rerank-effect-rerank-20260712015353-cc21a9` PASS，candidate `rerankApplied=true`、`rerankModel=qwen3-rerank`、`rerankFailureReason=""`，核心 RAG、no-evidence 和权限安全无回退；hard fixture 从 baseline target rank 2 / distractor rank 1 改善到 rerank target rank 1 / distractor rank 3，`hardUpliftObserved=true`。
- Rerank 代表语料 eval：`docpilot-rerank-representative-representative-hybrid-20260712151858-5543fd` / `docpilot-rerank-representative-representative-rerank-20260712152212-2e0f81` PASS；12 case 中 10/10 target coverage、2/2 no-evidence preserved、candidate `rerankApplied=true`、`targetRerankAppliedCaseCount=10`、`strictImprovementCaseCount=2`、`upliftCaseCount=10`、`citationLeakageCount=0`、`noEvidenceRegressionCount=0`。
- Citation locator UI：`docpilot-cloud-quality-20260712154804-0540c6` PASS；前端 lint/build PASS，文档详情、KnowledgeBase、Conversation 和 Agent 页面已展示 source locator / 页码 / section / block metadata。
- 本轮定向 ParseTask / rerank 回归：`ParseTaskServiceImplTest`、`ParseTaskConsumeEntryServiceImplTest`、`ParseTaskRecoveryServiceTest`、`RerankEffectSmokeScriptSafetyTest` 共 40 tests PASS；代表 eval 定向测试 26 tests PASS。
- 本轮全量后端回归：`mvn test -DskipITs` PASS（953 tests，0 failures，5 skipped）。

## 面试讲法

可以这样讲：

> 我做的是一个企业文档知识库 RAG 核心闭环。文档上传后走异步解析，parser 会保留 page、block、section path 和 source locator；chunk 后持久化到 MySQL，并把 metadata 写入 Qdrant payload。检索时用 userId、documentId / documentIds、indexVersion 做 metadata filter 和 service 二次校验，KnowledgeBase RAG 支持多文档检索、multi-query、hybrid search、no-evidence gate、grounded QA 和 quote-level citation。会话模式下，我把回答依据拆成 `MODEL_ONLY / AUTO_RAG / STRICT_KB`：未绑定知识库的普通问题直接走模型，AUTO 模式只有资料意图才检索，严格知识库模式无证据才拒答；RAG evidence 会和 summary、recent turns、memory 一起进入统一 context packing，并生成 trace，方便质量回归和问题定位。

不要这样讲：

- 不要说“完整商业 SaaS / 线上 SLA / 大规模多租户计费”。
- 不要说“支持复杂 PDF 智能解析、OCR、扫描件坐标级 citation”。
- 不要说“大规模或稳定真实 rerank relevance uplift 已验证”；当前只能说“百炼 rerank provider 已真实调用，hard fixture 观察到排序 uplift，12-case 代表 eval 未出现 coverage / no-evidence / citation leakage 回退，并观察到有限 uplift 信号”。
- 不要把 smoke / eval 写成大规模 relevance benchmark；当前更准确的说法是“小样本真实链路质量门禁 + 离线回归”。

## 下一步优先级

1. 扩展代表语料质量报告，把 hit、citation、no-evidence、answer faithfulness 和 distractor suppression 形成一页可读趋势摘要。
2. 继续把 Conversation grounding smoke、Memory quality smoke 和 RAG representative eval 聚合进 Quality Console 的趋势视图，让“路由可追踪、证据可定位、记忆可治理”更像完整求职作品。
3. 后续如继续增强 citation，可评估 PDF 坐标级定位 / 页面截图锚点；当前不把它写成已实现能力。
