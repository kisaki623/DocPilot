param(
  [string]$EnvFile = "backend/.env",
  [string]$SshHost = "",
  [string]$SshUser = "root",
  [int]$SshPort = 1557,
  [string]$IdentityFile = "$env:USERPROFILE\.ssh\hmdp_ed25519",
  [int]$MySqlLocalPort = 13306,
  [int]$QdrantLocalPort = 6333,
  [int]$StartupTimeoutSeconds = 10,
  [switch]$SkipConnectivityCheck
)

$ErrorActionPreference = "Stop"

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

function Resolve-CloudHost($values) {
  $candidateKeys = @(
    "DOCPILOT_SSH_HOST",
    "CLOUD_HOST",
    "REDIS_HOST",
    "ROCKETMQ_NAME_SERVER",
    "MINIO_ENDPOINT"
  )

  foreach ($key in $candidateKeys) {
    if (-not $values.ContainsKey($key)) {
      continue
    }
    $value = [string]$values[$key]
    if ($value -match '([0-9]{1,3}(\.[0-9]{1,3}){3})') {
      $ip = $matches[1]
      if ($ip -ne "127.0.0.1") {
        return $ip
      }
    }
    if ($value -and $value -notmatch 'localhost|127\.0\.0\.1') {
      return ($value -replace '^https?://', '' -replace '[:/].*$', '')
    }
  }

  return ""
}

function Get-ListeningProcess([int]$port) {
  return Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
}

function Test-TcpPort([int]$port) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $async = $client.BeginConnect("127.0.0.1", $port, $null, $null)
    if (-not $async.AsyncWaitHandle.WaitOne(3000)) {
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

$envValues = Read-EnvFile $EnvFile
if (-not $SshHost) {
  $SshHost = Resolve-CloudHost $envValues
}

if (-not $SshHost) {
  throw "Cannot resolve SSH host. Set DOCPILOT_SSH_HOST in backend/.env or pass -SshHost."
}

if (-not (Test-Path -LiteralPath $IdentityFile)) {
  throw "Identity file not found. Pass -IdentityFile with a valid SSH key path."
}

$mysqlListener = Get-ListeningProcess $MySqlLocalPort
$qdrantListener = Get-ListeningProcess $QdrantLocalPort
if ($mysqlListener -and $qdrantListener) {
  Write-Output "cloud tunnels already listening: mysql=${MySqlLocalPort}, qdrant=${QdrantLocalPort}"
  return
}
if ($mysqlListener -or $qdrantListener) {
  throw "One tunnel port is already in use. mysql=${MySqlLocalPort} listen=$([bool]$mysqlListener), qdrant=${QdrantLocalPort} listen=$([bool]$qdrantListener)."
}

$sshArgs = @(
  "-N",
  "-T",
  "-o", "ExitOnForwardFailure=yes",
  "-o", "ServerAliveInterval=30",
  "-i", $IdentityFile,
  "-p", "$SshPort",
  "-L", "127.0.0.1:${MySqlLocalPort}:127.0.0.1:13306",
  "-L", "127.0.0.1:${QdrantLocalPort}:127.0.0.1:6333",
  "${SshUser}@${SshHost}"
)

$process = Start-Process -FilePath "ssh.exe" -ArgumentList $sshArgs -WindowStyle Hidden -PassThru
$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
do {
  Start-Sleep -Milliseconds 500
  $mysqlReady = Test-TcpPort $MySqlLocalPort
  $qdrantReady = Test-TcpPort $QdrantLocalPort
  if ($mysqlReady -and $qdrantReady) {
    break
  }
} while ((Get-Date) -lt $deadline)

if (-not ($mysqlReady -and $qdrantReady)) {
  Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
  throw "Failed to open cloud tunnels within ${StartupTimeoutSeconds}s."
}

$result = [ordered]@{
  sshPid = $process.Id
  sshHost = "<configured>"
  mysqlTunnel = "127.0.0.1:${MySqlLocalPort} -> remote 127.0.0.1:13306"
  qdrantTunnel = "127.0.0.1:${QdrantLocalPort} -> remote 127.0.0.1:6333"
  mysqlTcp = "PASS"
  qdrantTcp = "PASS"
}

if (-not $SkipConnectivityCheck) {
  try {
    $qdrant = Invoke-RestMethod -Uri "http://127.0.0.1:${QdrantLocalPort}/collections" -TimeoutSec 10
    $result.qdrantHttp = "PASS"
    $result.qdrantCollections = $qdrant.result.collections.Count
  } catch {
    $result.qdrantHttp = "SKIPPED_OR_FAILED"
  }

  $mysqlExe = Get-Command mysql -ErrorAction SilentlyContinue
  $mysqlUser = $envValues["MYSQL_USERNAME"]
  if (-not $mysqlUser) {
    $mysqlUser = $envValues["MYSQL_USER"]
  }
  $mysqlPassword = $envValues["MYSQL_PASSWORD"]
  $mysqlDatabase = $envValues["MYSQL_DB"]
  if (-not $mysqlDatabase) {
    $mysqlDatabase = $envValues["MYSQL_DATABASE"]
  }

  if ($mysqlExe -and $mysqlUser -and $mysqlPassword -and $mysqlDatabase) {
    $env:MYSQL_PWD = $mysqlPassword
    try {
      $mysqlOutput = & mysql --protocol=TCP -h 127.0.0.1 -P $MySqlLocalPort -u $mysqlUser $mysqlDatabase -N -e "SELECT 1;" 2>$null
      if ($LASTEXITCODE -eq 0 -and ($mysqlOutput -join "") -match "1") {
        $result.mysqlCli = "PASS"
      } else {
        $result.mysqlCli = "SKIPPED_OR_FAILED"
      }
    } finally {
      Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
  } else {
    $result.mysqlCli = "SKIPPED"
  }
}

[PSCustomObject]$result | Format-List
