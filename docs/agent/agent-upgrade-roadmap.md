# DocPilot Agent 升级路线图

---

## 一、当前 Agent 模块现状

### 1.1 当前调用链路

```
前端 agent/page.tsx
  → agent-api.ts: runDocumentAgent()
    → POST /api/ai/agent/run  { documentId, task, sessionId? }
      → DocumentAgentController.run()
        → DocumentAgentServiceImpl.run(userId, request)
          │
          ├─ Step 1: DocumentStatusTool.execute()
          │     → DocumentService.getDetailById() → 获取 parseStatus / parseReady
          │
          ├─ [if !parseReady] → decision="status_only" → 返回"文档解析尚未完成"
          │
          ├─ [if status intent only] → decision="status_only" → 返回状态描述
          │
          ├─ [if summary intent] → Step 2: DocumentSummaryTool.execute()
          │     → 返回 summary（来自DB）或 内容截取前320字符
          │
          └─ [otherwise] → Step 2: DocumentQaTool.execute()
                → DocumentQaService.answer() → 走完整QA链路
```

**关键代码位置**：
- Controller: `backend/src/main/java/.../ai/agent/controller/DocumentAgentController.java`（第 30-44 行）
- Service: `backend/src/main/java/.../ai/agent/service/impl/DocumentAgentServiceImpl.java`（第 48-138 行）
- 前端: `frontend/app/agent/page.tsx` + `frontend/lib/agent-api.ts`

### 1.2 当前 Tool 有哪些

| Tool | 类 | 输入 | 输出 | 实际做什么 |
|------|-----|------|------|-----------|
| **DocumentStatusTool** | `agent/tool/DocumentStatusTool.java` | `StatusInput(userId, documentId)` | `StatusResult(parseStatus, parseReady, title, summary, content, parseStatusDescription)` | 调用 `DocumentService.getDetailById()` 查文档状态 |
| **DocumentSummaryTool** | `agent/tool/DocumentSummaryTool.java` | `SummaryInput(task, summary, content)` | `SummaryResult(output, source)` | 如果有 summary 字段则返回，否则截取 content 前 320 字符 |
| **DocumentQaTool** | `agent/tool/DocumentQaTool.java` | `QaInput(userId, documentId, task, sessionId)` | `DocumentQaResponse` (完整QA响应含answer+citations) | 直接 new QaInput → `DocumentQaService.answer()`，走完整 QA 链路 |

**Tool 接口**：`agent/tool/AgentTool.java` — 泛型接口 `AgentTool<I, O>`，定义了 `getToolName()` 和 `execute(I input)` 方法。

### 1.3 当前前端怎么调用

```typescript
// frontend/lib/agent-api.ts
export async function runDocumentAgent(request: {
  documentId: number;
  task: string;
  sessionId?: string;
}): Promise<AgentResponse>

// 请求: POST /api/ai/agent/run
// 响应: {
//   traceId, documentId, task, sessionId, decision, finalAnswer,
//   totalDurationMs, startedAt, finishedAt, success, citations,
//   steps: [{ stepIndex, toolName, inputSummary, outputSummary, durationMs, status }]
// }
```

前端 `agent/page.tsx` 展示：
- 3 个预设任务模板（文档摘要查询 / 状态+摘要查询 / 证据问答查询）
- 文档选择器（下拉从列表加载）
- 结果区域：decision、耗时、traceId、时间戳
- 工具步骤可折叠列表（每步显示 toolName、durationMs、输入输出摘要）
- 最终答案用 MarkdownViewer 渲染
- 引用片段展示

### 1.4 当前 Agent 和 DocumentQaServiceImpl 的关系

```
DocumentAgentServiceImpl
  └── DocumentQaTool.execute()
        └── DocumentQaService.answer()   ← 直接复用
              └── DocumentQaServiceImpl.answer()
                    ├── 限流检查 (RedisTokenBucketRateLimiter)
                    ├── 文档权限校验 (ensureOwnedDocument)
                    ├── 分块检索 + 关键词匹配 (splitIntoChunks / rankChunks)
                    ├── 会话上下文 (loadSessionTurns / appendSessionContext)
                    ├── AI 调用 (AiAnswerService → MockAiAnswerService 或 RealAiAnswerService)
                    ├── 问答缓存 (SHA-256 + Redis 180s TTL)
                    ├── 历史存储 (tb_qa_history)
                    └── 引文生成 (buildCitations / buildAnswerAwareCitations)
```

**关系本质**：Agent 是 DocumentQaService 的**薄封装层**，QaTool 不做任何新的检索或推理，只是把 userId/documentId/task 透传给已有的问答服务。SummaryTool 同理，直接从 DB 读取已有 summary 字段。StatusTool 调 DocumentService.getDetailById()。

**这意味着**：当前 Agent 没有独立的"思考链"，它只是对已有 API 的组合调用 + 简单 if-else 路由。

### 1.5 当前最大短板

1. **无 AgentTask 持久化** — 每次 Agent 执行后只有 HTTP 响应，没有数据库记录。无法查询"某文档执行过多少次 Agent""某次 Agent 执行了哪些工具"。
2. **无 AgentStep 执行轨迹** — 工具调用步骤只在内存中，响应返回后即丢弃。无法回溯"Step 2 QaTool 耗时为什么突然变长"。
3. **无失败定位能力** — Agent 执行中途失败时，只知道"整体失败了"，不知道失败在哪个 Step、输入是什么、异常是什么。
4. **路由逻辑脆弱** — 用 `containsAnyKeyword()` 做意图路由，依赖硬编码的中英文关键词列表，遇到新表达方式直接 fall through 到 QaTool。
5. **无法异步执行** — 整个 Agent 在 HTTP 请求线程内同步执行，QaTool 可能耗时数秒，前端只能干等。
6. **前端无进度追踪** — 前端只知道最终结果，不知道当前执行到第几步、每步进度如何。
7. **无执行回放** — 无法重放历史 Agent 调用的完整过程和结果。
8. **Tool 注册散落** — 3 个 Tool 是直接 `@Autowired` 注入到 `DocumentAgentServiceImpl` 的，新增 Tool 需要修改 ServiceImpl 代码。

---

## 二、为什么第一阶段必须先做 AgentTask / AgentStep

### 2.1 它解决什么工程问题

| 问题 | 如何解决 |
|------|---------|
| **执行不可追溯** | 每次 Agent 执行写入 `tb_agent_task`，生成唯一 traceId，记录启动时间、状态、决策、最终结果 |
| **步骤不可回溯** | 每次 Tool 调用写入 `tb_agent_step`，记录 toolName、输入参数摘要、输出结果摘要、耗时、状态、异常信息 |
| **失败不可定位** | Step 失败时精确记录 `error_message` + `status=FAILED`，AgentTask 同步标记失败，一眼看到"第几步/哪个工具/什么错误" |
| **无法统计和监控** | 有了表就可以查询"最近 100 次 Agent 执行的平均耗时""QaTool 的 P95 耗时""失败率" |
| **前端无状态** | 后续前端可以通过 `GET /api/ai/agent/task/{taskId}` 获取执行进度，不再只能干等 |

### 2.2 为什么比一上来接 LangChain4j / Spring AI / 向量库 / MCP 更优先

| 如果先做 | 后果 |
|---------|------|
| LangChain4j / Spring AI | 引入新的依赖和抽象层，但没有执行轨迹，出问题不知道是框架问题还是业务问题 |
| 向量库（pgvector / Milvus） | 检索质量提升了，但 Agent 失败时还是不知道哪一步出错 |
| MCP Server | 工具可以被外部调用了，但内部连调用记录都没有 |
| 多 Agent 编排 | 两个 Agent 协作，一个失败了，不知道是哪个的哪个 Step 出错 |

**核心原则**：Agent 的执行轨迹 = 关系型数据库的行 = 可查询、可统计、可告警。这是所有 Agent 框架（LangChain、LangGraph、CrewAI）都内置的 tracing 能力，只是它们封装在框架里。先自己实现一遍，理解了本质，再接框架时能判断框架的 tracing 是否满足需求。

### 2.3 它如何为后续高级能力打地基

```
AgentTask / AgentStep（P1）
  │
  ├── 为 P3 (LLM Tool Selection) 提供：每个 Step 的输入输出数据，用于训练/评估 Tool Selection 准确率
  │
  ├── 为 P5 (RocketMQ 异步 Agent) 提供：AgentTask 作为异步任务载体，Worker 消费后更新 Step 状态
  │
  ├── 为 P5 (SSE 进度推送) 提供：前端轮询 / SSE 订阅 AgentTask → AgentStep 状态变化
  │
  ├── 为 P6 (业务 Agent) 提供：合同审查/简历匹配等长任务需要持久化的执行记录
  │
  ├── 为 P7 (Spring AI / LangChain4j) 提供：评估框架 tracing 是否满足需求的标准
  │
  └── 为 P8 (MCP Server) 提供：外部调用内部工具时的执行审计记录
```

**一句话**：AgentTask / AgentStep 是 Agent 系统的"数据库事务日志"。没有它，后面的高级能力全是"黑盒"。

---

## 三、完整升级路线图（P0 - P8）

### P0：保存现场 + 敏感信息检查

**目标**：确保当前代码可安全提交，无敏感信息泄露

**为什么现在做**：当前工作区有大量未提交变更（20 个 modified + 多个 untracked），先整理干净，为后续开发建立干净的基线

**前置条件**：无

**需要改/检查的文件**：
- `.gitignore` — 确认 `backend/.env`、`backend/.env.local` 等已在忽略列表
- `backend/.env.demo.example` — 确认只有示例值，无真实密钥
- `application-local.yml` — 确认无硬编码的云服务真实 IP/密码

**预计难度**：极低

**面试价值**：低（但属于职业素养）

**不做会有什么问题**：可能意外提交云服务真实 IP、密码到公开仓库

**做过头会有什么风险**：无

---

### P1：AgentTask / AgentStep 执行轨迹

**目标**：
1. 新增 `tb_agent_task` 表（记录每次 Agent 执行）
2. 新增 `tb_agent_step` 表（记录每个 Tool 调用步骤）
3. `DocumentAgentServiceImpl.run()` 执行时写入 AgentTask + AgentStep
4. 成功/失败正确更新状态
5. 新增 `GET /api/ai/agent/task/{taskId}` 查询接口
6. 新增 `GET /api/ai/agent/tasks?documentId=xxx` 列表接口
7. 暂不接 MQ、不接 LLM Tool Selection、不接向量库、不改变前端

**为什么现在做**：见第二章

**前置条件**：P0 完成

**需要改/新增的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `sql/004_init_agent_tables.sql` | 新增 | DDL |
| `ai/agent/entity/AgentTask.java` | 新增 | 实体 |
| `ai/agent/entity/AgentStep.java` | 新增 | 实体 |
| `ai/agent/mapper/AgentTaskMapper.java` | 新增 | MyBatis Mapper |
| `ai/agent/mapper/AgentStepMapper.java` | 新增 | MyBatis Mapper |
| `ai/agent/vo/AgentTaskDetailResponse.java` | 新增 | 详情VO |
| `ai/agent/vo/AgentTaskListItemResponse.java` | 新增 | 列表项VO |
| `ai/agent/service/impl/DocumentAgentServiceImpl.java` | **修改** | 在 run() 中注入 Mapper，执行前后写入 task/step |
| `ai/agent/controller/DocumentAgentController.java` | **修改** | 新增 GET /task/{taskId} 和 GET /tasks 接口 |

**预计难度**：低-中（纯 CRUD + 事务，约 200-300 行新增代码）

**面试价值**：高 — 能说清楚"Agent 的执行轨迹为什么重要、怎么设计表结构、怎么保证事务一致性"

**不做会有什么问题**：
- 后续所有阶段（LLM Tool Selection、异步 Agent、业务 Agent）都在沙上建塔
- Agent 出问题时只能靠日志 grep，无法结构化查询
- 无法统计"Agent 平均耗时""各 Tool 的 P95 耗时""最近失败率"

**做过头会有什么风险**：
- 不要在 P1 就加"前端实时轮询 AgentStep 进度"——那是 P5 的事
- 不要设计过度复杂的表字段（如 token_usage、cost、model_name）——那些是 P3/P7 的事
- 不要建索引优化直到数据量上去——前 1000 条不需要

---

### P2：ToolRegistry / ToolSelector

**目标**：
1. 创建 `ToolRegistry` — 所有 `AgentTool` 自动注册
2. 创建 `ToolSelector` 接口 — 给定 task 和 context，返回应调用的 Tool 列表
3. 保留当前 `KeywordBasedToolSelector` 作为默认实现
4. `DocumentAgentServiceImpl` 不再硬编码 `new DocumentStatusTool()` 等，改为从 Registry 获取

**为什么现在做**：P1 有了轨迹，但 Tool 仍然散落，新增 Tool 要改 ServiceImpl。Registry 解决了开闭原则问题。

**前置条件**：P1 完成（AgentStep 表中有 toolName 字段，可以统计各 Tool 的使用频率和成功率）

**需要改/新增的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `ai/agent/tool/AgentToolRegistry.java` | 新增 | 收集所有 AgentTool Bean |
| `ai/agent/tool/ToolSelector.java` | 新增 | 接口：`List<AgentTool> select(String task, Context ctx)` |
| `ai/agent/tool/KeywordBasedToolSelector.java` | 新增 | 把现有 if-else 逻辑抽取到此 |
| `ai/agent/service/impl/DocumentAgentServiceImpl.java` | **修改** | 移除硬编码的 `containsAnyKeyword()`，改为调用 ToolSelector |

**预计难度**：低（纯重构，约 100 行新增代码）

**面试价值**：中 — 策略模式、开闭原则、Spring Bean 自动发现

**不做会有什么问题**：后续加 Tool 每次都要改 ServiceImpl，违反开闭原则，且无法做 A/B 测试不同的 Tool 选择策略

**做过头会有什么风险**：
- 不要设计过于复杂的 ToolSelector 接口（如多轮对话状态机）
- 不要引入 Spring SPI 或自定义注解扫描

---

### P3：LLM Tool Selection

**目标**：
1. 新增 `LlmBasedToolSelector` 实现 `ToolSelector` 接口
2. 用 LLM 做 function calling 选择 Tool（而不硬编码关键词）
3. 通过 `AiModeGuardConfig` 自动切换：mock 模式用关键词，real 模式用 LLM
4. 对比 P1 的 AgentStep 数据，评估 LLM Tool Selection 的准确率

**为什么现在做（不是 P1 或 P2）**：
- P1 的 AgentStep 给了基准数据（关键词路由选了什么 Tool、准不准）
- P2 的 ToolSelector 接口让新旧策略可以共存和对比
- 有了执行轨迹，才能评估"LLM 选的 Tool 对不对"

**前置条件**：P1 + P2 完成

**需要改/新增的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `ai/agent/tool/LlmBasedToolSelector.java` | 新增 | 调 AiAnswerService 做 function calling |
| `ai/agent/tool/ToolSelectorConfig.java` | 新增 | 根据 AI mode 决定用哪个 ToolSelector |
| `ai/agent/service/impl/DocumentAgentServiceImpl.java` | **修改** | 注入 ToolSelector（不关心具体实现） |
| `ai/service/impl/RealAiAnswerService.java` | **可能修改** | 如原生 function calling 需新增 method |

**预计难度**：中（需要设计 Tool Schema → LLM function calling 的映射）

**面试价值**：很高 — Agent 核心决策机制、LLM Function Calling 原理、A/B 测试 Tool 选择策略

**不做会有什么问题**：Agent 一直依赖硬编码关键词路由，无法处理复杂意图（"帮我对比文档 A 和文档 B 的第三季度数据"）

**做过头会有什么风险**：
- 不要引入复杂的 multi-step planning（ReAct 循环）——那是多 Agent 编排的事
- 不要让 LLM 直接生成 SQL 或操作系统命令
- 每步 Tool 调用仍通过 AgentStep 记录，确保可审计

---

### P4：RAG / 向量检索 / DocumentSearchTool

**目标**：
1. 为文档 chunk 生成 embedding 并存入向量库（推荐 pgvector 起步）
2. 新增 `DocumentSearchTool` — 语义搜索 + 关键词搜索的混合检索
3. `DocumentQaServiceImpl` 的 `rankChunks()` 支持向量相似度排序（替代纯关键词打分）
4. 保持向后兼容：向量库不可用时 fallback 到关键词检索

**为什么现在做（不是更早）**：
- 当前关键词检索已能满足基本问答（Stage C 评测 answerSuccessRate 57%）
- 向量检索需要 embedding 模型 + 向量库 + 索引维护，工程复杂度高
- P1 的 AgentStep 可以对比"关键词检索 vs 向量检索"的引用命中率

**前置条件**：P1 完成（用于对比评估），P2 完成（新增 Tool 通过 Registry）

**需要改/新增的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `common/config/VectorStoreConfig.java` | 新增 | pgvector 连接配置 |
| `ai/vector/EmbeddingService.java` | 新增 | Embedding 生成接口 |
| `ai/vector/OpenAiEmbeddingService.java` | 新增 | OpenAI 兼容 embedding 实现 |
| `ai/vector/VectorStoreService.java` | 新增 | 向量存储/检索接口 |
| `ai/vector/PgVectorStoreService.java` | 新增 | pgvector 实现 |
| `ai/agent/tool/DocumentSearchTool.java` | 新增 | 语义搜索 Tool |
| `ai/service/impl/DocumentQaServiceImpl.java` | **修改** | rankChunks() 支持混合排序 |
| `sql/005_init_vector_store.sql` | 新增 | pgvector extension + 表 |
| `pom.xml` | **修改** | pgvector 依赖 |

**预计难度**：高（需要 embedding API 集成 + 向量库运维 + 混合检索调参）

**面试价值**：很高 — RAG 全链路、Embedding 模型选择、pgvector 使用、混合检索策略

**不做会有什么问题**：关键词检索精度天花板明显（Stage C citationHitRate 仅 46%），长文档/复杂语义查询召回不足

**做过头会有什么风险**：
- 不要上来就上 Milvus/Qdrant 等专用向量数据库 — pgvector 足够
- 不要做复杂的 chunking 策略优化（父子chunk、摘要chunk）— 先用固定 600 字符
- 不要引入 rerank 模型 — 先靠 cosine similarity

---

### P5：RocketMQ 异步 Agent 执行 + SSE 进度

**目标**：
1. Agent 执行从 HTTP 同步改为 RocketMQ 异步消费
2. `POST /api/ai/agent/run` 立即返回 `taskId`（202 Accepted）
3. 前端通过 SSE 订阅 `/api/ai/agent/task/{taskId}/stream` 获取实时进度
4. AgentTask 状态流转：PENDING → RUNNING → SUCCESS / FAILED
5. 复用 `app.rocketmq.enabled` 开关：false 时走同步（兼容现有行为）

**为什么现在做（不是更早）**：
- P1 的 AgentTask/AgentStep 表是异步任务的状态载体
- P3 的 LLM Tool Selection 让单次 Agent 执行时间可能很长（多次 LLM 调用）
- 异步化是生产级 Agent 的基本要求

**前置条件**：P1 + P3 完成

**需要改/新增的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `mq/message/AgentTaskMessage.java` | 新增 | Agent 任务消息体 |
| `mq/producer/AgentTaskMessageProducer.java` | 新增 | 生产 |
| `mq/consumer/AgentTaskMessageConsumer.java` | 新增 | 消费 |
| `mq/entity/AgentTaskOutboxMessage.java` | 新增 | Outbox 实体（复用现有 Outbox 模式） |
| `mq/mapper/AgentTaskOutboxMessageMapper.java` | 新增 | Outbox Mapper |
| `mq/service/AgentTaskOutboxRelayService.java` | 新增 | Outbox 投递+补偿（复用 ParseTaskOutboxRelayService 模式） |
| `ai/agent/controller/DocumentAgentController.java` | **修改** | POST /run 改异步返回；新增 GET /task/{taskId}/stream |
| `ai/agent/service/impl/DocumentAgentServiceImpl.java` | **修改** | 拆分为"接收请求创建 AgentTask + 写 Outbox"和"消费消息执行 Agent"两个方法 |
| `frontend/lib/agent-api.ts` | **修改** | 支持 SSE 订阅 |
| `frontend/app/agent/page.tsx` | **修改** | 展示实时进度 |

**预计难度**：高（涉及 MQ 拓扑、Outbox 可靠性、SSE 双向通信、前后端联调）

**面试价值**：很高 — 异步任务系统设计、Outbox 模式、SSE 实时推送、分布式系统可靠性

**不做会有什么问题**：Agent 只能处理秒级任务，无法扩展到时分级复杂任务（合同审查、多文档对比）

**做过头会有什么风险**：
- 不要为 Agent 单独建一套 MQ Topic — 复用现有 RocketMQ 集群
- 不要做复杂的任务优先级队列
- 不要引入任务调度器（如 Quartz）— @Scheduled 补偿扫描足够

---

### P6：业务 Agent：合同审查 / 简历匹配 / 会议纪要

**目标**：
1. 基于已有的 Agent 基础设施（AgentTask/AgentStep/LLM Tool Selection/RAG/异步执行）
2. 新增 2-3 个业务 Agent：
   - `ContractReviewAgent` — 上传合同文档，自动审查关键条款
   - `ResumeMatchAgent` — 上传简历 + JD，匹配度分析
   - `MeetingSummaryAgent` — 会议纪要自动提取行动项
3. 每个业务 Agent 是独立的 Tool 集合，共享 AgentTask/AgentStep 基础设施

**为什么现在做（不是更早）**：前 5 个阶段把 Agent 的"发动机、变速箱、底盘"造好了，P6 是"造不同的车身"

**前置条件**：P1-P5 全部完成

**需要改/新增的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `ai/agent/business/ContractReviewTool.java` | 新增 | 合同审查专用 Tool |
| `ai/agent/business/ResumeMatchTool.java` | 新增 | 简历匹配专用 Tool |
| `ai/agent/business/MeetingActionExtractTool.java` | 新增 | 会议纪要专用 Tool |
| `ai/agent/business/BusinessAgentService.java` | 新增 | 业务 Agent 编排 |
| `ai/agent/controller/DocumentAgentController.java` | **修改** | 新增 /agent/review /agent/match /agent/meeting 端点 |

**预计难度**：中（业务逻辑 + Prompt 工程设计，基础设施已就绪）

**面试价值**：很高 — 从平台工程到业务落地的完整故事

**不做会有什么问题**：Agent 停留在技术 Demo 层面，缺乏业务场景说服力

**做过头会有什么风险**：
- 不要为每个业务 Agent 建独立表 — 复用 tb_agent_task + tb_agent_step
- 不要承诺"合同审查准确率 99%"— 这是 AI 辅助工具，不是法律意见
- 不要引入复杂的审批工作流

---

### P7：Spring AI 或 LangChain4j 适配

**目标**：
1. 评估 Spring AI / LangChain4j 的 tracing、tool calling、RAG 能力
2. 用框架替换部分自研代码（不重写整个 Agent 系统）
3. 优先替换：LLM 调用层（替代 RealAiAnswerService 中的 HttpClient）
4. 谨慎替换：Tool 执行层（AgentTool 接口保持，内部可委托给框架）
5. 保留自研：AgentTask/AgentStep 表结构、Outbox 模式、SSE 进度

**为什么现在做（不是更早）**：
- 前 6 个阶段已经把 Agent 系统的核心问题（执行轨迹、工具注册、异步执行）都自己实现过一遍
- 此时引入框架，能判断框架的哪些部分是"真帮手"、哪些是"黑盒负担"
- 如果 P1 先接框架，出了问题不知道是框架 bug 还是业务逻辑 bug

**前置条件**：P1-P5 完成（至少 P1-P3）

**需要改/新增的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `pom.xml` | **修改** | 添加 spring-ai-openai 或 langchain4j 依赖 |
| `ai/service/impl/FrameworkAiAnswerService.java` | 新增 | 用框架替代 HttpClient |
| `ai/agent/tool/FrameworkToolAdapter.java` | 新增 | 将框架的 @Tool 适配为 AgentTool 接口 |
| `ai/service/impl/RealAiAnswerService.java` | **修改** | 可选保留作为 fallback |

**预计难度**：中（框架学习曲线 + 适配层设计）

**面试价值**：很高 — 自研 vs 开源框架的技术选型、框架原理理解、适配层设计

**不做会有什么问题**：自研的 LLM 调用层需要持续维护（新模型、新协议、流式解析、错误处理），长期成本高

**做过头会有什么风险**：
- 不要全量重写 — 框架是工具，不是信仰
- 不要让框架接管 AgentTask/AgentStep 表 — 保留自研的执行轨迹
- 不要在框架不可用时系统完全不可用 — 保留 fallback 路径

---

### P8：MCP Server 暴露内部工具

**目标**：
1. 实现 MCP Server 协议，将 DocPilot 的内部 Tool（StatusTool / SummaryTool / QaTool / SearchTool 等）暴露为 MCP Tools
2. 外部 AI 应用（如 Claude Desktop、Cursor、VS Code Copilot）可以通过 MCP 调用 DocPilot 的文档处理能力
3. 所有外部 MCP 调用写入 AgentTask/AgentStep 表（审计）

**为什么最后做**：
- MCP 依赖于内部 Tool 的稳定性和丰富性（P1-P6 完成后才值得暴露）
- MCP 协议仍处于早期演进阶段
- 面试场景中，MCP 更像是"锦上添花"而非核心竞争力

**前置条件**：P1 + P2 + P6 完成（有稳定的 Tool 集合）

**需要改/新增的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `pom.xml` | **修改** | 添加 MCP SDK 依赖 |
| `ai/mcp/McpServerConfig.java` | 新增 | MCP Server 配置 |
| `ai/mcp/McpToolExporter.java` | 新增 | 将 AgentTool → MCP Tool 适配 |
| `ai/mcp/McpTransportConfig.java` | 新增 | SSE 或 stdio 传输层 |

**预计难度**：中-高（MCP 协议理解 + 适配层 + 安全控制）

**面试价值**：中-高 — Agent 工具标准化、跨应用互操作

**不做会有什么问题**：DocPilot 只能在自身前端使用，无法与其他 AI 应用集成

**做过头会有什么风险**：
- 不要急于支持 MCP 的所有特性（Resources、Prompts、Sampling）
- 不要在生产环境无认证暴露 MCP 端点
- MCP 协议版本更新可能导致 breaking changes

---

### 路线图总览

```
P0: 保存现场 ───────────────────── 1-2 小时
  │
P1: AgentTask / AgentStep 执行轨迹 ── 2-3 天
  │
P2: ToolRegistry / ToolSelector ──── 1-2 天
  │
P3: LLM Tool Selection ───────────── 2-4 天
  │
P4: RAG / 向量检索 ───────────────── 3-5 天
  │
P5: RocketMQ 异步 Agent + SSE 进度 ─ 3-5 天
  │
P6: 业务 Agent ───────────────────── 3-5 天/个
  │
P7: Spring AI / LangChain4j 适配 ─── 2-4 天
  │
P8: MCP Server ───────────────────── 3-5 天
```

**总预估**：约 4-6 周全职（P0-P5），+2-4 周（P6-P8）

---

## 四、第一阶段详细落地方案（P1）

### 4.1 设计原则

- **最小化改动**：只在 `ai/agent/` 包内新增文件，只修改 `DocumentAgentServiceImpl` 和 `DocumentAgentController`
- **不改前端**：P1 只加后端表 + 查询接口，Agent 页面仍然展示同步结果（但数据来自 DB 而非内存）
- **不接 MQ**：Agent 仍然在 HTTP 线程内同步执行
- **不接 LLM Tool Selection**：保持现有关键词路由
- **不接向量库**：保持现有关键词检索

### 4.2 数据库 DDL

```sql
-- sql/004_init_agent_tables.sql

CREATE TABLE IF NOT EXISTS tb_agent_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    trace_id VARCHAR(64) NOT NULL COMMENT 'Trace ID for log correlation',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    document_id BIGINT UNSIGNED NOT NULL COMMENT 'Related document id',
    task TEXT NOT NULL COMMENT 'Original task description',
    session_id VARCHAR(64) DEFAULT NULL COMMENT 'Session identifier',
    decision VARCHAR(32) DEFAULT NULL COMMENT 'Agent decision: status_only, summary_tool, qa_tool',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Task status: PENDING, RUNNING, SUCCESS, FAILED',
    final_answer TEXT COMMENT 'Final answer text',
    error_message VARCHAR(1024) DEFAULT NULL COMMENT 'Failure reason when status=FAILED',
    total_duration_ms BIGINT DEFAULT NULL COMMENT 'Total execution duration in ms',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_task_trace_id (trace_id),
    KEY idx_agent_task_user_doc (user_id, document_id, id),
    KEY idx_agent_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Agent task execution record';

CREATE TABLE IF NOT EXISTS tb_agent_step (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    task_id BIGINT UNSIGNED NOT NULL COMMENT 'Related agent task id',
    step_index INT NOT NULL COMMENT 'Step order in task',
    tool_name VARCHAR(64) NOT NULL COMMENT 'Tool name',
    input_summary VARCHAR(512) DEFAULT NULL COMMENT 'Brief summary of tool input',
    output_summary VARCHAR(1024) DEFAULT NULL COMMENT 'Brief summary of tool output',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Step status: PENDING, RUNNING, SUCCESS, FAILED',
    duration_ms BIGINT DEFAULT NULL COMMENT 'Step execution duration in ms',
    error_message VARCHAR(1024) DEFAULT NULL COMMENT 'Failure reason when status=FAILED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (id),
    KEY idx_agent_step_task_id (task_id),
    KEY idx_agent_step_tool_name (tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Agent tool execution step record';
```

### 4.3 实体类

**AgentTask.java** — 映射 `tb_agent_task`，标准 MyBatis-Plus Entity，字段与表结构一一对应。

**AgentStep.java** — 映射 `tb_agent_step`，标准 MyBatis-Plus Entity。

### 4.4 Mapper 接口

**AgentTaskMapper.java** — 继承 `BaseMapper<AgentTask>`，额外方法：
- `AgentTask selectByTraceId(@Param("traceId") String traceId)`
- `List<AgentTaskListItemResponse> selectByUserAndDocument(@Param("userId") Long userId, @Param("documentId") Long documentId, @Param("offset") int offset, @Param("limit") int limit)`
- `Long countByUserAndDocument(@Param("userId") Long userId, @Param("documentId") Long documentId)`

**AgentStepMapper.java** — 继承 `BaseMapper<AgentStep>`，额外方法：
- `List<AgentStep> selectByTaskId(@Param("taskId") Long taskId)`

### 4.5 VO

**AgentTaskDetailResponse.java** — 包含 AgentTask 全部字段 + `List<AgentStepDetail> steps`

**AgentTaskListItemResponse.java** — 列表项（省略 final_answer 和 steps，减少数据传输量）

### 4.6 修改 DocumentAgentServiceImpl.run()

**改动要点**：

```java
// 伪代码，描述改动逻辑

public DocumentAgentResponse run(Long userId, DocumentAgentRequest request) {
    // 1. 创建 AgentTask (status=PENDING)，获得 taskId
    AgentTask agentTask = new AgentTask();
    agentTask.setTraceId(UUID.randomUUID().toString());
    agentTask.setUserId(userId);
    agentTask.setDocumentId(request.getDocumentId());
    agentTask.setTask(request.getTask());
    agentTask.setSessionId(normalizeSessionId(request.getSessionId()));
    agentTask.setStatus("PENDING");
    agentTaskMapper.insert(agentTask);

    // 2. 更新状态为 RUNNING
    agentTask.setStatus("RUNNING");
    agentTaskMapper.updateById(agentTask);

    try {
        // 3. 执行现有逻辑（不变），但每次 Tool 调用前后写 AgentStep
        //    Step 1: StatusTool
        AgentStep step1 = createStep(agentTask.getId(), 1, "DocumentStatusTool", inputSummary);
        step1.setStatus("RUNNING");
        agentStepMapper.insert(step1);
        long step1Start = System.nanoTime();
        TimedResult<StatusResult> statusResult = timedExecute(() -> statusTool.execute(...));
        step1.setStatus("SUCCESS");
        step1.setOutputSummary(summarize(statusResult.value().toString()));
        step1.setDurationMs(statusResult.durationMs());
        agentStepMapper.updateById(step1);

        //    Step 2: SummaryTool 或 QaTool（同上模式）

        // 4. 更新 AgentTask 为 SUCCESS
        agentTask.setStatus("SUCCESS");
        agentTask.setDecision(decision);
        agentTask.setFinalAnswer(finalAnswer);
        agentTask.setTotalDurationMs(totalMs);
        agentTaskMapper.updateById(agentTask);

        return response;
    } catch (Exception ex) {
        // 5. 失败时更新 AgentTask 和最后一个 Step
        agentTask.setStatus("FAILED");
        agentTask.setErrorMessage(truncate(ex.getMessage(), 1024));
        agentTaskMapper.updateById(agentTask);
        // 最后一个 step 也标记失败
        throw ex; // 仍然抛给 Controller → GlobalExceptionHandler
    }
}
```

### 4.7 新增查询接口

**`GET /api/ai/agent/task/{taskId}`**
- 返回 AgentTask 详情 + 所有 AgentStep（按 step_index 排序）
- 校验 userId 归属权

**`GET /api/ai/agent/tasks?documentId={documentId}&pageNo={pageNo}&pageSize={pageSize}`**
- 分页返回某文档的 Agent 执行历史列表
- 校验 userId 归属权

### 4.8 验收标准

1. 每次 `POST /api/ai/agent/run` 在 `tb_agent_task` 中有一条记录
2. 每次 Tool 调用在 `tb_agent_step` 中有一条记录
3. `GET /api/ai/agent/task/{taskId}` 能返回完整执行轨迹
4. `GET /api/ai/agent/tasks?documentId=xxx` 能分页返回历史
5. 任务执行失败时，AgentTask.status=FAILED 且 error_message 有内容
6. 步骤执行失败时，AgentStep.status=FAILED 且 error_message 有内容
7. 不影响现有 `POST /api/ai/agent/run` 的响应格式
8. 现有 `DocumentAgentServiceImplTest` 测试仍然通过（加 mock AgentTaskMapper）

### 4.9 不在 P1 做的内容

| 不做 | 原因 |
|------|------|
| 前端实时轮询 AgentStep | 当前还是同步执行，一次请求就拿到完整结果 |
| RocketMQ 异步 | P5 |
| LLM Tool Selection | P3 |
| 向量检索 | P4 |
| 修改前端 agent/page.tsx | P1 只加后端查询接口，前端暂时保持现有行为 |
| AgentTask 缓存 | 数据量小，直接查 MySQL |
| 定时清理过期 AgentTask | P3 之后根据数据量决定 |

---

## 五、后续能力什么时候做（进入条件）

### 5.1 什么时候做 LLM Tool Selection（P3）

**进入条件**：
- [x] P1 + P2 完成
- [x] `tb_agent_step` 中积累了至少 50 条关键词路由的 Tool 调用记录（用于对比评估）
- [x] `AI_MODE=real` 已配置且可用（有可用的 API Key 和模型）
- [x] 当前关键词路由的"Tool 选择错误率"可被量化（通过人工标注 AgentStep 数据）

**现在强行做的风险**：
- 没有 P1 的 AgentStep 数据作为基准，无法评估 LLM 选 Tool 是否比关键词路由更好
- 没有 P2 的 ToolSelector 接口，LLM 版本和关键词版本无法共存对比
- RealAiAnswerService 目前只支持简单的 chat/completions 调用，function calling 需要扩展

### 5.2 什么时候做向量库（P4）

**进入条件**：
- [x] P1 完成（AgentStep 中有 citation 数据，可对比检索质量）
- [x] 当前关键词检索的 citationHitRate 成为可观测瓶颈（Stage C 评测中仅 46.154%）
- [x] 有可用的 Embedding API（或本地 embedding 模型）
- [x] PostgreSQL 环境已就绪（pgvector 扩展可用）

**现在强行做的风险**：
- 关键词检索对当前 txt/md 文档规模（通常 < 50 页）已够用
- Embedding API 有额外成本（token 计费）或需要本地 GPU
- pgvector 需要 PostgreSQL 环境（当前项目用的是 MySQL）
- 在检索质量基线不明确的情况下，无法评估向量检索的实际提升

### 5.3 什么时候接 RocketMQ（P5）

**进入条件**：
- [x] P1 + P3 完成（Agent 有执行轨迹，且 LLM Tool Selection 让单次执行时间变长）
- [x] `ROCKETMQ_ENABLED=true` 且 NameServer 可用
- [x] 单次 Agent 执行 P95 耗时 > 5 秒（同步 HTTP 等待开始影响用户体验）
- [x] 前端已有"提交任务 → 轮询/SSE 等待结果"的用户体验需求

**现在强行做的风险**：
- 当前 Agent 执行在 1-3 秒内完成，异步化的收益不大
- RocketMQ 异步链路引入了 Outbox 写入、MQ 投递、消费幂等等复杂度
- 前端需要从"一次请求拿结果"改为"提交→轮询→展示"，改动量大

### 5.4 什么时候引入 Spring AI 或 LangChain4j（P7）

**进入条件**：
- [x] P1-P5 至少完成（理解 Agent 系统的核心问题）
- [x] 自研的 LLM 调用层（RealAiAnswerService）出现了维护痛点：
  - 需要支持新的 LLM 提供商（Anthropic、Gemini、DeepSeek）
  - 流式解析逻辑越来越复杂
  - 错误处理和重试逻辑不够健壮
- [x] 团队成员对至少一个框架有基本了解

**现在强行做的风险**：
- 引入框架但不理解它在做什么（"魔法"）—— 出了 bug 无法定位
- 框架版本升级可能带来 breaking changes
- 框架的 tracing 和自研的 AgentTask/AgentStep 可能冲突
- Spring AI 仍在快速迭代中（1.0 之前 API 可能变化）

### 5.5 什么时候做 MCP（P8）

**进入条件**：
- [x] P1 + P2 + P6 完成（有稳定的 Tool 集合）
- [x] 至少 3 个可用 Tool 值得暴露给外部
- [x] 有外部 AI 应用（如 Claude Desktop）需要调用 DocPilot 的内部能力
- [x] MCP 协议规范相对稳定（当前仍在活跃演进）

**现在强行做的风险**：
- 当前只有 3 个 Tool，暴露价值有限
- MCP 协议版本更新可能导致不兼容
- 安全模型需要仔细设计（认证、授权、限流），否则等于开放内部 API
- 在 Tool 本身还不稳定的情况下暴露 MCP，接口变更成本高

---

## 六、交给 Codex CLI 的任务拆分

以下是每个阶段可以拆分给 Codex（或其他 AI 编码助手）的独立小任务。每个任务应该可以在 1-2 个 session 内完成。

---

### P0 任务

#### 任务 P0-1：安全审查当前变更

- **任务名**：检查工作区敏感信息
- **目标**：确保当前未提交变更中无硬编码的密钥/密码/云服务真实 IP
- **允许修改**：无（只读审查）
- **禁止修改**：所有文件
- **验收标准**：输出审查报告，列出（如有）潜在敏感信息位置
- **给 Codex 的提示词**：
  ```
  审查以下文件的未提交变更，检查是否包含：API Key、数据库密码、云服务真实IP、Token。
  文件列表：backend/.env.demo.example, backend/src/main/resources/application-local.yml, backend/src/main/resources/application.yml
  只读审查，不做任何修改。输出审查结果。
  ```

---

### P1 任务

#### 任务 P1-1：创建 Agent 数据库表

- **任务名**：创建 tb_agent_task 和 tb_agent_step DDL
- **目标**：按 4.2 节的 DDL 创建 SQL 文件 `sql/004_init_agent_tables.sql`
- **允许修改**：仅新建 `sql/004_init_agent_tables.sql`
- **禁止修改**：任何 Java 文件、任何已有 SQL 文件
- **验收标准**：SQL 语法正确，字段与 4.2 节完全一致，包含索引和注释
- **给 Codex 的提示词**：
  ```
  在 backend/src/main/resources/sql/ 下创建 004_init_agent_tables.sql。
  需要两张表：
  
  tb_agent_task: id(BIGINT UNSIGNED AUTO_INCREMENT PK), trace_id(VARCHAR 64 NOT NULL UNIQUE), user_id(BIGINT UNSIGNED NOT NULL), document_id(BIGINT UNSIGNED NOT NULL), task(TEXT NOT NULL), session_id(VARCHAR 64), decision(VARCHAR 32), status(VARCHAR 20 NOT NULL DEFAULT 'PENDING'), final_answer(TEXT), error_message(VARCHAR 1024), total_duration_ms(BIGINT), create_time(DATETIME DEFAULT CURRENT_TIMESTAMP), update_time(DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)。索引：idx_agent_task_user_doc(user_id, document_id, id), idx_agent_task_status(status)。
  
  tb_agent_step: id(BIGINT UNSIGNED AUTO_INCREMENT PK), task_id(BIGINT UNSIGNED NOT NULL), step_index(INT NOT NULL), tool_name(VARCHAR 64 NOT NULL), input_summary(VARCHAR 512), output_summary(VARCHAR 1024), status(VARCHAR 20 NOT NULL DEFAULT 'PENDING'), duration_ms(BIGINT), error_message(VARCHAR 1024), create_time(DATETIME DEFAULT CURRENT_TIMESTAMP), update_time(DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)。索引：idx_agent_step_task_id(task_id), idx_agent_step_tool_name(tool_name)。
  
  使用 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci。
  使用 deploy/mysql/init/00_init_docpilot.sql 作为格式参考。
  ```

#### 任务 P1-2：创建 AgentTask 和 AgentStep 实体类

- **任务名**：创建实体类 AgentTask.java 和 AgentStep.java
- **目标**：MyBatis-Plus Entity，映射 P1-1 的两张表
- **允许修改**：仅新建 `ai/agent/entity/AgentTask.java` 和 `ai/agent/entity/AgentStep.java`
- **禁止修改**：任何已有文件
- **验收标准**：@TableName 正确，@TableId 类型为 AUTO，字段名与表列名匹配（下划线转驼峰），有 getter/setter
- **给 Codex 的提示词**：
  ```
  在 ai/agent/entity/ 下创建两个实体类，参考 Document.java 的 MyBatis-Plus 写法：
  
  1. AgentTask.java — 表名 tb_agent_task，字段：id, traceId, userId, documentId, task, sessionId, decision, status, finalAnswer, errorMessage, totalDurationMs, createTime(LocalDateTime), updateTime(LocalDateTime)
  
  2. AgentStep.java — 表名 tb_agent_step，字段：id, taskId, stepIndex, toolName, inputSummary, outputSummary, status, durationMs, errorMessage, createTime(LocalDateTime), updateTime(LocalDateTime)
  
  使用 @TableId(type = IdType.AUTO) 和 @TableField 注解（仅当字段名与列名不完全一致时使用 @TableField）。
  ```

#### 任务 P1-3：创建 Mapper 接口

- **任务名**：创建 AgentTaskMapper 和 AgentStepMapper
- **目标**：MyBatis-Plus BaseMapper + 自定义查询方法
- **允许修改**：仅新建 `ai/agent/mapper/AgentTaskMapper.java` 和 `ai/agent/mapper/AgentStepMapper.java`
- **禁止修改**：任何已有文件
- **验收标准**：继承 BaseMapper，@Mapper 注解，自定义 SQL 方法使用 @Select 注解，参考 DocumentMapper 的写法
- **给 Codex 的提示词**：
  ```
  在 ai/agent/mapper/ 下创建两个 Mapper 接口，参考 DocumentMapper.java 的写法（@Mapper 注解 + 继承 BaseMapper + @Select 手写 SQL）：
  
  1. AgentTaskMapper:
     - 继承 BaseMapper<AgentTask>
     - AgentTask selectByTraceId(@Param("traceId") String traceId)
     - List<AgentTask> selectByUserAndDocument(@Param("userId") Long userId, @Param("documentId") Long documentId, @Param("offset") int offset, @Param("limit") int limit)
     - Long countByUserAndDocument(@Param("userId") Long userId, @Param("documentId") Long documentId)
  
  2. AgentStepMapper:
     - 继承 BaseMapper<AgentStep>
     - List<AgentStep> selectByTaskId(@Param("taskId") Long taskId)
  ```

#### 任务 P1-4：创建 VO 类

- **任务名**：创建 AgentTaskDetailResponse 和 AgentTaskListItemResponse
- **目标**：查询接口的返回 VO
- **允许修改**：仅新建 `ai/agent/vo/AgentTaskDetailResponse.java` 和 `ai/agent/vo/AgentTaskListItemResponse.java`
- **禁止修改**：任何已有文件
- **验收标准**：包含必要字段，有 getter/setter
- **给 Codex 的提示词**：
  ```
  在 ai/agent/vo/ 下创建两个 VO 类，参考 DocumentDetailResponse.java 的写法：
  
  1. AgentTaskDetailResponse: 包含 AgentTask 所有字段 + List<AgentStepDetail> steps（其中 AgentStepDetail 是内部静态类，包含 AgentStep 所有字段）
  
  2. AgentTaskListItemResponse: 包含 id, traceId, documentId, task（截断前100字符）, decision, status, totalDurationMs, createTime（不包含 finalAnswer 和 steps 以减少数据传输）
  ```

#### 任务 P1-5：修改 DocumentAgentServiceImpl 写入执行轨迹

- **任务名**：在 Agent 执行中写入 AgentTask/AgentStep
- **目标**：每次 Agent 执行时创建 AgentTask 和 AgentStep 记录
- **允许修改**：仅修改 `ai/agent/service/impl/DocumentAgentServiceImpl.java`
- **禁止修改**：Controller、前端、其他 Service、配置文件
- **验收标准**：
  1. 方法入口处创建 AgentTask (status=PENDING)
  2. 开始执行前更新为 RUNNING
  3. 每次 Tool 调用前后创建/更新 AgentStep
  4. 成功时更新 AgentTask 为 SUCCESS，填入 decision/finalAnswer/totalDurationMs
  5. 失败时更新 AgentTask 为 FAILED，填入 errorMessage；更新当前 Step 为 FAILED
  6. 异常重新抛出（不吞异常）
  7. 现有响应格式不变
  8. 现有测试通过（需 mock AgentTaskMapper 和 AgentStepMapper）
- **给 Codex 的提示词**：
  ```
  修改 DocumentAgentServiceImpl.java 的 run() 方法，在执行前后写入 AgentTask 和 AgentStep 记录。
  
  具体要求：
  1. 注入 AgentTaskMapper 和 AgentStepMapper
  2. 在 run() 方法开始处创建 AgentTask（traceId 用 UUID，status=PENDING），insert 后立即得到 taskId
  3. 更新 status 为 RUNNING
  4. 在 timedExecute() 调用前后创建/更新 AgentStep：
     - 调用前：new AgentStep(taskId, stepIndex, toolName, inputSummary, status="RUNNING")，insert
     - 调用后：更新 outputSummary, durationMs, status="SUCCESS"
  5. 正常结束时更新 AgentTask(status="SUCCESS", decision, finalAnswer, totalDurationMs)
  6. catch 块中更新 AgentTask(status="FAILED", errorMessage)，同时把当前正在执行的 Step 也标记为 FAILED，然后重新 throw
  7. 工具调用的 stepIndex 是全局递增的（StatusTool 始终为 1，后续 Tool 为 2）
  8. 不要修改现有的 keyword 路由逻辑和 decision 逻辑
  9. 不要修改方法签名
  10. 不要修改 response 的构建逻辑
  11. AgentStep 的 inputSummary 从 input 对象 toString 截取前 200 字符
  12. AgentStep 的 outputSummary 从 output 对象 toString 截取前 200 字符
  ```

#### 任务 P1-6：新增查询接口

- **任务名**：在 DocumentAgentController 新增 GET 接口
- **目标**：新增 AgentTask 详情查询和历史列表查询
- **允许修改**：仅修改 `ai/agent/controller/DocumentAgentController.java` 和 `ai/agent/service/DocumentAgentService.java`
- **禁止修改**：ServiceImpl 的执行逻辑、前端代码
- **验收标准**：
  1. `GET /api/ai/agent/task/{taskId}` 返回完整执行轨迹
  2. `GET /api/ai/agent/tasks?documentId=xxx&pageNo=1&pageSize=10` 返回分页历史
  3. 两个接口都校验 userId 归属权
  4. 返回格式为 ApiResponse<T>
- **给 Codex 的提示词**：
  ```
  在 DocumentAgentController 中新增两个查询接口：
  
  1. GET /api/ai/agent/task/{taskId}
     - 从 UserHolder 获取 userId
     - 调用 agentTaskMapper.selectById(taskId)，校验 userId 匹配
     - 调用 agentStepMapper.selectByTaskId(taskId)，按 stepIndex 排序
     - 组装为 AgentTaskDetailResponse 返回，ApiResponse.success()
  
  2. GET /api/ai/agent/tasks?documentId=xxx&pageNo=1&pageSize=10
     - 从 UserHolder 获取 userId
     - pageNo/pageSize 默认值参考 CommonConstants.DEFAULT_PAGE_NUM / DEFAULT_PAGE_SIZE
     - 调用 agentTaskMapper.selectByUserAndDocument + countByUserAndDocument
     - 返回分页结果（参考 DocumentController.list() 的格式）
  
  同时在 DocumentAgentService 接口中新增对应方法声明。
  ```

---

### P2 任务

#### 任务 P2-1：创建 ToolRegistry

- **任务名**：创建 AgentTool 自动注册器
- **目标**：所有实现 AgentTool 的 Bean 自动收集到 ToolRegistry
- **允许修改**：仅新建 `ai/agent/tool/AgentToolRegistry.java`；仅修改 `ai/agent/tool/AgentTool.java`（如需加注解）
- **禁止修改**：ServiceImpl、Controller、已有 Tool
- **验收标准**：ToolRegistry.getTools() 返回所有已注册 Tool；getTool(name) 按名称查找
- **给 Codex 的提示词**：
  ```
  创建 AgentToolRegistry，利用 Spring 的依赖注入收集所有 AgentTool 实现：
  
  public class AgentToolRegistry {
      private final Map<String, AgentTool<?, ?>> tools;
      
      public AgentToolRegistry(List<AgentTool<?, ?>> toolList) {
          // 遍历 toolList，按 getToolName() 构建 Map
      }
      
      public AgentTool<?, ?> getTool(String name) { ... }
      public List<AgentTool<?, ?>> getAllTools() { ... }
      public List<String> getToolNames() { ... }
  }
  ```

#### 任务 P2-2：抽取 KeywordBasedToolSelector

- **任务名**：将意图路由逻辑抽取为 ToolSelector 实现
- **目标**：创建 ToolSelector 接口和 KeywordBasedToolSelector 实现
- **允许修改**：新建 ToolSelector 接口和 KeywordBasedToolSelector；修改 DocumentAgentServiceImpl 使用 ToolSelector
- **禁止修改**：Tool 实现、Controller
- **验收标准**：关键词路由逻辑完全从 DocumentAgentServiceImpl 移到 KeywordBasedToolSelector；Agent 行为不变
- **给 Codex 的提示词**：
  ```
  1. 在 ai/agent/tool/ 下创建 ToolSelector 接口：
     interface ToolSelector {
         ToolSelectionResult select(String task, ToolContext ctx);
         // ToolContext: documentId, parseReady, hasSummary
         // ToolSelectionResult: selectedToolNames(List<String>), decision(String), reason(String)
     }
  
  2. 创建 KeywordBasedToolSelector 实现，把 DocumentAgentServiceImpl 中的 STATUS_KEYWORDS/SUMMARY_KEYWORDS/EVIDENCE_KEYWORDS 和 containsAnyKeyword() 逻辑移入。ToolSelector 返回 selectedToolNames 而不是直接调用 Tool。
  
  3. 修改 DocumentAgentServiceImpl，注入 ToolSelector 替代硬编码关键词判断。run() 中先调 ToolSelector.select() 获取应调用的 Tool 列表，再从 ToolRegistry 按名获取。
  ```

---

### P3 任务（每个任务均依赖 P1+P2）

#### 任务 P3-1：设计 Tool Schema 映射

- **任务名**：为每个 Tool 定义 LLM function calling 的 JSON Schema
- **允许修改**：仅修改 Tool 实现类（新增 getSchema() 方法）
- **给 Codex 的提示词**：略

#### 任务 P3-2：实现 LlmBasedToolSelector

- **任务名**：用 LLM function calling 实现 ToolSelector
- **允许修改**：新建 LlmBasedToolSelector；修改 ToolSelectorConfig
- **给 Codex 的提示词**：略

#### 任务 P3-3：评估和对比

- **任务名**：用 P1 采集的 AgentStep 数据对比两种 ToolSelector
- **允许修改**：新建测试类或评测脚本
- **给 Codex 的提示词**：略

---

### P4-P8 任务（概要，详细拆分在对应阶段前进行）

#### P4 任务组
- P4-1：搭建 pgvector 环境 + 建表
- P4-2：实现 EmbeddingService
- P4-3：实现 VectorStoreService
- P4-4：修改 rankChunks 支持混合排序
- P4-5：创建 DocumentSearchTool
- P4-6：重新运行 Stage C 评测对比

#### P5 任务组
- P5-1：创建 AgentTaskMessage + Outbox 实体
- P5-2：创建 Agent Producer + Consumer
- P5-3：拆分 DocumentAgentServiceImpl 为 "创建任务" 和 "执行任务" 两阶段
- P5-4：实现 GET /task/{taskId}/stream SSE 接口
- P5-5：修改前端支持异步提交 + 实时进度

#### P6 任务组
- P6-1：ContractReviewTool + Prompt 设计
- P6-2：ResumeMatchTool + Prompt 设计
- P6-3：MeetingActionExtractTool + Prompt 设计
- P6-4：BusinessAgentService 编排

#### P7 任务组
- P7-1：技术选型评估（Spring AI vs LangChain4j）
- P7-2：替换 LLM 调用层
- P7-3：适配 Tool 层
- P7-4：保留 fallback 路径

#### P8 任务组
- P8-1：MCP Server 协议实现
- P8-2：Tool → MCP Tool 适配
- P8-3：安全认证和审计

---

## 七、面试价值总结

### 按面试场景分类

| 面试场景 | 可讲的内容 | 涉及阶段 |
|---------|-----------|---------|
| **项目介绍** | "我做了一个 AI 文档问答平台，后端 Spring Boot + MyBatis-Plus，前端 Next.js，包含文件上传/异步解析/AI问答/Agent工具链全链路" | 当前 + P1 |
| **可靠性设计** | "Agent 的执行轨迹通过 AgentTask/AgentStep 表持久化，每次 Tool 调用都可追溯；异步任务使用 Outbox + RocketMQ 保证消息不丢；消费幂等通过 message_key 唯一索引实现" | P1 + P5 |
| **系统设计** | "Agent 系统分了 8 个阶段演进：先打地基（执行轨迹），再做能力（Tool Selection → RAG → 异步），最后才是框架适配和开放集成" | P0-P8 |
| **技术选型** | "我选择先自研 Agent 执行框架，在 P7 才引入 Spring AI。因为先自己实现一遍才知道框架在解决什么问题，引入时能判断框架的 tracing 是否满足需求" | P7 |
| **AI/LLM 能力** | "实现了关键词路由 → LLM Function Calling 的 Tool Selection 演进，用 AgentStep 数据做 A/B 对比评估；RAG 从纯关键词打分升级到混合检索（BM25 + 向量相似度）" | P3 + P4 |
| **代码质量** | "ToolRegistry + ToolSelector 让新增 Tool 无需修改 ServiceImpl，符合开闭原则；策略模式让 KeywordSelector 和 LlmSelector 可共存" | P2 + P3 |
| **未来规划** | "P8 计划通过 MCP 协议将内部 Tool 暴露给外部 AI 应用，实现跨应用互操作" | P8 |

### 推荐的面试话术（1 分钟版本）

> "DocPilot 是一个 AI 文档问答的全栈项目。后端 Agent 模块我设计了一条从 0 到 8 阶段的升级路线。目前已完成最小 Agent 工具链，下一步是做 AgentTask/AgentStep 执行轨迹持久化——这是整个 Agent 系统的基础设施，有了它才能做 LLM Tool Selection 的 A/B 评估、RocketMQ 异步执行、SSE 进度推送。我刻意把 Spring AI/LangChain4j 放到 P7 才引入，因为先自己实现一遍，才能判断框架的 tracing 是否真的满足需求。"

---

## 八、风险和边界

### 8.1 技术风险

| 风险 | 缓解措施 |
|------|---------|
| P1 表设计不够前瞻，后续需要加字段 | AgentTask/AgentStep 使用 TEXT 类型存储 task/final_answer，预留扩展空间；加字段是 MySQL Online DDL 的轻量操作 |
| P4 引入 pgvector 导致技术栈膨胀（当前只有 MySQL） | pgvector 只用于向量检索，不替代 MySQL 主库。如果不想引入 PostgreSQL，P4 可以用 Redis + 内存向量索引作为过渡方案 |
| P5 RocketMQ 异步化引入消息丢失风险 | 复用 ParseTask 的 Outbox 模式（PENDING → SENT/FAILED + 定时补偿扫描），已经被验证可靠 |
| P7 框架版本不稳定 | Spring AI / LangChain4j 仍在快速迭代，P7 时保持一层薄适配，方便替换 |

### 8.2 边界声明

- **本路线图不做的事**：
  - 不设计多 Agent 协作系统（Multi-Agent / Swarm）
  - 不做 Agent-to-Agent 通信协议
  - 不做 Agent 市场 / 插件系统
  - 不做实时协作编辑
  - 不做移动端 App
- **Agent 的范围**：单文档上下文内的问题解答 + 状态查询 + 摘要，不扩展到多文档对比（P6 可做但非核心）
- **安全边界**：Agent 不能执行任意的数据库写操作、不能调用系统命令、不能对外发送网络请求（除配置的 LLM API）
- **性能边界**：单次 Agent 执行设计目标是 P95 < 30 秒（含 LLM 调用），超过此时间应走异步模式

### 8.3 不做的事

1. **不要重写整个项目** — 所有改动都在 `ai/agent/` 包内增量添加
2. **不要破坏现有 API** — `POST /api/ai/agent/run` 的请求/响应格式在 P1-P4 保持兼容
3. **不要引入新的中间件（P1-P3）** — MySQL + Redis 即可，P4 才考虑 pgvector
4. **不要在 P1 就做前端大改** — 先让后端稳了，P5 再改前端
5. **不要跳过 P1 直接做 P3/P4** — AgentTask/AgentStep 是所有后续能力的评估基础
