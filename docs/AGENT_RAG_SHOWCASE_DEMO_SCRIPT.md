# Agent + RAG Showcase Demo Script

本文档用于 10 分钟以内的面试 / BOSS 沟通演示。它只描述当前真实能力：DocPilot 已完成 Agent + RAG Showcase 的浏览器验证和截图证据；当前 RAG 仍是 fake embedding + in-memory vector store，不是生产完整向量 RAG。

## 1. 30 秒项目介绍

DocPilot 是一个基于 Spring Boot + Next.js 的 AI 文档问答项目。它从文档上传、异步解析、普通问答和 SSE 流式问答，扩展到了一个可展示的 Agent + RAG Showcase：用户可以在 `/agent` 页面选择已解析文档，运行文档状态、摘要、问答或 RAG 检索类任务。页面会展示工具选择结果、`routingReason`、`matchedKeywords`、持久化 AgentTask / AgentStep trace、引用证据，以及 RAG 召回片段、score 和 metadata。这个项目重点展示 AI 应用工程化能力，而不是把 demo 包装成生产级多 Agent 或完整向量 RAG。

## 2. 2 分钟演示路线

按截图顺序讲，控制在 2 分钟内：

1. `agent-showcase-overview.png`
   - 先说明这是 `/agent` Showcase 页面。
   - 左侧是文档选择、documentId、任务模板和输入框。
   - 右侧是运行结果区域。
   - 强调页面明确写了 Lite 边界：只验证已解析文档上的 Agent 运行，不验证完整上传解析链路。

2. `agent-rag-retrieval-results.png`
   - 展示 `decision=rag_tool`。
   - 说明本次任务触发了 RAG 检索召回路径。
   - 页面展示 retrieved chunk、score / similarity、metadata 和 answer context 入口。
   - 明确这里用的是 fake embedding + in-memory vector store，用来证明链路和展示形态。

3. `agent-routing-explanation.png`
   - 展示 `routingReason` 和 `matchedKeywords`。
   - 说明工具选择不是黑盒：后端 `DocumentToolSelector` 会输出为什么选这个工具，以及命中了哪些关键词。
   - 面试讲法：这相当于 Function Calling / Tool Calling takeover 之前的可解释工具路由层。

4. `agent-persisted-steps.png`
   - 展示 AgentTask / AgentStep 持久化执行轨迹。
   - 重点看 `taskId`、`SUCCESS`、step count、`document_status_tool`、`document_rag_tool`、status、durationMs、inputSummary、outputSummary。
   - 说明 trace 可以帮助排查 Agent 执行过程，而不是只看最终回答。

5. `agent-citations.png`
   - 展示普通 QA 路径仍能返回 citations。
   - 说明新增 RAG demo 没有破坏原来的 QA / citations 能力。
   - 这里可以顺带说：Agent 页面既能展示 RAG 召回，也能展示原有问答引用证据。

## 3. 面试官问：你这个 RAG 是真实的吗？

诚实回答：

不是生产完整 RAG。当前实现的是求职展示和工程验证用的最小 RAG demo：后端已经打通文档切块、fake embedding、in-memory vector store、topK 召回、score / similarity、metadata、answer context 组装，并通过 Agent 页面展示出来。它证明了 RAG 的内部链路、召回展示、metadata 传递、answer context、Agent steps 和 citations 展示都已经打通。

但我不会说它已经接入真实向量数据库。当前还没有接真实 embedding provider，也没有接 Qdrant / Redis Vector，没有做 chunk 持久化、重建索引、rerank 和召回质量评估。所以我会把它描述为“最小 RAG demo / fake embedding RAG showcase”，而不是“生产级向量 RAG”。

## 4. 后续怎么生产化？

生产化会分几步推进：

1. 接真实 embedding provider：把当前 `FakeEmbeddingModel` 替换为可配置的真实 embedding client，并加 timeout、重试、降级和成本控制。
2. 接专用向量库：优先评估 Qdrant，也可以根据环境评估 Redis Vector / Redis Stack；MySQL 只保存 chunk metadata、hash、版本和 citation mapping。
3. 完善 chunk 策略：按文档类型、标题、段落、页码和 token 长度生成稳定 chunk，保留 chunkVersion 和 contentHash，支持增量重建。
4. 做检索评估：记录 topK 命中率、citation hit rate、answer success rate、match / mismatch 样例，避免只凭主观感觉调参数。
5. 加权限隔离：vector payload 必须带 userId / documentId，检索时按当前用户和文档过滤，不能跨用户召回。
6. 建召回质量指标：跟踪召回数量、score 分布、空召回率、低分召回率、fallback 次数和用户反馈。

这条路线的目标是从当前 demo 平滑升级到可维护的工程化 RAG，而不是一上来堆框架。

## 5. BOSS / 简历项目亮点版

- 实现 Agent + RAG Showcase：支持文档工具选择、可解释 routingReason / matchedKeywords、AgentTask / AgentStep trace、RAG retrieved chunks、similarity score 和 citation metadata 展示。
- 构建最小 RAG demo 链路：文档切块、fake embedding、in-memory vector store、topK 召回、answer context 组装，后续可替换为真实 embedding + Qdrant。
- 保持工程边界清晰：RAG demo 不接管生产问答链路，不新增公开 API，不接 LangChain4j，不把 fake vector store 夸大成生产向量数据库。

## 6. 10 分钟时间分配建议

- 0:00 - 0:30：项目定位与 Agent + RAG Showcase 一句话介绍。
- 0:30 - 2:30：按 5 张截图讲页面能力和运行证据。
- 2:30 - 4:00：解释 Agent 工具选择、routingReason、matchedKeywords 和 trace。
- 4:00 - 5:30：解释 RAG demo 的 fake embedding / in-memory 边界。
- 5:30 - 7:00：说明如何生产化到真实 embedding + Qdrant / Redis Vector。
- 7:00 - 10:00：回答追问：为什么不直接 LangChain4j、为什么 shadow-only、为什么完整 T010 仍 BLOCKED。

## 7. 不能这样说

- 不能说“已接入真实向量数据库”。
- 不能说“已接入 Qdrant / Redis Vector”。
- 不能说“已接 LangChain4j / Spring AI”。
- 不能说“完整生产 RAG 已完成”。
- 不能说“LLM function calling 已接管生产工具选择”。
- 不能说“完整上传解析到 Agent run 的 T010 已通过”。
