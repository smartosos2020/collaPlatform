param(
    [Parameter(Mandatory = $true)][string] $ManifestPath,
    [Parameter(Mandatory = $true)][string] $BackupPath,
    [Parameter(Mandatory = $true)][string] $ConfirmationText,
    [string] $ReportDirectory = ".local-reports"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "pilot-v2-common.ps1")
. (Join-Path $Root "deploy\scripts\operations-common.ps1")

function Resolve-ProjectPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return [System.IO.Path]::GetFullPath($Path) }
    return [System.IO.Path]::GetFullPath((Join-Path $Root $Path))
}

function Set-PilotProperty {
    param([Parameter(Mandatory = $true)] $Target, [Parameter(Mandatory = $true)][string] $Name, $Value)
    $Target | Add-Member -MemberType NoteProperty -Name $Name -Value $Value -Force
}

$resolvedManifest = Resolve-ProjectPath $ManifestPath
$localPilotRoot = [System.IO.Path]::GetFullPath((Join-Path $Root ".local-pilot")) + [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedManifest.StartsWith($localPilotRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Simulation kickoff may only update an ignored manifest under .local-pilot"
}

$manifest = Read-PilotManifest -ManifestPath $resolvedManifest
$initializationValidation = Test-PilotManifest -Manifest $manifest -Level "initialization"
if (-not $initializationValidation.valid) {
    throw "Manifest is not initialization-ready: $(@($initializationValidation.errors) -join '; ')"
}
if ([string] $manifest.mode -ne "rehearsal") {
    throw "Simulation kickoff requires manifest mode 'rehearsal'"
}

$expectedConfirmation = "SIMULATE:$($manifest.pilotId)"
if ($ConfirmationText -ne $expectedConfirmation) {
    throw "ConfirmationText must be exactly '$expectedConfirmation'"
}

$resolvedBackup = Resolve-ProjectPath $BackupPath
$backupManifest = Read-CollaBackupManifest -BackupPath $resolvedBackup -VerifyFiles
if ([string] $backupManifest.projectName -ne [string] $manifest.environment.projectName) {
    throw "Backup project '$($backupManifest.projectName)' does not match '$($manifest.environment.projectName)'"
}

$participantIds = @($manifest.participants | ForEach-Object { [string] $_.participantId })
foreach ($participant in @($manifest.participants)) {
    Set-PilotProperty -Target $participant -Name "participantKind" -Value "synthetic"
}
$kickoff = $manifest.kickoffApproval
Set-PilotProperty -Target $kickoff -Name "confirmationBasis" -Value "synthetic-personas"
$manifest.kickoffApproval.scopeConfirmedBy = $participantIds
$manifest.kickoffApproval.feedbackConfirmedBy = $participantIds
$manifest.kickoffApproval.stopConditionsConfirmedBy = $participantIds
$manifest.kickoffApproval.acceptedAt = (Get-Date).ToUniversalTime().ToString("o")
$manifest.kickoffApproval.releaseCommit = [string] $backupManifest.sourceGitCommit
$manifest.kickoffApproval.backupManifest = Join-Path $resolvedBackup "manifest.json"
Set-PilotProperty -Target $kickoff -Name "sourceSnapshot" -Value (Get-PilotSourceSnapshot -Root $Root)
Set-PilotProperty -Target $kickoff -Name "limitationsAcknowledged" -Value @(
    "no-real-user-feedback",
    "no-human-satisfaction-evidence",
    "not-production-release-approval"
)
$manifest.kickoffApproval.decision = "go"

$freezeValidation = Test-PilotManifest -Manifest $manifest -Level "simulation-freeze"
if (-not $freezeValidation.valid) {
    throw "Simulation freeze contract failed: $(@($freezeValidation.errors) -join '; ')"
}

$manifest | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $resolvedManifest -Encoding UTF8
$reportRoot = Resolve-ProjectPath $ReportDirectory
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $reportRoot "pilot-v2-simulation-kickoff-$timestamp.md"
$lines = @(
    "# PILOT-V2 Synthetic Kickoff",
    "",
    "- Pilot: $($manifest.pilotId)",
    "- Decision: SIMULATION-GO",
    "- Confirmation basis: synthetic personas",
    "- Participants: $($participantIds.Count)",
    "- Source snapshot: $($manifest.kickoffApproval.sourceSnapshot)",
    "- Backup manifest: $($manifest.kickoffApproval.backupManifest)",
    "- Release commit recorded by backup: $($manifest.kickoffApproval.releaseCommit)",
    "",
    "## Limitations",
    "",
    "- No real-user feedback was collected.",
    "- No human satisfaction evidence was collected.",
    "- This is not production release approval."
)
Set-Content -LiteralPath $reportPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Simulation kickoff decision: SIMULATION-GO"
Write-Host "Simulation kickoff report: $reportPath"
