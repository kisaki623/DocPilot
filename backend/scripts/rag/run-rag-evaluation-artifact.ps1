param(
  [switch]$Help,
  [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

if ($Help) {
  Write-Host "Generates offline RAG retrieval evaluation artifacts with fake embedding, in-memory retrieval, and local fake Qdrant server tests."
  Write-Host "Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/rag/run-rag-evaluation-artifact.ps1"
  Write-Host "Output is sanitized: aggregate metrics only, no document text, model inputs, credentials, connection details, or external responses."
  exit 0
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptRoot "..\..")
$repoRoot = Resolve-Path (Join-Path $backendRoot "..")
$summaryPath = Join-Path $repoRoot "docs\ai-dev\benchmarks\rag\offline-retrieval-evaluation.md"

Write-Host "RAG retrieval evaluation artifact generation starting. provider=in_memory embeddingProvider=fake qdrantMode=fake_server"

if (-not $SkipTests) {
  Push-Location $backendRoot
  try {
    mvn "-Dtest=*Rag*EvaluationArtifact*" test
  } finally {
    Pop-Location
  }
}

if (-not (Test-Path $summaryPath)) {
  Write-Host "RAG retrieval evaluation summary not found. Run without -SkipTests to generate it."
  exit 2
}

Write-Host "RAG retrieval evaluation sanitized markdown summary:"
Get-Content -Encoding UTF8 $summaryPath
