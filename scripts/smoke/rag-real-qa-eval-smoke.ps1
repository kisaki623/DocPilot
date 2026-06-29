param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/rag-real-qa",
  [ValidateRange(0.0, 1.0)]
  [double]$QualityMinSimilarityThreshold = 0.50,
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend,
  [switch]$ReuseRunningServices,
  [switch]$SkipRepresentativeCorpusGate,
  [switch]$SkipRealQaHardGate,
  [switch]$SkipRealQaSemanticGate,
  [switch]$SkipRealProviderFaithfulnessGate
)

$ErrorActionPreference = "Stop"

function Show-RagRealQaPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "RAG real QA smoke plan only. No env read, no service start, no data creation."
    delegatesTo = "scripts/smoke/cloud-quality-smoke.ps1"
    smokePrefix = "docpilot-rag-real-qa"
    artifactRoot = $ArtifactRoot
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
    representativeCorpusEnabledByDefault = (-not [bool]$SkipRepresentativeCorpusGate)
    realQaHardGateEnabledByDefault = (-not [bool]$SkipRealQaHardGate)
    realQaSemanticGateEnabledByDefault = (-not [bool]$SkipRealQaSemanticGate)
    realProviderFaithfulnessGateEnabledByDefault = (-not [bool]$SkipRealProviderFaithfulnessGate)
    caseTypes = @(
      "factual_lookup",
      "cross_document_summary",
      "comparison",
      "multi_hop",
      "no_evidence",
      "semantic_distractor",
      "hybrid_keyword_noise",
      "rerank_uplift_candidate",
      "hard_negative",
      "answer_faithfulness",
      "claim_support",
      "numeric_faithfulness",
      "real_provider_faithfulness",
      "representative_corpus",
      "answer_grounding"
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
      "representativeCorpus",
      "answerGrounding",
      "realQaHardGate",
      "realQaSemanticGate",
      "realProviderFaithfulness",
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
  Show-RagRealQaPlan
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
  "-SmokePrefix", "docpilot-rag-real-qa",
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
if (-not $SkipRepresentativeCorpusGate) {
  $argsList += "-EnableRepresentativeCorpusGate"
}
if (-not $SkipRealQaHardGate) {
  $argsList += "-EnableRealQaHardGate"
}
if (-not $SkipRealQaSemanticGate) {
  $argsList += "-EnableRealQaSemanticGate"
}
if (-not $SkipRealProviderFaithfulnessGate) {
  $argsList += "-EnableRealProviderFaithfulnessGate"
}

& powershell.exe @argsList
exit $LASTEXITCODE
