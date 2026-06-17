param(
    [ValidateSet("start", "checkpoint", "finish")]
    [string] $Stage,

    [string] $Goal = "",

    [string] $TaskRange = "",

    [ValidateSet("code-doc-report", "archive-only")]
    [string] $DocMode = "code-doc-report",

    [ValidateSet("quick", "full")]
    [string] $GateMode = "quick"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ReportDir = Join-Path $Root ".local-reports"
$ContextPath = Join-Path $ReportDir "work-cycle-current.json"
$MaxTasksPerWorkCycle = 8
$MaxMilestonesPerWorkCycle = 1
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Get-Milestone {
    param([string] $Range, [string] $Name)

    $source = if (-not [string]::IsNullOrWhiteSpace($Range)) { $Range } else { $Name }
    if ($source -match "(M\d{1,3})") {
        return $Matches[1].ToUpperInvariant()
    }
    return ""
}

function Get-NormalizedGitStatusPaths {
    if (-not (Get-Command git -ErrorAction SilentlyContinue) -or -not (Test-Path (Join-Path $Root ".git"))) {
        return @()
    }

    Push-Location $Root
    try {
        $status = git status --porcelain -uall
        if (-not $status) {
            return @()
        }
        return $status | ForEach-Object {
            $path = $_.Substring(3).Trim()
            if ($path -match " -> ") {
                $path = ($path -split " -> ")[1]
            }
            $path.Replace("\", "/")
        }
    } finally {
        Pop-Location
    }
}

function Get-TaskReferences {
    param([string] $Range)

    if ([string]::IsNullOrWhiteSpace($Range)) {
        return @()
    }

    $items = New-Object System.Collections.Generic.List[object]
    $matches = [regex]::Matches($Range.ToUpperInvariant(), "M(\d{1,3})-T(\d{2})")
    foreach ($match in $matches) {
        $items.Add([ordered]@{
            raw = $match.Value
            milestone = "M$([int] $match.Groups[1].Value)"
            task = [int] $match.Groups[2].Value
        }) | Out-Null
    }
    return $items.ToArray()
}

function Get-WorkScope {
    param([string] $Range)

    $refs = @(Get-TaskReferences -Range $Range)
    $milestones = @($refs | ForEach-Object { $_.milestone } | Select-Object -Unique)
    $warnings = New-Object System.Collections.Generic.List[string]
    $taskCount = $null
    $scopeValid = $true

    if (-not [string]::IsNullOrWhiteSpace($Range) -and $refs.Count -eq 0) {
        $scopeValid = $false
        $warnings.Add("TaskRange must use MXX-TYY references, for example M25-T01 to M25-T08.") | Out-Null
    }

    if ($milestones.Count -gt $MaxMilestonesPerWorkCycle) {
        $scopeValid = $false
        $warnings.Add("TaskRange crosses $($milestones.Count) milestones; max is $MaxMilestonesPerWorkCycle milestone per work cycle.") | Out-Null
    }

    if ($milestones.Count -eq 1 -and $refs.Count -gt 0) {
        $firstTask = [int] $refs[0].task
        $lastTask = [int] $refs[$refs.Count - 1].task
        $taskCount = [math]::Abs($lastTask - $firstTask) + 1
        if ($taskCount -gt $MaxTasksPerWorkCycle) {
            $scopeValid = $false
            $warnings.Add("TaskRange contains $taskCount tasks; max is $MaxTasksPerWorkCycle tasks per work cycle.") | Out-Null
        }
    }

    return [ordered]@{
        parsedTasks = @($refs)
        milestoneCount = $milestones.Count
        taskCount = $taskCount
        maxTasksPerCycle = $MaxTasksPerWorkCycle
        maxMilestonesPerCycle = $MaxMilestonesPerWorkCycle
        scopeValid = $scopeValid
        scopeWarnings = @($warnings)
    }
}

function Assert-WorkScope {
    param($Scope)

    if ($DocMode -eq "archive-only") {
        return
    }

    if (-not $Scope.scopeValid) {
        throw "Work cycle scope is too broad or ambiguous: $($Scope.scopeWarnings -join '; ')"
    }
}

function New-WorkCycleContext {
    $milestone = Get-Milestone -Range $TaskRange -Name $Goal
    $workScope = Get-WorkScope -Range $TaskRange
    Assert-WorkScope -Scope $workScope
    $reportPath = if ($milestone) { "docs/90-reports/$($milestone.ToLowerInvariant())-execution-report.md" } else { "" }
    $context = [ordered]@{
        goal = $Goal
        taskRange = $TaskRange
        milestone = $milestone
        workScope = $workScope
        docMode = $DocMode
        startedAt = (Get-Date -Format o)
        baselineChangedPaths = @(Get-NormalizedGitStatusPaths)
        requiredDocs = @(
            "docs/02-roadmap/current-roadmap.md",
            $reportPath
        ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        allowedActiveDocs = @(
            "docs/README.md",
            "docs/00-product/current-product-scope.md",
            "docs/01-architecture/current-architecture.md",
            "docs/01-architecture/technology-selection.md",
            "docs/01-architecture/platform-object-model.md",
            "docs/02-roadmap/current-roadmap.md",
            "docs/03-engineering/ai-engineering-governance.md"
        )
        allowedReportDir = "docs/90-reports"
        forbiddenByDefault = @(
            "creating docs in docs root except README.md",
            "creating new roadmap files outside docs/02-roadmap/current-roadmap.md",
            "editing docs/99-archive unless DocMode is archive-only",
            "crossing multiple milestones in one code-doc-report work cycle",
            "handling more than 8 tasks in one code-doc-report work cycle",
            "resetting database unless explicitly requested"
        )
        requiresRoadmapUpdate = $DocMode -eq "code-doc-report"
        requiresExecutionReport = $DocMode -eq "code-doc-report"
        requiresBrowserSmokeForFrontend = $true
    }
    $context | ConvertTo-Json -Depth 5 | Set-Content -Path $ContextPath -Encoding UTF8
    return $context
}

function Ensure-ExecutionReport {
    param($Context)

    if ($Context.docMode -ne "code-doc-report" -or [string]::IsNullOrWhiteSpace($Context.milestone)) {
        return
    }

    $reportRelativePath = "docs/90-reports/$($Context.milestone.ToLowerInvariant())-execution-report.md"
    $reportPath = Join-Path $Root $reportRelativePath
    if (Test-Path $reportPath) {
        return
    }

    $title = "$($Context.milestone) Execution Report"
    $today = Get-Date -Format "yyyy-MM-dd"
    $content = @(
        "---",
        "title: $title",
        "status: archived",
        "milestone: $($Context.milestone)",
        "updated_at: $today",
        "---",
        "",
        "# $title",
        "",
        "## Scope",
        "",
        "- $($Context.taskRange)",
        "",
        "## Completed Items",
        "",
        "| Task | Status | Evidence |",
        "| --- | --- | --- |",
        "",
        "## Code Changes",
        "",
        "- Backend:",
        "- Frontend:",
        "- Database:",
        "- Scripts:",
        "",
        "## Documentation Changes",
        "",
        "| Document | Action | Reason |",
        "| --- | --- | --- |",
        "",
        "## Validation",
        "",
        "- Backend tests:",
        "- Frontend build:",
        "- `pnpm verify`:",
        "- Browser smoke:",
        "",
        "## Remaining Gaps",
        "",
        "- ",
        "",
        "## Next Steps",
        "",
        "- "
    )
    New-Item -ItemType Directory -Force -Path (Split-Path $reportPath -Parent) | Out-Null
    Set-Content -Path $reportPath -Value ($content -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Execution report template created: $reportRelativePath"
}

function Write-DocumentContract {
    param($Context)

    Write-Host ""
    Write-Host "Document Contract"
    Write-Host "- Mode: $($Context.docMode)"
    if ($Context.taskRange) {
        Write-Host "- Task range: $($Context.taskRange)"
    }
    Write-Host "- Scope policy: max $($Context.workScope.maxMilestonesPerCycle) milestone and max $($Context.workScope.maxTasksPerCycle) tasks per code-doc-report cycle"
    if ($Context.workScope.taskCount) {
        Write-Host "- Parsed task count: $($Context.workScope.taskCount)"
    }
    if ($Context.milestone) {
        Write-Host "- Milestone: $($Context.milestone)"
    }
    if ($Context.docMode -eq "code-doc-report") {
        Write-Host "- MUST update: docs/02-roadmap/current-roadmap.md"
        if ($Context.milestone) {
            Write-Host "- MUST create/update: docs/90-reports/$($Context.milestone.ToLowerInvariant())-execution-report.md"
        } else {
            Write-Host "- MUST create/update: docs/90-reports/{milestone}-execution-report.md"
        }
        Write-Host "- MAY update active docs only from allowlist when product, architecture, platform object, technology, or governance facts change"
        Write-Host "- MUST document validation results in the execution report"
    } else {
        Write-Host "- Archive-only mode: modify docs structure and archived/superseded documents only"
    }
    Write-Host "- MUST NOT create new roadmap files"
    Write-Host "- MUST NOT create Markdown files in docs root except README.md"
    Write-Host "- MUST NOT edit docs/99-archive unless DocMode is archive-only"
    Write-Host "- Context: $ContextPath"
    Write-Host ""
}

function Invoke-QualityGate {
    param([string] $Mode)

    & (Join-Path $PSScriptRoot "ai-quality-gate.ps1") -Mode $Mode
    if ($LASTEXITCODE -ne 0) {
        throw "Quality gate failed with exit code $LASTEXITCODE"
    }
}

switch ($Stage) {
    "start" {
        $context = New-WorkCycleContext
        Ensure-ExecutionReport -Context $context
        Write-DocumentContract -Context $context
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "start-$Goal"
        Write-Host "Start checkpoint created. Next: implement a small vertical slice, then run checkpoint."
    }
    "checkpoint" {
        Invoke-QualityGate -Mode $GateMode
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "checkpoint-$Goal"
    }
    "finish" {
        Invoke-QualityGate -Mode "full"
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "finish-$Goal"
        Write-Host "Finish checkpoint completed. Summarize changed files, documentation updates, validation, residual risks, and next tasks."
    }
}
