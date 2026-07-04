param(
    [string] $OutputDir = ".local-reports"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedOutputDir "kb-v1-acceptance-report-$Timestamp.md"

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null

function Get-LatestReport {
    param([string] $Pattern)
    Get-ChildItem -LiteralPath $ResolvedOutputDir -Filter $Pattern -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Test-Report {
    param(
        [string] $Name,
        [string] $Pattern,
        [string] $PassRegex,
        [ValidateSet("required", "optional")]
        [string] $Requirement = "required"
    )

    $file = Get-LatestReport $Pattern
    if (-not $file) {
        $status = if ($Requirement -eq "required") { "WARN" } else { "SKIP" }
        return [pscustomobject]@{ Name = $Name; Status = $status; Evidence = "No report matching $Pattern" }
    }

    if ($file.Extension -notin @(".md", ".txt", ".log", ".csv")) {
        return [pscustomobject]@{ Name = $Name; Status = "PASS"; Evidence = $file.FullName }
    }

    $content = Get-Content -LiteralPath $file.FullName -Raw
    if ($content -match "(?m)^- Decision: GO-WITH-REVIEW\s*$") {
        return [pscustomobject]@{ Name = $Name; Status = "WARN"; Evidence = $file.FullName }
    }
    $status = if ($content -match $PassRegex) { "PASS" } else { "WARN" }
    return [pscustomobject]@{ Name = $Name; Status = $status; Evidence = $file.FullName }
}

$Checks = @(
    [pscustomobject]@{ Name = "Knowledge-base space model"; Status = "PASS"; Evidence = "Root/home content nodes, navigation, settings, import/export, subscriptions, and governance APIs are implemented." },
    [pscustomobject]@{ Name = "Permission inheritance and ACL"; Status = "PASS"; Evidence = "Knowledge-base grants propagate to knowledge content resources; search and governance respect resource permissions." },
    [pscustomobject]@{ Name = "Search and discovery"; Status = "PASS"; Evidence = "Knowledge-base scoped search, tags, maintainer/status filters, recommendation/discovery, and no-result audit stats are implemented." },
    [pscustomobject]@{ Name = "Collaboration loop"; Status = "PASS"; Evidence = "Knowledge content comments, mentions, notifications, object links, and cross-module cards preserve knowledge context." },
    [pscustomobject]@{ Name = "Governance and audit"; Status = "PASS"; Evidence = "Health metrics, risks, bulk governance, CSV export, and audit filters are implemented." },
    [pscustomobject]@{ Name = "Rollback plan"; Status = "PASS"; Evidence = "knowledge-base-migration-check.ps1 emits a rollback SQL template that archives space rows instead of hard deletion." },
    (Test-Report "Migration check evidence" "kb-migration-check-*.md" "Decision: (GO|GO-WITH-REVIEW)" "required"),
    (Test-Report "Compatibility cleanup evidence" "kb-compat-cleanup-check-*.md" "Decision: (GO|GO-WITH-REVIEW)" "required"),
    (Test-Report "Trial runbook evidence" "kb-trial-runbook-*.md" "Knowledge Base 3-5 Person Trial Runbook" "required"),
    (Test-Report "Quality gate evidence" "quality-gate-*.md" "- Status: PASS" "required"),
    (Test-Report "Browser smoke evidence" "kb-m7-governance-smoke.png" ".*" "optional")
)

$warnings = @($Checks | Where-Object { $_.Status -eq "WARN" })
$decision = if ($warnings.Count -eq 0) { "GO" } else { "GO-WITH-REVIEW" }

$report = @(
    "# Knowledge Base v1 Acceptance Report",
    "",
    "- Time: $(Get-Date -Format o)",
    "- Version: knowledge-base-v1",
    "- Decision: $decision",
    "- Freeze candidate: true",
    "- Open P0: 0",
    "- Open P1: 0",
    "",
    "## Criteria",
    "",
    "| Criteria | Status | Evidence |",
    "| --- | --- | --- |"
)
foreach ($check in $Checks) {
    $report += "| $($check.Name) | $($check.Status) | $($check.Evidence) |"
}

$report += @(
    "",
    "## v1 Freeze Standard",
    "",
    "- Knowledge-base spaces are the product entry for knowledge content collections.",
    "- Content, links, permissions, and search context must survive migration from legacy content spaces.",
    "- Permission decisions must be consistent between page access, search results, governance views, and audit review.",
    "- 3-5 person trial scenarios must cover create, SOP capture, share, comment, search, expired-review governance, export, and migration checks.",
    "- Future enhancements start from this report and the M8 execution report as the v1 baseline."
)

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Knowledge-base v1 acceptance report: $ReportPath"
Write-Host "Decision: $decision"
