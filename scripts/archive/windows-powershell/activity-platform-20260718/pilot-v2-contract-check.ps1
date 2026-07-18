param(
    [switch] $GenerateRehearsalManifest,
    [string] $RehearsalManifestPath = ".local-pilot\m9-rehearsal.json",
    [string] $RehearsalPilotId = "pilot-v2-m9-rehearsal",
    [string] $RehearsalProjectName = "colla-platform-pilot-m9r1",
    [string] $RehearsalBaseUrl = "http://127.0.0.1:18090"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "pilot-v2-common.ps1")
$ExamplePath = Join-Path $Root "deploy\pilot-v2\manifest.example.json"
$Scratch = Join-Path $Root ".local-reports\pilot-v2-contract-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$Results = New-Object System.Collections.Generic.List[string]

function Assert-Valid {
    param($Manifest, [string] $Level, [string] $Description)
    $result = Test-PilotManifest -Manifest $Manifest -Level $Level
    if (-not $result.valid) {
        throw "$Description failed: $($result.errors -join '; ')"
    }
    $Results.Add("- PASS: $Description") | Out-Null
}

function Assert-Invalid {
    param($Manifest, [string] $Level, [string] $Pattern, [string] $Description)
    $result = Test-PilotManifest -Manifest $Manifest -Level $Level
    if ($result.valid -or (@($result.errors) -join "; ") -notmatch $Pattern) {
        throw "$Description did not produce expected blocker '$Pattern': $($result.errors -join '; ')"
    }
    $Results.Add("- PASS: $Description") | Out-Null
}

New-Item -ItemType Directory -Force -Path $Scratch | Out-Null
try {
    $parseErrors = @()
    foreach ($script in @("pilot-v2-common.ps1", "pilot-v2-manifest-check.ps1", "pilot-v2-contract-check.ps1", "pilot-v2-initialize.ps1", "pilot-v2-readiness.ps1", "pilot-v2-simulation-kickoff.ps1")) {
        $path = Join-Path $PSScriptRoot $script
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $tokens = $null
        $errors = $null
        [void] [System.Management.Automation.Language.Parser]::ParseFile($path, [ref] $tokens, [ref] $errors)
        $parseErrors += @($errors | ForEach-Object { "$script`: $($_.Message)" })
    }
    if ($parseErrors.Count -gt 0) { throw "PowerShell parse errors: $($parseErrors -join '; ')" }
    $Results.Add("- PASS: active PILOT-V2 scripts parse") | Out-Null

    $example = Read-PilotManifest -ManifestPath $ExamplePath
    Assert-Valid -Manifest $example -Level "structural" -Description "versioned example satisfies structural contract"
    Assert-Invalid -Manifest $example -Level "initialization" -Pattern "placeholder|consent|concrete" -Description "example cannot masquerade as an initialization-ready roster"

    $validPath = Join-Path $Scratch "valid.json"
    $raw = Get-Content -LiteralPath $ExamplePath -Raw
    $raw = $raw.Replace("replace-with-pilot-id", "pilot-v2-contract")
    $raw = $raw.Replace("https://replace-with-pilot-host", "https://pilot.internal")
    $raw = $raw.Replace("replace-with-feedback-channel", "Pilot feedback project")
    $raw = $raw -replace 'replace-with-(owner|admin|member-0[1-3])-username', 'pilot-$1'
    $raw = $raw -replace 'replace-with-(owner|admin|member-0[1-3])-name', 'Pilot $1'
    $raw = $raw -replace 'replace-with-(owner|admin|member-0[1-3])-email', 'pilot-$1@colla.local'
    $raw = $raw.Replace('"consentConfirmed": false', '"consentConfirmed": true')
    Set-Content -LiteralPath $validPath -Value $raw -Encoding UTF8
    $valid = Read-PilotManifest -ManifestPath $validPath
    Assert-Valid -Manifest $valid -Level "initialization" -Description "complete 5-person roster is initialization-ready"

    if ($GenerateRehearsalManifest) {
        $valid.mode = "rehearsal"
        $valid.pilotId = $RehearsalPilotId
        $valid.environment.projectName = $RehearsalProjectName
        $valid.environment.baseUrl = $RehearsalBaseUrl
        $valid.kickoffApproval.releaseCommit = "replace-with-40-character-release-commit"
        $outputPath = if ([System.IO.Path]::IsPathRooted($RehearsalManifestPath)) { $RehearsalManifestPath } else { Join-Path $Root $RehearsalManifestPath }
        New-Item -ItemType Directory -Force -Path (Split-Path $outputPath -Parent) | Out-Null
        $valid | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $outputPath -Encoding UTF8
        $Results.Add("- PASS: rehearsal manifest generated at $outputPath") | Out-Null
    }

    $duplicate = $raw | ConvertFrom-Json
    $duplicate.participants[1].username = $duplicate.participants[0].username
    Assert-Invalid -Manifest $duplicate -Level "initialization" -Pattern "username values must be unique" -Description "duplicate account is rejected"

    $missingModule = $raw | ConvertFrom-Json
    $missingModule.scenarios = @($missingModule.scenarios | Where-Object { $_.module -ne "search" })
    Assert-Invalid -Manifest $missingModule -Level "initialization" -Pattern "search.*scenario" -Description "missing required module scenario is rejected"

    $unsupportedGroup = $raw | ConvertFrom-Json
    $unsupportedGroup.organization.userGroup.groupType = "static"
    Assert-Invalid -Manifest $unsupportedGroup -Level "initialization" -Pattern "groupType must be normal or permission" -Description "unsupported user group type is rejected before API mutation"

    Assert-Invalid -Manifest $valid -Level "freeze" -Pattern "kickoffApproval" -Description "human kickoff confirmations are mandatory for freeze"
    $simulation = $raw | ConvertFrom-Json
    $simulation.mode = "rehearsal"
    $simulation.participants | ForEach-Object { $_.participantKind = "synthetic" }
    $participantIds = @($simulation.participants | ForEach-Object { $_.participantId })
    $simulation.kickoffApproval.confirmationBasis = "synthetic-personas"
    $simulation.kickoffApproval.scopeConfirmedBy = $participantIds
    $simulation.kickoffApproval.feedbackConfirmedBy = $participantIds
    $simulation.kickoffApproval.stopConditionsConfirmedBy = $participantIds
    $simulation.kickoffApproval.acceptedAt = "2026-07-15T09:00:00+08:00"
    $simulation.kickoffApproval.releaseCommit = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    $simulation.kickoffApproval.backupManifest = "C:/pilot/manifest.json"
    $simulation.kickoffApproval.sourceSnapshot = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    $simulation.kickoffApproval.limitationsAcknowledged = @("no-real-user-feedback", "no-human-satisfaction-evidence", "not-production-release-approval")
    $simulation.kickoffApproval.decision = "go"
    Assert-Valid -Manifest $simulation -Level "simulation-freeze" -Description "synthetic personas can complete an explicitly limited simulation freeze"
    Assert-Invalid -Manifest $simulation -Level "freeze" -Pattern "human|mode 'real'|human-participants" -Description "simulation evidence cannot masquerade as a real freeze"
    foreach ($result in $Results) { Write-Host $result }
    Write-Host "PILOT-V2 contract check passed ($($Results.Count) checks)"
} finally {
    if (Test-Path -LiteralPath $Scratch) {
        Remove-Item -LiteralPath $Scratch -Recurse -Force
    }
}
