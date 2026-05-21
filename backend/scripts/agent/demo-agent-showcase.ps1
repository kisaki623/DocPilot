param(
  [string]$BackendBaseUrl = "http://localhost:8081",
  [long]$DocumentId = 0,
  [ValidateSet("qa", "rag", "summary")]
  [string]$Mode = "qa",
  [string]$Token = ""
)

$ErrorActionPreference = "Stop"
$baseUrl = $BackendBaseUrl.TrimEnd("/")

if ([string]::IsNullOrWhiteSpace($Token) -and -not [string]::IsNullOrWhiteSpace($env:DOCPILOT_AUTH_TOKEN)) {
  $Token = $env:DOCPILOT_AUTH_TOKEN
}

function Resolve-BackendLocation {
  param([string]$Value)
  try {
    $uri = [System.Uri]::new($Value)
    if ($uri.IsLoopback -or $uri.Host -eq "localhost") {
      return "localhost"
    }
    return "remote-redacted"
  } catch {
    return "unknown"
  }
}

function New-SanitizedSummary {
  param(
    [bool]$BackendReachable = $false,
    [bool]$AgentRunOk = $false,
    [string]$Decision = "not_run",
    [int]$RagRetrievedCount = 0,
    [int]$CitationCount = 0,
    [int]$TraceStepCount = 0,
    [string]$Note = ""
  )
  return [PSCustomObject]@{
    backendReachable = $BackendReachable
    backendLocation = Resolve-BackendLocation -Value $baseUrl
    authTokenPresent = -not [string]::IsNullOrWhiteSpace($Token)
    documentIdPresent = $DocumentId -gt 0
    agentRunOk = $AgentRunOk
    decision = $Decision
    ragRetrievedCount = $RagRetrievedCount
    citationCount = $CitationCount
    traceStepCount = $TraceStepCount
    mode = $Mode
    note = $Note
  }
}

function Write-SanitizedSummary {
  param([object]$Summary)
  Write-Host "Agent showcase demo sanitized summary:"
  $Summary | ConvertTo-Json -Depth 5
}

if ($DocumentId -le 0 -or [string]::IsNullOrWhiteSpace($Token)) {
  Write-SanitizedSummary -Summary (New-SanitizedSummary -Note "missing-token-or-document-id")
  Write-Host "Provide a current-shell token and a parsed documentId to run the live Agent demo. No token or endpoint value was printed."
  exit 0
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

try {
  $health = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5
  if ($health.StatusCode -ne 200) {
    Write-SanitizedSummary -Summary (New-SanitizedSummary -Note "backend-health-not-ready")
    exit 2
  }
} catch {
  Write-SanitizedSummary -Summary (New-SanitizedSummary -Note "backend-health-not-ready")
  exit 2
}

$headers = @{ Authorization = "Bearer $Token" }
$task = Resolve-AgentTask -RequestedMode $Mode
try {
  $runResponse = Invoke-JsonPost -Uri "$baseUrl/api/ai/agent/run" -Headers $headers -Body @{
    documentId = $DocumentId
    task = $task
  }
  Assert-ApiSuccess -Response $runResponse -Step "agent run"
} catch {
  Write-SanitizedSummary -Summary (New-SanitizedSummary -BackendReachable $true -Note "agent-run-failed")
  exit 3
}

if ($null -eq $runResponse.data) {
  Write-SanitizedSummary -Summary (New-SanitizedSummary -BackendReachable $true -Note "agent-run-empty-data")
  exit 3
}

$data = $runResponse.data
$summary = New-SanitizedSummary `
  -BackendReachable $true `
  -AgentRunOk $true `
  -Decision ([string]$data.decision) `
  -RagRetrievedCount (Count-Items -Items $data.ragResults) `
  -CitationCount (Count-Items -Items $data.citations) `
  -TraceStepCount (Count-Items -Items $data.steps) `
  -Note "completed"

Write-SanitizedSummary -Summary $summary
