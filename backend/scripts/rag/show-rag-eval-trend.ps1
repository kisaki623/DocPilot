param(
  [string]$HistoryPath = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($HistoryPath)) {
  $repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
  $HistoryPath = Join-Path $repoRoot "docs\ai-dev\benchmarks\rag\offline-retrieval-evaluation-history.json"
}

if (-not (Test-Path -LiteralPath $HistoryPath)) {
  throw "Offline RAG eval history artifact not found."
}

function Convert-ToDouble {
  param([object]$Value)
  return [double]::Parse([string]$Value, [System.Globalization.CultureInfo]::InvariantCulture)
}

$artifact = Get-Content -LiteralPath $HistoryPath -Raw -Encoding UTF8 | ConvertFrom-Json
$entries = @($artifact.entries)
$summaries = @()

foreach ($group in ($entries | Group-Object -Property vectorStoreProvider)) {
  $ordered = @($group.Group | Sort-Object -Property generatedAt)
  if ($ordered.Count -eq 0) {
    continue
  }

  $latest = $ordered[$ordered.Count - 1]
  $previous = $null
  if ($ordered.Count -gt 1) {
    $previous = $ordered[$ordered.Count - 2]
  }

  $latestRate = Convert-ToDouble -Value $latest.hitRate
  $previousRate = $null
  $delta = $null
  if ($null -ne $previous) {
    $previousRate = Convert-ToDouble -Value $previous.hitRate
    $delta = $latestRate - $previousRate
  }

  $summaries += [PSCustomObject]@{
    vectorStoreProvider = [string]$latest.vectorStoreProvider
    embeddingProvider = [string]$latest.embeddingProvider
    latestGeneratedAt = [string]$latest.generatedAt
    caseCount = [int]$latest.caseCount
    latestHitRate = $latestRate.ToString("0.0000", [System.Globalization.CultureInfo]::InvariantCulture)
    previousHitRatePresent = $null -ne $previous
    previousHitRate = if ($null -ne $previousRate) { $previousRate.ToString("0.0000", [System.Globalization.CultureInfo]::InvariantCulture) } else { "" }
    deltaPresent = $null -ne $delta
    delta = if ($null -ne $delta) { $delta.ToString("+0.0000;-0.0000;0.0000", [System.Globalization.CultureInfo]::InvariantCulture) } else { "" }
  }
}

[PSCustomObject]@{
  artifact = "offline-rag-eval-trend-summary"
  source = "offline-retrieval-evaluation-history"
  summaryCount = $summaries.Count
  summaries = $summaries
} | ConvertTo-Json -Depth 6
