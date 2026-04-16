Param(
    [int]$Port = 8081,
    [switch]$StopDocPilot
)

$ErrorActionPreference = "Stop"

function Get-Listener {
    Param(
        [int]$TargetPort
    )
    return Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Get-ProcessInfo {
    Param(
        [int]$ProcessId
    )
    try {
        return Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction Stop
    } catch {
        return $null
    }
}

function Is-DocPilotBackendProcess {
    Param(
        [string]$CommandLine
    )
    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return $false
    }
    return $CommandLine -like "*com.docpilot.backend.DocPilotApplication*" -or
        $CommandLine -like "*docpilot-backend*"
}

Write-Host "== Backend Port Preflight ==" -ForegroundColor Cyan
Write-Host "Checking port $Port ..."

$listener = Get-Listener -TargetPort $Port
if ($null -eq $listener) {
    Write-Host "[OK] Port $Port is free." -ForegroundColor Green
    exit 0
}

$ownerPid = $listener.OwningProcess
$procInfo = Get-ProcessInfo -ProcessId $ownerPid
$commandLine = if ($null -eq $procInfo) { "" } else { $procInfo.CommandLine }
$processName = if ($null -eq $procInfo) { "unknown" } else { $procInfo.Name }
$isDocPilot = Is-DocPilotBackendProcess -CommandLine $commandLine

Write-Host "[WARN] Port $Port is occupied by PID=$ownerPid Name=$processName" -ForegroundColor Yellow
if (-not [string]::IsNullOrWhiteSpace($commandLine)) {
    Write-Host "CommandLine:"
    Write-Host $commandLine
}

if ($StopDocPilot -and $isDocPilot) {
    Write-Host "StopDocPilot enabled. Stopping stale DocPilot backend process $ownerPid ..." -ForegroundColor Yellow
    Stop-Process -Id $ownerPid -Force
    Start-Sleep -Seconds 1
    $listenerAfterStop = Get-Listener -TargetPort $Port
    if ($null -eq $listenerAfterStop) {
        Write-Host "[OK] Stale DocPilot backend process stopped. Port $Port is now free." -ForegroundColor Green
        exit 0
    }
    Write-Host "[FAIL] Port $Port is still occupied after stop attempt." -ForegroundColor Red
    exit 1
}

if ($isDocPilot) {
    Write-Host "[WARN] A DocPilot backend instance is already running on port $Port." -ForegroundColor Yellow
    Write-Host "Use -StopDocPilot to stop it, or stop it from IDEA, then retry."
    exit 2
}

Write-Host "[FAIL] Port $Port is occupied by a non-DocPilot process. Stop that process or change SERVER_PORT." -ForegroundColor Red
exit 1
