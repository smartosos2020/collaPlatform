param(
    [ValidateSet("export", "verify", "restore")]
    [string]$Mode = "export",
    [string]$Database = "colla_platform",
    [string]$DatabaseUser = "colla",
    [string]$Output = ".local-reports/knowledge-content-pre-v045.sql"
)

$ErrorActionPreference = "Stop"
$container = "colla-postgres"

if ($Mode -eq "export") {
    $arguments = @("exec", $container, "pg_dump", "-U", $DatabaseUser, "-d", $Database, "--no-owner", "--no-privileges")
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Output) | Out-Null
    & docker @arguments | Set-Content -LiteralPath $Output -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw "Knowledge content snapshot export failed" }
    Write-Host "Offline pre-V045 snapshot: $Output"
    exit 0
}

if (-not (Test-Path -LiteralPath $Output)) {
    throw "Snapshot file not found: $Output"
}

if ($Mode -eq "verify") {
    $size = (Get-Item -LiteralPath $Output).Length
    if ($size -le 0) { throw "Snapshot file is empty: $Output" }
    Write-Host "Snapshot verified: $Output ($size bytes)"
    exit 0
}

Write-Warning "Restore is an offline recovery operation. The target database must be empty and isolated."
Get-Content -LiteralPath $Output -Raw | & docker exec -i $container psql -v ON_ERROR_STOP=1 -U $DatabaseUser -d $Database
if ($LASTEXITCODE -ne 0) { throw "Knowledge content snapshot restore failed" }
Write-Host "Snapshot restored into isolated database: $Database"
