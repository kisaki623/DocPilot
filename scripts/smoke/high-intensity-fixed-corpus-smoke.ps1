param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/high-intensity-acceptance",
  [ValidateRange(0.0, 1.0)]
  [double]$QualityMinSimilarityThreshold = 0.50,
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend,
  [switch]$ReuseRunningServices
)

$ErrorActionPreference = "Stop"

function Show-FixedCorpusPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "High intensity fixed corpus smoke plan only. No env read, no service start, no data creation."
    delegatesTo = "scripts/smoke/cloud-quality-smoke.ps1"
    smokePrefix = "docpilot-high-intensity-fixed-corpus"
    artifactRoot = $ArtifactRoot
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
    corpusVersion = "2026-07-12-fixed-business-corpus-v1"
    corpusKeys = @(
      "CONTRACT_ALPHA",
      "SLA_BETA",
      "API_POLICY",
      "INCIDENT_REVIEW",
      "DECOY_DRAFT",
      "PROMPT_INJECTION"
    )
    knowledgeBaseKeys = @("KB_CORE", "KB_NOISY")
    duplicateUploadCase = "T02_serial_duplicate_upload"
    caseIds = @(
      "T06_contract_precise_numbers",
      "T07_contract_paraphrase_payment",
      "T08_contract_wrong_premise_penalty",
      "T09_api_rotation_conflict",
      "T10_sla_incident_calculation",
      "T11_cross_document_risk_controls",
      "T12_multi_hop_approval",
      "T13_hard_negative_audit_retention",
      "T14_strict_no_evidence",
      "T15_prompt_injection"
    )
    gates = @(
      "tunnel",
      "backendHealth",
      "auth",
      "uploadParseIndex",
      "fixedBusinessCorpus",
      "artifactRedaction",
      "cleanup",
      "gitStatus"
    )
    artifactPolicy = "Stores only ids, document keys, booleans, counts and failure codes. It does not store raw question, answer, snippet, quote, prompt or evidence context."
    boundary = "Covers T02 serial duplicate upload and T06-T15 API quality matrix only. It does not cover concurrent upload, KB lifecycle, Memory, Agent, weak network, multi-tab or full UI zoom checks."
  } | ConvertTo-Json -Depth 6
}

if ($Mode -eq "plan") {
  Show-FixedCorpusPlan
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
  "-SmokePrefix", "docpilot-high-intensity-fixed-corpus",
  "-QualityMinSimilarityThreshold", $QualityMinSimilarityThreshold,
  "-MySqlLocalPort", $MySqlLocalPort,
  "-QdrantLocalPort", $QdrantLocalPort,
  "-IndexVersion", $IndexVersion,
  "-EnableFixedBusinessCorpusGate"
)

if ($SkipFrontend) {
  $argsList += "-SkipFrontend"
}
if ($ReuseRunningServices) {
  $argsList += "-ReuseRunningServices"
}

$powershellExecutable = if ($PSVersionTable.PSEdition -eq "Core") { "pwsh" } else { "powershell.exe" }
& $powershellExecutable @argsList
exit $LASTEXITCODE
