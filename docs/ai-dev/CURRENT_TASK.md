# Current Task

当前任务：T005 Retrieval + QA + SSE

## 目标

基于 T004 已完成的 indexing workflow，推进检索、引用组装、QA 和 SSE 闭环。

## 范围

下一轮优先做：

- retrieval service 从 VectorStoreClient 查询同用户、同文档、同版本 chunks；
- QA context builder 使用 chunkId / offsets / contentHash 组织引用；
- 普通 RAG QA service 层最小闭环；
- SSE RAG 问答与普通 QA 语义保持一致；
- fallback、trace 和 citations 的可测试边界；
- service / controller 层本地 stub 测试。

## 禁止事项

- 不接前端；
- 不调用真实 embedding / chat API；
- 不写生产级 RAG 夸大文案；
- 不读取或提交 `.env` / key / secret；
- 不 push。

## 验收标准

- retrieval 能按 userId / documentId / indexVersion 过滤；
- QA context 包含 citations 所需 chunkId、offset、contentHash；
- 普通 QA 和 SSE QA 共享同一检索语义；
- fallback 和 trace 可验证；
- 普通测试不依赖远程 Qdrant 或真实模型 API；
- 受影响后端测试通过。

## 完成后输出

- 修改文件；
- 测试结果；
- 新增能力；
- 下一步是否进入 T005 Retrieval + QA + SSE；
- 可写进简历的一句话。
