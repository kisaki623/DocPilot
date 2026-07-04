param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/rag-natural-corpus",
  [ValidateRange(0.0, 1.0)]
  [double]$QualityMinSimilarityThreshold = 0.50,
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend,
  [switch]$ReuseRunningServices,
  [switch]$SkipNaturalCorpusGate,
  [switch]$SkipMultiQueryGate,
  [switch]$SkipFrontendInteractionGate
)

$ErrorActionPreference = "Stop"

function Show-RagNaturalCorpusPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "RAG natural corpus audit plan only. No env read, no service start, no data creation."
    delegatesTo = "scripts/smoke/cloud-quality-smoke.ps1"
    smokePrefix = "docpilot-rag-natural-corpus"
    artifactRoot = $ArtifactRoot
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
    schemaVersion = 2
    defaultCorpusTarget = 3
    defaultDocumentTarget = 12
    defaultCaseTarget = 25
    naturalCorpusEnabledByDefault = (-not [bool]$SkipNaturalCorpusGate)
    multiQueryGateEnabledByDefault = (-not [bool]$SkipMultiQueryGate)
    frontendInteractionGateEnabledByDefault = ((-not [bool]$SkipFrontend) -and (-not [bool]$SkipFrontendInteractionGate))
    caseTypes = @(
      "natural_single_doc_fact",
      "natural_numeric_fact",
      "natural_multi_doc_summary",
      "natural_distractor_control",
      "natural_no_evidence",
      "natural_date_fact",
      "natural_approval_chain",
      "natural_negative_fact",
      "natural_case_coverage",
      "natural_conversation_trace",
      "frontend_interaction",
      "multi_query"
    )
    gates = @(
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
      "answerGrounding",
      "noEvidenceThreshold",
      "conversationTrace",
      "permissionIsolation",
      "frontendInteraction",
      "artifactRedaction",
      "gitStatus"
    )
    boundary = "No remote Docker, no hk-ops, no schema migration, no business-data deletion, no secret printing, no push."
  } | ConvertTo-Json -Depth 5
}

if ($Mode -eq "plan") {
  Show-RagNaturalCorpusPlan
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
  "-SmokePrefix", "docpilot-rag-natural-corpus",
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

& powershell.exe @argsList
exit $LASTEXITCODE
