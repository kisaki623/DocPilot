param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$SmokePrefix = "docpilot-memory-provider"
)

$ErrorActionPreference = "Stop"
$FixedSuiteCaseCount = 6

function Get-RepoRoot {
  $scriptPath = if ([string]::IsNullOrWhiteSpace($PSCommandPath)) { $MyInvocation.MyCommand.Path } else { $PSCommandPath }
  return Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $scriptPath))
}

function Get-ArtifactRoot([string]$repoRoot) {
  return Join-Path $repoRoot "backend/target/memory-provider"
}

function Test-SafeArtifactRoot([string]$repoRoot, [string]$artifactRoot) {
  $expected = [System.IO.Path]::GetFullPath((Get-ArtifactRoot $repoRoot)).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
  $actual = [System.IO.Path]::GetFullPath($artifactRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
  if ($actual -ne $expected) {
    return $false
  }
  & git -C $repoRoot check-ignore -q -- "backend/target/memory-provider"
  return ($LASTEXITCODE -eq 0)
}

function Test-SafeSmokePrefix([string]$prefix) {
  return -not [string]::IsNullOrWhiteSpace($prefix) -and $prefix -match '^[A-Za-z0-9-]+$'
}

function Show-MemoryProviderPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Memory provider extraction smoke plan only. No env read, no provider call, no data creation."
    smokePrefix = $SmokePrefix
    artifactRoot = "backend/target/memory-provider"
    maxModelCalls = $FixedSuiteCaseCount
    test = "MemoryProviderExtractionRealProviderSmokeTest"
    gates = @(
      "real provider config presence",
      "JSON-only memory suggestion contract",
      "ANSWER_STYLE and TASK_GOAL extraction",
      "TECH_CONTEXT extraction",
      "Chinese durable PREFERENCE and PROJECT_STATE extraction",
      "RAG evidence isolation",
      "secret-like content rejection",
      "one-time instruction suppression",
      "redacted artifact"
    )
    artifactPolicy = "Stores provider/model/call count/case ids/types/booleans/failure reasons only; no raw conversation text, provider output, memory content, prompt, token, credential, cloud address or connection string."
    boundary = "Small real-provider sample only. No remote Docker, no hk-ops, no schema migration, no business-data deletion, no artifact commit, no push."
  } | ConvertTo-Json -Depth 5
}

function Invoke-DryRun {
  $repoRoot = Get-RepoRoot
  $artifactRoot = Get-ArtifactRoot $repoRoot
  $checks = @(
    [ordered]@{ name = "mavenExists"; pass = [bool](Get-Command mvn -ErrorAction SilentlyContinue) },
    [ordered]@{ name = "fixedSuiteBudget"; pass = ($FixedSuiteCaseCount -eq 6) },
    [ordered]@{ name = "artifactRootIgnored"; pass = (Test-SafeArtifactRoot $repoRoot $artifactRoot) }
  )
  $allPassed = @($checks | Where-Object { -not $_.pass }).Count -eq 0
  [PSCustomObject][ordered]@{
    mode = "dry-run"
    overallStatus = if ($allPassed) { "PASS" } else { "BLOCKED" }
    checks = $checks
  } | ConvertTo-Json -Depth 5
  if (-not $allPassed) { exit 2 }
}

function Invoke-Run {
  $repoRoot = Get-RepoRoot
  $resolvedArtifactRoot = Get-ArtifactRoot $repoRoot
  if (-not (Test-SafeArtifactRoot $repoRoot $resolvedArtifactRoot)) {
    [PSCustomObject][ordered]@{ overallStatus = "BLOCKED"; failureCodes = @("artifact_root_not_ignored") } | ConvertTo-Json -Depth 4
    exit 2
  }
  if (-not (Test-SafeSmokePrefix $SmokePrefix)) {
    [PSCustomObject][ordered]@{ overallStatus = "BLOCKED"; failureCodes = @("smoke_prefix_invalid") } | ConvertTo-Json -Depth 4
    exit 2
  }
  $runSuffix = (Get-Date).ToString("yyyyMMddHHmmss") + "-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
  $smokeMarker = "$SmokePrefix-$runSuffix"
  $artifactDir = Join-Path $resolvedArtifactRoot $smokeMarker
  New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
  $artifactPath = Join-Path $artifactDir "artifact.json"

  $missing = @()
  foreach ($name in @("AI_REAL_PROVIDER", "AI_REAL_BASE_URL", "AI_REAL_API_KEY", "AI_REAL_MODEL")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, "Process"))) { $missing += $name }
  }
  if ($missing.Count -gt 0) {
    [PSCustomObject][ordered]@{
      overallStatus = "BLOCKED"
      failureCodes = @("provider_config_missing")
      smokeMarker = $smokeMarker
      artifact = $artifactPath
    } | ConvertTo-Json -Depth 5
    exit 2
  }

  [Environment]::SetEnvironmentVariable("DOCPILOT_MEMORY_PROVIDER_SMOKE_ENABLED", "true", "Process")
  [Environment]::SetEnvironmentVariable("DOCPILOT_MEMORY_PROVIDER_SMOKE_ARTIFACT", $artifactPath, "Process")
  try {
    Push-Location (Join-Path $repoRoot "backend")
    & mvn "-Dtest=MemoryProviderExtractionRealProviderSmokeTest" test *> $null
    $mavenExitCode = $LASTEXITCODE
  } finally {
    Pop-Location
    Remove-Item Env:DOCPILOT_MEMORY_PROVIDER_SMOKE_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:DOCPILOT_MEMORY_PROVIDER_SMOKE_ARTIFACT -ErrorAction SilentlyContinue
  }

  if ($mavenExitCode -ne 0) {
    if (-not (Test-Path -LiteralPath $artifactPath)) {
      [PSCustomObject][ordered]@{ smokeMarker = $smokeMarker; stage = "maven_test"; mavenExitCode = $mavenExitCode; failureCodes = @("maven_test_failed"); fixedSuiteCaseCount = $FixedSuiteCaseCount; rawProviderOutputStored = $false } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $artifactPath -Encoding utf8
    }
    [PSCustomObject][ordered]@{
      overallStatus = "FAILED_CORE_FLOW"
      failureCodes = @("maven_test_failed")
      smokeMarker = $smokeMarker
      artifact = $artifactPath
    } | ConvertTo-Json -Depth 5
    exit $mavenExitCode
  }

  $artifactSummary = if (Test-Path -LiteralPath $artifactPath) {
    Get-Content -LiteralPath $artifactPath -Encoding UTF8 | ConvertFrom-Json
  } else {
    $null
  }

  $artifactValid = $null -ne $artifactSummary -and $artifactSummary.modelCallCount -eq $FixedSuiteCaseCount -and $artifactSummary.caseSummaries.Count -eq $FixedSuiteCaseCount -and $artifactSummary.casePassRate -eq "1.0000" -and -not $artifactSummary.rawProviderOutputStored -and @($artifactSummary.caseSummaries | Where-Object { -not $_.passed }).Count -eq 0
  if (-not $artifactValid) {
    [PSCustomObject][ordered]@{ overallStatus = "FAILED_CORE_FLOW"; failureCodes = @("artifact_summary_invalid"); smokeMarker = $smokeMarker; artifact = $artifactPath } | ConvertTo-Json -Depth 4
    exit 1
  }

  [PSCustomObject][ordered]@{
    overallStatus = "PASS"
    smokeMarker = $smokeMarker
    artifact = $artifactPath
    provider = if ($null -eq $artifactSummary) { "unknown" } else { $artifactSummary.provider }
    model = if ($null -eq $artifactSummary) { "" } else { $artifactSummary.model }
    modelCallCount = if ($null -eq $artifactSummary) { 0 } else { $artifactSummary.modelCallCount }
    fixedSuiteCaseCount = $FixedSuiteCaseCount
    casePassRate = if ($null -eq $artifactSummary) { "" } else { $artifactSummary.casePassRate }
    rawProviderOutputStored = if ($null -eq $artifactSummary) { $false } else { $artifactSummary.rawProviderOutputStored }
    safeMessage = "memory provider extraction smoke passed with redacted artifact"
  } | ConvertTo-Json -Depth 5
}

if ($Mode -eq "plan") {
  Show-MemoryProviderPlan
  exit 0
}

if ($Mode -eq "dry-run") {
  Invoke-DryRun
  exit 0
}

Invoke-Run
