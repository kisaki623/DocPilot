# Current Task

当前任务：T008 parse success 自动触发 RAG indexing 已完成；下一步待确认

## 目标

基于 T001-T007 已完成的 RAG indexing、retrieval、QA、SSE、Agent 接入和离线 smoke 能力，将文档解析成功后的内容自动交给 RAG indexing workflow。

## 范围

T008 已完成：

- 在 parse success 收口后触发 RAG indexing；
- 通过独立 trigger service 隔离 RAG indexing 失败，避免破坏 parse success；
- indexVersion 继续默认使用 1；
- 保持测试不依赖远程 Qdrant、真实 embedding API 或真实大模型；
- 不大改 RocketMQ / Outbox 架构。

下一步候选：

- RAG indexing trigger 进一步 MQ / Outbox 化；
- 或前端小范围展示 RAG evidence / citations。

## 禁止事项

- 不接前端；
- 不调用真实 embedding / chat API；
- 不写生产级 RAG 夸大文案；
- 不做 reranker / 多文档复杂检索；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- parse success 后能自动触发 RAG indexing；
- RAG indexing 失败不影响 parse task/document 成功状态；
- 普通测试不依赖远程 Qdrant 或真实模型 API；
- 受影响后端测试通过。

## T008 输出

- 修改文件；
- 测试结果；
- 新增能力；
- 可写进简历的一句话。
