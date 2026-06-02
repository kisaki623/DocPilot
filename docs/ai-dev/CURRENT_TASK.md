# Current Task

当前任务：T002 EmbeddingProvider 抽象

## 目标

为 DocPilot 增加 RAG embedding provider 抽象，为后续 Qdrant 写入、RAG indexing workflow 和真实 retrieval 做准备。

## 范围

本轮只做：

- EmbeddingProvider 接口；
- EmbeddingRequest / EmbeddingResult；
- MockEmbeddingProvider；
- OpenAI-compatible embedding provider；
- 配置适配与兼容层；
- 单元测试。

## 禁止事项

- 不接 Qdrant；
- 不改前端；
- 不改根 README；
- 不操作远程服务器；
- 不读取或提交 `.env` / key / secret；
- 不默认调用真实外部 embedding API。

## 验收标准

- mock embedding deterministic；
- batch embedding 顺序稳定；
- blank input 行为明确；
- OpenAI-compatible provider 未完整配置时不联网；
- OpenAI-compatible provider 测试只打本地 stub；
- 现有 RAG / Agent RAG 测试继续通过。

## 完成后输出

- 修改文件；
- 测试结果；
- 新增能力；
- 下一步是否进入 T003；
- 可写进简历的一句话。
