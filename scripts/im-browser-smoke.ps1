param(
    [string] $WebBaseUrl = "http://127.0.0.1:5173",
    [string] $ApiBaseUrl = "http://localhost:8080/api",
    [string] $Username = "admin",
    [string] $Password = "",
    [switch] $Headed
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

$env:COLLA_E2E_WEB_BASE_URL = $WebBaseUrl
$env:COLLA_E2E_API_BASE_URL = $ApiBaseUrl
$env:COLLA_E2E_USERNAME = $Username
$env:COLLA_E2E_PASSWORD = $credentialValue

$args = @(
    "exec",
    "playwright",
    "test",
    "e2e/im-smoke.spec.ts",
    "--config=e2e/playwright.config.ts"
)
if ($Headed) {
    $args += "--headed"
}

Push-Location (Join-Path $Root "web")
try {
    & pnpm @args
    if ($LASTEXITCODE -ne 0) {
        throw "IM browser smoke failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
