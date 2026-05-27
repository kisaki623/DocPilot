param()

$ErrorActionPreference = "Stop"

function Test-Present {
  param([string]$Value)
  return -not [string]::IsNullOrWhiteSpace($Value)
}

function Write-Summary {
  param([hashtable]$Summary)
  [PSCustomObject]$Summary | ConvertTo-Json -Depth 4
}

$provider = $env:APP_RAG_EMBEDDING_PROVIDER
$baseUrl = $env:APP_RAG_EMBEDDING_BASE_URL
$model = $env:APP_RAG_EMBEDDING_MODEL
$apiKey = $env:APP_RAG_EMBEDDING_API_KEY
$connectTimeoutMs = $env:APP_RAG_EMBEDDING_CONNECT_TIMEOUT_MS
$requestTimeoutMs = $env:APP_RAG_EMBEDDING_REQUEST_TIMEOUT_MS
$dimension = $env:APP_RAG_EMBEDDING_DIMENSION

$providerPresent = Test-Present -Value $provider
$baseUrlPresent = Test-Present -Value $baseUrl
$modelPresent = Test-Present -Value $model
$apiKeyPresent = Test-Present -Value $apiKey
$connectTimeoutPresent = Test-Present -Value $connectTimeoutMs
$requestTimeoutPresent = Test-Present -Value $requestTimeoutMs
$dimensionPresent = Test-Present -Value $dimension

$normalizedProvider = if ($providerPresent) { $provider.Trim().ToLowerInvariant() } else { "" }
$providerIsOpenAiCompatible = @("openai_compatible", "openai-compatible", "openaicompatible").Contains($normalizedProvider)
$providerIsFake = $normalizedProvider -eq "fake"
$providerIsDisabled = $normalizedProvider -eq "disabled"
$requiredConfigPresent = $providerPresent -and $baseUrlPresent -and $modelPresent -and $apiKeyPresent

$status = "BLOCKED"
if ($providerIsFake -or $providerIsDisabled) {
  $status = "SKIPPED"
} elseif ($providerIsOpenAiCompatible -and $requiredConfigPresent) {
  $status = "READY_DRY_RUN"
}

Write-Summary @{
  status = $status
  providerPresent = $providerPresent
  baseUrlPresent = $baseUrlPresent
  modelPresent = $modelPresent
  apiKeyPresent = $apiKeyPresent
  connectTimeoutPresent = $connectTimeoutPresent
  requestTimeoutPresent = $requestTimeoutPresent
  dimensionPresent = $dimensionPresent
  providerIsOpenAiCompatible = $providerIsOpenAiCompatible
  providerIsFake = $providerIsFake
  providerIsDisabled = $providerIsDisabled
  requiredConfigPresent = $requiredConfigPresent
  realEmbeddingRuntimeBlocked = -not ($providerIsOpenAiCompatible -and $requiredConfigPresent)
  httpAttempted = $false
  dryRun = $true
}

if ($providerIsOpenAiCompatible -and $requiredConfigPresent) {
  exit 0
}

if ($providerIsFake -or $providerIsDisabled) {
  exit 0
}

exit 2
