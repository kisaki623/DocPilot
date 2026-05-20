param(
  [switch]$Help,
  [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

if ($Help) {
  Write-Host "Runs the local RAG QA trace smoke with fake embedding and in-memory vector store."
  Write-Host "Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/rag/run-rag-qa-trace-smoke.ps1"
  Write-Host "Fields: ragEnabled, embeddingProvider, vectorStoreType, retrievedCount, contextHashPresent, fallbackUsed, citationCount."
  Write-Host "Output is sanitized: RAG trace summary fields only, no document text, credentials, connection details, or external responses."
  exit 0
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptRoot "..\..")
$reportPath = Join-Path $backendRoot "target\rag-evidence\rag-qa-trace-summary.json"

Write-Host "RAG QA trace smoke starting. provider=in_memory embeddingProvider=fake"

if (-not $SkipTests) {
  Push-Location $backendRoot
  try {
    mvn "-Dtest=*RagQaTraceSmokeEvidence*,RagQaTraceSmokeScriptSafetyTest" test
  } finally {
    Pop-Location
  }
}

if (-not (Test-Path $reportPath)) {
  Write-Host "RAG QA trace smoke report not found. Run without -SkipTests to generate it."
  exit 2
}

Write-Host "RAG QA trace sanitized summary:"
Get-Content -Encoding UTF8 $reportPath
