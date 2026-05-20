param(
  [switch]$Help,
  [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

if ($Help) {
  Write-Host "Runs the local RAG retrieval eval with fake embedding and in-memory vector store."
  Write-Host "Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/rag/run-rag-retrieval-eval.ps1"
  Write-Host "Output is sanitized: aggregate metrics only, no document text, prompts, credentials, connection details, or external responses."
  exit 0
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptRoot "..\..")
$reportPath = Join-Path $backendRoot "target\rag-eval\rag-retrieval-eval-summary.json"

Write-Host "RAG retrieval eval starting. provider=in_memory embeddingProvider=fake"

if (-not $SkipTests) {
  Push-Location $backendRoot
  try {
    mvn "-Dtest=*RagRetrievalEvaluation*" test
  } finally {
    Pop-Location
  }
}

if (-not (Test-Path $reportPath)) {
  Write-Host "RAG retrieval eval report not found. Run without -SkipTests to generate it."
  exit 2
}

Write-Host "RAG retrieval eval sanitized summary:"
Get-Content -Encoding UTF8 $reportPath
