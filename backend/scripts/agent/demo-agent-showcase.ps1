param(
  [string]$BackendBaseUrl = "http://localhost:8081",
  [long]$DocumentId = 0,
  [ValidateSet("qa", "rag", "summary")]
  [string]$Mode = "qa",
  [string]$Token = ""
)

$ErrorActionPreference = "Stop"
$baseUrl = $BackendBaseUrl.TrimEnd("/")

if ($DocumentId -le 0) {
  throw "DocumentId is required. Example: .\demo-agent-showcase.ps1 -DocumentId 61 -Mode rag"
}

if ([string]::IsNullOrWhiteSpace($Token) -and -not [string]::IsNullOrWhiteSpace($env:DOCPILOT_AUTH_TOKEN)) {
  $Token = $env:DOCPILOT_AUTH_TOKEN
}

if ([string]::IsNullOrWhiteSpace($Token)) {
  throw "A bearer token is required. Pass -Token or set DOCPILOT_AUTH_TOKEN in the current shell. The script will not print it."
}

function Assert-ApiSuccess {
  param(
    [object]$Response,
    [string]$Step
  )
  if ($null -eq $Response) {
    throw "[$Step] Empty response."
  }
  if ($Response.code -ne 0) {
    throw "[$Step] API failed. code=$($Response.code), message=$($Response.message)"
  }
}

function Invoke-JsonPost {
  param(
    [string]$Uri,
    [hashtable]$Body,
    [hashtable]$Headers
  )
  $jsonBody = $Body | ConvertTo-Json -Depth 10 -Compress
  return Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec 90
}

function Resolve-AgentTask {
  param([string]$RequestedMode)
  switch ($RequestedMode) {
    "summary" {
      return "Please summarize this document for an interview demo. Keep the answer concise."
    }
    "rag" {
      return "Please run RAG retrieval and return topK evidence chunks with score and citation metadata."
    }
    default {
      return "Please answer with evidence: what are the core technical highlights in this document?"
    }
  }
}

function Count-Items {
  param([object]$Items)
  if ($null -eq $Items) {
    return 0
  }
  return @($Items).Count
}

$health = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5
if ($health.StatusCode -ne 200) {
  throw "Backend health check failed."
}

$headers = @{ Authorization = "Bearer $Token" }
$task = Resolve-AgentTask -RequestedMode $Mode
$runResponse = Invoke-JsonPost -Uri "$baseUrl/api/ai/agent/run" -Headers $headers -Body @{
  documentId = $DocumentId
  task = $task
}
Assert-ApiSuccess -Response $runResponse -Step "agent run"

$data = $runResponse.data
if ($null -eq $data) {
  throw "Agent run returned empty data."
}

$summary = [PSCustomObject]@{
  backendBaseUrl = $baseUrl
  documentId = $DocumentId
  mode = $Mode
  taskId = $data.taskId
  decision = $data.decision
  routingReasonPresent = -not [string]::IsNullOrWhiteSpace([string]$data.routingReason)
  matchedKeywordsCount = Count-Items -Items $data.matchedKeywords
  citationsCount = Count-Items -Items $data.citations
  ragResultsCount = Count-Items -Items $data.ragResults
  stepsCount = Count-Items -Items $data.steps
  fallbackUsed = $data.fallbackUsed
  toolSelectionSource = $data.toolSelectionSource
}

Write-Host "Agent showcase demo completed. Redacted summary:"
$summary | ConvertTo-Json -Depth 5
