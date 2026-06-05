# Current Task

当前任务：T011 多文档知识库 RAG 已完成；下一步待确认

## 目标

从单文档 RAG 升级为知识库级 RAG，支持在用户指定的知识库内进行跨文档召回、证据合并和引用返回。

## 范围

T011a 已完成：

- 新增 `tb_knowledge_base` 与 `tb_knowledge_base_document`；
- 新增 KnowledgeBase entity / mapper / service / controller；
- 新增 `KnowledgeBaseScopeGuard`；
- 知识库文档关系支持 `ACTIVE / REMOVED` 状态；
- `removeDocument` 采用软删除，`addDocuments` 可恢复 REMOVED 关系；
- 新增知识库创建、列表、详情、添加文档、移除文档 API。

T011b 已完成：

- `VectorSearchRequest` 兼容扩展 `documentIds`；
- InMemory / Qdrant search filter 支持多文档 IN 过滤；
- 新增 KnowledgeBase RAG retrieval service；
- 新增 KnowledgeBase RAG QA service；
- 新增多文档 citation / hit / response 模型；
- 新增 `KnowledgeBaseRagPromptBuilder`，不破坏单文档 `RagPromptBuilder`；
- 新增知识库 retrieval / 非流式 QA API。

下一步候选：

- T012：KnowledgeBase Agent Tool / ToolSpec 接入；
- 或前端小范围展示知识库 RAG citations。

## 禁止事项

- 不做知识库 RAG SSE；
- 不接 Agent / ToolSpec；
- 不接 MCP；
- 不做多文档 eval；
- 不做 reranker；
- 不改前端；
- 不改根 README；
- 不读取或提交 `.env` / key / secret；
- 不调用真实外部 embedding / LLM / 远程 Qdrant；
- 不操作远程服务器；
- 不 push。

## 验收标准

- 知识库创建、列表、详情、添加文档、重复添加、软删除和恢复关系可测；
- 跨用户文档、非 owner KB、越界 vector hit 均被拒绝；
- 多文档 retrieval 强制使用 `userId + documentIds + indexVersion` scope；
- `documentIds` 非空走 IN filter，旧单文档 `documentId` filter 行为保持不变；
- no-evidence 不调用大模型；
- retrieval unavailable fallback 不调用大模型；
- 多文档 citations 包含 `documentId / documentTitle / chunkIndex / score`；
- 测试不依赖真实 embedding、真实大模型或远程 Qdrant。

## T011 输出

- KnowledgeBase 管理底座；
- 多文档 KnowledgeBase RAG retrieval / QA 后端闭环；
- 向量检索多文档 filter 兼容扩展；
- 多文档 prompt / citation 模型；
- 管理、权限、retrieval、QA、controller 和 vector filter 单元测试；
- 更新 ai-dev 简短进度记录。
