# DocPilot 当前状态

## 2026-07-14 README Quality Console 展示口径（VERIFIED / UI）

- 根 README 的 Quality Console 图已从“内部排障混合数据概览”修正为“PASS 核心样本详情 + 核心门禁”视角，避免公开首屏直接显示全量最近 runs 的低通过率 / 失败率而被误读为系统整体质量差。
- 新展示样本使用真实 DB-backed run `docpilot-cloud-quality-20260712212603-173e7d`：状态 `PASS`、质量门禁 `20`、失败 / 复查 `0 / 0`。截图分别保存为 `docs/assets/screenshots/readme/readme-quality-console-overview.png` 和 `docs/assets/screenshots/readme/readme-quality-console-gates.png`。
- 本轮仍保留内部排障事实：最近 `50` 条 QualityRun 中 PASS `27`、REVIEW `17`、FAILED_CORE_FLOW `6`，后续需要按来源拆分预期失败、环境阻塞、真实质量缺口和 artifact 字段缺失；不能通过删除失败历史或伪造通过率解决。
- 真实验证：临时后端 `18081` + 前端 `3007`，临时内部管理员 userId `772`；Quality status enabled / authorized，runCount `55`；Playwright 截图 console error `0`。

## 2026-07-14 GitHub PR #4 发布分支 CI 状态（VERIFIED / LOCAL）

- PR #4 的本地 CI 等价验证已恢复：后端全量测试 `1031` tests PASS（`5` skipped），前端 lint / production build PASS，前端 Playwright E2E `14` tests PASS。
- 后端 CI 失败根因是 Memory 质量评测测试夹具未跟上 `MemorySelector` 的 `markUsed` 注入语义；已在 `MemoryQualityEvalRunner` eval harness 中补齐 mock。
- 前端 CI 失败根因是文档 RAG stream E2E fixture 未覆盖当前页面真实请求的 parse status 与 Quality Console status；已补齐 mock，避免未 mock 404 造成 console error 失败。
- GitHub Ubuntu runner 追加暴露了 PowerShell `ConvertTo-Json` 跨平台空格差异：本机 Windows 输出字段冒号后两个空格，Ubuntu 输出一个空格。相关 smoke safety tests 已改为字段语义正则断言，不再依赖 JSON pretty-print 空格。
- 当前边界：这是测试夹具 / CI 收口修复，不改变用户侧业务路由、RAG、Memory 或 Quality Console 权限语义；远端 GitHub Actions 结果以 push 后 PR checks 为准。

## 2026-07-14 Document Parser 长文档 batch split 与原失败任务状态（VERIFIED / CORE）

- `REA-20260713-P1-001` 已完成核心恢复复验：百炼 `text-embedding-v4` 单批上限导致的长文档 `RAG_INDEX_FAILED` 已由 `OpenAICompatibleEmbeddingProvider` 分批请求、ParseTask 结构化错误摘要和真实长文档 canary 覆盖。
- `document-parser-real-chain-smoke.ps1` 现在包含 `LONG_MD` fixture，真实 run 中生成约 `17755` 字 Markdown 并切出 `25` 个 chunks，超过 provider 单批 `10` 条上限；脚本同时验证 MySQL `INDEXED` chunk、`vectorId`、Qdrant filtered point、payload 摘要和 locator payload 的一致性。
- 最新真实 marker `docpilot-parser-real-chain-20260714184055-21d3de`：overall `REVIEW` 仅因为 `-SkipFrontend`；核心 `parserRealChain=PASS`，PDF / HTML / DOCX / LONG_MD 均 parse、direct retrieve、QA retrieval、citation、source locator 通过；总计 chunk / indexed / vectorId / Qdrant point 为 `32 / 32 / 32 / 32`，parser boundary `4/4` 和 artifact redaction PASS。
- 原用户失败链路已恢复：document `1431` 为 `ACTIVE / SUCCESS`，task `1322` 为 `SUCCESS`、`retry_count=2`、无错误摘要；MySQL chunk / indexed / vectorId 为 `12 / 12 / 12`，Qdrant filtered point 为 `12`，最新 outbox `SENT`、consume record `SUCCESS`。
- 已验证：脚本 plan / dry-run PASS，`DocumentParserRealChainSmokeScriptSafetyTest` PASS，后端定向 `38` tests PASS。边界：本片未冒充 zeus owner 调用原文档 QA，也未做浏览器 UI；原文档引用能力以 DB / Qdrant parity 证明恢复，retrieve / citation / locator 由同环境 LONG_MD canary 证明。

## 2026-07-14 Agent Memory 单条停用 / 恢复状态（VERIFIED / API+UI）

- `REA-20260713-P2-033` 已收口：长期记忆现在支持单条停用与恢复，后端复用既有 `ARCHIVED` 状态表示“停用 / 暂不注入上下文但保留可恢复”，未新增数据库迁移或 `DISABLED` 枚举。
- Memory API 新增 `GET /api/memories/disabled`、`POST /api/memories/{memoryId}/disable` 和 `POST /api/memories/{memoryId}/restore`。`disable` 为 `ACTIVE -> ARCHIVED`，重复停用幂等；`restore` 为 `ARCHIVED -> ACTIVE`，重复恢复 ACTIVE 幂等；`DELETED / SUGGESTED / IGNORED` 不可恢复为 ACTIVE。
- 恢复前会重新执行 `MemorySafetyValidator` 和重复 / 冲突治理；跨用户访问仍通过 `selectByIdAndUserId` 隔离。会改变 ACTIVE memory 集合的 `create / acceptSuggestion / resolveSuggestion / update / disable / restore` 路径已放入按 `userId + memoryType` 维度的 Redisson governance lock，锁内重新读取记录并执行治理检查，降低并发恢复 / 接受 / 创建绕过治理的风险。
- `UserMemoryMapper.markUsed` 只允许 `ACTIVE` 行更新，`MemorySelector` 只有在 `markUsed` 成功后才注入 `ContextItem`，避免停用竞态导致已停用 memory 进入上下文。
- Conversation Memory 抽屉现在展示生效 / 停用 / 候选 KPI，生效记忆支持“停用”，停用记忆在“已停用的长期记忆”分区中支持“恢复 / 删除”；恢复提示说明会重新检查冲突和敏感内容。停用列表加载失败时降级为空列表，不阻塞 Conversation 页面和其它 Memory 数据。
- 验证：后端定向 `67` tests PASS；PowerShell parser 对 cloud / memory smoke 均 PASS；`memory-quality-smoke.ps1 -Mode plan` PASS；前端 lint / build PASS。真实 memory smoke `docpilot-memory-quality-20260714175619-8f1939` 中 `memoryQuality=PASS`、`t31StrictMemoryDisableCapability=IMPLEMENTED`；真实 UI marker `memory-ui-disable-restore-20260714100303` 停用 / 恢复 PASS，console error `0`，桌面 / `390px` / `320px` 横向溢出均为 `0`。
- 边界：本次解决的是用户可控的单条长期记忆停用 / 恢复，不新增 Memory 版本历史、审计表、全局记忆开关、弱网 / 多标签冲突处理或真实模型大样本长期记忆质量 benchmark。

## 2026-07-14 Agent Quality Console 持久化内部控制台状态（VERIFIED / DB+API+UI / CLOSEOUT）

- Agent Quality Console 已从 artifact-backed 开发页升级为 DB-backed 内部质量控制台：默认仍由 `APP_QUALITY_CONSOLE_ENABLED=false` 关闭，开启后还必须满足当前登录用户 `status=ACTIVE` 且 `tb_user.is_internal_admin=1`。
- 已在协作约束中写入“受控开发库数据库常驻授权”：当前 DocPilot 开发库允许只读诊断、幂等迁移、精确 `UPDATE`、临时 smoke 数据和 QualityRun 导入；破坏性数据库操作、远程 Docker / 云资源变更、非 DocPilot schema、清空 collection 和 push 仍需单独确认。
- 真实开发库已执行 `011_init_quality_console_persistence.sql`，并二次执行验证幂等；确认 `tb_user.is_internal_admin`、`tb_quality_run`、`tb_quality_run_gate`、`tb_quality_run_case`、`tb_quality_import_event`、关键唯一索引均存在。
- `username='zeus'` 已在唯一且 ACTIVE 的前提下设置 `is_internal_admin=1`；未读取或重置 zeus 密码。自动化验证使用临时 smoke 管理员账号完成，临时 token / log 已从 ignored 运行目录删除。
- DB-backed 查询链路已验证：真实运行 `agent-quality-eval-smoke.ps1 -Mode run` 得到 marker `docpilot-agent-quality-eval-20260714151238-756d91`，导入后成为最新持久化 run，`status=PASS`、`dataSource=artifact_import`、`gateCount=1`、`evalCaseCount=19`。
- P3 导入污染已收尾：测试 artifact 改用 `@TempDir`，runtime import 在 `limit` 截断前过滤 `docpilot-import-*` 保留测试 marker；DB-backed runs/detail/trends 隐藏历史误导入测试 marker，不做破坏性删除。真实 API marker `quality-console-closeout-20260714160116` 验证 `limit=1` 不再被测试 marker 抢占，`limit=50` 导入后 `failed=0`。
- DB-backed 领域趋势已恢复：artifact-backed 与 DB-backed 查询共用 `QualityDomainTrendAssembler`；真实 `/api/quality/trends?limit=50` 返回 `domainTrends.memoryQuality.runCount=4`、`domainTrends.ragRepresentativeEval.runCount=12`。
- 权限和 UI 已验证：未登录 `/api/quality/runs` 业务 `401`，普通 ACTIVE 用户业务 `403`，内部管理员业务 `0`；管理员 `/quality?autoload=1` 显示最新 run、数据来源和导入信息，点击“趋势”tab 后可见 Memory / RAG representative 领域趋势卡；桌面与 `390px` 移动端无横向溢出，console error 为 `0`。
- 已验证：后端 `mvn "-Dtest=*Quality*,DemoMysqlBootstrapSchemaTest" test` PASS（69 tests / 1 skipped）；前端 lint PASS；前端 build 在 `NODE_OPTIONS=--max-old-space-size=4096` 下 PASS；临时 18081 / 3007 和常用 dev 端口已释放。边界：并发导入行锁、批量查询性能和 DB JSON 损坏观测仍是后续增强项，不影响当前求职级收口。

## 2026-07-14 Agent Quality Console disabled-state 状态（VERIFIED / UI+API）

- 当前 `/quality` 页面误显示“运行次数 0 / 暂无样本 / 当前账号无权限”的根因已确认并修复：后端返回 `code=403` / `quality console is disabled` 时，前端不再泛化成账号无权限，也不再把加载失败解释成“没有生成 artifact”。
- Quality Console 仍保持内部开关：默认 `app.quality.console.enabled=false`；未登录访问 `/api/quality/**` 仍返回 401，已登录但未开启 console flag 仍返回 403。只有本地内部验证环境显式开启 `APP_QUALITY_CONSOLE_ENABLED=true` 后，才允许读取本机 ignored artifact 聚合结果。
- 当前质量运行数据链路仍是 artifact-backed，不是数据库持久化 QualityRun：`QualityArtifactServiceImpl` 扫描 `backend/target/**` 与 `tmp-e2e/**` 中的脱敏 artifact，本地仓库已有约 92 个可识别 run 文件；本轮未新增 QualityRun 表、未新增 owner 过滤，也未改变权限模型。
- 前端修复点：`frontend/lib/api.ts` 增加 `quality console is disabled` 专用文案；`frontend/app/quality/page.tsx` 增加加载错误类型，disabled / forbidden / empty artifact 分开展示，并在刷新 runs 后自动修正过期 `selectedMarker`。
- 已验证：前端 lint PASS；前端 build 在 `NODE_OPTIONS=--max-old-space-size=4096` 下 PASS；Quality 后端聚合相关 22 tests PASS；临时 3007 指向当前 8081 验证 disabled 文案正确；临时 18081（`APP_QUALITY_CONSOLE_ENABLED=true`）+ 3008 验证 runs=20、trend points=20、eval cases=19、detail 可读且 diagnostics 存在。边界：生产 build 会影响正在运行的旧 3000 dev 进程 `_next/static`，该已有进程需要手动重启恢复。

## 2026-07-13 Conversation Context Inspector 状态（VERIFIED / UI+API）

- Conversation 页右侧 Context Inspector 已升级为单一入口、双层结构：顶部按钮与回答内“上下文溯源”复用同一面板，不新增重复入口；顶部按钮会打开当前已选 trace 或最近一条 assistant 回答，回答内按钮绑定对应 `messageId`。
- Inspector 默认展示产品化摘要层，继续突出本次回答来源、grounding policy、route decision、召回证据、实际引用、命中文档、Memory、Summary、fallback 和模型跳过状态；技术详情层需用户主动切换。
- 新增 `ContextTrace.technicalDetails` / `tb_context_trace.technical_details_json`：记录 `traceId=ctx-{conversationId}-{messageId}`、messageId、路由原因、阶段耗时、retrieval score rows、evidence gate、token 分配和 dropped reasons、Memory/Summary 使用、fallback/safeError。`010_add_context_trace_technical_details.sql` 已在真实 MySQL tunnel 链路执行，列从缺失变为存在。
- 技术详情安全边界已通过测试固化：不输出完整 prompt、assembled context、`ContextItem.content`、citation `quoteText/snippet`、证据全文、密钥、连接串、provider 原始错误体或 stack trace；历史 trace 缺少技术详情时标记 `available=false`。
- 已验证：后端编译 PASS；后端定向 48 tests PASS；后端全量 `mvn test -DskipITs` PASS（999 tests / 5 skipped）；前端 `npm run lint` PASS、`npm run build` PASS；临时新版后端 `18081` + 前端 `3007` 的 Playwright 验证通过，marker `docpilot-context-inspector-ui-20260714003520`，覆盖顶部入口、回答内入口、`摘要 / 技术详情` 两层、assistant `#561` 严格模式无证据 gate 和 assistant `#559` 普通模型链路，`1021px` 打开态无横向溢出。边界：未替换用户已有 `8081` 进程，正向 evidence score row 未做浏览器视觉验证。

## 2026-07-13 Conversation 引用来源展示状态（VERIFIED / UI）

- Conversation 回答卡片已从“横向铺满所有返回 citations”改成“摘要指标 + 默认实际引用 + 可展开完整返回证据”的结构，减少把检索召回结果误称为实际引用的混淆。
- 页面现在区分 `实际引用`、`召回证据`、`命中文档`：正文出现 `[n]` 的 citation 计入实际引用；`contextTrace.evidenceCount` 表示召回证据；`documentHitCounts` 过滤正命中文档，缺 trace 的历史消息从 citations 自身 fallback 统计命中文档。
- 正文 citation marker 会渲染为内部锚点样式，点击 `[n]` 可聚焦并高亮对应证据卡片；来源卡片展示文档标题、章节 / locator、chunk、quote/snippet 和次要 score，不再把文件名、编号、相似度压成一条横向 pill。
- Trace Inspector 文案同步调整为“召回证据 / 实际引用 / 命中文档”，和回答卡片的来源口径保持一致。
- 已验证：前端 `npm run lint` PASS、`npm run build` PASS；2026-07-14 临时后端 `18081` + 前端 `3007` 的 Playwright 复验通过，marker `conversation-citation-expand-20260714172419`，Conversation `261`，assistant `#567`。
- 复验结果：API 前置为 `STRICT_KB_EVIDENCE`、`evidenceCount=3`、`citationCount=3`、历史 list citation `3`；回答正文实际只引用 `[1]` / `[2]`。页面默认显示 `2` 实际引用、`3` 召回证据、`3` 命中文档和 2 张实际引用卡；展开后显示 3 张完整返回证据卡；点击正文 `[1]` 聚焦并高亮 `citation-567-1`；桌面 / `390px` / `320px` 横向溢出均为 `0`，console error 为 `0`。
- 边界：本轮验证的是 Conversation 回答卡片 citation UI，不覆盖 KnowledgeBase 页、文档详情页或 Agent 页的新视觉改造。

## 2026-07-13 Conversation citation 持久化与 hitCounts 去零状态（VERIFIED / API）

- 已修复 Conversation 历史消息刷新后 citation cards 丢失的代码根因：`send()` 返回即时 citations，`list()` 现在从 `tb_context_trace.citations_json` 快照恢复顶层 `ConversationMessageResponse.citations`；旧行 `citations_json=NULL` 兼容为空，不回填旧回答。
- 新增 `009_add_context_trace_citations.sql`，并同步 `007_init_conversation_context.sql` 与 demo fresh-volume 初始化脚本。`ContextTrace` 内部可携带 citations 供服务组装，但 JSON API 不暴露该字段，避免 Trace 摘要边界退化成 evidence 文本载体。
- 已收敛 `documentHitCounts` 语义：后端只输出正命中文档；Conversation / KnowledgeBase 前端防御性过滤 `<=0`；Quality Console 的 zero-hit 文档数不再从旧 0 值推断，降级为未知。
- 已验证：后端定向 72 tests PASS，`*Rag*,*KnowledgeBase*,*Conversation*` 回归 300 tests PASS（1 skipped），后端全量 `mvn test -DskipITs` PASS（996 tests / 0 failures / 5 skipped），前端 lint / build PASS，conversation grounding runner plan / dry-run PASS。
- 真实迁移与 runtime 证据：`009_add_context_trace_citations.sql` 已在真实 MySQL tunnel 链路执行，`citations_json` 从缺失变为存在；临时新版后端 `18081` 的 `docpilot-conversation-grounding-20260713223647-cc009f` 9/9 PASS；临时新版后端 `18082` 的 `docpilot-citation-list-20260713224003-82668e` 验证 send/list citation 数量一致、citation 签名一致、hitCounts 无零值、Trace API 不暴露 citations。
- 边界：这轮没有杀掉或替换用户已有 `8081` 后端进程，也没有跑浏览器 Playwright 页面刷新；当前手动 UI 要看到新版行为，需要重启现有后端。

## 2026-07-13 Conversation AUTO_RAG 路由状态（VERIFIED）

- 已修复 zeus / `运维知识库演示` 中 P1 SLA 问题未走 RAG 的通用根因：旧 AUTO_RAG 依赖少量正向 intent 关键词，业务制度类问题如 “P1 故障要求在多长时间内响应和恢复？” 未命中时会直接走底层模型。
- 当前 `AUTO_RAG + bound KnowledgeBase` 采用极窄 negative gate：问候、感谢、助手身份 / 能力问题直接模型；其它实质问题先执行 KnowledgeBase retrieval probe，命中 evidence 后进入 `AUTO_RAG_EVIDENCE` 并返回 citations。
- 当前 no-evidence 语义：非显式资料请求无 evidence 时 `AUTO_NO_EVIDENCE_MODEL`，模型继续回答；显式要求“根据 / 基于 / 引用文档或知识库”但无 evidence 时 `AUTO_REQUIRED_NO_EVIDENCE_FALLBACK`，`ragRequired=true`、`llmCalled=false`、`modelSkipped=true`；`STRICT_KB` 仍始终要求 evidence。
- Conversation grounding runner 已更新为 9 个真实 case：`no-kb-model-only`、`no-kb-strict-normalized`、`T27/T28 recent turns`、`auto-smalltalk-no-rag`、`auto-no-evidence-fallback-model`、`auto-required-no-evidence-refusal`、`strict-no-evidence-refusal`、`auto-rag-evidence-citations`。
- 最新真实 marker：`docpilot-conversation-grounding-20260713212058-5915ed`，命令为临时 18081 后端 + `conversation-grounding-smoke.ps1 -Mode run -SkipFrontend -ReuseRunningServices`；9/9 case PASS，artifact 只保存路由枚举、布尔、计数和脱敏 id，18081 已清理。
- 边界：历史 zeus 会话中的旧 assistant 消息不会自动重写；需要新版后端重启后重新提问才能在 UI 看到修复效果。当前 retrieval 异常仍显式失败，不静默伪装成 no-evidence。

## 2026-07-13 本地前后端启动报错诊断状态（BLOCKED / ENV）

- 用户已有本地 frontend `3000` 与 backend `8081` 进程在监听；公开前端路由 `/`、`/login`、`/dashboard` 可返回 200，后端未鉴权接口可返回业务 401，说明前后端进程本身未崩溃。
- 当前阻塞点是运行环境依赖：本地 MySQL / Qdrant tunnel 端口 `13306` / `6333` 不可达，而 `backend/.env` 的脱敏形状显示 MySQL 与 Qdrant 均按 localhost tunnel 方式配置。
- 运行时证据：后端 `/actuator/health` 通过直连与前端 `/backend/actuator/health` 代理均超时；登录接口直连与前端代理均超时；JVM thread dump 显示 Tomcat 请求线程等待 Hikari 获取 MySQL 连接。
- 结论：这轮不是前端 Next.js 进程崩溃，也不是端口未启动；主要是后端启动时未先建立云 MySQL / Qdrant 本地 tunnel，导致需要数据库的 API 卡住，并连带前端业务请求报错。
- 未执行修复启动：本轮只做只读诊断与脱敏记录，未启动 tunnel、未重启用户已有 backend / frontend、未创建业务数据。

## 2026-07-13 Agent Memory T31 删除 / 会话禁用自动化验收状态（SUPERSEDED / HISTORICAL）

- Memory quality smoke 已纳入高强度验收 T31 的两个可自动化子项：`RECENT_TURNS` contextMode 会话级禁用长期记忆，以及 ACTIVE memory soft delete 后不再被新的 `AGENT_MEMORY` Trace 选入。
- 最新真实 marker：`docpilot-memory-quality-20260713015241-320bed`，命令为 `scripts/smoke/memory-quality-smoke.ps1 -Mode run -SkipFrontend`；T31 case `passed=true`，目标 memory 创建后 `AGENT_MEMORY` Trace `memoryCount=1` 且 `use_count` 增加 1，`RECENT_TURNS` Trace `memoryCount=0` 且 `use_count` 不变，delete 后 DB 精确状态为 `DELETED`，新 `AGENT_MEMORY` Trace `memoryCount=0` 且 `use_count` 不再增加。
- 同轮 T29 / T30 继续 PASS：候选记忆仍需用户确认，敏感 `api key` / `sk-...` 形状仍不产生候选或持久行；artifact redaction PASS，常用端口无 LISTEN 残留。
- 历史边界：该 2026-07-13 run 当时只证明 `RECENT_TURNS` 会话级禁用和 delete lifecycle，并据此登记 `REA-20260713-P2-033`。严格 per-memory 停用 / 恢复已由 2026-07-14 新增 API、smoke 和浏览器验证收口；本历史结果不代表 T32 长会话摘要、Agent ToolCall 或弱网 / 多标签 UI 已通过。

## 2026-07-13 Agent Memory T29/T30 自动化验收状态（VERIFIED / PARTIAL）

- Memory quality smoke 已纳入高强度验收 T29 / T30：T29 验证候选记忆必须经用户确认后才从 `SUGGESTED` 变为 `ACTIVE`，且 accept 后可在 `AGENT_MEMORY` Trace 中以 `PREFERENCE` memory 类型被观测；T30 验证带凭据形状的偏好文本不会生成候选、不会进入 ACTIVE / SUGGESTED list，`tb_user_memory` 按 source conversation 计数为 0。
- 后端 Memory 安全边界同步补强：`api key` / `api-key` / `sk-...` 形状由 `MemorySafetyValidator` 拦截；规则式抽取先识别“优先考虑 / prefer / do not”等强偏好信号，避免中文项目偏好被“回答”关键词误归为回答风格。
- 最新真实 marker：`docpilot-memory-quality-20260713013642-34b1f5`，命令为 `scripts/smoke/memory-quality-smoke.ps1 -Mode run -SkipFrontend`；`memoryQuality` gate PASS，artifact redaction PASS，常用端口无 LISTEN 残留。
- 边界：本片只证明 T29/T30 的 API / Trace / DB gate 已通过；T31 删除 / 会话模式禁用已在后续 memory marker 中补充，严格 per-memory 停用 / 恢复已在 2026-07-14 单独收口。T32 长会话摘要、Agent ToolCall 和弱网 / 多标签 UI 仍待执行。

## 2026-07-13 Conversation 最近轮次 T27/T28 自动化验收状态（VERIFIED / PARTIAL）

- `conversation-grounding-smoke.ps1` 已纳入高强度验收 T27 / T28：`RECENT_TURNS` 同会话可使用最近轮次回答项目代号“蓝桥”，新建另一会话不会继承该上下文；runner artifact 仍只记录 caseId、路由枚举、布尔值、计数和脱敏 id。
- 最新真实 run marker `docpilot-conversation-grounding-20260713010452-f8e612` 为 `PASS`，8/8 case 通过；新增 case 中 T27 trace 为 `MODEL_ONLY` / `recentMessageCount>=2` / `citationCount=0`，T28 trace 为 `MODEL_ONLY` / `recentMessageCount=0` / `citationCount=0`，均调用模型且没有知识库 evidence。
- 同轮修复 smoke 基础设施问题 `REA-20260713-P3-032`：函数内 `$MyInvocation.MyCommand.Path` 为空导致 tunnel script path 解析失败；现改为 `$PSScriptRoot` 并由脚本安全测试覆盖。
- 已验证：`plan` PASS、`dry-run` PASS、PowerShell Parser `PARSE_OK`、`ConversationGroundingSmokeScriptSafetyTest` 3 tests PASS、真实 `run -SkipFrontend` PASS；artifact redaction scan PASS，常用端口无 LISTEN 残留。
- 边界：本片只证明 T27/T28 的 API / Trace / 会话隔离自动化 gate 已通过；前端会话页面、T29/T30 之外的长期记忆生命周期、长会话摘要、Agent ToolCall、权限矩阵和弱网并发仍在高强度验收剩余项中。

## 2026-07-13 高强度 KnowledgeBase 生命周期 T26 自动化验收状态（VERIFIED / PARTIAL）

- `cloud-quality-smoke.ps1 -EnableKnowledgeBaseLifecycleGate` 已覆盖 T22-T26，并让 `high-intensity-fixed-corpus-smoke.ps1` 同时启用 fixed corpus 与 lifecycle gate；该 gate 复用固定语料已索引文档创建 `KB_LIFECYCLE_A` / `KB_LIFECYCLE_B`，另为 T26 创建 `DELETE_DISPOSABLE` 临时文档和 `KB_LIFECYCLE_DELETE`，不删除共享固定语料。
- 已覆盖 T22-T26 的 API/RAG 主干：加入 KB 后立即可查、移出 KB 后 no-evidence 且 0 citation、重新加入后恢复、同一文档加入两个 KB 后移出 KB-A 不影响 KB-B；删除 disposable 文档后 KB detail 不再列出、retrieve / QA 均 no-evidence 且 0 citation，文档详情不可读。审查后已收紧 scope 断言：非空 hit / citation 必须带可审计 `documentId`，lifecycle-only dry-run 会标记 `BLOCKED`，artifact shape 检查覆盖实际 gate checks。
- 真实 run 先暴露 `REA-20260713-P1-031`：T11 citations 覆盖正确但答案遗漏部分风险控制措施；已增强 `KnowledgeBaseRagPromptBuilder` 的 summary prompt，要求数量型总结按 requested item count 输出、跨文档综合不能跳过已检索文档，并对风险控制 / 控制措施问题抽取审批、凭据 / Token / 日志 / 审计和运维缓解措施。
- 最新真实 run marker `docpilot-high-intensity-fixed-corpus-20260713004622-113df1` 中 `fixedBusinessCorpus` 与 `knowledgeBaseLifecycle` gate 均为 `PASS`；T26 disposable 文档删除前 retrieve / QA 各 1 条，删除后 KB detail 0 个文档、retrieve / QA no-evidence 且 0 citation，文档详情返回业务错误；overallStatus 仍为 `REVIEW`，原因是本轮显式 `-SkipFrontend`。
- 已验证：wrapper `plan` PASS、delegate `dry-run` PASS；`mvn "-Dtest=KnowledgeBaseRagPromptBuilderTest,KnowledgeBaseRagQaServiceImplTest,CloudQualitySmokeScriptSafetyTest,HighIntensityFixedCorpusSmokeScriptSafetyTest,DocumentServiceImplTest" test` PASS（51 tests）；artifact raw-field scan PASS，3000 / 3001 / 3002 / 3007 / 3100 / 8081 无 LISTEN 残留。
- 边界：T26 当前验证的是软删除文档后的 KB 关系清理和 citation / retrieve 不再召回；MySQL chunk 和 Qdrant point 仍作为残留计数观测，不声明物理删除。完整 T01-T47 仍未覆盖 T32 长会话摘要、Agent ToolCall 全矩阵、弱网并发、多标签页和浏览器缩放 UI；T31 严格 per-memory 停用 / 恢复已在 2026-07-14 单独收口。

## 2026-07-12 高强度固定业务语料自动化验收修复状态（VERIFIED / PARTIAL）

- 已新增高强度固定业务语料 smoke 入口：`scripts/smoke/high-intensity-fixed-corpus-smoke.ps1`，复用 `cloud-quality-smoke.ps1 -EnableFixedBusinessCorpusGate`，覆盖 T02 串行重复上传、6 份固定 Markdown 业务语料、`KB_CORE` / `KB_NOISY` 和 T06-T15 API/RAG 质量矩阵。
- 已验证脚本入口：wrapper `plan` PASS、delegate `dry-run` PASS、Windows PowerShell `ParseFile` PASS；`HighIntensityFixedCorpusSmokeScriptSafetyTest` + `CloudQualitySmokeScriptSafetyTest` 共 6 tests PASS。`cloud-quality-smoke.ps1` 需要保持 UTF-8 BOM + CRLF，避免 Windows PowerShell 5.1 读取中文 fixture 时 mojibake。
- 已修复并验证 P1 质量问题 `REA-20260712-P1-030`：KnowledgeBase QA 的数字 citation 精炼现在只作用于非多文档问题；中文“综合 / 分别出现在什么文档”等多文档问题会保留跨文档 citation；错误前提 / 冲突规则问题使用更明确的纠错 prompt，但不硬编码固定语料业务值。
- 最新真实 run marker `docpilot-high-intensity-fixed-corpus-20260712230404-a0bc35` 的 `fixedBusinessCorpus` gate 为 `PASS`：T02 duplicate upload 与 T06-T15 全部 PASS；T08 正确处理废弃草案冲突，T11 覆盖 `INCIDENT_REVIEW` / `API_POLICY` / `CONTRACT_ALPHA` / `SLA_BETA`，T12 覆盖 `CONTRACT_ALPHA` / `API_POLICY`。
- 本次真实 run 的 overallStatus 为 `REVIEW` 是因为命令显式 `-SkipFrontend`，不是 fixed corpus gate 失败；后续已补 T22-T26 KnowledgeBase 生命周期、T29/T30 Memory gate 以及 T31 删除 / 会话级禁用 gate。T31 严格 per-memory 停用 / 恢复已在 2026-07-14 单独收口；完整 T01-T47 高强度验收中的 T32、Agent、弱网并发、多标签页和缩放 UI 仍待后续阶段执行。
- 本轮真实 run 的 JSON artifact 只保存 ignored 脱敏摘要，固定 synthetic 源文件上传后从 artifact 目录删除，最新 marker 的 `fixed-business-corpus` 目录为空；run 会在业务库 / 存储中创建临时 smoke 文档作为测试输入，但不提交 token、密码、prompt、answer、evidence context、连接串或云地址。runner cleanup 后确认 3000 / 3001 / 3002 / 3007 / 3100 / 8081 均无 LISTEN 残留。

## 2026-07-12 高强度验收执行状态（VERIFIED / PARTIAL）

- 高强度验收计划已开始执行，第一层真实链路门禁 PASS：parser real-chain marker `docpilot-parser-real-chain-20260712212339-021ca3`，Conversation grounding marker `docpilot-conversation-grounding-20260712212500-d26151`，综合 cloud quality marker `docpilot-cloud-quality-20260712212603-173e7d`。
- 已验证能力：PDF / HTML / DOCX 解析、负向解析边界、chunk / Qdrant 索引一致性、单文档 RAG、KnowledgeBase RAG、no-evidence、Conversation Trace、KnowledgeBase Agent、跨用户权限隔离、前端关键路由和前端交互 gate。
- 当前未发现 P0 / P1 / P2 问题；因此未新增 `REAL_EXPERIENCE_AUDIT_LOG.md` 问题记录。所有 artifact 均为 ignored 脱敏产物，不提交 token、密码、prompt、answer、evidence context、连接串或云地址。
- 剩余差距：固定 6 份业务语料的 T06-T15 质量矩阵、重复上传和 KB 生命周期 T22-T25 已有自动化证据；KB 删除 / 归档 T26、长会话摘要、弱网并发、多标签页和缩放 UI 检查仍待执行，不能写成完整验收通过。

## 2026-07-12 高强度验收测试计划状态（TODO RECORDED）

- `docs/ai-dev/HIGH_INTENSITY_ACCEPTANCE_TEST_PLAN.md` 已记录一套后续可执行的高强度端到端验收计划，覆盖上传解析、RAG 精确问答、冲突证据、GroundingPolicy、KnowledgeBase 生命周期、Memory、Agent ToolCall、跨用户权限、安全和弱网并发 UI。
- `docs/ai-dev/TASKS.md` 已新增该计划的待办入口；当前状态仍是待执行，不代表真实验收已通过，也不进入 `docs/showcase/DEMO_SMOKE_RECORD.md`。
- 后续执行该计划时，P0/P1/P2 问题必须写入 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`，通过后的展示级摘要再考虑同步到 showcase。

## 2026-07-12 Gemini CLI 前端协作状态（VERIFIED）

- Gemini CLI 前端体验建议已整理到 `docs/ai-dev/TASKS.md`，作为滚动 TODO 而非已完成功能；首个建议落地点是 `/knowledge-bases` 的召回片段与引用卡片双向高亮。
- Gemini CLI 调用流程已固化到 `AGENTS.md` 与 `docs/ai-dev/CONSTRAINTS.md`：前端协作仍固定使用 `gemini-3.5-flash`，正式请求优先使用脱敏 PowerShell here-string + `gemini.cmd -m gemini-3.5-flash --prompt $prompt`，不默认走 stdin + `-p`。
- 本机 Codex 用户级 skill `gemini-cli-collab` 已创建并通过结构校验，可在后续 UI / 前端建议协作时复用；Codex 仍负责安全审查、代码落地、验证和文档回写。
- 验证：Gemini CLI 最小 `READY` 探测 PASS，skill `quick_validate.py` PASS。边界：本轮不声明任何前端交互已实现，也不把 Gemini 输出直接视为设计决策或测试结论。

## 2026-07-12 Quality Console 趋势视图状态（VERIFIED）

- Quality Console 已把 Memory quality smoke 与 RAG representative eval 从“散落 artifact / 文档证据”推进为可读趋势视图：`/api/quality/trends` 返回 `domainTrends.memoryQuality` 与 `domainTrends.ragRepresentativeEval`，前端 `/quality` 的“趋势”分区显示两张领域趋势卡。
- Memory 趋势卡展示候选抽取、记忆命中、RAG evidence 分离、冲突 / 敏感拦截等脱敏指标；RAG representative 趋势卡展示 target coverage、no-evidence guard、rerank applied、strict improvement / uplift / regression 等脱敏指标。
- `backend/target/rag-quality` 已纳入 Quality artifact root，但只读取 `artifact.json` 与 rerank representative `latest-summary.json` 的安全摘要；服务层按 marker 去重，避免 `latest-summary.json` 与同 marker run 目录 artifact 被重复计入 runs / trend。
- 验证结果：Quality API smoke 显示 `memoryQuality` 最新 `PASS`、`ragRepresentativeEval` 最新 `PASS`，RAG representative `caseCount=12`、`upliftCaseCount=10`、`strictImprovementCaseCount=2`、`targetCoverageRegressionCount=0`；最近 20 条 run marker 全唯一。浏览器 smoke 打开 `/quality?autoload=1` 并点击“趋势”后可见两张领域卡，console error `0`，`390px` 移动端横向溢出 `0px`。
- 已验证：后端 Quality 定向 27 tests PASS，前端 lint / build PASS，真实 API / 浏览器 smoke PASS。边界仍是内部质量台和小样本 smoke / eval 趋势，不是线上 SLA、跨机器持久质量仓库或大规模 benchmark。

## 2026-07-12 展示口径同步状态（VERIFIED）

- `docs/showcase/RAG_QUALITY_REPORT.md` 和 `docs/showcase/PROJECT_INTERVIEW_BRIEF.md` 已同步：Conversation grounding route smoke 不再只是待聚合项，而是已经进入 Quality Console runs、detail、Eval Catalog 和浏览器可见性证据。
- 后续展示规划已收敛为 Memory quality smoke 与 RAG representative eval 的趋势视图增强；仍保持“小样本真实链路 smoke / 内部质量台，不是线上 SLA 或大规模 benchmark”的边界。

## 2026-07-12 Quality Console Conversation grounding API 可见性（VERIFIED）

- 已完成最小真实 API 可见性 smoke：启动本地临时后端（18081，Quality Console enabled，mock AI），注册临时用户后调用 `/api/quality/runs`、`/api/quality/runs/{marker}` 和 `/api/quality/eval-cases`。
- 验证结果：`docpilot-conversation-grounding-20260712183609-a15fef` 在 Quality runs 中可见，source 为 `backend/target/conversation-grounding`；detail 返回 `conversationGrounding` gate，历史 `caseCount=6`、`evalCaseCount=6`。2026-07-13 后 runner 已扩展到 9 case，并新增 AUTO required no-evidence 拒答语义。
- 浏览器验证已补齐：临时 frontend（3007）指向 backend（18081），Playwright 打开 `/quality?autoload=1` 后可见 marker、source root 和 Artifact 分区当时的 catalog case；console error 为 `0`，`390px` 移动端无横向溢出。
- 边界：本次只验证 Quality API / 页面读取 ignored artifact 和 catalog 关联，不上传文档、不调用 provider；临时 18081 后端和 3007 前端已清理，无端口残留。

## 2026-07-12 Conversation grounding Eval Catalog 资产化（VERIFIED）

- Quality Eval Catalog 已纳入 Conversation grounding 路由矩阵，当前 catalog case 覆盖未绑定 KB 模型回答、未绑定 KB 误选 strict 归一、AUTO_RAG 明显闲聊不检索、AUTO_RAG 非显式资料无证据 fallback、AUTO_RAG 显式资料无证据拒答、STRICT_KB 无证据拒答、AUTO_RAG 有 evidence citation。
- 这些 case 通过 `regressionPolicy=["conversation_grounding_smoke","quality_tests"]` 固化回归入口；Catalog API 仍只返回安全 identifier、summary、policy、marker 和 hints，不返回 question / expectedBehavior 原文。
- 已验证：`mvn "-Dtest=AgentQualityEvalRunnerTest,QualityEvalCatalogServiceImplTest" test` PASS（6 tests）。

## 2026-07-12 Quality Console 与 Conversation grounding artifact（VERIFIED）

- Quality Console artifact 聚合已纳入 `backend/target/conversation-grounding`。`conversation-grounding-smoke.ps1` 真实 run 生成的 ignored 脱敏 artifact 现在可通过 `/api/quality/runs` / `/quality` 发现，不再只停留在文档记录里。
- `QualityArtifactServiceImpl` 已兼容顶层 `cases[]` 与 `pass` 字段，并在没有显式 gates 时生成 `conversationGrounding` synthetic gate；该 gate 只暴露 case 数、通过率、RAG 触发 / 必需、evidence / citation 覆盖等安全计数。
- eval case 明细现在可展示 `caseId`、`groundingPolicy`、`evidenceCount`、`citationCount`、`ragTriggered`、`ragRequired`、`llmCalled` 和 `modelSkipped`；仍不返回 raw prompt、raw answer、文档全文、evidence context、provider output、token、连接串或云地址。
- 已验证：`mvn "-Dtest=QualityArtifactServiceImplTest" test` PASS（14 tests）。

## 2026-07-12 对外展示口径同步（VERIFIED）

- `README.md`、`docs/showcase/RAG_QUALITY_REPORT.md`、`docs/showcase/PROJECT_INTERVIEW_BRIEF.md` 已同步 Conversation grounding policy 最新事实和 smoke 证据：`MODEL_ONLY / AUTO_RAG / STRICT_KB`、Trace 中的 `groundingPolicy` / `routeDecision` / `llmCalled` / `modelSkipped`、以及 `docpilot-conversation-grounding-20260712183609-a15fef`。对外口径继续声明这是小规模真实链路防回归 smoke，不是线上 SLA 或大规模对话质量 benchmark。

## 2026-07-12 Conversation grounding smoke 防回归状态（VERIFIED）

- Conversation grounding 修复已固化为专用真实链路 runner：`scripts/smoke/conversation-grounding-smoke.ps1`，支持 `plan` / `dry-run` / `run`；真实 run 会启动 / 复用本地 tunnel、backend、frontend，创建临时用户 / KnowledgeBase / Conversation / 文档并生成 ignored 脱敏 artifact。
- runner 覆盖未绑定 KB、未绑定 KB 误选 `STRICT_KB`、`AUTO_RAG` 明显闲聊跳过、`AUTO_RAG` 非显式资料无证据 fallback、`AUTO_RAG` 显式资料无证据拒答、`STRICT_KB` 无证据拒答、`AUTO_RAG` evidence citation，以及 T27/T28 最近轮次上下文隔离；Trace 断言 `groundingPolicy`、`routeDecision`、`ragTriggered`、`ragRequired`、`evidenceCount`、`llmCalled`、`modelSkipped`。
- 已验证：`conversation-grounding-smoke.ps1 -Mode plan` PASS，`conversation-grounding-smoke.ps1 -Mode dry-run` PASS，`ConversationGroundingSmokeScriptSafetyTest` 3 tests PASS；历史 marker `docpilot-conversation-grounding-20260712183609-a15fef` PASS；最新 marker `docpilot-conversation-grounding-20260713212058-5915ed` 为 9/9 case PASS。边界：这是小规模真实链路防回归 smoke，不是大规模对话质量 benchmark。

## 2026-07-12 Conversation grounding policy 路由状态（VERIFIED）

- Conversation 回答路由已拆分为独立 `GroundingPolicy`：未绑定 KnowledgeBase 默认 `MODEL_ONLY`，绑定 KB 默认 `AUTO_RAG`；用户显式选择 `STRICT_KB` 时始终要求 evidence；用户在 `AUTO_RAG` 中明确要求“根据 / 基于 / 引用文档或知识库”也会被视为 required evidence。“精炼回答模式”和 `contextMode` 不再等价于严格知识库模式。
- Conversation 入口不再复用单文档 / KnowledgeBase grounded QA 的 strict prompt，而是调用 `answerConversation(...)` 专用 prompt；明显闲聊或无绑定 KB 直接调用底层模型，绑定 KB 的实质问题优先 retrieval probe，有 evidence 才注入 RAG。
- Trace 现可记录并回读 `groundingPolicy`、`routeDecision`、`ragTriggered`、`ragRequired`、`evidenceCount`、`llmCalled`、`modelSkipped`、`fallbackReason`；消息与 Trace 同事务保存，刷新消息列表后仍能看到路由证据。
- 前端 Conversation 页面已按 Trace 区分“知识库来源 / 未使用知识库 / 资料不足”，普通模型回答不再显示“0 条来源”；回答依据可在“普通模型 / 自动知识库 / 仅基于知识库”之间显式选择。
- 已验证：Conversation / grounding 定向 37 tests PASS，schema / smoke script safety 9 tests PASS；新增 `ContextTraceSerializationTest` 锁定 `modelSkipped` JSON alias；授权后已执行 `008_add_context_trace_grounding.sql` 并确认云 MySQL Trace 三列存在；真实 Conversation grounding smoke marker `cg20260712175003-50312c` PASS，覆盖未绑定 KB、AUTO_RAG、STRICT_KB、evidence citation 和 no-evidence fallback；前端 Playwright marker `ui-cg-20260712095111-7094ef` PASS，确认普通回答显示“未使用知识库”且没有“0 条来源”，严格资料不足显示“资料不足 / 调用模型否 / 模型跳过是”；`mvn test -DskipITs` PASS（953 tests，0 failures，0 errors，5 skipped）；前端 `npm run lint` / `npm run build` PASS。

## 2026-07-12 四项任务收口：ParseTask、rerank、locator UI、Memory Governance（VERIFIED）

- ParseTask / reindex 恢复链路已完成 fail-closed + 可观测闭环：stale processing / outbox exhausted 会安全收口为 `FAILED`，status API 与前端“解析与恢复”卡片展示 consume/outbox、retry/reparse 和“禁止 content-only reindex”的安全边界。
- 阿里云百炼 `qwen3-rerank` 已通过 hard fixture 与代表语料真实链路验证：provider 真实调用成功，hard fixture 观察到 target rank `2 -> 1`、distractor 降权；代表 eval 12 case PASS、10/10 target coverage、2/2 no-evidence preserved。边界仍是小样本真实链路证据，不是大规模 relevance benchmark。
- Citation locator / metadata 已在文档详情、KnowledgeBase、Conversation 和 Agent 页面可见：统一展示文档名、`sourceLocator` / 页码 / section path、chunk/version 和 block / structure metadata；真实 cloud quality marker `docpilot-cloud-quality-20260712154804-0540c6` PASS。
- Memory Governance v1 边界已补测试并通过真实 smoke：相似 / 重复 / 冲突治理、跨用户 resolve 隔离、敏感手动创建 / merge / 系统抽取拦截、一次性指令抑制、assistant RAG evidence 不沉淀均有离线门禁；真实 marker `docpilot-memory-quality-20260712155609-7ba60d` 的 `memoryQuality` gate PASS。
- 当前仍不能写成完整商业 SaaS、线上 SLA、大规模多租户、复杂 PDF/OCR、成熟长期记忆质量或大规模 rerank benchmark；可对外强调的是 RAG 核心闭环、结构化 citation、恢复观测、真实 provider smoke 与持续质量门禁。

## 2026-07-12 ParseTask 恢复链路与 rerank uplift 收口（VERIFIED）

- ParseTask / reindex 恢复链路已从“只给失败状态文案”推进到 fail-closed 恢复：消费记录 `PROCESSING` 支持超时 takeover；恢复扫描会把长时间停留在 PENDING / UPLOADED / PARSING / SPLITTING / SUMMARIZING / INDEXING / PROCESSING 的任务、以及 outbox 重试耗尽但任务未终态的情况安全收口为 `FAILED`，并同步 document parse_status 与缓存失效。
- ParseTask status 投影补齐 `stale`、`staleReason`、`consumeStatus`、`outboxStatus`、`outboxRetryCount`、`outboxNextRetryTime`，stale 任务返回 `STALE_RECONCILIATION_PENDING`；恢复说明继续明确禁止仅基于 `Document.content` 重建结构化索引，后续 retry/reparse 必须走原文件解析链路，以保留 page、block metadata、section path 和 citation locator。
- 前端文档详情页已接入 ParseTask status 投影：右侧“解析与恢复”卡片展示当前阶段、恢复建议、stale 提示、retry/reparse 可用性、consume/outbox 摘要、失败阶段 / 错误码和安全边界；页面继续不暴露 content-only reindex，明确提示恢复必须走原文件 retry / reparse。
- 前端 citation locator 可见性已补齐：文档详情、KnowledgeBase、Conversation 和 Agent 页面统一展示文档名、`sourceLocator` / 页码 / section path、chunk/version 和 block / structure metadata；无 locator 时显式显示“来源定位待补充”，不再只暴露 chunk id / score。
- 本轮没有新增 content-only reindex API，也没有自动清空 / 重建 Qdrant collection；策略是对半成品 fail-closed + 可观测 + 后续原文件重试，避免丢失 parser metadata。
- 阿里云百炼 rerank 已从“provider 可用但 uplift 未证明”推进到小样本 hard fixture PASS：baseline marker `docpilot-rerank-effect-hybrid-20260712015151-46c631` 中 distractor rank 1、target rank 2；rerank marker `docpilot-rerank-effect-rerank-20260712015353-cc21a9` 中 `rerankApplied=true`、`rerankModel=qwen3-rerank`、target rank 1、distractor rank 3，`hardUpliftObserved=true`。
- 验证：ParseTask / rerank 定向 40 tests PASS；`mvn test -DskipITs` PASS（920 tests，0 failures，5 skipped）；前端 `npm run lint` / `npm run build` PASS；`rerank-effect-smoke.ps1 -Mode dry-run` PASS；真实 rerank effect PASS；parser real chain marker `docpilot-parser-real-chain-20260712015555-91d1fd` 与刷新 marker `docpilot-parser-real-chain-20260712154120-5bb049` PASS，source locator `3/3`、parser boundary `4/4`；locator UI 后完整 cloud quality marker `docpilot-cloud-quality-20260712154804-0540c6` PASS。
- 边界：ParseTask 恢复已完成 fail-closed / 观测 / 正常链路回归，不等于复杂自动重放或线上 SLA；rerank uplift 是小样本 hard fixture 证据，不是大规模或稳定 relevance benchmark。

## 2026-07-12 阿里云百炼 rerank provider 验证状态（VERIFIED / REVIEW）
- 本机 ignored `backend/.env` 已按当前项目实际 provider 纠正为阿里云百炼 rerank：`APP_RAG_RETRIEVAL_HYBRID_ENABLED=true`、`APP_RAG_RERANK_ENABLED=true`、`APP_RAG_RERANK_PROVIDER=aliyun_bailian`、`APP_RAG_RERANK_MODEL=qwen3-rerank`；base URL 使用百炼 `compatible-api/v1/reranks` endpoint，真实 API key 只保留在本机 `.env`，未写入 tracked 文件。
- 示例配置与 `backend/README.md` 已同步说明：百炼 chat / embedding 的 `compatible-mode/v1` 与 qwen3-rerank 的 `compatible-api/v1/reranks` 不同，不能沿用其他供应商的 `/v1/rerank` 口径。
- 真实 rerank provider 可用性已验证：`rerank-effect-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` baseline marker `docpilot-rerank-effect-hybrid-20260712003119-0b7ed3`，candidate marker `docpilot-rerank-effect-rerank-20260712003244-46b2e3`；candidate `rerankApplied=true`、`rerankModel=qwen3-rerank`、`rerankFailureReason=""`，rerank score count `4`，核心 RAG / no-evidence / 权限安全无回退。
- 边界保持 REVIEW：本次 hard fixture 未观察到 target rank / distractor rank uplift，overallStatus 为 `REVIEW`；因此只能说“百炼 rerank provider 已真实调用且无核心回退”，不能说“真实 rerank relevance uplift 已验证”。

## 2026-07-11 RAG 求职级三项收口（REVIEW）

- KnowledgeBase citation locator 已从“单文档完整、KB 仅 chunk/quote/score”为主，升级为 KB retrieve hit、KB citation response、KB Agent search / answer 路径均可携带 `sectionPath`、`structureType`、`pageNumber`、`sourceLocator`、`blockType`；字段来源仍是现有 parser block -> chunk metadata -> Qdrant payload，不新增 schema。
- Rerank 诊断已安全收口：HTTP rerank fallback 会返回 `provider_not_configured`、`provider_not_found`、`provider_timeout`、`provider_auth_failed`、`provider_rate_limited`、`provider_http_error`、`provider_error` 等枚举；cloud smoke 和 rerank effect artifact 会记录 `rerankFailureReason`，避免把 identity fallback 误写成真实 rerank 生效。
- 新增 `docs/showcase/RAG_QUALITY_REPORT.md` 作为求职展示版 RAG 质量报告，明确当前达到求职级核心闭环，同时声明非商业 SaaS、非 OCR/复杂 PDF、非大规模 benchmark，且真实 rerank 仍需 provider/model 可用性复验。
- 已验证：定向 34 tests PASS；`mvn test -DskipITs` PASS（912 tests，0 failures，5 skipped）；`rerank-effect-smoke.ps1 -Mode dry-run` PASS；真实 rerank 对照 marker `docpilot-rerank-effect-hybrid-20260711194601-2623f5` / `docpilot-rerank-effect-rerank-20260711194743-d98021` 为 REVIEW，`rerankFailureReason=provider_not_found`、`rerankApplied=false`，核心 RAG / no-evidence / 权限安全无回退。

## 2026-07-11 最大压力真实链路审计（VERIFIED / REVIEW）

- 本轮按“有界最大”审计执行真实链路：`real-user-qa-experience-audit.ps1` 最终 marker `docpilot-real-user-qa-20260711170544-dff948` PASS；`rag-real-qa-eval-smoke.ps1` marker `docpilot-rag-real-qa-20260711171137-ed38a0` PASS；`memory-provider-extraction-smoke.ps1` marker `docpilot-memory-provider-20260711172435-14083e` PASS；`agent-quality-eval-smoke.ps1` marker `docpilot-agent-quality-eval-20260711171903-fae364` PASS；parser marker `docpilot-parser-real-chain-20260711171912-a8e65c` PASS。
- 已修复两个审计中暴露的工程问题：`frontendInteraction` 失败时现在保留脱敏 `nodeOverallStatus/nodeSafeMessage`，不再把脚本异常吞成一组 false/0；Memory provider smoke 现在在 run 模式从 repo 内 `backend/.env` 安全注入缺失的 `AI_REAL_*`，直接运行不再误报 `provider_config_missing`。
- 自然语料财务多文档 compare case 已改成更明确的真实问题，要求答案说明“谁审批报销”和“发票归档保留多久”；复验 `docpilot-rag-natural-corpus-20260711165958-f4935a` 与完整 audit 均 PASS，`answerFaithfulnessPassCount=11/11`、`distractorCitationFreeCount=25/25`。
- Rerank 对照 marker `docpilot-rerank-effect-hybrid-20260711171329-bda3dc` / `docpilot-rerank-effect-rerank-20260711171449-522a4c` 为 REVIEW：核心 RAG、no-evidence 和权限安全无回退，但当前 `.env` 中真实 rerank model 调用返回 `NotFound`，服务降级为 `identity`，`rerankApplied=false`，本轮不能宣称真实 rerank provider 已生效。
- 基础门禁保持通过：`mvn test -DskipITs` PASS（911 tests，0 failures，5 skipped），`npm run lint` PASS，`npm run build` PASS；`npm audit --omit=dev` 仍是已知 Next high + PostCSS moderate，Next 16 major 不纳入本轮。

## 2026-07-11 真实用户全链路审计与恢复验证（VERIFIED）

- 真实用户链路已重新跑通：首轮 `docpilot-real-user-qa-20260711155558-573a81` 暴露自然语料 `finance-expense-invoice-compare` 的 `answerFactExpression` 误杀；修复同义表达门禁后，`docpilot-rag-natural-corpus-20260711160322-1cbcbc` 与完整 `docpilot-real-user-qa-20260711160913-98a440` 均 PASS。
- 本次完整 audit 覆盖 tunnel、backend health、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、短文档 RAG、自然语料 25 case、answer grounding、no-evidence、Conversation Trace、Memory quality、权限隔离、frontend routes / interaction、cleanup 和 artifact redaction。
- parser 专项 marker `docpilot-parser-real-chain-20260711161514-6c4786` PASS：PDF / HTML / DOCX 三类文件解析成功，累计 `chunkCount=7`，direct retrieve / QA retrieval / citation / source locator 均 `3/3`，parser boundary `4/4`。
- ParseTask status 真实登录态 API 已验证：成功解析文档返回 `SUCCESS` / terminal / parsed content present，且 `safeReindexAllowed=false`、`contentOnlyReindexAllowed=false`，未暴露纯 `Document.content` 重建索引入口。
- 真实检查中发现并修复注册参数校验落入 500 的问题：全局异常处理器现将 `BindException` 与 `ConstraintViolationException` 归一为 `BAD_REQUEST`；运行时超长 username 复验返回 `code=400`。后端全量默认测试 `mvn test -DskipITs` PASS（911 tests，0 failures，5 skipped）。

## 2026-07-11 求职级收口验收快照（VERIFIED / REVIEW）

- 离线总验收通过：`mvn test -DskipITs` PASS（909 tests，0 failures，5 skipped），`npm run lint` PASS，`npm run build` PASS。
- 真实 parser / RAG 链路已刷新验证：`document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` marker `docpilot-parser-real-chain-20260711152944-1db28d` PASS；PDF / HTML / DOCX 上传、异步解析、Qdrant direct retrieve、QA retrieval、citation、source locator、parser boundary 和 artifact redaction 均通过。
- 本轮服务清理完成，`3000/3001/3002/3007/3100/8081` 端口复查为空。仍保持 REVIEW 的边界：Next 14 生产 audit 剩余 high / moderate 需 Next 16 major 取舍；ParseTask status API 已离线验证但未单独做真实登录态 API smoke；fresh-clone Docker MySQL runtime 和 GitHub Actions 首跑仍未在本轮证明。

## 2026-07-11 多 block citation locator 覆盖门禁（REVIEW）

- `RagIndexingTriggerServiceImplTest` 的 parser locator 闭环从单 block 扩展为三页多 block / 多 chunk 场景，验证 parser block 的 `sectionPath`、`pageNumber`、`sourceLocator` 和 `blockType` 在 indexing、vector payload、retrieval hit 与 citation 中不丢失。
- 该测试覆盖 9 个 chunk 的脱敏 PDF 式 fixture，并要求 retrieval hits / citations 都保留 locator 字段，且 topK 中同时出现 `page:1`、`page:2`、`page:3`。
- 定向 RAG 回归 43 项 PASS；本片是离线门禁增强，真实 document-parser chain smoke 待后续统一运行。

## 2026-07-11 ParseTask 状态观测与安全恢复口径（REVIEW）

- 新增 `GET /api/task/parse/status?documentId=...` 状态投影，返回最新 ParseTask 阶段、Document parse 状态、失败错误码、失败阶段、retry/reparse 可用性和恢复建议；该接口只读，不触发 MQ、不重建索引、不修改 Document。
- RAG 索引失败会被识别为 `RAG_INDEX_*` / `INDEXING` 阶段问题，恢复建议明确指向 `retry/reparse` 重新解析原文件并携带 parser block metadata 后再索引；响应显式返回 `safeReindexAllowed=false` 与 `contentOnlyReindexAllowed=false`，避免把 `Document.content` 当成结构化重建索引来源。
- 定向测试 34 项 PASS，`mvn -q -DskipTests compile` PASS；尚未跑真实 cloud parse / RAG runtime smoke，因此状态为 `REVIEW`。

## 2026-07-11 Frontend Next 安全补丁恢复（REVIEW）

- 前端依赖已重新从 `next` / `eslint-config-next` `14.2.5` 升至 `14.2.35`，修复本次异常恢复后 tracked package 回退导致的生产依赖 critical audit 风险；`next-env.d.ts` 同步为当前 Next 14.2.35 生成内容。
- 已验证 `npm run lint` 与 `npm run build` PASS；`npm audit --omit=dev` 当前无 critical，但仍报告 1 项 Next high 与 1 项 PostCSS moderate，完整清零需要 Next 16 major 升级，仍作为单独取舍保留。
- 本片不升级 React / Next major，不改前端业务路由、后端 API、模型、数据库或 smoke runner；`REA-20260710-P1-011` 继续保持 OPEN，状态从“critical 回退已修复”进入“major-upgrade 风险待取舍”。

## 2026-07-11 reindex 半成品异常恢复（DONE）

- 已回滚上一轮新增的 `POST /api/task/parse/reindex`、`ParseTaskService.reindex` 和基于 `Document.content` 伪造 `ParseResult` 的手动重建索引实现；该实现会丢失 parser block 的 `pageNumber`、`sourceLocator`、`blockType` 和 parser-derived `sectionPath`，不符合当前结构化 citation 主线。
- 当前保留的事实仍是：解析消费者在 `INDEXING` 阶段同步等待 RAG 索引结果，只有索引成功且 chunk/vector 完整后才将 Document 与 ParseTask 标记为 `SUCCESS`；索引失败时保留已解析内容并标记 `FAILED`。
- 后续如重新设计 reindex，必须基于可恢复的 parser block / source metadata 或明确 fail-closed；不允许把纯 `Document.content` 重建写成结构化索引恢复能力。

## 2026-07-10 解析/索引成功状态收口（REVIEW）

- ParseTask 消费主链路现在把 RAG 索引纳入成功条件：进入 `INDEXING` 后先保存解析内容和摘要，等待同步索引结果，只有当前 user/document/indexVersion 一致、chunk/vector 完整的 `SUCCESS` 才把 Document 与 ParseTask 标记 `SUCCESS`。
- 索引失败、空结果、异常或结果错配统一收口到 `FAILED`，使用 `RAG_INDEX_*` 安全错误分类并保留已解析内容；失败日志只记录 task/document/file id 与错误码，不输出底层 provider、Qdrant 或原始异常内容。
- 定向后端回归 33 项 PASS。该片尚未实现 consumer/outbox lease、自动 reindex、状态投影 API、Prometheus 指标或真实 cloud run，故状态为 `REVIEW`，不能写成完整失败恢复已完成。

## 2026-07-10 cloud smoke Next dev origin 对齐（VERIFIED）

- `cloud-quality-smoke.ps1` 启动 Next dev 时现显式绑定 `FrontendBaseUrl` 的 loopback host，避免监听/访问 origin 不一致在初次 dev compile 与 RSC 刷新窗口触发 `fetchServerResponse` console error。
- 静态安全测试 2 项与三次完整真实 smoke 均通过：`docpilot-cloud-quality-20260710210913-5ea91b`、`docpilot-cloud-quality-20260710211110-0b226b`、`docpilot-cloud-quality-20260710211530-6de04e` 的 KnowledgeBase citation、console error=0、核心 RAG / Agent / Trace / 权限、cleanup 和 artifact redaction 全部 PASS。未改 Next 配置、依赖锁、业务 API、模型或数据库。

## 2026-07-10 RAG SSE clean-EOF fail-closed（VERIFIED）

- RAG stream 仅在收到命名 `event: done` 后成功结束；HTTP body clean EOF 若缺 done，会以 `transport_eof` 进入既有页面恢复状态机，首 chunk 前回退一次非流式 RAG，已有 chunk 则保留部分内容并提示重试。
- production Next + Playwright 已验证 EOF 前/后 chunk 和正常 done 三条路径，定向 5/5、完整 E2E 14/14、lint/build 均 PASS；独立审查确认与后端正常 done 协议一致。该覆盖不等价于 TCP reset、fetch reject 或跨 read 分帧断连。

## 2026-07-10 Memory provider extraction case diversity v2（VERIFIED）

- 测试侧真实 provider contract 已从 4 扩至固定 6 个脱敏 case，新增中文长期 `PREFERENCE + PROJECT_STATE` 抽取与一次性指令零 suggestion 抑制；最终真实 marker `docpilot-memory-provider-20260710204432-4540df` PASS，6 calls、`casePassRate=1.0000`、`rawProviderOutputStored=false`。
- wrapper 现不读取 `.env`、不落盘 Maven 原始日志，固定 ignored artifact root 并限制 marker 路径；非法 JSON / 畸形 suggestion 不会被误判为安全空列表，预算与 artifact 结果均固定为 six-case suite。运行时 Memory API 仍为规则式抽取，不新增数据库或 LLM runtime 注入；这仍是小样本输出语义 contract，不代表长期记忆质量成熟。详见 `REA-20260710-P3-016`、`REA-20260710-P1-017`。

## 2026-07-10 fresh-clone demo schema bootstrap（REVIEW）

- `deploy/mysql/init/` 已补齐为供 `docker-compose.demo.yml` 空数据卷首次初始化使用的完整 demo schema：17 张应用持久表覆盖文档 / Outbox、Agent、RAG、KnowledgeBase、Conversation、Trace 与 Memory。
- `tb_document.status` / `idx_document_status` 和 `tb_qa_history` 的 question、answer、时间索引已与当前应用参考 schema 对齐；新增离线 bootstrap contract test，相关 5 项 schema tests PASS。
- 不引入 Flyway/Liquibase 或 Spring 启动 DDL，不影响云 MySQL；已有 demo volume 也不会因新增 init 文件自动升级。隔离 MySQL runtime 验收待 Docker Engine 可用后执行，详见 `REA-20260710-P2-015`。

## 2026-07-10 Next dev 访问源兼容（VERIFIED）

- `next.config.js` 已在开发期显式允许 `127.0.0.1`，修复 cloud smoke / 本地联调以 loopback IP 访问 Next dev 时的 RSC `Failed to fetch` 控制台错误；该设置不影响 production rewrite、后端 API 权限或数据库。
- `npm run lint`、`npm run build` PASS；完整真实 cloud quality marker `docpilot-cloud-quality-20260710200547-6dec4e` PASS，KnowledgeBase 双 citation 可见且前端 `consoleErrorCount=0`，详见 `REA-20260710-P3-014`。

## 2026-07-10 阿里云百炼 Qwen 运行配置（VERIFIED）

- 当前本机真实回答使用百炼 `qwen-plus`，向量化使用百炼 OpenAI-compatible `text-embedding-v4`；回答最小请求、RAG 上下文请求和 1024 维 embedding 探测均成功。
- 完整真实 cloud quality run `docpilot-cloud-quality-20260710195347-5fbdb7` PASS，现有 Qdrant collection 与新 embedding 维度兼容，核心 RAG、Agent、权限和浏览器交互均通过。
- 旧 embedding 模型标识导致的索引失败已验证修复，详见 `REA-20260710-P2-013`。上述切换只在 ignored 本机 `.env` 生效，不代表仓库默认 provider 已改。

## 2026-07-10 SSE RAG 失败语义（VERIFIED）

- 流式 RAG 现在区分检索降级、首 token 前生成失败和部分回答中断；检索降级以非致命 `fallback` 事件继续返回已有降级答案。
- 前端仅在未收到 chunk 的 generation / transport 失败时回退一次非流式请求；已有内容或 scope 错误不会重放，部分内容保留并提示用户重试。
- 后端事件测试 14 项、前端 lint/build 通过；production Next + Playwright route-mock 真实页面已验证首 chunk 前 `generation` 错误只回退一次、已有 chunk 的 `generation_partial` 保留部分答案且不回退。完整前端 E2E 11/11 PASS。
- 边界：当前覆盖 SSE `error` event 的浏览器语义，不等价于底层 TCP 断流或跨读取分帧模拟。

## 2026-07-10 RAG 回答模型可靠性（VERIFIED）

- 单文档 RAG 与 KnowledgeBase RAG 已统一接入现有 `AiRetryExecutor`，且重试仅包围 `AiAnswerService.answer(...)`；不会重复检索、构造 prompt、精炼 citation 或写入单文档历史。
- AI 重试、单文档 RAG 与 KnowledgeBase RAG 的失败日志已改为只记录安全维度和异常类型；KnowledgeBase 的 `modelCallCount` 现在反映真实模型尝试次数。
- 本片不对 SSE 流式回答自动重放：部分 chunk 已发送时重试会造成重复输出，需作为独立体验切片处理。
- 定向回归共 29 项 PASS。根因确认是当前本机真实模型的非流式 RAG 响应可超过原 30 秒读取窗口，而不是模型标识、鉴权、解析、索引或 Qdrant 失败。
- 仅调优 ignored 本机 `.env` 的读取窗口后，完整真实 cloud quality run `docpilot-cloud-quality-20260710191822-ec80b6` PASS，覆盖单文档 / KnowledgeBase / 短文档 RAG、Conversation / Memory、Agent、权限、浏览器交互、cleanup 与 artifact redaction；详见 `REA-20260710-P1-012`。未改数据库、云端服务、项目默认超时或重试上限。

## 2026-07-09 当前补充

- 离线 Playwright E2E 路由 smoke 已接入但待首个 GitHub Actions run：前端以 production `next start` 启动临时 `3100` 端口，Chromium 覆盖 9 个未登录主导航路由的 HTTP、标题、`main` 可见性、page error 与 console error；本地 `npm run lint`、`npm run build`、`npm run test:e2e` 均 PASS，9/9 routes PASS，端口已清理。该门禁不登录、不调用 backend、不启动 tunnel、不访问云端；它只证明公开页面静态/未登录可渲染，不替代认证、上传、SSE、RAG、Agent 或云 runtime smoke。
- Gemini CLI 协作恢复边界已固化到 `docs/ai-dev/CONSTRAINTS.md`：CLI 明确不可用、连接失败或超时后，可在单独本地终端启动用户指定的 AIStudioToAPI `npm start`，待其报告就绪后仍以 `gemini-3.5-flash` 重试一次；该路径不读取或输出其配置 / 凭据，不保证恢复成功，失败后继续由 Codex 负责，不放宽远程、数据库或 Git 安全限制。本轮只更新规则，未启动该服务，未验证恢复效果。
- 离线 CI 基线已实现但待首个 GitHub Actions run 验证：`.github/workflows/ci.yml` 分离 Java 17 后端全量单测与 Node 20 前端 `npm ci` / lint / build，workflow 不传递 `.env`、不启动 tunnel、不访问云 MySQL / Qdrant。为适配 Ubuntu runner，脚本安全测试统一经 test-only `PowerShellTestSupport` 选择 Windows `powershell` 或非 Windows `pwsh`；离线 Agent demo suite 内层脚本启动也已改为 OS-aware，避免外层 `pwsh` 后再次硬编码 `powershell`。本地全量后端 891 tests PASS、前端 install/lint/build PASS。没有 push/PR 触发的远程 runner 证据前，CI 不能写成 VERIFIED；真实云 RAG 仍以本机 tunnel + smoke runner 为准。
- 用户工作区中的 Next 14.2.35 安全升级已通过当前 lint/build/E2E 14/14 与真实 cloud smoke 回归；`npm audit --omit=dev` 现仍报告 1 项 Next high 与 1 项 PostCSS moderate。审计建议的完全修复会升至 Next 16，属于破坏性升级，`REA-20260710-P1-011` 保持 OPEN，待明确取舍后处理。
- 2026-07-10 默认后端单测隔离已收口：`mvn test -DskipITs` 由 Maven Surefire 固定 test profile，并通过空 test resource 覆盖 `.env` import；scheduled outbox、RocketMQ、Redisson 可在测试中显式关闭，生产默认仍保持启用。已验证 `mvn clean "-Dtest=DocPilotApplicationTests" test` PASS、`mvn test -DskipITs` PASS（889 tests，0 failures，0 errors，5 skipped），且未新增 Surefire fork kill dumpstream；本项不代表云 MySQL / Qdrant runtime smoke 通过。
- Document Parser 多 chunk 真实 smoke v4 已通过。最新 marker `docpilot-parser-real-chain-20260710143019-38705a` 中 HTML fixture 含超过默认 `800/120` 策略的脱敏正文，产生 `5` 个 chunk，`expectedMinChunks=2`、`multiChunkVerified=true`，direct / QA retrieval 各 `5` hits / citations；PDF、DOCX 各 1 chunk，三类文件累计 7 chunks，`fixtureStructureCoverage=11/11`、parser boundary `4/4`、artifact redaction PASS。runner 把多 chunk 不足归为 `REVIEW` 的结构质量风险，而不误报为 parse / provider 核心失败。该证据仍只证明至少一条 citation 的来源定位，尚未证明所有跨 block chunk 的 locator 覆盖。
- Document Parser 自然结构真实 smoke v3 已通过。最新 `document-parser-real-chain-smoke.ps1` marker `docpilot-parser-real-chain-20260710142418-09566e` 在受控 local tunnel / backend / frontend 下 PASS：PDF / HTML / DOCX 均完成上传、异步解析、chunk、Qdrant direct retrieve、QA retrieval、citation 和来源定位；新增 HTML `aside` 噪声的安全结构信号 `html_noise_excluded` 通过，`fixtureStructureCoverage=10/10`，parser boundary `4/4` 与 artifact redaction PASS，`environmentUnstable=false`。artifact 只保存结构枚举、计数、布尔值与失败码，不保存解析全文、query、prompt、answer、evidence context 或敏感配置。该证据仍是单 chunk 小样本，不代表 OCR、扫描件、旧 `.doc`、复杂版面或大规模解析质量。
- Document Parser block 来源定位端到端 contract 已补齐。`RagIndexingTriggerServiceImpl` 会将 `ParseResult.DocumentBlock` 的 `pageNumber`、`sectionPath`、`sourceLocator`、`blockType` 传入 `RagSourceBlock`；`ChunkingServiceImpl`、`RagIndexingServiceImpl` 和 `RagDocumentRetrievalServiceImpl` 保持这些 metadata 到 citation。新增离线 in-memory 闭环测试验证 PDF 式 parser block 经索引触发、chunk、向量检索后，citation 仍返回 `Parser Evidence`、页码 `2`、`page:2` 与 `PAGE`；相关 43 tests PASS。该证据不替代真实 Qdrant runtime smoke，也不包含文档全文、query、prompt、answer、evidence context 或敏感配置。
- Document Parser 自然样本 fixture v2 已完成。`HtmlDocumentParser` 已把本地 HTML 的 `aside` 辅助栏纳入与 `script/style/nav/header/footer` 相同的噪声隔离范围，避免相关推荐或推广文本进入 RAG；新增脱敏自然文章 fixture 验证 `h1/h2/h3`、列表、表格行和正确 `sectionPath`，新增多章节 DOCX fixture 验证新的 `Heading1` 会重置后续段落 / 表格的来源路径。`DocumentParserTest`、`DocumentParserFixtureCorpusTest` 和 `ParseTaskConsumeEntryServiceImplTest` 共 26 tests PASS。该片仍只覆盖本地文本抽取和结构元数据，不代表 OCR、扫描件、旧 `.doc`、复杂版面理解、外部网页抓取或大规模解析 benchmark。
- Document Parser 结构覆盖 artifact 已完成真实链路复验。`document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 最新 marker `docpilot-parser-real-chain-20260710001619-a1b510` PASS：PDF / HTML / DOCX 均 `parseStatus=SUCCESS`、`chunkCount=1`、direct retrieve hit `1`、QA retrieval hit `1`、citation `1`、source locator present；parser boundary `4/4` PASS，artifact redaction PASS。新增 `fixtureStructureCoverage` 在真实 artifact 中为 `expectedSignals=9`、`coveredSignals=9`、`missingSignals=0`、`allCovered=true`，direct / QA 成功计数均为 `3`，最大重试次数均为 `1`，`environmentUnstable=false`。本次 run 证明小样本真实链路中结构覆盖摘要可用，但仍不代表 OCR、扫描件识别、旧 `.doc`、复杂版面理解、外部网页抓取或大规模解析 benchmark。
- Document Parser 真实 smoke 与长期 fixture corpus 的结构覆盖口径已对齐。`document-parser-real-chain-smoke.ps1` 的每个 parser case 现在输出 `expectedStructures` / `structureSignals` 安全枚举，`parserQualityReport.fixtureStructureCoverage` 汇总预期、覆盖、缺失结构信号和 `allCovered`；Quality API 只解析覆盖计数，`/quality` 文档解析质量摘要显示“结构覆盖”，诊断网格新增“结构 fixture 覆盖”。当前覆盖信号包括 PDF 文本 / 页码来源、HTML 标题 / 表格 / 链接 / 列表、DOCX 标题 / 表格 / 列表；这些都是脱敏枚举，不包含解析文本、query、answer 原文、prompt、evidence context、token、secret、连接串或云地址。本片已验证脚本 plan / dry-run、后端 Quality/parser targeted tests、前端 lint / build 和 `/quality?routeSmoke=2` 移动端检查；尚未新跑会创建业务数据的真实 `run`，下一片应复验结构覆盖 artifact。
- Document Parser 长期回归 fixture corpus 已补第一片。新增 `DocumentParserFixtureCorpusTest`，用内存生成的脱敏 PDF / HTML / DOCX fixture 覆盖文本型 PDF 多页与空页 warning、page-level source locator、本地 HTML 噪声剔除与标题 / 表格 / 列表 / 链接结构、DOCX 标题继承 / 段落 / 列表 / 表格和 `sectionPath` / source locator；同时确认 `txt/pdf/html/docx` parser selection 稳定。该片只增强测试资产，不改生产 parser、不新增依赖、不提交二进制 fixture 文件，也不代表 OCR、扫描件识别、旧 `.doc`、复杂版面理解或外部网页抓取已支持。
- Document Parser 质量报告已接入 Agent Quality Console 的可读诊断摘要。`document-parser-real-chain-smoke.ps1` 的 `parserQualityReport.ragChainSummary` 现在输出 direct retrieve / QA retrieve 的成功计数、no-evidence 计数、最大重试次数和运行环境稳定性；`QualityArtifactServiceImpl` 只通过白名单解析这些安全字段，`/quality` 文档解析质量摘要新增“直接检索接口”“问答检索接口”“运行环境稳定”以及“直接 / 问答一致性”“环境稳定性”诊断。该能力用于区分 parser / RAG 质量问题与本地 tunnel、JDBC、Qdrant 运行环境问题，不展示 query、prompt、answer 原文、文档全文、evidence context、token、secret、连接串或云地址。本片已验证脚本 plan / dry-run、后端 Quality targeted tests、前端 lint / build 和 `/quality?routeSmoke=2` 桌面 / 移动端 route smoke；没有新增真实业务数据，真实链路能力仍以最新 parser real-chain marker `docpilot-parser-real-chain-20260709233230-a08906` 为准。
- Document Parser direct retrieve / QA retrieve 同轮差异已收口到 PASS。上一片真实 run 先暴露两个问题：一是 `document-parser-real-chain-smoke.ps1` 的 PowerShell 计数逻辑会把缺失 hits 误计为 `1` 或 `null`；二是运行中本地 MySQL tunnel / JDBC 断链会把 parser smoke 污染成 `FAILED_CORE_FLOW`。脚本已新增 `directRetrieveDiagnostic` / `qaRetrieveDiagnostic` 脱敏摘要、`Get-SafeItemCount` 安全计数和 `environmentStability` 运行环境归因；环境断链时标为 `BLOCKED`，不再误判为 parser 核心失败。最新真实 marker `docpilot-parser-real-chain-20260709233230-a08906` 为 PASS：PDF / HTML / DOCX 均 `parseStatus=SUCCESS`、`chunkCount=1`、direct retrieve hit `1`、QA retrieval hit `1`、citation `1`、source locator present；parser boundary `4/4` PASS，artifact redaction PASS。该结论证明 Document Parser MVP 的 PDF / HTML / DOCX 真实上传 -> 异步解析 -> chunk -> Qdrant retrieve -> QA citation 小样本链路可用，但仍不代表 OCR、扫描件识别、复杂版面理解、旧 `.doc` 或大规模解析 benchmark。
- Document Parser 真实链路质量门禁已把 direct retrieve 缺口显性化。`scripts/smoke/document-parser-real-chain-smoke.ps1` 现在使用与 QA 相同的用户式问题验证 direct retrieve，并在 QA retrieval 通过后做 direct endpoint 二次确认；如果 PDF / HTML / DOCX 的 parse、chunk、QA retrieval、citation 和 source locator 都通过，但 direct retrieve 未覆盖全部 fixture，则 `parserRealChain` gate 标为 `REVIEW`，`parserQualityReport.reviewReasons` 记录 `direct_retrieve_missing`。最新真实 marker `docpilot-parser-real-chain-20260709223724-ceb637` 为 `REVIEW`：PDF / HTML / DOCX 均 `parseStatus=SUCCESS`、`chunkCount=1`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`，parser boundary 和 artifact redaction PASS，但 `directRetrieveHitCount=0/3`。重启本地 backend 后，同批文档手动调用 `/api/rag/retrieve` 可返回 direct hits `1/1/1`，说明索引最终可用；下一片需要定位同一 smoke 进程内 direct retrieve 与 QA retrieve 的差异。本片不改业务 service、不新增 schema、不提交 artifact 原文、不 push。
- Agent Quality Console ABC 求职级增强循环已完成真实登录态回归审计。复用本地已有 MySQL / Qdrant tunnel，启动 backend（local profile，Quality Console enabled，mock AI）和 frontend `3007` 后，通过浏览器注册临时用户并打开 `/quality?autoload=1`；Quality API 登录态回归通过，runs / eval-cases / trends 均成功，可见 `20` 条 run、`12` 个 eval case、`20` 个趋势点，最新 marker `docpilot-cloud-quality-20260709164330-452624` 状态为 `REVIEW`。页面可见运行详情、待处理、链路、评测、文档解析质量摘要、评测用例库、能力层覆盖、覆盖缺口、质量趋势、反复失败用例和最近运行点；真实 `/quality/trace` 可见链路瀑布图、步骤摘要、排查建议、关联门禁和关联评测用例。页面 DOM 未命中 Authorization 凭据、API key、secret、password、连接串、system prompt、answer raw、document full text 或 evidence context；桌面 console error 为 `0`，`390px` 移动端 `/quality` 与 `/quality/trace` 均无横向溢出。本轮只创建临时登录用户，不上传文档、不创建 KB / Conversation、不删除业务数据、不改 schema、不操作远程 Docker、不提交 artifact 原文、不 push。
- Agent Quality Console B3 Eval Catalog 覆盖缺口与用例分层审计已完成。`/quality` Eval Catalog 现在内置必需能力层清单，覆盖 Agent RAG Trace、Memory Context Trace、RAG no-evidence、Citation Precision、Agent Search Routing、KB Agent Grounded Answer、Document Parser Real Chain 和 Memory Provider Contract；顶部展示“能力层覆盖”分子 / 分母，下方展示“覆盖缺口”，缺层时显示中文能力层名称，全部覆盖时显示“核心能力层已覆盖”。本片不改后端 API、不新增数据库表、不改 eval runner 评分逻辑、不读取 raw artifact、不展示 question、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- Agent Quality Console A3 Trace Timeline 信息密度与步骤诊断增强已完成。`/quality/trace` 链路瀑布图现在新增步骤摘要，展示失败步骤、复查步骤、工具 / RAG 步骤、模型 / 引用步骤和主要失败 / 复查类型；每个 Trace step 会根据 `stepType`、状态和脱敏 bucket 显示排查建议，覆盖工具调用、RAG 检索、模型调用、引用校验、Agent step、eval case、权限、记忆和 parser 等常见方向。本片不改后端 API、不新增数据库表、不读取 raw artifact、不展示 question、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- Agent Quality Console C2 最近 N 次 repeated failure / regression 轻量趋势增强已完成。`/quality` 趋势区现在会用 Eval Catalog 丰富反复失败 case：展示 case type、能力层、risk gate、失败次数、复查次数、最近运行 marker 和首条修复建议；有 latest trace 的 case 可跳转 `/quality/trace`，没有链路引用时显示“暂无链路引用”。最近运行点卡片新增 case 通过率、失败 / 复查数、token、耗时、失败类型和复查类型。本片不改后端 API、不新增数据库表、不引入复杂趋势图、不读取 raw artifact、不展示 question、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- Agent Quality Console B2 Eval Case 风险与回归策略可读性增强已完成。`/quality` Eval Catalog 顶部新增用例总数、待处理用例、Trace 覆盖和高风险用例摘要；列表按失败、复查、未运行和高风险优先排序。每个 case 现在拆成风险分层、评分门禁、回归策略、历史与定位、修复建议和期望证据几个区域，常见 `caseLayer`、`riskGate`、`scoringSummary`、`regressionPolicy`、`remediationHints` 以中文短语展示；有 latest trace 的 case 可直接跳转 `/quality/trace`，没有链路引用时明确显示“暂无链路引用”。本片不改后端 API、不新增数据库表、不改 eval runner 评分逻辑、不展示 question、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- Agent Quality Console A2 Trace / Failure drill-down 入口联动增强已完成。`/quality` 的“待处理”失败分桶卡片现在展示模块标签、失败 / 复查次数、关联门禁数、关联评测数、关联链路数、说明和建议动作；有 trace reference 的 bucket 可直接跳到 `/quality/trace`，没有链路引用时明确显示“暂无链路引用”。Run Detail 的链路定位行新增步骤数和主要排查方向，`/quality/trace` 的 Trace reference 卡片新增步骤数。失败 / REVIEW eval case 也会显示“查看 Trace”或“暂无链路引用”，用于暴露 eval 到 trace 的覆盖缺口。`PARSER_FAILURE` 已进入失败类型筛选。本片不改后端 API、不新增数据库表、不读取 raw artifact、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- Agent Quality Console C1 趋势指标可信度增强已完成。`/quality` 的趋势面板现在把通过 / 复查 / 失败运行展示为 `x / totalRuns`，避免只显示百分比或压缩状态字符串；`totalTokens` / `estimatedCost` 字段缺失时显示“暂无统计 / 暂无样本”，只有明确数值为 `0` 时才显示 `0`。失败类型 TopN 与复查类型 TopN 已改为卡片化展示，每项包含类型名称、次数、模块标签、说明和建议动作；Parser 相关失败桶单独归为 `Parser`，artifact JSON 解析坏文件仍归为 `Env`。本片不改后端 API、不新增数据库表、不展示 raw artifact 或敏感原文。
- Agent Quality Console B1 Eval Case 资产化安全摘要增强已完成。`agent-quality-eval-cases.json` 新增 `kb-agent-grounded-answer-route`、`document-parser-real-chain`、`memory-provider-small-sample` 三个默认 catalog case，把最新 KB Agent grounded answer、Document Parser real-chain 和 Memory provider 小样本纳入长期评测资产；每个 case 都包含 caseLayer、riskGate、scoringSummary、regressionPolicy、failureHistoryMarkers、lastVerifiedMarker 和 remediationHints。`/quality` 标签层同步将 `kb_agent`、`parser`、`memory` 显示为中文。该片同时修复 `RealShadowProviderEvaluationTest` 的旧 allowed tools，补入 `document_search_tool`，使宽 Eval 测试与当前 Agent search route 口径一致。
- Agent Quality Console A1 Agent Tool / Trace drill-down 安全摘要增强已完成。`QualityTraceStepDetail` 新增安全 `attributes`，用于展示 `decision`、`selectedTools`、`answerDecision`、`answerSelectedTools` 这类短枚举属性；`QualityArtifactServiceImpl` 会把 `knowledgeBaseAgent` gate 生成 `knowledge-base-agent-runtime` trace reference，并在步骤中区分 KB Agent search tool、search retrieval、answer tool 和 citation。`/quality/trace` 链路瀑布图新增“链路属性”列，用中文标签展示路由决策、检索工具、回答决策和回答工具。该能力仍只基于 ignored 脱敏 artifact，不读业务库、不新增数据库表、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- Agent Quality Console ABC 求职级增强循环已启动。当前目标不再继续堆叠 raw artifact 字段，而是围绕三条可演示的质量控制台能力推进：A. Agent Tool / Trace drill-down，把 KB Agent search / grounded answer 的 selector、tool、RAG retrieve、citation、no-evidence、权限负向和 failure bucket 串成脱敏链路摘要；B. Eval Case 资产化，让 case 能说明能力层、风险等级、评分规则、失败桶、最近验证 marker、修复建议和回归策略；C. Quality Console 趋势分析，基于最近 N 个 ignored 脱敏 artifact 展示 pass/review/fail、case pass rate、failure bucket、token/cost 和 latency 趋势。统一边界仍是 artifact-only、字段白名单、不新增数据库表、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- KB Agent answer route 前端真实可见性回归已完成。启动本地 backend / frontend 后，`/api/quality/runs/docpilot-cloud-quality-20260709164330-452624` 可读到 `knowledgeBaseAgent` gate 的 `answerCitations=6`、`answerCoversBothDocuments=true`、`answerNoEvidenceHandled=true`、`foreignKnowledgeBaseRejected=true`；浏览器 `/quality?autoload=1` 登录临时用户后，切到“门禁”并展开“已通过门禁”，可见最新 marker、`知识库 Agent`、`Agent 回答引用数`、`KB 回答覆盖两份文档` 和 `KB 无证据回答已处理`。桌面和 `390px` 移动端 console error 均为 `0`，移动端 `scrollWidth=clientWidth`，未见横向溢出；页面未出现 prompt 原文、answer 原文、文档全文或 evidence context 字样。
- Agent Quality Console 的 KB Agent answer 诊断可读性已增强。`/quality` 现在把 `knowledgeBaseAgent` 显示为“知识库 Agent”，补充 `answerCitations`、`answerDurationMs`、`answerDecisionPass`、`answerSuccess`、`answerCoversBothDocuments`、`answerNoEvidenceHandled` 等中文标签；`kbAnswerDecisionMismatch` 会归类为“KB Agent 回答路由不匹配”，并提示检查 grounded answer 分支、`KnowledgeBaseRagQaService` 调用和 KB answer route smoke case。RAG 摘要的“回答引用数”会计入 `answerCitations`，并新增“无证据已处理”事实。已验证前端 lint / build PASS，Playwright `/quality?routeSmoke=2` 桌面和 `390px` 移动端 console error 为 `0`，未见横向溢出。
- KB Agent grounded answer 真实 cloud smoke 已完成。`cloud-quality-smoke.ps1 -Mode run -SkipFrontend -EnableKnowledgeBaseAgentGate` 最新 marker `docpilot-cloud-quality-20260709164330-452624` 中，`knowledgeBaseAgent` gate 为 PASS：search route 返回 `decision=search_tool` / `knowledge_base_search_tool`，retrieve hits / citations 均为 `6`，覆盖两份主文档；answer route 返回 `decision=rag_tool` / `knowledge_base_rag_qa`，answer citations 为 `6`，同样覆盖两份主文档；no-evidence answer 边界和跨用户 KB 权限负向均通过。本次整体 run 为 REVIEW 仅因为有意 `-SkipFrontend`，`frontendRoutes` 为 REVIEW，不代表 KB Agent gate 失败。
- KB Agent answer route smoke gate 已扩展并完成真实验证。`cloud-quality-smoke.ps1 -EnableKnowledgeBaseAgentGate` 现在同时验证 KB Agent search route、grounded answer route、no-evidence answer 边界和跨用户 KB 拒绝；离线 `agent-kb-search-route-smoke.ps1` 最新 marker `docpilot-agent-kb-search-route-20260709164129-9a8972` PASS，真实 cloud marker `docpilot-cloud-quality-20260709164330-452624` 的 artifact redaction 为 PASS。Quality parser 已允许 `answerCoversBothDocuments` 和 `answerNoEvidenceHandled` 等安全布尔摘要进入 flags。
- KB Agent grounded answer route P0 后端闭环已完成并有真实链路证据。现有 `POST /api/ai/agent/knowledge-bases/{knowledgeBaseId}/run` 不新增 endpoint：`search_tool` 继续走 `knowledge_base_search_tool`；`rag_tool` / `qa_tool` / `summary_tool` 现在走 `KnowledgeBaseRagQaService.answer(...)`，返回用户可见 `finalAnswer`、citations、retrieval hits、documentHitCounts、retrieval mode、multi-query / rerank 摘要、`noEvidence`、fallback 和模型调用计数。answer step 名称为 `knowledge_base_rag_qa`，step 摘要只包含参数和计数，不包含 prompt、answer 原文、文档全文或 evidence context。
- KB Agent Quality Console 真实前端可见性回归已完成。本地 tunnel、backend、frontend 启动后，`/api/quality/runs/docpilot-cloud-quality-20260709153428-d25e54` 可读取 `knowledgeBaseAgent` gate：PASS，`retrieveHits=6`、`citations=6`、`coversBothDocuments=true`、`unsupportedIntentRejected=true`、`foreignKnowledgeBaseRejected=true`。浏览器 `/quality?autoload=1` 可见最新 marker，门禁页展开已通过门禁后可见“知识库 Agent 检索 / 通过”；桌面和 `390px` 移动端 console error 均为 0，未发现横向溢出。页面脱敏检查未命中 Authorization 凭据、API key、secret、password、连接串、evidence context、system prompt、answer raw 或 document full text。
- 当前 Quality Console 的 KB Agent 可见性边界：PASS 门禁默认压缩，前端已能显示 gate 名称和状态；`retrieveHits / citations / coversBothDocuments / answerCoversBothDocuments / answerNoEvidenceHandled / foreignKnowledgeBaseRejected` 等深层指标在 Quality API detail 中可见，但 PASS 折叠列表暂未逐项展示。下一片建议增强 `/quality` 的 KB Agent answer 诊断可读性。
- KB Agent Quality Console gate 诊断可读性已收口。`QualityArtifactServiceImpl` 现在会把 `knowledgeBaseAgent` gate 中的 `success`、`coversBothDocuments`、`unsupportedIntentRejected`、`foreignKnowledgeBaseRejected` 等安全布尔摘要提升为 flags；`/quality` 标签层新增“知识库 Agent 检索”“覆盖两份文档”“不支持意图已拒绝”“跨用户知识库已拒绝”等中文展示。仍不展示 raw task、prompt、answer、文档全文或 evidence context。
- KB Agent real-link runtime smoke gate 已完成真实验证。`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableKnowledgeBaseAgentGate`，开启后复用主 cloud smoke 的临时两文档 KB，验证 KB Agent retrieval-only API 真实调用 `knowledge_base_search_tool`、命中两份文档、P0 unsupported answer intent 被拒绝、用户 B 访问用户 A KB 被拒绝。真实 marker `docpilot-cloud-quality-20260709153428-d25e54` 中 `knowledgeBaseAgent` gate 为 PASS：`decision=search_tool`，selected tool 为 `knowledge_base_search_tool`，retrieve hits / citations 均为 `6`，documentHitCounts 覆盖两份主文档 `{782:3,783:3}`，unsupported intent rejected=true，foreign KB rejected=true。
- 本次真实 run 整体状态为 `REVIEW`，原因是本轮刻意使用 `-SkipFrontend` 聚焦 KB Agent API，因此 `frontendRoutes` 被标记为 REVIEW；这不代表 KB Agent gate 失败。artifact 只保存计数、决策、工具名和布尔摘要，不保存原始 task、prompt、answer、文档全文或 evidence context。
- Agent search smoke artifact 已接入 Quality Console 聚合。`QualityArtifactServiceImpl` artifact root 白名单新增 `backend/target/agent-search-route` 与 `backend/target/agent-kb-search-route`，因此单文档 Agent search route smoke 和 KB Agent search route smoke 的 ignored 脱敏 artifact 都能被 `/api/quality/runs` / `/quality` 发现；单测覆盖两个 root 的解析以及 prompt、answer、documentText、secret 等诱饵字段不泄露。
- `/quality` 已补 KB Agent route 诊断映射。`agent_search_route` / `agent_kb_search_route` 会显示为中文 case type；`kbSearchDecisionMismatch`、`kbUnsupportedIntentMismatch`、`kbScopeFailureNotPropagated` 分别归到 KB Agent 路由、P0 意图边界和权限失败透传类诊断，带模块标签、说明和建议动作。当前仍未做 KB Agent 真实 backend / tunnel runtime smoke。
- KB Agent search route smoke runner 已完成。新增 `scripts/smoke/agent-kb-search-route-smoke.ps1`，支持 `plan / dry-run / run`，用于离线验证 KB Agent P0：retrieval-only 任务执行 `knowledge_base_search_tool`，answer intent 被 P0 安全拒绝且不调用工具，`KNOWLEDGE_BASE_FORBIDDEN` 会透传为安全失败。最新离线 marker `docpilot-agent-kb-search-route-20260709152049-d529d6` 为 PASS，artifact redaction scan PASS。
- 新增 `AgentKnowledgeBaseSearchRouteSmokeTest` 和 `AgentKnowledgeBaseSearchRouteSmokeScriptSafetyTest`。artifact 只保存 marker、状态、caseId、expected / actual decision、selected tool、布尔结果、stepCount 和 failure buckets，不保存原始 task、prompt、answer 原文、文档全文、evidence context、token、凭据、云地址或连接串。当前仍未做 KB Agent 真实 backend / tunnel runtime smoke。
- KB Agent retrieval-only route MVP 已完成。新增独立 `POST /api/ai/agent/knowledge-bases/{knowledgeBaseId}/run`，由 `KnowledgeBaseAgentServiceImpl` 在 P0 中只处理 retrieval-only `search_tool` 意图并调用 `knowledge_base_search_tool`；回答、总结或 grounded QA 意图不会误走 search，而是返回 P0 仅支持检索证据的安全提示。该入口返回 `documentHitCounts`、retrieval mode、rerank / multi-query 数值、限长 hits / citations 和 step 摘要，不生成 answer，不持久化 KB Agent task。
- KB Agent P0 已验证 targeted 27 tests PASS，Agent / Tool / KB RAG broader 241 tests PASS（2 skipped）。当前仍未做真实 backend / tunnel runtime smoke，也未做 KB answer agent；下一片建议补 `agent-kb-search-route-smoke.ps1`，再做小样本真实链路验证。
- KB Agent route design 已完成。结论是不能把 `knowledge_base_search_tool` 硬塞进当前单文档 `DocumentAgentRequest`；P0 应新增独立 `KnowledgeBaseAgentRequest` / `KnowledgeBaseAgentService` / `KnowledgeBaseAgentController`，建议 API 为 `POST /api/ai/agent/knowledge-bases/{knowledgeBaseId}/run`。P0 只做 retrieval-only KB search route，调用 ToolCall API 的 `knowledge_base_search_tool`，返回安全检索摘要、`documentHitCounts`、retrieval mode、multi-query / rerank 数值和限长 citation preview，不生成 answer。
- KB Agent route P0 边界：不新增数据库表，不改 `KnowledgeBaseRagQaService` 主链路，不复用单文档 request 承载 KB，不做复杂 planner，不保存 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、云地址或连接串。下一片可直接进入后端 P0 service/controller + 单测。
- Agent search route smoke runner 已完成。新增 `scripts/smoke/agent-search-route-smoke.ps1`，支持 `plan / dry-run / run`，用于离线验证 retrieval-only Agent 任务走 `search_tool` / `document_search_tool`，grounded answer 任务继续走 `rag_tool` / `rag_qa_tool`。`run` 只执行默认跳过、显式环境变量启用的 JUnit smoke，不启动 backend / frontend / tunnel，不创建业务数据。
- 新增 `AgentSearchRouteSmokeTest` 和 `AgentSearchRouteSmokeScriptSafetyTest`。smoke artifact 只保存 marker、状态、caseId、expected / actual decision、selected tool、布尔结果、stepCount 和 failure buckets，不保存原始 task、prompt、answer 原文、文档全文、evidence context、token、凭据、云地址或连接串。最新离线 smoke marker `docpilot-agent-search-route-20260709101258-021654` 为 PASS，artifact redaction scan PASS。
- 已验证：`agent-search-route-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS；`mvn "-Dtest=AgentSearchRouteSmokeTest,AgentSearchRouteSmokeScriptSafetyTest,DocumentAgentServiceImplTest" test` PASS（17 tests，1 skipped）；Agent / selector / eval targeted 63 tests PASS（1 skipped）。本片没有新增数据库表、没有改 Agent API、没有接 KB Agent 路由、没有提交 artifact 原文、没有 push。
- 当前 Agent search 工具链状态：单文档 `document_search_tool`、多文档 `knowledge_base_search_tool`、单文档 Agent search intent 路由、Agent Quality Eval 路由门禁、Quality Console search diagnostics 和离线 search route smoke 均已完成。仍未做 KB Agent 路由，因为当前 `DocumentAgentRequest` 是单文档语义，下一片需要先设计 KnowledgeBase Agent request / context。

## 2026-07-08 当前补充

- Agent Quality Console search diagnostics 已完成。`/quality` 现在能把 Agent search route eval 的 `agent_search` 显示为“Agent 检索路由”，把 `expectedDecisionMatched` 显示为“路由决策匹配：是 / 否”，并把 `expectedDecisionMismatch` / selector / routing / search-overrouting / answer-overrouting 归类为“Agent 路由不匹配”，模块标签为 `Agent`，建议动作指向 `DocumentToolSelector`、LLM selector prompt 和 search / answer 意图评测用例。该片只改前端安全标签和分桶映射，不改后端 API，不读取 raw artifact。已验证前端 lint/build PASS，`/quality?routeSmoke=2` 桌面与 `390px` 移动端 console error 为 0 且无横向溢出。
- Agent search eval 路由质量门禁已完成。默认 `agent-quality-eval-cases.json` 新增 `agent-document-search-route` 与 `agent-rag-answer-route`，分别防止 retrieval-only 意图误走 QA 和 grounded answer 意图误走 search；`AgentQualityEvalRunner` 会对带 `scoringRules.expectedDecision` 的 case 真实调用 `DocumentToolSelector`，失败时标记 `expectedDecisionMismatch`。artifact 仅保存 caseId、tags、failure buckets、traceId / agentRunId 和 `expectedDecisionMatched` 数值，不保存原始 question、expectedBehavior、prompt、answer 原文、文档全文或 evidence context。离线 smoke marker `docpilot-agent-quality-eval-20260708231648-f178d4` PASS；`*Quality*` 回归 41 tests PASS（1 skipped）。
- Agent search intent 路由与评测门禁已完成。单文档 Agent 现在能把 retrieval-only 意图路由到 `document_search_tool`，例如检索 topK、展示相似度、列出来源或 citation list；需要回答、解释、总结并引用证据或“说明”事实的任务继续走 `rag_qa_tool`。`DocumentAgentServiceImpl` 对 `search_tool` 只返回检索摘要和限长引用预览，不生成业务答案，不返回完整 chunk content、文档全文、prompt 或 answer 原文。已验证 targeted 53 tests PASS、selector eval 27 tests PASS、Agent/Tool/RAG broader 212 tests PASS（1 skipped）。当前仍未做 KB Agent 路由，因为现有 `DocumentAgentRequest` 是单文档上下文语义。
- KnowledgeBase `knowledge_base_search_tool` 已完成最小闭环。ToolCall API 现在可调用多文档 retrieval-only 工具，复用 `KnowledgeBaseRagRetrievalService`，返回脱敏 `documentHitCounts`、retrieval mode、rerank / multi-query 摘要和限长 evidence previews；不生成 answer，不返回完整 chunk content、文档全文或 prompt。当前 Agent 旧关键词路由仍未切到 search intent，后续需要单独做路由与评测门禁。
- 单文档 `document_search_tool` 已完成最小闭环。ToolCall API 现在除 `document_status_tool` 和 `rag_qa_tool` 外，也可调用 `document_search_tool`；该工具复用现有单文档 RAG retrieval 和 scope guard，只返回脱敏、限长的 retrieval hits / citations 摘要，不生成 answer，不返回完整 chunk content 或文档全文。当前仍未改 Agent 旧路由，后续再做 KB search tool 与 search intent routing。
- Agent 工具当前进入 Document Search Tool 求职级增强。现状是 `rag_qa_tool` 已能通过现有 RAG QA 链路返回 answer、retrieval hits 和 citations，但项目还缺少一等 retrieval-only `document_search_tool`，导致 Agent 在“先搜证据 / 展示检索命中 / 诊断漏召回”场景只能绕到 QA 工具。下一片以单文档 search tool 为最小闭环，复用 `RagDocumentRetrievalService` 与既有 scope guard，不返回完整 chunk content、文档全文、prompt 或 answer 原文。
- Document Parser 真实链路质量回归与 Quality Console 可见性收口已完成。最新真实 marker `docpilot-parser-real-chain-20260708212742-0f9baa` 为 PASS，覆盖 tunnel、受控 backend、frontend、临时用户、PDF / HTML / DOCX 上传与异步解析、chunk、RAG QA retrieval、citation、source locator、parser boundary 和 artifact redaction；三类文件均 `parseStatus=SUCCESS`、`chunkCount=1`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`，负向边界 `4/4` PASS。
- `document-parser-real-chain-smoke.ps1` 已收口受控服务策略：默认不再静默复用已有 backend / frontend，只有显式传 `-ReuseRunningServices` 才复用；runner 自己启动 backend 时使用子进程环境设置 `AI_MODE=mock` 和 `APP_QUALITY_CONSOLE_ENABLED=true`，避免真实 provider 超时或未开启 Quality Console 造成误判，不写入配置文件。
- Quality Console artifact 聚合已修复工作目录漂移问题。`QualityArtifactServiceImpl` 会从当前工作目录向上解析仓库根，后端从 `backend/` 或 `backend/target/classes` 启动时也能扫到 `backend/target/smoke/document-parser-real-chain`；`parserQuality` 现在白名单返回 `directRetrieveHitCount` 与 `qaRetrievalHitCount` 两个安全计数。
- `/quality` 的文档解析质量摘要新增“检索来源”，展示“直接 / 问答”计数。最新 PASS run 中 `directRetrieveHitCount=0`、`qaRetrievalHitCount=3`、`citationCount=3`，说明 parser QA 主链路可用，但 direct retrieve endpoint / query 语义仍值得后续单独排查。
- 已验证 `mvn "-Dtest=*Quality*" test` PASS（40 tests，1 skipped）、`document-parser-real-chain-smoke.ps1 -Mode plan` PASS、`-Mode dry-run` PASS、`-Mode run` PASS、`npm run lint` PASS、`npm run build` PASS、Quality API 最新 run 可见、浏览器 `/quality?autoload=1` 桌面与 `390px` 移动端无横向溢出且 console error 为 `0`。本片不提交 artifact 原文、不删除业务数据、不改 schema、不 push。
- Document Parser 解析质量报告 / Quality Console parser 诊断增强已完成。`scripts/smoke/document-parser-real-chain-smoke.ps1` 的 `run` artifact 新增脱敏 `parserQualityReport`，从现有安全摘要派生文件类型覆盖、解析成功率、source locator 覆盖、RAG retrieve / citation 覆盖、错误边界通过率、warning 统计可用性和 review reasons；不保存文档全文、prompt、answer、evidence context、异常堆栈、secret、连接串或云地址。
- Agent Quality Console 后端 `QualityRunDiagnostics` 已新增 `parserQuality` 安全摘要，`QualityArtifactServiceImpl` 只按白名单读取 parser report 中的数值、布尔值和安全短 bucket；未知字段和敏感字段不会透传到 API。
- `/quality` 的“文档解析质量摘要”已从普通 gate 数字升级为诊断卡展示：格式覆盖、解析成功率、检索与引用覆盖、错误边界；缺失 token / parser 指标继续显示“暂无统计”，不会把缺样本当作 0。
- 已验证 `mvn "-Dtest=*Quality*" test` PASS（39 tests，1 skipped）、`document-parser-real-chain-smoke.ps1 -Mode plan` PASS、`-Mode dry-run` PASS、`npm run lint` PASS、`npm run build` PASS、Playwright `/quality?routeSmoke=2` 桌面和 `390px` 移动端无 console error、无横向溢出。本片没有启动真实 run、没有创建业务数据、没有提交 artifact 原文、没有 push。
- 当前边界：parser 主链路仍保持 MVP 范围，不做 OCR、扫描件识别、外部网页抓取、旧 `.doc`、复杂版面还原或 PDF 坐标级 citation；下一片应通过真实 `document-parser-real-chain-smoke.ps1 -Mode run` 验证 parser report 在 `/quality?autoload=1` 的可见性，并继续扩 fixture corpus v3。

## 2026-06-29 当前补充

- 2026-07-06 Agent Quality Console 已接入 Document Parser 指标展示。`/quality` 的 Artifact 分区新增“文档解析质量摘要”，基于已有 `parserRealChain` / `parserBoundary` gate 展示解析成功文件、切片总数、检索 / 引用、来源定位、解析失败数、运行耗时、负向边界通过数和不支持格式拒绝；前端标签层补充 parser gate / metrics 中文展示。本片只使用 Quality API 既有脱敏数值 / 布尔字段，不读取 raw artifact，不展示文档全文、prompt、answer、evidence context、异常堆栈或任何凭据。已验证 `npm run lint` PASS、`npm run build` PASS，Playwright mock Quality API 桌面和 `390px` 移动端 PASS，端口已清理释放。
- 2026-07-06 Document Parser fixture corpus v2 已完成。HTML parser 现在保留表格单元格分隔文本，DOCX parser 现在将编号或 list 样式段落标记为 `BlockType.LIST` 并继承 `sectionPath`；`DocumentParserTest` 增强为多页 PDF 空页 warning、HTML h1/h2 / 表格 / 列表 / 独立链接 / 噪声剔除、DOCX Heading1/Heading2 / 段落 / 列表 / 表格 fixture。已验证 `DocumentParserTest` 7 tests PASS、parser / parse consumer / file reader 44 tests PASS，并复跑真实 `document-parser-real-chain-smoke.ps1 -Mode run` PASS，marker `docpilot-parser-real-chain-20260706215802-78374c`，PDF / HTML / DOCX parse、chunk、retrieve、QA citation、source locator 和 parserBoundary 均 PASS。
- 2026-07-06 Document Parser 错误边界 API 负向增强已完成。`scripts/smoke/document-parser-real-chain-smoke.ps1` 的 `parserBoundary` gate 现在真实执行 upload / document create / parse task create / parse polling，并只保存白名单失败码；真实 marker `docpilot-parser-real-chain-20260706215134-857b73` PASS，覆盖不支持格式上传拒绝、空白 TXT `PARSER_EMPTY_CONTENT`、损坏 PDF / DOCX `PARSER_CORRUPTED_FILE`，`negativeCasePassCount=4/4`、`negativeCaseFailCount=0`、`unsupportedUploadRejected=true`。本片不提交 artifact 原文，不删除业务数据，不改 schema，不做 OCR / 扫描件 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原。
- 2026-07-06 Document Parser source locator 已贯通到 RAG citation。新增 `RagSourceBlock`，parse success 后 `ParseTaskConsumeEntryServiceImpl` 会把 `ParseResult.blocks()` 交给 `RagIndexingTriggerService`；`ChunkingService` 将 parser block 的 `pageNumber`、`sourceLocator`、`blockType`、`sectionPath`、`structureType` 写入 chunk metadata；`RagIndexingServiceImpl` 将这些脱敏 locator 写入 embedding metadata 和 vector payload；单文档 RAG retrieve hit / QA citation response 现在返回 `pageNumber`、`sourceLocator`、`blockType`。HTML / DOCX heading block offset 已与 `fullText` 中的 Markdown heading 对齐。已验证 targeted 56 tests PASS、parser/RAG broader 88 tests PASS、`document-parser-real-chain-smoke.ps1` plan / dry-run / run PASS，真实 marker `docpilot-parser-real-chain-20260706214209-dcb8f2` 覆盖 PDF / HTML / DOCX 上传、parse、chunk、retrieve、QA citation 和 `sourceLocatorPresent=true`。本片不新增数据库表、不改 schema、不做 OCR / 扫描件 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原 / PDF 坐标级 citation，不提交 artifact 原文，不 push。
- 2026-07-06 Document Parser MVP 已完成真实链路验证。HTML / DOCX parser 现在在 `fullText` 中保留 Markdown heading，现有 chunker 可生成 `sectionPath` / `structureType`；单文档 RAG citation 返回脱敏 `sourceName`、`sectionPath`、`structureType`。新增 `scripts/smoke/document-parser-real-chain-smoke.ps1`，支持 `plan / dry-run / run`；真实 run marker `docpilot-parser-real-chain-20260706172220-f03956` PASS，覆盖本地 tunnel、backend、frontend、临时用户、PDF / HTML / DOCX 上传、parse `SUCCESS`、chunk count、RAG retrieve、QA citation、source locator、unsupported format boundary 和 artifact redaction。Agent Quality Console 已纳入 `backend/target/smoke/document-parser-real-chain` artifact root，并展示 parser smoke 的安全聚合指标。本轮不新增数据库表、不改 schema、不做 OCR / 扫描件 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原，不提交 artifact 原文，不 push。
- 2026-07-06 Document Parser MVP 第一片已完成离线收口。后端新增统一 `DocumentParser` / `ParserRegistry` / `ParseResult` 抽象，支持 `txt / md`、文本型 PDF、上传的本地 HTML 和 DOCX 基础文本抽取；PDF 保留页级 block，HTML 去除 `script/style/nav/footer/header` 等噪声，DOCX 抽取段落、标题和表格文本。解析消费侧已接入 parser registry，解析成功后继续进入既有 summary、chunking、RAG indexing trigger 和状态流转；新增 parser metrics 记录 parserName、duration、extractedChars、pageCount、blockCount 和 warningCount，不打印文档全文。上传 allowlist 已扩到 `pdf/md/txt/html/htm/docx`，配置新增 `APP_DOCUMENT_PARSER_MAX_FILE_SIZE_BYTES` 和 `APP_DOCUMENT_PARSER_TIMEOUT_MS`。已验证 targeted parser / parse / file tests 44/44 PASS，chunk/index/retrieval 回归 34/34 PASS，`mvn -DskipTests compile` 此前已 PASS；本片未启动真实 tunnel / backend / frontend，未创建业务数据，未做云端 PDF/HTML/DOCX runtime smoke。当前边界：不支持 OCR、扫描件、复杂版面还原、外部网页抓取、`.doc` 旧格式或 PDF 坐标级 citation。
- 2026-07-06 Agent Quality Console 真实可见性回归已完成。本地 tunnel 可用，backend `/actuator/health` 为 `UP`；前端通过 `next build` + `next start -p 3007` 启动后，临时 smoke 用户打开 `/quality?autoload=1` 可见质量运行记录、`totalRuns` 分母提示和“暂无统计”缺样本语义。Playwright 验证桌面 `1440px` 与移动端 `390px` 均无横向溢出，console error 为 `0`。本轮只创建临时登录用户，未上传文档、未创建 KB / Conversation、未提交 artifact 原文、未 push。
- 2026-07-06 Agent Quality Console token 文案一致性收口已完成。`/quality` 中面向用户的 `Token / TOKENS / tokens` 混用显示已统一为“token 数”“token 用量”“token 增量”，诊断卡优先排查文案同步改为“模型调用 / token 用量 / 重试”。本片只改前端展示文字，不改 DTO、不改 API、不新增功能。
- 2026-07-06 Agent Quality Console 延迟指标缺样本语义修正已完成。`/quality` Trend 面板“平均延迟”在缺少样本时显示“暂无统计”，Overview 的 P95 延迟说明明确依赖最近 trend points 的 `latencyMs`，没有 point 样本时不硬算。本片只改前端展示语义，不改后端 API、不新增数据库表、不读取 raw artifact。
- 2026-07-06 前端文档详情错误提示乱码兜底清理已完成。`frontend/app/documents/[documentId]/page.tsx` 的文档错误 hint 不再匹配历史 mojibake 字面量，只基于统一 API 错误归一后的正常中文 `无权` / `不存在` 生成提示；`frontend/lib/api.ts` 已负责把文档和知识库权限 / 不存在错误归一为中文。已验证 `npm run lint` PASS、`npm run build` PASS，前端源码和本轮文档乱码扫描无命中。
- 2026-07-06 Agent Quality Console 质量指标可信度与失败桶可行动化增强已完成。`/quality` 的通过率、复查率、失败率现在展示分子 / 分母并明确分母为 `totalRuns`；`token_usage` 或 `estimatedCost` 缺失时显示“暂无统计”或“暂无样本”，只有明确数值为 `0` 时才展示 `0`。质量诊断卡新增“优先排查”字段，失败 / 复查类型 TopN 新增模块标签、次数、说明和建议动作，无法归类时保留 `Unknown / 其他` 并提示需要补充 bucket 映射规则。本片只改前端安全映射，不改后端 API、不新增数据库表、不读取 raw artifact、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。已验证 `npm run lint` PASS、`npm run build` PASS，Playwright mock Quality API 桌面和 `390px` 移动端 PASS。
- 2026-07-06 Agent Quality Console P1 parser 安全摘要增强已完成。后端 `QualityRunDetail` 新增 `diagnostics`，从 artifact 白名单字段聚合 `documentCoverage`、`toolQuality` 和 `memoryQuality` 三组安全数值摘要；`documentHitCounts` 只转成覆盖数量、零命中文档数和 min/max 命中数，不返回文档 ID 或原始 map。前端 `/quality` 的 RAG / 记忆 / 工具调用诊断卡已展示命中文档分布、记忆命中摘要和工具参数复查摘要；缺字段时降级为“暂无安全摘要”。本片不新增数据库表、不新增 API、不读取业务库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。已验证 `mvn "-Dtest=*Quality*" test` PASS，38 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright mock Quality API 桌面和 `390px` 移动端 PASS，端口已清理释放。
- 2026-07-06 Agent Quality Console 诊断指标 P0 已完成。`/quality` 在不改后端 DTO / API、不读取 raw artifact 的前提下，基于现有脱敏 `runs`、`trend`、`QualityRunDetail` 和 eval catalog 派生质量诊断比率。Overview 新增通过率、复查率、失败率、P95 延迟、平均 tokens、成功运行成本，以及失败 / 复查类型 TopN 和建议动作；Run Detail 新增独立“评测”tab，RAG / 记忆 / 工具调用 tab 增加诊断比率卡，Failures tab 增加当前失败、复查、新增失败和已恢复失败类型的建议动作。P0 明确只做安全派生指标，`documentHitCounts`、严格工具选择准确率、工具参数准确率、记忆有用命中率和记忆噪声率仍标记为后续 parser / eval schema 扩展项。本片不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；token usage、latency、cost 仍只作为数值统计展示。已验证 `npm run lint` PASS、`npm run build` PASS。
- 2026-07-06 Agent Quality Console 前端信息架构重构已完成。`/quality` 现在从长页面数据堆叠改为内部质量排查控制台：顶部 Overview 先给出最近 run 健康判断和 PASS / REVIEW / FAILED / token 统计；左侧运行记录支持状态筛选和 marker / 来源搜索；右侧 Run Detail 通过“摘要 / 门禁 / 待处理 / 链路 / RAG / 记忆 / 工具调用 / Artifact”分区排查。FAILED / REVIEW 门禁默认展开，PASS 门禁和已通过 eval case 默认折叠；失败或需复查 eval case 可直接“查看 Trace”；Artifact 区只展示脱敏元信息，Eval Catalog / Trend 移到非首屏分区。Gemini CLI 可用性检查通过，但正式建议调用返回 malformed / empty response，本轮按协作约束由 Codex 直接集成、验证和回写。已验证 `npm run lint` PASS、`npm run build` PASS，Playwright 静态 route smoke 与 mock Quality API smoke 覆盖桌面和 `390px` 移动端，无 console error、无横向溢出。本片不改 Quality API、不新增后端字段、不读取业务库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；token usage 仍只作为数值统计展示。
- 2026-07-06 Agent Quality Console 前端中文化二次增强已完成。`/quality` 和 `/quality/trace` 的默认正文现在优先使用纯中文，不再显示 `通过 (PASS)`、`RAG 漏召回 (RAG_RETRIEVAL_MISS)`、`Agent 质量 (agent_quality)` 这类“中文 + raw key”形式；必要的 raw key 只保留在 `title` 悬停信息或 `marker`、`caseId`、`traceId`、`agentRunId` 等技术定位 ID 中。页面标题和计数文案已从 `Overview / Eval Catalog / Quality Trend / Run Detail / Run Comparison / Trace Reference / Gate / Eval Case` 收敛为“质量总览 / 评测用例库 / 质量趋势 / 运行详情 / 运行对比 / 链路定位 / 门禁 / 评测用例”。Gemini CLI READY 探测通过，但正式建议调用超时，本轮按约束由 Codex 直接集成、验证和回写。已验证 `npm run lint` PASS、`npm run build` PASS，Playwright route smoke 桌面和 `390px` 移动端均无业务 console error、无横向溢出、无典型 raw key 括号残留。本片不改 Quality API、不新增后端字段、不读取业务库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；token usage 仍只作为数值统计展示。
- 2026-07-06 Agent Quality Console 前端可读性增强已完成。新增 `frontend/lib/quality-labels.ts` 作为前端安全展示术语层，集中把 status、failure bucket、metric、flag、case type、gate 和 trace step 映射为中文标签；二次增强后默认正文已改为纯中文展示，raw key 不再作为括号注释混入中文字段。`/quality` 的质量总览、评测用例库、质量趋势、运行详情、失败分桶、链路定位、门禁 / 评测用例明细、模型与成本摘要、运行对比以及 `/quality/trace` 的链路瀑布图、关联门禁 / 评测用例已同步改为更易读的中文展示。本片不改 Quality API、不新增后端字段、不读取业务库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；token usage 仍只作为数值统计展示。已验证 `npm run lint` PASS、`npm run build` PASS，Playwright route smoke 覆盖 `/quality?routeSmoke=2` 与 `/quality/trace?routeSmoke=1...` 桌面和 `390px` 移动端，均无业务 console error、无横向溢出，端口已清理释放。
- 2026-07-05 Agent Quality Console Trace / Eval / Trend 真实链路回归已完成。最终真实审计 marker `docpilot-real-user-qa-20260705210119-7b8092` PASS；核心 RAG、KnowledgeBase、shortDocumentRag、naturalCorpus、multiQueryRag、answerGrounding、noEvidenceThreshold、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。`cloud-quality-smoke.ps1` 现在为真实审计写入脱敏 trace case result，使 Quality API detail 可返回 `traceReferenceCount=2`，并在 `/quality/trace` 展示链路瀑布图；这些字段只包含 `caseId/status/traceId/conversationId` 与数值 / 布尔指标，不包含用户消息、answer 原文、文档全文或 evidence context。Console 可见性已验证：`/api/quality/runs/{marker}` 返回 `summary.status=PASS`、`gateCount=22`、`evalCaseCount=27`、`traceReferenceCount=2`；`/api/quality/eval-cases` 返回 7 个 case；`/api/quality/trends?limit=20` 返回 20 个趋势点；浏览器 `/quality?autoload=1` 和 `/quality/trace` 桌面 / `390px` 移动端无 console error、无横向溢出。中途 `docpilot-real-user-qa-20260705205210-8c882e` 暴露 KB 阶段偶发 `TypeError` 且旧 gate 诊断不足，已记为 `REA-20260705-P3-008`；本轮先增强 frontendInteraction 的脱敏 console error 诊断，最终 PASS run 未复现。
- 2026-07-05 Agent Quality Console Quality Trend v1 已完成。后端新增 `QualityTrendSummary`、`QualityTrendPoint` 和 `QualityRepeatedCaseSummary`，并通过 `GET /api/quality/trends?limit=20` 返回最近 N 个脱敏 artifact 的趋势摘要；趋势覆盖状态分布、failure / review bucket 计数、平均 casePassRate、token / cost、latency / duration 和反复失败 / REVIEW case。前端 `/quality` 新增 `Quality Trend` 面板。该能力只使用现有 Quality DTO 的状态、计数、数值、caseId、marker 和 bucket 摘要，不展示原始 artifact、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。已验证 `mvn "-Dtest=*Quality*" test` PASS，37 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；`/quality?routeSmoke=2` 移动端无横向溢出，端口已清理释放。
- 2026-07-05 Agent Quality Console Eval Asset v2 已完成。7 个默认 `agent-quality-eval-cases.json` case 新增 `caseLayer`、`riskGate`、`scoringSummary`、`regressionPolicy` 和 `failureHistoryMarkers`，让 catalog 能说明 case 分层、风险门禁、评分摘要、回归策略和历史失败 / 修复 marker。`QualityEvalCaseCatalogItem`、`QualityEvalCatalogServiceImpl`、`GET /api/quality/eval-cases` 和 `/quality` Eval Catalog 已同步展示这些安全字段；failure history 只保存脱敏 marker、status 和 issue id 摘要，不返回 question、expectedBehavior、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。已验证 `mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；`/quality?routeSmoke=2` 移动端无横向溢出，端口已清理释放。
- 2026-07-05 Agent Quality Console Trace Drill-down v3 已完成。后端新增 `QualityTraceStepDetail`，`QualityTraceReference` 增加 `steps`；`QualityArtifactServiceImpl` 能从 eval case 的安全 metrics / flags / buckets 推断 `eval_case`、`agent_step`、`rag_retrieve`、`tool_call`、`model_call`、`citation` 和 `failure_bucket` 步骤摘要。前端 `/quality/trace` 新增“链路瀑布图”面板，只展示 step type、status、metrics、flags 和 buckets，不返回 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。已验证 `mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright route smoke 无 console error，`390px` 宽度无横向溢出，端口已清理释放。
- 2026-07-05 Agent Quality Console 三线升级收口已完成。后续求职级升级只围绕三条主线推进：Trace Drill-down v3 把 trace reference 升级为脱敏链路瀑布图；Eval Asset v2 把 7 个 eval catalog case 继续推进为长期质量资产；Quality Trend v1 基于最近 N 个脱敏 artifact 展示状态、case pass rate、失败桶、token / cost、latency 和反复失败 case 趋势。该路线继续默认 artifact-only、不新增数据库表、不返回 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；Trace Drill-down v3 已完成，下一片以 `CURRENT_TASK.md` 中的 Eval Asset v2 为准。
- 2026-07-05 Agent Quality Console 7-case 真实审计回归已完成。`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705192354-eba0fc`；核心 RAG、KnowledgeBase、Conversation Trace、Memory、权限隔离、frontendInteraction 和 artifact redaction 均 PASS。Console 可见性已验证：`/api/quality/eval-cases` 返回 7 个 case，其中 4 个带 `sourceIssueIds`，7 个带 `remediationHints`；浏览器 `/quality?autoload=1` 桌面和 `390px` 移动端无 console error、无横向溢出。后续 `mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 2026-07-05 Agent Quality Console Eval Catalog 筛选 v1 已完成。`/quality` 的 Eval Catalog 支持按 risk、owner 和 latest status 本地筛选；不新增后端 API、不新增数据库表。已验证 `npm run lint` PASS、`npm run build` PASS，Playwright `/quality?routeSmoke=2` 桌面与 `390px` 移动端无横向溢出、console error 为 `0`，端口已清理释放。
- 2026-07-05 Agent Quality Console Eval Catalog Remediation Hint v1 已完成。默认 7 个 eval catalog case 增加 `lastVerifiedMarker` 和 `remediationHints`，`/api/quality/eval-cases` 与 `/quality` Eval Catalog 同步展示上次验证 marker 和修复排查方向；字段仍走白名单过滤，不返回 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。已验证 `mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 2026-07-05 Agent Quality Console Real Audit Case 扩容 v1 已完成。默认 `agent-quality-eval-cases.json` 从 3 个 case 扩到 7 个，新增短文档单文档 RAG evidence、短文档 KB 双文档覆盖、summary 干扰 citation 裁剪和 Quality Console backend health 四类真实审计沉淀 case；`sourceIssueIds` 只暴露 `REA-...` 脱敏问题编号，API / 前端仍不返回 question、expectedBehavior、mustContain、mustNotContain、prompt、answer 原文、文档全文、evidence context 或凭据。已验证 `mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 2026-07-05 Agent Quality Console Eval Case Version v1 已完成。默认 `agent-quality-eval-cases.json` 增加 `caseVersion`、`owner`、`lastUpdated` 和 `riskLevel`；`GET /api/quality/eval-cases` 与 `/quality` Eval Catalog 卡片同步展示这些安全元数据。离线 eval runner 对未知 JSON 字段保持兼容，result artifact 仍不保存 question、expectedBehavior、mustContain、mustNotContain、prompt、answer 原文、文档全文或 evidence context。
- 2026-07-05 Agent Quality Console Trace Detail 最小入口已完成。前端新增内部 `/quality/trace` 页面，从现有 QualityRunDetail 中按 marker / caseId / traceId / agentRunId / conversationId 定位脱敏 trace reference，并展示关联 gate / eval case 的安全 metrics、flags 和 failure / review buckets；`/quality` Trace 定位行新增“打开”链接。本片未新增后端 API、不读业务库、不展示 prompt、answer 原文、文档全文或 evidence context。
- 2026-07-05 Agent Quality Console 求职展示打磨已完成。README 与 showcase 面试材料已从旧的“Agent 文档问答 demo”口径更新为“企业文档知识库 RAG + 会话记忆 + 内部质量门禁”口径；展示优先级调整为 `/quality` 质量闭环、KnowledgeBase / Conversations 的 RAG + Memory + Trace、文档详情 quote-level citation，最后再展示 Agent 工具链。对外材料明确 Agent Quality Console 是内部质量控制台，不是企业级 APM；真实 audit / eval 是小样本质量证据，不写成线上 SLA 或大规模 benchmark。
- 2026-07-05 Agent Quality Console 真实体验审计集成 v2 已完成。首轮真实 audit `docpilot-real-user-qa-20260705164732-f54da1` 暴露 Quality Eval Catalog service 构造器注入缺失导致 backend health BLOCKED；已补 `@Autowired` 和 Spring context 防回归测试。复跑真实审计 `docpilot-real-user-qa-20260705165151-bbe588` PASS，核心 RAG / Memory / Trace / 权限 / frontendInteraction / artifact redaction gate 均通过。开启 Quality Console 后，`/quality?autoload=1` 可见最新 marker、Eval Catalog、Failure Triage、Run Comparison 和 Model / Cost Summary，console error 为 `0`，移动端无横向溢出。
- 2026-07-05 Agent Quality Console Cost / Latency / Model Summary v1 已完成。Quality artifact parser 允许 `latencyMs`、`durationMs`、`estimatedCost` 等安全数值进入 metrics；`/quality` Run Detail 新增 `Model / Cost Summary` 面板，聚合展示 token usage、estimated cost、model calls、tool calls、latency、duration 和 retry 数值。本片仍不返回 prompt、answer 原文、provider 原始输出、文档全文或 evidence context。
- 2026-07-05 Agent Quality Console Run Comparison v1 已完成前端最小闭环。`/quality` Run Detail 新增 `Run Comparison` 面板，可选择 previous run，并展示 status、gate count、failed / review gate、token total、casePassRate、失败桶、gate status 和 eval case status 的脱敏差异。本片复用现有 Quality API，不新增 compare endpoint、不新增数据库表、不展示原始 artifact 或敏感原文。
- 2026-07-05 Agent Quality Console Eval Case Catalog v1 已完成。后端新增 `GET /api/quality/eval-cases`，从现有 `agent-quality-eval-cases.json` 读取安全 case 目录，并关联最近 Quality run 的 latest status / marker / traceId / agentRunId；前端 `/quality` 左侧新增 `Eval Catalog` 卡片。该 catalog 只返回 caseId、caseType、tags、expectedEvidence、expectedTools、scoringRules 和最近状态，不返回 question、expectedBehavior、mustContain、mustNotContain、answer 原文、prompt、文档全文或 evidence context。
- 2026-07-05 Agent Quality Console Failure Triage v1 已完成前端定位闭环。`/quality` Run Detail 新增 Failure Triage 面板，支持按 status、失败桶 taxonomy、gate name 和 case type 过滤 Gate、Eval Case 与 Trace 定位项；失败桶归一化覆盖 RAG retrieval miss、citation unsupported、distractor citation、no-evidence false positive、memory conflict、tool failure、permission regression、frontend UX 和 env blocked。本片未新增后端 API、未新增数据库表，仍只展示现有脱敏 Quality DTO。
- 2026-07-05 Agent Quality Console Trace Drill-down v2 已完成第一片轻量定位入口。后端 `QualityRunDetail` 现在包含脱敏 `traceReferences`，从 artifact 中递归收集 eval case 的 `traceId` / `agentRunId` / `conversationId`、父级 `gateName` 和 failure / review buckets；前端 `/quality` Run Detail 新增“Trace 定位”面板和复制 ID 按钮。该能力只做定位摘要，不新增数据库表、不读取业务库、不返回 prompt、answer 原文、文档全文或 evidence context。
- 2026-07-05 Agent Quality Console 求职级升级路线图已沉淀为 `docs/ai-dev/ROADMAP_AGENT_QUALITY_CONSOLE.md`。后续自驱循环以该文档为长期路线图，当前任务仍以 `CURRENT_TASK.md` 为准；下一片优先进入 Trace Drill-down v2，让失败 / REVIEW eval case 能定位 `traceId` / `agentRunId`。
- 2026-07-05 Agent Quality Console Explainability v1 已完成。后端 Quality artifact 聚合现在能解析 cloud quality / real-user audit 的嵌套 `gates.*`，并把单个 `checks` object 中的安全 metrics / flags 聚合到 gate；eval case detail 暴露脱敏 `metrics` / `flags`，不返回 question、answer 原文、文档全文、prompt 或 evidence context。
- `/quality` Run Detail 已展示 gate / eval case signals。真实回归 marker `docpilot-real-user-qa-20260705151944-950f42` PASS；Console autoload 验证可见最新 marker、`naturalCorpus`、`CASEPASSRATE`、`DISTRACTORCITATIONFREECOUNT` 和 eval case `ops-incident-support-summary`，console error count 为 `0`。本轮 `naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`。
- 2026-07-05 `REA-20260704-P2-006` 已收口为 `VERIFIED`。KnowledgeBase QA 的答案后 citation 后处理新增极低分引用裁剪：只在 summary / compare 等多文档意图下、且裁剪后仍保留至少两份文档 coverage 时生效；retrieval hits 和 `documentHitCounts` 继续保留，便于 Trace / audit 诊断。
- 本轮验证：`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest" test` PASS；`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS；真实 `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705145304-7a53b8`。该 run 中 `naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`，frontendInteraction、Memory quality、Conversation Trace、权限隔离和 artifact redaction 均 PASS。
- 2026-07-04 Agent Quality Console MVP Slice 6 已完成真实链路回归与 Console 可见性验证。离线 `agent-quality-eval-smoke.ps1 -Mode run` PASS，marker `docpilot-agent-quality-eval-20260704221655-48a5cf`；真实 `real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 完成，marker `docpilot-real-user-qa-20260704221704-4abc6f`，当时整体状态 `REVIEW`。
- 该轮真实审计中 tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk 质量、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、multi-query、answer grounding、no-evidence、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS；`naturalCorpus` 有 1 个 review bucket：`ops-incident-support-summary:distractorCitation`，已记为 `REA-20260704-P2-006` 并在 2026-07-05 修复验证。
- Agent Quality Console 真实可见性已验证：开启 `app.quality.console.enabled=true` 后，`GET /api/quality/runs`、`GET /api/quality/runs/{marker}` 和浏览器 `/quality?autoload=1` 都能看到 `docpilot-real-user-qa-20260704221704-4abc6f` 的 `REVIEW` 状态；浏览器 console error count 为 `0`。
- 本轮修复了 Quality service 的 Spring 装配缺口：`QualityArtifactServiceImpl` 显式标注构造器注入，并新增 `QualityArtifactServiceSpringContextTest`，避免后端真实启动时因 service 构造器选择失败而 health timeout。
- 2026-07-04 Agent Quality Console MVP Slice 5 已完成前端 `/quality` P0 页面。新增 `frontend/lib/quality-api.ts` 和 `frontend/app/quality/page.tsx`，页面包含 Overview + Run Detail，展示 run 状态、gate 统计、失败桶、token usage / cost 数值、gate 列表和 eval case 结果；Trace / Eval / Failures 作为预留入口。
- `/quality` 当前是内部页面，不在普通用户主流程中展示入口。默认打开页面不自动请求后端，避免未启动 backend 或旧 token 影响 route smoke；真实完整链路验证可用 `/quality?autoload=1`。
- 前端 route smoke 已验证：`npm run lint` PASS，`npm run build` PASS，Playwright 打开 `/quality` 无 console error，`390x844` 未见横向溢出。新增 `frontend/app/icon.svg` 解决 favicon 404。
- 2026-07-04 Agent Quality Console MVP Slice 4 已完成轻量 Agent Quality Eval Case JSON + Runner。新增 JSON case fixture、离线 runner、默认跳过的 smoke writer 和 `scripts/smoke/agent-quality-eval-smoke.ps1`；脚本支持 `plan` / `dry-run` / `run`，run 只执行离线 JUnit 并生成 ignored 脱敏 artifact。
- `backend/target/agent-quality-eval` 已加入 Quality artifact 聚合白名单。Agent Quality Eval artifact 只保存 marker、status、caseId、caseType、passed、traceId / agentRunId、失败桶和聚合计数，不保存 question、expectedBehavior、answer 原文、prompt、文档全文或 evidence context。
- 真实本地离线 run marker `docpilot-agent-quality-eval-20260704220047-9c9af0` PASS；该结果证明 eval contract / artifact 安全链路可用，不代表真实 Agent 大规模效果评测。
- 2026-07-04 Agent Quality Console MVP Slice 3 已完成后端 Quality API。新增 `GET /api/quality/runs` 与 `GET /api/quality/runs/{marker}`，返回 Slice 2 的脱敏 `QualityRunSummary` / `QualityRunDetail`，不透传原始 artifact。
- `/api/quality/**` 当前是内部只读 API，仍复用现有 `/api/**` 登录拦截，并额外要求 `app.quality.console.enabled=true`。默认关闭；P0 不新增 admin 角色表、不改变现有用户权限模型。
- 2026-07-04 Agent Quality Console MVP Slice 2 已完成后端 artifact 聚合 service。新增 `com.docpilot.backend.quality` 只读聚合能力，可以从白名单 root 扫描最近 artifact，输出 `QualityRunSummary` / `QualityRunDetail`，并把坏 JSON 降级为 `REVIEW` + `artifactParseFailed=true`。
- Quality artifact 聚合仍是 P0 后端 service 层能力，尚未暴露 `/api/quality/**`，也未新增前端 `/quality` 页面。service 不读取业务数据库、不启动真实链路、不创建数据、不新增表。
- 当前 Quality DTO 只允许返回 marker、source、artifactName、status、updatedAt、gate 计数、失败 / REVIEW 桶、gate 数值 / 布尔指标、eval case 摘要和 token usage 数值；不会透传原始 artifact、prompt、answer 原文、文档全文、evidence context、API key、secret、连接串或云地址。
- 2026-07-04 Agent Quality Console MVP Slice 1 已完成文档口径收敛。Agent Quality Console 定位为内部质量控制台，不是两个独立大平台；第一版信息架构为 `Overview`、`Trace`、`Eval`、`Failures`，P0 先实现 `Overview + Run Detail`，并保留 Trace / Eval 扩展入口。
- Agent Quality Console P0 数据源是 ignored artifact 的脱敏摘要聚合，默认扫描 `backend/target/audit`、`backend/target/rag-natural-corpus`、`backend/target/rag-real-qa`、`backend/target/memory-quality`、`backend/target/memory-provider` 和 `tmp-e2e/docpilot-cloud-quality-smoke`。P0 不新增数据库表；只有当需要跨机器保留历史、权限审计或趋势查询时，再评估 `quality_eval_run` / `quality_eval_gate` 表。
- Agent Quality Console 的 parser 和 API 必须采用字段白名单。禁止返回 prompt、answer 原文、文档全文、evidence context、API key、access token、secret、连接串和云地址；`token_usage` 只允许返回 `prompt_tokens`、`completion_tokens`、`total_tokens`、`estimated_cost` 等数值统计。若发现泄露风险，可关闭对应 artifact root 或隐藏 detail 字段。
- `/quality` 和 `/api/quality/**` 是内部页面 / API。P0 如没有完整 admin 角色，先用开发环境开关或 admin token 控制访问；普通用户页面不展示 Trace / Eval 详情。
- 2026-07-04 Memory provider 小样本 v1 已完成。新增默认关闭的 `MemoryProviderExtractionRealProviderSmokeTest` 和 `scripts/smoke/memory-provider-extraction-smoke.ps1`；真实 run marker `docpilot-memory-provider-20260704192850-695412` PASS，4 次真实 provider 调用，`casePassRate=1.0000`，`rawProviderOutputStored=false`。该结论是小样本 provider contract 证据，不代表大规模长期记忆抽取质量成熟。
- `MemoryProviderExtractionEvalRunner` 已能处理真实 provider 常见 JSON 包裹和类型表达漂移：支持 JSON code fence、大小写 / hyphen / space 归一化，并按 memory type multiset 判断命中；artifact 仍只保存 provider/model/call count/caseId/types/booleans/failure reasons，不保存原始对话、provider 输出、memory 内容、prompt 或凭据。
- 2026-07-04 真实用户问答体验审计 v2 已完成。新增 `scripts/smoke/real-user-qa-experience-audit.ps1`，默认组合 `naturalCorpus`、`multiQueryRag`、`frontendInteraction` 和 `memoryQuality` gate；真实 run marker `docpilot-real-user-qa-20260704191307-661bc0` PASS，覆盖 tunnel、backend health、frontend routes、上传 / parse / indexing、chunk 质量、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、25 个自然语料 case、Conversation Trace、Memory 质量、权限隔离和脱敏 artifact。
- 本轮真实审计先暴露 `answerFactExpression` 对单一英文短语过度敏感：evidence / citation 已支撑，但真实回答表达为其他自然说法时会误判。`cloud-quality-smoke.ps1` 已支持 `a|b|c` 同义表达组，保持 answer faithfulness 门禁同时减少字符串脆弱性；修正后 `answerFaithfulnessPassCount=11/11`、`citationPhraseSupportPassCount=22/22`。
- 2026-07-04 Evidence Coverage 报告 v1 已接入自然语料真实 smoke。`naturalCorpus` artifact 现在输出 `evidenceCoverageReport`，直接列出 retrieval / citation / phrase / answer / distractor / no-evidence 的 caseId 级 miss / leak / failure 清单。真实 run marker `docpilot-rag-natural-corpus-20260704160327-16b351` PASS；各类 miss / leak / failure 清单均为空。
- 2026-07-04 Answer / Citation Faithfulness v2 已在自然语料真实链路中收口。`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` marker `docpilot-rag-natural-corpus-20260704152850-e07b13` PASS；`naturalCorpus` 新增 `answerFaithfulnessPassCount=11/11`、`citationPhraseSupportPassCount=22/22`，并修正单条 QA citation 计数 artifact 可能显示为 `null` 的问题。
- 2026-07-04 RAG 自然语料扩容 v2 已完成并通过真实链路验证。`scripts/smoke/rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` marker `docpilot-rag-natural-corpus-20260704151615-bc193d` PASS；`naturalCorpus` 升级为 `schemaVersion=2`，覆盖 3 个 corpus、12 份临时 txt 文档、25 个 case，`casePassRate=1`，3 个 no-evidence case 全部正确拒答，4 个多文档 case 全部覆盖目标文档，25 个含干扰文档的 case 均无干扰 citation。
- 本轮真实 v2 gate 暴露并修复了一个多文档 citation 质量问题：KnowledgeBase QA 的答案数字一致性 citation 精炼曾在 compare / summary 问题中把引用压成单文档，导致 `ops-backup-rollback-compare` 漏掉 backup citation。现在多文档意图下，数字过滤不会破坏至少两份文档的 citation 覆盖；新增单测固定该回归。
- Smoke runner 质量同步增强：自然语料用户改用短 alias 避免超过注册用户名 32 字符约束；本地 backend / frontend 启动日志写入 ignored artifact 目录；API 传输失败时可在本地后端掉线场景尝试一次本地 backend 恢复并标记 `backendRecovery=REVIEW`，用于真实长链路审计取证。
- 2026-07-04 RAG 自然语料真实审计 gate v1 已完成并通过真实链路验证。新增 `scripts/smoke/rag-natural-corpus-audit-smoke.ps1`，默认运行 `naturalCorpus`、`multiQueryRag` 和 `frontendInteraction` gate；最终 marker `docpilot-rag-natural-corpus-20260704143033-86b4f3` PASS，覆盖 5 份自然语料临时文档、单文档事实、数字事实、多文档总结、干扰文档、no-evidence、Conversation Trace、前端交互、权限隔离和脱敏 artifact。
- 本轮自然语料审计真实暴露并修复了一个 KB QA citation 精度问题：invoice archive retention 的答案曾同时引用 marketing retention 干扰文档；现在 KnowledgeBase QA 会在回答生成后做答案数字一致性 citation 精炼，数字事实回答不再挂载只包含其他数字值的干扰引用，同时保留 retrieval hits / documentHitCounts 作为 trace 证据。最终真实 gate 中 `distractorInvoiceCitationCount=1`、`distractorMarketingCitationCount=0`。
- Smoke runner 稳定性同步增强：上传接口遇到限流 `code=1014` 会 retry/backoff；通用 API 请求 retry 更适合长链路真实审计。该增强只影响 smoke runner 的耐跑性，不绕过业务限流、不删除数据、不修改数据库结构。
- 2026-07-04 真实体验审计问题已进入防回归状态。`scripts/smoke/cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate -FrontendBaseUrl http://127.0.0.1:3007` marker `docpilot-cloud-quality-20260704135601-944384` PASS；`shortDocumentRag` 现在覆盖短文档中文内容 retrieve、数字事实 retrieve、KnowledgeBase 双文档覆盖、相似短文档干扰和 citation marker，`failureBuckets=[]`；`frontendInteraction` 同步覆盖 quote-first UI、KnowledgeBase 双 citation UI、权限提示和 console error，`failureBuckets=[]`。
- `scripts/smoke/rag-real-qa-eval-smoke.ps1` 在未 `-SkipFrontend` 时默认启用 `frontendInteraction`，因此后续 RAG real QA wrapper 也会把 P2/P3 浏览器交互回归纳入真实链路门禁。若需要快速 API-only 验证，可显式使用 `-SkipFrontend` 或 `-SkipFrontendInteractionGate`。
- 2026-07-03 真实体验审计 P2/P3 浏览器细验已收口。`scripts/smoke/cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate -FrontendBaseUrl http://127.0.0.1:3007` marker `docpilot-cloud-quality-20260703231920-e74334` PASS；新增 `frontendInteraction` gate 覆盖文档详情 RAG 检索预览 quote-first 可见、KnowledgeBase 短 Alpha / Beta 双 citation marker 可见、跨用户文档无权限提示可见和 console error count `0`。真实体验台账中的 `REA-20260703-P2-001`、`REA-20260703-P3-001` 已标为 `VERIFIED`。
- 2026-07-03 真实体验审计 P1 RAG 问题已完成修复并通过真实 cloud quality smoke 验证。`scripts/smoke/cloud-quality-smoke.ps1 -Mode run` marker `docpilot-cloud-quality-20260703213703-dbef08` PASS；新增 `shortDocumentRag` gate 覆盖短 Alpha 单文档 `1` hit / `1` citation、短 Alpha / Beta KnowledgeBase `2` hits / `2` citations 和 answer grounding，核心 gate、no-evidence、Conversation Trace、权限隔离、frontend routes、artifact redaction 和 cleanup 均保持 PASS。
- 本轮修复保持边界收窄：单文档 RAG 只在 query 与 scoped hit 内容存在同一个明确 marker token、且 threshold 后无 hit 时保留最强 evidence；KnowledgeBase RAG 只在总结类问题和明确 marker 场景按缺失文档 backfill，不降低全局 similarity threshold，不放宽普通 no-evidence。文档详情页 quote-first 展示只在问题中存在明确 marker token 且 snippet/content 更能命中该 token 时优先展示 marker-bearing evidence，普通问题仍保持 `quoteText -> snippet -> content`。
- 真实体验审计台账 `REA-20260703-P1-001`、`REA-20260703-P1-002`、`REA-20260703-P2-001`、`REA-20260703-P3-001` 均已标为 `VERIFIED`。
- 2026-07-03 起，内部协作文档、状态回写、真实体验审计问题台账默认使用中文。技术名、路径、API、状态枚举、命令可以保留原文，但目标、结论、复现步骤、实际结果、预期结果、可能原因和边界说明必须用中文。
- Codex / agent 真实启动 DocPilot、跑本地 tunnel / backend / frontend / smoke、用浏览器或 API 按用户路径体验后，如果发现 bug、体验问题、安全疑点或环境阻塞，必须自动追加到 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`，不能只口头汇报或只写 showcase 摘要。
- 2026-07-03 起，真实体验审计发现的问题统一写入内部长期台账 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md`。
- 最新真实体验审计问题不再有 OPEN 项；`docpilot-real-audit-20260703195519-5118e8` 发现的 4 个问题已由后续真实 cloud quality smoke 和浏览器交互 gate 验证收口。当前剩余风险是小规模 smoke / fixture 级证据，不应写成大规模 relevance benchmark 或线上 SLA。

- 2026-07-03 M1 provider extraction eval contract is DONE as a test-side contract. `MemoryProviderExtractionEvalRunner` can call an `AiAnswerService`, parse JSON memory suggestions, validate expected memory types, reject unsafe provider suggestions and output a redacted summary without storing conversation text, provider output or memory content.
- Current evidence uses a stub provider only: PASS for `ANSWER_STYLE` + `TASK_GOAL`, and FAIL for unsafe token-like output. This prepares the runner for later small real-provider validation but does not claim real LLM memory extraction quality yet.
- Verification: provider contract targeted tests PASS, 7 tests; Memory / Context tests PASS, 65 tests; wider RAG / KnowledgeBase / Conversation / Memory regression PASS, 276 tests.

- 2026-07-03 M1 Memory provider-readiness artifact is DONE for the offline eval path. Memory Quality Eval now reports `providerBackedCaseRate` and a redacted `providerEvaluation` block so artifacts clearly state that current extraction evidence is `rule_based`, `not_configured` for real provider, `modelCallCount=0` and `rawProviderOutputStored=false`.
- Per-case memory eval summaries now include `extractionProvider` and `providerBacked`, making later real-provider small-sample comparisons possible without changing the safe artifact contract again.
- Boundary: this does not claim real LLM long-term memory extraction quality; it is an artifact honesty / readiness slice with no provider call, no runtime smoke, no schema change and no raw conversation or memory text stored.
- Verification: targeted Memory / Context tests PASS, 27 tests; wider RAG / KnowledgeBase / Conversation / Memory regression PASS, 274 tests.

- 2026-07-03 R3 quote-level citation API is DONE. Single-document RAG and KnowledgeBase RAG citations now expose `quoteText`, `quoteStartOffset` and `quoteEndOffset` alongside existing chunk-level `snippet`, chunk offsets and scores.
- The quote is derived from the retrieved chunk and prefers an evidence-marker-bearing sentence when available; offsets are chunk-offset-derived API fields, not PDF/page coordinates.
- Frontend API types have optional quote fields, but quote-first page rendering is deferred to a separate encoding-safe frontend slice. No schema, ranking, prompt, answer-generation, runtime smoke, provider call or artifact submission was involved in this slice.
- Verification: `mvn "-Dtest=RagEvidenceQuoteExtractorTest,RagQaControllerTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,DocumentAgentServiceImplTest" test` PASS, 26 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 214 tests; `npm run lint` PASS.

- 2026-07-03 R1 request-scoped multi-query runtime smoke is PASS. `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` produced marker `docpilot-rag-real-qa-20260703192456-2a62e9`; `multiQueryRag` observed `multiQueryApplied=true`, `queryVariantCount=4`, `queryDedupeCount=24`, `6` retrieve hits and `6` QA citations with both temporary documents covered `3/3` in retrieve and citation counts.
- The same run kept tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, hard negative, semantic gate, real provider faithfulness, no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction at PASS. A first run reached `multiQueryRag` PASS but failed later at Conversation message request; cleanup succeeded and the immediate rerun passed.
- Boundary: this is small real-link smoke evidence for request-scoped multi-query, not a large-scale relevance uplift benchmark, LLM query planner evaluation or online SLA.

- 2026-07-03 R1 multi-query real smoke gate runner is implemented: `cloud-quality-smoke.ps1` has optional `-EnableMultiQueryGate`, and `rag-real-qa-eval-smoke.ps1` enables it by default with `-SkipMultiQueryGate` available.
- The gate stores only redacted multi-query booleans/counts, score summaries, document coverage counts and answer-grounding status. Verification so far is plan / dry-run / script safety only; real `run` evidence is still pending.

- 2026-07-03 R1 request-scoped multi-query retrieval control is implemented for KnowledgeBase retrieve and QA APIs. Requests can now explicitly set `multiQueryEnabled` and `maxQueryVariants`; absent fields keep the existing default-off global behavior.
- KnowledgeBase offline eval now includes `retrievalModeMetrics.multi_query` alongside `vector` and `hybrid`, so multi-query can be compared in redacted artifacts without storing rewritten query text, document text, prompt, evidence context or answer output.
- Verification: `mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalFixtureTest,RuleBasedQueryRewriteServiceTest,RagRetrievalPropertiesTest" test` PASS, 32 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 211 tests.
- Boundary: this is request/API and offline eval control-plane evidence only. No tunnel/backend/frontend runtime smoke was executed in this slice, and no real provider, business data, schema change, remote Docker operation or artifact commit was involved.

- Query Rewrite / Multi-query Retrieval v1 is implemented for KnowledgeBase retrieval behind `app.rag.retrieval.multi-query-enabled=false` by default. The first version uses deterministic rule-based query variants, bounded by `max-query-variants`, and deduplicates vector hits before the existing threshold / hybrid / rerank / scope / diversity gates.
- Retrieval responses now expose `multiQueryApplied`, `queryVariantCount` and `queryDedupeCount`; rewritten query text is not stored in the result response.
- Verification: `mvn "-Dtest=RuleBasedQueryRewriteServiceTest,RagRetrievalPropertiesTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseEvidenceContextBuilderTest" test` PASS, 32 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 209 tests; real `rag-real-qa-eval-smoke.ps1 -Mode run` PASS, marker `docpilot-rag-real-qa-20260629202542-3e47d9`.
- Boundary: this is default-off deterministic query expansion, not LLM query planning, real-provider rewrite or a proven large-scale relevance uplift benchmark.
- Chunk Quality v2 is implemented for the chunking / indexing path. Chunks now carry nested `sectionPath`, table / list structure detection, `window_split`, `mid_sentence_split` and `duplicate_content` quality flags; indexing metadata and Qdrant payload propagation include `sectionPath`.
- Verification: `mvn "-Dtest=ChunkingServiceImplTest,RagIndexingServiceImplTest,DocumentChunkServiceImplTest,VectorPointTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS, 41 tests.
- Boundary: this slice did not start tunnel/backend/frontend, create business data, call a real provider, change schema, touch remote Docker or run hk-ops.
- RAG Retrieval Error Analysis Report v1 is implemented for offline KnowledgeBase RAG eval and RAG Real QA eval artifacts. It adds redacted buckets for missed retrieval, wrong retrieval, no-evidence refusal, unsupported citation, unsupported answer, forbidden leak, scope violation and ranking candidate pass counts.
- The report is count / status / failure-reason only. It does not store document text, query text, answer text, model instructions, evidence context, credentials, cloud addresses or connection strings.
- Verification: `mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest,KnowledgeBaseRagEvalRunnerTest" test` PASS, 5 tests.
- Boundary: this is offline quality-gate evidence using mock embedding and in-memory vector store; it is not a real-provider relevance benchmark or production SLA.

最后更新：2026-06-29

旧状态快照已保留在 `docs/ai-dev/archive/STATE_2026-04-18.md`。本文件只维护当前事实，不追加流水账。

## 1. 项目定位

DocPilot 是 Java Spring Boot + Next.js 的企业文档知识库 RAG + 会话记忆平台，核心主线是文档上传、异步解析、结构化切片、向量索引、多文档检索增强问答、可信引用、会话上下文追踪、用户记忆沉淀、权限隔离和质量门禁。

当前目标不是停留在“能演示的 AI 文档问答”，而是推进到生产化知识库 RAG 核心闭环：系统要能判断什么时候有证据、什么时候必须拒答或降级，并能通过 trace / artifact 解释每次回答用了哪些 evidence。当前仍不宣称完整商业 SaaS、线上 SLA、大规模多租户计费、高可用运维或成熟多 Agent 编排系统。

## 2. 当前已实现能力

- 账号密码登录 / 注册；短信验证码登录保留兼容路径。
- 文件上传、分片上传、MinIO / local 存储模式。
- 文档创建、列表、详情。
- RocketMQ + Outbox 异步解析链路设计与实现，包括 outbox relay、scan job、producer 边界。
- Redisson 分布式锁、消费幂等、解析任务状态机和补偿思路。
- Redis 缓存、问答限流、登录 token / 上传会话等状态管理。
- 普通文档问答与 SSE 流式问答，支持 citations 和问答历史。
- Agent 工具选择、`AgentTask` / `AgentStep` 持久化、前端 Trace 展示。
- Agent 工具链包含文档状态、摘要、问答、旧 RAG showcase 工具和新 RAG QA 工具，能展示 routingReason、matchedKeywords、steps、citations。
- Agent 工具链已新增内部 `ToolSpec` / `ToolSpecRegistry` 元数据底座，并提供最小 ToolCall API 用于列出工具和调用安全子集工具；Agent 主流程已让 status 与新 RAG QA 工具复用 `ToolCallService`，summary / qa legacy 分支仍保持兼容；OpenAI-compatible Function Calling adapter 已有 mock tool_call 闭环。
- 当前 RAG 主线已有持久化 DocumentChunk、ChunkingService、EmbeddingProvider、VectorStoreClient、RagIndexingService、parse success 自动 indexing trigger、RagDocumentRetrievalService、单文档 RAG QA / SSE、KnowledgeBase 多文档 RAG retrieval / 非流式 QA、Agent RAG QA 接入和单文档 / 多文档离线 retrieval quality smoke；旧 Agent RAG showcase 链路仍保留为独立演示路径。
- KnowledgeBase RAG 已补充总结类问题质量治理：Markdown / 文本块合并式 chunking、摘要意图下的跨文档召回多样性、`documentHitCounts`、回答模型 provider / model / call count 观测字段，以及面向资料集总结的 prompt。
- KnowledgeBase RAG 已新增默认关闭的 Hybrid / Rerank 增强链路：BM25 keyword 检索按 user、KnowledgeBase 文档集合和 `indexVersion` 过滤；RRF 融合保留 chunk 元数据并经过二次 scope guard；可选 rerank 接入候选排序并输出 `retrievalMode`、`rerankApplied`、`rerankModel`、`vectorScore`、`keywordScore`、`fusedScore`、`rerankScore` 等观测字段；示例配置只提供安全占位，真实 provider 需要本地私有 `.env` 显式配置。
- RAG Quality Upgrade v1 已完成离线质量门禁增强：单文档 retrieval 与 KnowledgeBase retrieval 一样接入 `app.rag.retrieval.min-similarity-threshold`；KnowledgeBase RAG eval 从 hit / citation 扩展到 answer marker、forbidden answer leak、最少 citation 数和多文档覆盖指标，artifact 仍只保存脱敏 summary。
- RAG Quality Upgrade v3 已把真实链路 no-evidence 门禁从 `REVIEW` 推到 `PASS`：KnowledgeBase hybrid 检索在融合后继续执行 evidence confidence gate，并对带 `vectorScore` 的 hybrid hit 使用原始向量相似度做阈值判断，避免把 RRF `fusedScore` 当作 similarity；默认质量阈值校准为 `0.50`。2026-06-27 `scripts/smoke/rag-real-quality-smoke.ps1 -Mode run` 默认配置 PASS，marker 为 `docpilot-rag-real-quality-20260627210458-9d0321`，覆盖真实 embedding + Qdrant、chunk、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、populated-KB no-evidence、Conversation Trace、权限隔离、前端 route 和 artifact 脱敏。
- RAG Quality Upgrade v4 已新增 KnowledgeBase QA answer audit：QA response 暴露脱敏 `audit`，包含 `grounded`、evidence / citation count、documentHitCounts、score / vectorScore / fusedScore / rerankScore summary、retrievalMode、rerank 信息、fallbackReason 和 modelCallCount；离线 eval 新增 `groundedAnswerRate` 与 `noEvidenceCitationFreeRate`。2026-06-27 默认真实 smoke 再次 PASS，marker 为 `docpilot-rag-real-quality-20260627211711-383cda`。
- RAG Quality Upgrade v5 已新增 chunk structure quality：chunk candidate 生成 section title / ordinal / source block ordinal / structure type / quality flags，索引时透传到 embedding metadata 与 Qdrant payload；真实 smoke 的 chunkQuality gate 已覆盖 MySQL offset order、token/content length 和 duplicate hash，MySQL / Qdrant gate 已校验结构 payload 字段。2026-06-27 默认真实 smoke PASS，marker 为 `docpilot-rag-real-quality-20260627213040-4038e1`。
- RAG Quality Upgrade v6 已完成：KnowledgeBase 离线 eval artifact 新增 `retrievalModeMetrics`，同一批 case 对比 `vector` 与 `hybrid` 的 hit / citation / multi-document / no-evidence / grounding 指标；rerank provider 现在必须在 `enabled=true` 且外部配置完整时才发 HTTP，否则 identity fallback 且不调用外部服务。2026-06-27 默认真实 smoke PASS，marker 为 `docpilot-rag-real-quality-20260627214532-e1fb65`。
- RAG Quality Upgrade v7 已完成：`ContextTrace` API 暴露计算型 `contextSourceCounts` / `contextSourceFlags`，前端 `/conversations` Trace 面板展示会话摘要、最近消息、长期记忆和 RAG evidence 的拆分计数；测试门禁覆盖 assistant / RAG evidence 不会自动变成长期记忆，且只有 `ACTIVE` user memory 进入上下文；真实 smoke `docpilot-rag-real-quality-20260627220736-8f03b9` PASS，Conversation Trace 同时验证 `evidenceCount=6`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6`；不改数据库结构，不保存 prompt 或 evidence 原文。
- RAG Quality Upgrade v8 已完成前两片：KnowledgeBase RAG 离线 eval corpus 从 5 个 case 扩到 11 个 case，新增 case 级 `minSimilarityThreshold`，覆盖 populated-KB no-evidence、hybrid keyword 噪声、多文档总结、grounding 干扰、跨主题路由和 scope 干扰；单文档 RAG smoke case 从 4 个扩到 7 个 case，补充 populated-document no-evidence、grounding citation marker 和 distractor 抑制；2026-06-28 `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests；真实 smoke `docpilot-rag-real-quality-20260628141419-fb7c21` PASS。
- Phase 2 真实体验审计已完成并收口：2026-06-28 使用 marker `docpilot-phase2-ui-audit-1782628501578` 跑通浏览器注册、两文档上传 / parse、单文档 RAG、KnowledgeBase API 多文档 RAG、Conversation Trace 和 ACTIVE memory；Trace 证据为 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=2`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=2`。本轮同步修复本地 smoke 常用端口 `3007` / `3100` 的后端 CORS allowlist、`/conversations` 历史消息 footer 引用数和 Trace evidence 不一致、文档详情页 RAG SSE 引用来源不同步、移动端 `/conversations` 横向溢出，以及 KnowledgeBase 手动两文档总结问法只召回单文档的问题。后续真实 smoke `docpilot-rag-real-quality-20260628150434-2b7b39` PASS，KnowledgeBase 两文档 gate 命中分布 `{152:3,153:3}`，no-evidence、Conversation Trace、权限隔离和前端 route smoke 均保持通过。
- Phase 3 小规模真实 rerank provider 验证已完成，并在 Quality Loop v2.2 补过 hard fixture：`scripts/smoke/rerank-effect-smoke.ps1` 先用两轮真实 cloud quality smoke 证明 hybrid+real-rerank 可调用且无核心 gate 回退；随后 `cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRerankHardGate`，在真实链路中加入关键词干扰文档并比较 target / distractor 排序。2026-06-28 hard run PASS，baseline marker `docpilot-rerank-effect-hybrid-20260628204120-3e9f69`，rerank marker `docpilot-rerank-effect-rerank-20260628204339-7aac45`；hard fixture 中 target rank `2 -> 1`，distractor rank `3 -> 4`，`rerankApplied=true`，`hardUpliftObserved=true`，no-evidence 和权限隔离无回退。该结论是小规模 hard smoke uplift 证据，不等于大规模 relevance benchmark。
- 2026-07-12 已把 rerank 从单一 hard fixture 扩展到代表语料多 case 真实链路 eval：`rerank-representative-eval-smoke.ps1 -Mode run` 对比 hybrid-only baseline 与阿里云百炼 `qwen3-rerank` candidate，baseline marker `docpilot-rerank-representative-representative-hybrid-20260712151858-5543fd`，candidate marker `docpilot-rerank-representative-representative-rerank-20260712152212-2e0f81`。最终 12 case PASS，10 个 target case 覆盖无回退，2 个 no-evidence case 保持拒答，candidate `rerankApplied=true`、`targetRerankAppliedCaseCount=10`、`strictImprovementCaseCount=2`、`upliftCaseCount=10`、`citationLeakageCount=0`、`noEvidenceRegressionCount=0`。本次还修复了 summary intent 泛词绕过 no-evidence gate、中文问法 multi-query / hybrid keyword 支撑不足和 PowerShell 5.1 中文 JSON 编码问题；仍不能写成大规模 ranking benchmark 或线上稳定 uplift。
- DocPilot Quality Loop v2 已启动第一片：新增 RAG Real QA Eval v1 离线基线，fixture 覆盖事实查找、跨文档总结、比较、多跳式证据、no-evidence、语义干扰、hybrid keyword 噪声和 rerank uplift shaped case；metrics 输出 `casePassRate`、`answerCorrectnessRate`、`citationGroundingRate`、`noEvidencePrecision`、`multiDocumentCoverageRate`、`forbiddenLeakRate`、`scopeViolationRate` 和 `rerankUpliftCandidateRate`。该 eval 继续复用 `MockEmbeddingProvider` + `InMemoryVectorStoreClient`，artifact 只保存脱敏 summary，不保存文档原文、query、模型输入、evidence context 或模型输出；2026-06-28 离线 targeted tests 7/7 PASS，RAG / KnowledgeBase 回归 202/202 PASS。
- RAG Real QA Eval v1 已从离线基线推进到真实链路 smoke 证据：`scripts/smoke/rag-real-qa-eval-smoke.ps1` 支持 `plan` / `dry-run` / `run`，默认 marker 前缀为 `docpilot-rag-real-qa`，artifact 根目录为 ignored 的 `backend/target/rag-real-qa`。2026-06-28 已验证 plan / dry-run PASS、脚本安全测试 2/2 PASS，并执行真实 `run` PASS，marker 为 `docpilot-rag-real-qa-20260628164757-ac2a1d`；本次覆盖配置一致性、tunnel、backend health、frontend route、临时用户、两文档上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、populated-KB no-evidence、Conversation Trace、权限隔离和 artifact 脱敏。
- Memory Quality Eval v1 已启动离线基线：新增 `memory-quality-eval-cases.json` 和 test-side `MemoryQualityEvalRunner`，覆盖用户偏好抽取、assistant / RAG evidence 不进入 memory、ACTIVE / SUGGESTED / IGNORED 状态分层、敏感内容拦截，以及 summary / recent messages / user memory / RAG evidence 的 trace source counts。该 eval 复用现有 `RuleBasedMemoryExtractionService`、`MemorySelector`、`ContextAssemblyServiceImpl` 和 `MemorySafetyValidator`，artifact 只保存脱敏 summary；2026-06-28 targeted tests 48/48 PASS，RAG / KnowledgeBase / Conversation / Memory 回归 249/249 PASS。
- Memory Quality Eval v1 已补真实链路 smoke：新增 `scripts/smoke/memory-quality-smoke.ps1`，复用 cloud quality gate 并打开默认关闭的 `-EnableMemoryQualityGate`；2026-06-28 run PASS，marker 为 `docpilot-memory-quality-20260628193150-625bf6`。本次覆盖真实候选抽取、accepted suggestion -> `ACTIVE`、ignored suggestion -> `IGNORED` 且不进入 ACTIVE list、绑定 KB 后 trace 同时包含 `userMemory=1` 与 `ragEvidence=6`、documentHitCounts 覆盖两份临时文档；完整 delegated gates 中 tunnel、backend health、frontend routes、chunk / MySQL / Qdrant、单文档 RAG、KB RAG、no-evidence、权限隔离和 artifact 脱敏均 PASS。
- Frontend UX Audit v1 已完成真实浏览器审计：2026-06-28 使用 marker `docpilot-frontend-ux-2647184760` 创建临时用户、两份 txt 文档、KnowledgeBase、ACTIVE memory 和绑定 KB 的 Conversation；Conversation 页面真实显示 `2 条来源`、Trace 的 `userMemory=1` / `ragEvidence=2`、Memory 面板中的 ACTIVE memory；KnowledgeBase 页面真实展示 provider / 索引集合、来源文档分布 `#175:1 / #176:1`、召回片段和引用卡片。`390x844`、`360x780`、`320x740` 移动端 `/conversations` 与 `/knowledge-bases` 均无横向溢出；长 ACTIVE memory 未撑破 Memory 抽屉。本轮未发现需要改代码的阻断问题。
- KnowledgeBase 问答结果区已做产品化降噪：默认主 KPI 聚焦“来源覆盖 / 引用来源 / 回答状态 / 生成次数”，provider、collection、retrieval mode、rerank、answer provider / model 收进“工程观测”折叠区，既保留工程审计信息，又降低普通用户第一眼的底层名词负担。`npm run lint`、`npm run build` 和真实浏览器桌面 / `360px` 验证均通过。
- RAG Real QA Eval 已补更难 rerank uplift 候选：新增 `real-rerank-distractor-ordering`，覆盖 export / audit / retention 词面干扰但缺少目标 evidence marker 的情况；metrics 新增 `rerankUpliftCandidatePassRate`，用于单独观察 rerank 候选 case 是否通过，而不是只记录候选 case 占比。2026-06-28 targeted eval 9/9 PASS，`*Rag*,*KnowledgeBase*` 回归 204/204 PASS。
- RAG Real Corpus Eval 第一片已完成：`real-qa-eval-cases.json` 从 9 个 case 扩到 22 个 case，新增长文档、近义 no-evidence、多文档总结、citation grounding、scope isolation、hybrid keyword noise 和 rerank distractor 企业场景样例；`RagRealQaEvalMetrics` 新增 `longDocumentCasePassRate`、`nearMissNoEvidenceRate`、`multiDocSummaryPassRate`、`distractorSuppressionRate`。2026-06-28 已验证 `mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，9 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，204 tests。该结果仍是脱敏离线门禁，不代表大规模真实 provider benchmark。
- RAG Real Corpus 真实链路代表性三文档门禁已完成：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRepresentativeCorpusGate`，额外上传 incident review Gamma 文档，并与 Alpha / Beta 组成 Representative Corpus KB；`rag-real-qa-eval-smoke.ps1` 默认打开该 gate，另提供 `-SkipRepresentativeCorpusGate`。2026-06-28 已验证 plan / dry-run PASS，脚本安全测试 2/2 PASS，Real QA targeted tests 9/9 PASS；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260628234235-5c1b94`，representative gate 返回 `8` hits / `8` citations，documentHitCounts 覆盖 Gamma `196:2`、Beta `195:3`、Alpha `194:3`，no-evidence、Conversation Trace、权限隔离、前端 routes、cleanup 和 artifact 脱敏均保持 PASS。该结果是小规模真实链路代表性门禁，不是大规模 relevance benchmark。
- RAG Answer Grounding Gate v1 已完成：`cloud-quality-smoke.ps1` 新增 `answerGrounding` gate，对单文档 RAG、KnowledgeBase 两文档 RAG 和 representative corpus 三文档 RAG 的最终回答做 evidence marker / forbidden marker / citation marker 检查；artifact 只保存回答长度、marker 命中计数和布尔结果，不保存回答原文、prompt、evidence context 或 response 原文。2026-06-29 已验证 plan / dry-run PASS，`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629003157-630db5`，三个 scope 均 `expectedMarkersSatisfied=true`、`forbiddenMarkerHit=false`、`citationMarkerPresent=true`；no-evidence、Conversation Trace、权限隔离、前端 routes、cleanup 和 artifact 脱敏均保持 PASS。该结果是小规模真实链路回答落证门禁，不是大规模 answer faithfulness benchmark。
- RAG Hard Negative / Answer Faithfulness Eval 第一片已完成：`real-qa-eval-cases.json` 新增 `hard_negative` 与 `answer_faithfulness` 两类脱敏离线 case，覆盖强词面相似但缺少目标结论的 no-evidence 场景，以及目标 evidence / 相近 SLA 干扰文档下的回答忠实度场景；`RagRealQaEvalMetrics` 新增 `hardNegativePassRate` 与 `answerFaithfulnessPassRate`。2026-06-29 已验证 targeted Real QA eval 3/3 PASS，更宽 Real QA / KnowledgeBase eval 10/10 PASS。该结果仍是 MockEmbeddingProvider + InMemoryVectorStoreClient 的离线门禁，不代表真实 provider 大规模 benchmark。
- RAG Claim Support / Numeric Faithfulness Eval 已完成第一片：`real-qa-eval-cases.json` 新增 `claim_support` 与 `numeric_faithfulness` 两类脱敏 case，分别覆盖目标 evidence 支持的 manager approval 结论，以及 seven-year retention 不被 three-year 干扰文档污染；`RagRealQaEvalMetrics` 新增 `claimSupportPassRate` 与 `numericFaithfulnessPassRate`。2026-06-29 已验证 targeted Real QA eval 3/3 PASS、更宽 Real QA / KnowledgeBase eval 10/10 PASS、`*Rag*,*KnowledgeBase*` 回归 207/207 PASS。该结果仍是离线语义支持门禁，不代表真实 provider 大规模 answer faithfulness benchmark。
- RAG Real QA Semantic Gate Smoke 已完成：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRealQaSemanticGate`，`rag-real-qa-eval-smoke.ps1` 默认开启并支持 `-SkipRealQaSemanticGate`；真实链路将 `claim_support` 与 `numeric_faithfulness` 小样本门禁纳入临时 Alpha / Beta KnowledgeBase。2026-06-29 真实 marker `docpilot-rag-real-qa-20260629183549-4aafc3` PASS，`claimSupport` 和 `numericFaithfulness` 均为 `1` hit / `1` citation、target citation `1`、forbidden citation `0`，expected marker satisfied、forbidden marker absent、citation marker present；hard negative、representative corpus、answer grounding、普通 no-evidence、Conversation Trace、权限隔离、frontend routes 和 artifact redaction 均保持 PASS。该结果是小规模真实链路语义支持门禁，不代表通用语义蕴含模型或大规模真实 provider benchmark。
- RAG Real Corpus Expansion to 40 Cases 已完成第一片：`real-qa-eval-cases.json` 从 `26` 个 case 扩到 `40` 个 case，新增 `14` 个脱敏企业知识库样例，覆盖合同续约、访问变更审批链、SLA 数字忠实度、审计交接、多文档客户事故沟通、hard negative、near-miss no-evidence、answer faithfulness、SSO / MFA 比较、报销限额、scope isolation、长备份 runbook、hybrid keyword 噪声和 citation grounding；fixture 门禁同步要求总 case 数至少 `40`，并提高关键类别覆盖下限。2026-06-29 已验证 targeted Real QA eval 3/3 PASS、更宽 Real QA / KnowledgeBase eval 10/10 PASS、`*Rag*,*KnowledgeBase*` 回归 207/207 PASS。该结果仍是脱敏离线 eval 扩容，不代表真实 provider 大规模 benchmark。
- RAG Claim Support Evidence Scorer 已完成第一片：Real QA Eval 新增 test-side `RagClaimSupportScorer`，case 可声明 `expectedClaims`，每个 claim 只保存脱敏 claim id、answer marker、evidence marker 和 forbidden marker；artifact case summary 输出 claim support 计数和布尔结果，metrics 新增 `claimSupportScorerPassRate`、`supportedClaimRate`、`unsupportedClaimRate`、`forbiddenClaimRate`。2026-06-29 已验证 targeted Real QA eval 3/3 PASS、更宽 Real QA / KnowledgeBase eval 10/10 PASS、`*Rag*,*KnowledgeBase*` 回归 207/207 PASS。该 scorer 基于 synthetic marker contract，不代表通用自然语言蕴含模型或真实 provider 大规模 benchmark。
- RAG Real Provider Faithfulness Smoke 已完成：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRealProviderFaithfulnessGate`，`rag-real-qa-eval-smoke.ps1` 默认开启并支持跳过；2026-06-29 真实 marker `docpilot-rag-real-qa-20260629191831-69d71e` PASS，`realProviderFaithfulness` gate 在 `knowledgeBaseRag`、`answerFaithfulness`、`claimSupport`、`numericFaithfulness` 四个 scope 均观察到非 mock provider、`modelCallCount=1`、`noEvidence=false` 和非空回答；hard gate、semantic gate、representative corpus、answer grounding、no-evidence、Conversation Trace、权限隔离、frontend routes 和 artifact redaction 均保持 PASS。该结果是小规模真实 provider smoke，不代表大规模 answer faithfulness benchmark 或线上 SLA。
- RAG Real QA Hard Gate Smoke 第一片已完成实现并得到真实链路 `REVIEW` 证据：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRealQaHardGate`，`rag-real-qa-eval-smoke.ps1` 默认开启并支持 `-SkipRealQaHardGate`；2026-06-29 真实 run marker `docpilot-rag-real-qa-20260629125627-c0915e` 中核心 gate 全部 PASS，`answerFaithfulness` PASS，但 `hardNegative` 仍返回 `3` hits / `3` citations，vector score 约 `0.50-0.55`，整体状态为 `REVIEW`。这说明普通 no-evidence gate 已通过，但高词面相似且结论缺失的问题仍需要更细的 evidence support / grounding policy 治理。
- Hard Negative Near-threshold Support Gate 已将 v3.6 的真实链路 `REVIEW` 推到 `PASS`：KnowledgeBase retrieval 在非总结类问题、候选最高 threshold score 只略高于阈值、且 query 关键英文业务词在 evidence 中覆盖不足时返回 no-evidence；2026-06-29 已验证 `KnowledgeBaseRagRetrievalServiceImplTest` 13/13 PASS、`*Rag*,*KnowledgeBase*` 207/207 PASS，真实 marker `docpilot-rag-real-qa-20260629130454-1d1d6c` PASS，其中 `hardNegative` 为 `0` hits / `0` citations，`answerFaithfulness`、representative corpus、answer grounding、普通 no-evidence、Conversation Trace、权限隔离、frontend routes 和 artifact redaction 均保持 PASS。该门禁是近阈值启发式，不等于通用 entailment scorer。
- README / showcase 面试材料已同步 RAG Quality v3.5-v3.7 口径：对外材料现在可讲 no-evidence、answer grounding、hard negative、answer faithfulness、Conversation Trace、MySQL / Qdrant 一致性和脱敏 artifact 质量门禁；同时明确 hard-negative 支持度门禁是近阈值启发式，不是通用语义蕴含模型、大规模 relevance benchmark 或线上 SLA。
- Memory 长列表交互已完成一轮真实 UI 审计：2026-06-28 使用 marker `docpilot-memory-ui-1782649237433` 创建临时会话和 `16` 条 ACTIVE memory，`390x844` 下 Memory 抽屉可打开、列表可滚动、`memoryItemCount=17`、`deleteButtonCount=16`、`scrollWidth=clientWidth=390`；桌面 `1036x850` 同样无横向溢出。该审计不改变业务代码，不代表长期记忆真实模型抽取能力。
- Memory 产品化第一片已完成：`/conversations` Memory 抽屉新增生效 / 候选 / 重复提示 KPI、类型分布、来源说明、priority、confidence、更新时间、ACTIVE 重复提示和候选已存在提示；2026-06-28 真实浏览器 marker `docpilot-memory-ui-product-1782651263292` 验证桌面、`390x844`、`320x740` 均无横向溢出，5 张 memory / suggestion 卡片和 17 个 meta badge 可见。该结果只代表前端管理体验更可解释，不代表真实模型长期记忆抽取能力提升。
- Memory Governance 第一片已完成：`UserMemoryResponse` 暴露 `duplicateOfId`、`conflictWithId`、`governanceHint`、`similarityScore`，后端在手动创建和接受候选前检查同类型 ACTIVE memory 的精确重复、近似重复和明确偏好冲突；`/conversations` Memory 抽屉展示重复 / 冲突提示。2026-06-28 已验证 Memory / Context targeted tests 54/54 PASS，RAG / KnowledgeBase / Conversation / Memory 回归 255/255 PASS，`npm run lint` PASS，`npm run build` PASS；随后 `memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 真实链路 PASS，marker 为 `docpilot-memory-quality-20260628223255-0a06e6`，覆盖冲突 `ANSWER_STYLE` suggestion 返回 `governanceHint=conflict_active_memory`、`conflictWithId` 非空，以及直接 accept 被治理门禁阻止。该片仍不等于完整 Memory 合并 / 编辑能力或真实模型长期记忆抽取质量。
- Memory Governance v2 已完成用户可控编辑 / 处理闭环：`PATCH /api/memories/{memoryId}` 可编辑 ACTIVE memory，`POST /api/memories/suggestions/{memoryId}/resolve` 支持 `KEEP_ACTIVE` / `REPLACE_ACTIVE` / `MERGE_WITH_ACTIVE`；前端 `/conversations` Memory 抽屉支持编辑、保留旧记忆、替换旧记忆和手动合并。2026-06-29 已验证 Memory / Context targeted tests 63/63 PASS，RAG / KnowledgeBase / Conversation / Memory 回归 267/267 PASS，前端 lint / build PASS；真实 `memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-memory-quality-20260629140941-6668d9`，覆盖 keep / replace / merge、敏感 edit 拒绝、普通 edit 成功、Conversation Trace、权限隔离、frontend routes 和 artifact redaction。该结果仍是用户可控治理闭环，不等于真实模型长期记忆抽取或大规模个性化效果评测。
- Memory Extraction Quality Eval 已扩展第一片：规则式候选抽取现在会在候选生成前过滤敏感内容和一次性 / 临时指令；离线 `memory-quality-eval-cases.json` 新增多信号抽取、assistant 指令污染、低价值寒暄、临时回答风格和敏感 token/API key 指令五类脱敏 case；metrics 新增 `suggestionSafetyRate`、`userSignalExtractionRate`、`noiseSuppressionRate`、`temporaryInstructionSuppressionRate`。2026-06-29 已验证 targeted Memory eval 3/3 PASS、Memory / Context 63/63 PASS、RAG / KnowledgeBase / Conversation / Memory 回归 267/267 PASS。该结果仍是规则式离线质量门禁，不等于真实 LLM 长期记忆抽取质量。
- 目标 KnowledgeBase `3` 的文档 `83/84/85/86` 已授权重建索引到稳定 Qdrant collection `docpilot_rag_v2`；chunk / vector 数为 `35/35`、`18/18`、`10/10`、`16/16`，总结资料集检索分布为 `{83:2,84:1,85:1,86:2}`。
- Conversation Context Management / Agent Memory Mode 后端 MVP 已新增会话、消息、摘要、上下文 Trace、用户长期记忆五张新表和对应 API；`ContextAssemblyService` 可按 `RECENT_TURNS` / `AGENT_MEMORY` 组装系统提示、长期记忆、会话摘要、最近轮次与可选 KnowledgeBase evidence，并输出和持久化摘要级 trace。
- 会话发送链路已按工程化质量收窄事务边界：上下文装配和回答模型调用不在长事务内执行；仅最终 conversation 行锁、连续写入 user / assistant message 和更新时间处于事务内，trace 仍为 best-effort。
- 会话摘要已有显式 refresh API，当前采用本地 extractive 摘要压缩最近消息，不调用真实外部模型。
- 长期记忆已有候选机制：规则式提取会话用户消息中的偏好、目标、项目状态等候选记忆，候选以 `SUGGESTED` 保存，默认不进入 prompt；用户接受后才转为 `ACTIVE`。
- 前端已新增 `/conversations` 会话工作台 MVP，支持会话创建、非流式消息、KnowledgeBase 绑定、summary / trace 查看、ACTIVE 记忆维护和候选记忆接受 / 忽略。
- 前端展示已完成一轮产品化收口：首页、Dashboard、KnowledgeBase 和 Conversations 页面能直接呈现 RAG / KnowledgeBase / Agent Memory / Trace 主链路、smoke 证据口径与当前边界；KnowledgeBase 页面已展示 provider / collection、model call、citation 和命中文档分布等观测字段。
- 前端重点页已完成 AI 产品感二次精修：首页提供系统流程面板，Dashboard 更像演示指挥台，KnowledgeBase / Conversations 未登录态收敛为说明卡，登录态继续突出 evidence、Trace、Memory 等可观测字段。
- `/conversations` 已作为前端核心页按 GPT / DeepSeek 风格重做：页面主视觉改为居中聊天流、底部悬浮 composer 和左侧会话历史；Trace、Memory、Summary、KnowledgeBase evidence 收进右侧 Context Inspector 抽屉，保留会话上下文可观测性但不再把工程控制台作为主界面。
- 前端 UI 文案已完成一轮成熟化收口：产品页面不再直接暴露“求职 / 面试 / MVP / 演示 / 生产级 / smoke”等内部口径，改为工作空间、引用来源、上下文溯源、会话记忆、工具链等更克制的产品表达；详细边界仍保留在 docs / README / 面试材料中。
- A1 / S 系列真实链路 smoke 已补齐：单文档 RAG、多文档 KnowledgeBase RAG、真实回答模型、MinIO active storage、RocketMQ + Outbox active parse、真实 embedding + Qdrant、ToolCall API 和权限越界失败案例均有记录。
- 云端完整业务 smoke 质量门禁 runner 已落地并完成一次 `run` 模式验证：`scripts/smoke/cloud-quality-smoke.ps1` 支持 `plan` / `dry-run` / `run`，2026-06-27 使用 marker `docpilot-cloud-quality-20260627022219-37efd4` 跑通 tunnel、backend health、frontend route、临时用户、两文档上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、Conversation Trace、权限隔离负向检查、脱敏 artifact 和最终 git status 检查，整体状态 PASS。

## 3. 当前边界

- 当前目标是生产化知识库 RAG 核心闭环，但还不是完整商业知识库 SaaS 或已验证线上 SLA 的生产系统。
- fake embedding / in-memory vector store 仍属于测试、离线 eval 和稳定复现边界；真实 embedding + Qdrant 已在 smoke collection 验证，但不代表线上治理或固定 SLA。Hybrid / Rerank 目前是 KnowledgeBase RAG 的可选增强，默认关闭；v6 已验证“未完整配置不外呼 + identity fallback”，Phase 3 已小规模验证真实 rerank provider 可调用且不破坏核心 gate，v2.2 hard fixture 进一步观察到 target 排序提升和 distractor 降权，但仍不能写成大规模 relevance benchmark。
- RAG Quality Upgrade v1 的新增 eval 仍是 `MockEmbeddingProvider` + `InMemoryVectorStoreClient` + synthetic answer 的离线门禁；它可以防止明显的 retrieval / citation / answer coverage 退化，但不代表真实 embedding、真实 rerank 或真实回答模型的效果评测已经完成。
- RAG Real QA Eval v1 的离线部分仍是合成基线：它把 case 组织得更接近真实问答类型和质量指标，但不单独代表真实 embedding / rerank / answer provider 的用户体验结论；当前已补一次真实链路 `run` smoke 作为小规模 runtime evidence，仍不能写成大规模 relevance benchmark。
- RAG Quality Upgrade v3 已在真实 embedding + Qdrant 链路上证明 smoke 级 populated-KB no-evidence 可拒答：低于 `0.50` 的候选不进入 grounded QA，QA 返回 no-evidence 且不生成 citation。该结论仍是 smoke 级门禁，不等于跨大规模语料、复杂领域和全部问法的生产 relevance benchmark。
- Qdrant 已有 adapter、payload mapping、fake server 测试、preflight 参考和真实 tunnel smoke；普通测试不依赖远程 Qdrant，复现真实 Qdrant 仍需通过本地 `.env` 显式配置可用 endpoint / tunnel、`RAG_VECTOR_STORE_PROVIDER=qdrant` 和 `RAG_QDRANT_COLLECTION`。
- 持久化 RAG chunk 与 indexing workflow 已接入 parse success 自动触发；当前触发器为最小异步 service 调用，尚未独立 MQ / Outbox 化。
- Agent RAG QA 已接入新 RAG 查询链路，但仍是最小工具路由，不是复杂 LLM planner。
- ToolSpec / ToolCall API 目前是内部后端底座，Agent 只渐进复用 status 与 `rag_qa_tool` 调用；Function Calling 目前仅支持 OpenAI-compatible tools schema / mock tool_call adapter，不等于已接真实模型、MCP、KnowledgeBase Agent Tool 或完整通用工具编排。
- KnowledgeBase RAG v1 已提供后端管理、跨文档 retrieval、非流式 QA、前端可观测展示、Hybrid / Rerank 可选增强和离线 eval / smoke；尚未接 KnowledgeBase SSE 或 Agent / ToolSpec 主链路。
- Conversation Context Management 当前仍为非流式 MVP：不做后台自动摘要生成、不做真实模型长期记忆抽取、不持久化完整 prompt / evidence 原文、不接管现有 Agent 主链路；KnowledgeBase evidence 只通过既有 retrieval service 获取。
- Memory Quality Eval v1 / Memory Governance 已有离线 test-side 门禁、真实链路 smoke、治理门禁 smoke 和一轮 Memory 产品化 UI 验证，但仍不等于长期记忆真实模型抽取或大规模个性化效果评测；当前验证的是规则式候选、状态分层、ACTIVE memory 进入上下文、RAG evidence 不混成 memory、trace 计数、权限边界、冲突 suggestion 接受前阻断和前端管理可解释性。
- 前端重点页面已完成产品化文案收口：页面层不再使用“求职 / 面试 / MVP / smoke / 生产级”等内部或过度承诺表达，改为工作空间、引用来源、上下文溯源、会话记忆、工具链等用户可感知口径；工程边界仍保留在 ai-dev 文档中。
- Conversation Context Management 登录态 runtime smoke 已完成核心 MVP 链路：2026-06-13 已按用户授权通过当前本机 SSH tunnel 入口对云服务器 Docker MySQL 执行 `007_init_conversation_context.sql` 并确认五张 T013 表存在；随后本地前后端启动、健康检查、`/conversations` 登录态页面、创建会话、发送消息、trace、summary refresh、候选记忆提取 / 接受、ACTIVE 记忆进入第二轮 Agent Memory 上下文均验证通过。2026-06-13 追加完成带真实 KnowledgeBase 文档 evidence 的 API + 浏览器端到端 smoke：绑定 KB 的 Agent Memory 会话可触发 RAG evidence，trace 显示 `Evidence=1`、`ragTriggered=true`、`ragRequired=true`、命中文档分布 `#94: 1`。
- `scripts/smoke/cloud-quality-smoke.ps1 -Mode run` 会创建临时用户、临时文档、KnowledgeBase、Conversation 和本地脱敏 artifact；artifact 默认不提交。2026-06-27 的 PASS 记录位于 `docs/showcase/DEMO_SMOKE_RECORD.md`，本地 artifact 位于 `backend/target/smoke/docpilot-cloud-quality-20260627022219-37efd4/artifact.json`。
- 新 chunking 策略只影响后续 indexing；除已重建的 `83/84/85/86` 外，其他既有文档必须 rebuild / reindex 后，MySQL chunk 和 Qdrant 向量 payload 才会反映新的 chunk 质量。
- Agent 不是多智能体自主规划。
- `llm_execute` / real provider 等能力如果默认关闭，要视为待显式配置和 runtime 验证，不能写成默认生产能力；Function Calling 不能写成生产默认接管。
- 没有线上 SLA，不写 100% 可靠。

## 4. 当前生产化推进优先级

1. Quality Loop v2 已完成一轮 RAG Real QA Eval、Memory Quality Eval 和 Frontend UX Audit 闭环，并补过 `360px` / `320px` 极窄移动端、长 memory 检查、KnowledgeBase 技术字段产品化降噪、更难 rerank uplift 离线 fixture、真实 rerank hard smoke、代表语料 rerank 多 case eval 和 Memory 产品化第一片。
2. 下一阶段优先处理 ParseTask / reparse 产品恢复入口、前端 citation locator 可见性、Memory Governance v1 补充测试 / smoke；之后可继续做 Memory 版本历史 / 审计、Memory 抽取质量更真实的 provider 小样本验证，或回到 RAG 方向做 hard negative corpus、answer faithfulness 更细粒度审计。
3. 继续保持 Conversation Memory 与 KnowledgeBase evidence 分层：短期上下文、长期记忆、RAG evidence 和 trace 各自可解释。
4. 保持真实体验回归门禁：citation 展示、移动端会话布局、KB 多文档覆盖、Conversation Trace、权限隔离和 artifact 脱敏都要继续以真实 smoke / runtime evidence 收口。
5. 保持 README / docs 展示口径与真实 smoke 证据一致，不把 smoke 级 PASS 写成线上 SLA 或大规模生产 benchmark。

## 5. 事实源规则

- 当前任务看 `docs/ai-dev/CURRENT_TASK.md`。
- RAG 总路线看 `docs/ai-dev/ROADMAP_RAG.md`。
- 历史记录看 archive 或旧大文件。
- 不要让旧 TODO 覆盖当前路线。
- 文档和代码冲突时，以代码、测试和可运行结果为准。
- 自驱迭代模式采用真实链路优先验证：mock / unit test 只作为快速回归门禁；涉及 RAG、Memory、Conversation Trace、权限隔离和前端体验的质量结论，环境可达时应以真实 smoke / runtime evidence 收口。
- 用户已授予自驱模式下的受控真实验证权限：允许本地 tunnel / backend / frontend、真实 smoke、临时 smoke 数据、本机已有真实配置和 ignored 脱敏 artifact；远程破坏性操作、删数据、改 schema、push 和大规模高成本 provider eval 仍需单独确认。

## 6. 最近安全加固

- 2026-06-13 已修复云服务器 Prometheus 9090 公网暴露：`docpilot-prometheus` 仅绑定远程本机 `127.0.0.1:9090`，远程 `firewalld` 已移除 `9090/tcp` 放行，公网 `<PUBLIC_IP_REDACTED>:9090` 验证不可连；腾讯云安全组仍建议在控制台重新检测并收口云侧入站规则。
- T009 已补充 RAG scope guard：retrieval / QA / Agent `rag_qa_tool` / parse success indexing trigger 均以 userId、documentId、indexVersion 作为最小隔离边界。
- Vector search 仍依赖 metadata filter；service 层新增返回 hit 的二次校验，防止跨用户、跨文档或跨版本 citation 泄露。

## 7. 2026-07-13 ParseTask / embedding batch 当前事实

- 当前百炼 `text-embedding-v4` OpenAI-compatible embedding endpoint 单条请求和短 batch 可用，返回向量维度为 `1024`。
- 当前 Qdrant collection `docpilot_rag_v2` 可达、状态 green、vector size 为 `1024`，与本地 `RAG_QDRANT_DIMENSION=1024` 一致。
- 已确认真实解析失败 task `1322` 的根因不是 parser、Qdrant collection 不存在或维度不一致，而是 OpenAI-compatible embedding provider 一次提交约 `18` 个 chunks，超过百炼 batch size `10` 限制，provider 返回 HTTP 400。
- OpenAI-compatible embedding provider 已改为最多 `10` 条一组拆分 batch；这影响后续所有真实 embedding indexing / rebuild / retry。
- ParseTask 的 RAG indexing 失败错误现在采用结构化安全摘要，不再持久化原始 provider message；错误摘要用于用户可见 status API 和后端日志诊断。
- 历史上 task `1322` 需要新版后端 retry / reparse 才能恢复；2026-07-14 已只读核验其当前为 `SUCCESS`、`retry_count=2`，document `1431` 为 `SUCCESS`，MySQL / Qdrant parity 为 `12 / 12 / 12 / 12`，不再保持未恢复状态。
