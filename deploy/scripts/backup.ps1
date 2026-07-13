param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BackupDir = ".local-backups",
    [string] $BackupHelperImage = "alpine:3.20",
    [int] $RetentionDays = 0,
    [switch] $SkipMinio
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"

function Resolve-ProjectPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $Root $Path
}

function Invoke-Compose {
    param([string[]] $ComposeArgs)
    & docker compose --env-file $EnvPath -f $ComposePath @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($ComposeArgs -join ' ') failed with code $LASTEXITCODE"
    }
}

function Invoke-Docker {
    param([string[]] $DockerArgs)
    & docker @DockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($DockerArgs -join ' ') failed with code $LASTEXITCODE"
    }
}

function Get-RelativeToRoot {
    param([string] $Path)
    $rootPath = [System.IO.Path]::GetFullPath($Root.Path)
    if (-not $rootPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $rootPath = $rootPath + [System.IO.Path]::DirectorySeparatorChar
    }
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($rootPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is not under repository root: $fullPath"
    }
    return $fullPath.Substring($rootPath.Length)
}

function Get-Sha256Hash {
    param([string] $Path)
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha256.ComputeHash($stream)
            return (($hash | ForEach-Object { $_.ToString("x2") }) -join "").ToUpperInvariant()
        } finally {
            $sha256.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

$ComposePath = Resolve-ProjectPath $ComposeFile
$EnvPath = Resolve-ProjectPath $EnvFile
$BackupRoot = Resolve-ProjectPath $BackupDir
$TargetDir = Join-Path $BackupRoot $Timestamp

if (-not (Test-Path $ComposePath)) {
    throw "Compose file not found: $ComposePath"
}
if (-not (Test-Path $EnvPath)) {
    throw "Environment file not found: $EnvPath"
}

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

Push-Location $Root
try {
    $dbDump = Join-Path $TargetDir "postgres.sql"
    $dbDumpTarget = Get-RelativeToRoot $dbDump

    Invoke-Compose @(
        "exec", "-T", "postgres", "sh", "-c",
        'pg_dump --clean --if-exists --no-owner --no-privileges -U "$POSTGRES_USER" "$POSTGRES_DB" > /tmp/colla-postgres.sql'
    )
    Invoke-Compose @("cp", "postgres:/tmp/colla-postgres.sql", $dbDumpTarget)
    Invoke-Compose @("exec", "-T", "postgres", "rm", "-f", "/tmp/colla-postgres.sql")

    $files = New-Object System.Collections.Generic.List[object]
    $dbFile = Get-Item -LiteralPath $dbDump
    $files.Add([ordered]@{
        name = "postgres.sql"
        kind = "postgres"
        bytes = $dbFile.Length
        sha256 = Get-Sha256Hash -Path $dbDump
    }) | Out-Null

    if (-not $SkipMinio) {
        $minioArchive = Join-Path $TargetDir "minio-data.tgz"
        $minioContainer = (& docker compose --env-file $EnvPath -f $ComposePath ps -q minio) -join ""
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($minioContainer)) {
            throw "Unable to resolve running minio container"
        }

        Invoke-Docker @(
            "run", "--rm",
            "--volumes-from", $minioContainer,
            "-v", "${TargetDir}:/backup",
            $BackupHelperImage,
            "sh", "-c", "tar -C /data -czf /backup/minio-data.tgz ."
        )

        $minioFile = Get-Item -LiteralPath $minioArchive
        $files.Add([ordered]@{
            name = "minio-data.tgz"
            kind = "minio"
            bytes = $minioFile.Length
            sha256 = Get-Sha256Hash -Path $minioArchive
        }) | Out-Null
    }

    $manifest = New-Object psobject -Property @{
        createdAt = (Get-Date).ToString("o")
        composeFile = $ComposeFile
        envFile = $EnvFile
        backupPath = [System.IO.Path]::GetFullPath($TargetDir)
        files = @($files.ToArray())
    }
    $manifestJson = $manifest | ConvertTo-Json -Depth 5
    Set-Content -LiteralPath (Join-Path $TargetDir "manifest.json") -Value $manifestJson -Encoding UTF8

    $manifestMd = @(
        "# Colla Backup",
        "",
        "- Time: $($manifest.createdAt)",
        "- Compose file: $ComposeFile",
        "- Environment file: $EnvFile",
        "- Backup path: $TargetDir",
        "",
        "| File | Kind | Bytes | SHA-256 |",
        "| --- | --- | ---: | --- |"
    )
    foreach ($file in $files) {
        $manifestMd += "| $($file.name) | $($file.kind) | $($file.bytes) | $($file.sha256) |"
    }
    Set-Content -LiteralPath (Join-Path $TargetDir "manifest.md") -Value ($manifestMd -join [Environment]::NewLine) -Encoding UTF8

    if ($RetentionDays -gt 0) {
        $resolvedBackupRoot = Resolve-Path $BackupRoot
        if (-not $resolvedBackupRoot.Path.StartsWith($Root.Path, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to prune backups outside repository root: $($resolvedBackupRoot.Path)"
        }
        $cutoff = (Get-Date).AddDays(-1 * $RetentionDays)
        Get-ChildItem -LiteralPath $resolvedBackupRoot -Directory |
            Where-Object { $_.LastWriteTime -lt $cutoff } |
            ForEach-Object {
                if (-not $_.FullName.StartsWith($resolvedBackupRoot.Path, [System.StringComparison]::OrdinalIgnoreCase)) {
                    throw "Refusing to remove path outside backup root: $($_.FullName)"
                }
                Remove-Item -LiteralPath $_.FullName -Recurse -Force
            }
    }

    Write-Host "Backup completed: $TargetDir"
} finally {
    Pop-Location
}
