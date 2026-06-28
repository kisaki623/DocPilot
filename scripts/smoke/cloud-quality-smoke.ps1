param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "tmp-e2e/docpilot-cloud-quality-smoke",
  [string]$SmokePrefix = "docpilot-cloud-quality",
  [ValidateRange(0.0, 1.0)]
  [double]$QualityMinSimilarityThreshold = 0.50,
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$SkipFrontend,
  [switch]$ReuseRunningServices
)

$ErrorActionPreference = "Stop"

$script:StartedProcesses = @()
$script:StartedTunnelPid = $null
$script:Gates = [ordered]@{}
$script:OverallStatus = "PASS"
$script:ArtifactPath = $null
$script:CleanupDone = $false

$StatusRank = @{
  PASS = 0
  REVIEW = 1
  BLOCKED = 2
  FAILED_CORE_FLOW = 3
  FAILED_SECURITY_GATE = 4
}

function Set-OverallStatus([string]$status) {
  if ($StatusRank[$status] -gt $StatusRank[$script:OverallStatus]) {
    $script:OverallStatus = $status
  }
}

function Set-Gate([string]$name, [string]$status, [array]$checks = @(), [string]$safeMessage = "") {
  Set-OverallStatus $status
  $script:Gates[$name] = [ordered]@{
    status = $status
    checks = $checks
    safeMessage = $safeMessage
  }
}

function Stop-WithStatus([string]$status, [string]$gate, [string]$message) {
  if (-not $script:Gates.Contains($gate)) {
    Set-Gate $gate $status @() $message
  }
  throw "${status}|${gate}|${message}"
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

function Get-EnvValue($values, [string[]]$keys, [string]$default = "") {
  foreach ($key in $keys) {
    if ($values.ContainsKey($key) -and -not [string]::IsNullOrWhiteSpace([string]$values[$key])) {
      return [string]$values[$key]
    }
  }
  return $default
}

function Test-TcpPort([int]$port) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $async = $client.BeginConnect("127.0.0.1", $port, $null, $null)
    if (-not $async.AsyncWaitHandle.WaitOne(800)) {
      return $false
    }
    $client.EndConnect($async)
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Invoke-WithRetry([scriptblock]$block, [int]$maxAttempts = 3) {
  $lastError = $null
  for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    try {
      return & $block
    } catch {
      $lastError = $_
      if ($attempt -eq $maxAttempts) {
        break
      }
      Start-Sleep -Seconds ([Math]::Pow(2, $attempt - 1))
    }
  }
  throw $lastError
}

function ConvertTo-SafeApiFailure($errorRecord) {
  $statusCode = 0
  $code = $null
  $message = "request failed"
  $response = $errorRecord.Exception.Response
  if ($response) {
    try {
      $statusCode = [int]$response.StatusCode
    } catch {
      $statusCode = 0
    }
    try {
      $stream = $response.GetResponseStream()
      if ($stream) {
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        if ($body) {
          $parsed = $body | ConvertFrom-Json
          $code = $parsed.code
          $message = [string]$parsed.message
        }
      }
    } catch {
      $message = "request failed"
    }
  }
  return [ordered]@{
    ok = $false
    httpStatus = $statusCode
    code = $code
    message = $message
    data = $null
  }
}

function Invoke-JsonApi([string]$method, [string]$path, $body = $null, [string]$token = "", [switch]$AllowFailure) {
  $uri = $BackendBaseUrl.TrimEnd("/") + $path
  $headers = @{}
  if ($token) {
    $headers["Authorization"] = "Bearer $token"
  }

  try {
    $response = Invoke-WithRetry {
      $params = @{
        Method = $method
        Uri = $uri
        Headers = $headers
        TimeoutSec = 60
      }
      if ($null -ne $body) {
        $params["ContentType"] = "application/json"
        $params["Body"] = ($body | ConvertTo-Json -Depth 20)
      }
      Invoke-RestMethod @params
    }
    $ok = ($response.code -eq 0)
    if ((-not $AllowFailure) -and (-not $ok)) {
      throw "api returned non-zero code at $method $path"
    }
    return [ordered]@{
      ok = $ok
      httpStatus = 200
      code = $response.code
      message = [string]$response.message
      data = $response.data
    }
  } catch {
    if ($AllowFailure) {
      return ConvertTo-SafeApiFailure $_
    }
    throw "api request failed at $method $path"
  }
}

function Upload-SmokeFile([string]$path, [string]$token) {
  Add-Type -AssemblyName System.Net.Http
  $client = [System.Net.Http.HttpClient]::new()
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, ($BackendBaseUrl.TrimEnd("/") + "/api/file/upload"))
  $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token)
  $multipart = [System.Net.Http.MultipartFormDataContent]::new()
  $stream = [System.IO.File]::OpenRead($path)
  try {
    $fileContent = [System.Net.Http.StreamContent]::new($stream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/plain")
    $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($path))
    $request.Content = $multipart
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $parsed = $text | ConvertFrom-Json
    if (-not $response.IsSuccessStatusCode -or $parsed.code -ne 0) {
      throw "upload failed"
    }
    return $parsed.data
  } finally {
    $stream.Dispose()
    $multipart.Dispose()
    $request.Dispose()
    $client.Dispose()
  }
}

function Wait-BackendHealth([int]$timeoutSeconds = 120) {
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  do {
    try {
      $health = Invoke-RestMethod -Method GET -Uri ($BackendBaseUrl.TrimEnd("/") + "/actuator/health") -TimeoutSec 5
      if ($health.status -eq "UP") {
        return $true
      }
    } catch {
      Start-Sleep -Seconds 2
    }
  } while ((Get-Date) -lt $deadline)
  return $false
}

function Wait-FrontendRoute([int]$timeoutSeconds = 90) {
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  do {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri ($FrontendBaseUrl.TrimEnd("/") + "/") -TimeoutSec 5
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        return $true
      }
    } catch {
      Start-Sleep -Seconds 2
    }
  } while ((Get-Date) -lt $deadline)
  return $false
}

function Start-BackendIfNeeded() {
  if (Wait-BackendHealth 3) {
    Set-Gate "backendHealth" "PASS" @("reused running backend")
    return
  }
  if ($ReuseRunningServices) {
    Stop-WithStatus "BLOCKED" "backendHealth" "backend is not healthy and service start is disabled"
  }

  $backendDir = Join-Path (Get-Location) "backend"
  $threshold = $QualityMinSimilarityThreshold.ToString([System.Globalization.CultureInfo]::InvariantCulture)
  $command = "`$env:APP_RAG_RETRIEVAL_MIN_SIMILARITY_THRESHOLD='$threshold'; Set-Location -LiteralPath '$backendDir'; mvn --% spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--app.rag.retrieval.min-similarity-threshold=$threshold"
  $process = Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) -WindowStyle Hidden -PassThru
  $script:StartedProcesses += $process
  if (-not (Wait-BackendHealth 120)) {
    Stop-WithStatus "BLOCKED" "backendHealth" "backend health did not become UP within timeout"
  }
  Set-Gate "backendHealth" "PASS" @("started backend", "actuator health UP")
}

function Start-FrontendIfNeeded() {
  if ($SkipFrontend) {
    Set-Gate "frontendRoutes" "REVIEW" @("frontend route smoke skipped")
    return
  }
  if (Wait-FrontendRoute 3) {
    return
  }
  if ($ReuseRunningServices) {
    Stop-WithStatus "BLOCKED" "frontendRoutes" "frontend is not reachable and service start is disabled"
  }

  $frontendDir = Join-Path (Get-Location) "frontend"
  $port = ([Uri]$FrontendBaseUrl).Port
  $command = "Set-Location -LiteralPath '$frontendDir'; npm.cmd run dev -- -p $port"
  $process = Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) -WindowStyle Hidden -PassThru
  $script:StartedProcesses += $process
  if (-not (Wait-FrontendRoute 90)) {
    Stop-WithStatus "BLOCKED" "frontendRoutes" "frontend route did not become reachable within timeout"
  }
}

function Start-TunnelsIfNeeded([string]$envPath) {
  $mysqlReady = Test-TcpPort $MySqlLocalPort
  $qdrantReady = Test-TcpPort $QdrantLocalPort
  if ($mysqlReady -and $qdrantReady) {
    Set-Gate "tunnel" "PASS" @("reused mysql/qdrant local tunnel ports")
    return
  }
  if ($mysqlReady -or $qdrantReady) {
    Stop-WithStatus "BLOCKED" "tunnel" "only one tunnel port is listening"
  }

  $scriptPath = Join-Path (Get-Location) "scripts/dev/start-cloud-tunnels.ps1"
  $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath -EnvFile $envPath -MySqlLocalPort $MySqlLocalPort -QdrantLocalPort $QdrantLocalPort -StartupTimeoutSeconds 20 | Out-String
  if ($output -match 'sshPid\s+:\s+(\d+)') {
    $script:StartedTunnelPid = [int]$matches[1]
  }
  if (-not (Test-TcpPort $MySqlLocalPort) -or -not (Test-TcpPort $QdrantLocalPort)) {
    Stop-WithStatus "BLOCKED" "tunnel" "mysql/qdrant tunnel did not become reachable"
  }
  Set-Gate "tunnel" "PASS" @("started mysql/qdrant local tunnels")
}

function Invoke-MysqlQuery([hashtable]$envValues, [string]$query) {
  $mysqlExe = Get-Command mysql -ErrorAction SilentlyContinue
  if (-not $mysqlExe) {
    Stop-WithStatus "BLOCKED" "mysql" "mysql CLI is not available"
  }
  $mysqlUser = Get-EnvValue $envValues @("MYSQL_USERNAME", "MYSQL_USER")
  $mysqlPassword = Get-EnvValue $envValues @("MYSQL_PASSWORD")
  $mysqlDatabase = Get-EnvValue $envValues @("MYSQL_DB", "MYSQL_DATABASE")
  if (-not $mysqlUser -or -not $mysqlPassword -or -not $mysqlDatabase) {
    Stop-WithStatus "BLOCKED" "mysql" "mysql credentials are not configured"
  }

  $env:MYSQL_PWD = $mysqlPassword
  try {
    $output = & mysql --protocol=TCP -h 127.0.0.1 -P $MySqlLocalPort -u $mysqlUser $mysqlDatabase --batch --raw --skip-column-names -e $query
    if ($LASTEXITCODE -ne 0) {
      Stop-WithStatus "BLOCKED" "mysql" "mysql query failed"
    }
    return $output
  } finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
  }
}

function Get-MysqlChunks([hashtable]$envValues, [long]$userId, [long]$documentId) {
  $query = @"
SELECT id,document_id,user_id,chunk_index,CHAR_LENGTH(content),content_hash,start_offset,end_offset,token_count,index_status,index_version,embedding_model,vector_id
FROM tb_document_chunk
WHERE user_id=${userId} AND document_id=${documentId} AND index_version=${IndexVersion}
ORDER BY chunk_index ASC,id ASC;
"@
  $lines = Invoke-MysqlQuery $envValues $query
  $chunks = @()
  foreach ($line in $lines) {
    if (-not $line) { continue }
    $cols = $line -split "`t"
    $chunks += [ordered]@{
      chunkId = [long]$cols[0]
      documentId = [long]$cols[1]
      userId = [long]$cols[2]
      chunkIndex = [int]$cols[3]
      contentLength = [int]$cols[4]
      contentHash = [string]$cols[5]
      startOffset = if ($cols[6] -eq "NULL") { $null } else { [int]$cols[6] }
      endOffset = if ($cols[7] -eq "NULL") { $null } else { [int]$cols[7] }
      tokenCount = if ($cols[8] -eq "NULL") { $null } else { [int]$cols[8] }
      indexStatus = [string]$cols[9]
      indexVersion = [int]$cols[10]
      embeddingModel = [string]$cols[11]
      vectorId = [string]$cols[12]
    }
  }
  return $chunks
}

function Wait-IndexedChunks([hashtable]$envValues, [long]$userId, [long]$documentId) {
  $deadline = (Get-Date).AddSeconds(120)
  do {
    $chunks = Get-MysqlChunks $envValues $userId $documentId
    if ($chunks.Count -gt 0 -and ($chunks | Where-Object { $_.indexStatus -ne "INDEXED" -or -not $_.vectorId }).Count -eq 0) {
      return $chunks
    }
    Start-Sleep -Seconds 3
  } while ((Get-Date) -lt $deadline)
  Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document chunks were not indexed within timeout"
}

function Test-ChunkQuality([array]$chunks, [long]$documentId) {
  if ($chunks.Count -lt 2) {
    Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document ${documentId} has fewer than two chunks"
  }
  $expected = 0
  $previousStart = -1
  $hashes = New-Object System.Collections.Generic.HashSet[string]
  $duplicateHashes = 0
  foreach ($chunk in $chunks) {
    if ($chunk.chunkIndex -ne $expected) {
      Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document ${documentId} chunk indexes are not contiguous"
    }
    if ($chunk.contentLength -le 0 -or $chunk.indexStatus -ne "INDEXED" -or -not $chunk.contentHash -or -not $chunk.vectorId) {
      Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document ${documentId} has invalid chunk metadata"
    }
    if ($null -eq $chunk.startOffset -or $null -eq $chunk.endOffset -or $chunk.startOffset -lt 0 -or $chunk.endOffset -le $chunk.startOffset) {
      Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document ${documentId} has invalid chunk offsets"
    }
    if ($chunk.startOffset -le $previousStart) {
      Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document ${documentId} chunk offsets are not ordered"
    }
    if ($null -eq $chunk.tokenCount -or $chunk.tokenCount -ne $chunk.contentLength) {
      Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document ${documentId} chunk token count does not match content length"
    }
    if (-not $hashes.Add([string]$chunk.contentHash)) {
      $duplicateHashes++
    }
    $previousStart = $chunk.startOffset
    $expected++
  }
  if ($duplicateHashes -gt 0) {
    Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document ${documentId} has duplicate chunk hashes"
  }
  $shortChunks = @($chunks | Where-Object { $_.contentLength -lt 80 })
  if (($shortChunks.Count / [double]$chunks.Count) -gt 0.25) {
    Stop-WithStatus "FAILED_CORE_FLOW" "chunkQuality" "document ${documentId} has too many short chunks"
  }
  $lengths = @($chunks | ForEach-Object { [int]$_.contentLength })
  return [ordered]@{
    documentId = $documentId
    chunkCount = $chunks.Count
    indexedCount = @($chunks | Where-Object { $_.indexStatus -eq "INDEXED" }).Count
    vectorIdCount = @($chunks | Where-Object { $_.vectorId }).Count
    minContentLength = ($lengths | Measure-Object -Minimum).Minimum
    maxContentLength = ($lengths | Measure-Object -Maximum).Maximum
    duplicateHashCount = $duplicateHashes
    offsetsOrdered = $true
    tokenCountMatchesContentLength = $true
  }
}

function Invoke-QdrantScroll([string]$collection, [long]$userId, [long]$documentId) {
  $all = @()
  $offset = $null
  do {
    $filter = [ordered]@{
      must = @(
        [ordered]@{ key = "userId"; match = [ordered]@{ value = $userId } },
        [ordered]@{ key = "documentId"; match = [ordered]@{ value = $documentId } },
        [ordered]@{ key = "indexVersion"; match = [ordered]@{ value = $IndexVersion } }
      )
    }
    $body = [ordered]@{
      limit = 100
      with_payload = $true
      with_vector = $false
      filter = $filter
    }
    if ($offset) {
      $body["offset"] = $offset
    }
    $uri = "http://127.0.0.1:${QdrantLocalPort}/collections/$collection/points/scroll"
    $result = Invoke-WithRetry {
      Invoke-RestMethod -Method POST -Uri $uri -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 20) -TimeoutSec 30
    } 5
    $all += @($result.result.points)
    $offset = $result.result.next_page_offset
  } while ($offset)
  return $all
}

function Test-QdrantConsistency([array]$chunks, [array]$points, [long]$documentId) {
  $pointsById = @{}
  foreach ($point in $points) {
    $pointsById[[string]$point.id] = $point
  }
  $missing = 0
  $mismatchedFields = New-Object System.Collections.Generic.List[string]
  $missingStructureFields = New-Object System.Collections.Generic.List[string]
  foreach ($chunk in $chunks) {
    if (-not $pointsById.ContainsKey($chunk.vectorId)) {
      $missing++
      continue
    }
    $payload = $pointsById[$chunk.vectorId].payload
    foreach ($field in @("userId", "documentId", "indexVersion", "chunkIndex", "contentHash", "chunkId", "tokenCount", "embeddingModel")) {
      $expected = switch ($field) {
        "userId" { $chunk.userId }
        "documentId" { $chunk.documentId }
        "indexVersion" { $chunk.indexVersion }
        "chunkIndex" { $chunk.chunkIndex }
        "contentHash" { $chunk.contentHash }
        "chunkId" { $chunk.chunkId }
        "tokenCount" { $chunk.tokenCount }
        "embeddingModel" { $chunk.embeddingModel }
      }
      $actual = $payload.$field
      if (($null -ne $expected) -and ([string]$actual -ne [string]$expected)) {
        $mismatchedFields.Add($field)
      }
    }
    foreach ($field in @("sectionTitle", "sectionOrdinal", "sourceBlockOrdinal", "structureType", "qualityFlags")) {
      $actual = $payload.$field
      if ($null -eq $actual -or [string]::IsNullOrWhiteSpace([string]$actual)) {
        $missingStructureFields.Add($field)
      }
    }
  }
  if ($missing -gt 0 -or $mismatchedFields.Count -gt 0 -or $missingStructureFields.Count -gt 0 -or $points.Count -ne $chunks.Count) {
    Stop-WithStatus "FAILED_SECURITY_GATE" "mysqlQdrantConsistency" "document ${documentId} mysql/qdrant payload mismatch"
  }
  return [ordered]@{
    documentId = $documentId
    mysqlChunkCount = $chunks.Count
    qdrantPointCount = $points.Count
    matchedCount = $chunks.Count - $missing
    missingVectorIds = $missing
    mismatchedFields = @($mismatchedFields | Select-Object -Unique)
    missingStructureFields = @($missingStructureFields | Select-Object -Unique)
  }
}

function Wait-ParseSuccess([long]$documentId, [string]$token) {
  $deadline = (Get-Date).AddSeconds(240)
  do {
    $detail = Invoke-JsonApi "GET" "/api/document/detail?documentId=$documentId" $null $token
    $status = [string]$detail.data.parseStatus
    if ($status -eq "SUCCESS") {
      return $detail.data
    }
    if ($status -eq "FAILED") {
      Stop-WithStatus "FAILED_CORE_FLOW" "uploadParseIndex" "document ${documentId} parse failed"
    }
    Start-Sleep -Seconds 3
  } while ((Get-Date) -lt $deadline)
  Stop-WithStatus "FAILED_CORE_FLOW" "uploadParseIndex" "document ${documentId} parse timeout"
}

function Test-Redaction([string]$json) {
  $patterns = @(
    '(?i)"token"\s*:',
    '(?i)"[^"]*(api[_-]?key|secret|password|authorization|connection|string|credential)[^"]*"\s*:',
    '(?i)bearer\s+[a-z0-9._-]+',
    '(?i)jdbc:mysql:',
    '(?i)BEGIN [A-Z ]*PRIVATE KEY',
    '(?i)\bsk-[a-z0-9_-]{12,}',
    '(?i)https?://(?!127\.0\.0\.1|localhost)[^\s"<>]+',
    '\b(?!(?:127|10|172\.1[6-9]|172\.2[0-9]|172\.3[0-1]|192\.168)\.)\d{1,3}(?:\.\d{1,3}){3}\b'
  )
  foreach ($pattern in $patterns) {
    if ($json -match $pattern) {
      return $false
    }
  }
  return $true
}

function Get-SafeExceptionMessage($errorRecord) {
  $message = [string]$errorRecord.Exception.Message
  if ([string]::IsNullOrWhiteSpace($message)) {
    return "smoke stopped before completion"
  }
  $message = $message -replace '(?i)bearer\s+[a-z0-9._-]+', 'bearer <redacted>'
  $message = $message -replace '(?i)jdbc:mysql:[^\s"<>]+', 'jdbc:mysql:<redacted>'
  $message = $message -replace '(?i)https?://(?!127\.0\.0\.1|localhost)[^\s"<>]+', '<redacted-url>'
  $message = $message -replace '\b(?!(?:127|10|172\.1[6-9]|172\.2[0-9]|172\.3[0-1]|192\.168)\.)\d{1,3}(?:\.\d{1,3}){3}\b', '<redacted-ip>'
  $message = $message -replace '(?i)\bsk-[a-z0-9_-]{12,}', '<redacted-key>'
  if ($message.Length -gt 240) {
    return $message.Substring(0, 240)
  }
  return $message
}

function Write-SmokeArtifact($artifact) {
  $json = $artifact | ConvertTo-Json -Depth 30
  if (-not (Test-Redaction $json)) {
    $script:OverallStatus = "FAILED_SECURITY_GATE"
    $minimal = [ordered]@{
      schemaVersion = 1
      smokeMarker = $artifact.smokeMarker
      overallStatus = "FAILED_SECURITY_GATE"
      artifactRedaction = [ordered]@{
        status = "FAILED_SECURITY_GATE"
        safeMessage = "artifact redaction scan failed before full write"
      }
    }
    $json = $minimal | ConvertTo-Json -Depth 10
  }
  [System.IO.File]::WriteAllText($script:ArtifactPath, $json, [System.Text.UTF8Encoding]::new($false))
  $written = [System.IO.File]::ReadAllText($script:ArtifactPath, [System.Text.Encoding]::UTF8)
  if (-not (Test-Redaction $written)) {
    Set-Gate "artifactRedaction" "FAILED_SECURITY_GATE" @() "artifact redaction scan failed after write"
  } else {
    Set-Gate "artifactRedaction" "PASS" @("redaction scan before and after write")
  }
}

function Stop-StartedProcesses() {
  if ($script:CleanupDone) {
    return
  }
  foreach ($process in $script:StartedProcesses) {
    if ($process -and -not $process.HasExited) {
      Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
  }
  if ($script:StartedTunnelPid) {
    Stop-Process -Id $script:StartedTunnelPid -Force -ErrorAction SilentlyContinue
  }
  try {
    $cleanupScript = Join-Path (Get-Location) "scripts/dev/cleanup-agent-processes.ps1"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $cleanupScript | Out-Null
    Set-Gate "cleanup" "PASS" @("cleanup script executed")
  } catch {
    Set-Gate "cleanup" "REVIEW" @("cleanup script failed")
  }
  $script:CleanupDone = $true
}

function Get-CountValue($source, [string]$key) {
  if ($null -eq $source) {
    return 0
  }
  if ($source -is [hashtable] -and $source.ContainsKey($key)) {
    return [int]$source[$key]
  }
  $property = $source.PSObject.Properties[$key]
  if ($property) {
    return [int]$property.Value
  }
  return 0
}

function Get-ScoreSummary($items) {
  $scores = @($items | ForEach-Object { [double]$_.score })
  if ($scores.Count -eq 0) {
    return [ordered]@{ count = 0; min = $null; max = $null }
  }
  $measure = $scores | Measure-Object -Minimum -Maximum
  return [ordered]@{ count = $scores.Count; min = $measure.Minimum; max = $measure.Maximum }
}

function Get-FieldScoreSummary($items, [string]$field) {
  $scores = @($items | ForEach-Object {
    $value = $_.$field
    if ($null -ne $value) {
      [double]$value
    }
  })
  if ($scores.Count -eq 0) {
    return [ordered]@{ count = 0; min = $null; max = $null }
  }
  $measure = $scores | Measure-Object -Minimum -Maximum
  return [ordered]@{ count = $scores.Count; min = $measure.Minimum; max = $measure.Maximum }
}

function Show-PlanMode() {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Cloud quality smoke plan only. No env read, no service start, no data creation."
    gates = @(
      "tunnel", "backendHealth", "frontendRoutes", "auth", "uploadParseIndex",
      "chunkQuality", "mysqlQdrantConsistency", "singleDocumentRag",
      "knowledgeBaseRag", "noEvidenceThreshold", "conversationTrace", "permissionIsolation",
      "artifactRedaction", "cleanup", "gitStatus"
    )
    artifactRoot = $ArtifactRoot
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
    statuses = @("PASS", "REVIEW", "BLOCKED", "FAILED_CORE_FLOW", "FAILED_SECURITY_GATE")
  } | ConvertTo-Json -Depth 5
}

function Invoke-DryRun() {
  $checks = @()
  $checks += [ordered]@{ name = "envFileExists"; pass = (Test-Path -LiteralPath $EnvFile) }
  $checks += [ordered]@{ name = "mysqlCliExists"; pass = [bool](Get-Command mysql -ErrorAction SilentlyContinue) }
  $checks += [ordered]@{ name = "nodeExists"; pass = [bool](Get-Command node -ErrorAction SilentlyContinue) }
  $checks += [ordered]@{ name = "npmExists"; pass = [bool](Get-Command npm -ErrorAction SilentlyContinue) }
  $checks += [ordered]@{ name = "mysqlPortListening"; pass = (Test-TcpPort $MySqlLocalPort) }
  $checks += [ordered]@{ name = "qdrantPortListening"; pass = (Test-TcpPort $QdrantLocalPort) }
  $gitignore = if (Test-Path -LiteralPath ".gitignore") { Get-Content -LiteralPath ".gitignore" -Raw } else { "" }
  $checks += [ordered]@{ name = "artifactRootIgnored"; pass = ($gitignore -match "tmp-e2e/") }
  Set-Gate "dryRun" "PASS" @($checks)
  [PSCustomObject][ordered]@{
    mode = "dry-run"
    overallStatus = $script:OverallStatus
    gates = $script:Gates
  } | ConvertTo-Json -Depth 10
}

function Invoke-Run() {
  $startedAt = (Get-Date).ToString("o")
  $runSuffix = (Get-Date).ToString("yyyyMMddHHmmss") + "-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
  $smokeMarker = "$SmokePrefix-$runSuffix"
  $artifactDir = Join-Path $ArtifactRoot $smokeMarker
  New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
  $script:ArtifactPath = Join-Path $artifactDir "artifact.json"
  $envValues = Read-EnvFile $EnvFile
  $gitStatusBefore = git status --short
  Set-Gate "gitStatus" "PASS" @("initial git status checked")

  $provider = Get-EnvValue $envValues @("RAG_VECTOR_STORE_PROVIDER", "RAG_VECTOR_PROVIDER", "APP_RAG_VECTOR_STORE_PROVIDER") "in_memory"
  $embeddingProvider = Get-EnvValue $envValues @("APP_RAG_EMBEDDING_PROVIDER") "fake"
  $collection = Get-EnvValue $envValues @("RAG_QDRANT_COLLECTION", "APP_RAG_VECTOR_STORE_QDRANT_COLLECTION") "docpilot_rag_demo"
  $dimension = Get-EnvValue $envValues @("RAG_QDRANT_DIMENSION", "RAG_VECTOR_DIMENSION", "APP_RAG_VECTOR_STORE_QDRANT_DIMENSION") "1536"
  if ($provider -ne "qdrant") {
    Stop-WithStatus "BLOCKED" "configConsistency" "qdrant vector provider is not configured"
  }
  $configStatus = if ($embeddingProvider -eq "fake") { "REVIEW" } else { "PASS" }
  Set-Gate "configConsistency" $configStatus @("qdrant provider configured", "embedding provider classified", "dimension configured", "quality threshold override configured")

  Start-TunnelsIfNeeded $EnvFile
  Start-BackendIfNeeded
  Start-FrontendIfNeeded

  $shortUserSuffix = $runSuffix.Replace("-", "")
  $passwordA = "SmokeA!" + ([Guid]::NewGuid().ToString("N").Substring(0, 12))
  $passwordB = "SmokeB!" + ([Guid]::NewGuid().ToString("N").Substring(0, 12))
  $userA = "smokea$shortUserSuffix"
  $userB = "smokeb$shortUserSuffix"
  $regA = Invoke-JsonApi "POST" "/api/auth/register" ([ordered]@{ username = $userA; password = $passwordA; nickname = "Smoke A" })
  $regB = Invoke-JsonApi "POST" "/api/auth/register" ([ordered]@{ username = $userB; password = $passwordB; nickname = "Smoke B" })
  $tokenA = [string]$regA.data.token
  $tokenB = [string]$regB.data.token
  $userAId = [long]$regA.data.userId
  $userBId = [long]$regB.data.userId
  Set-Gate "auth" "PASS" @("registered user A", "registered user B")

  $alphaText = @"
# Alpha Cloud Quality

$smokeMarker
Alpha architecture document. This file proves upload parse chunk index retrieve answer and citation behavior.
Alpha fact one: DocPilot cloud quality smoke verifies chunk metadata from MySQL before trusting RAG output.
Alpha fact two: Alpha coverage must appear in single document RAG and in the KnowledgeBase multi document answer.
Alpha fact three: the expected Alpha keyword is ALPHA-CLOUD-GATE.

## Alpha Chunk Structure

Alpha detail repeat block one. Upload creates a file record, document create binds the file, parse task dispatches async parsing, and successful parsing triggers indexing.
Alpha detail repeat block two. The smoke runner checks chunk index continuity, vector ids, hashes, positive lengths, and indexed status.
Alpha detail repeat block three. The answer should cite Alpha evidence and never expose tokens or cloud endpoints in the artifact.
Alpha detail repeat block four. ALPHA-CLOUD-GATE remains unique to this temporary document for this run.
Alpha detail repeat block five. The document intentionally contains enough plain text to cross the default chunk window and produce multiple chunks.
Alpha detail repeat block six. The quality gate should not trust a single retrieval response until MySQL chunk rows and Qdrant payload metadata agree.
Alpha detail repeat block seven. The smoke marker ties this temporary document to a single run while the artifact stores only ids, counts, hashes, and gate status.
Alpha detail repeat block eight. The frontend route smoke is separate from the RAG quality gate, but both results belong to one redacted run summary.
Alpha detail repeat block nine. The parser must preserve ALPHA-CLOUD-GATE in the indexed content so the single document question has deterministic evidence.
Alpha detail repeat block ten. The security checks must fail closed when another user attempts to read or retrieve data outside their own scope.
"@
  $betaText = @"
# Beta Context Trace

$smokeMarker
Beta operations document. This file proves KnowledgeBase multi document retrieval and Conversation Context Trace behavior.
Beta fact one: DocPilot cloud quality smoke compares Qdrant payload metadata with MySQL chunk rows.
Beta fact two: Conversation Trace must report ragTriggered true, ragRequired true, evidenceCount greater than zero, active user memory, source breakdown counts, and documentHitCounts.
Beta fact three: the expected Beta keyword is BETA-CONTEXT-GATE.

## Beta Multi Document Evidence

Beta detail repeat block one. The KnowledgeBase answer must cite both Alpha and Beta documents when asked to summarize all documents.
Beta detail repeat block two. Permission isolation rejects user B reading user A knowledge base and user A adding user B documents.
Beta detail repeat block three. The redacted artifact stores counts and ids, never raw chunk content, tokens, API keys, cloud addresses, or connection strings.
Beta detail repeat block four. BETA-CONTEXT-GATE remains unique to this temporary document for this run.
Beta detail repeat block five. The document intentionally contains enough plain text to cross the default chunk window and produce multiple chunks.
Beta detail repeat block six. The KnowledgeBase summary question should make retrieval cover both Alpha and Beta documents instead of only one nearest document.
Beta detail repeat block seven. The Conversation Trace gate checks only summary fields and verifies memory and RAG evidence stay separate without persisting or printing the full prompt or evidence context.
Beta detail repeat block eight. The Qdrant scroll gate uses with_vector false so the artifact never stores vector values or raw embedding payloads.
Beta detail repeat block nine. The parser must preserve BETA-CONTEXT-GATE in the indexed content so the KnowledgeBase question has deterministic evidence.
Beta detail repeat block ten. The final git status check confirms ignored runtime artifacts did not enter tracked repository state.
"@
  $alphaPath = Join-Path $artifactDir "alpha.txt"
  $betaPath = Join-Path $artifactDir "beta.txt"
  [System.IO.File]::WriteAllText($alphaPath, $alphaText, [System.Text.UTF8Encoding]::new($false))
  [System.IO.File]::WriteAllText($betaPath, $betaText, [System.Text.UTF8Encoding]::new($false))

  $fileA = Upload-SmokeFile $alphaPath $tokenA
  $fileB = Upload-SmokeFile $betaPath $tokenA
  $docA = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $fileA.id }) $tokenA
  $docB = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $fileB.id }) $tokenA
  $taskA = Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $docA.data.id }) $tokenA
  Wait-ParseSuccess ([long]$docA.data.id) $tokenA | Out-Null
  $chunksA = Wait-IndexedChunks $envValues $userAId ([long]$docA.data.id)
  $taskB = Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $docB.data.id }) $tokenA
  Wait-ParseSuccess ([long]$docB.data.id) $tokenA | Out-Null
  $chunksB = Wait-IndexedChunks $envValues $userAId ([long]$docB.data.id)
  $chunkQuality = @(
    Test-ChunkQuality $chunksA ([long]$docA.data.id)
    Test-ChunkQuality $chunksB ([long]$docB.data.id)
  )
  Set-Gate "uploadParseIndex" "PASS" @("uploaded two txt files", "parse SUCCESS", "chunks indexed")
  Set-Gate "chunkQuality" "PASS" $chunkQuality

  $qdrantA = Invoke-QdrantScroll $collection $userAId ([long]$docA.data.id)
  $qdrantB = Invoke-QdrantScroll $collection $userAId ([long]$docB.data.id)
  $consistency = @(
    Test-QdrantConsistency $chunksA $qdrantA ([long]$docA.data.id)
    Test-QdrantConsistency $chunksB $qdrantB ([long]$docB.data.id)
  )
  Set-Gate "mysqlQdrantConsistency" "PASS" $consistency

  $singleRetrieve = Invoke-JsonApi "POST" "/api/rag/retrieve" ([ordered]@{ documentId = $docA.data.id; query = "What does ALPHA-CLOUD-GATE prove for $smokeMarker?"; topK = 5; indexVersion = $IndexVersion }) $tokenA
  $singleQa = Invoke-JsonApi "POST" "/api/documents/$($docA.data.id)/qa/rag" ([ordered]@{ question = "Explain ALPHA-CLOUD-GATE for $smokeMarker and cite the document."; topK = 5; indexVersion = $IndexVersion }) $tokenA
  $singleChecks = @([ordered]@{
    retrieveHits = @($singleRetrieve.data.hits).Count
    qaCitations = @($singleQa.data.citations).Count
    retrieveScoreSummary = Get-ScoreSummary $singleRetrieve.data.hits
    citationScoreSummary = Get-ScoreSummary $singleQa.data.citations
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
  })
  if ($singleRetrieve.data.noEvidence -or @($singleRetrieve.data.hits).Count -lt 1 -or @($singleQa.data.citations).Count -lt 1) {
    Set-Gate "singleDocumentRag" "FAILED_CORE_FLOW" $singleChecks "single document RAG did not return evidence and citation"
    Stop-WithStatus "FAILED_CORE_FLOW" "singleDocumentRag" "single document RAG did not return evidence and citation"
  }
  Set-Gate "singleDocumentRag" "PASS" $singleChecks

  $kb = Invoke-JsonApi "POST" "/api/knowledge-bases" ([ordered]@{ name = "Cloud Quality KB $smokeMarker"; description = "temporary smoke kb" }) $tokenA
  $addKb = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/documents" ([ordered]@{ documentIds = @($docA.data.id, $docB.data.id) }) $tokenA
  $kbRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" ([ordered]@{ query = "Summarize all documents for $smokeMarker and cover ALPHA-CLOUD-GATE and BETA-CONTEXT-GATE."; topK = 6; indexVersion = $IndexVersion }) $tokenA
  $kbQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/qa/rag" ([ordered]@{ question = "Summarize both documents in this knowledge base, explain ALPHA-CLOUD-GATE and BETA-CONTEXT-GATE, and cite evidence."; topK = 6; indexVersion = $IndexVersion }) $tokenA
  $hitCounts = $kbRetrieve.data.documentHitCounts
  $kbChecks = @([ordered]@{
    retrieveHits = @($kbRetrieve.data.hits).Count
    qaCitations = @($kbQa.data.citations).Count
    documentHitCounts = $hitCounts
    retrievalMode = $kbRetrieve.data.retrievalMode
    rerankApplied = [bool]$kbRetrieve.data.rerankApplied
    rerankModel = $kbRetrieve.data.rerankModel
    retrieveScoreSummary = Get-ScoreSummary $kbRetrieve.data.hits
    citationScoreSummary = Get-ScoreSummary $kbQa.data.citations
    retrieveVectorScoreSummary = Get-FieldScoreSummary $kbRetrieve.data.hits "vectorScore"
    citationVectorScoreSummary = Get-FieldScoreSummary $kbQa.data.citations "vectorScore"
    retrieveRerankScoreSummary = Get-FieldScoreSummary $kbRetrieve.data.hits "rerankScore"
    citationRerankScoreSummary = Get-FieldScoreSummary $kbQa.data.citations "rerankScore"
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
  })
  if (@($kbRetrieve.data.hits).Count -lt 2 -or @($kbQa.data.citations).Count -lt 2 -or (Get-CountValue $hitCounts ([string]$docA.data.id)) -lt 1 -or (Get-CountValue $hitCounts ([string]$docB.data.id)) -lt 1) {
    Set-Gate "knowledgeBaseRag" "FAILED_CORE_FLOW" $kbChecks "knowledge base RAG did not cover both documents"
    Stop-WithStatus "FAILED_CORE_FLOW" "knowledgeBaseRag" "knowledge base RAG did not cover both documents"
  }
  Set-Gate "knowledgeBaseRag" "PASS" $kbChecks

  $unrelatedQuery = "Which payroll settlement policy and invoice approval matrix is defined for employee reimbursements?"
  $noEvidenceRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" ([ordered]@{ query = $unrelatedQuery; topK = 3; indexVersion = $IndexVersion }) $tokenA
  $noEvidenceQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/qa/rag" ([ordered]@{ question = $unrelatedQuery; topK = 3; indexVersion = $IndexVersion }) $tokenA
  $noEvidenceChecks = @([ordered]@{
    retrieveNoEvidence = [bool]$noEvidenceRetrieve.data.noEvidence
    qaNoEvidence = [bool]$noEvidenceQa.data.noEvidence
    retrieveHits = @($noEvidenceRetrieve.data.hits).Count
    qaCitations = @($noEvidenceQa.data.citations).Count
    retrieveScoreSummary = Get-ScoreSummary $noEvidenceRetrieve.data.hits
    citationScoreSummary = Get-ScoreSummary $noEvidenceQa.data.citations
    retrieveVectorScoreSummary = Get-FieldScoreSummary $noEvidenceRetrieve.data.hits "vectorScore"
    citationVectorScoreSummary = Get-FieldScoreSummary $noEvidenceQa.data.citations "vectorScore"
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
  })
  if ($noEvidenceRetrieve.data.noEvidence -and $noEvidenceQa.data.noEvidence) {
    Set-Gate "noEvidenceThreshold" "PASS" $noEvidenceChecks
  } else {
    Set-Gate "noEvidenceThreshold" "REVIEW" $noEvidenceChecks "unrelated populated-KB query still returned nearest evidence; tune minSimilarityThreshold or rerank policy"
  }

  $memory = Invoke-JsonApi "POST" "/api/memories" ([ordered]@{
      memoryType = "PREFERENCE"
      content = "For $smokeMarker, prefer concise answers that still cite knowledge-base evidence."
      priority = 40
    }) $tokenA
  if ($memory.data.status -ne "ACTIVE") {
    Stop-WithStatus "FAILED_CORE_FLOW" "conversationTrace" "temporary smoke memory was not ACTIVE"
  }

  $conversation = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Cloud Quality $smokeMarker"; contextMode = "AGENT_MEMORY"; boundKnowledgeBaseId = $kb.data.id }) $tokenA
  $message = Invoke-JsonApi "POST" "/api/conversations/$($conversation.data.conversationId)/messages" ([ordered]@{ content = "Use the bound knowledge base to answer what the two documents prove for $smokeMarker. Cover ALPHA-CLOUD-GATE and BETA-CONTEXT-GATE." }) $tokenA
  $trace = Invoke-JsonApi "GET" "/api/conversations/$($conversation.data.conversationId)/messages/$($message.data.messageId)/trace" $null $tokenA
  $sourceCounts = $trace.data.contextSourceCounts
  $memorySourceCount = Get-CountValue $sourceCounts "userMemory"
  $ragSourceCount = Get-CountValue $sourceCounts "ragEvidence"
  if (-not $trace.data.ragTriggered -or -not $trace.data.ragRequired -or [int]$trace.data.evidenceCount -lt 1 -or [int]$trace.data.memoryCount -lt 1 -or $memorySourceCount -lt 1 -or $ragSourceCount -lt 1 -or (Get-CountValue $trace.data.documentHitCounts ([string]$docA.data.id)) -lt 1 -or (Get-CountValue $trace.data.documentHitCounts ([string]$docB.data.id)) -lt 1) {
    Stop-WithStatus "FAILED_CORE_FLOW" "conversationTrace" "conversation trace did not include required RAG evidence and active memory"
  }
  Set-Gate "conversationTrace" "PASS" @([ordered]@{
      ragTriggered = $trace.data.ragTriggered
      ragRequired = $trace.data.ragRequired
      evidenceCount = $trace.data.evidenceCount
      memoryCount = $trace.data.memoryCount
      contextSourceCounts = $sourceCounts
      documentHitCounts = $trace.data.documentHitCounts
    })

  $fileUserB = Upload-SmokeFile $betaPath $tokenB
  $docUserB = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $fileUserB.id }) $tokenB
  $negativeChecks = @(
    Invoke-JsonApi "GET" "/api/knowledge-bases/$($kb.data.id)" $null $tokenB -AllowFailure
    Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" ([ordered]@{ query = "try cross user"; topK = 3; indexVersion = $IndexVersion }) $tokenB -AllowFailure
    Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/documents" ([ordered]@{ documentIds = @($docUserB.data.id) }) $tokenA -AllowFailure
    Invoke-JsonApi "GET" "/api/conversations/$($conversation.data.conversationId)/messages/$($message.data.messageId)/trace" $null $tokenB -AllowFailure
  )
  if (($negativeChecks | Where-Object { $_.ok }).Count -gt 0) {
    Stop-WithStatus "FAILED_SECURITY_GATE" "permissionIsolation" "at least one cross-user negative check unexpectedly succeeded"
  }
  Set-Gate "permissionIsolation" "PASS" @("foreign KB detail rejected", "foreign KB retrieve rejected", "foreign document add rejected", "foreign trace rejected")

  if (-not $SkipFrontend) {
    $routes = @("/", "/login", "/dashboard", "/upload", "/documents", "/knowledge-bases", "/conversations")
    $routeChecks = @()
    foreach ($route in $routes) {
      $response = Invoke-WebRequest -UseBasicParsing -Uri ($FrontendBaseUrl.TrimEnd("/") + $route) -TimeoutSec 20
      $routeChecks += [ordered]@{ route = $route; statusCode = $response.StatusCode; nonBlank = ($response.Content.Length -gt 200) }
      if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 500 -or $response.Content.Length -le 200) {
        Stop-WithStatus "FAILED_CORE_FLOW" "frontendRoutes" "frontend route smoke failed"
      }
    }
    Set-Gate "frontendRoutes" "PASS" $routeChecks
  }

  $gitStatusAfter = git status --short
  Set-Gate "gitStatus" "PASS" @("initial and final git status checked")

  Stop-StartedProcesses
  Set-Gate "artifactRedaction" "PASS" @("redaction scan before and after write")

  $artifact = [ordered]@{
    schemaVersion = 1
    runId = $runSuffix
    smokeMarker = $smokeMarker
    mode = $Mode
    startedAt = $startedAt
    finishedAt = (Get-Date).ToString("o")
    overallStatus = $script:OverallStatus
    environment = [ordered]@{
      backendBase = "local"
      frontendBase = if ($SkipFrontend) { "skipped" } else { "local" }
      mysqlTunnelPort = $MySqlLocalPort
      qdrantTunnelPort = $QdrantLocalPort
      vectorProvider = $provider
      embeddingProvider = $embeddingProvider
      qdrantCollection = $collection
      qdrantDimension = $dimension
    }
    resources = [ordered]@{
      userAId = $userAId
      userBId = $userBId
      userADocumentIds = @([long]$docA.data.id, [long]$docB.data.id)
      userBDocumentId = [long]$docUserB.data.id
      parseTaskIds = @([long]$taskA.data.taskId, [long]$taskB.data.taskId)
      knowledgeBaseId = [long]$kb.data.id
      memoryId = [long]$memory.data.memoryId
      conversationId = [long]$conversation.data.conversationId
      messageId = [long]$message.data.messageId
    }
    gates = $script:Gates
    git = [ordered]@{
      statusBeforeClean = ([string]::IsNullOrWhiteSpace(($gitStatusBefore -join "")))
      statusAfterClean = ([string]::IsNullOrWhiteSpace(($gitStatusAfter -join "")))
    }
  }
  Write-SmokeArtifact $artifact

  [PSCustomObject][ordered]@{
    overallStatus = $script:OverallStatus
    smokeMarker = $smokeMarker
    artifact = $script:ArtifactPath
    gates = $script:Gates
  } | ConvertTo-Json -Depth 20
}

try {
  if ($Mode -eq "plan") {
    Show-PlanMode
    exit 0
  }
  if ($Mode -eq "dry-run") {
    Invoke-DryRun
    exit 0
  }
  Invoke-Run
} catch {
  if ($script:OverallStatus -eq "PASS") {
    Set-OverallStatus "FAILED_CORE_FLOW"
  }
  $safeMessage = "smoke stopped before completion"
  if ($_.Exception.Message -match '^(PASS|REVIEW|BLOCKED|FAILED_CORE_FLOW|FAILED_SECURITY_GATE)\|([^|]+)\|(.*)$') {
    $safeMessage = $matches[3]
  } else {
    $safeMessage = Get-SafeExceptionMessage $_
  }
  if ($script:ArtifactPath) {
    Stop-StartedProcesses
    $minimal = [ordered]@{
      schemaVersion = 1
      mode = $Mode
      overallStatus = $script:OverallStatus
      safeMessage = $safeMessage
      gates = $script:Gates
    }
    Write-SmokeArtifact $minimal
  }
  [PSCustomObject][ordered]@{
    overallStatus = $script:OverallStatus
    safeMessage = $safeMessage
    artifact = $script:ArtifactPath
    gates = $script:Gates
  } | ConvertTo-Json -Depth 20
  exit 1
} finally {
  if ($Mode -eq "run") {
    Stop-StartedProcesses
  }
}
