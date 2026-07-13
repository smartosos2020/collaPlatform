param(
    [string] $ReportDir = ".local-reports",
    [switch] $RequireFullGate,
    [switch] $RequireDataReset,
    [switch] $M31SmokePassed
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedReportDir = if ([System.IO.Path]::IsPathRooted($ReportDir)) { $ReportDir } else { Join-Path $Root $ReportDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedReportDir "team-trial-readiness-$Timestamp.md"
$Checks = New-Object System.Collections.Generic.List[object]

New-Item -ItemType Directory -Force -Path $ResolvedReportDir | Out-Null

function Add-Check {
    param(
        [string] $Name,
        [ValidateSet("PASS", "WARN", "FAIL")]
        [string] $Status,
        [string] $Evidence
    )
    $Checks.Add([pscustomobject]@{ Name = $Name; Status = $Status; Evidence = $Evidence }) | Out-Null
}

function Get-LatestFile {
    param([string] $Pattern)
    Get-ChildItem -LiteralPath $ResolvedReportDir -Filter $Pattern -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Test-ReportStatus {
    param(
        [string] $Pattern,
        [string] $RequiredPattern,
        [string] $Name,
        [string] $MissingStatus = "WARN"
    )
    $file = Get-LatestFile $Pattern
    if (-not $file) {
        Add-Check $Name $MissingStatus "No report matching $Pattern"
        return
    }
    $content = Get-Content -LiteralPath $file.FullName -Raw
    if ($content -match $RequiredPattern) {
        Add-Check $Name "PASS" $file.FullName
    } else {
        Add-Check $Name "FAIL" $file.FullName
    }
}

function Test-AnyReportStatus {
    param(
        [string] $Pattern,
        [string] $RequiredPattern,
        [string] $Name,
        [string] $MissingStatus = "WARN"
    )
    $files = @(
        Get-ChildItem -LiteralPath $ResolvedReportDir -Filter $Pattern -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    )
    if ($files.Count -eq 0) {
        Add-Check $Name $MissingStatus "No report matching $Pattern"
        return
    }
    foreach ($file in $files) {
        $content = Get-Content -LiteralPath $file.FullName -Raw
        if ($content -match $RequiredPattern) {
            Add-Check $Name "PASS" $file.FullName
            return
        }
    }
    Add-Check $Name "FAIL" "No report matching $Pattern contains $RequiredPattern; latest: $($files[0].FullName)"
}

$requiredDocs = @(
    "docs\05-runbooks\team-trial-readiness.md",
    "docs\05-runbooks\admin-operations.md",
    "docs\02-roadmap\current-roadmap.md",
    "docs\90-reports\m40-execution-report.md",
    ".github\ISSUE_TEMPLATE\trial-issue.yml"
)
foreach ($doc in $requiredDocs) {
    $path = Join-Path $Root $doc
    Add-Check "Required artifact $doc" ($(if (Test-Path $path) { "PASS" } else { "FAIL" })) $path
}

$qualityPattern = if ($RequireFullGate) { "- Mode: full" } else { "- Status: PASS" }
Test-ReportStatus "quality-gate-*.md" $qualityPattern "Quality gate evidence" "FAIL"
Test-ReportStatus "security-audit-gate-*.md" "- Status: PASS" "Security audit evidence" "FAIL"
Test-ReportStatus "performance-baseline-*.md" "- Status: (PASS|WARN)" "Performance baseline evidence" "WARN"
Test-ReportStatus "restore-drill-*.md" "dry-run only; restore command was not executed" "Restore drill evidence" "WARN"
Test-ReportStatus "health-check-*.md" "Prometheus metrics" "Health and metrics evidence" "WARN"

if ($RequireDataReset) {
    Test-AnyReportStatus "m31-collab-simulation-*.md" "- Stage: all" "M31 data reset evidence" "FAIL"
} else {
    Add-Check "M31 data reset evidence" "WARN" "Not required by this run"
}

if ($M31SmokePassed) {
    Add-Check "M31 browser smoke evidence" "PASS" "Caller supplied -M31SmokePassed after pnpm smoke:m31"
} else {
    Add-Check "M31 browser smoke evidence" ($(if ($RequireDataReset) { "FAIL" } else { "WARN" })) "No explicit smoke pass supplied"
}

$failures = @($Checks | Where-Object { $_.Status -eq "FAIL" })
$warnings = @($Checks | Where-Object { $_.Status -eq "WARN" })
$decision = if ($failures.Count -eq 0) { "GO" } else { "NO-GO" }

$report = @(
    "# Team Trial Readiness Report",
    "",
    "- Time: $(Get-Date -Format o)",
    "- Decision: $decision",
    "- Failures: $($failures.Count)",
    "- Warnings: $($warnings.Count)",
    "",
    "| Check | Status | Evidence |",
    "| --- | --- | --- |"
)
foreach ($check in $Checks) {
    $report += "| $($check.Name) | $($check.Status) | $($check.Evidence) |"
}

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Team trial readiness report: $ReportPath"
Write-Host "Decision: $decision"

if ($decision -eq "NO-GO") {
    exit 1
}
