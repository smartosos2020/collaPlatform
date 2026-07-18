param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [ValidateSet("quick", "full")]
    [string] $GateMode = "full",
    [string] $ExpectedProjectName = "",
    [string] $BackupPath = "",
    [string] $BackupDir = ".local-backups",
    [int] $MaxBackupAgeHours = 24,
    [switch] $CreateBackup,
    [switch] $AllowDirty,
    [switch] $SkipQualityGate,
    [switch] $SkipImageBuild,
    [switch] $SkipBackupCheck,
    [switch] $AllowPartial
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $PSScriptRoot "operations-common.ps1")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "release-check-$Timestamp.md"
$ArtifactPath = Join-Path $ReportDir "release-artifacts-$Timestamp.json"
$Results = New-Object System.Collections.Generic.List[string]
$decision = "FAIL"
$partial = $AllowDirty -or $SkipQualityGate -or $SkipImageBuild -or $SkipBackupCheck
$artifactEvidence = $null

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
}

if ($partial -and -not $AllowPartial) {
    throw "Allowing a dirty worktree or skipping a gate makes the check partial. Add -AllowPartial for an explicit diagnostic run."
}
if ($CreateBackup -and -not [string]::IsNullOrWhiteSpace($BackupPath)) {
    throw "Use either -CreateBackup or -BackupPath, not both"
}

$ComposePath = Resolve-CollaPath -Root $Root -Path $ComposeFile
$EnvPath = Resolve-CollaPath -Root $Root -Path $EnvFile
if (-not (Test-Path -LiteralPath $ComposePath -PathType Leaf)) {
    throw "Compose file not found: $ComposePath"
}
if (-not (Test-Path -LiteralPath $EnvPath -PathType Leaf)) {
    throw "Environment file not found: $EnvPath"
}
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

Push-Location $Root
try {
    foreach ($command in @("git", "docker", "pnpm")) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Required command is unavailable: $command"
        }
    }
    docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker daemon is unavailable"
    }
    Add-Result "- PASS: required command line tools and Docker daemon are available"

    $status = git status --porcelain
    $isDirty = [bool] $status
    if ($isDirty -and -not $AllowDirty) {
        throw "Working tree is dirty. Commit changes or pass -AllowDirty only for a non-release diagnostic run."
    }
    if ($isDirty) {
        Add-Result "- PARTIAL: dirty worktree explicitly allowed; produced images are rehearsal artifacts only"
    } else {
        Add-Result "- PASS: Git worktree is clean"
    }

    $environment = Assert-CollaProductionEnvironment -EnvPath $EnvPath
    Add-Result "- PASS: required environment values, immutable image tags, and source revision are valid"

    $headCommit = ([string] (& git rev-parse HEAD | Select-Object -Last 1)).Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to resolve the current Git commit"
    }
    $configuredCommit = ([string] $environment["SOURCE_COMMIT"]).ToLowerInvariant()
    if ($configuredCommit -cne $headCommit) {
        throw "SOURCE_COMMIT '$($environment["SOURCE_COMMIT"])' does not match current Git commit '$headCommit'"
    }
    Add-Result "- PASS: release source revision matches the current Git commit $headCommit"

    $model = Get-CollaComposeModel -ComposePath $ComposePath -EnvPath $EnvPath
    $actualProjectName = [string] $model.name
    if (-not [string]::IsNullOrWhiteSpace($ExpectedProjectName) -and $actualProjectName -cne $ExpectedProjectName) {
        throw "Release target mismatch. Expected '$ExpectedProjectName', compose resolves to '$actualProjectName'."
    }
    foreach ($requiredService in @("postgres", "redis", "minio", "server", "collaboration-a", "collaboration-b", "web", "nginx")) {
        if (-not ($model.services.PSObject.Properties.Name -contains $requiredService)) {
            throw "Production compose is missing required service: $requiredService"
        }
        if (-not ($model.services.$requiredService.PSObject.Properties.Name -contains "healthcheck")) {
            throw "Production compose service has no health check: $requiredService"
        }
    }
    if ([string] $model.services.server.image -cne [string] $environment["SERVER_IMAGE"] -or
        [string] $model.services.web.image -cne [string] $environment["WEB_IMAGE"] -or
        [string] $model.services.'collaboration-a'.image -cne [string] $environment["COLLABORATION_IMAGE"] -or
        [string] $model.services.'collaboration-b'.image -cne [string] $environment["COLLABORATION_IMAGE"]) {
        throw "Compose application images do not match SERVER_IMAGE/WEB_IMAGE/COLLABORATION_IMAGE"
    }
    foreach ($dependencyService in @("postgres", "redis", "minio", "nginx")) {
        $dependencyImage = [string] $model.services.$dependencyService.image
        if ($dependencyImage -notmatch '@sha256:[0-9a-f]{64}$') {
            throw "Production dependency image must be pinned by digest: $dependencyService ($dependencyImage)"
        }
    }
    $publishedPorts = @($model.services.nginx.ports | ForEach-Object { $_.published }) -join ", "
    Add-Result "- PASS: compose config contains the backend plus dual collaboration-node stack (published ports: $publishedPorts)"

    if (-not $SkipBackupCheck) {
        if ($CreateBackup) {
            $before = @(Get-ChildItem -LiteralPath (Resolve-CollaPath -Root $Root -Path $BackupDir) -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
            & (Join-Path $PSScriptRoot "backup.ps1") -ComposeFile $ComposeFile -EnvFile $EnvFile -BackupDir $BackupDir
            if ($LASTEXITCODE -ne 0) {
                throw "Pre-release backup failed"
            }
            $BackupPath = Get-ChildItem -LiteralPath (Resolve-CollaPath -Root $Root -Path $BackupDir) -Directory |
                Where-Object { $before -notcontains $_.FullName } |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 1 -ExpandProperty FullName
        }
        if ([string]::IsNullOrWhiteSpace($BackupPath)) {
            throw "A verified recent backup is required. Pass -BackupPath or -CreateBackup."
        }
        $manifest = Read-CollaBackupManifest -BackupPath $BackupPath -VerifyFiles
        if ([string] $manifest.projectName -cne $actualProjectName) {
            throw "Backup source '$($manifest.projectName)' does not match release target '$actualProjectName'"
        }
        $createdAt = [datetimeoffset]::Parse([string] $manifest.createdAt)
        if ($createdAt -lt [datetimeoffset]::UtcNow.AddHours(-1 * $MaxBackupAgeHours)) {
            throw "Backup is older than $MaxBackupAgeHours hours: $createdAt"
        }
        Add-Result "- PASS: recent manifest-v2 backup matches the release target and all hashes verify"
    } else {
        Add-Result "- SKIP: backup verification was explicitly skipped"
    }

    if (-not $SkipQualityGate) {
        if ($GateMode -eq "full") {
            pnpm verify:full
        } else {
            pnpm verify
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Quality gate failed"
        }
        Add-Result "- PASS: $GateMode quality gate passed"
    } else {
        Add-Result "- SKIP: quality gate was explicitly skipped"
    }

    if (-not $SkipImageBuild) {
        docker compose --env-file $EnvPath -f $ComposePath build server web collaboration-a
        if ($LASTEXITCODE -ne 0) {
            throw "Production image build failed"
        }
        $serverImage = [string] $environment["SERVER_IMAGE"]
        $webImage = [string] $environment["WEB_IMAGE"]
        $collaborationImage = [string] $environment["COLLABORATION_IMAGE"]
        $serverDetails = (& docker image inspect $serverImage --format '{{json .}}' | Out-String) | ConvertFrom-Json
        $webDetails = (& docker image inspect $webImage --format '{{json .}}' | Out-String) | ConvertFrom-Json
        $collaborationDetails = (& docker image inspect $collaborationImage --format '{{json .}}' | Out-String) | ConvertFrom-Json
        $serverRevision = [string] $serverDetails.Config.Labels.'org.opencontainers.image.revision'
        $webRevision = [string] $webDetails.Config.Labels.'org.opencontainers.image.revision'
        $collaborationRevision = [string] $collaborationDetails.Config.Labels.'org.opencontainers.image.revision'
        if ($serverRevision -cne $headCommit -or $webRevision -cne $headCommit -or $collaborationRevision -cne $headCommit) {
            throw "Built image revision labels do not match the release source commit"
        }
        $artifactEvidence = [ordered]@{
            createdAt = (Get-Date).ToUniversalTime().ToString("o")
            sourceCommit = $headCommit
            worktreeDirty = $isDirty
            composeProject = $actualProjectName
            server = [ordered]@{ image = $serverImage; id = [string] $serverDetails.Id; revision = $serverRevision }
            web = [ordered]@{ image = $webImage; id = [string] $webDetails.Id; revision = $webRevision }
            collaboration = [ordered]@{ image = $collaborationImage; id = [string] $collaborationDetails.Id; revision = $collaborationRevision }
        }
        $artifactEvidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ArtifactPath -Encoding UTF8
        Add-Result "- PASS: production images built with immutable tags and matching OCI revision labels"
        Add-Result "- EVIDENCE: image artifact manifest written to $ArtifactPath"
    } else {
        Add-Result "- SKIP: production image build was explicitly skipped"
    }

    $decision = if ($partial) { "PARTIAL" } else { "PASS" }
    if ($partial) {
        Write-Host "Partial release diagnostic completed; this is not release approval."
    } else {
        Write-Host "Release check passed"
    }
} finally {
    $report = @(
        "# Release Check",
        "",
        "- Time: $(Get-Date -Format o)",
        "- Compose file: $ComposeFile",
        "- Environment file: $EnvFile",
        "- Decision: $decision",
        "- Artifact manifest: $(if ($null -ne $artifactEvidence) { $ArtifactPath } else { 'not generated' })",
        "",
        "## Results"
    ) + $Results
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Release check report: $ReportPath"
    Pop-Location
}
