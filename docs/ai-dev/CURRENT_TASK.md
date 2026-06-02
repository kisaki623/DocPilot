# Current Task

当前任务：T001 RAG 数据模型和 ChunkingService

## 目标

为 DocPilot 增加 RAG chunk 持久化和文本切分能力，为后续 embedding、Qdrant、RAG retrieval 做准备。

## 范围

本轮只做：

- DocumentChunk 数据模型；
- ChunkingService；
- 单元测试；
- 必要 SQL / migration。

## 禁止事项

- 不接 Qdrant；
- 不接真实 embedding；
- 不改前端；
- 不改根 README；
- 不提交 `.env` / key / secret；
- 不做 docs-only 任务。

## 验收标准

- 短文本切成 1 个 chunk；
- 长文本切成多个 chunk；
- overlap 生效；
- chunkIndex 从 0 连续递增；
- contentHash 稳定；
- 空文本处理明确；
- 后端相关测试通过。

## 完成后输出

- 修改文件；
- 测试结果；
- 新增能力；
- 下一步是否进入 T002；
- 可写进简历的一句话。
