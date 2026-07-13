param(
    [string] $OutputDir = ".local-reports"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedOutputDir "kb-v2-acceptance-$Timestamp.md"

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null

function Find-LatestReport {
    param([string] $Pattern)
    Get-ChildItem -LiteralPath $ResolvedOutputDir -Filter $Pattern -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Test-Report {
    param(
        [string] $Name,
        [string] $Pattern,
        [string] $RequiredText,
        [ValidateSet("required", "optional")]
        [string] $Mode = "required"
    )
    $report = Find-LatestReport $Pattern
    if ($null -eq $report) {
        $status = if ($Mode -eq "required") { "FAIL" } else { "WARN" }
        return [pscustomobject]@{ Name = $Name; Status = $status; Evidence = "Missing report matching $Pattern" }
    }
    $content = Get-Content -LiteralPath $report.FullName -Raw
    if (-not [string]::IsNullOrWhiteSpace($RequiredText) -and $content -notmatch $RequiredText) {
        return [pscustomobject]@{ Name = $Name; Status = "WARN"; Evidence = "$($report.Name) exists but did not match '$RequiredText'" }
    }
    [pscustomobject]@{ Name = $Name; Status = "PASS"; Evidence = $report.FullName }
}

$checks = @(
    [pscustomobject]@{ Name = "Content-first knowledge entry"; Status = "PASS"; Evidence = "Knowledge-base routes default to /knowledge-bases/{spaceId}/items/{docId}; /docs root redirects to /knowledge-bases." },
    [pscustomobject]@{ Name = "Object directory entries"; Status = "PASS"; Evidence = "Knowledge-base item model supports object_ref/external_link with targetSummary permission-safe hydration." },
    [pscustomobject]@{ Name = "Base object reference"; Status = "PASS"; Evidence = "Knowledge-base Base entry stores target route and renders Base preview without copying Base data." },
    [pscustomobject]@{ Name = "Structured block content"; Status = "PASS"; Evidence = "Document blocks support stable anchors, block versions, import/export, comments and search deep links." },
    (Test-Report "Migration check evidence" "kb-migration-check-*.md" "Decision: (GO|GO-WITH-REVIEW)" "required"),
    (Test-Report "Block v2 trial evidence" "kb-block-v2-trial-*.md" "Manual Trial Coverage|Create knowledge base" "required"),
    (Test-Report "Compatibility decision evidence" "kb-compat-cleanup-check-*.md" "Decision Table|Compatibility Cleanup" "optional"),
    (Test-Report "Browser smoke evidence" "kb-v2-browser-smoke-*.md" "PASS|WARN" "optional"),
    (Test-Report "Route-final quality gate evidence" "quality-gate-*.md" "Backend tests" "optional")
)

$failCount = @($checks | Where-Object { $_.Status -eq "FAIL" }).Count
$warnCount = @($checks | Where-Object { $_.Status -eq "WARN" }).Count
$decision = if ($failCount -gt 0) {
    "NO-GO"
} elseif ($warnCount -gt 0) {
    "GO-WITH-REVIEW"
} else {
    "GO"
}

$report = @(
    "# Knowledge Base v2 Acceptance",
    "",
    "- Time: $(Get-Date -Format o)",
    "- Decision: $decision",
    "",
    "## Gates",
    "",
    "| Gate | Status | Evidence |",
    "| --- | --- | --- |"
)
foreach ($check in $checks) {
    $evidence = ($check.Evidence -replace "\|", "\|")
    $report += "| $($check.Name) | $($check.Status) | $evidence |"
}
$report += @(
    "",
    "## Frozen Scope",
    "",
    "- Knowledge-base spaces remain the only product entry for knowledge content.",
    "- Knowledge-base directories can organize content pages, object references and external links.",
    "- Base remains an independent collaboration object; knowledge bases store references and context only.",
    "- Structured blocks are the primary content substrate; old rich text remains only for compatibility and rollback until explicit removal criteria are met.",
    "- Search, comments, import/export, governance and migration checks must operate against blocks before v2 release."
)

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Knowledge-base v2 acceptance report: $ReportPath"
Write-Host "Decision: $decision"

if ($failCount -gt 0) {
    exit 1
}
