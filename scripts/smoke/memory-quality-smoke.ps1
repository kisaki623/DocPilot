param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/memory-quality",
  [ValidateRange(0.0, 1.0)]
  [double]$QualityMinSimilarityThreshold = 0.50,
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend,
  [switch]$ReuseRunningServices
)

$ErrorActionPreference = "Stop"

function Show-MemoryQualityPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Memory quality smoke plan only. No env read, no service start, no data creation."
    delegatesTo = "scripts/smoke/cloud-quality-smoke.ps1"
    smokePrefix = "docpilot-memory-quality"
    artifactRoot = $ArtifactRoot
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
    memoryGates = @(
      "manual active memory enters trace",
      "suggestion extraction produces answer style and task goal candidates",
      "accepted suggestion becomes ACTIVE",
      "ignored suggestion stays out of active memory list",
      "bound-KB conversation trace separates userMemory and ragEvidence",
      "artifact redaction"
    )
    delegatedGates = @(
      "tunnel",
      "backendHealth",
      "frontendRoutes",
      "auth",
      "uploadParseIndex",
      "chunkQuality",
      "mysqlQdrantConsistency",
      "singleDocumentRag",
      "knowledgeBaseRag",
      "noEvidenceThreshold",
      "conversationTrace",
      "permissionIsolation",
      "artifactRedaction",
      "gitStatus"
    )
    boundary = "No remote Docker, no hk-ops, no schema migration, no business-data deletion, no secret printing, no push."
  } | ConvertTo-Json -Depth 5
}

if ($Mode -eq "plan") {
  Show-MemoryQualityPlan
  exit 0
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
$delegate = Join-Path $repoRoot "scripts/smoke/cloud-quality-smoke.ps1"
if (-not (Test-Path -LiteralPath $delegate)) {
  throw "cloud quality smoke delegate is missing"
}

$argsList = @(
  "-NoProfile",
  "-ExecutionPolicy", "Bypass",
  "-File", $delegate,
  "-Mode", $Mode,
  "-BackendBaseUrl", $BackendBaseUrl,
  "-FrontendBaseUrl", $FrontendBaseUrl,
  "-EnvFile", $EnvFile,
  "-ArtifactRoot", $ArtifactRoot,
  "-SmokePrefix", "docpilot-memory-quality",
  "-QualityMinSimilarityThreshold", $QualityMinSimilarityThreshold,
  "-MySqlLocalPort", $MySqlLocalPort,
  "-QdrantLocalPort", $QdrantLocalPort,
  "-IndexVersion", $IndexVersion,
  "-EnableMemoryQualityGate"
)

if ($SkipFrontend) {
  $argsList += "-SkipFrontend"
}
if ($ReuseRunningServices) {
  $argsList += "-ReuseRunningServices"
}

& powershell.exe @argsList
exit $LASTEXITCODE
