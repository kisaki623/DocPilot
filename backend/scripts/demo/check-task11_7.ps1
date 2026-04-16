Param(
    [switch]$CheckPrometheus,
    [ValidateSet("local", "cloud")]
    [string]$Mode = "local",
    [string]$CloudHost = "116.204.132.136",
    [string]$BackendHost = "127.0.0.1",
    [int]$BackendPort = 8081,
    [switch]$SkipRocketMQ,
    [switch]$SkipMinio
)

$ErrorActionPreference = "Stop"

function Test-Port {
    Param(
        [string]$TargetHost,
        [int]$Port
    )

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        $success = $iar.AsyncWaitHandle.WaitOne(1200, $false)
        if (-not $success) {
            $client.Close()
            return $false
        }
        $client.EndConnect($iar)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

Write-Host "== DocPilot 11.7 environment check start ==" -ForegroundColor Cyan

function First-NonEmpty {
    Param(
        [string[]]$Candidates,
        [string]$DefaultValue
    )

    foreach ($candidate in $Candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            return $candidate
        }
    }
    return $DefaultValue
}

function Parse-HostPort {
    Param(
        [string]$InputValue,
        [string]$DefaultHost,
        [int]$DefaultPort
    )

    $result = @{
        Host = $DefaultHost
        Port = $DefaultPort
    }

    if ([string]::IsNullOrWhiteSpace($InputValue)) {
        return $result
    }

    if ($InputValue -match "^[a-zA-Z]+://") {
        try {
            $uri = [System.Uri]$InputValue
            $result.Host = $uri.Host
            $result.Port = if ($uri.Port -gt 0) { $uri.Port } else { $DefaultPort }
            return $result
        } catch {
            return $result
        }
    }

    $parts = $InputValue.Split(":")
    if ($parts.Length -ge 1 -and -not [string]::IsNullOrWhiteSpace($parts[0])) {
        $result.Host = $parts[0]
    }
    if ($parts.Length -ge 2) {
        $parsedPort = 0
        if ([int]::TryParse($parts[1], [ref]$parsedPort)) {
            $result.Port = $parsedPort
        }
    }

    return $result
}

$requiredPorts = @()
$backendHost = $BackendHost
$backendPort = $BackendPort

if ($Mode -eq "local") {
    $requiredPorts += @{ Name = "MySQL"; Host = "127.0.0.1"; Port = 3306 }
    $requiredPorts += @{ Name = "Redis"; Host = "127.0.0.1"; Port = 6379 }
    if (-not $SkipRocketMQ) {
        $requiredPorts += @{ Name = "RocketMQ NameServer"; Host = "127.0.0.1"; Port = 9876 }
    }
    if (-not $SkipMinio) {
        $requiredPorts += @{ Name = "MinIO API"; Host = "127.0.0.1"; Port = 9000 }
        $requiredPorts += @{ Name = "MinIO Console"; Host = "127.0.0.1"; Port = 9001 }
    }
} else {
    $mysqlHost = First-NonEmpty -Candidates @($env:MYSQL_HOST) -DefaultValue $CloudHost
    $mysqlPort = First-NonEmpty -Candidates @($env:MYSQL_PORT) -DefaultValue "3306"
    $redisHost = First-NonEmpty -Candidates @($env:REDIS_HOST) -DefaultValue $CloudHost
    $redisPort = First-NonEmpty -Candidates @($env:REDIS_PORT) -DefaultValue "6379"

    $requiredPorts += @{ Name = "MySQL"; Host = $mysqlHost; Port = [int]$mysqlPort }
    $requiredPorts += @{ Name = "Redis"; Host = $redisHost; Port = [int]$redisPort }

    if (-not $SkipRocketMQ) {
        $nameSrv = Parse-HostPort -InputValue $env:ROCKETMQ_NAME_SERVER -DefaultHost $CloudHost -DefaultPort 9876
        $requiredPorts += @{ Name = "RocketMQ NameServer"; Host = $nameSrv.Host; Port = $nameSrv.Port }
    }

    if (-not $SkipMinio) {
        $minioEndpoint = Parse-HostPort -InputValue $env:MINIO_ENDPOINT -DefaultHost $CloudHost -DefaultPort 9000
        $requiredPorts += @{ Name = "MinIO API"; Host = $minioEndpoint.Host; Port = $minioEndpoint.Port }
    }
}

$failed = @()
foreach ($item in $requiredPorts) {
    $ok = Test-Port -TargetHost $item.Host -Port $item.Port
    if ($ok) {
        Write-Host "[OK] $($item.Name) port $($item.Port) reachable"
    } else {
        Write-Host "[FAIL] $($item.Name) port $($item.Port) unreachable" -ForegroundColor Yellow
        $failed += $item.Name
    }
}

try {
    $health = Invoke-WebRequest -Uri "http://${backendHost}:${backendPort}/actuator/health" -UseBasicParsing -TimeoutSec 3
    Write-Host "[OK] backend health endpoint reachable: /actuator/health"
} catch {
    Write-Host "[WARN] backend health endpoint unreachable: ensure backend is running on ${backendPort}" -ForegroundColor Yellow
}

try {
    $metrics = Invoke-WebRequest -Uri "http://${backendHost}:${backendPort}/actuator/prometheus" -UseBasicParsing -TimeoutSec 3
    Write-Host "[OK] prometheus endpoint reachable: /actuator/prometheus"
} catch {
    Write-Host "[WARN] prometheus endpoint unreachable: ensure backend is running" -ForegroundColor Yellow
}

if ($CheckPrometheus) {
    if (Test-Port -TargetHost "127.0.0.1" -Port 9090) {
        Write-Host "[OK] Prometheus UI port 9090 reachable"
    } else {
        Write-Host "[WARN] Prometheus UI port 9090 unreachable" -ForegroundColor Yellow
    }
}

if ($failed.Count -gt 0) {
    Write-Host "== Environment check done: some dependencies are not ready ==" -ForegroundColor Yellow
    exit 1
}

Write-Host "== Environment check done: core dependency ports are ready ==" -ForegroundColor Green



