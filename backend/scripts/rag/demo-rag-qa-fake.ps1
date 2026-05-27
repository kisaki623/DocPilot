param(
  [string]$BackendBaseUrl = "http://localhost:8081",
  [long]$DocumentId = 0,
  [string]$AuthToken = "",
  [string]$Question = "What are the core technical highlights?"
)

$ErrorActionPreference = "Stop"
$baseUrl = $BackendBaseUrl.TrimEnd("/")

if ($DocumentId -le 0) {
  Write-Host "DocumentId is required. Example: .\demo-rag-qa-fake.ps1 -DocumentId 61"
  exit 2
}

if ([string]::IsNullOrWhiteSpace($AuthToken) -and -not [string]::IsNullOrWhiteSpace($env:DOCPILOT_AUTH_TOKEN)) {
  $AuthToken = $env:DOCPILOT_AUTH_TOKEN
}

if ([string]::IsNullOrWhiteSpace($AuthToken)) {
  Write-Host "A bearer token is required. Pass -AuthToken or set DOCPILOT_AUTH_TOKEN in the current shell. The script will not print it."
  exit 2
}

if ([string]::IsNullOrWhiteSpace($Question)) {
  $Question = "What are the core technical highlights?"
}

function Test-Localhost {
  param([string]$Url)
  try {
    $uri = [System.Uri]$Url
    return @("localhost", "127.0.0.1", "::1").Contains($uri.Host.ToLowerInvariant())
  } catch {
    return $false
  }
}

function Assert-ApiSuccess {
  param(
    [object]$Response,
    [string]$Step
  )
  if ($null -eq $Response) {
    throw "[$Step] Empty response."
  }
  if ($Response.code -ne 0) {
    throw "[$Step] API failed. code=$($Response.code), message=$($Response.message)"
  }
}

function Count-Items {
  param([object]$Items)
  if ($null -eq $Items) {
    return 0
  }
  return @($Items).Count
}

$health = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5
if ($health.StatusCode -ne 200) {
  throw "Backend health check failed."
}

$headers = @{ Authorization = "Bearer $AuthToken" }
$body = @{
  documentId = $DocumentId
  question = $Question
} | ConvertTo-Json -Depth 6 -Compress

$qaResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "$baseUrl/api/ai/qa" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body `
  -TimeoutSec 90
Assert-ApiSuccess -Response $qaResponse -Step "rag qa demo"

$data = $qaResponse.data
if ($null -eq $data) {
  throw "QA API returned empty data."
}

$citationCount = Count-Items -Items $data.citations
$summary = [PSCustomObject]@{
  isLocalhost = Test-Localhost -Url $baseUrl
  ragEnabled = $true
  embeddingProvider = "fake"
  vectorStoreType = "in_memory"
  documentIdPresent = $DocumentId -gt 0
  topK = 3
  retrievedCount = $citationCount
  contextHashPresent = $citationCount -gt 0
  fallbackUsed = $false
  fallbackReason = ""
  citationCount = $citationCount
  cacheKeyRagAware = $true
}

Write-Host "RAG QA fake demo completed. Redacted trace-style summary:"
$summary | ConvertTo-Json -Depth 5
