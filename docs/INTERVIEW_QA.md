# DocPilot Interview QA

本文档面向 Java 后端实习面试，整理 DocPilot 高频问题回答。回答重点是后端工程、AI 应用工程化和真实边界，不以算法工程师视角夸大能力。

## 1. 项目整体介绍

### Q1：请用一分钟介绍 DocPilot。

面试可背版回答：DocPilot 是一个 Java Spring Boot + Next.js 的 AI 文档平台，覆盖文件上传、异步解析、轻量检索增强问答、SSE 流式输出和最小 Agent 工具链。后端重点在 Outbox + RocketMQ、Redisson 幂等锁、Redis 缓存限流、MinIO 对象存储和 Agent trace 持久化。

面试官追问：它和普通 CRUD 项目相比有什么工程价值？

诚实边界：它不是生产级 SaaS，也不是完整向量 RAG，价值在于把中间件、AI 调用、流式输出、可观测性和验证文档串成一个可讲清楚的工程闭环。

对应位置：`backend/src/main/java/com/docpilot/backend`、`frontend/app`、`docs/PROJECT_INTERVIEW_BRIEF.md`。

### Q2：项目最核心的技术亮点是什么？

面试可背版回答：我会讲五点：异步解析链路、SSE 问答、Agent 工具链和持久化 trace、selector shadow compare、默认关闭的安全观测入口设计。

面试官追问：哪个最能体现后端能力？

诚实边界：最能体现后端能力的是 Outbox + RocketMQ + 幂等消费，以及 AgentTask / AgentStep 的执行轨迹持久化。

对应位置：`backend/src/main/java/com/docpilot/backend/mq`、`backend/src/main/java/com/docpilot/backend/ai/agent`。

### Q3：你在项目中最关注什么？

面试可背版回答：我关注的是“AI 功能怎么工程化落地”：失败降级、幂等、可观测、可复现测试、边界文档和不夸大能力。

面试官追问：为什么不继续堆功能？

诚实边界：当前阶段更适合把真实链路讲清楚，继续堆 Prometheus 或 Spring Security 之前应先解决 T010 / T030 的 blocker。

对应位置：`docs/TODO_NEXT.md`、`docs/CODEX_HANDOFF.md`。

## 2. 文档上传与解析

### Q4：文档上传链路怎么设计？

面试可背版回答：前端上传文件，后端通过 FileController 和 FileService 保存文件记录，再通过 FileStorageWriter 写入本地或 MinIO。大文件场景支持分片上传会话、分片状态和合并完成。

面试官追问：为什么引入 MinIO？

诚实边界：MinIO 用于对象存储演示和大文件上传链路，不代表已做生产级对象存储治理。

对应位置：`backend/src/main/java/com/docpilot/backend/file`、`frontend/app/upload/page.tsx`。

### Q5：文档解析任务怎么触发？

面试可背版回答：用户创建文档后会创建 ParseTask，并通过 Outbox / RocketMQ 推进异步解析。解析结果更新文档状态和内容片段。

面试官追问：现在完整链路是否已验证？

诚实边界：完整上传 -> 解析 -> Agent run 的 T010 当前 BLOCKED，因为 MQ disabled / Noop producer 模式下不会发送解析消息。

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

### Q9：T010 为什么 BLOCKED？

面试可背版回答：T010 要验证完整上传解析链路，但当前运行环境是 MQ disabled / `NoopParseTaskMessageProducer`，不会真正投递解析消息，所以 parseStatus 不会推进。

面试官追问：那项目是不是不能演示？

诚实边界：完整上传解析链路暂时不能说通过，但已解析文档上的 Agent-only lite 验证通过，可以演示 Agent run、路由解释、trace 和 citations。

对应位置：`NoopParseTaskMessageProducer`、`docs/TODO_NEXT.md`。

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

诚实边界：不能保证绝对准确；当前是轻量检索增强，不是完整语义检索和 rerank，需要 eval 和更多样本持续校准。

对应位置：`DocumentQaServiceImpl`、`frontend/app/documents/[documentId]/page.tsx`。

## 5. Agent 设计

### Q13：Agent 在项目里做什么？

面试可背版回答：当前 Agent 是文档业务工具链：根据用户任务选择状态、摘要或问答工具，执行后返回答案、引用、路由解释和 taskId。

面试官追问：这算不算多 Agent？

诚实边界：不算。它是最小 Agent / 工具链演示，不是成熟多 Agent 编排。

对应位置：`DocumentAgentController`、`DocumentAgentServiceImpl`、`AgentTool`。

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

面试可背版回答：primary selector 是 `DocumentToolSelector`，基于任务关键词和文档状态选择 status、summary 或 QA 工具，并返回 routingReason 和 matchedKeywords。

面试官追问：规则会不会太简单？

诚实边界：是的，它是可解释、可测试的基线；后续 LLM selector 只在 shadow mode 里比较，不直接接管生产。

对应位置：`DocumentToolSelector`、`ToolSelectorEvaluationTest`。

### Q17：shadow mode 是什么？

面试可背版回答：shadow mode 是在不改变生产决策的前提下，旁路运行 fake 或 real shadow selector，对比 primary / shadow decision 是否一致，并记录 metrics。

面试官追问：为什么不直接用 LLM 选择？

诚实边界：LLM 输出可能不稳定，先用 shadow compare 和阈值策略观察，避免直接影响线上行为。

对应位置：`RealLlmSelectorShadowRunner`、`SelectorMetricsCollector`。

### Q18：shadow decision 会不会影响真实回答？

面试可背版回答：不会。真实工具执行永远使用 primary `DocumentToolSelector` 的 decision，shadow decision 只用于 compare、日志和 metrics。

面试官追问：代码里怎么保证？

诚实边界：`DocumentAgentServiceImpl` 的真实工具执行路径仍读取 primary decision；相关测试覆盖了 production routing 不变。

对应位置：`DocumentAgentServiceImpl`、`DocumentAgentServiceImplTest`。

## 7. 真实 LLM provider shadow-only

### Q19：真实 provider 接入到什么程度？

面试可背版回答：项目支持 OpenAI-compatible 风格 provider client，并在用户授权下做过 summary / QA 的 shadow-only 验证。默认配置仍是 disabled。

面试官追问：是不是生产默认会调用真实模型？

诚实边界：不是。真实 provider 调用需要显式配置和授权，默认不会调用。

对应位置：`OpenAiCompatibleLlmToolSelectionClient`、`RealLlmToolSelectorFactory`。

### Q20：真实调用有没有安全风险？

面试可背版回答：有，所以文档明确要求不输出 API Key、完整连接地址、prompt、文档内容或模型完整返回。真实 provider shadow-only 也不改变 production routing。

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

## 9. 当前 BLOCKED 点如何解释

### Q25：T010 BLOCKED 会影响项目价值吗？

面试可背版回答：影响完整端到端验证口径，但不否定已实现模块。当前已解析文档上的 Agent-only 链路能展示问答、路由解释和 trace；完整上传解析链路需要 MQ 环境恢复后再验。

面试官追问：你会怎么修？

诚实边界：先只读确认 producer / consumer / profile 条件，再恢复 RocketMQ enabled 和 NameServer 环境，不会用硬编码 documentId 掩盖问题。

对应位置：`NoopParseTaskMessageProducer`、`ParseTaskMessageConsumer`。

### Q26：T030 BLOCKED 怎么解释？

面试可背版回答：它是安全测试 blocker，不是业务功能 blocker。当前项目只有 `spring-security-crypto`，没有 Web 鉴权体系，所以不能可靠验证 401 / 403 / 角色访问。

面试官追问：为什么不马上加依赖？

诚实边界：安全体系会影响全局访问行为，应该单独设计和验证，不能为了一个测试随手引入。

对应位置：`docs/CHANGELOG_CODING.md`、`backend/pom.xml`。

### Q27：你如何避免简历夸大？

面试可背版回答：我把能力分成已实现、半实现、BLOCKED 和设计中。比如 Prometheus、Spring Security、生产 Actuator 暴露都只能写成设计或预留，不能写成已完成。

面试官追问：哪些不能写？

诚实边界：不能写完整向量 RAG、多 Agent、生产级权限、Prometheus 已接入、LLM selector 已接管路由。

对应位置：`docs/RESUME_BULLETS.md`、`docs/PROJECT_INTERVIEW_BRIEF.md`。

## 10. 项目不足和后续优化

### Q28：项目最大不足是什么？

面试可背版回答：最大不足是完整上传解析链路在当前环境下还没重新跑通，另一个是 Actuator 鉴权体系还停留在设计阶段。

面试官追问：优先优化哪个？

诚实边界：优先恢复 MQ 解析链路，因为它影响主业务闭环；安全体系要另开任务，避免影响现有接口。

对应位置：`docs/TODO_NEXT.md`。

### Q29：如果继续做，你下一步做什么？

面试可背版回答：我会先做 T010m 只读 MQ readiness check，确认 no-op producer 生效条件和恢复 MQ 所需环境；或者做 T030-design-review，收窄鉴权验证方案。

面试官追问：为什么不是接 Prometheus？

诚实边界：Prometheus 已有设计，继续接入前应先解决更关键的主链路和安全边界。

对应位置：`docs/AGENT_SELECTOR_PROMETHEUS_METRICS_DESIGN.md`、`docs/TODO_NEXT.md`。

### Q30：这个项目如何体现你的成长？

面试可背版回答：它让我不只关注功能跑通，还关注异步可靠性、幂等、降级、可观测、测试证据和诚实边界。尤其是 Agent shadow mode，让我学会先用旁路验证降低 AI 决策接管风险。

面试官追问：你最想重构哪块？

诚实边界：我会先治理历史文档和 eval 记录，再完善 MQ 环境验证和安全鉴权，而不是直接重构业务代码。

对应位置：`docs/CODEX_HANDOFF.md`、`docs/PROJECT_INTERVIEW_BRIEF.md`。
