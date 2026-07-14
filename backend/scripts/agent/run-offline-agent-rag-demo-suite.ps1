param(
  [switch]$Help,
  [switch]$SkipTests,
  [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

function Get-PowerShellExecutable {
  if ($env:OS -eq "Windows_NT") {
    return "powershell"
  }
  return "pwsh"
}

if ($Help) {
  Write-Host "Runs the offline Agent/RAG demo suite with fake embedding and in-memory vector store checks."
  Write-Host "Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/agent/run-offline-agent-rag-demo-suite.ps1 [-SkipTests]"
  Write-Host "Output is sanitized: aggregate check status only, no credentials, connection details, prompts, document text, or provider responses."
  exit 0
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptRoot "..\..")
$repoRoot = Resolve-Path (Join-Path $backendRoot "..")

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
  $OutputPath = Join-Path $backendRoot "target\rag-demo\offline-agent-rag-demo-suite-summary.json"
}

function Convert-ToRepoRelativePath {
  param([string]$Path)

  $rootPath = $repoRoot.Path.TrimEnd("\", "/")
  $fullPath = [System.IO.Path]::GetFullPath($Path)
  if ($fullPath.StartsWith($rootPath, [System.StringComparison]::OrdinalIgnoreCase)) {
    return $fullPath.Substring($rootPath.Length).TrimStart("\", "/").Replace("\", "/")
  }
  return [System.IO.Path]::GetFileName($Path)
}

function New-ArtifactPath {
  param([string]$Path)
  return [PSCustomObject]@{
    name = [System.IO.Path]::GetFileName($Path)
    relativePath = Convert-ToRepoRelativePath -Path $Path
    present = Test-Path -LiteralPath $Path
  }
}

function Invoke-OfflineScriptCheck {
  param(
    [string]$Name,
    [string]$ScriptPath,
    [string[]]$Arguments = @(),
    [bool]$RunWhenSkipping = $false
  )

  if ($SkipTests -and -not $RunWhenSkipping) {
    return [PSCustomObject]@{
      name = $Name
      status = "planned"
      exitCode = $null
    }
  }

  $commandArguments = @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    $ScriptPath
  ) + $Arguments

  Push-Location $backendRoot
  try {
    $powerShellExecutable = Get-PowerShellExecutable
    & $powerShellExecutable @commandArguments *> $null
    $exitCode = $LASTEXITCODE
  } finally {
    Pop-Location
  }

  if ($null -eq $exitCode) {
    $exitCode = 0
  }
  return [PSCustomObject]@{
    name = $Name
    status = if ($exitCode -eq 0) { "passed" } else { "failed" }
    exitCode = $exitCode
  }
}

$agentDemoScript = Join-Path $scriptRoot "demo-agent-showcase.ps1"
$vectorStoreScript = Join-Path $backendRoot "scripts\rag\run-rag-vector-store-offline-demo.ps1"
$retrievalEvalScript = Join-Path $backendRoot "scripts\rag\run-rag-retrieval-eval.ps1"
$evalArtifactScript = Join-Path $backendRoot "scripts\rag\run-rag-evaluation-artifact.ps1"
$traceSmokeScript = Join-Path $backendRoot "scripts\rag\run-rag-qa-trace-smoke.ps1"
$trendScript = Join-Path $backendRoot "scripts\rag\show-rag-eval-trend.ps1"

$checks = @()
$checks += Invoke-OfflineScriptCheck -Name "agent_showcase_dry_run" -ScriptPath $agentDemoScript -Arguments @("-DryRun") -RunWhenSkipping $true
$checks += Invoke-OfflineScriptCheck -Name "rag_vector_store_offline_demo" -ScriptPath $vectorStoreScript
$checks += Invoke-OfflineScriptCheck -Name "rag_retrieval_eval" -ScriptPath $retrievalEvalScript
$checks += Invoke-OfflineScriptCheck -Name "rag_evaluation_artifact" -ScriptPath $evalArtifactScript
$checks += Invoke-OfflineScriptCheck -Name "rag_qa_trace_smoke" -ScriptPath $traceSmokeScript
$checks += Invoke-OfflineScriptCheck -Name "rag_eval_trend_summary" -ScriptPath $trendScript -RunWhenSkipping $true

$failed = @($checks | Where-Object { $_.status -eq "failed" })
$planned = @($checks | Where-Object { $_.status -eq "planned" })
$status = if ($failed.Count -gt 0) { "failed" } elseif ($planned.Count -gt 0) { "planned" } else { "passed" }

$artifactPaths = @(
  New-ArtifactPath -Path (Join-Path $backendRoot "target\rag-demo\rag-vector-store-offline-demo-summary.json")
  New-ArtifactPath -Path (Join-Path $backendRoot "target\rag-eval\rag-retrieval-eval-summary.json")
  New-ArtifactPath -Path (Join-Path $backendRoot "target\rag-evidence\rag-qa-trace-summary.json")
  New-ArtifactPath -Path (Join-Path $repoRoot "docs\ai-dev\benchmarks\rag\offline-retrieval-evaluation.json")
  New-ArtifactPath -Path (Join-Path $repoRoot "docs\ai-dev\benchmarks\rag\offline-retrieval-evaluation-history.json")
)

$summary = [PSCustomObject]@{
  demoName = "offline-agent-rag-demo-suite"
  generatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
  mode = "offline"
  embeddingProvider = "fake"
  vectorStore = "in_memory"
  qdrantEnabled = $false
  providerHttp = $false
  status = $status
  testsOrChecksExecuted = @($checks | ForEach-Object { $_.name })
  checks = $checks
  artifactPaths = $artifactPaths
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
  New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

$json = $summary | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.UTF8Encoding]::new($false))
$json

if ($status -eq "failed") {
  exit 2
}
