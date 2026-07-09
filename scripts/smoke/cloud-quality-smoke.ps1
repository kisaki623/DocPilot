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
  [switch]$ReuseRunningServices,
  [switch]$EnableMemoryQualityGate,
  [switch]$EnableRerankHardGate,
  [switch]$EnableRepresentativeCorpusGate,
  [switch]$EnableMultiQueryGate,
  [switch]$EnableRealQaHardGate,
  [switch]$EnableRealQaSemanticGate,
  [switch]$EnableRealProviderFaithfulnessGate,
  [switch]$EnableNaturalCorpusGate,
  [switch]$EnableKnowledgeBaseAgentGate,
  [switch]$EnableFrontendInteractionGate
)

$ErrorActionPreference = "Stop"

$script:StartedProcesses = @()
$script:StartedTunnelPid = $null
$script:Gates = [ordered]@{}
$script:OverallStatus = "PASS"
$script:ArtifactPath = $null
$script:RunArtifactDir = $null
$script:BackendStartCount = 0
$script:FrontendStartCount = 0
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
      Start-Sleep -Seconds ([Math]::Min(20, [Math]::Pow(2, $attempt)))
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
      return ConvertTo-SafeApiFailure $_
    }
    $failure = ConvertTo-SafeApiFailure $_
    if ($Mode -eq "run" -and (-not $ReuseRunningServices) -and [int]$failure.httpStatus -eq 0) {
      if (Recover-BackendForApiFailure) {
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
          if (-not $ok) {
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
          $failure = ConvertTo-SafeApiFailure $_
        }
      }
    }
    throw "api request failed at $method $path status=$($failure.httpStatus) code=$($failure.code) message=$($failure.message)"
  }
}

function Upload-SmokeFile([string]$path, [string]$token) {
  Add-Type -AssemblyName System.Net.Http
  $attempt = 0
  $maxAttempts = 5
  $lastFailure = $null
  while ($attempt -lt $maxAttempts) {
    $attempt++
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
      if ($response.IsSuccessStatusCode -and $parsed.code -eq 0) {
        return $parsed.data
      }
      $lastFailure = "upload failed status=$([int]$response.StatusCode) code=$($parsed.code) message=$($parsed.message)"
      if ([int]$parsed.code -ne 1014 -or $attempt -ge $maxAttempts) {
        throw $lastFailure
      }
    } finally {
      $stream.Dispose()
      $multipart.Dispose()
      $request.Dispose()
      $client.Dispose()
    }
    Start-Sleep -Seconds ([Math]::Min(45, 10 * $attempt))
  }
  throw $lastFailure
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
  $startArgs = @{
    FilePath = "powershell.exe"
    ArgumentList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command)
    WindowStyle = "Hidden"
    PassThru = $true
  }
  if ($script:RunArtifactDir) {
    $script:BackendStartCount++
    $startArgs["RedirectStandardOutput"] = Join-Path $script:RunArtifactDir ("backend-{0}.out.log" -f $script:BackendStartCount)
    $startArgs["RedirectStandardError"] = Join-Path $script:RunArtifactDir ("backend-{0}.err.log" -f $script:BackendStartCount)
  }
  $process = Start-Process @startArgs
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
  $startArgs = @{
    FilePath = "powershell.exe"
    ArgumentList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command)
    WindowStyle = "Hidden"
    PassThru = $true
  }
  if ($script:RunArtifactDir) {
    $script:FrontendStartCount++
    $startArgs["RedirectStandardOutput"] = Join-Path $script:RunArtifactDir ("frontend-{0}.out.log" -f $script:FrontendStartCount)
    $startArgs["RedirectStandardError"] = Join-Path $script:RunArtifactDir ("frontend-{0}.err.log" -f $script:FrontendStartCount)
  }
  $process = Start-Process @startArgs
  $script:StartedProcesses += $process
  if (-not (Wait-FrontendRoute 90)) {
    Stop-WithStatus "BLOCKED" "frontendRoutes" "frontend route did not become reachable within timeout"
  }
}

function Recover-BackendForApiFailure() {
  if (Wait-BackendHealth 3) {
    return $false
  }
  try {
    Start-BackendIfNeeded
    Set-Gate "backendRecovery" "REVIEW" @("local backend restarted after API transport failure")
    return $true
  } catch {
    Set-Gate "backendRecovery" "BLOCKED" @("local backend recovery failed") "backend could not recover after API transport failure"
    return $false
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

function ConvertTo-MysqlStringLiteral([string]$value) {
  if ($null -eq $value) {
    return "NULL"
  }
  $escaped = $value.Replace("\", "\\").Replace("'", "''")
  return "'" + $escaped + "'"
}

function Add-SmokeConversationUserMessages([hashtable]$envValues, [long]$userId, [long]$conversationId, [string[]]$messages) {
  $values = @()
  $sequenceNo = 1
  foreach ($content in $messages) {
    $contentLiteral = ConvertTo-MysqlStringLiteral $content
    $values += "(${conversationId},${userId},'USER',${contentLiteral},${sequenceNo},CHAR_LENGTH(${contentLiteral}),'ACTIVE')"
    $sequenceNo++
  }
  $joinedValues = $values -join ","
  $query = @"
INSERT INTO tb_conversation_message (conversation_id,user_id,role,content,sequence_no,token_count,status)
VALUES ${joinedValues};
UPDATE tb_conversation SET last_message_time = NOW() WHERE id = ${conversationId} AND user_id = ${userId};
"@
  Invoke-MysqlQuery $envValues $query | Out-Null
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
    foreach ($field in @("sectionTitle", "sectionOrdinal", "sectionPath", "sourceBlockOrdinal", "structureType", "qualityFlags")) {
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

function Test-RagItemsContainMarker($items, [string]$marker) {
  if ([string]::IsNullOrWhiteSpace($marker)) {
    return $false
  }
  foreach ($item in @($items)) {
    foreach ($field in @("quoteText", "snippet", "content")) {
      if ($null -ne $item.$field -and ([string]$item.$field).Contains($marker)) {
        return $true
      }
    }
  }
  return $false
}

function Test-TextContainsAll([string]$text, [string[]]$phrases) {
  $resolved = if ($null -eq $text) { "" } else { [string]$text }
  foreach ($phrase in @($phrases)) {
    if ([string]::IsNullOrWhiteSpace($phrase)) {
      continue
    }
    if (-not (Test-TextContainsPhraseGroup $resolved $phrase)) {
      return $false
    }
  }
  return $true
}

function Test-TextContainsAny([string]$text, [string[]]$phrases) {
  $resolved = if ($null -eq $text) { "" } else { [string]$text }
  foreach ($phrase in @($phrases)) {
    if ([string]::IsNullOrWhiteSpace($phrase)) {
      continue
    }
    if (Test-TextContainsPhraseGroup $resolved $phrase) {
      return $true
    }
  }
  return $false
}

function Test-TextContainsPhraseGroup([string]$text, [string]$phraseGroup) {
  $resolved = if ($null -eq $text) { "" } else { [string]$text }
  foreach ($phrase in @(([string]$phraseGroup).Split([char]'|'))) {
    if ([string]::IsNullOrWhiteSpace($phrase)) {
      continue
    }
    if ($resolved.IndexOf($phrase.Trim(), [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
      return $true
    }
  }
  return $false
}

function Get-NaturalDocIds($docIds, [string[]]$keys) {
  $ids = @()
  foreach ($key in @($keys)) {
    if (-not [string]::IsNullOrWhiteSpace($key) -and $docIds.Contains($key)) {
      $ids += [long]$docIds[$key]
    }
  }
  return $ids
}

function Test-DocumentCoverage($items, [long[]]$documentIds) {
  foreach ($documentId in @($documentIds)) {
    if ((Get-DocumentHitCount $items $documentId) -lt 1) {
      return $false
    }
  }
  return $true
}

function Get-DocumentCoverageCounts($items, [long[]]$documentIds) {
  $counts = [ordered]@{}
  foreach ($documentId in @($documentIds)) {
    $counts[[string]$documentId] = Get-DocumentHitCount $items $documentId
  }
  return $counts
}

function Test-RagItemsContainAllPhrases($items, [string[]]$phrases) {
  foreach ($phrase in @($phrases)) {
    if (-not (Test-RagItemsContainMarker $items $phrase)) {
      return $false
    }
  }
  return $true
}

function Invoke-NaturalCorpusCase($case, $corpus, [int]$indexVersion) {
  $topK = if ($case.Contains("topK")) { [int]$case.topK } else { 5 }
  $mode = if ($case.Contains("mode")) { [string]$case.mode } else { "retrieve" }
  $expectedNoEvidence = [bool]$case.noEvidence
  $targetDocIds = Get-NaturalDocIds $corpus.docIds @($case.targetKeys)
  $distractorDocIds = Get-NaturalDocIds $corpus.docIds @($case.distractorKeys)
  $retrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($corpus.knowledgeBaseId)/rag/retrieve" ([ordered]@{ query = $case.question; topK = $topK; indexVersion = $indexVersion }) $corpus.authToken
  $qa = $null
  if ($mode -eq "qa") {
    $qa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($corpus.knowledgeBaseId)/qa/rag" ([ordered]@{ question = $case.question; topK = $topK; indexVersion = $indexVersion }) $corpus.authToken
  }

  $hits = @($retrieve.data.hits)
  $citations = if ($null -ne $qa) { @($qa.data.citations) } else { @() }
  $hitCount = @($hits).Count
  $citationCount = @($citations).Count
  $evidenceItems = if ($mode -eq "qa") { $citations } else { $hits }
  $noEvidenceCorrect = if ($expectedNoEvidence) {
    ([bool]$retrieve.data.noEvidence -and ($null -eq $qa -or [bool]$qa.data.noEvidence))
  } else {
    (-not [bool]$retrieve.data.noEvidence -and ($null -eq $qa -or -not [bool]$qa.data.noEvidence))
  }
  $targetRetrieveCovered = if ($expectedNoEvidence) { $true } else { Test-DocumentCoverage $hits $targetDocIds }
  $targetCitationCovered = if ($expectedNoEvidence -or $mode -ne "qa") { $true } else { Test-DocumentCoverage $citations $targetDocIds }
  $expectedEvidenceSupported = if ($expectedNoEvidence) { $true } else { Test-RagItemsContainAllPhrases $evidenceItems @($case.expectedPhrases) }
  $distractorRetrieveCount = 0
  $distractorCitationCount = 0
  foreach ($documentId in @($distractorDocIds)) {
    $distractorRetrieveCount += Get-DocumentHitCount $hits $documentId
    $distractorCitationCount += Get-DocumentHitCount $citations $documentId
  }
  $forbiddenAnswerHit = $false
  $answerFactExpression = $true
  if ($null -ne $qa) {
    $answer = [string]$qa.data.answer
    $forbiddenAnswerHit = Test-TextContainsAny $answer @($case.forbiddenAnswerPhrases)
    if ($case.Contains("answerAllPhrases")) {
      $answerFactExpression = $answerFactExpression -and (Test-TextContainsAll $answer @($case.answerAllPhrases))
    }
    if ($case.Contains("answerAnyPhrases")) {
      $answerFactExpression = $answerFactExpression -and (Test-TextContainsAny $answer @($case.answerAnyPhrases))
    }
  }

  $failureBuckets = @()
  $reviewBuckets = @()
  $answerFaithfulnessRequired = ($mode -eq "qa" -and ($case.Contains("answerAllPhrases") -or $case.Contains("answerAnyPhrases")))
  if (-not $noEvidenceCorrect) { $failureBuckets += "noEvidence" }
  if (-not $targetRetrieveCovered) { $failureBuckets += "targetRetrieveCoverage" }
  if (-not $targetCitationCovered) { $failureBuckets += "targetCitationCoverage" }
  if (-not $expectedEvidenceSupported) { $failureBuckets += "citationPhraseSupport" }
  if ($mode -eq "qa" -and $distractorCitationCount -gt 0) {
    if ([string]$case.caseType -eq "natural_multi_doc_summary" -and $targetCitationCovered -and $expectedEvidenceSupported) {
      $reviewBuckets += "distractorCitation"
    } else {
      $failureBuckets += "distractorCitation"
    }
  }
  if ($forbiddenAnswerHit) { $failureBuckets += "forbiddenAnswer" }
  if ($answerFaithfulnessRequired -and -not $answerFactExpression) {
    $failureBuckets += "answerFactExpression"
  } elseif (-not $answerFactExpression) {
    $reviewBuckets += "answerFactExpression"
  }

  return [ordered]@{
    caseId = [string]$case.caseId
    caseType = [string]$case.caseType
    mode = $mode
    corpus = [string]$case.corpus
    targetDocumentCount = @($targetDocIds).Count
    distractorDocumentCount = @($distractorDocIds).Count
    retrieveHits = $hitCount
    qaCitations = $citationCount
    retrieveNoEvidence = [bool]$retrieve.data.noEvidence
    qaNoEvidence = if ($null -eq $qa) { $null } else { [bool]$qa.data.noEvidence }
    noEvidenceExpected = $expectedNoEvidence
    noEvidenceCorrect = $noEvidenceCorrect
    targetRetrieveCovered = $targetRetrieveCovered
    targetCitationCovered = $targetCitationCovered
    expectedEvidenceSupported = $expectedEvidenceSupported
    citationPhraseSupport = $expectedEvidenceSupported
    answerFaithfulnessRequired = $answerFaithfulnessRequired
    answerFactExpression = $answerFactExpression
    forbiddenAnswerHit = $forbiddenAnswerHit
    targetRetrieveCounts = Get-DocumentCoverageCounts $hits $targetDocIds
    targetCitationCounts = Get-DocumentCoverageCounts $citations $targetDocIds
    distractorRetrieveCount = $distractorRetrieveCount
    distractorCitationCount = $distractorCitationCount
    retrieveScoreSummary = Get-ScoreSummary $hits
    citationScoreSummary = Get-ScoreSummary $citations
    failureBuckets = $failureBuckets
    reviewBuckets = $reviewBuckets
  }
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

function Get-DocumentHitCount($items, [long]$documentId) {
  return @($items | Where-Object { [long]$_.documentId -eq $documentId }).Count
}

function Get-FirstDocumentRank($items, [long]$documentId) {
  $rank = 1
  foreach ($item in @($items)) {
    if ([long]$item.documentId -eq $documentId) {
      return $rank
    }
    $rank++
  }
  return 0
}

function Test-AnswerGrounding([string]$scope, [string]$answer, [string[]]$expectedMarkers, [string[]]$forbiddenMarkers) {
  $resolvedAnswer = if ($null -eq $answer) { "" } else { [string]$answer }
  $expectedHitCount = 0
  foreach ($marker in @($expectedMarkers)) {
    if (-not [string]::IsNullOrWhiteSpace($marker) -and $resolvedAnswer.Contains($marker)) {
      $expectedHitCount++
    }
  }
  $forbiddenHitCount = 0
  foreach ($marker in @($forbiddenMarkers)) {
    if (-not [string]::IsNullOrWhiteSpace($marker) -and $resolvedAnswer.Contains($marker)) {
      $forbiddenHitCount++
    }
  }
  return [ordered]@{
    scope = $scope
    answerPresent = -not [string]::IsNullOrWhiteSpace($resolvedAnswer)
    answerLength = $resolvedAnswer.Length
    expectedMarkerCount = @($expectedMarkers).Count
    expectedMarkerHits = $expectedHitCount
    expectedMarkersSatisfied = ($expectedHitCount -eq @($expectedMarkers).Count)
    forbiddenMarkerCount = @($forbiddenMarkers).Count
    forbiddenMarkerHits = $forbiddenHitCount
    forbiddenMarkerHit = ($forbiddenHitCount -gt 0)
    citationMarkerPresent = ($resolvedAnswer -match '\[\d+\]')
  }
}

function Test-RealAnswerProvider([string]$scope, $qaResponse) {
  $provider = [string]$qaResponse.data.answerProvider
  $model = [string]$qaResponse.data.answerModel
  $modelCallCount = [int]$qaResponse.data.modelCallCount
  $answer = [string]$qaResponse.data.answer
  return [ordered]@{
    scope = $scope
    answerProvider = $provider
    answerModel = $model
    modelCallCount = $modelCallCount
    answerLength = $answer.Length
    noEvidence = [bool]$qaResponse.data.noEvidence
    passed = ($provider -and $provider -ne "mock" -and $modelCallCount -ge 1 -and $answer.Length -gt 0 -and (-not [bool]$qaResponse.data.noEvidence))
  }
}

function Invoke-FrontendInteractionGate([string]$artifactDir,
                                        [string]$smokeMarker,
                                        [long]$documentId,
                                        [long]$foreignDocumentId,
                                        [string]$token) {
  if ($SkipFrontend) {
    Stop-WithStatus "BLOCKED" "frontendInteraction" "frontend interaction gate cannot run when frontend is skipped"
  }
  $playwrightPath = Join-Path (Get-Location) "frontend/node_modules/playwright"
  if (-not (Test-Path -LiteralPath $playwrightPath)) {
    Stop-WithStatus "BLOCKED" "frontendInteraction" "Playwright dependency is not installed"
  }
  $scriptPath = Join-Path $artifactDir "frontend-interaction-gate.js"
  $script = @'
const { chromium } = require(process.env.DOCPILOT_PLAYWRIGHT_PATH);

const frontend = process.env.DOCPILOT_FRONTEND_BASE.replace(/\/+$/, "");
const marker = process.env.DOCPILOT_SMOKE_MARKER;
const token = process.env.DOCPILOT_UI_TOKEN;
const documentId = process.env.DOCPILOT_UI_DOCUMENT_ID;
const foreignDocumentId = process.env.DOCPILOT_UI_FOREIGN_DOCUMENT_ID;

async function clickButtonByText(page, text) {
  await page.waitForFunction((label) => Array.from(document.querySelectorAll("button"))
    .some((item) => (item.textContent || "").includes(label) && !item.disabled), text, { timeout: 90000 });
  await page.evaluate((label) => {
    const button = Array.from(document.querySelectorAll("button"))
      .find((item) => (item.textContent || "").includes(label) && !item.disabled);
    if (!button) {
      throw new Error(`button not found or disabled: ${label}`);
    }
    button.click();
  }, text);
}

async function run() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript((value) => localStorage.setItem("docpilot_token", value), token);
  const page = await context.newPage();
  const consoleErrors = [];
  let currentPhase = "documentDetail";
  function classifyConsoleError(text) {
    const value = text || "";
    if (value.includes("Failed to load resource")) {
      return "failedResource";
    }
    if (value.includes("Hydration failed") || value.includes("hydration")) {
      return "hydration";
    }
    if (value.includes("TypeError")) {
      return "typeError";
    }
    if (value.includes("ReferenceError")) {
      return "referenceError";
    }
    return "consoleError";
  }
  function summarizeConsoleError(text) {
    const value = text || "";
    const typeErrorMatch = value.match(/TypeError:\s*(Cannot read properties of [^ ]+ \(reading '[^']+'\))/);
    if (typeErrorMatch) {
      return typeErrorMatch[1].replace(/'[^']+'/g, "'<field>'");
    }
    const plainTypeError = value.match(/TypeError:\s*([^\\n]+)/);
    if (plainTypeError) {
      return plainTypeError[1].replace(/https?:\/\/\S+/g, "<url>").slice(0, 120);
    }
    if (value.includes("Failed to load resource")) {
      return "Failed to load resource";
    }
    return classifyConsoleError(value);
  }
  page.on("console", (message) => {
    if (message.type() === "error") {
      consoleErrors.push({
        phase: currentPhase,
        kind: classifyConsoleError(message.text()),
        messageShape: summarizeConsoleError(message.text())
      });
    }
  });

  await page.goto(`${frontend}/documents/${documentId}`, { waitUntil: "domcontentloaded" });
  await page.locator("#qa-question-input").waitFor({ state: "visible", timeout: 90000 });
  await page.locator("#qa-question-input").fill(`What does ALPHA-SHORT-GATE prove for ${marker}?`);
  const retrieveResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/rag/retrieve") && response.request().method() === "POST",
    { timeout: 90000 });
  await clickButtonByText(page, "\u68c0\u7d22\u9884\u89c8");
  const retrieveResponse = await retrieveResponsePromise;
  const retrievePayload = await retrieveResponse.json().catch(() => null);
  const retrieveData = retrievePayload && retrievePayload.data ? retrievePayload.data : {};
  const documentRetrieveStatus = retrieveResponse.status();
  const documentRetrieveHitCount = Array.isArray(retrieveData.hits) ? retrieveData.hits.length : 0;
  const documentRetrieveCitationCount = Array.isArray(retrieveData.citations) ? retrieveData.citations.length : 0;
  const documentRetrieveQuoteHasAlpha = Array.isArray(retrieveData.citations)
    ? retrieveData.citations.some((item) => [item.quoteText, item.snippet].some((value) => (value || "").includes("ALPHA-SHORT-GATE")))
    : false;
  const documentRetrieveHitHasAlpha = Array.isArray(retrieveData.hits)
    ? retrieveData.hits.some((item) => [item.quoteText, item.content].some((value) => (value || "").includes("ALPHA-SHORT-GATE")))
    : false;
  await page.locator("[title=\"\u7cbe\u786e\u5f15\u7528\u539f\u6587\"]", { hasText: "ALPHA-SHORT-GATE" }).first()
    .waitFor({ state: "visible", timeout: 90000 })
    .catch(() => undefined);
  const documentQuoteFirstVisible = await page.locator("[title=\"\u7cbe\u786e\u5f15\u7528\u539f\u6587\"]", { hasText: "ALPHA-SHORT-GATE" }).count() > 0;
  const documentBodyHasAlpha = await page.evaluate(() => document.body.innerText.includes("ALPHA-SHORT-GATE"));

  currentPhase = "knowledgeBase";
  await page.goto(`${frontend}/knowledge-bases`, { waitUntil: "domcontentloaded" });
  await page.locator("button", { hasText: `Short Quality KB ${marker}` }).first()
    .waitFor({ state: "visible", timeout: 90000 });
  await page.locator("button", { hasText: `Short Quality KB ${marker}` }).first().click();
  await page.locator("textarea").last().waitFor({ state: "visible", timeout: 90000 });
  await page.locator("textarea").last().fill(`Summarize both short documents for ${marker}. Include ALPHA-SHORT-GATE and BETA-SHORT-GATE verbatim, and cite both documents.`);
  await clickButtonByText(page, "\u751f\u6210\u56de\u7b54");
  await page.locator("li", { hasText: "ALPHA-SHORT-GATE" }).first().waitFor({ state: "visible", timeout: 120000 });
  await page.locator("li", { hasText: "BETA-SHORT-GATE" }).first().waitFor({ state: "visible", timeout: 120000 });
  const knowledgeBaseAlphaCitationVisible = await page.locator("li", { hasText: "ALPHA-SHORT-GATE" }).count() > 0;
  const knowledgeBaseBetaCitationVisible = await page.locator("li", { hasText: "BETA-SHORT-GATE" }).count() > 0;

  currentPhase = "permissionCheck";
  await page.goto(`${frontend}/documents/${foreignDocumentId}`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => document.body.innerText.includes("\u6587\u6863\u4e0d\u5b58\u5728\u6216\u5f53\u524d\u8d26\u53f7\u65e0\u6743\u8bbf\u95ee"), null, { timeout: 60000 });
  const permissionMessageVisible = await page.evaluate(() => document.body.innerText.includes("\u6587\u6863\u4e0d\u5b58\u5728\u6216\u5f53\u524d\u8d26\u53f7\u65e0\u6743\u8bbf\u95ee"));

  const blockingConsoleErrors = consoleErrors.filter((item) =>
    !(item.phase === "permissionCheck" && item.kind === "failedResource"));

  await browser.close();
  return {
    overallStatus: "PASS",
    checks: {
      documentQuoteFirstVisible,
      documentBodyHasAlpha,
      documentRetrieveStatus,
      documentRetrieveHitCount,
      documentRetrieveCitationCount,
      documentRetrieveQuoteHasAlpha,
      documentRetrieveHitHasAlpha,
      knowledgeBaseAlphaCitationVisible,
      knowledgeBaseBetaCitationVisible,
      permissionMessageVisible,
      consoleErrorCount: consoleErrors.length,
      blockingConsoleErrorCount: blockingConsoleErrors.length,
      consoleErrorSamples: consoleErrors.slice(0, 5)
    }
  };
}

run().then((result) => {
  console.log(JSON.stringify(result));
}).catch((error) => {
  console.log(JSON.stringify({ overallStatus: "FAILED", safeMessage: error.message }));
  process.exit(1);
});
'@
  [System.IO.File]::WriteAllText($scriptPath, $script, [System.Text.UTF8Encoding]::new($false))

  $oldPlaywrightPath = $env:DOCPILOT_PLAYWRIGHT_PATH
  $oldFrontendBase = $env:DOCPILOT_FRONTEND_BASE
  $oldSmokeMarker = $env:DOCPILOT_SMOKE_MARKER
  $oldToken = $env:DOCPILOT_UI_TOKEN
  $oldDocumentId = $env:DOCPILOT_UI_DOCUMENT_ID
  $oldForeignDocumentId = $env:DOCPILOT_UI_FOREIGN_DOCUMENT_ID
  try {
    $env:DOCPILOT_PLAYWRIGHT_PATH = $playwrightPath
    $env:DOCPILOT_FRONTEND_BASE = $FrontendBaseUrl
    $env:DOCPILOT_SMOKE_MARKER = $smokeMarker
    $env:DOCPILOT_UI_TOKEN = $token
    $env:DOCPILOT_UI_DOCUMENT_ID = [string]$documentId
    $env:DOCPILOT_UI_FOREIGN_DOCUMENT_ID = [string]$foreignDocumentId
    $output = & node $scriptPath 2>&1 | Out-String
  } finally {
    $env:DOCPILOT_PLAYWRIGHT_PATH = $oldPlaywrightPath
    $env:DOCPILOT_FRONTEND_BASE = $oldFrontendBase
    $env:DOCPILOT_SMOKE_MARKER = $oldSmokeMarker
    $env:DOCPILOT_UI_TOKEN = $oldToken
    $env:DOCPILOT_UI_DOCUMENT_ID = $oldDocumentId
    $env:DOCPILOT_UI_FOREIGN_DOCUMENT_ID = $oldForeignDocumentId
  }

  $result = $null
  try {
    $result = $output.Trim() | ConvertFrom-Json
  } catch {
    Stop-WithStatus "FAILED_CORE_FLOW" "frontendInteraction" "frontend interaction gate returned non-json output"
  }
  $failedSubGates = @()
  if (-not [bool]$result.checks.documentQuoteFirstVisible) { $failedSubGates += "quoteFirstUi" }
  if (-not [bool]$result.checks.knowledgeBaseAlphaCitationVisible) { $failedSubGates += "knowledgeBaseAlphaCitationUi" }
  if (-not [bool]$result.checks.knowledgeBaseBetaCitationVisible) { $failedSubGates += "knowledgeBaseBetaCitationUi" }
  if (-not [bool]$result.checks.permissionMessageVisible) { $failedSubGates += "permissionUx" }
  $blockingConsoleErrorCount = if ($result.checks.PSObject.Properties.Name -contains "blockingConsoleErrorCount") {
    [int]$result.checks.blockingConsoleErrorCount
  } else {
    [int]$result.checks.consoleErrorCount
  }
  if ($blockingConsoleErrorCount -gt 0) { $failedSubGates += "consoleErrors" }
  $checks = @([ordered]@{
    documentQuoteFirstVisible = [bool]$result.checks.documentQuoteFirstVisible
    documentBodyHasAlpha = [bool]$result.checks.documentBodyHasAlpha
    documentRetrieveStatus = [int]$result.checks.documentRetrieveStatus
    documentRetrieveHitCount = [int]$result.checks.documentRetrieveHitCount
    documentRetrieveCitationCount = [int]$result.checks.documentRetrieveCitationCount
    documentRetrieveQuoteHasAlpha = [bool]$result.checks.documentRetrieveQuoteHasAlpha
    documentRetrieveHitHasAlpha = [bool]$result.checks.documentRetrieveHitHasAlpha
    knowledgeBaseAlphaCitationVisible = [bool]$result.checks.knowledgeBaseAlphaCitationVisible
    knowledgeBaseBetaCitationVisible = [bool]$result.checks.knowledgeBaseBetaCitationVisible
    permissionMessageVisible = [bool]$result.checks.permissionMessageVisible
    consoleErrorCount = [int]$result.checks.consoleErrorCount
    blockingConsoleErrorCount = $blockingConsoleErrorCount
    consoleErrorSamples = @($result.checks.consoleErrorSamples)
    failureBuckets = $failedSubGates
  })
  if ($result.overallStatus -ne "PASS" -or
      -not [bool]$result.checks.documentQuoteFirstVisible -or
      -not [bool]$result.checks.knowledgeBaseAlphaCitationVisible -or
      -not [bool]$result.checks.knowledgeBaseBetaCitationVisible -or
      -not [bool]$result.checks.permissionMessageVisible -or
      $blockingConsoleErrorCount -gt 0) {
    $message = if ($result.safeMessage) { [string]$result.safeMessage } else { "frontend interaction gate failed" }
    if ($failedSubGates.Count -gt 0) {
      $message = "frontend interaction gate failed: " + ($failedSubGates -join ",")
    }
    Set-Gate "frontendInteraction" "FAILED_CORE_FLOW" $checks $message
    Stop-WithStatus "FAILED_CORE_FLOW" "frontendInteraction" $message
  }
  Set-Gate "frontendInteraction" "PASS" $checks
  return $checks
}

function Show-PlanMode() {
  [PSCustomObject][ordered]@{
    mode = "plan"
    summary = "Cloud quality smoke plan only. No env read, no service start, no data creation."
    gates = @(
      "tunnel", "backendHealth", "frontendRoutes", "auth", "uploadParseIndex",
      "chunkQuality", "mysqlQdrantConsistency", "singleDocumentRag",
      "knowledgeBaseRag", "knowledgeBaseAgent(optional)", "shortDocumentRag", "naturalCorpus(optional)", "frontendInteraction(optional)", "multiQueryRag(optional)", "representativeCorpus(optional)", "answerGrounding", "realQaHardGate(optional)", "realQaSemanticGate(optional)", "realProviderFaithfulness(optional)", "noEvidenceThreshold", "rerankHardFixture(optional)", "conversationTrace", "memoryQuality(optional)", "permissionIsolation",
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
  $checks += [ordered]@{ name = "playwrightExists"; pass = (Test-Path -LiteralPath "frontend/node_modules/playwright") }
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
  $script:RunArtifactDir = $artifactDir
  $envValues = Read-EnvFile $EnvFile
  $gitStatusBefore = git status --short
  Set-Gate "gitStatus" "PASS" @("initial git status checked")
  $answerGroundingChecks = @()

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
  $rerankHardResources = $null
  $representativeCorpusResources = $null
  $naturalCorpusResources = $null
  $naturalCorpusGateChecks = $null
  $multiQueryGateChecks = $null
  $realQaHardGateChecks = $null
  $realQaSemanticGateChecks = $null
  $realProviderFaithfulnessChecks = $null
  $frontendInteractionChecks = $null
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
Alpha detail repeat block four. ALPHA-CLOUD-GATE remains unique to this temporary document for this run, and semantic support marker real-claim-support-manager-approval-marker says vendor access renewal requires manager approval before the quarterly review.
Alpha detail repeat block five. The document intentionally contains enough plain text to cross the default chunk window and produce multiple chunks.
Alpha detail repeat block six. The quality gate should not trust a single retrieval response until MySQL chunk rows and Qdrant payload metadata agree; numeric faithfulness marker real-invoice-retention-seven-year-marker says invoice archives are retained for seven years.
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
Beta detail repeat block four. BETA-CONTEXT-GATE remains unique to this temporary document for this run, while semantic distractor marker real-claim-support-schedule-forbidden-marker mentions quarterly review scheduling but does not mention manager approval.
Beta detail repeat block five. The document intentionally contains enough plain text to cross the default chunk window and produce multiple chunks.
Beta detail repeat block six. The KnowledgeBase summary question should make retrieval cover both Alpha and Beta documents instead of only one nearest document, while numeric distractor marker real-invoice-retention-three-year-forbidden-marker says staging cache expires after three years.
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
  $answerGroundingChecks += Test-AnswerGrounding "singleDocumentRag" ([string]$singleQa.data.answer) @("ALPHA-CLOUD-GATE") @("BETA-CONTEXT-GATE", "real-marketing-export-forbidden-marker")
  Set-Gate "singleDocumentRag" "PASS" $singleChecks

  $kb = Invoke-JsonApi "POST" "/api/knowledge-bases" ([ordered]@{ name = "Cloud Quality KB $smokeMarker"; description = "temporary smoke kb" }) $tokenA
  $addKb = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/documents" ([ordered]@{ documentIds = @($docA.data.id, $docB.data.id) }) $tokenA
  $kbQuestion = "Summarize both documents for $smokeMarker. Include these exact evidence markers verbatim in the answer: ALPHA-CLOUD-GATE, BETA-CONTEXT-GATE. Cite the evidence."
  $kbRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" ([ordered]@{ query = $kbQuestion; topK = 6; indexVersion = $IndexVersion }) $tokenA
  $kbQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/qa/rag" ([ordered]@{ question = $kbQuestion; topK = 6; indexVersion = $IndexVersion }) $tokenA
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
  $answerGroundingChecks += Test-AnswerGrounding "knowledgeBaseRag" ([string]$kbQa.data.answer) @("ALPHA-CLOUD-GATE", "BETA-CONTEXT-GATE") @("real-marketing-export-forbidden-marker")
  Set-Gate "knowledgeBaseRag" "PASS" $kbChecks

  $knowledgeBaseAgentChecks = $null
  if ($EnableKnowledgeBaseAgentGate) {
    $kbAgentQuestion = "Search evidence chunks and list sources for ALPHA-CLOUD-GATE and BETA-CONTEXT-GATE in $smokeMarker."
    $kbAgent = Invoke-JsonApi "POST" "/api/ai/agent/knowledge-bases/$($kb.data.id)/run" ([ordered]@{
        task = $kbAgentQuestion
        topK = 6
        indexVersion = $IndexVersion
      }) $tokenA
    $kbAgentUnsupported = Invoke-JsonApi "POST" "/api/ai/agent/knowledge-bases/$($kb.data.id)/run" ([ordered]@{
        task = "Summarize and answer both documents for this temporary knowledge base."
        topK = 3
        indexVersion = $IndexVersion
      }) $tokenA
    $kbAgentForeign = Invoke-JsonApi "POST" "/api/ai/agent/knowledge-bases/$($kb.data.id)/run" ([ordered]@{
        task = "Search evidence chunks for a foreign knowledge base."
        topK = 3
        indexVersion = $IndexVersion
      }) $tokenB -AllowFailure

    $kbAgentHitCounts = $kbAgent.data.documentHitCounts
    $kbAgentToolNames = @($kbAgent.data.steps | ForEach-Object { [string]$_.toolName })
    $kbAgentRetrieveHits = @($kbAgent.data.retrievalHits).Count
    $kbAgentCitations = @($kbAgent.data.citations).Count
    $kbAgentCoversAlpha = (Get-CountValue $kbAgentHitCounts ([string]$docA.data.id)) -ge 1
    $kbAgentCoversBeta = (Get-CountValue $kbAgentHitCounts ([string]$docB.data.id)) -ge 1
    $kbAgentUsedSearchTool = $kbAgentToolNames -contains "knowledge_base_search_tool"
    $kbAgentUnsupportedRejected = (-not [bool]$kbAgentUnsupported.data.success) -and @($kbAgentUnsupported.data.steps).Count -eq 0
    $kbAgentForeignRejected = -not [bool]$kbAgentForeign.ok
    $knowledgeBaseAgentChecks = @([ordered]@{
      success = [bool]$kbAgent.data.success
      decision = [string]$kbAgent.data.decision
      selectedTools = $kbAgentToolNames
      retrieveHits = $kbAgentRetrieveHits
      citations = $kbAgentCitations
      documentHitCounts = $kbAgentHitCounts
      coversBothDocuments = ($kbAgentCoversAlpha -and $kbAgentCoversBeta)
      unsupportedIntentRejected = $kbAgentUnsupportedRejected
      foreignKnowledgeBaseRejected = $kbAgentForeignRejected
      retrievalMode = [string]$kbAgent.data.retrievalMode
      rerankApplied = [bool]$kbAgent.data.rerankApplied
      multiQueryApplied = [bool]$kbAgent.data.multiQueryApplied
      queryVariantCount = [int]$kbAgent.data.queryVariantCount
      durationMs = [long]$kbAgent.data.totalDurationMs
    })
    if (-not [bool]$kbAgent.data.success -or [string]$kbAgent.data.decision -ne "search_tool" -or -not $kbAgentUsedSearchTool -or $kbAgentRetrieveHits -lt 2 -or $kbAgentCitations -lt 2 -or -not $kbAgentCoversAlpha -or -not $kbAgentCoversBeta) {
      Set-Gate "knowledgeBaseAgent" "FAILED_CORE_FLOW" $knowledgeBaseAgentChecks "knowledge base agent search route did not return expected evidence"
      Stop-WithStatus "FAILED_CORE_FLOW" "knowledgeBaseAgent" "knowledge base agent search route did not return expected evidence"
    }
    if (-not $kbAgentUnsupportedRejected) {
      Set-Gate "knowledgeBaseAgent" "FAILED_CORE_FLOW" $knowledgeBaseAgentChecks "knowledge base agent P0 unsupported intent boundary regressed"
      Stop-WithStatus "FAILED_CORE_FLOW" "knowledgeBaseAgent" "knowledge base agent P0 unsupported intent boundary regressed"
    }
    if (-not $kbAgentForeignRejected) {
      Set-Gate "knowledgeBaseAgent" "FAILED_SECURITY_GATE" $knowledgeBaseAgentChecks "knowledge base agent permission isolation regressed"
      Stop-WithStatus "FAILED_SECURITY_GATE" "knowledgeBaseAgent" "knowledge base agent permission isolation regressed"
    }
    Set-Gate "knowledgeBaseAgent" "PASS" $knowledgeBaseAgentChecks
  }

  $zhShortSentence = -join ([char[]](0x4E2D, 0x6587, 0x77ED, 0x53E5))
  $shortAlphaText = @"
$smokeMarker
Short Alpha note. ALPHA-SHORT-GATE proves a small txt document can still return grounded RAG evidence.
ZH-SHORT-GATE $zhShortSentence citation path for short document retrieval.
Numeric short fact. NUMERIC-SHORT-SEVEN-DAY-GATE says the review window is 7 days.
Similar short policy category: onboarding evidence.
"@
  $shortBetaText = @"
$smokeMarker
Short Beta note. BETA-SHORT-GATE proves a second small txt document can join KnowledgeBase summary evidence.
ZH-BETA-SHORT-GATE $zhShortSentence citation path for the second short document.
Numeric short fact. NUMERIC-SHORT-NINE-DAY-GATE says the review window is 9 days.
Similar short policy category: onboarding evidence.
"@
  $shortAlphaPath = Join-Path $artifactDir "short-alpha.txt"
  $shortBetaPath = Join-Path $artifactDir "short-beta.txt"
  [System.IO.File]::WriteAllText($shortAlphaPath, $shortAlphaText, [System.Text.UTF8Encoding]::new($false))
  [System.IO.File]::WriteAllText($shortBetaPath, $shortBetaText, [System.Text.UTF8Encoding]::new($false))
  $shortFileA = Upload-SmokeFile $shortAlphaPath $tokenB
  $shortFileB = Upload-SmokeFile $shortBetaPath $tokenB
  $shortDocA = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $shortFileA.id }) $tokenB
  $shortDocB = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $shortFileB.id }) $tokenB
  Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $shortDocA.data.id }) $tokenB | Out-Null
  Wait-ParseSuccess ([long]$shortDocA.data.id) $tokenB | Out-Null
  $shortChunksA = Wait-IndexedChunks $envValues $userBId ([long]$shortDocA.data.id)
  Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $shortDocB.data.id }) $tokenB | Out-Null
  Wait-ParseSuccess ([long]$shortDocB.data.id) $tokenB | Out-Null
  $shortChunksB = Wait-IndexedChunks $envValues $userBId ([long]$shortDocB.data.id)
  $shortSingleRetrieve = Invoke-JsonApi "POST" "/api/rag/retrieve" ([ordered]@{ documentId = $shortDocA.data.id; query = "What does ALPHA-SHORT-GATE prove for $smokeMarker?"; topK = 3; indexVersion = $IndexVersion }) $tokenB
  $shortSingleQa = Invoke-JsonApi "POST" "/api/documents/$($shortDocA.data.id)/qa/rag" ([ordered]@{ question = "Explain ALPHA-SHORT-GATE for $smokeMarker and cite the short Alpha document."; topK = 3; indexVersion = $IndexVersion }) $tokenB
  $shortKb = Invoke-JsonApi "POST" "/api/knowledge-bases" ([ordered]@{ name = "Short Quality KB $smokeMarker"; description = "temporary short document regression kb" }) $tokenB
  Invoke-JsonApi "POST" "/api/knowledge-bases/$($shortKb.data.id)/documents" ([ordered]@{ documentIds = @($shortDocA.data.id, $shortDocB.data.id) }) $tokenB | Out-Null
  $shortKbQuestion = "Summarize both short documents for $smokeMarker. Include ALPHA-SHORT-GATE and BETA-SHORT-GATE verbatim, and cite both documents."
  $shortKbRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($shortKb.data.id)/rag/retrieve" ([ordered]@{ query = $shortKbQuestion; topK = 4; indexVersion = $IndexVersion }) $tokenB
  $shortKbQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($shortKb.data.id)/qa/rag" ([ordered]@{ question = $shortKbQuestion; topK = 4; indexVersion = $IndexVersion }) $tokenB
  $shortHitCounts = $shortKbRetrieve.data.documentHitCounts
  $shortSingleEvidenceOk = ((-not $shortSingleRetrieve.data.noEvidence) -and @($shortSingleRetrieve.data.hits).Count -ge 1 -and @($shortSingleQa.data.citations).Count -ge 1)
  $shortSingleAlphaRetrieveOk = Test-RagItemsContainMarker $shortSingleRetrieve.data.hits "ALPHA-SHORT-GATE"
  $shortSingleAlphaCitationOk = Test-RagItemsContainMarker $shortSingleQa.data.citations "ALPHA-SHORT-GATE"
  $shortSingleChineseRetrieveOk = Test-RagItemsContainMarker $shortSingleRetrieve.data.hits "ZH-SHORT-GATE"
  $shortSingleNumericRetrieveOk = Test-RagItemsContainMarker $shortSingleRetrieve.data.hits "NUMERIC-SHORT-SEVEN-DAY-GATE"
  $shortKbEvidenceOk = (@($shortKbRetrieve.data.hits).Count -ge 2 -and @($shortKbQa.data.citations).Count -ge 2)
  $shortKbDocumentCoverageOk = ((Get-CountValue $shortHitCounts ([string]$shortDocA.data.id)) -ge 1 -and (Get-CountValue $shortHitCounts ([string]$shortDocB.data.id)) -ge 1)
  $shortKbAlphaCitationOk = Test-RagItemsContainMarker $shortKbQa.data.citations "ALPHA-SHORT-GATE"
  $shortKbBetaCitationOk = Test-RagItemsContainMarker $shortKbQa.data.citations "BETA-SHORT-GATE"
  $shortKbChineseRetrieveOk = (Test-RagItemsContainMarker $shortKbRetrieve.data.hits "ZH-SHORT-GATE") -and (Test-RagItemsContainMarker $shortKbRetrieve.data.hits "ZH-BETA-SHORT-GATE")
  $shortKbNumericRetrieveOk = (Test-RagItemsContainMarker $shortKbRetrieve.data.hits "NUMERIC-SHORT-SEVEN-DAY-GATE") -and (Test-RagItemsContainMarker $shortKbRetrieve.data.hits "NUMERIC-SHORT-NINE-DAY-GATE")
  $shortKbSimilarInterferenceOk = $shortKbDocumentCoverageOk -and $shortKbAlphaCitationOk -and $shortKbBetaCitationOk
  $shortFailedBuckets = @()
  if (-not $shortSingleEvidenceOk) { $shortFailedBuckets += "singleDocumentEvidence" }
  if (-not $shortSingleAlphaRetrieveOk) { $shortFailedBuckets += "singleDocumentRetrieveMarker" }
  if (-not $shortSingleAlphaCitationOk) { $shortFailedBuckets += "singleDocumentCitationMarker" }
  if (-not $shortSingleChineseRetrieveOk) { $shortFailedBuckets += "singleDocumentChineseShortRetrieve" }
  if (-not $shortSingleNumericRetrieveOk) { $shortFailedBuckets += "singleDocumentNumericShortRetrieve" }
  if (-not $shortKbEvidenceOk) { $shortFailedBuckets += "knowledgeBaseEvidence" }
  if (-not $shortKbDocumentCoverageOk) { $shortFailedBuckets += "knowledgeBaseDocumentCoverage" }
  if (-not $shortKbAlphaCitationOk) { $shortFailedBuckets += "knowledgeBaseAlphaCitation" }
  if (-not $shortKbBetaCitationOk) { $shortFailedBuckets += "knowledgeBaseBetaCitation" }
  if (-not $shortKbChineseRetrieveOk) { $shortFailedBuckets += "knowledgeBaseChineseShortRetrieve" }
  if (-not $shortKbNumericRetrieveOk) { $shortFailedBuckets += "knowledgeBaseNumericShortRetrieve" }
  if (-not $shortKbSimilarInterferenceOk) { $shortFailedBuckets += "knowledgeBaseSimilarShortInterference" }
  $shortChecks = @([ordered]@{
    shortAlphaChunkCount = @($shortChunksA).Count
    shortBetaChunkCount = @($shortChunksB).Count
    singleRetrieveHits = @($shortSingleRetrieve.data.hits).Count
    singleQaCitations = @($shortSingleQa.data.citations).Count
    kbRetrieveHits = @($shortKbRetrieve.data.hits).Count
    kbQaCitations = @($shortKbQa.data.citations).Count
    documentHitCounts = $shortHitCounts
    singleDocumentEvidence = $shortSingleEvidenceOk
    singleDocumentRetrieveMarker = $shortSingleAlphaRetrieveOk
    singleDocumentCitationMarker = $shortSingleAlphaCitationOk
    singleDocumentChineseShortRetrieve = $shortSingleChineseRetrieveOk
    singleDocumentNumericShortRetrieve = $shortSingleNumericRetrieveOk
    knowledgeBaseEvidence = $shortKbEvidenceOk
    knowledgeBaseDocumentCoverage = $shortKbDocumentCoverageOk
    knowledgeBaseAlphaCitation = $shortKbAlphaCitationOk
    knowledgeBaseBetaCitation = $shortKbBetaCitationOk
    knowledgeBaseChineseShortRetrieve = $shortKbChineseRetrieveOk
    knowledgeBaseNumericShortRetrieve = $shortKbNumericRetrieveOk
    knowledgeBaseSimilarShortInterference = $shortKbSimilarInterferenceOk
    failureBuckets = $shortFailedBuckets
    singleRetrieveScoreSummary = Get-ScoreSummary $shortSingleRetrieve.data.hits
    kbRetrieveScoreSummary = Get-ScoreSummary $shortKbRetrieve.data.hits
    qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
  })
  if ($shortFailedBuckets.Count -gt 0) {
    $shortFailureMessage = "short document RAG regression failed: " + ($shortFailedBuckets -join ",")
    Set-Gate "shortDocumentRag" "FAILED_CORE_FLOW" $shortChecks $shortFailureMessage
    Stop-WithStatus "FAILED_CORE_FLOW" "shortDocumentRag" $shortFailureMessage
  }
  $answerGroundingChecks += Test-AnswerGrounding "shortDocumentRag" ([string]$shortSingleQa.data.answer) @("ALPHA-SHORT-GATE") @("BETA-SHORT-GATE", "real-marketing-export-forbidden-marker")
  $answerGroundingChecks += Test-AnswerGrounding "shortKnowledgeBaseRag" ([string]$shortKbQa.data.answer) @("ALPHA-SHORT-GATE", "BETA-SHORT-GATE") @("real-marketing-export-forbidden-marker")
  Set-Gate "shortDocumentRag" "PASS" $shortChecks

  if ($EnableNaturalCorpusGate) {
    $naturalCorpusDefinitions = @(
      [ordered]@{
        key = "finance"
        userAlias = "fin"
        nickname = "Finance Corpus"
        docs = @(
          [ordered]@{
            key = "expense"
            fileName = "natural-finance-expense-policy.txt"
            text = @"
# Expense Policy Note

$smokeMarker
Finance operations policy for routine reimbursements.
Expense reports must be submitted within 7 days after the trip ends.
Team manager approval is required before Finance releases reimbursement.
Receipts should be attached to each line item so the reviewer can match the claim to the travel request.
"@
          },
          [ordered]@{
            key = "invoice"
            fileName = "natural-finance-invoice-retention.txt"
            text = @"
# Invoice Retention Policy

$smokeMarker
Finance keeps invoice archive records for external audit review.
Invoice archive retention is 7 years.
The archive owner is Finance Operations, not Marketing.
Retention exceptions require written approval from the controller.
"@
          },
          [ordered]@{
            key = "marketing"
            fileName = "natural-finance-marketing-draft.txt"
            text = @"
# Marketing Draft Retention

$smokeMarker
Marketing campaign drafts are retained for 3 years.
This document is about campaign copy review and should not be used as invoice archive evidence.
Campaign draft owners may delete rejected ideas after launch review.
"@
          },
          [ordered]@{
            key = "procurement"
            fileName = "natural-finance-procurement-access.txt"
            text = @"
# Vendor Access Renewal

$smokeMarker
Vendor access renewal requires manager approval before the quarterly review.
The procurement coordinator must attach the renewal ticket to the supplier record.
Temporary vendor accounts expire after 14 days unless the manager renews access.
"@
          }
        )
      },
      [ordered]@{
        key = "ops"
        userAlias = "ops"
        nickname = "Operations Corpus"
        docs = @(
          [ordered]@{
            key = "incident"
            fileName = "natural-ops-incident-review.txt"
            text = @"
# Checkout Incident Review

$smokeMarker
The checkout worker queue created delayed confirmations during the afternoon incident.
Engineers paused background retries and drained the queue before reopening checkout traffic.
Support was told to send a short customer update after the incident commander confirmed recovery.
"@
          },
          [ordered]@{
            key = "support"
            fileName = "natural-ops-support-sla.txt"
            text = @"
# Support SLA Note

$smokeMarker
Customer support classifies payment outage reports as P1 when checkout cannot complete.
The P1 response target is 30 minutes.
The P2 response target is 4 hours when the customer has a workaround.
Incident summaries should mention the customer communication owner.
"@
          },
          [ordered]@{
            key = "backup"
            fileName = "natural-ops-backup-runbook.txt"
            text = @"
# Database Backup Runbook

$smokeMarker
Database backup verification runs every 14 days.
The restore drill owner is the SRE on-call lead.
Failed verification must open an incident ticket before the next business day.
"@
          },
          [ordered]@{
            key = "rollback"
            fileName = "natural-ops-rollback-runbook.txt"
            text = @"
# Feature Rollback Runbook

$smokeMarker
The release captain can trigger feature flag rollback within 15 minutes.
Rollback approval is not required during an active Sev1 checkout incident.
The release notes must mention the flag name and the rollback window.
"@
          }
        )
      },
      [ordered]@{
        key = "governance"
        userAlias = "gov"
        nickname = "Governance Corpus"
        docs = @(
          [ordered]@{
            key = "contract"
            fileName = "natural-governance-contract-renewal.txt"
            text = @"
# Enterprise Contract Renewal

$smokeMarker
Enterprise contract renewal notice must be sent 60 days before the current term ends.
Legal review is required before the renewal package is sent to the customer.
Commercial discount approval belongs to the account director.
"@
          },
          [ordered]@{
            key = "audit"
            fileName = "natural-governance-access-review.txt"
            text = @"
# Access Review Calendar

$smokeMarker
Quarterly access review evidence is due on the last business day of each quarter.
Security Operations owns the review evidence package.
Department managers must confirm privileged accounts before the review is closed.
"@
          },
          [ordered]@{
            key = "customer"
            fileName = "natural-governance-customer-communication.txt"
            text = @"
# Customer Incident Communication

$smokeMarker
The final customer incident update must be sent within 2 hours after recovery is confirmed.
The customer communication owner is Support Operations.
Draft updates should not include internal incident commander notes.
"@
          },
          [ordered]@{
            key = "version"
            fileName = "natural-governance-version-retention.txt"
            text = @"
# Release Note Retention

$smokeMarker
Minor version release notes are retained for 18 months.
Critical hotfix changelog records are retained for 5 years.
Engineering Operations owns the release archive.
"@
          }
        )
      }
    )

    $naturalCorpora = [ordered]@{}
    foreach ($corpusDefinition in $naturalCorpusDefinitions) {
      $password = "SmokeNatural!" + ([Guid]::NewGuid().ToString("N").Substring(0, 12))
      $username = "smk$($corpusDefinition.userAlias)$shortUserSuffix"
      $registered = Invoke-JsonApi "POST" "/api/auth/register" ([ordered]@{ username = $username; password = $password; nickname = $corpusDefinition.nickname })
      $token = [string]$registered.data.token
      $userId = [long]$registered.data.userId
      $docIds = [ordered]@{}
      $parseTaskIds = @()
      foreach ($naturalDoc in @($corpusDefinition.docs)) {
        $naturalPath = Join-Path $artifactDir $naturalDoc.fileName
        [System.IO.File]::WriteAllText($naturalPath, $naturalDoc.text, [System.Text.UTF8Encoding]::new($false))
        $naturalFile = Upload-SmokeFile $naturalPath $token
        $naturalCreated = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $naturalFile.id }) $token
        $naturalTask = Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $naturalCreated.data.id }) $token
        Wait-ParseSuccess ([long]$naturalCreated.data.id) $token | Out-Null
        Wait-IndexedChunks $envValues $userId ([long]$naturalCreated.data.id) | Out-Null
        $docIds[$naturalDoc.key] = [long]$naturalCreated.data.id
        $parseTaskIds += [long]$naturalTask.data.taskId
      }
      $naturalKb = Invoke-JsonApi "POST" "/api/knowledge-bases" ([ordered]@{ name = "Natural $($corpusDefinition.nickname) $smokeMarker"; description = "temporary natural corpus audit kb" }) $token
      Invoke-JsonApi "POST" "/api/knowledge-bases/$($naturalKb.data.id)/documents" ([ordered]@{ documentIds = @($docIds.Values) }) $token | Out-Null
      $naturalCorpora[$corpusDefinition.key] = [ordered]@{
        userId = $userId
        authToken = $token
        knowledgeBaseId = [long]$naturalKb.data.id
        docIds = $docIds
        parseTaskIds = $parseTaskIds
      }
    }

    $naturalCases = @(
      [ordered]@{ caseId = "finance-expense-approval"; corpus = "finance"; caseType = "natural_single_doc_fact"; mode = "qa"; question = "How quickly must expense reports be submitted, and who approves reimbursement?"; targetKeys = @("expense"); distractorKeys = @("invoice", "marketing"); expectedPhrases = @("Expense reports must be submitted within 7 days", "Team manager approval"); answerAnyPhrases = @("7 days|within 7 days|seven days|within seven days"); answerAllPhrases = @("manager|team manager|manager approval|manager approves"); topK = 4 },
      [ordered]@{ caseId = "finance-invoice-retention"; corpus = "finance"; caseType = "natural_numeric_fact"; mode = "qa"; question = "How long are invoice archive records retained?"; targetKeys = @("invoice"); distractorKeys = @("marketing"); expectedPhrases = @("Invoice archive retention is 7 years"); answerAnyPhrases = @("7 years|seven years|7-year|seven-year"); forbiddenAnswerPhrases = @("3 years|three years|3-year|three-year"); topK = 4 },
      [ordered]@{ caseId = "finance-invoice-distractor-control"; corpus = "finance"; caseType = "natural_distractor_control"; mode = "qa"; question = "Which policy states invoice archive retention, and what retention period should be used?"; targetKeys = @("invoice"); distractorKeys = @("marketing"); expectedPhrases = @("Invoice archive retention is 7 years"); answerAnyPhrases = @("7 years|seven years|7-year|seven-year"); forbiddenAnswerPhrases = @("3 years|three years|3-year|three-year"); topK = 5 },
      [ordered]@{ caseId = "finance-procurement-approval-chain"; corpus = "finance"; caseType = "natural_approval_chain"; mode = "retrieve"; question = "What approval is required before vendor access renewal reaches quarterly review?"; targetKeys = @("procurement"); distractorKeys = @("expense"); expectedPhrases = @("Vendor access renewal requires manager approval before the quarterly review"); topK = 4 },
      [ordered]@{ caseId = "finance-payroll-no-evidence"; corpus = "finance"; caseType = "natural_no_evidence"; mode = "qa"; question = "What is the payroll payment date for contractors?"; targetKeys = @(); distractorKeys = @("expense", "invoice", "marketing", "procurement"); expectedPhrases = @(); noEvidence = $true; topK = 3 },
      [ordered]@{ caseId = "finance-marketing-draft-retention"; corpus = "finance"; caseType = "natural_numeric_fact"; mode = "retrieve"; question = "How long are marketing campaign drafts retained?"; targetKeys = @("marketing"); distractorKeys = @("invoice"); expectedPhrases = @("Marketing campaign drafts are retained for 3 years"); topK = 4 },
      [ordered]@{ caseId = "finance-expense-invoice-compare"; corpus = "finance"; caseType = "natural_multi_doc_summary"; mode = "qa"; question = "Compare the reimbursement approval rule with the invoice archive retention rule."; targetKeys = @("expense", "invoice"); distractorKeys = @("marketing"); expectedPhrases = @("Team manager approval", "Invoice archive retention is 7 years"); answerAnyPhrases = @("7 years|seven years|7-year|seven-year"); answerAllPhrases = @("manager|team manager|manager approval|manager approves"); topK = 6 },
      [ordered]@{ caseId = "finance-vendor-temporary-access"; corpus = "finance"; caseType = "natural_date_fact"; mode = "retrieve"; question = "When do temporary vendor accounts expire if access is not renewed?"; targetKeys = @("procurement"); distractorKeys = @("expense"); expectedPhrases = @("Temporary vendor accounts expire after 14 days"); topK = 4 },
      [ordered]@{ caseId = "ops-incident-support-summary"; corpus = "ops"; caseType = "natural_multi_doc_summary"; mode = "qa"; question = "Summarize the checkout worker queue incident response and the P1 response target from customer support SLA."; targetKeys = @("incident", "support"); distractorKeys = @("backup", "rollback"); expectedPhrases = @("paused background retries", "The P1 response target is 30 minutes"); answerAnyPhrases = @("30 minutes|thirty minutes|P1"); answerAllPhrases = @("checkout"); topK = 6 },
      [ordered]@{ caseId = "ops-support-p1-target"; corpus = "ops"; caseType = "natural_numeric_fact"; mode = "qa"; question = "What is the P1 response target for checkout payment outages?"; targetKeys = @("support"); distractorKeys = @("backup", "rollback"); expectedPhrases = @("The P1 response target is 30 minutes"); answerAnyPhrases = @("30 minutes|thirty minutes"); topK = 4 },
      [ordered]@{ caseId = "ops-backup-interval"; corpus = "ops"; caseType = "natural_date_fact"; mode = "retrieve"; question = "How often does database backup verification run?"; targetKeys = @("backup"); distractorKeys = @("support"); expectedPhrases = @("Database backup verification runs every 14 days"); topK = 4 },
      [ordered]@{ caseId = "ops-rollback-owner"; corpus = "ops"; caseType = "natural_approval_chain"; mode = "retrieve"; question = "Who can trigger feature flag rollback and how quickly can it happen?"; targetKeys = @("rollback"); distractorKeys = @("incident"); expectedPhrases = @("The release captain can trigger feature flag rollback within 15 minutes"); topK = 4 },
      [ordered]@{ caseId = "ops-wifi-no-evidence"; corpus = "ops"; caseType = "natural_no_evidence"; mode = "qa"; question = "Which office Wi-Fi network should visitors use during checkout incidents?"; targetKeys = @(); distractorKeys = @("incident", "support", "backup", "rollback"); expectedPhrases = @(); noEvidence = $true; topK = 3 },
      [ordered]@{ caseId = "ops-backup-rollback-compare"; corpus = "ops"; caseType = "natural_multi_doc_summary"; mode = "qa"; question = "Compare backup verification ownership with feature rollback authority."; targetKeys = @("backup", "rollback"); distractorKeys = @("support"); expectedPhrases = @("restore drill owner is the SRE on-call lead", "release captain can trigger feature flag rollback"); answerAnyPhrases = @("SRE|on-call lead|release captain"); topK = 6 },
      [ordered]@{ caseId = "ops-p2-workaround"; corpus = "ops"; caseType = "natural_numeric_fact"; mode = "retrieve"; question = "What is the response target when a customer has a workaround?"; targetKeys = @("support"); distractorKeys = @("incident"); expectedPhrases = @("The P2 response target is 4 hours"); topK = 4 },
      [ordered]@{ caseId = "ops-incident-customer-update"; corpus = "ops"; caseType = "natural_single_doc_fact"; mode = "retrieve"; question = "What update should Support send after the incident commander confirms recovery?"; targetKeys = @("incident"); distractorKeys = @("support"); expectedPhrases = @("Support was told to send a short customer update"); topK = 4 },
      [ordered]@{ caseId = "ops-rollback-no-approval"; corpus = "ops"; caseType = "natural_negative_fact"; mode = "retrieve"; question = "Does active Sev1 checkout rollback require approval?"; targetKeys = @("rollback"); distractorKeys = @("backup"); expectedPhrases = @("Rollback approval is not required during an active Sev1 checkout incident"); topK = 4 },
      [ordered]@{ caseId = "governance-contract-notice"; corpus = "governance"; caseType = "natural_date_fact"; mode = "qa"; question = "When must enterprise contract renewal notice be sent, and what review is required?"; targetKeys = @("contract"); distractorKeys = @("audit"); expectedPhrases = @("60 days before the current term ends", "Legal review is required"); answerAnyPhrases = @("60 days|sixty days"); answerAllPhrases = @("Legal|legal review"); topK = 4 },
      [ordered]@{ caseId = "governance-audit-deadline"; corpus = "governance"; caseType = "natural_date_fact"; mode = "retrieve"; question = "When is quarterly access review evidence due?"; targetKeys = @("audit"); distractorKeys = @("contract"); expectedPhrases = @("last business day of each quarter"); topK = 4 },
      [ordered]@{ caseId = "governance-customer-final-update"; corpus = "governance"; caseType = "natural_single_doc_fact"; mode = "qa"; question = "When must the final customer incident update be sent after recovery?"; targetKeys = @("customer"); distractorKeys = @("version"); expectedPhrases = @("within 2 hours after recovery is confirmed"); answerAnyPhrases = @("2 hours|two hours"); topK = 4 },
      [ordered]@{ caseId = "governance-version-retention"; corpus = "governance"; caseType = "natural_numeric_fact"; mode = "qa"; question = "How long are minor version release notes retained?"; targetKeys = @("version"); distractorKeys = @("customer"); expectedPhrases = @("Minor version release notes are retained for 18 months"); answerAnyPhrases = @("18 months|eighteen months|18-month|eighteen-month"); forbiddenAnswerPhrases = @("5 years|five years|5-year|five-year"); topK = 4 },
      [ordered]@{ caseId = "governance-pricing-no-evidence"; corpus = "governance"; caseType = "natural_no_evidence"; mode = "qa"; question = "What is the enterprise pricing discount ladder for new customers?"; targetKeys = @(); distractorKeys = @("contract", "audit", "customer", "version"); expectedPhrases = @(); noEvidence = $true; topK = 3 },
      [ordered]@{ caseId = "governance-contract-audit-compare"; corpus = "governance"; caseType = "natural_multi_doc_summary"; mode = "retrieve"; question = "Compare contract renewal notice timing with access review evidence timing."; targetKeys = @("contract", "audit"); distractorKeys = @("version"); expectedPhrases = @("60 days before the current term ends", "last business day of each quarter"); topK = 6 },
      [ordered]@{ caseId = "governance-hotfix-retention"; corpus = "governance"; caseType = "natural_numeric_fact"; mode = "qa"; question = "How long are critical hotfix changelog records retained?"; targetKeys = @("version"); distractorKeys = @("contract"); expectedPhrases = @("Critical hotfix changelog records are retained for 5 years"); answerAnyPhrases = @("5 years|five years|5-year|five-year"); forbiddenAnswerPhrases = @("18 months|eighteen months|18-month|eighteen-month"); topK = 4 },
      [ordered]@{ caseId = "governance-customer-owner"; corpus = "governance"; caseType = "natural_approval_chain"; mode = "retrieve"; question = "Who owns customer incident communication updates?"; targetKeys = @("customer"); distractorKeys = @("audit"); expectedPhrases = @("customer communication owner is Support Operations"); topK = 4 }
    )

    $naturalCaseResults = @()
    foreach ($naturalCase in $naturalCases) {
      $naturalCaseResults += Invoke-NaturalCorpusCase $naturalCase $naturalCorpora[$naturalCase.corpus] $IndexVersion
    }

    $opsCorpus = $naturalCorpora["ops"]
    $naturalConversation = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Natural Corpus $smokeMarker"; contextMode = "AGENT_MEMORY"; boundKnowledgeBaseId = $opsCorpus.knowledgeBaseId }) $opsCorpus.authToken
    $naturalMessage = Invoke-JsonApi "POST" "/api/conversations/$($naturalConversation.data.conversationId)/messages" ([ordered]@{ content = "Use the bound knowledge base to summarize the checkout incident and support SLA." }) $opsCorpus.authToken
    $naturalTrace = Invoke-JsonApi "GET" "/api/conversations/$($naturalConversation.data.conversationId)/messages/$($naturalMessage.data.messageId)/trace" $null $opsCorpus.authToken
    $naturalIncidentDocId = [long]$opsCorpus.docIds["incident"]
    $naturalSupportDocId = [long]$opsCorpus.docIds["support"]
    $naturalTraceOk = ([bool]$naturalTrace.data.ragTriggered -and [bool]$naturalTrace.data.ragRequired -and [int]$naturalTrace.data.evidenceCount -gt 0 -and
      (Get-CountValue $naturalTrace.data.documentHitCounts ([string]$naturalIncidentDocId)) -ge 1 -and
      (Get-CountValue $naturalTrace.data.documentHitCounts ([string]$naturalSupportDocId)) -ge 1)
    $naturalTraceCaseResult = [ordered]@{
      caseId = "natural-conversation-trace"
      caseType = "conversation_trace"
      status = if ($naturalTraceOk) { "PASS" } else { "FAILED_CORE_FLOW" }
      passed = [bool]$naturalTraceOk
      traceId = "conversation-$($naturalConversation.data.conversationId)-message-$($naturalMessage.data.messageId)"
      conversationId = [string]$naturalConversation.data.conversationId
      ragTriggered = [bool]$naturalTrace.data.ragTriggered
      ragRequired = [bool]$naturalTrace.data.ragRequired
      evidenceCount = [int]$naturalTrace.data.evidenceCount
      failureBuckets = if ($naturalTraceOk) { @() } else { @("conversationTraceCoverage") }
      reviewBuckets = @()
    }

    $failedCaseResults = @($naturalCaseResults | Where-Object { @($_.failureBuckets).Count -gt 0 })
    $reviewCaseResults = @($naturalCaseResults | Where-Object { @($_.reviewBuckets).Count -gt 0 })
    $naturalHardFailures = @($failedCaseResults | ForEach-Object { "$($_.caseId):$(@($_.failureBuckets) -join '+')" })
    $naturalReviewBuckets = @($reviewCaseResults | ForEach-Object { "$($_.caseId):$(@($_.reviewBuckets) -join '+')" })
    if (-not $naturalTraceOk) { $naturalHardFailures += "conversationTraceCoverage" }
    $naturalQaCases = @($naturalCaseResults | Where-Object { $_.mode -eq "qa" })
    $naturalNoEvidenceCases = @($naturalCaseResults | Where-Object { $_.noEvidenceExpected })
    $naturalMultiDocCases = @($naturalCaseResults | Where-Object { $_.targetDocumentCount -gt 1 })
    $naturalDistractorCases = @($naturalCaseResults | Where-Object { $_.distractorDocumentCount -gt 0 })
    $naturalAnswerFaithfulnessCases = @($naturalCaseResults | Where-Object { $_.answerFaithfulnessRequired })
    $naturalCitationSupportCases = @($naturalCaseResults | Where-Object { -not $_.noEvidenceExpected })
    $naturalPassedCases = @($naturalCaseResults | Where-Object { @($_.failureBuckets).Count -eq 0 })
    $naturalEvidenceCoverageReport = [ordered]@{
      retrieveCoveragePassCount = @($naturalCitationSupportCases | Where-Object { $_.targetRetrieveCovered }).Count
      citationCoveragePassCount = @($naturalCitationSupportCases | Where-Object { $_.targetCitationCovered }).Count
      citationPhraseSupportPassCount = @($naturalCitationSupportCases | Where-Object { $_.citationPhraseSupport }).Count
      answerFaithfulnessPassCount = @($naturalAnswerFaithfulnessCases | Where-Object { $_.answerFactExpression -and -not $_.forbiddenAnswerHit }).Count
      noEvidenceCorrectCount = @($naturalNoEvidenceCases | Where-Object { $_.noEvidenceCorrect }).Count
      distractorCitationFreeCount = @($naturalDistractorCases | Where-Object { $_.distractorCitationCount -eq 0 }).Count
      retrievalCoverageMisses = @($naturalCitationSupportCases | Where-Object { -not $_.targetRetrieveCovered } | ForEach-Object { $_.caseId })
      citationCoverageMisses = @($naturalCitationSupportCases | Where-Object { -not $_.targetCitationCovered } | ForEach-Object { $_.caseId })
      citationPhraseMisses = @($naturalCitationSupportCases | Where-Object { -not $_.citationPhraseSupport } | ForEach-Object { $_.caseId })
      answerFaithfulnessMisses = @($naturalAnswerFaithfulnessCases | Where-Object { -not $_.answerFactExpression -or $_.forbiddenAnswerHit } | ForEach-Object { $_.caseId })
      distractorCitationLeaks = @($naturalDistractorCases | Where-Object { $_.distractorCitationCount -gt 0 } | ForEach-Object { $_.caseId })
      noEvidenceFailures = @($naturalNoEvidenceCases | Where-Object { -not $_.noEvidenceCorrect } | ForEach-Object { $_.caseId })
    }

    $corpusResources = @()
    foreach ($corpusKey in @($naturalCorpora.Keys)) {
      $corpus = $naturalCorpora[$corpusKey]
      $corpusResources += [ordered]@{
        corpus = $corpusKey
        userId = [long]$corpus.userId
        knowledgeBaseId = [long]$corpus.knowledgeBaseId
        documentIds = @($corpus.docIds.Values)
        parseTaskIds = $corpus.parseTaskIds
      }
    }

    $naturalCorpusGateChecks = @([ordered]@{
        schemaVersion = 2
        corpusCount = @($naturalCorpora.Keys).Count
        documentCount = @($corpusResources | ForEach-Object { @($_.documentIds).Count } | Measure-Object -Sum).Sum
        caseCount = $naturalCaseResults.Count
        qaCaseCount = $naturalQaCases.Count
        noEvidenceCaseCount = $naturalNoEvidenceCases.Count
        multiDocumentCaseCount = $naturalMultiDocCases.Count
        distractorCaseCount = $naturalDistractorCases.Count
        answerFaithfulnessCaseCount = $naturalAnswerFaithfulnessCases.Count
        citationSupportCaseCount = $naturalCitationSupportCases.Count
        casePassRate = if ($naturalCaseResults.Count -eq 0) { 0 } else { [Math]::Round($naturalPassedCases.Count / $naturalCaseResults.Count, 4) }
        noEvidencePassCount = @($naturalNoEvidenceCases | Where-Object { $_.noEvidenceCorrect }).Count
        multiDocumentCoveragePassCount = @($naturalMultiDocCases | Where-Object { $_.targetRetrieveCovered -and $_.targetCitationCovered }).Count
        distractorCitationFreeCount = @($naturalDistractorCases | Where-Object { $_.distractorCitationCount -eq 0 }).Count
        answerFaithfulnessPassCount = @($naturalAnswerFaithfulnessCases | Where-Object { $_.answerFactExpression -and -not $_.forbiddenAnswerHit }).Count
        citationPhraseSupportPassCount = @($naturalCitationSupportCases | Where-Object { $_.citationPhraseSupport }).Count
        traceRagTriggered = [bool]$naturalTrace.data.ragTriggered
        traceRagRequired = [bool]$naturalTrace.data.ragRequired
        traceEvidenceCount = [int]$naturalTrace.data.evidenceCount
        traceDocumentHitCounts = $naturalTrace.data.documentHitCounts
        hardFailureBuckets = $naturalHardFailures
        reviewBuckets = $naturalReviewBuckets
        evidenceCoverageReport = $naturalEvidenceCoverageReport
        caseResults = @($naturalCaseResults) + @($naturalTraceCaseResult)
      })
    if ($naturalHardFailures.Count -gt 0) {
      $naturalFailureMessage = "natural corpus audit failed: " + ($naturalHardFailures -join ",")
      Set-Gate "naturalCorpus" "FAILED_CORE_FLOW" $naturalCorpusGateChecks $naturalFailureMessage
      Stop-WithStatus "FAILED_CORE_FLOW" "naturalCorpus" $naturalFailureMessage
    }
    $naturalStatus = if ($naturalReviewBuckets.Count -gt 0) { "REVIEW" } else { "PASS" }
    $naturalMessageText = if ($naturalReviewBuckets.Count -gt 0) { "natural corpus audit has answer expression review buckets" } else { "" }
    Set-Gate "naturalCorpus" $naturalStatus $naturalCorpusGateChecks $naturalMessageText
    $naturalCorpusResources = [ordered]@{
      corpora = $corpusResources
      conversationId = [long]$naturalConversation.data.conversationId
      messageId = [long]$naturalMessage.data.messageId
    }
  }

  if ($EnableMultiQueryGate) {
    $multiQueryQuestion = "Compare the alpha chunk quality evidence and beta context trace evidence for $smokeMarker. Include ALPHA-CLOUD-GATE and BETA-CONTEXT-GATE verbatim, and cite both documents."
    $multiQueryBody = [ordered]@{
      query = $multiQueryQuestion
      topK = 6
      indexVersion = $IndexVersion
      multiQueryEnabled = $true
      maxQueryVariants = 4
    }
    $multiQueryQaBody = [ordered]@{
      question = $multiQueryQuestion
      topK = 6
      indexVersion = $IndexVersion
      multiQueryEnabled = $true
      maxQueryVariants = 4
    }
    $multiQueryRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" $multiQueryBody $tokenA
    $multiQueryQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/qa/rag" $multiQueryQaBody $tokenA
    $multiQueryHits = @($multiQueryRetrieve.data.hits)
    $multiQueryCitations = @($multiQueryQa.data.citations)
    $multiQueryGateChecks = @([ordered]@{
        retrieveHits = $multiQueryHits.Count
        qaCitations = $multiQueryCitations.Count
        documentHitCounts = $multiQueryRetrieve.data.documentHitCounts
        alphaRetrieveCount = Get-DocumentHitCount $multiQueryHits ([long]$docA.data.id)
        betaRetrieveCount = Get-DocumentHitCount $multiQueryHits ([long]$docB.data.id)
        alphaCitationCount = Get-DocumentHitCount $multiQueryCitations ([long]$docA.data.id)
        betaCitationCount = Get-DocumentHitCount $multiQueryCitations ([long]$docB.data.id)
        multiQueryApplied = [bool]$multiQueryRetrieve.data.multiQueryApplied
        queryVariantCount = [int]$multiQueryRetrieve.data.queryVariantCount
        queryDedupeCount = [int]$multiQueryRetrieve.data.queryDedupeCount
        retrievalMode = $multiQueryRetrieve.data.retrievalMode
        retrieveScoreSummary = Get-ScoreSummary $multiQueryHits
        citationScoreSummary = Get-ScoreSummary $multiQueryCitations
        retrieveVectorScoreSummary = Get-FieldScoreSummary $multiQueryHits "vectorScore"
        citationVectorScoreSummary = Get-FieldScoreSummary $multiQueryCitations "vectorScore"
      })
    if ((-not [bool]$multiQueryRetrieve.data.multiQueryApplied) -or [int]$multiQueryRetrieve.data.queryVariantCount -lt 2 -or
      $multiQueryHits.Count -lt 2 -or $multiQueryCitations.Count -lt 2 -or
      $multiQueryGateChecks[0].alphaRetrieveCount -lt 1 -or $multiQueryGateChecks[0].betaRetrieveCount -lt 1 -or
      $multiQueryGateChecks[0].alphaCitationCount -lt 1 -or $multiQueryGateChecks[0].betaCitationCount -lt 1) {
      Set-Gate "multiQueryRag" "REVIEW" $multiQueryGateChecks "multi-query gate did not trigger or did not cover both smoke documents"
    } else {
      Set-Gate "multiQueryRag" "PASS" $multiQueryGateChecks
    }
    $answerGroundingChecks += Test-AnswerGrounding "multiQueryRag" ([string]$multiQueryQa.data.answer) @("ALPHA-CLOUD-GATE", "BETA-CONTEXT-GATE") @("real-marketing-export-forbidden-marker")
  }

  if ($EnableRepresentativeCorpusGate) {
    $gammaText = @"
# Incident Review Corpus

$smokeMarker
Incident review evidence document. This representative corpus note is modeled after the real QA eval multi document incident review cases.
Incident detection marker real-incident-detection-marker says alert correlation found the checkout issue.
Incident mitigation marker real-incident-mitigation-marker says the team disabled the faulty worker queue.
Customer communication marker real-customer-communication-marker says support posted the final status update.

## Grounding Notes

The answer should cite this incident review document together with the Alpha chunk metadata document and the Beta context trace document.
Forbidden appendix marker real-marketing-export-forbidden-marker is unrelated marketing export noise and must not be used as incident evidence.
The representative gate stores only ids, counts, ranks, and score summaries in the artifact.
"@
    $gammaPath = Join-Path $artifactDir "gamma-incident-review.txt"
    [System.IO.File]::WriteAllText($gammaPath, $gammaText, [System.Text.UTF8Encoding]::new($false))

    $fileC = Upload-SmokeFile $gammaPath $tokenA
    $docC = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $fileC.id }) $tokenA
    $taskC = Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $docC.data.id }) $tokenA
    Wait-ParseSuccess ([long]$docC.data.id) $tokenA | Out-Null
    Wait-IndexedChunks $envValues $userAId ([long]$docC.data.id) | Out-Null

    $representativeKb = Invoke-JsonApi "POST" "/api/knowledge-bases" ([ordered]@{ name = "Representative Corpus KB $smokeMarker"; description = "temporary representative real qa smoke kb" }) $tokenA
    Invoke-JsonApi "POST" "/api/knowledge-bases/$($representativeKb.data.id)/documents" ([ordered]@{ documentIds = @($docA.data.id, $docB.data.id, $docC.data.id) }) $tokenA | Out-Null
    $representativeQuery = "Summarize the representative corpus for $smokeMarker. The retrieved evidence contains these exact markers: ALPHA-CLOUD-GATE, BETA-CONTEXT-GATE, real-incident-detection-marker. Copy all three markers verbatim in the final answer and cite the evidence with citation markers."
    $representativeRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($representativeKb.data.id)/rag/retrieve" ([ordered]@{ query = $representativeQuery; topK = 8; indexVersion = $IndexVersion }) $tokenA
    $representativeQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($representativeKb.data.id)/qa/rag" ([ordered]@{ question = $representativeQuery; topK = 8; indexVersion = $IndexVersion }) $tokenA
    $representativeHits = @($representativeRetrieve.data.hits)
    $representativeCitations = @($representativeQa.data.citations)
    $representativeHitCounts = $representativeRetrieve.data.documentHitCounts
    $representativeChecks = @([ordered]@{
        retrieveHits = $representativeHits.Count
        qaCitations = $representativeCitations.Count
        documentHitCounts = $representativeHitCounts
        alphaRetrieveCount = Get-DocumentHitCount $representativeHits ([long]$docA.data.id)
        betaRetrieveCount = Get-DocumentHitCount $representativeHits ([long]$docB.data.id)
        gammaRetrieveCount = Get-DocumentHitCount $representativeHits ([long]$docC.data.id)
        alphaCitationCount = Get-DocumentHitCount $representativeCitations ([long]$docA.data.id)
        betaCitationCount = Get-DocumentHitCount $representativeCitations ([long]$docB.data.id)
        gammaCitationCount = Get-DocumentHitCount $representativeCitations ([long]$docC.data.id)
        retrievalMode = $representativeRetrieve.data.retrievalMode
        rerankApplied = [bool]$representativeRetrieve.data.rerankApplied
        retrieveScoreSummary = Get-ScoreSummary $representativeHits
        citationScoreSummary = Get-ScoreSummary $representativeCitations
        retrieveVectorScoreSummary = Get-FieldScoreSummary $representativeHits "vectorScore"
        citationVectorScoreSummary = Get-FieldScoreSummary $representativeCitations "vectorScore"
        qualityMinSimilarityThreshold = $QualityMinSimilarityThreshold
      })
    if ($representativeHits.Count -lt 3 -or $representativeCitations.Count -lt 3 -or
      $representativeChecks[0].alphaRetrieveCount -lt 1 -or $representativeChecks[0].betaRetrieveCount -lt 1 -or $representativeChecks[0].gammaRetrieveCount -lt 1 -or
      $representativeChecks[0].alphaCitationCount -lt 1 -or $representativeChecks[0].betaCitationCount -lt 1 -or $representativeChecks[0].gammaCitationCount -lt 1) {
      Set-Gate "representativeCorpus" "FAILED_CORE_FLOW" $representativeChecks "representative corpus gate did not cover all three documents"
      Stop-WithStatus "FAILED_CORE_FLOW" "representativeCorpus" "representative corpus gate did not cover all three documents"
    }
    $answerGroundingChecks += Test-AnswerGrounding "representativeCorpus" ([string]$representativeQa.data.answer) @("ALPHA-CLOUD-GATE", "BETA-CONTEXT-GATE", "real-incident-detection-marker") @("real-marketing-export-forbidden-marker")
    Set-Gate "representativeCorpus" "PASS" $representativeChecks
    $representativeCorpusResources = [ordered]@{
      knowledgeBaseId = [long]$representativeKb.data.id
      documentIds = @([long]$docA.data.id, [long]$docB.data.id, [long]$docC.data.id)
      parseTaskId = [long]$taskC.data.taskId
    }
  }

  $failedAnswerGrounding = @($answerGroundingChecks | Where-Object {
    (-not $_.answerPresent) -or (-not $_.expectedMarkersSatisfied) -or $_.forbiddenMarkerHit -or (-not $_.citationMarkerPresent)
  })
  if ($failedAnswerGrounding.Count -gt 0) {
    Set-Gate "answerGrounding" "FAILED_CORE_FLOW" $answerGroundingChecks "RAG answer did not satisfy expected marker/citation grounding"
    Stop-WithStatus "FAILED_CORE_FLOW" "answerGrounding" "RAG answer did not satisfy expected marker/citation grounding"
  }
  Set-Gate "answerGrounding" "PASS" $answerGroundingChecks

  if ($EnableRealQaHardGate) {
    $realQaHardNegativeQuery = "Which evidence says payroll tax remittance is delegated to the context trace custodian after chunk metadata verification?"
    $realQaHardNegativeRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" ([ordered]@{ query = $realQaHardNegativeQuery; topK = 3; indexVersion = $IndexVersion }) $tokenA
    $realQaHardNegativeQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/qa/rag" ([ordered]@{ question = $realQaHardNegativeQuery; topK = 3; indexVersion = $IndexVersion }) $tokenA
    $hardNegativeCheck = [ordered]@{
      scope = "hardNegative"
      retrieveNoEvidence = [bool]$realQaHardNegativeRetrieve.data.noEvidence
      qaNoEvidence = [bool]$realQaHardNegativeQa.data.noEvidence
      retrieveHits = @($realQaHardNegativeRetrieve.data.hits).Count
      qaCitations = @($realQaHardNegativeQa.data.citations).Count
      retrieveScoreSummary = Get-ScoreSummary $realQaHardNegativeRetrieve.data.hits
      citationScoreSummary = Get-ScoreSummary $realQaHardNegativeQa.data.citations
      retrieveVectorScoreSummary = Get-FieldScoreSummary $realQaHardNegativeRetrieve.data.hits "vectorScore"
      citationVectorScoreSummary = Get-FieldScoreSummary $realQaHardNegativeQa.data.citations "vectorScore"
      passed = ([bool]$realQaHardNegativeRetrieve.data.noEvidence -and [bool]$realQaHardNegativeQa.data.noEvidence)
    }

    $realQaFaithfulnessQuestion = "What does ALPHA-CLOUD-GATE prove for $smokeMarker? Include ALPHA-CLOUD-GATE verbatim and cite the exact Alpha evidence."
    $realQaFaithfulnessRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" ([ordered]@{ query = $realQaFaithfulnessQuestion; topK = 1; indexVersion = $IndexVersion }) $tokenA
    $realQaFaithfulnessQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/qa/rag" ([ordered]@{ question = $realQaFaithfulnessQuestion; topK = 1; indexVersion = $IndexVersion }) $tokenA
    $faithfulnessGrounding = Test-AnswerGrounding "answerFaithfulness" ([string]$realQaFaithfulnessQa.data.answer) @("ALPHA-CLOUD-GATE") @("BETA-CONTEXT-GATE", "real-marketing-export-forbidden-marker")
    $faithfulnessCitations = @($realQaFaithfulnessQa.data.citations)
    $faithfulnessCheck = [ordered]@{
      scope = "answerFaithfulness"
      retrieveHits = @($realQaFaithfulnessRetrieve.data.hits).Count
      qaCitations = $faithfulnessCitations.Count
      targetCitationCount = Get-DocumentHitCount $faithfulnessCitations ([long]$docA.data.id)
      forbiddenCitationCount = Get-DocumentHitCount $faithfulnessCitations ([long]$docB.data.id)
      retrieveScoreSummary = Get-ScoreSummary $realQaFaithfulnessRetrieve.data.hits
      citationScoreSummary = Get-ScoreSummary $faithfulnessCitations
      expectedMarkersSatisfied = [bool]$faithfulnessGrounding.expectedMarkersSatisfied
      forbiddenMarkerHit = [bool]$faithfulnessGrounding.forbiddenMarkerHit
      citationMarkerPresent = [bool]$faithfulnessGrounding.citationMarkerPresent
      answerLength = $faithfulnessGrounding.answerLength
      passed = ([bool]$faithfulnessGrounding.answerPresent -and [bool]$faithfulnessGrounding.expectedMarkersSatisfied -and
        (-not [bool]$faithfulnessGrounding.forbiddenMarkerHit) -and [bool]$faithfulnessGrounding.citationMarkerPresent -and
        $faithfulnessCitations.Count -ge 1 -and (Get-DocumentHitCount $faithfulnessCitations ([long]$docA.data.id)) -ge 1 -and
        (Get-DocumentHitCount $faithfulnessCitations ([long]$docB.data.id)) -eq 0)
    }
    $realQaHardGateChecks = @($hardNegativeCheck, $faithfulnessCheck)
    if (-not $faithfulnessCheck.passed) {
      Set-Gate "realQaHardGate" "FAILED_CORE_FLOW" $realQaHardGateChecks "answer faithfulness gate failed"
      Stop-WithStatus "FAILED_CORE_FLOW" "realQaHardGate" "answer faithfulness gate failed"
    }
    if (-not $hardNegativeCheck.passed) {
      Set-Gate "realQaHardGate" "REVIEW" $realQaHardGateChecks "hard negative query still returned evidence; tune threshold or grounding policy"
    } else {
      Set-Gate "realQaHardGate" "PASS" $realQaHardGateChecks
    }
  }

  if ($EnableRealQaSemanticGate) {
    $claimSupportQuestion = "Which evidence says vendor access renewal requires manager approval before the quarterly review? Include real-claim-support-manager-approval-marker verbatim and cite the exact evidence."
    $claimSupportRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" ([ordered]@{ query = $claimSupportQuestion; topK = 1; indexVersion = $IndexVersion }) $tokenA
    $claimSupportQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/qa/rag" ([ordered]@{ question = $claimSupportQuestion; topK = 1; indexVersion = $IndexVersion }) $tokenA
    $claimSupportGrounding = Test-AnswerGrounding "claimSupport" ([string]$claimSupportQa.data.answer) @("real-claim-support-manager-approval-marker") @("real-claim-support-schedule-forbidden-marker", "does not mention manager approval")
    $claimSupportCitations = @($claimSupportQa.data.citations)
    $claimSupportCheck = [ordered]@{
      scope = "claimSupport"
      retrieveNoEvidence = [bool]$claimSupportRetrieve.data.noEvidence
      qaNoEvidence = [bool]$claimSupportQa.data.noEvidence
      retrieveHits = @($claimSupportRetrieve.data.hits).Count
      qaCitations = $claimSupportCitations.Count
      targetCitationCount = Get-DocumentHitCount $claimSupportCitations ([long]$docA.data.id)
      forbiddenCitationCount = Get-DocumentHitCount $claimSupportCitations ([long]$docB.data.id)
      retrieveScoreSummary = Get-ScoreSummary $claimSupportRetrieve.data.hits
      citationScoreSummary = Get-ScoreSummary $claimSupportCitations
      retrieveVectorScoreSummary = Get-FieldScoreSummary $claimSupportRetrieve.data.hits "vectorScore"
      citationVectorScoreSummary = Get-FieldScoreSummary $claimSupportCitations "vectorScore"
      expectedMarkersSatisfied = [bool]$claimSupportGrounding.expectedMarkersSatisfied
      forbiddenMarkerHit = [bool]$claimSupportGrounding.forbiddenMarkerHit
      citationMarkerPresent = [bool]$claimSupportGrounding.citationMarkerPresent
      answerLength = $claimSupportGrounding.answerLength
      passed = ((-not [bool]$claimSupportRetrieve.data.noEvidence) -and (-not [bool]$claimSupportQa.data.noEvidence) -and
        [bool]$claimSupportGrounding.answerPresent -and [bool]$claimSupportGrounding.expectedMarkersSatisfied -and
        (-not [bool]$claimSupportGrounding.forbiddenMarkerHit) -and [bool]$claimSupportGrounding.citationMarkerPresent -and
        $claimSupportCitations.Count -ge 1 -and (Get-DocumentHitCount $claimSupportCitations ([long]$docA.data.id)) -ge 1 -and
        (Get-DocumentHitCount $claimSupportCitations ([long]$docB.data.id)) -eq 0)
    }

    $numericFaithfulnessQuestion = "Which evidence states the invoice archive retention window is seven years? Include real-invoice-retention-seven-year-marker verbatim and cite the exact evidence."
    $numericFaithfulnessRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/rag/retrieve" ([ordered]@{ query = $numericFaithfulnessQuestion; topK = 1; indexVersion = $IndexVersion }) $tokenA
    $numericFaithfulnessQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($kb.data.id)/qa/rag" ([ordered]@{ question = $numericFaithfulnessQuestion; topK = 1; indexVersion = $IndexVersion }) $tokenA
    $numericFaithfulnessGrounding = Test-AnswerGrounding "numericFaithfulness" ([string]$numericFaithfulnessQa.data.answer) @("real-invoice-retention-seven-year-marker") @("real-invoice-retention-three-year-forbidden-marker", "three years")
    $numericFaithfulnessCitations = @($numericFaithfulnessQa.data.citations)
    $numericFaithfulnessCheck = [ordered]@{
      scope = "numericFaithfulness"
      retrieveNoEvidence = [bool]$numericFaithfulnessRetrieve.data.noEvidence
      qaNoEvidence = [bool]$numericFaithfulnessQa.data.noEvidence
      retrieveHits = @($numericFaithfulnessRetrieve.data.hits).Count
      qaCitations = $numericFaithfulnessCitations.Count
      targetCitationCount = Get-DocumentHitCount $numericFaithfulnessCitations ([long]$docA.data.id)
      forbiddenCitationCount = Get-DocumentHitCount $numericFaithfulnessCitations ([long]$docB.data.id)
      retrieveScoreSummary = Get-ScoreSummary $numericFaithfulnessRetrieve.data.hits
      citationScoreSummary = Get-ScoreSummary $numericFaithfulnessCitations
      retrieveVectorScoreSummary = Get-FieldScoreSummary $numericFaithfulnessRetrieve.data.hits "vectorScore"
      citationVectorScoreSummary = Get-FieldScoreSummary $numericFaithfulnessCitations "vectorScore"
      expectedMarkersSatisfied = [bool]$numericFaithfulnessGrounding.expectedMarkersSatisfied
      forbiddenMarkerHit = [bool]$numericFaithfulnessGrounding.forbiddenMarkerHit
      citationMarkerPresent = [bool]$numericFaithfulnessGrounding.citationMarkerPresent
      answerLength = $numericFaithfulnessGrounding.answerLength
      passed = ((-not [bool]$numericFaithfulnessRetrieve.data.noEvidence) -and (-not [bool]$numericFaithfulnessQa.data.noEvidence) -and
        [bool]$numericFaithfulnessGrounding.answerPresent -and [bool]$numericFaithfulnessGrounding.expectedMarkersSatisfied -and
        (-not [bool]$numericFaithfulnessGrounding.forbiddenMarkerHit) -and [bool]$numericFaithfulnessGrounding.citationMarkerPresent -and
        $numericFaithfulnessCitations.Count -ge 1 -and (Get-DocumentHitCount $numericFaithfulnessCitations ([long]$docA.data.id)) -ge 1 -and
        (Get-DocumentHitCount $numericFaithfulnessCitations ([long]$docB.data.id)) -eq 0)
    }

    $realQaSemanticGateChecks = @($claimSupportCheck, $numericFaithfulnessCheck)
    if (-not $claimSupportCheck.passed -or -not $numericFaithfulnessCheck.passed) {
      Set-Gate "realQaSemanticGate" "REVIEW" $realQaSemanticGateChecks "claim support or numeric faithfulness gate did not satisfy grounded answer checks"
    } else {
      Set-Gate "realQaSemanticGate" "PASS" $realQaSemanticGateChecks
    }
  }

  if ($EnableRealProviderFaithfulnessGate) {
    $realProviderFaithfulnessChecks = @(
      Test-RealAnswerProvider "knowledgeBaseRag" $kbQa
    )
    if ($null -ne $realQaFaithfulnessQa) {
      $realProviderFaithfulnessChecks += Test-RealAnswerProvider "answerFaithfulness" $realQaFaithfulnessQa
    }
    if ($null -ne $claimSupportQa) {
      $realProviderFaithfulnessChecks += Test-RealAnswerProvider "claimSupport" $claimSupportQa
    }
    if ($null -ne $numericFaithfulnessQa) {
      $realProviderFaithfulnessChecks += Test-RealAnswerProvider "numericFaithfulness" $numericFaithfulnessQa
    }
    $failedRealProviderFaithfulness = @($realProviderFaithfulnessChecks | Where-Object { -not $_.passed })
    if ($failedRealProviderFaithfulness.Count -gt 0) {
      Set-Gate "realProviderFaithfulness" "REVIEW" $realProviderFaithfulnessChecks "real answer provider gate did not observe non-mock grounded answers"
    } else {
      Set-Gate "realProviderFaithfulness" "PASS" $realProviderFaithfulnessChecks
    }
  }

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

  if ($EnableRerankHardGate) {
    $hardDistractorText = @"
# Hard Rerank Distractor

$smokeMarker
This distractor repeats upload parse chunk index retrieve answer citation context trace evidence terms many times.
Upload parse chunk index retrieve answer citation context trace evidence terms are repeated again for keyword retrieval pressure.
Upload parse chunk index retrieve answer citation context trace evidence terms appear a third time, but this note is marketing noise.
HARD-RERANK-FORBIDDEN says this document must not be treated as the exact Alpha or Beta policy evidence.
"@
    $hardDistractorPath = Join-Path $artifactDir "hard-distractor.txt"
    [System.IO.File]::WriteAllText($hardDistractorPath, $hardDistractorText, [System.Text.UTF8Encoding]::new($false))

    $hardDistractorFile = Upload-SmokeFile $hardDistractorPath $tokenA
    $hardTargetDoc = $docA
    $hardSupportDoc = $docB
    $hardDistractorDoc = Invoke-JsonApi "POST" "/api/document/create" ([ordered]@{ fileRecordId = $hardDistractorFile.id }) $tokenA
    Invoke-JsonApi "POST" "/api/task/parse/create" ([ordered]@{ documentId = $hardDistractorDoc.data.id }) $tokenA | Out-Null
    Wait-ParseSuccess ([long]$hardDistractorDoc.data.id) $tokenA | Out-Null
    Wait-IndexedChunks $envValues $userAId ([long]$hardDistractorDoc.data.id) | Out-Null

    $hardKb = Invoke-JsonApi "POST" "/api/knowledge-bases" ([ordered]@{ name = "Rerank Hard KB $smokeMarker"; description = "temporary rerank hard fixture" }) $tokenA
    Invoke-JsonApi "POST" "/api/knowledge-bases/$($hardKb.data.id)/documents" ([ordered]@{ documentIds = @($hardTargetDoc.data.id, $hardSupportDoc.data.id, $hardDistractorDoc.data.id) }) $tokenA | Out-Null
    $hardQuestion = "Which evidence explains upload parse chunk indexing and conversation context trace behavior?"
    $hardRetrieve = Invoke-JsonApi "POST" "/api/knowledge-bases/$($hardKb.data.id)/rag/retrieve" ([ordered]@{ query = $hardQuestion; topK = 6; indexVersion = $IndexVersion }) $tokenA
    $hardQa = Invoke-JsonApi "POST" "/api/knowledge-bases/$($hardKb.data.id)/qa/rag" ([ordered]@{ question = $hardQuestion; topK = 6; indexVersion = $IndexVersion }) $tokenA
    $hardHits = @($hardRetrieve.data.hits)
    $hardCitations = @($hardQa.data.citations)
    $hardChecks = @([ordered]@{
        targetDocumentId = [long]$hardTargetDoc.data.id
        supportDocumentId = [long]$hardSupportDoc.data.id
        distractorDocumentId = [long]$hardDistractorDoc.data.id
        retrievalMode = $hardRetrieve.data.retrievalMode
        rerankApplied = [bool]$hardRetrieve.data.rerankApplied
        rerankModel = $hardRetrieve.data.rerankModel
        retrieveHits = $hardHits.Count
        qaCitations = $hardCitations.Count
        targetRetrieveCount = Get-DocumentHitCount $hardHits ([long]$hardTargetDoc.data.id)
        supportRetrieveCount = Get-DocumentHitCount $hardHits ([long]$hardSupportDoc.data.id)
        distractorRetrieveCount = Get-DocumentHitCount $hardHits ([long]$hardDistractorDoc.data.id)
        targetBestRank = Get-FirstDocumentRank $hardHits ([long]$hardTargetDoc.data.id)
        supportBestRank = Get-FirstDocumentRank $hardHits ([long]$hardSupportDoc.data.id)
        distractorBestRank = Get-FirstDocumentRank $hardHits ([long]$hardDistractorDoc.data.id)
        targetCitationCount = Get-DocumentHitCount $hardCitations ([long]$hardTargetDoc.data.id)
        supportCitationCount = Get-DocumentHitCount $hardCitations ([long]$hardSupportDoc.data.id)
        distractorCitationCount = Get-DocumentHitCount $hardCitations ([long]$hardDistractorDoc.data.id)
        retrieveScoreSummary = Get-ScoreSummary $hardHits
        retrieveVectorScoreSummary = Get-FieldScoreSummary $hardHits "vectorScore"
        retrieveRerankScoreSummary = Get-FieldScoreSummary $hardHits "rerankScore"
        citationRerankScoreSummary = Get-FieldScoreSummary $hardCitations "rerankScore"
      })
    if ($hardRetrieve.data.noEvidence -or $hardHits.Count -lt 1) {
      Set-Gate "rerankHardFixture" "FAILED_CORE_FLOW" $hardChecks "hard rerank fixture returned no evidence"
      Stop-WithStatus "FAILED_CORE_FLOW" "rerankHardFixture" "hard rerank fixture returned no evidence"
    }
    $hardStatus = "PASS"
    if ($hardChecks[0].targetRetrieveCount -lt 1 -or $hardChecks[0].targetCitationCount -lt 1) {
      $hardStatus = "REVIEW"
    }
    Set-Gate "rerankHardFixture" $hardStatus $hardChecks
    $rerankHardResources = [ordered]@{
      knowledgeBaseId = [long]$hardKb.data.id
      targetDocumentId = [long]$hardTargetDoc.data.id
      supportDocumentId = [long]$hardSupportDoc.data.id
      distractorDocumentId = [long]$hardDistractorDoc.data.id
    }
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
      caseResults = @([ordered]@{
          caseId = "conversation-trace-rag-memory"
          caseType = "conversation_trace"
          status = "PASS"
          passed = $true
          traceId = "conversation-$($conversation.data.conversationId)-message-$($message.data.messageId)"
          conversationId = [string]$conversation.data.conversationId
          ragTriggered = [bool]$trace.data.ragTriggered
          ragRequired = [bool]$trace.data.ragRequired
          evidenceCount = [int]$trace.data.evidenceCount
          memoryCount = [int]$trace.data.memoryCount
          failureBuckets = @()
          reviewBuckets = @()
        })
    })

  if ($EnableMemoryQualityGate) {
    $memoryConversation = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Memory Quality $smokeMarker"; contextMode = "AGENT_MEMORY"; boundKnowledgeBaseId = $kb.data.id }) $tokenA
    Add-SmokeConversationUserMessages $envValues $userAId ([long]$memoryConversation.data.conversationId) @(
      "Please answer with the conclusion first, then explain tradeoffs for $smokeMarker.",
      "Current goal is finishing the Memory Quality smoke phase while keeping user memory separate from RAG proof for $smokeMarker."
    )
    $suggestions = Invoke-JsonApi "POST" "/api/memories/suggestions/extract" ([ordered]@{ conversationId = $memoryConversation.data.conversationId; limit = 20 }) $tokenA
    $suggestionList = @($suggestions.data)
    $answerStyleSuggestion = $suggestionList | Where-Object { $_.memoryType -eq "ANSWER_STYLE" } | Select-Object -First 1
    $taskGoalSuggestion = $suggestionList | Where-Object { $_.memoryType -eq "TASK_GOAL" } | Select-Object -First 1
    if (-not $answerStyleSuggestion -or -not $taskGoalSuggestion) {
      Set-Gate "memoryQuality" "FAILED_CORE_FLOW" @([ordered]@{
          extractedSuggestionCount = $suggestionList.Count
          suggestionTypes = @($suggestionList | ForEach-Object { $_.memoryType })
          hasAnswerStyle = [bool]$answerStyleSuggestion
          hasTaskGoal = [bool]$taskGoalSuggestion
        }) "memory suggestion extraction did not produce answer style and task goal candidates"
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "memory suggestion extraction did not produce answer style and task goal candidates"
    }
    $acceptedMemory = Invoke-JsonApi "POST" "/api/memories/suggestions/$($answerStyleSuggestion.memoryId)/accept" $null $tokenA
    $ignoredMemory = Invoke-JsonApi "POST" "/api/memories/suggestions/$($taskGoalSuggestion.memoryId)/ignore" $null $tokenA
    $activeMemories = Invoke-JsonApi "GET" "/api/memories?limit=50" $null $tokenA
    $activeMemoryIds = @($activeMemories.data | ForEach-Object { [long]$_.memoryId })
    if ($acceptedMemory.data.status -ne "ACTIVE" -or $ignoredMemory.data.status -ne "IGNORED" -or ($activeMemoryIds -notcontains [long]$acceptedMemory.data.memoryId) -or ($activeMemoryIds -contains [long]$ignoredMemory.data.memoryId)) {
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "accepted and ignored memory status isolation failed"
    }
    $governanceActive = Invoke-JsonApi "POST" "/api/memories" ([ordered]@{
        memoryType = "ANSWER_STYLE"
        content = "Concise style for $smokeMarker."
        priority = 45
      }) $tokenA
    if ($governanceActive.data.status -ne "ACTIVE") {
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "temporary governance baseline memory was not ACTIVE"
    }
    $governanceConversation = Invoke-JsonApi "POST" "/api/conversations" ([ordered]@{ title = "Memory Governance $smokeMarker"; contextMode = "AGENT_MEMORY"; boundKnowledgeBaseId = $kb.data.id }) $tokenA
    Add-SmokeConversationUserMessages $envValues $userAId ([long]$governanceConversation.data.conversationId) @(
      "Please answer with detailed explanations for $smokeMarker."
    )
    $governanceSuggestions = Invoke-JsonApi "POST" "/api/memories/suggestions/extract" ([ordered]@{ conversationId = $governanceConversation.data.conversationId; limit = 10 }) $tokenA
    $governanceSuggestionList = @($governanceSuggestions.data)
    $conflictingSuggestion = $governanceSuggestionList |
      Where-Object { $_.memoryType -eq "ANSWER_STYLE" -and $_.governanceHint -eq "conflict_active_memory" -and $null -ne $_.conflictWithId } |
      Select-Object -First 1
    if (-not $conflictingSuggestion) {
      Set-Gate "memoryQuality" "FAILED_CORE_FLOW" @([ordered]@{
          governanceSuggestionCount = $governanceSuggestionList.Count
          governanceHints = @($governanceSuggestionList | ForEach-Object { $_.governanceHint })
          conflictWithIds = @($governanceSuggestionList | ForEach-Object { $_.conflictWithId })
        }) "memory governance did not flag conflicting answer-style suggestion"
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "memory governance did not flag conflicting answer-style suggestion"
    }
    $conflictAccept = Invoke-JsonApi "POST" "/api/memories/suggestions/$($conflictingSuggestion.memoryId)/accept" $null $tokenA -AllowFailure
    if ($conflictAccept.ok) {
      Set-Gate "memoryQuality" "FAILED_CORE_FLOW" @([ordered]@{
          conflictingSuggestionId = $conflictingSuggestion.memoryId
          governanceHint = $conflictingSuggestion.governanceHint
          conflictWithIdPresent = ($null -ne $conflictingSuggestion.conflictWithId)
          conflictAcceptBlocked = (-not $conflictAccept.ok)
      }) "conflicting memory suggestion was accepted without governance"
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "conflicting memory suggestion was accepted without governance"
    }
    if (([string]$conflictAccept.message) -notlike "*memory suggestion requires governance before accept*") {
      Set-Gate "memoryQuality" "FAILED_CORE_FLOW" @([ordered]@{
          conflictingSuggestionId = $conflictingSuggestion.memoryId
          governanceHint = $conflictingSuggestion.governanceHint
          conflictWithIdPresent = ($null -ne $conflictingSuggestion.conflictWithId)
          conflictAcceptBlocked = (-not $conflictAccept.ok)
          conflictAcceptCode = $conflictAccept.code
      }) "conflicting memory suggestion was blocked by an unexpected error"
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "conflicting memory suggestion was blocked by an unexpected error"
    }
    $keepResolved = Invoke-JsonApi "POST" "/api/memories/suggestions/$($conflictingSuggestion.memoryId)/resolve" ([ordered]@{
        action = "KEEP_ACTIVE"
        activeMemoryId = $conflictingSuggestion.conflictWithId
      }) $tokenA
    if ($keepResolved.data.status -ne "IGNORED") {
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "memory governance keep action did not ignore suggestion"
    }
    $replaceSuggestions = Invoke-JsonApi "POST" "/api/memories/suggestions/extract" ([ordered]@{ conversationId = $governanceConversation.data.conversationId; limit = 10 }) $tokenA
    $replaceConflict = @($replaceSuggestions.data) |
      Where-Object { $_.memoryType -eq "ANSWER_STYLE" -and $_.governanceHint -eq "conflict_active_memory" -and $null -ne $_.conflictWithId } |
      Select-Object -First 1
    if (-not $replaceConflict) {
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "memory governance did not recreate conflicting suggestion for replace"
    }
    $replaceResolved = Invoke-JsonApi "POST" "/api/memories/suggestions/$($replaceConflict.memoryId)/resolve" ([ordered]@{
        action = "REPLACE_ACTIVE"
        activeMemoryId = $replaceConflict.conflictWithId
      }) $tokenA
    if ($replaceResolved.data.memoryId -ne $governanceActive.data.memoryId -or $replaceResolved.data.status -ne "ACTIVE") {
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "memory governance replace action did not update active memory"
    }
    $sensitiveEdit = Invoke-JsonApi "PATCH" "/api/memories/$($governanceActive.data.memoryId)" ([ordered]@{
        content = "password token should never become user memory for $smokeMarker"
        priority = 46
      }) $tokenA -AllowFailure
    if ($sensitiveEdit.ok) {
      Stop-WithStatus "FAILED_SECURITY_GATE" "memoryQuality" "sensitive memory edit was accepted"
    }
    $editedActive = Invoke-JsonApi "PATCH" "/api/memories/$($governanceActive.data.memoryId)" ([ordered]@{
        content = "Concise style reset for $smokeMarker."
        priority = 46
      }) $tokenA
    if ($editedActive.data.status -ne "ACTIVE" -or [int]$editedActive.data.priority -ne 46) {
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "active memory edit did not persist"
    }
    $mergeSuggestions = Invoke-JsonApi "POST" "/api/memories/suggestions/extract" ([ordered]@{ conversationId = $governanceConversation.data.conversationId; limit = 10 }) $tokenA
    $mergeConflict = @($mergeSuggestions.data) |
      Where-Object { $_.memoryType -eq "ANSWER_STYLE" -and $_.governanceHint -eq "conflict_active_memory" -and $null -ne $_.conflictWithId } |
      Select-Object -First 1
    if (-not $mergeConflict) {
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "memory governance did not recreate conflicting suggestion for merge"
    }
    $mergedContent = "Use conclusion first and detailed explanation for $smokeMarker."
    $mergeResolved = Invoke-JsonApi "POST" "/api/memories/suggestions/$($mergeConflict.memoryId)/resolve" ([ordered]@{
        action = "MERGE_WITH_ACTIVE"
        activeMemoryId = $mergeConflict.conflictWithId
        mergedContent = $mergedContent
      }) $tokenA
    if ($mergeResolved.data.memoryId -ne $governanceActive.data.memoryId -or $mergeResolved.data.status -ne "ACTIVE" -or [int]$mergeResolved.data.content.Length -ne $mergedContent.Length) {
      Stop-WithStatus "FAILED_CORE_FLOW" "memoryQuality" "memory governance merge action did not update active memory"
    }
    Set-Gate "memoryQuality" "PASS" @([ordered]@{
        extractedSuggestionCount = $suggestionList.Count
        acceptedStatus = $acceptedMemory.data.status
        ignoredStatus = $ignoredMemory.data.status
        activeMemoryContainsAccepted = ($activeMemoryIds -contains [long]$acceptedMemory.data.memoryId)
        activeMemoryContainsIgnored = ($activeMemoryIds -contains [long]$ignoredMemory.data.memoryId)
        governanceHint = $conflictingSuggestion.governanceHint
        conflictWithIdPresent = ($null -ne $conflictingSuggestion.conflictWithId)
        conflictAcceptBlocked = (-not $conflictAccept.ok)
        conflictAcceptReasonMatched = (([string]$conflictAccept.message) -like "*memory suggestion requires governance before accept*")
        keepResolvedStatus = $keepResolved.data.status
        replaceResolvedActive = ($replaceResolved.data.memoryId -eq $governanceActive.data.memoryId)
        sensitiveEditBlocked = (-not $sensitiveEdit.ok)
        sensitiveEditCode = $sensitiveEdit.code
        editedActivePriority = $editedActive.data.priority
        mergeResolvedActive = ($mergeResolved.data.memoryId -eq $governanceActive.data.memoryId)
        mergedContentLength = $mergeResolved.data.content.Length
        contextSourceCounts = $sourceCounts
        memoryCount = $trace.data.memoryCount
        evidenceCount = $trace.data.evidenceCount
        documentHitCounts = $trace.data.documentHitCounts
      })
  }

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

  if ($EnableFrontendInteractionGate) {
    $frontendInteractionChecks = Invoke-FrontendInteractionGate $artifactDir $smokeMarker ([long]$shortDocA.data.id) ([long]$docA.data.id) $tokenB
  }

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
      naturalCorpusGateEnabled = [bool]$EnableNaturalCorpusGate
      naturalCorpusGate = $naturalCorpusResources
      representativeCorpusGateEnabled = [bool]$EnableRepresentativeCorpusGate
      representativeCorpusGate = $representativeCorpusResources
      multiQueryGateEnabled = [bool]$EnableMultiQueryGate
      multiQueryGate = $multiQueryGateChecks
      memoryQualityGateEnabled = [bool]$EnableMemoryQualityGate
      rerankHardGateEnabled = [bool]$EnableRerankHardGate
      rerankHardGate = $rerankHardResources
      realQaHardGateEnabled = [bool]$EnableRealQaHardGate
      realQaHardGate = $realQaHardGateChecks
      realQaSemanticGateEnabled = [bool]$EnableRealQaSemanticGate
      realQaSemanticGate = $realQaSemanticGateChecks
      realProviderFaithfulnessGateEnabled = [bool]$EnableRealProviderFaithfulnessGate
      realProviderFaithfulnessGate = $realProviderFaithfulnessChecks
      knowledgeBaseAgentGateEnabled = [bool]$EnableKnowledgeBaseAgentGate
      knowledgeBaseAgentGate = $knowledgeBaseAgentChecks
      frontendInteractionGateEnabled = [bool]$EnableFrontendInteractionGate
      frontendInteractionGate = $frontendInteractionChecks
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
