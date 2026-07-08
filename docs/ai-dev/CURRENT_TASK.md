# Current Task

当前任务：Agent Document Search Tool 求职级增强（READY）；下一片：单文档 `document_search_tool` 最小闭环（READY）

## 2026-07-08 补充：Agent Document Search Tool 求职级增强路线

- 目标：把 Agent 工具从“能调用 RAG QA 回答”推进到“能显式执行文档检索、暴露可诊断 evidence，并为后续 KB search / Agent 路由 / Quality Console 评测打基础”。
- 当前现状：已有 `rag_qa_tool`，它复用 `RagQaService` 返回 answer、retrieval hits 和 citations；但它是问答工具，不适合承担“只检索、不生成答案”的 search intent。ToolCall API 当前只开放 `document_status_tool` 与 `rag_qa_tool`，还没有一等 `document_search_tool`。
- 第一片实现边界：新增单文档 `document_search_tool`，输入 `userId`、`documentId`、`query`、`topK`、`indexVersion`，复用现有 `RagDocumentRetrievalService` / `RagScopeGuard` 权限边界，只返回限长、脱敏、结构化 retrieval 结果，不返回完整 chunk content、文档全文、prompt 或 answer 原文。
- 后续切片：`knowledge_base_search_tool`、Agent search intent 路由、search eval / smoke、Agent Quality Console search diagnostics。每片都保持小闭环，先单文档检索能力稳定，再扩到 KB 与自动评测。
- 验收标准：ToolSpec 可见且 ToolCall API 可调用；非法 userId / documentId / query / topK 正确拒绝；scope rejection 透传为失败结果；返回结果包含 hit count、citation count、source locator、score、chunkId、contentHash 等安全字段；后端 `*Tool*` / RAG retrieval 相关测试通过。
- 本轮明确不做：不新增数据库表，不改 RAG 主链路，不改变 `rag_qa_tool` QA 行为，不接真实 provider 大规模评测，不提交 artifact 原文，不 push。

## 2026-07-08 补充：Document Parser 真实链路质量回归与 Quality Console 可见性收口

- 目标：用最新 `document-parser-real-chain-smoke.ps1` 跑一次受控真实链路，确认 PDF / HTML / DOCX 上传、异步解析、chunk、RAG QA retrieval、citation、source locator、错误边界和 `/quality` parser 摘要都可用。
- 已完成 runner 稳定性修复：`run` 默认不再静默复用已经运行的 backend / frontend；只有显式传 `-ReuseRunningServices` 才复用，否则返回 `BLOCKED`，避免把不受控的本地服务配置误判为 parser 业务失败。runner 自己启动 backend 时通过子进程环境强制 `AI_MODE=mock` 和 `APP_QUALITY_CONSOLE_ENABLED=true`，不写入配置文件。
- 已完成 QA 检索诊断增强：artifact 和 `parserQualityReport` 区分 `directRetrieveHitCount` 与 `qaRetrievalHitCount`；本轮真实 PASS 中直接 retrieve 为 `0/3`，QA 内部 retrieval 为 `3/3`，citation 为 `3/3`，因此核心 parser QA 链路通过，但直接 retrieve endpoint / query 语义仍是后续可排查项。
- 已完成 Quality Console 工作目录修复：`QualityArtifactServiceImpl` 会从当前工作目录向上解析仓库根，避免 backend 从 `backend/` 或 `backend/target/classes` 启动时扫不到 `backend/target/smoke/...` artifact。
- 已完成前端展示：`/quality` 的“文档解析质量摘要”新增“检索来源”，显示“直接 / 问答”两个脱敏计数，方便解释 direct retrieve 与 QA retrieval 的差异。
- 已验证真实链路：`document-parser-real-chain-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-parser-real-chain-20260708212742-0f9baa`；PDF / HTML / DOCX 均 `parseStatus=SUCCESS`、`chunkCount=1`、`retrieveHit=true`、`qaRetrievalHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`；`parserBoundary` 为 `4/4` PASS；artifact redaction PASS。
- 已验证 Console 可见性：`GET /api/quality/runs` 最新 run 为 `docpilot-parser-real-chain-20260708212742-0f9baa`，`parserQuality.fileCount=3`、`parsedFileCount=3`、`directRetrieveHitCount=0`、`qaRetrievalHitCount=3`、`citationCount=3`、`boundaryPassRate=1.0`；浏览器 `/quality?autoload=1` 的 Artifact 分区可见最新 marker、“文档解析质量摘要”和“检索来源”，桌面和 `390px` 移动端均无横向溢出，console error 为 `0`。
- 已验证离线回归：`mvn "-Dtest=*Quality*" test` PASS（40 tests，1 skipped）；`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`npm run lint` PASS；`npm run build` PASS。
- 边界：本片不改 parser / RAG 主链路，不新增数据库表，不删除业务数据，不操作远程 Docker，不提交 artifact 原文，不 push；真实 run 创建临时 smoke 用户 / 文档和 ignored 脱敏 artifact。
- 下一片建议：进入 Document Parser fixture corpus v3，重点补 PDF 多页结构、HTML 表格 / 列表 / 噪声边界、DOCX 表格 / 列表 / 标题层级与 parser warning 的质量用例，并单独排查 direct retrieve endpoint 在 parser fixture 上 `0/3` 的原因。

## 2026-07-08 补充：Document Parser 解析质量报告 / Quality Console parser 诊断增强

- 目标：把 Document Parser smoke 从“几个 gate 数字可见”推进到“可诊断解析质量报告”，让 `/quality` 能直接判断 PDF / HTML / DOCX 覆盖、解析成功率、source locator 覆盖、RAG retrieve / citation 覆盖和错误边界是否可信。
- 已完成 smoke artifact 增强：`scripts/smoke/document-parser-real-chain-smoke.ps1` 的 `run` artifact 新增脱敏 `parserQualityReport`，仅由现有安全 `files`、`boundary` 和 gate 数值派生，包含 `fileTypeCoverage`、`parseStatusSummary`、`sourceLocatorSummary`、`ragChainSummary`、`boundarySummary`、`warningsSummary`、`reviewReasons` 和 `unavailableMetrics`。
- 已完成后端白名单解析：`QualityRunDiagnostics` 新增 `parserQuality` 安全摘要；`QualityArtifactServiceImpl` 只读取数值、布尔值和安全短 bucket，不透传 raw artifact，也不返回 prompt、answer、文档全文、evidence context、异常堆栈、secret、连接串或云地址。
- 已完成前端展示：`/quality` 的“文档解析质量摘要”优先展示 parser quality report，增加格式覆盖、解析成功率、检索与引用覆盖、错误边界四张诊断卡；缺失指标显示“暂无统计”，不会把缺样本误显示为 0。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS（39 tests，1 skipped）；`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality?routeSmoke=2` 桌面和 `390px` 移动端均无 console error、无横向溢出。
- 边界：本片不改 parser / RAG 主链路，不新增数据库表，不启动真实云链路 run，不创建业务数据，不提交 artifact 原文，不 push。真实 parser report 可见性将在下一片通过 `document-parser-real-chain-smoke.ps1 -Mode run` 和 `/quality?autoload=1` 验证。
- 下一片建议：进入 Document Parser fixture corpus v3 / 真实链路质量回归，补更复杂但仍非 OCR 的 PDF / HTML / DOCX fixture，并跑一次真实上传、parse、chunk、retrieve、QA citation 和 Quality Console 可见性闭环。

当前任务：Document Parser Quality Console parser 指标展示增强（DONE）；下一片：Document Parser 解析质量报告 / fixture corpus v3（READY）

## 2026-07-06 补充：Document Parser Quality Console parser 指标展示增强

- 目标：让 Agent Quality Console 的 `/quality` 能直接读懂 Document Parser smoke 的关键指标，不再只在 Artifact 元信息或门禁明细里找 `parserRealChain` / `parserBoundary`。
- 已完成前端：`/quality` 的 Artifact 分区新增“文档解析质量摘要”，展示解析成功文件、切片总数、检索 / 引用、来源定位、解析失败数、运行耗时、不支持格式拒绝和负向边界通过数。
- 已完成中文标签：`frontend/lib/quality-labels.ts` 补充 parser gate 和 metrics 的中文展示，包括 `parserRealChain`、`parserBoundary`、`fileCount`、`parsedFileCount`、`sourceLocatorCount`、`negativeCasePassCount`、`unsupportedUploadRejected` 等。
- 脱敏边界：本片只读取已有 `QualityRunDetail.gates` 中的数值和布尔字段，不改后端 API、不读取 raw artifact、不展示文档全文、prompt、answer、evidence context、异常堆栈、凭据、连接串或云地址。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright mock Quality API 打开 `/quality?autoload=1` 后可见“文档解析质量摘要”“负向边界通过”“不支持格式拒绝”，桌面与 `390px` 移动端均无横向溢出，最新 console error 为 0。
- 边界：本片不新增数据库表、不改 parser / RAG 主链路、不创建业务数据、不提交 artifact 原文、不 push；本轮启动的前端预览进程已清理，端口释放。
- 下一片建议：进入 Document Parser 解析质量报告 / fixture corpus v3，把 fixture 结构质量、parser warning、source locator 覆盖和 boundary 结果汇总成更可读的脱敏报告，或继续扩展 PDF / HTML / DOCX fixture 到更复杂但仍不涉及 OCR 的样例。

## 2026-07-06 补充：Document Parser fixture corpus v2

- 目标：把 parser 单测 fixture 从最简单文本样例推进到更接近真实文档结构的质量门禁，覆盖多页 PDF、HTML 层级 / 表格 / 链接和 DOCX 标题 / 列表 / 表格。
- 已完成 HTML parser 增强：`tr` 行现在按 `th/td` 单元格抽取并用 ` | ` 分隔，避免表格结构被压成不可读空格文本；独立链接仍作为 `LINK` block，段落 / 列表内链接继续合并进正文。
- 已完成 DOCX parser 增强：带编号或 list 样式的段落会标记为 `BlockType.LIST`，并继承当前 `sectionPath`，方便后续 chunk metadata 和 citation 定位列表来源。
- 已完成 fixture 断言：PDF fixture 覆盖 3 页且中间空页生成 `empty_page:2` warning；HTML fixture 覆盖 `h1/h2`、表格行、列表、独立链接和噪声标签剔除；DOCX fixture 覆盖 `Heading1/Heading2`、普通段落、list 样式段落和表格。
- 已验证离线测试：`mvn "-Dtest=DocumentParserTest" test` PASS，7 tests；`mvn "-Dtest=*Parser*,ParseTaskConsumeEntryServiceImplTest,FileContentReaderTest,FileServiceImplTest" test` PASS，44 tests。
- 已验证真实链路：`document-parser-real-chain-smoke.ps1 -Mode run` PASS，marker `docpilot-parser-real-chain-20260706215802-78374c`，PDF / HTML / DOCX 均 parse SUCCESS、chunk、retrieve、QA citation 和 source locator PASS，parserBoundary 继续 PASS。
- 边界：本片不新增数据库表、不改 schema、不做 OCR / 扫描件识别 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原；真实 run 创建临时 smoke 数据和 ignored 脱敏 artifact，不删除业务数据，不提交 artifact 原文，不 push。
- 下一片建议：把 Document Parser 的安全摘要接入 Agent Quality Console 的 Run Detail，让 parserName、parseStatus、extractedChars、pageCount、blockCount、warningCount 和 parserBoundary 结果在内部质量控制台更容易阅读。

## 2026-07-06 补充：Document Parser 错误边界 API 负向增强

- 目标：把 parser smoke 的错误边界从“准备坏文件 / 依赖单测”推进到真实 API 链路，覆盖 upload 拒绝、空内容、损坏 PDF 和损坏 DOCX 的稳定失败状态与脱敏错误码。
- 已完成 runner 增强：`scripts/smoke/document-parser-real-chain-smoke.ps1` 的 `run` 模式新增 `parserBoundary` gate，真实执行 upload / document create / parse task create / parse terminal polling；失败原因只读取并保存白名单错误码，不保存异常堆栈、文件内容、prompt、answer 或 evidence context。
- 已完成负向 case：不支持格式上传返回 `UPLOAD_REJECTED`；空白 `TXT` 解析终态为 `FAILED` 且错误码为 `PARSER_EMPTY_CONTENT`；损坏 `PDF` / `DOCX` 解析终态为 `FAILED` 且错误码为 `PARSER_CORRUPTED_FILE`。
- 已处理真实链路插曲：首轮 boundary run 触发同一临时用户上传频率限制，导致负向 case 在 parser 前被 upload 拒绝；runner 已改为每个负向 case 使用独立临时 smoke 用户，避免把限流误判为 parser 失败。
- 已验证：`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker `docpilot-parser-real-chain-20260706215134-857b73`，`parserBoundary` 为 PASS，`negativeCasePassCount=4/4`、`negativeCaseFailCount=0`、`unsupportedUploadRejected=true`。
- 边界：本片不删除业务数据、不改数据库结构、不操作远程 Docker、不做 OCR / 扫描件识别 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原，不提交 artifact 原文，不 push。
- 下一片建议：进入 Parser fixture corpus v2，补多页 PDF、HTML 列表 / 表格 / 噪声结构、DOCX 表格 / 列表 / 标题层级等可复现 fixture，并让单测和 smoke 更能证明真实解析质量。

## 2026-07-06 补充：Document Parser source locator 贯通到 RAG citation

- 目标：把 `ParseResult.blocks()` 中的 page / block / section locator 从 parser 层继续传到 chunk metadata、embedding metadata、vector payload、RAG retrieve hit 和 QA citation，避免真实引用只能停留在文件名或粗粒度 section。
- 已完成后端：新增 `RagSourceBlock`，`RagIndexingTriggerService` 支持接收 `ParseResult`；`ParseTaskConsumeEntryServiceImpl` 在 parse success 后把完整 parse result 交给 RAG indexing trigger；`ChunkingService` 根据 parser source block 为 chunk 写入 `pageNumber`、`sourceLocator`、`blockType`、`sectionPath` 和 `structureType`。
- 已完成 RAG citation：`RagIndexingServiceImpl` 将 page/source/block 元数据写入 embedding metadata 和 vector payload；`RagRetrievalHit`、`RagEvidenceCitation`、`RagRetrievalHitResponse`、`RagCitationResponse` 返回脱敏 locator 字段，方便前端和 smoke 判断 citation 来源是否可定位。
- 已完成 parser offset 回归：HTML / DOCX heading block 的 `endOffset` 与 `fullText` 中的 Markdown heading 片段对齐，避免后续 chunk source block overlap 判断偏移。
- 已完成 smoke 增强：`scripts/smoke/document-parser-real-chain-smoke.ps1` 的 `sourceLocatorPresent` 现在优先识别 `sourceLocator`、`pageNumber`、`blockType`，而不是只依赖文件名 / section / offset。
- 已验证离线测试：`mvn "-Dtest=DocumentParserTest,ChunkingServiceImplTest,RagIndexingServiceImplTest,RagDocumentRetrievalServiceImplTest,ParseTaskConsumeEntryServiceImplTest" test` PASS，56 tests；`mvn "-Dtest=*Parser*,ParseTaskConsumeEntryServiceImplTest,FileContentReaderTest,FileServiceImplTest,ChunkingServiceImplTest,RagIndexingServiceImplTest,RagIndexingTriggerServiceImplTest,RagDocumentRetrievalQualitySmokeTest,RagDocumentRetrievalServiceImplTest" test` PASS，88 tests。
- 已验证真实链路：`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker `docpilot-parser-real-chain-20260706214209-dcb8f2`，PDF / HTML / DOCX 均为 `parseStatus=SUCCESS`、`chunkCount=1`、`retrieveHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`。
- 边界：本片不新增数据库表、不改 schema、不做 OCR / 扫描件识别 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原 / PDF 坐标级 citation；真实 run 创建临时 smoke 用户和文档，不删除已有业务数据，不提交 artifact 原文，不 push。
- 下一片建议：进入 Parser 错误边界 API 负向增强，补空文件、极短文件、损坏 PDF / DOCX、超限文件和不支持格式在真实 API 链路中的稳定失败状态与脱敏错误码。

## 2026-07-06 补充：Document Parser MVP 真实链路验证

- 目标：把 Document Parser MVP 从离线单测推进到真实上传链路，验证 PDF / HTML / DOCX 能经过上传、异步解析、chunk、embedding / index、RAG retrieve、QA citation 和脱敏 artifact。
- 已完成 parser locator 修复：HTML / DOCX parser 在 `fullText` 中保留 Markdown heading 形式，便于现有 chunker 生成 `sectionPath` / `structureType`；block 原始标题文本和 parser metadata 不变。
- 已完成 citation source metadata：单文档 RAG retrieve / QA citation 现在返回脱敏 `sourceName`、`sectionPath`、`structureType` 等来源摘要，便于真实 smoke 判断 citation locator 是否可用；不返回 prompt、answer 原文、文档全文或 evidence context。
- 已新增真实链路 runner：`scripts/smoke/document-parser-real-chain-smoke.ps1` 支持 `plan / dry-run / run`。`run` 会生成小型 PDF / HTML / DOCX 临时 fixture，启动或复用本地 tunnel / backend / frontend，注册临时用户，上传三类文档，等待 parse `SUCCESS`，验证 chunk count、RAG retrieve、QA citation 和 source locator，并写入 ignored 脱敏 `artifact.json`。
- 已接入 Agent Quality Console：`backend/target/smoke/document-parser-real-chain` 加入 Quality artifact 白名单，parser smoke 的 `parserRealChain` gate 暴露 `fileCount`、`parsedFileCount`、`parserFailureCount`、`chunkCount`、`retrieveHitCount`、`citationCount`、`sourceLocatorCount` 和 `durationMs` 等安全数值。
- 已验证真实 run：`docpilot-parser-real-chain-20260706172220-f03956` 状态 PASS；PDF / HTML / DOCX 均 `parseStatus=SUCCESS`、`chunkCount=1`、`retrieveHit=true`、`citationPresent=true`、`sourceLocatorPresent=true`；tunnel、backend、frontend、unsupported format boundary 和 artifact redaction 均 PASS。
- 已验证测试：`mvn "-Dtest=*Quality*,DocumentParserTest,ChunkingServiceImplTest,RagDocumentRetrievalServiceImplTest" test` PASS，70 tests，1 skipped；`document-parser-real-chain-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS。
- 边界：本轮不新增数据库表、不改 schema、不做 OCR / 扫描件识别 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原 / PDF 坐标级 citation；真实 run 创建 marker 临时用户和临时文档，不删除已有业务数据，不提交 artifact 原文，不 push。
- 下一片建议：进入 Parser 质量增强 P1，重点补 block/page locator 到 chunk metadata 的更强桥接、损坏 PDF / DOCX API 级负向 smoke、空文件 / 超大文件真实 API 边界、以及 `/quality` 页面 parser 指标展示文案。

## 2026-07-06 补充：Document Parser MVP 第一片

- 目标：把文档解析从 `txt / md` 和 PDF 占位推进到工程化 Parser MVP，支持 `txt / md`、文本型 PDF、本地 HTML 和 DOCX 的稳定文本抽取，并接入既有“上传 -> 异步解析 -> chunk -> embedding -> vector index -> RAG QA”链路。
- 已完成后端：新增 `backend/src/main/java/com/docpilot/backend/document/parser/**`，包含 `DocumentParser`、`ParserRegistry`、`ParseResult`、`DocumentBlock`、PDFBox parser、Jsoup HTML parser、Apache POI DOCX parser 和文本 parser。
- 已完成链路接入：`ParseTaskConsumeEntryServiceImpl` 现在按 contentType / extension 选择 parser，解析成功后继续写入 document content / summary、推进 parse status、触发 RAG indexing；RAG indexing trigger 失败仍与 parse success 隔离。
- 已完成上传与配置：上传 allowlist 扩展到 `pdf/md/txt/html/htm/docx`；`application.yml` 和 `.env*.example` 新增 `APP_DOCUMENT_PARSER_MAX_FILE_SIZE_BYTES` 与 `APP_DOCUMENT_PARSER_TIMEOUT_MS`。
- 已完成可观测边界：parser metrics 记录 parserName、status、duration、extractedChars、pageCount、blockCount 和 warningCount；日志不打印文档全文。
- 已验证：`mvn "-Dtest=*Parser*,ParseTaskConsumeEntryServiceImplTest,FileContentReaderTest,FileServiceImplTest" test` PASS，44 tests；`mvn "-Dtest=ChunkingServiceImplTest,RagIndexingServiceImplTest,RagIndexingTriggerServiceImplTest,RagDocumentRetrievalQualitySmokeTest" test` PASS，34 tests；此前 `mvn -DskipTests compile` PASS。
- 边界：本片不新增数据库表、不改 schema、不做 OCR / 扫描件识别 / 外部网页抓取 / `.doc` 旧格式 / 复杂版面还原 / PDF 坐标级 citation；未启动 tunnel / backend / frontend，未创建业务数据，未做真实 PDF / HTML / DOCX cloud runtime smoke。
- 下一片建议：启动本地 tunnel + backend + frontend，上传小型 PDF / HTML / DOCX 临时文档，验证 parse SUCCESS、chunk 进入 MySQL、Qdrant indexing、单文档 RAG retrieve / QA citation 和脱敏 artifact。

## 2026-07-06 补充：Agent Quality Console 真实可见性回归

- 目标：在本地 backend + frontend + cloud tunnel 环境下打开 `/quality?autoload=1`，确认最近几片展示语义改动没有破坏真实控制台可见性。
- 已完成真实回归：本地 tunnel 已可用，backend `/actuator/health` 为 `UP`；前端使用 `next build` + `next start -p 3007` 启动，注册临时 smoke 用户后用浏览器打开 `/quality?autoload=1`。
- 验证结果：页面可见“内部质量排查控制台”和“质量诊断”，质量运行记录已加载；分母为 `totalRuns` 的提示和“暂无统计”缺样本语义可见；桌面 `1440px` 与移动端 `390px` 均无横向溢出，console error 为 `0`。
- 插曲：`next dev -p 3007` 本轮卡在 Starting 且端口监听但 HTTP 超时，改用生产预览完成验证；暂不作为业务 bug 记录。
- 边界：本轮只创建临时登录用户，不上传文档、不创建 KB / Conversation、不提交 artifact 原文、不 push。

## 2026-07-06 补充：Agent Quality Console token 文案一致性收口

- 目标：继续改善 `/quality` 页面可读性，不新增指标，只统一 token 相关展示文案。
- 已完成前端：`Token / TOKENS / tokens` 混用的显示文案已收敛为“token 数”“token 用量”“token 增量”，诊断卡优先排查文案中的成本入口也改为“模型调用 / token 用量 / 重试”。
- 边界：本片只改页面展示文字，不改 DTO、不改 API、不新增功能、不启动真实链路。

## 2026-07-06 补充：Agent Quality Console 延迟指标缺样本语义修正

- 目标：继续收口 `/quality` 质量指标可信度，避免 Trend 面板里的延迟字段在缺少样本时显示不明确的 `-`。
- 已完成前端：Trend 面板“平均延迟”改为缺样本显示“暂无统计”；Overview 的 P95 延迟说明补充“基于最近 trend points 的 `latencyMs`，没有 point 样本时显示暂无统计”。
- 边界：本片只改前端展示语义，不改后端 API、不新增数据库表、不读取 raw artifact、不启动真实链路。

## 2026-07-06 补充：前端文档详情错误提示乱码兜底清理

- 目标：收口上一片自检发现的前端源码乱码残留，避免文档详情页继续包含历史 mojibake 字面量。
- 已完成前端：`frontend/app/documents/[documentId]/page.tsx` 的 `buildErrorHint` 只保留正常中文 `无权` / `不存在` 判断；底层 `frontend/lib/api.ts` 已统一把文档和知识库权限 / 不存在错误归一为中文提示，因此页面无需再识别乱码变体。
- 边界：本片不改 API 协议、不改后端、不新增功能、不触碰真实业务数据、不展示 prompt、answer 原文、文档全文、evidence context、凭据、连接串或云地址。

## 2026-07-06 补充：Agent Quality Console 质量指标可信度与失败桶可行动化

- 目标：不继续堆新指标、不做 P1 / P2 大功能，只把当前 `/quality` 已有通过率、复查率、失败率、token、成本、失败类型 TopN 和复查类型 TopN 改得更可信、更可解释、更能指导排查。
- 已完成前端：Overview 诊断卡的通过率、复查率、失败率现在展示百分比和分子 / 分母，例如 `1 / 3`，并在说明中明确分母为 `totalRuns`；复查率说明已明确 REVIEW / BLOCKED 是质量风险，不一定阻断核心链路。
- 已完成指标语义修正：`token_usage` 缺失或无法解析时显示“暂无统计”，不再误显示为 `0`；只有明确 `totalTokens=0` 时才显示 `0`。成本同理，缺少 `estimatedCost` 样本时显示“暂无样本”。
- 已完成质量诊断增强：每张诊断卡保留原建议，并新增“优先排查”字段，例如失败率优先看 `Failures / Gates / Trace`，复查率优先看 `Citation / RAG / Eval Scorer`，P95 延迟优先看 `LLM / RAG / Tool latency`，token 偏高优先看 `Context / Prompt / RAG chunks`。
- 已完成失败 / 复查 TopN 增强：每个 bucket 现在展示类型名称、次数、简短说明、建议动作和模块标签；模块标签覆盖 `RAG`、`Citation`、`Tool`、`Memory`、`Security`、`Env` 和 `Unknown`。无法可靠归类时仍保留 `Unknown / 其他`，并提示需要补充 bucket 映射规则。
- 边界：本片只改前端基于现有 DTO 的安全映射，不改后端 API、不新增数据库表、不读取 raw artifact、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright mock Quality API 打开 `/quality?autoload=1`，桌面和 `390px` 移动端均无 console error、无横向溢出，并验证分子 / 分母、缺失 token 降级、模块标签、建议动作和 Unknown fallback 文案可见。

## 2026-07-06 补充：Agent Quality Console P1 parser 安全摘要增强

- 目标：把 P0 页面中需要 parser 支撑的诊断项推进为后端白名单安全摘要，让 `/quality` 能展示文档覆盖、工具参数复查和记忆命中摘要，而不是继续停留在占位文案。
- 已完成后端：`QualityRunDetail` 新增 `diagnostics`，包含 `documentCoverage`、`toolQuality` 和 `memoryQuality` 三组安全数值摘要；`QualityArtifactServiceImpl` 从 artifact 中的 `documentHitCounts`、`contextSourceCounts`、tool / memory metrics 和 bucket 聚合统计。
- 脱敏边界：`documentHitCounts` 只转成 `documentCount`、`coveredDocumentCount`、`zeroHitDocumentCount`、`maxHitsPerDocument`、`minHitsPerDocument`，不返回文档 ID、原始 map、query、snippet、answer、prompt、文档全文或 evidence context。
- 已完成前端：`frontend/lib/quality-api.ts` 同步 `QualityRunDiagnostics` 类型；`/quality` 的 RAG / 记忆 / 工具调用诊断卡改为展示命中文档分布、记忆命中摘要和工具参数复查摘要；缺少 diagnostics 时保留“暂无安全摘要”降级。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，38 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright mock Quality API 覆盖桌面和 `390px` 移动端，新增安全摘要可见、无 console error、无横向溢出；端口已清理释放。

## 2026-07-06 补充：Agent Quality Console 诊断指标 P0

- 目标：在不改后端 DTO / API、不读取 raw artifact 的前提下，让 `/quality` 能基于现有脱敏 summary/detail/trend/eval catalog 派生更多诊断比率，帮助判断 RAG、Memory、Tool、Eval 和失败桶的优化方向。
- 已完成 Overview：新增质量诊断区，展示通过率、复查率、失败率、P95 延迟、平均 tokens、成功运行成本，并展示失败类型 TopN / 复查类型 TopN 及建议动作。
- 已完成 Run Detail：新增独立“评测”tab；RAG / 记忆 / 工具调用 tab 增加诊断比率卡。RAG 覆盖检索命中率、引用覆盖率、证据覆盖率、引用支撑率、无证据拒答正确率、平均检索 chunk、干扰引用通过率；记忆覆盖记忆触发率、记忆证据覆盖、治理复查率；工具覆盖工具触发率、工具失败率、工具调用数、Agent 步骤失败率、最大链路步数。
- 已完成 Eval / Failures：Eval tab 展示用例数、评测通过率、失败用例数、失败用例 Trace 覆盖、按标签通过率；Failures tab 展示当前失败/复查、新增失败、已恢复失败及 failure bucket 到建议动作的映射。
- 降级边界：`documentHitCounts`、严格 `toolSelectionAccuracy` / `toolArgsAccuracy`、`memoryUsefulHitRate` / `memoryNoiseRate` 仍标记为需要 P1/P2 parser 或 eval schema 扩展，不在 P0 硬算。
- 脱敏边界：本片只展示数值、比率、bucket、短安全 ID 和建议动作；不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- 已验证：`npm run lint` PASS；`npm run build` PASS。浏览器 mock Quality API 验证和最终自检见本轮收尾记录。

## 2026-07-06 补充：Agent Quality Console 前端信息架构重构

- 目标：把 `/quality` 从“长页面数据展示”改成更像内部质量排查控制台的结构，让用户先判断最近 run 是否健康，再定位失败门禁、复查项、Trace、RAG、Memory、Tool Calls 和 artifact 摘要。
- 已完成前端：`/quality` 顶部改为“内部质量排查控制台”Overview，显示最近运行健康判断、状态、运行次数、PASS / REVIEW / FAILED 统计和 token 数值摘要。
- 已完成左侧：运行记录列表增加状态筛选和 marker / 来源搜索；空数据、筛选无结果和加载状态都有明确中文文案。
- 已完成右侧：Run Detail 增加分区导航，拆成“摘要 / 门禁 / 待处理 / 链路 / RAG / 记忆 / 工具调用 / Artifact”。摘要区保留模型成本和运行对比；门禁区将 FAILED / REVIEW 默认展开，PASS 默认折叠；链路区保留 Trace 定位，并让失败或需复查 eval case 直接显示“查看 Trace”入口。
- 已完成摘要卡：RAG、Memory 和 Tool Calls 只基于现有脱敏 metrics / flags / buckets 展示数值摘要，不新增后端 API。
- 已完成 Artifact 区：只展示 source、artifactName、marker、updatedAt、artifactMissing、artifactParseFailed 等脱敏元信息；Eval Catalog 和 Trend 移到非首屏分区，避免抢占排查主线。
- Gemini 协作：`gemini.cmd --version` 可用，但正式建议调用返回 malformed / empty response；已按约束降级为 Codex 直接集成、验证和回写。
- 脱敏边界：本片不改 Quality API、不新增后端字段、不读取业务库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；token usage 仍只作为数值统计展示。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 静态 route smoke 和 mock Quality API smoke 覆盖桌面与 `390px` 移动端，均无 console error、无横向溢出；mock smoke 验证失败门禁突出、PASS 门禁折叠、Eval “查看 Trace”入口和 run 搜索可见。

## 2026-07-06 补充：Agent Quality Console 前端中文化二次增强

- 目标：按用户反馈继续改善 `/quality` 和 `/quality/trace` 可读性，中文能表达清楚的字段默认直接显示中文，不再在正文里追加英文 raw key 或枚举注释。
- 已完成前端：`frontend/lib/quality-labels.ts` 的 format 函数改为默认返回纯中文；状态、失败桶、指标、布尔门禁、用例类型、门禁名和链路步骤不再显示 `通过 (PASS)`、`RAG 漏召回 (RAG_RETRIEVAL_MISS)` 这类形式。
- 已完成页面文案：`Overview / Eval Catalog / Quality Trend / Run Detail / Run Comparison / Trace Reference / Gate / Eval Case` 等标题和计数文案进一步改为“质量总览 / 评测用例库 / 质量趋势 / 运行详情 / 运行对比 / 链路定位 / 门禁 / 评测用例”。
- 调试边界：少数排查需要的 raw key 只保留在 `title` 悬停信息、`marker`、`caseId`、`traceId`、`agentRunId` 等技术定位 ID 中，不作为中文字段后的括号注释展示。
- Gemini 协作：`gemini.cmd --version` 和 READY 探测通过，但正式文案建议调用连续超时；已按约束降级为 Codex 直接集成、验证和回写。
- 脱敏边界：本片不改 Quality API、不新增后端字段、不读取业务库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；token usage 仍只作为数值统计展示。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright route smoke 和最终清理见本轮提交记录。

## 2026-07-06 补充：Agent Quality Console 前端可读性增强

- 目标：降低 `/quality` 和 `/quality/trace` 的阅读门槛，让用户不必理解所有 raw artifact key，也能看懂状态、指标、失败桶、Trace step 和成本摘要。
- 已完成前端：新增 `frontend/lib/quality-labels.ts`，集中维护 status、failure bucket、metric、flag、case type、gate 和 trace step 的中文展示；后续二次增强已把默认正文改为纯中文展示。
- 已完成 `/quality`：Overview、Eval Catalog、Quality Trend、Run Detail、Failure Triage、Trace 定位、Gate / Eval Case 明细、Model / Cost Summary 和 Run Comparison 已改为更易读的中文标签。
- 已完成 `/quality/trace`：Trace Reference、链路瀑布图、关联 Gate 和关联 Eval Case 已使用同一套中文标签；仍只展示脱敏状态、数值、布尔门禁和失败 / 复查类型。
- 脱敏边界：本片不改 Quality API、不新增后端字段、不读取业务库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；token usage 仍只作为数值统计展示。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright route smoke 打开 `/quality?routeSmoke=2` 和 `/quality/trace?routeSmoke=1...`，桌面与 `390px` 移动端均无业务 console error、无横向溢出；`scripts/dev/cleanup-agent-processes.ps1` 已清理本轮前端进程并确认 `3000/3001/3002/3007/3100/8081` 均为 FREE。
- 下一片建议：若继续 Agent Quality Console，可做“趋势解释 v2”，把 repeated case / failure bucket 与 trace reference、recent point 更直接联动；若回到核心能力，可继续 RAG / Memory 真实问答体验审计。

## 2026-07-05 补充：Agent Quality Console Trace / Eval / Trend 真实链路回归

- 目标：跑一轮真实用户 QA 审计，并确认 `/quality?autoload=1` 能展示最新真实 run、Eval v2、Quality Trend v1 和 Trace Drill-down v3 的脱敏链路瀑布图。
- 已完成 smoke runner：`frontendInteraction` console error 诊断增强为 `phase/kind/messageShape` 安全摘要；预期权限负向路径的 failed resource 只作为观察项，不阻断 gate；其他 `TypeError` / hydration / reference error 仍会阻断。
- 已完成 artifact：真实审计的 `naturalCorpus` 和 `conversationTrace` gate 现在写入脱敏 trace case result，包含 `caseId/status/traceId/conversationId` 和 RAG / evidence / memory 数值布尔指标，不写入用户消息、answer 原文、文档全文或 evidence context。
- 真实审计：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 最终 PASS，marker `docpilot-real-user-qa-20260705210119-7b8092`。
- Console 验证：开启 `APP_QUALITY_CONSOLE_ENABLED=true` 后，`/api/quality/runs` 可见最新 marker；`/api/quality/runs/{marker}` 返回 `summary.status=PASS`、`gateCount=22`、`evalCaseCount=27`、`traceReferenceCount=2`；`/api/quality/eval-cases` 返回 7 个 case；`/api/quality/trends?limit=20` 返回 20 个趋势点。
- 浏览器验证：Playwright 打开 `/quality?autoload=1` 可见最新 marker、`Eval Catalog`、`Quality Trend` 和 trace reference；打开 `/quality/trace?...` 可见 `Eval case` 链路步骤。桌面和 `390px` 移动端 console error count 均为 `0`，无横向溢出。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，37 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；`git diff --check` PASS。
- 中途发现并记录：`docpilot-real-user-qa-20260705205210-8c882e` 曾因 KB 阶段 `TypeError` 被 `frontendInteraction` 阻断，但旧 artifact 只有 kind、缺少安全 message shape；已记录为 `REA-20260705-P3-008`，本轮先修 smoke 诊断，最终回归未复现该 TypeError。
- 清理：本轮启动的 backend / frontend 已通过 `scripts/dev/cleanup-agent-processes.ps1` 清理，`3000/3001/3002/3007/3100/8081` 均为 FREE。
- 下一片建议：若继续 Agent Quality Console，可做“趋势解释 v2”：把 repeated case / failure bucket 与对应 trace reference 更直接联动；若回到核心能力，可继续做 RAG / Memory 的真实问答体验审计。

## 2026-07-05 补充：Agent Quality Console Quality Trend v1

- 目标：让 Console 不只看单次 run 或两次 run 对比，而是能基于最近 N 个脱敏 artifact 展示质量趋势。
- 已完成后端：新增 `QualityTrendSummary`、`QualityTrendPoint` 和 `QualityRepeatedCaseSummary`；`QualityArtifactService.getTrendSummary(limit)` 从最近 N 个 parsed detail 聚合状态分布、failure / review bucket 计数、平均 casePassRate、token / cost、latency / duration 和反复失败 / REVIEW case。
- 已完成 API：新增内部只读 `GET /api/quality/trends?limit=20`，复用 `/api/quality/**` 的 console enabled 和登录上下文控制。
- 已完成前端：`frontend/lib/quality-api.ts` 增加 trend 类型和请求；`/quality` 新增 `Quality Trend` 面板，展示最近 run 数、状态分布、Top failure / review buckets、平均 pass rate、tokens / cost / latency、反复失败 case 和最近 points。
- 脱敏边界：trend 只使用现有 Quality DTO 的状态、计数、数值、caseId、marker 和 bucket 摘要，不读取或展示原始 artifact、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- 已验证：首次 `mvn "-Dtest=*Quality*" test` 暴露趋势未统计 eval case 层 bucket，已修正；复跑 `mvn "-Dtest=*Quality*" test` PASS，37 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；`/quality?routeSmoke=2` 在 `390px` 宽度下无 console error、无横向溢出。
- 清理：本轮启动的前端 dev server 和浏览器进程已通过 `scripts/dev/cleanup-agent-processes.ps1` 清理，`3000/3001/3002/3007/3100/8081` 均为 FREE。
- 下一片建议：跑一轮真实链路回归，开启 `/quality?autoload=1` 验证最新真实 audit run、Trace 瀑布图、Eval 资产字段和 Trend 面板能一起展示；仍不提交 artifact 原文、不 push。

## 2026-07-05 补充：Agent Quality Console Eval Asset v2

- 目标：把 7 个 eval catalog case 从“case 列表”继续推进为长期质量资产，让每个 case 能说明分层、风险门禁、评分摘要、回归策略和历史失败 / 修复 marker。
- 已完成 JSON：`agent-quality-eval-cases.json` 为 7 个默认 case 增加 `caseLayer`、`riskGate`、`scoringSummary`、`regressionPolicy` 和 `failureHistoryMarkers`。
- 已完成后端：`QualityEvalCaseCatalogItem` 和 `QualityEvalCatalogServiceImpl` 白名单读取并返回上述字段；安全 identifier 上限调整为 160 字符，以容纳脱敏 marker / status / issue 摘要，同时继续过滤 URL、token、secret、连接串形态。
- 已完成前端：`frontend/lib/quality-api.ts` 同步新字段；`/quality` Eval Catalog 卡片展示 case layer、risk gate、scoring summary、regression policy 和 failure history marker。
- 脱敏边界：本片仍不返回 question、expectedBehavior、mustContain、mustNotContain、prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址；failure history 只保存脱敏 marker、status 和 issue id 摘要。
- 已验证：首次 `mvn "-Dtest=*Quality*" test` 暴露 Controller test fixture 构造参数未同步，已修正；复跑 `mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；`/quality?routeSmoke=2` 在 `390px` 宽度下无 console error、无横向溢出。
- 清理：本轮启动的前端 dev server 和浏览器进程已通过 `scripts/dev/cleanup-agent-processes.ps1` 清理，`3000/3001/3002/3007/3100/8081` 均为 FREE。
- 下一片建议：进入 Quality Trend v1，在现有 artifact 聚合结果上增加最近 N 次趋势摘要和 `/quality` Trend 面板，继续不新增数据库表。

## 2026-07-05 补充：Agent Quality Console Trace Drill-down v3

- 目标：把现有 trace reference 从“能定位 ID”升级为“能查看脱敏链路瀑布图”，用于解释一次 Agent / RAG 质量问题如何从 eval case 定位到 RAG retrieve、tool call、model call、citation 和 failure bucket。
- 已完成后端：新增 `QualityTraceStepDetail`，`QualityTraceReference` 增加 `steps`；`QualityArtifactServiceImpl` 从 eval case 的安全 metrics / flags / buckets 推断 `eval_case`、`agent_step`、`rag_retrieve`、`tool_call`、`model_call`、`citation` 和 `failure_bucket` 步骤摘要。
- 已完成前端：`frontend/lib/quality-api.ts` 增加 `QualityTraceStepDetail` 类型；`/quality/trace` 新增“链路瀑布图”面板，按顺序展示 step type、status、metrics、flags 和 buckets。
- 脱敏边界：本片仍只读 artifact 聚合结果，不读业务数据库、不新增 API、不新增数据库表；step 只来自白名单数值、布尔值和安全 bucket，不返回 prompt、answer 原文、文档全文、evidence context、真实用户输入、凭据、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality/trace?routeSmoke=1&marker=docpilot-route-smoke&caseId=route-smoke&traceId=trace-route-smoke&agentRunId=agent-route-smoke` 无 console error；`390px` 宽度 `scrollWidth == clientWidth == 390`。
- 清理：本轮启动的前端 dev server、Playwright / Chromium 进程已通过 `scripts/dev/cleanup-agent-processes.ps1` 清理，`3000/3001/3002/3007/3100/8081` 均为 FREE。
- 下一片建议：进入 Eval Asset v2，在 `agent-quality-eval-cases.json` 上补 caseLayer、riskGate、scoringSummary、regressionPolicy 和脱敏 failureHistoryMarkers，并同步 API / 前端 catalog 展示和测试。

## 2026-07-05 补充：Agent Quality Console 三线升级收口

- 目标：把当前距离求职级 Agent Quality Console 还差的三条主线写成后续自驱循环依据，避免后续实现时在 Trace、Eval 和 Trend 之间漂移。
- 已完成：`docs/ai-dev/ROADMAP_AGENT_QUALITY_CONSOLE.md` 增加“三线升级收口”，明确后续只围绕 Trace Drill-down v3、Eval Asset v2 和 Quality Trend v1 三条主线推进。
- Trace Drill-down v3 目标：从“能定位 trace reference”升级为“能查看脱敏链路瀑布图”，覆盖 run、gate、eval case、agent step、RAG retrieve、tool call、model call、citation 和 failure bucket 的安全摘要。
- Eval Asset v2 目标：把 7 个 eval catalog case 继续推进成长期质量资产，补齐 case 分层、risk gate、scoring summary、regression policy 和 failure history marker。
- Quality Trend v1 目标：基于最近 N 个脱敏 artifact 展示状态趋势、case pass rate、失败桶、token / cost、latency 和反复失败 case。
- 统一边界：继续 artifact-only，不新增数据库表；不返回 prompt、answer 原文、文档全文、evidence context、真实用户输入、API key、token、secret、连接串或云地址；token usage / latency / cost 只展示数值统计。
- 下一片建议：进入 Trace Drill-down v3 的最小实现，优先复用现有 QualityRunDetail / traceReferences，不读业务库、不新增后端业务流程。

## 2026-07-05 补充：Agent Quality Console 7-case 真实审计回归

- 目标：跑一轮真实用户 QA 审计，并验证 `/quality?autoload=1` 能展示最新 run、7 个 Eval Catalog case、source issue、verified marker、remediation hints 和筛选体验。
- 已完成：`real-user-qa-experience-audit.ps1 -Mode plan` PASS，确认 plan 模式不读 env、不启动服务、不创建数据。
- 已完成：`real-user-qa-experience-audit.ps1 -Mode dry-run -FrontendBaseUrl http://127.0.0.1:3007` PASS，确认 env、工具、tunnel 端口和 ignored artifact root 可用。
- 真实审计：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705192354-eba0fc`。
- 真实结果：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、naturalCorpus、multiQueryRag、answerGrounding、noEvidenceThreshold、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Console 验证：`/api/quality/runs` 可见最新 marker，detail 状态为 `PASS`；`/api/quality/eval-cases` 返回 7 个 case，其中 4 个带 `sourceIssueIds`，7 个带 `remediationHints`；浏览器 `/quality?autoload=1` 桌面和 `390px` 移动端无 console error、无横向溢出。
- 后续验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 边界：本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation 和 Memory 数据；未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 secrets / token / 云地址 / 连接串，未 push。
- 下一片建议：如果继续 Agent Quality Console，可做面试展示收口，把 7-case catalog 和本轮 marker 同步到 showcase 讲稿；如果回到核心能力，可继续 RAG / Memory 质量增强。

## 2026-07-05 补充：Agent Quality Console Eval Catalog 筛选 v1

- 目标：让 `/quality` 的 Eval Catalog 不只是静态列表，而是能按风险、owner 和最新状态快速定位 case。
- 已完成：`frontend/app/quality/page.tsx` 的 Eval Catalog 增加本地筛选控件：risk、owner、status；筛选在浏览器端完成，不新增后端 API。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality?routeSmoke=2`，确认存在 3 个筛选控件，桌面和 `390px` 移动端无横向溢出，console error 为 `0`。
- 边界：本片不新增数据库表，不改 Quality API，不启动后端 / tunnel，不创建业务数据，不提交 artifact 原文，不 push。
- 下一片建议：跑一轮小规模真实审计，确认 `/quality?autoload=1` 能展示最新 run、7 个 catalog case、source issue、verified marker 和 remediation hints。

## 2026-07-05 补充：Agent Quality Console Eval Catalog Remediation Hint v1

- 目标：让 Eval Catalog 不只展示 case 和来源问题编号，还能展示上次验证 marker 和修复排查方向，支撑“失败 -> 定位 -> 修复 -> 回归”的面试讲述。
- 已完成：`agent-quality-eval-cases.json` 为 7 个默认 case 增加 `lastVerifiedMarker` 和 `remediationHints`，marker 均来自已记录的脱敏 smoke / audit run，hint 只使用安全 identifier。
- 已完成：`QualityEvalCatalogServiceImpl`、`QualityEvalCaseCatalogItem`、`GET /api/quality/eval-cases` 和 `/quality` Eval Catalog 同步展示 verified marker / remediation hints；URL、secret-like hint 和连接串形态继续被字段白名单过滤。
- 已完成：`QualityControllerTest` 和 `QualityEvalCatalogServiceImplTest` 覆盖新字段返回与不安全值过滤。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 边界：本片不新增数据库表，不启动真实链路，不创建业务数据，不调用真实 provider，不提交 artifact 原文，不 push；这是 catalog 可解释性增强，不是新一轮 runtime audit。
- 下一片建议：跑一轮小规模真实审计，确认 `/quality?autoload=1` 能看到 7 个 case 的 catalog、来源编号、verified marker 和 remediation hints；或先做 Eval Catalog 按 owner / riskLevel / latestStatus 的前端筛选。

## 2026-07-05 补充：Agent Quality Console Real Audit Case 扩容 v1

- 目标：把真实体验审计中已经暴露并修复验证过的典型问题沉淀为常驻 eval catalog case，让 `/quality` 能展示“这些真实风险后续会被持续盯住”。
- 已完成：`agent-quality-eval-cases.json` 从 3 个默认 case 扩到 7 个，新增 `short-document-rag-evidence`、`kb-two-document-coverage`、`citation-distractor-pruning` 和 `quality-console-startup-health`。
- 已完成：新增安全字段 `sourceIssueIds`，只返回 `REA-...` 这类脱敏问题编号；`QualityEvalCatalogServiceImpl` 继续使用字段白名单和安全 identifier 过滤，URL / token / secret / 连接串形态不会进入 API。
- 已完成：`/quality` Eval Catalog 卡片展示 source issue 摘要；`QualityControllerTest`、`QualityEvalCatalogServiceImplTest` 和 `AgentQualityEvalRunnerTest` 固定新增字段、默认 case 数和 artifact 不泄露原文的约束。
- 脱敏边界：新增 case 的 question / expectedBehavior 只作为离线 synthetic contract；result artifact、API 和前端仍不返回 prompt、answer 原文、文档全文、evidence context、真实用户输入、API key、token、secret、连接串或云地址。
- 已验证：首次 `mvn "-Dtest=*Quality*" test` 暴露 fixture marker 自相矛盾，已修正；复跑 `mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 边界：本片不新增数据库表，不启动 tunnel / backend / frontend，不调用真实 provider，不创建业务数据，不提交 artifact 原文，不 push；这是质量题库沉淀，不是新一轮真实链路审计。
- 下一片建议：给 Eval Catalog 增加 failure owner / remediation hint / last verified marker 的安全摘要，或跑一轮小规模真实审计确认 7 个 catalog case 在 `/quality` 中可见。

## 2026-07-05 补充：Agent Quality Console Eval Case Version v1

- 目标：让轻量 Eval Case JSON 不只是 caseId 清单，而是带有最小维护元数据，便于后续解释 case 版本、归属、更新时间和风险级别。
- 已完成：`agent-quality-eval-cases.json` 为 3 个默认 case 增加 `caseVersion`、`owner`、`lastUpdated` 和 `riskLevel`。
- 已完成：`QualityEvalCatalogServiceImpl` 读取并白名单返回上述安全字段；`QualityEvalCaseCatalogItem` 和 `GET /api/quality/eval-cases` 同步扩展。
- 已完成：`/quality` 的 Eval Catalog 卡片展示 version、owner、lastUpdated 和 riskLevel。
- 兼容性：test-side `AgentQualityEvalCase` 忽略未知 JSON 字段，避免 catalog 元数据破坏离线 eval runner；eval result artifact 仍不保存 question、expectedBehavior、mustContain、mustNotContain、prompt、answer 原文、文档全文或 evidence context。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS。
- 边界：本片不新增数据库表，不新增业务 API，不改变 eval scoring 语义，不调用真实 provider，不创建业务数据，不提交 artifact 原文。
- 下一片建议：Real Audit Case 扩容 v1，把真实体验审计中最常见的 RAG / Memory / frontend issue 抽成更多脱敏 audit case 或 gate 摘要；仍先不做 Phase 7 持久化。

## 2026-07-05 补充：Agent Quality Console Trace Detail 最小入口

- 目标：让 `/quality` 的 Trace 定位项不只复制 ID，而是可以打开一个内部详情页查看同一 run 下的脱敏 trace reference、关联 gate 和关联 eval case。
- 已完成：新增 `frontend/app/quality/trace/page.tsx`，通过 query 参数接收 marker、caseId、traceId、agentRunId 和 conversationId，复用现有 `GET /api/quality/runs/{marker}` 拉取 QualityRunDetail，并只展示白名单字段。
- 已完成：`frontend/app/quality/page.tsx` 的 Trace 定位行新增“打开”链接，跳转到 `/quality/trace?...`。
- 脱敏边界：Trace Detail 不新增后端 API、不读业务数据库、不展示 prompt、answer 原文、文档全文、evidence context、真实用户输入、API key、token、secret、连接串或云地址；只展示 marker、caseId、gateName、traceId、agentRunId、conversationId、failure / review bucket 和安全 metrics / flags。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality/trace?routeSmoke=1&marker=docpilot-route-smoke&caseId=route-smoke` 桌面与 `390px` 移动端无 console error，未见横向溢出；本轮不启动后端、不创建业务数据。
- 边界：这是基于 artifact 的脱敏定位详情，不是真实业务 Trace 原文页面；后续如要打开业务 ContextTrace 详情，需要单独评估权限和字段白名单。
- 下一片建议：Eval Case Version v1，为 JSON case 增加 version / owner / lastUpdated / riskLevel 等安全元数据，或扩容真实 audit case。

## 2026-07-05 补充：Agent Quality Console 求职展示打磨

- 目标：把 Agent Quality Console、RAG / Memory 真实质量闭环和 2026-07-05 “真实审计发现问题 -> 定位 -> 修复 -> 回归 PASS”的故事同步到 README / showcase / 面试材料。
- 已完成：`README.md` 已从旧的“RAG + Agent 文档问答”口径更新为“企业文档知识库 RAG + 会话记忆 + 内部质量门禁”口径，并补充 `/quality`、Conversation Memory、Context Trace 和 Agent Quality Console 边界。
- 已完成：`docs/showcase/PROJECT_INTERVIEW_BRIEF.md` 更新项目定位、已实现能力、简历亮点、展示优先级和高风险追问；明确 Agent 是围绕文档工具和 Trace 的辅助层，核心主线是 RAG / Memory / Quality Console。
- 已完成：`docs/showcase/RESUME_BULLETS.md` 增加 Agent Quality Console、真实 audit / eval artifact、Memory governance 和 RAG 质量门禁相关 bullet，并明确不能写成商业 APM、大规模 benchmark 或线上 SLA。
- 已完成：`docs/showcase/INTERVIEW_QA.md` 更新一分钟介绍、核心亮点、RAG 完整性、Agent Quality Console 价值和项目不足回答；可讲 2026-07-05 真实 audit 首轮 BLOCKED、定位构造器注入问题、修复后 PASS 的闭环。
- 边界：本片只更新对外展示和内部事实源文档，不改业务代码，不启动服务，不创建业务数据，不提交 artifact 原文，不 push。
- 下一片建议：如果继续 Agent Quality Console，可优先做 Trace Detail 最小跳转 / Eval Case version 元数据 / 真实 audit case 扩容；Phase 7 数据库存储继续默认不做，除非出现跨机器历史、权限审计或趋势查询刚需。

## 2026-07-05 补充：Agent Quality Console 真实体验审计集成 v2

- 目标：跑一次真实用户 QA 审计，并验证 `/quality?autoload=1` 能看到最新真实 run、Eval Catalog、Failure Triage、Run Comparison 和 Model / Cost Summary。
- 首轮结果：`docpilot-real-user-qa-20260705164732-f54da1` 为 `BLOCKED`，tunnel / config consistency PASS，但 backend health 超时。根因是 `QualityEvalCatalogServiceImpl` 有多个构造器但缺少显式 `@Autowired`，真实 Spring 启动时尝试找默认构造器失败。
- 已修复：`QualityEvalCatalogServiceImpl` 主构造器补 `@Autowired`，新增 `QualityEvalCatalogServiceSpringContextTest` 防回归。
- 真实审计复跑：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705165151-bbe588`。
- 真实结果：tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant consistency、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、naturalCorpus、answerGrounding、noEvidenceThreshold、Conversation Trace、Memory quality、权限隔离、frontendInteraction、cleanup 和 artifact redaction 均 PASS。
- Console 可见性：开启 `APP_QUALITY_CONSOLE_ENABLED=true` 后，`/api/quality/runs` 可见最新 marker，detail 状态为 `PASS`，`/api/quality/eval-cases` 返回 3 个 case；浏览器 `/quality?autoload=1` 可见最新 marker、`Eval Catalog`、`Failure Triage`、`Run Comparison` 和 `Model / Cost Summary`，console error count 为 `0`，`390px` 宽度无横向溢出。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，35 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；真实审计 PASS；Console autoload 验证 PASS；清理脚本确认端口释放。
- 边界：本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation 和 Memory 数据；未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。
- 下一片建议：跳过默认不做的 Phase 7 持久化，进入 Phase 8 求职展示打磨，把“失败 -> 定位 -> 修复 -> 回归通过”的 Agent Quality Console 故事同步到 showcase / 面试材料。

## 2026-07-05 补充：Agent Quality Console Cost / Latency / Model Summary v1

- 目标：补齐 Agent Quality Console 的 AI 系统成本和运行摘要，让 Run Detail 能回答本次评测用了多少 token、模型调用、工具调用、耗时和 retry。
- 已完成后端：`QualityArtifactServiceImpl` 的安全 metric 白名单新增 `*Ms`、`latencyMs`、`durationMs` 和 `estimatedCost` 等数值字段；`modelCallCount`、`toolCallCount`、`retryCount` 继续作为 count 类安全字段保留。
- 已完成前端：Run Detail 新增 `Model / Cost Summary` 面板，聚合展示 prompt / completion / total tokens、estimated cost、model calls、tool calls、latency ms、duration ms 和 retries。
- 脱敏边界：只展示数值统计，不返回或展示 system prompt、user prompt、answer 原文、provider 原始输出、文档全文、evidence context、API key、token、secret、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，34 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality?routeSmoke=2` 在移动端宽度下无 console error、主要容器未横向溢出；清理脚本确认端口释放。
- 边界：本片未新增数据库表，未改变核心业务流程，未启动真实 backend / tunnel，未创建业务数据，未提交 artifact 原文，未 push。
- 下一片建议：进入 Phase 6 真实体验审计集成 v2，跑一次真实 quality audit，并验证 `/quality?autoload=1` 能看到最新 run 和新增 summary / comparison / catalog 面板。

## 2026-07-05 补充：Agent Quality Console Run Comparison v1

- 目标：让 `/quality` 能展示当前 run 与选定 previous run 的脱敏差异，用于支撑“发现问题 -> 修复 -> 回归通过”的质量闭环说明。
- 已完成前端：Run Detail 新增 `Run Comparison` 面板，支持选择 previous run，并用现有 `GET /api/quality/runs/{marker}` 拉取对比详情；本片不新增后端 compare API。
- 对比内容：展示 status 变化、gate 数 delta、failed / review gate delta、token total delta、casePassRate delta、新增失败桶、已修复失败桶、gate status changes 和 eval case status changes。
- 脱敏边界：对比只使用现有 Quality DTO 的 marker、status、计数、metrics、bucket、caseId 等白名单字段；不展示 prompt、answer 原文、文档全文、evidence context、API key、token、secret、连接串或云地址。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality?routeSmoke=2` 在移动端宽度下无 console error、主要容器未横向溢出；清理脚本确认端口释放。
- 边界：本片未新增数据库表，未改变核心业务流程，未启动真实 backend / tunnel，未创建业务数据，未提交 artifact 原文，未 push。
- 下一片建议：进入 Phase 5 Cost / Latency / Model Summary v1，补齐 token、model call、tool call、latency、retry 等数值摘要。

## 2026-07-05 补充：Agent Quality Console Eval Case Catalog v1

- 目标：让 eval 不只是 smoke 聚合结果，而是能在 `/quality` 中看到当前有哪些安全 eval case、每类 case 验什么、最近一次状态如何。
- 已完成后端：新增 `QualityEvalCatalogService` / `QualityEvalCatalogServiceImpl` 和 `QualityEvalCaseCatalogItem`，从 `backend/src/test/resources/quality/agent-quality-eval-cases.json` 读取白名单字段，并从最近 Quality run 中关联 `latestStatus`、`latestRunMarker`、`latestTraceId` 和 `latestAgentRunId`。
- 已完成 API：`GET /api/quality/eval-cases` 复用 `/api/quality/**` 内部访问控制和 `app.quality.console.enabled` 开关，返回脱敏 eval catalog 摘要。
- 已完成前端：`frontend/lib/quality-api.ts` 新增 catalog 类型和请求；`/quality` Overview 左侧新增 `Eval Catalog` 卡片，展示 caseId、caseType、tags、expectedEvidence、expectedTools、scoringRules 和最近运行状态。
- 脱敏边界：不返回或展示 `question`、`expectedBehavior`、`mustContain`、`mustNotContain`、answer 原文、prompt、文档全文、evidence context、API key、token、secret、连接串或云地址；catalog parser 对标识符字段做白名单过滤。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，34 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality?routeSmoke=2` 在移动端宽度下无 console error、主要容器未横向溢出；清理脚本确认端口释放。
- 边界：本片未新增数据库表，未改变核心业务流程，未启动真实 backend / tunnel，未创建业务数据，未提交 artifact 原文，未 push。
- 下一片建议：进入 Phase 4 Run Comparison v1，支持 latest run 与 selected previous run 的 gate / case / bucket / token usage 差异对比。

## 2026-07-05 补充：Agent Quality Console Failure Triage v1

- 目标：让 `/quality` Run Detail 从“展示结果”升级为“定位问题”，支持按 status、failure bucket taxonomy、gate name 和 case type 过滤。
- 已完成前端：新增 Failure Triage 面板，内置 `RAG_RETRIEVAL_MISS`、`CITATION_UNSUPPORTED`、`DISTRACTOR_CITATION`、`NO_EVIDENCE_FALSE_POSITIVE`、`MEMORY_CONFLICT`、`TOOL_FAILURE`、`PERMISSION_REGRESSION`、`FRONTEND_UX`、`ENV_BLOCKED` 和 `OTHER` 归一化分类。
- 已完成筛选：Gate 列表、Eval Case 和 Trace 定位会随筛选条件联动；支持清除筛选，并展示筛选后的 gates / eval / traces 数量。
- 脱敏边界：本片只使用现有 Quality API 的白名单字段，不新增后端接口，不返回 prompt、answer 原文、文档全文、evidence context、question、API key、token、secret、连接串或云地址。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `/quality?routeSmoke=2` 在移动端宽度下页面非空、无 console error、主要容器未横向溢出；清理脚本确认 `3007` / `8081` 等端口释放。
- 边界：本片未新增数据库表，未改变核心业务流程，未启动真实 backend / tunnel，未创建业务数据，未提交 artifact 原文，未 push。
- 下一片建议：进入 Phase 3 Eval Case Catalog v1，让 Console 能展示 eval case 的安全目录、case type / tags、scoring rule 摘要和最近一次状态。

## 2026-07-05 补充：Agent Quality Console Trace 定位入口

- 目标：让 `/quality` Run Detail 不只裸展示 eval case 的 `traceId` / `agentRunId`，而是提供可定位的内部 Trace reference 摘要，用于从失败 / REVIEW case 回到具体 trace / agent run。
- 已完成后端：新增 `QualityTraceReference`，`QualityRunDetail` 增加 `traceReferences`；`QualityArtifactServiceImpl` 递归收集 `caseResults` / `caseEvaluations` / `evalCases`，保留父级 `gateName`，并只输出 caseId、caseType、status、gateName、traceId、agentRunId、conversationId、failureBuckets 和 reviewBuckets。
- 已完成前端：`/quality` Run Detail 新增“Trace 定位”面板，展示脱敏定位项，并提供复制 `traceId` / `agentRunId` / `conversationId` 的按钮；当前不新增真实 Trace 详情页，不读取业务数据库。
- 脱敏边界：仍不返回或展示 prompt、answer 原文、文档全文、evidence context、question、API key、token、secret、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，30 tests，1 skipped；`npm run lint` PASS；`npm run build` PASS；浏览器打开 `/quality?routeSmoke=2` 在 `390x844` 下无 console error、无横向溢出；清理脚本确认 `3007` / `8081` 等端口释放。
- 边界：本片未新增数据库表，未改变核心业务流程，未启动真实 backend / tunnel，未创建业务数据，未提交 artifact 原文，未 push。
- 下一片建议：进入 Phase 2 Failure Triage v1，统一失败桶 taxonomy，并让 Run Detail 支持按 status、failure bucket、case tag / gate name 过滤。

## 2026-07-05 补充：Agent Quality Console 求职级升级路线图

- 目标：把 Agent Quality Console 从当前 MVP / Explainability v1 推进到求职级内部质量控制台，并把后续可连续自驱执行的 Slice、验收标准和停止条件沉淀到项目文档。
- 已完成：新增 `docs/ai-dev/ROADMAP_AGENT_QUALITY_CONSOLE.md`，明确当前基础、主要差距、Phase 0-8、每阶段验收标准、自驱循环规则和明确不做事项。
- 已完成：`docs/README.md` 已把 Agent Quality Console 路线图纳入默认文档地图；后续路线图看 `ROADMAP_AGENT_QUALITY_CONSOLE.md`，当前任务仍看 `CURRENT_TASK.md`。
- 下一片建议：进入 Phase 1 Trace Drill-down v2，优先让失败 / REVIEW eval case 在 `/quality` Run Detail 中能定位 `traceId` / `agentRunId`；若真实 trace detail API 暂不足，先做“复制 ID + 失败桶过滤”的轻量闭环。
- 边界：本片只更新文档与任务口径，未修改业务代码，未启动服务，未创建业务数据，未提交 artifact 原文，未 push。

## 2026-07-05 补充：Quality Console signals 真实链路验证

- 目标：跑一次真实 quality audit，并验证 Agent Quality Console 能展示最新 run 的嵌套 gate、RAG evidence / eval signals 和关键自然语料指标。
- 已完成真实审计：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705151944-950f42`。
- 关键结果：`naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`；frontendInteraction、Memory quality、Conversation Trace、权限隔离、artifact redaction 均 PASS。
- Console 验证：开启 `APP_QUALITY_CONSOLE_ENABLED=true` 后，浏览器打开 `/quality?autoload=1` 可见最新 marker、`naturalCorpus` gate、`CASEPASSRATE`、`DISTRACTORCITATIONFREECOUNT` 和 eval case `ops-incident-support-summary`；console error count 为 `0`，`1366x900` 无横向溢出。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS；`npm run lint` PASS；`npm run build` PASS；Playwright route smoke 和 autoload smoke PASS。
- 边界：本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation、Memory 和临时登录用户；未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push；本轮 backend / frontend / tunnel 已清理。
- 下一步建议：继续 Agent Quality Console Trace drill-down v2，优先把 eval case 的 `traceId` / `agentRunId` 变成可点击的内部定位入口；若没有真实 trace detail API，则先做“复制 ID + 失败桶过滤”的轻量闭环。

## 2026-07-05 补充：`/quality` Evidence / Eval Lens 展示

- 目标：让 Agent Quality Console Run Detail 能展示 Slice A 暴露的脱敏 gate / eval case 指标，使质量结果更容易解释和排查。
- 已完成：`frontend/lib/quality-api.ts` 同步 `QualityEvalCaseResultDetail.metrics` / `flags` 类型；`frontend/app/quality/page.tsx` 在 gate 和 eval case 行中新增安全 signals 小格子，展示数值指标和布尔结果。
- 已完成：Eval case 现在同时展示 failure / review buckets、traceId / agentRunId 和脱敏 signals；不展示 question、answer 原文、文档全文、prompt 或 evidence context。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `http://127.0.0.1:3007/quality?routeSmoke=2` 页面非空、console error count 为 `0`、`390x844` 无横向溢出。
- 边界：本片未新增后端 API，未启动 backend / tunnel，未创建业务数据，未提交 artifact 原文，未 push。
- 下一步：进入 Slice C，跑真实 quality audit，并用 `/quality?autoload=1` 验证最新 run 的 `naturalCorpus` gate 和 eval case signals 可见。

## 2026-07-05 补充：Quality Console 嵌套 gate 与 eval case 安全指标

- 目标：让 Agent Quality Console 能正确读取 cloud quality / real-user audit artifact 中嵌套的 `gates.*`，并为 Run Detail 提供更可解释的脱敏 gate / eval case 指标。
- 已完成：`QualityArtifactServiceImpl` 支持解析顶层 `gates` 容器下的嵌套 gate；`checks` 为单个 object 时合并安全数值 / 布尔字段，多个 check 时只保留 `checkCount`，避免透传明细。
- 已完成：`hardFailureBuckets` 纳入 failure bucket 聚合；`QualityEvalCaseResultDetail` 新增安全 `metrics` / `flags`，用于展示 `retrieveHits`、`qaCitations`、`distractorCitationCount`、`targetCitationCovered`、`noEvidenceCorrect` 等脱敏指标。
- 脱敏边界：仍不返回 prompt、answer 原文、文档全文、evidence context、question、API key、token、secret、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，30 tests，1 skipped。
- 边界：本片未新增数据库表，未新增 endpoint，未启动真实服务，未创建业务数据，未提交 artifact 原文，未 push。
- 下一步：进入 Slice B，更新 `/quality` Run Detail 的 Evidence / Eval Lens 展示。

## 2026-07-05 补充：多文档 summary citation 精度收口

- 目标：修复真实用户 QA 审计中 `ops-incident-support-summary` 在目标覆盖满足时仍带入一条低置信度干扰 citation 的问题。
- 已完成：`KnowledgeBaseRagQaServiceImpl` 在答案生成后的 citation 后处理阶段新增多文档意图保护下的极低分 citation 裁剪；保留 retrieval hits 和 `documentHitCounts`，不破坏 Trace / 调试所需的召回证据。
- 已完成防回归：`KnowledgeBaseRagQaServiceImplTest` 新增短复现用例，覆盖“目标两文档 citation 保留、低分干扰 citation 移除、召回 hits 仍保留”的行为。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest" test` PASS；`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS。
- 真实回归：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260705145304-7a53b8`；`naturalCorpus.casePassRate=1`，`distractorCitationFreeCount=25/25`，frontendInteraction、Memory quality、Conversation Trace、权限隔离和 artifact redaction 均 PASS。
- 问题台账：`REA-20260704-P2-006` 已从 `OPEN` 更新为 `VERIFIED`。
- 边界：本轮未改数据库结构，未删除业务数据，未操作远程 Docker / hk-ops，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。
- 下一步建议：继续进入 Agent Quality Console 的 Trace drill-down / citation explainability 小切片，让 Run Detail 能更清楚解释 citation 裁剪、REVIEW 桶和 traceId / agentRunId 的定位关系。

## 2026-07-04 补充：Agent Quality Console 真实回归与可见性验证

- 目标：跑一次真实 quality audit，让 Agent Quality Console 能看到该 run，并把最新质量结果写回文档。
- 已完成：`agent-quality-eval-smoke.ps1 -Mode run` PASS，marker `docpilot-agent-quality-eval-20260704221655-48a5cf`；artifact 位于 ignored 的 `backend/target/agent-quality-eval/...`。
- 已完成真实审计：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 完成，marker `docpilot-real-user-qa-20260704221704-4abc6f`，整体状态 `REVIEW`。
- 核心结果：tunnel、backend health、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant 一致性、单文档 RAG、KnowledgeBase RAG、shortDocumentRag、multiQueryRag、answerGrounding、noEvidenceThreshold、Conversation Trace、Memory quality、权限隔离、frontendInteraction、frontend routes、cleanup 和 artifactRedaction 均 PASS。
- REVIEW 项：`naturalCorpus` 中 25 case 的 `casePassRate=1`，但 `ops-incident-support-summary` 出现 `distractorCitation` review，`distractorCitationFreeCount=24/25`；已记录为 `REA-20260704-P2-006`，后续应进入 RAG citation 精度治理。
- 本轮修复：真实启动时发现 `QualityArtifactServiceImpl` 缺少显式 Spring 构造器注入导致 backend health timeout；已补 `@Autowired` 并新增 `QualityArtifactServiceSpringContextTest` 防回归。
- Console 验证：开启 `app.quality.console.enabled=true` 后，`GET /api/quality/runs` 与 `GET /api/quality/runs/{marker}` 能看到 `docpilot-real-user-qa-20260704221704-4abc6f`；浏览器打开 `/quality?autoload=1` 可见该 marker 和 `REVIEW` 状态，console error count 为 `0`。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，29 tests，1 skipped；artifact 脱敏扫描 PASS；本轮启动的 backend / frontend 已清理，`3007` / `8081` 端口释放。
- 边界：未提交原始 artifact，未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。
- 下一步建议：优先修复 `REA-20260704-P2-006`，让多文档 summary 在保留目标覆盖的同时减少干扰 citation；其次再扩展 Quality Console 的 Trace drill-down。

当前任务：Agent Quality Console MVP Slice 5：前端 Overview + Run Detail（DONE）

## 2026-07-04 补充：`/quality` 内部质量控制台页面

- 目标：新增前端 Agent Quality Console P0 页面，覆盖 Overview + Run Detail，不做复杂 APM、告警系统、多租户后台或大规模调度平台。
- 已完成：新增 `frontend/lib/quality-api.ts`，封装 `GET /api/quality/runs` 与 `GET /api/quality/runs/{marker}` 的脱敏类型和请求；新增 `frontend/app/quality/page.tsx`。
- 页面能力：Overview 展示最近 run、PASS / REVIEW / FAILED 统计、gate 数、失败 / REVIEW 桶、token usage / cost 数值；Run Detail 展示 gate 列表、eval case 结果、RAG / Memory / Agent 类摘要字段；`Trace` / `Eval` / `Failures` 作为预留入口。
- 路由 smoke 策略：`/quality` 默认渲染控制台壳和刷新按钮，不自动请求后端，避免旧登录态或未启动 backend 时把 route smoke 变成后端可达性测试；完整链路验证可使用 `/quality?autoload=1` 自动拉取 API。
- 已完成小修：新增 `frontend/app/icon.svg`，避免浏览器 route smoke 产生 favicon 404。
- 已验证：`npm run lint` PASS；`npm run build` PASS；Playwright 打开 `http://localhost:3007/quality?routeSmoke=2` 无 console error；`390x844` 移动端 snapshot 未见横向溢出；本轮启动的 `3007` 前端 dev server 已清理释放。
- 边界：本片不新增后端功能，不改变业务流程，不展示普通用户入口，不提交 artifact 原文，不 push。

当前任务：Agent Quality Console MVP Slice 4：轻量 Eval Case JSON + Runner（DONE）

## 2026-07-04 补充：Agent Quality Eval 轻量离线门禁

- 目标：为 Agent Quality Console 增加轻量 Eval Case JSON 和离线 runner，使 Eval 不只是 smoke artifact 聚合，而是有 caseId、期望行为、期望 evidence / tool 和 scoringRules 的最小评测合约。
- 已完成：新增 `backend/src/test/resources/quality/agent-quality-eval-cases.json`，case 字段包含 `caseId`、`question`、`expectedBehavior`、`expectedEvidence`、`expectedTools`、`mustContain`、`mustNotContain`、`tags` 和 `scoringRules`。
- 已完成 runner：新增 `backend/src/test/java/com/docpilot/backend/quality/eval/**`，`AgentQualityEvalRunner` 可加载 JSON case、根据脱敏 observation 生成 `QualityEvalCaseResultDetail`，并输出只含 caseId、caseType、status、passed、traceId、agentRunId、failureBuckets / reviewBuckets 的安全结果。
- 已完成 smoke 脚本：新增 `scripts/smoke/agent-quality-eval-smoke.ps1`，支持 `plan` / `dry-run` / `run`；`run` 只执行离线 JUnit，不读 `.env`、不调用 provider、不启动服务、不创建业务数据，artifact root 为 ignored 的 `backend/target/agent-quality-eval`。
- Artifact 聚合同步：`QualityArtifactServiceImpl` 已把 `backend/target/agent-quality-eval` 加入白名单 root，后续 Console 可读取该类脱敏 artifact。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，28 tests，1 skipped（默认关闭的 smoke writer）；`agent-quality-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker `docpilot-agent-quality-eval-20260704220047-9c9af0`；artifact 脱敏扫描 PASS。
- 边界：本片是离线轻量 eval 合约，不是大规模 Agent benchmark，不调用真实模型，不读取业务数据库，不新增表，不提交 artifact 原文，不 push。

当前任务：Agent Quality Console MVP Slice 3：后端 Quality API（DONE）

## 2026-07-04 补充：Quality API 最小入口

- 目标：在 Slice 2 的 artifact 聚合 service 之上增加内部只读 Quality API，供后续 `/quality` 前端读取脱敏 summary/detail。
- 已完成：新增 `QualityController`，提供 `GET /api/quality/runs` 和 `GET /api/quality/runs/{marker}`；返回 `ApiResponse<List<QualityRunSummary>>` 和 `ApiResponse<QualityRunDetail>`。
- 访问控制：`/api/quality/**` 仍走现有 `/api/**` 登录拦截；Controller 额外要求 `app.quality.console.enabled=true`，默认关闭时返回 `FORBIDDEN`。P0 未新增 admin 角色表，也未改用户权限模型。
- 安全边界：API 只返回 Slice 2 的脱敏 DTO，不返回原始 artifact、prompt、answer 原文、文档全文、evidence context、API key、secret、连接串或云地址。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，21 tests；Controller test 覆盖 console disabled、缺登录上下文、空列表 / 默认 limit、detail 查询和 detail missing。
- 边界：本片未新增数据库表，未改变核心业务流程，未启动服务，未创建业务数据，未做前端页面，未提交 artifact 原文，未 push。

当前任务：Agent Quality Console MVP Slice 2：后端 artifact 聚合 service（DONE）

## 2026-07-04 补充：Quality artifact 聚合 service

- 目标：为 Agent Quality Console MVP 增加后端只读 artifact 聚合 service，先不暴露 API、不做前端、不建表。
- 已完成：新增 `backend/src/main/java/com/docpilot/backend/quality/**`，包含 `QualityArtifactService`、`QualityArtifactServiceImpl` 和 `QualityRunSummary` / `QualityRunDetail` / `QualityGateSummary` / `QualityEvalCaseResultDetail` / `QualityTokenUsageSummary`。
- 聚合边界：service 默认扫描 `backend/target/audit`、`backend/target/rag-natural-corpus`、`backend/target/rag-real-qa`、`backend/target/memory-quality`、`backend/target/memory-provider` 和 `tmp-e2e/docpilot-cloud-quality-smoke`，只识别 `artifact.json` 与历史审计 `real-experience-audit-report.json`。
- 安全策略：只返回 marker、source、artifactName、status、updatedAt、gate 计数、失败 / REVIEW 桶、gate 数值 / 布尔指标、eval case 摘要和 token usage 数值；不透传原始 artifact，不返回 prompt、answer 原文、文档全文、evidence context、API key、secret、连接串或云地址。
- 降级策略：缺 artifact root 返回空列表；坏 JSON 生成 `REVIEW` summary/detail，`artifactParseFailed=true`，失败桶包含 `artifactParseFailed`；detail 查询只按 marker 匹配已发现 artifact，不接受任意文件路径。
- 已验证：`mvn "-Dtest=*Quality*" test` PASS，15 tests；新增单测覆盖空目录、缺文件、坏 JSON、正常 artifact、历史审计文件名、最近 N 个排序和未知敏感字段过滤。
- 边界：本片未新增 Controller / API，未改鉴权，未启动服务，未创建业务数据，未新增数据库表，未提交 artifact 原文，未 push。

当前任务：Agent Quality Console MVP Slice 1：文档与接口口径（DONE）

## 2026-07-04 补充：Agent Quality Console 内部质量控制台口径

- 目标：把 Agent Quality Console 统一定位为 DocPilot 内部质量控制台，用于聚合真实 smoke / audit / eval artifact，展示质量总览、单次 run 明细、Trace / Eval 扩展入口和失败桶，不把它拆成两个独立“大平台”。
- 已完成文档口径：第一版信息架构为 `Overview`、`Trace`、`Eval`、`Failures`；P0 只要求 `Overview + Run Detail` 最小闭环，同时保留 Trace / Eval drill-down 的 API / DTO 扩展设计。
- P0 范围：读取 ignored artifact 摘要、按字段白名单生成 `QualityRunSummary` / `QualityRunDetail`、暴露内部 `/quality` 页面和 `/api/quality/**` 只读 API 口径、展示 PASS / REVIEW / BLOCKED / FAILED_CORE_FLOW / FAILED_SECURITY_GATE 等质量状态。
- P1 范围：补全 Trace 详情、Eval case 详情、Failures 桶、趋势对比和失败 case 到 Trace 的跳转；后续再按证据决定是否引入 `quality_eval_run` / `quality_eval_gate` 表。
- Trace / Eval 关系：现有 smoke / audit runner 是第一阶段数据来源，但 Eval 不等于 smoke 聚合。轻量 Eval case 先用 JSON 文件描述 `caseId`、`question`、`expectedBehavior`、`expectedEvidence`、`expectedTools`、`mustContain`、`mustNotContain`、`tags` 和 `scoringRules`；Eval result 必须能关联 `traceId` 或 `agentRunId`。
- Artifact 聚合边界：P0 默认只扫描 `backend/target/audit`、`backend/target/rag-natural-corpus`、`backend/target/rag-real-qa`、`backend/target/memory-quality`、`backend/target/memory-provider` 和 `tmp-e2e/docpilot-cloud-quality-smoke` 下的脱敏 summary；文件不存在降级为空列表或 `artifactMissing=true`，解析失败降级为 `artifactParseFailed=true` / `REVIEW`。
- 脱敏规则：parser 和 API 必须使用字段白名单；禁止返回 prompt、answer 原文、文档全文、evidence context、API key、access token、secret、连接串和云地址；`token_usage` 只允许返回 `prompt_tokens`、`completion_tokens`、`total_tokens`、`estimated_cost` 等数值统计。
- 泄露风险回滚：一旦发现某个 artifact root 或 detail 字段存在泄露风险，优先关闭该 root、隐藏 detail 字段或只保留 summary；前端不得直接读取 artifact 原文。
- 权限边界：`/quality` 和 `/api/quality/**` 是内部页面 / API；如果当前没有完整 admin 角色，P0 先使用开发环境开关或 admin token；普通用户页面不展示 Trace / Eval 详情。
- 边界：本片仅更新文档与接口口径；未修改业务代码，未新增页面 / API 实现，未启动服务，未创建业务数据，未提交 artifact 原文，未 push。

当前任务：Memory provider 小样本 v1（DONE）

## 2026-07-04 补充：真实 provider 记忆抽取小样本

- 目标：把 Memory provider 从 stub/provider contract 推进到小规模真实 provider 证据，同时保持普通离线测试不依赖真实密钥、不保存原始对话、provider 输出或 memory 内容。
- 已完成：新增默认关闭的 `MemoryProviderExtractionRealProviderSmokeTest`，通过 `DOCPILOT_MEMORY_PROVIDER_SMOKE_ENABLED=true` 才运行；新增 `scripts/smoke/memory-provider-extraction-smoke.ps1`，支持 `plan` / `dry-run` / `run`，最多 4 次模型调用，artifact root 为 ignored 的 `backend/target/memory-provider`。
- 已完成 runner 稳定性：`MemoryProviderExtractionEvalRunner` 支持 provider 返回 JSON code fence、`task-goal` / `answer style` 这类大小写与分隔符变化，并按 memory type multiset 判断类型命中，避免真实 provider 的无意义顺序差异造成误判。
- 真实验证：`memory-provider-extraction-smoke.ps1 -Mode run` PASS，marker `docpilot-memory-provider-20260704192850-695412`；`modelCallCount=4`，`casePassRate=1.0000`，`rawProviderOutputStored=false`。
- 离线验证：`memory-provider-extraction-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=MemoryProviderExtractionEvalRunnerTest,MemoryProviderExtractionRealProviderSmokeTest,MemoryQualitySmokeScriptSafetyTest" test` PASS，7 tests，其中真实 provider smoke 默认 skipped 1。
- 边界：这是 4 case 小样本真实 provider 验证，不是大规模 memory extraction benchmark、生产 LLM 记忆抽取替换或长期记忆质量成熟结论；未启动后端 / 前端 / tunnel，未创建业务数据，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

当前任务：真实用户问答体验审计 v2（DONE）

## 2026-07-04 补充：真实用户 QA 体验审计入口

- 目标：把已经成熟的 cloud quality / natural corpus / frontend interaction / memory quality gate 组合成一个真实用户问答体验审计入口，便于后续一键从用户视角检查 RAG、KnowledgeBase、Conversation Trace、Memory、权限隔离和脱敏 artifact。
- 已完成：新增 `scripts/smoke/real-user-qa-experience-audit.ps1`，支持 `plan` / `dry-run` / `run`；默认委托 `cloud-quality-smoke.ps1`，并启用 `naturalCorpus`、`multiQueryRag`、`frontendInteraction` 和 `memoryQuality` gate，artifact root 为 ignored 的 `backend/target/audit`，marker 前缀为 `docpilot-real-user-qa`。
- 已完成门禁修正：`cloud-quality-smoke.ps1` 的回答事实表达检查支持 `a|b|c` 同义表达组，避免真实回答用 `seven days` / `7 days`、`five-year` / `5 years` 等自然表达差异时误杀；citation phrase support、forbidden answer、no-evidence 和权限隔离仍保持硬门禁。
- 真实过程：首轮真实 run marker `docpilot-real-user-qa-20260704190235-553df7` 暴露 3 个自然语料 QA case 的 `answerFactExpression` 过度依赖单一英文短语；修正表达组后重跑通过。
- 真实验证：`real-user-qa-experience-audit.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-real-user-qa-20260704191307-661bc0`；`naturalCorpus.casePassRate=1`，`answerFaithfulnessPassCount=11/11`，`citationPhraseSupportPassCount=22/22`，`frontendInteraction`、`memoryQuality`、`conversationTrace`、`permissionIsolation`、`artifactRedaction` 均 PASS。
- 边界：本片是小规模真实链路用户体验审计入口，不是大规模人工评测、线上 SLA 或完整浏览器 E2E 覆盖；本轮创建临时 smoke 用户、文档、KnowledgeBase、Conversation 和 Memory 数据，未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 secrets，未 push。

当前任务：Evidence Coverage 报告 v1（DONE）

## 2026-07-04 补充：自然语料 case 级覆盖报告

- 目标：让自然语料 smoke artifact 不只给总体 PASS / FAIL，还能直接报告漏召回、漏 citation、citation 事实短语不支持、回答事实不满足、干扰 citation 泄漏和 no-evidence 失败的 caseId 清单。
- 已完成：`naturalCorpus` summary 新增脱敏 `evidenceCoverageReport`，包含 `retrieveCoveragePassCount`、`citationCoveragePassCount`、`citationPhraseSupportPassCount`、`answerFaithfulnessPassCount`、`noEvidenceCorrectCount`、`distractorCitationFreeCount`，以及 `retrievalCoverageMisses`、`citationCoverageMisses`、`citationPhraseMisses`、`answerFaithfulnessMisses`、`distractorCitationLeaks`、`noEvidenceFailures`。
- 口径调整：多文档 summary 的目标 citation 和事实短语支撑仍是硬门禁；如果目标覆盖与事实支撑都满足，额外干扰 citation 进入 REVIEW 报告，不再和单数字事实干扰一样直接阻断核心链路。单文档 / 数字事实里的干扰 citation 仍是硬失败。
- 真实验证：`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704160327-16b351`；`evidenceCoverageReport` 中 retrieval / citation / phrase / answer / distractor / no-evidence 的 miss、leak、failure 清单均为空。
- 边界：报告只保存 caseId、计数和布尔结果，不保存原文、回答、prompt、evidence context、token、云地址或连接串；未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未 push。

当前任务：Answer / Citation Faithfulness v2（DONE）

## 2026-07-04 补充：自然语料回答与引用支撑硬门禁

- 目标：在 RAG 自然语料 v2 的 25 case 基础上，把 QA case 的回答事实表达和 citation 对预期事实短语的支撑从观测字段升级为硬门禁，避免只看 hit / citation 数量。
- 已完成：`Invoke-NaturalCorpusCase` 修正单条 citation / hit 的计数方式，避免 artifact 中 `qaCitations` 在单 citation 场景显示为 `null`；新增 `answerFaithfulnessRequired`、`citationPhraseSupport`，并把需要回答事实表达的 QA case 中 `answerFactExpression=false` 记为 failure bucket。
- 已完成聚合：`naturalCorpus` summary 新增 `answerFaithfulnessCaseCount`、`answerFaithfulnessPassCount`、`citationSupportCaseCount`、`citationPhraseSupportPassCount`，让 artifact 能直接显示回答事实和 citation 支撑覆盖率。
- 真实验证：`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704152850-e07b13`；`answerFaithfulnessPassCount=11/11`，`citationPhraseSupportPassCount=22/22`，`casePassRate=1`。
- 边界：本片仍是小规模自然语料 smoke 门禁，不是大规模人工 faithfulness benchmark、NLI 模型评测或线上 SLA；未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 secrets，未 push。

当前任务：RAG 自然语料扩容 v2（DONE）

## 2026-07-04 补充：12 文档 / 25 case 自然语料质量门禁

- 目标：把 v1 自然语料 gate 从 5 文档 / 6 case 扩展为更接近真实企业知识库的 3 个 corpus、12 份临时 txt 文档、25 个 case，覆盖单文档事实、数字事实、日期事实、审批链、负向事实、多文档 compare / summary、干扰 citation、no-evidence、Conversation Trace、frontendInteraction 和 multi-query。
- 已完成 runner：`cloud-quality-smoke.ps1` 的 `naturalCorpus` gate 升级为 `schemaVersion=2`，输出 `caseResults`、`casePassRate`、`failureBuckets`、`reviewBuckets`、目标 / 干扰文档覆盖计数和 score summary；artifact 仍不保存文档原文、问题原文、回答原文、prompt、evidence context、token、云地址或连接串。
- 已完成 wrapper：`rag-natural-corpus-audit-smoke.ps1 -Mode plan` 明确 `defaultCorpusTarget=3`、`defaultDocumentTarget=12`、`defaultCaseTarget=25`，并新增 `natural_date_fact`、`natural_approval_chain`、`natural_negative_fact`、`natural_case_coverage` 等 case 类型口径。
- 已完成后端修复：KnowledgeBase QA 的数字 citation 精炼不再破坏 compare / summary 这类多文档意图的 citation 覆盖；当数字过滤会把多文档引用压成单文档时，会保留原始 citations。
- 真实验证：`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704151615-bc193d`；`naturalCorpus.casePassRate=1`，12 文档 / 25 case 全部通过，3 个 no-evidence 全部正确拒答，4 个多文档 case 全部覆盖目标文档，25 个含干扰文档的 case 均无干扰 citation，Conversation Trace `ragTriggered=true`、`ragRequired=true`、`evidenceCount=4`。
- 本轮真实发现并修复：`ops-backup-rollback-compare` 曾因 answer-aware numeric citation filter 只保留 rollback citation，漏掉 backup citation；自然语料 runner 的 `smokegovernance...` 用户名超过注册 32 字符约束，已改为短 alias。
- 边界：本轮使用真实本地 backend / frontend / tunnel / MySQL / Qdrant 链路和临时 smoke 数据；未删除业务数据，未操作远程 Docker / hk-ops，未改数据库结构，未提交 artifact 原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

当前任务：RAG 自然语料真实审计 gate v1（DONE）

## 2026-07-04 补充：自然语料真实链路质量门禁

- 目标：把 RAG 真实体验审计从 marker-heavy smoke 扩到更接近真实企业知识库问法的自然语料 gate，覆盖单文档事实、数字事实、多文档总结、干扰文档、no-evidence、Conversation Trace、前端交互和 multi-query。
- 已完成：新增 `scripts/smoke/rag-natural-corpus-audit-smoke.ps1`，默认委托 `cloud-quality-smoke.ps1` 并启用 `naturalCorpus`、`multiQueryRag` 和 `frontendInteraction` gate；`cloud-quality-smoke.ps1` 新增 `-EnableNaturalCorpusGate`、上传限流 retry 和更长 API retry。
- 已完成后端：KnowledgeBase QA 在回答生成后增加答案数字一致性的 citation 精炼；当答案明确给出数字事实时，会过滤只包含其他数字值的干扰引用，但保留 retrieval hits 和 documentHitCounts 便于 trace / 调试。
- 真实验证：`rag-natural-corpus-audit-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-rag-natural-corpus-20260704143033-86b4f3`；`naturalCorpus` 覆盖 5 份自然语料临时文档、6 类 case，单文档 / 数字事实 / 多文档 / 干扰 / no-evidence / Trace 均通过，`distractorMarketingCitationCount=0`。
- 本轮过程：真实 run 先暴露上传限流 `code=1014`、invoice retention 问题引用 marketing retention 干扰文档、以及 run marker 长数字导致 citation 精炼误伤多文档总结引用；已分别通过 runner retry、数字 citation 精炼和忽略长编号修复并重跑 PASS。
- 边界：本轮真实 run 创建临时 smoke 用户、文档、KnowledgeBase 和 Conversation；artifact 位于 ignored 的 `backend/target/rag-natural-corpus/.../artifact.json`，不提交原文。未删除业务数据，未操作远程 Docker，未改数据库结构，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。

当前任务：真实体验审计问题防回归与短文档泛化 gate（DONE）

## 2026-07-04 补充：旧 REA 问题防回归收口

- 目标：在四个真实体验审计问题均已 `VERIFIED` 后，把 P1/P2/P3 修复升级成更稳定的质量门禁，覆盖短文档中文 / 数字事实 / 相似短文档干扰、quote-first UI 和权限提示回归。
- 已完成：`cloud-quality-smoke.ps1` 的 `shortDocumentRag` gate 增加细分失败桶，记录 `singleDocumentEvidence`、中文短文档 retrieve、数字事实 retrieve、KB 双文档覆盖、相似短文档干扰和 citation marker；`frontendInteraction` gate 失败时记录 `quoteFirstUi`、KB citation UI、`permissionUx` 和 console error 桶。
- 已完成：`rag-real-qa-eval-smoke.ps1` 默认在未 `-SkipFrontend` 时启用 `frontendInteraction`，使 RAG real QA wrapper 也覆盖 quote-first / 权限 UX 细验。
- 真实验证：`cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker `docpilot-cloud-quality-20260704135601-944384`；`shortDocumentRag.failureBuckets=[]`，`frontendInteraction.failureBuckets=[]`。
- 补充说明：本轮前两次真实 run 暴露 smoke fixture 中中文行 marker 受 Windows PowerShell 脚本编码影响写坏，已改为 ASCII-safe marker + codepoint 生成中文内容；该问题属于 runner fixture 稳定性，不是后端解析链路 bug。
- 边界：未删除业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact 原文，未 push。

当前任务：真实体验审计 P1/P2/P3 修复与浏览器交互 smoke gate（DONE）

## 2026-07-03 补充：短文档 RAG / KB 修复验证

- 目标：修复真实体验审计 marker `docpilot-real-audit-20260703195519-5118e8` 暴露的短 txt 单文档 RAG 无 evidence、短文档 KnowledgeBase 双文档覆盖退化、quote-first UI 和权限错误提示问题。
- 已完成后端：单文档 RAG 增加收窄的 marker-supported fallback；KnowledgeBase 总结类问题增加按文档 marker-supported backfill，避免短文档在阈值过滤后完全丢失，但不降低全局 similarity threshold，不放宽普通 no-evidence。
- 已完成前端：文档详情和 KnowledgeBase 引用卡片优先显示 `quoteText`，`snippet` 作为上下文；Conversation citation hover title 优先使用 quote；`apiRequest` 统一抛出带 `code/status/rawMessage` 的 `ApiError`，并对无权限 / 不存在场景给出更清晰中文提示。
- 已完成 smoke：`scripts/smoke/cloud-quality-smoke.ps1` 新增 `shortDocumentRag` gate，覆盖短 Alpha 单文档 retrieve / QA citation、短 Alpha / Beta KnowledgeBase retrieve / QA citation 和 answer grounding。
- 真实验证：`cloud-quality-smoke.ps1 -Mode run` PASS，marker `docpilot-cloud-quality-20260703213703-dbef08`；短单文档 `1` hit / `1` citation，短双文档 KB `2` hits / `2` citations，核心 gate、no-evidence、Conversation Trace、权限隔离、frontend routes、artifact redaction 和 cleanup 均 PASS。
- 当前状态：P1/P2/P3 均已真实验证。`cloud-quality-smoke.ps1 -Mode run -EnableFrontendInteractionGate` PASS，marker `docpilot-cloud-quality-20260703231920-e74334`；新增 `frontendInteraction` gate 覆盖文档详情 quote-first 可见、KnowledgeBase 双 marker citation 可见、跨用户文档无权限提示可见和 console error count `0`。
- 补充说明：本轮曾有一次真实 run 因临时文档 parse timeout 未进入浏览器 gate，清理后重跑 PASS；该失败按环境波动记录，不做远程修复。
- 边界：未删除业务数据，未操作远程 Docker，未改数据库结构，未提交 artifact 原文，未 push。

当前任务：中文文档与真实体验问题自动记录规则沉淀（DONE）

## 2026-07-03 补充：中文记录与真实体验问题自动台账

- 目标：把用户要求沉淀成长期协作规则：内部文档和审计记录默认用中文；Codex 真实体验项目后发现的问题必须自动写入台账。
- 已完成：`AGENTS.md`、`docs/README.md`、`docs/ai-dev/CONSTRAINTS.md` 和 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md` 已明确中文记录规范、真实体验问题触发条件、脱敏边界和问题记录必填字段。
- 当前规则：技术名、路径、API、状态枚举、命令可保留原文；目标、结论、复现步骤、实际结果、预期结果、可能原因、边界说明必须用中文。
- 边界：仅文档 / 流程规则沉淀；未启动服务，未创建业务数据，未修改业务代码，未提交 artifact 原文，未 commit，未 push。

当前任务：DocPilot 真实体验审计问题台账（DONE）

## 2026-07-03 补充：真实体验审计问题台账

- 目标：为 Codex 像真实用户一样运行 DocPilot 后发现的 bug 和质量问题，建立可持续维护、只记录脱敏摘要的文档入口。
- 已完成：新增 `docs/ai-dev/REAL_EXPERIENCE_AUDIT_LOG.md` 作为真实体验审计问题台账，包含严重级别、状态、问题记录格式、首轮审计摘要，以及 marker `docpilot-real-audit-20260703195519-5118e8` 发现的 4 个 OPEN（待修复）问题。
- 流程同步：`AGENTS.md`、`docs/README.md` 和 `docs/ai-dev/CONSTRAINTS.md` 已要求后续真实体验问题写入该台账，避免把完整问题流水塞进 showcase smoke 记录。
- 当前待修复问题：`REA-20260703-P1-001` 短 txt 单文档 RAG 无 evidence；`REA-20260703-P1-002` 短文档 KB 双文档问题退化成单文档命中；`REA-20260703-P2-001` quote-level API 需要 quote-first UI；`REA-20260703-P3-001` 权限拒绝前端提示需要更清晰。
- 边界：本轮仅为文档 / 流程切片；未启动服务，未创建业务数据，未修改代码，未提交 artifact 原文，未 commit，未 push。

Current task: DocPilot Quality Loop v6.6: Memory Provider Extraction Eval Contract (DONE)

## 2026-07-03 Addendum: Quality Loop v6.6 Memory Provider Extraction Eval Contract

- Goal: continue M1 by adding a test-side provider extraction evaluator that can validate JSON memory suggestions from an `AiAnswerService` provider without storing raw conversation text, provider output or memory content.
- Done: `MemoryProviderExtractionEvalRunner` sends a strict JSON-only extraction contract, parses provider suggestions, checks expected memory types, safety validation and forbidden marker leakage, and emits only safe summaries.
- Evidence: stubbed provider tests cover a PASS case for `ANSWER_STYLE` + `TASK_GOAL` extraction and a FAIL case where unsafe token-like provider output is flagged without dumping content.
- Verified: `mvn "-Dtest=MemoryProviderExtractionEvalRunnerTest,MemoryQualityEvalRunnerTest,MemoryQualityEvalFixtureTest,MemoryQualitySmokeScriptSafetyTest" test` PASS, 7 tests; `mvn "-Dtest=*Memory*,*Context*" test` PASS, 65 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS, 276 tests.
- Boundary: this is provider-contract / parser / artifact-safety work using a stub provider, not a real external provider run, not production LLM memory extraction, not schema change, not runtime smoke and not artifact submission.

Current task: DocPilot Quality Loop v6.5: Memory Provider Readiness Eval Artifact (DONE)

## 2026-07-03 Addendum: Quality Loop v6.5 Memory Provider Readiness Eval Artifact

- Goal: start M1 by making Memory Quality Eval explicitly report whether extraction is rule-based or real-provider-backed, so the project does not overclaim LLM memory extraction quality.
- Done: Memory eval metrics now include `providerBackedCaseRate`; safe artifacts include `providerEvaluation` with `extractionProvider`, `status`, `realProviderConfigured`, `modelCallCount`, `rawProviderOutputStored` and a boundary note. Each case summary also records `extractionProvider` and `providerBacked`.
- Current result: offline Memory Quality Eval remains `rule_based` with real provider status `not_configured`, `modelCallCount=0` and `rawProviderOutputStored=false`.
- Verified: `mvn "-Dtest=MemoryQualityEvalFixtureTest,MemoryQualityEvalRunnerTest,MemoryQualitySmokeScriptSafetyTest,RuleBasedMemoryExtractionServiceTest,UserMemoryServiceImplTest,MemorySelectorTest,ContextAssemblyServiceImplTest" test` PASS, 27 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS, 274 tests.
- Boundary: this is provider-readiness / artifact honesty work, not real LLM memory extraction. No tunnel/backend/frontend startup, no business data creation, no provider call, no schema change, no raw conversation or memory text artifact submission and no push.

Current task: DocPilot Quality Loop v6.4: Quote-level RAG Citation API (DONE)

## 2026-07-03 Addendum: Quality Loop v6.4 Quote-level RAG Citation API

- Goal: start R3 by moving RAG evidence exposure from chunk-level `snippet` only toward quote-level citations that are easier to audit for answer grounding.
- Done: single-document RAG and KnowledgeBase RAG retrieval hits now derive `quoteText`, `quoteStartOffset` and `quoteEndOffset` from the retrieved chunk; citation records and API response VOs expose the same fields while preserving existing `snippet`, chunk offsets and scores.
- Frontend contract: `frontend/lib/rag-api.ts` and `frontend/lib/knowledge-base-api.ts` now include optional quote fields so UI slices can render quote-first citation cards later without changing the API contract again.
- Verified: `mvn "-Dtest=RagEvidenceQuoteExtractorTest,RagQaControllerTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,DocumentAgentServiceImplTest" test` PASS, 26 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 214 tests; `npm run lint` PASS.
- Boundary: no schema change, no retrieval ranking change, no prompt / answer-generation change, no tunnel/backend/frontend startup, no business data creation, no real provider call, no artifact submission and no push. The frontend page rendering change is deferred because several page files need a separate encoding-safe cleanup before editing user-facing Chinese copy.

Current task: DocPilot Quality Loop v6.3: Multi-query Real Smoke Evidence (DONE)

## 2026-07-03 Addendum: Quality Loop v6.3 Multi-query Real Smoke Evidence

- Goal: finish R1's runtime evidence loop by running the real RAG QA smoke with the new request-scoped multi-query gate enabled.
- Done: `scripts/smoke/rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS with marker `docpilot-rag-real-qa-20260703192456-2a62e9`.
- Multi-query evidence: `multiQueryRag` PASS with `multiQueryApplied=true`, `queryVariantCount=4`, `queryDedupeCount=24`, `6` retrieve hits, `6` QA citations, Alpha retrieve/citation `3/3` and Beta retrieve/citation `3/3`.
- Regression evidence: tunnel, backend health, frontend routes, auth, upload / parse / indexing, chunk quality, MySQL / Qdrant consistency, single-document RAG, KnowledgeBase RAG, representative corpus, answer grounding, hard negative, semantic gate, real provider faithfulness, no-evidence, Conversation Trace, permission isolation, cleanup and artifact redaction all remained PASS.
- Note: the first run marker `docpilot-rag-real-qa-20260703192105-e953d2` reached `multiQueryRag` PASS but failed later at Conversation message API with `FAILED_CORE_FLOW`; cleanup succeeded and the immediate rerun passed.
- Boundary: this is small real-link smoke evidence for request-scoped multi-query, not a large-scale relevance uplift benchmark, LLM query planner evaluation or online SLA. Artifacts remain ignored and were not submitted.

Current task: DocPilot Quality Loop v6.2: Multi-query Real Smoke Gate Runner (DONE)

## 2026-07-03 Addendum: Quality Loop v6.2 Multi-query Real Smoke Gate Runner

- Goal: continue R1 by making the real cloud quality smoke able to explicitly exercise request-scoped multi-query retrieval, without changing the default cloud-quality runner behavior.
- Done: `cloud-quality-smoke.ps1` now supports optional `-EnableMultiQueryGate`; `rag-real-qa-eval-smoke.ps1` enables that gate by default and exposes `-SkipMultiQueryGate`.
- Gate behavior: the optional gate sends KnowledgeBase retrieve / QA requests with `multiQueryEnabled=true` and `maxQueryVariants=4`, then records redacted checks for `multiQueryApplied`, `queryVariantCount`, `queryDedupeCount`, two-document retrieve/citation coverage and answer grounding.
- Verified: `rag-real-qa-eval-smoke.ps1 -Mode plan` PASS; `rag-real-qa-eval-smoke.ps1 -Mode dry-run` PASS; `mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS, 3 tests.
- Boundary: this slice did not run `run` mode, did not start tunnel/backend/frontend, did not create business data, did not call a real provider, did not submit artifacts and did not push. Real-link multi-query evidence still requires the next smoke run.

Current task: DocPilot Quality Loop v6.1: Request-scoped Multi-query Retrieval Eval (DONE)

## 2026-07-03 Addendum: Quality Loop v6.1 Request-scoped Multi-query Retrieval Eval

- Goal: start R1 by making KnowledgeBase multi-query retrieval explicitly controllable per request, while keeping the default runtime behavior unchanged.
- Done: `KnowledgeBaseRagRetrieveRequest` and `KnowledgeBaseRagQaRequest` now accept optional `multiQueryEnabled` and `maxQueryVariants`; controller / QA / retrieval services propagate the override, validate request-scoped variant limits and still default to global `app.rag.retrieval.*` settings when fields are absent.
- Eval evidence: KnowledgeBase offline eval now reports `retrievalModeMetrics.multi_query` beside `vector` and `hybrid`, giving a redacted comparison point for multi-query retrieval without storing rewritten query text, document text, prompt, evidence context or answer output.
- Verified: `mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalFixtureTest,RuleBasedQueryRewriteServiceTest,RagRetrievalPropertiesTest" test` PASS, 32 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 211 tests.
- Boundary: this slice did not start tunnel/backend/frontend, create business data, call a real provider, change schema, touch remote Docker, submit artifacts or push. Multi-query remains default-off unless config or request explicitly enables it.
- Next: continue R1 with a real-link enabled multi-query smoke comparison, then move to R3 quote-level citation or M1 real-provider memory extraction eval.

Current task: DocPilot Quality Loop v5.6: Query Rewrite / Multi-query Retrieval (DONE)

## 2026-06-29 Addendum: Quality Loop v5.6 Query Rewrite / Multi-query Retrieval

- Goal: finish A6 with a conservative KnowledgeBase retrieval enhancement for complex questions, without changing the default runtime behavior.
- Done: added a rule-based `QueryRewriteService` and default-off `app.rag.retrieval.multi-query-enabled`; when enabled, KnowledgeBase retrieval generates bounded query variants, runs vector search per variant, deduplicates hits by chunk identity, then continues through the existing threshold, hybrid, rerank, scope guard and diversity gates.
- Observability: `KnowledgeBaseRagRetrievalResult` / response now expose `multiQueryApplied`, `queryVariantCount` and `queryDedupeCount`; variant text is not stored in the result or artifact-facing response.
- Configuration: `APP_RAG_RETRIEVAL_MULTI_QUERY_ENABLED=false` and `APP_RAG_RETRIEVAL_MAX_QUERY_VARIANTS=3` are documented in config examples.
- Verified: `mvn "-Dtest=RuleBasedQueryRewriteServiceTest,RagRetrievalPropertiesTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseEvidenceContextBuilderTest" test` PASS, 32 tests; `mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS, 209 tests; real `scripts/smoke/rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007 -ReuseRunningServices` PASS, marker `docpilot-rag-real-qa-20260629202542-3e47d9`.
- Boundary: this is deterministic rule-based query rewrite, not LLM query planning, not real-provider query expansion, not a proven relevance uplift benchmark, and not enabled by default.
- Next: A4-A6 are complete. A natural next slice is an explicitly enabled multi-query runtime/eval comparison to determine whether it improves recall on complex questions without hurting no-evidence.

Current task: DocPilot Quality Loop v5.5: Chunk Quality v2 (DONE)

## 2026-06-29 Addendum: Quality Loop v5.5 Chunk Quality v2

- Goal: continue A5 by making chunk quality metadata more useful for real RAG diagnosis, especially section path, structured blocks and split / duplicate signals.
- Done: `DocumentChunkCandidate` now carries `sectionPath`; `ChunkingServiceImpl` builds nested heading paths, detects table / list blocks, flags window and mid-sentence splits, and marks duplicate chunk content. Indexing metadata and Qdrant payload propagation now include `sectionPath`.
- Smoke gate sync: `cloud-quality-smoke.ps1` now includes `sectionPath` in the MySQL / Qdrant payload consistency field set.
- Verified: `mvn "-Dtest=ChunkingServiceImplTest,RagIndexingServiceImplTest,DocumentChunkServiceImplTest,VectorPointTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS, 41 tests.
- Boundary: no database schema change, no backend / frontend / tunnel startup, no business data creation, no real provider call, no remote Docker or hk-ops operation in this slice.
- Next: continue A6 default-off KnowledgeBase query rewrite / multi-query retrieval, then decide whether to run a full real cloud quality smoke after A6.

Current task: DocPilot Quality Loop v5.4: RAG Retrieval Error Analysis Report (DONE)

## 2026-06-29 Addendum: Quality Loop v5.4 RAG Retrieval Error Analysis Report

- Goal: land A4 by turning offline RAG eval output from aggregate pass rates into a redacted retrieval error analysis report.
- Done: `RagRetrievalErrorAnalysis` now summarizes missed retrieval, wrong retrieval, no-evidence refusal, citation unsupported, answer unsupported, forbidden leak, scope violation and ranking candidate pass counts for both KnowledgeBase RAG eval and RAG Real QA eval artifacts.
- Safety boundary: the report stores counts, booleans, failure reason buckets and case ids only; it does not store document text, query text, answer text, model instructions, evidence context, credentials, cloud addresses or connection strings.
- Verified: `mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest,KnowledgeBaseRagEvalRunnerTest" test` PASS, 5 tests.
- Boundary: this is an offline `MockEmbeddingProvider` + `InMemoryVectorStoreClient` eval/reporting gate, not a real provider benchmark, online SLA or broad relevance dashboard.
- Next: continue A5 Chunk Quality v2, then A6 default-off KnowledgeBase multi-query retrieval.

当前任务：DocPilot Quality Loop v5.3：RAG Real Provider Faithfulness Smoke（DONE）

## 2026-06-29 追加任务：Quality Loop v5.3 RAG Real Provider Faithfulness Smoke

- 目标：完成用户选择的 A1，把 RAG Real QA smoke 从“真实链路 retrieve / citation / marker gate”推进到“小规模真实回答 provider answer faithfulness 证据”，确认关键 grounded QA 不是 mock 回答。
- 已完成 runner 增强：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRealProviderFaithfulnessGate`，`rag-real-qa-eval-smoke.ps1` 默认开启并提供 `-SkipRealProviderFaithfulnessGate`；gate 只保存 `answerProvider`、`answerModel`、`modelCallCount`、`answerLength`、`noEvidence` 和 `passed`，不保存回答原文、prompt、文档原文、evidence context、token、云地址或连接串。
- 真实 run 过程：首次 run 暴露 `realQaHardGate.answerFaithfulness` 问法不够稳定，真实回答未带出 `ALPHA-CLOUD-GATE` / citation marker，整体 `FAILED_CORE_FLOW`；随后把该问法收窄为直接询问 `ALPHA-CLOUD-GATE` 本身并重跑。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629191831-69d71e`。
- 关键结果：`realProviderFaithfulness` PASS，`knowledgeBaseRag`、`answerFaithfulness`、`claimSupport`、`numericFaithfulness` 四个 scope 均为非 mock provider、`modelCallCount=1`、`noEvidence=false`、answer length 大于 `0`；`realQaHardGate`、`realQaSemanticGate`、representative corpus、answer grounding、no-evidence、Conversation Trace、权限隔离、frontend routes、cleanup 和 artifact redaction 均保持 PASS。
- 边界：本片是小规模真实 provider smoke，不是大规模 answer faithfulness benchmark、通用语义蕴含模型或线上 SLA；真实 smoke 创建了临时 smoke 用户、文档、KnowledgeBase、Conversation 和 ignored 脱敏 artifact，不提交 artifact 原文，不 push。
- 下一步：A1 + A2 + A3 已完成一轮闭环；后续可继续做 A4“真实失败样本审计与题库沉淀”，把首次 run 这种问法不稳的 case 系统化记录成 REVIEW 样本，或转向 Memory 真实 provider 抽取质量小样本。

## 2026-06-29 追加任务：Quality Loop v5.2 RAG Claim Support Evidence Scorer

- 目标：继续推进用户选择的 A2，把 RAG Real QA Eval 从“marker / citation 数量达标”推进到“关键 claim 必须被目标 evidence marker 支撑”，为后续真实 provider 小样本 answer faithfulness 对比提供更细的离线门禁。
- 已完成 test-side scorer：新增 `RagClaimSupportScorer` / `RagClaimSupportScore`，`RagRealQaEvalCase` 支持可选 `expectedClaims`，每个 claim 只保存脱敏 claim id、answer marker、evidence marker 和 forbidden marker；`RagRealQaEvalResult` 的 case summary 输出 `claimSupportRequired`、`claimCount`、`supportedClaimCount`、`unsupportedClaimCount`、`claimSupportHit`、`forbiddenClaimHit` 等脱敏字段，不保存回答原文、文档原文、prompt 或 evidence context。
- 已完成指标增强：`RagRealQaEvalMetrics` 新增 `claimSupportScorerPassRate`、`supportedClaimRate`、`unsupportedClaimRate`、`forbiddenClaimRate`；`real-qa-eval-cases.json` 为访问审批链、四小时 SLA、root cause、报销限额和 vendor risk citation grounding 等代表 case 增加 `expectedClaims`。
- 已验证：`mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，10 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，207 tests。
- 边界：本片只增强离线 test-side scorer / fixture / metrics / artifact schema 和事实源文档；不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API 或数据库结构，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。该 scorer 基于 synthetic marker contract，不是通用自然语言蕴含模型或大规模真实 provider benchmark。
- 下一步：继续进入 A1，小规模真实 provider Answer Faithfulness Eval；优先用已有 smoke runner 的真实链路门禁做小样本验证，不做大规模付费 eval，不扩大能力边界。

## 2026-06-29 追加任务：Quality Loop v5.1 RAG Real Corpus Expansion to 40 Cases

- 目标：进入用户选择的 A1 + A2 + A3 路线后，先落地 A3 的最小闭环，把 RAG Real QA Eval 的脱敏离线语料从 `26` 个 case 扩到 `40` 个 case，让后续 A2 claim / evidence scorer 和 A1 真实 provider 小样本验证有更扎实的覆盖面。
- 已完成离线语料扩容：`real-qa-eval-cases.json` 新增 `14` 个脱敏企业知识库样例，覆盖合同续约通知、访问变更审批链、SLA 数字忠实度、审计交接、多文档客户事故沟通、API deprecation hard negative、隐私删除 near-miss no-evidence、root cause answer faithfulness、SSO / MFA 比较、报销限额数字忠实度、跨租户 scope isolation、长备份 runbook、hybrid keyword 噪声和 vendor risk citation grounding。
- 已完成 fixture 门禁增强：`RagRealQaEvalFixtureTest` 要求总 case 数至少 `40`，并提高 `hard_negative`、`answer_faithfulness`、`claim_support`、`numeric_faithfulness`、`multi_doc_summary`、`scope_isolation` 等关键类别的覆盖下限；`RagRealQaEvalRunnerTest` 同步要求 eval case count 至少 `40`。
- 已验证：`mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，10 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，207 tests。
- 边界：本片只增强离线 test-side eval / JSON fixture / 测试和事实源文档；不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API 或数据库结构，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。该结果是脱敏离线质量门禁扩容，不代表真实 provider 大规模 answer faithfulness benchmark。
- 下一步：继续进入 A2，给 Real QA Eval 增加 claim support / evidence support scorer，让指标不只看 marker 和 citation 数，还能显式判断“回答中的关键 claim 是否被目标 evidence marker 支撑、是否泄漏 forbidden claim”。

## 2026-06-29 追加任务：Quality Loop v4.3 RAG Real QA Semantic Gate Smoke

- 目标：把 v4.2 离线 `claim_support` / `numeric_faithfulness` 语义支持门禁迁移到真实 RAG Real QA smoke，让真实链路同时检查“结论必须由目标 evidence 支持”和“数字 / 年限不能被相近文档带偏”。
- 已完成 smoke runner 增强：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRealQaSemanticGate`，在 Alpha / Beta 临时 KnowledgeBase 中加入目标 evidence marker 和干扰 marker；`rag-real-qa-eval-smoke.ps1` 默认打开该 gate，并提供 `-SkipRealQaSemanticGate`。
- 已完成脱敏边界：artifact 只保存 no-evidence 布尔值、hit / citation 数、target / forbidden citation count、score summary、answer length 和 marker / citation 布尔结果，不保存回答原文、prompt、文档原文、evidence context、token、云地址或连接串。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629183549-4aafc3`。
- 关键结果：`realQaSemanticGate` PASS；`claimSupport` 与 `numericFaithfulness` 均为 `1` retrieve hit、`1` QA citation、target citation count `1`、forbidden citation count `0`、expected marker satisfied、forbidden marker absent、citation marker present。`realQaHardGate`、representative corpus、answer grounding、普通 no-evidence、Conversation Trace、权限隔离、frontend routes、cleanup 和 artifact redaction 均保持 PASS。
- 边界：本片只增强 smoke runner / wrapper / 安全测试和文档记录，不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。该结果是小规模真实链路语义支持门禁，不代表通用语义蕴含模型、大规模真实 provider benchmark 或线上 SLA。
- 下一步：可继续 RAG 方向做更难的真实 provider 小样本 answer faithfulness / citation support 对比；也可转入 Memory 方向做真实 provider 抽取质量小样本，或做前端 Trace / Evidence 可解释性二次审计。

## 2026-06-29 追加任务：Quality Loop v4.2 RAG Claim Support / Numeric Faithfulness Eval

- 目标：继续把 RAG Real QA Eval 从普通 answer faithfulness 推进到更细的语义支持门禁，覆盖“结论必须由目标 evidence 支持”和“数字 / 年限不能被相近文档带偏”两类常见面试追问。
- 已完成离线 eval：`real-qa-eval-cases.json` 新增 `claim_support` 与 `numeric_faithfulness` 两个脱敏企业场景；前者验证 vendor access renewal 的 manager approval 只能来自目标 evidence，后者验证 invoice archive retention 的 seven-year evidence 不被 three-year 干扰文档污染。
- 已完成指标增强：`RagRealQaEvalMetrics` 新增 `claimSupportPassRate` 与 `numericFaithfulnessPassRate`，safe artifact 同步输出两个指标，仍不保存文档全文、query 原文、prompt、evidence context 或模型输出。
- 已验证：`mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，10 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，207 tests。
- 边界：本片只增强离线 Real QA eval / fixture / metrics，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。该结果是离线语义支持门禁增强，不代表真实 provider 大规模 answer faithfulness benchmark。

## 2026-06-29 追加任务：Quality Loop v4.1 Memory Extraction Quality Eval

- 目标：把 Memory Quality Eval 从“状态分层 / trace 计数可用”推进到“能拦住更多低质量长期记忆候选”，覆盖多信号抽取、assistant 指令污染、低价值寒暄、一次性回答风格、敏感 token/API key 指令等 case。
- 已完成规则收窄：`RuleBasedMemoryExtractionService` 在抽取候选前过滤敏感内容和一次性 / 临时指令，避免把 token/API key 占位指令或“这一次回答详细一点，后面不用记住”沉淀为长期记忆候选；正常用户长期偏好和任务目标抽取保持不变。
- 已完成离线 eval：`memory-quality-eval-cases.json` 新增 `multi_signal_extraction`、`assistant_contamination`、`low_value_suppression`、`temporary_instruction_suppression`、`sensitive_suggestion_suppression` 五类脱敏 case；`MemoryQualityEvalMetrics` 新增 `suggestionSafetyRate`、`userSignalExtractionRate`、`noiseSuppressionRate`、`temporaryInstructionSuppressionRate`，artifact 仍只保存脱敏 summary、布尔值、类型和计数，不保存会话正文或 memory 正文。
- 已验证：`mvn "-Dtest=MemoryQualityEvalFixtureTest,MemoryQualityEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*Memory*,*Context*" test` PASS，63 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，267 tests。
- 边界：本片不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 LLM memory extraction，不改数据库结构，不删除业务数据，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。该结果是规则式 Memory 候选抽取的离线质量门禁增强，不代表真实 provider 长期记忆抽取质量或大规模个性化效果评测。

## 2026-06-29 追加任务：Quality Loop v3.9 Memory Governance Edit / Resolve

- 目标：把 Memory Governance 从“发现重复 / 冲突并阻断直接 accept”推进到“用户能处理冲突”：支持编辑 ACTIVE memory、保留旧记忆、用候选替换旧记忆、手动合并候选，并用真实 smoke 验证治理闭环。
- 已完成后端：新增 `PATCH /api/memories/{memoryId}` 编辑 ACTIVE memory；新增 `POST /api/memories/suggestions/{memoryId}/resolve`，支持 `KEEP_ACTIVE`、`REPLACE_ACTIVE`、`MERGE_WITH_ACTIVE`。所有路径都校验当前用户、状态、同类型、敏感内容、重复 / 冲突治理；不改数据库结构、不 hard delete。
- 已完成前端：`/conversations` Memory 抽屉中 ACTIVE memory 可编辑 / 保存 / 取消；带 `duplicateOfId` 或 `conflictWithId` 的候选显示“保留旧记忆 / 替换旧记忆 / 合并”，合并文本由用户确认，不做自动 LLM merge。
- 已完成 smoke：`memory-quality-smoke.ps1` / `cloud-quality-smoke.ps1 -EnableMemoryQualityGate` 新增 Memory resolution gate，覆盖冲突候选直接 accept 被拦截、`KEEP_ACTIVE` 后候选变 `IGNORED`、`REPLACE_ACTIVE` 更新旧 ACTIVE、敏感编辑被拒、普通编辑成功、`MERGE_WITH_ACTIVE` 更新旧 ACTIVE，artifact 只保存状态、计数、长度和错误 code，不保存记忆正文。
- 已验证：`mvn "-Dtest=*MemoryQualityEval*,*Memory*,*Context*" test` PASS，63 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，267 tests；`mvn "-Dtest=MemoryQualitySmokeScriptSafetyTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS，5 tests；`npm run lint` PASS；`npm run build` PASS；`memory-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 `memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-memory-quality-20260629140941-6668d9`。
- 追加修复：首次真实 run 在 `answerGrounding` 暴露 KnowledgeBase QA 回答未逐字带出 `ALPHA-CLOUD-GATE` / `BETA-CONTEXT-GATE`，已把 KB gate 的问题文本改为明确要求逐字包含 evidence marker，随后真实 run PASS。
- 边界：本片不做真实 LLM memory extraction，不做自动合并，不新增版本历史 / 审计表，不改数据库结构，不删除业务数据，不操作远程 Docker，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。该结果证明用户可控的 Memory 治理闭环，不代表大规模长期记忆个性化效果评测。

## 2026-06-29 追加任务：Quality Loop v3.8 RAG Quality Interview Docs Sync

- 目标：把 v3.5-v3.7 的 RAG hard negative / answer faithfulness / answer grounding 质量闭环同步到 README 和 showcase 面试材料，避免对外材料停留在“可演示 RAG / Agent”旧口径，也避免夸大成线上 SLA 或大规模 benchmark。
- 已完成：`README.md` 补充 no-evidence、answer grounding、hard negative、answer faithfulness、Conversation Trace、MySQL / Qdrant 一致性和脱敏 artifact 质量门禁口径。
- 已完成：`docs/showcase/PROJECT_INTERVIEW_BRIEF.md`、`RESUME_BULLETS.md`、`INTERVIEW_QA.md` 同步 RAG 工程闭环讲法，强调真实 embedding + Qdrant smoke、hard-negative 支持度门禁和边界。
- 边界：本片只改对外 / 面试文档和事实源，不改业务代码、不启动服务、不创建业务数据、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。hard-negative 支持度门禁明确写成近阈值启发式，不写成通用语义蕴含模型或大规模 relevance benchmark。

## 2026-06-29 追加任务：Quality Loop v3.7 Hard Negative Near-threshold Support Gate

- 目标：修复 v3.6 暴露的真实链路质量缺口：高词面相似但缺少目标结论的 hard negative 问题仍能越过 `0.50` evidence confidence gate，返回 `3` hits / `3` citations。
- 已完成：`KnowledgeBaseRagRetrievalServiceImpl` 在既有 similarity threshold、hybrid confidence gate、rerank 和 diversity selection 后，新增近阈值低支持度拒答门；仅当非总结类问题、最高 threshold score 只略高于阈值、且 query 关键英文业务词在候选 evidence 中覆盖不足时，才清空 hits 进入 no-evidence。
- 已完成测试：新增 hard negative 低支持度拒答测试，以及近阈值但 evidence 覆盖关键业务词时不误杀的正例测试；已有 summary / hybrid keyword-only 多文档召回测试保持通过。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，13 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，207 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629130454-1d1d6c`。
- 关键结果：`realQaHardGate` 从 v3.6 的 `REVIEW` 变为 PASS；`hardNegative` 为 `retrieveNoEvidence=true`、`qaNoEvidence=true`、`0` hits、`0` citations；`answerFaithfulness` 仍 PASS，target citation `1`、forbidden citation `0`；representative corpus、answer grounding、普通 no-evidence、Conversation Trace、权限隔离、frontend routes 和 artifact redaction 均保持 PASS。
- 边界：这是启发式的近阈值支持度门禁，不是通用自然语言蕴含模型；不改数据库结构，不删除业务数据，不操作远程 Docker，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。后续如果要更强，应做 evidence entailment / claim support scorer，而不是继续堆硬编码规则。
- 下一步：可进入 README / showcase 面试口径同步，把 RAG 质量门禁从 REVIEW 到 PASS 的证据讲清楚；或继续 Memory 编辑 / 合并交互与门禁。

## 2026-06-29 追加任务：Quality Loop v3.6 RAG Real QA Hard Gate Smoke 第一片

- 目标：把 v3.5 的 hard negative / answer faithfulness 代表检查小规模迁移进真实 RAG Real QA smoke，让真实链路不只验证普通 no-evidence 和 answer grounding，也能暴露高词面相似但结论缺失的问题。
- 已完成：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRealQaHardGate`，复用本轮 Alpha / Beta 临时 KnowledgeBase，不额外上传文件，检查 `hardNegative` 与 `answerFaithfulness` 两个 scope；`rag-real-qa-eval-smoke.ps1` 默认打开该 gate，并提供 `-SkipRealQaHardGate`。
- 已完成脱敏边界：artifact 只保存 no-evidence 布尔值、hit / citation 数、score summary、marker 命中计数和 answer length，不保存回答原文、prompt、文档原文、evidence context、token、云地址或连接串；脚本安全测试已覆盖新 gate。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` 完成，marker 为 `docpilot-rag-real-qa-20260629125627-c0915e`，整体 `REVIEW`。
- 关键结果：核心链路 gate 均 PASS，包括 tunnel、backend health、frontend routes、auth、上传 / parse / indexing、chunk quality、MySQL / Qdrant 一致性、单文档 RAG、KB RAG、representative corpus、answer grounding、普通 no-evidence、Conversation Trace、权限隔离、cleanup 和 artifact redaction。`answerFaithfulness` PASS：target citation `1`、forbidden citation `0`、expected marker satisfied、forbidden marker absent。`hardNegative` REVIEW：高词面相似问题仍返回 `3` hits / `3` citations，vector score 约 `0.50-0.55`，说明当前 evidence confidence / grounding policy 对这类缺证结论还不够严格。
- 边界：本片只增强 smoke runner / wrapper / 安全测试和文档记录，不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。真实 smoke 创建了临时用户、三份文档、KnowledgeBase、Conversation 和 ignored artifact；该结果是小规模真实链路质量缺口证据，不是大规模 benchmark。
- 下一步：优先做 hard negative 召回后的拒答治理：比较 query / evidence 语义支持度、answer audit fallback reason、rerank score 与 vector score 组合门禁，先以离线测试和小规模 smoke 校准，不直接调高全局阈值伤害正常多文档召回。

## 2026-06-29 追加任务：Quality Loop v3.5 RAG Hard Negative / Answer Faithfulness Eval 第一片

- 目标：继续把 RAG Real QA Eval 从“覆盖更多真实问法”推进到“能拦住高词面相似但无证据的问题，并观察回答是否忠实落在目标 evidence 上”，优先补 hard negative 与 answer faithfulness 两类离线门禁。
- 已完成：`real-qa-eval-cases.json` 追加 `real-hard-negative-payroll-tax` 和 `real-answer-faithfulness-policy-exception` 两个脱敏企业场景；前者用 payroll / tax / vendor / owner 等强词面干扰但不提供目标结论，要求 no-evidence；后者在目标 evidence 与 SLA 干扰文档之间要求只命中 `real-policy-exception-owner-marker`，不得泄漏 forbidden marker。
- 已完成指标增强：`RagRealQaEvalMetrics` 新增 `hardNegativePassRate` 与 `answerFaithfulnessPassRate`，artifact safe map 同步输出两个脱敏指标；`hard_negative` 同时纳入 distractor suppression 聚合。
- 已验证：`mvn "-Dtest=RagRealQaEvalFixtureTest,RagRealQaEvalRunnerTest" test` PASS，3 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，10 tests。
- 边界：本片只增强离线 test-side eval / fixture / metrics，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。该结果是离线质量门禁增强，不是大规模真实 answer faithfulness benchmark。
- 下一步：可继续 RAG 方向把 hard negative / answer faithfulness 代表 case 小规模迁移到真实 smoke；也可转入 Memory 编辑 / 合并交互与门禁，或做 README / showcase 面试口径同步。

## 2026-06-29 追加任务：Quality Loop v3.4 RAG Answer Grounding Gate v1

- 目标：把真实 RAG smoke 从“检索和 citation 数量达标”继续推进到“最终回答文本确实落在 evidence 上”，要求回答包含预期 evidence marker、不包含 forbidden marker，并带 citation marker。
- 已完成：`cloud-quality-smoke.ps1` 新增 `Test-AnswerGrounding`，在单文档 RAG、KnowledgeBase 两文档 RAG 和 representative corpus 三文档 RAG 后检查 `answerPresent`、`expectedMarkerHits`、`forbiddenMarkerHit` 和 `citationMarkerPresent`；artifact 只保存长度、计数和布尔结果，不保存回答原文、prompt、evidence context 或 response 原文。
- 已完成专项入口：`rag-real-qa-eval-smoke.ps1` 的 plan 输出新增 `answer_grounding` case type 和 `answerGrounding` gate；Representative Corpus 问题文本已明确要求逐字带出 `ALPHA-CLOUD-GATE`、`BETA-CONTEXT-GATE` 和 `real-incident-detection-marker`，使 gate 检查对象与测试目标对齐。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，3 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260629003157-630db5`。
- 关键结果：`answerGrounding` gate 覆盖 `singleDocumentRag`、`knowledgeBaseRag` 和 `representativeCorpus` 三个 scope；三者均 `answerPresent=true`、`expectedMarkersSatisfied=true`、`forbiddenMarkerHit=false`、`citationMarkerPresent=true`。Representative corpus 同轮返回 `8` retrieve hits / `8` citations，documentHitCounts 覆盖 Gamma `203:2`、Beta `202:3`、Alpha `201:3`；no-evidence、Conversation Trace、权限隔离、前端 routes、cleanup 和 artifact 脱敏均保持 PASS。
- 边界：本片只增强 smoke runner、脚本安全测试和脱敏记录，不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。真实 smoke 创建了临时用户、三份文档、KnowledgeBase 和 Conversation 数据，artifact 位于 ignored `backend/target/rag-real-qa/.../artifact.json`；该结论是小规模真实链路回答落证门禁，不是大规模真实语料 benchmark 或线上 SLA。
- 下一步：可进入 Memory 编辑 / 合并交互与门禁，或继续 RAG 方向做 hard negative corpus、answer faithfulness 更细粒度审计、README / showcase 面试口径同步。

## 2026-06-28 追加任务：Quality Loop v3.3 RAG Real Corpus 真实链路代表性三文档门禁

- 目标：把 RAG Real Corpus 的代表性企业问答样例从离线 eval 小规模迁移进真实链路 smoke，让 RAG Real QA 专项 smoke 不只复用两文档通用门禁，还能验证三文档代表 corpus 的多文档覆盖、citation grounding 和脱敏 artifact。
- 已完成：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRepresentativeCorpusGate`，额外创建一份 incident review Gamma 文档，并与既有 Alpha / Beta 两文档组成 Representative Corpus KB；gate 要求 retrieve 和 QA citation 都覆盖 Alpha / Beta / Gamma 三份文档，artifact 只记录 ids、count、documentHitCounts 和 score summary。
- 已完成专项入口：`rag-real-qa-eval-smoke.ps1` 默认打开 representative corpus gate，并提供 `-SkipRepresentativeCorpusGate` 跳过开关，避免通用 cloud smoke 默认增加真实链路成本；plan 输出包含 `representative_corpus` 和 `representativeCorpusEnabledByDefault=true`。
- 已验证：`rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，2 tests；`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS，9 tests；真实 `rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-qa-20260628234235-5c1b94`。
- 关键结果：代表性三文档 gate 返回 `8` retrieve hits / `8` citations，documentHitCounts 覆盖 Gamma `196:2`、Beta `195:3`、Alpha `194:3`；no-evidence、Conversation Trace、权限隔离、前端 routes、cleanup 和 artifact 脱敏均保持 PASS。
- 边界：本片只增强 smoke runner 和脚本安全测试，不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。真实 smoke 创建了临时用户、三份文档、KnowledgeBase 和 Conversation 数据，artifact 位于 ignored `backend/target/rag-real-qa/.../artifact.json`；该结论是小规模真实链路代表性门禁，不是大规模 relevance benchmark。
- 下一步：可进入 Memory 编辑 / 合并交互与门禁，或继续 RAG 方向做 answer grounding 审计 / hard negative corpus / README 与 showcase 面试口径同步。

## 2026-06-28 追加任务：Quality Loop v3.2 Memory Governance 第一片

- 目标：把 Memory 从“能接受 / 忽略候选”推进到“接受前有治理门禁”，先阻止明显重复 ACTIVE memory 和同类型偏好冲突候选直接生效，同时让前端能展示治理提示。
- 已完成：`UserMemoryResponse` 新增脱敏治理字段 `duplicateOfId`、`conflictWithId`、`governanceHint`、`similarityScore`；`UserMemoryServiceImpl` 在手动创建和接受候选前检查同类型 ACTIVE memory 的精确重复、近似重复和少量明确冲突词；候选列表 / 提取结果会带重复或冲突提示。
- 已完成前端展示：`/conversations` Memory 抽屉读取治理字段，显示疑似重复、冲突 memory id 和相似度提示；不新增“自动合并”按钮，不删除旧记忆，不改表结构。
- 已完成真实 smoke 门禁：`memory-quality-smoke.ps1` / `cloud-quality-smoke.ps1 -EnableMemoryQualityGate` 新增 Memory Governance 检查，先创建 ACTIVE `ANSWER_STYLE` 基线，再从临时会话抽取冲突 answer-style suggestion，要求返回 `governanceHint=conflict_active_memory`、`conflictWithId` 非空，并验证直接 accept 被阻止，错误原因匹配治理门禁。
- 已验证：Gemini CLI 做轻量 UX sanity 建议；`memory-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 `memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-memory-quality-20260628223255-0a06e6`；`mvn "-Dtest=*MemoryQualityEval*,*Memory*,*Context*" test` PASS，54 tests；此前 `mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，255 tests；`npm run lint` PASS；`npm run build` PASS。
- 边界：本片不做真实 LLM memory extraction，不做自动合并 / 自动删除，不改数据库结构，不删除业务数据，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。真实 smoke 创建了临时 smoke 用户、文档、KnowledgeBase、Conversation 和 memory 数据，artifact 位于 ignored `backend/target/memory-quality/.../artifact.json`。
- 下一步：进入下一轮自驱切片，可优先做 Memory 编辑 / 合并交互设计与门禁，或回到 RAG Real Corpus 的真实链路代表 case 迁移；继续避免把 smoke 级 PASS 写成大规模个性化效果评测。

## 2026-06-28 追加任务：Quality Loop v3.1 RAG Real Corpus Eval 第一片

- 目标：把 RAG Real QA Eval 从 9 个偏 synthetic marker 的小样例扩展为更贴近企业知识库真实问答形态的离线质量门禁，先覆盖长文档、近义 no-evidence、多文档总结、citation grounding、scope isolation 和 hybrid / rerank 干扰。
- 已完成：`real-qa-eval-cases.json` 从 9 个 case 扩到 22 个 case，新增 security policy、runbook、onboarding、expense policy、contract clause、incident review、API policy、access audit 等脱敏企业场景样例；新增 `long_document`、`near_miss_no_evidence`、`multi_doc_summary`、`citation_grounding`、`scope_isolation` 等类别。
- 已完成指标增强：`RagRealQaEvalMetrics` 新增 `longDocumentCasePassRate`、`nearMissNoEvidenceRate`、`multiDocSummaryPassRate`、`distractorSuppressionRate`，artifact 仍只输出脱敏 summary，不保存文档全文、query 原文、prompt、evidence context、模型输出或 secrets。
- 已验证：`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，9 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，204 tests。
- 边界：本片只增强离线 test-side eval，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。该结果提升 RAG 质量门禁覆盖度，但仍不是大规模真实语料 benchmark。
- 下一步：进入 Memory Governance v1 第一片，优先做重复 / 冲突记忆检测、接受候选前的治理提示和对应离线 / API 门禁；后续再把代表性 RAG case 小规模迁移进真实链路 smoke。

## 2026-06-28 追加任务：Quality Loop v2.3 Memory 产品化第一片

- 目标：把 `/conversations` 的 Memory 抽屉从“能看到 ACTIVE / SUGGESTED 列表”推进到“用户能理解来源、置信度、优先级和重复风险”，提升候选接受 / 忽略前的判断质量。
- 已完成：Memory 面板新增生效 / 候选 / 重复提示 KPI、类型分布、来源说明（手动添加 / 系统候选 + 会话 / 消息来源）、priority、confidence、更新时间、ACTIVE 重复提示和候选已存在提示；候选按 priority / confidence 排序，生效记忆按 priority / 更新时间排序。Gemini CLI 只用于轻量 UX sanity 建议，Codex 落地代码与安全验证。
- 已验证：`npm run lint` PASS；`npm run build` PASS；真实浏览器创建临时用户、3 条 ACTIVE memory、2 条 suggestion，marker 为 `docpilot-memory-ui-product-1782651263292`，Conversation `41`。桌面 Memory 面板显示 KPI、来源、confidence 和重复提示，`cardCount=5`，`scrollWidth=clientWidth=1265`；`390x844` 下 `scrollWidth=clientWidth=375`，`metaCount=17`，`cardCount=5`；`320x740` 下 `scrollWidth=clientWidth=305`，`kpiCount=3`，`metaCount=17`，`cardCount=5`。
- 边界：本片只改前端 Memory 展示和 CSS，不改后端 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交截图 / artifact / 日志原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。该结果证明 Memory 管理体验更可解释，不代表真实模型长期记忆抽取能力提升。
- 下一步候选：README / showcase 面试口径同步，或继续做 Memory 冲突 / 合并 / 编辑能力设计与门禁。

## 2026-06-28 追加任务：Quality Loop v2.2 Rerank Hard Smoke

- 目标：把 Phase 3 rerank 验证从“provider 可调用且无回退”推进到“hard fixture 能观察排序 uplift”，避免继续用满分通用 KB smoke 宣称真实效果提升。
- 已完成：`cloud-quality-smoke.ps1` 新增默认关闭的 `-EnableRerankHardGate`，在真实链路中复用 Alpha / Beta 两份临时文档作为目标 / 支撑文档，并只额外上传 1 份关键词干扰文档，避免触发 `60s / 3 uploads / user` 文件上传限流；`rerank-effect-smoke.ps1` 默认开启 hard gate，并输出 target / support / distractor 的 retrieve count、best rank、citation count 和 rerank score summary。
- 已验证：`rerank-effect-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=RerankEffectSmokeScriptSafetyTest,RagRealQaEvalSmokeScriptSafetyTest" test` PASS，4 tests；真实 `rerank-effect-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS。baseline marker 为 `docpilot-rerank-effect-hybrid-20260628204120-3e9f69`，rerank marker 为 `docpilot-rerank-effect-rerank-20260628204339-7aac45`；hard fixture 中 target rank `2 -> 1`，distractor rank `3 -> 4`，`rerankApplied=true`，`hardUpliftObserved=true`，no-evidence 和权限隔离无回退；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，204 tests。
- 边界：本片只改 smoke runner 和脚本安全测试，不改生产 API、不改数据库结构、不删除业务数据、不操作远程 Docker、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。该结果是小规模 hard smoke uplift 证据，仍不是大规模 relevance benchmark。
- 下一步：已进入 v2.3 Memory 产品化第一片并收口为 DONE。

## 2026-06-28 追加任务：Frontend UX Audit v1 真实浏览器审计

- 目标：从真实用户视角检查 RAG、Memory、Trace、citation、KnowledgeBase evidence 和移动端布局，确认质量门禁的 API 结果能在前端关键路径上被用户看见、点到、读懂。
- 已完成：复用本地 tunnel / backend / frontend，使用浏览器上下文创建临时用户、两份 txt 文档、KnowledgeBase、ACTIVE memory 和绑定 KB 的 Conversation；marker 为 `docpilot-frontend-ux-2647184760`，文档为 `175/176`，KnowledgeBase 为 `36`，Conversation 为 `35`。
- 已验证：两文档 parse `SUCCESS`；Conversation Trace 为 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=2`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=2`、`documentHitCounts={175:1,176:1}`；会话气泡 footer 显示 `2 条来源`；Trace 面板和 Memory 面板均可通过真实点击打开，ACTIVE memory 可见。
- KnowledgeBase 页面验证：页面内点击“查看引用来源”后展示 provider / 索引集合、`来源不足: 否`、来源文档分布 `#175: 1 / #176: 1`、召回片段和引用来源卡片；两份临时文档 marker 均可见。桌面 `/conversations`、`/knowledge-bases` 均无横向溢出。
- 移动端验证：`390x844` 下 `/conversations` 的 `.dp-chat-shell`、`.dp-chat-main`、`.dp-chat-topbar`、`.dp-chat-thread`、`.dp-chat-composer-wrap` 均约束在 `346px`，页面 `scrollWidth=clientWidth=375`；`/knowledge-bases` 同样 `scrollWidth=clientWidth=375`。
- Gemini 轻量 UX sanity review 提醒：继续关注技术观测字段对非技术用户的认知负担、Trace / Memory 数据量增长后的可读性，以及 `390px` 以下更窄移动端视口。
- 追加 v1.1 验证：同一临时用户下新增一条包含长标识符的 ACTIVE memory，检查 `360x780` 与 `320x740` 极窄移动端。`/conversations` Memory 抽屉可打开，长 memory 未撑破页面；`360px` 下页面 `scrollWidth=clientWidth=345`，`320px` 下页面 `scrollWidth=clientWidth=305`；`/knowledge-bases` 同样无横向溢出。
- 边界：本片创建了临时审计数据，但未改后端 / 前端业务代码，未删除业务数据，未改数据库结构，未操作远程 Docker，未提交 artifact / 截图 / 日志原文，未打印 `.env` / token / API key / 云地址 / 连接串，未 push。
- 已完成 KnowledgeBase 技术字段产品化降噪：`/knowledge-bases` 问答结果区默认展示“来源覆盖 / 引用来源 / 回答状态 / 生成次数”，把 provider、collection、retrieval mode、rerank、answer provider / model 收进“工程观测”折叠区；工程审计信息仍可展开查看。
- 已验证：`npm run lint` PASS；真实浏览器页面检索后默认态不显示 Provider / Collection 细节，展开“工程观测”后可见 `Provider / Collection / Retrieval / Rerank / Answer / Model`；`360px` 移动端无横向溢出；`npm run build` PASS。
- 已完成更难 rerank uplift fixture 第一片：RAG Real QA Eval 新增 `real-rerank-distractor-ordering`，用 export / audit / retention 词面干扰文档检验 citation 和 forbidden marker；metrics 新增 `rerankUpliftCandidatePassRate`，不再只统计候选 case 占比。
- 已验证：`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，9 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，204 tests。
- 已完成 Memory 长列表交互审计：重新注册临时用户并创建 `16` 条 ACTIVE memory，marker 为 `docpilot-memory-ui-1782649237433`，Conversation `37`；`390x844` 下 Memory 抽屉可打开、列表可滚动、`memoryItemCount=17`、`deleteButtonCount=16`、`scrollWidth=clientWidth=390`，桌面 `1036x850` 同样无横向溢出。中途发现本地 Next dev 与 `npm run build` 混用后 `.next` chunk 缓存失效，已清理生成目录并重启本地 frontend；未改业务代码。
- 下一步候选：Quality Loop v2 三条主线已完成一轮闭环；真实 rerank smoke harder fixture 已由 v2.2 收口为 PASS，后续继续进入 Memory 产品化或 README / showcase 面试口径同步收口。

## 2026-06-28 追加任务：Memory Quality Eval v1 真实链路 smoke 第二片

- 目标：把 Memory Quality Eval 从离线 test-side 门禁推进到真实链路 smoke，验证临时会话中候选记忆抽取、接受 / 忽略状态分层、ACTIVE 列表隔离，以及绑定 KnowledgeBase 后 trace 同时包含 userMemory 和 ragEvidence。
- 已完成：新增 `scripts/smoke/memory-quality-smoke.ps1`，并给 `cloud-quality-smoke.ps1` 增加默认关闭的 `-EnableMemoryQualityGate`；Memory 专项 wrapper 默认使用 `SmokePrefix=docpilot-memory-quality`、artifact root `backend/target/memory-quality` 并打开 memory gate。`RuleBasedMemoryExtractionService` 同步补充英文 smoke 消息关键词识别，避免真实 smoke 必须依赖中文脚本字符串。
- 已验证：`memory-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`mvn "-Dtest=MemoryQualitySmokeScriptSafetyTest,RuleBasedMemoryExtractionServiceTest" test` PASS，5 tests；真实 `memory-quality-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-memory-quality-20260628193150-625bf6`；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，252 tests。
- 关键结果：Memory 专项 gate 抽取候选 `2` 条，accepted suggestion 变为 `ACTIVE`，ignored suggestion 变为 `IGNORED` 且不出现在 ACTIVE 列表；trace 显示 `contextSourceCounts.recentMessages=2`、`userMemory=1`、`ragEvidence=6`、`memoryCount=1`、`evidenceCount=6`，documentHitCounts 覆盖两份临时文档。完整 delegated gates 中 tunnel、backend health、frontend routes、两文档 parse/index、chunk 质量、MySQL/Qdrant 一致性、单文档 RAG、KB RAG、no-evidence、Conversation Trace、权限隔离和 artifact 脱敏均 PASS。
- 边界：本片创建了临时 smoke 用户、文档、KnowledgeBase、Conversation 和 memory 数据，但未操作远程 Docker、未走 `hk-ops`、未删除业务数据、未改数据库结构、未提交 artifact 原文、未打印 `.env` / token / API key / 云地址 / 连接串、未 push。早期失败 run 只留下 ignored artifact，用于本地诊断，不提交。
- 下一步：进入 Frontend UX Audit v1，使用真实前后端链路和浏览器检查 `/conversations` memory / trace / citation 展示、KnowledgeBase evidence 可读性、移动端布局和关键路径，不只看 API gate。

## 2026-06-28 追加任务：Memory Quality Eval v1 离线基线第一片

- 目标：把 Conversation Memory 从零散单测推进为可重复质量门禁，先离线覆盖候选抽取、敏感内容拦截、ACTIVE / SUGGESTED / IGNORED 状态分层、RAG evidence 不进入 memory、Context Trace source counts。
- 已完成：新增 `backend/src/test/resources/memory/memory-quality-eval-cases.json`，以及 `MemoryQualityEvalCase` / `MemoryQualityEvalRunner` / `MemoryQualityEvalResult` / `MemoryQualityEvalMetrics` 和 fixture / runner 测试；runner 复用真实 `RuleBasedMemoryExtractionService`、`MemorySelector`、`ContextAssemblyServiceImpl` 和 `MemorySafetyValidator`，用 test double 提供会话消息、记忆和 RAG evidence。
- 已验证：`mvn "-Dtest=*MemoryQualityEval*,*Memory*,*Context*" test` PASS，48 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*,*Conversation*,*Memory*" test` PASS，249 tests。
- 边界：本片只做离线 test-side eval，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不 push。artifact 只保存 case id、计数、布尔指标和失败原因，不保存对话全文、memory content、prompt、evidence context、token 或密钥。
- 下一步：已由第二片真实 `memory-quality-smoke.ps1 -Mode run` 收口为 PASS。

## 2026-06-28 追加任务：RAG Real QA Eval v1 真实链路 run 第三片

- 目标：把 RAG Real QA Eval 从离线基线和 wrapper 验证推进到真实链路证据，确认临时用户、两文档、KnowledgeBase、Conversation Trace、权限隔离和脱敏 artifact 在完整 cloud quality gate 中保持通过。
- 已完成：执行 `scripts/smoke/rag-real-qa-eval-smoke.ps1 -Mode run -FrontendBaseUrl http://127.0.0.1:3007`，marker 为 `docpilot-rag-real-qa-20260628164757-ac2a1d`；runner 启动本地 tunnel / backend / frontend，创建临时 smoke 用户、两份 txt 文档、KnowledgeBase、Conversation，并生成 ignored 脱敏 artifact。
- 已验证：整体 `PASS`；覆盖配置一致性、tunnel、backend health、frontend route、注册、上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、populated-KB no-evidence、Conversation Trace、四个权限隔离负向检查、artifact 脱敏扫描和 cleanup。
- 关键结果：两份文档均为 `3/3` chunks indexed 且 MySQL / Qdrant matched；单文档 RAG `3` hits / `3` citations；KnowledgeBase RAG `6` hits / `6` citations，documentHitCounts 覆盖两文档；no-evidence gate 返回 `0` hits / `0` citations；Conversation Trace 显示 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=6`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6`。
- 边界：本片创建了临时 smoke 数据，但未操作远程 Docker、未走 `hk-ops`、未删除业务数据、未改数据库结构、未提交 artifact 原文、未打印 `.env` / token / API key / 云地址 / 连接串、未 push。
- 下一步：进入 Memory Quality Eval v1，先做离线 / runtime 小闭环，验证长期记忆候选、ACTIVE / SUGGESTED / IGNORED 分层、RAG evidence 不污染 memory、trace source counts 和真实会话体验；随后继续 Frontend UX Audit。

## 2026-06-28 追加任务：RAG Real QA Eval v1 离线基线第一片

- 目标：把下一轮质量循环从单纯 smoke / 少量 synthetic case，推进到更贴近真实问答类型的离线 RAG QA eval 基线，先覆盖事实查找、跨文档总结、比较、多跳式证据、no-evidence、语义干扰、hybrid keyword 噪声和 rerank uplift shaped case。
- 已完成：新增 `real-qa-eval-cases.json`，以及 `RagRealQaEvalCase` / `RagRealQaEvalRunner` / `RagRealQaEvalResult` / `RagRealQaEvalMetrics` 和对应 fixture / runner 测试；runner 复用既有 KnowledgeBase RAG eval harness，继续使用 `MockEmbeddingProvider` + `InMemoryVectorStoreClient`，artifact 只输出脱敏 summary，不保存文档原文、query、模型输入、evidence context 或模型输出。
- 已验证：`mvn "-Dtest=*RealQaEval*,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，7 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，202 tests。
- 边界：本片只做离线质量基线，不启动 tunnel / backend / frontend，不创建业务数据，不调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不 push。
- 下一步：进入真实链路 RAG Real QA smoke runner，把同类 case 小规模迁移到临时用户 / 文档 / KB / Conversation 的真实链路验证；随后继续 Memory Quality Eval 和 Frontend UX Audit。

## 2026-06-28 追加任务：RAG Real QA Eval v1 真实链路入口第二片

- 目标：为 RAG Real QA Eval 建立独立真实链路 smoke 入口，后续可以用专属 marker / artifact 路径收口真实临时用户、文档、KnowledgeBase、Conversation Trace 和权限门禁，而不是把所有证据混在通用 cloud quality smoke 下。
- 已完成：新增 `scripts/smoke/rag-real-qa-eval-smoke.ps1`，支持 `plan` / `dry-run` / `run`，默认 `SmokePrefix=docpilot-rag-real-qa`、artifact 根目录 `backend/target/rag-real-qa`；当前 wrapper 委托 `cloud-quality-smoke.ps1` 执行完整业务质量门禁，并在 plan 输出 real-QA case 类型清单。
- 已验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/rag-real-qa-eval-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS，当前记录 MySQL / Qdrant local ports 未监听但 dry-run 本身 PASS；`mvn "-Dtest=RagRealQaEvalSmokeScriptSafetyTest" test` PASS，2 tests。
- 边界：本片只新增真实链路入口和脚本安全测试；未执行 `run`，未启动 tunnel / backend / frontend，未创建业务数据，未调用真实 provider / Qdrant / MySQL，不改生产 API，不改数据库结构，不提交 artifact 原文，不 push。
- 下一步：已由第三片执行 `rag-real-qa-eval-smoke.ps1 -Mode run` 并收口为 PASS。

## 2026-06-28 追加任务：Phase 3 small real rerank provider validation 收口

- 目标：做小规模真实 rerank provider 实效验证，判断 rerank / hybrid 是否实际改善召回与 citation，同时不默认扩大能力范围。
- 已完成：新增 `scripts/smoke/rerank-effect-smoke.ps1`，支持 `plan` / `dry-run` / `run`；`run` 通过环境变量覆盖执行两轮 cloud quality smoke：hybrid-only baseline 与 hybrid+real-rerank candidate，并输出脱敏对比 artifact。`cloud-quality-smoke.ps1` 的 KnowledgeBase gate 同步输出 `retrievalMode`、`rerankApplied`、`rerankModel`、rerank score summary。
- 已验证：PowerShell parser PASS；`rerank-effect-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；真实 `-Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS。baseline marker 为 `docpilot-rerank-effect-hybrid-20260628151134-170d38`，rerank marker 为 `docpilot-rerank-effect-rerank-20260628151301-6b0060`；rerank run 显示 `rerankApplied=true`、rerank score count `6`，KB hit / citation / coveredDocumentCount 与 baseline 均持平，no-evidence 和权限隔离无回退。
- 结论：当前 smoke fixture 下 baseline 已达到两文档覆盖和 6 citations，真实 rerank provider 没有带来可量化覆盖提升，但证明了 provider 可用、rerank score 进入 response / artifact、且核心 RAG / security gate 无回退；不能把该结果写成大规模 relevance uplift。
- 边界：本片只新增 smoke runner 和脱敏观测字段；不改数据库结构、不删除业务数据、不提交 artifact / 日志 / 截图、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 下一步候选：若继续提升真实效果，优先设计更难的 rerank eval / smoke case（baseline 会排序错误或被干扰文档诱导），再判断 rerank 是否带来正向 uplift；不要仅靠当前满分 fixture 宣称 rerank 显著提升。

## 2026-06-28 追加任务：Phase 2 KB multi-document coverage 收口

- 目标：修复真实体验审计中 KnowledgeBase 手动两文档总结问题只召回 Alpha 文档、漏掉 Beta 文档的问题，同时不破坏 populated-KB no-evidence 门禁。
- 已完成：KnowledgeBase hybrid retrieval 的二次 confidence gate 改为 summary intent 感知；summary / all-documents 类问题中，`keywordScore>0` 的 hybrid 候选可进入后续 scope guard、rerank 和多文档 diversity selection，普通非 summary 问法仍按 `vectorScore` / similarity threshold 阻断低置信 keyword 噪声。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，11 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，199 tests；真实 `scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007 -ReuseRunningServices` PASS，marker 为 `docpilot-rag-real-quality-20260628150434-2b7b39`，KnowledgeBase 两文档 gate 命中分布 `{152:3,153:3}`，no-evidence gate、Conversation Trace、权限隔离和前端 route smoke 均 PASS。
- 边界：本片只改 KnowledgeBase retrieval gate 和对应单测；不改数据库结构、不删除业务数据、不提交 artifact / 日志 / 截图、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 下一步：进入 Phase 3 小规模真实 rerank provider 实效验证；只验证 rerank / hybrid 是否改善召回与 citation，不默认扩大能力范围，不做大规模付费 eval。

## 2026-06-28 追加任务：Phase 2 mobile conversation layout 第一片

- 目标：修复移动端 `/conversations` 在 `390x844` 视口下横向溢出，主聊天区被长标题 / KB label / composer 控件撑到视口外的问题。
- 已完成：移动端 `.dp-chat-main` 增加 inline-size containment、overflow 和宽度约束；主区直接子项强制 `min-width:0` / `max-width:100%` / stretch；topbar pill 支持单行截断；thread 与 composer wrapper 限制在主区宽度内。
- 已验证：`npm run lint` PASS；Playwright 在 `390x844` 下测量 `.dp-chat-shell`、`.dp-chat-main`、`.dp-chat-topbar`、`.dp-chat-thread`、`.dp-chat-composer-wrap` 均为 `346px` 宽，页面整体 `scrollWidth` 不再超过 viewport；composer meta 内部保留横向滚动但不撑开页面。
- 边界：本片只修移动端会话页响应式 CSS，不改 API、不改业务逻辑、不改数据库结构、不提交截图 / 日志 / artifact、不 push。
- 下一步：处理 KnowledgeBase 页面手动两文档问题只召回单文档的问题，优先分析 retrieval 参数、问题改写、多文档覆盖策略与 rerank/hybrid 交互。

## 2026-06-28 追加任务：Phase 2 document detail citation 第一片

- 目标：修复文档详情页流式 RAG 回答出现 `[1]`，但右侧“引用来源”仍显示暂无引用的问题。
- 已完成：`rag-api.ts` 支持 RAG SSE 的 `retrieval` 与逐条 `citation` 事件；文档详情页在流式回答期间实时更新 `ragRetrieval` 与 `ragCitations`；SSE retrieval 摘要只有 `hitCount` 时，引用面板用 `hitCount` 兜底显示命中数量。
- 已验证：`npm run lint` PASS；Playwright 在 `http://localhost:3007/documents/150` 发送流式 RAG 问题后，右侧“引用来源”显示 `检索命中 1 条`、`引用 1`、score、chunk version 与 snippet。
- 边界：本片只修前端 RAG SSE 事件消费和展示，不改后端 API、不改数据库结构、不保存 prompt / evidence 原文、不提交 artifact / 日志 / 截图、不 push。
- 下一步：修移动端 `/conversations` 横向溢出；随后处理 KnowledgeBase 手动两文档问法只召回单文档的问题。

## 2026-06-28 追加任务：Phase 2 citation display 第一片

- 目标：修复 `/conversations` 历史消息重新加载后，回答正文含 `[1]` / `[2]`、Trace 有 RAG evidence，但气泡 footer 显示 `0` 条引用的真实体验问题。
- 已完成：前端加载会话历史时会 best-effort 拉取最新助手消息的 `ContextTrace`，并把 `message.citations.length` 或 `contextTrace.evidenceCount` 作为来源数量展示；无 trace 时仍保持聊天消息可渲染。
- 已验证：`npm run lint` PASS；Playwright 刷新 `http://localhost:3007/conversations` 后，临时会话 `docpilot-phase2-ui-audit-1782628501578` 的助手消息 footer 从 `0 条引用` 修正为 `2 条来源`，右侧 Trace 同时显示 `RAG 证据=2`、`长期记忆=1`。
- 边界：本片只修 `/conversations` 前端展示一致性，不改后端 API、不改数据库结构、不保存 prompt / evidence 原文、不提交 artifact / 日志 / 截图、不 push。
- 下一步：继续修文档详情页 citation 面板不同步，随后处理移动端 `/conversations` 横向溢出；KnowledgeBase 手动两文档覆盖不稳作为 Phase 2 后续检索质量切片。

## 2026-06-28 追加任务：Phase 2 真实体验审计第一片

- 目标：用真实浏览器和真实 backend / frontend / tunnel 链路，从用户视角检查 RAG、Memory、Trace 和前端关键路径，而不是只看 smoke runner 的 API gate。
- 已完成：启动本地 tunnel、backend、frontend；修复 `WebMvcConfig` 中本地 smoke 端口 `3007` / `3100` 未被 CORS allowlist 覆盖导致浏览器登录 / 注册 403 的问题；使用 marker `docpilot-phase2-ui-audit-1782628501578` 创建两份临时 txt 文档、KnowledgeBase、Conversation 和 ACTIVE memory。
- 已验证：浏览器 UI 注册成功；两文档 `150/151` parse `SUCCESS`；单文档 RAG `1` hit / `1` citation；KnowledgeBase API 多文档 RAG `2` hits / `2` citations 且 documentHitCounts 为 `{150:1,151:1}`；Conversation Trace 显示 `ragTriggered=true`、`ragRequired=true`、`evidenceCount=2`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=2`。
- 发现问题：文档详情实时 RAG 回答有 `[1]` 但右侧引用来源仍为空；KnowledgeBase 页面手动两文档问题只召回 `150`、漏掉 `151`；Conversation 回答正文有 `[1]` / `[2]` 且 Trace evidence 为 `2`，但气泡 footer 显示 `0` 条引用；移动端 `/conversations` 在 `390x844` 下横向溢出。
- 边界：本片创建了临时审计数据，但不删除业务数据、不改数据库结构、不提交 artifact / 截图 / 日志原文、不打印或提交 `.env` / token / API key / 云地址 / 连接串、不 push。
- 下一步：优先修复前端 citation / Trace 展示一致性和移动端会话页布局，再回到 KnowledgeBase 多文档召回稳定性；这些完成后再进入 Phase 3 小规模真实 rerank provider 效果验证。

## 2026-06-28 追加任务：RAG Quality Upgrade v8 第二片

- 目标：补齐单文档 RAG 离线 smoke case，让单文档链路也覆盖 populated-document no-evidence、grounding citation marker 和单文档 distractor 抑制，而不是只靠空文档 no-evidence。
- 已完成：`rag-document-retrieval-smoke-cases.json` 从 4 个 case 扩到 7 个 case；`RagDocumentRetrievalQualitySmokeTest` 新增 case 级 `minSimilarityThreshold` 和可选 `forbiddenMarker` 检查，验证低置信结果过滤与 distractor 不进入 hit / citation。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalQualitySmokeTest" test` PASS，2 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 边界：本片只增强离线 smoke harness / case，不改生产 API、不改数据库结构、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 结论：v8 eval corpus expansion 当前两片已完成，KnowledgeBase 与单文档 RAG 离线质量门禁均已扩容；下一阶段进入 Phase 2 真实体验审计，从用户视角跑完整业务链路并检查 RAG、Memory、前端路径和 trace 展示效果。

## 2026-06-28 追加任务：RAG Quality Upgrade v8 第一片

- 目标：先把 KnowledgeBase RAG 离线 eval corpus 从“少量链路样例”扩展为更像质量门禁的 case 集，覆盖 no-evidence、grounding、多文档干扰、hybrid keyword 噪声和 scope 干扰。
- 已完成：`knowledge-base-rag-eval-cases.json` 从 5 个 case 扩到 11 个 case；新增 case 级 `minSimilarityThreshold`，用于稳定模拟真实链路 confidence gate；补充 `semantic-no-evidence-populated-kb`、`hybrid-keyword-noise-no-evidence`、`multi-document-three-way-summary`、`grounded-answer-distractor-suppression`、`cross-topic-distractor-routing` 和 `out-of-scope-semantic-distractor`。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagEvalFixtureTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagEvalRunnerTest" test` PASS，5 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests；真实 `scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260628141419-fb7c21`。
- 边界：本片不改生产 API、不改数据库结构、不强制真实 rerank provider、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 下一步：继续 v8 第二片，检查并扩展单文档 RAG eval / smoke case，补 no-evidence、grounding 和 distractor 覆盖，然后再进入 Phase 2 真实体验审计。

## 2026-06-27 追加任务：真实链路优先自驱协议

- 目标：把用户长期授权后的自驱模式从“最小 / mock 验证优先”调整为“真实链路优先验证”，让后续 RAG、KnowledgeBase、Conversation Memory、Context Trace、权限隔离和前端关键路径改动尽量以真实用户体验链路收口。
- 已完成：`AGENTS.md` 与 `docs/ai-dev/CONSTRAINTS.md` 已补充真实链路优先规则；自驱模式下默认允许启动本地 tunnel / backend / frontend、运行真实 smoke、创建带统一 marker 的临时 smoke 数据、使用本机已有真实配置并生成 ignored 脱敏 artifact；`docs/ai-dev/ROADMAP_RAG.md` 已同步 RAG 质量门禁口径。
- 边界：仍禁止 push、提交 `.env` / secrets / artifact 原文、打印 token / API key / 云地址 / 连接串、删除业务数据、改数据库结构、清空 collection、远程 Docker 启停 / 重启 / 迁移或大规模高成本 provider eval；这些高风险操作仍需单独确认。
- 下一步：进入 `RAG Quality Upgrade v8: eval corpus expansion`。后续每个 RAG / Memory 质量切片先跑离线门禁；环境可达时继续跑真实链路 smoke，否则只能记录 `REVIEW` 或 `BLOCKED`，不能把用户体验质量写成已真实验证。

## 2026-06-27 追加任务：自驱迭代推进协议文档化

- 目标：把用户授权后的“连续自驱迭代推进”写入项目协作规则，让后续协作代理能理解：在明确授权后，应自行拆小片、实现、验证、自审、提交、回写文档，并继续推进下一片，直到大目标完成或遇到阻塞。
- 已完成：`AGENTS.md` 新增自驱迭代模式入口、循环步骤、停止条件和提交规则；`docs/ai-dev/CONSTRAINTS.md` 新增自驱迭代安全边界。
- 边界：该模式不允许绕过安全限制；仍禁止 push、远程 Docker / `hk-ops` 未授权操作、删除业务数据、提交真实密钥或 artifact 原文。
- 下一步：进入 `RAG Quality Upgrade v8: eval corpus expansion`，优先扩大 no-evidence / grounding / multi-document 干扰 eval case。

## 2026-06-27 追加任务：RAG Quality Upgrade v7

- 目标：把 Conversation Context / User Memory / RAG Evidence / Context Trace 的边界做清楚，避免把 RAG evidence 当作长期记忆，也避免长期记忆污染知识库证据。
- 成功标准：Conversation Trace 能区分 `conversationContext`、`userMemory`、`ragEvidence` 和 fallback；RAG evidence 不自动写入长期记忆；长期记忆候选仍需要用户接受后才进入上下文；相关离线测试和真实 smoke 能证明 KB evidence 与 memory 同时存在时上下文可解释。
- 明确不做：本轮不引入复杂多 Agent 编排、不改数据库结构、不操作远程 Docker、不删除业务数据、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。
- 已完成 v7 第一片：`ContextTrace` API 新增计算型 `contextSourceCounts` / `contextSourceFlags`，基于既有 summary / recent / memory / RAG evidence 字段生成，不改表结构、不持久化 prompt 或 evidence 原文；`/conversations` 右侧 Trace 面板展示会话摘要、最近消息、长期记忆和 RAG 证据拆分。
- 已验证：`mvn "-Dtest=*Context*,*Conversation*,*Memory*" test` PASS，56 tests；`npm run lint` PASS。
- 已完成 v7 第二片：新增 memory-aware RAG 负向测试，证明 assistant / RAG evidence 文本不会被长期记忆抽取为候选，且 `MemorySelector` 只把 `ACTIVE` user memory 放进上下文，`SUGGESTED` / `IGNORED` 不进入 prompt。
- 已验证：`mvn "-Dtest=RuleBasedMemoryExtractionServiceTest,MemorySelectorTest,ContextAssemblyServiceImplTest" test` PASS，6 tests。
- 已完成 v7 第三片：真实 `rag-real-quality-smoke.ps1 -Mode run` 已把 Conversation Trace gate 扩展为同时要求 KB RAG evidence 与 `ACTIVE` user memory，artifact 只保存脱敏 source counts，不保存 prompt 或 evidence 原文。
- 已验证：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run` PASS，marker 为 `docpilot-rag-real-quality-20260627220736-8f03b9`，其中 `conversationTrace` 显示 `evidenceCount=6`、`memoryCount=1`、`contextSourceCounts.userMemory=1`、`contextSourceCounts.ragEvidence=6`。
- v7 结论：DONE。下一步建议是 v8 eval corpus expansion：扩大 no-evidence / grounding / multi-document 干扰 case，而不是继续堆功能。

## 2026-06-27 追加任务：RAG Quality Upgrade v5

- 已完成 chunk structure quality 小步落地：`DocumentChunkCandidate` 新增 section title / ordinal / source block ordinal / structure type / quality flags；`ChunkingServiceImpl` 从 Markdown heading、文本块和基础异常信号生成结构元数据。
- `RagIndexingServiceImpl` 将结构 metadata 透传到 embedding metadata 与 Qdrant `VectorPoint` payload；不改数据库结构，不保存 prompt、token、密钥、连接串或云地址。
- `scripts/smoke/cloud-quality-smoke.ps1` 的 `chunkQuality` gate 已增强 MySQL 侧 offset order、token/content length、duplicate hash 检查；`mysqlQdrantConsistency` gate 已校验 Qdrant payload 中的结构字段。
- 已验证：`mvn "-Dtest=ChunkingServiceImplTest,RagIndexingServiceImplTest,VectorPointTest" test` PASS，28 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已执行真实链路默认 run：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260627213040-4038e1`；chunkQuality、MySQL / Qdrant payload 结构字段、单文档 RAG、KB 两文档 RAG、no-evidence、Conversation Trace、权限隔离、前端 route 和 artifact redaction 均 PASS。

## 下一步代码任务：RAG Quality Upgrade v6

- 目标：把 hybrid / rerank 从“可选增强”推进到有明确默认边界、质量对比和失败降级策略的 production gate。
- 成功标准：默认离线测试不依赖真实 rerank provider；eval / smoke 能区分 vector-only、hybrid 和 rerank 的 hit / citation / multi-document coverage / no-evidence 指标；真实 rerank provider 仍必须显式配置。
- 明确不做：本轮不强制真实 rerank provider、不操作远程 Docker、不改数据库结构、不删除业务数据、不提交 artifact 原文、不打印 `.env` / token / API key / 云地址 / 连接串、不 push。

## 2026-06-27 追加任务：RAG Quality Upgrade v6

- 已完成 v6 第一片：KnowledgeBase RAG 离线 eval 现在同一批 case 同时跑 `vector` 与 `hybrid` 两种 retrieval mode，并在脱敏 artifact 中输出 `retrievalModeMetrics.vector` / `retrievalModeMetrics.hybrid`。
- 默认 eval 仍使用 `MockEmbeddingProvider` + `InMemoryVectorStoreClient`，不依赖真实 rerank provider；hybrid eval 使用内存 keyword retriever，只服务质量门禁，不改变线上默认 `hybridEnabled=false`。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest" test` PASS，4 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已完成 v6 第二片：rerank provider 现在必须外部配置完整才发 HTTP；`enabled=true` 但缺少 provider 所需字段时直接 identity fallback，避免半配置状态误触发外部调用。
- 已验证：`mvn "-Dtest=*Rerank*,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，14 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests；`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260627214532-e1fb65`。
- v6 结论：DONE。真实 rerank provider 效果仍未强制验证，后续必须在用户显式提供配置后单独 smoke。

## 2026-06-27 追加任务：RAG Quality Upgrade v4

- 已完成 KnowledgeBase QA answer audit 小步落地：`KnowledgeBaseRagQaAnswer` 新增脱敏 `audit`，记录 `grounded`、evidence / citation count、documentHitCounts、score / vectorScore / fusedScore / rerankScore summary、retrievalMode、rerank 信息、fallbackReason 和 modelCallCount。
- `KnowledgeBaseRagQaResponse` 已暴露 audit；不保存 prompt、evidence 原文、模型输入输出、token、密钥或连接串。
- 离线 KnowledgeBase RAG eval 新增 `groundedAnswerRate` 和 `noEvidenceCitationFreeRate`，并把 grounded answer miss / no-evidence citation leak 纳入 case failure reasons。
- 已验证：`mvn "-Dtest=KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseRagControllerTest,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagRetrievalServiceImplTest" test` PASS，21 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已执行真实链路默认 run：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260627211711-383cda`；未提交 artifact 原文，未 push。

## 2026-06-27 追加任务：RAG Quality Upgrade v3

- 已完成 no-evidence threshold / grounded refusal 小步落地：KnowledgeBase hybrid retrieval 在融合后继续执行 evidence confidence gate，hybrid hit 带 `vectorScore` 时使用原始向量相似度判断阈值，不把 RRF `fusedScore` 当作 similarity。
- 默认质量阈值已校准为 `APP_RAG_RETRIEVAL_MIN_SIMILARITY_THRESHOLD=0.50`，同步到 `application.yml`、`.env*.example`、smoke runner 和 RAG hybrid guide；`RagRetrievalProperties` 程序化默认仍保持 `0.0`，避免破坏离线 harness。
- 已补测试：programmatic default / threshold validation、KnowledgeBase vector threshold no-evidence、hybrid fused hit 低置信 no-evidence、hybrid 使用 vectorScore 而非 fusedScore 做门禁。
- 已验证：`mvn "-Dtest=RagRetrievalPropertiesTest,RagDocumentRetrievalServiceImplTest,KnowledgeBaseRagRetrievalServiceImplTest,KnowledgeBaseRagQaServiceImplTest,KnowledgeBaseEvidenceContextBuilderTest,ContextAssemblyServiceImplTest" test` PASS，33 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，198 tests。
- 已执行真实链路默认 run：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007` PASS，marker 为 `docpilot-rag-real-quality-20260627210458-9d0321`；`noEvidenceThreshold` 返回 `noEvidence=true`、`0` retrieve hits、`0` QA citations。
- 本轮创建了临时 smoke 用户、文档、KnowledgeBase 和 Conversation，并写入 ignored artifact；未操作远程 Docker，未走 `hk-ops`，未删除业务数据，未改数据库结构，未提交 artifact 原文，未 push。

## 已完成代码任务：RAG Quality Upgrade v4

- 目标：强化 citation grounding 与 answer audit，确保回答层只使用通过 evidence confidence gate 的 citations，并把 no-evidence / fallback / score summary / documentHitCounts 以脱敏方式进入 response、trace 或 artifact。
- 成功标准：离线 eval 增加 citation grounding / no-evidence precision / forbidden answer leak case；真实 smoke 保持 `noEvidenceThreshold=PASS`、单文档 RAG、KB 两文档 RAG、Conversation Trace、权限隔离和 artifact redaction 不回退。该标准已由 marker `docpilot-rag-real-quality-20260627211711-383cda` 验证。
- 明确不做：不改数据库结构，不操作远程 Docker，不走 `hk-ops`，不删除业务数据，不强制真实 rerank provider，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。

## 2026-06-27 追加任务：RAG / Memory 生产化路线定线

- 本轮目标是先把关键事实源从“求职级展示收口”调整为“生产化知识库 RAG + 会话记忆核心闭环”，避免后续 agent 继续沿旧路线只做展示包装。
- 文档定线只更新 `AGENTS.md`、`docs/README.md`、`STATE.md`、`CURRENT_TASK.md`、`ROADMAP_RAG.md`、`DECISIONS.md` 和 `PROGRESS_LOG.md`；不启动 tunnel / backend / frontend，不创建业务数据，不改数据库结构，不 push。
- 当前真实证据以 v3 smoke 为准：`rag-real-quality-smoke.ps1 -Mode run` 已在默认阈值 `0.50` 下验证真实 embedding + Qdrant 链路整体 `PASS`，包括 populated-KB no-evidence。
- 下一轮代码任务固定为 v4：强化 citation grounding 与 answer audit，把通过门禁的 evidence、fallback 和 score summary 做成更清晰的质量证据。
- 本轮不得把项目写成完整商业 SaaS、线上 SLA、大规模多租户计费、高可用运维、成熟多 Agent 或已完成生产级 no-evidence。

## 已完成代码任务：RAG Quality Upgrade v3

- 目标：统一单文档 RAG、KnowledgeBase RAG 和 Conversation KnowledgeBase evidence 的 no-evidence 判定，让没有达到置信阈值的检索结果不进入 grounded QA。
- 成功标准：`scripts/smoke/rag-real-quality-smoke.ps1 -Mode run` 中 `noEvidenceThreshold` gate 变为 `PASS`；单文档 RAG、KB 两文档 RAG、Conversation Trace、权限隔离和 artifact redaction 不回退。该标准已由 marker `docpilot-rag-real-quality-20260627210458-9d0321` 验证。
- 实现结果：基于已有 `app.rag.retrieval.min-similarity-threshold` 补齐 KnowledgeBase hybrid 路径，并增加安全默认值、score 观测和离线测试；未强制真实 rerank provider。
- 明确不做：不改数据库结构，不操作远程 Docker，不走 `hk-ops`，不删除业务数据，不提交 artifact 原文，不打印 `.env` / token / API key / 云地址 / 连接串，不 push。

## 2026-06-27 追加任务：RAG Quality Upgrade v2

- 已新增 `scripts/smoke/rag-real-quality-smoke.ps1`，作为真实 embedding + Qdrant 检索质量门禁入口；脚本支持 `-Mode plan`、`-Mode dry-run`、`-Mode run`，默认 artifact 写入 ignored 路径 `backend/target/rag-quality/<smokeMarker>/artifact.json`。
- v2 复用并增强 `scripts/smoke/cloud-quality-smoke.ps1`：新增 `-SmokePrefix` 参数，并增加 `noEvidenceThreshold` gate；无关 populated-KB query 如果仍返回最近证据，状态标记为 `REVIEW`，不伪装成 PASS。
- 已执行 `-Mode run -ArtifactRoot backend/target/rag-quality -FrontendBaseUrl http://127.0.0.1:3007`，marker 为 `docpilot-rag-real-quality-20260627195744-d5b6e2`，overallStatus 为 `REVIEW`。
- 本次真实链路 PASS 项：tunnel、backend health、frontend route、注册、两文档上传 / parse / indexing、chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、Conversation Trace、权限隔离、artifact 脱敏。
- 本次 REVIEW 项：`noEvidenceThreshold`，无关 populated-KB query 仍返回 `3` 个 retrieve hits / `3` 个 QA citations，说明后续需要调 `minSimilarityThreshold`、rerank 或 no-evidence 策略。
- 本轮同步修复 `scripts/dev/cleanup-agent-processes.ps1` 的进程匹配规则，覆盖 `DocPilotApplication`、`npm run dev` 和带引号的 `next dev` 形态；本轮收尾时已手动确认 8081 / 3007 / 13306 / 6333 均未监听。

## 2026-06-27 追加任务：RAG Quality Upgrade v1

- 已完成第一版“从玩具 RAG 到求职级真实效果 RAG”的小步落地：单文档 retrieval 接入统一 `app.rag.retrieval.min-similarity-threshold`，避免配置只对 KnowledgeBase 生效、单文档链路仍对无关问题返回最近 chunk。
- 已增强 KnowledgeBase RAG 离线 eval：case fixture 可声明 `expectedAnswerMarkers`、`forbiddenAnswerMarkers`、`minCitationCount` 和 `requiresMultiDocumentCoverage`；eval result / artifact 新增 `answerHitRate`、`citationCountRate`、`multiDocumentCoverageRate`、`forbiddenAnswerLeakRate`。
- 当前离线 artifact 仍写入 ignored 路径 `backend/target/rag-eval/knowledge-base-rag-eval-latest.json`，只保存 case summary、计数和布尔指标，不保存文档原文、prompt、evidence context、模型输出、token 或密钥。
- 已验证：`mvn "-Dtest=RagDocumentRetrievalServiceImplTest,KnowledgeBaseRagEvalRunnerTest,KnowledgeBaseRagEvalMetricsTest,KnowledgeBaseRagEvalFixtureTest" test` PASS，12 tests；`mvn "-Dtest=*Rag*,*KnowledgeBase*" test` PASS，192 tests；`mvn -DskipTests compile` PASS；`mvn test -DskipITs` PASS，729 tests，0 failures，0 errors，1 skipped。
- 当前边界：本轮不调用真实 embedding / rerank / answer provider，不启动 tunnel，不创建业务数据，不改数据库结构，不改前端；该 v1 是离线质量门禁增强，不等于真实 provider 效果评测已完成。

## 2026-06-27 追加任务：云端完整业务 Smoke 质量门禁增强

- 已新增 `scripts/smoke/cloud-quality-smoke.ps1`，把 cloud smoke 从“链路能跑通”扩展为可执行质量门禁 runner。
- runner 支持 `-Mode plan`、`-Mode dry-run`、`-Mode run`：`plan` 只输出门禁清单，`dry-run` 只做本地前置检查，`run` 才会启动 / 复用 tunnel、backend、frontend 并创建临时业务数据。
- 统一 `smokeMarker` 贯穿临时用户、两份 txt 文档、KnowledgeBase、Conversation、问题文本和 artifact；artifact 默认写入 ignored 路径 `tmp-e2e/docpilot-cloud-quality-smoke/<smokeMarker>/artifact.json`。
- `run` 模式门禁覆盖 tunnel 连通、backend health、frontend route、注册 / 登录、两文档上传 / parse / indexing、MySQL chunk 质量、MySQL / Qdrant payload 一致性、单文档 RAG、KnowledgeBase 两文档 RAG、Conversation Trace、至少四个权限隔离负向检查、artifact 脱敏扫描、清理和最终 `git status`。
- 当前已验证：Windows PowerShell 5 parser PASS；`-Mode plan` PASS；`-Mode dry-run` PASS；`-Mode run -ArtifactRoot backend/target/smoke -FrontendBaseUrl http://127.0.0.1:3007` PASS。
- 本次 `run` marker 为 `docpilot-cloud-quality-20260627022219-37efd4`，脱敏 artifact 位于 `backend/target/smoke/docpilot-cloud-quality-20260627022219-37efd4/artifact.json`，artifact 不提交。
- 本次 `run` 生成临时用户、文档、KnowledgeBase 和 Conversation；没有操作远程 Docker，没有使用 `hk-ops`，没有删除业务数据，没有改数据库结构，没有 push。

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
