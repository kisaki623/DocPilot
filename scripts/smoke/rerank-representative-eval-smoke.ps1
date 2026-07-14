param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/rag-quality",
  [ValidateRange(0.0, 1.0)]
  [double]$QualityMinSimilarityThreshold = 0.50,
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"

function Show-Plan {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Bounded real rerank representative eval smoke. No env read, no service start, no data creation."
    baseline = "representative hybrid retrieval with APP_RAG_RERANK_ENABLED=false"
    candidate = "representative hybrid retrieval with APP_RAG_RERANK_ENABLED=true and existing private rerank provider config"
    gates = @(
      "12-case representative rerank eval",
      "target evidence coverage",
      "target rank uplift or target-over-distractor quality",
      "distractor demotion and citation leakage guard",
      "no-evidence regression guard",
      "rerankApplied check",
      "artifact redaction"
    )
    boundary = "Creates temporary smoke users/documents/knowledge bases only in run mode; no remote Docker, no schema migration, no business-data deletion, no secret printing, no push."
  } | ConvertTo-Json -Depth 6
}

function Test-TcpPort([int]$port) {
  try {
    $client = [System.Net.Sockets.TcpClient]::new()
    $iar = $client.BeginConnect("127.0.0.1", $port, $null, $null)
    $success = $iar.AsyncWaitHandle.WaitOne(1000)
    if ($success) {
      $client.EndConnect($iar)
    }
    $client.Close()
    return $success
  } catch {
    return $false
  }
}

function Read-EnvPresence([string]$path) {
  $required = @(
    "APP_RAG_RETRIEVAL_HYBRID_ENABLED",
    "APP_RAG_RERANK_ENABLED",
    "APP_RAG_RERANK_PROVIDER",
    "APP_RAG_RERANK_BASE_URL",
    "APP_RAG_RERANK_MODEL",
    "APP_RAG_RERANK_API_KEY"
  )
  $values = @{}
  if (Test-Path -LiteralPath $path) {
    foreach ($line in [System.IO.File]::ReadAllLines((Resolve-Path $path), [System.Text.Encoding]::UTF8)) {
      if ($line -match '^\s*#') {
        continue
      }
      if ($line -match '^\s*([^=]+)=(.*)$') {
        $values[$Matches[1].Trim()] = $Matches[2].Trim()
      }
    }
  }
  foreach ($key in $required) {
    $value = if ($values.ContainsKey($key)) { [string]$values[$key] } else { "" }
    [PSCustomObject][ordered]@{
      key = $key
      present = $values.ContainsKey($key)
      nonEmpty = -not [string]::IsNullOrWhiteSpace($value)
      trueValue = $value.ToLowerInvariant() -eq "true"
    }
  }
}

function Stop-LocalPort([int]$port) {
  $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  foreach ($connection in $connections) {
    if ($connection.OwningProcess) {
      Stop-Process -Id $connection.OwningProcess -Force -ErrorAction SilentlyContinue
    }
  }
}

function Invoke-CloudSmokeVariant([string]$label, [bool]$rerankEnabled) {
  $repoRoot = Resolve-Path "."
  $delegate = Join-Path $repoRoot "scripts/smoke/cloud-quality-smoke.ps1"
  if (-not (Test-Path -LiteralPath $delegate)) {
    throw "cloud quality smoke script is missing"
  }

  Stop-LocalPort 8081
  if (-not $SkipFrontend) {
    try {
      $frontendUri = [Uri]$FrontendBaseUrl
      if ($frontendUri.Port -gt 0) {
        Stop-LocalPort $frontendUri.Port
      }
    } catch {
      Stop-LocalPort 3000
    }
  }
  Start-Sleep -Seconds 2

  $oldHybrid = $env:APP_RAG_RETRIEVAL_HYBRID_ENABLED
  $oldRerank = $env:APP_RAG_RERANK_ENABLED
  try {
    $env:APP_RAG_RETRIEVAL_HYBRID_ENABLED = "true"
    $env:APP_RAG_RERANK_ENABLED = if ($rerankEnabled) { "true" } else { "false" }
    $argsList = @(
      "-NoProfile",
      "-ExecutionPolicy", "Bypass",
      "-File", $delegate,
      "-Mode", "run",
      "-BackendBaseUrl", $BackendBaseUrl,
      "-FrontendBaseUrl", $FrontendBaseUrl,
      "-EnvFile", $EnvFile,
      "-ArtifactRoot", $ArtifactRoot,
      "-SmokePrefix", "docpilot-rerank-representative-$label",
      "-QualityMinSimilarityThreshold", $QualityMinSimilarityThreshold,
      "-MySqlLocalPort", $MySqlLocalPort,
      "-QdrantLocalPort", $QdrantLocalPort,
      "-IndexVersion", $IndexVersion,
      "-EnableRerankRepresentativeEvalGate"
    )
    if ($SkipFrontend) {
      $argsList += "-SkipFrontend"
    }
    $output = & powershell.exe @argsList
    if ($LASTEXITCODE -ne 0) {
      throw "cloud quality smoke variant '$label' failed"
    }
    return (($output -join "`n") | ConvertFrom-Json)
  } finally {
    if ($null -eq $oldHybrid) {
      Remove-Item Env:APP_RAG_RETRIEVAL_HYBRID_ENABLED -ErrorAction SilentlyContinue
    } else {
      $env:APP_RAG_RETRIEVAL_HYBRID_ENABLED = $oldHybrid
    }
    if ($null -eq $oldRerank) {
      Remove-Item Env:APP_RAG_RERANK_ENABLED -ErrorAction SilentlyContinue
    } else {
      $env:APP_RAG_RERANK_ENABLED = $oldRerank
    }
  }
}

function Get-RepresentativeEvalCheck($result) {
  if ($null -eq $result.gates.rerankRepresentativeEval) {
    return $null
  }
  return $result.gates.rerankRepresentativeEval.checks[0]
}

function Find-CaseById($cases, [string]$caseId) {
  return @($cases | Where-Object { $_.caseId -eq $caseId } | Select-Object -First 1)[0]
}

function Test-RankImproved([int]$baselineRank, [int]$candidateRank) {
  if ($candidateRank -le 0) {
    return $false
  }
  return $baselineRank -le 0 -or $candidateRank -lt $baselineRank
}

function Test-RankDemoted([int]$baselineRank, [int]$candidateRank) {
  if ($baselineRank -le 0) {
    return $false
  }
  return $candidateRank -le 0 -or $candidateRank -gt $baselineRank
}

function Test-TargetAboveDistractor([int]$targetRank, [int]$distractorRank) {
  if ($targetRank -le 0) {
    return $false
  }
  return $distractorRank -le 0 -or $targetRank -lt $distractorRank
}

function Write-SafeArtifact($summary) {
  $dir = Join-Path $ArtifactRoot "rerank-representative-eval"
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $path = Join-Path $dir "latest-summary.json"
  $consolePath = Join-Path $dir "artifact.json"
  $json = $summary | ConvertTo-Json -Depth 14
  $consoleArtifact = [PSCustomObject][ordered]@{
    schemaVersion = 1
    smokeMarker = $summary.rerankMarker
    overallStatus = $summary.overallStatus
    baselineMarker = $summary.baselineMarker
    rerankMarker = $summary.rerankMarker
    gates = [ordered]@{
      ragRepresentativeEval = [ordered]@{
        status = $summary.overallStatus
        checks = @([ordered]@{
            caseCount = [int]$summary.rerank.caseCount
            targetCaseCount = [int]$summary.comparison.targetCaseCount
            noEvidenceCaseCount = [int]$summary.comparison.noEvidenceCaseCount
            targetCoveragePassCount = [int]$summary.rerank.targetCoveragePassCount
            noEvidenceCorrectCount = [int]$summary.rerank.noEvidenceCorrectCount
            targetQualityCaseCount = [int]$summary.comparison.targetQualityCaseCount
            strictImprovementCaseCount = [int]$summary.comparison.strictImprovementCaseCount
            upliftCaseCount = [int]$summary.comparison.upliftCaseCount
            targetCoverageRegressionCount = [int]$summary.comparison.targetCoverageRegressionCount
            citationLeakageCount = [int]$summary.comparison.citationLeakageCount
            noEvidenceRegressionCount = [int]$summary.comparison.noEvidenceRegressionCount
            targetRerankAppliedCaseCount = [int]$summary.comparison.targetRerankAppliedCaseCount
            rerankApplied = [bool]$summary.comparison.rerankApplied
          })
      }
    }
  }
  $consoleJson = $consoleArtifact | ConvertTo-Json -Depth 8
  $redactionPatterns = @(
    '(?i)"[^"]*(api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|password|secret)[^"]*"\s*:',
    '(?i)Bearer\s+[A-Za-z0-9._-]+',
    '(?i)jdbc:',
    '(?i)mysql://',
    '(?i)qdrant.*://',
    '\b(?!127\.0\.0\.1\b)(?:\d{1,3}\.){3}\d{1,3}\b'
  )
  foreach ($pattern in $redactionPatterns) {
    if ($json -match $pattern -or $consoleJson -match $pattern) {
      throw "redaction scan failed for rerank representative eval artifact"
    }
  }
  [System.IO.File]::WriteAllText($path, $json, [System.Text.UTF8Encoding]::new($false))
  [System.IO.File]::WriteAllText($consolePath, $consoleJson, [System.Text.UTF8Encoding]::new($false))
  return $path
}

if ($Mode -eq "plan") {
  Show-Plan
  exit 0
}

if ($Mode -eq "dry-run") {
  $presence = @(Read-EnvPresence $EnvFile)
  [PSCustomObject][ordered]@{
    mode = "dry-run"
    envPresence = $presence
    mysqlPortListening = Test-TcpPort $MySqlLocalPort
    qdrantPortListening = Test-TcpPort $QdrantLocalPort
    cloudSmokeExists = Test-Path -LiteralPath "scripts/smoke/cloud-quality-smoke.ps1"
    artifactRoot = $ArtifactRoot
  } | ConvertTo-Json -Depth 8
  exit 0
}

$baseline = Invoke-CloudSmokeVariant "representative-hybrid" $false
$rerank = Invoke-CloudSmokeVariant "representative-rerank" $true

$baselineEval = Get-RepresentativeEvalCheck $baseline
$rerankEval = Get-RepresentativeEvalCheck $rerank
if ($null -eq $baselineEval -or $null -eq $rerankEval) {
  throw "rerankRepresentativeEval gate did not return comparable checks"
}

$baselineCases = @($baselineEval.caseResults)
$rerankCases = @($rerankEval.caseResults)
$caseComparisons = @()
foreach ($baselineCase in $baselineCases) {
  $candidateCase = Find-CaseById $rerankCases ([string]$baselineCase.caseId)
  if ($null -eq $candidateCase) {
    throw "candidate result is missing representative case '$($baselineCase.caseId)'"
  }
  $isNoEvidence = [bool]$candidateCase.noEvidenceExpected
  $targetRankImproved = Test-RankImproved ([int]$baselineCase.targetBestRank) ([int]$candidateCase.targetBestRank)
  $distractorDemoted = Test-RankDemoted ([int]$baselineCase.distractorBestRank) ([int]$candidateCase.distractorBestRank)
  $targetAboveDistractor = Test-TargetAboveDistractor ([int]$candidateCase.targetBestRank) ([int]$candidateCase.distractorBestRank)
  $targetCoverageRegression = (-not $isNoEvidence) -and [bool]$baselineCase.targetCovered -and (-not [bool]$candidateCase.targetCovered)
  $citationLeakage = (-not $isNoEvidence) -and [int]$candidateCase.distractorBestRank -gt 0 -and ([int]$candidateCase.targetBestRank -le 0 -or [int]$candidateCase.distractorBestRank -lt [int]$candidateCase.targetBestRank)
  $noEvidenceRegression = $isNoEvidence -and (-not [bool]$candidateCase.noEvidenceCorrect)
  $strictImprovementObserved = (-not $isNoEvidence) -and ($targetRankImproved -or $distractorDemoted)
  $qualityOrUpliftObserved = (-not $isNoEvidence) -and ($strictImprovementObserved -or $targetAboveDistractor)
  $caseComparisons += [ordered]@{
    caseId = $candidateCase.caseId
    noEvidenceExpected = $isNoEvidence
    baseline = [ordered]@{
      targetCovered = [bool]$baselineCase.targetCovered
      noEvidenceCorrect = [bool]$baselineCase.noEvidenceCorrect
      targetBestRank = [int]$baselineCase.targetBestRank
      supportBestRank = [int]$baselineCase.supportBestRank
      distractorBestRank = [int]$baselineCase.distractorBestRank
      targetRetrieveCount = [int]$baselineCase.targetRetrieveCount
      supportRetrieveCount = [int]$baselineCase.supportRetrieveCount
      distractorRetrieveCount = [int]$baselineCase.distractorRetrieveCount
      targetCitationCount = [int]$baselineCase.targetCitationCount
      distractorCitationCount = [int]$baselineCase.distractorCitationCount
      rerankApplied = [bool]$baselineCase.rerankApplied
      multiQueryApplied = [bool]$baselineCase.multiQueryApplied
      queryVariantCount = [int]$baselineCase.queryVariantCount
      queryDedupeCount = [int]$baselineCase.queryDedupeCount
    }
    rerank = [ordered]@{
      targetCovered = [bool]$candidateCase.targetCovered
      noEvidenceCorrect = [bool]$candidateCase.noEvidenceCorrect
      targetBestRank = [int]$candidateCase.targetBestRank
      supportBestRank = [int]$candidateCase.supportBestRank
      distractorBestRank = [int]$candidateCase.distractorBestRank
      targetRetrieveCount = [int]$candidateCase.targetRetrieveCount
      supportRetrieveCount = [int]$candidateCase.supportRetrieveCount
      distractorRetrieveCount = [int]$candidateCase.distractorRetrieveCount
      targetCitationCount = [int]$candidateCase.targetCitationCount
      distractorCitationCount = [int]$candidateCase.distractorCitationCount
      rerankApplied = [bool]$candidateCase.rerankApplied
      rerankModel = $candidateCase.rerankModel
      rerankFailureReason = $candidateCase.rerankFailureReason
      multiQueryApplied = [bool]$candidateCase.multiQueryApplied
      queryVariantCount = [int]$candidateCase.queryVariantCount
      queryDedupeCount = [int]$candidateCase.queryDedupeCount
    }
    targetRankImproved = $targetRankImproved
    distractorDemoted = $distractorDemoted
    targetAboveDistractor = $targetAboveDistractor
    strictImprovementObserved = $strictImprovementObserved
    qualityOrUpliftObserved = $qualityOrUpliftObserved
    targetCoverageRegression = $targetCoverageRegression
    citationLeakage = $citationLeakage
    noEvidenceRegression = $noEvidenceRegression
  }
}

$targetCaseComparisons = @($caseComparisons | Where-Object { -not $_.noEvidenceExpected })
$noEvidenceCaseComparisons = @($caseComparisons | Where-Object { $_.noEvidenceExpected })
$targetCaseCount = $targetCaseComparisons.Count
$targetQualityCaseCount = @($targetCaseComparisons | Where-Object { $_.targetAboveDistractor }).Count
$strictImprovementCaseCount = @($targetCaseComparisons | Where-Object { $_.strictImprovementObserved }).Count
$upliftCaseCount = @($targetCaseComparisons | Where-Object { $_.qualityOrUpliftObserved }).Count
$targetCoverageRegressionCount = @($targetCaseComparisons | Where-Object { $_.targetCoverageRegression }).Count
$citationLeakageCount = @($targetCaseComparisons | Where-Object { $_.citationLeakage }).Count
$noEvidenceRegressionCount = @($noEvidenceCaseComparisons | Where-Object { $_.noEvidenceRegression }).Count
$targetRerankAppliedCaseCount = @($targetCaseComparisons | Where-Object { $_.rerank.rerankApplied }).Count
$rerankApplied = $targetCaseCount -gt 0 -and $targetRerankAppliedCaseCount -eq $targetCaseCount
$rerankFailureReasons = @($targetCaseComparisons | ForEach-Object { $_.rerank.rerankFailureReason } | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } | Select-Object -Unique)
if (-not $rerankApplied -and $rerankFailureReasons.Count -eq 0) {
  $rerankFailureReasons = @("identity_fallback_or_not_applied")
}

$qualityThreshold = [Math]::Min(8, $targetCaseCount)
$status = "PASS"
if (-not $rerankApplied -or $targetQualityCaseCount -lt $qualityThreshold -or $strictImprovementCaseCount -lt 1) {
  $status = "REVIEW"
}
if ($targetCoverageRegressionCount -gt 0 -or $citationLeakageCount -gt 0 -or $noEvidenceRegressionCount -gt 0) {
  $status = "FAILED_CORE_FLOW"
}

$summary = [PSCustomObject][ordered]@{
  overallStatus = $status
  baselineMarker = $baseline.smokeMarker
  rerankMarker = $rerank.smokeMarker
  baseline = [ordered]@{
    overallStatus = $baseline.overallStatus
    representativeEvalStatus = $baseline.gates.rerankRepresentativeEval.status
    caseCount = [int]$baselineEval.caseCount
    targetCoveragePassCount = [int]$baselineEval.targetCoveragePassCount
    noEvidenceCorrectCount = [int]$baselineEval.noEvidenceCorrectCount
  }
  rerank = [ordered]@{
    overallStatus = $rerank.overallStatus
    representativeEvalStatus = $rerank.gates.rerankRepresentativeEval.status
    caseCount = [int]$rerankEval.caseCount
    targetCoveragePassCount = [int]$rerankEval.targetCoveragePassCount
    noEvidenceCorrectCount = [int]$rerankEval.noEvidenceCorrectCount
    targetRerankAppliedCaseCount = $targetRerankAppliedCaseCount
    rerankApplied = $rerankApplied
    rerankFailureReasons = $rerankFailureReasons
  }
  comparison = [ordered]@{
    targetCaseCount = $targetCaseCount
    noEvidenceCaseCount = $noEvidenceCaseComparisons.Count
    targetQualityCaseCount = $targetQualityCaseCount
    strictImprovementCaseCount = $strictImprovementCaseCount
    upliftCaseCount = $upliftCaseCount
    targetCoverageRegressionCount = $targetCoverageRegressionCount
    citationLeakageCount = $citationLeakageCount
    noEvidenceRegressionCount = $noEvidenceRegressionCount
    targetRerankAppliedCaseCount = $targetRerankAppliedCaseCount
    rerankApplied = $rerankApplied
    rerankFailureReasons = $rerankFailureReasons
  }
  caseComparisons = $caseComparisons
}

$artifact = Write-SafeArtifact $summary
$summary | Add-Member -NotePropertyName artifact -NotePropertyValue $artifact
$summary | ConvertTo-Json -Depth 14
