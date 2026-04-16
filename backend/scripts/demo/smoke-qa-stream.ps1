param(
  [string]$BackendBaseUrl = "http://localhost:8081",
  [string]$Phone = "13800000000",
  [int]$DocumentId = 0,
  [string]$Question = "Please summarize this document in detail with at least 500 Chinese characters."
)

$ErrorActionPreference = "Stop"
$baseUrl = $BackendBaseUrl.TrimEnd("/")

function Invoke-JsonPost {
  param(
    [string]$Uri,
    [hashtable]$Body,
    [hashtable]$Headers = @{}
  )
  return Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 6)
}

Write-Host "[1/5] Login via dev sms code..."
$codeResp = $null
for ($attempt = 0; $attempt -lt 5; $attempt++) {
  $codeResp = Invoke-JsonPost -Uri "$baseUrl/api/auth/code" -Body @{ phone = $Phone }
  if ($codeResp.code -eq 0 -and $codeResp.data -and $codeResp.data.devCode) {
    break
  }
  if ($codeResp.code -eq 1014 -and $attempt -lt 4) {
    Start-Sleep -Seconds 15
    continue
  }
  throw "Login failed: /api/auth/code returned code=$($codeResp.code), message=$($codeResp.message)"
}

$devCode = $codeResp.data.devCode
if (-not $devCode) {
  throw "Login failed: /api/auth/code did not return devCode after retries."
}

$loginResp = Invoke-JsonPost -Uri "$baseUrl/api/auth/login" -Body @{ phone = $Phone; code = $devCode }
$token = $loginResp.data.token
if (-not $token) {
  throw "Login failed: missing token."
}

$headers = @{ Authorization = "Bearer $token" }

if ($DocumentId -le 0) {
  Write-Host "[2/5] Find one SUCCESS document..."
  $listResp = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/document/list?pageNum=1&pageSize=20" -Headers $headers
  $records = @($listResp.data.records)
  $successDoc = $records | Where-Object { $_.parseStatus -eq "SUCCESS" } | Select-Object -First 1
  if (-not $successDoc) {
    throw "No SUCCESS document found. Upload and parse at least one document first."
  }
  $DocumentId = [int]$successDoc.documentId
}

Write-Host "[3/5] Request SSE stream..."
$sessionId = "smoke-stream-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$payload = @{
  documentId = $DocumentId
  question = $Question
  sessionId = $sessionId
} | ConvertTo-Json -Depth 6

Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(120)

$request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$baseUrl/api/ai/qa/stream")
$request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token)
$request.Headers.Accept.ParseAdd("text/event-stream")
$request.Content = [System.Net.Http.StringContent]::new($payload, [System.Text.Encoding]::UTF8, "application/json")

$response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
if (-not $response.IsSuccessStatusCode) {
  $errorBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
  throw "SSE endpoint failed: $([int]$response.StatusCode) $errorBody"
}

$contentType = ""
if ($response.Content.Headers.ContentType) {
  $contentType = $response.Content.Headers.ContentType.MediaType
}
if ($contentType -ne "text/event-stream") {
  throw "Invalid SSE content-type: $contentType"
}

Write-Host "[4/5] Read SSE events..."
$stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
$reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
$watch = [System.Diagnostics.Stopwatch]::StartNew()

$events = New-Object System.Collections.Generic.List[object]
$currentEvent = "message"
$dataLines = New-Object System.Collections.Generic.List[string]

while (-not $reader.EndOfStream) {
  $line = $reader.ReadLine()
  if ($null -eq $line) {
    break
  }

  if ($line -eq "") {
    if ($currentEvent -ne "message" -or $dataLines.Count -gt 0) {
      $events.Add([PSCustomObject]@{
          event = $currentEvent
          data = ($dataLines -join "`n")
          atMs = [int]$watch.ElapsedMilliseconds
        })
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
    if ($dataValue.StartsWith(" ")) {
      $dataValue = $dataValue.Substring(1)
    }
    $dataLines.Add($dataValue) | Out-Null
  }
}

$reader.Dispose()
$stream.Dispose()
$response.Dispose()
$client.Dispose()

Write-Host "[5/5] Validate stream events..."
$chunkEvents = @($events | Where-Object { $_.event -eq "chunk" })
$doneEvents = @($events | Where-Object { $_.event -eq "done" })
$errorEvents = @($events | Where-Object { $_.event -eq "error" })

if ($errorEvents.Count -gt 0) {
  throw "SSE returned error event: $($errorEvents[0].data)"
}
if ($chunkEvents.Count -lt 2) {
  throw "Not enough chunk events ($($chunkEvents.Count)); cannot prove streaming."
}
if ($doneEvents.Count -lt 1) {
  throw "Missing done event."
}

$distinctChunkMoments = @($chunkEvents | ForEach-Object { $_.atMs } | Sort-Object -Unique)
if ($distinctChunkMoments.Count -lt 2) {
  throw "Chunk timestamps are not separated; stream may be buffered."
}

$summary = [PSCustomObject]@{
  backendBaseUrl = $baseUrl
  documentId = $DocumentId
  contentType = $contentType
  chunkCount = $chunkEvents.Count
  doneCount = $doneEvents.Count
  firstChunkAtMs = $chunkEvents[0].atMs
  lastChunkAtMs = $chunkEvents[$chunkEvents.Count - 1].atMs
  distinctChunkMoments = $distinctChunkMoments
}

Write-Host "SSE smoke passed:"
$summary | ConvertTo-Json -Depth 6
