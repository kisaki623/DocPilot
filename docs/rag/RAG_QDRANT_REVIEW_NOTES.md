# RAG / Qdrant Review Notes

记录 T099 对 T092-T098 RAG retrieval eval、Qdrant adapter safety、trace smoke 和边界文档的只读审查结论。

## 审查范围

- `backend/scripts/rag/run-rag-retrieval-eval.ps1`
- `backend/scripts/rag/run-rag-qa-trace-smoke.ps1`
- `backend/src/main/java/com/docpilot/backend/ai/rag/QdrantPointPayload.java`
- `backend/src/main/java/com/docpilot/backend/ai/rag/QdrantVectorStore.java`
- `backend/src/main/java/com/docpilot/backend/ai/rag/RagDebugReporter.java`
- `backend/src/test/java/com/docpilot/backend/ai/rag/*`
- `backend/src/test/resources/rag/rag-retrieval-eval-cases.json`
- RAG / vector store 相关设计文档

## 结论

- 默认 vector store provider 仍是 `in_memory`，不会在默认路径连接外部 Qdrant。
- `qdrant` provider 只有显式配置后才会构造 HTTP adapter；endpoint 为空时 fail-fast，不会发 HTTP。
- T092-T098 新增脚本只运行 Maven 测试并读取 `target` 下的脱敏 JSON 摘要，不读取 `backend/.env`。
- eval fixture 使用 synthetic 文本；生成的 report 只包含 provider、hit / miss、hitRate、index reuse、document isolation 和 failedCaseIds 等聚合字段。
- trace smoke report 只包含 RAG trace 白名单字段，不包含文档正文、prompt、Authorization、API Key、endpoint 原文或 provider response。
- Qdrant adapter 错误信息已覆盖 HTTP 500 / 缺 endpoint 脱敏测试，不输出 endpoint 原文、collection、Authorization、API key 或 response body。
- Qdrant metadata / citation 已收敛为白名单字段，不复制 `documentBody`、`prompt`、`providerResponse` 等非 citation 字段。

## 需要继续保留的边界说明

- `QdrantPointPayload` 仍会把 chunk text 放入 Qdrant payload；这不是默认路径泄漏，但如果未来显式启用真实 Qdrant，chunk 文本会发送到配置的 Qdrant endpoint。真实 runtime 前需要确认 Qdrant 部署归属、网络边界和数据合规口径。
- 现有 Qdrant fake server 测试只证明 adapter request / parser / fallback 形态，不代表真实 Qdrant runtime 已通过。
- 真实 embedding runtime 仍因当前 shell 缺必要环境变量保持 BLOCKED；本次审查没有发起 embedding HTTP。
- T010 完整上传解析链路仍因 MQ disabled / `NoopParseTaskMessageProducer` 保持 BLOCKED。

## 审查命令

- `git diff --name-only 0872202^..6f4883b`
- `rg -n "endpoint|Authorization|api[_-]?key|baseUrl|provider response|prompt|正文|document text|content|response body|Qdrant|qdrant|RagDebug|Trace|Evaluation|fallback" ...`
- `rg -n "APP_RAG|RAG_QDRANT|backend/.env|Get-Content|Write-Host|Invoke-|HttpClient|Authorization|apiKey|secret|prompt|provider response|documentText|PRIVATE_" ...`

## 本轮未做

- 未修改生产代码。
- 未新增 API、数据库表、Maven 依赖或 docker-compose 服务。
- 未读取 `backend/.env`。
- 未真实调用 provider。
- 未真实连接 Qdrant。
