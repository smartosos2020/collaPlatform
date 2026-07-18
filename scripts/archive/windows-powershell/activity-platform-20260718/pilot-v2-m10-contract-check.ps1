param(
    [string] $ContractPath = "deploy/pilot-v2/m10-simulation-contract.json",
    [string] $ReportDirectory = ".local-reports"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resolvedContract = if ([System.IO.Path]::IsPathRooted($ContractPath)) { $ContractPath } else { Join-Path $Root $ContractPath }
$resolvedReport = if ([System.IO.Path]::IsPathRooted($ReportDirectory)) { $ReportDirectory } else { Join-Path $Root $ReportDirectory }

if (-not (Test-Path -LiteralPath $resolvedContract -PathType Leaf)) {
    throw "M10 simulation contract not found: $resolvedContract"
}

$contract = Get-Content -LiteralPath $resolvedContract -Raw | ConvertFrom-Json
$errors = New-Object System.Collections.Generic.List[string]
$expectedPersonas = @("pilot-owner", "pilot-admin", "pilot-member-01", "pilot-member-02", "pilot-member-03")
$expectedModules = @("im", "project", "knowledge", "base", "approval", "search")
$expectedRounds = @("round-1-baseline", "round-2-retry", "round-3-fault-recovery")
$expectedMetrics = @(
    "personaAuthenticationRate", "scenarioAttemptRate", "scenarioSuccessRate", "moduleRoundCoverage",
    "unexpectedAuthorizationCount", "dataConsistencyViolationCount", "openCriticalIssueCount",
    "faultRecoveryRate", "automationFlakeRate"
)

if ([int] $contract.schemaVersion -ne 1) { $errors.Add("schemaVersion must be 1") | Out-Null }
if ([string] $contract.evidenceKind -ne "synthetic-engineering") { $errors.Add("evidenceKind must be synthetic-engineering") | Out-Null }
if ([int] $contract.minimumRoundCount -lt 3) { $errors.Add("minimumRoundCount must be at least 3") | Out-Null }
if ((@($contract.personas) -join ",") -ne ($expectedPersonas -join ",")) { $errors.Add("personas must define the five synthetic identities in order") | Out-Null }
if ((@($contract.modules) -join ",") -ne ($expectedModules -join ",")) { $errors.Add("modules must cover im, project, knowledge, base, approval, search in order") | Out-Null }
if ((@($contract.rounds | ForEach-Object { $_.roundId }) -join ",") -ne ($expectedRounds -join ",")) { $errors.Add("rounds must define baseline, retry, and fault-recovery") | Out-Null }
foreach ($metric in $expectedMetrics) {
    if (-not ($contract.metrics.PSObject.Properties.Name -contains $metric)) {
        $errors.Add("missing metric: $metric") | Out-Null
    }
}
if (@($contract.stopConditions).Count -lt 4) { $errors.Add("at least four stop conditions are required") | Out-Null }
if (@($contract.limitations).Count -lt 3) { $errors.Add("synthetic evidence limitations are incomplete") | Out-Null }

New-Item -ItemType Directory -Force -Path $resolvedReport | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$result = [ordered]@{
    contractId = [string] $contract.contractId
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    decision = if ($errors.Count -eq 0) { "PASS" } else { "FAIL" }
    personaCount = @($contract.personas).Count
    moduleCount = @($contract.modules).Count
    roundCount = @($contract.rounds).Count
    errors = @($errors)
}
$reportPath = Join-Path $resolvedReport "pilot-v2-m10-contract-$timestamp.json"
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host "M10 contract decision: $($result.decision)"
Write-Host "M10 contract report: $reportPath"
if ($errors.Count -gt 0) { throw ($errors -join "; ") }
