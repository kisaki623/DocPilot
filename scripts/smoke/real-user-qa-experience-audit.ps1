param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/audit",
  [ValidateRange(0.0, 1.0)]
  [double]$QualityMinSimilarityThreshold = 0.50,
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend,
  [switch]$ReuseRunningServices,
  [switch]$SkipNaturalCorpusGate,
  [switch]$SkipMultiQueryGate,
  [switch]$SkipFrontendInteractionGate,
  [switch]$SkipMemoryQualityGate
)

$ErrorActionPreference = "Stop"

function Show-RealUserQaAuditPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Real user QA experience audit plan only. No env read, no service start, no data creation."
    delegatesTo = "scripts/smoke/cloud-quality-smoke.ps1"
    smokePrefix = "docpilot-real-user-qa"
    artifactRoot = $ArtifactRoot
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
    naturalCorpusEnabledByDefault = (-not [bool]$SkipNaturalCorpusGate)
    multiQueryGateEnabledByDefault = (-not [bool]$SkipMultiQueryGate)
    frontendInteractionGateEnabledByDefault = ((-not [bool]$SkipFrontend) -and (-not [bool]$SkipFrontendInteractionGate))
    memoryQualityGateEnabledByDefault = (-not [bool]$SkipMemoryQualityGate)
    userExperienceChecks = @(
      "dashboard and key frontend routes are non-empty",
      "document upload parse indexing from temporary txt files",
      "single-document RAG answer has grounded citation",
      "KnowledgeBase multi-document RAG covers both target documents",
      "natural corpus QA covers factual numeric date approval negative summary and no-evidence cases",
      "quote-first citation UI is visible in document and KnowledgeBase pages",
      "Conversation bound to KnowledgeBase has ragTriggered ragRequired evidenceCount and documentHitCounts",
      "ACTIVE user memory and RAG evidence stay separated in context trace",
      "user A and user B permission isolation negative checks",
      "redacted artifact and final git status"
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
      "shortDocumentRag",
      "naturalCorpus",
      "multiQueryRag",
      "frontendInteraction",
      "conversationTrace",
      "memoryQuality",
      "permissionIsolation",
      "artifactRedaction",
      "cleanup",
      "gitStatus"
    )
    statuses = @("PASS", "REVIEW", "BLOCKED", "FAILED_CORE_FLOW", "FAILED_SECURITY_GATE")
    boundary = "No remote Docker, no hk-ops, no schema migration, no business-data deletion, no secret printing, no artifact commit, no push."
  } | ConvertTo-Json -Depth 6
}

if ($Mode -eq "plan") {
  Show-RealUserQaAuditPlan
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
  "-SmokePrefix", "docpilot-real-user-qa",
  "-QualityMinSimilarityThreshold", $QualityMinSimilarityThreshold,
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
if (-not $SkipNaturalCorpusGate) {
  $argsList += "-EnableNaturalCorpusGate"
}
if (-not $SkipMultiQueryGate) {
  $argsList += "-EnableMultiQueryGate"
}
if ((-not $SkipFrontend) -and (-not $SkipFrontendInteractionGate)) {
  $argsList += "-EnableFrontendInteractionGate"
}
if (-not $SkipMemoryQualityGate) {
  $argsList += "-EnableMemoryQualityGate"
}

& powershell.exe @argsList
exit $LASTEXITCODE
