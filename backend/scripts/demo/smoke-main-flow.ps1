Param(
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$Phone,
    [string]$FilePath,
    [string]$Question = "Please summarize the core content of this document.",
    [int]$ParseTimeoutSeconds = 120,
    [int]$PollIntervalSeconds = 2
)

$ErrorActionPreference = "Stop"

function Assert-ApiSuccess {
    Param(
        [object]$Response,
        [string]$Step
    )

    if ($null -eq $Response) {
        throw "[$Step] Empty response."
    }
    if ($null -eq $Response.code) {
        throw "[$Step] Invalid response envelope: missing code."
    }
    if ($Response.code -ne 0) {
        throw "[$Step] API failed. code=$($Response.code), message=$($Response.message)"
    }
}

function Invoke-JsonPost {
    Param(
        [string]$Uri,
        [hashtable]$Body,
        [hashtable]$Headers = @{}
    )

    $jsonBody = $Body | ConvertTo-Json -Depth 10 -Compress
    return Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType "application/json" -Body $jsonBody -TimeoutSec 20
}

function Invoke-FileUpload {
    Param(
        [string]$Uri,
        [string]$Token,
        [string]$ResolvedFilePath
    )

    $curl = Get-Command curl.exe -ErrorAction Stop
    if ($null -eq $curl) {
        throw "curl.exe is required for multipart upload."
    }

    $args = @(
        "-sS",
        "-X", "POST",
        $Uri,
        "-H", "Authorization: Bearer $Token",
        "-F", "file=@$ResolvedFilePath"
    )
    $raw = & curl.exe @args
    if ($LASTEXITCODE -ne 0) {
        throw "File upload failed: curl exited with code $LASTEXITCODE."
    }
    try {
        return $raw | ConvertFrom-Json
    } catch {
        throw "File upload returned non-JSON response: $raw"
    }
}

function New-RandomPhone {
    $tail = Get-Date -Format "MMddHHmmss"
    return "1$tail"
}

function Wait-ParseSuccess {
    Param(
        [string]$BaseUrl,
        [long]$DocumentId,
        [hashtable]$Headers,
        [int]$TimeoutSeconds,
        [int]$IntervalSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $detailResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/document/detail?documentId=$DocumentId" -Headers $Headers -TimeoutSec 20
        Assert-ApiSuccess -Response $detailResp -Step "document detail poll"

        $status = [string]$detailResp.data.parseStatus
        Write-Host "Parse status: $status"

        if ($status -eq "SUCCESS") {
            return $detailResp
        }
        if ($status -eq "FAILED") {
            $desc = [string]$detailResp.data.parseStatusDescription
            throw "Parse failed. statusDescription=$desc"
        }

        Start-Sleep -Seconds $IntervalSeconds
    } while ((Get-Date) -lt $deadline)

    throw "Parse timeout after $TimeoutSeconds seconds. Increase -ParseTimeoutSeconds or check MQ/consumer."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..\..")

if ([string]::IsNullOrWhiteSpace($Phone)) {
    $Phone = New-RandomPhone
}

if ([string]::IsNullOrWhiteSpace($FilePath)) {
    $FilePath = Join-Path $repoRoot "README.md"
}

$resolvedFile = Resolve-Path -LiteralPath $FilePath -ErrorAction Stop
$resolvedFilePath = $resolvedFile.Path

if ($PollIntervalSeconds -lt 1) {
    throw "Poll interval must be >= 1 second."
}

Write-Host "== DocPilot main-flow smoke start =="
Write-Host "BaseUrl: $BaseUrl"
Write-Host "Phone: $Phone"
Write-Host "File: $resolvedFilePath"

try {
    $health = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5
    Write-Host "[OK] Backend health reachable."
} catch {
    throw "Backend is not ready at $BaseUrl. Start backend first."
}

$sendCodeResp = Invoke-JsonPost -Uri "$BaseUrl/api/auth/code" -Body @{ phone = $Phone }
Assert-ApiSuccess -Response $sendCodeResp -Step "send code"
$devCode = [string]$sendCodeResp.data.devCode
if ([string]::IsNullOrWhiteSpace($devCode)) {
    throw "[send code] Missing devCode in response."
}
Write-Host "[OK] send code"

$loginResp = Invoke-JsonPost -Uri "$BaseUrl/api/auth/login" -Body @{ phone = $Phone; code = $devCode }
Assert-ApiSuccess -Response $loginResp -Step "login"
$token = [string]$loginResp.data.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "[login] Missing token."
}
$headers = @{ Authorization = "Bearer $token" }
Write-Host "[OK] login"

$uploadResp = Invoke-FileUpload -Uri "$BaseUrl/api/file/upload" -Token $token -ResolvedFilePath $resolvedFilePath
Assert-ApiSuccess -Response $uploadResp -Step "file upload"
$fileRecordId = [long]$uploadResp.data.id
if ($fileRecordId -le 0) {
    throw "[file upload] Invalid fileRecordId: $fileRecordId"
}
Write-Host "[OK] file upload, fileRecordId=$fileRecordId"

$createDocResp = Invoke-JsonPost -Uri "$BaseUrl/api/document/create" -Headers $headers -Body @{ fileRecordId = $fileRecordId }
Assert-ApiSuccess -Response $createDocResp -Step "document create"
$documentId = [long]$createDocResp.data.id
if ($documentId -le 0) {
    throw "[document create] Invalid documentId: $documentId"
}
Write-Host "[OK] document create, documentId=$documentId"

$createTaskResp = Invoke-JsonPost -Uri "$BaseUrl/api/task/parse/create" -Headers $headers -Body @{ documentId = $documentId }
Assert-ApiSuccess -Response $createTaskResp -Step "parse create"
$taskId = [long]$createTaskResp.data.taskId
Write-Host "[OK] parse create, taskId=$taskId"

$detailResp = Wait-ParseSuccess -BaseUrl $BaseUrl -DocumentId $documentId -Headers $headers -TimeoutSeconds $ParseTimeoutSeconds -IntervalSeconds $PollIntervalSeconds
Write-Host "[OK] parse success"

$qaResp = Invoke-JsonPost -Uri "$BaseUrl/api/ai/qa" -Headers $headers -Body @{ documentId = $documentId; question = $Question }
Assert-ApiSuccess -Response $qaResp -Step "qa"
$answer = [string]$qaResp.data.answer
if ([string]::IsNullOrWhiteSpace($answer)) {
    throw "[qa] Empty answer."
}
$citationCount = 0
if ($qaResp.data.citations) {
    $citationCount = @($qaResp.data.citations).Count
}
Write-Host "[OK] qa answer length=$($answer.Length), citations=$citationCount"

$historyResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/ai/qa/history?documentId=$documentId&limit=5" -Headers $headers -TimeoutSec 20
Assert-ApiSuccess -Response $historyResp -Step "qa history"
$historyCount = 0
if ($historyResp.data) {
    $historyCount = @($historyResp.data).Count
}
Write-Host "[OK] qa history count=$historyCount"

Write-Host "== Smoke passed. documentId=$documentId, taskId=$taskId =="
