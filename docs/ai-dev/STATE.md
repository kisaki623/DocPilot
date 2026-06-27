# DocPilot 当前状态

最后更新：2026-06-27

旧状态快照已保留在 `docs/ai-dev/archive/STATE_2026-04-18.md`。本文件只维护当前事实，不追加流水账。

## 1. 项目定位

DocPilot 是 Java Spring Boot + Next.js 的企业文档知识库 RAG + 会话记忆平台，核心主线是文档上传、异步解析、结构化切片、向量索引、多文档检索增强问答、可信引用、会话上下文追踪、用户记忆沉淀、权限隔离和质量门禁。

当前目标不是停留在“能演示的 AI 文档问答”，而是推进到生产化知识库 RAG 核心闭环：系统要能判断什么时候有证据、什么时候必须拒答或降级，并能通过 trace / artifact 解释每次回答用了哪些 evidence。当前仍不宣称完整商业 SaaS、线上 SLA、大规模多租户计费、高可用运维或成熟多 Agent 编排系统。

## 2. 当前已实现能力

- 账号密码登录 / 注册；短信验证码登录保留兼容路径。
- 文件上传、分片上传、MinIO / local 存储模式。
- 文档创建、列表、详情。
- RocketMQ + Outbox 异步解析链路设计与实现，包括 outbox relay、scan job、producer 边界。
- Redisson 分布式锁、消费幂等、解析任务状态机和补偿思路。
- Redis 缓存、问答限流、登录 token / 上传会话等状态管理。
- 普通文档问答与 SSE 流式问答，支持 citations 和问答历史。
- Agent 工具选择、`AgentTask` / `AgentStep` 持久化、前端 Trace 展示。
- Agent 工具链包含文档状态、摘要、问答、旧 RAG showcase 工具和新 RAG QA 工具，能展示 routingReason、matchedKeywords、steps、citations。
- Agent 工具链已新增内部 `ToolSpec` / `ToolSpecRegistry` 元数据底座，并提供最小 ToolCall API 用于列出工具和调用安全子集工具；Agent 主流程已让 status 与新 RAG QA 工具复用 `ToolCallService`，summary / qa legacy 分支仍保持兼容；OpenAI-compatible Function Calling adapter 已有 mock tool_call 闭环。
- 当前 RAG 主线已有持久化 DocumentChunk、ChunkingService、EmbeddingProvider、VectorStoreClient、RagIndexingService、parse success 自动 indexing trigger、RagDocumentRetrievalService、单文档 RAG QA / SSE、KnowledgeBase 多文档 RAG retrieval / 非流式 QA、Agent RAG QA 接入和单文档 / 多文档离线 retrieval quality smoke；旧 Agent RAG showcase 链路仍保留为独立演示路径。
- KnowledgeBase RAG 已补充总结类问题质量治理：Markdown / 文本块合并式 chunking、摘要意图下的跨文档召回多样性、`documentHitCounts`、回答模型 provider / model / call count 观测字段，以及面向资料集总结的 prompt。
- KnowledgeBase RAG 已新增默认关闭的 Hybrid / Rerank 增强链路：BM25 keyword 检索按 user、KnowledgeBase 文档集合和 `indexVersion` 过滤；RRF 融合保留 chunk 元数据并经过二次 scope guard；可选 rerank 接入候选排序并输出 `retrievalMode`、`rerankApplied`、`rerankModel`、`vectorScore`、`keywordScore`、`fusedScore`、`rerankScore` 等观测字段；示例配置只提供安全占位，真实 provider 需要本地私有 `.env` 显式配置。
- RAG Quality Upgrade v1 已完成离线质量门禁增强：单文档 retrieval 与 KnowledgeBase retrieval 一样接入 `app.rag.retrieval.min-similarity-threshold`；KnowledgeBase RAG eval 从 hit / citation 扩展到 answer marker、forbidden answer leak、最少 citation 数和多文档覆盖指标，artifact 仍只保存脱敏 summary。
- RAG Quality Upgrade v3 已把真实链路 no-evidence 门禁从 `REVIEW` 推到 `PASS`：KnowledgeBase hybrid 检索在融合后继续执行 evidence confidence gate，并对带 `vectorScore` 的 hybrid hit 使用原始向量相似度做阈值判断，避免把 RRF `fusedScore` 当作 similarity；默认质量阈值校准为 `0.50`。2026-06-27 `scripts/smoke/rag-real-quality-smoke.ps1 -Mode run` 默认配置 PASS，marker 为 `docpilot-rag-real-quality-20260627210458-9d0321`，覆盖真实 embedding + Qdrant、chunk、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、populated-KB no-evidence、Conversation Trace、权限隔离、前端 route 和 artifact 脱敏。
- 目标 KnowledgeBase `3` 的文档 `83/84/85/86` 已授权重建索引到稳定 Qdrant collection `docpilot_rag_v2`；chunk / vector 数为 `35/35`、`18/18`、`10/10`、`16/16`，总结资料集检索分布为 `{83:2,84:1,85:1,86:2}`。
- Conversation Context Management / Agent Memory Mode 后端 MVP 已新增会话、消息、摘要、上下文 Trace、用户长期记忆五张新表和对应 API；`ContextAssemblyService` 可按 `RECENT_TURNS` / `AGENT_MEMORY` 组装系统提示、长期记忆、会话摘要、最近轮次与可选 KnowledgeBase evidence，并输出和持久化摘要级 trace。
- 会话发送链路已按工程化质量收窄事务边界：上下文装配和回答模型调用不在长事务内执行；仅最终 conversation 行锁、连续写入 user / assistant message 和更新时间处于事务内，trace 仍为 best-effort。
- 会话摘要已有显式 refresh API，当前采用本地 extractive 摘要压缩最近消息，不调用真实外部模型。
- 长期记忆已有候选机制：规则式提取会话用户消息中的偏好、目标、项目状态等候选记忆，候选以 `SUGGESTED` 保存，默认不进入 prompt；用户接受后才转为 `ACTIVE`。
- 前端已新增 `/conversations` 会话工作台 MVP，支持会话创建、非流式消息、KnowledgeBase 绑定、summary / trace 查看、ACTIVE 记忆维护和候选记忆接受 / 忽略。
- 前端展示已完成一轮产品化收口：首页、Dashboard、KnowledgeBase 和 Conversations 页面能直接呈现 RAG / KnowledgeBase / Agent Memory / Trace 主链路、smoke 证据口径与当前边界；KnowledgeBase 页面已展示 provider / collection、model call、citation 和命中文档分布等观测字段。
- 前端重点页已完成 AI 产品感二次精修：首页提供系统流程面板，Dashboard 更像演示指挥台，KnowledgeBase / Conversations 未登录态收敛为说明卡，登录态继续突出 evidence、Trace、Memory 等可观测字段。
- `/conversations` 已作为前端核心页按 GPT / DeepSeek 风格重做：页面主视觉改为居中聊天流、底部悬浮 composer 和左侧会话历史；Trace、Memory、Summary、KnowledgeBase evidence 收进右侧 Context Inspector 抽屉，保留会话上下文可观测性但不再把工程控制台作为主界面。
- 前端 UI 文案已完成一轮成熟化收口：产品页面不再直接暴露“求职 / 面试 / MVP / 演示 / 生产级 / smoke”等内部口径，改为工作空间、引用来源、上下文溯源、会话记忆、工具链等更克制的产品表达；详细边界仍保留在 docs / README / 面试材料中。
- A1 / S 系列真实链路 smoke 已补齐：单文档 RAG、多文档 KnowledgeBase RAG、真实回答模型、MinIO active storage、RocketMQ + Outbox active parse、真实 embedding + Qdrant、ToolCall API 和权限越界失败案例均有记录。
- 云端完整业务 smoke 质量门禁 runner 已落地并完成一次 `run` 模式验证：`scripts/smoke/cloud-quality-smoke.ps1` 支持 `plan` / `dry-run` / `run`，2026-06-27 使用 marker `docpilot-cloud-quality-20260627022219-37efd4` 跑通 tunnel、backend health、frontend route、临时用户、两文档上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、Conversation Trace、权限隔离负向检查、脱敏 artifact 和最终 git status 检查，整体状态 PASS。

## 3. 当前边界

- 当前目标是生产化知识库 RAG 核心闭环，但还不是完整商业知识库 SaaS 或已验证线上 SLA 的生产系统。
- fake embedding / in-memory vector store 仍属于测试、离线 eval 和稳定复现边界；真实 embedding + Qdrant 已在 smoke collection 验证，但不代表线上治理或固定 SLA。Hybrid / Rerank 目前是 KnowledgeBase RAG 的可选增强，默认关闭，真实 provider smoke 需要显式配置和单独验证。
- RAG Quality Upgrade v1 的新增 eval 仍是 `MockEmbeddingProvider` + `InMemoryVectorStoreClient` + synthetic answer 的离线门禁；它可以防止明显的 retrieval / citation / answer coverage 退化，但不代表真实 embedding、真实 rerank 或真实回答模型的效果评测已经完成。
- RAG Quality Upgrade v3 已在真实 embedding + Qdrant 链路上证明 smoke 级 populated-KB no-evidence 可拒答：低于 `0.50` 的候选不进入 grounded QA，QA 返回 no-evidence 且不生成 citation。该结论仍是 smoke 级门禁，不等于跨大规模语料、复杂领域和全部问法的生产 relevance benchmark。
- Qdrant 已有 adapter、payload mapping、fake server 测试、preflight 参考和真实 tunnel smoke；普通测试不依赖远程 Qdrant，复现真实 Qdrant 仍需通过本地 `.env` 显式配置可用 endpoint / tunnel、`RAG_VECTOR_STORE_PROVIDER=qdrant` 和 `RAG_QDRANT_COLLECTION`。
- 持久化 RAG chunk 与 indexing workflow 已接入 parse success 自动触发；当前触发器为最小异步 service 调用，尚未独立 MQ / Outbox 化。
- Agent RAG QA 已接入新 RAG 查询链路，但仍是最小工具路由，不是复杂 LLM planner。
- ToolSpec / ToolCall API 目前是内部后端底座，Agent 只渐进复用 status 与 `rag_qa_tool` 调用；Function Calling 目前仅支持 OpenAI-compatible tools schema / mock tool_call adapter，不等于已接真实模型、MCP、KnowledgeBase Agent Tool 或完整通用工具编排。
- KnowledgeBase RAG v1 已提供后端管理、跨文档 retrieval、非流式 QA、前端可观测展示、Hybrid / Rerank 可选增强和离线 eval / smoke；尚未接 KnowledgeBase SSE 或 Agent / ToolSpec 主链路。
- Conversation Context Management 当前仍为非流式 MVP：不做后台自动摘要生成、不做真实模型长期记忆抽取、不持久化完整 prompt / evidence 原文、不接管现有 Agent 主链路；KnowledgeBase evidence 只通过既有 retrieval service 获取。
- 前端重点页面已完成产品化文案收口：页面层不再使用“求职 / 面试 / MVP / smoke / 生产级”等内部或过度承诺表达，改为工作空间、引用来源、上下文溯源、会话记忆、工具链等用户可感知口径；工程边界仍保留在 ai-dev 文档中。
- Conversation Context Management 登录态 runtime smoke 已完成核心 MVP 链路：2026-06-13 已按用户授权通过当前本机 SSH tunnel 入口对云服务器 Docker MySQL 执行 `007_init_conversation_context.sql` 并确认五张 T013 表存在；随后本地前后端启动、健康检查、`/conversations` 登录态页面、创建会话、发送消息、trace、summary refresh、候选记忆提取 / 接受、ACTIVE 记忆进入第二轮 Agent Memory 上下文均验证通过。2026-06-13 追加完成带真实 KnowledgeBase 文档 evidence 的 API + 浏览器端到端 smoke：绑定 KB 的 Agent Memory 会话可触发 RAG evidence，trace 显示 `Evidence=1`、`ragTriggered=true`、`ragRequired=true`、命中文档分布 `#94: 1`。
- `scripts/smoke/cloud-quality-smoke.ps1 -Mode run` 会创建临时用户、临时文档、KnowledgeBase、Conversation 和本地脱敏 artifact；artifact 默认不提交。2026-06-27 的 PASS 记录位于 `docs/showcase/DEMO_SMOKE_RECORD.md`，本地 artifact 位于 `backend/target/smoke/docpilot-cloud-quality-20260627022219-37efd4/artifact.json`。
- 新 chunking 策略只影响后续 indexing；除已重建的 `83/84/85/86` 外，其他既有文档必须 rebuild / reindex 后，MySQL chunk 和 Qdrant 向量 payload 才会反映新的 chunk 质量。
- Agent 不是多智能体自主规划。
- `llm_execute` / real provider 等能力如果默认关闭，要视为待显式配置和 runtime 验证，不能写成默认生产能力；Function Calling 不能写成生产默认接管。
- 没有线上 SLA，不写 100% 可靠。

## 4. 当前生产化推进优先级

1. 扩展 v4 citation grounding / answer audit：回答层继续只接收通过 gate 的 evidence，并输出可脱敏的 score / fallback / citation 审计信息。
2. 增加更多 no-evidence eval case，覆盖语义相近但无证据、跨主题、跨文档干扰和 hybrid keyword-only 噪声。
3. 继续把 Conversation Memory 与 KnowledgeBase evidence 分清：短期上下文、长期记忆、RAG evidence 和 trace 各自可解释。
4. 保持 README / docs 展示口径与真实 smoke 证据一致，不把 smoke 级 PASS 写成线上 SLA 或大规模生产 benchmark。

## 5. 事实源规则

- 当前任务看 `docs/ai-dev/CURRENT_TASK.md`。
- RAG 总路线看 `docs/ai-dev/ROADMAP_RAG.md`。
- 历史记录看 archive 或旧大文件。
- 不要让旧 TODO 覆盖当前路线。
- 文档和代码冲突时，以代码、测试和可运行结果为准。

## 6. 最近安全加固

- 2026-06-13 已修复云服务器 Prometheus 9090 公网暴露：`docpilot-prometheus` 仅绑定远程本机 `127.0.0.1:9090`，远程 `firewalld` 已移除 `9090/tcp` 放行，公网 `62.234.3.22:9090` 验证不可连；腾讯云安全组仍建议在控制台重新检测并收口云侧入站规则。
- T009 已补充 RAG scope guard：retrieval / QA / Agent `rag_qa_tool` / parse success indexing trigger 均以 userId、documentId、indexVersion 作为最小隔离边界。
- Vector search 仍依赖 metadata filter；service 层新增返回 hit 的二次校验，防止跨用户、跨文档或跨版本 citation 泄露。
