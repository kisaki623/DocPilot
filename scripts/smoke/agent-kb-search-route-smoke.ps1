param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$ArtifactRoot = "backend/target/agent-kb-search-route",
  [string]$SmokePrefix = "docpilot-agent-kb-search-route"
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
  $scriptPath = if ([string]::IsNullOrWhiteSpace($PSCommandPath)) { $MyInvocation.MyCommand.Path } else { $PSCommandPath }
  return Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $scriptPath))
}

function Show-AgentKbSearchRoutePlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Agent KB search route smoke plan only. No env read, no provider call, no service start, no business data creation."
    smokePrefix = $SmokePrefix
    artifactRoot = $ArtifactRoot
    runnerTest = "AgentKnowledgeBaseSearchRouteSmokeTest"
    checks = @(
      "retrieval-only KB task is routed to knowledge_base_search_tool",
      "answer intent is rejected by KB Agent P0 without tool execution",
      "KnowledgeBase scope failure is propagated as a security failure",
      "KB search output redaction keeps raw task, answer, document body and evidence context out of artifact"
    )
    resultPolicy = "Stores marker, status, case ids, decisions, selected tool names, booleans, counts and failure buckets only. No raw task, prompt, answer, document text, evidence context, token, credential, cloud address or connection string."
    boundary = "Offline lightweight KB Agent route contract only. No backend/frontend/tunnel start, no remote Docker, no hk-ops, no schema migration, no business-data deletion, no artifact commit, no push."
  } | ConvertTo-Json -Depth 5
}

function Invoke-DryRun {
  $repoRoot = Get-RepoRoot
  $runner = Join-Path $repoRoot "backend/src/test/java/com/docpilot/backend/ai/agent/AgentKnowledgeBaseSearchRouteSmokeTest.java"
  [PSCustomObject][ordered]@{
    mode = "dry-run"
    overallStatus = "PASS"
    checks = @(
      [ordered]@{ name = "runnerTestExists"; pass = (Test-Path -LiteralPath $runner) },
      [ordered]@{ name = "mavenExists"; pass = [bool](Get-Command mvn -ErrorAction SilentlyContinue) },
      [ordered]@{ name = "artifactRootUnderBackendTarget"; pass = $ArtifactRoot.Replace("\", "/").StartsWith("backend/target/") }
    )
  } | ConvertTo-Json -Depth 5
}

function Invoke-Run {
  $repoRoot = Get-RepoRoot
  $runSuffix = (Get-Date).ToString("yyyyMMddHHmmss") + "-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
  $smokeMarker = "$SmokePrefix-$runSuffix"
  $resolvedArtifactRoot = if ([System.IO.Path]::IsPathRooted($ArtifactRoot)) {
    $ArtifactRoot
  } else {
    Join-Path $repoRoot $ArtifactRoot
  }
  $artifactDir = Join-Path $resolvedArtifactRoot $smokeMarker
  New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
  $artifactPath = Join-Path $artifactDir "artifact.json"
  $mavenLogPath = Join-Path $artifactDir "maven.log"

  Push-Location (Join-Path $repoRoot "backend")
  try {
    $oldErrorActionPreference = $ErrorActionPreference
    $oldNativePreference = $PSNativeCommandUseErrorActionPreference
    $PSNativeCommandUseErrorActionPreference = $false
    $oldEnabled = [Environment]::GetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ENABLED", "Process")
    $oldArtifact = [Environment]::GetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ARTIFACT", "Process")
    $oldMarker = [Environment]::GetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_MARKER", "Process")
    [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ENABLED", "true", "Process")
    [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ARTIFACT", $artifactPath, "Process")
    [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_MARKER", $smokeMarker, "Process")
    $ErrorActionPreference = "Continue"
    & mvn "-Dtest=AgentKnowledgeBaseSearchRouteSmokeTest" test > $mavenLogPath 2>&1
    $mavenExitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $oldErrorActionPreference
    $PSNativeCommandUseErrorActionPreference = $oldNativePreference
    if ($null -eq $oldEnabled) { Remove-Item "Env:DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ENABLED" -ErrorAction SilentlyContinue } else { [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ENABLED", $oldEnabled, "Process") }
    if ($null -eq $oldArtifact) { Remove-Item "Env:DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ARTIFACT" -ErrorAction SilentlyContinue } else { [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ARTIFACT", $oldArtifact, "Process") }
    if ($null -eq $oldMarker) { Remove-Item "Env:DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_MARKER" -ErrorAction SilentlyContinue } else { [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_MARKER", $oldMarker, "Process") }
    Pop-Location
  }

  if ($mavenExitCode -ne 0) {
    [PSCustomObject][ordered]@{
      overallStatus = "FAILED_CORE_FLOW"
      smokeMarker = $smokeMarker
      artifact = $artifactPath
      mavenLog = $mavenLogPath
      safeMessage = "agent KB search route offline runner failed"
    } | ConvertTo-Json -Depth 5
    exit $mavenExitCode
  }

  [PSCustomObject][ordered]@{
    overallStatus = "PASS"
    smokeMarker = $smokeMarker
    artifact = $artifactPath
    mavenLog = $mavenLogPath
    safeMessage = "agent KB search route offline runner passed"
  } | ConvertTo-Json -Depth 5
}

if ($Mode -eq "plan") {
  Show-AgentKbSearchRoutePlan
  exit 0
}

if ($Mode -eq "dry-run") {
  Invoke-DryRun
  exit 0
}

Invoke-Run
