# Current Task

当前任务：T006 Agent Integration

## 目标

基于 T005 已完成的 Retrieval + QA + SSE 后端闭环，规划 Agent 工具链如何接入新的持久化 RAG 查询能力。

## 范围

下一轮优先做：

- 明确旧 Agent RAG showcase 与新 RagDocumentRetrievalService 的隔离边界；
- 评估 DocumentRagTool 是否接入 T005 RetrievalService；
- Agent Step 记录 retrieval hits / citations 的最小结构；
- Trace 展示 toolName、routingReason、citations 的后端数据准备；
- 不扩大为复杂多 Agent 编排。

## 禁止事项

- 不接前端；
- 不调用真实 embedding / chat API；
- 不写生产级 RAG 夸大文案；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- Agent 工具接入不破坏旧 showcase 路径；
- Agent Step / Trace 能记录最小 retrieval evidence；
- fallback 和 citations 可验证；
- 普通测试不依赖远程 Qdrant 或真实模型 API；
- 受影响后端测试通过。

## 完成后输出

- 修改文件；
- 测试结果；
- 新增能力；
- 下一步是否进入 T007 Eval；
- 可写进简历的一句话。
