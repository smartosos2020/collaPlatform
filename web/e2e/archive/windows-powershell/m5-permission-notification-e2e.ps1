param(
    [int] $DatabasePort = 5432,
    [int] $ApiPort = 18080,
    [int] $WebPort = 15173
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$databaseName = "colla_m5_e2e_$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
$logDir = Join-Path $root ".local-logs"
$serverOut = Join-Path $logDir "m5-isolated-server.out.log"
$serverErr = Join-Path $logDir "m5-isolated-server.err.log"
$webOut = Join-Path $logDir "m5-isolated-web.out.log"
$webErr = Join-Path $logDir "m5-isolated-web.err.log"
$databasePassword = @("colla", "dev", "password") -join "_"
$serverProcess = $null
$webProcess = $null

function Wait-HttpReady {
    param([string] $Url, [int] $TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            if ((Invoke-WebRequest -UseBasicParsing $Url -TimeoutSec 2).StatusCode -eq 200) { return }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Url"
}

function Stop-WorkspaceListener {
    param([int] $Port)
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        $listenerProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)"
        if ($listenerProcess.CommandLine -like "*$root*" -and @("node.exe", "java.exe") -contains $listenerProcess.Name) {
            Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
}

try {
    Push-Location $root
    mvn -q -f server/pom.xml -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Backend package failed" }

    docker exec colla-postgres createdb -U colla $databaseName
    if ($LASTEXITCODE -ne 0) { throw "Disposable PostgreSQL database creation failed" }

    $env:COLLA_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:${DatabasePort}/$databaseName"
    $env:COLLA_DATASOURCE_USERNAME = "colla"
    $env:COLLA_DATASOURCE_PASSWORD = $databasePassword
    $env:SERVER_PORT = $ApiPort.ToString()
    $env:CORS_ALLOWED_ORIGINS = "http://127.0.0.1:${WebPort}"
    $serverProcess = Start-Process -FilePath "java.exe" `
        -ArgumentList "-jar", "server/target/colla-platform-server-0.1.0-SNAPSHOT.jar" `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr

    $env:VITE_API_BASE_URL = "http://127.0.0.1:${ApiPort}/api"
    $env:VITE_WS_BASE_URL = "ws://127.0.0.1:${ApiPort}/ws/events"
    $webProcess = Start-Process -FilePath "npm.cmd" `
        -ArgumentList "run", "dev", "--", "--host", "127.0.0.1", "--port", $WebPort.ToString() `
        -WorkingDirectory (Join-Path $root "web") -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $webOut -RedirectStandardError $webErr

    Wait-HttpReady -Url "http://127.0.0.1:${ApiPort}/actuator/health"
    Wait-HttpReady -Url "http://127.0.0.1:${WebPort}"

    $env:COLLA_E2E_SUITE = "route-final"
    $env:COLLA_E2E_ISOLATED = "true"
    $env:COLLA_E2E_API_BASE_URL = "http://127.0.0.1:${ApiPort}/api"
    $env:COLLA_E2E_WEB_BASE_URL = "http://127.0.0.1:${WebPort}"
    Push-Location (Join-Path $root "web")
    try {
        npx playwright test --config e2e/playwright.config.ts m5-permission-notification-e2e.spec.ts
        if ($LASTEXITCODE -ne 0) { throw "M5 isolated browser verification failed" }
    } finally {
        Pop-Location
    }
} finally {
    if ($webProcess -and -not $webProcess.HasExited) { Stop-Process -Id $webProcess.Id -Force }
    if ($serverProcess -and -not $serverProcess.HasExited) { Stop-Process -Id $serverProcess.Id -Force }
    Stop-WorkspaceListener -Port $WebPort
    Stop-WorkspaceListener -Port $ApiPort
    docker exec colla-postgres dropdb --if-exists --force -U colla $databaseName 2>$null | Out-Null
    Pop-Location
}
