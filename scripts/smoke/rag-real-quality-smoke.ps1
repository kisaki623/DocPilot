param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/rag-quality",
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend,
  [switch]$ReuseRunningServices
)

$ErrorActionPreference = "Stop"

function Show-RagPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "RAG real quality smoke plan only. No env read, no service start, no data creation."
    delegatesTo = "scripts/smoke/cloud-quality-smoke.ps1"
    smokePrefix = "docpilot-rag-real-quality"
    artifactRoot = $ArtifactRoot
    gates = @(
      "tunnel",
      "backendHealth",
      "frontendRoutes",
      "configConsistency",
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
      "cleanup",
      "gitStatus"
    )
    statuses = @("PASS", "REVIEW", "BLOCKED", "FAILED_CORE_FLOW", "FAILED_SECURITY_GATE")
    boundary = "No remote Docker, no hk-ops, no schema migration, no business-data deletion, no secret printing, no push."
  } | ConvertTo-Json -Depth 5
}

if ($Mode -eq "plan") {
  Show-RagPlan
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
  "-SmokePrefix", "docpilot-rag-real-quality",
  "-MySqlLocalPort", $MySqlLocalPort,
  "-QdrantLocalPort", $QdrantLocalPort,
  "-IndexVersion", $IndexVersion
)

if ($SkipFrontend) {
  $argsList += "-SkipFrontend"
}
if ($ReuseRunningServices) {
  $argsList += "-ReuseRunningServices"
}

& powershell.exe @argsList
exit $LASTEXITCODE
