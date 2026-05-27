param(
  [switch]$Help,
  [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

if ($Help) {
  Write-Host "Runs the offline RAG vector store demo with fake embedding, in-memory retrieval, and local fake Qdrant server tests."
  Write-Host "Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/rag/run-rag-vector-store-offline-demo.ps1"
  Write-Host "Output is sanitized: aggregate checks only, no document text, model inputs, credentials, connection details, or external responses."
  exit 0
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptRoot "..\..")
$reportPath = Join-Path $backendRoot "target\rag-demo\rag-vector-store-offline-demo-summary.json"

Write-Host "RAG vector store offline demo starting. provider=in_memory embeddingProvider=fake qdrantMode=fake_server"

if (-not $SkipTests) {
  Push-Location $backendRoot
  try {
    mvn "-Dtest=*RagVectorStoreOfflineDemo*" test
  } finally {
    Pop-Location
  }
}

if (-not (Test-Path $reportPath)) {
  Write-Host "RAG vector store offline demo report not found. Run without -SkipTests to generate it."
  exit 2
}

Write-Host "RAG vector store offline demo sanitized summary:"
Get-Content -Encoding UTF8 $reportPath
