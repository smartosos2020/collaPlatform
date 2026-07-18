param(
    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,
    [Parameter(Mandatory = $true)]
    [string] $EnvFile,
    [string] $ContractPath = "deploy/pilot-v2/m10-simulation-contract.json",
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $ReportDirectory = ".local-reports",
    [Parameter(Mandatory = $true)]
    [string] $ConfirmationText
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "pilot-v2-common.ps1")

function Resolve-RootPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return [System.IO.Path]::GetFullPath($Path) }
    return [System.IO.Path]::GetFullPath((Join-Path $Root $Path))
}

function Invoke-M10Compose {
    param([string[]] $Arguments)
    & docker compose --env-file $script:EnvPath -f $script:ComposePath @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE" }
}

function Invoke-M10Phase {
    param([string] $Phase)

    $evidence = Join-Path $script:RunDirectory "$Phase.json"
    $log = Join-Path $script:RunDirectory "$Phase-playwright.log"
    $previous = @{
        COLLA_E2E_SUITE = $env:COLLA_E2E_SUITE
        COLLA_E2E_M10_PHASE = $env:COLLA_E2E_M10_PHASE
        COLLA_E2E_M10_RUN_ID = $env:COLLA_E2E_M10_RUN_ID
        COLLA_E2E_M10_EVIDENCE_PATH = $env:COLLA_E2E_M10_EVIDENCE_PATH
        COLLA_E2E_M10_STATE_PATH = $env:COLLA_E2E_M10_STATE_PATH
        COLLA_E2E_API_BASE_URL = $env:COLLA_E2E_API_BASE_URL
        COLLA_E2E_WEB_BASE_URL = $env:COLLA_E2E_WEB_BASE_URL
    }
    try {
        $env:COLLA_E2E_SUITE = "pilot-m10"
        $env:COLLA_E2E_M10_PHASE = $Phase
        $env:COLLA_E2E_M10_RUN_ID = $script:RunId
        $env:COLLA_E2E_M10_EVIDENCE_PATH = $evidence
        $env:COLLA_E2E_M10_STATE_PATH = $script:StatePath
        $env:COLLA_E2E_API_BASE_URL = "$($script:BaseUrl)/api"
        $env:COLLA_E2E_WEB_BASE_URL = $script:BaseUrl
        $env:COLLA_E2E_ADMIN_USERNAME = [string] $script:Manifest.participants[0].username
        $env:COLLA_E2E_MEMBER_USERNAME = "pilot-member-01"
        Push-Location (Join-Path $Root "web")
        try {
            & pnpm exec playwright test --config e2e/playwright.config.ts e2e/pilot-v2-m10-simulation.spec.ts 2>&1 | Tee-Object -FilePath $log | Out-Host
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        if (-not (Test-Path -LiteralPath $evidence -PathType Leaf)) {
            throw "M10 phase '$Phase' did not produce evidence: $evidence"
        }
        if ($exitCode -ne 0) {
            throw "M10 phase '$Phase' failed with exit code $exitCode; first-attempt evidence was preserved"
        }
        return $evidence
    } finally {
        foreach ($name in $previous.Keys) {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], "Process")
        }
    }
}

function Get-Rate {
    param([double] $Numerator, [double] $Denominator)
    if ($Denominator -le 0) { return 0.0 }
    return [math]::Round($Numerator / $Denominator, 4)
}

function Invoke-M10TrackedPhase {
    param([string] $Phase)
    try {
        $script:phaseFiles.Add((Invoke-M10Phase -Phase $Phase)) | Out-Null
    } catch {
        $evidence = Join-Path $script:RunDirectory "$Phase.json"
        if (Test-Path -LiteralPath $evidence -PathType Leaf) { $script:phaseFiles.Add($evidence) | Out-Null }
        throw
    }
}

$ManifestPath = Resolve-RootPath $ManifestPath
$EnvPath = Resolve-RootPath $EnvFile
$ContractPath = Resolve-RootPath $ContractPath
$ComposePath = Resolve-RootPath $ComposeFile
$ReportRoot = Resolve-RootPath $ReportDirectory
foreach ($requiredPath in @($ManifestPath, $EnvPath, $ContractPath, $ComposePath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) { throw "Required M10 file not found: $requiredPath" }
}

$Manifest = Read-PilotManifest -ManifestPath $ManifestPath
$manifestCheck = Test-PilotManifest -Manifest $Manifest -Level "initialization"
if (-not $manifestCheck.valid) { throw "Pilot manifest failed initialization validation: $($manifestCheck.errors -join '; ')" }
if ([string] $Manifest.mode -ne "rehearsal") { throw "M10 synthetic runner requires manifest mode=rehearsal" }
if (@($Manifest.participants | Where-Object { $_.participantKind -ne "synthetic" }).Count -gt 0) {
    throw "M10 synthetic runner requires every participantKind to be synthetic"
}

$projectName = [string] $Manifest.environment.projectName
if ($projectName -notmatch '^colla-platform-pilot-m10[a-z0-9-]*$') {
    throw "M10 runner may only target an isolated project named colla-platform-pilot-m10*; resolved '$projectName'"
}
if ($ConfirmationText -cne "RUN-SIMULATION:$($Manifest.pilotId):$projectName") {
    throw "Confirmation mismatch. Expected RUN-SIMULATION:$($Manifest.pilotId):$projectName"
}

$configuredProject = (& docker compose --env-file $EnvPath -f $ComposePath config --format json | ConvertFrom-Json).name
if ($LASTEXITCODE -ne 0 -or [string] $configuredProject -cne $projectName) {
    throw "Compose project '$configuredProject' does not match manifest project '$projectName'"
}

& (Join-Path $PSScriptRoot "pilot-v2-m10-contract-check.ps1") -ContractPath $ContractPath -ReportDirectory $ReportRoot
if ($LASTEXITCODE -ne 0) { throw "M10 contract validation failed" }

$contract = Get-Content -LiteralPath $ContractPath -Raw | ConvertFrom-Json
$RunId = "m10-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ'))"
$BaseUrl = ([string] $Manifest.environment.baseUrl).TrimEnd('/')
$RunDirectory = Join-Path $ReportRoot "pilot-v2-m10-$RunId"
$StatePath = Join-Path $RunDirectory "state.json"
New-Item -ItemType Directory -Force -Path $RunDirectory | Out-Null
$phaseFiles = New-Object System.Collections.Generic.List[string]
$runnerFailure = $null
$startedAt = (Get-Date).ToUniversalTime().ToString("o")

try {
    Invoke-M10TrackedPhase -Phase "baseline"
    Invoke-M10TrackedPhase -Phase "retry"
    Invoke-M10TrackedPhase -Phase "fault"

    $restartStartedAt = (Get-Date).ToUniversalTime().ToString("o")
    Invoke-M10Compose -Arguments @("restart", "server")
    Invoke-M10Compose -Arguments @("up", "-d", "--wait", "server", "nginx")
    $restartFinishedAt = (Get-Date).ToUniversalTime().ToString("o")
    [ordered]@{
        projectName = $projectName
        service = "server"
        startedAt = $restartStartedAt
        finishedAt = $restartFinishedAt
        decision = "PASS"
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $RunDirectory "service-restart.json") -Encoding UTF8

    Invoke-M10TrackedPhase -Phase "recovery"
} catch {
    $runnerFailure = $_.Exception.Message
}

$allSteps = New-Object System.Collections.Generic.List[object]
foreach ($file in $phaseFiles) {
    $phaseEvidence = Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
    foreach ($step in @($phaseEvidence.steps)) { $allSteps.Add($step) | Out-Null }
}
$steps = $allSteps.ToArray()
$authentication = @($steps | Where-Object { $_.category -eq "authentication" })
$scenarios = @($steps | Where-Object { $_.category -eq "scenario" })
$faults = @($steps | Where-Object { $_.category -eq "fault" })
$failed = @($steps | Where-Object { $_.status -eq "failed" })
$productCritical = @($failed | Where-Object { $_.failureClass -eq "product" -and $_.severity -in @("P0", "P1") })
$harnessFailures = @($failed | Where-Object { $_.failureClass -eq "automation-harness" })
$consistencyFailures = @($failed | Where-Object { $_.stepId -eq "retry-im" -or $_.detail -match '(?i)(idempotency|changed state|lost marker|regression|duplicate)' })
$recoveryScenarios = @($scenarios | Where-Object { $_.phase -eq "recovery" })
$roundCoverage = [ordered]@{}
foreach ($round in @($contract.rounds)) {
    $roundModules = @($scenarios | Where-Object { $_.roundId -eq $round.roundId } | ForEach-Object { [string] $_.module } | Sort-Object -Unique)
    $roundCoverage[$round.roundId] = Get-Rate -Numerator $roundModules.Count -Denominator @($contract.modules).Count
}
$minimumCoverage = if ($roundCoverage.Count -eq 0) { 0.0 } else { [double](@($roundCoverage.Values | Measure-Object -Minimum).Minimum) }
$metrics = [ordered]@{
    personaAuthenticationRate = Get-Rate -Numerator @($authentication | Where-Object { $_.status -eq "passed" }).Count -Denominator $authentication.Count
    scenarioAttemptRate = Get-Rate -Numerator $scenarios.Count -Denominator (@($contract.modules).Count * [int] $contract.minimumRoundCount)
    scenarioSuccessRate = Get-Rate -Numerator @($scenarios | Where-Object { $_.status -eq "passed" }).Count -Denominator $scenarios.Count
    moduleRoundCoverage = $minimumCoverage
    unexpectedAuthorizationCount = @($faults | Where-Object { $_.status -eq "failed" }).Count
    dataConsistencyViolationCount = $consistencyFailures.Count
    openCriticalIssueCount = $productCritical.Count
    faultRecoveryRate = Get-Rate -Numerator @($recoveryScenarios | Where-Object { $_.status -eq "passed" }).Count -Denominator @($contract.modules).Count
    automationFlakeRate = Get-Rate -Numerator $harnessFailures.Count -Denominator $steps.Count
}
$metricPass = [ordered]@{
    personaAuthenticationRate = $metrics.personaAuthenticationRate -eq 1.0
    scenarioAttemptRate = $metrics.scenarioAttemptRate -eq 1.0
    scenarioSuccessRate = $metrics.scenarioSuccessRate -ge 0.95
    moduleRoundCoverage = $metrics.moduleRoundCoverage -eq 1.0
    unexpectedAuthorizationCount = $metrics.unexpectedAuthorizationCount -eq 0
    dataConsistencyViolationCount = $metrics.dataConsistencyViolationCount -eq 0
    openCriticalIssueCount = $metrics.openCriticalIssueCount -eq 0
    faultRecoveryRate = $metrics.faultRecoveryRate -eq 1.0
    automationFlakeRate = $metrics.automationFlakeRate -le 0.05
}
$roundDecisions = [ordered]@{}
foreach ($round in @($contract.rounds)) {
    $roundFailed = @($failed | Where-Object { $_.roundId -eq $round.roundId -and $_.severity -in @("P0", "P1") })
    $roundDecisions[$round.roundId] = if ($roundFailed.Count -eq 0) { "CONTINUE" } else { "STOP" }
}
$decision = if ($null -eq $runnerFailure -and @($metricPass.Values | Where-Object { -not $_ }).Count -eq 0) { "PASS" } else { "FAIL" }
$summary = [ordered]@{
    schemaVersion = 1
    evidenceKind = "synthetic-engineering"
    runId = $RunId
    pilotId = [string] $Manifest.pilotId
    projectName = $projectName
    startedAt = $startedAt
    finishedAt = (Get-Date).ToUniversalTime().ToString("o")
    decision = $decision
    runnerFailure = $runnerFailure
    metrics = $metrics
    metricPass = $metricPass
    moduleRoundCoverage = $roundCoverage
    roundDecisions = $roundDecisions
    phaseEvidence = @($phaseFiles.ToArray() | ForEach-Object { [System.IO.Path]::GetFileName($_) })
    failedSteps = $failed
    limitations = @($contract.limitations)
}
$summaryPath = Join-Path $RunDirectory "summary.json"
$summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$markdown = @(
    "# PILOT-V2 M10 Synthetic Continuous Run",
    "",
    "- Run: $RunId",
    "- Evidence: synthetic engineering evidence; not real participant evidence",
    "- Compose project: $projectName",
    "- Started: $startedAt",
    "- Finished: $($summary.finishedAt)",
    "- Decision: $decision",
    "",
    "## Metrics",
    "",
    "| Metric | Value | Pass |",
    "| --- | ---: | --- |"
)
foreach ($name in $metrics.Keys) { $markdown += "| $name | $($metrics[$name]) | $($metricPass[$name]) |" }
$markdown += @("", "## Round Decisions", "", "| Round | Decision | Coverage |", "| --- | --- | ---: |")
foreach ($name in $roundDecisions.Keys) { $markdown += "| $name | $($roundDecisions[$name]) | $($roundCoverage[$name]) |" }
$markdown += @("", "## Failed Steps", "")
if ($failed.Count -eq 0) { $markdown += "- None" } else { foreach ($step in $failed) { $markdown += "- [$($step.severity)] $($step.stepId): $($step.detail)" } }
$markdown += @("", "## Limitations", "")
foreach ($limitation in @($contract.limitations)) { $markdown += "- $limitation" }
$markdownPath = Join-Path $RunDirectory "summary.md"
$markdown -join [Environment]::NewLine | Set-Content -LiteralPath $markdownPath -Encoding UTF8

Write-Host "M10 synthetic run decision: $decision"
Write-Host "M10 synthetic run summary: $summaryPath"
if ($decision -ne "PASS") { throw "M10 synthetic run failed. See $summaryPath" }
