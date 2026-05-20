param(
  [switch]$SkipRequest
)

$ErrorActionPreference = "Stop"

function Test-Localhost {
  param([string]$Url)
  try {
    $uri = [System.Uri]$Url
    return @("localhost", "127.0.0.1", "::1").Contains($uri.Host.ToLowerInvariant())
  } catch {
    return $false
  }
}

function Test-Present {
  param([string]$Value)
  return -not [string]::IsNullOrWhiteSpace($Value)
}

function Write-Summary {
  param([hashtable]$Summary)
  [PSCustomObject]$Summary | ConvertTo-Json -Depth 4
}

$provider = $env:RAG_VECTOR_STORE_PROVIDER
$endpoint = $env:RAG_QDRANT_ENDPOINT
$collection = $env:RAG_QDRANT_COLLECTION
$apiKey = $env:RAG_QDRANT_API_KEY

$providerPresent = Test-Present -Value $provider
$endpointPresent = Test-Present -Value $endpoint
$collectionPresent = Test-Present -Value $collection
$apiKeyPresent = Test-Present -Value $apiKey
$isQdrant = $providerPresent -and $provider.Trim().ToLowerInvariant() -eq "qdrant"

if (-not $isQdrant) {
  Write-Summary @{
    status = "SKIPPED"
    providerIsQdrant = $false
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    isLocalhost = $false
    requestAttempted = $false
  }
  exit 0
}

$isLocalhost = Test-Localhost -Url $endpoint

if (-not $endpointPresent -or -not $collectionPresent) {
  Write-Summary @{
    status = "BLOCKED"
    providerIsQdrant = $true
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    isLocalhost = $isLocalhost
    requestAttempted = $false
  }
  exit 2
}

if ($SkipRequest) {
  Write-Summary @{
    status = "READY"
    providerIsQdrant = $true
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    isLocalhost = $isLocalhost
    requestAttempted = $false
  }
  exit 0
}

$baseEndpoint = $endpoint.TrimEnd("/")
$escapedCollection = [System.Uri]::EscapeDataString($collection)
$headers = @{}
if ($apiKeyPresent) {
  $headers["api-key"] = $apiKey
}

try {
  $response = Invoke-WebRequest `
    -Method Get `
    -Uri "$baseEndpoint/collections/$escapedCollection" `
    -Headers $headers `
    -UseBasicParsing `
    -TimeoutSec 5

  Write-Summary @{
    status = "OK"
    providerIsQdrant = $true
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    isLocalhost = $isLocalhost
    requestAttempted = $true
    statusCode = $response.StatusCode
  }
  exit 0
} catch {
  Write-Summary @{
    status = "FAILED"
    providerIsQdrant = $true
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    isLocalhost = $isLocalhost
    requestAttempted = $true
    errorType = $_.Exception.GetType().Name
  }
  exit 1
}
