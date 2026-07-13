param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BackupHelperImage = "alpine:3.20",
    [string] $BaseUrl = "http://localhost",
    [switch] $ConfirmRestore,
    [switch] $SkipHealthCheck
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")

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

function Assert-FileHash {
    param(
        [string] $Path,
        [string] $ExpectedHash
    )
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha256.ComputeHash($stream)
            $actual = (($hash | ForEach-Object { $_.ToString("x2") }) -join "").ToUpperInvariant()
        } finally {
            $sha256.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
    if ($actual -ne $ExpectedHash) {
        throw "Hash mismatch for $Path. Expected $ExpectedHash, got $actual"
    }
}

if (-not $ConfirmRestore) {
    throw "Restore is destructive. Re-run with -ConfirmRestore after confirming the target environment and backup path."
}

$ComposePath = Resolve-ProjectPath $ComposeFile
$EnvPath = Resolve-ProjectPath $EnvFile
$BackupDir = Resolve-Path $BackupPath
$DbDump = Join-Path $BackupDir "postgres.sql"
$MinioArchive = Join-Path $BackupDir "minio-data.tgz"
$ManifestPath = Join-Path $BackupDir "manifest.json"

if (-not (Test-Path $ComposePath)) {
    throw "Compose file not found: $ComposePath"
}
if (-not (Test-Path $EnvPath)) {
    throw "Environment file not found: $EnvPath"
}
if (-not (Test-Path $DbDump)) {
    throw "Missing postgres dump: $DbDump"
}

if (Test-Path $ManifestPath) {
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    foreach ($file in @($manifest.files)) {
        $path = Join-Path $BackupDir $file.name
        if (-not (Test-Path $path)) {
            throw "Manifest file is missing: $path"
        }
        Assert-FileHash -Path $path -ExpectedHash $file.sha256
    }
}

Push-Location $Root
try {
    Invoke-Compose @("cp", $DbDump, "postgres:/tmp/colla-restore-postgres.sql")
    Invoke-Compose @(
        "exec", "-T", "postgres", "sh", "-c",
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" "$POSTGRES_DB" < /tmp/colla-restore-postgres.sql'
    )
    Invoke-Compose @("exec", "-T", "postgres", "rm", "-f", "/tmp/colla-restore-postgres.sql")

    if (Test-Path $MinioArchive) {
        $minioContainer = (& docker compose --env-file $EnvPath -f $ComposePath ps -q minio) -join ""
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($minioContainer)) {
            throw "Unable to resolve running minio container"
        }
        Invoke-Docker @(
            "run", "--rm",
            "--volumes-from", $minioContainer,
            "-v", "${BackupDir}:/backup",
            $BackupHelperImage,
            "sh", "-c", "rm -rf /data/* && tar -C /data -xzf /backup/minio-data.tgz"
        )
    }

    if (-not $SkipHealthCheck) {
        & (Join-Path $PSScriptRoot "health-check.ps1") -ComposeFile $ComposeFile -EnvFile $EnvFile -BaseUrl $BaseUrl
        if ($LASTEXITCODE -ne 0) {
            throw "Health check failed after restore"
        }
    }

    Write-Host "Restore completed from: $BackupDir"
} finally {
    Pop-Location
}
