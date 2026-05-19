# TODO_NEXT.md

DocPilot Codex 协作看板。每轮只执行一个任务；没有真实验证结果不能标记为 DONE；代码已改但验证不完整只能标记为 REVIEW；缺环境、账号、密钥、数据库、依赖或用户确认时标记为 BLOCKED。

## 看板规则

- 一次只允许有一个任务处于 IN_PROGRESS。
- 每个任务尽量控制在 30-90 分钟内可完成。
- 任务必须围绕真实短板、可运行性、稳定性、工程化和面试价值。
- 每轮结束后更新本文件、`docs/CODEX_HANDOFF.md`、`docs/CHANGELOG_CODING.md`。
- 如发现工作区有 modified / untracked 文件，先汇报再行动，不自动 `git add` / `git commit` / `git push`。
- 提交时必须使用一行 conventional commit（`type(scope): description`），不允许 `Co-Authored-By` 或工具/模型签名；详细规则见 `AGENTS.md` Commit Message 规则。

## 已完成

### T055-RAG-Agent-Showcase

- 状态：DONE
- 完成时间：2026-05-20
- 任务目标：把 T054 的 fake embedding + in-memory vector store 内部 RAG 能力接入 Agent Showcase demo 路径，并在 `/agent` 页面展示 RAG 召回结果。
- 当前结果：新增实验性 `document_rag_tool` / `rag_tool` route；`/api/ai/agent/run` 返回向后兼容的 `ragResults` / `ragAnswerContext` 字段；`/agent` 页面新增 RAG 召回模板，展示 retrieved chunks、score / similarity、citation metadata、Agent step trace、routingReason 和 matchedKeywords。
- 验证结果：`cd backend; mvn -Dtest=*Rag* test` 通过；`mvn -Dtest=DocumentAgentServiceImplTest test` 通过；`mvn -Dtest=DocumentToolSelectorTest test` 通过；`mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过；`cd frontend; npm run lint` 通过；`npm run build` 通过。
- 边界：未读取 `backend/.env`；未新增依赖；未新增公开 REST API；未新增数据库表；未修改 `application.yml` / `application-local.yml` 或 docker-compose；未接真实 embedding provider、Qdrant、Redis Vector、LangChain4j；未改变原有 summary / QA / status 行为；RAG demo 仍是求职展示用的 fake embedding + in-memory 检索路径，不是生产完整 RAG。

### T054-RAG-Minimal-Internal-Service

- 状态：DONE
- 完成时间：2026-05-19
- 任务目标：用 fake embedding + in-memory fake vector store 打通最小 RAG 内部 service 闭环。
- 当前结果：新增 `backend/src/main/java/com/docpilot/backend/ai/rag/` 包，包含 `DocumentChunk`、`EmbeddingVector`、`EmbeddingModel`、`FakeEmbeddingModel`、`VectorStore`、`InMemoryVectorStore`、`RagIndexService`、`RagRetrievalService`、`RagAnswerContextBuilder` 等内部对象；新增 `RagMinimalInternalServiceTest` 覆盖 chunk split、fake embedding deterministic、vector store topK、retrieval by query 和 answer context with citations。
- 验证结果：`cd backend; mvn -Dtest=RagMinimalInternalServiceTest test` 通过；`mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过。全量测试前已通过 T054x 稳定既有 benchmark timing 断言。
- 边界：未读取 `backend/.env`；未新增依赖；未新增公开 REST API；未新增数据库表；未修改 `application.yml` / `application-local.yml`；未接真实 embedding provider、Qdrant、Redis Vector、LangChain4j 或 RAG production routing；未修改现有 QA / Agent 主流程。

### T053-Vector-Store-Selection

- 状态：DONE
- 完成时间：2026-05-19
- 任务目标：比较 Qdrant、Redis Vector / Redis Stack、MySQL fallback 和 in-memory fake vector store，确定 DocPilot 最小 RAG 的最快落地方案。
- 当前结果：新增 `docs/VECTOR_STORE_SELECTION.md`，从接入成本、中间件成本、Java / Spring Boot 复杂度、求职展示价值、生产化潜力、测试难度和面试解释难度对比四类方案；推荐求职冲刺先用 fake embedding + in-memory fake vector store，后续工程化再接 Qdrant + MySQL chunk metadata。
- 验证结果：文档 diff 自查；未运行代码测试，因为本任务只做选型。
- 边界：未修改 docker-compose、配置、DDL、后端代码、前端代码或 production routing；未实现 embedding、向量库或 RAG。

### T052-RAG-Minimal-Design

- 状态：DONE
- 完成时间：2026-05-19
- 任务目标：设计 DocPilot 从当前轻量文档问答升级到最小 RAG 的最短路径。
- 当前结果：新增 `docs/RAG_MINIMAL_DESIGN.md`，明确当前已有上传 / 解析状态、文档问答、citations 和 Agent QA tool；设计 `parsed text -> chunk -> embedding -> vector store -> retrieve topK -> prompt assemble -> answer -> citations / score display` 链路；补充 `document_chunk`、`chunk_embedding` / vector payload、内部 service 草案、fallback 和测试策略。
- 验证结果：文档 diff 自查；未运行代码测试，因为本任务只做设计。
- 边界：T052 本身未实现 RAG，未新增 API，未修改后端代码、前端代码、配置、DDL 或 production routing；后续 T054 / T055 已分别完成内部 fake RAG service 和 Agent Showcase 展示，但真实 embedding、Qdrant / Redis Vector 和生产 RAG routing 仍未接入。

### T056-Job-Materials

- 状态：DONE
- 完成时间：2026-05-19
- 任务目标：把 README 和面试材料切换到 AI Agent / RAG / Function Calling 求职展示口径。
- 当前结果：README 新增面向 AI Agent Internship Reviewers 的快速说明；项目 brief、架构说明、简历 bullet 和面试 QA 补充 Agent Showcase、Function Calling 风格工具抽象、RAG 未完成边界和下一阶段规划。
- 验证结果：已执行文档 diff 自查；已检查过度宣传关键词，命中均处于否定、边界或规划语境。
- 边界：仅修改文档；未修改代码、配置、DDL 或 production routing；未把 RAG / Function Calling takeover / Prometheus / Spring Security 写成已完成。

### T050-Agent-Showcase

- 状态：DONE
- 完成时间：2026-05-19
- 任务目标：把 `/agent` 页面收口为适合求职投递截图的 Agent Showcase 页面。
- 当前结果：页面标题调整为 Java AI Agent 文档问答 Demo；新增招聘方可读说明、Lite 验证边界、Tool Calling / Function Calling 风格说明、三类任务模板说明；结果区强化 decision、routingReason、matchedKeywords、taskId、citations 和持久化 steps 展示。
- 验证结果：`cd frontend; npm run lint` 通过；`cd frontend; npm run build` 通过。
- 边界：未修改后端 Java、后端配置、DDL、package / lock 文件或 production routing；未接 RAG，未接真实 Function Calling takeover，未新增后端 API。本轮未做浏览器 runtime 验证，后续可用当前账号已解析文档复测。

### T003-fast-submit

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：自审、验证并提交当前 Agent Demo。
- 验证结果：后端 compile/test 通过（141 tests），前端 lint/build 通过。
- commit：25793ed feat(agent): add minimal document agent demo

### T004a-fast-submit

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：新增 AgentTask / AgentStep 持久化 DDL、Entity、Mapper、PersistenceService 骨架。
- 验证结果：后端 `mvn -DskipTests compile` 通过。
- 边界：未接入 `DocumentAgentServiceImpl`，未执行 DDL，未修改前端，未接 MQ。

### T004b

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：将 AgentTask / AgentStep 持久化接入 `DocumentAgentServiceImpl`。
- 验证结果：后端 compile/test 通过。
- 边界：未新增查询 API，未执行 DDL，未修改前端。

### T004c

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：增加 Agent task / step 查询 API。
- 验证结果：后端 compile/test 通过。
- 边界：未改前端，未执行 DDL。

### T004d

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：补充 Agent smoke 的 taskId 断言并完成协作文档收尾。
- 验证结果：后端 test 通过；smoke 脚本命令可解析。
- 边界：未实跑 smoke，未启动后端服务，未执行 DDL。

### T004e

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：验证 AgentTask / AgentStep 远程 DDL 与运行时持久化闭环。
- 验证结果：远程表已创建并结构匹配；Agent smoke 通过；`/api/ai/agent/run` 返回 `taskId`；task / step 查询接口通过；hk-ops 只读 SELECT 确认远程库存在本次 task / steps。
- 边界：未修改 Java 业务代码，未修改前端，未执行除 T004e-2 授权建表外的远程写 SQL，未 git push。

### T005b-fast-submit

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：补充 `DocumentToolSelector` 独立规则测试。
- 覆盖范围：`status_only`、`summary_tool`、`qa_tool`、默认 QA、summary + evidence 冲突、空字符串 / 空白输入。
- 验证结果：`mvn -Dtest=DocumentToolSelectorTest test` 通过；`mvn test -DskipITs` 通过（147 tests, 0 failures）。
- 边界：未修改生产代码，未修改前端，未修改 DDL。

### T005c

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：运行 Agent runtime smoke，确认 `ToolSelector` 接入后真实接口链路仍正常。
- 验证结果：本地后端 8081 启动成功；`backend/scripts/agent/smoke-agent-min.ps1` 通过；summary run 返回 `summary_tool`；QA run 返回 `qa_tool`；run 响应包含有效 `taskId`；task / step 查询接口均通过。
- 边界：未修改生产代码，未修改前端，未修改 DDL，未读取 `backend/.env`，未执行 `git add` / `git commit` / `git push`。

### T006a-fast-submit

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：前端 Agent 页面根据 `taskId` 查询并展示持久化 task / step trace。
- 验证结果：`npm run lint` 通过；`npm run build` 通过。
- 边界：未修改后端 Java，未修改 DDL，未修改 README / `.run` / benchmark / docs-ai-dev。

### T006b-runtime-verify

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：真实运行前端 Agent 页面，验证持久化 task / step trace 展示可用。
- 验证结果：后端 Agent smoke 通过；Playwright 打开 `/agent` 并完成临时用户、文档、Agent run 验证；页面展示 `taskId`、`SUCCESS` 状态、`qa_tool`、2 条 step、toolName 与 durationMs；`npm run lint` 通过；`npm run build` 通过。
- 边界：未修改业务代码，未修改 DDL，未读取 `backend/.env`，未执行 `git push`；本轮启动的 8081 / 3000 服务已停止并释放端口。

### T007-docs-polish

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：收口 README / frontend README，补齐当前真实 Agent 工程化进展与边界。
- 验证结果：`npm run lint` 通过；`npm run build` 通过。
- 边界：未修改业务代码，未修改后端 Java，未修改 DDL，未提交 `.run` / benchmark / docs-ai-dev。

### T009a

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：增强 Agent ToolSelector 可解释性，让 run 响应返回 `routingReason` 与 `matchedKeywords`。
- 验证结果：后端 `mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过（147 tests, 0 failures）。
- 边界：未修改 DDL、Controller、前端、smoke 脚本或 AgentTask / AgentStep 持久化结构；未接 MQ / RAG / MCP / LLM Tool Calling。

### T009b

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：补充 `DocumentToolSelector` 可解释性单元测试，覆盖 decision / reason / matchedKeywords。
- 验证结果：`mvn -Dtest=DocumentToolSelectorTest test` 通过（8 tests, 0 failures）；`mvn test -DskipITs` 通过（149 tests, 0 failures）。
- 边界：未修改生产代码，未修改前端，未修改 DDL；测试生成的 Stage11 benchmark 产物已由精确 `.gitignore` 规则忽略。

### T009c

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：前端 Agent 页面展示后端返回的 `routingReason` 和 `matchedKeywords`。
- 验证结果：`npm run lint` 通过；`npm run build` 通过。
- 边界：未修改后端 Java，未修改 DDL，未新增依赖，未修改 package / lock 文件。

### T009d

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：增强 Agent smoke 的路由解释断言，补充 AgentTool Javadoc，并新增异步 Agent 演进设计文档。
- 验证结果：后端 `mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过（149 tests, 0 failures）；`scripts/agent/smoke-agent-min.ps1` 通过；前端 `npm run lint` / `npm run build` 通过。
- 边界：未实现异步 Agent，未接 MQ / RAG / MCP / Spring AI / LangChain4j / LLM Tool Calling，未修改 DDL 或前端页面。

### T009e

- 状态：DONE
- 完成时间：2026-05-13
- 任务目标：同步协作文档到当前真实 git / 代码状态。
- 验证结果：`git status --short` 为空；`git log --oneline -20` 确认 T009a-d 已完成；协作文档不再把 T000 作为当前推荐任务，下一步指向 T010 runtime 验证。
- 边界：未修改 README、前端业务代码、后端 Java、DDL、`.run`、benchmark 或 `docs/ai-dev`。

### T010-lite-ui

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：让 `/agent` 页面支持手动输入当前账号可访问的 `documentId`，用于后续 Agent-only lite 验证。
- 验证结果：前端 `npm run lint` 通过；`npm run build` 通过。
- 边界：仅解决已解析文档上的 Agent lite 验证入口；不验证上传、解析、MQ 或 `ParseTaskMessageConsumer`；完整 T010 仍为 BLOCKED。

### T010-lite-run

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：使用当前账号可访问的已解析文档，完成 Agent-only lite runtime 验证。
- 验证结果：Playwright 真实打开 `/agent`，使用 `documentId=61` 完成 summary 与 QA 两类 Agent run；页面展示 `routingReason`、`matchedKeywords`、持久化 task / step trace、`taskId`、`SUCCESS`、2 条 step、toolName、durationMs、inputSummary、outputSummary，QA 场景展示 citations；后端 compile/test 通过；前端 lint/build 通过。
- 边界：仅验证“已解析文档 -> Agent run -> 路由解释 -> 持久化 trace -> 前端展示”；不验证上传、解析、MQ 或 `ParseTaskMessageConsumer`；完整 T010 仍为 BLOCKED。

### T011a

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 Agent Tool Schema / Tool Metadata，为后续 LLM Tool Selection 做基础设施准备。
- 验证结果：`mvn -Dtest=ToolDefinitionProviderTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：未调用真实 LLM，未接 function calling，未改变默认 `DocumentToolSelector` 关键词规则和 `/api/ai/agent/run` 行为。

### T011b

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增未来 LLM Tool Selection 的 JSON 输出协议和 parser。
- 验证结果：`mvn -Dtest=LlmToolSelectionParserTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：仅解析和校验未来 LLM 输出文本；未调用真实 LLM，未接 function calling，未改变默认 Agent 行为。

### T011c

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增未来 LLM Tool Selection 的 prompt builder，为后续选择器演进提供稳定提示词骨架。
- 验证结果：`mvn -Dtest=LlmToolSelectionPromptBuilderTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：仅构建 prompt 字符串；未调用真实 LLM，未接 function calling，未改变默认 Agent 行为。

### T011d

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 selector 评估样例集和离线测试，为后续比较关键词 selector 与未来 LLM selector 建立基线。
- 验证结果：`mvn -Dtest=ToolSelectorEvaluationTest test` 通过，24/24 cases；`mvn test -DskipITs` 通过。
- 边界：仅使用当前 `DocumentToolSelector` 跑离线样例；未调用真实 LLM，未接 function calling，未改变默认 Agent 行为。

### T012a

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 LLM selector 接口、fake shadow adapter 和 shadow compare result，为 P3 影子模式打基础。
- 验证结果：`mvn -Dtest=FakeLlmToolSelectorTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：`FakeLlmToolSelector` 不联网、不调用真实 LLM，仅复用当前关键词规则或 parse-not-ready 状态决策；未改变默认 Agent routing。

### T012b

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 Agent selector feature flags，为 shadow compare 提供显式开关。
- 验证结果：`mvn -Dtest=AgentSelectorPropertiesTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：默认 `app.agent.selector.mode=keyword` 且 `shadow-enabled=false`；即使配置 shadow，本轮仍未改变生产 routing。

### T012c

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：在 `DocumentAgentServiceImpl` 中接入 primary selector + shadow selector compare。
- 验证结果：`mvn -Dtest=DocumentAgentServiceImplTest test` 通过；`mvn test -DskipITs` 通过。
- 边界：shadow compare 只在 `shadow-enabled=true` 且文档 parseReady 后旁路执行；真实工具执行仍只使用 primary `DocumentToolSelector` decision；未修改 API 返回、前端、DDL 或 AgentTask schema。

### T012d

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 selector shadow metrics 的内存态 collector 和 snapshot。
- 验证结果：`mvn -Dtest=SelectorMetricsCollectorTest test` 通过；`mvn test -DskipITs` 通过。
- 边界：metrics 仅内存态记录 totalComparisons、matchedCount、mismatchCount、matchRate 和 lastUpdatedTime；未落库，未接 Micrometer / Prometheus，未新增 API。

### T013a

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：将 `SelectorMetricsCollector` 接入 `DocumentAgentServiceImpl` 的 shadow compare 路径。
- 验证结果：`mvn -Dtest=DocumentAgentServiceImplTest test` 通过；`mvn test -DskipITs` 通过，179 tests，0 failures，0 errors。
- 边界：仅在 shadow compare 成功执行后记录 primary / shadow decision；shadow 关闭、parseReady=false 或 shadow selector 失败时不记录；真实工具执行仍只由 primary decision 决定，API 返回、前端、DDL 和持久化 schema 均未改变。

### T013b

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 Shadow Selector 离线评估测试，复用现有 selector eval cases 对比 primary 与 fake shadow。
- 验证结果：`mvn -Dtest=ShadowToolSelectorEvaluationTest test` 通过；24 cases，23 matched，1 mismatch，matchRate=0.9583；`mvn test -DskipITs` 通过，180 tests，0 failures，0 errors。
- 边界：仅测试 primary `DocumentToolSelector` 与 `FakeLlmToolSelector` 的离线对比；未修改生产代码，未修改 eval cases，未调用真实 LLM，shadow decision 不接管生产 routing。

### T013c

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 Selector Shadow Mode 设计说明，明确 shadow selector 架构、feature flag、metrics、验证结果和边界。
- 验证结果：`git status --short` 检查通过；新增 `docs/AGENT_SELECTOR_SHADOW_MODE.md`。
- 边界：仅修改文档；未修改代码，未新增 API，未调用真实 LLM，未改变默认 routing。

### T013d

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：更新协作文档当前阶段，把 T013 收口并将下一步推荐切换到 T014。
- 验证结果：`git status --short` 检查通过；`git diff -- docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md` 已复核。
- 边界：仅修改协作文档；未修改代码，未新增 API，未调用真实 LLM，未改变默认 routing。

### T014a

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 LLM Tool Selection client 抽象、响应对象和 disabled client。
- 验证结果：`mvn -Dtest=DisabledLlmToolSelectionClientTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：`DisabledLlmToolSelectionClient` 不联网、不调用真实模型、不读取环境变量或 `backend/.env`；未接入生产 routing。

### T014b

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 `RealLlmToolSelector` adapter，串联 prompt builder、client 和 parser。
- 验证结果：`mvn -Dtest=RealLlmToolSelectorTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：`RealLlmToolSelector` 是普通类，未注入 `DocumentAgentServiceImpl`，未接入生产 routing；测试仅用 fake client 模拟 JSON 返回，未真实调用 LLM。

### T014c

- 状态：DONE
- 完成时间：2026-05-14
- 任务目标：新增 Real LLM selector disabled shadow runner 和运行结果对象。
- 验证结果：`mvn -Dtest=RealLlmSelectorShadowRunnerTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：runner 仅在单元测试中验证 disabled / fake client 行为；未接入 `DocumentAgentServiceImpl`，未记录 metrics，未真实调用 LLM，未接管生产 routing。

### T014d

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：更新 selector shadow mode 文档和协作文档，说明 real LLM selector adapter 已有但默认 disabled。
- 验证结果：`git status --short` 检查通过；`git diff -- docs/AGENT_SELECTOR_SHADOW_MODE.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md` 已复核。
- 边界：仅修改文档；未修改代码，未调用真实 LLM，未接 function calling，未接入生产 service，未改变默认 routing。

### T015a

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：补充 real LLM selector shadow runner 的安全开关。
- 验证结果：`mvn -Dtest=AgentSelectorPropertiesTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：默认 `realShadowEnabled=false`、`realShadowRecordMetrics=false`、`realShadowFailOpen=true`；未修改 `application.yml`，未启用真实模型调用，未改变默认 routing。

### T015b

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：将 `RealLlmSelectorShadowRunner` 接入 `DocumentAgentServiceImpl` 的禁用态 shadow 路径。
- 验证结果：`mvn -Dtest=DocumentAgentServiceImplTest test` 通过；`mvn test -DskipITs` 通过。
- 边界：real shadow 仅在 `shadowEnabled=true` 且 `realShadowEnabled=true` 时旁路执行；默认不运行；失败 fail-open；真实执行工具仍只来自 primary `DocumentToolSelector` decision；未修改 API、前端、DDL 或默认 routing。

### T015c

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：新增聚焦 real shadow path 的 service 单元测试。
- 验证结果：`mvn -Dtest=DocumentAgentRealShadowPathTest test` 通过；`mvn test -DskipITs` 通过。
- 边界：仅新增测试；覆盖默认关闭、fake shadow 不隐式启用 real shadow、disabled / exception fail-open、parseReady=false 跳过 real shadow、real metrics 默认不记录和显式开启后记录；未修改生产代码。

### T015d

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：更新 selector shadow 文档和协作状态，说明 real shadow runner 已接入 service 但默认关闭。
- 验证结果：`git status --short` 检查通过；`git diff -- docs/AGENT_SELECTOR_SHADOW_MODE.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md` 已复核。
- 边界：仅修改文档；未调用真实 LLM，未接 function calling，未新增 API，未修改前端，未改变默认 routing；完整 T010 仍 BLOCKED。

### T016a

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：新增 LLM selector provider 配置模型。
- 验证结果：`mvn -Dtest=AgentSelectorPropertiesTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：默认 `llmProvider=disabled`、`llmModel` / `llmBaseUrl` 为空、`llmRequestTimeoutMs=3000`；未修改 `application.yml`，未读取环境变量或 `backend/.env`，未启用真实模型调用。

### T016b

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：新增 `FakeLlmToolSelectionClient`，为后续 shadow-only smoke 提供不联网的 provider client。
- 验证结果：`mvn -Dtest=FakeLlmToolSelectionClientTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：fake client 不联网、不读取环境变量或 `backend/.env`、不需要 API Key；仅按 prompt 中 task 生成可被 parser 解析的 JSON，不影响 disabled client 或生产 routing。

### T016c

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：新增 OpenAI-compatible LLM selection client skeleton。
- 验证结果：`mvn -Dtest=OpenAiCompatibleLlmToolSelectionClientTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：仅提供 request / response 和 dry-run client 骨架；`completeSelectionPrompt` 返回 disabled，不发 HTTP、不读取 API Key、不读取环境变量或 `backend/.env`，未接入生产 service。

### T016d

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：新增 LLM selection client factory，根据 provider 选择 disabled / fake / openai-compatible client。
- 验证结果：`mvn -Dtest=LlmToolSelectionClientFactoryTest test` 通过；`mvn test -DskipITs` 通过。
- 边界：factory 未接入 production service；默认 provider 返回 disabled client；openai-compatible 当前仍是 disabled/dry-run，不联网、不读取 API Key 或 `backend/.env`。

### T016e

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：更新 selector shadow 文档和协作状态，说明 provider-specific skeleton 已有但仍未真实调用。
- 验证结果：`git status --short` 检查通过；`git diff -- docs/AGENT_SELECTOR_SHADOW_MODE.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md` 已复核。
- 边界：仅修改文档；provider 默认 disabled，fake 仅用于测试，openai-compatible 仅为 dry-run skeleton；未真实调用 LLM，未读取 API Key 或 `backend/.env`，未新增 API，未改变 production routing。

### T017a

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：新增 factory-backed real selector builder。
- 验证结果：`mvn -Dtest=RealLlmToolSelectorFactoryTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：默认 provider=disabled 时 selector 调用明确失败；provider=fake 可返回合法 decision；openai-compatible 仍 dry-run disabled；未接入 service，未读取 API Key 或 `backend/.env`。

### T017b

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：让 `RealLlmSelectorShadowRunner` 支持 factory-backed selector。
- 验证结果：`mvn -Dtest=RealLlmSelectorShadowRunnerTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：默认 disabled provider 返回 success=false；provider=fake 可返回 success=true / matched；openai-compatible 仍 dry-run disabled；未修改 service、API、前端或 production routing。

### T017c

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：在 service 测试中验证 factory-backed real shadow success path。
- 验证结果：`mvn -Dtest=DocumentAgentRealShadowPathTest test` 通过；`mvn -Dtest=DocumentAgentServiceImplTest test` 通过；`mvn test -DskipITs` 通过，223 tests。
- 边界：`DocumentAgentServiceImpl` 的 real shadow runner 现在通过 `RealLlmToolSelectorFactory` 和 provider 配置构造 selector；默认 provider 仍 disabled，provider=fake 仅用于测试验证 real shadow success；未真实调用 LLM，未读取 API Key 或 `backend/.env`，未新增 API，未修改前端，未改变 production routing。

### T017x

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：修正 `FakeLlmToolSelectionClient` 的本地规则，使 fake provider 更稳定模拟未来 LLM selector 输出。
- 验证结果：`mvn -Dtest=FakeLlmToolSelectionClientTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：仅增强 fake provider 的 prompt task 提取与本地规则；未修改 `DocumentToolSelector`，未修改 eval cases，未真实调用 LLM，未读取 API Key 或 `backend/.env`，未改变 production routing。

### T017d

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：新增 fake provider real shadow 离线评估。
- 验证结果：`mvn -Dtest=RealShadowProviderEvaluationTest test` 通过；`mvn test -DskipITs` 通过，229 tests。评估结果：total=24，success=22，failures=2，matched=22，mismatch=0，matchRate=0.9167。
- 边界：仅使用 `RealLlmSelectorShadowRunner + provider=fake` 跑离线对比；两个 failure 来自 blank task 被 prompt builder 拒绝；未修改 eval cases，未真实调用 LLM，未读取 API Key 或 `backend/.env`，未改变 production routing。

### T017e

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：更新 selector shadow 文档和协作状态，收口 T017。
- 验证结果：`git status --short` 检查通过；`git diff -- docs/AGENT_SELECTOR_SHADOW_MODE.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md` 已复核。
- 边界：仅修改文档；provider=disabled 仍是默认，provider=fake 仅用于测试 / 后续 shadow-only runtime，openai-compatible 仍不联网；未真实调用 LLM，未读取 API Key 或 `backend/.env`，未新增 API，未改变 production routing。

### T018a

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：补充 real shadow runtime 安全日志，便于 fake provider shadow-only runtime 验证。
- 验证结果：`mvn -Dtest=DocumentAgentRealShadowPathTest test` 通过；`mvn -Dtest=DocumentAgentServiceImplTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：日志只记录 provider、primary / shadow decision、matched、metricsRecorded 等安全摘要；不输出 prompt、task 全文、文档内容、密钥、token 或真实连接信息；未改变 production routing、API 或前端。

### T018b-retry / T018c / T018d

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：使用 provider=fake 完成 real shadow selector 的 shadow-only runtime / smoke 验证，并记录结果。
- 验证结果：本地后端连接用户授权的远程中间件运行；使用已解析文档 `documentId=61` 完成浏览器 summary / QA 验证；后端安全日志可见 `provider=fake` real shadow compare；summary primary decision=`summary_tool`，QA primary decision=`qa_tool`；页面展示 routingReason、matchedKeywords、持久化 trace 和 QA citations。
- 回归结果：后端 `mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过，229 tests；前端 `npm run lint` 通过；`npm run build` 通过。
- 边界：只验证已解析文档上的 Agent runtime 和 fake provider shadow compare；未验证上传 / 解析 / MQ 全链路；未真实调用 LLM，未读取 API Key 或 `backend/.env`，未向模型 provider 发真实 HTTP，未使用 hk-ops，未新增 API，未修改前端，未改变 production routing。完整 T010 仍为 BLOCKED。

### T019-preflight

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：为真实 provider shadow-only 调用制定前置安全方案和代码检查清单。
- 验证结果：新增 `docs/REAL_PROVIDER_SHADOW_PREFLIGHT.md`；复核 openai-compatible client、client factory、real selector、shadow runner、selector properties 和 service 当前边界；`git diff` 已复核。
- 边界：仅修改文档；未真实调用 provider，未读取 API Key 或 `backend/.env`，未发真实 HTTP，未修改 application 配置，未新增 API，未修改前端，未改变 production routing。

### T019-real-shadow-only

- 状态：DONE
- 优先级：P3
- 任务目标：在用户明确授权后，用真实 provider 做 shadow-only runtime 验证。
- 验证结果：用户授权后使用 provider=`openai_compatible` 完成真实 provider shadow-only runtime；真实 HTTP 调用 2 次；summary primary / shadow 均为 `summary_tool`，QA primary / shadow 均为 `qa_tool`，shadow parse success=true，mismatch=false，QA citations 正常。
- 回归结果：后端 `mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过，244 tests；前端 `npm run lint` 通过；`npm run build` 通过。
- 边界：真实执行工具仍由 `DocumentToolSelector` 决定；real provider 只产生 shadowDecision；协作代理未读取或输出 API Key，未输出完整 baseUrl、prompt、文档内容或模型完整返回；未改变 Agent API、前端、数据库或 production routing；完整 T010 仍为 BLOCKED。

### T019-recovery

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：修复 T019e 全量测试因本机真实 provider 环境变量继承导致的配置绑定 / 测试隔离问题。
- 验证结果：`AgentSelectorProperties` 支持 OpenAI-compatible 常见 provider alias；`AgentSelectorPropertiesTest` 与 `DocPilotApplicationTests` 显式隔离 selector provider 默认值；targeted tests 通过；后端全量测试通过。
- 边界：未读取或输出真实环境变量值，未读取 `backend/.env`，未改变 production routing，未新增 API，未修改前端代码。

### T020

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：补齐真实 provider shadow mismatch / metrics 记录与阈值策略基础设施。
- 验证结果：`SelectorMetricsCollector` / `SelectorMetricsSnapshot` 已支持 total / success / failure / matched / mismatch、matchRate、failureRate、provider 聚合和 decision pair 聚合；新增 `SelectorShadowThresholdPolicy` / `SelectorShadowThresholdDecision`；新增阈值评估测试，确认 `promotionCandidate` 不会改变 `DocumentAgentServiceImpl` 的 primary decision。
- 默认阈值：`minimumSamples=20`，`minMatchRate=0.95`，`maxFailureRate=0.05`。
- 回归结果：后端 `mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过；前端 `npm run lint` 通过；`npm run build` 通过。
- 边界：threshold policy 只产生 `allowPromotionCandidate` 和 reason，不自动接管 routing，不修改配置，不落库，不接 Prometheus，不新增 API，不改前端，不真实调用 provider；完整 T010 仍为 BLOCKED。

### T021

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：为 selector shadow metrics 提供内部只读 debug dump / reporter。
- 当前结果：已新增 `SelectorMetricsDebugSnapshot` 和 `SelectorMetricsDebugReporter`，并用离线 evaluation 测试验证 dump 可展示 total / success / failure / matched / mismatch、matchRate、failureRate、provider 聚合、decision pair 聚合和 threshold decision。
- 设计边界：T021 不新增 HTTP API，不新增 Actuator endpoint，不接 Prometheus，不落库，不改变 production routing，不让 `promotionCandidate` 接管真实工具执行。
- 暂不开放 API / Actuator 原因：selector metrics 可能包含 provider / decision 等运行信息，管理端鉴权、内网暴露范围和脱敏策略尚未单独设计。
- 后续观测入口选择：A. 本地 CLI / debug dump；B. Actuator endpoint，仅限内网和认证；C. 管理端 API，需要鉴权和脱敏。
- 回归结果：后端 `mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过；前端 `npm run lint` 通过；`npm run build` 通过。
- 下一步：推荐 T022 先写 Actuator / 管理 API / Prometheus 观测入口设计文档，不直接写接口。

### T022

- 状态：DONE
- 完成时间：2026-05-16
- 任务目标：完成 Agent selector shadow metrics 观测入口设计决策。
- 当前结果：已新增 `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`，覆盖本地 debug dump、Actuator endpoint、管理端 API、Prometheus metrics 四种方案；已补充决策矩阵和安全威胁模型。
- 推荐路线：短期继续本地 debug dump；T023 优先写 Actuator endpoint 设计草案；中期再考虑 Prometheus 数值指标；管理端 API 暂缓。
- 边界：T022 只做设计文档；不新增 API，不新增 Actuator endpoint，不接 Prometheus，不落库，不修改 Java / 前端 / 配置，不改变 production routing；完整 T010 仍为 BLOCKED。
- 自检结果：`git diff --name-only HEAD~4..HEAD` 仅包含允许文档；未修改 Java 生产代码、测试代码、前端、配置、DDL 或 API 层。
- 下一步：进入 T023，但 T023 也应先写 Actuator endpoint 设计草案，不直接实现接口。

### T023

- 状态：DONE
- 完成时间：2026-05-16
- 任务目标：完成 Agent selector shadow metrics Actuator endpoint 设计草案。
- 当前结果：已新增 `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`，设计候选 endpoint `agentSelectorShadow` / `/actuator/agentSelectorShadow` / GET / readOnly，补充字段白名单、字段黑名单、默认开关、安全鉴权、审计、候选实现类、依赖关系和测试策略。
- 设计边界：T023 只写设计文档；尚未实现 Actuator endpoint，未新增 API / Controller，未接 Prometheus，未落库，未修改 Java / 测试 / 前端 / 配置，未改变 production routing。
- 自检结果：`git diff --name-only HEAD~4..HEAD` 仅包含允许文档；未修改 Java 生产代码、测试代码、前端、配置、DDL 或 API 层。
- 下一步：若后续进入 T024 候选实现，建议先做 Claude Code / 人工安全审查，确认默认关闭、白名单字段、黑名单字段和鉴权边界。

### T024

- 状态：DONE
- 完成时间：2026-05-16
- 任务目标：实现默认关闭的 Agent selector shadow metrics Actuator endpoint。
- 实现边界：必须使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`；这是项目中第一个自定义 Actuator endpoint，没有既有模式可复用，因此只做最小实现。
- 配置边界：不修改 `application.yml` / `application-local.yml`，不加入 `management.endpoints.web.exposure.include`，不接 Prometheus；现有 Prometheus endpoint 与 selector-specific Prometheus metrics 是两回事，本轮不修改现有 Prometheus 配置。
- 测试边界：T024 只做默认关闭 endpoint、单元测试和 context 默认 404 测试；暂不测试“未授权访问被拒绝”，因为当前没有专门的 Actuator Spring Security 配置，该项留到 T025。
- 当前结果：已新增 `AgentSelectorShadowEndpoint`，endpoint id 为 `agentSelectorShadow`，候选 path 为 `/actuator/agentSelectorShadow`，通过 `@ReadOperation` 提供只读 GET 语义，默认关闭且不加入 exposure include。
- 验证结果：`mvn -Dtest=AgentSelectorShadowEndpointTest test`、`mvn -Dtest=AgentSelectorShadowEndpointExposureTest test`、`mvn -DskipTests compile`、`mvn test -DskipITs`、`npm run lint`、`npm run build` 均通过。
- 边界：未新增普通 REST API / Controller，未修改配置文件、前端、DDL 或 production routing，未接 Prometheus，未落库，未真实调用 provider，未读取或输出 secret。

### T025

- 状态：DONE
- 完成时间：2026-05-16
- 任务目标：完成 Agent selector shadow Actuator endpoint 安全配置 / 显式开启策略设计。
- 当前结果：已新增 `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`，记录 T024 默认关闭 endpoint 的安全开启目标、默认关闭策略、local / dev / test / prod 分环境策略、禁止策略和 T026-T030 后续拆分。
- 设计边界：T025 只写设计文档，不真正开启 endpoint；endpoint 当前仍默认关闭，尚未修改 `application.yml` / `application-local.yml`，尚未加入 `management.endpoints.web.exposure.include`，尚未接 Spring Security，尚未接 Prometheus，尚未开启 dev / prod 访问。
- 自检结果：`git diff --name-only cc7dd7f^..HEAD` 仅包含允许文档；未修改 Java 生产代码、测试代码、前端、`application.yml`、`application-local.yml` 或 profile 配置；未新增 exposure include、Spring Security 或 Prometheus；未读取或输出 secret；未改变 production routing。
- 下一步：先做 T026 CC / 人工安全审查 T025 策略，再考虑 T027 测试内显式开启验证；不要直接暴露到生产。
- 边界：未修改 Java 生产代码、测试代码、前端、配置文件、DDL 或 production routing；未真实调用 provider，未读取或输出 secret；完整 T010 仍为 BLOCKED。

### T027

- 状态：DONE
- 完成时间：2026-05-16
- 任务目标：只在测试内显式开启 Agent selector shadow Actuator endpoint，验证访问和安全字段。
- commit：71db1cd test(agent): verify selector actuator endpoint enabled in test
- 当前结果：新增 `AgentSelectorShadowEndpointEnabledTest`；显式开启后 `GET /actuator/agentSelectorShadow` 返回 200；白名单字段检查、黑名单字段检查、空 metrics 检查和 metrics 不变检查均通过。
- 配置命名：开启开关使用单数 `management.endpoint.agent-selector-shadow.enabled=true`，其中 `agent-selector-shadow` 是 endpoint id `agentSelectorShadow` 的 relaxed binding 写法；web 暴露使用复数 `management.endpoints.web.exposure.include=agentSelectorShadow`，值使用 endpoint id，不要写成 `agent-selector-shadow`；禁止 `management.endpoints.web.exposure.include=*`。
- 验证结果：`mvn -Dtest=AgentSelectorShadowEndpointEnabledTest test` 通过；`mvn "-Dtest=AgentSelectorShadowEndpointTest,AgentSelectorShadowEndpointExposureTest,AgentSelectorShadowEndpointEnabledTest" test` 通过；`mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过，292 tests；前端 `npm run lint` / `npm run build` 通过。
- 边界：T027 只修改测试代码，不修改生产代码、`application.yml`、`application-local.yml`、前端或文档；未新增 Spring Security，未接 Prometheus，未真实调用 provider，未读取或输出 secret；默认状态仍关闭，生产环境仍未开启；完整 T010 仍为 BLOCKED。

### T028

- 状态：DONE
- 完成时间：2026-05-16
- 任务目标：补充 dev profile / local enablement proposal，避免后续直接上生产开启。
- 当前结果：`docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md` 已补充 local 临时环境变量开启草案、dev 部署环境变量开启草案、dev 开启前置条件，以及 T029-T032 后续拆分。
- 后续拆分：T029-security-integration-design 只做 Spring Security / Actuator 安全方案设计；T030-test-security-integration 只在测试中验证鉴权策略；T031-dev-profile-example 只提供 example 配置或文档；T032-prometheus-metrics-design 只设计 Prometheus 数值指标。
- 自检结果：`git diff --name-only a9d0ffc^..HEAD` 仅包含允许文档；未修改 Java 生产代码、测试代码、前端、`application.yml`、`application-local.yml` 或 profile 配置。
- 边界：T028 是设计文档任务，未真正开启 endpoint，未新增 Spring Security，未接 Prometheus，未真实调用 provider，未读取或输出 secret；完整 T010 仍为 BLOCKED。
- 下一步：T029 已完成，建议进入 T030-test-security-integration 的测试内鉴权策略验证设计审查，或先让 CC / 人工审查 T029。

### T029

- 状态：DONE
- 完成时间：2026-05-17
- 任务目标：完成 Spring Security / Actuator 安全集成设计。
- 当前结果：新增 `docs/AGENT_ACTUATOR_SECURITY_INTEGRATION_DESIGN.md`，并同步 selector actuator / observability / shadow mode 协作文档；设计内容覆盖访问角色、路径边界、未授权访问行为、访问来源限制、未来 T030 测试策略、dev / prod 开启前 checklist 和回滚策略。
- 边界：T029 只写设计文档，没有实现 Spring Security，没有新增 `SecurityFilterChain`，没有修改 `application.yml` / `application-local.yml`，没有真正开启 endpoint，没有接 Prometheus，没有修改 Java / 测试 / 前端代码，没有读取或输出 secret；endpoint 当前仍默认关闭。
- 下一步：建议进入 T030-test-security-integration 的测试内鉴权策略验证设计审查，或先让 CC / 人工审查 T029；不建议直接进入生产开启。

### T030

- 状态：BLOCKED
- 完成时间：2026-05-17
- 任务目标：测试内验证 `agentSelectorShadow` Actuator endpoint 的鉴权策略。
- 当前结果：T030a preflight 已完成；当前后端只发现 `spring-security-crypto`，没有发现 `spring-boot-starter-security`、`spring-security-test`、`SecurityFilterChain` 或现有 Web 鉴权测试配置。
- 阻塞原因：按本轮边界不允许新增 Maven 依赖，不允许修改生产配置或新增生产 Spring Security；因此无法在测试内可靠验证未认证 401 / 403、普通用户 403、OPS / ACTUATOR_ADMIN 200 的访问策略。
- 边界：未新增测试类，未修改 Java 生产代码，未修改现有测试，未修改 `application.yml` / `application-local.yml`，未修改前端，未接 Prometheus，未操作远程中间件，未真实调用 provider，未修改 production routing。
- 下一步：需要用户确认是否允许新增测试所需的 Spring Security 依赖，或先开 T030-design-review 重新收窄鉴权验证方案；完整 T010 仍为 BLOCKED。

### T031

- 状态：DONE
- 完成时间：2026-05-17
- 任务目标：补充 dev / local 如何临时开启 `agentSelectorShadow` endpoint 的文档示例。
- 当前结果：`docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md` 和 `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md` 已补充 PowerShell local 临时环境变量示例、dev 运维侧显式开启边界、配置命名说明和禁止策略。
- 边界：T031 只是文档示例，没有修改 `application.yml`、`application-local.yml` 或任何 profile 配置文件，没有真正开启 endpoint，没有新增 Spring Security，没有接 Prometheus，没有修改 Java / 测试 / 前端代码。

### T032

- 状态：DONE
- 完成时间：2026-05-17
- 任务目标：设计 selector shadow metrics 未来接 Prometheus 的方案。
- 当前结果：新增 `docs/AGENT_SELECTOR_PROMETHEUS_METRICS_DESIGN.md`，记录候选数值指标、允许的低风险 label、禁止字段、cardinality 风险和 T033-T036 后续拆分。
- 边界：T032 只是 Prometheus 设计文档，没有接入 Prometheus，没有修改 `pom.xml`，没有修改代码、测试、配置或前端，没有新增依赖，没有改变 production routing。

### T040a

- 状态：DONE
- 完成时间：2026-05-17
- 任务目标：审计 DocPilot 当前真实能力、半实现能力、BLOCKED 能力和面试风险。
- 当前结果：新增 `docs/PROJECT_INTERVIEW_BRIEF.md`，记录一句话定位、真实已实现能力、半实现能力、T010 / T030 BLOCKED、不能写成已完成的能力、5 个简历亮点和高风险追问的诚实回答。
- 边界：仅修改面试向文档和协作文档；未修改 Java、测试、前端或配置；未读取 `backend/.env`；未输出 secret。

### T040b

- 状态：DONE
- 完成时间：2026-05-17
- 任务目标：新增项目架构说明，面向投递和面试讲解。
- 当前结果：新增 `docs/PROJECT_ARCHITECTURE_OVERVIEW.md`，包含总体架构、核心链路、系统总体架构 Mermaid 图和 Agent 执行链路 Mermaid 图，并标注 T010 / Actuator / Prometheus 边界。
- 边界：仅修改文档；未修改 Java、测试、前端或配置；未读取 `backend/.env`；未输出 secret。

### T040c

- 状态：DONE
- 完成时间：2026-05-17
- 任务目标：整理适合 Java 后端实习 / AI 应用工程化投递的简历 bullet。
- 当前结果：新增 `docs/RESUME_BULLETS.md`，包含保守版、标准后端实习版和 AI 应用工程化版三套写法，并标注禁止写成已完成的能力。
- 边界：仅修改文档；未修改 Java、测试、前端或配置；未读取 `backend/.env`；未输出 secret。

### T040d

- 状态：DONE
- 完成时间：2026-05-17
- 任务目标：生成 DocPilot 面试问答稿。
- 当前结果：新增 `docs/INTERVIEW_QA.md`，按 10 个模块整理 30 个高频问题，每题包含面试可背版回答、追问、诚实边界和对应文件或模块位置。
- 边界：仅修改文档；未修改 Java、测试、前端或配置；未读取 `backend/.env`；未输出 secret。

### T040e

- 状态：DONE
- 完成时间：2026-05-17
- 任务目标：更新 README 对外展示口径，让 GitHub 主页更适合投递且不夸大能力。
- 当前结果：README 已补充 AI 文档问答、SSE、Agent 最小闭环、AgentTask / AgentStep 持久化、ToolSelector、selector shadow compare、真实 provider shadow-only、metrics debug dump 和默认关闭 Actuator endpoint；同时明确完整上传解析链路 T010 BLOCKED、Actuator endpoint 默认关闭、Spring Security 未接入、selector Prometheus metrics 未接入、shadow decision 不接管 production routing。
- 边界：仅修改 README 和协作文档；未修改 Java、测试、前端或配置；未读取 `backend/.env`；未输出 secret；未真正开启 endpoint；未接 Prometheus；未新增 Spring Security。

### T019a

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：补齐 selector real provider 最小配置字段。
- 验证结果：`mvn -Dtest=AgentSelectorPropertiesTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：默认 provider 仍为 disabled，默认 `realShadowEnabled=false`、`realShadowRecordMetrics=false`；新增 `llmApiKey`、`llmMaxTokens`、`llmTemperature` 仅作为配置字段，不读取或输出真实 API Key，未修改 `application.yml`，未改变 production routing。

### T019b

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：实现 OpenAI-compatible selector client 的真实 HTTP 能力。
- 验证结果：`mvn -Dtest=OpenAiCompatibleLlmToolSelectionClientTest test` 通过；`mvn -Dtest=LlmToolSelectionClientFactoryTest test` 通过；`mvn -DskipTests compile` 通过。
- 边界：只有 apiKey、baseUrl、model 都存在时才会发 HTTP；测试使用本地 stub server，不需要真实 API Key；client 不打印 request body、prompt、Authorization、完整 baseUrl 或模型完整返回；未修改 `application.yml`，未新增 API，未改变 production routing。

### T019c

- 状态：DONE
- 完成时间：2026-05-15
- 任务目标：补充 real provider shadow fail-open 后端测试。
- 验证结果：`mvn -Dtest=DocumentAgentRealShadowPathTest test` 通过；`mvn -Dtest=RealLlmSelectorShadowRunnerTest test` 通过；`mvn -Dtest=RealLlmToolSelectorFactoryTest test` 通过；`mvn test -DskipITs` 通过。
- 边界：测试覆盖 openai-compatible 缺 apiKey / 缺 baseUrl、client failure、parser failure、shadow failure 不记录成功 metrics、primary decision 不变；未真实调用外部模型，未读取 API Key 或 `backend/.env`，未改变 production routing。

## 任务列表

### T000

- 状态：TODO
- 优先级：P0
- 任务目标：审计工作区改动 + 敏感信息检查。
- 为什么要做：这是历史遗留审计项；截至 T009e 同步时工作区干净，但如果后续再次出现 modified / untracked 文件，仍需先明确哪些文件安全、哪些可能包含敏感信息、哪些属于历史残留。
- 涉及文件：全仓库只读审计；重点包括 `git status`、`.env`、`.env.*`、`application-*.yml`、`*.example`、`README.md`、`docs/`。
- 前置依赖：无；本任务只做审计，不修改文件。
- 验收标准：列出所有 modified 文件；列出所有 untracked 文件；检查 `.env`、`.env.*`、`application-*.yml`、`*.example`、README、docs 是否包含 API Key、token、password、secret、真实云服务 IP；只输出审计报告，不自动提交，不删除文件，不修改业务代码。
- 验证命令：`git status --short`；`git diff --name-only`；`git ls-files --others --exclude-standard`；`rg -n "(?i)(api[_-]?key|token|password|secret|access[_-]?key|private[_-]?key|116\.204\.|[0-9]{1,3}(\.[0-9]{1,3}){3})" .env* backend/src/main/resources/application-*.yml **/*.example README.md docs`。
- 风险点：只能审计和报告，不下结论是否提交；不能删除本地文件；不能把敏感值复制到最终回复里，发现时只说明文件和键名。
- 面试价值：体现代码公开前的安全审计、仓库治理和交付责任感。
- 下一步动作：先输出完整审计报告，再由用户决定哪些文件保留、忽略、清理或提交。

### T000b

- 状态：REVIEW
- 优先级：P0
- 任务目标：敏感信息脱敏修复 + 协作文档入库策略修正。
- 为什么要做：T000 审计发现 README、backend README、cloud env example、application-local、CONSTRAINTS 中存在真实公网 IP 风险，同时根目录 `docs/*` 规则导致协作文档无法被普通 `git status` 看见。
- 涉及文件：`.gitignore`、`README.md`、`backend/README.md`、`backend/.env.cloud.example`、`backend/.env.demo.example`、`backend/src/main/resources/application-local.yml`、`docs/ai-dev/CONSTRAINTS.md`、`AGENTS.md`、`docs/TODO_NEXT.md`、`docs/CODEX_HANDOFF.md`、`docs/CHANGELOG_CODING.md`。
- 前置依赖：已完成 T000 审计；本任务只处理安全脱敏和协作文档入库策略，不做业务代码开发。
- 验收标准：指定文件中不再保留真实公网 IP；`backend/.env` 仍未被 Git 跟踪；`docs/CODEX_HANDOFF.md`、`docs/TODO_NEXT.md`、`docs/CHANGELOG_CODING.md` 能被 `git status` 看见；不执行 `git add` / `git commit` / `git push`。
- 验证命令：`git status`；`git diff --stat`；`git diff --name-only`；对指定文件执行公网 IP 扫描；`git check-ignore -v backend/.env docs/CODEX_HANDOFF.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md`。
- 风险点：不能输出真实 IP、密码、token、API Key；不能修改 `backend/.env`；不能顺手改业务代码。
- 面试价值：体现开源前安全脱敏、仓库可追踪性和协作文档治理。
- 下一步动作：等待用户复核本轮 diff；通过后再进入 T001a 定位权威 eval artifact。

### T000c

- 状态：REVIEW
- 优先级：P0
- 任务目标：剩余敏感信息复核，重点检查 `.run` 配置文件。
- 为什么要做：T000b 已完成主要文档和配置脱敏，但 `.run/*.xml` 仍处于 modified 状态，需要确认是否包含真实公网 IP、密码、token、secret、API Key 等敏感信息。
- 涉及文件：`.run/*.xml`、README、backend README、backend env example、`application-*.yml`、docs markdown、`AGENTS.md`。
- 前置依赖：已完成 T000b；本任务只做剩余敏感信息复核，必要时仅脱敏 `.run/*.xml`。
- 验收标准：`.run/*.xml` 不包含真实公网 IP 或敏感值；允许检查范围内不输出任何真实敏感值；`backend/.env` 仍未被 Git 跟踪；不执行 `git add` / `git commit` / `git push`。
- 验证命令：扫描 `.run/*.xml` 的 IPv4 字面量和敏感关键词；扫描允许范围内的公网 IP；`git ls-files --error-unmatch backend/.env`；`git diff --stat`。
- 风险点：不要输出真实 IP、密码、token、API Key；不要读取或修改 `backend/.env`；不要修改业务代码。
- 面试价值：体现开源前最后一轮敏感信息复核和 IDEA 运行配置治理。
- 下一步动作：等待用户复核；确认无敏感信息后进入 T001a。

### T000d

- 状态：REVIEW
- 优先级：P0
- 任务目标：记录 Codex subagents 和 MCP 工具能力边界。
- 为什么要做：后续 Claude Code、Codex、ChatGPT 接手时需要知道本地 subagents、context7 MCP、playwright MCP 的用途、授权条件和禁止事项，避免误用远程工具、残留进程或泄露敏感信息。
- 涉及文件：`AGENTS.md`、`docs/CODEX_TOOLING.md`、`docs/CODEX_HANDOFF.md`、`docs/TODO_NEXT.md`、`docs/CHANGELOG_CODING.md`。
- 前置依赖：已完成 T000b/T000c 敏感信息脱敏和 `.run` 复核。
- 验收标准：新增 `docs/CODEX_TOOLING.md`；AGENTS 和 HANDOFF 有简短引用；hk-ops 明确需要用户确认；playwright 明确不要未经确认启动长期 dev server；context7 明确用于查官方/库文档；不记录真实 IP、账号、密码、token、API Key 或 `.env` 内容。
- 验证命令：`rg -n "CODEX_TOOLING|hk-ops|playwright|context7|password|token|API Key|CLOUD_HOST" AGENTS.md docs/CODEX_TOOLING.md docs/CODEX_HANDOFF.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md`；`git diff --stat`。
- 风险点：不要把工具说明写成实际凭据或服务器信息；不要误导后续 agent 以为 hk-ops 可直接使用。
- 面试价值：体现多 agent 协作、工具治理和安全边界意识。
- 下一步动作：等待用户复核；后续继续 T005a 或仓库提交前风险复查。

### T001a

- 状态：DONE
- 优先级：P0
- 任务目标：定位权威 eval artifact，确认哪份评测结果是当前基准。
- 为什么要做：当前 README、STATE、最新 artifact JSON 存在三套指标冲突，不能直接用其中任意一份继续写展示口径。
- 涉及文件：eval artifact 目录、`README.md`、`docs/ai-dev/STATE.md`、`docs/ai-dev/SHOWCASE.md`、`docs/ai-dev/HANDOFF.md`。
- 前置依赖：先完成 T000，确认 docs / artifact 中没有敏感信息风险。
- 验收标准：列出所有候选 eval markdown / json artifact；标出生成时间、样本数、关键指标；明确推荐哪一份作为当前基准，或说明需要重跑 eval。已完成只读定位：当前权威基准为 `docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`。
- 验证命令：`rg -n "answerSuccessRate|citationHitRate|casePassRate|streamVsNonStreamConsistency" README.md docs backend/scripts`；必要时读取 artifact JSON / markdown。
- 风险点：不能因为指标更好就选最新 artifact；必须说明为什么它是当前权威基准。
- 面试价值：体现评测证据链和可追溯指标管理。
- 下一步动作：已完成只读定位；进入 T001b 统一文档引用。

### T001b

- 状态：REVIEW
- 优先级：P0
- 任务目标：统一 README / STATE / docs / artifact 中的指标引用。
- 为什么要做：T001a 确定权威基准后，需要把公开文档和协作文档中的指标口径收敛到同一证据链。
- 涉及文件：`README.md`、`docs/ai-dev/SHOWCASE.md`、`docs/ai-dev/STATE.md`、`docs/ai-dev/HANDOFF.md`、必要的 artifact 引用说明。
- 前置依赖：必须先完成 T001a。
- 验收标准：所有公开指标都能追溯到同一 artifact；明确写清轻量检索增强边界；不再出现 README / STATE / artifact 互相矛盾的数字。已统一到 `stagec_eval_latest.json`，等待人工复查。
- 验证命令：`rg -n "answerSuccessRate|citationHitRate|casePassRate|90%|100%|57\.143|46\.154|50%" README.md docs`；`git diff --stat`。
- 风险点：不能篡改 artifact；不能把单次本地 eval 写成线上 SLA；不能夸大为向量 RAG。
- 面试价值：让项目证据链可信，便于说明“如何用数据驱动 AI 应用质量改进”。
- 下一步动作：等待用户复查本轮文档 diff；保留 T005a/T005b，用于补充 eval 规则、运行时配置记录和重跑验证。

### T002

- 状态：TODO
- 优先级：P0
- 任务目标：完成一次最小本地 smoke 验证并记录真实结果。
- 为什么要做：需要周期性确认本地基础启动、构建、测试是否仍可用；当前 Agent 路由解释链路更具体的运行时验证由 T010 承接。
- 涉及文件：`backend/README.md`、`frontend/README.md`、`docs/CODEX_HANDOFF.md`、`docs/CHANGELOG_CODING.md`。
- 前置依赖：本地 JDK、Maven、Node、npm、Docker 或可用中间件；先完成 T000，避免在敏感信息不明时扩大改动。
- 验收标准：后端 compile/test、前端 lint/build、基础健康检查或失败原因被真实记录。
- 验证命令：`cd backend; mvn -DskipTests compile; mvn test -DskipITs`；`cd frontend; npm run lint; npm run build`；`curl http://localhost:8081/actuator/health`。
- 风险点：本地中间件、端口占用、真实模型密钥可能缺失；不能伪造通过结果。
- 面试价值：体现工程项目可运行、可验证、可交接。
- 下一步动作：按 README 运行最小验证，失败时只记录真实阻塞，不顺手改业务代码。

### T002a

- 状态：DONE
- 优先级：P0
- 任务目标：验证 AI 问答 / SSE 后端改进包的编译和测试结果。
- 为什么要做：当前工作区存在一批历史业务改动，提交前需要先确认 AI 问答 / SSE 后端改进是否具备基础可合入性。
- 涉及文件：`backend/src/main/java/com/docpilot/backend/ai/service/impl/DocumentQaServiceImpl.java`、`backend/src/main/java/com/docpilot/backend/ai/service/impl/MockAiAnswerService.java`、`backend/src/main/java/com/docpilot/backend/ai/service/impl/RealAiAnswerService.java`、`backend/src/main/java/com/docpilot/backend/common/config/MinioStorageConfig.java`、`backend/src/main/resources/application.yml`、`backend/src/test/java/com/docpilot/backend/ai/service/DocumentQaServiceImplTest.java`、`backend/src/test/java/com/docpilot/backend/ai/service/RealAiAnswerServiceTest.java`。
- 前置依赖：本地 JDK 和 Maven 可用；不启动后端服务；不验证前端。
- 验收标准：`mvn -DskipTests compile` 通过；`mvn test -DskipITs` 通过；测试数量和失败数被记录。
- 验证命令：`cd backend; mvn -DskipTests compile; mvn test -DskipITs; cd ..; git status --short`。
- 验证结果：2026-05-13 本地执行通过；compile `BUILD SUCCESS`；test `BUILD SUCCESS`；`Tests run: 141, Failures: 0, Errors: 0, Skipped: 0`。
- 风险点：测试日志中包含限流、SSE 兜底、Redis 降级、解析失败等预期异常路径日志；这些日志未导致测试失败，但提交前仍建议做只读 diff 审查。
- 面试价值：体现 AI 问答 / SSE 改进不仅停留在实现层面，也经过后端编译和测试验证。
- 下一步动作：建议进入 Claude Code 只读提交前审查；该 AI 问答 / SSE 改进包适合作为独立提交候选。

### T002b

- 状态：DONE
- 优先级：P0
- 任务目标：自审、验证并提交前端 QA / SSE 展示改进包。
- 为什么要做：后端 AI 问答 / SSE 改进已作为独立提交落地，需要把前端流式事件解析、引用展示、Markdown 渲染和降级体验作为独立提交收口。
- 涉及文件：`frontend/app/documents/[documentId]/page.tsx`、`frontend/components/markdown-viewer.tsx`、`frontend/components/markdown-viewer.module.css`、`frontend/lib/qa-api.ts`、`docs/TODO_NEXT.md`、`docs/CHANGELOG_CODING.md`、`docs/CODEX_HANDOFF.md`。
- 前置依赖：T002a 后端改进已提交；本地 Node/npm 依赖可用。
- 验收标准：白名单 diff 自审无敏感信息；前端 lint/build 通过；暂存区只包含本任务允许文件；不提交 Agent Demo、benchmark、`.run`、根 README 或 AGENTS。
- 验证命令：`cd frontend; npm run lint; npm run build`；`git diff --cached --name-only`；`git diff --cached --stat`。
- 验证结果：2026-05-13 本地执行通过；`npm run lint` 无 warning/error；`npm run build` 编译、类型检查和静态生成通过。
- 风险点：当时工作区仍有未提交 Agent 页面和 dashboard/layout Agent 入口改动；本任务不提交这些内容。
- 面试价值：体现 SSE 前端事件协议适配、流式降级、引用展示和 Markdown 稳定渲染的工程闭环。
- 下一步动作：继续拆分剩余文档修复、Agent Demo、benchmark 和 `.run` 配置，不混提。

### T003

- 状态：TODO
- 优先级：P0
- 任务目标：审计并修复核心协作文档中的中文乱码和阶段流水账残留。
- 为什么要做：`docs/ai-dev/HANDOFF.md` 等历史文档存在乱码，影响后续 agent 接手和项目可信度。
- 涉及文件：`docs/ai-dev/HANDOFF.md`、`docs/ai-dev/STATE.md`、`docs/ai-dev/TASKS.md`、`docs/ai-dev/SHOWCASE.md`。
- 前置依赖：先完成 T000；确认哪些历史内容仍有保留价值，避免误删事实源。
- 验收标准：核心交接文档可读；不再把轮次日志当成当前事实；删除或合并内容有理由。
- 验证命令：`rg "�|Ã|Â|乱码" docs README.md backend/README.md frontend/README.md`；人工抽读修改后的文档。
- 风险点：可能误删历史决策；应只收口文档，不改业务逻辑。
- 面试价值：体现文档治理和工程协作习惯。
- 下一步动作：先列出乱码文件和重复段落，再小步整理。

### T004

- 状态：TODO
- 优先级：P1
- 任务目标：补齐或稳定一条 Playwright 主链路 smoke。
- 为什么要做：登录、上传、文档详情、问答、Agent 是展示链路，缺浏览器级回归会让 UI 改动风险变高。
- 涉及文件：`frontend/`、可能的 e2e 脚本目录、`docs/CODEX_HANDOFF.md`。
- 前置依赖：前后端可启动；有可用测试账号或注册接口；中间件可用；先完成 T000 / T002。
- 验收标准：Playwright 能验证至少登录 / 文档列表或详情 / Agent 页面；失败时有明确日志。
- 验证命令：使用 Playwright MCP 打开页面并走主流程；如已有脚本则执行对应 npm 命令。
- 风险点：本地环境不稳定；真实上传文件和数据状态可能影响可重复性。
- 面试价值：体现端到端验证和演示稳定性。
- 下一步动作：先查现有 e2e / Playwright 配置，再决定用 MCP 手工流还是脚本化。

### T005a

- 状态：TODO
- 优先级：P1
- 任务目标：列出当前 eval 规则与门禁阈值。
- 为什么要做：在修改 eval 之前，必须先明确当前 answerSuccess、citationHit、casePass、stream consistency 的判定逻辑和默认 gate。
- 涉及文件：`backend/scripts/benchmark/`、eval dataset、eval artifact、`docs/ai-dev/SHOWCASE.md`。
- 前置依赖：先完成 T001a，确认当前权威 artifact。
- 验收标准：输出当前规则、阈值、样本覆盖、已知宽松点和失败 case 分类；不修改 runner 逻辑。
- 验证命令：`rg -n "answerSuccess|citationHit|casePass|gate|threshold|consistency" backend/scripts docs`；读取当前 eval 报告。
- 风险点：不要在没理解规则前直接改阈值；不要把规则审计写成指标优化。
- 面试价值：体现 AI 评测规则审计和质量门禁意识。
- 下一步动作：逐项列出现有规则和它们的可信度问题。

### T005b

- 状态：TODO
- 优先级：P1
- 任务目标：逐项修改 eval 规则并重跑评测。
- 为什么要做：T005a 明确规则后，再小步改进判定逻辑和门禁阈值，避免通过放宽规则美化指标。
- 涉及文件：`backend/scripts/benchmark/`、eval dataset / artifact、`README.md`、`docs/ai-dev/SHOWCASE.md`。
- 前置依赖：必须先完成 T005a；本地后端和 eval 依赖可运行。
- 验收标准：规则变化有说明；不达标时脚本返回非 0；输出新的 markdown + json artifact；README / SHOWCASE 只引用真实结果。
- 验证命令：执行当前 stage C eval 脚本；检查生成的 markdown / json artifact；`git diff --stat`。
- 风险点：不能通过放宽规则美化指标；真实模型波动可能影响结果；需要保留旧结果可追溯。
- 面试价值：体现 AI 应用质量评测、门禁和回归治理。
- 下一步动作：一次只改一类规则，重跑并记录指标变化。

### T006

- 状态：TODO
- 优先级：P1
- 任务目标：明确 PDF 解析能力边界并统一 UI / README / 后端说明。
- 为什么要做：项目容易被误解为完整 PDF 智能解析系统，但当前主能力更偏 txt / md 和轻量文本解析。
- 涉及文件：`README.md`、`backend/README.md`、`frontend/README.md`、上传页文案、文档解析相关代码注释或提示。
- 前置依赖：确认当前 PDF 解析实际实现。
- 验收标准：所有公开文档和页面都清楚说明支持边界；不夸大 PDF 能力。
- 验证命令：`rg "PDF|pdf|解析" README.md backend/README.md frontend/README.md frontend backend/src/main/java`；必要时上传样例验证。
- 风险点：如果改前端文案，需要跑 lint/build；不能改解析逻辑。
- 面试价值：体现诚实边界管理和产品化表达。
- 下一步动作：先审计所有 PDF 表述，再统一口径。

### T007

- 状态：TODO
- 优先级：P2
- 任务目标：整理 Agent 演示案例和最小验证说明。
- 为什么要做：Agent 已有最小闭环，但需要更清楚的 demo 输入、预期 trace 和失败边界，才能稳定演示。
- 涉及文件：`README.md`、`docs/ai-dev/SHOWCASE.md`、`frontend/app/agent/`、后端 Agent service / DTO。
- 前置依赖：前后端 Agent 页面和 API 可运行。
- 验收标准：文档提供 2 个可复制 demo 输入；页面能展示工具步骤和最终回答；边界不夸大为多 Agent。
- 验证命令：后端 agent smoke；Playwright MCP 打开 `/agent` 并执行 demo 输入。
- 风险点：真实模型不可用时需要 mock 口径；不能把原始 JSON 当作用户展示。
- 面试价值：体现 AI Agent 工具调用和可视化 trace 能力。
- 下一步动作：先确认 `/agent` 当前交互，再补 demo 文档或最小 smoke。

### T008

- 状态：TODO
- 优先级：P2
- 任务目标：建立本地 / 香港云中间件排障清单。
- 为什么要做：项目依赖 MySQL、Redis、RocketMQ、MinIO、Prometheus，本地和云环境切换容易导致启动问题。
- 涉及文件：`backend/README.md`、`docs/CODEX_HANDOFF.md`、`.run/` 配置说明、`.env.example` / demo env 模板。
- 前置依赖：确认当前推荐启动方式和可用 run config。
- 验收标准：常见启动失败能按端口、配置、Bean、数据库、Redis、MQ、MinIO 分类排查；不包含真实密钥。
- 验证命令：`docker compose -f docker-compose.demo.yml ps`；`curl http://localhost:8081/actuator/health`；必要时检查 IDEA run config。
- 风险点：不能泄露云服务器密码或真实 env；不能把临时本机配置写成通用规则。
- 面试价值：体现复杂中间件联调和问题定位能力。
- 下一步动作：先基于 README 和 run config 梳理排障路径。

### T010-runtime-verify

- 状态：BLOCKED
- 优先级：P0
- 任务目标：完整验证 Agent 路由可解释性在浏览器端真实可用。
- 为什么要做：T009a-d 已完成后端 `routingReason` / `matchedKeywords`、selector 测试、前端展示和 smoke 断言，但仍需要一次完整前后端 runtime 验证确认浏览器页面真实可用。
- 涉及文件：只读验证后端 Agent API、smoke 脚本和前端 `/agent` 页面；仅在全部验证通过后更新 `docs/TODO_NEXT.md`、`docs/CODEX_HANDOFF.md`、`docs/CHANGELOG_CODING.md`。
- 前置依赖：T009e 已同步协作文档且 `git status --short` 干净；本地后端、前端和必要中间件可用。
- 验收标准：后端 smoke 通过；真实浏览器验证通过；页面看到 `routingReason` / `matchedKeywords`；页面看到持久化 task / step trace；summary / QA 两类 Agent run 均正常；QA 场景返回 citations；后端 compile/test 通过；前端 lint/build 通过。
- 验证命令：`cd backend; powershell -ExecutionPolicy Bypass -File scripts/agent/smoke-agent-min.ps1`；Playwright 打开 `/agent` 走 summary / QA；`cd backend; mvn -DskipTests compile; mvn test -DskipITs`；`cd frontend; npm run lint; npm run build`。
- 阻塞原因：T010x 已复现后端 smoke 在上传文档后等待 `parseStatus` 超时，原始报错为 `Parse timeout after 120 seconds.`；后端日志显示 `NoopParseTaskMessageProducer` 跳过解析消息发送；当前 MQ disabled / no-op producer 模式下不会推进真实异步解析，因此完整 T010 需要可用 MQ / 解析消费环境后才能验证。
- 风险点：本地端口占用、中间件不可用、解析超时或浏览器自动化失败；失败时停止并报告，不直接改业务代码。
- 面试价值：证明 Agent 路由解释、持久化 trace 和引用展示不是只停留在代码层，而是浏览器端可真实演示。
- 下一步动作：先执行 T010m 本地只读检查 MQ / parse 配置入口；如需处理中间件环境，等待用户确认是否允许 hk-ops 做远程中间件只读检查；如果 MQ 可用，再重跑完整 T010；如果用户不想接 MQ，可单独定义 T010-lite，但必须标注不是完整上传解析链路验证。暂不进入 T011，除非用户明确接受 Agent-only 替代验证。

### T010m-local-mq-readiness-check

- 状态：TODO
- 优先级：P0
- 任务目标：本地只读检查 MQ / parse 配置入口，定位 no-op producer 生效条件和完整 T010 所需环境条件。
- 为什么要做：T010 被解析链路阻塞，需要先厘清 producer / consumer / profile / property 条件，再决定是否由用户授权远程只读检查或定义替代验证。
- 涉及文件：后端 MQ / parse 相关代码、`application*.yml`、`deploy/`、`docker-compose.demo.yml`、Agent smoke 脚本和协作文档；本任务只读。
- 前置依赖：T010z 已记录 BLOCKED 并保持工作区干净。
- 验收标准：说明 `NoopParseTaskMessageProducer` 与真实 producer / consumer 的生效条件；说明当前默认配置为什么导致 parse timeout；列出完整 T010 所需环境；给出完整 T010、T010-lite、脚本诊断增强三种方案。
- 验证命令：`rg` / `Get-Content` 只读检查本地文件；不启动 MQ，不远程连接，不读取 `backend/.env`。
- 风险点：不能把本地只读检查写成远程环境已可用；不能把 T010 BLOCKED 写成通过。
- 面试价值：体现异步队列链路诊断、环境边界识别和验证口径治理。

### T011

- 状态：TODO
- 优先级：P1
- 任务目标：面试向项目总结、架构图和简历亮点收口。
- 为什么要做：T010 通过后，项目需要把已验证能力整理成克制、可信、可讲清楚的展示材料。
- 涉及文件：README、docs 展示材料或新增面试准备文档，具体范围进入任务前再限定。
- 前置依赖：T010 runtime 验证通过。
- 验收标准：总结不夸大为完整向量 RAG / 生产级多 Agent；突出 RocketMQ + Outbox、Agent trace、SSE、eval / smoke 证据链。
- 风险点：不要把未实现的 MQ Agent、RAG、MCP、LLM Tool Calling 写成已实现。
- 面试价值：把工程实现转成面试官能快速理解的项目叙事。

## 推荐第一个任务

求职展示冲刺路线已完成 T050 / T056 / T052 / T053 / T054 / T055。下一步建议进入 T057：截图包 / 演示验证，或 T054+：接真实 embedding / Qdrant 前的默认关闭实验路径；也可以进入 T051：Function Calling takeover 可开关模式。完整 T010 仍因 MQ disabled / `NoopParseTaskMessageProducer` BLOCKED；暂不建议继续推进 Prometheus / Spring Security / 生产 Actuator 暴露。
