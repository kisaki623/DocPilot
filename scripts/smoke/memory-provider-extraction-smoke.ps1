param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/memory-provider",
  [string]$SmokePrefix = "docpilot-memory-provider",
  [int]$MaxModelCalls = 4
)

$ErrorActionPreference = "Stop"

function Show-MemoryProviderPlan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Memory provider extraction smoke plan only. No env read, no provider call, no data creation."
    smokePrefix = $SmokePrefix
    artifactRoot = $ArtifactRoot
    maxModelCalls = $MaxModelCalls
    test = "MemoryProviderExtractionRealProviderSmokeTest"
    gates = @(
      "real provider config presence",
      "JSON-only memory suggestion contract",
      "ANSWER_STYLE and TASK_GOAL extraction",
      "TECH_CONTEXT extraction",
      "RAG evidence isolation",
      "secret-like content rejection",
      "redacted artifact"
    )
    artifactPolicy = "Stores provider/model/call count/case ids/types/booleans/failure reasons only; no raw conversation text, provider output, memory content, prompt, token, credential, cloud address or connection string."
    boundary = "Small real-provider sample only. No remote Docker, no hk-ops, no schema migration, no business-data deletion, no artifact commit, no push."
  } | ConvertTo-Json -Depth 5
}

function Read-EnvFile([string]$path) {
  $values = @{}
  if (-not (Test-Path -LiteralPath $path)) {
    return $values
  }

  Get-Content -LiteralPath $path | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+?)\s*=\s*(.*)\s*$') {
      $values[$matches[1].Trim()] = $matches[2].Trim().Trim('"').Trim("'")
    }
  }
  return $values
}

function Get-ConfigValue($values, [string]$name, [string]$fallback = "") {
  $envValue = [Environment]::GetEnvironmentVariable($name, "Process")
  if (-not [string]::IsNullOrWhiteSpace($envValue)) {
    return $envValue.Trim()
  }
  if ($values.ContainsKey($name) -and -not [string]::IsNullOrWhiteSpace([string]$values[$name])) {
    return ([string]$values[$name]).Trim()
  }
  return $fallback
}

function Set-ProcessEnvValue([string]$name, [string]$value, $oldValues) {
  $oldValues[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
  if ([string]::IsNullOrWhiteSpace($value)) {
    Remove-Item "Env:$name" -ErrorAction SilentlyContinue
  } else {
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
  }
}

function Restore-ProcessEnv($oldValues) {
  foreach ($name in $oldValues.Keys) {
    $oldValue = $oldValues[$name]
    if ($null -eq $oldValue) {
      Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    } else {
      [Environment]::SetEnvironmentVariable($name, $oldValue, "Process")
    }
  }
}

function Invoke-DryRun {
  [PSCustomObject][ordered]@{
    mode = "dry-run"
    overallStatus = "PASS"
    checks = @(
      [ordered]@{ name = "envFileExists"; pass = (Test-Path -LiteralPath $EnvFile) },
      [ordered]@{ name = "mavenExists"; pass = [bool](Get-Command mvn -ErrorAction SilentlyContinue) },
      [ordered]@{ name = "artifactRootUnderBackendTarget"; pass = $ArtifactRoot.Replace("\", "/").StartsWith("backend/target/") }
    )
  } | ConvertTo-Json -Depth 5
}

function Invoke-Run {
  if ($MaxModelCalls -gt 6) {
    [PSCustomObject][ordered]@{
      overallStatus = "BLOCKED"
      safeMessage = "max model calls is above the small-sample boundary"
      maxModelCalls = $MaxModelCalls
    } | ConvertTo-Json -Depth 5
    exit 2
  }

  $scriptPath = if ([string]::IsNullOrWhiteSpace($PSCommandPath)) { $MyInvocation.MyCommand.Path } else { $PSCommandPath }
  $repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $scriptPath))
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

  $values = Read-EnvFile $EnvFile
  $provider = Get-ConfigValue $values "AI_REAL_PROVIDER" "openai-compatible"
  $baseUrl = Get-ConfigValue $values "AI_REAL_BASE_URL"
  $apiKey = Get-ConfigValue $values "AI_REAL_API_KEY"
  $model = Get-ConfigValue $values "AI_REAL_MODEL"

  $missing = @()
  if ([string]::IsNullOrWhiteSpace($baseUrl)) { $missing += "AI_REAL_BASE_URL" }
  if ([string]::IsNullOrWhiteSpace($apiKey)) { $missing += "AI_REAL_API_KEY" }
  if ([string]::IsNullOrWhiteSpace($model)) { $missing += "AI_REAL_MODEL" }
  if ($missing.Count -gt 0) {
    [PSCustomObject][ordered]@{
      overallStatus = "BLOCKED"
      safeMessage = "real answer provider config is incomplete"
      missingKeys = $missing
      smokeMarker = $smokeMarker
      artifact = $artifactPath
    } | ConvertTo-Json -Depth 5
    exit 2
  }

  $oldValues = @{}
  try {
    Set-ProcessEnvValue "DOCPILOT_MEMORY_PROVIDER_SMOKE_ENABLED" "true" $oldValues
    Set-ProcessEnvValue "DOCPILOT_MEMORY_PROVIDER_SMOKE_ARTIFACT" $artifactPath $oldValues
    Set-ProcessEnvValue "DOCPILOT_MEMORY_PROVIDER_SMOKE_MARKER" $smokeMarker $oldValues
    Set-ProcessEnvValue "AI_REAL_PROVIDER" $provider $oldValues
    Set-ProcessEnvValue "AI_REAL_BASE_URL" $baseUrl $oldValues
    Set-ProcessEnvValue "AI_REAL_API_KEY" $apiKey $oldValues
    Set-ProcessEnvValue "AI_REAL_MODEL" $model $oldValues
    foreach ($name in @(
        "AI_REAL_CONNECT_TIMEOUT_MS",
        "AI_REAL_READ_TIMEOUT_MS",
        "AI_REAL_TEMPERATURE",
        "AI_REAL_MAX_OUTPUT_TOKENS",
        "AI_REAL_INPUT_COST_PER_1K_USD",
        "AI_REAL_OUTPUT_COST_PER_1K_USD"
      )) {
      Set-ProcessEnvValue $name (Get-ConfigValue $values $name) $oldValues
    }

    Push-Location (Join-Path $repoRoot "backend")
    try {
      & mvn "-Dtest=MemoryProviderExtractionRealProviderSmokeTest" test *> $mavenLogPath
      $mavenExitCode = $LASTEXITCODE
    } finally {
      Pop-Location
    }
  } finally {
    Restore-ProcessEnv $oldValues
  }

  if ($mavenExitCode -ne 0) {
    [PSCustomObject][ordered]@{
      overallStatus = "FAILED_CORE_FLOW"
      safeMessage = "memory provider extraction smoke failed"
      smokeMarker = $smokeMarker
      artifact = $artifactPath
      mavenLog = $mavenLogPath
    } | ConvertTo-Json -Depth 5
    exit $mavenExitCode
  }

  $artifactSummary = if (Test-Path -LiteralPath $artifactPath) {
    Get-Content -LiteralPath $artifactPath -Encoding UTF8 | ConvertFrom-Json
  } else {
    $null
  }

  [PSCustomObject][ordered]@{
    overallStatus = "PASS"
    smokeMarker = $smokeMarker
    artifact = $artifactPath
    provider = if ($null -eq $artifactSummary) { "unknown" } else { $artifactSummary.provider }
    model = if ($null -eq $artifactSummary) { "" } else { $artifactSummary.model }
    modelCallCount = if ($null -eq $artifactSummary) { 0 } else { $artifactSummary.modelCallCount }
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
