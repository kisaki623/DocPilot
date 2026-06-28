param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/rag-quality",
  [ValidateRange(0.0, 1.0)]
  [double]$QualityMinSimilarityThreshold = 0.50,
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"

function Show-Plan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Small real rerank effect smoke. No env read, no service start, no data creation."
    baseline = "hybrid retrieval with APP_RAG_RERANK_ENABLED=false"
    candidate = "hybrid retrieval with APP_RAG_RERANK_ENABLED=true and existing private rerank provider config"
    gates = @(
      "cloud quality smoke baseline",
      "cloud quality smoke rerank",
      "knowledgeBaseRag coverage comparison",
      "noEvidence regression comparison",
      "rerankApplied check",
      "artifact redaction"
    )
    boundary = "Creates temporary smoke data only in run mode; no remote Docker, no schema migration, no business-data deletion, no secret printing, no push."
  } | ConvertTo-Json -Depth 6
}

function Test-TcpPort([int]$port) {
  try {
    $client = [System.Net.Sockets.TcpClient]::new()
    $iar = $client.BeginConnect("127.0.0.1", $port, $null, $null)
    $success = $iar.AsyncWaitHandle.WaitOne(1000)
    if ($success) {
      $client.EndConnect($iar)
    }
    $client.Close()
    return $success
  } catch {
    return $false
  }
}

function Read-EnvPresence([string]$path) {
  $required = @(
    "APP_RAG_RETRIEVAL_HYBRID_ENABLED",
    "APP_RAG_RERANK_ENABLED",
    "APP_RAG_RERANK_PROVIDER",
    "APP_RAG_RERANK_BASE_URL",
    "APP_RAG_RERANK_MODEL",
    "APP_RAG_RERANK_API_KEY"
  )
  $values = @{}
  if (Test-Path -LiteralPath $path) {
    foreach ($line in [System.IO.File]::ReadAllLines((Resolve-Path $path), [System.Text.Encoding]::UTF8)) {
      if ($line -match '^\s*#') {
        continue
      }
      if ($line -match '^\s*([^=]+)=(.*)$') {
        $values[$Matches[1].Trim()] = $Matches[2].Trim()
      }
    }
  }
  foreach ($key in $required) {
    $value = if ($values.ContainsKey($key)) { [string]$values[$key] } else { "" }
    [PSCustomObject][ordered]@{
      key = $key
      present = $values.ContainsKey($key)
      nonEmpty = -not [string]::IsNullOrWhiteSpace($value)
      trueValue = $value.ToLowerInvariant() -eq "true"
    }
  }
}

function Stop-LocalPort([int]$port) {
  $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  foreach ($connection in $connections) {
    if ($connection.OwningProcess) {
      Stop-Process -Id $connection.OwningProcess -Force -ErrorAction SilentlyContinue
    }
  }
}

function Invoke-CloudSmokeVariant([string]$label, [bool]$rerankEnabled) {
  $repoRoot = Resolve-Path "."
  $delegate = Join-Path $repoRoot "scripts/smoke/cloud-quality-smoke.ps1"
  if (-not (Test-Path -LiteralPath $delegate)) {
    throw "cloud quality smoke script is missing"
  }

  Stop-LocalPort 8081
  if (-not $SkipFrontend) {
    try {
      $frontendUri = [Uri]$FrontendBaseUrl
      if ($frontendUri.Port -gt 0) {
        Stop-LocalPort $frontendUri.Port
      }
    } catch {
      Stop-LocalPort 3000
    }
  }
  Start-Sleep -Seconds 2

  $oldHybrid = $env:APP_RAG_RETRIEVAL_HYBRID_ENABLED
  $oldRerank = $env:APP_RAG_RERANK_ENABLED
  try {
    $env:APP_RAG_RETRIEVAL_HYBRID_ENABLED = "true"
    $env:APP_RAG_RERANK_ENABLED = if ($rerankEnabled) { "true" } else { "false" }
    $argsList = @(
      "-NoProfile",
      "-ExecutionPolicy", "Bypass",
      "-File", $delegate,
      "-Mode", "run",
      "-BackendBaseUrl", $BackendBaseUrl,
      "-FrontendBaseUrl", $FrontendBaseUrl,
      "-EnvFile", $EnvFile,
      "-ArtifactRoot", $ArtifactRoot,
      "-SmokePrefix", "docpilot-rerank-effect-$label",
      "-QualityMinSimilarityThreshold", $QualityMinSimilarityThreshold,
      "-MySqlLocalPort", $MySqlLocalPort,
      "-QdrantLocalPort", $QdrantLocalPort,
      "-IndexVersion", $IndexVersion
    )
    if ($SkipFrontend) {
      $argsList += "-SkipFrontend"
    }
    $output = & powershell.exe @argsList
    if ($LASTEXITCODE -ne 0) {
      throw "cloud quality smoke variant '$label' failed"
    }
    return (($output -join "`n") | ConvertFrom-Json)
  } finally {
    if ($null -eq $oldHybrid) {
      Remove-Item Env:APP_RAG_RETRIEVAL_HYBRID_ENABLED -ErrorAction SilentlyContinue
    } else {
      $env:APP_RAG_RETRIEVAL_HYBRID_ENABLED = $oldHybrid
    }
    if ($null -eq $oldRerank) {
      Remove-Item Env:APP_RAG_RERANK_ENABLED -ErrorAction SilentlyContinue
    } else {
      $env:APP_RAG_RERANK_ENABLED = $oldRerank
    }
  }
}

function Get-KbCheck($result) {
  return $result.gates.knowledgeBaseRag.checks[0]
}

function Get-CountValue($map, [string]$key) {
  if ($null -eq $map) {
    return 0
  }
  $property = $map.PSObject.Properties[$key]
  if ($null -eq $property) {
    return 0
  }
  return [int]$property.Value
}

function Write-SafeArtifact($summary) {
  $dir = Join-Path $ArtifactRoot "rerank-effect"
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $path = Join-Path $dir "latest-summary.json"
  $json = $summary | ConvertTo-Json -Depth 12
  $redactionPatterns = @(
    '(?i)api[_-]?key',
    '(?i)token',
    '(?i)password',
    '(?i)secret',
    '(?i)jdbc:',
    '(?i)mysql://',
    '(?i)qdrant.*://',
    '\b(?!127\.0\.0\.1\b)(?:\d{1,3}\.){3}\d{1,3}\b'
  )
  foreach ($pattern in $redactionPatterns) {
    if ($json -match $pattern) {
      throw "redaction scan failed for rerank effect artifact"
    }
  }
  [System.IO.File]::WriteAllText((Resolve-Path $dir).Path + "\latest-summary.json", $json, [System.Text.UTF8Encoding]::new($false))
  return $path
}

if ($Mode -eq "plan") {
  Show-Plan
  exit 0
}

if ($Mode -eq "dry-run") {
  $presence = @(Read-EnvPresence $EnvFile)
  [PSCustomObject][ordered]@{
    mode = "dry-run"
    envPresence = $presence
    mysqlPortListening = Test-TcpPort $MySqlLocalPort
    qdrantPortListening = Test-TcpPort $QdrantLocalPort
    cloudSmokeExists = Test-Path -LiteralPath "scripts/smoke/cloud-quality-smoke.ps1"
    artifactRoot = $ArtifactRoot
  } | ConvertTo-Json -Depth 8
  exit 0
}

$baseline = Invoke-CloudSmokeVariant "hybrid" $false
$rerank = Invoke-CloudSmokeVariant "rerank" $true

$baselineKb = Get-KbCheck $baseline
$rerankKb = Get-KbCheck $rerank
$baselineDocs = $baselineKb.documentHitCounts
$rerankDocs = $rerankKb.documentHitCounts

$baselineCoveredDocs = @($baselineDocs.PSObject.Properties | Where-Object { [int]$_.Value -gt 0 }).Count
$rerankCoveredDocs = @($rerankDocs.PSObject.Properties | Where-Object { [int]$_.Value -gt 0 }).Count
$coverageDelta = $rerankCoveredDocs - $baselineCoveredDocs
$citationDelta = [int]$rerankKb.qaCitations - [int]$baselineKb.qaCitations
$hitDelta = [int]$rerankKb.retrieveHits - [int]$baselineKb.retrieveHits
$rerankApplied = [bool]$rerankKb.rerankApplied
$noEvidenceRegression = $baseline.gates.noEvidenceThreshold.status -ne "PASS" -or $rerank.gates.noEvidenceThreshold.status -ne "PASS"
$securityRegression = $baseline.gates.permissionIsolation.status -ne "PASS" -or $rerank.gates.permissionIsolation.status -ne "PASS"

$status = "PASS"
if (-not $rerankApplied) {
  $status = "REVIEW"
}
if ($coverageDelta -lt 0 -or $citationDelta -lt 0 -or $noEvidenceRegression -or $securityRegression) {
  $status = "FAILED_CORE_FLOW"
}

$summary = [PSCustomObject][ordered]@{
  overallStatus = $status
  baselineMarker = $baseline.smokeMarker
  rerankMarker = $rerank.smokeMarker
  baseline = [ordered]@{
    overallStatus = $baseline.overallStatus
    retrievalMode = $baselineKb.retrievalMode
    rerankApplied = [bool]$baselineKb.rerankApplied
    retrieveHits = [int]$baselineKb.retrieveHits
    qaCitations = [int]$baselineKb.qaCitations
    coveredDocumentCount = $baselineCoveredDocs
    documentHitCounts = $baselineKb.documentHitCounts
    retrieveVectorScoreSummary = $baselineKb.retrieveVectorScoreSummary
    retrieveRerankScoreSummary = $baselineKb.retrieveRerankScoreSummary
  }
  rerank = [ordered]@{
    overallStatus = $rerank.overallStatus
    retrievalMode = $rerankKb.retrievalMode
    rerankApplied = $rerankApplied
    rerankModel = $rerankKb.rerankModel
    retrieveHits = [int]$rerankKb.retrieveHits
    qaCitations = [int]$rerankKb.qaCitations
    coveredDocumentCount = $rerankCoveredDocs
    documentHitCounts = $rerankKb.documentHitCounts
    retrieveVectorScoreSummary = $rerankKb.retrieveVectorScoreSummary
    retrieveRerankScoreSummary = $rerankKb.retrieveRerankScoreSummary
  }
  comparison = [ordered]@{
    coverageDelta = $coverageDelta
    hitDelta = $hitDelta
    citationDelta = $citationDelta
    rerankApplied = $rerankApplied
    noEvidenceRegression = $noEvidenceRegression
    securityRegression = $securityRegression
  }
}

$artifact = Write-SafeArtifact $summary
$summary | Add-Member -NotePropertyName artifact -NotePropertyValue $artifact
$summary | ConvertTo-Json -Depth 12
