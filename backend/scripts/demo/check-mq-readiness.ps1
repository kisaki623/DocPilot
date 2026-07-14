param(
  [switch]$Help,
  [switch]$Json
)

$ErrorActionPreference = "Stop"

if ($Help) {
  Write-Host "Read-only T010/MQ readiness diagnosis. Does not read .env, start services, or connect to middleware."
  Write-Host "Usage: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo/check-mq-readiness.ps1 [-Json]"
  Write-Host "Output is sanitized: environment variable names and true/false presence only."
  exit 0
}

function Test-EnvPresent {
  param([string]$Name)
  $value = [System.Environment]::GetEnvironmentVariable($Name)
  return -not [string]::IsNullOrWhiteSpace($value)
}

function New-EnvCheck {
  param(
    [string]$Name,
    [string]$Purpose,
    [bool]$RequiredForFullRuntime = $true
  )
  return [PSCustomObject]@{
    name = $Name
    present = Test-EnvPresent -Name $Name
    requiredForFullRuntime = $RequiredForFullRuntime
    purpose = $Purpose
  }
}

$checks = @(
  New-EnvCheck -Name "ROCKETMQ_ENABLED" -Purpose "Must be true for RocketMQ producer and consumer beans"
  New-EnvCheck -Name "ROCKETMQ_NAME_SERVER" -Purpose "RocketMQ NameServer location"
  New-EnvCheck -Name "ROCKETMQ_PRODUCER_GROUP" -Purpose "RocketMQ producer group"
  New-EnvCheck -Name "ROCKETMQ_CONSUMER_GROUP" -Purpose "RocketMQ consumer group"
  New-EnvCheck -Name "DOCPILOT_PARSE_TOPIC" -Purpose "Parse task topic"
  New-EnvCheck -Name "MYSQL_HOST" -Purpose "MySQL host"
  New-EnvCheck -Name "MYSQL_PORT" -Purpose "MySQL port"
  New-EnvCheck -Name "MYSQL_DATABASE" -Purpose "MySQL database name"
  New-EnvCheck -Name "MYSQL_USERNAME" -Purpose "MySQL user name"
  New-EnvCheck -Name "MYSQL_PASSWORD" -Purpose "MySQL password"
  New-EnvCheck -Name "REDIS_HOST" -Purpose "Redis host"
  New-EnvCheck -Name "REDIS_PORT" -Purpose "Redis port"
  New-EnvCheck -Name "REDIS_PASSWORD" -Purpose "Redis password or empty-password mode" -RequiredForFullRuntime $false
  New-EnvCheck -Name "FILE_STORAGE_MODE" -Purpose "local or minio storage mode"
  New-EnvCheck -Name "FILE_UPLOAD_DIR" -Purpose "local file storage directory" -RequiredForFullRuntime $false
  New-EnvCheck -Name "MINIO_ENDPOINT" -Purpose "MinIO endpoint when storage mode is minio" -RequiredForFullRuntime $false
  New-EnvCheck -Name "MINIO_ACCESS_KEY" -Purpose "MinIO access key when storage mode is minio" -RequiredForFullRuntime $false
  New-EnvCheck -Name "MINIO_SECRET_KEY" -Purpose "MinIO secret key when storage mode is minio" -RequiredForFullRuntime $false
  New-EnvCheck -Name "MINIO_BUCKET" -Purpose "MinIO bucket when storage mode is minio" -RequiredForFullRuntime $false
)

$rocketMqEnabledPresent = Test-EnvPresent -Name "ROCKETMQ_ENABLED"
$rocketMqEnabledValue = [System.Environment]::GetEnvironmentVariable("ROCKETMQ_ENABLED")
$rocketMqExplicitlyTrue = $rocketMqEnabledPresent -and "true".Equals($rocketMqEnabledValue, [System.StringComparison]::OrdinalIgnoreCase)

$requiredMissing = @($checks | Where-Object { $_.requiredForFullRuntime -and -not $_.present } | ForEach-Object { $_.name })
$summary = [PSCustomObject]@{
  diagnosis = "t010-mq-readiness"
  mode = "read-only"
  status = "BLOCKED"
  blocked = $true
  backendServiceStarted = $false
  middlewareConnectionAttempted = $false
  envFileRead = $false
  currentDefault = "MQ disabled unless ROCKETMQ_ENABLED=true is injected into Spring configuration"
  noopProducerCondition = "NoopParseTaskMessageProducer is active when app.rocketmq.enabled=false or missing"
  realProducerConsumerCondition = "RocketMqParseTaskMessageProducer and ParseTaskMessageConsumer are active only when app.rocketmq.enabled=true"
  rocketMqEnabledPresent = $rocketMqEnabledPresent
  rocketMqEnabledTrue = $rocketMqExplicitlyTrue
  envChecks = $checks
  requiredMissingNames = $requiredMissing
  middlewareRequired = @("MySQL", "Redis", "RocketMQ NameServer/Broker", "MinIO or local file storage")
  validationSteps = @(
    "Inject required environment variables in the shell or runtime configuration",
    "Start MySQL, Redis, RocketMQ NameServer/Broker, and configured file storage",
    "Start backend with local profile after environment is ready",
    "Upload a document and create parse task through the normal API path",
    "Verify outbox dispatch, RocketMQ producer send, consumer receive, parse status transition, and document detail availability"
  )
  note = "This script does not fix MQ and does not mark T010 done."
}

if ($Json) {
  $summary | ConvertTo-Json -Depth 6
  exit 0
}

Write-Host "T010/MQ readiness diagnosis"
Write-Host "mode=read-only"
Write-Host "status=BLOCKED"
Write-Host "backendServiceStarted=false"
Write-Host "middlewareConnectionAttempted=false"
Write-Host "envFileRead=false"
Write-Host "currentDefault=MQ disabled unless ROCKETMQ_ENABLED=true is injected"
Write-Host "noopProducerCondition=app.rocketmq.enabled false or missing"
Write-Host "realProducerConsumerCondition=app.rocketmq.enabled true"
Write-Host "rocketMqEnabledPresent=$rocketMqEnabledPresent"
Write-Host "rocketMqEnabledTrue=$rocketMqExplicitlyTrue"
Write-Host "requiredMissingNames=$($requiredMissing -join ',')"
Write-Host "middlewareRequired=MySQL,Redis,RocketMQ NameServer/Broker,MinIO or local file storage"
Write-Host "validationSteps=inject env vars; start middleware; start backend; upload document; verify outbox producer consumer parse status"
Write-Host "note=This script does not fix MQ and does not mark T010 done."
