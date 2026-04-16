param(
  [switch]$DryRun
)

$ErrorActionPreference = 'SilentlyContinue'

$workspace = (Get-Location).Path
$patterns = @(
  '*next dev*',
  '*spring-boot:run*',
  '*@playwright/mcp*',
  '*playwright-mcp*',
  '*mcp-chrome*',
  '*frontend\\node_modules\\next\\dist\\server\\lib\\start-server.js*'
)

function Match-AnyPattern([string]$text, [string[]]$pats) {
  foreach ($pat in $pats) {
    if ($text -like $pat) {
      return $true
    }
  }
  return $false
}

$targets = Get-CimInstance Win32_Process | Where-Object {
  $cmd = $_.CommandLine
  if (-not $cmd) { return $false }

  $inWorkspace = $cmd -like "*$workspace*"
  $mcpRelated = ($cmd -like '*@playwright/mcp*') -or ($cmd -like '*playwright-mcp*') -or ($cmd -like '*mcp-chrome*')

  (Match-AnyPattern $cmd $patterns) -and ($inWorkspace -or $mcpRelated)
}

$killed = @()
foreach ($p in $targets) {
  if ($DryRun) {
    $killed += [PSCustomObject]@{
      ProcessId = $p.ProcessId
      Name = $p.Name
      Action = 'would_kill'
    }
    continue
  }

  try {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
    $killed += [PSCustomObject]@{
      ProcessId = $p.ProcessId
      Name = $p.Name
      Action = 'killed'
    }
  } catch {
    $killed += [PSCustomObject]@{
      ProcessId = $p.ProcessId
      Name = $p.Name
      Action = 'failed'
    }
  }
}

Write-Output '=== Cleanup Result ==='
if ($killed.Count -eq 0) {
  Write-Output 'No target process found.'
} else {
  $killed | Sort-Object ProcessId | Format-Table -AutoSize
}

Write-Output "`n=== Port Status ==="
$ports = @(3000, 3001, 3002, 3100, 8081)
foreach ($port in $ports) {
  $conn = Get-NetTCPConnection -LocalPort $port -State Listen
  if ($conn) {
    Write-Output "port ${port}: LISTEN"
  } else {
    Write-Output "port ${port}: FREE"
  }
}

Write-Output "`n=== Residual Check ==="
$residual = Get-CimInstance Win32_Process | Where-Object {
  $cmd = $_.CommandLine
  if (-not $cmd) { return $false }
  $inWorkspace = $cmd -like "*$workspace*"
  $mcpRelated = ($cmd -like '*@playwright/mcp*') -or ($cmd -like '*playwright-mcp*') -or ($cmd -like '*mcp-chrome*')
  (Match-AnyPattern $cmd $patterns) -and ($inWorkspace -or $mcpRelated)
}

if ($residual) {
  $residual | Select-Object ProcessId, Name, CommandLine | Sort-Object ProcessId | Format-Table -AutoSize
} else {
  Write-Output 'No residual process.'
}
