# DocPilot Project Interview Brief

本文档面向 Java 后端实习 / AI 应用工程化面试，记录 DocPilot 当前真实能力、边界和推荐讲法。它只总结当前仓库事实，不把 BLOCKED、设计中或未接入能力写成已完成。

## 1. 一句话定位

DocPilot 是一个基于 Java Spring Boot + Next.js 的 AI 文档平台，覆盖文件上传、异步解析、轻量检索增强问答、SSE 流式输出和最小 Agent 工具链演示。

更克制的面试讲法：这是一个展示后端工程链路和 AI 应用工程化意识的项目，不是生产级 SaaS、完整向量 RAG 平台或成熟多 Agent 系统。

面向 AI Agent 实习岗位的讲法：DocPilot 当前已经具备可演示的 Agent 工具选择、执行轨迹、引用证据和 RAG 召回展示；真实 embedding / 向量库、真实 Function Calling takeover 和 Workflow 增强是接下来的求职展示冲刺方向。

## 2. 当前真实已实现能力

- 账号密码注册 / 登录、文档上传、文档创建、文档列表和详情页。
- 基于 Outbox + RocketMQ 设计的异步解析链路，包含解析任务、消费幂等、Redisson 分布式锁和补偿思路。
- MySQL 持久化、Redis 缓存 / 限流 / 会话上下文、MinIO 对象存储和分片上传。
- 轻量检索增强问答：基于文档内容切分、关键词检索、上下文组装、AI 回答和引用展示。
- SSE 流式问答，前端支持流式事件解析、引用展示和失败降级。
- 最小 Agent 演示：`DocumentToolSelector` 按规则选择状态 / 摘要 / 问答工具，Agent run 返回可解释路由信息。
- AgentTask / AgentStep 持久化：记录 Agent run 和工具步骤，支持按 taskId 查询并在前端展示 trace。
- Tool selector shadow mode：primary selector 与 fake / real shadow selector 可旁路比较，不改变生产 routing。
- 真实 provider shadow-only 验证：在用户授权下完成 OpenAI-compatible provider 的 summary / QA shadow 调用验证，shadow decision 未接管 production routing。
- selector shadow metrics、threshold policy、内部 debug dump / reporter。
- 默认关闭的 `agentSelectorShadow` Actuator endpoint，测试内显式开启已验证 200、字段白名单 / 黑名单和只读边界。
- Agent + RAG Showcase：`/agent` 页面已通过 runtime 验证，`rag_tool` 能展示 retrieved chunk、score / similarity、citation metadata、routingReason、matchedKeywords 和 persisted steps；普通 QA 路径仍展示 citations。

## 3. 当前半实现能力

- RAG Showcase 当前使用 fake embedding + in-memory vector store，不是向量数据库 + 真实 embedding + rerank 的完整生产 RAG。
- PDF 支持偏占位，主能力更适合 txt / md 文档。
- Agent 是同步 API 下的最小工具链闭环，不是异步多 Agent 编排。
- LLM selector / real provider 路径只用于 shadow-only 观察，尚未接管生产工具选择。
- selector metrics 当前主要是内存态和 debug dump；Actuator endpoint 默认关闭，Prometheus 仅有设计文档。
- eval / benchmark 有 artifact 和脚本基础，但仍需补运行时配置记录和重跑验证。

## 4. 当前 BLOCKED 能力

- T010 完整上传解析链路：BLOCKED，原因是当前 MQ disabled / `NoopParseTaskMessageProducer` 模式下不会推进真实异步解析，完整上传 -> 解析 -> Agent run 链路需要可用 MQ / 解析消费环境。
- T030 鉴权测试：BLOCKED，原因是项目当前只有 `spring-security-crypto`，缺少 Spring Security Web 鉴权体系、`spring-security-test` 和 `SecurityFilterChain`。

## 5. 不能写成已完成的能力

- Prometheus 未接入；T032 只是 selector shadow Prometheus metrics 设计文档。
- Spring Security 未接入；T029 只是 Spring Security / Actuator 安全集成设计。
- Actuator endpoint 默认关闭，未生产开启，未加入默认 exposure include。
- shadow decision 未接管 production routing，真实工具执行仍由 primary `DocumentToolSelector` 决定。
- 没有完整向量 RAG、MCP、LangChain4j / Spring AI function calling 或成熟多 Agent 编排。
- 没有线上 SLA、生产权限体系或生产短信网关。

## 6. 最适合写进简历的 5 个工程亮点

1. 设计并实现文档上传后的异步解析链路，结合 Outbox、RocketMQ、Redisson 分布式锁和幂等消费，降低同步阻塞和重复消费风险。
2. 实现普通问答与 SSE 流式问答链路，支持引用片段展示、历史问答和流式失败降级，提升 AI 文档问答体验。
3. 实现最小 Agent 工具链闭环，抽象 ToolRegistry / ToolSelector，并将 AgentTask / AgentStep 落库，支持可解释路由和持久化 trace。
4. 构建 selector shadow mode：在不改变生产 routing 的前提下，对比 primary / shadow decision，记录 match / mismatch、provider 聚合和 threshold policy。
5. 完成真实 provider shadow-only 验证和安全观测设计，包含脱敏日志、debug dump、默认关闭 Actuator endpoint 和 Prometheus metrics 设计边界。

## 7. 求职展示优先级

1. 先展示 `/agent` Agent Showcase：文档选择、任务模板、工具决策、routingReason、matchedKeywords、taskId、steps 和 citations。
2. 再展示详情页普通问答 / SSE 流式问答：说明 citations 如何来自轻量检索增强。
3. 对 RAG 保持诚实：当前已能展示 fake embedding + in-memory vector store 的 topK 召回、score 和 metadata；下一步才是接真实 embedding、chunk 持久化和 Qdrant / Redis Vector。
4. 对 Function Calling 保持诚实：当前有工具定义、prompt、parser、real provider shadow-only；生产工具执行仍未交给 LLM decision。
5. 面试时不要优先讲 Actuator / Prometheus / Spring Security，除非面试官追问可观测性或安全边界。

## 8. 面试高风险追问与诚实回答

### 你这个是完整 RAG 吗？

不是。当前已做出求职展示用的最小 RAG demo：fake embedding、in-memory vector store、topK 召回、score 和 metadata 展示；但还没有真实 embedding provider、Qdrant / Redis Vector、chunk 持久化和 rerank。我会把它描述为 RAG demo 或轻量检索增强演进，不会包装成生产完整向量 RAG。

### Agent 是多 Agent 吗？

不是。当前是单 Agent / 工具链演示，重点在工具注册、规则选择、可解释路由、执行步骤和持久化 trace。多 Agent 编排、长期记忆和工具市场都没有实现。

### LLM selector 是否已经接管生产路由？

没有。生产真实工具执行仍由 primary `DocumentToolSelector` 决定。fake / real shadow selector 只做旁路比较、日志和 metrics，不改变 API 返回和 production routing。

### 真实 provider 调用是否会泄露敏感信息？

真实 provider shadow-only 验证只在用户授权下运行，协作过程未读取或输出 API Key、完整连接地址、prompt、文档内容或模型完整返回。默认配置仍是 disabled，后续再次调用必须重新确认。

### Actuator endpoint 是否已经能上线使用？

不能这么说。`agentSelectorShadow` endpoint 已实现但 `enableByDefault=false`，默认关闭；测试内显式开启验证过 200 和字段安全边界，但没有生产开启，也没有 Spring Security 保护。

### Prometheus 是否已经接入 selector metrics？

没有。当前只是设计了未来指标名、低风险 label 和禁止字段。实际 Micrometer / Prometheus 接入还没做。

### 为什么完整 T010 还是 BLOCKED？

完整 T010 要验证上传后真实异步解析推进到 Agent run，但当前 MQ disabled / no-op producer 模式下不会发送解析消息，所以 parseStatus 会卡住。已解析文档上的 Agent-only lite 链路验证通过，但它不能替代完整上传解析链路。

### 为什么 T030 鉴权测试 BLOCKED？

项目目前没有 Spring Security Web 鉴权体系，只有 `spring-security-crypto`。在不允许新增依赖和不允许新增生产安全配置的边界下，不能硬做 401 / 403 / 角色访问测试。
