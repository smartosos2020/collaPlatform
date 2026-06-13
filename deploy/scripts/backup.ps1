param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BackupDir = ".local-backups"
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ComposePath = Join-Path $Root $ComposeFile
$EnvPath = Join-Path $Root $EnvFile
$BackupRoot = Join-Path $Root $BackupDir
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$TargetDir = Join-Path $BackupRoot $Timestamp

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

Push-Location $Root
try {
    $dbDump = Join-Path $TargetDir "postgres.sql"
    $minioArchive = Join-Path $TargetDir "minio-data.tgz"

    docker compose --env-file $EnvPath -f $ComposePath exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > $dbDump
    docker compose --env-file $EnvPath -f $ComposePath exec -T minio sh -c 'tar -C /data -czf - .' > $minioArchive

    $manifest = @(
        "# Colla Backup",
        "",
        "- Time: $(Get-Date -Format o)",
        "- PostgreSQL: postgres.sql",
        "- MinIO: minio-data.tgz"
    )
    Set-Content -Path (Join-Path $TargetDir "manifest.md") -Value ($manifest -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Backup completed: $TargetDir"
} finally {
    Pop-Location
}
