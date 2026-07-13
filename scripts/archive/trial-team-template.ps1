param(
    [string] $OutputDir = ".local-reports"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$CsvPath = Join-Path $ResolvedOutputDir "trial-team-template-$Timestamp.csv"
$ReportPath = Join-Path $ResolvedOutputDir "trial-team-template-$Timestamp.md"

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null

$members = @(
    [pscustomobject]@{ Username = "trial_admin"; DisplayName = "Trial Admin"; Department = "Platform"; Role = "admin"; PrimaryFlow = "Accounts, permissions, audit, backup"; Project = "All" },
    [pscustomobject]@{ Username = "trial_pm"; DisplayName = "Trial PM"; Department = "Project"; Role = "member"; PrimaryFlow = "Requirement triage, task split, meeting follow-up"; Project = "P1/P2/P5" },
    [pscustomobject]@{ Username = "trial_product"; DisplayName = "Trial Product"; Department = "Product"; Role = "member"; PrimaryFlow = "PRD, requirement changes, Base definitions"; Project = "P1/P3" },
    [pscustomobject]@{ Username = "trial_design"; DisplayName = "Trial Design"; Department = "Design"; Role = "member"; PrimaryFlow = "Design handoff and document comments"; Project = "P1" },
    [pscustomobject]@{ Username = "trial_frontend"; DisplayName = "Trial Frontend"; Department = "Engineering"; Role = "member"; PrimaryFlow = "Task work, fix feedback, IM collaboration"; Project = "P1/P2/P5" },
    [pscustomobject]@{ Username = "trial_backend"; DisplayName = "Trial Backend"; Department = "Engineering"; Role = "member"; PrimaryFlow = "API fixes, Base data, release support"; Project = "P2/P3/P4/P5" },
    [pscustomobject]@{ Username = "trial_qa"; DisplayName = "Trial QA"; Department = "Quality"; Role = "member"; PrimaryFlow = "Bug filing, failed verification, passed verification"; Project = "P2/P3/P5" },
    [pscustomobject]@{ Username = "trial_ops"; DisplayName = "Trial Ops"; Department = "Operations"; Role = "member"; PrimaryFlow = "Release approval, health checks, rollback"; Project = "P4/P5" },
    [pscustomobject]@{ Username = "trial_business"; DisplayName = "Trial Business"; Department = "Business"; Role = "member"; PrimaryFlow = "Acceptance, change confirmation, read-only review"; Project = "P1/P3/P4" },
    [pscustomobject]@{ Username = "trial_viewer"; DisplayName = "Trial Viewer"; Department = "Observer"; Role = "member"; PrimaryFlow = "Permission boundary and denied-object checks"; Project = "Read-only/Denied" }
)

$members | Export-Csv -LiteralPath $CsvPath -NoTypeInformation -Encoding UTF8

$report = @(
    "# Trial Team Initialization Template",
    "",
    "- Time: $(Get-Date -Format o)",
    "- CSV: $CsvPath",
    "",
    "## Account Rules",
    "",
    "- Create accounts through `/admin/users` or the admin API.",
    "- Use a one-time password outside this repository; do not commit real credentials.",
    "- Disable accounts immediately after the trial or when a participant leaves.",
    "- Keep `trial_viewer` outside sensitive projects and Bases for permission-boundary checks.",
    "",
    "## Members",
    "",
    "| Username | Display Name | Department | Role | Primary Flow | Project |",
    "| --- | --- | --- | --- | --- | --- |"
)
foreach ($member in $members) {
    $report += "| $($member.Username) | $($member.DisplayName) | $($member.Department) | $($member.Role) | $($member.PrimaryFlow) | $($member.Project) |"
}

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Trial team CSV: $CsvPath"
Write-Host "Trial team report: $ReportPath"
