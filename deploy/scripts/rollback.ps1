param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [Parameter(Mandatory = $true)]
    [string] $ServerImage,
    [Parameter(Mandatory = $true)]
    [string] $WebImage,
    [Parameter(Mandatory = $true)]
    [string] $CollaborationImage,
    [string] $ExpectedSourceCommit = "",
    [string] $BackupPath = "",
    [string] $BaseUrl = "http://localhost",
    [Parameter(Mandatory = $true)]
    [string] $ExpectedProjectName,
    [Parameter(Mandatory = $true)]
    [string] $ConfirmationText,
    [switch] $ConfirmRollback,
    [switch] $RestoreData
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $PSScriptRoot "operations-common.ps1")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "rollback-$Timestamp.md"
$Results = New-Object System.Collections.Generic.List[string]
$decision = "FAIL"

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
}

function Get-ImageEvidence {
    param([Parameter(Mandatory = $true)][string] $Image)

    if ($Image -notmatch '^[^\s:]+(?:/[^\s:]+)*:[^\s:]+$' -or $Image.EndsWith(":latest")) {
        throw "Rollback images must use explicit immutable release tags, not latest: $Image"
    }
    $raw = (& docker image inspect $Image --format '{{json .}}' | Out-String)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)) {
        throw "Rollback image is not available locally: $Image"
    }
    $details = $raw | ConvertFrom-Json
    $revision = [string] $details.Config.Labels.'org.opencontainers.image.revision'
    if ($revision -notmatch '^[0-9a-fA-F]{40}$') {
        throw "Rollback image has no valid source revision label: $Image"
    }
    return [pscustomobject]@{
        image = $Image
        id = [string] $details.Id
        revision = $revision.ToLowerInvariant()
    }
}

if (-not $ConfirmRollback) {
    throw "Rollback changes the running environment. Add -ConfirmRollback after confirming the target."
}
$expectedConfirmation = "ROLLBACK:$ExpectedProjectName`:$ServerImage`:$WebImage`:$CollaborationImage"
if ($ConfirmationText -cne $expectedConfirmation) {
    throw "Confirmation text must exactly match $expectedConfirmation"
}
if ($RestoreData -and [string]::IsNullOrWhiteSpace($BackupPath)) {
    throw "-BackupPath is required when -RestoreData is used"
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

$previousServerImage = $env:SERVER_IMAGE
$previousWebImage = $env:WEB_IMAGE
$previousCollaborationImage = $env:COLLABORATION_IMAGE
$previousSourceCommit = $env:SOURCE_COMMIT
try {
    $serverEvidence = Get-ImageEvidence -Image $ServerImage
    $webEvidence = Get-ImageEvidence -Image $WebImage
    $collaborationEvidence = Get-ImageEvidence -Image $CollaborationImage
    if ($serverEvidence.revision -cne $webEvidence.revision -or $serverEvidence.revision -cne $collaborationEvidence.revision) {
        throw "Server, web, and collaboration rollback images were built from different revisions"
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedSourceCommit) -and
        $serverEvidence.revision -cne $ExpectedSourceCommit.ToLowerInvariant()) {
        throw "Rollback image revision '$($serverEvidence.revision)' does not match expected '$ExpectedSourceCommit'"
    }
    Add-Result "- PASS: immutable server, web, and collaboration images share revision $($serverEvidence.revision)"

    $env:SERVER_IMAGE = $ServerImage
    $env:WEB_IMAGE = $WebImage
    $env:COLLABORATION_IMAGE = $CollaborationImage
    $env:SOURCE_COMMIT = $serverEvidence.revision
    $actualProjectName = Get-CollaComposeProjectName -ComposePath $ComposePath -EnvPath $EnvPath
    if ($actualProjectName -cne $ExpectedProjectName) {
        throw "Rollback target mismatch. Expected '$ExpectedProjectName', compose resolves to '$actualProjectName'."
    }
    Add-Result "- PASS: compose target is the explicitly confirmed project $actualProjectName"

    Push-Location $Root
    try {
        if ($RestoreData) {
            & (Join-Path $PSScriptRoot "restore.ps1") `
                -BackupPath $BackupPath `
                -ComposeFile $ComposeFile `
                -EnvFile $EnvFile `
                -BaseUrl $BaseUrl `
                -ExpectedProjectName $ExpectedProjectName `
                -ConfirmationText "RESTORE:$ExpectedProjectName" `
                -ConfirmRestore
            if ($LASTEXITCODE -ne 0) {
                throw "Data restore failed during rollback"
            }
            Add-Result "- PASS: verified data restore completed with rollback images selected"
        } else {
            & docker compose --env-file $EnvPath -f $ComposePath up -d --no-build --wait
            if ($LASTEXITCODE -ne 0) {
                throw "Compose deployment from rollback images failed"
            }
            & (Join-Path $PSScriptRoot "health-check.ps1") `
                -ComposeFile $ComposeFile `
                -EnvFile $EnvFile `
                -BaseUrl $BaseUrl `
                -ExpectedProjectName $ExpectedProjectName `
                -RequireLogCorrelation
            if ($LASTEXITCODE -ne 0) {
                throw "Health check failed after application rollback"
            }
            Add-Result "- PASS: image-only rollback passed health and request-log correlation"
        }
    } finally {
        Pop-Location
    }

    $decision = "PASS"
    Write-Host "Rollback completed without changing the Git worktree"
} finally {
    $report = @(
        "# Rollback Evidence",
        "",
        "- Time: $(Get-Date -Format o)",
        "- Expected project: $ExpectedProjectName",
        "- Server image: $ServerImage",
        "- Web image: $WebImage",
        "- Collaboration image: $CollaborationImage",
        "- Restore data: $RestoreData",
        "- Decision: $decision",
        "",
        "## Results"
    ) + $Results
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Rollback evidence: $ReportPath"

    $env:SERVER_IMAGE = $previousServerImage
    $env:WEB_IMAGE = $previousWebImage
    $env:COLLABORATION_IMAGE = $previousCollaborationImage
    $env:SOURCE_COMMIT = $previousSourceCommit
}
