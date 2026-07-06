# DocPilot Project Interview Brief

本文档面向 Java 后端实习 / AI 应用工程化面试，记录 DocPilot 当前真实能力、边界和推荐讲法。它只总结当前仓库事实，不把 BLOCKED、设计中或未接入能力写成已完成。

## 1. 一句话定位

DocPilot 是一个基于 Java Spring Boot + Next.js 的企业文档知识库 RAG + 会话记忆工程化项目，覆盖文件上传、异步解析、结构化 chunk、Qdrant indexing、单文档 / 多文档 KnowledgeBase RAG、可信 citation、Conversation Trace、用户记忆治理和内部 Agent Quality Console 质量门禁。

更克制的面试讲法：这是一个把 RAG、Memory、Trace、Eval 和真实链路 smoke 串成闭环的 AI 应用工程项目，不是完整商业 SaaS、线上 SLA 平台或成熟多 Agent 编排系统。

面向 AI Agent / RAG 实习岗位的讲法：DocPilot 的主线是生产化知识库 RAG 核心闭环。RAG 侧已有 chunk 持久化、EmbeddingProvider 抽象、真实 embedding + Qdrant smoke、单文档 / KnowledgeBase 多文档 retrieval / QA、scope isolation、no-evidence / hard-negative / answer-grounding / answer faithfulness 质量门禁、Conversation Trace、Memory governance 和真实体验审计。Agent 侧保留工具选择、ToolCall、AgentTask / AgentStep trace 和默认关闭的 LLM tool execution mode；Agent Quality Console 则把真实 audit、eval case、failure bucket、trace reference、run comparison 和 token / cost 数值聚合成内部质量控制台。

## 2. 当前真实已实现能力

- 账号密码注册 / 登录、文档上传、文档创建、文档列表和详情页。
- Document Parser MVP：支持 `txt / md`、文本型 PDF、本地 HTML 和 DOCX 的基础文本抽取，并保留 page / block / section 级来源字段供后续 citation 细化。
- 基于 Outbox + RocketMQ 的异步解析链路，包含解析任务、消费幂等、Redisson 分布式锁和补偿思路，并已完成 active MQ smoke。
- MySQL 持久化、Redis 缓存 / 限流 / 会话上下文、MinIO 对象存储和分片上传；MinIO active storage 已完成最小 smoke。
- RAG 文档问答：基于文档切分、chunk 持久化、EmbeddingProvider、Qdrant / in-memory VectorStore、上下文组装、AI 回答和引用展示。
- KnowledgeBase 多文档 RAG：支持知识库创建、文档加入、跨文档 retrieval / QA、quote-level citation、`documentHitCounts`、no-evidence 和多文档 coverage 质量门禁。
- Conversation Context / Memory：支持会话、摘要、ACTIVE / SUGGESTED / IGNORED memory、候选治理、冲突 / 重复提示、KnowledgeBase evidence 进入 Context Trace，并保持 memory 与 RAG evidence 分层。
- SSE 流式问答，前端支持流式事件解析、引用展示和失败降级。
- 最小 Agent 演示：`DocumentToolSelector` 按规则选择状态 / 摘要 / 问答工具，Agent run 返回可解释路由信息。
- AgentTask / AgentStep 持久化：记录 Agent run 和工具步骤，支持按 taskId 查询并在前端展示 trace。
- Tool selector shadow mode：primary selector 与 fake / real shadow selector 可旁路比较，不改变生产 routing。
- 真实 provider shadow-only 验证：在用户授权下完成 OpenAI-compatible provider 的 summary / QA shadow 调用验证，shadow decision 未接管 production routing。
- 默认关闭的 LLM tool execution mode：显式设置 `app.agent.selector.mode=llm_execute` 后，可用 LLM selector 选择 allowlist 内工具，服务端仍使用已有 `userId / documentId / task / sessionId` 上下文执行 summary / QA / RAG 等工具；provider 失败、解析失败或非法 toolName 会回退 keyword selector。
- selector shadow metrics、threshold policy、内部 debug dump / reporter。
- 默认关闭的 `agentSelectorShadow` Actuator endpoint，测试内显式开启已验证 200、字段白名单 / 黑名单和只读边界。
- Agent + RAG Showcase：`/agent` 页面已通过 runtime 验证，`rag_tool` 能展示 retrieved chunk、score / similarity、citation metadata、routingReason、matchedKeywords、脱敏 RAG trace 摘要和 persisted steps；普通 QA 路径仍展示 citations。
- Agent Workflow 展示：`/agent` 页面基于已有响应和 persisted trace 展示接收任务、选择工具、执行工具、生成结果和持久化 trace，不新增 API 或后端路由逻辑。
- Prompt Engineering 证据链：`docs/PROMPT_ENGINEERING_NOTES.md` 说明 tool selection prompt 结构、JSON 输出协议、parser 校验、allowlist、fallback 和 bad cases，不记录真实文档内容或完整运行时 prompt。
- Agent Quality Console 内部质量控制台：`/quality` 可展示最近真实 audit / eval run、Gate 状态、Eval Catalog、Failure Triage、Trace 定位、Run Comparison 和 Model / Cost Summary；所有 parser / API 采用字段白名单，不返回 prompt、answer 原文、文档全文、evidence context 或凭据。
- 单文档 RAG、多文档 KnowledgeBase RAG、真实回答模型、真实 embedding + Qdrant、Memory governance、RAG no-evidence / hard-negative / answer faithfulness 质量门禁、MinIO active storage、RocketMQ + Outbox 和权限越界失败案例均已有 smoke 记录。

## 3. 当前半实现能力

- RAG 测试 / eval 仍保留 fake embedding + in-memory vector store，便于稳定复现；真实 embedding + Qdrant 已在 smoke collection 验证；KnowledgeBase RAG 已有默认关闭的 Hybrid / Rerank 可选增强，近阈值 hard-negative 支持度门禁已通过小规模真实 smoke，但不是线上治理完整的生产 RAG 或通用 entailment scorer。
- PDF / HTML / DOCX 解析已从占位推进为基础文本抽取 MVP，但仍不是 OCR、扫描件识别、外部网页抓取、复杂版面理解或商业级文档理解平台。
- Agent 是同步 API 下的最小工具链闭环，不是异步多 Agent 编排。
- LLM execute mode 只在显式配置时启用；默认仍是 keyword selector。真实 provider / execute 类验证必须在用户授权、配置可用和日志脱敏边界下运行。
- selector metrics 当前主要是内存态和 debug dump；Actuator endpoint 默认关闭，Prometheus 仅有设计文档。
- eval / benchmark 已有 artifact、脚本和 Console 聚合入口；当前仍是小样本真实 smoke + 离线 eval 组合，不代表大规模 relevance benchmark。

## 4. 当前仍不能夸大的能力

- T030 鉴权测试：BLOCKED，原因是项目当前只有 `spring-security-crypto`，缺少 Spring Security Web 鉴权体系、`spring-security-test` 和 `SecurityFilterChain`。
- 生产级完整向量 RAG、默认开启的 rerank / hybrid search、线上 SLA、生产权限体系、多 Agent 编排和 MCP 仍不是当前能力。
- OpenAI-compatible Function Calling adapter / `llm_execute` 仍是显式开启或 mock/offline 边界，不能写成生产默认接管。

## 5. 不能写成已完成的能力

- Prometheus 未接入；T032 只是 selector shadow Prometheus metrics 设计文档。
- Spring Security 未接入；T029 只是 Spring Security / Actuator 安全集成设计。
- Actuator endpoint 默认关闭，未生产开启，未加入默认 exposure include。
- 默认 production routing 仍由 primary `DocumentToolSelector` 决定；`llm_execute` 需要显式开启，且不能写成生产环境已启用 Function Calling。
- 没有生产级完整向量 RAG、MCP、LangChain4j / Spring AI function calling 生产接管或成熟多 Agent 编排。
- 没有线上 SLA、生产权限体系或生产短信网关。

## 6. 最适合写进简历的 5 个工程亮点

1. 设计并实现文档上传后的异步解析链路，结合 Outbox、RocketMQ、Redisson 分布式锁和幂等消费，降低同步阻塞和重复消费风险。
2. 设计统一 Document Parser 抽象并接入异步解析到 RAG indexing 链路，支持 txt / md、文本型 PDF、本地 HTML、DOCX 的基础文本抽取和 parser metrics。
3. 构建知识库 RAG 主链路：chunk 持久化、EmbeddingProvider 抽象、Qdrant 检索、scope guard、no-evidence、grounded QA、quote-level citation 和 MySQL / Qdrant 一致性门禁。
4. 实现 Conversation Context / Memory 闭环：短期上下文、摘要、长期记忆候选、用户可控治理、KnowledgeBase evidence 和 Context Trace 分层。
5. 建设真实链路质量门禁和 Agent Quality Console：用 smoke / audit / eval artifact 聚合 PASS / REVIEW / BLOCKED、failure bucket、trace reference、run comparison 和 token / cost 数值。

## 7. 求职展示优先级

1. 先展示 `/quality` Agent Quality Console：最近真实 audit、Gate 列表、Eval Catalog、Failure Triage、Trace 定位、Run Comparison 和 Model / Cost Summary。
2. 再展示 KnowledgeBase / Conversations：说明多文档 RAG citation、Context Trace、`ragTriggered=true`、`evidenceCount>0` 和 ACTIVE memory 如何进入上下文。
3. 然后展示文档详情普通问答 / SSE 流式问答：说明 quote-level citation 和 no-evidence 如何约束回答可信度。
4. 最后展示 `/agent` Agent Showcase：工具选择、routingReason、matchedKeywords、taskId、steps 和 citations，强调 Agent 是围绕文档工具和 Trace 的辅助层。
5. 对 RAG / Memory / Quality Console 保持诚实：当前是小样本真实链路 smoke + 离线 eval + 内部质量台，不是线上 SLA、大规模 benchmark 或商业 APM。

可补充材料：`docs/PROMPT_ENGINEERING_NOTES.md` 适合用来讲 Tool Selection Engineering，不包含真实 prompt、文档正文或 secret。

## 8. 面试高风险追问与诚实回答

### 你这个是完整 RAG 吗？


不是线上级完整 RAG，但已经不是只会拼接口的玩具 RAG。当前做到了知识库 RAG 核心闭环：chunk 持久化、EmbeddingProvider、Qdrant adapter、真实 embedding + Qdrant smoke、单文档 / 多文档 retrieval / QA、scope isolation、quote-level citations、no-evidence、answer grounding、hard negative、answer faithfulness、Conversation Trace、MySQL / Qdrant 一致性和真实链路质量门禁。仍不能夸大为大规模 relevance benchmark、固定 SLA 或通用语义蕴含系统。

### Agent Quality Console 解决了什么问题？

它解决的是“AI 功能出了问题怎么发现、定位和回归”的问题。比如 2026-07-05 的真实审计首轮发现 Quality Eval Catalog 构造器注入缺失导致 backend health BLOCKED；通过 Console / audit artifact 定位后修复，再跑真实审计 `docpilot-real-user-qa-20260705165151-bbe588` PASS，并在 `/quality?autoload=1` 看到 Eval Catalog、Failure Triage、Run Comparison 和 Model / Cost Summary。边界是它是内部质量控制台，不是企业级 APM，也不保存 prompt、answer 原文、文档全文或 evidence context。

### Agent 是多 Agent 吗？

不是。当前是单 Agent / 工具链演示，重点在工具注册、规则选择、可解释路由、执行步骤和持久化 trace。多 Agent 编排、长期记忆和工具市场都没有实现。

### LLM selector 是否已经接管生产路由？

默认没有。生产默认真实工具执行仍由 primary `DocumentToolSelector` 决定。现在新增了显式开关 `llm_execute`，开启后 LLM 只能选择 allowlist 内工具，服务端执行已有工具输入；失败会回退 keyword selector。

### 真实 provider 调用是否会泄露敏感信息？

真实 provider shadow-only 验证只在用户授权下运行，协作过程未读取或输出 API Key、完整连接地址、prompt、文档内容或模型完整返回。默认配置仍是 disabled，后续再次调用必须重新确认。

### Actuator endpoint 是否已经能上线使用？

不能这么说。`agentSelectorShadow` endpoint 已实现但 `enableByDefault=false`，默认关闭；测试内显式开启验证过 200 和字段安全边界，但没有生产开启，也没有 Spring Security 保护。

### Prometheus 是否已经接入 selector metrics？

没有。当前只是设计了未来指标名、低风险 label 和禁止字段。实际 Micrometer / Prometheus 接入还没做。

### RocketMQ + Outbox 是否真的跑通过？

已在演示环境跑通过 active smoke：`parse/create` 返回 `PENDING`，生产者发送 `SEND_OK`，消费者收到消息并推进解析，最终 parseStatus 为 `SUCCESS`。但这仍是演示环境证据，不代表线上 SLA；复现需要可用 RocketMQ NameServer / Broker / consumer。

### 为什么 T030 鉴权测试 BLOCKED？

项目目前没有 Spring Security Web 鉴权体系，只有 `spring-security-crypto`。在不允许新增依赖和不允许新增生产安全配置的边界下，不能硬做 401 / 403 / 角色访问测试。
