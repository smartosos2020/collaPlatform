param(
    [string] $OutputDir = ".local-reports"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedOutputDir "kb-trial-runbook-$Timestamp.md"
$CsvPath = Join-Path $ResolvedOutputDir "kb-trial-checklist-$Timestamp.csv"

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null

$participants = @(
    [pscustomobject]@{ Role = "owner"; Count = 1; Responsibility = "Create knowledge base, set default permission, run governance export" },
    [pscustomobject]@{ Role = "editor"; Count = 1; Responsibility = "Create SOP, import markdown, comment and resolve feedback" },
    [pscustomobject]@{ Role = "reviewer"; Count = 1; Responsibility = "Search, request review, validate expired knowledge flow" },
    [pscustomobject]@{ Role = "viewer"; Count = 1; Responsibility = "Validate read-only access, denied content page, and share link behavior" },
    [pscustomobject]@{ Role = "admin"; Count = 1; Responsibility = "Inspect permission governance, audit logs, and migration report" }
)

$steps = @(
    [pscustomobject]@{ Step = 1; Flow = "Create"; Actor = "owner"; Action = "Create a private knowledge base, set icon/cover/default view permission, and verify root/home content nodes."; Pass = "Space appears in /knowledge-bases and direct URL opens the home content page." },
    [pscustomobject]@{ Step = 2; Flow = "Structure"; Actor = "owner/editor"; Action = "Create folders for SOP, FAQ, incident, and project review; create one content page under each."; Pass = "Tree navigation, breadcrumbs, and knowledge context point to /knowledge-bases/{id}?docId={id}." },
    [pscustomobject]@{ Step = 3; Flow = "Content"; Actor = "editor"; Action = "Use SOP template, import a markdown batch, tag content pages, assign maintainer, and set review due date."; Pass = "Templates, import result, tags, maintainer, and review status are visible in knowledge search/governance." },
    [pscustomobject]@{ Step = 4; Flow = "Share"; Actor = "owner/viewer"; Action = "Grant viewer access at knowledge-base level, break inheritance for one content page, and verify denied access."; Pass = "Allowed pages open; broken-inheritance content page is denied for viewer." },
    [pscustomobject]@{ Step = 5; Flow = "Collaborate"; Actor = "editor/reviewer"; Action = "Comment on a selected section, mention reviewer, resolve and reopen one thread."; Pass = "Comment, notification, and audit trail keep knowledge content context." },
    [pscustomobject]@{ Step = 6; Flow = "Search"; Actor = "reviewer"; Action = "Search by title, tag, maintainer, status, and a term that returns no result inside the knowledge base."; Pass = "ACL-filtered results show only permitted content pages; no-result term appears in governance stats." },
    [pscustomobject]@{ Step = 7; Flow = "Govern"; Actor = "owner/admin"; Action = "Open governance panel, bulk assign maintainer, request review, archive one outdated content page, and export CSV."; Pass = "Metrics change after bulk action; CSV downloads; audit log shows bulk governance event." },
    [pscustomobject]@{ Step = 8; Flow = "Migrate"; Actor = "admin"; Action = "Run knowledge-base migration, compatibility cleanup, and acceptance report scripts."; Pass = "Migration and compatibility checks are GO or approved GO-WITH-REVIEW; acceptance report is GO or approved GO-WITH-REVIEW." }
)

$steps | Export-Csv -LiteralPath $CsvPath -NoTypeInformation -Encoding UTF8

$report = @(
    "# Knowledge Base 3-5 Person Trial Runbook",
    "",
    "- Time: $(Get-Date -Format o)",
    "- Checklist CSV: $CsvPath",
    "- Scope: create, structure, content, share, collaborate, search, govern, migrate",
    "",
    "## Participants",
    "",
    "| Role | Count | Responsibility |",
    "| --- | --- | --- |"
)
foreach ($participant in $participants) {
    $report += "| $($participant.Role) | $($participant.Count) | $($participant.Responsibility) |"
}

$report += @(
    "",
    "## Trial Steps",
    "",
    "| Step | Flow | Actor | Action | Pass Criteria |",
    "| --- | --- | --- | --- | --- |"
)
foreach ($step in $steps) {
    $report += "| $($step.Step) | $($step.Flow) | $($step.Actor) | $($step.Action) | $($step.Pass) |"
}

$report += @(
    "",
    "## Exit Criteria",
    "",
    "- No P0/P1 defects remain open for create, permission, search, collaboration, governance, or migration flows.",
    "- All participants finish their assigned steps without database reset.",
    "- Admin can reproduce audit evidence for permission changes, search no-result, and bulk governance.",
    "- Owner can export a governance CSV and explain remaining overdue or unmaintained content pages."
)

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Knowledge-base trial runbook: $ReportPath"
Write-Host "Checklist CSV: $CsvPath"
