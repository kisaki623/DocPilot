param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$ArtifactRoot = "backend/target/agent-quality-eval",
  [string]$SmokePrefix = "docpilot-agent-quality-eval"
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
  $scriptPath = if ([string]::IsNullOrWhiteSpace($PSCommandPath)) { $MyInvocation.MyCommand.Path } else { $PSCommandPath }
  return Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $scriptPath))
}

function Show-AgentQualityEvalPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Agent Quality Eval smoke plan only. No env read, no provider call, no service start, no data creation."
    smokePrefix = $SmokePrefix
    artifactRoot = $ArtifactRoot
    fixture = "backend/src/test/resources/quality/agent-quality-eval-cases.json"
    runnerTest = "AgentQualityEvalRunnerSmokeTest"
    caseFields = @(
      "caseId",
      "question",
      "expectedBehavior",
      "expectedEvidence",
      "expectedTools",
      "mustContain",
      "mustNotContain",
      "tags",
      "scoringRules",
      "scoringRules.expectedDecision"
    )
    resultPolicy = "Stores caseId, tags, pass/fail booleans, failure buckets, traceId/agentRunId and numeric expectedDecisionMatched only. No raw question, answer, prompt, document text, evidence context, token, credential, cloud address or connection string."
    boundary = "Offline lightweight eval contract only. No remote Docker, no hk-ops, no schema migration, no business-data deletion, no artifact commit, no push."
  } | ConvertTo-Json -Depth 5
}

function Invoke-DryRun {
  $repoRoot = Get-RepoRoot
  $fixture = Join-Path $repoRoot "backend/src/test/resources/quality/agent-quality-eval-cases.json"
  [PSCustomObject][ordered]@{
    mode = "dry-run"
    overallStatus = "PASS"
    checks = @(
      [ordered]@{ name = "fixtureExists"; pass = (Test-Path -LiteralPath $fixture) },
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
    $oldEnabled = [Environment]::GetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_ENABLED", "Process")
    $oldArtifact = [Environment]::GetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_ARTIFACT", "Process")
    $oldMarker = [Environment]::GetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_MARKER", "Process")
    [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_ENABLED", "true", "Process")
    [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_ARTIFACT", $artifactPath, "Process")
    [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_MARKER", $smokeMarker, "Process")
    & mvn "-Dtest=AgentQualityEvalRunnerSmokeTest" test *> $mavenLogPath
    $mavenExitCode = $LASTEXITCODE
  } finally {
    if ($null -eq $oldEnabled) { Remove-Item "Env:DOCPILOT_AGENT_QUALITY_EVAL_ENABLED" -ErrorAction SilentlyContinue } else { [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_ENABLED", $oldEnabled, "Process") }
    if ($null -eq $oldArtifact) { Remove-Item "Env:DOCPILOT_AGENT_QUALITY_EVAL_ARTIFACT" -ErrorAction SilentlyContinue } else { [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_ARTIFACT", $oldArtifact, "Process") }
    if ($null -eq $oldMarker) { Remove-Item "Env:DOCPILOT_AGENT_QUALITY_EVAL_MARKER" -ErrorAction SilentlyContinue } else { [Environment]::SetEnvironmentVariable("DOCPILOT_AGENT_QUALITY_EVAL_MARKER", $oldMarker, "Process") }
    Pop-Location
  }

  if ($mavenExitCode -ne 0) {
    [PSCustomObject][ordered]@{
      overallStatus = "FAILED_CORE_FLOW"
      smokeMarker = $smokeMarker
      artifact = $artifactPath
      mavenLog = $mavenLogPath
      safeMessage = "agent quality eval offline runner failed"
    } | ConvertTo-Json -Depth 5
    exit $mavenExitCode
  }

  [PSCustomObject][ordered]@{
    overallStatus = "PASS"
    smokeMarker = $smokeMarker
    artifact = $artifactPath
    mavenLog = $mavenLogPath
    safeMessage = "agent quality eval offline runner passed"
  } | ConvertTo-Json -Depth 5
}

if ($Mode -eq "plan") {
  Show-AgentQualityEvalPlan
  exit 0
}

if ($Mode -eq "dry-run") {
  Invoke-DryRun
  exit 0
}

Invoke-Run
