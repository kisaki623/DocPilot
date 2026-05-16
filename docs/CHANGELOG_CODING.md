# CHANGELOG_CODING.md

记录 Codex 协作过程中的关键变更。不要把它写成业务功能宣传页；每条记录都应说明目标、范围、验证和遗留问题。

## 2026-05-17 - T040a Project Interview Brief

### 本轮目标

进入 T040 项目投递和面试向收口，先完成当前真实能力审计。

### 修改文件

- `docs/PROJECT_INTERVIEW_BRIEF.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增项目一句话定位。
- 梳理当前真实已实现能力、半实现能力和 BLOCKED 能力。
- 明确不能写成已完成的能力：Prometheus 未接入、Spring Security 未接入、Actuator endpoint 默认关闭、shadow decision 未接管 production routing。
- 整理 5 个适合简历展示的工程亮点。
- 补充面试高风险追问和诚实回答。

### 当前边界

- 仅修改文档。
- 未修改 Java 生产代码。
- 未修改测试代码。
- 未修改前端代码。
- 未修改配置文件。
- 未读取 `backend/.env`。
- 未输出 secret。
- T010 仍 BLOCKED。
- T030 仍 BLOCKED。

## 2026-05-17 - T031/T032 Actuator Enablement And Metrics Planning

### 本轮目标

连续完成 T031 local / dev 临时开启示例文档和 T032 selector shadow Prometheus metrics 设计。本轮只写文档，不修改代码、测试、配置或前端。

### 修改文件

- `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`
- `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`
- `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/AGENT_SELECTOR_PROMETHEUS_METRICS_DESIGN.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- T031 已完成：补充 local PowerShell 临时环境变量示例、dev 运维侧显式开启边界、配置命名注意和禁止策略。
- T032 已完成：新增 `docs/AGENT_SELECTOR_PROMETHEUS_METRICS_DESIGN.md`，设计候选数值指标、低风险 label、禁止字段、风险和 T033-T036 后续拆分。
- 同步 TODO / HANDOFF / observability / shadow mode，标记 T031 / T032 完成。

### 当前边界

- T031 只是文档示例，没有真正开启 endpoint。
- T032 只是 Prometheus 设计，没有接入 Prometheus。
- T030 仍 BLOCKED，原因是项目缺少 Spring Security Web 鉴权体系。
- 不建议现在为了 T030 引入 Spring Security 依赖。
- endpoint 当前仍默认关闭。
- 没有修改 Java 生产代码。
- 没有修改测试代码。
- 没有修改 `application.yml`。
- 没有修改 `application-local.yml`。
- 没有修改任何 profile 配置文件。
- 没有新增 Maven 依赖。
- 没有修改前端。
- 没有读取或输出 secret。
- 没有修改 production routing。
- 完整 T010 仍为 BLOCKED。

### 下一步

建议进入 T033 Prometheus metrics 设计审查，或先开 T030-design-review 重新收窄鉴权验证方案。

## 2026-05-17 - T030 Selector Actuator Security Test Preflight

### 本轮目标

测试内验证 `agentSelectorShadow` Actuator endpoint 的鉴权策略，包括未认证、普通用户、运维 / 管理角色访问行为。

### 当前结果

T030a preflight 已完成，但 T030 进入 BLOCKED。当前后端只发现 `spring-security-crypto`，没有发现 `spring-boot-starter-security`、`spring-security-test`、`SecurityFilterChain` 或现有 Web 鉴权测试配置。

### 阻塞原因

本轮边界不允许新增 Maven 依赖，不允许修改生产配置，也不允许新增生产 Spring Security 配置。因此无法在测试内可靠验证未认证 401 / 403、普通用户 403、OPS / ACTUATOR_ADMIN 200 的访问策略。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 当前边界

- 未新增测试类。
- 未修改 Java 生产代码。
- 未修改现有测试代码。
- 未修改 `application.yml`。
- 未修改 `application-local.yml`。
- 未修改前端。
- 未接 Prometheus。
- 未操作远程中间件。
- 未真实调用 provider。
- 未读取或输出 secret。
- 未修改 production routing。
- 完整 T010 仍为 BLOCKED。

### 下一步

需要用户确认是否允许新增测试所需的 Spring Security 依赖，或先开 T030-design-review 重新收窄鉴权验证方案。

## 2026-05-17 - T029 Actuator Security Integration Design

### 本轮目标

完成 Spring Security / Actuator 安全集成设计。本轮只写文档，不修改 Java、测试、配置、前端，不真正开启 endpoint。

### 修改文件

- `docs/AGENT_ACTUATOR_SECURITY_INTEGRATION_DESIGN.md`
- `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`
- `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`
- `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `docs/AGENT_ACTUATOR_SECURITY_INTEGRATION_DESIGN.md`。
- 设计未来 `agentSelectorShadow` Actuator endpoint 的访问角色、路径边界、未授权访问行为和访问来源限制。
- 设计未来 T030 测试内鉴权策略验证范围。
- 补充 dev / prod 开启前人工 checklist 和回滚策略。
- 同步 TODO / HANDOFF / selector actuator / observability / shadow mode 文档，将 T029 标记为完成的设计任务。

### 当前边界

- T029 只写设计文档。
- endpoint 当前仍默认关闭。
- 没有实现 Spring Security。
- 没有新增 `SecurityFilterChain`。
- 没有修改 `application.yml`。
- 没有修改 `application-local.yml`。
- 没有真正开启 endpoint。
- 没有接 Prometheus。
- 没有修改 Java 生产代码。
- 没有修改测试代码。
- 没有修改前端。
- 没有读取或输出 secret。
- 没有修改 production routing。
- 完整 T010 仍为 BLOCKED。

### 下一步

建议进入 T030-test-security-integration 的测试内鉴权策略验证设计审查，或先让 CC / 人工审查 T029；不建议直接进入生产开启。

## 2026-05-16 - T028 Selector Actuator Local Enablement Plan

### 本轮目标

同步 T027 完成状态，并完成 T028 dev profile / local enablement proposal。本轮只写文档，不修改 Java、测试、配置、前端，不真正开启 endpoint。

### 修改文件

- `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`
- `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`
- `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 记录 T027 已完成：`AgentSelectorShadowEndpointEnabledTest` 在测试 properties 中显式开启 endpoint，返回 200，字段白名单 / 黑名单、空 metrics 和 metrics 不变检查通过。
- 补充配置命名坑：`management.endpoint.agent-selector-shadow.enabled=true` 使用单数 endpoint 和 relaxed binding；`management.endpoints.web.exposure.include=agentSelectorShadow` 使用复数 endpoints，值必须是 endpoint id；禁止 `management.endpoints.web.exposure.include=*`。
- 补充 local 临时环境变量开启草案，明确只是本地临时示例，不是仓库默认配置。
- 补充 dev 部署环境变量开启草案和 dev 开启前置条件。
- 拆分后续 T029-T032，避免后续直接上生产开启。

### 当前边界

- T028 是设计文档任务。
- endpoint 当前仍默认关闭。
- 没有修改 `application.yml`。
- 没有修改 `application-local.yml`。
- 没有真正开启 dev / local / prod endpoint。
- 没有新增 Spring Security。
- 没有接 Prometheus。
- 没有修改 Java 生产代码。
- 没有修改测试代码。
- 没有修改前端。
- 没有读取或输出 secret。
- 没有修改 production routing。
- 完整 T010 仍为 BLOCKED。

### 下一步

当时下一步指向 T029-security-integration-design，或先让 CC / 人工审查 T028 的 local / dev 开启方案；该设计任务现已由 T029 完成。

## 2026-05-16 - T027 Selector Actuator Enabled Test

### 本轮目标

同步 T027 测试内显式开启验证结果。本轮只更新文档，不修改代码、测试、配置或前端。

### 修改文件

- `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`
- `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`
- `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 记录内容

- T027 已完成，commit 为 `71db1cd test(agent): verify selector actuator endpoint enabled in test`。
- T027 新增 `AgentSelectorShadowEndpointEnabledTest`。
- T027 只在测试 properties 中显式开启 endpoint。
- 显式开启后 `GET /actuator/agentSelectorShadow` 返回 200。
- 白名单字段检查、黑名单字段检查、空 metrics 检查和 metrics 不变检查均通过。
- T027 未修改 `application.yml`、`application-local.yml`、生产代码、前端、Spring Security 或 Prometheus。
- T027 未真实调用 provider，未读取或输出 secret。
- 默认状态仍关闭，生产环境仍未开启。
- 完整 T010 仍为 BLOCKED。

### 配置命名注意

- `management.endpoint.agent-selector-shadow.enabled=true`：`endpoint` 是单数，`agent-selector-shadow` 是 endpoint id `agentSelectorShadow` 的 relaxed binding 写法。
- `management.endpoints.web.exposure.include=agentSelectorShadow`：`endpoints` 是复数，`exposure.include` 的值使用 endpoint id `agentSelectorShadow`，不要写成 `agent-selector-shadow`。
- 禁止使用 `management.endpoints.web.exposure.include=*`。

## 2026-05-16 - T025 Selector Actuator Security Plan

### 本轮目标

完成 Agent selector shadow Actuator endpoint 安全配置 / 显式开启策略设计。本轮只写设计文档，不真正开启 endpoint。

### 修改文件

- `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`
- `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`
- `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/AGENT_ASYNC_DESIGN.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `docs/AGENT_SELECTOR_ACTUATOR_SECURITY_PLAN.md`。
- 记录 T024 已实现默认关闭的 `AgentSelectorShadowEndpoint`，endpoint id 为 `agentSelectorShadow`，并使用 `enableByDefault=false`。
- 明确 T025 只设计如何安全开启，不真正开启 endpoint。
- 补充 local / dev / test / prod 分环境策略。
- 明确禁止 `management.endpoints.web.exposure.include=*`、公网匿名访问、普通用户访问、前端直接调用和日志打印完整响应。
- 明确禁止输出 API Key、Authorization、baseUrl、prompt、用户 task、文档内容、模型完整返回、provider raw error、用户 ID、文档 ID、sessionId 或最终回答。
- 拆分未来 T026-T030：安全审查、测试内显式开启验证、dev profile 方案、Spring Security / Actuator 安全集成、Prometheus 数值指标设计。
- 同步现有 selector shadow / observability / actuator / async 设计文档和协作文档。

### 当前边界

- endpoint 当前仍默认关闭。
- 未修改 `application.yml`。
- 未修改 `application-local.yml`。
- 未加入 `management.endpoints.web.exposure.include`。
- 未新增 Spring Security 配置。
- 未接 Prometheus。
- 未开启 dev / prod 访问。
- 未修改 Java 生产代码。
- 未修改测试代码。
- 未修改前端。
- 未真实调用 provider。
- 未读取 `backend/.env`。
- 未输出 secret。
- 未修改 production routing。
- 未让 shadow decision 接管 primary decision。
- 完整 T010 仍为 BLOCKED，等待 MQ / 解析消费链路。

### 下一步

建议进入 T026：CC / 人工审查 T025 安全开启策略。审查通过后，再考虑 T027 只在测试 properties 中显式开启 endpoint，验证 200 和字段白名单 / 黑名单。

### T025e 最终自检

- T025a-e 已完成。
- T025 是设计文档任务。
- endpoint 仍默认关闭。
- 没有开启 actuator exposure。
- 没有新增 Spring Security 配置。
- 没有接 Prometheus。
- 未修改 Java 生产代码。
- 未修改测试代码。
- 未修改前端。
- 未修改 `application.yml`。
- 未修改 `application-local.yml`。
- 未新增 `management.endpoints.web.exposure.include`。
- 未读取或输出 secret。
- 未修改 production routing。
- 下一步建议 T026：CC / 人工审查 T025 安全开启策略。
- 完整 T010 仍为 BLOCKED。

## 2026-05-12 - 初始化 Codex 协作文档与 TODO 看板

### 本轮目标

只做项目体检、文档初始化和 TODO 协同机制建立，为后续 Codex 接手提供固定入口。本轮不修改业务代码、不改接口逻辑、不改依赖版本、不改数据库结构、不改配置逻辑。

### 阅读过的关键文件

- `README.md`
- `backend/README.md`
- `frontend/README.md`
- `docs/ai-dev/STATE.md`
- `docs/ai-dev/PROJECT.md`
- `docs/ai-dev/TASKS.md`
- `docs/ai-dev/CONSTRAINTS.md`
- `docs/ai-dev/HANDOFF.md`
- `docs/ai-dev/SHOWCASE.md`
- `backend/pom.xml`
- `frontend/package.json`
- `docker-compose.demo.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/sql/`
- `deploy/`
- `git status --short`
- `git log --oneline -5`
- `git diff --stat`

### 新增 / 更新文档

- 新增 `AGENTS.md`：后续 agent 的项目规则、启动验证命令、协作约束和面试口径。
- 新增 `docs/CODEX_HANDOFF.md`：当前真实状态、功能边界、风险和接手路径。
- 新增 `docs/TODO_NEXT.md`：Codex 协作看板，初始化 8 个最小可执行任务。
- 新增 `docs/CHANGELOG_CODING.md`：记录本轮文档初始化。

### 本轮没有修改业务代码

本轮只创建协作文档，不修改后端源码、前端源码、接口逻辑、依赖版本、数据库脚本或配置逻辑。当前工作区已有的业务文件改动来自本轮之前，未在本轮处理或回滚。

### 当前发现的主要问题

- 工作区存在大量未提交改动和未跟踪文件，后续每轮必须先看 `git status` / `git diff`。
- `docs/ai-dev/HANDOFF.md` 等历史文档存在乱码和阶段漂移风险。
- README / SHOWCASE / STATE / HANDOFF 中的 eval 指标存在明确冲突，不只是“可能漂移”：README 记录 `answerSuccessRate=57.143%`、`citationHitRate=46.154%`；STATE 记录 `answerSuccessRate=50%`、`citationHitRate=50%`；最新 artifact JSON 记录 `answerSuccessRate=90%`、`citationHitRate=100%`。需要通过 T001a/T001b 定位权威 artifact 后统一引用。
- 项目功能已较多，但必须保持克制口径：轻量检索增强不是完整向量 RAG，最小 Agent 不是成熟多 Agent 平台，PDF 解析不是主能力。
- 本地 / 云中间件、真实模型密钥、端口占用仍是后续验证的主要不确定项。

### 下一步建议

当时建议先执行 `T000：审计工作区改动 + 敏感信息检查`。完成后再执行 T001a/T001b 收敛 eval 指标证据链，随后执行最小 smoke 验证，确认当前仓库在本地仍能构建、测试和演示。

## 2026-05-12 - T000b 敏感信息脱敏与协作文档入库策略修正

### 本轮目标

只做安全脱敏和 `.gitignore` 协作文档规则修正，不做业务功能开发。

### 修改文件

- `.gitignore`
- `README.md`
- `backend/README.md`
- `backend/.env.cloud.example`
- `backend/src/main/resources/application-local.yml`
- `docs/ai-dev/CONSTRAINTS.md`
- `docs/TODO_NEXT.md`
- `docs/CHANGELOG_CODING.md`

### 脱敏信息类型

- 将公开文档中的真实公网 IP 替换为 `<CLOUD_HOST>`。
- 将 cloud example 文件中的真实公网 IP 替换为 `<YOUR_CLOUD_HOST>`。
- 将本地 Spring 配置中的真实公网 IP 替换为 `${CLOUD_HOST:localhost}`。

### 协作文档入库策略

- 保留 `docs/ai-dev/**` 既有放行规则。
- 新增 `docs/CODEX_HANDOFF.md`、`docs/TODO_NEXT.md`、`docs/CHANGELOG_CODING.md` 的 `.gitignore` 例外规则。
- 暂不放行 `docs/agent-upgrade-roadmap.md`，等待用户确认其是否属于正式规划文档。

### 明确未做事项

- 未修改后端业务代码。
- 未修改前端业务代码。
- 未修改 `backend/.env`。
- 未执行 `git add` / `git commit` / `git push`。

## 2026-05-13 - T002a AI 问答 / SSE 后端验证结果记录

### 本轮目标

只记录 T002a-verify 的真实验证结果，不修改业务代码、测试代码或配置文件，不执行 Git 暂存、提交或推送。

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过，Maven 输出 `BUILD SUCCESS`。
- `cd backend; mvn test -DskipITs`：通过，Maven 输出 `BUILD SUCCESS`。
- 测试统计：`Tests run: 141, Failures: 0, Errors: 0, Skipped: 0`。
- 测试日志中出现 SSE 兜底、限流、Redis 降级、解析失败等异常路径日志，但均属于测试覆盖场景，未造成测试失败。

### 结论

- 当前 AI 问答 / SSE 后端改进包没有编译或测试阻塞。
- 建议下一步进入 Claude Code 只读提交前审查。
- 该改进包适合作为独立提交候选，但提交前仍需审查 diff 边界和敏感信息风险。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 明确未做事项

- 未修改后端业务代码。
- 未修改前端业务代码。
- 未修改测试代码。
- 未修改配置文件。
- 未执行 `git add` / `git commit` / `git push`。

## 2026-05-13 - T002b 前端 QA / SSE 展示改进验证记录

### 本轮目标

自审、验证并提交前端 QA / SSE 展示改进包；保持与后端 `91421a9 feat(ai): improve document QA SSE robustness` 拆分提交，不混入 Agent Demo、benchmark、`.run` 或根 README / AGENTS。

### 验证结果

- `cd frontend; npm run lint`：通过，`next lint` 输出无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建、类型检查和静态页面生成成功。
- 当前 `package.json` 无 `test` 脚本，因此本轮未执行前端 test。

### 自审结论

- 白名单文件未发现真实 API Key、token、password、secret 或真实公网 IP。
- `frontend/lib/qa-api.ts` 已适配后端 SSE `meta/chunk/done/error` 事件，并能解析结构化 `done/error` payload。
- 文档详情页保留流式输出、首字延迟展示、引用更新、失败自动降级普通问答和历史回答 Markdown 渲染。
- `MarkdownViewer` 新增 inline mode，不改变默认 card 模式，降低破坏普通 Markdown 展示的风险。
- `dashboard/layout/frontend README` 中存在 Agent 入口或说明改动，不属于本轮 QA/SSE 提交范围，本轮不提交。

### 修改文件

- `frontend/app/documents/[documentId]/page.tsx`
- `frontend/components/markdown-viewer.tsx`
- `frontend/components/markdown-viewer.module.css`
- `frontend/lib/qa-api.ts`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 明确未做事项

- 未提交 Agent Demo。
- 未提交 benchmark。
- 未提交 `.run` 配置。
- 未提交根目录 `README.md` 或 `AGENTS.md`。
- 未执行 `git push`。

## 2026-05-13 - 固化 commit message 协作规则

### 本轮目标

只将 commit message 格式规则写入协作文档（AGENTS.md、CODEX_TOOLING.md），使后续 Claude Code / Codex CLI 生成 commit message 时有统一约束，避免出现 Co-Authored-By、多行 body、工具/模型签名等问题。

### 修改文件

- `AGENTS.md`
- `docs/CODEX_TOOLING.md`
- `docs/CHANGELOG_CODING.md`

### 规则摘要

- 单行 conventional commits 格式（`type(scope): description`）。
- 不生成多行 body、不添加 Co-Authored-By、不出现工具/模型名称（Claude、Anthropic、Opus、AI assistant、Codex）。
- 详细实现说明写入 CHANGELOG / HANDOFF / TODO_NEXT，不写入 commit message。
- commit message 要像正常开发者提交，不是 AI 生成说明。

### 明确未做事项

- 未修改业务代码。
- 未修改前端代码。
- 未修改配置文件。
- 未执行 `git add` / `git commit` / `git push`。

### 后续约束

所有 Claude Code / Codex CLI 在本仓库生成 commit message 时必须遵守 AGENTS.md 和 CODEX_TOOLING.md 中的 Commit Message 规则。

## 2026-05-13 - T003 Agent Demo 提交

### 本轮目标

自审、验证并提交 Agent Demo 最小工具编排闭环。

### 提交文件

- 后端 Agent 模块（9 个）：controller / dto / service / impl / tool x4 / vo。
- 后端测试（1 个）：`DocumentAgentServiceImplTest.java`。
- 后端 smoke 脚本（1 个）：`smoke-agent-min.ps1`。
- 前端 Agent 页面/API（2 个）：`agent/page.tsx` / `agent-api.ts`。
- 前端导航入口（2 个）：`layout.tsx` / `dashboard/page.tsx`。

### 验证结果

- `mvn -DskipTests compile`：通过。
- `mvn test -DskipITs`：通过（141 tests, 0 failures）。
- `npm run lint`：通过。
- `npm run build`：通过。
- smoke 脚本语法已确认，未实跑。

### 明确未提交

- `.run` 配置、benchmark、README、AGENTS、`docs/ai-dev`。

## 2026-05-13 - T004a Agent 执行痕迹持久化骨架

### 本轮目标

新增 AgentTask / AgentStep 持久化骨架，为后续 Agent 执行痕迹、工具步骤 trace 和失败诊断落库做准备。

### 修改文件

- `deploy/mysql/init/01_add_agent_tables.sql`
- `backend/src/main/java/com/docpilot/backend/ai/agent/entity/AgentTask.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/entity/AgentStep.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/mapper/AgentTaskMapper.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/mapper/AgentStepMapper.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/AgentTaskPersistenceService.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/AgentTaskPersistenceServiceImpl.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 结果摘要

- 新增 `tb_agent_task` 和 `tb_agent_step` DDL。
- 新增 MyBatis-Plus Entity / Mapper。
- 新增 `AgentTaskPersistenceService` 骨架，支持创建 task、标记成功/失败、创建 step。

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未接入 `DocumentAgentServiceImpl`。
- 未执行 DDL。
- 未修改前端。
- 未接 RocketMQ。
- 未引入 Spring AI / LangChain4j / MCP。

## 2026-05-13 - T004b Agent 执行痕迹持久化接入

### 本轮目标

将 T004a 的 AgentTask / AgentStep 持久化服务接入 `DocumentAgentServiceImpl`，让每次 Agent run best-effort 记录 task、tool step、成功或失败状态。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/vo/DocumentAgentResponse.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未新增 Agent task 查询 API。
- 未执行 DDL。
- 未修改前端。
- 未接 RocketMQ。

## 2026-05-13 - T004c Agent 执行轨迹查询 API

### 本轮目标

提供 Agent task / step 查询接口，可按 `taskId` 查询当前用户的单次 Agent 执行记录和步骤列表。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/controller/DocumentAgentController.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/mapper/AgentTaskMapper.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/mapper/AgentStepMapper.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/AgentTaskPersistenceService.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/AgentTaskPersistenceServiceImpl.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未修改前端 Agent 页面。
- 未执行 DDL。
- 未新增 MQ / Outbox。

## 2026-05-13 - T004d Agent smoke 与协作文档收尾

### 本轮目标

补充 Agent smoke 对 `taskId` 的断言，并将 T004b/T004c/T004d 的完成状态同步到协作文档。

### 修改文件

- `backend/scripts/agent/smoke-agent-min.ps1`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- `cd backend; mvn test -DskipITs`：通过。
- `powershell -NoProfile -Command "Get-Command .\backend\scripts\agent\smoke-agent-min.ps1 -ErrorAction Stop"`：通过。

### 明确未做事项

- 未实跑 Agent smoke。
- 未启动后端服务。
- 未执行 DDL。

## 2026-05-13 - T004e Agent 持久化运行时 smoke

### 本轮目标

连续完成 AgentTask / AgentStep 远程表检查、授权建表、运行时 smoke、接口查询和远程数据库只读核验。

### 修改文件

- `backend/scripts/agent/smoke-agent-min.ps1`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- T004e-1：hk-ops 只读检查确认远程 `docpilot` 数据库存在，目标表起初不存在。
- T004e-2：经用户确认后，hk-ops 仅执行两条授权的 `CREATE TABLE IF NOT EXISTS`，随后确认 `tb_agent_task` / `tb_agent_step` 存在且结构与 DDL 大致一致。
- T004e-3：本地后端启动后，`scripts/agent/smoke-agent-min.ps1` 通过；Agent run 返回 `taskId`；task 查询接口与 step 查询接口通过。
- hk-ops 远程只读 SELECT 确认本次 smoke 的 task 与 steps 已写入远程 `tb_agent_task` / `tb_agent_step`。

### 明确未做事项

- 未修改 Java 业务代码。
- 未修改前端业务代码。
- 未修改 DDL。
- 未读取或输出 `backend/.env`。
- 未执行远程 `ALTER / DROP / DELETE / UPDATE / INSERT / TRUNCATE`。
- 未执行 `git push`。

## 2026-05-13 - T005b DocumentToolSelector 独立规则测试

### 本轮目标

补充 `DocumentToolSelector` 的独立单元测试，锁定 status / summary / evidence / 默认 QA 路由逻辑，避免后续工具选择规则漂移。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/DocumentToolSelectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 覆盖范围

- `status_only`：状态 / 解析完成类任务仅选择 `document_status_tool`。
- `summary_tool`：总结 / 摘要类任务选择 `document_status_tool` + `document_summary_tool`。
- `qa_tool`：证据 / 引用类任务选择 `document_status_tool` + `document_qa_tool`。
- 默认 QA：普通问题默认进入 QA 工具链。
- summary + evidence 冲突：证据需求优先进入 QA，避免总结工具丢失引用诉求。
- 空字符串 / 空白输入：默认进入 QA 工具链。

### 验证结果

- `cd backend; mvn -Dtest=DocumentToolSelectorTest test`：通过（6 tests, 0 failures）。
- `cd backend; mvn test -DskipITs`：通过（147 tests, 0 failures）。

### 明确未做事项

- 未修改生产代码。
- 未修改 `DocumentAgentServiceImpl`。
- 未修改前端。
- 未修改 DDL。
- 未接 MQ / RAG / MCP / Spring AI / LangChain4j。

## 2026-05-13 - T005c Agent ToolSelector runtime smoke

### 本轮目标

运行 Agent runtime smoke，确认 T005a 接入 `ToolRegistry` / `ToolSelector` 后，真实接口链路的 summary / QA 路由、`taskId` 返回和 task / step 查询仍然正常。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- 本地后端以 `local` profile 启动，`/actuator/health` 可访问。
- `cd backend; powershell -ExecutionPolicy Bypass -File scripts/agent/smoke-agent-min.ps1`：通过。
- summary run 返回 `summary_tool`，包含 2 个步骤和有效 `taskId`。
- QA run 返回 `qa_tool`，包含 2 个步骤、有效 `taskId` 和引用结果。
- Agent task 查询接口返回对应 task 与 steps。
- Agent step 查询接口返回对应 steps。

### 明确未做事项

- 未修改生产代码。
- 未修改前端。
- 未修改 DDL。
- 未修改 smoke 脚本。
- 未读取 `backend/.env`。
- 未执行 `git add` / `git commit` / `git push`。
- 本轮结束前已停止本地后端，确认 8081 端口释放。

## 2026-05-13 - T006a 前端 Agent 持久化执行轨迹展示

### 本轮目标

让前端 Agent 页面在 `/api/ai/agent/run` 返回 `taskId` 后，调用后端 task / step 查询接口并展示持久化后的执行轨迹。

### 修改文件

- `frontend/lib/agent-api.ts`
- `frontend/app/agent/page.tsx`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `frontend/lib/agent-api.ts` 新增 `getAgentTask(taskId)` 和 `getAgentTaskSteps(taskId)`，路径来自 `DocumentAgentController`。
- `frontend/app/agent/page.tsx` 在 Agent run 成功且存在 `taskId` 时自动查询持久化 task / steps。
- 页面新增“持久化执行轨迹”区域，展示 taskId、status、decision、totalDurationMs、step count，以及每个 step 的 stepIndex、toolName、status、durationMs、inputSummary、outputSummary。
- task / step 查询失败时只显示友好提示，不影响原始 Agent 回答、内存 trace 和引用展示。

### 验证结果

- `cd frontend; npm run lint`：通过，无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建、类型检查和静态页面生成完成。

### 明确未做事项

- 未修改后端 Java。
- 未修改 DDL。
- 未修改 README / frontend README。
- 未修改 `.run`。
- 未修改 benchmark / docs-ai-dev。
- 未新增依赖或修改 package / lock 文件。

## 2026-05-13 - T006b 前端 Agent trace 运行时验证

### 本轮目标

真实运行前端 Agent 页面，确认 T006a 新增的持久化 task / step trace 展示在浏览器中可用。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 验证结果

- 本地后端 8081 启动成功，`/actuator/health` 可访问。
- 本地前端 3000 启动成功，`/agent` 可访问。
- `cd backend; powershell -ExecutionPolicy Bypass -File scripts/agent/smoke-agent-min.ps1`：通过，summary / QA run 均返回有效 `taskId`，task / step 查询接口通过。
- Playwright 打开 `/agent`，通过合法注册、上传、建文档、解析和 Agent run 验证页面展示。
- 页面展示“持久化执行轨迹”，包含 taskId、`SUCCESS` 状态、`qa_tool` 决策、2 条 step、`document_status_tool` / `document_qa_tool`、durationMs、inputSummary、outputSummary。
- `cd frontend; npm run lint`：通过，无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建和类型检查完成。

### 明确未做事项

- 未修改业务代码。
- 未修改 DDL。
- 未读取 `backend/.env`。
- 未输出真实 token、密码、API Key。
- 未执行 `git push`。
- 本轮启动的后端 / 前端进程已停止，8081 / 3000 / 3001 端口已释放。

## 2026-05-13 - T007 项目文档收口

### 本轮目标

收口根 README 与 frontend README，让公开文档反映当前真实实现：AI 问答 / SSE、最小 Agent、AgentTask / AgentStep 执行轨迹、task / step 查询接口和前端持久化 trace 展示。

### 修改文件

- `README.md`
- `frontend/README.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- README 补充最小 Agent 工具链闭环、执行轨迹落库、查询接口、前端 trace 展示和当前验证记录。
- README 明确边界：当前不是成熟多 Agent 平台；ToolSelector 是规则 / 关键词选择，不是 LLM Tool Calling；未接 MQ 异步 Agent、完整 RAG、向量库、MCP、Spring AI 或 LangChain4j。
- frontend README 补充 dashboard、document QA、Agent 页面和持久化 task / step trace 展示能力。
- frontend README 保留启动、lint、build 和联调说明，不记录敏感信息。

### 验证结果

- `cd frontend; npm run lint`：通过，无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建和类型检查完成。

### 明确未做事项

- 未修改业务代码。
- 未修改后端 Java。
- 未修改 DDL。
- 未提交 `.run`。
- 未提交 benchmark / docs-ai-dev。
- 未新增依赖或修改 package / lock 文件。

## 2026-05-13 - T009a Agent ToolSelector 可解释性增强

### 本轮目标

让 Agent 工具路由结果从仅有 `decision` 扩展为同时返回 `routingReason` 和 `matchedKeywords`，便于前端展示和后续 smoke 验证。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/ToolSelector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/DocumentToolSelector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/vo/DocumentAgentResponse.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `ToolSelector.SelectResult` 新增 `reason` 和 `matchedKeywords`，并保留 `of(decision, toolNames)` 兼容便捷方法。
- `DocumentToolSelector` 改为收集命中关键词，summary + evidence 冲突时继续路由到 `qa_tool`。
- `DocumentAgentServiceImpl` 将 selector reason / matched keywords 写入 `DocumentAgentResponse`；parseReady=false 短路路径仍不调用 selector，并返回固定路由原因。
- `DocumentAgentServiceImplTest` 增加路由原因断言。

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过（147 tests, 0 failures）。

### 明确未做事项

- 未修改 DDL。
- 未修改 Controller。
- 未修改前端。
- 未修改 smoke 脚本。
- 未持久化 `routingReason` / `matchedKeywords` 到 AgentTask。
- 未接 MQ / RAG / MCP / Spring AI / LangChain4j / LLM Tool Calling。

## 2026-05-13 - T009b DocumentToolSelector 可解释性测试

### 本轮目标

补充 `DocumentToolSelector` 独立单元测试，锁定 `decision`、`reason`、`matchedKeywords` 的规则行为。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/DocumentToolSelectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 覆盖范围

- 状态查询路由到 `status_only`，并返回状态类命中关键词和 reason。
- 摘要任务路由到 `summary_tool`，并返回摘要类命中关键词和 reason。
- 证据 / 原文引用任务路由到 `qa_tool`，reason 体现证据或引用需求。
- 默认 QA 在无关键词命中时返回空 matchedKeywords 和非空 reason。
- summary + evidence 冲突时继续优先 `qa_tool`。
- 英文关键词大小写不敏感。
- 空字符串、空白字符串和 null 输入按当前实现默认进入 QA。

### 验证结果

- `cd backend; mvn -Dtest=DocumentToolSelectorTest test`：通过（8 tests, 0 failures）。
- `cd backend; mvn test -DskipITs`：通过（149 tests, 0 failures）。

### 明确未做事项

- 未修改生产代码。
- 未修改前端。
- 未修改 DDL。
- 未接 MQ / RAG / MCP / Spring AI / LangChain4j / LLM Tool Calling。

## 2026-05-13 - T009c 前端展示 Agent 路由解释

### 本轮目标

让 Agent 页面展示后端返回的 `routingReason` 和 `matchedKeywords`，使工具路由结果对用户可见。

### 修改文件

- `frontend/lib/agent-api.ts`
- `frontend/app/agent/page.tsx`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `DocumentAgentRunData` 增加 `routingReason?: string` 和 `matchedKeywords?: string[]`。
- Agent 运行结果区新增“路由决策”卡片，仅在 `routingReason` 存在时展示。
- 命中关键词以轻量 badge 展示；关键词为空或字段缺失时不渲染标签。
- 保留原有 decision、finalAnswer、citations、steps 和持久化 trace 展示。

### 验证结果

- `cd frontend; npm run lint`：通过，无 warning / error。
- `cd frontend; npm run build`：通过，Next.js 生产构建和类型检查完成。

### 明确未做事项

- 未修改后端 Java。
- 未修改 DDL。
- 未新增依赖。
- 未修改 package / lock 文件。

## 2026-05-13 - T009d Agent 路由解释 smoke 与异步设计文档

### 本轮目标

验证 T009a-c 的路由解释全链路，并记录未来异步 Agent 演进方案。本轮不实现异步 Agent，不接 MQ。

### 修改文件

- `backend/scripts/agent/smoke-agent-min.ps1`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/AgentTool.java`
- `docs/AGENT_ASYNC_DESIGN.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- smoke 脚本增加 summary run 的 `routingReason`、`matchedKeywords` 和 `summary_tool` 断言。
- smoke 脚本增加 QA run 的 `routingReason`、`qa_tool` 和 citation 断言。
- smoke 输出增加 summary / QA 的 routing reason 与 matched keywords。
- `AgentTool` 补充 Javadoc，说明新增工具的最小接入路径。
- 新增 `docs/AGENT_ASYNC_DESIGN.md`，对比 RocketMQ、Spring `@Async` 和 DB polling，并推荐未来基于 `AgentTask` 状态机和 RocketMQ 的异步演进路线。

### 验证结果

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过（149 tests, 0 failures）。
- `cd backend; powershell -ExecutionPolicy Bypass -File scripts/agent/smoke-agent-min.ps1`：通过，summary / QA 均返回 routing reason，summary 返回 matched keywords，QA 返回引用。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 未实现异步 Agent。
- 未新增 RocketMQ topic / consumer。
- 未修改 DDL。
- 未修改前端页面。
- 未接 RAG / MCP / Spring AI / LangChain4j / LLM Tool Calling。

## 2026-05-12 - T001b 统一 eval 指标引用

### 本轮目标

统一 README / STATE / 协作文档中的 Stage C eval 指标引用，消除 README、STATE、artifact 三套指标冲突。

### 修改文件

- `README.md`
- `docs/ai-dev/STATE.md`
- `docs/CODEX_HANDOFF.md`
- `docs/TODO_NEXT.md`
- `docs/CHANGELOG_CODING.md`

### 当前权威指标来源

- Artifact：`docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json`
- `generatedAt=2026-04-18T18:58:42.2763129+00:00`
- `datasetName=stagec-core-qa-eval`
- `datasetVersion=2026-04-19-r2`
- `caseCount/streamPairs=20/8`
- `answerSuccessRate=90%`
- `citationHitRate=100%`

### 明确未做事项

- 未修改后端业务代码。
- 未修改前端业务代码。
- 未重跑 eval。
- 未修改 artifact JSON。
- 未执行 `git add` / `git commit` / `git push`。

### 不确定项

- `stagec_eval_latest.json` 未记录实际运行时 `AI_MODE`、模型名或 provider。
- 当前指标是仓库内 artifact 证据，不代表线上 SLA。
- 后续 T005a/T005b 需要补充运行时配置记录并重跑 eval。

## 2026-05-12 - T000d Codex subagents 与 MCP 工具边界记录

### 本轮目标

新增工具能力边界文档，让后续 Claude Code、Codex、ChatGPT 接手时能明确 subagents、context7 MCP、playwright MCP 的用途、授权条件和禁止事项。

### 修改文件

- `AGENTS.md`
- `docs/CODEX_TOOLING.md`
- `docs/CODEX_HANDOFF.md`
- `docs/TODO_NEXT.md`
- `docs/CHANGELOG_CODING.md`

### 记录内容

- `code-map`、`docs-research`、`hk-ops`、`risk-review`、`test-audit`、`ui-check` 的用途和边界。
- `context7 MCP` 用于查询官方/库文档，避免凭空猜测。
- `playwright MCP` 用于浏览器自动化、前端 smoke test、UI/E2E 检查。
- `hk-ops` 远程访问前必须说明目的、命令类别、是否只读，并等待用户确认。
- `playwright` 不应未经用户确认启动长期驻留 dev server。

### 明确未做事项

- 未记录任何真实 IP、账号、密码、token、API Key 或 `.env` 内容。
- 未修改后端业务代码。
- 未修改前端业务代码。
- 未执行 `git add` / `git commit` / `git push`。

## 2026-05-12 - T000c 剩余敏感信息复核与 `.run` 配置检查

### 本轮目标

只做剩余敏感信息复核，重点检查 IDEA `.run/*.xml` 配置文件；如发现真实公网 IP 或敏感值，仅对 `.run/*.xml` 做必要脱敏。

### 检查范围

- `.run/*.xml`
- `README.md`
- `backend/README.md`
- `backend/.env.cloud.example`
- `backend/.env.demo.example`
- `backend/src/main/resources/application-*.yml`
- `docs/**/*.md`
- `AGENTS.md`

### 结果摘要

- `.run/*.xml` 未命中真实公网 IP。
- `.run/*.xml` 未命中密码、token、secret、API Key 等敏感关键词。
- T000b 已处理的目标文件未再次命中真实公网 IP。
- `backend/.env` 仍未被 Git 跟踪，且本轮未读取或修改其内容。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CHANGELOG_CODING.md`

### 明确未做事项

- 未修改 `.run/*.xml`，因为未发现需要脱敏的真实敏感值。
- 未修改后端业务代码。
- 未修改前端业务代码。
- 未修改 `backend/.env`。
- 未执行 `git add` / `git commit` / `git push`。

## 2026-05-13 - T009e 协作文档事实状态同步

### 本轮目标

同步协作文档中的当前状态描述，让 `docs/CODEX_HANDOFF.md`、`docs/TODO_NEXT.md` 和 `docs/CHANGELOG_CODING.md` 与当前 git / 代码状态一致。

### 原因

新会话恢复上下文时发现协作文档仍停留在旧的工作区风险口径、推荐先做 T000，但实际 `git status --short` 为空，且 `git log --oneline -20` 已显示 T009a-d 完成。

### 修正内容

- `docs/CODEX_HANDOFF.md` 改为记录当前工作区干净、T009a-d 已完成、`ToolSelector` 已支持 `routingReason` / `matchedKeywords`、前端 Agent 页面已展示路由决策、smoke 已增强路由解释断言。
- `docs/TODO_NEXT.md` 新增 T009e 完成记录和 T010 runtime 验证任务，推荐下一步改为 T010，并补充 T011 面试向项目总结 / 架构图 / 简历亮点收口。
- 协作文档明确暂不直接进入 MQ / RAG / MCP / LLM Tool Calling。

### 明确未做事项

- 未修改业务代码。
- 未修改后端 Java。
- 未修改前端业务代码。
- 未修改 DDL、README、`.run`、benchmark 或 `docs/ai-dev`。
- 未执行 T010 runtime 验证。
- 未读取或修改 `backend/.env`。

### 下一步

进入 `T010-runtime-verify`，完整验证 Agent 路由可解释性在浏览器端真实可用。

## 2026-05-14 - T010z 记录 runtime 验证阻塞状态

### 本轮目标

记录 T010-runtime-verify 当前 BLOCKED 状态，避免后续新会话误以为 Agent 路由解释浏览器端完整验证已经通过。

### T010x 诊断结果

- T010x 已复现后端 smoke 在文档解析阶段超时。
- 原始失败信息：`Parse timeout after 120 seconds.`
- 后端日志摘要显示 `NoopParseTaskMessageProducer` 跳过解析消息发送。
- 当前 MQ disabled / no-op producer 模式下不会推进真实异步解析，worker 不会消费并更新 `parseStatus`。

### 结论

- T010 未通过。
- 失败原因不是 Agent `routingReason` / `matchedKeywords` 代码问题，而是完整 smoke 依赖上传后真实异步解析链路。
- 完整 T010 需要可用 MQ / 解析消费环境后再重跑；若用户不想接 MQ，只能单独定义 T010-lite，并明确它不是完整上传解析链路验证。

### 本轮未做事项

- 未修改业务代码。
- 未修改后端 Java。
- 未修改前端代码。
- 未修改 DDL、README、`.run`、benchmark 或 `docs/ai-dev`。
- 未使用 hk-ops。
- 未远程连接服务器。
- 未启动或修改 RocketMQ。
- 未把 T010 写成通过。

### 下一步

执行 `T010m-local-mq-readiness-check`，只读检查本地 MQ / parse 配置入口和完整 T010 所需环境条件。

## 2026-05-14 - T010-lite-ui 前端 Agent lite 验证入口

### 本轮目标

在完整 T010 仍被 MQ disabled / no-op parser queue 阻塞的前提下，只为 `/agent` 页面增加当前用户可访问 `documentId` 的选择 / 输入入口，方便后续执行 Agent-only lite 验证。

### 修改文件

- `frontend/app/agent/page.tsx`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 保留 `/agent` 页面已有当前用户文档列表下拉框。
- 新增手动输入 `documentId` 的输入框，不硬编码任何文档 ID。
- 页面展示 Lite 验证模式说明：仅验证已解析文档上的 Agent 运行，不验证上传和解析链路。
- 文档不存在或当前账号无权访问时，页面显示友好错误，不影响原有 Agent run、decision、routingReason、matchedKeywords、持久化 trace 和 citations 展示逻辑。

### 验证结果

- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 未修改后端 Java。
- 未修改 DDL。
- 未修改 `package.json` 或 lock 文件。
- 未修改 smoke 脚本。
- 未启动或修改 RocketMQ。
- 未使用 hk-ops 或远程连接。
- 未验证上传解析链路。
- 未把 T010-lite 写成完整 T010 通过。

## 2026-05-14 - T010-lite-run Agent-only runtime 验证

### 本轮目标

在完整 T010 仍被 MQ disabled / no-op parser queue 阻塞的前提下，只验证已解析文档上的 Agent routing explainability 运行链路：已解析文档 -> Agent run -> routingReason / matchedKeywords -> task / step trace -> 前端展示。

### 验证对象

- 使用 `documentId=61`，该文档来自当前浏览器登录账号的 `/agent` 文档下拉列表，页面显示为解析成功。
- 本轮不使用 `documentId=58`，不硬编码 documentId，不验证上传解析链路。

### 浏览器验证结果

- Playwright 打开 `/agent` 并使用 `documentId=61`。
- summary 任务“总结一下这篇文档”通过，页面显示 `decision=summary_tool`、路由决策、`routingReason`、matched keyword、持久化执行轨迹、`taskId`、`SUCCESS`、2 条 step、toolName、durationMs、inputSummary、outputSummary。
- QA 任务“根据原文证据回答这篇文档的核心内容是什么”通过，页面显示 `decision=qa_tool`、路由决策、`routingReason`、matched keyword、持久化执行轨迹、`taskId`、`SUCCESS`、2 条 step、toolName、durationMs、inputSummary、outputSummary，并展示 citations。

### CLI lite smoke

- 未执行。
- 原因：当前 smoke 脚本会注册新的临时账号，无法保证该账号有权访问浏览器当前账号下的 `documentId=61`；本轮不把 CLI 账号不一致误判为浏览器 lite 验证失败。

### 构建验证

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过，测试统计为 149 tests，0 failures，0 errors。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 未修改业务代码。
- 未修改后端 Java。
- 未修改前端业务代码。
- 未修改 DDL。
- 未启动或修改 RocketMQ。
- 未使用 hk-ops。
- 未远程连接服务器。
- 未读取或输出 `backend/.env`。
- 未执行 `git push`。
- 未把 T010-lite 写成完整 T010 通过；完整 T010 仍为 BLOCKED。

## 2026-05-14 - T011a Tool Schema / Tool Metadata

### 本轮目标

进入 P3 LLM Tool Selection 的基础设施阶段，先为当前 Agent 工具建立稳定 Tool Definition，不调用真实 LLM、不接 function calling、不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/ToolDefinition.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/ToolDefinitionProvider.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/ToolDefinitionProviderTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `ToolDefinition` record，包含 toolName、displayName、description、inputSchemaText、outputSchemaText 和 safeForLlmSelection。
- 新增 `ToolDefinitionProvider`，基于当前 `ToolRegistry` 注册工具返回 `document_status_tool`、`document_summary_tool`、`document_qa_tool` 三个工具定义。
- 单元测试覆盖 3 个工具定义存在、toolName 不重复、description / schema 非空，以及 QA 工具描述不声明危险能力。

### 验证结果

- `cd backend; mvn -Dtest=ToolDefinitionProviderTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-15 - T017c Fake Provider Real Shadow Service Path

### 本轮目标

在 service 测试中验证 `realShadowEnabled=true + provider=fake` 时 real shadow 可以成功执行，但真实工具仍由 primary `DocumentToolSelector` decision 决定。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentRealShadowPathTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `DocumentAgentServiceImpl` 的 real shadow runner 改为通过 `RealLlmToolSelectorFactory` 和 `AgentSelectorProperties` 创建 selector。
- 默认 provider 仍是 `disabled`，默认 `realShadowEnabled=false`，默认不运行 real shadow。
- provider=fake 只在测试中用于验证 real shadow success path。
- 测试覆盖 provider=fake 成功时真实执行仍来自 primary decision。
- 测试覆盖 `realShadowRecordMetrics=false` 时不记录 real metrics，显式开启后才记录。
- 保留 real shadow fail-open 边界，失败不影响 Agent 主流程。

### 验证结果

- `cd backend; mvn -Dtest=DocumentAgentRealShadowPathTest test`：通过。
- `cd backend; mvn -Dtest=DocumentAgentServiceImplTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过，223 tests。

### 明确未做事项

- 未真实调用 LLM。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未接 function calling。
- 未新增 API。
- 未修改前端。
- 未修改 DDL。
- 未改变 production routing。

## 2026-05-15 - T017x Fake Provider Routing Alignment

### 本轮目标

修正 `FakeLlmToolSelectionClient` 的本地规则，使 provider=fake 更稳定模拟未来 LLM selector 的 JSON 输出，并尽量对齐当前 `DocumentToolSelector` 的 routing 基线。

### 诊断结论

- fake provider 已优先从 prompt 的 `Current task:` 提取真实 task，没有直接扫描整个 prompt。
- T017d failures 主要来自 blank task 被 `LlmToolSelectionPromptBuilder` 拒绝，属于 real shadow runner 输入边界。
- mismatch 主要来自 fake provider 规则缺口：`progress` / `state` 状态词、中文摘要词、evidence / 引用 / 根据原文词，以及 summary + evidence 冲突优先级。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/FakeLlmToolSelectionClient.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/FakeLlmToolSelectionClientTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- fake provider 增强 status / summary / evidence 关键词集合。
- routing 优先级调整为 evidence 优先，其次 summary，其次 status，最后默认 QA。
- 保留从 `Current task:` 提取真实 task 的行为，避免可用工具描述污染 decision。
- 单元测试覆盖 summary、QA/evidence、status、summary + evidence 冲突、中文摘要、中文证据、英文大小写、空白输入、JSON 可解析性、合法 toolNames 和 confidence 范围。

### 验证结果

- `cd backend; mvn -Dtest=FakeLlmToolSelectionClientTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未修改 `DocumentToolSelector`。
- 未修改 `tool-selector-eval-cases.json`。
- 未真实调用 LLM。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未发 HTTP。
- 未新增 API。
- 未修改前端。
- 未改变 production routing。

## 2026-05-15 - T017d Fake Provider Real Shadow Evaluation

### 本轮目标

新增 provider=fake 的 real shadow 离线评估，验证 `RealLlmSelectorShadowRunner` 通过 factory-backed selector 路径运行时能达到 shadow compare 阈值。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/RealShadowProviderEvaluationTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `RealShadowProviderEvaluationTest`。
- 复用 `tool-selector-eval-cases.json` 的 24 条样例。
- primary 使用 `DocumentToolSelector`。
- shadow 使用 `RealLlmSelectorShadowRunner + provider=fake`。
- 输出 total、success、failures、matched、mismatch、matchRate 和 successRate。

### 评估结果

- total=24
- success=22
- failures=2
- matched=22
- mismatch=0
- matchRate=0.9167
- successRate=0.9167

两个 failure 来自 blank task 被 `LlmToolSelectionPromptBuilder` 拒绝，属于 real shadow prompt 输入边界；非空样例均成功且无 mismatch。

### 验证结果

- `cd backend; mvn -Dtest=RealShadowProviderEvaluationTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过，229 tests。

### 明确未做事项

- 未修改 `tool-selector-eval-cases.json`。
- 未修改 `DocumentToolSelector`。
- 未真实调用 LLM。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未发 HTTP。
- 未新增 API。
- 未修改前端。
- 未改变 production routing。

## 2026-05-15 - T017e Fake Provider Shadow Validation 文档状态

### 本轮目标

更新 selector shadow 文档和协作状态，说明 factory-backed real shadow 路径已具备 provider=fake 离线评估证据，但仍没有真实 provider 调用。

### 修改文件

- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 记录 `RealLlmToolSelectorFactory` 已有，`RealLlmSelectorShadowRunner` 支持 factory-backed selector。
- 记录 provider=fake 已完成离线 shadow evaluation。
- 记录 T017d 评估结果：total=24、success=22、failures=2、matched=22、mismatch=0、matchRate=0.9167。
- 明确 provider=disabled 仍是默认，openai-compatible 仍 dry-run disabled 且不联网。
- 将下一步推荐更新为 T018：fake provider shadow-only runtime / smoke。

### 验证结果

- `git status --short`：已检查。
- `git diff -- docs/AGENT_SELECTOR_SHADOW_MODE.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md`：已复核。

### 明确未做事项

- 未修改代码。
- 未真实调用 LLM。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未发 HTTP。
- 未新增 API。
- 未修改前端。
- 未改变 production routing。

## 2026-05-15 - T018 Fake Provider Shadow Runtime Verification

### 本轮目标

使用 provider=fake 完成 real shadow selector 的 shadow-only runtime / smoke 验证，确认运行时 real shadow 分支可观察，但 production routing 仍由 primary `DocumentToolSelector` 决定。

### 验证方式

- 本地后端在用户授权下连接远程中间件运行。
- 启动时使用命令行参数开启 `shadowEnabled=true`、`realShadowEnabled=true`、`realShadowRecordMetrics=true`、`llmProvider=fake`，未修改配置文件。
- 前端浏览器打开 `/agent`，选择当前账号可访问的已解析文档 `documentId=61`。
- 未使用 hk-ops，未执行远程 DB 只读 SELECT。

### Runtime 结果

- summary 验证通过：primary decision=`summary_tool`，页面正常返回回答，展示 routingReason、matchedKeywords 和持久化 trace；后端安全日志可见 `provider=fake` real shadow compare，shadow decision=`summary_tool`，matched=true，metricsRecorded=true。
- QA 验证通过：primary decision=`qa_tool`，页面正常返回回答并展示 citations，展示 routingReason、matchedKeywords 和持久化 trace；后端安全日志可见 `provider=fake` real shadow compare，shadow decision=`qa_tool`，matched=true，metricsRecorded=true。
- 真实执行工具仍由 primary decision 决定；fake provider 只用于 shadow compare / metrics。

### 回归验证

- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过，229 tests。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。
- 本轮启动的后端 / 前端进程已清理，端口已释放。

### 明确未做事项

- 未验证完整上传 / 解析 / MQ 链路；完整 T010 仍为 BLOCKED。
- 未真实调用 LLM。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未向模型 provider 发真实 HTTP。
- 未使用 hk-ops。
- 未修改 production routing。
- 未新增 API。
- 未修改前端。

## 2026-05-15 - T019-preflight Real Provider Shadow Safety Plan

### 本轮目标

只做真实 provider shadow-only 调用前置检查与安全方案，不真实调用 provider，不读取 API Key，不改变当前 Agent 行为。

### 检查范围

- `OpenAiCompatibleLlmToolSelectionClient`：当前仍返回 disabled response，不发 HTTP。
- `LlmToolSelectionClientFactory`：默认 provider=disabled，默认返回 `DisabledLlmToolSelectionClient`。
- `RealLlmToolSelector`：只串联 prompt builder、client 和 parser；client disabled 或 blank response 会失败，不 fallback 成 keyword selector。
- `RealLlmSelectorShadowRunner`：捕获 selector 失败并返回 `success=false` / `shouldRecordMetrics=false`。
- `AgentSelectorProperties`：默认 `realShadowEnabled=false`、`realShadowRecordMetrics=false`、`llmProvider=disabled`。
- `DocumentAgentServiceImpl`：真实执行仍以 primary `DocumentToolSelector` decision 为准，real shadow 只在开关打开后旁路执行。

### 修改文件

- `docs/REAL_PROVIDER_SHADOW_PREFLIGHT.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增真实 provider shadow-only 前置安全文档。
- 明确 T019 只能 shadow-only，不能接管 production routing，不能影响 Agent API 返回。
- 明确 API Key 注入和日志脱敏原则。
- 明确真实 HTTP 调用边界、验证方案、停止条件和用户确认项。
- 将 `T019-real-shadow-only` 标记为 BLOCKED，等待用户确认 provider、baseUrl、model、API Key 注入方式、真实 HTTP、费用和日志脱敏策略。

### 明确未做事项

- 未真实调用 DeepSeek / OpenAI / 硅基流动或其他 provider。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未发真实 HTTP。
- 未修改 `application.yml` 或 `application-local.yml`。
- 未新增 API。
- 未修改前端。
- 未修改数据库。
- 未改变 production routing。

## 2026-05-15 - T019a Real Selector Provider Credentials Config

### 本轮目标

补齐 selector real provider 需要的最小配置字段，但保持默认安全关闭，不修改配置文件。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/config/AgentSelectorProperties.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/config/AgentSelectorPropertiesTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `llmApiKey` 字段，默认空字符串。
- 新增 `llmMaxTokens` 字段，默认 256，并校验必须为正数。
- 新增 `llmTemperature` 字段，默认 0，并校验范围为 0.0 到 2.0。
- 测试覆盖默认 provider disabled、默认 real shadow 关闭、openai-compatible provider 绑定不会自动启用 real shadow，以及新增字段校验。

### 验证结果

- `cd backend; mvn -Dtest=AgentSelectorPropertiesTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未读取真实 API Key。
- 未读取 `backend/.env`。
- 未修改 `application.yml` 或 `application-local.yml`。
- 未真实调用 provider。
- 未改变 production routing。

## 2026-05-15 - T019b OpenAI-compatible Selector Client

### 本轮目标

将 `OpenAiCompatibleLlmToolSelectionClient` 从 dry-run skeleton 升级为可真实调用 OpenAI-compatible chat completions 的 client，同时保持缺配置时不联网。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/OpenAiCompatibleLlmToolSelectionClient.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/OpenAiCompatibleToolSelectionRequest.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionClientFactory.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/OpenAiCompatibleLlmToolSelectionClientTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionClientFactoryTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 使用 JDK `HttpClient` 调用 `{baseUrl}/chat/completions`。
- 请求 body 使用 OpenAI-compatible chat completions 格式，包含 system / user messages、`temperature`、`max_tokens` 和 `stream=false`。
- 缺少 apiKey / baseUrl / model 时直接返回 disabled response，不发 HTTP。
- 响应只提取 `choices[0].message.content` 作为 rawText，后续仍由 `LlmToolSelectionParser` 校验。
- 非 2xx、provider JSON 解析失败、空 content、IO / timeout / interrupted 均返回 disabled/failure，不影响 primary routing。

### 验证结果

- `cd backend; mvn -Dtest=OpenAiCompatibleLlmToolSelectionClientTest test`：通过。
- `cd backend; mvn -Dtest=LlmToolSelectionClientFactoryTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 单元测试未使用真实 API Key，仅使用本地 stub server。
- 未读取 `backend/.env`。
- 未输出 API Key、Authorization header、prompt、文档内容或完整 baseUrl。
- 未修改 `application.yml` 或 `application-local.yml`。
- 未新增 API。
- 未改变 production routing。

## 2026-05-15 - T019c Real Provider Shadow Fail-open Tests

### 本轮目标

补充真实 provider shadow fail-open 测试，确保 openai-compatible 配置缺失、client failure 或 parser failure 不影响 primary Agent run。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentRealShadowPathTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/RealLlmSelectorShadowRunnerTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/RealLlmToolSelectorFactoryTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- service 测试覆盖 openai-compatible provider 缺 apiKey / 缺 baseUrl 时 real shadow fail-open，primary decision 仍为 `summary_tool`。
- service 测试覆盖 real shadow parser failure 不影响 Agent run，且不记录 real shadow 成功 metrics。
- runner 测试覆盖 client exception 和 openai-compatible 缺 baseUrl failure。
- factory 测试覆盖 openai-compatible apiKey 为空时仍为 disabled failure。

### 验证结果

- `cd backend; mvn -Dtest=DocumentAgentRealShadowPathTest test`：通过。
- `cd backend; mvn -Dtest=RealLlmSelectorShadowRunnerTest test`：通过。
- `cd backend; mvn -Dtest=RealLlmToolSelectorFactoryTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未修改生产代码。
- 未真实调用外部模型。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未新增 API。
- 未改变 production routing。

## 2026-05-15 - T019 Recovery and Real Provider Shadow Validation

### 本轮目标

修复 T019e 全量测试受本机真实 provider 环境变量影响的问题，并在回归全部通过后记录真实 provider shadow-only 验证结果。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/config/AgentSelectorProperties.java`
- `backend/src/test/java/com/docpilot/backend/DocPilotApplicationTests.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/config/AgentSelectorPropertiesTest.java`
- `docs/REAL_PROVIDER_SHADOW_PREFLIGHT.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `AgentSelectorProperties` 增加 OpenAI-compatible 常见 provider alias 归一化，避免兼容接口 provider 命名差异导致配置绑定失败。
- `AgentSelectorPropertiesTest` 显式隔离 selector provider、model、baseUrl、apiKey、timeout、maxTokens、temperature 和 shadow flags 默认值，避免继承本机真实 provider 环境。
- `DocPilotApplicationTests` 显式隔离 selector provider 默认值，避免 contextLoads 受真实 provider 环境污染。
- 记录 T019 真实 provider shadow-only 运行结果：provider=`openai_compatible`，真实 HTTP 调用 2 次，summary primary / shadow 均为 `summary_tool`，QA primary / shadow 均为 `qa_tool`，mismatch=false，QA citations 正常。

### 验证结果

- `cd backend; mvn -Dtest=AgentSelectorPropertiesTest test`：通过。
- `cd backend; mvn -Dtest=LlmToolSelectionClientFactoryTest test`：通过。
- `cd backend; mvn -Dtest=OpenAiCompatibleLlmToolSelectionClientTest test`：通过。
- `cd backend; mvn -Dtest=DocumentAgentRealShadowPathTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过，244 tests。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 协作代理未读取或输出 API Key；未输出完整 baseUrl、Authorization header、prompt、文档内容或模型完整返回。
- 未读取 `backend/.env`。
- 未修改 `application.yml` 或 `application-local.yml`。
- 未改变 production routing，真实工具执行仍由 `DocumentToolSelector` primary decision 决定。
- 未新增 API。
- 未修改前端代码。
- 未把完整 T010 写成通过；完整上传 / 解析 / MQ 链路仍为 BLOCKED。

## 2026-05-16 - T024 Selector Actuator Endpoint Implementation

### 本轮目标

实现默认关闭、只读、安全字段白名单的 Agent selector shadow metrics Actuator endpoint。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/AgentSelectorShadowEndpoint.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/AgentSelectorShadowEndpointTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/AgentSelectorShadowEndpointExposureTest.java`
- `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`
- `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `AgentSelectorShadowEndpoint`。
- endpoint 使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`。
- endpoint id 为 `agentSelectorShadow`，候选 path 为 `/actuator/agentSelectorShadow`，通过 `@ReadOperation` 提供只读 GET 语义。
- endpoint 只依赖 `SelectorMetricsDebugReporter`，返回 `SelectorMetricsDebugSnapshot`。
- 新增单元测试验证空 metrics、字段黑名单和只读行为。
- 新增 Spring context 测试验证默认未开启 / 未加入 exposure 时返回 404。

### 验证结果

- `cd backend; mvn -Dtest=AgentSelectorShadowEndpointTest test`：通过。
- `cd backend; mvn -Dtest=AgentSelectorShadowEndpointExposureTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过，286 tests，0 failures，0 errors。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 未修改 `application.yml`。
- 未修改 `application-local.yml`。
- 未加入 `management.endpoints.web.exposure.include`。
- 未新增普通 REST API 或 Controller。
- 未接 Prometheus。
- 未落库。
- 未修改前端。
- 未真实调用 provider。
- 未读取或输出 secret。
- 未改变 production routing，真实工具执行仍由 `DocumentToolSelector` 决定。
- 未测试未授权访问；当前没有专门的 Actuator Spring Security 配置，该项留到 T025。

## 2026-05-16 - T024 Selector Actuator Endpoint Implementation Boundary

### 本轮目标

先补充 T024 实现前的安全边界文档，再进入默认关闭 Actuator endpoint 最小实现。

### 修改文件

- `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 补充内容

- T024 实现必须使用 `@Endpoint(id = "agentSelectorShadow", enableByDefault = false)`。
- 这是项目中第一个自定义 Actuator endpoint，没有既有模式可复用，因此必须最小实现。
- T024 不修改 `application.yml` / `application-local.yml`。
- T024 不加入 `management.endpoints.web.exposure.include`。
- T024 只做默认关闭 endpoint、单元测试和 context 默认 404 测试。
- T024 暂不测试“未授权访问被拒绝”，因为当前项目没有专门的 Actuator Spring Security 配置；该项留到 T025。
- T024 不接 Prometheus；项目现有 Prometheus endpoint 和 selector-specific Prometheus metrics 是两回事，本轮不修改现有 Prometheus 配置。

### 明确未做事项

- 未新增 Actuator endpoint 代码。
- 未修改 Java 生产代码。
- 未修改配置文件。
- 未修改前端。
- 未接 Prometheus。
- 未读取或输出 secret。

## 2026-05-16 - T023 Selector Actuator Endpoint Design

### 本轮目标

只写 Agent selector shadow metrics Actuator endpoint 设计草案，不新增 endpoint、不改 Java 生产代码、不改配置、不接 Prometheus。

### 修改文件

- `docs/AGENT_SELECTOR_ACTUATOR_ENDPOINT_DESIGN.md`
- `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/AGENT_ASYNC_DESIGN.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 设计内容

- 新增 Actuator endpoint 设计草案，候选 id 为 `agentSelectorShadow`，候选 path 为 `/actuator/agentSelectorShadow`，候选方法为 GET，只读。
- 明确目标：给开发者和运维查看 selector shadow metrics，只输出聚合指标，不影响 production routing。
- 明确非目标：不做管理端 API、前端页面、Prometheus export、metrics 落库、用户 / 文档维度查询、raw sample、阈值修改或 production routing 切换。
- 补充返回字段白名单和禁止字段黑名单。
- 补充访问控制、Actuator exposure、脱敏、审计和风险。
- 补充未来 T024 候选实现类、依赖关系、测试策略和验收标准。
- 同步路线文档，说明 T024 前建议先做 Claude Code / 人工安全审查。

### 当前结论

- T023 只是设计文档任务。
- 尚未实现 Actuator endpoint。
- 短期仍使用 T021 内部 debug dump。
- T024 才可能进入候选实现，且应先安全审查。
- 完整 T010 仍为 BLOCKED，等待 MQ / 解析消费链路。

### T023e 自检结果

- `git status --short` 干净后进入 T023e。
- `git diff --name-only HEAD~4..HEAD` 仅包含允许文档。
- 未修改 Java 生产代码。
- 未修改测试代码。
- 未修改前端。
- 未新增 HTTP API / Controller。
- 未新增 Actuator endpoint。
- 未接 Prometheus。
- 未读取或输出 secret。

### 明确未做事项

- 未新增 HTTP API。
- 未新增 Controller。
- 未新增 Actuator endpoint。
- 未接 Prometheus。
- 未落库。
- 未修改 Java 生产代码或测试代码。
- 未修改前端。
- 未读取或输出 API Key、baseUrl、Authorization、prompt、用户 task、文档内容或模型完整返回。
- 未改变 production routing。

## 2026-05-16 - T022 Selector Observability Decision

### 本轮目标

只做 Agent selector shadow metrics 观测入口设计决策，不新增接口、不新增 Actuator endpoint、不接 Prometheus、不修改生产代码。

### 修改文件

- `docs/AGENT_SELECTOR_OBSERVABILITY_DECISION.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/AGENT_ASYNC_DESIGN.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 设计内容

- 新增 selector observability 决策文档，记录 T019 / T020 / T021 后的观测入口选择。
- 比较本地 debug dump、Actuator endpoint、管理端 API、Prometheus metrics 四种方案。
- 补充决策矩阵，覆盖实现成本、安全风险、本地开发、线上运维、鉴权、网络暴露、面试展示、生产环境、趋势观察和告警。
- 补充安全威胁模型，明确可能泄露的信息、攻击面、防护策略、字段白名单和字段黑名单。
- 同步现有 shadow mode / async design / TODO / handoff，当前推荐下一步为 T023 Actuator endpoint 设计草案，不直接实现接口。

### 当前结论

- 短期继续使用 T021 的内部 debug dump。
- T023 优先做 Actuator endpoint 设计草案，默认不实现。
- Prometheus 作为中期路线，只暴露数值指标和安全枚举 label。
- 管理端 API 暂缓，等待权限体系、管理员角色和审计策略明确。

### T022e 自检结果

- `git status --short` 干净后进入 T022e。
- `git diff --name-only HEAD~4..HEAD` 仅包含允许文档。
- 未修改 Java 生产代码。
- 未修改测试代码。
- 未修改前端。
- 未新增 HTTP API / Controller。
- 未新增 Actuator endpoint。
- 未接 Prometheus。
- 未读取或输出 secret。

### 明确未做事项

- 未新增 HTTP API。
- 未新增 Controller。
- 未新增 Actuator endpoint。
- 未接 Prometheus。
- 未落库。
- 未修改 Java 生产代码。
- 未修改前端。
- 未读取或输出 API Key、baseUrl、Authorization、prompt、用户 task、文档内容或模型完整返回。
- 未改变 production routing。
- 未把完整 T010 写成通过；完整上传 / 解析 / MQ 链路仍为 BLOCKED。

## 2026-05-15 - T021 Selector Metrics Debug Boundary

### 本轮目标

为 selector shadow metrics 提供内部只读 debug dump / reporter，并说明为什么当前暂不开放 HTTP API / Actuator / Prometheus 观测入口。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsDebugSnapshot.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsDebugReporter.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsDebugSnapshotTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsDebugReporterTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsDebugEvaluationTest.java`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/AGENT_ASYNC_DESIGN.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `SelectorMetricsDebugSnapshot`，将 metrics snapshot 和 threshold decision 格式化为安全 view。
- 新增 `SelectorMetricsDebugReporter`，只读组合 `SelectorMetricsCollector` 与 `SelectorShadowThresholdPolicy`，不清空 metrics，不改变状态。
- 新增离线 debug evaluation 测试，验证 dump 可展示 total / success / failure / matched / mismatch、matchRate、failureRate、provider 聚合、decision pair 聚合和 threshold decision。
- 文档说明暂不开放 API / Actuator：避免在管理端鉴权、内网边界和脱敏策略未设计前暴露 provider / decision metrics。

### 验证结果

- `cd backend; mvn -Dtest=SelectorMetricsDebug*Test test`：通过。
- `cd backend; mvn -Dtest=SelectorMetricsDebugEvaluationTest test`：通过。
- `cd backend; mvn -Dtest=ShadowToolSelectorEvaluationTest test`：通过。
- `cd backend; mvn -Dtest=RealShadowProviderEvaluationTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过。
- `cd backend; mvn -DskipTests compile`：通过。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### T021e 状态收口

- `docs/TODO_NEXT.md` 已将 T021 标记为 DONE。
- `docs/CODEX_HANDOFF.md` 已更新下一步为 T022：Actuator / 管理 API / Prometheus 观测入口设计决策。
- 当前仍仅提供内部 debug dump / reporter，不新增 API / Actuator / Prometheus。
- 完整 T010 仍为 BLOCKED，等待 MQ / 解析消费链路。

### 明确未做事项

- 未真实调用 provider。
- 未读取或输出 API Key、完整 baseUrl、Authorization header、prompt、用户 task、文档内容或模型完整返回。
- 未读取 `backend/.env`。
- 未改变 production routing。
- 未新增 HTTP API。
- 未新增 Actuator endpoint。
- 未接 Prometheus。
- 未落库。
- 未修改前端。
- 未把完整 T010 写成通过；完整上传 / 解析 / MQ 链路仍为 BLOCKED。

## 2026-05-15 - T020 Selector Shadow Threshold Metrics

### 本轮目标

把 T019 的真实 provider shadow-only 能力升级为可观测、可评估、可设置阈值的 shadow 评估基础设施，但不让 shadow decision 接管 production routing。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsCollector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsSnapshot.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorShadowThresholdDecision.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorShadowThresholdPolicy.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsCollectorTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/SelectorShadowThresholdPolicyTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/SelectorShadowThresholdEvaluationTest.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/REAL_PROVIDER_SHADOW_PREFLIGHT.md`
- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `SelectorMetricsCollector` / `SelectorMetricsSnapshot` 增强为记录 totalCount、successCount、failureCount、matchedCount、mismatchCount、matchRate、failureRate、lastUpdatedTime。
- metrics 支持 provider 维度聚合和 primaryDecision / shadowDecision 的安全 decision pair 聚合。
- 新增 `SelectorShadowThresholdPolicy` / `SelectorShadowThresholdDecision`，默认 `minimumSamples=20`、`minMatchRate=0.95`、`maxFailureRate=0.05`。
- 阈值策略只返回 `allowPromotionCandidate` 和 reason，不修改配置，不改变 production routing。
- 新增离线 threshold evaluation 测试，确认 promotion candidate 不会改变 `DocumentAgentServiceImpl` 的 primary decision。
- 文档补充 metrics 字段、threshold policy、日志脱敏边界和下一步 T021。

### 验证结果

- `cd backend; mvn -Dtest=SelectorMetricsCollectorTest test`：通过。
- `cd backend; mvn -Dtest=SelectorShadowThresholdPolicyTest test`：通过。
- `cd backend; mvn -Dtest=SelectorShadowThresholdEvaluationTest test`：通过。
- `cd backend; mvn -Dtest=ShadowToolSelectorEvaluationTest test`：通过。
- `cd backend; mvn -Dtest=RealShadowProviderEvaluationTest test`：通过。
- `cd backend; mvn -Dtest=DocumentAgentServiceImplTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。
- `cd backend; mvn test -DskipITs`：通过。
- `cd frontend; npm run lint`：通过。
- `cd frontend; npm run build`：通过。

### 明确未做事项

- 未真实调用 provider。
- 未读取或输出 API Key、完整 baseUrl、Authorization header、prompt、用户 task、文档内容或模型完整返回。
- 未读取 `backend/.env`。
- 未改变 production routing，真实工具执行仍由 `DocumentToolSelector` primary decision 决定。
- 未新增 API，未改前端。
- 未落库，未接 Prometheus。
- 未接 function calling / RAG / MCP / Spring AI / LangChain4j。
- 未把完整 T010 写成通过；完整上传 / 解析 / MQ 链路仍为 BLOCKED。

## 2026-05-14 - T011d Tool Selector Evaluation Cases

### 本轮目标

新增 selector 评估样例集和离线测试，为后续比较关键词 selector 与未来 LLM selector 建立基线。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/ToolSelectorEvaluationTest.java`
- `backend/src/test/resources/agent/tool-selector-eval-cases.json`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 24 条 `tool-selector-eval-cases.json` 样例，覆盖状态查询、摘要查询、证据问答、英文大小写、中文表达、模糊表达、summary + evidence 冲突和空白输入。
- 新增 `ToolSelectorEvaluationTest`，读取 JSON 样例并调用当前 `DocumentToolSelector`，断言 decision 符合 expectedDecision。
- 测试输出 pass count / total count，当前基线为 24/24。

### 验证结果

- `cd backend; mvn -Dtest=ToolSelectorEvaluationTest test`：通过，24/24 cases。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T012a Shadow LLM Selector Adapter

### 本轮目标

进入 P3 Shadow LLM Selector 基础设施阶段，新增未来 LLM selector 的适配接口、fake shadow implementation 和 compare result。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/FakeLlmToolSelector.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmSelectorShadowResult.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/FakeLlmToolSelectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `LlmToolSelector` 接口，定义 `selectWithPrompt` 输入 task、parseReady、hasSummary 和工具定义列表，返回 `LlmToolSelectionResult`。
- 新增 `FakeLlmToolSelector`，不联网、不调用真实 LLM，仅复用 `DocumentToolSelector` 或在 parseReady=false 时返回状态工具决策。
- 新增 `LlmSelectorShadowResult`，记录 primaryDecision、shadowDecision、matched、primaryReason 和 shadowReason。
- 单元测试覆盖 summary、QA、status、parseReady=false、matched=true 和 shadowDecision 非空。

### 验证结果

- `cd backend; mvn -Dtest=FakeLlmToolSelectorTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T012b Selector Feature Flags

### 本轮目标

新增 Agent selector feature flags，为后续 shadow compare 提供显式配置。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/config/AgentSelectorProperties.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/docpilot/backend/ai/agent/config/AgentSelectorPropertiesTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `AgentSelectorProperties`，绑定 `app.agent.selector` 配置。
- 新增 `app.agent.selector.mode`，允许 `keyword` / `shadow_llm`，默认 `keyword`。
- 新增 `app.agent.selector.shadow-enabled`，默认 `false`。
- 配置测试覆盖默认值、shadow 配置绑定和非法 mode 拒绝。

### 验证结果

- `cd backend; mvn -Dtest=AgentSelectorPropertiesTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T012c Selector Shadow Comparison

### 本轮目标

在 `DocumentAgentServiceImpl` 中接入 primary selector + shadow selector compare，但 primary decision 仍唯一生效。本轮不调用真实 LLM，不接 function calling，不改变 API 返回或默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmSelectorShadowResult.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `DocumentAgentServiceImpl` 在文档 parseReady 后仍先使用 primary `ToolSelector` 得到真实 decision。
- 当 `app.agent.selector.shadow-enabled=true` 时，旁路调用 `LlmToolSelector`，生成 `LlmSelectorShadowResult` 并记录 compare 日志。
- shadow selector 异常只记录 warn，primary decision 和真实工具执行不受影响。
- parseReady=false 时仍直接返回状态提示，不运行 primary selector 或 shadow compare。
- 单元测试覆盖 shadow compare 不影响真实工具执行、matched=true、开关关闭不运行、parseReady=false 不运行。

### 验证结果

- `cd backend; mvn -Dtest=DocumentAgentServiceImplTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过，175 tests，0 failures，0 errors。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 API 返回协议。
- 未修改 AgentTask / AgentStep schema。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T012d Selector Shadow Metrics

### 本轮目标

新增 selector compare metrics 的内存态 collector 和 snapshot，为后续 shadow compare 观测打基础。本轮不接 Micrometer / Prometheus，不新增 API，不落库。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsSnapshot.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsCollector.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/SelectorMetricsCollectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `SelectorMetricsSnapshot`，包含 totalComparisons、matchedCount、mismatchCount、matchRate 和 lastUpdatedTime。
- 新增 `SelectorMetricsCollector`，通过 `record(primaryDecision, shadowDecision)` 线程安全记录 match / mismatch。
- collector 仅内存态保存数据，不落库、不接外部指标系统、不暴露接口。
- 单元测试覆盖全 match、部分 mismatch、matchRate 正确、空 snapshot 和并发 record。

### 验证结果

- `cd backend; mvn -Dtest=SelectorMetricsCollectorTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过，179 tests，0 failures，0 errors。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未新增 API。
- 未落库。
- 未接 Micrometer / Prometheus。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T013a Selector Shadow Metrics 接入

### 本轮目标

将 `SelectorMetricsCollector` 接入 `DocumentAgentServiceImpl` 的 shadow compare 路径，让 shadow compare 成功执行后能记录 primary / shadow decision 的 match 情况。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `DocumentAgentServiceImpl` 构造函数注入 `SelectorMetricsCollector`。
- shadow compare 成功生成 `LlmSelectorShadowResult` 后调用 `record(primaryDecision, shadowDecision)`。
- shadow 关闭、parseReady=false、shadow selector 未执行或 shadow selector 异常时不记录 comparison。
- 单元测试补充 metrics 断言：关闭时不增加、开启且 matched 时 totalComparisons / matchedCount 增加、parseReady=false 不增加、真实 decision 和工具执行仍由 primary selector 决定。

### 验证结果

- `cd backend; mvn -Dtest=DocumentAgentServiceImplTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过，179 tests，0 failures，0 errors。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未新增 API。
- 未修改 API 返回协议。
- 未修改 AgentTask / AgentStep schema。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T013b Shadow Selector 离线评估

### 本轮目标

新增 Shadow Selector 离线评估测试，复用现有 `tool-selector-eval-cases.json` 对比 primary `DocumentToolSelector` 与 `FakeLlmToolSelector`，统计 match rate。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/ShadowToolSelectorEvaluationTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `ShadowToolSelectorEvaluationTest`，读取现有 24 条 tool selector eval cases。
- 每条 case 执行 primary selector 与 fake shadow selector，并记录到 `SelectorMetricsCollector`。
- 断言 shadow decision 非空，并断言 matchRate 不低于 0.95。
- mismatch 时输出 task、primary decision 与 shadow decision，便于后续排查。

### 验证结果

- `cd backend; mvn -Dtest=ShadowToolSelectorEvaluationTest test`：通过。
- 离线结果：24 cases，23 matched，1 mismatch，matchRate=0.9583。
- 唯一 mismatch：空白 task 且 parseReady=false 时，primary 当前默认 `qa_tool`，fake shadow 根据 parse-not-ready 返回 `status_only`。
- `cd backend; mvn test -DskipITs`：通过，180 tests，0 failures，0 errors。

### 明确未做事项

- 未修改生产代码。
- 未修改 eval cases。
- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未新增 API。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T013c Selector Shadow Mode 设计说明

### 本轮目标

新增 selector shadow mode 设计说明，避免后续接手者误以为 LLM selector 已接管生产。

### 修改文件

- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 记录当前 selector 架构：primary 为 `DocumentToolSelector`，shadow 为 `FakeLlmToolSelector`。
- 说明 `ToolDefinitionProvider`、`LlmToolSelectionPromptBuilder` 和 `LlmToolSelectionParser` 的边界。
- 记录 feature flag、内存态 metrics、当前已验证内容、完整 T010 BLOCKED 原因和不能硬吹的边界。
- 给出后续 T014-T017 路线：disabled real adapter、shadow-only real call、人工审核 eval、达到阈值后再考虑小流量接管。

### 验证结果

- `git status --short`：检查通过。
- `docs/AGENT_SELECTOR_SHADOW_MODE.md` 已新增。

### 明确未做事项

- 未修改代码。
- 未调用真实 LLM。
- 未接 function calling。
- 未新增 API。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T013d Selector Shadow Roadmap 状态更新

### 本轮目标

更新协作文档当前阶段，把 T013 Selector Shadow Observability 闭环收口，并将下一步推荐切换到 T014。

### 修改文件

- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 记录 T013a-c 已完成：selector shadow metrics 已接入、shadow offline evaluation 已完成、shadow mode 文档已新增。
- 明确当前默认行为仍是 keyword selector。
- 明确当前没有真实 LLM 调用，shadow decision 不接管生产 routing。
- 明确完整 T010 仍为 BLOCKED，原因仍是 MQ disabled / `NoopParseTaskMessageProducer`。
- 下一步推荐 T014：real LLM selector disabled adapter，默认关闭，不接管生产。
- 明确不建议直接进入生产 LLM tool calling、MCP、RAG、多 Agent 或 MQ 异步 Agent。

### 验证结果

- `git status --short`：检查通过。
- `git diff -- docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md`：已复核。

### 明确未做事项

- 未修改代码。
- 未调用真实 LLM。
- 未接 function calling。
- 未新增 API。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T014a Disabled LLM Selection Client

### 本轮目标

新增真实 LLM tool selector 未来调用模型时使用的 client 抽象，同时提供 disabled client，确保当前不会误调用外部模型。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionClient.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionClientResponse.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/DisabledLlmToolSelectionClient.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/DisabledLlmToolSelectionClientTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `LlmToolSelectionClient` 接口，定义 `completeSelectionPrompt(String prompt)`。
- 新增 `LlmToolSelectionClientResponse`，包含 rawText、provider、model、disabled 和 errorMessage。
- 新增 `DisabledLlmToolSelectionClient`，调用时只返回 disabled response，不联网、不调用真实模型、不读取环境变量。
- 单元测试覆盖 disabled=true、provider/model disabled 标识、errorMessage 非空，以及 blank / null prompt 不抛敏感异常。

### 验证结果

- `cd backend; mvn -Dtest=DisabledLlmToolSelectionClientTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未读取 `backend/.env`。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未新增 API。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T014b Real LLM Tool Selector Adapter

### 本轮目标

新增 `RealLlmToolSelector` adapter，把 prompt builder、client、parser 串起来。本轮不接入生产 service，不真实调用外部模型。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/RealLlmToolSelector.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/RealLlmToolSelectorTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `RealLlmToolSelector`，实现 `LlmToolSelector`。
- `selectWithPrompt` 先构建 prompt，再调用 `LlmToolSelectionClient`，最后用 `LlmToolSelectionParser` 解析 rawText。
- client disabled 时抛出明确异常。
- client 返回非法 JSON 或 client 失败时抛出异常，不静默 fallback 到 keyword selector。
- 单元测试使用 fake client 覆盖 summary、QA、disabled、非法 JSON 和不 fallback 路径。

### 验证结果

- `cd backend; mvn -Dtest=RealLlmToolSelectorTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未读取 `backend/.env`。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未新增 API。
- 未修改 DDL。
- 未修改前端。
- 未接入 `DocumentAgentServiceImpl`。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T014c Real LLM Selector Shadow Runner

### 本轮目标

新增 Real LLM selector disabled shadow runner，用于未来把 `RealLlmToolSelector` 接入 shadow compare。本轮仍不接入生产 service，不记录 metrics，不真实调用模型。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/RealLlmSelectorShadowRunner.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/RealLlmSelectorShadowRunResult.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/RealLlmSelectorShadowRunnerTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `RealLlmSelectorShadowRunner`，输入 primary decision、task、parseReady、hasSummary 和工具定义列表。
- runner 内部调用 `RealLlmToolSelector`。
- disabled client 或解析异常失败时返回 success=false、shouldRecordMetrics=false，不影响 primary decision。
- 成功时返回 shadowDecision、matched 和 shouldRecordMetrics=true。
- 单元测试覆盖 disabled、valid fake client、matched、mismatch、失败不记录 metrics 和成功可记录 metrics。

### 验证结果

- `cd backend; mvn -Dtest=RealLlmSelectorShadowRunnerTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未读取 `backend/.env`。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未新增 API。
- 未修改 DDL。
- 未修改前端。
- 未接入 `DocumentAgentServiceImpl`。
- 未记录 metrics。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-15 - T014d Real LLM Selector Adapter 文档状态

### 本轮目标

更新 Selector Shadow Mode 文档和协作文档，说明 real LLM selector adapter 已有，但当前默认 disabled、未真实调用、未接入生产 service、未接管 routing。

### 修改文件

- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 在 `docs/AGENT_SELECTOR_SHADOW_MODE.md` 中补充 `LlmToolSelectionClient`、`DisabledLlmToolSelectionClient`、`RealLlmToolSelector` 和 `RealLlmSelectorShadowRunner` 的当前状态。
- 明确 disabled client 不联网、不调用真实模型、不读取环境变量或 `backend/.env`，用于防止误调用真实 provider。
- 明确 `RealLlmToolSelector` 只串联 prompt builder、client 和 parser，当前不是生产 Spring bean，未注入 `DocumentAgentServiceImpl`。
- 明确 `RealLlmSelectorShadowRunner` 未来可用于 real selector shadow compare，当前只在单元测试中验证 disabled / fake client 行为。
- 将协作文档下一步推荐更新为 T015：在 feature flag 严格关闭的前提下接入 runner 到 service shadow 路径，真实 provider 调用另开任务。

### 验证结果

- `git status --short`：已检查。
- `git diff -- docs/AGENT_SELECTOR_SHADOW_MODE.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md`：已复核。

### 明确未做事项

- 未修改业务代码。
- 未调用真实 LLM。
- 未接 function calling。
- 未新增 API。
- 未修改前端。
- 未修改 DDL。
- 未接入 `DocumentAgentServiceImpl`。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-15 - T016b Fake LLM Selection Client

### 本轮目标

新增可测试的 fake provider client，用于后续 shadow-only smoke。本 client 不联网、不读取密钥、不读取环境变量。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/FakeLlmToolSelectionClient.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/FakeLlmToolSelectionClientTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `FakeLlmToolSelectionClient`，实现 `LlmToolSelectionClient`。
- 根据 prompt 中 `Current task` 内容返回 `summary_tool`、`qa_tool` 或 `status_only` 的合法 JSON。
- 返回 provider=`fake`、model=`fake-selector`、disabled=false。
- 测试使用 `LlmToolSelectionParser` 解析 fake client 的 rawText，验证输出协议可用。

### 验证结果

- `cd backend; mvn -Dtest=FakeLlmToolSelectionClientTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未联网。
- 未读取 API Key。
- 未读取环境变量。
- 未读取 `backend/.env`。
- 未调用真实 LLM。
- 未接 function calling。
- 未改变 disabled client。
- 未改变 production routing。

## 2026-05-15 - T016c OpenAI-compatible Selector Client Skeleton

### 本轮目标

新增 OpenAI-compatible LLM selection client 结构骨架，但本轮不发 HTTP 请求、不联网、不读取密钥。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/OpenAiCompatibleToolSelectionRequest.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/OpenAiCompatibleToolSelectionResponse.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/OpenAiCompatibleLlmToolSelectionClient.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/OpenAiCompatibleLlmToolSelectionClientTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 OpenAI-compatible request record，包含 model、messages、temperature 和 maxTokens。
- 新增 OpenAI-compatible response record，包含 rawText、provider、model 和 finishReason。
- 新增 `OpenAiCompatibleLlmToolSelectionClient`，实现 `LlmToolSelectionClient`。
- 当前 `completeSelectionPrompt` 只返回 provider=`openai_compatible` 的 disabled response，不发起网络请求。
- 提供 `buildRequest(prompt)` 纯构造方法，为未来真实 provider 接入做结构准备。

### 验证结果

- `cd backend; mvn -Dtest=OpenAiCompatibleLlmToolSelectionClientTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未引入 HTTP client。
- 未发 HTTP 请求。
- 未读取 API Key。
- 未读取环境变量。
- 未读取 `backend/.env`。
- 未调用真实 LLM。
- 未接 function calling。
- 未接入 production service。
- 未改变 production routing。

## 2026-05-15 - T017b Factory-backed Real Shadow Runner

### 本轮目标

让 `RealLlmSelectorShadowRunner` 支持通过 `RealLlmToolSelectorFactory` 和 `AgentSelectorProperties` 创建 factory-backed selector。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/RealLlmSelectorShadowRunner.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/RealLlmSelectorShadowRunnerTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `RealLlmSelectorShadowRunner` 保留原有直接注入 `RealLlmToolSelector` 的构造方式。
- 新增 factory-backed 构造方式：`RealLlmToolSelectorFactory` + `AgentSelectorProperties`。
- 默认 disabled provider 返回 success=false / shouldRecordMetrics=false。
- provider=`fake` 可返回 success=true，并根据 primary / shadow decision 判断 matched。
- provider=`openai_compatible` 仍 dry-run disabled，不联网。

### 验证结果

- `cd backend; mvn -Dtest=RealLlmSelectorShadowRunnerTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未修改 `DocumentAgentServiceImpl`。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未发 HTTP 请求。
- 未调用真实 LLM。
- 未新增 API。
- 未修改前端。
- 未改变 production routing。

## 2026-05-15 - T017a Factory-backed Real Selector Builder

### 本轮目标

新增小型构造器，将 provider settings、LLM selection client factory、prompt builder 和 parser 串成 `RealLlmToolSelector`。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/RealLlmToolSelectorFactory.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/RealLlmToolSelectorFactoryTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `RealLlmToolSelectorFactory`。
- 默认 properties 下通过 `LlmToolSelectionClientFactory` 创建 disabled client，selector 调用明确失败。
- provider=`fake` 时 selector 可返回合法 decision。
- provider=`openai_compatible` 时 selector 仍使用 dry-run disabled client，不联网。
- unknown provider fallback disabled。

### 验证结果

- `cd backend; mvn -Dtest=RealLlmToolSelectorFactoryTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未接入 `DocumentAgentServiceImpl`。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未发 HTTP 请求。
- 未调用真实 LLM。
- 未新增 API。
- 未修改前端。
- 未改变 production routing。

## 2026-05-15 - T016e Provider Client Skeleton 文档状态

### 本轮目标

更新 selector shadow 文档和协作状态，说明 provider-specific skeleton 已有，但当前仍未真实调用任何外部模型。

### 修改文件

- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 记录 T016 新增的 provider settings、`FakeLlmToolSelectionClient`、`OpenAiCompatibleLlmToolSelectionClient` skeleton 和 `LlmToolSelectionClientFactory`。
- 明确 provider 当前状态：`disabled` 为默认；`fake` 仅用于测试和未来 shadow-only 验证；`openai_compatible` 只有 dry-run 骨架，不联网。
- 明确当前没有 DeepSeek / OpenAI / 硅基流动真实调用，没有读取 API Key，没有读取 `backend/.env`，没有真实 HTTP 请求。
- 将下一步推荐更新为 T017：以默认 disabled 的方式把 factory 接入 real shadow client 构造路径。

### 验证结果

- `git status --short`：已检查。
- `git diff -- docs/AGENT_SELECTOR_SHADOW_MODE.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md`：已复核。

### 明确未做事项

- 未修改业务代码。
- 未调用真实 LLM。
- 未读取 API Key。
- 未读取 `backend/.env`。
- 未发 HTTP 请求。
- 未新增 API。
- 未修改前端。
- 未改变 production routing。

## 2026-05-15 - T016d LLM Selection Client Factory

### 本轮目标

新增 LLM selection client factory，根据 provider 配置选择 disabled / fake / OpenAI-compatible client，但默认必须返回 disabled client。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionClientFactory.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionClientFactoryTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `LlmToolSelectionClientFactory`。
- provider=`disabled` 或 null properties 时返回 `DisabledLlmToolSelectionClient`。
- provider=`fake` 时返回 `FakeLlmToolSelectionClient`。
- provider=`openai_compatible` 时返回 `OpenAiCompatibleLlmToolSelectionClient` skeleton。
- unknown provider fallback disabled，不返回真实联网 client。
- 本轮不把 factory 接入 production service，不改变现有 bean 注入结构。

### 验证结果

- `cd backend; mvn -Dtest=LlmToolSelectionClientFactoryTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未接入 `DocumentAgentServiceImpl`。
- 未调用真实 LLM。
- 未发 HTTP 请求。
- 未读取 API Key。
- 未读取环境变量。
- 未读取 `backend/.env`。
- 未接 function calling。
- 未新增 API。
- 未修改前端。
- 未改变 production routing。

## 2026-05-15 - T016a LLM Selector Provider Settings

### 本轮目标

新增 LLM selector provider 配置模型，为后续 disabled / fake / OpenAI-compatible client skeleton 选择做准备，但默认仍为 disabled。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/config/AgentSelectorProperties.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/config/AgentSelectorPropertiesTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `llmProvider`，允许 `disabled`、`fake`、`openai_compatible`，默认 `disabled`。
- 新增 `llmModel`，默认空字符串。
- 新增 `llmBaseUrl`，默认空字符串。
- 新增 `llmRequestTimeoutMs`，默认 `3000`，并校验必须为正数。
- 配置测试覆盖默认安全值、显式绑定、非法 provider 和非法 timeout。

### 验证结果

- `cd backend; mvn -Dtest=AgentSelectorPropertiesTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未修改 `application.yml` 或 `application-local.yml`。
- 未读取环境变量。
- 未读取 `backend/.env`。
- 未调用真实 LLM。
- 未接 function calling。
- 未新增 API。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-15 - T015d Real Shadow Selector Integration 文档状态

### 本轮目标

更新 selector shadow mode 文档和协作状态，说明 `RealLlmSelectorShadowRunner` 已接入 service 的 real shadow 分支，但默认关闭，且 disabled client 防止真实模型调用。

### 修改文件

- `docs/AGENT_SELECTOR_SHADOW_MODE.md`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 更新 real shadow path 当前状态：runner 已接入 `DocumentAgentServiceImpl`，默认 `realShadowEnabled=false`，默认不运行。
- 明确即使开启 real shadow，当前 client 仍为 `DisabledLlmToolSelectionClient`，不会真实调用模型。
- 明确 selector 决策顺序：primary `DocumentToolSelector` 唯一决定真实工具执行；fake shadow 与 real shadow 都只用于旁路 compare。
- 明确 fake shadow metrics 已可记录，real shadow metrics 默认不记录，只有 `realShadowRecordMetrics=true` 且 real shadow success 时才允许记录。
- 更新后续路线到 T016：provider-specific disabled / fake client skeleton，真实 provider 调用另开任务。

### 验证结果

- `git status --short`：已检查。
- `git diff -- docs/AGENT_SELECTOR_SHADOW_MODE.md docs/TODO_NEXT.md docs/CHANGELOG_CODING.md docs/CODEX_HANDOFF.md`：已复核。

### 明确未做事项

- 未修改业务代码。
- 未调用真实 LLM。
- 未接 function calling。
- 未新增 API。
- 未修改前端。
- 未修改 DDL。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-15 - T015c Real Shadow Path Tests

### 本轮目标

新增聚焦 real shadow path 的 service 单元测试，避免 `DocumentAgentServiceImplTest` 继续膨胀，同时锁定 real shadow 接入边界。

### 修改文件

- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentRealShadowPathTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `DocumentAgentRealShadowPathTest`。
- 覆盖默认配置下不运行 real shadow。
- 覆盖开启 fake shadow 不会隐式开启 real shadow。
- 覆盖 real shadow 使用 disabled client 时 agent run 仍成功。
- 覆盖 real shadow 异常时 fail-open。
- 覆盖 parseReady=false 时跳过 fake shadow 和 real shadow。
- 覆盖 `realShadowRecordMetrics=false` 时 real shadow success 不记录 metrics。
- 覆盖 `realShadowRecordMetrics=true` 且 real shadow success 时记录 metrics。

### 验证结果

- `cd backend; mvn -Dtest=DocumentAgentRealShadowPathTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未修改生产代码。
- 未调用真实 LLM。
- 未接 function calling。
- 未新增 API。
- 未修改前端。
- 未修改 DDL。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-15 - T015b Disabled Real Selector Shadow Path

### 本轮目标

把 `RealLlmSelectorShadowRunner` 接入 `DocumentAgentServiceImpl` 的 shadow 路径，但默认严格关闭，不让 real shadow 影响生产工具选择。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/service/impl/DocumentAgentServiceImpl.java`
- `backend/src/test/java/com/docpilot/backend/ai/service/DocumentAgentServiceImplTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- `DocumentAgentServiceImpl` 在构造时基于 prompt builder、LLM selection client 和 parser 创建 `RealLlmSelectorShadowRunner`。
- fake shadow compare 与 real shadow compare 分离执行，互相失败不影响 primary decision。
- real shadow 仅在 `shadowEnabled=true` 且 `realShadowEnabled=true` 时执行。
- real shadow 返回失败或抛出异常时 fail-open，主流程继续使用 primary `DocumentToolSelector` decision。
- real shadow metrics 只有在 `realShadowRecordMetrics=true` 且 runner success 时才允许记录；默认不记录。
- 单元测试覆盖默认不运行、fake shadow 不隐式启用 real shadow、disabled client 不影响主流程、real shadow 异常 fail-open、parseReady=false 跳过 shadow。

### 验证结果

- `cd backend; mvn -Dtest=DocumentAgentServiceImplTest test`：通过。
- `cd backend; mvn test -DskipITs`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未新增 API。
- 未修改前端。
- 未修改 DDL。
- 未修改 `application.yml`。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-15 - T015a Real Shadow Selector Safety Flags

### 本轮目标

为 real LLM selector shadow runner 补充更细粒度安全开关，默认严格关闭，避免真实 selector shadow 路径被误启用。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/config/AgentSelectorProperties.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/config/AgentSelectorPropertiesTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `realShadowEnabled`，默认 `false`，用于控制是否允许执行 `RealLlmSelectorShadowRunner`。
- 新增 `realShadowRecordMetrics`，默认 `false`，避免 real shadow metrics 与现有 fake shadow metrics 混淆。
- 新增 `realShadowFailOpen`，默认 `true`，确保 real shadow 失败时主流程继续使用 primary decision。
- 配置测试覆盖默认值和显式绑定值。

### 验证结果

- `cd backend; mvn -Dtest=AgentSelectorPropertiesTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未修改 `application.yml` 或 `application-local.yml`。
- 未调用真实 LLM。
- 未接 function calling。
- 未新增 API。
- 未修改前端。
- 未修改 DDL。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T011c Tool Selection Prompt Builder

### 本轮目标

新增未来 LLM Tool Selection 的 prompt builder，为后续真实 LLM selector 提供稳定提示词骨架。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionPromptBuilder.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionPromptBuilderTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `LlmToolSelectionPromptBuilder`，输入 task、parseReady、hasSummary 和 `ToolDefinition` 列表，输出工具选择 prompt。
- prompt 包含当前任务、文档解析状态、是否已有 summary、可用工具列表、每个工具的输入输出 schema、JSON 输出协议和安全限制。
- prompt 明确 decision 只能从 `status_only`、`summary_tool`、`qa_tool` 中选择，并禁止生成 SQL、系统命令或调用未列出的工具。
- 单元测试覆盖 toolName、JSON 输出格式、安全限制、task、parseReady、hasSummary 和 decision 值。

### 验证结果

- `cd backend; mvn -Dtest=LlmToolSelectionPromptBuilderTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。

## 2026-05-14 - T011b LLM Tool Selection 输出协议和解析器

### 本轮目标

定义未来 LLM Tool Selection 的 JSON 输出协议，并实现离线 parser。本轮不调用真实 LLM，不接 function calling，不改变默认 Agent 行为。

### 修改文件

- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionResult.java`
- `backend/src/main/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionParser.java`
- `backend/src/test/java/com/docpilot/backend/ai/agent/tool/LlmToolSelectionParserTest.java`
- `docs/TODO_NEXT.md`
- `docs/CODEX_HANDOFF.md`
- `docs/CHANGELOG_CODING.md`

### 实现内容

- 新增 `LlmToolSelectionResult`，包含 decision、toolNames、routingReason、matchedKeywords、confidence。
- 新增 `LlmToolSelectionParser`，可从原始文本中提取第一个 JSON object，并校验 decision、已注册工具名、confidence 范围和 toolNames 非空。
- 解析失败时抛出明确异常，不做静默 fallback。
- 单元测试覆盖标准 JSON、前后带自然语言、非法 decision、未知 toolName、confidence 越界、空输入和空 toolNames。

### 验证结果

- `cd backend; mvn -Dtest=LlmToolSelectionParserTest test`：通过。
- `cd backend; mvn -DskipTests compile`：通过。

### 明确未做事项

- 未调用真实 LLM。
- 未接 function calling。
- 未引入新依赖。
- 未修改 `pom.xml`。
- 未修改 DDL。
- 未修改前端。
- 未改变当前默认 `DocumentToolSelector` 关键词路由行为。
