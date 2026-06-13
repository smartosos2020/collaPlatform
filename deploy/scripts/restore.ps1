param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod"
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ComposePath = Join-Path $Root $ComposeFile
$EnvPath = Join-Path $Root $EnvFile
$BackupDir = Resolve-Path $BackupPath
$DbDump = Join-Path $BackupDir "postgres.sql"
$MinioArchive = Join-Path $BackupDir "minio-data.tgz"

if (-not (Test-Path $DbDump)) {
    throw "Missing postgres dump: $DbDump"
}
if (-not (Test-Path $MinioArchive)) {
    throw "Missing MinIO archive: $MinioArchive"
}

Push-Location $Root
try {
    Get-Content -LiteralPath $DbDump | docker compose --env-file $EnvPath -f $ComposePath exec -T postgres sh -c 'psql -U "$POSTGRES_USER" "$POSTGRES_DB"'
    Get-Content -LiteralPath $MinioArchive -AsByteStream | docker compose --env-file $EnvPath -f $ComposePath exec -T minio sh -c 'rm -rf /data/* && tar -C /data -xzf -'
    Write-Host "Restore completed from: $BackupDir"
} finally {
    Pop-Location
}
