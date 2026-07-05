# DocPilot Interview QA

本文档面向 Java 后端实习面试，整理 DocPilot 高频问题回答。回答重点是后端工程、AI 应用工程化和真实边界，不以算法工程师视角夸大能力。

## 1. 项目整体介绍

### Q1：请用一分钟介绍 DocPilot。

面试可背版回答：DocPilot 是一个 Java Spring Boot + Next.js 的企业文档知识库 RAG + 会话记忆工程化项目，覆盖文件上传、异步解析、RAG indexing、单文档 / 多文档 KnowledgeBase 检索问答、SSE 流式输出、Conversation Trace、用户记忆治理和内部 Agent Quality Console。后端重点在 Outbox + RocketMQ、Redisson 幂等锁、Redis 缓存限流、MinIO 对象存储、Qdrant 检索、Context Trace 和脱敏质量门禁。

面试官追问：它和普通 CRUD 项目相比有什么工程价值？

诚实边界：它不是完整商业 SaaS、线上 SLA 或大规模 benchmark，价值在于把中间件、AI 调用、RAG 可信引用、Memory、Trace、真实链路审计和质量控制台串成一个可解释的工程闭环。

对应位置：`backend/src/main/java/com/docpilot/backend`、`frontend/app`、`docs/showcase/PROJECT_INTERVIEW_BRIEF.md`。

### Q2：项目最核心的技术亮点是什么？

面试可背版回答：我会讲五点：Outbox + RocketMQ 异步解析、知识库 RAG 与可信 citation、Conversation Memory 与 Context Trace、真实链路质量门禁、Agent Quality Console 内部质量控制台。

面试官追问：哪个最能体现后端能力？

诚实边界：最能体现后端能力的是 Outbox + RocketMQ + 幂等消费、MySQL / Qdrant 一致性校验、RAG scope guard 和真实 smoke / audit 的质量闭环。

对应位置：`backend/src/main/java/com/docpilot/backend/mq`、`backend/src/main/java/com/docpilot/backend/ai/rag`、`backend/src/main/java/com/docpilot/backend/quality`。

### Q3：你在项目中最关注什么？

面试可背版回答：我关注的是“AI 功能怎么工程化落地”：证据是否足够、引用是否可信、无证据时是否拒答、Memory 是否污染上下文、失败能否被 eval / trace / audit 定位，以及修复后能否真实回归。

面试官追问：为什么不继续堆功能？

诚实边界：当前阶段更适合把 RAG / Memory / Quality Console 的真实链路质量做实。Prometheus、Spring Security、企业级 APM 可以作为后续增强，但不能盖过当前主线。

对应位置：`docs/ai-dev/ROADMAP_RAG.md`、`docs/ai-dev/ROADMAP_AGENT_QUALITY_CONSOLE.md`。

## 2. 文档上传与解析

### Q4：文档上传链路怎么设计？

面试可背版回答：前端上传文件，后端通过 FileController 和 FileService 保存文件记录，再通过 FileStorageWriter 写入本地或 MinIO。大文件场景支持分片上传会话、分片状态和合并完成。

面试官追问：为什么引入 MinIO？

诚实边界：MinIO 用于对象存储演示和大文件上传链路，不代表已做生产级对象存储治理。

对应位置：`backend/src/main/java/com/docpilot/backend/file`、`frontend/app/upload/page.tsx`。

### Q5：文档解析任务怎么触发？

面试可背版回答：用户创建文档后会创建 ParseTask，并通过 Outbox / RocketMQ 推进异步解析。解析结果更新文档状态和内容片段。

面试官追问：现在完整链路是否已验证？

诚实边界：上传 -> 解析链路已在演示环境验证 active MQ producer / consumer 和 parse success；Agent run 仍是同步 API 下的最小工具链演示，不代表异步多 Agent 编排或线上 SLA。

对应位置：`backend/src/main/java/com/docpilot/backend/task`、`backend/src/main/java/com/docpilot/backend/mq`。

### Q6：PDF 解析能力怎么样？

面试可背版回答：当前项目主能力是 txt / md 的文本解析和轻量检索增强。PDF 支持更偏占位，不能说成生产级 PDF 结构化解析。

面试官追问：如果要增强 PDF 怎么做？

诚实边界：需要接更可靠的 PDF 文本抽取、版面结构处理和解析质量评测，当前还没做。

对应位置：`docs/PROJECT_INTERVIEW_BRIEF.md`、`README.md`。

## 3. RocketMQ / 异步任务

### Q7：为什么用 RocketMQ？

面试可背版回答：解析是耗时任务，不适合阻塞接口响应。RocketMQ 将创建任务和执行解析解耦，配合 Outbox 降低事务和消息不一致风险。

面试官追问：Outbox 解决什么问题？

诚实边界：Outbox 不是万能事务，只是把待发送消息先落库，再由 relay / scan 补偿发送，降低接口事务提交后消息丢失风险。

对应位置：`ParseTaskOutboxMessage`、`ParseTaskOutboxRelayService`、`ParseTaskOutboxScanJob`。

### Q8：如何保证消费幂等？

面试可背版回答：通过消费记录和业务状态判断避免重复处理，同时在任务创建和消费侧使用 Redisson 锁降低并发重复执行风险。

面试官追问：重复消息一定不会造成问题吗？

诚实边界：不能说“绝对不会”。工程上通过幂等记录、状态检查和锁降低风险，还需要持续测试异常路径。

对应位置：`ParseTaskConsumeRecord`、`ParseTaskConsumeEntryService`、`RedissonConfig`。

### Q9：RocketMQ + Outbox 是否真的跑通过？

面试可背版回答：跑通过。演示环境里 `parse/create` 返回 `PENDING`，RocketMQ producer 发送 `SEND_OK`，consumer 收到消息并执行解析，最终文档 parseStatus 到 `SUCCESS`。

面试官追问：那项目是不是不能演示？

诚实边界：可以演示，但要说清这是演示环境 smoke，不是线上 SLA；复现依赖可用 RocketMQ NameServer / Broker / consumer。关闭 MQ 时会进入 no-op producer 路径。

对应位置：`ParseTaskOutboxRelayService`、`RocketMqParseTaskMessageProducer`、`ParseTaskMessageConsumer`、`docs/showcase/DEMO_SMOKE_RECORD.md`。

## 4. SSE 流式问答

### Q10：SSE 流式输出怎么实现？

面试可背版回答：后端提供 SSE 问答接口，前端解析 meta / chunk / done / error 等事件，实时展示回答片段和最终引用信息。

面试官追问：为什么不用 WebSocket？

诚实边界：当前场景是服务端单向推送生成文本，SSE 足够简单；如果未来有双向实时交互，再考虑 WebSocket。

对应位置：`DocumentQaController`、`DocumentQaServiceImpl`、`frontend/lib/qa-api.ts`。

### Q11：流式失败怎么办？

面试可背版回答：前端保留普通问答降级路径，流式异常时可以降级普通问答，避免用户只看到失败。

面试官追问：降级是否会重复扣费？

诚实边界：真实 provider 模式下需要控制重试和费用；当前项目更偏工程演示，费用治理不是完整生产能力。

对应位置：`frontend/app/documents/[documentId]/page.tsx`、`AiRetryExecutor`。

### Q12：引用是怎么展示的？

面试可背版回答：问答服务会返回答案和 citations，前端展示引用片段，让用户知道回答依据来自哪些文档内容。

面试官追问：引用一定准确吗？

诚实边界：不能保证绝对准确，但现在已经不只是展示 snippet。项目有 quote-level citation、no-evidence、answer grounding、answer faithfulness、干扰 citation 检查和真实 audit 回归；KnowledgeBase RAG 还有默认关闭的 Hybrid / Rerank 可选增强。更大规模语料、人工标注和通用 entailment 仍需后续建设。

对应位置：`DocumentQaServiceImpl`、`frontend/app/documents/[documentId]/page.tsx`。

## 5. Agent 设计

### Q13：Agent 在项目里做什么？

面试可背版回答：当前 Agent 是文档业务工具链：根据用户任务选择状态、摘要或问答工具，执行后返回答案、引用、路由解释和 taskId。

面试官追问：这算不算多 Agent？

诚实边界：不算。它是最小 Agent / 工具链演示，不是成熟多 Agent 编排。

对应位置：`DocumentAgentController`、`DocumentAgentServiceImpl`、`AgentTool`。

### Q13-1：这和 LangChain / LangGraph / Dify 有什么区别？

面试可背版回答：我没有一开始接框架，而是先把 Agent 的基础设施自己做出来：ToolRegistry、ToolSelector、ToolDefinition、AgentTask / AgentStep trace、前端 Showcase。这样可以看清楚工具选择、执行轨迹、引用证据和失败边界，后续再接 LangChain / LangGraph 时也能判断它们的 tracing 和 tool abstraction 是否满足项目需求。

面试官追问：是不是重复造轮子？

诚实边界：是有取舍的。我的目标不是替代框架，而是通过一个小项目理解 Agent 工程的底层问题；真正生产化时可以引入成熟框架，但要保留自己的业务工具、权限、trace 和降级边界。

对应位置：`ToolRegistry`、`ToolDefinitionProvider`、`AgentTaskPersistenceService`、`frontend/app/agent/page.tsx`。

### Q14：AgentTask / AgentStep 为什么要落库？

面试可背版回答：落库可以保留每次 Agent run 的状态、工具步骤和耗时，便于前端展示 trace，也便于后续排障。

面试官追问：落库失败怎么办？

诚实边界：当前是 best-effort 记录，核心回答链路优先；生产级还要更完整的事务边界和补偿策略。

对应位置：`AgentTaskPersistenceService`、`AgentTask`、`AgentStep`。

### Q15：Agent run 是同步还是异步？

面试可背版回答：当前 Agent run 是同步 API 链路。异步 Agent 方案已有设计文档，但没有实现。

面试官追问：为什么还不做异步？

诚实边界：完整 T010 解析链路还被 MQ 环境阻塞，优先把现有同步链路讲清楚更稳。

对应位置：`docs/AGENT_ASYNC_DESIGN.md`、`DocumentAgentServiceImpl`。

## 6. Tool selector / shadow mode

### Q16：Tool selector 怎么选工具？

面试可背版回答：默认 primary selector 是 `DocumentToolSelector`，基于任务关键词和文档状态选择 status、summary、QA 或 RAG 召回工具，并返回 routingReason 和 matchedKeywords。显式开启 `llm_execute` 后，LLM selector 可以选择 allowlist 内工具，但服务端仍负责执行已有工具。

面试官追问：规则会不会太简单？

诚实边界：是的，它是可解释、可测试的默认基线。`llm_execute` 只是显式开关模式，不是默认生产行为；provider 或解析失败会回退 keyword selector。

对应位置：`DocumentToolSelector`、`ToolSelectorEvaluationTest`。

### Q17：shadow mode 是什么？

面试可背版回答：shadow mode 是在不改变生产决策的前提下，旁路运行 fake 或 real shadow selector，对比 primary / shadow decision 是否一致，并记录 metrics。

面试官追问：为什么不直接用 LLM 选择？

诚实边界：LLM 输出可能不稳定，先用 shadow compare 和阈值策略观察，避免直接影响线上行为。

对应位置：`RealLlmSelectorShadowRunner`、`SelectorMetricsCollector`。

### Q18：shadow decision 会不会影响真实回答？

面试可背版回答：shadow decision 不会。真实工具执行默认使用 primary `DocumentToolSelector` 的 decision，shadow decision 只用于 compare、日志和 metrics。只有显式开启 `llm_execute` 时，LLM decision 才可能作为最终工具选择。

面试官追问：代码里怎么保证？

诚实边界：默认路径仍读取 primary decision；`llm_execute` 路径会先校验 LLM 返回的 decision / toolName 是否在 `ToolRegistry` allowlist 内，再由服务端用已有上下文执行工具，失败则回退 keyword。

对应位置：`DocumentAgentServiceImpl`、`DocumentAgentServiceImplTest`。

### Q18-1：这是真 Function Calling 吗？

面试可背版回答：它已经具备默认关闭的 Function Calling / Tool Execution 工程形态：工具定义、prompt builder、LLM 输出 parser、OpenAI-compatible provider client、allowlist 校验和服务端工具执行都已打通。默认仍是 keyword selector，`llm_execute` 需要显式开启；不能写成生产默认接管官方 tools/function_call。

面试官追问：为什么不直接让 LLM 决定工具？

诚实边界：因为 LLM tool selection 可能解析失败、选错工具或受 prompt 波动影响。所以 execute mode 默认关闭，并且只允许已注册工具名；模型不能生成代码，不能决定任意参数，provider 失败会 fail-open 回到 keyword selector。

对应位置：`ToolDefinitionProvider`、`LlmToolSelectionPromptBuilder`、`LlmToolSelectionParser`、`ToolExecutionDecision`、`DocumentAgentLlmExecuteModeTest`。

### Q18-1-1：Prompt Engineering 怎么做的？

面试可背版回答：selector prompt 不让模型回答业务内容，只让它在已注册工具中选择一个 decision，并返回固定 JSON。后端 parser 校验 JSON、decision、toolNames 和 confidence，service 再用 ToolRegistry allowlist 做二次校验，最后由服务端使用已有上下文执行工具。

面试官追问：为什么还要 fallback？

诚实边界：模型可能返回非法 JSON、未知工具、decision 和 toolNames 冲突，provider 也可能 timeout。任何这类失败都会 fail-open 回退 keyword selector，不影响 Agent API 可用性。

对应位置：`docs/PROMPT_ENGINEERING_NOTES.md`、`LlmToolSelectionPromptBuilder`、`LlmToolSelectionParser`。

### Q18-2：这是完整 RAG 吗？

面试可背版回答：不是线上级完整 RAG，但已经是求职级可讲清楚的知识库 RAG 工程闭环。项目已覆盖 chunk 持久化、EmbeddingProvider 抽象、Qdrant adapter、真实 embedding + Qdrant smoke、单文档 / 多文档 KnowledgeBase retrieval / QA、metadata scope filter、quote-level citation、no-evidence、answer grounding、hard negative、answer faithfulness、Conversation Trace、Memory 分层和真实链路质量门禁。

面试官追问：那它还缺什么？

诚实边界：缺线上治理、固定 SLA、大规模人工标注和通用语义蕴含 scorer；离线 eval 仍有 mock embedding + in-memory vector store 边界，真实 embedding + Qdrant 是小规模 smoke 证据，KnowledgeBase Hybrid / Rerank 目前是默认关闭的可选增强。

对应位置：`RagIndexingServiceImpl`、`RagDocumentRetrievalServiceImpl`、`QdrantVectorStoreClient`、`docs/showcase/DEMO_SMOKE_RECORD.md`。

### Q18-3：Agent Quality Console 是什么，为什么有价值？

面试可背版回答：它是项目内部质量控制台，不是普通用户功能。它聚合真实 audit / eval artifact，展示最近 run、Gate 状态、Eval Catalog、Failure Triage、Trace Reference、Run Comparison 和 token / cost 数值。它的价值是把“AI 回答质量问题”从口头感觉变成可复现的质量闭环。

面试官追问：有没有真实发现过问题？

诚实边界：有。2026-07-05 真实审计首轮发现 Quality Eval Catalog 构造器注入缺失，导致 backend health BLOCKED；修复后复跑真实用户 QA 审计 `docpilot-real-user-qa-20260705165151-bbe588` PASS，`/quality?autoload=1` 能看到最新 run、Eval Catalog、Failure Triage、Run Comparison 和 Model / Cost Summary。边界是当前仍基于 ignored artifact 聚合，不是企业级 APM，也不保存 prompt、answer 原文、文档全文或 evidence context。

对应位置：`backend/src/main/java/com/docpilot/backend/quality`、`frontend/app/quality/page.tsx`、`docs/showcase/DEMO_SMOKE_RECORD.md`。

## 7. 真实 LLM provider shadow-only

### Q19：真实 provider 接入到什么程度？

面试可背版回答：项目支持 OpenAI-compatible 风格 provider client，并在用户授权下做过真实回答模型 smoke 和 selector shadow-only 验证。当前也实现了默认关闭的 `llm_execute` 模式，但真实 provider 调用必须显式配置、显式授权并遵守日志脱敏边界。

面试官追问：是不是生产默认会调用真实模型？

诚实边界：不是。真实 provider 调用需要显式配置和授权，默认不会调用；不要把它说成生产默认真实模型链路。

对应位置：`OpenAiCompatibleLlmToolSelectionClient`、`RealLlmToolSelectorFactory`。

### Q20：真实调用有没有安全风险？

面试可背版回答：有，所以文档明确要求不输出 API Key、完整连接地址、prompt、文档内容或模型完整返回。`llm_execute` 测试也覆盖了 fallback 响应不泄露 prompt、文档正文或 secret marker。

面试官追问：如果日志泄露怎么办？

诚实边界：当前只做了脱敏边界和安全日志设计；生产还需要统一日志脱敏、权限和审计体系。

对应位置：`docs/REAL_PROVIDER_SHADOW_PREFLIGHT.md`、`docs/AGENT_SELECTOR_SHADOW_MODE.md`。

### Q21：provider=fake 有什么价值？

面试可背版回答：fake provider 不联网、不读密钥，适合测试 shadow runner、parser 和 metrics 链路，降低真实模型波动和费用影响。

面试官追问：fake 能代表真实模型吗？

诚实边界：不能完全代表。fake 是工程链路验证，真实 provider shadow-only 才能观察真实模型行为。

对应位置：`FakeLlmToolSelectionClient`、`RealShadowProviderEvaluationTest`。

## 8. metrics / debug dump / Actuator endpoint

### Q22：selector metrics 记录什么？

面试可背版回答：记录 total、success、failure、matched、mismatch、matchRate、failureRate、provider 聚合和 primary / shadow decision pair 聚合。

面试官追问：记录用户输入吗？

诚实边界：不记录 prompt、task、文档内容、模型完整返回、API Key、baseUrl 或 Authorization。

对应位置：`SelectorMetricsCollector`、`SelectorMetricsSnapshot`。

### Q23：debug dump 有什么作用？

面试可背版回答：debug dump 是内部只读快照，用来查看 selector shadow metrics 和 threshold decision，不新增外部 API。

面试官追问：为什么不直接开放 API？

诚实边界：因为管理端鉴权、内网边界和脱敏策略还没完全实现，贸然开放会有安全风险。

对应位置：`SelectorMetricsDebugReporter`、`SelectorMetricsDebugSnapshot`。

### Q24：Actuator endpoint 现在能访问吗？

面试可背版回答：默认不能访问。`agentSelectorShadow` endpoint 使用 `enableByDefault=false`，测试内显式开启验证过 200，但生产未开启。

面试官追问：为什么不接 Spring Security？

诚实边界：T030 preflight 发现项目缺少 Spring Security Web 鉴权体系，本轮不建议为了测试硬加依赖。

对应位置：`AgentSelectorShadowEndpoint`、`AgentSelectorShadowEndpointEnabledTest`。

## 9. 当前边界如何解释

### Q25：还有哪些不能夸大？

面试可背版回答：不能写生产级完整向量 RAG、多 Agent、MCP、生产级权限、Prometheus 已接入、LLM selector 已在生产默认启用或线上 SLA。

面试官追问：那现在最强证据是什么？

诚实边界：最强证据是真实 smoke / audit record：单文档 RAG、多文档 KnowledgeBase RAG、Conversation Trace、Memory quality、Agent Quality Console、MinIO active storage、RocketMQ + Outbox active parse、真实回答模型、真实 embedding + Qdrant 和权限越界失败案例。

对应位置：`docs/showcase/DEMO_SMOKE_RECORD.md`。

### Q26：T030 BLOCKED 怎么解释？

面试可背版回答：它是安全测试 blocker，不是业务功能 blocker。当前项目只有 `spring-security-crypto`，没有 Web 鉴权体系，所以不能可靠验证 401 / 403 / 角色访问。

面试官追问：为什么不马上加依赖？

诚实边界：安全体系会影响全局访问行为，应该单独设计和验证，不能为了一个测试随手引入。

对应位置：`docs/CHANGELOG_CODING.md`、`backend/pom.xml`。

### Q27：你如何避免简历夸大？

面试可背版回答：我把能力分成已 smoke 验证、离线 eval 验证、默认关闭能力和设计中能力。比如 Prometheus、Spring Security、生产 Actuator 暴露都只能写成设计或预留，不能写成已完成。

面试官追问：哪些不能写？

诚实边界：不能写生产级完整向量 RAG、多 Agent、MCP、生产级权限、Prometheus 已接入、LLM selector 已在生产启用。

对应位置：`docs/RESUME_BULLETS.md`、`docs/PROJECT_INTERVIEW_BRIEF.md`。

## 10. 项目不足和后续优化

### Q28：项目最大不足是什么？

面试可背版回答：最大不足不是“链路没跑通”，而是质量证据还偏小样本：真实 audit / eval 已能发现和回归问题，但还不是大规模人工评测或线上 SLA；另外 Spring Security / Prometheus / 企业级 APM 仍不是当前能力。

面试官追问：优先优化哪个？

诚实边界：优先继续扩大 RAG / Memory 的真实问答评测和 Trace drill-down，再评估是否引入持久化质量表；安全体系要另开任务，避免影响现有接口。

对应位置：`docs/ai-dev/ROADMAP_RAG.md`、`docs/ai-dev/ROADMAP_AGENT_QUALITY_CONSOLE.md`。

### Q29：如果继续做，你下一步做什么？

面试可背版回答：我会继续沿 Agent Quality Console 和 RAG / Memory 质量路线做：更细的 Trace drill-down、Eval case 版本化、更多真实用户问答审计 case、Memory provider 小样本扩容，以及必要时再评估质量结果持久化。

面试官追问：为什么不是接 Prometheus？

诚实边界：Prometheus 已有设计，继续接入前应先解决更关键的主链路和安全边界。

对应位置：`docs/AGENT_SELECTOR_PROMETHEUS_METRICS_DESIGN.md`、`docs/TODO_NEXT.md`。

### Q30：这个项目如何体现你的成长？

面试可背版回答：它让我不只关注功能跑通，还关注异步可靠性、幂等、降级、可观测、测试证据和诚实边界。尤其是 Agent shadow mode，让我学会先用旁路验证降低 AI 决策接管风险。

面试官追问：你最想重构哪块？

诚实边界：我会先治理历史文档和 eval 记录，再完善 MQ 环境验证和安全鉴权，而不是直接重构业务代码。

对应位置：`docs/CODEX_HANDOFF.md`、`docs/PROJECT_INTERVIEW_BRIEF.md`。
