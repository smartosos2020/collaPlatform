param(
    [ValidateSet("start", "checkpoint", "finish")]
    [string] $Stage,

    [string] $Goal = "",

    [string] $TaskRange = "",

    [ValidateSet("code-doc-report", "archive-only")]
    [string] $DocMode = "code-doc-report",

    # Deprecated: validation profile is derived from the stage (checkpoint=light,
    # finish=stage) or from -ValidationProfile. Kept only for CLI compatibility.
    [ValidateSet("quick", "full")]
    [string] $GateMode = "quick",

    [ValidateSet("", "light", "stage", "route-final")]
    [string] $ValidationProfile = "",

    [string] $BackendTestPattern = "",

    [string] $BrowserTestCommand = "",

    [ValidateSet("", "real", "mock")]
    [string] $BrowserEvidenceKind = "",

    [ValidateSet("", "isolated", "shared-readonly", "mock")]
    [string] $BrowserEvidenceEnvironment = "",

    [string] $BrowserNotRequiredReason = "",

    [switch] $Force
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ReportDir = Join-Path $Root ".local-reports"
$ContextPath = Join-Path $ReportDir "work-cycle-current.json"
$MaxMilestonesPerWorkCycle = 1
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Get-Milestone {
    param([string] $Range, [string] $Name)

    $source = if (-not [string]::IsNullOrWhiteSpace($Range)) { $Range } else { $Name }
    $source = $source.ToUpperInvariant()
    if ($source -match "(?<![A-Z0-9])((?:[A-Z][A-Z0-9]*-)*M\d{1,3})(?=-T\d{2}|\b)") {
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
function Get-WorkCycleFileSignature {
    param([string] $Path)

    $fullPath = Join-Path $Root $Path
    if (-not (Test-Path -LiteralPath $fullPath)) {
        return "<missing>"
    }

    $item = Get-Item -LiteralPath $fullPath
    if ($item.PSIsContainer) {
        return "<directory>"
    }

    if ($item.Length -le 2MB -and $item.Extension -notin @(".png", ".jpg", ".jpeg", ".gif", ".ico", ".jar", ".class", ".zip", ".gz")) {
        return (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash
    }
    return "$($item.Length):$($item.LastWriteTimeUtc.Ticks)"
}

function Get-WorkCycleFileSignatures {
    $signatures = [ordered]@{}
    foreach ($path in @(Get-NormalizedGitStatusPaths)) {
        $signatures[$path] = Get-WorkCycleFileSignature -Path $path
    }
    return $signatures
}

function Get-ChangedWorkCyclePaths {
    param($Context)

    $currentPaths = @(Get-NormalizedGitStatusPaths)
    if ($null -eq $Context -or -not ($Context.PSObject.Properties.Name -contains "baselineFileSignatures")) {
        return $currentPaths
    }

    $baseline = $Context.baselineFileSignatures
    $baselinePaths = @($baseline.PSObject.Properties.Name)
    $candidatePaths = @($currentPaths + $baselinePaths | Sort-Object -Unique)
    return @($candidatePaths | Where-Object {
        $currentSignature = Get-WorkCycleFileSignature -Path $_
        $baselineSignature = if ($baseline.PSObject.Properties.Name -contains $_) { [string] $baseline.$_ } else { "<not-present-at-start>" }
        $currentSignature -ne $baselineSignature
    })
}

function Get-WorkCycleAffectedAreas {
    param($Context)

    $paths = @(Get-ChangedWorkCyclePaths -Context $Context)
    $areas = New-Object System.Collections.Generic.List[string]
    foreach ($path in $paths) {
        if ($path -match "^(server/|pom\.xml$|server\\)" -and -not $areas.Contains("backend")) {
            $areas.Add("backend") | Out-Null
        }
        if ($path -match "^(web/|package\.json$|pnpm-lock\.yaml$|web\\)" -and -not $areas.Contains("frontend")) {
            $areas.Add("frontend") | Out-Null
        }
        if ($path -match "^(collaboration/|collaboration\\)" -and -not $areas.Contains("collaboration")) {
            $areas.Add("collaboration") | Out-Null
        }
        if ($path -match "^(docs/|scripts/|deploy/|deploy\\)" -and -not $areas.Contains("workbench")) {
            $areas.Add("workbench") | Out-Null
        }
    }
    if ($areas.Count -eq 0) {
        $areas.Add("none") | Out-Null
    }
    return $areas.ToArray()
}

function Get-TaskReferences {
    param([string] $Range)

    if ([string]::IsNullOrWhiteSpace($Range)) {
        return @()
    }

    $items = New-Object System.Collections.Generic.List[object]
    $matches = [regex]::Matches(
        $Range.ToUpperInvariant(),
        "(?<![A-Z0-9])((?:[A-Z][A-Z0-9]*-)*M\d{1,3})-T(\d{2})(?!\d)"
    )
    foreach ($match in $matches) {
        $items.Add([ordered]@{
            raw = $match.Value
            milestone = $match.Groups[1].Value
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
    $expectedTasks = @()
    $scopeValid = $true

    if (-not [string]::IsNullOrWhiteSpace($Range) -and $refs.Count -eq 0) {
        $scopeValid = $false
        $warnings.Add("TaskRange must use MXX-TYY or PREFIX-MXX-TYY references, for example M25-T01 to M25-T12 or KB-NAME-M1-T01 to KB-NAME-M1-T12.") | Out-Null
    }

    if ($milestones.Count -gt $MaxMilestonesPerWorkCycle) {
        $scopeValid = $false
        $warnings.Add("TaskRange crosses $($milestones.Count) milestones; max is $MaxMilestonesPerWorkCycle milestone per work cycle.") | Out-Null
    }

    if ($milestones.Count -eq 1 -and $refs.Count -gt 0) {
        $firstTask = [int] $refs[0].task
        $lastTask = [int] $refs[$refs.Count - 1].task
        $taskCount = [math]::Abs($lastTask - $firstTask) + 1
        $startTask = [math]::Min($firstTask, $lastTask)
        $endTask = [math]::Max($firstTask, $lastTask)
        $expectedTasks = @($startTask..$endTask | ForEach-Object { "$($milestones[0])-T$($_.ToString('D2'))" })
    }

    return [ordered]@{
        parsedTasks = @($refs)
        milestoneCount = $milestones.Count
        taskCount = $taskCount
        expectedTasks = @($expectedTasks)
        maxTasksPerCycle = "unbounded-within-one-milestone"
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
        status = "in-progress"
        taskRange = $TaskRange
        milestone = $milestone
        workScope = $workScope
        docMode = $DocMode
        startedAt = (Get-Date -Format o)
            baselineChangedPaths = @(Get-NormalizedGitStatusPaths)
            baselineFileSignatures = Get-WorkCycleFileSignatures
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
            "resetting a shared database as part of ordinary verification",
            "running archived fixture or migration scripts without auditing their schema, routes, and destructive scope"
        )
        dataBaseline = "No shared reset baseline; integration tests use isolated Testcontainers data"
        dataResetCommand = "none"
        dataResetPolicy = "A route-specific reset procedure requires explicit user approval and a current backup"
        flowRegressionCommand = "scope-specific browser smoke"
        flowRegressionPolicy = "run only for the changed user flow"
        browserSmokePolicy = "Run scope-specific browser smoke when frontend or user-flow changes require it; archived M31/M40 flows are not valid baselines."
        validationPolicy = [ordered]@{
            checkpointDefault = "light"
            finishDefault = "stage"
            routeFinalProfile = "route-final"
            light = "validate only changed backend/frontend areas with compile or lint; skip Docker startup and repository-wide audits"
            stage = "finish requires -BackendTestPattern and runs targeted backend integration tests plus changed frontend build; skip backend package and repository-wide audits"
            routeFinal = "run full mvn test, backend package, frontend lint/build, migration checks, security gates, and strict document contract"
            compactOutput = "successful commands print summary and write full logs under .local-reports"
        }
        requiresRoadmapUpdate = $DocMode -eq "code-doc-report"
        requiresExecutionReport = $DocMode -eq "code-doc-report"
        requiresBrowserSmokeForFrontend = $true
        evidencePolicy = [ordered]@{
            contractVersion = 2
            acceptanceMatrix = "one row per expected task with acceptance criterion, implementation evidence, automated evidence, browser evidence or an explicit N/A reason, and status"
            verificationContract = "one row per expected task declaring verification level, browser evidence kind, environment, mock allowance, and required real flow"
            finishStatus = "every expected task must be Done only after the verification contract and acceptance evidence both pass"
            browser = "finish must execute -BrowserTestCommand with explicit real/mock kind and environment, or record -BrowserNotRequiredReason"
            coreClosure = "authentication, permission, resource mutations, security, handover, export, and audit closures require isolated real E2E evidence"
            gaps = "a gap related to an expected task blocks Done and must reopen that task"
        }
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
    $verificationRows = @($Context.workScope.expectedTasks | ForEach-Object { "| $_ | TODO | TODO | TODO | TODO | TODO |" })
    $acceptanceRows = @($Context.workScope.expectedTasks | ForEach-Object { "| $_ | TODO | TODO | TODO | TODO | Pending |" })
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
        "## Verification Contract",
        "",
        "| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |",
        "| --- | --- | --- | --- | --- | --- |"
    ) + $verificationRows + @(
        "",
        "## Completed Items",
        "",
        "| Task | Status | Evidence |",
        "| --- | --- | --- |",
        "",
        "## Acceptance Evidence",
        "",
        "| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |",
        "| --- | --- | --- | --- | --- | --- |"
    ) + $acceptanceRows + @(
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
        "- Local quality gate:",
        "- Browser smoke:",
        "",
        "## Remaining Gaps",
        "",
        "| Related task | Gap | Acceptance effect | Tracking |",
        "| --- | --- | --- | --- |",
        "| N/A | None | non-blocking | N/A |",
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
    Write-Host "- Scope policy: max $($Context.workScope.maxMilestonesPerCycle) milestone; all roadmap tasks for that milestone are allowed"
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
        Write-Host "- MUST provide one Acceptance Evidence row for every task in the current range"
        Write-Host "- MUST provide one Verification Contract row for every task: level, real/mock browser evidence, environment, mock allowance, and required real flow"
        Write-Host "- Data baseline: $($Context.dataBaseline)"
        Write-Host "- Data reset command when explicitly requested: $($Context.dataResetCommand)"
        Write-Host "- Data reset policy: $($Context.dataResetPolicy)"
        Write-Host "- Browser smoke policy: $($Context.browserSmokePolicy)"
        Write-Host "- Validation policy: checkpoint=light, finish=stage, route-final requires -ValidationProfile route-final"
        Write-Host "- Stage backend tests: finish requires -BackendTestPattern and runs scoped integration tests"
        Write-Host "- Browser evidence: finish requires -BrowserTestCommand or -BrowserNotRequiredReason"
        Write-Host "- Real closure evidence: authentication, permissions, resource mutations, security, handover, export, and audit flows require isolated real E2E; mock browser tests cannot close them"
        Write-Host "- Gap rule: a Remaining Gaps row tied to an expected task blocks Done and must reopen that task"
        Write-Host "- Output policy: successful gates use compact summaries and write full logs to .local-reports"
        Write-Host "- Browser flow verification: $($Context.flowRegressionCommand)"
    } else {
        Write-Host "- Archive-only mode: modify local documentation, governance scripts, and explicitly superseded historical material only"
    }
    Write-Host "- MUST NOT create new roadmap files"
    Write-Host "- MUST NOT create Markdown files in docs root except README.md"
    Write-Host "- MUST NOT edit docs/99-archive unless DocMode is archive-only"
    Write-Host "- Context: $ContextPath"
    Write-Host ""
}

function Invoke-QualityGate {
    param(
        [string] $Mode,
        [string] $BackendStrategy = "full",
        [string] $BackendTestPattern = "",
        [string] $FrontendStrategy = "full",
        [string] $CollaborationStrategy = "skip",
        [bool] $SkipDocker = $false,
        [bool] $SkipAudit = $false
    )

    $arguments = @{
        Mode = $Mode
        FrontendStrategy = $FrontendStrategy
        CompactOutput = $true
    }
    if ($BackendStrategy -eq "skip") {
        $arguments.SkipBackend = $true
    } else {
        $arguments.BackendStrategy = $BackendStrategy
    }
    if (-not [string]::IsNullOrWhiteSpace($BackendTestPattern)) {
        $arguments.BackendTestPattern = $BackendTestPattern
    }
    if ($CollaborationStrategy -ne "skip") {
        $arguments.CollaborationStrategy = $CollaborationStrategy
    }
    if ($SkipDocker) {
        $arguments.SkipDocker = $true
    }
    if ($SkipAudit) {
        $arguments.SkipAudit = $true
    }

    $qualityGatePath = Join-Path $PSScriptRoot "ai-quality-gate.ps1"
    & $qualityGatePath @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Quality gate failed with exit code $LASTEXITCODE"
    }
}

function Get-ValidationPlan {
    param(
        [string] $StageName,
        [string] $RequestedProfile,
        [string] $RequestedBackendTestPattern
    )

    $context = if (Test-Path -LiteralPath $ContextPath) {
        Get-Content -LiteralPath $ContextPath -Raw | ConvertFrom-Json
    } else {
        $null
    }
    $affectedAreas = @(Get-WorkCycleAffectedAreas -Context $context)
    $hasBackendChanges = $affectedAreas -contains "backend"
    $hasFrontendChanges = $affectedAreas -contains "frontend"
    $hasCollaborationChanges = $affectedAreas -contains "collaboration"

    $profile = $RequestedProfile
    if ([string]::IsNullOrWhiteSpace($profile)) {
        $profile = if ($StageName -eq "checkpoint") { "light" } else { "stage" }
    }

    if ($profile -eq "light") {
        return [ordered]@{
            profile = $profile
            mode = "quick"
            backendStrategy = if ($hasBackendChanges) { "compile" } else { "skip" }
            backendTestPattern = ""
            frontendStrategy = if ($hasFrontendChanges) { "lint" } else { "skip" }
            collaborationStrategy = if ($hasCollaborationChanges) { "test" } else { "skip" }
            skipDocker = $true
            skipAudit = $true
            snapshotProfile = "light"
            affectedAreas = $affectedAreas
        }
    }

    if ($profile -eq "stage") {
        if ($StageName -eq "finish" -and $DocMode -eq "code-doc-report" -and [string]::IsNullOrWhiteSpace($RequestedBackendTestPattern)) {
            throw "Stage finish requires -BackendTestPattern. Use targeted milestone tests or choose route-final for the complete backend suite."
        }
        $strategy = if (-not [string]::IsNullOrWhiteSpace($RequestedBackendTestPattern)) {
            "targeted"
        } elseif ($hasBackendChanges) {
            "compile"
        } else {
            "skip"
        }
        return [ordered]@{
            profile = $profile
            mode = if ($StageName -eq "finish") { "stage" } else { "quick" }
            backendStrategy = $strategy
            backendTestPattern = $RequestedBackendTestPattern
            frontendStrategy = if ($hasFrontendChanges) {
                if ($StageName -eq "finish") { "full" } else { "lint" }
            } else {
                "skip"
            }
            collaborationStrategy = if ($hasCollaborationChanges) { "test" } else { "skip" }
            skipDocker = $true
            skipAudit = $true
            snapshotProfile = "light"
            affectedAreas = $affectedAreas
        }
    }

    return [ordered]@{
        profile = $profile
        mode = "full"
        backendStrategy = "full"
        backendTestPattern = ""
        frontendStrategy = "full"
        collaborationStrategy = "test"
        skipDocker = $false
        skipAudit = $false
        snapshotProfile = "full"
        affectedAreas = @("backend", "frontend", "collaboration", "workbench")
    }
}

function Set-BrowserEvidence {
    param([object] $Evidence)

    $context = Get-Content -LiteralPath $ContextPath -Raw | ConvertFrom-Json
    $context | Add-Member -NotePropertyName browserEvidence -NotePropertyValue $Evidence -Force
    $context | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $ContextPath -Encoding UTF8
}

function Assert-RealBrowserEvidenceSources {
    param([string] $Command)

    # Runs in-process: a violation throws inside the assertion script and, with
    # ErrorActionPreference=Stop, terminates this caller with the detailed message.
    $assertScript = Join-Path $PSScriptRoot "assert-real-browser-evidence.ps1"
    & $assertScript -Command $Command -Root $Root
}

function Invoke-BrowserEvidence {
    if ($DocMode -ne "code-doc-report") {
        return
    }
    $hasCommand = -not [string]::IsNullOrWhiteSpace($BrowserTestCommand)
    $hasReason = -not [string]::IsNullOrWhiteSpace($BrowserNotRequiredReason)
    if ($hasCommand -eq $hasReason) {
        throw "Finish requires exactly one of -BrowserTestCommand or -BrowserNotRequiredReason."
    }
    if ($hasReason) {
        if (-not [string]::IsNullOrWhiteSpace($BrowserEvidenceKind) -or -not [string]::IsNullOrWhiteSpace($BrowserEvidenceEnvironment)) {
            throw "Browser evidence kind/environment are only valid with -BrowserTestCommand."
        }
        if ($BrowserNotRequiredReason.Trim().Length -lt 20) {
            throw "-BrowserNotRequiredReason must be specific and at least 20 characters long."
        }
        Set-BrowserEvidence -Evidence ([ordered]@{
            status = "not_required"
            kind = "not-required"
            environment = "not-required"
            reason = $BrowserNotRequiredReason.Trim()
            completedAt = (Get-Date -Format o)
        })
        Write-Host "Browser verification not required: $($BrowserNotRequiredReason.Trim())"
        return
    }

    if ([string]::IsNullOrWhiteSpace($BrowserEvidenceKind) -or [string]::IsNullOrWhiteSpace($BrowserEvidenceEnvironment)) {
        throw "-BrowserTestCommand requires both -BrowserEvidenceKind (real/mock) and -BrowserEvidenceEnvironment (isolated/shared-readonly/mock)."
    }
    if ($BrowserEvidenceKind -eq "real" -and $BrowserEvidenceEnvironment -notin @("isolated", "shared-readonly")) {
        throw "Real browser evidence must use an isolated or shared-readonly environment."
    }
    if ($BrowserEvidenceKind -eq "mock" -and $BrowserEvidenceEnvironment -ne "mock") {
        throw "Mock browser evidence must declare -BrowserEvidenceEnvironment mock."
    }
    if ($BrowserEvidenceKind -eq "real") {
        Assert-RealBrowserEvidenceSources -Command $BrowserTestCommand
    }

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $logPath = Join-Path $ReportDir "work-cycle-browser-$timestamp.log"
    Push-Location $Root
    try {
        Write-Host "Running browser verification: $BrowserTestCommand"
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $output = @(Invoke-Expression $BrowserTestCommand 2>&1)
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        $exitCode = $LASTEXITCODE
        $output | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath $logPath -Encoding UTF8
        if ($exitCode -ne 0) {
            $output | Select-Object -Last 80 | ForEach-Object { Write-Host $_ }
            throw "Browser verification failed with exit code $exitCode; log: $logPath"
        }
    } finally {
        Pop-Location
    }
    Set-BrowserEvidence -Evidence ([ordered]@{
        status = "passed"
        kind = $BrowserEvidenceKind
        environment = $BrowserEvidenceEnvironment
        command = $BrowserTestCommand
        logPath = $logPath.ToString()
        completedAt = (Get-Date -Format o)
    })
    Write-Host "Browser verification passed. Log: $logPath"
}

switch ($Stage) {
    "start" {
        if ((Test-Path -LiteralPath $ContextPath) -and -not $Force) {
            $existingContext = Get-Content -LiteralPath $ContextPath -Raw | ConvertFrom-Json
            $existingStatus = if ($existingContext.PSObject.Properties.Name -contains "status") { [string] $existingContext.status } else { "in-progress" }
            if ($existingStatus -ne "finished") {
                throw "An unfinished work cycle already exists for goal '$($existingContext.goal)' started at $($existingContext.startedAt). Finish it first, or pass -Force to replace it."
            }
        }
        $context = New-WorkCycleContext
        Ensure-ExecutionReport -Context $context
        Write-DocumentContract -Context $context
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "start-$Goal" -Profile full
        Write-Host "Start checkpoint created. Next: implement a small vertical slice, then run checkpoint."
    }
    "checkpoint" {
        $plan = Get-ValidationPlan -StageName "checkpoint" -RequestedProfile $ValidationProfile -RequestedBackendTestPattern $BackendTestPattern
        Write-Host "Validation profile: $($plan.profile); affected=$($plan.affectedAreas -join ','); backend=$($plan.backendStrategy); frontend=$($plan.frontendStrategy); collaboration=$($plan.collaborationStrategy); mode=$($plan.mode); audit=$(-not $plan.skipAudit)"
        Invoke-QualityGate -Mode $plan.mode -BackendStrategy $plan.backendStrategy -BackendTestPattern $plan.backendTestPattern -FrontendStrategy $plan.frontendStrategy -CollaborationStrategy $plan.collaborationStrategy -SkipDocker:$plan.skipDocker -SkipAudit:$plan.skipAudit
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "checkpoint-$Goal" -Profile $plan.snapshotProfile
    }
    "finish" {
        if (-not (Test-Path -LiteralPath $ContextPath)) {
            throw "No active work-cycle context found. Run scripts/ai-work-cycle.ps1 -Stage start before finish."
        }
        $plan = Get-ValidationPlan -StageName "finish" -RequestedProfile $ValidationProfile -RequestedBackendTestPattern $BackendTestPattern
        Write-Host "Validation profile: $($plan.profile); affected=$($plan.affectedAreas -join ','); backend=$($plan.backendStrategy); frontend=$($plan.frontendStrategy); collaboration=$($plan.collaborationStrategy); mode=$($plan.mode); audit=$(-not $plan.skipAudit)"
        Invoke-BrowserEvidence
        Invoke-QualityGate -Mode $plan.mode -BackendStrategy $plan.backendStrategy -BackendTestPattern $plan.backendTestPattern -FrontendStrategy $plan.frontendStrategy -CollaborationStrategy $plan.collaborationStrategy -SkipDocker:$plan.skipDocker -SkipAudit:$plan.skipAudit
        $finishedContext = Get-Content -LiteralPath $ContextPath -Raw | ConvertFrom-Json
        $finishedContext | Add-Member -NotePropertyName status -NotePropertyValue "finished" -Force
        $finishedContext | Add-Member -NotePropertyName finishedAt -NotePropertyValue (Get-Date -Format o) -Force
        $finishedContext | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $ContextPath -Encoding UTF8
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "finish-$Goal" -Profile $plan.snapshotProfile
        Write-Host "Finish checkpoint completed. Summarize changed files, documentation updates, validation, residual risks, and next tasks."
    }
}
