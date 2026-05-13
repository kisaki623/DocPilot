param(
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FilePath = "README.md",
  [int]$ParseTimeoutSeconds = 120,
  [int]$PollIntervalSeconds = 2
)

$ErrorActionPreference = "Stop"
$baseUrl = $BackendBaseUrl.TrimEnd("/")

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
    [hashtable]$Headers = @{}
  )
  $jsonBody = $Body | ConvertTo-Json -Depth 10 -Compress
  return Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec 120
}

function Invoke-JsonGet {
  param(
    [string]$Uri,
    [hashtable]$Headers = @{}
  )
  return Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers -TimeoutSec 60
}

function Invoke-FileUpload {
  param(
    [string]$Uri,
    [string]$Token,
    [string]$ResolvedFilePath
  )
  $args = @(
    "-sS",
    "-X", "POST",
    $Uri,
    "-H", "Authorization: Bearer $Token",
    "-F", "file=@$ResolvedFilePath"
  )
  $raw = & curl.exe @args
  if ($LASTEXITCODE -ne 0) {
    throw "File upload failed: curl exited with code $LASTEXITCODE."
  }
  return $raw | ConvertFrom-Json
}

function Wait-ParseSuccess {
  param(
    [string]$BaseUrl,
    [long]$DocumentId,
    [hashtable]$Headers,
    [int]$TimeoutSeconds,
    [int]$IntervalSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $detailResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/document/detail?documentId=$DocumentId" -Headers $Headers -TimeoutSec 20
    Assert-ApiSuccess -Response $detailResp -Step "document detail poll"
    $status = [string]$detailResp.data.parseStatus
    if ($status -eq "SUCCESS") {
      return
    }
    if ($status -eq "FAILED") {
      throw "Parse failed."
    }
    Start-Sleep -Seconds $IntervalSeconds
  } while ((Get-Date) -lt $deadline)

  throw "Parse timeout after $TimeoutSeconds seconds."
}

$health = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5
if ($health.StatusCode -ne 200) {
  throw "Backend not ready."
}

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$username = "agent_user_$runId"
$registerResp = Invoke-JsonPost -Uri "$baseUrl/api/auth/register" -Body @{
  username = $username
  nickname = "AgentSmoke"
  password = "DocPilot@Agent2026"
}
Assert-ApiSuccess -Response $registerResp -Step "register"
$token = [string]$registerResp.data.token
$headers = @{ Authorization = "Bearer $token" }

$resolvedFilePath = (Resolve-Path -LiteralPath $FilePath).Path
$uploadResp = Invoke-FileUpload -Uri "$baseUrl/api/file/upload" -Token $token -ResolvedFilePath $resolvedFilePath
Assert-ApiSuccess -Response $uploadResp -Step "file upload"
$fileRecordId = [long]$uploadResp.data.id

$createDocResp = Invoke-JsonPost -Uri "$baseUrl/api/document/create" -Headers $headers -Body @{ fileRecordId = $fileRecordId }
Assert-ApiSuccess -Response $createDocResp -Step "document create"
$documentId = [long]$createDocResp.data.id

$parseResp = Invoke-JsonPost -Uri "$baseUrl/api/task/parse/create" -Headers $headers -Body @{ documentId = $documentId }
Assert-ApiSuccess -Response $parseResp -Step "parse create"
Wait-ParseSuccess -BaseUrl $baseUrl -DocumentId $documentId -Headers $headers -TimeoutSeconds $ParseTimeoutSeconds -IntervalSeconds $PollIntervalSeconds

$summaryRun = Invoke-JsonPost -Uri "$baseUrl/api/ai/agent/run" -Headers $headers -Body @{
  documentId = $documentId
  task = "Please summarize the document for a quick project overview."
}
Assert-ApiSuccess -Response $summaryRun -Step "agent summary run"
if (-not $summaryRun.data.taskId -or $summaryRun.data.taskId -le 0) {
  throw "Agent summary run missing valid taskId."
}
$summaryDecision = [string]$summaryRun.data.decision
if ($summaryDecision -ne "summary_tool") {
  throw "Unexpected summary decision: $summaryDecision"
}
if ([string]::IsNullOrWhiteSpace([string]$summaryRun.data.routingReason)) {
  throw "Agent summary run missing routingReason."
}
if (@($summaryRun.data.matchedKeywords).Count -lt 1) {
  throw "Agent summary run missing matchedKeywords."
}

$qaRun = Invoke-JsonPost -Uri "$baseUrl/api/ai/agent/run" -Headers $headers -Body @{
  documentId = $documentId
  task = "Please answer with evidence: what are the core technical highlights?"
}
Assert-ApiSuccess -Response $qaRun -Step "agent qa run"
if (-not $qaRun.data.taskId -or $qaRun.data.taskId -le 0) {
  throw "Agent qa run missing valid taskId."
}
$qaDecision = [string]$qaRun.data.decision
if ($qaDecision -ne "qa_tool") {
  throw "Unexpected qa decision: $qaDecision"
}
if ([string]::IsNullOrWhiteSpace([string]$qaRun.data.routingReason)) {
  throw "Agent qa run missing routingReason."
}

if (@($summaryRun.data.steps).Count -lt 2) {
  throw "Agent summary run has insufficient steps."
}
if (@($qaRun.data.steps).Count -lt 2) {
  throw "Agent qa run has insufficient steps."
}
if (@($qaRun.data.citations).Count -lt 1) {
  throw "Agent qa run should return at least one citation."
}

$qaTaskId = [long]$qaRun.data.taskId
$qaTaskResp = Invoke-JsonGet -Uri "$baseUrl/api/ai/agent/task/$qaTaskId" -Headers $headers
Assert-ApiSuccess -Response $qaTaskResp -Step "agent task query"
if ([long]$qaTaskResp.data.task.id -ne $qaTaskId) {
  throw "Agent task query returned unexpected taskId: $($qaTaskResp.data.task.id)"
}
if (@($qaTaskResp.data.steps).Count -lt 1) {
  throw "Agent task query returned no steps."
}

$qaStepResp = Invoke-JsonGet -Uri "$baseUrl/api/ai/agent/task/$qaTaskId/steps" -Headers $headers
Assert-ApiSuccess -Response $qaStepResp -Step "agent step query"
if (@($qaStepResp.data).Count -lt 1) {
  throw "Agent step query returned no steps."
}

$result = [PSCustomObject]@{
  documentId = $documentId
  summaryTaskId = $summaryRun.data.taskId
  summaryDecision = $summaryDecision
  summaryRoutingReason = $summaryRun.data.routingReason
  summaryMatchedKeywords = @($summaryRun.data.matchedKeywords)
  summaryStepCount = @($summaryRun.data.steps).Count
  qaTaskId = $qaRun.data.taskId
  qaDecision = $qaDecision
  qaRoutingReason = $qaRun.data.routingReason
  qaMatchedKeywords = @($qaRun.data.matchedKeywords)
  qaStepCount = @($qaRun.data.steps).Count
  qaTaskQueryStepCount = @($qaTaskResp.data.steps).Count
  qaStepQueryCount = @($qaStepResp.data).Count
  qaCitationCount = @($qaRun.data.citations).Count
}

Write-Host "Agent smoke passed:"
$result | ConvertTo-Json -Depth 8
