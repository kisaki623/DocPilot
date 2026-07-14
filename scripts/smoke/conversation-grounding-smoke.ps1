param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/conversation-grounding",
  [string]$SmokePrefix = "docpilot-conversation-grounding",
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend,
  [switch]$ReuseRunningServices
)

$ErrorActionPreference = "Stop"

$script:StartedProcesses = @()
$script:StartedBackendPortPid = $null
$script:StartedFrontendPortPid = $null
$script:RunArtifactDir = $null

function Show-ConversationGroundingPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Conversation grounding route smoke plan only. No env read, no service start, no data creation."
    smokePrefix = $SmokePrefix
    artifactRoot = $ArtifactRoot
    gates = @(
      "no KB uses MODEL_ONLY and never no-evidence refusal",
      "no KB with requested STRICT_KB is normalized to MODEL_ONLY",
      "AUTO_RAG obvious small talk does not trigger RAG",
      "AUTO_RAG substantive no evidence falls back to model",
      "AUTO_RAG explicitly required no evidence refuses and skips the model",
      "STRICT_KB no evidence refuses and skips the model",
      "AUTO_RAG evidence returns citations",
      "T27 RECENT_TURNS keeps prior turns inside the same conversation",
      "T28 RECENT_TURNS does not leak prior turns into a different conversation",
      "trace exposes groundingPolicy routeDecision llmCalled modelSkipped",
      "optional frontend route reachability",
      "artifact redaction"
    )
    plannedCases = @(
      "no-kb-model-only",
      "no-kb-strict-normalized",
      "auto-smalltalk-no-rag",
      "auto-no-evidence-fallback-model",
      "auto-required-no-evidence-refusal",
      "strict-no-evidence-refusal",
      "auto-rag-evidence-citations",
      "T27-recent-turns-context",
      "T28-recent-turns-session-isolation"
    )
    boundary = "No remote Docker, no hk-ops, no schema migration, no business-data deletion, no collection clearing, no secret printing, no raw prompt/evidence/answer artifact, no push."
  } | ConvertTo-Json -Depth 6
}

function Test-SafeName([string]$value, [string]$name) {
  if ([string]::IsNullOrWhiteSpace($value) -or $value -notmatch '^[A-Za-z0-9._/\-]+$' -or $value -match '\.\.') {
    throw "${name}_invalid"
  }
}

function Test-TcpPort([int]$port) {
  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $async = $client.BeginConnect("127.0.0.1", $port, $null, $null)
    if (-not $async.AsyncWaitHandle.WaitOne(800)) {
      return $false
    }
    $client.EndConnect($async)
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Get-PortOwner([int]$port) {
  $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($conn) {
    return [int]$conn.OwningProcess
  }
  return $null
}

function Wait-BackendHealth([int]$timeoutSeconds = 120) {
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  do {
    try {
      $health = Invoke-RestMethod -Method GET -Uri ($BackendBaseUrl.TrimEnd("/") + "/actuator/health") -TimeoutSec 5
      if ($health.status -eq "UP") {
        return $true
      }
    } catch {
      Start-Sleep -Seconds 2
    }
  } while ((Get-Date) -lt $deadline)
  return $false
}

function Wait-FrontendRoute([int]$timeoutSeconds = 90) {
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  do {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri ($FrontendBaseUrl.TrimEnd("/") + "/conversations") -TimeoutSec 5
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        return $true
      }
    } catch {
      Start-Sleep -Seconds 2
    }
  } while ((Get-Date) -lt $deadline)
  return $false
}

function Start-TunnelIfNeeded {
  $mysqlReady = Test-TcpPort $MySqlLocalPort
  $qdrantReady = Test-TcpPort $QdrantLocalPort
  if ($mysqlReady -and $qdrantReady) {
    return "reused"
  }
  if ($mysqlReady -or $qdrantReady) {
    throw "one_tunnel_port_already_in_use"
  }
  if ($ReuseRunningServices) {
    throw "tunnel_not_reachable"
  }
  $scriptDir = $PSScriptRoot
  if ([string]::IsNullOrWhiteSpace($scriptDir)) {
    throw "script_root_unavailable"
  }
  $repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
  $tunnelScript = Join-Path $repoRoot "scripts/dev/start-cloud-tunnels.ps1"
  if (-not (Test-Path -LiteralPath $tunnelScript)) {
    throw "tunnel_script_missing"
  }
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $tunnelScript -EnvFile $EnvFile -MySqlLocalPort $MySqlLocalPort -QdrantLocalPort $QdrantLocalPort | Out-Null
  if (-not ((Test-TcpPort $MySqlLocalPort) -and (Test-TcpPort $QdrantLocalPort))) {
    throw "tunnel_not_ready"
  }
  return "started"
}

function Start-BackendIfNeeded {
  if (Wait-BackendHealth 3) {
    return "reused"
  }
  if ($ReuseRunningServices) {
    throw "backend_not_healthy"
  }
  $repoRoot = Get-Location
  $backendOut = Join-Path $script:RunArtifactDir "backend.out.log"
  $backendErr = Join-Path $script:RunArtifactDir "backend.err.log"
  $process = Start-Process -FilePath "mvn.cmd" `
    -ArgumentList @("spring-boot:run", "-Dspring-boot.run.profiles=local") `
    -WorkingDirectory (Join-Path $repoRoot "backend") `
    -WindowStyle Hidden `
    -RedirectStandardOutput $backendOut `
    -RedirectStandardError $backendErr `
    -PassThru
  $script:StartedProcesses += $process
  if (-not (Wait-BackendHealth 150)) {
    throw "backend_health_timeout"
  }
  $script:StartedBackendPortPid = Get-PortOwner ([Uri]$BackendBaseUrl).Port
  return "started"
}

function Start-FrontendIfNeeded {
  if ($SkipFrontend) {
    return "skipped"
  }
  if (Wait-FrontendRoute 3) {
    return "reused"
  }
  if ($ReuseRunningServices) {
    throw "frontend_not_reachable"
  }
  $frontendUri = [Uri]$FrontendBaseUrl
  if ($frontendUri.Host -notin @("127.0.0.1", "localhost", "::1")) {
    throw "frontend_host_must_be_loopback"
  }
  if ($frontendUri.Port -le 0) {
    throw "frontend_port_invalid"
  }
  $repoRoot = Get-Location
  $frontendOut = Join-Path $script:RunArtifactDir "frontend.out.log"
  $frontendErr = Join-Path $script:RunArtifactDir "frontend.err.log"
  $process = Start-Process -FilePath "npm.cmd" `
    -ArgumentList @("run", "dev", "--", "-H", $frontendUri.Host, "-p", ([string]$frontendUri.Port)) `
    -WorkingDirectory (Join-Path $repoRoot "frontend") `
    -WindowStyle Hidden `
    -RedirectStandardOutput $frontendOut `
    -RedirectStandardError $frontendErr `
    -PassThru
  $script:StartedProcesses += $process
  if (-not (Wait-FrontendRoute 120)) {
    throw "frontend_route_timeout"
  }
  $script:StartedFrontendPortPid = Get-PortOwner $frontendUri.Port
  return "started"
}

function Stop-StartedProcesses {
  foreach ($pidValue in @($script:StartedBackendPortPid, $script:StartedFrontendPortPid)) {
    if ($pidValue) {
      $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
      if ($process) {
        Stop-Process -Id $pidValue -Force
      }
    }
  }
  foreach ($process in $script:StartedProcesses) {
    $running = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
    if ($running) {
      Stop-Process -Id $process.Id -Force
    }
  }
}

function Invoke-JsonApi([string]$method, [string]$path, $body = $null, [string]$token = "") {
  $headers = @{}
  if ($token) {
    $headers["Authorization"] = "Bearer $token"
  }
  $params = @{
    Method = $method
    Uri = ($BackendBaseUrl.TrimEnd("/") + $path)
    Headers = $headers
    TimeoutSec = 180
  }
  if ($null -ne $body) {
    $params["ContentType"] = "application/json; charset=utf-8"
    $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20))
  }
  $response = Invoke-RestMethod @params
  if ($response.code -ne 0) {
    throw "api_failed_${method}_${path}_code_$($response.code)"
  }
  return $response.data
}

function Upload-SmokeFile([string]$path, [string]$token) {
  Add-Type -AssemblyName System.Net.Http
  $client = [System.Net.Http.HttpClient]::new()
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, ($BackendBaseUrl.TrimEnd("/") + "/api/file/upload"))
  $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token)
  $multipart = [System.Net.Http.MultipartFormDataContent]::new()
  $stream = [System.IO.File]::OpenRead($path)
  try {
    $fileContent = [System.Net.Http.StreamContent]::new($stream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/plain")
    $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($path))
    $request.Content = $multipart
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $parsed = $text | ConvertFrom-Json
    if (-not $response.IsSuccessStatusCode -or $parsed.code -ne 0) {
      throw "upload_failed_status_$([int]$response.StatusCode)_code_$($parsed.code)"
    }
    return $parsed.data
  } finally {
    $stream.Dispose()
    $multipart.Dispose()
    $request.Dispose()
    $client.Dispose()
  }
}

function Wait-ParseSuccess([long]$documentId, [string]$token) {
  $deadline = (Get-Date).AddSeconds(240)
  do {
    $detail = Invoke-JsonApi "GET" "/api/document/detail?documentId=$documentId" $null $token
    $status = [string]$detail.parseStatus
    if ($status -eq "SUCCESS") {
      return $detail
    }
    if ($status -eq "FAILED") {
      throw "parse_failed_document_$documentId"
    }
    Start-Sleep -Seconds 3
  } while ((Get-Date) -lt $deadline)
  throw "parse_timeout_document_$documentId"
}

function Assert-True([bool]$condition, [string]$name) {
  if (-not $condition) {
    throw "assertion_failed_${name}"
  }
}

function Send-And-Trace($conversationId, [string]$content, [string]$token, [string]$policy = "") {
  $body = [ordered]@{ content = $content }
  if ($policy) {
    $body.groundingPolicy = $policy
  }
  $message = Invoke-JsonApi "POST" "/api/conversations/$conversationId/messages" $body $token
  $trace = Invoke-JsonApi "GET" "/api/conversations/$conversationId/messages/$($message.messageId)/trace" $null $token
  return [ordered]@{ message = $message; trace = $trace }
}

function New-CaseSummary([string]$caseId, $trace, [int]$citationCount = 0, [string]$extra = "") {
  return [ordered]@{
    caseId = $caseId
    pass = $true
    groundingPolicy = [string]$trace.groundingPolicy
    routeDecision = [string]$trace.routeDecision
    ragTriggered = [bool]$trace.ragTriggered
    ragRequired = [bool]$trace.ragRequired
    evidenceCount = [int]$trace.evidenceCount
    llmCalled = [bool]$trace.llmCalled
    modelSkipped = [bool]$trace.modelSkipped
    citationCount = $citationCount
    extra = $extra
  }
}

if ($Mode -eq "plan") {
  Show-ConversationGroundingPlan
  exit 0
}

Test-SafeName $SmokePrefix "smoke_prefix"

$repoRoot = Get-Location
if ($Mode -eq "dry-run") {
  [PSCustomObject][ordered]@{
    mode = "dry-run"
    smokePrefix = $SmokePrefix
    checks = @(
      [ordered]@{ name = "backendDirExists"; pass = (Test-Path -LiteralPath (Join-Path $repoRoot "backend")) },
      [ordered]@{ name = "frontendDirExists"; pass = (Test-Path -LiteralPath (Join-Path $repoRoot "frontend")) },
      [ordered]@{ name = "migrationScriptExists"; pass = (Test-Path -LiteralPath (Join-Path $repoRoot "backend/src/main/resources/sql/008_add_context_trace_grounding.sql")) },
      [ordered]@{ name = "citationMigrationScriptExists"; pass = (Test-Path -LiteralPath (Join-Path $repoRoot "backend/src/main/resources/sql/009_add_context_trace_citations.sql")) },
      [ordered]@{ name = "noDataCreated"; pass = $true }
    )
    plannedCases = @(
      "no-kb-model-only",
      "no-kb-strict-normalized",
      "auto-smalltalk-no-rag",
      "auto-no-evidence-fallback-model",
      "auto-required-no-evidence-refusal",
      "strict-no-evidence-refusal",
      "auto-rag-evidence-citations",
      "T27-recent-turns-context",
      "T28-recent-turns-session-isolation"
    )
  } | ConvertTo-Json -Depth 6
  exit 0
}

$marker = "$($SmokePrefix)-$(Get-Date -Format 'yyyyMMddHHmmss')-$([guid]::NewGuid().ToString('N').Substring(0, 6))"
$script:RunArtifactDir = Join-Path $ArtifactRoot $marker
New-Item -ItemType Directory -Force -Path $script:RunArtifactDir | Out-Null

$cases = New-Object System.Collections.Generic.List[object]
$overallStatus = "PASS"
$safeFailure = ""

try {
  $tunnelStatus = Start-TunnelIfNeeded
  $backendStatus = Start-BackendIfNeeded
  $frontendStatus = Start-FrontendIfNeeded

  $username = ("cg" + (Get-Date -Format "MMddHHmmss") + ([guid]::NewGuid().ToString("N").Substring(0, 6))).Substring(0, 18)
  $password = "SmokePass2026!"
  $auth = Invoke-JsonApi "POST" "/api/auth/register" ([ordered]@{ username = $username; password = $password; nickname = "Conversation Grounding Smoke" })
  $token = [string]$auth.token
  $userId = [long]$auth.userId

  $convNoKb = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "No KB $marker"; contextMode = "RECENT_TURNS" }) $token
  $case1 = Send-And-Trace $convNoKb.conversationId "Please explain Newton's first law in one sentence." $token
  Assert-True ($case1.trace.groundingPolicy -eq "MODEL_ONLY") "no_kb_policy"
  Assert-True ($case1.trace.routeDecision -eq "MODEL_ONLY") "no_kb_route"
  Assert-True (-not [bool]$case1.trace.ragTriggered) "no_kb_rag_triggered_false"
  Assert-True (-not [bool]$case1.trace.ragRequired) "no_kb_rag_required_false"
  Assert-True ([int]$case1.trace.evidenceCount -eq 0) "no_kb_evidence_zero"
  Assert-True ([bool]$case1.trace.llmCalled) "no_kb_llm_called"
  Assert-True (-not [bool]$case1.trace.modelSkipped) "no_kb_model_not_skipped"
  Assert-True (($case1.message.citations | Measure-Object).Count -eq 0) "no_kb_citations_empty"
  Assert-True (($case1.message.content -notmatch "根据提供的文档上下文无法回答") -and ($case1.message.content -notmatch "资料不足")) "no_kb_no_refusal"
  $cases.Add((New-CaseSummary "no-kb-model-only" $case1.trace 0))

  $case1b = Send-And-Trace $convNoKb.conversationId "Even if STRICT_KB was requested, answer this general question: what is H2O?" $token "STRICT_KB"
  Assert-True ($case1b.trace.groundingPolicy -eq "MODEL_ONLY") "no_kb_strict_normalized"
  Assert-True ([bool]$case1b.trace.llmCalled -and -not [bool]$case1b.trace.modelSkipped) "no_kb_strict_calls_model"
  $cases.Add((New-CaseSummary "no-kb-strict-normalized" $case1b.trace 0))

  $projectCode = ([string][char]0x84DD) + ([string][char]0x6865)
  $projectCodePattern = [regex]::Escape($projectCode)
  $convRecentA = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Recent Turns A $marker"; contextMode = "RECENT_TURNS" }) $token
  Send-And-Trace $convRecentA.conversationId "In this conversation, remember the project code: $projectCode." $token | Out-Null
  $caseRecent = Send-And-Trace $convRecentA.conversationId "What project code did I mention earlier in this conversation?" $token
  Assert-True ($caseRecent.trace.contextMode -eq "RECENT_TURNS") "recent_turns_context_mode"
  Assert-True ($caseRecent.trace.groundingPolicy -eq "MODEL_ONLY") "recent_turns_policy"
  Assert-True ($caseRecent.trace.routeDecision -eq "MODEL_ONLY") "recent_turns_route"
  Assert-True ([int]$caseRecent.trace.recentMessageCount -ge 2) "recent_turns_previous_pair_used"
  Assert-True ([bool]$caseRecent.trace.llmCalled -and -not [bool]$caseRecent.trace.modelSkipped) "recent_turns_calls_model"
  Assert-True (($caseRecent.message.citations | Measure-Object).Count -eq 0) "recent_turns_citations_empty"
  Assert-True ([string]$caseRecent.message.content -match $projectCodePattern) "recent_turns_answer_contains_project_code"
  $cases.Add((New-CaseSummary "T27-recent-turns-context" $caseRecent.trace 0 "sameConversationProjectCodeRetained=true"))

  $convRecentB = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Recent Turns B $marker"; contextMode = "RECENT_TURNS" }) $token
  $caseIsolation = Send-And-Trace $convRecentB.conversationId "What is the project code?" $token
  Assert-True ($caseIsolation.trace.contextMode -eq "RECENT_TURNS") "recent_turns_isolation_context_mode"
  Assert-True ($caseIsolation.trace.groundingPolicy -eq "MODEL_ONLY") "recent_turns_isolation_policy"
  Assert-True ($caseIsolation.trace.routeDecision -eq "MODEL_ONLY") "recent_turns_isolation_route"
  Assert-True ([int]$caseIsolation.trace.recentMessageCount -eq 0) "recent_turns_isolation_no_previous_messages"
  Assert-True ([bool]$caseIsolation.trace.llmCalled -and -not [bool]$caseIsolation.trace.modelSkipped) "recent_turns_isolation_calls_model"
  Assert-True (($caseIsolation.message.citations | Measure-Object).Count -eq 0) "recent_turns_isolation_citations_empty"
  Assert-True ([string]$caseIsolation.message.content -notmatch $projectCodePattern) "recent_turns_isolation_no_project_code_leak"
  $cases.Add((New-CaseSummary "T28-recent-turns-session-isolation" $caseIsolation.trace 0 "crossConversationProjectCodeLeaked=false"))

  $emptyKb = Invoke-JsonApi "POST" "/api/knowledge-bases" ([ordered]@{ name = "Empty KB $marker"; description = "temporary grounding smoke empty kb" }) $token
  $convAutoGeneric = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Auto Generic $marker"; contextMode = "RECENT_TURNS"; boundKnowledgeBaseId = $emptyKb.id }) $token
  $case2 = Send-And-Trace $convAutoGeneric.conversationId "Hello" $token
  Assert-True ($case2.trace.groundingPolicy -eq "AUTO_RAG") "auto_smalltalk_policy"
  Assert-True ($case2.trace.routeDecision -eq "AUTO_INTENT_NOT_TRIGGERED_MODEL") "auto_smalltalk_route"
  Assert-True (-not [bool]$case2.trace.ragTriggered) "auto_smalltalk_rag_not_triggered"
  Assert-True ([bool]$case2.trace.llmCalled -and -not [bool]$case2.trace.modelSkipped) "auto_smalltalk_calls_model"
  $cases.Add((New-CaseSummary "auto-smalltalk-no-rag" $case2.trace 0))

  $convAutoNoEvidence = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Auto No Evidence $marker"; contextMode = "AGENT_MEMORY"; boundKnowledgeBaseId = $emptyKb.id }) $token
  $case3 = Send-And-Trace $convAutoNoEvidence.conversationId "What does $($marker)-EMPTY-EVIDENCE mean?" $token "AUTO_RAG"
  Assert-True ($case3.trace.groundingPolicy -eq "AUTO_RAG") "auto_no_evidence_policy"
  Assert-True ($case3.trace.routeDecision -eq "AUTO_NO_EVIDENCE_MODEL") "auto_no_evidence_route"
  Assert-True ([bool]$case3.trace.ragTriggered) "auto_no_evidence_rag_triggered"
  Assert-True (-not [bool]$case3.trace.ragRequired) "auto_no_evidence_not_required"
  Assert-True ([int]$case3.trace.evidenceCount -eq 0) "auto_no_evidence_zero"
  Assert-True ([bool]$case3.trace.llmCalled -and -not [bool]$case3.trace.modelSkipped) "auto_no_evidence_calls_model"
  $cases.Add((New-CaseSummary "auto-no-evidence-fallback-model" $case3.trace 0))

  $case3b = Send-And-Trace $convAutoNoEvidence.conversationId "Based on the documents, answer whether $($marker)-EMPTY-EVIDENCE exists." $token "AUTO_RAG"
  Assert-True ($case3b.trace.groundingPolicy -eq "AUTO_RAG") "auto_required_no_evidence_policy"
  Assert-True ($case3b.trace.routeDecision -eq "AUTO_REQUIRED_NO_EVIDENCE_FALLBACK") "auto_required_no_evidence_route"
  Assert-True ([bool]$case3b.trace.ragTriggered) "auto_required_no_evidence_rag_triggered"
  Assert-True ([bool]$case3b.trace.ragRequired) "auto_required_no_evidence_required"
  Assert-True ([int]$case3b.trace.evidenceCount -eq 0) "auto_required_no_evidence_zero"
  Assert-True (-not [bool]$case3b.trace.llmCalled -and [bool]$case3b.trace.modelSkipped) "auto_required_no_evidence_skips_model"
  Assert-True ($case3b.trace.fallbackReason -eq "REQUIRED_EVIDENCE_NO_EVIDENCE") "auto_required_no_evidence_fallback_reason"
  $cases.Add((New-CaseSummary "auto-required-no-evidence-refusal" $case3b.trace 0))

  $case4 = Send-And-Trace $convAutoNoEvidence.conversationId "Only use the knowledge base: what does $($marker)-STRICT-MISSING mean?" $token "STRICT_KB"
  Assert-True ($case4.trace.groundingPolicy -eq "STRICT_KB") "strict_no_evidence_policy"
  Assert-True ($case4.trace.routeDecision -eq "STRICT_NO_EVIDENCE_FALLBACK") "strict_no_evidence_route"
  Assert-True ([bool]$case4.trace.ragTriggered) "strict_no_evidence_rag_triggered"
  Assert-True ([bool]$case4.trace.ragRequired) "strict_no_evidence_required"
  Assert-True ([int]$case4.trace.evidenceCount -eq 0) "strict_no_evidence_zero"
  Assert-True (-not [bool]$case4.trace.llmCalled -and [bool]$case4.trace.modelSkipped) "strict_no_evidence_skips_model"
  Assert-True ($case4.trace.fallbackReason -eq "STRICT_KB_NO_EVIDENCE") "strict_no_evidence_fallback_reason"
  $cases.Add((New-CaseSummary "strict-no-evidence-refusal" $case4.trace 0))

  $docText = @(
    "DocPilot conversation grounding runtime smoke $marker.",
    "The marker $($marker)-AUTO-EVIDENCE proves AUTO_RAG should retrieve knowledge-base evidence and return citations.",
    "This temporary text document exists only for route verification."
  ) -join "`n"
  $docPath = Join-Path $script:RunArtifactDir "$($marker)-evidence.txt"
  [System.IO.File]::WriteAllText($docPath, $docText, [System.Text.UTF8Encoding]::new($false))
  $file = Upload-SmokeFile $docPath $token
  $doc = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $file.id }) $token
  Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $doc.id }) $token | Out-Null
  Wait-ParseSuccess ([long]$doc.id) $token | Out-Null
  $evidenceKb = Invoke-JsonApi "POST" "/api/knowledge-bases" ([ordered]@{ name = "Evidence KB $marker"; description = "temporary grounding smoke evidence kb" }) $token
  Invoke-JsonApi "POST" "/api/knowledge-bases/$($evidenceKb.id)/documents" ([ordered]@{ documentIds = @($doc.id) }) $token | Out-Null
  $convEvidence = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Auto Evidence $marker"; contextMode = "AGENT_MEMORY"; boundKnowledgeBaseId = $evidenceKb.id }) $token
  $case5 = Send-And-Trace $convEvidence.conversationId "What does $($marker)-AUTO-EVIDENCE prove?" $token "AUTO_RAG"
  $citationCount = ($case5.message.citations | Measure-Object).Count
  Assert-True ($case5.trace.groundingPolicy -eq "AUTO_RAG") "auto_evidence_policy"
  Assert-True ($case5.trace.routeDecision -eq "AUTO_RAG_EVIDENCE") "auto_evidence_route"
  Assert-True ([bool]$case5.trace.ragTriggered) "auto_evidence_rag_triggered"
  Assert-True (-not [bool]$case5.trace.ragRequired) "auto_evidence_not_required"
  Assert-True ([int]$case5.trace.evidenceCount -gt 0) "auto_evidence_count"
  Assert-True ([bool]$case5.trace.llmCalled -and -not [bool]$case5.trace.modelSkipped) "auto_evidence_calls_model"
  Assert-True ($citationCount -gt 0) "auto_evidence_citations"
  $cases.Add((New-CaseSummary "auto-rag-evidence-citations" $case5.trace $citationCount))

  $artifact = [ordered]@{
    marker = $marker
    status = $overallStatus
    generatedAt = (Get-Date).ToString("o")
    userId = [string]$userId
    tunnel = $tunnelStatus
    backend = $backendStatus
    frontend = $frontendStatus
    indexVersion = $IndexVersion
    cases = $cases
    artifactPolicy = "Sanitized: route booleans/counts and ids only; no token, password, raw prompt, raw answer, raw evidence, provider output, cloud address or connection string."
  }
  $artifactPath = Join-Path $script:RunArtifactDir "artifact.json"
  [System.IO.File]::WriteAllText($artifactPath, ($artifact | ConvertTo-Json -Depth 30), [System.Text.UTF8Encoding]::new($false))

  [PSCustomObject][ordered]@{
    status = $overallStatus
    marker = $marker
    artifact = $artifactPath
    caseCount = $cases.Count
  } | ConvertTo-Json -Depth 6
} catch {
  $overallStatus = "FAILED_CORE_FLOW"
  $safeFailure = [string]$_.Exception.Message
  $artifact = [ordered]@{
    marker = $marker
    status = $overallStatus
    generatedAt = (Get-Date).ToString("o")
    safeFailure = $safeFailure
    cases = $cases
    artifactPolicy = "Sanitized failure summary only; no token, password, raw prompt, raw answer, raw evidence, provider output, cloud address or connection string."
  }
  $artifactPath = Join-Path $script:RunArtifactDir "artifact.json"
  [System.IO.File]::WriteAllText($artifactPath, ($artifact | ConvertTo-Json -Depth 30), [System.Text.UTF8Encoding]::new($false))
  Write-Error ("{0}|conversationGrounding|{1}" -f $overallStatus, $safeFailure)
  exit 1
} finally {
  Stop-StartedProcesses
}
