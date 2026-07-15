param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BackupDir = ".local-backups",
    [string] $BackupHelperImage = "alpine:3.20@sha256:d9e853e87e55526f6b2917df91a2115c36dd7c696a35be12163d44e6e2a4b6bc",
    [int] $RetentionDays = 0,
    [switch] $SkipMinio,
    [switch] $SkipQuiesce,
    [switch] $AllowExternalBackupRoot
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $PSScriptRoot "operations-common.ps1")
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"

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

$ComposePath = Resolve-CollaPath -Root $Root -Path $ComposeFile
$EnvPath = Resolve-CollaPath -Root $Root -Path $EnvFile
$BackupRoot = Resolve-CollaPath -Root $Root -Path $BackupDir
if (-not (Test-Path -LiteralPath $ComposePath -PathType Leaf)) {
    throw "Compose file not found: $ComposePath"
}
if (-not (Test-Path -LiteralPath $EnvPath -PathType Leaf)) {
    throw "Environment file not found: $EnvPath"
}
if (-not $AllowExternalBackupRoot) {
    Assert-CollaPathWithin -Parent $Root -Candidate $BackupRoot -Description "backup root" | Out-Null
}

New-Item -ItemType Directory -Force -Path $BackupRoot | Out-Null
$BackupRoot = (Resolve-Path -LiteralPath $BackupRoot).Path
$TargetDir = Assert-CollaPathWithin -Parent $BackupRoot -Candidate (Join-Path $BackupRoot $Timestamp) -Description "backup target"
if (Test-Path -LiteralPath $TargetDir) {
    throw "Backup target already exists: $TargetDir"
}
New-Item -ItemType Directory -Path $TargetDir | Out-Null
Set-Content -LiteralPath (Join-Path $TargetDir ".backup-in-progress") -Value (Get-Date -Format o) -Encoding UTF8

$projectName = Get-CollaComposeProjectName -ComposePath $ComposePath -EnvPath $EnvPath
$serviceNames = @(Get-CollaComposeServiceNames -ComposePath $ComposePath -EnvPath $EnvPath)
$runningServices = @(& docker compose --env-file $EnvPath -f $ComposePath ps --status running --services)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect running compose services"
}
$servicesToResume = @()
$minioShouldResume = $false
$minioContainer = ""
$minioObjectCount = $null
$consistencyMode = "operator-managed"

Push-Location $Root
try {
    if (-not $SkipQuiesce -and $serviceNames -contains "server" -and $runningServices -contains "server") {
        $servicesToStop = @("server")
        if ($serviceNames -contains "nginx" -and $runningServices -contains "nginx") {
            $servicesToStop = @("nginx", "server")
        }
        Invoke-Compose (@("stop") + $servicesToStop)
        $servicesToResume = $servicesToStop
        $consistencyMode = "application-quiesced"
    } elseif ($SkipQuiesce) {
        $consistencyMode = "non-quiesced-explicit"
        Write-Warning "Application quiescing was explicitly skipped; PostgreSQL and MinIO are not a cross-store atomic snapshot."
    } else {
        Write-Warning "Compose has no running server service. The operator is responsible for ensuring no external process writes during backup."
    }

    if (-not $SkipMinio) {
        if ($runningServices -notcontains "minio") {
            throw "MinIO must be running before a full backup"
        }
        $minioObjectCount = Get-CollaMinioObjectCount -ComposePath $ComposePath -EnvPath $EnvPath -BackupHelperImage $BackupHelperImage
        $minioContainer = (& docker compose --env-file $EnvPath -f $ComposePath ps -q minio) -join ""
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($minioContainer)) {
            throw "Unable to resolve running MinIO container"
        }
        Invoke-Compose @("stop", "minio")
        $minioShouldResume = $true
    }

    $databaseCounts = Get-CollaDatabaseCounts -ComposePath $ComposePath -EnvPath $EnvPath
    $flywayVersion = Get-CollaFlywayVersion -ComposePath $ComposePath -EnvPath $EnvPath
    $dbDump = Join-Path $TargetDir "postgres.sql"
    Invoke-Compose @(
        "exec", "-T", "postgres", "sh", "-c",
        'pg_dump --clean --if-exists --no-owner --no-privileges --serializable-deferrable -U "$POSTGRES_USER" "$POSTGRES_DB" > /tmp/colla-postgres.sql'
    )
    Invoke-Compose @("cp", "postgres:/tmp/colla-postgres.sql", $dbDump)
    Invoke-Compose @("exec", "-T", "postgres", "rm", "-f", "/tmp/colla-postgres.sql")

    $files = New-Object System.Collections.Generic.List[object]
    $dbFile = Get-Item -LiteralPath $dbDump
    $files.Add([ordered]@{
        name = "postgres.sql"
        kind = "postgres"
        bytes = $dbFile.Length
        sha256 = Get-CollaSha256 -Path $dbDump
    }) | Out-Null

    if (-not $SkipMinio) {
        $minioArchive = Join-Path $TargetDir "minio-data.tgz"
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
            sha256 = Get-CollaSha256 -Path $minioArchive
        }) | Out-Null
    }

    $gitCommit = (& git rev-parse HEAD 2>$null | Select-Object -Last 1)
    if ($LASTEXITCODE -ne 0) {
        $gitCommit = "unknown"
    }
    $manifest = [ordered]@{
        manifestVersion = 2
        createdAt = (Get-Date).ToUniversalTime().ToString("o")
        projectName = $projectName
        sourceGitCommit = ([string] $gitCommit).Trim()
        composeFile = $ComposeFile
        consistencyMode = $consistencyMode
        flywayVersion = $flywayVersion
        databaseCounts = $databaseCounts
        minioObjectCount = $minioObjectCount
        files = @($files.ToArray())
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $TargetDir "manifest.json") -Encoding UTF8

    $manifestMd = @(
        "# Colla Backup",
        "",
        "- Manifest version: 2",
        "- Time: $($manifest.createdAt)",
        "- Compose project: $projectName",
        "- Source commit: $($manifest.sourceGitCommit)",
        "- Consistency mode: $consistencyMode",
        "- Flyway version: $flywayVersion",
        "- Backup path: $TargetDir",
        "",
        "| File | Kind | Bytes | SHA-256 |",
        "| --- | --- | ---: | --- |"
    )
    foreach ($file in $files) {
        $manifestMd += "| $($file.name) | $($file.kind) | $($file.bytes) | $($file.sha256) |"
    }
    Set-Content -LiteralPath (Join-Path $TargetDir "manifest.md") -Value ($manifestMd -join [Environment]::NewLine) -Encoding UTF8
    Remove-Item -LiteralPath (Join-Path $TargetDir ".backup-in-progress") -Force

    Read-CollaBackupManifest -BackupPath $TargetDir -VerifyFiles | Out-Null

    if ($RetentionDays -gt 0) {
        $cutoff = (Get-Date).AddDays(-1 * $RetentionDays)
        foreach ($candidate in Get-ChildItem -LiteralPath $BackupRoot -Directory) {
            if ($candidate.FullName -eq $TargetDir -or $candidate.Name -notmatch '^\d{8}-\d{6}$' -or $candidate.LastWriteTime -ge $cutoff) {
                continue
            }
            Assert-CollaPathWithin -Parent $BackupRoot -Candidate $candidate.FullName -Description "retention candidate" | Out-Null
            try {
                Read-CollaBackupManifest -BackupPath $candidate.FullName | Out-Null
            } catch {
                Write-Warning "Retention skipped unverified directory '$($candidate.FullName)': $($_.Exception.Message)"
                continue
            }
            Remove-Item -LiteralPath $candidate.FullName -Recurse -Force
            Write-Host "Pruned verified backup: $($candidate.FullName)"
        }
    }

    Write-Host "Backup completed: $TargetDir"
} finally {
    if ($minioShouldResume) {
        Invoke-Compose @("up", "-d", "--wait", "minio")
    }
    if ($servicesToResume.Count -gt 0) {
        Invoke-Compose (@("up", "-d", "--wait") + $servicesToResume)
    }
    Pop-Location
}
