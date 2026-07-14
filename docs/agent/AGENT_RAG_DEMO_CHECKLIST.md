# Agent RAG Demo Checklist

本文只用于后续截图和面试演示前的自检，不是简历文案，也不代表完整上传解析链路已通过。

## 演示入口

- 页面：`/agent`
- 前置：使用已登录账号，准备当前账号可访问且 `parseStatus=SUCCESS` 的 `documentId`
- 推荐任务模板：`RAG 召回`
- 推荐说明口径：这是已解析文档上的 Agent-only demo；完整上传、解析、MQ 消费链路仍受 T010 blocker 影响

## 操作顺序

1. 登录后进入 `/agent`。
2. 刷新文档列表，选择已解析成功文档，或手动输入当前账号可访问的 `documentId`。
3. 选择 `RAG 召回` 模板，确认任务文本包含 RAG、topK、score、metadata 等触发词。
4. 运行 Agent，等待结果区和持久化 trace 加载完成。
5. 再运行一次 `证据问答` 模板，确认普通 QA citations 仍可展示。

## 必须截图的字段

- 文档选择区：`documentId`、文档解析状态、Lite 验证边界提示
- Agent 结果概览：`decision`、`taskId`、`totalDurationMs`、`citations` 数量、`RAG Chunks` 数量
- 路由决策：`routingReason`、`matchedKeywords`
- RAG 召回片段：rank、chunk index、score / similarity、metadata
- Agent Workflow：接收任务、选择工具、执行工具、生成结果、持久化 trace
- 持久化执行轨迹：task status、decision、step count、toolName、durationMs、inputSummary、outputSummary
- 普通 QA 路径：citations / 引用片段，证明 RAG demo 未破坏原问答链路

## 必须说明的边界

- 当前 RAG Showcase 默认使用 fake embedding + in-memory vector store。
- Qdrant HTTP adapter 已有本地 fake server 测试和脱敏 preflight，但默认 disabled；本 checklist 不真实连接 Qdrant。
- 真实 embedding provider 缺少必要环境变量时继续 BLOCKED；fake embedding / in-memory 离线 demo 和测试不受影响。
- `app.rag.qa.enabled=false` 仍是 QA RAG context 的默认配置；页面里的 `rag_tool` 是 Agent demo 路径。
- `llm_execute` 默认关闭；默认 Agent routing 仍由关键词 / 规则 selector 决定。
- 当前页面验证的是已解析文档上的 Agent run，不覆盖上传、解析、MQ、`ParseTaskMessageConsumer` 或 T010 完整链路。

## 不应截图或输出

- API Key、token、Authorization、baseUrl、endpoint 原文、环境变量值
- provider request / response
- prompt 原文
- 不适合公开的真实文档正文或敏感业务资料
- `backend/.env` 或本机私有配置

## 演示失败时的处理

- 如果文档不存在或无权访问，换成当前账号可访问的已解析文档。
- 如果 RAG chunks 为 0，确认任务触发了 `rag_tool`，并检查文档是否有可检索文本。
- 如果持久化 trace 暂时未返回，保留 runtime steps 截图，并标注 task / step 查询接口需要后端可用。
- 如果真实 provider、真实 embedding 或真实 Qdrant 缺环境变量，标记 BLOCKED，不要把 fake / in-memory 演示写成真实 runtime。
