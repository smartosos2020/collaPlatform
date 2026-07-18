param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BaseUrl = "http://localhost",
    [string] $ExpectedProjectName = "",
    [switch] $RunRestore,
    [switch] $ConfirmRestore
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $PSScriptRoot "operations-common.ps1")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "restore-drill-$Timestamp.md"
$Results = New-Object System.Collections.Generic.List[string]
$startedAt = Get-Date
$decision = "FAIL"

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
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
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

try {
    $manifest = Read-CollaBackupManifest -BackupPath $BackupDir -VerifyFiles
    foreach ($file in @($manifest.files)) {
        Add-Result "- PASS: $($file.name) size and SHA-256 verified"
    }

    $actualProjectName = Get-CollaComposeProjectName -ComposePath $ComposePath -EnvPath $EnvPath
    Add-Result "- PASS: compose config validated as project $actualProjectName"
    if (-not [string]::IsNullOrWhiteSpace($ExpectedProjectName) -and $actualProjectName -cne $ExpectedProjectName) {
        throw "Restore drill target mismatch. Expected '$ExpectedProjectName', compose resolves to '$actualProjectName'."
    }

    if ($RunRestore) {
        if (-not $ConfirmRestore) {
            throw "Restore drill writes data. Add -ConfirmRestore to continue."
        }
        if ([string]::IsNullOrWhiteSpace($ExpectedProjectName)) {
            throw "-ExpectedProjectName is required for a restore drill"
        }
        if ($actualProjectName -notmatch '^colla-platform-drill-[a-z0-9-]+$') {
            throw "Restore drills may only target an isolated project named colla-platform-drill-<id>; resolved '$actualProjectName'."
        }
        if ($actualProjectName -ceq [string] $manifest.projectName) {
            throw "Restore drill target must differ from the backup source project"
        }
        & (Join-Path $PSScriptRoot "restore.ps1") `
            -BackupPath $BackupDir `
            -ComposeFile $ComposeFile `
            -EnvFile $EnvFile `
            -BaseUrl $BaseUrl `
            -ExpectedProjectName $ExpectedProjectName `
            -ConfirmationText "RESTORE:$ExpectedProjectName" `
            -ConfirmRestore
        if ($LASTEXITCODE -ne 0) {
            throw "Restore command failed"
        }
        Add-Result "- PASS: isolated restore and post-restore health verification completed"
        $decision = "PASS"
    } else {
        Add-Result "- PASS: dry-run completed; no target data was changed"
        $decision = "DRY-RUN-PASS"
    }
} finally {
    $report = @(
        "# Restore Drill",
        "",
        "- Started: $($startedAt.ToUniversalTime().ToString('o'))",
        "- Finished: $((Get-Date).ToUniversalTime().ToString('o'))",
        "- Backup path: $BackupDir",
        "- Mode: $(if ($RunRestore) { 'isolated-restore' } else { 'dry-run' })",
        "- Decision: $decision",
        "",
        "## Results"
    ) + $Results
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Restore drill report: $ReportPath"
}
