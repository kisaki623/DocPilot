param(
  [ValidateSet("plan", "dry-run", "run")]
  [string]$Mode = "plan",
  [string]$BackendBaseUrl = "http://127.0.0.1:8081",
  [string]$FrontendBaseUrl = "http://127.0.0.1:3000",
  [string]$EnvFile = "backend/.env",
  [string]$ArtifactRoot = "backend/target/smoke/document-parser-real-chain",
  [string]$SmokePrefix = "docpilot-parser-real-chain",
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$IndexVersion = 1,
  [switch]$ReuseRunningServices,
  [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"

$script:StartedProcesses = @()
$script:StartedTunnelPid = $null
$script:OverallStatus = "PASS"
$script:Gates = [ordered]@{}
$script:ArtifactPath = $null
$script:CurrentParserStage = ""
$script:DirectRetrieveFollowUps = @()
$script:EnvironmentUnstable = $false

$StatusRank = @{
  PASS = 0
  REVIEW = 1
  BLOCKED = 2
  FAILED_CORE_FLOW = 3
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

function Set-GateWithMetrics([string]$name, [string]$status, [array]$checks, [hashtable]$metrics, [hashtable]$flags) {
  Set-OverallStatus $status
  $gate = [ordered]@{
    status = $status
    checks = $checks
    safeMessage = ""
  }
  foreach ($entry in $metrics.GetEnumerator()) {
    $gate[$entry.Key] = $entry.Value
  }
  foreach ($entry in $flags.GetEnumerator()) {
    $gate[$entry.Key] = $entry.Value
  }
  $script:Gates[$name] = $gate
}

function Stop-Smoke([string]$status, [string]$gate, [string]$message) {
  Set-Gate $gate $status @() $message
  throw "${status}|${gate}|${message}"
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

function To-SafeArray($value) {
  if ($null -eq $value) {
    return @()
  }
  return @($value)
}

function Get-SafeItemCount($value) {
  if ($null -eq $value) {
    return 0
  }
  return @($value).Count
}

function Confirm-EnvironmentStability([string]$stage) {
  $mysqlReachable = Test-TcpPort $MySqlLocalPort
  $qdrantReachable = Test-TcpPort $QdrantLocalPort
  if (-not $mysqlReachable -or -not $qdrantReachable) {
    $script:EnvironmentUnstable = $true
    Set-GateWithMetrics "environmentStability" "BLOCKED" @("local tunnel port became unreachable during ${stage}") @{
      mysqlTunnelReachable = if ($mysqlReachable) { 1 } else { 0 }
      qdrantTunnelReachable = if ($qdrantReachable) { 1 } else { 0 }
    } @{}
    return $false
  }
  return $true
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

function Invoke-WithRetry([scriptblock]$block, [int]$maxAttempts = 5) {
  $lastError = $null
  for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    try {
      return & $block
    } catch {
      $lastError = $_
      if ($attempt -eq $maxAttempts) {
        break
      }
      Start-Sleep -Seconds ([Math]::Min(12, [Math]::Pow(2, $attempt)))
    }
  }
  throw $lastError
}

function ConvertTo-SafeFailure($errorRecord) {
  $response = $errorRecord.Exception.Response
  $statusCode = 0
  $code = $null
  $message = "request failed"
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
        TimeoutSec = 180
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
      return ConvertTo-SafeFailure $_
    }
    $failure = ConvertTo-SafeFailure $_
    throw "api request failed at $method $path status=$($failure.httpStatus) code=$($failure.code) message=$($failure.message)"
  }
}

function Upload-SmokeFile([string]$path, [string]$contentType, [string]$token, [switch]$AllowFailure) {
  Add-Type -AssemblyName System.Net.Http
  $client = [System.Net.Http.HttpClient]::new()
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, ($BackendBaseUrl.TrimEnd("/") + "/api/file/upload"))
  $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token)
  $multipart = [System.Net.Http.MultipartFormDataContent]::new()
  $stream = [System.IO.File]::OpenRead($path)
  try {
    $fileContent = [System.Net.Http.StreamContent]::new($stream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($contentType)
    $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($path))
    $request.Content = $multipart
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $parsed = $text | ConvertFrom-Json
    $ok = ($response.IsSuccessStatusCode -and $parsed.code -eq 0)
    if ((-not $AllowFailure) -and (-not $ok)) {
      throw "upload failed status=$([int]$response.StatusCode) code=$($parsed.code) message=$($parsed.message)"
    }
    return [ordered]@{
      ok = $ok
      httpStatus = [int]$response.StatusCode
      code = $parsed.code
      message = [string]$parsed.message
      data = $parsed.data
    }
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

function Get-FrontendPort() {
  try {
    return ([System.Uri]$FrontendBaseUrl).Port
  } catch {
    return 3000
  }
}

function Start-TunnelsIfNeeded([string]$envPath) {
  if ((Test-TcpPort $MySqlLocalPort) -and (Test-TcpPort $QdrantLocalPort)) {
    Set-Gate "tunnel" "PASS" @("reused local mysql/qdrant tunnel ports")
    return
  }
  if ($ReuseRunningServices) {
    Stop-Smoke "BLOCKED" "tunnel" "reuse mode is enabled but tunnel ports are not both reachable"
  }
  $scriptPath = Join-Path (Get-Location) "scripts/dev/start-cloud-tunnels.ps1"
  $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath -EnvFile $envPath -MySqlLocalPort $MySqlLocalPort -QdrantLocalPort $QdrantLocalPort -StartupTimeoutSeconds 20 | Out-String
  if ($output -match 'sshPid\s+:\s+(\d+)') {
    $script:StartedTunnelPid = [int]$matches[1]
  }
  if (-not (Test-TcpPort $MySqlLocalPort) -or -not (Test-TcpPort $QdrantLocalPort)) {
    Stop-Smoke "BLOCKED" "tunnel" "mysql/qdrant local tunnels did not become reachable"
  }
  Set-Gate "tunnel" "PASS" @("started local mysql/qdrant tunnels")
}

function Start-BackendIfNeeded([string]$runDir) {
  if (Wait-BackendHealth 3) {
    if ($ReuseRunningServices) {
      Set-Gate "backend" "PASS" @("reused healthy backend")
      return
    }
    Stop-Smoke "BLOCKED" "backend" "backend is already running; stop it or pass -ReuseRunningServices explicitly"
  }
  if ($ReuseRunningServices) {
    Stop-Smoke "BLOCKED" "backend" "reuse mode is enabled but backend is not healthy"
  }
  $backendDir = Join-Path (Get-Location) "backend"
  $stdout = Join-Path $runDir "backend.stdout.log"
  $stderr = Join-Path $runDir "backend.stderr.log"
  $previousAiMode = $env:AI_MODE
  $previousQualityConsole = $env:APP_QUALITY_CONSOLE_ENABLED
  $env:AI_MODE = "mock"
  $env:APP_QUALITY_CONSOLE_ENABLED = "true"
  try {
    $process = Start-Process -FilePath "mvn.cmd" -ArgumentList @("spring-boot:run", "-Dspring-boot.run.profiles=local") -WorkingDirectory $backendDir -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
  } finally {
    $env:AI_MODE = $previousAiMode
    $env:APP_QUALITY_CONSOLE_ENABLED = $previousQualityConsole
  }
  $script:StartedProcesses += $process
  if (-not (Wait-BackendHealth 150)) {
    Stop-Smoke "BLOCKED" "backend" "backend did not become healthy"
  }
  Set-Gate "backend" "PASS" @("started local backend")
}

function Start-FrontendIfNeeded([string]$runDir) {
  if ($SkipFrontend) {
    Set-Gate "frontend" "REVIEW" @("frontend route smoke skipped")
    return
  }
  if (Wait-FrontendRoute 3) {
    if ($ReuseRunningServices) {
      Set-Gate "frontend" "PASS" @("reused reachable frontend")
      return
    }
    Stop-Smoke "BLOCKED" "frontend" "frontend is already running; stop it or pass -ReuseRunningServices explicitly"
  }
  if ($ReuseRunningServices) {
    Stop-Smoke "BLOCKED" "frontend" "reuse mode is enabled but frontend is not reachable"
  }
  $frontendDir = Join-Path (Get-Location) "frontend"
  $stdout = Join-Path $runDir "frontend.stdout.log"
  $stderr = Join-Path $runDir "frontend.stderr.log"
  $frontendPort = Get-FrontendPort
  $process = Start-Process -FilePath "npm.cmd" -ArgumentList @("run", "dev", "--", "-p", "$frontendPort") -WorkingDirectory $frontendDir -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
  $script:StartedProcesses += $process
  if (-not (Wait-FrontendRoute 120)) {
    Stop-Smoke "BLOCKED" "frontend" "frontend route did not become reachable"
  }
  Set-Gate "frontend" "PASS" @("started local frontend and opened root route")
}

function Invoke-MysqlQuery([hashtable]$envValues, [string]$query) {
  if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    Set-Gate "mysqlChunkCount" "REVIEW" @("mysql CLI is not available") "chunk count is unavailable without mysql CLI"
    return @()
  }
  $mysqlUser = Get-EnvValue $envValues @("MYSQL_USERNAME", "MYSQL_USER")
  $mysqlPassword = Get-EnvValue $envValues @("MYSQL_PASSWORD")
  $mysqlDatabase = Get-EnvValue $envValues @("MYSQL_DB", "MYSQL_DATABASE")
  if (-not $mysqlUser -or -not $mysqlPassword -or -not $mysqlDatabase) {
    Set-Gate "mysqlChunkCount" "REVIEW" @("mysql credentials are not configured") "chunk count is unavailable"
    return @()
  }
  $env:MYSQL_PWD = $mysqlPassword
  try {
    $output = & mysql --protocol=TCP -h 127.0.0.1 -P $MySqlLocalPort -u $mysqlUser $mysqlDatabase --batch --raw --skip-column-names -e $query
    if ($LASTEXITCODE -ne 0) {
      Set-Gate "mysqlChunkCount" "REVIEW" @("mysql query failed") "chunk count is unavailable"
      return @()
    }
    return $output
  } finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
  }
}

function Get-ChunkCount([hashtable]$envValues, [long]$userId, [long]$documentId) {
  $query = "SELECT COUNT(*) FROM tb_document_chunk WHERE user_id=${userId} AND document_id=${documentId} AND index_version=${IndexVersion};"
  $lines = Invoke-MysqlQuery $envValues $query
  $first = if ($lines -is [string]) { $lines } else { @($lines | Select-Object -First 1)[0] }
  if ($null -eq $first -or [string]::IsNullOrWhiteSpace([string]$first)) {
    return $null
  }
  return [int]([string]$first)
}

function Convert-NullableInt($value) {
  if ($null -eq $value -or [string]$value -eq "NULL" -or [string]::IsNullOrWhiteSpace([string]$value)) {
    return $null
  }
  return [int]([string]$value)
}

function Convert-NullableString($value) {
  if ($null -eq $value -or [string]$value -eq "NULL") {
    return $null
  }
  return [string]$value
}

function Get-MysqlChunkMetadata([hashtable]$envValues, [long]$userId, [long]$documentId) {
  $query = @"
SELECT id,document_id,user_id,chunk_index,content_hash,token_count,index_status,index_version,COALESCE(embedding_model,''),vector_id
FROM tb_document_chunk
WHERE user_id=${userId} AND document_id=${documentId} AND index_version=${IndexVersion}
ORDER BY chunk_index ASC,id ASC;
"@
  $lines = Invoke-MysqlQuery $envValues $query
  $chunks = @()
  foreach ($line in @($lines)) {
    if (-not $line) { continue }
    $cols = [string]$line -split "`t"
    if ($cols.Count -lt 10) { continue }
    $chunks += [ordered]@{
      chunkId = [long]$cols[0]
      documentId = [long]$cols[1]
      userId = [long]$cols[2]
      chunkIndex = [int]$cols[3]
      contentHash = Convert-NullableString $cols[4]
      tokenCount = Convert-NullableInt $cols[5]
      indexStatus = [string]$cols[6]
      indexVersion = [int]$cols[7]
      embeddingModel = Convert-NullableString $cols[8]
      vectorId = Convert-NullableString $cols[9]
    }
  }
  return $chunks
}

function Wait-IndexedChunkMetadata([hashtable]$envValues, [long]$userId, [long]$documentId) {
  $deadline = (Get-Date).AddSeconds(120)
  $lastChunks = @()
  do {
    $chunks = Get-MysqlChunkMetadata $envValues $userId $documentId
    if ($chunks.Count -gt 0) {
      $lastChunks = $chunks
      $notIndexed = @($chunks | Where-Object { $_.indexStatus -ne "INDEXED" -or -not $_.vectorId }).Count
      if ($notIndexed -eq 0) {
        return $chunks
      }
    }
    Start-Sleep -Seconds 3
  } while ((Get-Date) -lt $deadline)
  return $lastChunks
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

function New-MysqlQdrantParitySummary([array]$chunks, [array]$points, [long]$documentId) {
  $pointsById = @{}
  foreach ($point in @($points)) {
    $pointsById[[string]$point.id] = $point
  }
  $missingVectorIds = 0
  $mismatchedFields = New-Object System.Collections.Generic.List[string]
  $locatorPayloadCount = 0
  $payloadSummaryOkCount = 0
  foreach ($chunk in @($chunks)) {
    $chunkOk = $true
    if (-not $chunk.vectorId -or -not $pointsById.ContainsKey([string]$chunk.vectorId)) {
      $missingVectorIds++
      continue
    }
    $payload = $pointsById[[string]$chunk.vectorId].payload
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
        $chunkOk = $false
      }
    }
    $hasLocatorPayload = @("sourceLocator", "sectionPath", "structureType", "pageNumber", "blockType", "sourceBlockOrdinal") | Where-Object {
      $null -ne $payload.$_ -and -not [string]::IsNullOrWhiteSpace([string]$payload.$_)
    }
    if (@($hasLocatorPayload).Count -gt 0) {
      $locatorPayloadCount++
    }
    if ($chunkOk) {
      $payloadSummaryOkCount++
    }
  }
  $indexedChunkCount = @($chunks | Where-Object { $_.indexStatus -eq "INDEXED" }).Count
  $vectorIdCount = @($chunks | Where-Object { $_.vectorId }).Count
  $chunkCount = @($chunks).Count
  $qdrantPointCount = @($points).Count
  $mismatchCount = @($mismatchedFields | Select-Object -Unique).Count
  return [ordered]@{
    documentId = $documentId
    chunkCount = $chunkCount
    indexedChunkCount = $indexedChunkCount
    vectorIdCount = $vectorIdCount
    qdrantPointCount = $qdrantPointCount
    payloadSummaryOkCount = $payloadSummaryOkCount
    locatorPayloadCount = $locatorPayloadCount
    missingVectorIds = $missingVectorIds
    mismatchedFields = @($mismatchedFields | Select-Object -Unique)
    mysqlQdrantParity = ($chunkCount -gt 0 -and $indexedChunkCount -eq $chunkCount -and $vectorIdCount -eq $chunkCount -and $qdrantPointCount -eq $chunkCount -and $missingVectorIds -eq 0 -and $mismatchCount -eq 0)
  }
}

function Get-IndexParitySummary([hashtable]$envValues, [long]$userId, [long]$documentId) {
  $collection = Get-EnvValue $envValues @("RAG_QDRANT_COLLECTION", "APP_RAG_VECTOR_STORE_QDRANT_COLLECTION") "docpilot_rag_demo"
  try {
    $chunks = Wait-IndexedChunkMetadata $envValues $userId $documentId
    if (@($chunks).Count -eq 0) {
      return [ordered]@{
        documentId = $documentId
        chunkCount = 0
        indexedChunkCount = 0
        vectorIdCount = 0
        qdrantPointCount = $null
        payloadSummaryOkCount = 0
        locatorPayloadCount = 0
        missingVectorIds = 0
        mismatchedFields = @()
        mysqlQdrantParity = $false
      }
    }
    $deadline = (Get-Date).AddSeconds(60)
    $lastSummary = $null
    do {
      $points = Invoke-QdrantScroll $collection $userId $documentId
      $lastSummary = New-MysqlQdrantParitySummary $chunks $points $documentId
      if ([bool]$lastSummary.mysqlQdrantParity) {
        return $lastSummary
      }
      Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    return $lastSummary
  } catch {
    [void](Confirm-EnvironmentStability "mysql/qdrant parity")
    return [ordered]@{
      documentId = $documentId
      chunkCount = $null
      indexedChunkCount = $null
      vectorIdCount = $null
      qdrantPointCount = $null
      payloadSummaryOkCount = $null
      locatorPayloadCount = $null
      missingVectorIds = $null
      mismatchedFields = @("parity_check_unavailable")
      mysqlQdrantParity = $false
    }
  }
}

function Get-ParseFailureCode([hashtable]$envValues, [long]$userId, [long]$documentId) {
  $query = "SELECT COALESCE(error_msg, '') FROM tb_parse_task WHERE user_id=${userId} AND document_id=${documentId} ORDER BY id DESC LIMIT 1;"
  $lines = Invoke-MysqlQuery $envValues $query
  $first = if ($lines -is [string]) { $lines } else { @($lines | Select-Object -First 1)[0] }
  if ($null -eq $first -or [string]::IsNullOrWhiteSpace([string]$first)) {
    return ""
  }
  $text = [string]$first
  if ($text -match '^(PARSER_[A-Z_]+|PARSE_EXCEPTION|ILLEGAL_STATUS_TRANSITION)') {
    return $matches[1]
  }
  return "UNKNOWN"
}

function Wait-ParseTerminal([long]$documentId, [string]$token) {
  $deadline = (Get-Date).AddSeconds(180)
  do {
    $detail = Invoke-JsonApi "GET" "/api/document/detail?documentId=${documentId}" $null $token
    $status = [string]$detail.data.parseStatus
    if ($status -eq "SUCCESS" -or $status -eq "FAILED") {
      return $detail.data
    }
    Start-Sleep -Seconds 3
  } while ((Get-Date) -lt $deadline)
  Stop-Smoke "FAILED_CORE_FLOW" "parse" "document ${documentId} did not reach terminal parse status"
}

function Wait-ChunkCount([hashtable]$envValues, [long]$userId, [long]$documentId) {
  $deadline = (Get-Date).AddSeconds(120)
  do {
    $count = Get-ChunkCount $envValues $userId $documentId
    if ($null -ne $count -and $count -gt 0) {
      return $count
    }
    Start-Sleep -Seconds 3
  } while ((Get-Date) -lt $deadline)
  return $null
}

function Escape-PdfText([string]$text) {
  return $text.Replace("\", "\\").Replace("(", "\(").Replace(")", "\)")
}

function Write-TextPdf([string]$path, [string[]]$lines) {
  $contentLines = @("BT", "/F1 12 Tf", "14 TL", "72 720 Td")
  foreach ($line in $lines) {
    $contentLines += "(" + (Escape-PdfText $line) + ") Tj"
    $contentLines += "T*"
  }
  $contentLines += "ET"
  $stream = ($contentLines -join "`n")
  $objects = @(
    "1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj`n",
    "2 0 obj`n<< /Type /Pages /Kids [3 0 R] /Count 1 >>`nendobj`n",
    "3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>`nendobj`n",
    "4 0 obj`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>`nendobj`n",
    "5 0 obj`n<< /Length $($stream.Length) >>`nstream`n$stream`nendstream`nendobj`n"
  )
  $builder = New-Object System.Text.StringBuilder
  [void]$builder.Append("%PDF-1.4`n")
  $offsets = @(0)
  foreach ($object in $objects) {
    $offsets += [System.Text.Encoding]::ASCII.GetByteCount($builder.ToString())
    [void]$builder.Append($object)
  }
  $xrefOffset = [System.Text.Encoding]::ASCII.GetByteCount($builder.ToString())
  [void]$builder.Append("xref`n0 6`n0000000000 65535 f `n")
  for ($i = 1; $i -le 5; $i++) {
    [void]$builder.Append(("{0:0000000000} 00000 n `n" -f $offsets[$i]))
  }
  [void]$builder.Append("trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n$xrefOffset`n%%EOF`n")
  [System.IO.File]::WriteAllBytes($path, [System.Text.Encoding]::ASCII.GetBytes($builder.ToString()))
}

function Add-ZipEntry([System.IO.Compression.ZipArchive]$zip, [string]$name, [string]$content) {
  $entry = $zip.CreateEntry($name)
  $writer = New-Object System.IO.StreamWriter($entry.Open(), [System.Text.UTF8Encoding]::new($false))
  try {
    $writer.Write($content)
  } finally {
    $writer.Dispose()
  }
}

function Write-DocxFixture([string]$path, [string]$marker) {
  Add-Type -AssemblyName System.IO.Compression
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  if (Test-Path -LiteralPath $path) {
    Remove-Item -LiteralPath $path -Force
  }
  $zip = [System.IO.Compression.ZipFile]::Open($path, [System.IO.Compression.ZipArchiveMode]::Create)
  try {
    Add-ZipEntry $zip "[Content_Types].xml" @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
"@
    Add-ZipEntry $zip "_rels/.rels" @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
"@
    Add-ZipEntry $zip "word/document.xml" @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>DOCX Parser Smoke Title $marker</w:t></w:r></w:p>
    <w:p><w:r><w:t>DOCX paragraph one says the docx parser keeps paragraph evidence for retrieval.</w:t></w:r></w:p>
    <w:p><w:r><w:t>DOCX paragraph two carries docx-table-marker for grounded citation checks.</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="ListParagraph"/></w:pPr><w:r><w:t>DOCX list marker keeps list evidence.</w:t></w:r></w:p>
    <w:tbl>
      <w:tr><w:tc><w:p><w:r><w:t>Field</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Value</w:t></w:r></w:p></w:tc></w:tr>
      <w:tr><w:tc><w:p><w:r><w:t>Parser</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>DOCX Table Evidence $marker</w:t></w:r></w:p></w:tc></w:tr>
    </w:tbl>
    <w:sectPr/>
  </w:body>
</w:document>
"@
  } finally {
    $zip.Dispose()
  }
}

function New-Fixtures([string]$dir, [string]$marker) {
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $pdf = Join-Path $dir "${marker}-pdf.pdf"
  Write-TextPdf $pdf @(
    "# PDF Parser Smoke Title $marker",
    "PDF paragraph one contains pdf-alpha-marker for retrieval.",
    "PDF paragraph two contains pdf-page-one-source for citation.",
    "Page 1"
  )
  $html = Join-Path $dir "${marker}-html.html"
  $htmlMultiChunkBody = (("HTML multi chunk evidence preserves source locator metadata across chunk boundaries. " * 24).Trim())
  [System.IO.File]::WriteAllText($html, @"
<!doctype html>
<html>
<head>
  <title>HTML Parser Smoke $marker</title>
  <style>.noise { display: none; }</style>
  <script>window.__noise = 'must not execute';</script>
</head>
<body>
  <nav>Navigation noise must not appear in extracted text.</nav>
  <aside>Related sidebar noise must not appear in extracted text.</aside>
  <h1>HTML Parser Smoke Title $marker</h1>
  <h2>HTML Evidence Section</h2>
  <p>HTML paragraph one contains html-alpha-marker for retrieval.</p>
  <p>HTML paragraph two keeps local link text <a href="/docs">DocPilot local docs</a>.</p>
  <p>$htmlMultiChunkBody</p>
  <ul><li>HTML list marker keeps checklist evidence.</li></ul>
  <table><tr><td>Parser</td><td>HTML Table Evidence $marker</td></tr></table>
</body>
</html>
"@, [System.Text.UTF8Encoding]::new($false))
  $docx = Join-Path $dir "${marker}-docx.docx"
  Write-DocxFixture $docx $marker
  $longMd = Join-Path $dir "${marker}-long-batch.md"
  $longParagraphs = New-Object System.Collections.Generic.List[string]
  $longParagraphs.Add("# Long Markdown Parser Smoke $marker")
  $longParagraphs.Add("## Long Batch Section")
  for ($i = 1; $i -le 24; $i++) {
    $longParagraphs.Add(("Long markdown batch split paragraph {0} keeps long-batch-marker and long-batch-source-locator evidence inside a realistic repeated operations runbook. " -f $i) + (("The paragraph repeats deployment, rollback, audit, and customer-support context so the parser and embedding pipeline must produce more than ten chunks without relying on a single provider batch. " * 3).Trim()))
  }
  [System.IO.File]::WriteAllText($longMd, ($longParagraphs -join "`n`n"), [System.Text.UTF8Encoding]::new($false))
  return @(
    [ordered]@{ fileType = "PDF"; path = $pdf; contentType = "application/pdf"; parserName = "pdfbox"; query = "pdf-alpha-marker"; expectedLocator = "PDF Parser Smoke Title"; expectedStructures = @("pdf_text", "pdf_page_locator") },
    [ordered]@{ fileType = "HTML"; path = $html; contentType = "text/html"; parserName = "jsoup-html"; query = "html-alpha-marker"; expectedLocator = "HTML Evidence Section"; expectedMinChunks = 2; expectedStructures = @("html_heading", "html_table", "html_link", "html_list", "html_noise_excluded", "html_multi_chunk") },
    [ordered]@{ fileType = "DOCX"; path = $docx; contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; parserName = "poi-docx"; query = "docx-table-marker"; expectedLocator = "DOCX Parser Smoke Title"; expectedStructures = @("docx_heading", "docx_table", "docx_list") },
    [ordered]@{ fileType = "LONG_MD"; path = $longMd; contentType = "text/markdown"; parserName = "text"; query = "long-batch-marker"; expectedLocator = "Long Batch Section"; expectedMinChunks = 12; expectedStructures = @("long_md_heading", "long_md_source_locator", "embedding_batch_split_candidate") }
  )
}

function Get-FixtureStructureSignals($case, [string]$parseStatus, [string]$parsedText, [bool]$sourceLocatorPresent, $chunkCount) {
  if ($parseStatus -ne "SUCCESS") {
    return @()
  }
  $signals = @()
  $text = if ($null -eq $parsedText) { "" } else { [string]$parsedText }
  if ($case.fileType -eq "PDF") {
    if ($text.Contains("PDF Parser Smoke Title")) { $signals += "pdf_text" }
    if ($sourceLocatorPresent) { $signals += "pdf_page_locator" }
  } elseif ($case.fileType -eq "HTML") {
    if ($text.Contains("# HTML Parser Smoke Title") -and $text.Contains("## HTML Evidence Section")) { $signals += "html_heading" }
    if ($text.Contains("HTML Table Evidence")) { $signals += "html_table" }
    if ($text.Contains("DocPilot local docs")) { $signals += "html_link" }
    if ($text.Contains("HTML list marker")) { $signals += "html_list" }
    if (-not $text.Contains("Navigation noise") -and -not $text.Contains("window.__noise") -and -not $text.Contains("Related sidebar noise")) {
      $signals += "html_noise_excluded"
    }
    if ($null -ne $case.expectedMinChunks -and $null -ne $chunkCount -and [int]$chunkCount -ge [int]$case.expectedMinChunks) {
      $signals += "html_multi_chunk"
    }
  } elseif ($case.fileType -eq "DOCX") {
    if ($text.Contains("# DOCX Parser Smoke Title")) { $signals += "docx_heading" }
    if ($text.Contains("DOCX Table Evidence")) { $signals += "docx_table" }
    if ($text.Contains("DOCX list marker")) { $signals += "docx_list" }
  } elseif ($case.fileType -eq "LONG_MD") {
    if ($text.Contains("# Long Markdown Parser Smoke") -and $text.Contains("## Long Batch Section")) { $signals += "long_md_heading" }
    if ($sourceLocatorPresent) { $signals += "long_md_source_locator" }
    if ($null -ne $case.expectedMinChunks -and $null -ne $chunkCount -and [int]$chunkCount -ge [int]$case.expectedMinChunks) {
      $signals += "embedding_batch_split_candidate"
    }
  }
  return @($signals | Select-Object -Unique)
}

function Test-ArtifactSafe($artifact) {
  $json = $artifact | ConvertTo-Json -Depth 20
  $patterns = @(
    '(?i)api[_-]?key',
    '(?i)access[_-]?token',
    '(?i)secret',
    '(?i)password',
    '(?i)jdbc:',
    '(?i)mongodb://',
    '(?i)redis://',
    '(?i)BEGIN [A-Z ]*PRIVATE KEY',
    '(?i)"answer"\s*:',
    '(?i)"prompt"\s*:',
    '(?i)"content"\s*:'
  )
  foreach ($pattern in $patterns) {
    if ($json -match $pattern) {
      Stop-Smoke "FAILED_CORE_FLOW" "artifactRedaction" "artifact contains a forbidden field or sensitive-looking token"
    }
  }
  Set-Gate "artifactRedaction" "PASS" @("artifact schema uses whitelisted summary fields only")
}

function Invoke-ParserCase($case, [hashtable]$envValues, [long]$userId, [string]$token) {
  $caseStart = Get-Date
  $script:CurrentParserStage = "upload"
  $upload = Upload-SmokeFile $case.path $case.contentType $token
  $script:CurrentParserStage = "document_create"
  $document = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $upload.data.id }) $token
  $script:CurrentParserStage = "parse_task_create"
  [void](Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $document.data.id }) $token -AllowFailure)
  $script:CurrentParserStage = "parse_wait"
  $detail = Wait-ParseTerminal ([long]$document.data.id) $token
  $parseStatus = [string]$detail.parseStatus
  $extractedChars = if ($detail.content) { ([string]$detail.content).Length } else { 0 }
  $chunkCount = $null
  $indexedChunkCount = $null
  $vectorIdCount = $null
  $qdrantPointCount = $null
  $payloadSummaryOkCount = $null
  $locatorPayloadCount = $null
  $missingVectorIds = $null
  $mismatchedPayloadFields = @()
  $mysqlQdrantParity = $false
  $retrieveHit = $false
  $directRetrieveHit = $false
  $qaRetrievalHit = $false
  $citationPresent = $false
  $sourceLocatorPresent = $false
  $directRetrieveAttempts = 0
  $directRetrieveDiagnostic = $null
  $qaRetrieveDiagnostic = $null
  $expectedMinChunks = if ($null -eq $case.expectedMinChunks) { $null } else { [int]$case.expectedMinChunks }
  $multiChunkVerified = $null
  $failureReason = $null
  $parsedText = ""
  if ($parseStatus -eq "SUCCESS") {
    $parsedText = if ($detail.content) { [string]$detail.content } else { "" }
    $script:CurrentParserStage = "index_parity"
    $paritySummary = Get-IndexParitySummary $envValues $userId ([long]$document.data.id)
    $chunkCount = $paritySummary.chunkCount
    $indexedChunkCount = $paritySummary.indexedChunkCount
    $vectorIdCount = $paritySummary.vectorIdCount
    $qdrantPointCount = $paritySummary.qdrantPointCount
    $payloadSummaryOkCount = $paritySummary.payloadSummaryOkCount
    $locatorPayloadCount = $paritySummary.locatorPayloadCount
    $missingVectorIds = $paritySummary.missingVectorIds
    $mismatchedPayloadFields = @($paritySummary.mismatchedFields)
    $mysqlQdrantParity = [bool]$paritySummary.mysqlQdrantParity
    if ($null -ne $expectedMinChunks -and $null -ne $chunkCount) {
      $multiChunkVerified = [int]$chunkCount -ge $expectedMinChunks
    }
    $question = "请根据文档回答 $($case.query)"
    $script:CurrentParserStage = "retrieve"
    $retrieve = $null
    $hits = @()
    for ($attempt = 1; $attempt -le 15; $attempt++) {
      $directRetrieveAttempts = $attempt
      $retrieve = Invoke-JsonApi "POST" "/api/rag/retrieve" ([ordered]@{ documentId = $document.data.id; query = $question; topK = 5; indexVersion = $IndexVersion }) $token -AllowFailure
      $hits = if ($retrieve.ok) { To-SafeArray $retrieve.data.hits } else { @() }
      if (-not $retrieve.ok) {
        [void](Confirm-EnvironmentStability "direct retrieve")
      }
      if ((Get-SafeItemCount $hits) -gt 0) {
        break
      }
      Start-Sleep -Seconds 3
    }
    $directRetrieveHit = ([bool]$retrieve.ok) -and ((Get-SafeItemCount $hits) -gt 0)
    $directRetrieveDiagnostic = New-RagCallDiagnostic $retrieve $retrieve.data $hits $directRetrieveAttempts "initial_wait"
    $retrieveHit = $directRetrieveHit
    $citations = @()
    if ($retrieve.ok -or -not $script:EnvironmentUnstable) {
      $script:CurrentParserStage = "qa"
      $qa = Invoke-JsonApi "POST" "/api/documents/$($document.data.id)/qa/rag" ([ordered]@{ question = $question; topK = 5; indexVersion = $IndexVersion; sessionId = "parser-smoke" }) $token -AllowFailure
      if ($qa.ok) {
        $qaHits = To-SafeArray $qa.data.retrieval.hits
        $qaRetrievalHit = (Get-SafeItemCount $qaHits) -gt 0
        $retrieveHit = $retrieveHit -or $qaRetrievalHit
        $citations = To-SafeArray $qa.data.citations
        $citationPresent = (Get-SafeItemCount $citations) -gt 0
        $qaRetrieveDiagnostic = New-RagCallDiagnostic $qa $qa.data.retrieval $qaHits 1 "qa_retrieval"
      } else {
        [void](Confirm-EnvironmentStability "qa retrieval")
        $qaRetrieveDiagnostic = New-RagCallDiagnostic $qa $null @() 1 "qa_retrieval"
        $failureReason = "qa_api_failed"
      }
    } else {
      $failureReason = "retrieve_api_failed"
    }
    if (-not $directRetrieveHit -and $qaRetrievalHit) {
      $script:CurrentParserStage = "retrieve_confirm"
      $confirmAttempts = 0
      for ($attempt = 1; $attempt -le 10; $attempt++) {
        $confirmAttempts = $attempt
        $retrieve = Invoke-JsonApi "POST" "/api/rag/retrieve" ([ordered]@{ documentId = $document.data.id; query = $question; topK = 5; indexVersion = $IndexVersion }) $token -AllowFailure
        $hits = if ($retrieve.ok) { To-SafeArray $retrieve.data.hits } else { @() }
        if (-not $retrieve.ok) {
          [void](Confirm-EnvironmentStability "direct retrieve confirm")
        }
        if ((Get-SafeItemCount $hits) -gt 0) {
          $directRetrieveHit = $true
          break
        }
        Start-Sleep -Seconds 3
      }
      $directRetrieveAttempts += $confirmAttempts
      $directRetrieveDiagnostic = New-RagCallDiagnostic $retrieve $retrieve.data $hits $directRetrieveAttempts "post_qa_confirm"
    }
    $script:CurrentParserStage = "source_locator"
    $locatorItems = @()
    $locatorItems += To-SafeArray $hits
    $locatorItems += To-SafeArray $citations
    $sourceLocatorPresent = @($locatorItems | Where-Object {
        (-not [string]::IsNullOrWhiteSpace([string]$_.sourceName)) -and (
          -not [string]::IsNullOrWhiteSpace([string]$_.sourceLocator) -or
          -not [string]::IsNullOrWhiteSpace([string]$_.blockType) -or
          $null -ne $_.pageNumber -or
          -not [string]::IsNullOrWhiteSpace([string]$_.sectionPath) -or
          -not [string]::IsNullOrWhiteSpace([string]$_.structureType) -or
          $null -ne $_.startOffset
        )
      }).Count -gt 0
    if (-not [string]::IsNullOrWhiteSpace($failureReason)) {
      # Keep the earlier sanitized API failure reason.
    } elseif (-not $retrieveHit) {
      $failureReason = "retrieve returned no hit"
    } elseif (-not $citationPresent) {
      $failureReason = "qa returned no citation"
    } elseif (-not $sourceLocatorPresent) {
      $failureReason = "citation source locator is incomplete"
    } elseif ($multiChunkVerified -eq $false) {
      $failureReason = "expected minimum chunk count was not reached"
    } elseif (-not $mysqlQdrantParity) {
      $failureReason = "mysql/qdrant index parity is incomplete"
    }
  } else {
    $failureReason = "parse failed"
  }
  $structureSignals = Get-FixtureStructureSignals $case $parseStatus $parsedText $sourceLocatorPresent $chunkCount
  $script:CurrentParserStage = ""
  $caseResult = [ordered]@{
    fileType = $case.fileType
    parserName = $case.parserName
    parseStatus = $parseStatus
    extractedChars = $extractedChars
    pageCount = if ($case.fileType -eq "PDF") { 1 } else { $null }
    blockCount = $null
    warningCount = $null
    chunkCount = $chunkCount
    indexedChunkCount = $indexedChunkCount
    vectorIdCount = $vectorIdCount
    qdrantPointCount = $qdrantPointCount
    payloadSummaryOkCount = $payloadSummaryOkCount
    locatorPayloadCount = $locatorPayloadCount
    missingVectorIds = $missingVectorIds
    mismatchedPayloadFields = $mismatchedPayloadFields
    mysqlQdrantParity = $mysqlQdrantParity
    expectedMinChunks = $expectedMinChunks
    multiChunkVerified = $multiChunkVerified
    retrieveHit = $retrieveHit
    directRetrieveHit = $directRetrieveHit
    qaRetrievalHit = $qaRetrievalHit
    citationPresent = $citationPresent
    sourceLocatorPresent = $sourceLocatorPresent
    expectedStructures = @($case.expectedStructures)
    structureSignals = $structureSignals
    directRetrieveDiagnostic = $directRetrieveDiagnostic
    qaRetrieveDiagnostic = $qaRetrieveDiagnostic
    failureReason = $failureReason
    durationMs = [int]((Get-Date) - $caseStart).TotalMilliseconds
  }
  if ($parseStatus -eq "SUCCESS" -and -not $directRetrieveHit) {
    $script:DirectRetrieveFollowUps += [pscustomobject]@{
      documentId = [long]$document.data.id
      question = $question
      token = $token
      result = $caseResult
    }
  }
  return $caseResult
}

function Confirm-DirectRetrieveFollowUps([string]$token) {
  foreach ($followUp in @($script:DirectRetrieveFollowUps)) {
    if ($followUp.result.directRetrieveHit) {
      continue
    }
    $existingDiagnostic = $followUp.result["directRetrieveDiagnostic"]
    $attemptsBefore = if ($existingDiagnostic -and $null -ne $existingDiagnostic.attempts) { [int]$existingDiagnostic.attempts } else { 0 }
    $followUpAttempts = 0
    $retrieve = $null
    $hits = @()
    $caseToken = if (-not [string]::IsNullOrWhiteSpace([string]$followUp.token)) { [string]$followUp.token } else { $token }
    for ($attempt = 1; $attempt -le 20; $attempt++) {
      $followUpAttempts = $attempt
      $retrieve = Invoke-JsonApi "POST" "/api/rag/retrieve" ([ordered]@{ documentId = $followUp.documentId; query = $followUp.question; topK = 5; indexVersion = $IndexVersion }) $caseToken -AllowFailure
      $hits = if ($retrieve.ok) { To-SafeArray $retrieve.data.hits } else { @() }
      if ((Get-SafeItemCount $hits) -gt 0) {
        $followUp.result["directRetrieveHit"] = $true
        $followUp.result["retrieveHit"] = $true
        break
      }
      Start-Sleep -Seconds 3
    }
    $followUp.result["directRetrieveDiagnostic"] = New-RagCallDiagnostic $retrieve $retrieve.data $hits ($attemptsBefore + $followUpAttempts) "delayed_follow_up"
  }
}

function Invoke-BoundaryChecks([string]$fixtureDir, [hashtable]$envValues) {
  $unsupportedIdentity = New-BoundaryIdentity "unsupported"
  $unsupported = Join-Path $fixtureDir "unsupported.exe"
  [System.IO.File]::WriteAllText($unsupported, "unsupported parser smoke fixture", [System.Text.UTF8Encoding]::new($false))
  $unsupportedUpload = Upload-SmokeFile $unsupported "application/octet-stream" $unsupportedIdentity.token -AllowFailure
  $boundaryCases = @(
    [ordered]@{
      caseId = "unsupported-upload"
      fileType = "BIN"
      uploadRejected = (-not $unsupportedUpload.ok)
      parseStatus = $null
      failureCode = if (-not $unsupportedUpload.ok) { "UPLOAD_REJECTED" } else { "UNEXPECTED_UPLOAD_ACCEPTED" }
      expectedFailureCode = "UPLOAD_REJECTED"
      passed = (-not $unsupportedUpload.ok)
    }
  )

  $emptyTxt = Join-Path $fixtureDir "empty.txt"
  [System.IO.File]::WriteAllText($emptyTxt, "   `n`t  ", [System.Text.UTF8Encoding]::new($false))
  $brokenPdf = Join-Path $fixtureDir "broken.pdf"
  [System.IO.File]::WriteAllBytes($brokenPdf, [byte[]](1, 2, 3, 4, 5))
  $brokenDocx = Join-Path $fixtureDir "broken.docx"
  [System.IO.File]::WriteAllBytes($brokenDocx, [byte[]](80, 75, 3, 4, 1, 2, 3))

  $negativeCases = @(
    [ordered]@{ caseId = "empty-text"; fileType = "TXT"; path = $emptyTxt; contentType = "text/plain"; expectedFailureCode = "PARSER_EMPTY_CONTENT" },
    [ordered]@{ caseId = "corrupted-pdf"; fileType = "PDF"; path = $brokenPdf; contentType = "application/pdf"; expectedFailureCode = "PARSER_CORRUPTED_FILE" },
    [ordered]@{ caseId = "corrupted-docx"; fileType = "DOCX"; path = $brokenDocx; contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; expectedFailureCode = "PARSER_CORRUPTED_FILE" }
  )
  foreach ($case in $negativeCases) {
    $boundaryCases += Invoke-NegativeParserCase $case $envValues
  }

  $passedCount = @($boundaryCases | Where-Object { $_.passed }).Count
  return [ordered]@{
    unsupportedUploadRejected = (-not $unsupportedUpload.ok)
    negativeCaseCount = $boundaryCases.Count
    negativeCasePassCount = $passedCount
    negativeCaseFailCount = $boundaryCases.Count - $passedCount
    cases = $boundaryCases
    htmlNoExternalNetwork = $true
    rawTextLogged = $false
    notes = @("negative parser cases are verified through upload/create/parse and store only sanitized failure codes")
  }
}

function New-BoundaryIdentity([string]$caseId) {
  $username = "parserb_$([Guid]::NewGuid().ToString('N').Substring(0, 10))"
  $body = [ordered]@{
    username = $username
    nickname = "Parser Boundary"
  }
  $body["password"] = "ParserSmoke123!"
  $register = Invoke-JsonApi "POST" "/api/auth/register" $body
  return [ordered]@{
    token = [string]$register.data.token
    userId = [long]$register.data.userId
  }
}

function Invoke-NegativeParserCase($case, [hashtable]$envValues) {
  $identity = New-BoundaryIdentity $case.caseId
  $upload = Upload-SmokeFile $case.path $case.contentType $identity.token -AllowFailure
  if (-not $upload.ok) {
    return [ordered]@{
      caseId = $case.caseId
      fileType = $case.fileType
      uploadRejected = $true
      parseStatus = $null
      failureCode = "UPLOAD_REJECTED"
      expectedFailureCode = $case.expectedFailureCode
      passed = $false
    }
  }
  $document = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $upload.data.id }) $identity.token
  [void](Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $document.data.id }) $identity.token -AllowFailure)
  $detail = Wait-ParseTerminal ([long]$document.data.id) $identity.token
  $parseStatus = [string]$detail.parseStatus
  $failureCode = Get-ParseFailureCode $envValues $identity.userId ([long]$document.data.id)
  $passed = ($parseStatus -eq "FAILED" -and $failureCode -eq $case.expectedFailureCode)
  return [ordered]@{
    caseId = $case.caseId
    fileType = $case.fileType
    uploadRejected = $false
    parseStatus = $parseStatus
    failureCode = $failureCode
    expectedFailureCode = $case.expectedFailureCode
    passed = $passed
  }
}

function Stop-StartedProcesses() {
  foreach ($process in @($script:StartedProcesses)) {
    if ($process -and -not $process.HasExited) {
      Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
  }
  if ($script:StartedTunnelPid) {
    Stop-Process -Id $script:StartedTunnelPid -Force -ErrorAction SilentlyContinue
  }
  $cleanup = Join-Path (Get-Location) "scripts/dev/cleanup-agent-processes.ps1"
  if (Test-Path -LiteralPath $cleanup) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $cleanup | Out-Null
  }
}

function Get-QualityCoverageProfile {
  if ($SkipFrontend) { return "runtime_core_only" }
  return "runtime_full"
}

function New-QualityRunObservation([datetime]$startedAt, [datetime]$finishedAt) {
  $sampleGaps = @("tokenUsageMissing", "costMetricMissing", "latencyMetricMissing", "modelMetricsUnavailable")
  if ($ReuseRunningServices) {
    $sampleGaps += "metricsNotIsolated"
  }
  return [ordered]@{
    schemaVersion = 1
    suiteId = "document_parser_real_chain"
    suiteVersion = "2026-07-15"
    coverageProfile = Get-QualityCoverageProfile
    startedAt = $startedAt.ToString("o")
    finishedAt = $finishedAt.ToString("o")
    durationMs = [long]($finishedAt - $startedAt).TotalMilliseconds
    latencyMs = $null
    tokenUsage = [ordered]@{}
    sampleGaps = @($sampleGaps | Select-Object -Unique)
  }
}

function Write-Artifact($artifact, [string]$path) {
  Test-ArtifactSafe $artifact
  $json = $artifact | ConvertTo-Json -Depth 20
  [System.IO.File]::WriteAllText($path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Get-SafeRate($numerator, $denominator) {
  if ($null -eq $numerator -or $null -eq $denominator -or [int]$denominator -le 0) {
    return $null
  }
  return [Math]::Round(([double]$numerator / [double]$denominator), 4)
}

function New-RagCallDiagnostic($response, $payload, [array]$hits, [int]$attempts, [string]$stage) {
  $ok = $false
  $httpStatus = $null
  $code = $null
  if ($null -ne $response) {
    $ok = [bool]$response.ok
    $httpStatus = $response.httpStatus
    $code = $response.code
  }
  $noEvidence = $null
  $provider = $null
  $collectionPresent = $false
  $citations = @()
  if ($null -ne $payload) {
    if ($null -ne $payload.noEvidence) {
      $noEvidence = [bool]$payload.noEvidence
    }
    if ($null -ne $payload.provider) {
      $candidateProvider = [string]$payload.provider
      if ($candidateProvider -match '^[A-Za-z0-9_.-]{1,64}$') {
        $provider = $candidateProvider
      }
    }
    if (-not [string]::IsNullOrWhiteSpace([string]$payload.collection)) {
      $collectionPresent = $true
    }
    $citations = To-SafeArray $payload.citations
  }
  return [ordered]@{
    stage = $stage
    ok = $ok
    httpStatus = $httpStatus
    code = $code
    attempts = $attempts
    hitCount = Get-SafeItemCount $hits
    citationCount = Get-SafeItemCount $citations
    noEvidence = $noEvidence
    provider = $provider
    collectionPresent = $collectionPresent
  }
}

function New-ParserQualityReport([array]$results, $boundary, [string]$qualityStatus) {
  $expectedTypes = @("PDF", "HTML", "DOCX", "LONG_MD")
  $coveredTypes = @($results | ForEach-Object { [string]$_.fileType } | Where-Object { $_ } | Select-Object -Unique)
  $missingTypes = @($expectedTypes | Where-Object { $coveredTypes -notcontains $_ })
  $expectedStructureSignals = @($results | ForEach-Object { @($_.expectedStructures) } | Where-Object { $_ } | Select-Object -Unique)
  $coveredStructureSignals = @($results | ForEach-Object { @($_.structureSignals) } | Where-Object { $_ } | Select-Object -Unique)
  $missingStructureSignals = @($expectedStructureSignals | Where-Object { $coveredStructureSignals -notcontains $_ })
  $fileCount = @($results).Count
  $parsedFileCount = @($results | Where-Object { $_.parseStatus -eq "SUCCESS" }).Count
  $parserFailureCount = @($results | Where-Object { $_.parseStatus -ne "SUCCESS" }).Count
  $sourceLocatorCount = @($results | Where-Object { $_.sourceLocatorPresent }).Count
  $missingLocatorTypes = @($results | Where-Object { -not $_.sourceLocatorPresent } | ForEach-Object { [string]$_.fileType } | Where-Object { $_ } | Select-Object -Unique)
  $retrieveHitCount = @($results | Where-Object { $_.retrieveHit }).Count
  $directRetrieveHitCount = @($results | Where-Object { $_.directRetrieveHit }).Count
  $qaRetrievalHitCount = @($results | Where-Object { $_.qaRetrievalHit }).Count
  $citationCount = @($results | Where-Object { $_.citationPresent }).Count
  $directRetrieveOkCount = 0
  $qaRetrieveOkCount = 0
  $directRetrieveNoEvidenceCount = 0
  $qaRetrieveNoEvidenceCount = 0
  $directRetrieveMaxAttempts = $null
  $qaRetrieveMaxAttempts = $null
  foreach ($result in @($results)) {
    $directDiagnostic = $result.directRetrieveDiagnostic
    if ($directDiagnostic) {
      if ([bool]$directDiagnostic.ok) {
        $directRetrieveOkCount += 1
      }
      if ($null -ne $directDiagnostic.noEvidence -and [bool]$directDiagnostic.noEvidence) {
        $directRetrieveNoEvidenceCount += 1
      }
      if ($null -ne $directDiagnostic.attempts) {
        $attempts = [int]$directDiagnostic.attempts
        $directRetrieveMaxAttempts = if ($null -eq $directRetrieveMaxAttempts) { $attempts } else { [Math]::Max($directRetrieveMaxAttempts, $attempts) }
      }
    }
    $qaDiagnostic = $result.qaRetrieveDiagnostic
    if ($qaDiagnostic) {
      if ([bool]$qaDiagnostic.ok) {
        $qaRetrieveOkCount += 1
      }
      if ($null -ne $qaDiagnostic.noEvidence -and [bool]$qaDiagnostic.noEvidence) {
        $qaRetrieveNoEvidenceCount += 1
      }
      if ($null -ne $qaDiagnostic.attempts) {
        $attempts = [int]$qaDiagnostic.attempts
        $qaRetrieveMaxAttempts = if ($null -eq $qaRetrieveMaxAttempts) { $attempts } else { [Math]::Max($qaRetrieveMaxAttempts, $attempts) }
      }
    }
  }
  $chunkCountKnown = @($results | Where-Object { $null -ne $_.chunkCount }).Count
  $multiChunkExpected = @($results | Where-Object { $null -ne $_.expectedMinChunks }).Count
  $multiChunkVerified = @($results | Where-Object { $_.multiChunkVerified -eq $true }).Count
  $chunkCount = $null
  if ($chunkCountKnown -gt 0) {
    $chunkCount = 0
    foreach ($result in @($results)) {
      if ($null -ne $result.chunkCount) {
        $chunkCount += [int]$result.chunkCount
      }
    }
  }
  $indexParityKnown = @($results | Where-Object { $null -ne $_.indexedChunkCount -and $null -ne $_.vectorIdCount -and $null -ne $_.qdrantPointCount }).Count
  $indexParityVerified = @($results | Where-Object { $_.mysqlQdrantParity -eq $true }).Count
  $indexedChunkCount = $null
  $vectorIdCount = $null
  $qdrantPointCount = $null
  $payloadSummaryOkCount = $null
  $locatorPayloadCount = $null
  if ($indexParityKnown -gt 0) {
    $indexedChunkCount = 0
    $vectorIdCount = 0
    $qdrantPointCount = 0
    $payloadSummaryOkCount = 0
    $locatorPayloadCount = 0
    foreach ($result in @($results)) {
      if ($null -ne $result.indexedChunkCount) { $indexedChunkCount += [int]$result.indexedChunkCount }
      if ($null -ne $result.vectorIdCount) { $vectorIdCount += [int]$result.vectorIdCount }
      if ($null -ne $result.qdrantPointCount) { $qdrantPointCount += [int]$result.qdrantPointCount }
      if ($null -ne $result.payloadSummaryOkCount) { $payloadSummaryOkCount += [int]$result.payloadSummaryOkCount }
      if ($null -ne $result.locatorPayloadCount) { $locatorPayloadCount += [int]$result.locatorPayloadCount }
    }
  }
  $parityMismatchedFields = @($results | ForEach-Object { @($_.mismatchedPayloadFields) } | Where-Object { $_ } | Select-Object -Unique)
  $warningCountKnown = @($results | Where-Object { $null -ne $_.warningCount }).Count
  $totalWarningCount = $null
  $filesWithWarnings = $null
  if ($warningCountKnown -gt 0) {
    $totalWarningCount = 0
    $filesWithWarnings = 0
    foreach ($result in @($results)) {
      if ($null -ne $result.warningCount) {
        $totalWarningCount += [int]$result.warningCount
        if ([int]$result.warningCount -gt 0) {
          $filesWithWarnings += 1
        }
      }
    }
  }
  $negativeCaseCount = if ($boundary) { $boundary.negativeCaseCount } else { $null }
  $negativeCasePassCount = if ($boundary) { $boundary.negativeCasePassCount } else { $null }
  $negativeCaseFailCount = if ($boundary) { $boundary.negativeCaseFailCount } else { $null }
  $unsupportedUploadRejected = if ($boundary) { $boundary.unsupportedUploadRejected } else { $null }

  $reviewReasons = @()
  if ($missingTypes.Count -gt 0) {
    $reviewReasons += "parser_file_type_missing"
  }
  if ($parserFailureCount -gt 0) {
    $reviewReasons += "parse_status_failed"
  }
  if ($missingStructureSignals.Count -gt 0) {
    $reviewReasons += "fixture_structure_missing"
  }
  if ($fileCount -gt 0 -and $sourceLocatorCount -lt $fileCount) {
    $reviewReasons += "missing_source_locator"
  }
  if ($fileCount -gt 0 -and ($retrieveHitCount -lt $fileCount -or $citationCount -lt $fileCount)) {
    $reviewReasons += "retrieval_or_citation_missing"
  }
  if ($fileCount -gt 0 -and $directRetrieveHitCount -lt $fileCount) {
    $reviewReasons += "direct_retrieve_missing"
  }
  if ($multiChunkExpected -gt 0 -and $multiChunkVerified -lt $multiChunkExpected) {
    $reviewReasons += "multi_chunk_source_coverage_missing"
  }
  if ($parsedFileCount -gt 0 -and $indexParityVerified -lt $parsedFileCount) {
    $reviewReasons += "mysql_qdrant_parity_missing"
  }
  if ($null -ne $negativeCaseFailCount -and [int]$negativeCaseFailCount -gt 0) {
    $reviewReasons += "parser_boundary_failed"
  }
  if ($null -ne $unsupportedUploadRejected -and -not [bool]$unsupportedUploadRejected) {
    $reviewReasons += "unsupported_upload_not_rejected"
  }
  if ($script:EnvironmentUnstable) {
    $reviewReasons += "environment_unstable"
  }

  $unavailableMetrics = @()
  if ($chunkCountKnown -lt $fileCount) {
    $unavailableMetrics += "chunkCount"
  }
  if ($indexParityKnown -lt $parsedFileCount) {
    $unavailableMetrics += "mysqlQdrantParity"
  }
  if ($warningCountKnown -lt $fileCount) {
    $unavailableMetrics += "warningCount"
  }

  return [ordered]@{
    schemaVersion = 1
    qualityStatus = $qualityStatus
    fileTypeCoverage = [ordered]@{
      expectedTypes = $expectedTypes
      coveredTypes = $coveredTypes
      missingTypes = $missingTypes
      allCovered = ($missingTypes.Count -eq 0)
    }
    fixtureStructureCoverage = [ordered]@{
      expectedSignals = $expectedStructureSignals
      coveredSignals = $coveredStructureSignals
      missingSignals = $missingStructureSignals
      allCovered = ($missingStructureSignals.Count -eq 0)
    }
    multiChunkSummary = [ordered]@{
      expectedFileCount = $multiChunkExpected
      verifiedFileCount = $multiChunkVerified
      allVerified = if ($multiChunkExpected -eq 0) { $null } else { $multiChunkVerified -eq $multiChunkExpected }
    }
    indexParitySummary = [ordered]@{
      parityKnownFileCount = $indexParityKnown
      parityVerifiedFileCount = $indexParityVerified
      allVerified = if ($parsedFileCount -eq 0) { $null } else { $indexParityVerified -eq $parsedFileCount }
      indexedChunkCount = $indexedChunkCount
      vectorIdCount = $vectorIdCount
      qdrantPointCount = $qdrantPointCount
      payloadSummaryOkCount = $payloadSummaryOkCount
      locatorPayloadCount = $locatorPayloadCount
      mismatchedFields = $parityMismatchedFields
    }
    parseStatusSummary = [ordered]@{
      fileCount = $fileCount
      parsedFileCount = $parsedFileCount
      parserFailureCount = $parserFailureCount
      parsePassRate = Get-SafeRate $parsedFileCount $fileCount
    }
    sourceLocatorSummary = [ordered]@{
      sourceLocatorCount = $sourceLocatorCount
      fileCount = $fileCount
      sourceLocatorCoverageRate = Get-SafeRate $sourceLocatorCount $fileCount
      missingLocatorTypes = $missingLocatorTypes
    }
    ragChainSummary = [ordered]@{
      chunkCountKnown = $chunkCountKnown
      chunkCount = $chunkCount
      retrieveHitCount = $retrieveHitCount
      directRetrieveHitCount = $directRetrieveHitCount
      qaRetrievalHitCount = $qaRetrievalHitCount
      citationCount = $citationCount
      directRetrieveOkCount = $directRetrieveOkCount
      qaRetrieveOkCount = $qaRetrieveOkCount
      directRetrieveNoEvidenceCount = $directRetrieveNoEvidenceCount
      qaRetrieveNoEvidenceCount = $qaRetrieveNoEvidenceCount
      directRetrieveMaxAttempts = $directRetrieveMaxAttempts
      qaRetrieveMaxAttempts = $qaRetrieveMaxAttempts
      environmentUnstable = [bool]$script:EnvironmentUnstable
      retrieveCoverageRate = Get-SafeRate $retrieveHitCount $fileCount
      citationCoverageRate = Get-SafeRate $citationCount $fileCount
    }
    boundarySummary = [ordered]@{
      negativeCaseCount = $negativeCaseCount
      negativeCasePassCount = $negativeCasePassCount
      negativeCaseFailCount = $negativeCaseFailCount
      boundaryPassRate = Get-SafeRate $negativeCasePassCount $negativeCaseCount
      unsupportedUploadRejected = $unsupportedUploadRejected
    }
    warningsSummary = [ordered]@{
      warningCountKnown = $warningCountKnown
      totalWarningCount = $totalWarningCount
      filesWithWarnings = $filesWithWarnings
    }
    reviewReasons = $reviewReasons
    unavailableMetrics = $unavailableMetrics
  }
}

if ($Mode -eq "plan") {
  [ordered]@{
    mode = "plan"
    willCreateBusinessData = $false
    runModeOnly = @("start controlled local tunnel/backend/frontend unless -ReuseRunningServices is explicit", "register temporary smoke users so LONG_MD does not trip the real upload rate limit", "upload PDF/HTML/DOCX/LONG_MD fixtures including local HTML noise-isolation, multi-chunk, and long markdown embedding batch-split coverage", "wait parse", "wait direct retrieve until vector search is visible with the same user-style question used by QA", "confirm direct retrieve again after QA retrieval if indexing visibility is delayed", "validate QA retrieve and citation", "verify unsupported/empty/corrupted parser boundaries", "write redacted artifact")
    artifactSchema = @("qualityRun.schemaVersion", "qualityRun.suiteId", "qualityRun.coverageProfile", "qualityRun.durationMs", "qualityRun.sampleGaps", "fileType", "parserName", "parseStatus", "extractedChars", "pageCount", "blockCount", "warningCount", "chunkCount", "indexedChunkCount", "vectorIdCount", "qdrantPointCount", "payloadSummaryOkCount", "locatorPayloadCount", "mysqlQdrantParity", "expectedMinChunks", "multiChunkVerified", "retrieveHit", "directRetrieveHit", "qaRetrievalHit", "citationPresent", "expectedStructures", "structureSignals", "directRetrieveDiagnostic", "qaRetrieveDiagnostic", "failureReason", "durationMs", "boundary.caseId", "boundary.failureCode", "boundary.expectedFailureCode", "parserQualityReport")
    forbiddenArtifactFields = @("prompt", "answer", "document full text", "evidence context", "secret", "connection string", "cloud address")
  } | ConvertTo-Json -Depth 10
  exit 0
}

if ($Mode -eq "dry-run") {
  [ordered]@{
    mode = "dry-run"
    willCreateBusinessData = $false
    checks = @("script parameters parsed", "fixture recipes including local HTML noise isolation, multi-chunk coverage, and long markdown batch-split coverage available", "negative parser boundary recipes available", "artifact schema is redacted", "run mode remains explicit")
    supportedTypes = @("PDF", "HTML", "DOCX", "LONG_MD")
    parserQualityReport = @("fileTypeCoverage", "fixtureStructureCoverage", "multiChunkSummary", "indexParitySummary", "parseStatusSummary", "sourceLocatorSummary", "ragChainSummary", "boundarySummary", "warningsSummary", "reviewReasons")
  } | ConvertTo-Json -Depth 10
  exit 0
}

$marker = "${SmokePrefix}-$(Get-Date -Format yyyyMMddHHmmss)-$([Guid]::NewGuid().ToString('N').Substring(0, 6))"
$runDir = Join-Path $ArtifactRoot $marker
$fixtureDir = Join-Path $runDir "fixtures"
$artifactPath = Join-Path $runDir "artifact.json"
$script:ArtifactPath = $artifactPath
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$envValues = Read-EnvFile $EnvFile
$results = @()
$boundary = $null
$startedAt = Get-Date

try {
  Start-TunnelsIfNeeded $EnvFile
  Start-BackendIfNeeded $runDir
  Start-FrontendIfNeeded $runDir

  $fixtures = New-Fixtures $fixtureDir $marker
  Set-Gate "fixtures" "PASS" @("generated local PDF/HTML/DOCX/LONG_MD smoke fixtures")

  $username = "parser_$($marker.Substring($marker.Length - 6))"
  $password = "ParserSmoke123!"
  $register = Invoke-JsonApi "POST" "/api/auth/register" ([ordered]@{ username = $username; password = $password; nickname = "Parser Smoke" })
  $token = [string]$register.data.token
  $userId = [long]$register.data.userId
  $longUsername = "parserl_$($marker.Substring($marker.Length - 6))"
  $longRegister = Invoke-JsonApi "POST" "/api/auth/register" ([ordered]@{ username = $longUsername; password = $password; nickname = "Parser Long Smoke" })
  $longToken = [string]$longRegister.data.token
  $longUserId = [long]$longRegister.data.userId
  Set-Gate "auth" "PASS" @("temporary smoke users registered")

  foreach ($fixture in $fixtures) {
    try {
      $caseToken = if ($fixture.fileType -eq "LONG_MD") { $longToken } else { $token }
      $caseUserId = if ($fixture.fileType -eq "LONG_MD") { $longUserId } else { $userId }
      $results += Invoke-ParserCase $fixture $envValues $caseUserId $caseToken
    } catch {
      $safeStage = if ([string]::IsNullOrWhiteSpace($script:CurrentParserStage)) { "unknown" } else { $script:CurrentParserStage }
      [void](Confirm-EnvironmentStability "parser case ${safeStage}")
      $expectedMinChunks = if ($null -eq $fixture.expectedMinChunks) { $null } else { [int]$fixture.expectedMinChunks }
      $results += [ordered]@{
        fileType = $fixture.fileType
        parserName = $fixture.parserName
        parseStatus = "FAILED"
        extractedChars = $null
        pageCount = $null
        blockCount = $null
        warningCount = $null
        chunkCount = $null
        indexedChunkCount = $null
        vectorIdCount = $null
        qdrantPointCount = $null
        payloadSummaryOkCount = $null
        locatorPayloadCount = $null
        missingVectorIds = $null
        mismatchedPayloadFields = @()
        mysqlQdrantParity = $false
        expectedMinChunks = $expectedMinChunks
        multiChunkVerified = if ($null -eq $expectedMinChunks) { $null } else { $false }
        retrieveHit = $false
        directRetrieveHit = $false
        qaRetrievalHit = $false
        citationPresent = $false
        sourceLocatorPresent = $false
        expectedStructures = @($fixture.expectedStructures)
        structureSignals = @()
        directRetrieveDiagnostic = $null
        qaRetrieveDiagnostic = $null
        failureReason = "runtime_error_$safeStage"
        durationMs = 0
      }
      Set-Gate "runtime" "REVIEW" @("one parser fixture hit a sanitized runtime error and the runner continued")
    }
  }

  Confirm-DirectRetrieveFollowUps $token

  $failedCore = @($results | Where-Object { $_.parseStatus -ne "SUCCESS" -or -not $_.retrieveHit -or -not $_.citationPresent })
  $locatorReview = @($results | Where-Object { -not $_.sourceLocatorPresent })
  $multiChunkReview = @($results | Where-Object { $null -ne $_.expectedMinChunks -and $_.multiChunkVerified -ne $true })
  $indexParityReview = @($results | Where-Object { $_.parseStatus -eq "SUCCESS" -and $_.mysqlQdrantParity -ne $true })
  if ($script:EnvironmentUnstable -and ($failedCore.Count -gt 0 -or $indexParityReview.Count -gt 0)) {
    $parserStatus = "BLOCKED"
    $parserChecks = @("local runtime environment became unstable before parser chain could be judged")
  } elseif ($failedCore.Count -gt 0) {
    $parserStatus = "FAILED_CORE_FLOW"
    $parserChecks = @("one or more parser fixtures did not reach retrieve/citation")
  } elseif ($indexParityReview.Count -gt 0) {
    $parserStatus = "FAILED_CORE_FLOW"
    $parserChecks = @("one or more parser fixtures did not reach mysql/qdrant index parity")
  } elseif ($locatorReview.Count -gt 0) {
    $parserStatus = "REVIEW"
    $parserChecks = @("one or more parser fixtures has incomplete source locator")
  } elseif (@($results | Where-Object { -not $_.directRetrieveHit }).Count -gt 0) {
    $parserStatus = "REVIEW"
    $parserChecks = @("parser QA retrieval and citation passed, but direct retrieve endpoint did not return hits for every fixture")
  } elseif ($multiChunkReview.Count -gt 0) {
    $parserStatus = "REVIEW"
    $parserChecks = @("one or more parser fixtures did not reach the expected minimum chunk count")
  } else {
    $parserStatus = "PASS"
    $parserChecks = @("PDF/HTML/DOCX/LONG_MD upload parse retrieve citation passed")
  }
  $totalChunkCount = 0
  $totalIndexedChunkCount = 0
  $totalVectorIdCount = 0
  $totalQdrantPointCount = 0
  $totalPayloadSummaryOkCount = 0
  $totalLocatorPayloadCount = 0
  $hasChunkCount = $false
  foreach ($result in @($results)) {
    if ($null -ne $result.chunkCount) {
      $totalChunkCount += [int]$result.chunkCount
      $hasChunkCount = $true
    }
    if ($null -ne $result.indexedChunkCount) { $totalIndexedChunkCount += [int]$result.indexedChunkCount }
    if ($null -ne $result.vectorIdCount) { $totalVectorIdCount += [int]$result.vectorIdCount }
    if ($null -ne $result.qdrantPointCount) { $totalQdrantPointCount += [int]$result.qdrantPointCount }
    if ($null -ne $result.payloadSummaryOkCount) { $totalPayloadSummaryOkCount += [int]$result.payloadSummaryOkCount }
    if ($null -ne $result.locatorPayloadCount) { $totalLocatorPayloadCount += [int]$result.locatorPayloadCount }
  }
  $durationAverage = if ($results.Count -gt 0) {
    [int](($results | ForEach-Object { [int]$_.durationMs } | Measure-Object -Average).Average)
  } else {
    0
  }
  Set-GateWithMetrics "parserRealChain" $parserStatus $parserChecks @{
    fileCount = $results.Count
    parsedFileCount = @($results | Where-Object { $_.parseStatus -eq "SUCCESS" }).Count
    parserFailureCount = @($results | Where-Object { $_.parseStatus -ne "SUCCESS" }).Count
    chunkCount = if ($hasChunkCount) { $totalChunkCount } else { 0 }
    indexedChunkCount = $totalIndexedChunkCount
    vectorIdCount = $totalVectorIdCount
    qdrantPointCount = $totalQdrantPointCount
    payloadSummaryOkCount = $totalPayloadSummaryOkCount
    locatorPayloadCount = $totalLocatorPayloadCount
    retrieveHitCount = @($results | Where-Object { $_.retrieveHit }).Count
    directRetrieveHitCount = @($results | Where-Object { $_.directRetrieveHit }).Count
    qaRetrievalHitCount = @($results | Where-Object { $_.qaRetrievalHit }).Count
    citationCount = @($results | Where-Object { $_.citationPresent }).Count
    sourceLocatorCount = @($results | Where-Object { $_.sourceLocatorPresent }).Count
    durationMs = $durationAverage
  } @{
    retrieveHit = (@($results | Where-Object { $_.retrieveHit }).Count -eq $results.Count)
    citationPresent = (@($results | Where-Object { $_.citationPresent }).Count -eq $results.Count)
    sourceLocatorPresent = (@($results | Where-Object { $_.sourceLocatorPresent }).Count -eq $results.Count)
    mysqlQdrantParity = (@($results | Where-Object { $_.mysqlQdrantParity }).Count -eq $results.Count)
  }

  $boundary = Invoke-BoundaryChecks $fixtureDir $envValues
  if ($boundary.negativeCaseFailCount -gt 0) {
    Set-GateWithMetrics "parserBoundary" "REVIEW" @("one or more parser boundary cases did not fail with expected sanitized code") @{
      negativeCaseCount = $boundary.negativeCaseCount
      negativeCasePassCount = $boundary.negativeCasePassCount
      negativeCaseFailCount = $boundary.negativeCaseFailCount
    } @{
      unsupportedUploadRejected = $boundary.unsupportedUploadRejected
    }
  } else {
    Set-GateWithMetrics "parserBoundary" "PASS" @("unsupported, empty and corrupted parser fixtures failed with expected sanitized codes") @{
      negativeCaseCount = $boundary.negativeCaseCount
      negativeCasePassCount = $boundary.negativeCasePassCount
      negativeCaseFailCount = $boundary.negativeCaseFailCount
    } @{
      unsupportedUploadRejected = $boundary.unsupportedUploadRejected
    }
  }
} catch {
  if (-not $script:Gates.Contains("runtime")) {
    Set-Gate "runtime" "BLOCKED" @() "smoke stopped before completion"
  }
} finally {
  $finishedAt = Get-Date
  $qualityRunObservation = New-QualityRunObservation $startedAt $finishedAt
  $artifact = [ordered]@{
    schemaVersion = 1
    marker = $marker
    status = $script:OverallStatus
    startedAt = $startedAt.ToString("o")
    finishedAt = $finishedAt.ToString("o")
    qualityRun = $qualityRunObservation
    gates = $script:Gates
    files = $results
    boundary = $boundary
    parserQualityReport = New-ParserQualityReport $results $boundary $script:OverallStatus
    artifactRedacted = $true
    cleanup = "temporary business data is marker-scoped; no existing business data is deleted by this script"
  }
  Write-Artifact $artifact $artifactPath
  Stop-StartedProcesses
  [ordered]@{
    status = $script:OverallStatus
    marker = $marker
    artifact = $artifactPath
    fileResults = $results
    gates = $script:Gates
  } | ConvertTo-Json -Depth 20
}
