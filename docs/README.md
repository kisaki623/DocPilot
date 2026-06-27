# DocPilot 文档地图

本文件是 `docs` 目录的总入口。它的作用不是记录历史流水账，而是告诉新会话和开发者：现在应该先读什么、每类文档是干什么的、哪些文档只在追溯历史时才需要打开。

## 1. 当前推进优先级

现在项目推进的重点是 RAG Quality / Memory Quality 生产化升级：把核心路线从“可演示 RAG”推进到“可拒答、可引用、可追踪、可评测的知识库 RAG + 会话记忆闭环”。阅读顺序建议如下：

1. `docs/ai-dev/STATE.md`：先确认项目当前真实状态和边界。
2. `docs/ai-dev/CURRENT_TASK.md`：确认当前要做的任务。`RAG Quality Upgrade v7: memory-aware RAG` 已完成；下一步优先考虑扩大 no-evidence / grounding / multi-document 干扰 eval corpus。
3. `docs/showcase/DEMO_SMOKE_RECORD.md`：确认最近真实链路 smoke 证据。
4. `docs/ai-dev/ROADMAP_RAG.md`：确认 RAG 长期路线和任务拆分。
5. `docs/ai-dev/DECISIONS.md`：确认关键技术决策，例如 RAG 主链路自研、Qdrant、MySQL/Qdrant 分工。
6. `docs/ai-dev/CONSTRAINTS.md`：确认协作和安全约束。
7. `docs/ai-dev/PROGRESS_LOG.md`：只看最近简短进度，不追旧流水账。

一句话原则：当前任务看 `CURRENT_TASK.md`，项目事实看 `STATE.md`，展示证据看 `DEMO_SMOKE_RECORD.md`，RAG 总路线看 `ROADMAP_RAG.md`。

## 2. docs/ai-dev：当前开发事实源

这一组文档最重要，用于指导后续 Codex 会话和本地开发。

- `docs/ai-dev/STATE.md`：当前项目状态。写清楚已实现能力、默认关闭能力、RAG/Agent 边界。
- `docs/ai-dev/CURRENT_TASK.md`：当前任务卡。后续实现时优先按它推进，不被旧 TODO 带偏。
- `docs/ai-dev/ROADMAP_RAG.md`：RAG / Memory 生产化升级路线。包含当前真实基础、质量缺口、v3-v7 任务拆分和 smoke / eval 门禁；v3 no-evidence gate 已完成 smoke 级 PASS。
- `docs/ai-dev/DECISIONS.md`：ADR 简版。记录认证、异步解析、存储、AI 模式、RAG 选型等关键决策。
- `docs/ai-dev/CONSTRAINTS.md`：项目协作约束和安全边界。
- `docs/ai-dev/PROGRESS_LOG.md`：简短进度记录。不要把它写成新的大 changelog。
- `docs/ai-dev/会话级上下文管理/`：T013 Conversation Context / Agent Memory 的设计参考资料；只作为实现追溯和方案说明，不覆盖 `STATE.md` / `CURRENT_TASK.md` 的当前事实。
- `docs/ai-dev/archive/`：旧状态快照或归档文件。只有追溯历史时读取。
- `docs/ai-dev/benchmarks/`：eval / benchmark 产物和方法记录。用于验证或展示证据，不等同线上 SLA。

## 3. RAG 设计参考

这些文件记录过往 RAG 设计、选型和安全审查。它们是参考资料，不是当前任务源。

- `docs/rag/RAG_MINIMAL_DESIGN.md`：从轻量问答演进到最小 RAG 的早期设计。
- `docs/rag/VECTOR_STORE_SELECTION.md`：向量库选型比较，包含 Qdrant、Redis Vector、MySQL fallback、in-memory fake store。
- `docs/rag/RAG_VECTOR_STORE_ADAPTER_DESIGN.md`：Qdrant / vector store adapter 边界设计。
- `docs/rag/RAG_QDRANT_REVIEW_NOTES.md`：Qdrant adapter、preflight、trace、eval 的安全审查记录。

当前 RAG 事实以 `docs/ai-dev/STATE.md` 和 `docs/showcase/DEMO_SMOKE_RECORD.md` 为准；长期规划以 `docs/ai-dev/ROADMAP_RAG.md` 为准。旧 RAG 文档只在实现细节、边界解释或历史追溯时打开。

## 4. Agent 设计参考

这些文件记录 Agent、selector、trace、shadow mode、prompt engineering 等设计。

- `docs/agent/agent-upgrade-roadmap.md`：Agent 升级路线和历史规划，较大，不建议默认读取。
- `docs/agent/AGENT_ASYNC_DESIGN.md`：Agent 异步化设计参考。
- `docs/agent/AGENT_RAG_DEMO_CHECKLIST.md`：Agent + RAG showcase 演示检查清单。
- `docs/agent/AGENT_RAG_SHOWCASE_DEMO_SCRIPT.md`：Agent + RAG 展示脚本参考。
- `docs/agent/AGENT_SELECTOR_SHADOW_MODE.md`：selector shadow mode 设计。
- `docs/agent/AGENT_SELECTOR_*`：selector、Actuator、安全、观测、Prometheus 等细分设计。
- `docs/agent/PROMPT_ENGINEERING_NOTES.md`：工具选择 prompt、JSON 输出协议、parser、allowlist、fallback 等说明。
- `docs/agent/REAL_PROVIDER_SHADOW_PREFLIGHT.md`：真实 provider shadow-only 验证前置说明。

当前项目短期重点不是继续扩复杂 Agent，而是把 RAG retrieval、KnowledgeBase evidence、Conversation Memory 和 Context Trace 做成可信主链路；Agent 只作为工具调用和可观测性的辅助层。

## 5. 面试和求职材料

这些文件用于对外讲项目、写简历、准备面试。

- `docs/showcase/PROJECT_INTERVIEW_BRIEF.md`：项目面试总述，适合面试前快速复习。
- `docs/showcase/RESUME_BULLETS.md`：简历 bullet 候选写法。
- `docs/showcase/INTERVIEW_QA.md`：面试问答材料，偏长，按需读取。
- `docs/showcase/DEMO_SMOKE_RECORD.md`：真实链路 smoke 证据，包含单文档 / 多文档 RAG、MinIO、RocketMQ + Outbox、真实模型和真实 embedding + Qdrant。
- `docs/interview/`：分章节面试材料，覆盖项目概览、架构、数据库、API、异步解析、缓存、部署、测试等。
- `docs/showcase/PROJECT_ARCHITECTURE_OVERVIEW.md`：项目架构概览。
- `docs/showcase/DEMO_SCRIPT.md`：演示脚本参考。

对外材料要讲主线，不要提前夸大未完成能力。fake embedding、in-memory vector store、blocked runtime 等边界可以在内部文档中诚实记录，或在面试追问时说明。

## 6. 历史流水账和大文件

这些文件保留历史价值，但不建议新会话默认读取：

- `docs/archive/CHANGELOG_CODING.md`：长周期编码流水账，体量很大。
- `docs/archive/TODO_NEXT.md`：旧任务看板，很多任务已经过期或被当前路线替代。
- `docs/archive/CODEX_HANDOFF.md`：旧交接文档，适合追溯上下文，不适合默认通读。
- `docs/archive/CODEX_LOW_QUOTA_MODE.md`：低额度协作模式说明。
- `docs/archive/CODEX_TOOLING.md`：Codex 工具使用约定。

规则：只有需要追溯历史决策、旧任务来源、旧验证记录时才读这些文件。不要让旧 TODO 覆盖 `docs/ai-dev/CURRENT_TASK.md` 和 `docs/ai-dev/ROADMAP_RAG.md`。

根目录下的重复旧路径已经清理；后续请按本索引中的分类目录读取正文。

## 7. 截图和展示资源

- `docs/assets/screenshots/`：README、项目展示、求职展示用截图资源。

当前截图主要服务已有 showcase。RAG no-evidence、answer audit、chunk structure quality、v6 hybrid / rerank gate 和 v7 memory-aware trace 已有 smoke / eval 证据；等 eval corpus 和面试展示材料继续增强后，再补新的 RAG / Memory 截图会更有价值。

## 8. 使用规则

- 不删除历史文档；过时内容先标注，不粗暴删除。
- 不默认读取大文件；先读当前事实源，再按需追溯。
- 文档和代码冲突时，以代码、测试和可运行结果为准。
- 当前开发优先推进 `docs/ai-dev/CURRENT_TASK.md`。
- 长期 RAG 方向以 `docs/ai-dev/ROADMAP_RAG.md` 为准。
- 面试材料只讲已实现或能诚实解释的能力；可以讲“生产化 RAG 核心闭环建设”和 v3 smoke 级 no-evidence PASS，不能写线上 SLA、完整商业 SaaS、多智能体自主规划或大规模 relevance benchmark。
