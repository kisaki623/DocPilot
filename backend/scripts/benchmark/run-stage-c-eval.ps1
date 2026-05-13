param(
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$DatasetPath = "docs/ai-dev/benchmarks/datasets/stagec_eval_dataset.json",
  [string]$OutputJsonPath = "docs/ai-dev/benchmarks/artifacts/stagec_eval_latest.json",
  [string]$OutputMarkdownPath = "docs/ai-dev/benchmarks/STAGEC_EVAL_RESULTS.md",
  [int]$ParseTimeoutSeconds = 120,
  [int]$PollIntervalSeconds = 2,
  [int]$CaseRetryMax = 3,
  [int]$CaseRetryDelayMs = 1500,
  [int]$StreamPairDelayMs = 13000,
  [int]$CaseIntervalMs = 1200,
  [switch]$DisableGate,
  [double]$GateAnswerSuccessRateMin = [double]::NaN,
  [double]$GateCitationHitRateMin = [double]::NaN,
  [double]$GateCasePassRateMin = [double]::NaN,
  [double]$GateStreamConsistencyMin = [double]::NaN,
  [double]$GateResponseTimeP95MsMax = [double]::NaN,
  [double]$GateStreamFirstTokenP95MsMax = [double]::NaN,
  [int]$GateMinCaseCount = -1,
  [int]$GateMinStreamPairCount = -1
)

$ErrorActionPreference = "Stop"

try {
  $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
  [Console]::OutputEncoding = $utf8NoBom
  $OutputEncoding = $utf8NoBom
} catch {
  # Best effort only.
}

function Write-Utf8File {
  param(
    [string]$LiteralPath,
    [string]$Content
  )

  $targetDir = Split-Path -Parent $LiteralPath
  if (-not [string]::IsNullOrWhiteSpace($targetDir)) {
    New-Item -Path $targetDir -ItemType Directory -Force | Out-Null
  }
  $utf8NoBomLocal = [System.Text.UTF8Encoding]::new($false)
  [System.IO.File]::WriteAllText($LiteralPath, $Content, $utf8NoBomLocal)
}

function Assert-ApiSuccess {
  param(
    [object]$Response,
    [string]$Step
  )

  if ($null -eq $Response) {
    throw "[$Step] Empty response."
  }
  if ($null -eq $Response.code) {
    throw "[$Step] Invalid response envelope: missing code."
  }
  if ($Response.code -ne 0) {
    throw "[$Step] API failed. code=$($Response.code), message=$($Response.message)"
  }
}

function Resolve-ProjectPath {
  param(
    [string]$RepoRoot,
    [string]$PathValue
  )

  if ([System.IO.Path]::IsPathRooted($PathValue)) {
    return (Resolve-Path -LiteralPath $PathValue -ErrorAction Stop).Path
  }
  return (Resolve-Path -LiteralPath (Join-Path $RepoRoot $PathValue) -ErrorAction Stop).Path
}

function Invoke-JsonPost {
  param(
    [string]$Uri,
    [hashtable]$Body,
    [hashtable]$Headers = @{}
  )

  $jsonBody = $Body | ConvertTo-Json -Depth 12 -Compress
  return Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec 60
}

function Invoke-JsonPostChecked {
  param(
    [string]$Uri,
    [hashtable]$Body,
    [hashtable]$Headers = @{},
    [string]$Step
  )

  $resp = Invoke-JsonPost -Uri $Uri -Body $Body -Headers $Headers
  Assert-ApiSuccess -Response $resp -Step $Step
  return $resp
}

function Invoke-FileUpload {
  param(
    [string]$Uri,
    [string]$Token,
    [string]$ResolvedFilePath
  )

  $args = @(
    "-sS",
    "-X", "POST",
    $Uri,
    "-H", "Authorization: Bearer $Token",
    "-F", "file=@$ResolvedFilePath"
  )
  $raw = & curl.exe @args
  if ($LASTEXITCODE -ne 0) {
    throw "File upload failed: curl exited with code $LASTEXITCODE."
  }

  try {
    return $raw | ConvertFrom-Json
  } catch {
    throw "File upload returned non-JSON response: $raw"
  }
}

function Wait-ParseSuccess {
  param(
    [string]$BaseUrl,
    [long]$DocumentId,
    [hashtable]$Headers,
    [int]$TimeoutSeconds,
    [int]$IntervalSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $detailResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/document/detail?documentId=$DocumentId" -Headers $Headers -TimeoutSec 30
    Assert-ApiSuccess -Response $detailResp -Step "document detail poll"
    $status = [string]$detailResp.data.parseStatus
    if ($status -eq "SUCCESS") {
      return $detailResp
    }
    if ($status -eq "FAILED") {
      $desc = [string]$detailResp.data.parseStatusDescription
      throw "Parse failed. statusDescription=$desc"
    }
    Start-Sleep -Seconds $IntervalSeconds
  } while ((Get-Date) -lt $deadline)

  throw "Parse timeout after $TimeoutSeconds seconds."
}

function Try-ParseJson {
  param(
    [string]$RawValue
  )

  if ([string]::IsNullOrWhiteSpace($RawValue)) {
    return $null
  }
  try {
    return $RawValue | ConvertFrom-Json
  } catch {
    return $null
  }
}

function Invoke-SseQa {
  param(
    [string]$BaseUrl,
    [string]$Token,
    [long]$DocumentId,
    [string]$Question,
    [string]$SessionId
  )

  Add-Type -AssemblyName System.Net.Http
  $client = [System.Net.Http.HttpClient]::new()
  $client.Timeout = [TimeSpan]::FromSeconds(180)

  $payload = @{
    documentId = $DocumentId
    question = $Question
    sessionId = $SessionId
  } | ConvertTo-Json -Depth 10

  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$BaseUrl/api/ai/qa/stream")
  $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
  $request.Headers.Accept.ParseAdd("text/event-stream")
  $request.Content = [System.Net.Http.StringContent]::new($payload, [System.Text.Encoding]::UTF8, "application/json")

  $response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
  if (-not $response.IsSuccessStatusCode) {
    $errorBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    throw "SSE endpoint failed: $([int]$response.StatusCode) $errorBody"
  }

  $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
  $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
  $watch = [System.Diagnostics.Stopwatch]::StartNew()

  $chunkBuilder = New-Object System.Text.StringBuilder
  $chunkCount = 0
  $firstChunkMs = $null
  $donePayload = $null
  $metaPayload = $null
  $errorPayload = $null
  $citationsFromDone = @()

  $currentEvent = "message"
  $dataLines = New-Object System.Collections.Generic.List[string]

  while (-not $reader.EndOfStream) {
    $line = $reader.ReadLine()
    if ($null -eq $line) {
      break
    }

    if ($line -eq "") {
      if ($currentEvent -ne "message" -or $dataLines.Count -gt 0) {
        $eventData = $dataLines -join "`n"
        switch ($currentEvent) {
          "meta" {
            $metaPayload = Try-ParseJson -RawValue $eventData
          }
          "chunk" {
            if ($null -eq $firstChunkMs) {
              $firstChunkMs = [int]$watch.ElapsedMilliseconds
            }
            $chunkText = $eventData
            $maybeJsonString = Try-ParseJson -RawValue $eventData
            if ($maybeJsonString -is [string]) {
              $chunkText = [string]$maybeJsonString
            }
            [void]$chunkBuilder.Append($chunkText)
            $chunkCount++
          }
          "done" {
            $donePayload = Try-ParseJson -RawValue $eventData
            if ($donePayload -and $donePayload.citations) {
              $citationsFromDone = @($donePayload.citations)
            }
            break
          }
          "error" {
            $errorPayload = Try-ParseJson -RawValue $eventData
            if ($null -eq $errorPayload) {
              $errorPayload = [PSCustomObject]@{ message = $eventData }
            }
            break
          }
        }

        if ($currentEvent -eq "done" -or $currentEvent -eq "error") {
          break
        }
      }

      $currentEvent = "message"
      $dataLines.Clear()
      continue
    }

    if ($line.StartsWith("event:")) {
      $currentEvent = $line.Substring(6).Trim()
      continue
    }
    if ($line.StartsWith("data:")) {
      $dataValue = $line.Substring(5)
      $dataLines.Add($dataValue) | Out-Null
    }
  }

  $totalMs = [int]$watch.ElapsedMilliseconds
  $watch.Stop()
  $reader.Dispose()
  $stream.Dispose()
  $response.Dispose()
  $client.Dispose()

  return [PSCustomObject]@{
    answer = $chunkBuilder.ToString()
    firstChunkMs = $firstChunkMs
    totalMs = $totalMs
    chunkCount = $chunkCount
    donePayload = $donePayload
    metaPayload = $metaPayload
    errorPayload = $errorPayload
    citations = @($citationsFromDone)
  }
}

function Normalize-AnswerText {
  param([string]$TextValue)
  if ($null -eq $TextValue) {
    return ""
  }
  return (($TextValue.ToLowerInvariant() -replace "\s+", " ").Trim())
}

function Get-KeywordHitCount {
  param(
    [string]$TextValue,
    [object[]]$Keywords
  )

  if ($null -eq $Keywords -or $Keywords.Count -eq 0) {
    return 0
  }

  $normalized = Normalize-AnswerText -TextValue $TextValue
  $compact = ($normalized -replace "[^\p{L}\p{N}]+", "")
  $hits = 0
  foreach ($keyword in $Keywords) {
    if ($null -eq $keyword) {
      continue
    }
    $needle = Normalize-AnswerText -TextValue ([string]$keyword)
    if ([string]::IsNullOrWhiteSpace($needle)) {
      continue
    }

    $directHit = $normalized.Contains($needle)
    if ($directHit) {
      $hits++
      continue
    }

    $compactNeedle = ($needle -replace "[^\p{L}\p{N}]+", "")
    if (-not [string]::IsNullOrWhiteSpace($compactNeedle) -and $compact.Contains($compactNeedle)) {
      $hits++
    }
  }
  return $hits
}

function Test-AnswerTextConsistency {
  param(
    [string]$LeftText,
    [string]$RightText
  )

  $left = Normalize-AnswerText -TextValue $LeftText
  $right = Normalize-AnswerText -TextValue $RightText
  if ([string]::IsNullOrWhiteSpace($left) -or [string]::IsNullOrWhiteSpace($right)) {
    return $false
  }

  if ($left -eq $right) {
    return $true
  }

  $leftCompact = ($left -replace "[^\p{L}\p{N}]+", "")
  $rightCompact = ($right -replace "[^\p{L}\p{N}]+", "")
  if ([string]::IsNullOrWhiteSpace($leftCompact) -or [string]::IsNullOrWhiteSpace($rightCompact)) {
    return $false
  }
  if ($leftCompact -eq $rightCompact) {
    return $true
  }

  $minLength = [Math]::Min($leftCompact.Length, $rightCompact.Length)
  if ($minLength -lt 20) {
    return $false
  }

  return $leftCompact.Contains($rightCompact) -or $rightCompact.Contains($leftCompact)
}

function Measure-CitationHit {
  param(
    [object[]]$Citations,
    [object[]]$CitationKeywords,
    [int]$MinHits,
    [int]$MinCitationCount = 1
  )

  $citationList = @($Citations)
  if ($citationList.Count -eq 0) {
    return [PSCustomObject]@{
      hit = $false
      hits = 0
      keywordSatisfied = $false
      countSatisfied = ($MinCitationCount -le 0)
      totalCount = 0
      validCount = 0
      requiredKeywordHits = [Math]::Max(1, $MinHits)
      requiredCitationCount = [Math]::Max(0, $MinCitationCount)
    }
  }

  $validCount = 0
  foreach ($item in $citationList) {
    if ($null -eq $item) {
      continue
    }
    $snippet = [string]$item.snippet
    $start = $item.charStart
    $end = $item.charEnd
    if (-not [string]::IsNullOrWhiteSpace($snippet) -and $start -ne $null -and $end -ne $null -and ([int]$end -gt [int]$start)) {
      $validCount++
    }
  }

  $allSnippets = @($citationList | ForEach-Object { [string]$_.snippet }) -join "\n"
  $normalizedSnippet = Normalize-AnswerText -TextValue $allSnippets

  $hits = 0
  $hasKeywordRule = ($null -ne $CitationKeywords -and @($CitationKeywords).Count -gt 0)
  foreach ($keyword in $CitationKeywords) {
    $needle = Normalize-AnswerText -TextValue ([string]$keyword)
    if (-not [string]::IsNullOrWhiteSpace($needle) -and $normalizedSnippet.Contains($needle)) {
      $hits++
    }
  }

  $required = [Math]::Max(1, $MinHits)
  $requiredCount = [Math]::Max(0, $MinCitationCount)
  $keywordSatisfied = if ($hasKeywordRule) { $hits -ge $required } else { $true }
  $countSatisfied = ($citationList.Count -ge $requiredCount) -and ($validCount -ge $requiredCount)
  return [PSCustomObject]@{
    hit = ($keywordSatisfied -and $countSatisfied)
    hits = $hits
    keywordSatisfied = $keywordSatisfied
    countSatisfied = $countSatisfied
    totalCount = $citationList.Count
    validCount = $validCount
    requiredKeywordHits = $required
    requiredCitationCount = $requiredCount
  }
}

function Get-CitationKeySet {
  param(
    [object[]]$Citations
  )

  $set = @{}
  foreach ($item in @($Citations)) {
    if ($null -eq $item) {
      continue
    }
    if ($item.chunkIndex -eq $null -or $item.charStart -eq $null -or $item.charEnd -eq $null) {
      continue
    }
    $start = [int]$item.charStart
    $end = [int]$item.charEnd
    if ($end -le $start) {
      continue
    }
    $key = "$([int]$item.chunkIndex):${start}:${end}"
    $set[$key] = $true
  }
  return $set
}

function Measure-CitationOverlap {
  param(
    [object[]]$NonStreamCitations,
    [object[]]$StreamCitations
  )

  $left = Get-CitationKeySet -Citations $NonStreamCitations
  $right = Get-CitationKeySet -Citations $StreamCitations

  $overlap = 0
  foreach ($key in $left.Keys) {
    if ($right.ContainsKey($key)) {
      $overlap++
    }
  }
  $denominator = [Math]::Max($left.Count, $right.Count)
  $ratio = if ($denominator -le 0) { 0.0 } else { ($overlap * 1.0 / $denominator) }

  return [PSCustomObject]@{
    leftCount = [int]$left.Count
    rightCount = [int]$right.Count
    overlapCount = [int]$overlap
    overlapRatio = [double]$ratio
  }
}

function Measure-StreamConsistency {
  param(
    [string]$NonStreamAnswer,
    [string]$StreamAnswer,
    [object[]]$ExpectedKeywords,
    [int]$NonStreamMinKeywordHits,
    [int]$StreamMinKeywordHits,
    [object[]]$MustNotContainKeywords,
    [object[]]$NonStreamCitations,
    [object[]]$StreamCitations,
    [bool]$RequireCitationOverlap,
    [double]$MinCitationOverlapRatio
  )

  $left = Normalize-AnswerText -TextValue $NonStreamAnswer
  $right = Normalize-AnswerText -TextValue $StreamAnswer
  if ([string]::IsNullOrWhiteSpace($left) -or [string]::IsNullOrWhiteSpace($right)) {
    return [PSCustomObject]@{
      pass = $false
      reason = "empty_answer"
      nonStreamKeywordHits = 0
      streamKeywordHits = 0
      nonStreamForbiddenHits = 0
      streamForbiddenHits = 0
      keywordPass = $false
      textEquivalent = $false
      compactEquivalent = $false
      lengthDeltaRatio = 1.0
      citationOverlapCount = 0
      citationOverlapRatio = 0.0
      citationLeftCount = 0
      citationRightCount = 0
      citationPass = $false
    }
  }

  $leftCompact = ($left -replace "[^\p{L}\p{N}]+", "")
  $rightCompact = ($right -replace "[^\p{L}\p{N}]+", "")
  $textEquivalent = ($left -eq $right)
  $compactEquivalent = (-not [string]::IsNullOrWhiteSpace($leftCompact)) -and ($leftCompact -eq $rightCompact)

  $nonStreamKeywordHits = Get-KeywordHitCount -TextValue $NonStreamAnswer -Keywords $ExpectedKeywords
  $streamKeywordHits = Get-KeywordHitCount -TextValue $StreamAnswer -Keywords $ExpectedKeywords
  $nonStreamForbiddenHits = Get-KeywordHitCount -TextValue $NonStreamAnswer -Keywords $MustNotContainKeywords
  $streamForbiddenHits = Get-KeywordHitCount -TextValue $StreamAnswer -Keywords $MustNotContainKeywords

  $requiredNonStream = [Math]::Max(0, $NonStreamMinKeywordHits)
  $requiredStream = [Math]::Max(0, $StreamMinKeywordHits)
  $keywordPass = ($nonStreamKeywordHits -ge $requiredNonStream) -and ($streamKeywordHits -ge $requiredStream) -and ($nonStreamForbiddenHits -eq 0) -and ($streamForbiddenHits -eq 0)

  $overlap = Measure-CitationOverlap -NonStreamCitations $NonStreamCitations -StreamCitations $StreamCitations
  $citationPass = $true
  if ($RequireCitationOverlap) {
    $citationPass = ($overlap.leftCount -gt 0) -and ($overlap.rightCount -gt 0) -and ($overlap.overlapRatio -ge $MinCitationOverlapRatio)
  }

  $maxLength = [Math]::Max($leftCompact.Length, $rightCompact.Length)
  $lengthDeltaRatio = if ($maxLength -le 0) { 0.0 } else { ([Math]::Abs($leftCompact.Length - $rightCompact.Length) * 1.0 / $maxLength) }
  $lengthClose = ($lengthDeltaRatio -le 0.35)

  $pass = $keywordPass -and $citationPass -and (($textEquivalent -or $compactEquivalent) -or $lengthClose)
  $reason = "consistent_by_keyword_citation_alignment"
  if (-not $keywordPass) {
    $reason = "keyword_or_forbidden_mismatch"
  } elseif (-not $citationPass) {
    $reason = "citation_overlap_below_threshold"
  } elseif ($textEquivalent -or $compactEquivalent) {
    $reason = "text_equivalent"
  } elseif (-not $lengthClose) {
    $reason = "length_gap_too_large"
  }

  return [PSCustomObject]@{
    pass = $pass
    reason = $reason
    nonStreamKeywordHits = $nonStreamKeywordHits
    streamKeywordHits = $streamKeywordHits
    nonStreamForbiddenHits = $nonStreamForbiddenHits
    streamForbiddenHits = $streamForbiddenHits
    keywordPass = $keywordPass
    textEquivalent = $textEquivalent
    compactEquivalent = $compactEquivalent
    lengthDeltaRatio = [double]$lengthDeltaRatio
    citationOverlapCount = [int]$overlap.overlapCount
    citationOverlapRatio = [double]$overlap.overlapRatio
    citationLeftCount = [int]$overlap.leftCount
    citationRightCount = [int]$overlap.rightCount
    citationPass = $citationPass
  }
}

function Get-Percentile {
  param(
    [double[]]$Values,
    [double]$Percentile
  )

  if ($null -eq $Values -or $Values.Count -eq 0) {
    return 0.0
  }
  $sorted = $Values | Sort-Object
  $index = [int][Math]::Ceiling($sorted.Count * $Percentile) - 1
  if ($index -lt 0) { $index = 0 }
  if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
  return [double]$sorted[$index]
}

function Format-Double {
  param([double]$Value)
  return [Math]::Round($Value, 3)
}

function Fix-ArtifactText {
  param([string]$Raw)

  if ([string]::IsNullOrWhiteSpace($Raw)) {
    return ""
  }

  $text = $Raw.Replace([string][char]0xFEFF, "")

  if ($text -match "[ÃÂâæåçéï]") {
    try {
      $latin1 = [System.Text.Encoding]::GetEncoding("ISO-8859-1")
      $bytes = $latin1.GetBytes($text)
      $decoded = [System.Text.Encoding]::UTF8.GetString($bytes)
      if (-not [string]::IsNullOrWhiteSpace($decoded)) {
        $text = $decoded
      }
    } catch {
      # keep original text
    }
  }

  return ($text -replace "\s+", " ").Trim()
}

function Get-PreviewText {
  param(
    [string]$Raw,
    [int]$MaxLength = 180
  )

  if ([string]::IsNullOrWhiteSpace($Raw)) {
    return ""
  }
  if ($Raw.Length -le $MaxLength) {
    return $Raw
  }
  return $Raw.Substring(0, $MaxLength) + "..."
}

function Resolve-GateValue {
  param(
    [double]$Override,
    [object]$DatasetValue,
    [double]$Fallback
  )

  if (-not [double]::IsNaN($Override)) {
    return $Override
  }
  if ($null -ne $DatasetValue) {
    return [double]$DatasetValue
  }
  return $Fallback
}

function Test-IsRetryableEvalError {
  param(
    [string]$Message
  )

  if ([string]::IsNullOrWhiteSpace($Message)) {
    return $false
  }

  $normalized = $Message.ToLowerInvariant()
  $retryHints = @(
    "code=1014",
    "too frequent",
    "rate limit",
    "too many requests",
    "429",
    "500",
    "timeout",
    "timed out",
    "502",
    "503",
    "504",
    "connection was closed",
    "forcibly closed"
  )

  foreach ($hint in $retryHints) {
    if ($normalized.Contains($hint)) {
      return $true
    }
  }

  return $false
}

function Resolve-RetryDelayMs {
  param(
    [string]$Message,
    [int]$DefaultDelayMs
  )

  if ([string]::IsNullOrWhiteSpace($Message)) {
    return $DefaultDelayMs
  }

  $normalized = $Message.ToLowerInvariant()
  if ($normalized.Contains("code=1014") -or $normalized.Contains("too frequent") -or $normalized.Contains("rate limit")) {
    return [Math]::Max($DefaultDelayMs, 13000)
  }

  return $DefaultDelayMs
}

function Invoke-WithRetry {
  param(
    [scriptblock]$Action,
    [string]$Label,
    [int]$MaxAttempts = 3,
    [int]$DelayMs = 700
  )

  $attempts = [Math]::Max(1, $MaxAttempts)
  for ($attempt = 1; $attempt -le $attempts; $attempt++) {
    try {
      return & $Action
    } catch {
      $message = $_.Exception.Message
      $isRetryable = Test-IsRetryableEvalError -Message $message
      $hasNext = $attempt -lt $attempts
      if (-not $hasNext -or -not $isRetryable) {
        throw
      }

      $retryDelayMs = Resolve-RetryDelayMs -Message $message -DefaultDelayMs $DelayMs
      Write-Host "[retry][$Label] attempt $attempt/$attempts failed, wait ${retryDelayMs}ms: $message"
      Start-Sleep -Milliseconds $retryDelayMs
    }
  }

  throw "[$Label] Retry exhausted."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..\..")
$resolvedDatasetPath = Resolve-ProjectPath -RepoRoot $repoRoot -PathValue $DatasetPath
$resolvedJsonPath = Join-Path $repoRoot $OutputJsonPath
$resolvedMarkdownPath = Join-Path $repoRoot $OutputMarkdownPath

$dataset = Get-Content -Raw -LiteralPath $resolvedDatasetPath | ConvertFrom-Json
if ($null -eq $dataset.cases -or @($dataset.cases).Count -eq 0) {
  throw "Dataset contains no cases: $resolvedDatasetPath"
}

$gateConfig = $dataset.gates
$gateEnabled = -not $DisableGate
if ($null -ne $gateConfig -and $null -ne $gateConfig.enabled) {
  $gateEnabled = $gateEnabled -and [bool]$gateConfig.enabled
}

$gateThresholds = if ($null -ne $gateConfig.thresholds) { $gateConfig.thresholds } else { $null }
$minCaseCount = if ($GateMinCaseCount -gt 0) { $GateMinCaseCount } elseif ($null -ne $gateConfig.minCaseCount) { [int]$gateConfig.minCaseCount } else { 12 }
$minStreamPairCount = if ($GateMinStreamPairCount -ge 0) { $GateMinStreamPairCount } elseif ($null -ne $gateConfig.minStreamPairCount) { [int]$gateConfig.minStreamPairCount } else { 4 }

$thresholdAnswerSuccess = Resolve-GateValue -Override $GateAnswerSuccessRateMin -DatasetValue $gateThresholds.answerSuccessRateMin -Fallback 50
$thresholdCitationHit = Resolve-GateValue -Override $GateCitationHitRateMin -DatasetValue $gateThresholds.citationHitRateMin -Fallback 50
$thresholdCasePass = Resolve-GateValue -Override $GateCasePassRateMin -DatasetValue $gateThresholds.casePassRateMin -Fallback 60
$thresholdStreamConsistency = Resolve-GateValue -Override $GateStreamConsistencyMin -DatasetValue $gateThresholds.streamVsNonStreamConsistencyMin -Fallback 90
$thresholdResponseP95 = Resolve-GateValue -Override $GateResponseTimeP95MsMax -DatasetValue $gateThresholds.responseTimeP95MsMax -Fallback 12000
$thresholdStreamFirstTokenP95 = Resolve-GateValue -Override $GateStreamFirstTokenP95MsMax -DatasetValue $gateThresholds.streamFirstTokenP95MsMax -Fallback 8000

$baseUrl = $BackendBaseUrl.TrimEnd("/")
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$username = "eval_user_$runId"
$password = "DocPilot@Eval2026"

Write-Host "== Stage C Eval Start =="
Write-Host "BaseUrl: $baseUrl"
Write-Host "Dataset: $resolvedDatasetPath"

try {
  $health = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5
  if ($health.StatusCode -ne 200) {
    throw "Health check returned status $($health.StatusCode)"
  }
} catch {
  throw "Backend is not ready at $baseUrl."
}

$registerResp = Invoke-JsonPost -Uri "$baseUrl/api/auth/register" -Body @{
  username = $username
  nickname = "StageCEval"
  password = $password
}
Assert-ApiSuccess -Response $registerResp -Step "register"
$token = [string]$registerResp.data.token
if ([string]::IsNullOrWhiteSpace($token)) {
  throw "[register] Missing token."
}
$headers = @{ Authorization = "Bearer $token" }

$tempDir = Join-Path $repoRoot "pwtmp"
New-Item -Path $tempDir -ItemType Directory -Force | Out-Null
$evalFileName = "stagec-eval-$runId.md"
$evalFilePath = Join-Path $tempDir $evalFileName
Write-Utf8File -LiteralPath $evalFilePath -Content ([string]$dataset.document.content)

$uploadResp = Invoke-FileUpload -Uri "$baseUrl/api/file/upload" -Token $token -ResolvedFilePath $evalFilePath
Assert-ApiSuccess -Response $uploadResp -Step "file upload"
$fileRecordId = [long]$uploadResp.data.id
if ($fileRecordId -le 0) {
  throw "[file upload] Invalid fileRecordId: $fileRecordId"
}

$createDocResp = Invoke-JsonPost -Uri "$baseUrl/api/document/create" -Headers $headers -Body @{ fileRecordId = $fileRecordId }
Assert-ApiSuccess -Response $createDocResp -Step "document create"
$documentId = [long]$createDocResp.data.id
if ($documentId -le 0) {
  throw "[document create] Invalid documentId: $documentId"
}

$parseResp = Invoke-JsonPost -Uri "$baseUrl/api/task/parse/create" -Headers $headers -Body @{ documentId = $documentId }
Assert-ApiSuccess -Response $parseResp -Step "parse create"
Wait-ParseSuccess -BaseUrl $baseUrl -DocumentId $documentId -Headers $headers -TimeoutSeconds $ParseTimeoutSeconds -IntervalSeconds $PollIntervalSeconds | Out-Null

$caseResults = @()
$requestDurations = @()
$streamFirstTokenDurations = @()

foreach ($caseItem in @($dataset.cases)) {
  $caseId = [string]$caseItem.id
  $question = [string]$caseItem.question
  $requestMode = if ($caseItem.requestMode) { ([string]$caseItem.requestMode).ToLowerInvariant() } else { "qa" }

  $expectedKeywords = @($caseItem.expectedKeywords)
  $minKeywordHits = if ($caseItem.minKeywordHits -ne $null) { [int]$caseItem.minKeywordHits } else { 1 }
  $streamMinKeywordHits = if ($caseItem.streamMinKeywordHits -ne $null) { [int]$caseItem.streamMinKeywordHits } else { $minKeywordHits }
  $mustNotContainKeywords = @($caseItem.mustNotContainKeywords)

  $citationKeywords = @($caseItem.citationKeywords)
  $minCitationKeywordHits = if ($caseItem.minCitationKeywordHits -ne $null) { [int]$caseItem.minCitationKeywordHits } else { 1 }
  $expectCitation = if ($caseItem.expectCitation -ne $null) { [bool]$caseItem.expectCitation } else { $true }
  $minCitationCount = if ($caseItem.minCitationCount -ne $null) { [int]$caseItem.minCitationCount } elseif ($expectCitation) { 1 } else { 0 }
  $streamMinCitationOverlapRatio = if ($caseItem.streamMinCitationOverlapRatio -ne $null) { [double]$caseItem.streamMinCitationOverlapRatio } else { 0.5 }

  $runStreamPair = if ($caseItem.runStreamPair -ne $null) { [bool]$caseItem.runStreamPair } else { $false }
  if ($requestMode -eq "agent") {
    $runStreamPair = $false
  }

  $expectedDecision = if ($caseItem.expectedDecision) { [string]$caseItem.expectedDecision } else { "" }
  $minStepCount = if ($caseItem.minStepCount -ne $null) { [int]$caseItem.minStepCount } else { 0 }

  $sessionId = "eval-$caseId-$runId"
  $apiDurationWatch = [System.Diagnostics.Stopwatch]::StartNew()

  $answer = ""
  $citations = @()
  $decision = ""
  $stepCount = 0
  $decisionMatched = $true
  $stepCountMatched = $true
  $apiErrorMessage = ""

  try {
    if ($requestMode -eq "agent") {
      $agentResp = Invoke-WithRetry -Label "agent/$caseId" -MaxAttempts $CaseRetryMax -DelayMs $CaseRetryDelayMs -Action {
        Invoke-JsonPostChecked -Uri "$baseUrl/api/ai/agent/run" -Headers $headers -Step "agent/$caseId" -Body @{
          documentId = $documentId
          task = $question
          sessionId = $sessionId
        }
      }

      $answer = [string]$agentResp.data.finalAnswer
      if ($agentResp.data.citations) {
        $citations = @($agentResp.data.citations)
      }
      $decision = [string]$agentResp.data.decision
      $stepCount = @($agentResp.data.steps).Count

      if (-not [string]::IsNullOrWhiteSpace($expectedDecision)) {
        $decisionMatched = ($decision -eq $expectedDecision)
      }
      if ($minStepCount -gt 0) {
        $stepCountMatched = ($stepCount -ge $minStepCount)
      }
    } else {
      $qaResp = Invoke-WithRetry -Label "qa/$caseId" -MaxAttempts $CaseRetryMax -DelayMs $CaseRetryDelayMs -Action {
        Invoke-JsonPostChecked -Uri "$baseUrl/api/ai/qa" -Headers $headers -Step "qa/$caseId" -Body @{
          documentId = $documentId
          question = $question
          sessionId = "$sessionId-ns"
        }
      }

      $answer = [string]$qaResp.data.answer
      if ($qaResp.data.citations) {
        $citations = @($qaResp.data.citations)
      }
    }
  } catch {
    $apiErrorMessage = $_.Exception.Message
  } finally {
    $apiDurationWatch.Stop()
  }

  $nonStreamDurationMs = [double]$apiDurationWatch.ElapsedMilliseconds
  $requestDurations += $nonStreamDurationMs

  $keywordHits = Get-KeywordHitCount -TextValue $answer -Keywords $expectedKeywords
  $forbiddenKeywordHits = Get-KeywordHitCount -TextValue $answer -Keywords $mustNotContainKeywords
  $answerSuccess = ($keywordHits -ge $minKeywordHits) -and ($forbiddenKeywordHits -eq 0)

  $citationEval = Measure-CitationHit -Citations $citations -CitationKeywords $citationKeywords -MinHits $minCitationKeywordHits -MinCitationCount $minCitationCount
  $citationSatisfied = if ($expectCitation) { [bool]$citationEval.hit } else { $true }

  $streamConsistent = $null
  $streamConsistencyReason = ""
  $streamDurationMs = $null
  $streamFirstTokenMs = $null
  $streamChunkCount = $null
  $streamAnswerPreview = ""
  $streamKeywordHits = $null
  $streamForbiddenKeywordHits = $null
  $streamCitationOverlapCount = $null
  $streamCitationOverlapRatio = $null
  $streamLengthDeltaRatio = $null
  $streamErrorMessage = ""

  if ($runStreamPair -and [string]::IsNullOrWhiteSpace($apiErrorMessage)) {
    try {
      if ($StreamPairDelayMs -gt 0) {
        Start-Sleep -Milliseconds $StreamPairDelayMs
      }
      $streamResult = Invoke-WithRetry -Label "sse/$caseId" -MaxAttempts $CaseRetryMax -DelayMs $CaseRetryDelayMs -Action {
        $result = Invoke-SseQa -BaseUrl $baseUrl -Token $token -DocumentId $documentId -Question $question -SessionId "$sessionId-sse"
        if ($result.errorPayload) {
          $errorMessage = [string]$result.errorPayload.message
          throw "SSE eval payload error for case=${caseId}: $errorMessage"
        }
        return $result
      }

      $streamAnswer = [string]$streamResult.answer
      $streamConsistencyEval = Measure-StreamConsistency `
        -NonStreamAnswer $answer `
        -StreamAnswer $streamAnswer `
        -ExpectedKeywords $expectedKeywords `
        -NonStreamMinKeywordHits $minKeywordHits `
        -StreamMinKeywordHits $streamMinKeywordHits `
        -MustNotContainKeywords $mustNotContainKeywords `
        -NonStreamCitations $citations `
        -StreamCitations @($streamResult.citations) `
        -RequireCitationOverlap $expectCitation `
        -MinCitationOverlapRatio $streamMinCitationOverlapRatio

      $streamConsistent = [bool]$streamConsistencyEval.pass
      $streamConsistencyReason = [string]$streamConsistencyEval.reason
      $streamKeywordHits = [int]$streamConsistencyEval.streamKeywordHits
      $streamForbiddenKeywordHits = [int]$streamConsistencyEval.streamForbiddenHits
      $streamCitationOverlapCount = [int]$streamConsistencyEval.citationOverlapCount
      $streamCitationOverlapRatio = [double]$streamConsistencyEval.citationOverlapRatio
      $streamLengthDeltaRatio = [double]$streamConsistencyEval.lengthDeltaRatio

      $streamDurationMs = [double]$streamResult.totalMs
      $streamFirstTokenMs = $streamResult.firstChunkMs
      $streamChunkCount = [int]$streamResult.chunkCount
      $streamAnswerPreview = Fix-ArtifactText -Raw (Get-PreviewText -Raw $streamAnswer -MaxLength 180)

      $requestDurations += $streamDurationMs
      if ($streamResult.firstChunkMs -ne $null) {
        $streamFirstTokenDurations += [double]$streamResult.firstChunkMs
      }
    } catch {
      $streamConsistent = $false
      $streamConsistencyReason = "stream_eval_exception"
      $streamErrorMessage = $_.Exception.Message
    }
  } elseif ($runStreamPair) {
    $streamConsistent = $false
    $streamConsistencyReason = "non_stream_failed"
    $streamErrorMessage = "Skipped stream check because non-stream request failed: $apiErrorMessage"
  }

  $casePass = $answerSuccess -and $citationSatisfied -and $decisionMatched -and $stepCountMatched
  if ($runStreamPair) {
    $casePass = $casePass -and ($streamConsistent -eq $true)
  }

  $caseResults += [PSCustomObject]@{
    id = $caseId
    type = [string]$caseItem.type
    requestMode = $requestMode
    question = $question
    expectedKeywords = $expectedKeywords
    minKeywordHits = $minKeywordHits
    streamMinKeywordHits = $streamMinKeywordHits
    keywordHits = $keywordHits
    forbiddenKeywordHits = $forbiddenKeywordHits
    answerSuccess = $answerSuccess
    citationKeywords = $citationKeywords
    minCitationCount = $minCitationCount
    minCitationKeywordHits = $minCitationKeywordHits
    citationKeywordHits = [int]$citationEval.hits
    citationValidCount = [int]$citationEval.validCount
    citationCountSatisfied = [bool]$citationEval.countSatisfied
    citationKeywordSatisfied = [bool]$citationEval.keywordSatisfied
    citationSatisfied = $citationSatisfied
    expectCitation = $expectCitation
    casePass = $casePass
    decision = $decision
    expectedDecision = $expectedDecision
    decisionMatched = $decisionMatched
    stepCount = $stepCount
    minStepCount = $minStepCount
    stepCountMatched = $stepCountMatched
    apiErrorMessage = $apiErrorMessage
    nonStreamDurationMs = $nonStreamDurationMs
    citationCount = @($citations).Count
    firstCitationSnippet = if (@($citations).Count -gt 0) { Fix-ArtifactText -Raw ([string]$citations[0].snippet) } else { "" }
    nonStreamAnswerPreview = Fix-ArtifactText -Raw (Get-PreviewText -Raw $answer -MaxLength 180)
    runStreamPair = $runStreamPair
    streamConsistent = $streamConsistent
    streamConsistencyReason = $streamConsistencyReason
    streamKeywordHits = $streamKeywordHits
    streamForbiddenKeywordHits = $streamForbiddenKeywordHits
    streamCitationOverlapCount = $streamCitationOverlapCount
    streamCitationOverlapRatio = if ($streamCitationOverlapRatio -ne $null) { Format-Double $streamCitationOverlapRatio } else { $null }
    streamLengthDeltaRatio = if ($streamLengthDeltaRatio -ne $null) { Format-Double $streamLengthDeltaRatio } else { $null }
    streamDurationMs = $streamDurationMs
    streamFirstTokenMs = $streamFirstTokenMs
    streamChunkCount = $streamChunkCount
    streamAnswerPreview = $streamAnswerPreview
    streamErrorMessage = $streamErrorMessage
  }

  if ($CaseIntervalMs -gt 0) {
    Start-Sleep -Milliseconds $CaseIntervalMs
  }
}

$answerSuccessCount = @($caseResults | Where-Object { $_.answerSuccess }).Count
$citationEligible = @($caseResults | Where-Object { $_.expectCitation -eq $true })
$citationHitCount = @($citationEligible | Where-Object { $_.citationSatisfied -eq $true }).Count
$consistencyPairs = @($caseResults | Where-Object { $_.runStreamPair -eq $true }).Count
$consistencyHitCount = @($caseResults | Where-Object { $_.runStreamPair -eq $true -and $_.streamConsistent -eq $true }).Count
$casePassCount = @($caseResults | Where-Object { $_.casePass -eq $true }).Count

$answerSuccessRate = if ($caseResults.Count -eq 0) { 0.0 } else { ($answerSuccessCount * 100.0 / $caseResults.Count) }
$citationHitRate = if ($citationEligible.Count -eq 0) { 0.0 } else { ($citationHitCount * 100.0 / $citationEligible.Count) }
$consistencyRate = if ($consistencyPairs -eq 0) { 0.0 } else { ($consistencyHitCount * 100.0 / $consistencyPairs) }
$casePassRate = if ($caseResults.Count -eq 0) { 0.0 } else { ($casePassCount * 100.0 / $caseResults.Count) }
$responseAvg = if ($requestDurations.Count -eq 0) { 0.0 } else { ($requestDurations | Measure-Object -Average).Average }
$responseP95 = Get-Percentile -Values $requestDurations -Percentile 0.95
$streamFirstTokenAvg = if ($streamFirstTokenDurations.Count -eq 0) { 0.0 } else { ($streamFirstTokenDurations | Measure-Object -Average).Average }
$streamFirstTokenP95 = Get-Percentile -Values $streamFirstTokenDurations -Percentile 0.95

$metrics = [ordered]@{
  answerSuccessRate = Format-Double $answerSuccessRate
  citationHitRate = Format-Double $citationHitRate
  casePassRate = Format-Double $casePassRate
  responseTimeMs = [ordered]@{
    avg = Format-Double $responseAvg
    p95 = Format-Double $responseP95
  }
  streamVsNonStreamConsistency = Format-Double $consistencyRate
  streamFirstTokenMs = [ordered]@{
    avg = Format-Double $streamFirstTokenAvg
    p95 = Format-Double $streamFirstTokenP95
  }
}

$gateChecks = @()
function Add-GateCheck {
  param(
    [string]$Name,
    [bool]$Pass,
    [string]$Expect,
    [string]$Actual
  )

  $script:gateChecks += [PSCustomObject]@{
    name = $Name
    pass = $Pass
    expect = $Expect
    actual = $Actual
  }
}

Add-GateCheck -Name "caseCount" -Pass ($caseResults.Count -ge $minCaseCount) -Expect ">= $minCaseCount" -Actual "$($caseResults.Count)"
Add-GateCheck -Name "streamPairCount" -Pass ($consistencyPairs -ge $minStreamPairCount) -Expect ">= $minStreamPairCount" -Actual "$consistencyPairs"
Add-GateCheck -Name "answerSuccessRate" -Pass ($answerSuccessRate -ge $thresholdAnswerSuccess) -Expect ">= $thresholdAnswerSuccess" -Actual ("{0}" -f (Format-Double $answerSuccessRate))
Add-GateCheck -Name "citationHitRate" -Pass ($citationHitRate -ge $thresholdCitationHit) -Expect ">= $thresholdCitationHit" -Actual ("{0}" -f (Format-Double $citationHitRate))
Add-GateCheck -Name "casePassRate" -Pass ($casePassRate -ge $thresholdCasePass) -Expect ">= $thresholdCasePass" -Actual ("{0}" -f (Format-Double $casePassRate))
Add-GateCheck -Name "streamVsNonStreamConsistency" -Pass ($consistencyRate -ge $thresholdStreamConsistency) -Expect ">= $thresholdStreamConsistency" -Actual ("{0}" -f (Format-Double $consistencyRate))
Add-GateCheck -Name "responseTimeP95Ms" -Pass ($responseP95 -le $thresholdResponseP95) -Expect "<= $thresholdResponseP95" -Actual ("{0}" -f (Format-Double $responseP95))
if ($streamFirstTokenDurations.Count -gt 0) {
  Add-GateCheck -Name "streamFirstTokenP95Ms" -Pass ($streamFirstTokenP95 -le $thresholdStreamFirstTokenP95) -Expect "<= $thresholdStreamFirstTokenP95" -Actual ("{0}" -f (Format-Double $streamFirstTokenP95))
} else {
  Add-GateCheck -Name "streamFirstTokenP95Ms" -Pass $false -Expect "<= $thresholdStreamFirstTokenP95" -Actual "N/A"
}

$failedChecks = @($gateChecks | Where-Object { $_.pass -ne $true } | ForEach-Object { $_.name })
$gatePassed = ($failedChecks.Count -eq 0)

$gate = [ordered]@{
  enabled = $gateEnabled
  passed = if ($gateEnabled) { $gatePassed } else { $true }
  thresholds = [ordered]@{
    minCaseCount = $minCaseCount
    minStreamPairCount = $minStreamPairCount
    answerSuccessRateMin = $thresholdAnswerSuccess
    citationHitRateMin = $thresholdCitationHit
    casePassRateMin = $thresholdCasePass
    streamVsNonStreamConsistencyMin = $thresholdStreamConsistency
    responseTimeP95MsMax = $thresholdResponseP95
    streamFirstTokenP95MsMax = $thresholdStreamFirstTokenP95
  }
  actual = [ordered]@{
    caseCount = $caseResults.Count
    streamPairCount = $consistencyPairs
    answerSuccessRate = Format-Double $answerSuccessRate
    citationHitRate = Format-Double $citationHitRate
    streamVsNonStreamConsistency = Format-Double $consistencyRate
    responseTimeP95Ms = Format-Double $responseP95
    streamFirstTokenP95Ms = Format-Double $streamFirstTokenP95
    casePassRate = Format-Double $casePassRate
  }
  checks = $gateChecks
  failedChecks = $failedChecks
}

$resultPayload = [ordered]@{
  generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
  backendBaseUrl = $baseUrl
  datasetPath = $DatasetPath
  datasetName = [string]$dataset.datasetName
  datasetVersion = [string]$dataset.datasetVersion
  document = [ordered]@{
    fileName = [string]$dataset.document.fileName
    documentId = $documentId
    fileRecordId = $fileRecordId
    parseStatus = "SUCCESS"
  }
  metrics = $metrics
  gate = $gate
  caseResults = $caseResults
  boundaryNotes = @(
    "Current QA is lightweight retrieval-enhanced QA, not vector RAG.",
    "This eval runs against current local/backend environment and does not represent online SLA.",
    "PDF parsing remains placeholder; main parsing support is txt/md."
  )
  scoringRules = [ordered]@{
    answerSuccess = "keywordHits >= minKeywordHits AND mustNotContainKeywords not hit"
    citationSatisfied = "expectCitation=true then citationKeywordHits >= minCitationKeywordHits AND citationCount/validCount >= minCitationCount; otherwise true"
    casePass = "answerSuccess AND citationSatisfied AND mode checks (and stream consistency for stream pair)"
    streamConsistency = "both sides pass keyword/forbidden checks AND citation overlap ratio >= threshold (if expectCitation) AND (text equivalent OR length delta ratio <= 0.35)"
  }
}

$jsonDir = Split-Path -Parent $resolvedJsonPath
$mdDir = Split-Path -Parent $resolvedMarkdownPath
New-Item -Path $jsonDir -ItemType Directory -Force | Out-Null
New-Item -Path $mdDir -ItemType Directory -Force | Out-Null

$jsonText = $resultPayload | ConvertTo-Json -Depth 20
Write-Utf8File -LiteralPath $resolvedJsonPath -Content $jsonText

$mdLines = @()
$mdLines += "# Stage C Eval Results"
$mdLines += ""
$mdLines += "- GeneratedAt: ``$($resultPayload.generatedAt)``"
$mdLines += "- Dataset: ``$DatasetPath``"
$mdLines += "- BackendBaseUrl: ``$baseUrl``"
$mdLines += "- DocumentId: ``$documentId``"
$mdLines += "- CaseCount / StreamPairs: ``$($caseResults.Count) / $consistencyPairs``"
$mdLines += ""
$mdLines += "## Core Metrics"
$mdLines += ""
$mdLines += "| metric | value |"
$mdLines += "|---|---:|"
$mdLines += "| answerSuccessRate (%) | $($metrics.answerSuccessRate) |"
$mdLines += "| citationHitRate (%) | $($metrics.citationHitRate) |"
$mdLines += "| casePassRate (%) | $($metrics.casePassRate) |"
$mdLines += "| responseTime avg (ms) | $($metrics.responseTimeMs.avg) |"
$mdLines += "| responseTime p95 (ms) | $($metrics.responseTimeMs.p95) |"
$mdLines += "| streamVsNonStreamConsistency (%) | $($metrics.streamVsNonStreamConsistency) |"
$mdLines += "| stream first token avg (ms) | $($metrics.streamFirstTokenMs.avg) |"
$mdLines += "| stream first token p95 (ms) | $($metrics.streamFirstTokenMs.p95) |"
$mdLines += ""
$mdLines += "## Gate"
$mdLines += ""
$mdLines += "- Enabled: ``$($gate.enabled)``"
$mdLines += "- Passed: ``$($gate.passed)``"
$mdLines += ""
$mdLines += "| check | pass | expect | actual |"
$mdLines += "|---|---|---|---|"
foreach ($check in $gate.checks) {
  $mdLines += "| $($check.name) | $($check.pass) | $($check.expect) | $($check.actual) |"
}
$mdLines += ""
$mdLines += "## Per-case Detail"
$mdLines += ""
$mdLines += "| caseId | mode | type | answerSuccess | citationSatisfied | casePass | keywordHits | citationHits | citationCount | decision | streamConsistent | streamReason | nonStreamMs | streamMs |"
$mdLines += "|---|---|---|---|---|---|---:|---:|---:|---|---|---|---:|---:|"
foreach ($item in $caseResults) {
  $mdLines += "| $($item.id) | $($item.requestMode) | $($item.type) | $($item.answerSuccess) | $($item.citationSatisfied) | $($item.casePass) | $($item.keywordHits) | $($item.citationKeywordHits) | $($item.citationCount) | $($item.decision) | $($item.streamConsistent) | $($item.streamConsistencyReason) | $([Math]::Round([double]$item.nonStreamDurationMs, 3)) | $($item.streamDurationMs) |"
}
$mdLines += ""
$mdLines += "## Scoring Rules"
$mdLines += ""
$mdLines += "- answerSuccess: ``$($resultPayload.scoringRules.answerSuccess)``"
$mdLines += "- citationSatisfied: ``$($resultPayload.scoringRules.citationSatisfied)``"
$mdLines += "- casePass: ``$($resultPayload.scoringRules.casePass)``"
$mdLines += "- streamConsistency: ``$($resultPayload.scoringRules.streamConsistency)``"
$mdLines += ""
$mdLines += "## Boundary Notes"
$mdLines += ""
foreach ($line in $resultPayload.boundaryNotes) {
  $mdLines += "- $line"
}
$mdLines += ""
$mdLines += "## Artifact"
$mdLines += ""
$mdLines += "- JSON: ``$OutputJsonPath``"

$mdText = $mdLines -join "`n"
Write-Utf8File -LiteralPath $resolvedMarkdownPath -Content $mdText

Write-Host "== Stage C Eval Completed =="
Write-Host "answerSuccessRate: $($metrics.answerSuccessRate)%"
Write-Host "citationHitRate: $($metrics.citationHitRate)%"
Write-Host "casePassRate: $($metrics.casePassRate)%"
Write-Host "response avg/p95: $($metrics.responseTimeMs.avg) / $($metrics.responseTimeMs.p95) ms"
Write-Host "stream consistency: $($metrics.streamVsNonStreamConsistency)%"
Write-Host "gate passed: $($gate.passed)"
Write-Host "Artifacts:"
Write-Host " - $resolvedMarkdownPath"
Write-Host " - $resolvedJsonPath"

if ($gateEnabled -and -not $gatePassed) {
  throw "Stage C gate failed. failedChecks=$($failedChecks -join ',')"
}
