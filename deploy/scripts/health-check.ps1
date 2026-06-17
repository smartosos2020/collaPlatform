param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BaseUrl = "http://localhost",
    [switch] $SkipCompose,
    [switch] $RequirePrometheus
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "health-check-$Timestamp.md"
$Results = New-Object System.Collections.Generic.List[string]

function Resolve-ProjectPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $Root $Path
}

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
}

function Invoke-JsonGet {
    param([string] $Url)
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "Unexpected status $($response.StatusCode) from $Url"
    }
    return $response.Content
}

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
$ComposePath = Resolve-ProjectPath $ComposeFile
$EnvPath = Resolve-ProjectPath $EnvFile
$NormalizedBaseUrl = $BaseUrl.TrimEnd("/")

Push-Location $Root
try {
    if (-not $SkipCompose) {
        if (-not (Test-Path $ComposePath)) {
            throw "Compose file not found: $ComposePath"
        }
        if (-not (Test-Path $EnvPath)) {
            throw "Environment file not found: $EnvPath"
        }
        docker compose --env-file $EnvPath -f $ComposePath ps | Out-String | ForEach-Object { Add-Result $_.TrimEnd() }
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose ps failed with code $LASTEXITCODE"
        }
    }

    $apiHealth = Invoke-JsonGet "$NormalizedBaseUrl/api/health"
    Add-Result "- PASS: API health $NormalizedBaseUrl/api/health"

    $actuatorHealth = Invoke-JsonGet "$NormalizedBaseUrl/actuator/health"
    if ($actuatorHealth -notmatch '"status"\s*:\s*"UP"') {
        throw "Actuator health is not UP: $actuatorHealth"
    }
    Add-Result "- PASS: Actuator health $NormalizedBaseUrl/actuator/health"

    if ($RequirePrometheus) {
        $prometheus = Invoke-JsonGet "$NormalizedBaseUrl/actuator/prometheus"
        if ($prometheus -notmatch "jvm_") {
            throw "Prometheus output does not contain JVM metrics"
        }
        Add-Result "- PASS: Prometheus metrics $NormalizedBaseUrl/actuator/prometheus"
    }

    $report = @(
        "# Health Check",
        "",
        "- Time: $(Get-Date -Format o)",
        "- BaseUrl: $BaseUrl",
        "",
        "## Results"
    ) + $Results
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Health check report: $ReportPath"
} finally {
    Pop-Location
}
