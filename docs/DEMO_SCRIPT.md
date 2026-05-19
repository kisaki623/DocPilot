# Demo Script

本文记录 DocPilot Agent Showcase 的安全演示脚本。脚本只验证当前已有后端服务，不读取 `backend/.env`，不启动后端或前端，不输出 token、API Key、baseUrl 以外的敏感连接信息、Authorization、prompt、文档正文或完整回答。

## 脚本位置

```powershell
backend/scripts/agent/demo-agent-showcase.ps1
```

## 参数

- `BackendBaseUrl`：默认 `http://localhost:8081`。
- `DocumentId`：必须传入，且需要属于当前 token 对应用户。
- `Mode`：可选 `qa`、`rag`、`summary`，默认 `qa`。
- `Token`：可选；也可以通过当前 shell 的 `DOCPILOT_AUTH_TOKEN` 注入。脚本不会打印 token。

## 示例

```powershell
cd backend
$env:DOCPILOT_AUTH_TOKEN = "<current-user-token>"
powershell -ExecutionPolicy Bypass -File scripts/agent/demo-agent-showcase.ps1 -DocumentId 61 -Mode rag
```

## 输出

脚本只输出脱敏摘要：

- `taskId`
- `decision`
- `routingReasonPresent`
- `matchedKeywordsCount`
- `citationsCount`
- `ragResultsCount`
- `stepsCount`
- `fallbackUsed`
- `toolSelectionSource`

脚本不会输出完整 answer、文档正文、prompt、Authorization 或 secret。

## 当前验证状态

T060 本轮完成了 PowerShell 语法检查。当前 shell 未提供 `DOCPILOT_AUTH_TOKEN`，因此未执行真实 runtime 调用；没有启动服务，也没有连接远程环境。
