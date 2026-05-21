param(
  [switch]$AllowRequest,
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

function Select-EffectiveValue {
  param(
    [string]$Primary,
    [string]$Fallback
  )
  if (Test-Present -Value $Primary) {
    return $Primary
  }
  return $Fallback
}

function Write-Summary {
  param([hashtable]$Summary)
  [PSCustomObject]$Summary | ConvertTo-Json -Depth 4
}

$appProvider = $env:APP_RAG_VECTOR_STORE_PROVIDER
$appEndpoint = $env:APP_RAG_VECTOR_STORE_QDRANT_ENDPOINT
$appCollection = $env:APP_RAG_VECTOR_STORE_QDRANT_COLLECTION
$appApiKey = $env:APP_RAG_VECTOR_STORE_QDRANT_API_KEY
$appConnectTimeoutMs = $env:APP_RAG_VECTOR_STORE_QDRANT_CONNECT_TIMEOUT_MS
$appRequestTimeoutMs = $env:APP_RAG_VECTOR_STORE_QDRANT_REQUEST_TIMEOUT_MS

$legacyProvider = $env:RAG_VECTOR_STORE_PROVIDER
$legacyEndpoint = $env:RAG_QDRANT_ENDPOINT
$legacyCollection = $env:RAG_QDRANT_COLLECTION
$legacyApiKey = $env:RAG_QDRANT_API_KEY
$legacyConnectTimeoutMs = $env:RAG_QDRANT_CONNECT_TIMEOUT_MS
$legacyRequestTimeoutMs = $env:RAG_QDRANT_REQUEST_TIMEOUT_MS

$provider = Select-EffectiveValue -Primary $appProvider -Fallback $legacyProvider
$endpoint = Select-EffectiveValue -Primary $appEndpoint -Fallback $legacyEndpoint
$collection = Select-EffectiveValue -Primary $appCollection -Fallback $legacyCollection
$apiKey = Select-EffectiveValue -Primary $appApiKey -Fallback $legacyApiKey
$connectTimeoutMs = Select-EffectiveValue -Primary $appConnectTimeoutMs -Fallback $legacyConnectTimeoutMs
$requestTimeoutMs = Select-EffectiveValue -Primary $appRequestTimeoutMs -Fallback $legacyRequestTimeoutMs

$providerPresent = Test-Present -Value $provider
$endpointPresent = Test-Present -Value $endpoint
$collectionPresent = Test-Present -Value $collection
$apiKeyPresent = Test-Present -Value $apiKey
$connectTimeoutPresent = Test-Present -Value $connectTimeoutMs
$requestTimeoutPresent = Test-Present -Value $requestTimeoutMs
$appProviderPresent = Test-Present -Value $appProvider
$appEndpointPresent = Test-Present -Value $appEndpoint
$appCollectionPresent = Test-Present -Value $appCollection
$appApiKeyPresent = Test-Present -Value $appApiKey
$appConnectTimeoutPresent = Test-Present -Value $appConnectTimeoutMs
$appRequestTimeoutPresent = Test-Present -Value $appRequestTimeoutMs
$legacyProviderPresent = Test-Present -Value $legacyProvider
$legacyEndpointPresent = Test-Present -Value $legacyEndpoint
$legacyCollectionPresent = Test-Present -Value $legacyCollection
$legacyApiKeyPresent = Test-Present -Value $legacyApiKey
$legacyConnectTimeoutPresent = Test-Present -Value $legacyConnectTimeoutMs
$legacyRequestTimeoutPresent = Test-Present -Value $legacyRequestTimeoutMs
$isQdrant = $providerPresent -and $provider.Trim().ToLowerInvariant() -eq "qdrant"
$requestAllowed = [bool]$AllowRequest -and -not [bool]$SkipRequest -and -not [bool]$DryRun
$effectiveDryRun = -not $requestAllowed

if (-not $isQdrant) {
  Write-Summary @{
    status = "SKIPPED"
    providerIsQdrant = $false
    appProviderPresent = $appProviderPresent
    appEndpointPresent = $appEndpointPresent
    appCollectionPresent = $appCollectionPresent
    appApiKeyPresent = $appApiKeyPresent
    appConnectTimeoutPresent = $appConnectTimeoutPresent
    appRequestTimeoutPresent = $appRequestTimeoutPresent
    legacyProviderPresent = $legacyProviderPresent
    legacyEndpointPresent = $legacyEndpointPresent
    legacyCollectionPresent = $legacyCollectionPresent
    legacyApiKeyPresent = $legacyApiKeyPresent
    legacyConnectTimeoutPresent = $legacyConnectTimeoutPresent
    legacyRequestTimeoutPresent = $legacyRequestTimeoutPresent
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $false
    requestAllowed = $requestAllowed
    requestAttempted = $false
    dryRun = $effectiveDryRun
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
    appProviderPresent = $appProviderPresent
    appEndpointPresent = $appEndpointPresent
    appCollectionPresent = $appCollectionPresent
    appApiKeyPresent = $appApiKeyPresent
    appConnectTimeoutPresent = $appConnectTimeoutPresent
    appRequestTimeoutPresent = $appRequestTimeoutPresent
    legacyProviderPresent = $legacyProviderPresent
    legacyEndpointPresent = $legacyEndpointPresent
    legacyCollectionPresent = $legacyCollectionPresent
    legacyApiKeyPresent = $legacyApiKeyPresent
    legacyConnectTimeoutPresent = $legacyConnectTimeoutPresent
    legacyRequestTimeoutPresent = $legacyRequestTimeoutPresent
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $isLocalhost
    requestAllowed = $requestAllowed
    requestAttempted = $false
    dryRun = $effectiveDryRun
    allowCreateCollection = [bool]$AllowCreateCollection
    createAttempted = $false
  }
  exit 2
}

if (-not $requestAllowed) {
  Write-Summary @{
    status = "READY_DRY_RUN"
    providerIsQdrant = $true
    appProviderPresent = $appProviderPresent
    appEndpointPresent = $appEndpointPresent
    appCollectionPresent = $appCollectionPresent
    appApiKeyPresent = $appApiKeyPresent
    appConnectTimeoutPresent = $appConnectTimeoutPresent
    appRequestTimeoutPresent = $appRequestTimeoutPresent
    legacyProviderPresent = $legacyProviderPresent
    legacyEndpointPresent = $legacyEndpointPresent
    legacyCollectionPresent = $legacyCollectionPresent
    legacyApiKeyPresent = $legacyApiKeyPresent
    legacyConnectTimeoutPresent = $legacyConnectTimeoutPresent
    legacyRequestTimeoutPresent = $legacyRequestTimeoutPresent
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $isLocalhost
    requestAllowed = $requestAllowed
    requestAttempted = $false
    dryRun = $true
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
    appProviderPresent = $appProviderPresent
    appEndpointPresent = $appEndpointPresent
    appCollectionPresent = $appCollectionPresent
    appApiKeyPresent = $appApiKeyPresent
    appConnectTimeoutPresent = $appConnectTimeoutPresent
    appRequestTimeoutPresent = $appRequestTimeoutPresent
    legacyProviderPresent = $legacyProviderPresent
    legacyEndpointPresent = $legacyEndpointPresent
    legacyCollectionPresent = $legacyCollectionPresent
    legacyApiKeyPresent = $legacyApiKeyPresent
    legacyConnectTimeoutPresent = $legacyConnectTimeoutPresent
    legacyRequestTimeoutPresent = $legacyRequestTimeoutPresent
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $isLocalhost
    requestAllowed = $requestAllowed
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
        appProviderPresent = $appProviderPresent
        appEndpointPresent = $appEndpointPresent
        appCollectionPresent = $appCollectionPresent
        appApiKeyPresent = $appApiKeyPresent
        appConnectTimeoutPresent = $appConnectTimeoutPresent
        appRequestTimeoutPresent = $appRequestTimeoutPresent
        legacyProviderPresent = $legacyProviderPresent
        legacyEndpointPresent = $legacyEndpointPresent
        legacyCollectionPresent = $legacyCollectionPresent
        legacyApiKeyPresent = $legacyApiKeyPresent
        legacyConnectTimeoutPresent = $legacyConnectTimeoutPresent
        legacyRequestTimeoutPresent = $legacyRequestTimeoutPresent
        providerPresent = $providerPresent
        endpointPresent = $endpointPresent
        collectionPresent = $collectionPresent
        apiKeyPresent = $apiKeyPresent
        connectTimeoutPresent = $connectTimeoutPresent
        requestTimeoutPresent = $requestTimeoutPresent
        isLocalhost = $isLocalhost
        requestAllowed = $requestAllowed
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
        appProviderPresent = $appProviderPresent
        appEndpointPresent = $appEndpointPresent
        appCollectionPresent = $appCollectionPresent
        appApiKeyPresent = $appApiKeyPresent
        appConnectTimeoutPresent = $appConnectTimeoutPresent
        appRequestTimeoutPresent = $appRequestTimeoutPresent
        legacyProviderPresent = $legacyProviderPresent
        legacyEndpointPresent = $legacyEndpointPresent
        legacyCollectionPresent = $legacyCollectionPresent
        legacyApiKeyPresent = $legacyApiKeyPresent
        legacyConnectTimeoutPresent = $legacyConnectTimeoutPresent
        legacyRequestTimeoutPresent = $legacyRequestTimeoutPresent
        providerPresent = $providerPresent
        endpointPresent = $endpointPresent
        collectionPresent = $collectionPresent
        apiKeyPresent = $apiKeyPresent
        connectTimeoutPresent = $connectTimeoutPresent
        requestTimeoutPresent = $requestTimeoutPresent
        isLocalhost = $isLocalhost
        requestAllowed = $requestAllowed
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
    appProviderPresent = $appProviderPresent
    appEndpointPresent = $appEndpointPresent
    appCollectionPresent = $appCollectionPresent
    appApiKeyPresent = $appApiKeyPresent
    appConnectTimeoutPresent = $appConnectTimeoutPresent
    appRequestTimeoutPresent = $appRequestTimeoutPresent
    legacyProviderPresent = $legacyProviderPresent
    legacyEndpointPresent = $legacyEndpointPresent
    legacyCollectionPresent = $legacyCollectionPresent
    legacyApiKeyPresent = $legacyApiKeyPresent
    legacyConnectTimeoutPresent = $legacyConnectTimeoutPresent
    legacyRequestTimeoutPresent = $legacyRequestTimeoutPresent
    providerPresent = $providerPresent
    endpointPresent = $endpointPresent
    collectionPresent = $collectionPresent
    apiKeyPresent = $apiKeyPresent
    connectTimeoutPresent = $connectTimeoutPresent
    requestTimeoutPresent = $requestTimeoutPresent
    isLocalhost = $isLocalhost
    requestAllowed = $requestAllowed
    requestAttempted = $true
    dryRun = $false
    allowCreateCollection = [bool]$AllowCreateCollection
    createAttempted = $false
    errorType = $_.Exception.GetType().Name
  }
  exit 1
}
