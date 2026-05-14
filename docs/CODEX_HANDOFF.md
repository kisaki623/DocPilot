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

1. 继续 `T016`：新增 provider-specific client disabled / fake adapter，例如 DeepSeek 或 OpenAI-compatible client skeleton，但默认不注入真实 provider，真实 provider 调用必须另开任务。
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

优先执行 `T016`。

原因：T015 已将 `RealLlmSelectorShadowRunner` 接入 service 的 real shadow 分支，并用安全开关保持默认关闭；下一步可以先做 provider-specific disabled / fake client skeleton，但仍不能真实调用外部模型、不能接管生产 routing，也不能改变 `/api/ai/agent/run` 返回协议。
