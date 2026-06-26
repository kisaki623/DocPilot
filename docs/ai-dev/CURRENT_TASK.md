# Current Task

当前任务：T013 Conversation Context Management / Agent Memory Mode MVP

## T013 本轮已完成

- 新增会话级上下文 MVP 后端底座：`tb_conversation`、`tb_conversation_message`、`tb_conversation_summary`、`tb_context_trace`、`tb_user_memory` 五张新表迁移脚本；未改动既有 `qa_history`、`agent_task`、`agent_step`、RAG 或 KnowledgeBase 表结构。
- 新增 `conversation` / `memory` / `ai.context` 后端 package，提供会话创建、列表、知识库绑定、非流式消息发送、会话摘要读取 / 删除、用户长期记忆手动维护接口。
- 新增 `ContextAssemblyService`，支持 `RECENT_TURNS` 和 `AGENT_MEMORY` 两种模式；上下文来源包含系统提示、长期记忆、会话摘要、最近轮次和可选 KnowledgeBase evidence。
- KnowledgeBase evidence 只复用现有 `KnowledgeBaseRagRetrievalService` 与 `KnowledgeBaseScopeGuard`，不直连 Qdrant，不改现有 RAG / Agent / ToolCall 主链路。
- 新增 token 预算、优先级裁剪、prompt rendering、trace response、记忆敏感内容拦截和权限过滤单元测试。
- 新增摘要级 `ContextTrace` 持久化与按消息查询 API：只保存 mode、计数、token 估算、RAG hit 分布、fallback / truncated 摘要字段，不保存完整 prompt 或 evidence 原文；trace 写入失败不影响主回答链路。
- 新增显式 `POST /api/conversations/{conversationId}/summary/refresh`，使用本地 extractive 摘要压缩最近消息；不调用真实外部模型，不做后台自动摘要。
- 新增长期记忆候选机制：规则式 `MemoryExtractionService` 从会话用户消息中提取 `SUGGESTED` 记忆，提供候选列表、提取、接受、忽略 API；候选记忆默认不进入 prompt，只有接受后才转为 `ACTIVE`。
- 新增 `KnowledgeBaseEvidenceContextBuilderTest`，覆盖 Agent Memory 绑定 KnowledgeBase 后的中文必需 RAG 触发、英文可选触发、no-evidence 中文 fallback、禁用 RAG 时不检索、长 evidence 截断。
- 新增前端会话工作台 MVP：`/conversations` 页面、会话 / 记忆 API wrapper、顶部导航入口；支持创建会话、发送非流式消息、绑定 / 解绑 KnowledgeBase、查看 summary / trace、手动维护 ACTIVE 记忆、提取 / 接受 / 忽略候选记忆。
- 完成求职展示级前端收口：`/` 首页改为工程链路总览与 smoke 边界入口，`/dashboard` 增加推荐演示路径和会话上下文入口，`/knowledge-bases` 增加 retrieval / evidence / answer model / model call / 命中文档分布可观测卡片，`/conversations` 增强非流式 MVP、摘要级 Trace、Memory 与 KnowledgeBase evidence 的展示口径。
- 完成前端 AI 产品感重点页二次精修：`/` 首页新增系统流程面板和产品级 CTA，`/dashboard` 收敛为 Demo Command Center，`/knowledge-bases` 和 `/conversations` 未登录态不再暴露完整空工作台，并强化 KnowledgeBase evidence / Agent Memory Trace 的观测层级。
- 完成 `/conversations` 核心页 GPT / DeepSeek 风格重做：按 Gemini CLI headless 建议，将页面从三栏工程控制台收敛为左侧会话历史、居中聊天流、底部悬浮 composer 和右侧 Context Inspector 抽屉；Trace / Memory / Summary / KnowledgeBase evidence 继续保留，但退为聊天辅助信息，不再抢占主聊天区。
- 完成前端 UI 文案成熟化收口：按 Gemini CLI 文案建议去掉页面上的“求职 / 面试 / MVP / 演示 / 生产级 / smoke”等内部口径，把首页、Dashboard、KnowledgeBase、Conversations、Agent、登录、上传和文档详情页的说明改为更克制的产品表达；不改后端 API，不新增依赖。
- 完成近期新增代码质量修复：KnowledgeBase Hybrid 检索按 `indexVersion` 过滤并保留 keyword-only hit 元数据；RRF `rrfK` 配置生效；BM25 scorer 改为请求内局部状态；rerank 真正接入 KnowledgeBase RAG 主链路并输出观测字段；会话消息发送改为先生成答案、再用 conversation 行锁连续写入 user / assistant 消息；前端记忆类型改为后端合法枚举并展示 score breakdown。
- 完成当前收口修复：README / showcase 面试材料已同步 Hybrid / Rerank “默认关闭可选增强”的口径；`.env.example` / `.env.demo.example` / `.env.cloud.example` 已补充安全占位配置；`DEMO_SMOKE_RECORD.md` 明确真实 rerank provider 尚未 smoke。
- 完成 tunnel 协作入口修复：MySQL / Qdrant tunnel 详细说明原本已在 `backend/README.md`；本轮已在 `AGENTS.md` 增加一线提醒，明确云 MySQL / Qdrant runtime smoke 前必须启动 `scripts/dev/start-cloud-tunnels.ps1`，普通离线测试和前端未登录态 smoke 不要求 tunnel。
- 完成交付前审查收口修复：`.claude/` 与 `test-hybrid-rag.sh` 已加入 `.gitignore`，保留本地文件但不作为交付内容；会话发送事务已收窄到最终落库阶段，模型调用和 trace best-effort 不再包在同一个长事务内。

## T013 已验证

```powershell
cd backend
mvn -DskipTests compile
mvn "-Dtest=*Context*,*Conversation*,*Memory*" test
mvn "-Dtest=*Rag*,*KnowledgeBase*" test
mvn "-Dtest=*Agent*,*Tool*,*ToolCall*,OpenAi*" test

cd frontend
npm run lint
npm run build
```

验证结果：

- backend compile：PASS。
- Context / Conversation / Memory tests：54 tests，0 failures，0 errors。
- RAG / KnowledgeBase 回归：189 tests，0 failures，0 errors。
- 2026-06-26 Hybrid / Rerank / Conversation 修复回归：`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`npm run lint` PASS；`npm run build` PASS。
- 2026-06-26 收口验证：`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS；`git diff --check` 仅有 CRLF 工作区提示；中文乱码扫描仅命中既有测试正则 / 归档历史 / AGENTS 规则文本；敏感配置扫描确认真实配置只在本地 `.env` 类文件中，未复制密钥值。
- 2026-06-26 Playwright 收口验证：本地启动 `frontend` dev server 于 `http://localhost:3007`，打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations`、`/agent` 桌面页面，以及移动端 `/`、`/conversations`，页面均可渲染；console 主要为既有 `favicon.ico` 404 和 dev Fast Refresh / RSC fallback 日志。
- 2026-06-26 tunnel 文档收口验证：已确认 `backend/README.md` 存在 `scripts/dev/start-cloud-tunnels.ps1`、`13306`、`6333` 说明；本轮只做文档 / 脚本收口，未启动 SSH tunnel，未做云 MySQL / Qdrant runtime smoke。
- 2026-06-26 交付前审查收口验证：`mvn "-Dtest=ConversationMessageServiceImplTest" test` PASS，5 tests，0 failures，0 errors；`mvn -DskipTests compile` PASS；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*,*Rerank*" test` PASS，233 tests，0 failures，0 errors；`mvn test -DskipITs` PASS，728 tests，0 failures，0 errors，1 skipped；`npm run lint` PASS；`npm run build` PASS；`git diff --check` 仅有 CRLF 工作区提示；乱码扫描仅命中 AGENTS 规则文本；脱敏敏感配置扫描确认真实密钥命中位于未跟踪 `backend/.env`，tracked 示例 / yml 为占位、默认本地值或环境变量引用。
- Agent / Tool / ToolCall / OpenAI adapter 回归：186 tests，0 failures，0 errors。
- 2026-06-12 本轮补充连接点测试后：默认离线全量 `mvn test` 已通过，结果为 707 tests，0 failures，0 errors，1 skipped；测试结束阶段出现 scheduled task 访问云端 MySQL 的本机 SSH tunnel 入口被拒日志，说明当时 tunnel / 转发端口未连通，但 Surefire 最终 BUILD SUCCESS。
- 2026-06-12 追加候选记忆后：默认离线全量 `mvn test` 已通过，结果为 702 tests，0 failures，0 errors，1 skipped。
- 2026-06-12 追加实现后：默认离线全量 `mvn test` 已通过，结果为 693 tests，0 failures，0 errors，1 skipped。
- 2026-06-12 此前修复记录：默认离线全量 `mvn test` 曾通过，结果为 683 tests，0 failures，0 errors，1 skipped。
- `DocumentChunkServiceImplTest.shouldReplaceChunksByDeletingVersionBeforeInsert` 已按当前 chunking policy 更新断言：短文本块会先合并，再按默认 `800/120` 切分，因此示例文本应保存为 1 个 chunk，并继续校验 delete-before-insert、version、chunk index、content、hash、offset、token count 和 status。
- 先前报告中的 `DocumentAgentRealProviderRuntimeHarnessTest` 与 `ManualKnowledgeBaseRagProbeTest` 在当前源码树和 git 索引中不存在；clean 前 surefire 目录残留了旧失败报告。执行 `mvn clean test` 后旧报告已清除，默认测试未运行真实 provider 或远程 Qdrant probe。
- frontend lint：PASS。
- frontend build：PASS；`/conversations` 已进入 Next.js route 输出。
- Playwright 打开 `http://localhost:3007/conversations`：PASS；未登录态页面正常渲染，console 仅有既有 `favicon.ico` 404。
- 2026-06-13 前端展示收口验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations` PASS，桌面和移动端首页 / 会话页无明显重叠或空白；console 仅观察到既有 `favicon.ico` 404。
- 2026-06-13 前端 AI 产品感精修验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations` PASS，桌面截图检查重点页面无明显重叠，移动端首页按钮和流程面板可用；console 仅有既有 `favicon.ico` 404。
- 2026-06-13 `/conversations` 核心页精修验证：`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/conversations` 桌面 / 移动端 PASS，未登录态不暴露空工作台；首页 / Dashboard / KnowledgeBase HTTP 200。构建期间重启 dev server 后，Next 静态 chunk 404 消失，未观察到新增页面错误。
- 2026-06-14 `/conversations` 聊天产品页重做验证：Gemini CLI 通过 `-p` headless 模式输出 GPT / DeepSeek 风格建议；`npm run lint` PASS，`npm run build` PASS；Playwright 打开 `/conversations` 桌面 / 移动端 PASS，未登录态为居中聊天产品入口，登录态布局烟测确认左侧会话栏、中间聊天流、底部 composer 和右侧 Context Inspector 抽屉可渲染；console 仅有既有 `favicon.ico` 404。
- 2026-06-14 前端 UI 文案成熟化验证：Gemini CLI 通过 stdin + `-p` headless 模式输出文案方向；Codex 落地并拦截过度营销表达。`npm run lint` PASS，`npm run build` PASS；前端高暴露词扫描未命中“求职 / 面试 / MVP / smoke / 生产级 / 演示”等 UI 文案残留，中文乱码扫描未命中。Playwright 打开 `/`、`/dashboard`、`/knowledge-bases`、`/conversations`、`/agent`、`/agent/tools` PASS，并检查移动端 `/`、`/conversations` 无明显溢出；console 仅有既有 `favicon.ico` 404 和一次 dev hot reload RSC fallback，页面已正常渲染。
- 2026-06-13 已按用户授权，通过当前本机 SSH tunnel 入口对云服务器 Docker MySQL 执行 `backend/src/main/resources/sql/007_init_conversation_context.sql`；已确认 `tb_conversation`、`tb_conversation_message`、`tb_conversation_summary`、`tb_context_trace`、`tb_user_memory` 五张表存在。
- 2026-06-13 迁移后 runtime smoke：本机 SSH tunnel 入口到云服务器 Docker MySQL / Qdrant 检查 PASS，backend `/actuator/health` 为 `UP`，frontend `/conversations` 为 HTTP 200；登录态完成创建会话、发送消息、查看 trace、刷新摘要、提取候选记忆、接受候选记忆、再次发送消息验证 ACTIVE 记忆进入 Agent Memory 上下文。第二轮 trace 显示 `Memory=1`、`summaryUsed=是`、最近消息 `2` 条 / `1` 轮、无截断、无 fallback、未跳过模型。
- 2026-06-13 T013 KnowledgeBase-bound evidence runtime smoke：新建临时用户、上传 txt、创建文档、触发解析并等待 `SUCCESS`，创建 KnowledgeBase 并添加文档；KnowledgeBase retrieval 命中 1 条 evidence。随后创建绑定该 KB 的 Agent Memory 会话并发送知识库问题，API trace 显示 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=1`、`documentHitCounts={93:1}`、citation `1`、无 fallback、未跳过模型。
- 2026-06-13 T013 浏览器端到端验证：在 `/conversations` 页面使用绑定 KnowledgeBase `#8` 的会话发送中文“根据知识库”问题，助手回答引用 `t013-ui-kb-0613093939.txt`，Context Trace 显示 `Evidence=1`、`RAG 触发=是`、`RAG 必需=是`、`No Evidence=否`、`Fallback=否`、`模型跳过=否`，展开命中文档分布显示 `#94: 1`。

## T013 当前边界

- 本轮仅小范围修正文档入口、配置示例和本地清理脚本；未调用真实外部服务、未操作远程服务器、未读取或提交 `.env` / secrets / API key。
- `.claude/` 和 `test-hybrid-rag.sh` 已按 local-only 处理并加入 `.gitignore`；未删除本地文件，未执行 `git add` / `git commit` / `git push`。
- 不做后台自动摘要生成、不做真实模型记忆抽取、不持久化完整 prompt / evidence 原文、不接管现有 Agent 主链路、不新增 KnowledgeBase Agent Tool。
- 新 API 与前端工作台先提供非流式 MVP；SSE、Agent 主链路集成和真实模型记忆抽取留到后续阶段。
- 登录态 runtime smoke 已覆盖 Conversation API、summary、trace、candidate memory -> ACTIVE memory -> Agent Memory 上下文选择，以及绑定 KnowledgeBase 后 evidence 进入 Context Trace 的浏览器端到端验证。

## 建议提交切片

- `feat(conversation)`: `ai.context`、`conversation`、`memory` 后端包，`007_init_conversation_context.sql`，对应 controller / service / mapper / schema / unit tests，前端 `/conversations`、`conversation-api.ts`、`memory-api.ts`。
- `feat(rag)`: KnowledgeBase Hybrid / Rerank 增强、BM25 / RRF / rerank 包、retrieval response 观测字段、RAG 配置示例、RAG / KnowledgeBase 相关测试和 `RAG_HYBRID_*` 参考文档。
- `feat(frontend)`: 首页、Dashboard、KnowledgeBase、Agent、登录、上传、文档页等产品化展示和全局样式改动；注意继续保持页面文案不直接使用“求职 / 面试 / MVP / smoke / 生产级”等内部口径。
- `docs(workflow)`: `AGENTS.md`、`backend/README.md`、`docs/ai-dev/CONSTRAINTS.md`、`scripts/dev/start-cloud-tunnels.ps1`、`scripts/dev/cleanup-agent-processes.ps1`，聚焦 tunnel / Gemini / agent 协作和清理规则。
- `docs(showcase)`: 根 `README.md`、`docs/showcase/*`、`STATE.md`、`CURRENT_TASK.md`、`PROGRESS_LOG.md`，用于对外展示口径和当前事实源收口。

## 当前交付状态

- 已按切片完成本地提交：`feat(conversation): add context memory workspace`、`feat(rag): add hybrid retrieval and rerank controls`、`feat(frontend): polish AI workspace presentation`、`docs(workflow): document cloud tunnel workflow`。
- 最终 `docs(showcase)` 切片包含根 `README.md`、`docs/README.md`、`STATE.md`、`CURRENT_TASK.md`、`PROGRESS_LOG.md`、`docs/showcase/*` 和 T013 设计参考资料，用于对外展示口径和当前事实源收口。
- 当前交付整理验证通过：staged diff whitespace check、全仓 diff check、敏感配置扫描、中文乱码扫描、后端 compile、后端重点测试、后端全量单测、前端 lint / build 均通过。
- 本轮仍未做云 MySQL / Qdrant runtime smoke；全量后端测试中的 scheduled outbox tunnel 连接失败日志只说明未连 runtime 环境，当前云链路仍以既有 smoke 文档为准。

## 已提交切片归属

- `feat(rag)` 文件范围：`backend/src/main/java/com/docpilot/backend/ai/rag/**` 中 Hybrid / BM25 / RRF / Rerank 相关新增和响应字段改动，`KnowledgeBaseRagQaServiceImpl` / `KnowledgeBaseRagRetrievalServiceImpl`，`KnowledgeBaseRag*Response`，`application.yml` 中 retrieval / rerank 配置，`backend/.env*.example` 安全占位，RAG / KnowledgeBase 相关测试，以及 `docs/ai-dev/RAG_HYBRID_*`。
- `feat(frontend)` 文件范围：`frontend/app/{page,dashboard,knowledge-bases,agent,agent/tools,documents,login,upload,layout}.tsx`、`frontend/app/globals.css`、`frontend/lib/knowledge-base-api.ts`；`globals.css` 同时支撑 `/conversations` 视觉，第一包单独提交后会话页可编译但完整样式依赖本切片。
- `docs(workflow)` 文件范围：`.gitignore`、`AGENTS.md`、`backend/README.md`、`docs/ai-dev/CONSTRAINTS.md`、`scripts/dev/start-cloud-tunnels.ps1`、`scripts/dev/cleanup-agent-processes.ps1`。
- `docs(showcase)` 文件范围：根 `README.md`、`docs/README.md`、`docs/ai-dev/STATE.md`、`docs/ai-dev/CURRENT_TASK.md`、`docs/ai-dev/PROGRESS_LOG.md`、`docs/showcase/*`，以及 `docs/ai-dev/会话级上下文管理/` / `docs/ai-dev/上下文会话系统设计路线.md` 作为 T013 设计参考资料。
- 跨切片注意：`application.yml` 同时包含 `.env` import 上移和 RAG retrieval / rerank 配置，已随 `feat(rag)` 提交；workflow 文档只解释行为，不重复实现配置。

## 设计文档归属

- `docs/ai-dev/会话级上下文管理/` 和 `docs/ai-dev/上下文会话系统设计路线.md` 当前应作为 T013 设计参考资料保留，适合随 `feat(conversation)` 或单独 `docs(conversation)` 提交。
- 这些设计文档不作为当前任务源；后续 agent 仍以 `STATE.md`、`CURRENT_TASK.md`、`PROGRESS_LOG.md` 和代码 / 测试为准。
- 如后续要压缩文档体量，优先在单独任务中归档或提炼，不在当前交付收口中删除。

## 剩余真实风险

- 全仓状态需以最终 `docs(showcase)` 提交后的 `git status --short` 为准。
- 本轮未启动 SSH tunnel，未执行云 MySQL / Qdrant runtime smoke；`mvn test -DskipITs` 中 scheduled outbox job 的 tunnel 连接失败日志只能说明未连 runtime 环境，不代表云链路验证通过。
- KnowledgeBase Hybrid / Rerank 仍是默认关闭的可选增强；真实 rerank provider 尚未 smoke。
- T013 Conversation / Memory 仍是非流式 MVP，不接管现有 Agent 主链路。

## 上一任务记录：KnowledgeBase RAG 问答质量修复

## 目标

修复“总结整个资料集”类问题中，多文档知识库虽然有 4 个成员文档和 6 条 evidence，但召回几乎被单一文档垄断、chunk 过短、回答模型无法总结整个资料集的问题。

## 本轮已完成

- 后端 chunking 从“短段落直接成 chunk”改为先合并 Markdown / 文本块，再按窗口切分，默认 chunk size 调整为 `800`、overlap 调整为 `120`。
- KnowledgeBase retrieval 扩大向量候选池，对外仍保留请求 `topK`；摘要 / 资料集 / 知识库类问题优先覆盖每个文档，并限制单文档命中数。
- KnowledgeBase retrieval response 新增 `documentHitCounts`，用于观察每个文档的最终命中数量。
- KnowledgeBase QA response 新增 `answerProvider`、`answerModel`、`modelCallCount`，用于确认是否真实调用回答模型。
- KnowledgeBase summary prompt 增加“整体总结 + 按文档标题总结 + 缺失文档证据需说明”的提示。
- RAG vector store 配置兼容 `RAG_VECTOR_PROVIDER` / `RAG_VECTOR_DIMENSION` 别名；未把误用的 `RAG_VECTOR_COLLECTION=http://...` 当 endpoint。
- `.env` 导入职责已从 `application-local.yml` 上移到 `application.yml`，并保留 `SPRING_CONFIG_IMPORT` 覆盖能力；`application-local.yml` 只保留 local profile 的端口、目录和中间件默认值覆盖。
- 前端 KnowledgeBase API 类型已同步新增 response 字段。
- 已按用户授权对目标 KnowledgeBase 文档 `83/84/85/86` 执行 rebuild / reindex：先写入临时验证 collection `docpilot_kb_quality_20260606`，随后将本地 `backend/.env` 切到稳定 collection `docpilot_rag_v2` 并完成重建；KnowledgeBase id 为 `3`，userId 为 `21`。

## 已验证

```powershell
cd backend
mvn "-Dtest=ChunkingServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagPromptBuilderTest,RagVectorStorePropertiesTest,KnowledgeBaseRagControllerTest" test
mvn -DskipTests compile
mvn "-Dtest=*Rag*" test

cd frontend
npm run lint
```

授权后的运行时 reindex 验证：

```powershell
cd backend
mvn "-Dtest=ManualKnowledgeBaseRagReindexTest" "-Dspring.profiles.active=local" test
```

配置整理验证：

```powershell
cd backend
mvn "-Dtest=RagVectorStorePropertiesTest" test
mvn "-Dtest=DocPilotApplicationTests" test
mvn -DskipTests compile
```

验证结果：

- targeted backend tests：36 tests，0 failures，0 errors。
- backend `*Rag*` tests：164 tests，0 failures，0 errors。
- backend compile：PASS。
- frontend lint：PASS。
- config import tests：`RagVectorStorePropertiesTest` 9/9 pass，`DocPilotApplicationTests` 1/1 pass。
- runtime reindex：document `83/84/85/86` rebuild 成功，稳定 collection 为 `docpilot_rag_v2`，chunk / vector 数分别为 `35/35`、`18/18`、`10/10`、`16/16`；“总结资料集”检索 hit 数为 `6`，`documentHitCounts={83:2,84:1,85:1,86:2}`。

## 当前边界

- 本轮没有操作远程 / 云端 MySQL、Qdrant 或服务进程。
- 已通过 Spring service 正式执行 rebuild / reindex，没有直接手写 SQL 或直接改 Qdrant payload。
- 当前本地 `backend/.env` 已配置为 `RAG_VECTOR_STORE_PROVIDER=qdrant`、`RAG_QDRANT_COLLECTION=docpilot_rag_v2`、`RAG_QDRANT_DIMENSION=1024`，并继续使用本机 `.env` 中的真实 endpoint / key；真实 `.env` 不提交。
- 如果当前环境仍使用 mock / fake embedding，语义召回质量仍会受限；本轮代码只让 provider/model/call count 更可观测。

## 下一步候选

- 前端展示 `documentHitCounts`、`answerProvider`、`answerModel`、`modelCallCount`，便于演示时解释检索和模型调用。
- 为 KnowledgeBase QA 补 SSE 流式路径，并保持与非流式 response 字段一致。

## 2026-06-08 环境恢复插曲（DONE）

- 目标：让本地后端通过 SSH tunnel 连接云端 MySQL / Qdrant，并恢复 `/actuator/health`。
- 已确认：`backend/.env` 目标配置为 `MYSQL_HOST=127.0.0.1`、`MYSQL_PORT=13306`、`RAG_QDRANT_ENDPOINT=http://127.0.0.1:6333`。
- 已验证：通过本地 `13306` tunnel 使用 `docpilot_app` 登录 `docpilot` 仍失败，错误来源为 `docpilot_app@172.20.0.1`，说明 Spring 配置解析生效，但远程 MySQL 用户认证 / host 授权仍不匹配。
- 已由 `hk-ops` 确认远程 MySQL 数据目录备份有效：`/data/docpilot/backups/mysql-datadir-20260607-010918.tar`，基础 tar 完整性校验通过。
- 已由 `hk-ops` 修复 `docpilot_app` 认证 / 授权，保留 `docpilot_app` 对 `docpilot` schema 的访问能力；未修改业务表结构或业务数据。
- 已由 `hk-ops` 将 Docker MySQL host 端口从公网监听收口为远程本机 `127.0.0.1:13306` 监听，`docpilot-mysql` 仍为 healthy。
- 本地已恢复 SSH tunnel：`127.0.0.1:13306` 连接远程 MySQL，`127.0.0.1:6333` 连接远程 Qdrant；MySQL CLI `SELECT 1` 成功，Qdrant `/collections` 可达。
- 后端已用 local profile 启动，HikariPool 初始化成功，未再出现 MySQL `Access denied` 或 Hikari timeout；`GET http://localhost:8081/actuator/health` 返回 `UP`。
- 已完成最小业务 smoke：注册临时用户、上传 txt、创建文档、创建解析任务、解析达到 `SUCCESS`、RAG retrieve 命中、RAG QA 返回 citation 且回答包含本次 smoke marker；记录 ID 为 user `88`、file `89`、document `87`、parseTask `83`。
- 已新增 `scripts/dev/start-cloud-tunnels.ps1` 固化本地 MySQL / Qdrant tunnel 启动与连通性检查；`backend/README.md` 已同步说明云 MySQL / Qdrant 不再走公网直连。
- 已定位并修复前端多文档问答报 `knowledge base RAG answer generation failed`：复现确认 KnowledgeBase retrieve 成功但真实回答模型在约 12 秒 read timeout 后失败；后端已为 KnowledgeBase QA 增加 answer 生成失败兜底，保留 retrieval / citations 返回，并将本机 `backend/.env` 的 `AI_REAL_READ_TIMEOUT_MS` 调整为 `30000`。复验 KnowledgeBase QA code `0`、citation `2`、modelCallCount `1`，记录 ID 为 user `90`、KB `5`、documents `90/91`。
