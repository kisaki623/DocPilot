# Codex Handoff

本文是 DocPilot 当前版本给下一位 Codex / API agent / Claude Code 的简明交接。它只记录当前真实状态，不把规划写成已完成。

## 1. 项目当前真实状态

DocPilot 是一个 Java 后端 + Next.js 前端的 AI 文档平台。当前仓库已经具备文档上传、文档创建、异步解析、文档列表 / 详情、轻量检索增强问答、SSE 流式输出、引用展示、历史问答、账号密码认证和最小 Agent 演示链路。

项目仍处于作品展示与实习投递导向的持续打磨阶段。它可以展示工程化能力，但还不是生产级 SaaS，也不是完整向量 RAG / 多 Agent 平台。

截至 2026-05-14 当前交接记录同步时，`git status --short` 为空，工作区干净；后续接手仍必须每轮先检查 `git status` / `git diff`，避免覆盖用户本地改动。

## 2. 已经实现的功能

- 账号密码注册 / 登录，旧短信登录逻辑保留兼容口径。
- 文件上传、分片上传、对象存储落盘与文件记录。
- 文档创建、文档列表、文档详情、状态展示。
- 解析任务创建、Outbox、RocketMQ 异步消费、Redisson 锁与幂等保护。
- 文档内容轻量切分、关键词检索、上下文组装、引用返回。
- 普通问答与 SSE 流式问答。
- Markdown 展示、历史问答、引用证据面板。
- 最小 Agent 后端 / 前端演示入口，能展示输入、工具步骤、持久化执行轨迹与最终回答。
- Agent 执行痕迹持久化与查询 API，Agent run 返回 `taskId`，支持按 `taskId` 查询 task / steps。
- Agent 路由可解释性：`ToolSelector` 已支持 `routingReason` / `matchedKeywords`，后端 run 响应透出字段，前端 Agent 页面展示“路由决策”和命中关键词，smoke 脚本已增强对应断言。
- `docs/AGENT_ASYNC_DESIGN.md` 已新增异步 Agent 演进设计草案；它仅记录未来方案，当前未实现异步 Agent。
- eval / benchmark 脚本和 artifact 雏形，用于记录问答质量指标。
- Docker Compose demo 中间件编排。

## 3. 半实现 / 有边界的功能

- RAG 是轻量检索增强，主要依赖文本 chunk、关键词、上下文拼装，不是向量数据库 + embedding + rerank 的完整 RAG。
- PDF 解析不是当前主能力，txt / md 更可靠。
- Agent 是单 Agent / 工具链演示，不是多 Agent 协作系统。
- eval 数据集和门禁已经有基础，但质量指标、样本覆盖和判定规则仍需继续校准。
- 真实模型接入依赖环境变量和外部 provider，可使用 mock 模式保证本地演示。
- 前端页面已具备展示感，但仍有局部样式和交互稳定性可以继续打磨。

## 4. 未实现的功能

- 完整向量化索引、embedding 存储、语义 rerank。
- 生产级 PDF 结构化解析。
- 生产级短信网关、账号风控、权限体系。
- 多租户、团队协作、权限隔离。
- 完整 CI/CD、线上部署流水线和长期 SLA 监控。
- 完整多 Agent 编排、长期记忆、复杂工具市场。

## 5. 不确定 / 需要用户确认的地方

- 香港云中间件当前是否可稳定连通，需要实际联调时确认。
- 当前交接点 `git status --short` 为空；仍需按协作规则在每轮开始检查 `git status` / `git diff`。
- `docs/ai-dev/HANDOFF.md` 等历史文档存在乱码和阶段漂移，是否继续保留旧阶段文档需要用户确认。
- T001a 已定位当前权威 eval 基准为 `docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`：`answerSuccessRate=90%`、`citationHitRate=100%`、`caseCount/streamPairs=20/8`、`generatedAt=2026-04-18T18:58:42.2763129+00:00`、`datasetVersion=2026-04-19-r2`。T001b 已将 README / STATE / docs 准备统一到该 artifact。保留不确定项：artifact 未记录实际运行时 `AI_MODE`、模型名或 provider，且本轮未重跑 eval。
- Agent Demo 已通过 T003-fast-submit 提交：`25793ed feat(agent): add minimal document agent demo`，包含后端 `/api/ai/agent/run`、三类工具、前端 `/agent` 页面和 smoke 脚本。
- T004a 已新增 AgentTask / AgentStep 持久化骨架，包括 `tb_agent_task` / `tb_agent_step` DDL、Entity、Mapper 和 `AgentTaskPersistenceService`；尚未接入 `DocumentAgentServiceImpl`，尚未执行 DDL，也未接 MQ。
- T004b 已将 AgentTask / AgentStep 持久化接入 `DocumentAgentServiceImpl`：Agent run 会 best-effort 创建 task、记录 tool step、成功/失败更新 task 状态；尚未新增 task 查询 API，尚未执行 DDL。
- T004c 已新增 Agent task / step 查询 API：支持按 `taskId` 查询当前用户的 task 与 steps，未修改前端，未执行 DDL。
- T004d 已补充 Agent smoke 的 `taskId` 断言和协作文档收尾；smoke 脚本仅做语法/命令可解析检查，未实跑。
- T004e 已完成远程 DDL 与运行时 smoke：远程 `docpilot` 库存在 `tb_agent_task` / `tb_agent_step`，Agent smoke 返回 `taskId`，task / step 查询接口可用，hk-ops 只读 SELECT 确认远程库写入了本次 task / steps。
- T005a 已将 Agent 工具注册和工具选择逻辑抽出为 `ToolRegistry` / `ToolSelector` / `DocumentToolSelector`；T005b 已补充 `DocumentToolSelector` 独立规则测试，覆盖 status、summary、evidence、默认 QA 与 summary + evidence 冲突；T005c 已通过 Agent runtime smoke，确认 summary / QA 路由、`taskId` 返回和 task / step 查询接口仍正常；T006a 已让前端 Agent 页面根据 `taskId` 查询并展示持久化 task / step trace；T006b 已用 Playwright 真实验证 `/agent` 页面可展示持久化 trace。
- T007 已收口 README / frontend README：同步当前 AI 问答、SSE、最小 Agent、AgentTask / AgentStep 落库、task / step 查询接口、前端持久化 trace 展示与真实验证结果，同时明确非成熟多 Agent、非 LLM Tool Calling、非完整向量 RAG。
- T009a 已增强 Agent 工具路由可解释性：`ToolSelector.SelectResult` 返回 `reason` 和 `matchedKeywords`，`DocumentAgentResponse` 透出 `routingReason` / `matchedKeywords`；parseReady=false 仍在 `DocumentAgentServiceImpl` 内短路，不调用 selector。
- T009b 已补充 `DocumentToolSelector` 可解释性单元测试：覆盖状态、摘要、证据/引用、默认 QA、summary + evidence 冲突、英文大小写、空白和 null 输入。
- T009c 已让前端 Agent 页面展示路由决策说明和命中关键词；无路由说明时页面保持兼容，不影响原始回答、引用、内存 trace 或持久化 trace 展示。
- T009d 已增强 Agent smoke 对 `routingReason` / `matchedKeywords` 的断言，新增 `docs/AGENT_ASYNC_DESIGN.md` 记录异步 Agent 未来演进方案；该文档仅为设计，当前未实现异步 Agent，也未接 MQ。
- T009e 已同步协作文档到当前真实 git / 代码状态。
- T010 当前为 BLOCKED：T010x 已复现后端 smoke 在上传文档后等待 `parseStatus` 超时，原始报错为 `Parse timeout after 120 seconds.`；后端日志显示 `NoopParseTaskMessageProducer` 跳过解析消息发送；当前 MQ disabled / no-op producer 模式下不会推进真实异步解析，因此完整 T010 需要可用 MQ / 解析消费环境，或用户明确接受 Agent-only 替代验证。
- T010-lite-ui 已完成：`/agent` 页面支持从当前用户文档列表选择文档，也支持手动输入当前账号可访问的 `documentId`，并明确展示 Lite 验证模式说明。该入口仅用于已解析文档上的 Agent-only 验证，不代表完整上传解析链路已通过。
- T010-lite-run 已完成：Playwright 真实打开 `/agent`，使用当前账号可访问且已解析成功的 `documentId=61` 完成 summary / QA Agent run；页面可展示 `routingReason`、`matchedKeywords`、持久化 task / step trace、`taskId`、`SUCCESS`、steps 与 QA citations。该结论仍只覆盖已解析文档上的 Agent runtime，不覆盖上传、解析、MQ 或 `ParseTaskMessageConsumer`。
- T011a 已完成：新增 `ToolDefinition` / `ToolDefinitionProvider`，为当前 3 个 AgentTool 提供 toolName、displayName、description、输入输出 schema 文本和 LLM 选择安全标记；该能力仅是 P3 LLM Tool Selection 基础设施，未调用真实 LLM，未改变默认 Agent 行为。
- T011b 已完成：新增 `LlmToolSelectionResult` / `LlmToolSelectionParser`，可从 LLM 原始文本中提取第一个 JSON object，并校验 decision、toolNames、routingReason、matchedKeywords 和 confidence；该能力仍未调用真实 LLM，也未启用为默认 selector。
- T011c 已完成：新增 `LlmToolSelectionPromptBuilder`，基于 task、parseReady、hasSummary 和 `ToolDefinition` 列表构建未来 LLM Tool Selection prompt；prompt 明确可选 decision、JSON 输出协议和安全限制。该能力仍未调用真实 LLM，也未改变默认 selector。
- T011d 已完成：新增 `tool-selector-eval-cases.json` 与 `ToolSelectorEvaluationTest`，用 24 条状态、摘要、证据问答、英文大小写、中文、模糊输入、summary+evidence 冲突和空白输入样例跑当前 `DocumentToolSelector` 基线；该能力仍未调用真实 LLM，也未改变默认 selector。
- T012a 已完成：新增 `LlmToolSelector` 接口、`FakeLlmToolSelector` 和 `LlmSelectorShadowResult`，用于未来 shadow LLM selector adapter；fake 实现不联网、不调用真实 LLM，只复用当前关键词规则或 parse-not-ready 状态决策，未改变默认 Agent routing。
- T012b 已完成：新增 `AgentSelectorProperties` 和 `app.agent.selector.mode` / `app.agent.selector.shadow-enabled` 配置；默认 mode 为 `keyword`，shadow 默认关闭。该配置仅为 shadow compare 基础设施，未改变当前生产 routing。
- T012c 已完成：`DocumentAgentServiceImpl` 在 shadow 开启且文档 parseReady 后可旁路执行 `LlmToolSelector` 并生成 `LlmSelectorShadowResult` compare 日志；真实执行工具仍只由 primary `DocumentToolSelector` decision 决定，API 返回、前端、DDL 和 AgentTask schema 均未改变。
- T012d 已完成：新增 `SelectorMetricsCollector` 和 `SelectorMetricsSnapshot`，内存态记录 totalComparisons、matchedCount、mismatchCount、matchRate 与 lastUpdatedTime；未落库，未接 Micrometer / Prometheus，未新增 API。
- T013a 已完成：`DocumentAgentServiceImpl` 在 shadow compare 成功执行后会调用 `SelectorMetricsCollector.record(primaryDecision, shadowDecision)`；shadow 关闭、parseReady=false 或 shadow selector 异常时不记录 metrics，真实工具执行仍只由 primary decision 决定。
- T013b 已完成：新增 `ShadowToolSelectorEvaluationTest`，复用 24 条 `tool-selector-eval-cases.json` 对比 primary `DocumentToolSelector` 与 `FakeLlmToolSelector`；当前结果 23/24 matched，matchRate=0.9583，唯一 mismatch 为 blank task + parseReady=false 的 shadow 状态短路边界。该测试不调用真实 LLM，不改变生产 routing。
- T013c 已完成：新增 `docs/AGENT_SELECTOR_SHADOW_MODE.md`，明确当前 primary / shadow selector 架构、feature flag、内存态 metrics、已验证内容、不能硬吹的边界和后续 T014-T017 路线。
- T013d 已完成：协作文档已更新到 Selector Shadow Observability 收口状态；当前默认行为仍是 keyword selector，当前没有真实 LLM 调用，完整 T010 仍因 MQ disabled / `NoopParseTaskMessageProducer` 保持 BLOCKED，下一步推荐 T014 disabled real LLM selector adapter。
- T014a 已完成：新增 `LlmToolSelectionClient`、`LlmToolSelectionClientResponse` 和 `DisabledLlmToolSelectionClient`；disabled client 不联网、不调用真实模型、不读取环境变量或 `backend/.env`，仅返回 disabled response，尚未接入生产 routing。
- T014b 已完成：新增 `RealLlmToolSelector` adapter，串联 `LlmToolSelectionPromptBuilder`、`LlmToolSelectionClient` 和 `LlmToolSelectionParser`；该类当前不是 Spring 生产 bean，未注入 `DocumentAgentServiceImpl`，测试仅用 fake client 验证 JSON 解析和失败路径。
- T014c 已完成：新增 `RealLlmSelectorShadowRunner` / `RealLlmSelectorShadowRunResult`；runner 可在测试中调用 `RealLlmToolSelector`，disabled 或解析失败时返回 success=false / shouldRecordMetrics=false，成功时返回 shadowDecision / matched / shouldRecordMetrics=true。当前未接入 `DocumentAgentServiceImpl`。
- T014d 已完成：`docs/AGENT_SELECTOR_SHADOW_MODE.md` 和协作文档已更新，明确 real LLM selector adapter 已存在，但默认 disabled，未真实调用 LLM，未接 function calling，未接入生产 service，未接管 routing。
- T015a 已完成：`AgentSelectorProperties` 新增 real shadow 安全开关，默认 `realShadowEnabled=false`、`realShadowRecordMetrics=false`、`realShadowFailOpen=true`；未修改 `application.yml`，未启用真实模型调用。
- T015b 已完成：`DocumentAgentServiceImpl` 已接入 `RealLlmSelectorShadowRunner` 禁用态 shadow 路径；只有 `shadowEnabled=true` 且 `realShadowEnabled=true` 时才会旁路执行，默认不运行。real shadow 失败 fail-open，不影响 primary routing、真实工具执行或 API 返回。
- T015c 已完成：新增 `DocumentAgentRealShadowPathTest`，聚焦验证 real shadow path 默认关闭、fake shadow 不隐式启用 real shadow、disabled / exception fail-open、parseReady=false 跳过、real metrics 默认不记录和显式开启后记录。
- T015d 已完成：`docs/AGENT_SELECTOR_SHADOW_MODE.md` 和协作文档已更新，明确 real shadow runner 已接入 service 但默认关闭；当前仍未真实调用 LLM，未接 function calling，未新增 API，未改变 production routing。
- T016a 已完成：`AgentSelectorProperties` 新增 provider 配置模型，默认 `llmProvider=disabled`、`llmModel` / `llmBaseUrl` 为空、`llmRequestTimeoutMs=3000`；未修改 `application.yml`，未读取环境变量或 `backend/.env`，不会自动启用真实 provider。
- T016b 已完成：新增 `FakeLlmToolSelectionClient`，不联网、不读取环境变量或 `backend/.env`，按 prompt 中当前任务返回可被 `LlmToolSelectionParser` 解析的 JSON；仅用于测试和未来 shadow-only smoke，不影响生产 routing。
- T016c 已完成：新增 OpenAI-compatible request / response 和 `OpenAiCompatibleLlmToolSelectionClient` skeleton；当前 client 是 dry-run disabled 行为，不发 HTTP，不读取 API Key、环境变量或 `backend/.env`，未接入生产 service。
- T016d 已完成：新增 `LlmToolSelectionClientFactory`，可根据 provider 返回 disabled / fake / openai-compatible client；默认返回 disabled，factory 尚未接入 production service，不改变当前 Agent 行为。
- T016e 已完成：selector shadow 文档和协作文档已更新；provider skeleton 当前默认 disabled，fake 仅用于测试，openai-compatible 不联网、不读 API Key、不读 `backend/.env`，下一步推荐 T017 默认 disabled factory 接入。
- T017a 已完成：新增 `RealLlmToolSelectorFactory`，将 `AgentSelectorProperties`、`LlmToolSelectionClientFactory`、prompt builder 和 parser 串起来创建 `RealLlmToolSelector`；默认 disabled，fake provider 可在测试中返回合法 decision，openai-compatible 仍 dry-run disabled。
- T017b 已完成：`RealLlmSelectorShadowRunner` 支持 factory-backed selector；默认 disabled provider 返回 success=false，provider=fake 可在测试中 success=true，openai-compatible 仍 dry-run disabled。未修改 service 或 production routing。
- T017c 已完成：`DocumentAgentServiceImpl` 的 real shadow runner 已改为 factory-backed 构造路径；默认 provider 仍 disabled，`realShadowEnabled=false` 时不运行；service 测试覆盖 provider=fake real shadow success、primary decision 仍决定真实工具执行、real metrics 默认不记录且显式开启后才记录。
- T017x 已完成：`FakeLlmToolSelectionClient` 已增强 task 提取和本地规则，覆盖 status / summary / evidence、中文关键词、英文大小写、summary + evidence 冲突和空白 fallback；未修改 primary selector 或 eval cases。
- T017d 已完成：新增 `RealShadowProviderEvaluationTest`，使用 24 条 eval cases 对比 primary `DocumentToolSelector` 与 `RealLlmSelectorShadowRunner + provider=fake`；结果 total=24、success=22、failures=2、matched=22、mismatch=0、matchRate=0.9167。两个 failure 来自 blank task 被 prompt builder 拒绝，未修改 eval cases。
- T017e 已完成：`docs/AGENT_SELECTOR_SHADOW_MODE.md` 和协作文档已更新，明确 factory-backed real shadow 路径具备 provider=fake 离线验证，但仍没有真实 provider 调用、没有 API 变化、没有 production routing 接管。
- T018a 已完成：`DocumentAgentServiceImpl` 增加 real shadow runtime 安全日志字段 `provider`，用于观察 fake provider shadow-only runtime；日志不输出 prompt、task 全文、文档内容、密钥、token 或真实连接信息，production routing 未改变。
- T018b-retry / T018c 已完成：本地后端在用户授权下连接远程中间件，使用 `shadowEnabled=true`、`realShadowEnabled=true`、`realShadowRecordMetrics=true`、`llmProvider=fake` 完成浏览器 runtime 验证；已解析文档 `documentId=61` 的 summary / QA 均成功，primary decision 分别为 `summary_tool` / `qa_tool`，页面可见 routingReason、matchedKeywords、持久化 trace 和 QA citations；后端安全日志可见 `provider=fake` real shadow compare matched。后端 compile/test 与前端 lint/build 均通过。
- T018d 已完成：协作文档记录 fake provider shadow-only runtime 结果；本轮未使用 hk-ops，未执行远程 DB 只读 SELECT，未真实调用 LLM，未读取 API Key 或 `backend/.env`，未向模型 provider 发真实 HTTP，未新增 API，未修改前端，未改变 production routing。
- T019-preflight 已完成：新增 `docs/REAL_PROVIDER_SHADOW_PREFLIGHT.md`，记录真实 provider shadow-only 前置安全方案、日志脱敏原则、HTTP 调用边界、验证方案、停止条件和用户确认项；本轮没有真实调用 provider，没有读取 API Key 或 `backend/.env`，也没有改变 production routing。
- T019a 已完成：`AgentSelectorProperties` 新增 `llmApiKey`、`llmMaxTokens`、`llmTemperature` 配置字段；默认 provider 仍 disabled，real shadow 默认关闭，真实 key 只能由运行环境注入，本轮未读取或输出 API Key。
- T019b 已完成：`OpenAiCompatibleLlmToolSelectionClient` 可在 apiKey / baseUrl / model 齐全时调用 OpenAI-compatible `/chat/completions`；缺配置时返回 disabled，不联网。测试仅用本地 stub server；未读取真实 API Key，未修改配置文件，未改变 production routing。
- T019c 已完成：后端测试覆盖 openai-compatible 缺 apiKey / 缺 baseUrl、client failure、parser failure 和 fail-open；shadow failure 不记录成功 metrics，primary decision 和 API 返回仍保持不变。
- T019d 已完成：用户授权后执行真实 provider shadow-only runtime，provider=`openai_compatible`，真实 HTTP 调用 2 次；使用 `documentId=61` 验证 summary / QA，primary 与 shadow decision 分别均为 `summary_tool` / `qa_tool`，无 mismatch，QA citations 正常；未输出 API Key、完整 baseUrl、prompt、文档内容或模型完整返回。
- T019e 已完成：后端 `mvn -DskipTests compile` 通过；后端 `mvn test -DskipITs` 通过，244 tests；前端 `npm run lint` 与 `npm run build` 均通过。
- T019-recovery 已完成：修复测试进程继承本机真实 provider 环境导致的配置绑定 / 测试隔离问题；`AgentSelectorProperties` 支持 OpenAI-compatible 常见 provider alias，配置测试和 Spring context test 显式隔离 selector provider 默认值。
- T019f 已完成：协作文档记录真实 provider shadow-only 验证结果；默认行为仍不启用真实 provider，不改变 production routing，不新增 API，不修改前端。
- T020 已完成：selector shadow metrics 增强为 total / success / failure / matched / mismatch、matchRate、failureRate、provider 聚合和 decision pair 聚合；新增 `SelectorShadowThresholdPolicy` / `SelectorShadowThresholdDecision`，默认阈值为 minimumSamples=20、minMatchRate=0.95、maxFailureRate=0.05。
- T020 边界：threshold policy 只输出 `allowPromotionCandidate` 和 reason，不自动接管 routing，不修改配置，不新增 API，不落库，不接 Prometheus，不修改前端；production routing 仍由 `DocumentToolSelector` 决定。
- T020 验证：`SelectorMetricsCollectorTest`、`SelectorShadowThresholdPolicyTest`、`SelectorShadowThresholdEvaluationTest`、`ShadowToolSelectorEvaluationTest`、`RealShadowProviderEvaluationTest`、`DocumentAgentServiceImplTest` 与后端全量测试通过；T020e 也完成前端 lint/build。
- T021a-c 已完成：新增 `SelectorMetricsDebugSnapshot` / `SelectorMetricsDebugReporter` 和离线 debug evaluation 测试；内部 dump 只展示安全字段和 threshold decision，不输出 prompt、task、文档内容、模型完整返回、API Key、baseUrl 或 Authorization。
- T021d 已补充文档边界：当前不新增 HTTP API，不新增 Actuator endpoint，不接 Prometheus，不落库；这么做是为了避免在管理端鉴权、内网边界和脱敏策略未设计前暴露 provider / decision metrics。
- T021e 已完成：后端 `mvn -DskipTests compile`、`mvn test -DskipITs`、前端 `npm run lint`、`npm run build` 均通过；T021 已收口为内部 debug dump 能力，不新增 API / Actuator / Prometheus。
- T022a-c 已完成：新增 `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`，对本地 debug dump、Actuator endpoint、管理端 API、Prometheus metrics 做方案对比和安全威胁模型；T022 只做设计文档，没有新增接口、Actuator endpoint 或 Prometheus。
- T022d 已同步 shadow mode / async design / TODO / changelog / handoff，当前推荐路线改为 T023：Actuator endpoint 设计草案，不直接实现。
- T022e 已完成：最终自检确认 T022 只修改允许文档；未修改 Java 生产代码、测试代码、前端、配置、DDL 或 API 层；未读取或输出 secret。
- T023a-c 已完成：新增 `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`，仅设计候选 Actuator endpoint `agentSelectorShadow` / `/actuator/agentSelectorShadow` / GET / readOnly；补充字段白名单、黑名单、默认关闭策略、安全鉴权、审计、未来候选实现类和测试策略。
- T023d 已同步观测路线文档：T023 仍只是设计草案，尚未实现 endpoint；T024 才可能进入候选实现，且 T024 前建议先做 Claude Code / 人工安全审查。
- T023e 已完成：最终自检确认 T023 只修改允许文档；未修改 Java 生产代码、测试代码、前端、配置、DDL 或 API 层；未读取或输出 secret。
- T024a 已完成：根据安全审查补充 T024 实现边界；未来 endpoint 必须使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`，本轮不修改 `application.yml` / `application-local.yml`，不加入 exposure include，不接 Prometheus，不测试未授权访问，该项留到 T025。
- T024 已完成：新增默认关闭的 `AgentSelectorShadowEndpoint` 和对应单元 / context 默认 404 测试；endpoint id 为 `agentSelectorShadow`，候选 path 为 `/actuator/agentSelectorShadow`，只读返回 `SelectorMetricsDebugSnapshot`。本轮未修改配置文件，未加入 exposure include，未新增普通 REST API，未接 Prometheus，未改前端，未真实调用 provider，production routing 未改变。
- T025 已完成：新增 `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`，完成 Actuator endpoint 安全配置 / 显式开启策略设计；明确默认关闭、local / dev / test / prod 分环境策略、禁止 `exposure.include=*`、禁止公网匿名访问、禁止前端直接调用、禁止输出 prompt / task / 文档内容 / 模型完整返回，并拆分 T026-T030 后续任务。T025 只写设计文档，endpoint 当前仍默认关闭，未修改 `application.yml` / `application-local.yml`，未加入 exposure include，未接 Spring Security，未接 Prometheus，未开启 dev / prod 访问；最终自检确认 T025 只修改允许文档，未修改 Java / 测试 / 前端 / 配置或 production routing。
- T027 已完成：新增 `AgentSelectorShadowEndpointEnabledTest`，只在测试 properties 中显式开启 endpoint；`GET /actuator/agentSelectorShadow` 返回 200，字段白名单 / 黑名单、空 metrics 和 metrics 不变检查均通过。配置命名已确认：`management.endpoint.agent-selector-shadow.enabled=true` 使用单数 endpoint 和 relaxed binding；`management.endpoints.web.exposure.include=agentSelectorShadow` 使用复数 endpoints，值必须是 endpoint id，不是 `agent-selector-shadow`，且禁止 `*`。T027 未修改生产代码、配置文件、前端、文档、Spring Security 或 Prometheus，未真实调用 provider，未读取或输出 secret；默认状态仍关闭，生产环境仍未开启。
- T028 已完成：`docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md` 已补充 local 临时环境变量开启草案、dev 部署环境变量开启草案、dev 开启前置条件，并拆分后续 T029-T032。T028 未修改 Java / 测试 / 前端 / 配置，未真正开启 endpoint，未新增 Spring Security，未接 Prometheus。
- T029 已完成：新增 `docs/AGENT_ACTUATOR_SECURITY_INTEGRATION_DESIGN.md`，完成 Spring Security / Actuator 安全集成设计；设计内容覆盖 `ROLE_ACTUATOR_ADMIN`、`ROLE_OPS`、`ROLE_DEVELOPER_DEBUG`、`/actuator/agentSelectorShadow` 单独保护、401 / 403 / 404 行为、内网 / VPN / IP allowlist / 网关限制、T030 测试策略、dev / prod 开启前 checklist 和回滚策略。T029 只写文档，没有实现 Spring Security，没有新增 `SecurityFilterChain`，没有修改 `application.yml` / `application-local.yml`，没有真正开启 endpoint，没有接 Prometheus，没有修改 Java / 测试 / 前端代码。
- T030 当前 BLOCKED：T030a preflight 发现当前后端只包含 `spring-security-crypto`，没有 `spring-boot-starter-security`、`spring-security-test`、`SecurityFilterChain` 或现有 Web 鉴权测试配置。本轮边界不允许新增 Maven 依赖或生产 Spring Security 配置，因此没有新增测试类，未验证未认证 / 普通用户 / OPS / ACTUATOR_ADMIN 的访问行为。
- T031 已完成：补充 local / dev 临时开启文档示例，包含 PowerShell 环境变量示例、`endpoint` / `endpoints` 命名注意、enabled 使用 `agent-selector-shadow`、`exposure.include` 使用 `agentSelectorShadow`、禁止 `*`、禁止公网匿名和普通用户访问。T031 没有修改任何配置文件，也没有真正开启 endpoint。
- T032 已完成：新增 `docs/AGENT_SELECTOR_PROMETHEUS_METRICS_DESIGN.md`，只设计 selector shadow Prometheus 数值指标、低风险 label、禁止字段、cardinality 风险和 T033-T036 后续拆分。T032 没有接 Prometheus，没有修改 `pom.xml`，没有修改代码、测试、配置或前端。
- T040a 已完成：新增 `docs/PROJECT_INTERVIEW_BRIEF.md`，面向 Java 后端实习 / AI 应用工程化面试整理项目一句话定位、真实能力、半实现能力、T010 / T030 BLOCKED、不能硬吹的能力、5 个简历亮点和风险追问回答。该文档只做投递材料收口，不修改代码或配置。
- T040b 已完成：新增 `docs/PROJECT_ARCHITECTURE_OVERVIEW.md`，包含总体架构、核心链路、系统总体架构 Mermaid 图和 Agent 执行链路 Mermaid 图；明确完整 MQ 解析链路当前验证 BLOCKED、Actuator endpoint 默认关闭、Prometheus 只是设计未接入。
- T040c 已完成：新增 `docs/RESUME_BULLETS.md`，提供保守版、标准后端实习版和 AI 应用工程化版三套简历 bullet，并明确 Prometheus、Spring Security、生产 Actuator 暴露、shadow 接管 routing 等能力不能写成已完成。
- T040d 已完成：新增 `docs/INTERVIEW_QA.md`，按项目整体、上传解析、RocketMQ、SSE、Agent、selector shadow、真实 provider、metrics / Actuator、BLOCKED 点和后续优化整理 30 个高频面试问题及诚实回答。
- subagents 与 MCP 工具能力边界见 `docs/CODEX_TOOLING.md`；尤其是 hk-ops 远程访问前必须说明目的、命令类别和是否只读，并等待用户确认。

## 6. 核心业务链路

1. 用户注册 / 登录，前端保存 token。
2. 用户上传文件，后端生成文件记录并写入对象存储。
3. 用户创建文档，并触发解析任务。
4. 后端通过 Outbox / RocketMQ 异步解析文档，更新文档状态和内容片段。
5. 用户进入文档列表 / 详情查看摘要、正文和状态。
6. 用户发起普通问答或 SSE 问答。
7. 后端检索文档片段，组装上下文，调用 mock 或 real AI service 生成回答。
8. 前端展示回答、引用、历史记录和流式输出过程。
9. Agent 页面可基于文档业务工具展示任务输入、工具调用步骤、持久化执行轨迹和最终结果。

## 7. 关键技术点

- Spring Boot 分层架构与 MyBatis-Plus 数据访问。
- RocketMQ + Outbox 异步解析链路。
- Redisson 分布式锁、幂等与去重。
- MinIO 对象存储与分片上传。
- Redis 缓存、会话上下文、限流。
- SSE 流式输出与 Markdown 稳定渲染。
- 轻量检索增强问答、引用映射和 eval artifact。
- Agent 工具抽象与 trace 展示。
- Agent task / step 持久化骨架、执行时记录接入、查询 API 与前端 trace 展示。
- Agent runtime smoke 与远程 MySQL 只读核验闭环。
- Agent ToolRegistry / ToolSelector 解耦工具路由，并用独立单元测试锁定规则行为。
- Agent 路由可解释性：后端 run 响应可返回 routing reason 与 matched keywords，便于前端展示和 smoke 断言。
- 异步 Agent 演进设计文档：明确当前同步流程、瓶颈、方案对比和未来 RocketMQ 方案边界。
- Actuator / Prometheus 可观测性基础。

## 8. 当前代码风险

- 当前工作区干净；风险主要来自运行环境、历史文档口径和后续任务边界，下一轮仍不能跳过 `git status` / `git diff`。
- T010 不应被记录为通过；当前 blocker 是 MQ disabled / `NoopParseTaskMessageProducer` 导致 parse timeout。下一位接手者不要为了通过 smoke 改业务代码、硬编码 documentId 或绕过上传解析链路。
- eval 指标已按 T001a/T001b 收敛到 `stagec_eval_latest.json`（90% / 100%，20 cases / 8 stream pairs）。后续风险不再是“三套指标冲突”，而是需要通过 T005 重新运行 eval，并补充实际运行时 `AI_MODE`、模型名、provider 记录。
- AI 问答 / SSE 后端改进包已在 2026-05-13 完成 T002a 验证：`mvn -DskipTests compile` 通过，`mvn test -DskipITs` 通过，测试统计为 `Tests run: 141, Failures: 0, Errors: 0, Skipped: 0`。当前无编译或测试阻塞，适合作为独立提交候选进入 Claude Code 只读提交前审查。
- 前端 QA / SSE 展示改进已在 2026-05-13 完成 T002b 验证：`npm run lint` 通过，`npm run build` 通过。当前前端流式事件解析、引用展示、Markdown inline 渲染和降级提示可作为独立提交候选；本轮不提交 Agent Demo、benchmark、`.run`、根 README 或 AGENTS。
- SSE 质量和普通问答一致性仍需持续回归；T002a/T002b 只覆盖后端单元测试和前端 lint/build，未启动完整前后端服务、未用 Playwright 走浏览器主链路、未重跑 eval。
- mock / real provider 边界容易被文档夸大。
- PDF 能力边界容易被误写成完整解析能力。
- 部分历史中文文档存在乱码，影响交接质量。

## 9. 后续最应该做的 3 个方向

1. T031 / T032 已完成，下一步建议进入 T033 Prometheus metrics 设计审查，或先开 T030-design-review 重新收窄鉴权验证方案。不要真正开启 endpoint，不要修改 `application.yml` / `application-local.yml`；如后续再次真实调用 provider，必须重新获得用户确认 provider、baseUrl、model、API Key 注入方式、费用、调用次数上限和日志脱敏策略。
2. 完整 T010 仍需要可用 MQ / 解析消费环境；如要验证上传解析链路，应回到 `T010m-local-mq-readiness-check` 和环境确认。
3. 不要直接进入生产 LLM tool calling / MCP / RAG / 多 Agent / MQ 异步 Agent；如后续做完整 T010，需用户确认是否通过 hk-ops 检查远程 MQ / Redis / MinIO / MySQL。

## 10. 接手时优先阅读

- `AGENTS.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`
- `README.md`
- `backend/README.md`
- `frontend/README.md`
- `backend/src/main/java/com/docpilot/backend/ai/service/impl/DocumentQaServiceImpl.java`
- `frontend/app/documents/[documentId]/page.tsx`
- `frontend/app/agent/page.tsx`（若存在）
- `git status --short`
- `git diff --stat`

## 11. 本地启动方式

中间件：

```powershell
docker compose -f docker-compose.demo.yml up -d
```

后端：

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

具体环境变量以 `backend/README.md`、`frontend/README.md`、`.env.example`、`.env.demo.example` 为准。

## 12. 本地测试方式

```powershell
cd backend
mvn -DskipTests compile
mvn test -DskipITs
```

```powershell
cd frontend
npm run lint
npm run build
```

## 13. 本地验证方式

- 后端健康检查：`curl http://localhost:8081/actuator/health`
- 前端页面：打开 `/`、`/login`、`/dashboard`、`/upload`、`/documents`、`/agent`。
- 问答链路：准备一个 txt / md 文档，上传、创建文档、等待解析、发起普通问答和 SSE 问答。
- Agent 链路：进入 Agent 页面，输入文档总结或状态查询类任务，观察工具步骤和最终回答。
- eval：以仓库当前 benchmark / scripts 文档为准，重跑后再更新指标。

## 14. 当前最建议优先做的一个最小任务

优先进入 T033 Prometheus metrics 设计审查，或先开 T030-design-review 重新收窄鉴权验证方案，不要直接生产暴露 endpoint。

原因：T024 已完成默认关闭 endpoint，T025 已完成安全开启策略设计，T027 已完成测试内显式开启验证，T028 已完成 local / dev 显式开启方案草案，T029 已完成 Spring Security / Actuator 安全集成设计，T031 已完成 local / dev 临时开启示例，T032 已完成 Prometheus metrics 设计；但当前仓库仍缺少可支撑 Web 鉴权测试的 Spring Security 依赖和配置。本轮不应修改真实配置或开启生产访问。默认仍不能启用真实 provider，也不能改变 `/api/ai/agent/run` 返回协议。
