param(
    [Parameter(Mandatory = $true)][string] $ManifestPath,
    [Parameter(Mandatory = $true)][string] $InitializationReceiptPath,
    [Parameter(Mandatory = $true)][string] $BackupPath,
    [Parameter(Mandatory = $true)][string] $RestoreDrillReportPath,
    [Parameter(Mandatory = $true)][string] $QualityGateReportPath,
    [string] $ApiBaseUrl = "",
    [string] $ReportDirectory = ".local-reports",
    [switch] $Freeze,
    [switch] $SimulationFreeze
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "pilot-v2-common.ps1")
. (Join-Path $Root "deploy\scripts\operations-common.ps1")

if ($Freeze -and $SimulationFreeze) {
    throw "-Freeze and -SimulationFreeze are mutually exclusive"
}

function Resolve-ProjectPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return [System.IO.Path]::GetFullPath($Path) }
    return [System.IO.Path]::GetFullPath((Join-Path $Root $Path))
}

function Add-Check {
    param([string] $Name, [bool] $Passed, [string] $Detail)
    $script:Checks.Add([ordered]@{ name = $Name; passed = $Passed; detail = $Detail }) | Out-Null
    Write-Host "- $(if ($Passed) { 'PASS' } else { 'BLOCKED' }): $Name - $Detail"
}

function Invoke-PilotReadinessApi {
    param([string] $Method, [string] $Path, [object] $Body = $null, [hashtable] $Headers = @{})
    $parameters = @{
        Uri = "$script:ApiRoot$Path"
        Method = $Method
        Headers = $Headers
        ContentType = "application/json"
        UseBasicParsing = $true
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 8 }
    $response = Invoke-RestMethod @parameters
    if ($response -is [System.Array] -and $response.Count -eq 0) { return }
    return $response
}

$resolvedManifest = Resolve-ProjectPath $ManifestPath
$resolvedReceipt = Resolve-ProjectPath $InitializationReceiptPath
$resolvedBackup = Resolve-ProjectPath $BackupPath
$resolvedRestoreReport = Resolve-ProjectPath $RestoreDrillReportPath
$resolvedQualityReport = Resolve-ProjectPath $QualityGateReportPath
$reportRoot = Resolve-ProjectPath $ReportDirectory
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null

$manifest = Read-PilotManifest -ManifestPath $resolvedManifest
$validationLevel = if ($Freeze) { "freeze" } elseif ($SimulationFreeze) { "simulation-freeze" } else { "initialization" }
$validation = Test-PilotManifest -Manifest $manifest -Level $validationLevel
$receipt = Get-Content -LiteralPath $resolvedReceipt -Raw | ConvertFrom-Json
$backupManifest = Read-CollaBackupManifest -BackupPath $resolvedBackup -VerifyFiles
$restoreReport = Get-Content -LiteralPath $resolvedRestoreReport -Raw
$qualityReport = Get-Content -LiteralPath $resolvedQualityReport -Raw
$script:Checks = New-Object System.Collections.Generic.List[object]

Add-Check -Name "manifest-$validationLevel" -Passed ([bool] $validation.valid) -Detail $(if ($validation.valid) { "manifest contract passed" } else { @($validation.errors) -join "; " })
Add-Check -Name "initialization-receipt" -Passed ($receipt.decision -eq "APPLIED" -and $receipt.pilotId -eq $manifest.pilotId -and $receipt.targetProject -eq $manifest.environment.projectName) -Detail "decision=$($receipt.decision), target=$($receipt.targetProject)"
Add-Check -Name "backup-source" -Passed ($backupManifest.projectName -eq $manifest.environment.projectName) -Detail "project=$($backupManifest.projectName), flyway=$($backupManifest.flywayVersion), consistency=$($backupManifest.consistencyMode)"
$escapedBackup = [regex]::Escape($resolvedBackup)
Add-Check -Name "restore-drill" -Passed ($restoreReport -match '(?m)^- Decision: PASS\s*$' -and $restoreReport -match $escapedBackup) -Detail "isolated restore report references the selected backup and passed"
Add-Check -Name "quality-gate" -Passed ($qualityReport -match '(?m)^- Status: PASS\s*$') -Detail "selected quality gate reports PASS"

$adminUsername = [string] $env:COLLA_PILOT_ADMIN_USERNAME
$adminPassword = [string] $env:COLLA_PILOT_ADMIN_PASSWORD
if ([string]::IsNullOrWhiteSpace($adminUsername) -or [string]::IsNullOrWhiteSpace($adminPassword)) {
    Add-Check -Name "api-session" -Passed $false -Detail "COLLA_PILOT_ADMIN_USERNAME and COLLA_PILOT_ADMIN_PASSWORD are required"
} else {
    $targetBase = if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) { [string] $manifest.environment.baseUrl } else { $ApiBaseUrl }
    $script:ApiRoot = $targetBase.TrimEnd("/")
    if (-not $script:ApiRoot.EndsWith("/api")) { $script:ApiRoot += "/api" }
    try {
        $health = Invoke-PilotReadinessApi -Method GET -Path "/health"
        $session = Invoke-PilotReadinessApi -Method POST -Path "/auth/login" -Body @{
            username = $adminUsername
            password = $adminPassword
            deviceType = "web"
            deviceFingerprint = "pilot-v2-readiness-$($manifest.pilotId)"
            deviceName = "PILOT-V2 readiness"
            appVersion = "pilot-v2"
        }
        $headers = @{ Authorization = "Bearer $($session.accessToken)" }
        Add-Check -Name "api-session" -Passed ($health.status -eq "ok" -and -not [string]::IsNullOrWhiteSpace([string] $session.accessToken)) -Detail "health and authenticated session passed"

        $projects = @(Invoke-PilotReadinessApi -Method GET -Path "/projects" -Headers $headers)
        $pilotProject = $projects | Where-Object { $_.projectKey -eq $manifest.templates.project.projectKey } | Select-Object -First 1
        if ($null -eq $pilotProject) {
            Add-Check -Name "open-p0-p1" -Passed $false -Detail "pilot project was not found"
        } else {
            $urgent = @(Invoke-PilotReadinessApi -Method GET -Path "/projects/$($pilotProject.id)/issues?priority=urgent" -Headers $headers)
            $high = @(Invoke-PilotReadinessApi -Method GET -Path "/projects/$($pilotProject.id)/issues?priority=high" -Headers $headers)
            $openBlockers = @($urgent + $high | Where-Object { $_.status -notin @("done", "closed", "rejected") })
            Add-Check -Name "open-p0-p1" -Passed ($openBlockers.Count -eq 0) -Detail "open urgent/high issues=$($openBlockers.Count)"
        }
    } catch {
        Add-Check -Name "api-runtime" -Passed $false -Detail $_.Exception.Message
    }
}

if ($Freeze) {
    $head = (& git -C $Root rev-parse HEAD).Trim()
    $dirty = @(& git -C $Root status --porcelain)
    Add-Check -Name "release-commit" -Passed ($dirty.Count -eq 0 -and $head -eq $manifest.kickoffApproval.releaseCommit -and $head -eq $backupManifest.sourceGitCommit) -Detail "clean=$($dirty.Count -eq 0), head=$head, backup=$($backupManifest.sourceGitCommit)"
}
if ($SimulationFreeze) {
    $sourceSnapshot = Get-PilotSourceSnapshot -Root $Root
    $declaredBackupManifest = Resolve-ProjectPath ([string] $manifest.kickoffApproval.backupManifest)
    $selectedBackupManifest = Join-Path $resolvedBackup "manifest.json"
    Add-Check -Name "simulation-source-snapshot" -Passed ($sourceSnapshot -eq [string] $manifest.kickoffApproval.sourceSnapshot) -Detail "current=$sourceSnapshot, declared=$($manifest.kickoffApproval.sourceSnapshot)"
    Add-Check -Name "simulation-backup-manifest" -Passed ($declaredBackupManifest -eq $selectedBackupManifest) -Detail "declared backup manifest matches selected backup"
    Add-Check -Name "simulation-release-commit" -Passed ([string] $manifest.kickoffApproval.releaseCommit -eq [string] $backupManifest.sourceGitCommit) -Detail "declared=$($manifest.kickoffApproval.releaseCommit), backup=$($backupManifest.sourceGitCommit)"
}

$blocked = @($script:Checks.ToArray() | Where-Object { -not $_.passed })
$decision = if ($blocked.Count -gt 0) { "BLOCKED" } elseif ($Freeze) { "READY" } elseif ($SimulationFreeze) { "SIMULATION-READY" } else { "REHEARSAL-READY" }
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$jsonPath = Join-Path $reportRoot "pilot-v2-readiness-$timestamp.json"
$markdownPath = Join-Path $reportRoot "pilot-v2-readiness-$timestamp.md"
$result = [ordered]@{
    schemaVersion = 1
    pilotId = [string] $manifest.pilotId
    mode = if ($Freeze) { "freeze" } elseif ($SimulationFreeze) { "simulation-freeze" } else { "rehearsal" }
    decision = $decision
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    checks = @($script:Checks.ToArray())
}
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
$lines = @(
    "# PILOT-V2 Readiness",
    "",
    "- Pilot: $($manifest.pilotId)",
    "- Mode: $($result.mode)",
    "- Decision: $decision",
    "- Checked: $($result.checkedAt)",
    "",
    "| Check | Result | Detail |",
    "| --- | --- | --- |"
)
$lines += @($script:Checks.ToArray() | ForEach-Object { "| $($_.name) | $(if ($_.passed) { 'PASS' } else { 'BLOCKED' }) | $($_.detail -replace '\|', '\|') |" })
Set-Content -LiteralPath $markdownPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Readiness decision: $decision"
Write-Host "Readiness report: $markdownPath"
if ($decision -eq "BLOCKED") { exit 2 }
