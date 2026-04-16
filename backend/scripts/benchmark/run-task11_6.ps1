$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Resolve-Path (Join-Path $scriptDir "..\..")

Push-Location $backendDir
try {
    Write-Host "[Stage11.6] Running benchmark test..."
    mvn -q "-Dtest=com.docpilot.backend.benchmark.Task11_6BenchmarkTest" test

    $resultPath = Resolve-Path "..\docs\ai-dev\benchmarks\STAGE11_TASK11_6_RESULTS.md"
    $artifactPath = Resolve-Path "..\docs\ai-dev\benchmarks\artifacts\task11_6_latest.json"

    Write-Host "[Stage11.6] Result markdown: $resultPath"
    Write-Host "[Stage11.6] Raw artifact: $artifactPath"
}
finally {
    Pop-Location
}

