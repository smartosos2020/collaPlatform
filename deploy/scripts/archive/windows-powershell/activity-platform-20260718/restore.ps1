param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BackupHelperImage = "alpine:3.20@sha256:d9e853e87e55526f6b2917df91a2115c36dd7c696a35be12163d44e6e2a4b6bc",
    [string] $BaseUrl = "http://localhost",
    [Parameter(Mandatory = $true)]
    [string] $ExpectedProjectName,
    [Parameter(Mandatory = $true)]
    [string] $ConfirmationText,
    [switch] $ConfirmRestore,
    [switch] $SkipHealthCheck
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $PSScriptRoot "operations-common.ps1")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "restore-$Timestamp.md"
$PostgresLogPath = Join-Path $ReportDir "restore-$Timestamp-postgres.log"

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

if (-not $ConfirmRestore) {
    throw "Restore is destructive. Add -ConfirmRestore after confirming the isolated or production target."
}
if ($ConfirmationText -cne "RESTORE:$ExpectedProjectName") {
    throw "Confirmation text must exactly match RESTORE:$ExpectedProjectName"
}

$ComposePath = Resolve-CollaPath -Root $Root -Path $ComposeFile
$EnvPath = Resolve-CollaPath -Root $Root -Path $EnvFile
$BackupDir = (Resolve-Path -LiteralPath $BackupPath).Path
if (-not (Test-Path -LiteralPath $ComposePath -PathType Leaf)) {
    throw "Compose file not found: $ComposePath"
}
if (-not (Test-Path -LiteralPath $EnvPath -PathType Leaf)) {
    throw "Environment file not found: $EnvPath"
}

$actualProjectName = Get-CollaComposeProjectName -ComposePath $ComposePath -EnvPath $EnvPath
if ($actualProjectName -cne $ExpectedProjectName) {
    throw "Restore target mismatch. Expected '$ExpectedProjectName', compose resolves to '$actualProjectName'."
}
$manifest = Read-CollaBackupManifest -BackupPath $BackupDir -VerifyFiles
$DbDump = Assert-CollaPathWithin -Parent $BackupDir -Candidate (Join-Path $BackupDir "postgres.sql") -Description "database dump"
$minioEntry = @($manifest.files | Where-Object { $_.name -eq "minio-data.tgz" }) | Select-Object -First 1
$MinioArchive = if ($null -ne $minioEntry) {
    Assert-CollaPathWithin -Parent $BackupDir -Candidate (Join-Path $BackupDir "minio-data.tgz") -Description "MinIO archive"
} else {
    $null
}
$serviceNames = @(Get-CollaComposeServiceNames -ComposePath $ComposePath -EnvPath $EnvPath)
$appServices = @("nginx", "web", "server") | Where-Object { $serviceNames -contains $_ }
$dependencyServices = @("postgres", "redis", "minio") | Where-Object { $serviceNames -contains $_ }
$results = New-Object System.Collections.Generic.List[string]
$startedAt = Get-Date
$completed = $false
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

Push-Location $Root
try {
    if ($appServices.Count -gt 0) {
        Invoke-Compose (@("stop") + $appServices)
        $results.Add("- PASS: write-capable application services stopped before restore") | Out-Null
    }
    Invoke-Compose (@("up", "-d", "--wait") + $dependencyServices)
    $results.Add("- PASS: dependency services are healthy") | Out-Null

    Invoke-Compose @(
        "exec", "-T", "postgres", "sh", "-c",
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" "$POSTGRES_DB" -c "select pg_terminate_backend(pid) from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid()"'
    )
    Invoke-Compose @("cp", $DbDump, "postgres:/tmp/colla-restore-postgres.sql")
    try {
        $restoreOutput = & docker compose --env-file $EnvPath -f $ComposePath exec -T postgres sh -c `
            'psql -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" "$POSTGRES_DB" < /tmp/colla-restore-postgres.sql' 2>&1
        $restoreExitCode = $LASTEXITCODE
        $restoreOutput | Set-Content -LiteralPath $PostgresLogPath -Encoding UTF8
        if ($restoreExitCode -ne 0) {
            throw "PostgreSQL restore failed with code $restoreExitCode. See $PostgresLogPath"
        }
    } finally {
        Invoke-Compose @("exec", "-T", "postgres", "rm", "-f", "/tmp/colla-restore-postgres.sql")
    }
    $results.Add("- PASS: PostgreSQL restore completed in one transaction (details: $PostgresLogPath)") | Out-Null

    if ($null -ne $MinioArchive) {
        Invoke-Compose @("stop", "minio")
        $minioContainer = (& docker compose --env-file $EnvPath -f $ComposePath ps -q --all minio) -join ""
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($minioContainer)) {
            throw "Unable to resolve stopped MinIO container"
        }
        Invoke-Docker @(
            "run", "--rm",
            "--volumes-from", $minioContainer,
            "-v", "${BackupDir}:/backup:ro",
            $BackupHelperImage,
            "sh", "-c",
            "find /data -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -C /data -xzf /backup/minio-data.tgz"
        )
        Invoke-Compose @("up", "-d", "--wait", "minio")
        $results.Add("- PASS: MinIO data replaced from verified archive") | Out-Null
    }

    $actualCounts = Get-CollaDatabaseCounts -ComposePath $ComposePath -EnvPath $EnvPath
    Compare-CollaCounts -Expected $manifest.databaseCounts -Actual $actualCounts
    $results.Add("- PASS: key PostgreSQL counts match backup manifest") | Out-Null
    if ($null -ne $MinioArchive -and $null -ne $manifest.minioObjectCount) {
        $actualMinioCount = Get-CollaMinioObjectCount -ComposePath $ComposePath -EnvPath $EnvPath -BackupHelperImage $BackupHelperImage
        if ($actualMinioCount -ne [long] $manifest.minioObjectCount) {
            throw "Restored MinIO object count mismatch. Expected $($manifest.minioObjectCount), got $actualMinioCount"
        }
        $results.Add("- PASS: MinIO object count matches backup manifest ($actualMinioCount)") | Out-Null
    }

    if ($appServices.Count -gt 0) {
        Invoke-Compose (@("up", "-d", "--wait") + $appServices)
        $results.Add("- PASS: application services restarted after verified data restore") | Out-Null
    }
    if (-not $SkipHealthCheck -and $serviceNames -contains "server") {
        & (Join-Path $PSScriptRoot "health-check.ps1") `
            -ComposeFile $ComposeFile `
            -EnvFile $EnvFile `
            -BaseUrl $BaseUrl `
            -ExpectedProjectName $ExpectedProjectName
        if ($LASTEXITCODE -ne 0) {
            throw "Health check failed after restore"
        }
        $results.Add("- PASS: post-restore health check passed") | Out-Null
    }

    $completed = $true
    Write-Host "Restore completed from: $BackupDir"
} finally {
    $report = @(
        "# Restore Evidence",
        "",
        "- Started: $($startedAt.ToUniversalTime().ToString('o'))",
        "- Finished: $((Get-Date).ToUniversalTime().ToString('o'))",
        "- Target project: $actualProjectName",
        "- Source project: $($manifest.projectName)",
        "- Backup path: $BackupDir",
        "- Decision: $(if ($completed) { 'PASS' } else { 'FAIL' })",
        "",
        "## Results"
    ) + $results
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Restore evidence: $ReportPath"
    Pop-Location
}
