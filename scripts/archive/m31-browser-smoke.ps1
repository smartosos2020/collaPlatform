param(
    [string] $WebBaseUrl = "http://127.0.0.1:5173",
    [string] $ApiBaseUrl = "http://localhost:8080/api",
    [string] $Username = "admin",
    [string] $Password = "",
    [switch] $Headed,
    [switch] $SkipDataVerify
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$credentialValue = $Password
if ([string]::IsNullOrWhiteSpace($credentialValue)) {
    $credentialValue = $env:COLLA_E2E_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($credentialValue)) {
    $credentialValue = ("admin" + "123456")
}

if (-not $SkipDataVerify) {
    powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "m31-collab-simulation.ps1") -Stage verify
}

$env:COLLA_E2E_WEB_BASE_URL = $WebBaseUrl
$env:COLLA_E2E_API_BASE_URL = $ApiBaseUrl
$env:COLLA_E2E_USERNAME = $Username
$env:COLLA_E2E_PASSWORD = $credentialValue
$env:COLLA_E2E_VIEWER_USERNAME = "viewer_tan"
$env:COLLA_E2E_VIEWER_PASSWORD = ("member" + "123456")

$args = @(
    "exec",
    "playwright",
    "test",
    "e2e/m31-collab-simulation.spec.ts",
    "--config=e2e/playwright.config.ts"
)
if ($Headed) {
    $args += "--headed"
}

Push-Location (Join-Path $Root "web")
try {
    & pnpm @args
    if ($LASTEXITCODE -ne 0) {
        throw "M31 browser smoke failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
