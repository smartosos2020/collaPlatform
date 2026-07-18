param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BaseUrl = "http://localhost",
    [string] $MetricsBaseUrl = "",
    [string] $ExpectedProjectName = "",
    [switch] $SkipCompose,
    [switch] $RequirePrometheus,
    [switch] $RequireLogCorrelation
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $PSScriptRoot "operations-common.ps1")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "health-check-$Timestamp.md"
$Results = New-Object System.Collections.Generic.List[string]
$decision = "FAIL"

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
}

function Invoke-CheckedGet {
    param(
        [string] $Url,
        [hashtable] $Headers = @{}
    )
    $response = Invoke-WebRequest -Uri $Url -Headers $Headers -UseBasicParsing -TimeoutSec 15
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "Unexpected status $($response.StatusCode) from $Url"
    }
    return $response
}

function Get-ResponseText {
    param($Response)
    if ($Response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string] $Response.Content
}

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
$ComposePath = Resolve-CollaPath -Root $Root -Path $ComposeFile
$EnvPath = Resolve-CollaPath -Root $Root -Path $EnvFile
$NormalizedBaseUrl = $BaseUrl.TrimEnd("/")
$NormalizedMetricsUrl = if ([string]::IsNullOrWhiteSpace($MetricsBaseUrl)) { $NormalizedBaseUrl } else { $MetricsBaseUrl.TrimEnd("/") }
$requestId = "health-$([guid]::NewGuid().ToString('N'))"
$actualProjectName = "not-checked"

Push-Location $Root
try {
    if (-not $SkipCompose) {
        if (-not (Test-Path -LiteralPath $ComposePath -PathType Leaf)) {
            throw "Compose file not found: $ComposePath"
        }
        if (-not (Test-Path -LiteralPath $EnvPath -PathType Leaf)) {
            throw "Environment file not found: $EnvPath"
        }
        $model = Get-CollaComposeModel -ComposePath $ComposePath -EnvPath $EnvPath
        $actualProjectName = [string] $model.name
        if (-not [string]::IsNullOrWhiteSpace($ExpectedProjectName) -and $actualProjectName -cne $ExpectedProjectName) {
            throw "Health target mismatch. Expected '$ExpectedProjectName', compose resolves to '$actualProjectName'."
        }

        $containers = @()
        $lines = & docker compose --env-file $EnvPath -f $ComposePath ps --all --format json
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose ps failed"
        }
        foreach ($line in $lines) {
            if (-not [string]::IsNullOrWhiteSpace($line)) {
                $containers += $line | ConvertFrom-Json
            }
        }
        foreach ($service in @($model.services.PSObject.Properties)) {
            $container = @($containers | Where-Object { $_.Service -eq $service.Name }) | Select-Object -First 1
            if ($null -eq $container) {
                throw "Compose service is missing: $($service.Name)"
            }
            if ($container.State -ne "running") {
                throw "Compose service is not running: $($service.Name) ($($container.State))"
            }
            if ($service.Value.PSObject.Properties.Name -contains "healthcheck" -and $container.Health -ne "healthy") {
                throw "Compose service is not healthy: $($service.Name) ($($container.Health))"
            }
        }
        Add-Result "- PASS: all compose services are running and configured health checks are healthy"
    }

    $apiResponse = Invoke-CheckedGet -Url "$NormalizedBaseUrl/api/health" -Headers @{ "X-Colla-Request-Id" = $requestId }
    $apiText = Get-ResponseText -Response $apiResponse
    $api = $apiText | ConvertFrom-Json
    if ($api.status -ne "ok" -or $api.service -ne "colla-platform") {
        throw "API health payload is invalid: $apiText"
    }
    $parsedTime = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParse([string] $api.time, [ref] $parsedTime)) {
        throw "API health time is invalid: $($api.time)"
    }
    if ([string] $apiResponse.Headers["X-Colla-Request-Id"] -cne $requestId) {
        throw "API did not echo the supplied request id"
    }
    Add-Result "- PASS: API health payload and request-id echo are valid"

    $actuatorResponse = Invoke-CheckedGet -Url "$NormalizedBaseUrl/actuator/health"
    $actuatorText = Get-ResponseText -Response $actuatorResponse
    $actuator = $actuatorText | ConvertFrom-Json
    if ($actuator.status -ne "UP") {
        throw "Actuator health is not UP: $actuatorText"
    }
    Add-Result "- PASS: Actuator health is UP"

    if ($RequirePrometheus) {
        $prometheus = Invoke-CheckedGet -Url "$NormalizedMetricsUrl/actuator/prometheus"
        if ((Get-ResponseText -Response $prometheus) -notmatch '(?m)^jvm_') {
            throw "Prometheus output does not contain JVM metrics"
        }
        Add-Result "- PASS: Prometheus exposes JVM metrics"
    }

    if ($RequireLogCorrelation) {
        if ($SkipCompose) {
            throw "Log correlation requires compose inspection"
        }
        $matched = $false
        for ($attempt = 0; $attempt -lt 10 -and -not $matched; $attempt++) {
            $logs = (& docker compose --env-file $EnvPath -f $ComposePath logs --since 2m server | Out-String)
            if ($LASTEXITCODE -ne 0) {
                throw "Unable to read server logs for request correlation"
            }
            $matched = $logs.Contains($requestId)
            if (-not $matched) {
                Start-Sleep -Milliseconds 500
            }
        }
        if (-not $matched) {
            throw "Request id was not found in server logs: $requestId"
        }
        Add-Result "- PASS: request id is present in server logs"
    }

    $decision = "PASS"
    Write-Host "Health check passed"
} finally {
    $report = @(
        "# Health Check",
        "",
        "- Time: $(Get-Date -Format o)",
        "- BaseUrl: $BaseUrl",
        "- Compose project: $actualProjectName",
        "- Request id: $requestId",
        "- Decision: $decision",
        "",
        "## Results"
    ) + $Results
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Health check report: $ReportPath"
    Pop-Location
}
