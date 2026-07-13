param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $GitRef,
    [string] $BackupPath,
    [string] $BaseUrl = "http://localhost",
    [switch] $ConfirmRollback,
    [switch] $RestoreData,
    [switch] $AllowDirty
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

if (-not $ConfirmRollback) {
    throw "Rollback changes the running environment. Re-run with -ConfirmRollback after confirming the target version and backup."
}

$ComposePath = Resolve-ProjectPath $ComposeFile
$EnvPath = Resolve-ProjectPath $EnvFile

if (-not (Test-Path $ComposePath)) {
    throw "Compose file not found: $ComposePath"
}
if (-not (Test-Path $EnvPath)) {
    throw "Environment file not found: $EnvPath"
}
if ($RestoreData -and [string]::IsNullOrWhiteSpace($BackupPath)) {
    throw "-BackupPath is required when -RestoreData is used"
}

Push-Location $Root
try {
    $status = git status --porcelain
    if ($status -and -not $AllowDirty) {
        throw "Working tree is dirty. Commit or stash changes, or pass -AllowDirty for an emergency rollback."
    }

    if (-not [string]::IsNullOrWhiteSpace($GitRef)) {
        git rev-parse --verify $GitRef | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Git ref not found: $GitRef"
        }
        git checkout --detach $GitRef
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to checkout $GitRef"
        }
    }

    docker compose --env-file $EnvPath -f $ComposePath up -d --build
    if ($LASTEXITCODE -ne 0) {
        throw "Compose deployment failed"
    }

    if ($RestoreData) {
        & (Join-Path $PSScriptRoot "restore.ps1") -BackupPath $BackupPath -ComposeFile $ComposeFile -EnvFile $EnvFile -BaseUrl $BaseUrl -ConfirmRestore
        if ($LASTEXITCODE -ne 0) {
            throw "Data restore failed during rollback"
        }
    } else {
        & (Join-Path $PSScriptRoot "health-check.ps1") -ComposeFile $ComposeFile -EnvFile $EnvFile -BaseUrl $BaseUrl
        if ($LASTEXITCODE -ne 0) {
            throw "Health check failed during rollback"
        }
    }

    Write-Host "Rollback completed"
} finally {
    Pop-Location
}
