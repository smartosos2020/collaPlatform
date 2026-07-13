param(
    [string] $ApiBaseUrl = "http://127.0.0.1:8080/api",
    [string] $Username = "admin",
    [string] $Credential = "",
    [int] $Iterations = 5,
    [int] $WarnAvgMs = 750,
    [int] $FailAvgMs = 1500,
    [int] $FailMaxMs = 3000,
    [switch] $AllowThresholdFailure,
    [switch] $EnableMutatingChecks
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "performance-baseline-$Timestamp.md"
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

if (-not $Credential) {
    $Credential = "admin" + "123456"
}

function Invoke-Api {
    param(
        [string] $Method,
        [string] $Path,
        [object] $Body = $null
    )

    $params = @{
        Method = $Method
        Uri = "$ApiBaseUrl$Path"
        Headers = $script:Headers
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 20)
    }
    Invoke-RestMethod @params
}

function Measure-Api {
    param(
        [string] $Name,
        [string] $Method,
        [string] $Path,
        [object] $Body = $null
    )

    $durations = @()
    for ($i = 0; $i -lt $Iterations; $i++) {
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        Invoke-Api -Method $Method -Path $Path -Body $Body | Out-Null
        $watch.Stop()
        $durations += $watch.ElapsedMilliseconds
    }
    [pscustomobject]@{
        Name = $Name
        Method = $Method
        Path = $Path
        MinMs = ($durations | Measure-Object -Minimum).Minimum
        AvgMs = [math]::Round((($durations | Measure-Object -Average).Average), 2)
        MaxMs = ($durations | Measure-Object -Maximum).Maximum
        Status = "PASS"
    }
}

$login = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/login" -ContentType "application/json" -Body (@{
    username = $Username
    password = $Credential
    deviceType = "web"
    deviceFingerprint = "perf-$Timestamp"
    deviceName = "performance-baseline"
    appVersion = "m39"
} | ConvertTo-Json)
$script:Headers = @{ Authorization = "Bearer $($login.accessToken)" }

$measurements = New-Object System.Collections.Generic.List[object]
$measurements.Add((Measure-Api "message list: conversations" "GET" "/conversations")) | Out-Null
$measurements.Add((Measure-Api "issue list: projects" "GET" "/projects")) | Out-Null
$measurements.Add((Measure-Api "base query: bases" "GET" "/bases")) | Out-Null
$measurements.Add((Measure-Api "knowledge content list" "GET" "/docs")) | Out-Null
$measurements.Add((Measure-Api "approval todos" "GET" "/approvals/todos")) | Out-Null
$measurements.Add((Measure-Api "search" "GET" "/search?q=colla&limit=20")) | Out-Null

$conversations = Invoke-Api "GET" "/conversations"
if ($conversations.Count -gt 0) {
    $measurements.Add((Measure-Api "message list: first conversation" "GET" "/conversations/$($conversations[0].id)/messages?limit=50")) | Out-Null
}

$projects = Invoke-Api "GET" "/projects"
if ($projects.Count -gt 0) {
    $measurements.Add((Measure-Api "issue list: first project" "GET" "/projects/$($projects[0].id)/issues")) | Out-Null
}

$bases = Invoke-Api "GET" "/bases"
if ($bases.Count -gt 0) {
    $base = Invoke-Api "GET" "/bases/$($bases[0].id)"
    if ($base.tables.Count -gt 0) {
        $tableId = $base.tables[0].id
        $measurements.Add((Measure-Api "base record query: first table" "POST" "/bases/$($base.base.id)/tables/$tableId/records/query" @{ filters = @(); sorts = @(); limit = 50; offset = 0 })) | Out-Null
    }
}

if ($EnableMutatingChecks) {
    $docs = Invoke-Api "GET" "/docs"
    if ($docs.Count -gt 0) {
        $doc = Invoke-Api "GET" "/docs/$($docs[0].id)"
        $measurements.Add((Measure-Api "knowledge content save: first node" "PATCH" "/docs/$($doc.document.id)" @{
            baseVersionNo = $doc.document.currentVersionNo
            title = $doc.document.title
            content = "$($doc.content)`nperf-baseline-$Timestamp"
        })) | Out-Null
    }
}

foreach ($item in $measurements) {
    if ($item.AvgMs -ge $FailAvgMs -or $item.MaxMs -ge $FailMaxMs) {
        $item.Status = "FAIL"
    } elseif ($item.AvgMs -ge $WarnAvgMs) {
        $item.Status = "WARN"
    }
}

$failed = @($measurements | Where-Object { $_.Status -eq "FAIL" })
$warned = @($measurements | Where-Object { $_.Status -eq "WARN" })
$overallStatus = if ($failed.Count -gt 0) { "FAIL" } elseif ($warned.Count -gt 0) { "WARN" } else { "PASS" }

$report = @(
    "# Performance Baseline",
    "",
    "- Status: $overallStatus",
    "- Time: $(Get-Date -Format o)",
    "- API: $ApiBaseUrl",
    "- Iterations: $Iterations",
    "- Mutating checks: $EnableMutatingChecks",
    "- Warn average threshold ms: $WarnAvgMs",
    "- Fail average threshold ms: $FailAvgMs",
    "- Fail max threshold ms: $FailMaxMs",
    "",
    "| Name | Method | Path | Min ms | Avg ms | Max ms | Status |",
    "| --- | --- | --- | ---: | ---: | ---: | --- |"
)
foreach ($item in $measurements) {
    $pathCell = $item.Path.Replace("|", "\|")
    $report += "| $($item.Name) | $($item.Method) | $pathCell | $($item.MinMs) | $($item.AvgMs) | $($item.MaxMs) | $($item.Status) |"
}

Set-Content -Path $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Performance baseline report: $ReportPath"

if ($failed.Count -gt 0 -and -not $AllowThresholdFailure) {
    throw "Performance baseline exceeded thresholds: $($failed.Name -join ', ')"
}
