# DocPilot Resume Bullets

本文档给出 DocPilot 在不同投递场景下的简历写法。所有 bullet 都只基于当前真实代码或已提交文档，不把 BLOCKED、设计中或未接入能力写成已完成。

## 版本一：保守版

项目名称：DocPilot AI 文档问答平台

项目描述：基于 Spring Boot + Next.js 的文档上传、轻量检索增强问答和最小 Agent 工具链演示项目。

- 负责后端文档上传、文档创建、解析任务与问答链路开发，使用 MyBatis-Plus 管理 MySQL 持久化，并通过 MinIO 支持文件对象存储和分片上传。
- 实现 AI 文档问答和 SSE 流式响应，支持文档片段引用展示、历史问答和流式失败降级，提升问答交互体验。
- 实现最小 Agent 工具链闭环，抽象状态查询、摘要、问答三类文档工具，并通过规则 selector 输出可解释路由信息。
- 设计并接入 AgentTask / AgentStep 持久化，记录 Agent run 和工具步骤，支持按 taskId 查询执行轨迹并在前端展示。
- 补充后端单元测试、Agent / RAG / MinIO / RocketMQ smoke 和前端 lint/build 验证，沉淀展示证据并明确项目能力边界。

## 版本二：标准后端实习版

项目名称：DocPilot AI 文档平台

项目描述：一个覆盖文件上传、异步解析、文档问答、SSE 流式输出和 Agent trace 的 Java 后端项目。

- 基于 Spring Boot 3、MyBatis-Plus、MySQL 设计文档、解析任务、问答历史、AgentTask / AgentStep 等核心表和服务分层。
- 设计并验证 Outbox + RocketMQ 异步解析链路，配合 Redisson 分布式锁、消费记录和补偿扫描，降低消息丢失、重复消费和并发重复创建任务风险。
- 使用 Redis 支撑文档详情缓存、问答缓存、会话上下文和令牌桶限流，提升热点链路稳定性。
- 实现普通问答与 SSE 流式问答双模式，前端可展示流式回答、引用片段和失败降级后的普通问答结果。
- 实现 Agent 工具注册、规则 selector、可解释路由和持久化执行轨迹，支持从 API 到前端页面查看工具调用步骤。

注意：RocketMQ + Outbox 已完成演示环境 active smoke，但简历中仍应写成“演示环境验证通过”，不要包装成线上生产 SLA。

## 版本三：AI 应用工程化版

项目名称：DocPilot Java + AI 文档问答与 Agent 工程化项目

项目描述：围绕文档问答和最小 Agent 场景，探索 AI 应用的检索增强、流式输出、工具路由、shadow 验证和可观测性治理。

- 构建 RAG 文档问答链路：文档切分、chunk 持久化、EmbeddingProvider 抽象、Qdrant VectorStore adapter、metadata scope filter、上下文组装、模型回答和 citations 引用展示，并支持 mock / OpenAI-compatible 风格 provider。
- 实现 SSE 流式问答和前端事件解析，覆盖 meta / chunk / done / error 等事件，支持流式失败降级普通问答。
- 实现最小 Agent 工具链：ToolRegistry 注册文档状态、摘要、问答工具，DocumentToolSelector 根据任务选择工具，并返回 routingReason / matchedKeywords。
- 实现默认关闭的 LLM 工具选择执行模式，通过 allowlist 校验模型返回的 toolName，并由服务端执行 summary / QA / RAG 等工具；支持 provider 失败回退规则路由，避免模型输出直接影响默认链路。
- 设计 selector shadow mode，在不改变生产 routing 的前提下旁路比较 primary / shadow decision，记录 match / mismatch、provider 聚合和 threshold policy。
- 完成真实 provider shadow-only 验证和安全观测方案：默认 disabled，真实调用需显式授权；debug dump 和默认关闭 Actuator endpoint 只输出聚合指标，不输出 prompt、文档内容、模型原文或敏感凭据。

## 版本四：AI Agent / RAG 实习投递版

项目名称：DocPilot AI Agent 文档问答 Demo

项目描述：面向文档问答场景的 Java + Next.js AI Agent 展示项目，已实现工具选择、执行轨迹和引用证据展示，并规划最小向量 RAG 演进路线。

- 实现 Agent Showcase 页面，支持选择已解析文档并展示工具决策、routingReason、matchedKeywords、taskId、持久化 steps、最终回答和 citations。
- 增强 Agent Workflow 展示：基于已有 run response 和 persisted trace 生成“接收任务、选择工具、执行工具、生成结果、持久化 trace”的 timeline，便于面试演示 Agent 执行链路。
- 抽象 ToolRegistry / ToolSelector / ToolDefinition，为文档状态、摘要、问答工具提供统一注册、规则路由和未来 Function Calling 输出协议。
- 设计并验证 real provider shadow-only 路径，并实现默认关闭的 `llm_execute` 模式；LLM 只能选择 ToolRegistry allowlist 内工具，服务端负责实际执行和失败回退。
- 沉淀 Tool Selection Engineering 证据链，覆盖 prompt 模板结构、JSON 输出协议、parser 校验、allowlist、fallback 和非法 JSON / 未知工具 / provider timeout 等 bad cases。
- 实现单文档 / 多文档 RAG 检索问答链路，支持可配置文档切块、chunk 持久化、真实 embedding + Qdrant smoke、topK 片段、相似度分数、引用元数据、检索 scope 隔离、脱敏 trace / debug snapshot、index lifecycle 和离线 retrieval eval。
- 构建默认关闭的 QA RAG context feature flag，开启后可向 QA 注入受限 RAG context，并通过 fallback / cache key 隔离测试验证默认 QA 行为不变。
- 通过 Maven 测试、前端 lint/build、Agent / ToolCall / KnowledgeBase RAG / MinIO / RocketMQ / 真实 embedding smoke 记录沉淀验证证据，明确 Function Calling 未默认接管和非生产级完整 RAG 的边界。

可写成“设计了”的能力：

- 设计了 Spring Security / Actuator 安全集成方案，但当前未实现 Spring Security。
- 设计了 selector shadow Prometheus metrics 方案，但当前未接 Prometheus。
- 预留并实现默认关闭的 Actuator endpoint，但当前未生产开启。

禁止写成已完成：

- Prometheus 已接入 selector metrics。
- Spring Security 已保护 Actuator endpoint。
- Actuator endpoint 已在生产暴露。
- shadow decision 已接管 production routing。
- 生产级完整向量 RAG、多 Agent 编排、MCP 或生产环境已启用 LLM function calling。

面试可讲但简历不建议硬写：

- 可以讲“已完成 Function Calling 风格工具抽象、输出协议和默认关闭的 LLM 工具执行模式”，但不要写“真实 Function Calling 已在生产启用”。
- 可以讲“已完成单文档 / 多文档 RAG、EmbeddingProvider 抽象、Qdrant adapter、真实 embedding + Qdrant smoke、scope isolation、citations、trace、offline eval，以及默认关闭的 KnowledgeBase Hybrid / Rerank 可选增强”，但不要写“生产级完整向量 RAG / rerank / hybrid search 已上线”。
- 可以讲“Actuator / Prometheus 有设计和默认关闭 endpoint”，但不要写“生产可观测体系已上线”。
