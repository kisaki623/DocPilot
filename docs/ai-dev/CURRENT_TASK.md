# Current Task

当前任务：T007 Eval / Retrieval Quality Smoke

## 目标

基于 T001-T006 已完成的 RAG indexing、retrieval、QA、SSE 和 Agent 接入能力，规划一组最小可复现的检索质量验证与面试展示证据。

## 范围

下一轮优先做：

- 设计小规模固定样本文档和问题集；
- 验证 indexing -> retrieval -> QA citations 的端到端行为；
- 输出可读的 retrieval hit / citation / no-evidence 结果摘要；
- 保持测试不依赖远程 Qdrant、真实 embedding API 或真实大模型；
- 不扩大为复杂 eval 平台。

## 禁止事项

- 不接前端；
- 不调用真实 embedding / chat API；
- 不写生产级 RAG 夸大文案；
- 不做 reranker / 多文档复杂检索；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- eval smoke 能证明 metadata filter、citations、no-evidence fallback；
- 普通测试不依赖远程 Qdrant 或真实模型 API；
- 受影响后端测试通过。

## 完成后输出

- 修改文件；
- 测试结果；
- 新增能力；
- 下一步是否进入前端展示或 parse success 自动 indexing；
- 可写进简历的一句话。
