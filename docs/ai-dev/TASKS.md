# DocPilot 滚动待办

本文件只保留仍有价值、可后续执行的滚动待办。当前正在执行的任务仍以 `docs/ai-dev/CURRENT_TASK.md` 为准；已完成事实写入 `docs/ai-dev/STATE.md` 和 `docs/ai-dev/PROGRESS_LOG.md`。

## 2026-07-12 High Intensity Acceptance Test TODO

来源：2026-07-12 用户提供的网页版 GPT 高强度验收建议。完整计划见 `docs/ai-dev/HIGH_INTENSITY_ACCEPTANCE_TEST_PLAN.md`；当前只记录为待执行测试，不代表已通过。

| 优先级 | 状态 | 范围 | 目标 | 执行入口 | 验证 / 回写 |
| --- | --- | --- | --- | --- | --- |
| P0/P1 | IN_PROGRESS / PARTIAL VERIFIED | 上传解析、RAG、GroundingPolicy、KnowledgeBase、Memory、Agent、权限安全、弱网并发 UI | 用固定语料、固定问题、固定预期结果完成一次 3-5 小时可复现验收，检查 UI、接口、Trace、数据库状态和 citation 来源。 | `docs/ai-dev/HIGH_INTENSITY_ACCEPTANCE_TEST_PLAN.md` | 第一层真实链路已 PASS：parser `docpilot-parser-real-chain-20260712212339-021ca3`、grounding `docpilot-conversation-grounding-20260712212500-d26151`、cloud quality `docpilot-cloud-quality-20260712212603-173e7d`。固定语料自动化 runner 已落地，`REA-20260712-P1-030` 与 `REA-20260713-P1-031` 均已修复验证；marker `docpilot-high-intensity-fixed-corpus-20260713004622-113df1` 的 `fixedBusinessCorpus` 与 `knowledgeBaseLifecycle` gate PASS，覆盖 T02、T06-T15、T22-T26。Conversation 最近轮次 gate marker `docpilot-conversation-grounding-20260713010452-f8e612` PASS，新增覆盖 T27 / T28。Memory gate marker `docpilot-memory-quality-20260713013642-34b1f5` 的 `memoryQuality` PASS，新增覆盖 T29 候选确认与 T30 敏感记忆拒绝。剩余 T31 删除 / 禁用记忆、T32 长会话摘要、Agent、弱网并发、多标签页和 UI 缩放仍待执行。 |

## 2026-07-12 Frontend UX TODO from Gemini CLI

来源：2026-07-12 Gemini CLI `gemini-3.5-flash` 脱敏前端体验协作建议。以下均为前端待办，不要求后端 schema、数据库迁移、远程服务操作或大规模重构；实现时继续保持页面文案克制，不把 `smoke`、线上 SLA 或求职口径放进 UI。

| 优先级 | 状态 | 页面 | 机会点 | 具体 UI / 交互改法 | 复杂度 | 验证方式 |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | TODO | `/knowledge-bases` | 召回片段与引用来源并排展示但缺少对应关系，用户难判断哪些片段被回答采纳。 | 增加召回片段与引用来源的双向 hover / focus 高亮；用 `documentId + chunkId/chunkIndex` 做前端临时匹配 key；召回卡显示“已采纳 / 仅召回”轻量 badge。 | S | `npm run lint`、`npm run build`；浏览器生成一次 KB 问答后检查 hover 高亮、badge 准确、移动端无横向溢出。 |
| P2 | TODO | `/conversations` | Context Inspector 的 Trace 指标丰富但偏静态，用户难一眼看出本轮上下文组装路径。 | 在“溯源” tab 增加视觉上下文流水线：最近对话 -> 摘要 -> 长期记忆 -> 知识库检索 -> 模型生成；节点按 `summaryUsed`、`memoryCount`、`ragTriggered`、`llmCalled` 激活；增加 token 预算进度条，截断时显著提示。 | M | `npm run lint`、`npm run build`；真实或 mock 会话发送后打开“上下文溯源”，检查节点状态、token 比例和 390px 布局。 |
| P3 | TODO | `/quality` | Run Detail 详情 tab 数量多，移动端可能占用过多纵向空间。 | 移动端把详情 tab 改成单行横向滚动并隐藏滚动条，增加边缘渐变提示；桌面端保持现状或按“全局透视 / 问题排查 / 特色领域”做轻量分组。 | S | `npm run lint`、`npm run build`；Playwright 打开 `/quality?autoload=1`，检查 390px / 320px 无横向页面溢出且 tab 可滑动。 |
| P4 | TODO | `/quality` | 趋势 mini points 只能看状态，无法快速跳回某个历史 run。 | 让趋势点可点击，点击后切换 `selectedMarker` 并加载对应 run detail；当前选中 marker 在 sparkline 中加 focus ring。 | S | `npm run lint`、`npm run build`；点击趋势点后确认详情 marker 同步变化，URL / 返回路径不混乱。 |
| P5 | TODO | `/quality` | RAG representative / Memory 趋势卡的“0 回归 / 0 泄漏”安全信号不够醒目。 | 当回归、泄漏、失败计数为 0 时使用克制的绿色状态点和“0 回归 / 0 泄漏”文案；避免写成 SLA。可在前端基于现有 metrics / flags 计算。 | S | `npm run lint`、`npm run build`；用现有 PASS artifact 检查趋势卡显示正向零风险信号。 |
| P6 | TODO | `/quality` | Failures / Triage 信息多，首要行动点不够突出。 | 在待处理区顶部增加“首要修复行动点”卡片，从当前 run 的 FAILED / REVIEW eval case 或 failure bucket 中取最高风险项，展示模块、caseId、建议动作和 Trace 入口；无待处理时显示空状态。 | M | `npm run lint`、`npm run build`；用含 REVIEW/FAILED 的历史 artifact 检查首要卡片、过滤和 Trace 链接。 |
| P7 | TODO | `/documents/[documentId]` | 引用来源已有 locator / metadata，但点击引用后无法快速定位正文上下文。 | 给引用卡片增加点击定位：优先用 quote/snippet 在正文容器内查找近似文本并平滑滚动；命中后短暂高亮目标段落；未命中时给出轻量提示，不阻断问答。 | M | `npm run lint`、`npm run build`；已解析文档提问后点击引用，检查正文滚动、高亮和未命中 fallback。 |
| P8 | TODO | `/dashboard`、首页工作空间入口 | 解析中任务的异步状态反馈偏静态，用户不易感知后台流水线正在推进。 | 对非终态解析状态卡片增加柔和 shimmer / spinner 状态；hover tooltip 展示上传、Outbox、解析、索引等生命周期说明，当前阶段高亮。 | S | `npm run lint`、`npm run build`；构造 PENDING / PARSING / INDEXING 状态数据后检查动效克制、无布局抖动。 |

### 首推切片

优先实现 `/knowledge-bases` 的“召回片段与引用来源双向高亮”。该切片纯前端、复杂度低、能直接提升 RAG 可解释性；不改变 API、schema、检索排序或回答生成。
