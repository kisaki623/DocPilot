param(
  [switch]$SkipRequest,
  [switch]$DryRun,
  [switch]$AllowCreateCollection,
  [int]$VectorSize = 1536,
  [string]$Distance = "Cosine"
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
$connectTimeoutMs = $env:RAG_QDRANT_CONNECT_TIMEOUT_MS
$requestTimeoutMs = $env:RAG_QDRANT_REQUEST_TIMEOUT_MS

$providerPresent = Test-Present -Value $provider
$endpointPresent = Test-Present -Value $endpoint
$collectionPresent = Test-Present -Value $collection
$apiKeyPresent = Test-Present -Value $apiKey
$connectTimeoutPresent = Test-Present -Value $connectTimeoutMs
$requestTimeoutPresent = Test-Present -Value $requestTimeoutMs
$isQdrant = $providerPresent -and $provider.Trim().ToLowerInvariant() -eq "qdrant"

if (-not $isQdrant) {
  Write-Summary @{
    status = "SKIPPED"
    providerIsQdrant = $false
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $false
    requestAttempted = $false
    dryRun = [bool]$DryRun
    allowCreateCollection = [bool]$AllowCreateCollection
    createAttempted = $false
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
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $isLocalhost
    requestAttempted = $false
    dryRun = [bool]$DryRun
    allowCreateCollection = [bool]$AllowCreateCollection
    createAttempted = $false
  }
  exit 2
}

if ($SkipRequest -or $DryRun) {
  Write-Summary @{
    status = "READY"
    providerIsQdrant = $true
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $isLocalhost
    requestAttempted = $false
    dryRun = [bool]$DryRun
    allowCreateCollection = [bool]$AllowCreateCollection
    createAttempted = $false
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
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $isLocalhost
    requestAttempted = $true
    dryRun = $false
    allowCreateCollection = [bool]$AllowCreateCollection
    createAttempted = $false
    statusCode = $response.StatusCode
  }
  exit 0
} catch {
  $statusCode = $null
  if ($_.Exception.Response -ne $null) {
    try {
      $statusCode = [int]$_.Exception.Response.StatusCode
    } catch {
      $statusCode = $null
    }
  }
  if ($statusCode -eq 404 -and $AllowCreateCollection) {
    $createPayload = @{
      vectors = @{
        size = $VectorSize
        distance = $Distance
      }
    } | ConvertTo-Json -Depth 4
    try {
      $createResponse = Invoke-WebRequest `
        -Method Put `
        -Uri "$baseEndpoint/collections/$escapedCollection" `
        -Headers $headers `
        -Body $createPayload `
        -ContentType "application/json" `
        -UseBasicParsing `
        -TimeoutSec 5

      Write-Summary @{
        status = "CREATED"
        providerIsQdrant = $true
        providerPresent = $providerPresent
        endpointPresent = $endpointPresent
        collectionPresent = $collectionPresent
        apiKeyPresent = $apiKeyPresent
        connectTimeoutPresent = $connectTimeoutPresent
        requestTimeoutPresent = $requestTimeoutPresent
        isLocalhost = $isLocalhost
        requestAttempted = $true
        dryRun = $false
        allowCreateCollection = $true
        createAttempted = $true
        statusCode = $createResponse.StatusCode
      }
      exit 0
    } catch {
      Write-Summary @{
        status = "CREATE_FAILED"
        providerIsQdrant = $true
        providerPresent = $providerPresent
        endpointPresent = $endpointPresent
        collectionPresent = $collectionPresent
        apiKeyPresent = $apiKeyPresent
        connectTimeoutPresent = $connectTimeoutPresent
        requestTimeoutPresent = $requestTimeoutPresent
        isLocalhost = $isLocalhost
        requestAttempted = $true
        dryRun = $false
        allowCreateCollection = $true
        createAttempted = $true
        errorType = $_.Exception.GetType().Name
      }
      exit 1
    }
  }
  Write-Summary @{
    status = "FAILED"
    providerIsQdrant = $true
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $isLocalhost
    requestAttempted = $true
    dryRun = $false
    allowCreateCollection = [bool]$AllowCreateCollection
    createAttempted = $false
    errorType = $_.Exception.GetType().Name
  }
  exit 1
}
