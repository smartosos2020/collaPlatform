param(
    [ValidateSet("quick", "stage", "full")]
    [string] $Mode = "quick",

    [ValidateSet("", "compile", "targeted", "full", "skip")]
    [string] $BackendStrategy = "",

    [string] $BackendTestPattern = "",

    [ValidateSet("", "lint", "full", "skip")]
    [string] $FrontendStrategy = "",

    [ValidateSet("", "test", "skip")]
    [string] $CollaborationStrategy = "",

    [switch] $CompactOutput,

    [switch] $SkipDocker,
    [switch] $SkipFrontend,
    [switch] $SkipBackend,
    [switch] $SkipAudit
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "quality-gate-$Timestamp.md"
$Failures = New-Object System.Collections.Generic.List[string]
$Warnings = New-Object System.Collections.Generic.List[string]
$Results = New-Object System.Collections.Generic.List[string]
$StepLogs = New-Object System.Collections.Generic.List[string]

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
}

function Add-Warning {
    param([string] $Message)
    $Warnings.Add($Message) | Out-Null
    Write-Warning $Message
}

function Add-Failure {
    param([string] $Message)
    $Failures.Add($Message) | Out-Null
    Write-Error $Message -ErrorAction Continue
}

function Invoke-Step {
    param(
        [string] $Name,
        [scriptblock] $Action
    )

    Write-Host ""
    Write-Host "==> $Name"
    try {
        & $Action
        Add-Result "- PASS: $Name"
    } catch {
        Add-Failure "- FAIL: $Name - $($_.Exception.Message)"
    }
}

function Test-CommandExists {
    param([string] $CommandName)
    $null -ne (Get-Command $CommandName -ErrorAction SilentlyContinue)
}

function Invoke-LoggedCommand {
    param(
        [string] $Command,
        [string] $WorkingDirectory,
        [switch] $Compact
    )

    Push-Location $WorkingDirectory
    try {
        Write-Host "Running: $Command"

        if (-not $Compact) {
            $previousErrorActionPreference = $ErrorActionPreference
            $previousNativeErrorActionPreference = $null
            $hasNativeErrorActionPreference = Test-Path variable:PSNativeCommandUseErrorActionPreference
            if ($hasNativeErrorActionPreference) {
                $previousNativeErrorActionPreference = $PSNativeCommandUseErrorActionPreference
                $PSNativeCommandUseErrorActionPreference = $false
            }
            $ErrorActionPreference = "Continue"
            try {
                Invoke-Expression $Command
            } finally {
                $ErrorActionPreference = $previousErrorActionPreference
                if ($hasNativeErrorActionPreference) {
                    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorActionPreference
                }
            }
            if ($LASTEXITCODE -ne 0) {
                throw "Command exited with code $LASTEXITCODE"
            }
            return
        }

        $safeName = ($Command -replace "[^A-Za-z0-9_.-]+", "-").Trim("-")
        if ($safeName.Length -gt 80) {
            $safeName = $safeName.Substring(0, 80)
        }
        $logPath = Join-Path $ReportDir "quality-gate-$Timestamp-$safeName.log"
        $previousErrorActionPreference = $ErrorActionPreference
        $previousNativeErrorActionPreference = $null
        $hasNativeErrorActionPreference = Test-Path variable:PSNativeCommandUseErrorActionPreference
        if ($hasNativeErrorActionPreference) {
            $previousNativeErrorActionPreference = $PSNativeCommandUseErrorActionPreference
            $PSNativeCommandUseErrorActionPreference = $false
        }
        $ErrorActionPreference = "Continue"
        try {
            $output = @(Invoke-Expression $Command 2>&1)
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
            if ($hasNativeErrorActionPreference) {
                $PSNativeCommandUseErrorActionPreference = $previousNativeErrorActionPreference
            }
        }
        $exitCode = $LASTEXITCODE
        $lines = @($output | ForEach-Object { $_.ToString() })
        Set-Content -Path $logPath -Value $lines -Encoding UTF8
        $StepLogs.Add($logPath) | Out-Null

        Write-Host "Log: $logPath"
        if ($exitCode -ne 0) {
            $lines | Select-Object -Last 120 | ForEach-Object { Write-Host $_ }
            throw "Command exited with code $exitCode; full log: $logPath"
        }

        $summary = @($lines | Where-Object {
            $_ -match "Tests run:" -or
            $_ -match "BUILD SUCCESS" -or
            $_ -match "BUILD FAILURE" -or
            $_ -match "Successfully validated \d+ migrations" -or
            $_ -match "Successfully applied \d+ migrations" -or
            $_ -match "PASS:" -or
            $_ -match "FAIL:" -or
            $_ -match "Quality gate report:" -or
            $_ -match "Security audit gate report:" -or
            $_ -match "✓ built in" -or
            $_ -match "warning" -or
            $_ -match "error TS" -or
            $_ -match "^\s*✖ \d+ problems"
        })
        if ($summary.Count -eq 0) {
            Write-Host "Command succeeded; full log: $logPath"
        } else {
            $summary | Select-Object -Last 20 | ForEach-Object { Write-Host $_ }
        }
    } finally {
        Pop-Location
    }
}

function Get-NormalizedGitStatusPaths {
    if (-not (Test-CommandExists "git") -or -not (Test-Path (Join-Path $Root ".git"))) {
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

function Get-GateFileSignature {
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

function Test-DocumentChanged {
    param(
        [string] $Path,
        [string[]] $ChangedPaths,
        [object] $Context = $null
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $true
    }
    $normalized = $Path.Replace("\", "/")

    # Preferred: compare against the baseline signatures captured at Stage start, so a
    # document that was already dirty before the cycle does not count as updated by it.
    if ($null -ne $Context -and ($Context.PSObject.Properties.Name -contains "baselineFileSignatures")) {
        $baseline = $Context.baselineFileSignatures
        $baselineSignature = if ($baseline.PSObject.Properties.Name -contains $normalized) { [string] $baseline.$normalized } else { "<not-present-at-start>" }
        return (Get-GateFileSignature -Path $normalized) -ne $baselineSignature
    }

    if ($ChangedPaths -contains $normalized) {
        return $true
    }

    if ($null -eq $Context -or -not ($Context.PSObject.Properties.Name -contains "startedAt")) {
        return $false
    }

    $fullPath = Join-Path $Root $Path
    if (-not (Test-Path $fullPath)) {
        return $false
    }

    try {
        $startedAt = [datetime]::Parse([string] $Context.startedAt)
        return (Get-Item -LiteralPath $fullPath).LastWriteTime -gt $startedAt
    } catch {
        return $false
    }
}

function Get-MarkdownTableCells {
    param([string] $Line)

    if ([string]::IsNullOrWhiteSpace($Line) -or -not $Line.Trim().StartsWith("|")) {
        return @()
    }
    $trimmed = $Line.Trim().Trim('|')
    # Split on unescaped pipes only; evidence cells may legitimately contain \| escapes.
    return @($trimmed -split "(?<!\\)\|" | ForEach-Object { $_.Replace("\|", "|").Trim() })
}

function Assert-ConcreteEvidence {
    param([string] $Value, [string] $Label)

    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -match "(?i)^(todo|tbd|pending|n/?a)$" -or $Value -match "待补|待执行|稍后|占位") {
        throw "$Label must contain concrete evidence, not a placeholder: '$Value'"
    }
}

function Get-ReportSection {
    param(
        [string] $Content,
        [string] $StartHeading,
        [string] $EndHeading
    )

    $match = [regex]::Match(
        $Content,
        "(?ms)^$([regex]::Escape($StartHeading))\s*(.+?)\s*^$([regex]::Escape($EndHeading))\s*$"
    )
    if (-not $match.Success) {
        throw "Execution report section cannot be parsed: $StartHeading"
    }
    return $match.Groups[1].Value
}

function Test-CoreClosureCriterion {
    param([string] $Criterion)

    return $Criterion -match "(?i)(登录|认证|权限|创建|新增|修改|删除|停用|启用|密码|安全策略|会话|设备|交接|导出|审计|login|auth|permission|create|update|delete|disable|enable|password|security|session|device|handover|offboard|export|audit)"
}

function Get-ComposeServices {
    # Use plain text format to avoid Windows console encoding issues with --format json.
    $output = docker compose ps --format "table {{.Service}}\t{{.State}}\t{{.Health}}" 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose ps failed with code $LASTEXITCODE"
    }
    if (-not $output) {
        return @()
    }
    return @($output | Select-Object -Skip 1 | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object {
        $parts = $_ -split '\s+', 3
        [pscustomobject]@{
            Service = $parts[0]
            State = $parts[1]
            Health = if ($parts.Count -gt 2) { $parts[2] } else { "" }
        }
    })
}

function Wait-DockerServicesHealthy {
    param(
        [string[]] $Services,
        [int] $TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastState = ""
    while ((Get-Date) -lt $deadline) {
        $ps = @(Get-ComposeServices)
        $pending = New-Object System.Collections.Generic.List[string]

        foreach ($service in $Services) {
            $item = $ps | Where-Object { $_.Service -eq $service }
            if (-not $item) {
                $pending.Add("$service:not-found") | Out-Null
                continue
            }
            if ($item.State -ne "running") {
                $pending.Add("${service}:$($item.State)") | Out-Null
                continue
            }
            if ($item.Health -and $item.Health -ne "healthy") {
                $pending.Add("${service}:$($item.Health)") | Out-Null
            }
        }

        if ($pending.Count -eq 0) {
            return @(Get-ComposeServices)
        }

        $currentState = $pending -join ", "
        if ($currentState -ne $lastState) {
            Write-Host "Waiting for Docker services: $currentState"
            $lastState = $currentState
        }
        Start-Sleep -Seconds 2
    }

    throw "Docker services did not become healthy within $TimeoutSeconds seconds: $lastState"
}

Invoke-Step "Toolchain" {
    foreach ($command in @("java", "mvn", "node", "pnpm", "docker")) {
        if (-not (Test-CommandExists $command)) {
            throw "Missing required command: $command"
        }
    }

    $javaVersion = (cmd /c "java -version 2>&1" | Select-Object -First 1) -join ""
    $mvnVersion = (& mvn -version | Select-Object -First 1) -join ""
    $nodeVersion = (& node --version) -join ""
    $pnpmVersion = (& pnpm --version) -join ""

    Add-Result "  java: $javaVersion"
    Add-Result "  maven: $mvnVersion"
    Add-Result "  node: $nodeVersion"
    Add-Result "  pnpm: $pnpmVersion"
}

if (-not $SkipDocker) {
    Invoke-Step "Docker dependencies" {
        Invoke-LoggedCommand "docker compose up -d postgres redis minio" $Root
        $required = @("postgres", "redis", "minio")
        $ps = @(Wait-DockerServicesHealthy -Services $required)
        foreach ($service in $required) {
            $item = $ps | Where-Object { $_.Service -eq $service }
            if (-not $item) {
                throw "Service not found: $service"
            }
            if ($item.State -ne "running") {
                throw "Service $service is not running"
            }
            if ($item.Health -and $item.Health -ne "healthy") {
                throw "Service $service health is $($item.Health)"
            }
        }
    }
}

if (-not $SkipBackend) {
    $effectiveBackendStrategy = $BackendStrategy
    if ([string]::IsNullOrWhiteSpace($effectiveBackendStrategy)) {
        $effectiveBackendStrategy = if ($Mode -eq "full") { "full" } else { "compile" }
    }

    if ($effectiveBackendStrategy -eq "skip") {
        Invoke-Step "Backend verification" {
            Add-Warning "Backend verification skipped by BackendStrategy=skip. Record the reason in the execution report."
        }
    } elseif ($effectiveBackendStrategy -eq "compile") {
        Invoke-Step "Backend compile" {
            Invoke-LoggedCommand "mvn -DskipTests test" (Join-Path $Root "server") -Compact:$CompactOutput
        }
    } elseif ($effectiveBackendStrategy -eq "targeted") {
        if ([string]::IsNullOrWhiteSpace($BackendTestPattern)) {
            throw "BackendStrategy=targeted requires -BackendTestPattern"
        }
        Invoke-Step "Backend targeted tests" {
            Invoke-LoggedCommand "mvn ""-Dtest=$BackendTestPattern"" test" (Join-Path $Root "server") -Compact:$CompactOutput
        }
    } elseif ($effectiveBackendStrategy -eq "full") {
        Invoke-Step "Backend tests" {
            Invoke-LoggedCommand "mvn test" (Join-Path $Root "server") -Compact:$CompactOutput
        }
    }

    if ($Mode -eq "full") {
        Invoke-Step "Backend package" {
            Invoke-LoggedCommand "mvn -DskipTests package" (Join-Path $Root "server") -Compact:$CompactOutput
        }
    }
}

if (-not $SkipFrontend) {
    $effectiveFrontendStrategy = $FrontendStrategy
    if ([string]::IsNullOrWhiteSpace($effectiveFrontendStrategy)) {
        $effectiveFrontendStrategy = "full"
    }

    if ($effectiveFrontendStrategy -eq "lint" -or $effectiveFrontendStrategy -eq "full") {
        Invoke-Step "Frontend lint" {
            Invoke-LoggedCommand "pnpm web:lint" $Root -Compact:$CompactOutput
        }
    }

    if ($effectiveFrontendStrategy -eq "full") {
        Invoke-Step "Frontend build" {
            Invoke-LoggedCommand "pnpm web:build" $Root -Compact:$CompactOutput
        }

        Invoke-Step "Frontend chunk budget" {
            $assetDir = Join-Path $Root "web\dist\assets"
            if (-not (Test-Path $assetDir)) {
                throw "Frontend assets directory not found: $assetDir"
            }

            $maxBytes = 500KB
            $oversized = Get-ChildItem -Path $assetDir -Filter "*.js" -File | Where-Object { $_.Length -gt $maxBytes }
            if ($oversized) {
                $details = $oversized | ForEach-Object { "$($_.Name)=$([math]::Round($_.Length / 1KB, 2))KB" }
                throw "JavaScript chunks exceed 500KB budget: $($details -join ', ')"
            }
        }

        Invoke-Step "Frontend route lazy-loading" {
            $routerPath = Join-Path $Root "web\src\app\router.tsx"
            $router = Get-Content -LiteralPath $routerPath -Raw
            if ($router -match "import\s+\{[^}]+Page[^}]*\}\s+from\s+['""]\.\./modules/.+/pages/.+Page['""]") {
                throw "Route pages must be loaded with lazyRoute dynamic imports, not static imports"
            }
            if ($router -notmatch "lazyRoute\(\(\)\s*=>\s*import\(") {
                throw "Router must use lazyRoute dynamic imports for page components"
            }
        }
    } elseif ($effectiveFrontendStrategy -notin @("lint", "skip")) {
        throw "Unsupported frontend strategy: $effectiveFrontendStrategy"
    }
}

$effectiveCollaborationStrategy = $CollaborationStrategy
if ([string]::IsNullOrWhiteSpace($effectiveCollaborationStrategy)) {
    $effectiveCollaborationStrategy = if ($Mode -eq "full") { "test" } else { "skip" }
}
if ($effectiveCollaborationStrategy -eq "test") {
    Invoke-Step "Collaboration tests" {
        Invoke-LoggedCommand "pnpm collaboration:test" $Root -Compact:$CompactOutput
    }
}

if (-not $SkipAudit) {
    Invoke-Step "Mockito javaagent configuration" {
        $pomPath = Join-Path $Root "server\pom.xml"
        $pom = Get-Content -LiteralPath $pomPath -Raw
        if ($pom -notmatch "maven-surefire-plugin" -or $pom -notmatch "-javaagent:\$\{settings\.localRepository\}/org/mockito/mockito-core") {
            throw "Server test runtime must configure Mockito as a javaagent in maven-surefire-plugin"
        }
    }

    Invoke-Step "Sensitive data scan" {
        Invoke-LoggedCommand "powershell -NoProfile -ExecutionPolicy Bypass -File scripts/sensitive-data-scan.ps1 -SkipReport" $Root -Compact:$CompactOutput
    }

    Invoke-Step "Security audit guardrails" {
        Invoke-LoggedCommand "powershell -NoProfile -ExecutionPolicy Bypass -File scripts/security-audit-gate.ps1" $Root -Compact:$CompactOutput
    }

    Invoke-Step "Flyway migration order" {
        $migrationDir = Join-Path $Root "server\src\main\resources\db\migration"
        $files = Get-ChildItem $migrationDir -Filter "V*__*.sql" | Sort-Object Name
        $versions = @()
        foreach ($file in $files) {
            if ($file.Name -notmatch "^V(\d{3})__.+\.sql$") {
                throw "Migration file name must match V001__name.sql: $($file.Name)"
            }
            $versions += [int] $Matches[1]
        }

        if (($versions | Select-Object -Unique).Count -ne $versions.Count) {
            throw "Duplicate Flyway migration version detected"
        }

        for ($i = 0; $i -lt $versions.Count; $i++) {
            $expected = $i + 1
            if ($versions[$i] -ne $expected) {
                throw "Expected migration V$("{0:D3}" -f $expected), found V$("{0:D3}" -f $versions[$i])"
            }
        }
    }

    Invoke-Step "Knowledge naming guard" {
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root "scripts/knowledge-naming-guard.ps1")
        if ($LASTEXITCODE -ne 0) {
            throw "Knowledge naming guard failed"
        }
    }

    Invoke-Step "Generated artifact scan" {
        $blocked = @()
        $generatedPaths = @("web\dist", "server\target", "node_modules", ".local-logs", ".local-reports", ".local-backups", "web\test-results", "web\playwright-report")
        foreach ($path in $generatedPaths) {
            $fullPath = Join-Path $Root $path
            if ((Test-Path $fullPath) -and (Test-CommandExists "git") -and (Test-Path (Join-Path $Root ".git"))) {
                Push-Location $Root
                try {
                    git check-ignore -q -- $path
                    if ($LASTEXITCODE -ne 0) {
                        throw "Generated/local path is not ignored: $path"
                    }
                } finally {
                    Pop-Location
                }
            }
        }

        if ((Test-CommandExists "git") -and (Test-Path (Join-Path $Root ".git"))) {
            Push-Location $Root
            try {
                $trackedGenerated = git ls-files "web/dist" "server/target" "node_modules" ".local-logs" ".local-reports" ".local-backups" "web/test-results" "web/playwright-report" 2>$null
                if ($trackedGenerated) {
                    $blocked += $trackedGenerated
                }
            } finally {
                Pop-Location
            }
        }

        if ($blocked.Count -gt 0) {
            throw "Generated artifacts are tracked: $($blocked -join ', ')"
        }
    }

    Invoke-Step "TODO/FIXME inventory" {
        if (Test-CommandExists "rg") {
            $todo = & rg -n "\b(TODO|FIXME|HACK|XXX)\b" $Root `
                -g "!node_modules" -g "!target" -g "!dist" -g "!.local-logs" -g "!.local-reports" -g "!.local-backups" -g "!test-results" -g "!playwright-report" `
                -g "!scripts/ai-quality-gate.ps1" -g "!docs/03-engineering/ai-engineering-governance.md" 2>$null
            if ($todo) {
                Add-Warning "Open implementation markers found. Review before release."
                $todo | Select-Object -First 50 | ForEach-Object { Add-Warning "  $_" }
            }
        } else {
            Add-Warning "rg not found on PATH; TODO/FIXME inventory was skipped."
        }
    }

    Invoke-Step "Documentation structure" {
        $docsRoot = Join-Path $Root "docs"
        if (-not (Test-Path $docsRoot)) {
            throw "docs directory is missing"
        }

        $rootMarkdown = Get-ChildItem -Path $docsRoot -File -Filter "*.md"
        $invalidRootDocs = $rootMarkdown | Where-Object { $_.Name -ne "README.md" }
        if ($invalidRootDocs) {
            throw "Only docs/README.md is allowed in docs root: $($invalidRootDocs.Name -join ', ')"
        }

        $activeDocs = @(
            "docs/README.md",
            "docs/00-product/current-product-scope.md",
            "docs/01-architecture/current-architecture.md",
            "docs/01-architecture/technology-selection.md",
            "docs/01-architecture/platform-object-model.md",
            "docs/02-roadmap/current-roadmap.md",
            "docs/03-engineering/ai-engineering-governance.md"
        )
        foreach ($doc in $activeDocs) {
            $fullPath = Join-Path $Root $doc
            if (-not (Test-Path $fullPath)) {
                throw "Active document is missing: $doc"
            }
            $content = Get-Content -LiteralPath $fullPath -Raw
            if ($content -notmatch "(?s)^---\s+.*status:\s+active.*---") {
                throw "Active document must have front matter with status: active: $doc"
            }
        }

        $illegalRootPattern = Get-ChildItem -Path $docsRoot -File -Filter "*.md" | Where-Object {
            $_.Name -match "^(m\d+|.*roadmap.*|.*report.*)\.md$" -and $_.Name -ne "README.md"
        }
        if ($illegalRootPattern) {
            throw "Milestone, roadmap, and report knowledge_base_items are not allowed in docs root: $($illegalRootPattern.Name -join ', ')"
        }
    }
}

# The work-cycle documentation contract is evidence enforcement, not a static audit:
# it must also run in stage finishes (which skip the security/static audit block).
Invoke-Step "Work-cycle documentation contract" {
        $contextPath = Join-Path $ReportDir "work-cycle-current.json"
        if (-not (Test-Path $contextPath)) {
            Add-Result "  no active work-cycle context; skipped strict document contract"
            return
        }

        $context = Get-Content -LiteralPath $contextPath -Raw | ConvertFrom-Json
        $changedPaths = @(Get-NormalizedGitStatusPaths)
        $strictWorkCycle = $Mode -in @("stage", "full")
        if ($context.docMode -eq "archive-only") {
            Add-Result "  archive-only document mode; code-doc-report contract not required"
            return
        }

        if ($context.PSObject.Properties.Name -contains "workScope") {
            $scope = $context.workScope
            $maxMilestones = if ($null -ne $scope.maxMilestonesPerCycle) { [int] $scope.maxMilestonesPerCycle } else { 1 }

            if (-not [bool] $scope.scopeValid) {
                throw "Active work-cycle scope is invalid: $($scope.scopeWarnings -join '; ')"
            }
            if ($null -ne $scope.milestoneCount -and [int] $scope.milestoneCount -gt $maxMilestones) {
                throw "Active work-cycle crosses $($scope.milestoneCount) milestones; max is $maxMilestones"
            }
        } else {
            $message = "Active work-cycle context has no scope metadata; restart with scripts/ai-work-cycle.ps1 start to enforce task range limits."
            if ($Mode -eq "full") {
                throw $message
            }
            Add-Warning $message
        }

        foreach ($doc in $context.requiredDocs) {
            $fullPath = Join-Path $Root $doc
            if (-not (Test-Path $fullPath)) {
                throw "Required work-cycle document is missing: $doc"
            }
        }

        if ($Mode -ne "full") {
            foreach ($doc in $context.requiredDocs) {
                if (-not (Test-DocumentChanged -Path $doc -ChangedPaths $changedPaths -Context $context)) {
                    Add-Warning "Work-cycle document has not changed yet: $doc"
                }
            }
            return
        }

        $reportRelativePath = @($context.requiredDocs | Where-Object { $_ -like "docs/90-reports/*-execution-report.md" }) | Select-Object -First 1
        if ([string]::IsNullOrWhiteSpace($reportRelativePath)) {
            throw "Work-cycle execution report path is missing from the active context"
        }
        $reportPath = Join-Path $Root $reportRelativePath
        $reportContent = Get-Content -LiteralPath $reportPath -Raw
        $reportLines = @(Get-Content -LiteralPath $reportPath)
        foreach ($heading in @("## Scope", "## Completed Items", "## Acceptance Evidence", "## Validation", "## Remaining Gaps", "## Next Steps")) {
            if ($reportContent -notmatch "(?m)^$([regex]::Escape($heading))\s*$") {
                throw "Execution report is missing required heading: $heading"
            }
        }
        if (-not [string]::IsNullOrWhiteSpace([string] $context.taskRange) -and -not $reportContent.Contains([string] $context.taskRange)) {
            # PowerShell 5 may decode a UTF-8 report without BOM differently from the
            # JSON context. Preserve the scope guard by requiring every parsed task ID
            # in the report when the human-language range string cannot be compared.
            $missingScopeTasks = @($context.workScope.expectedTasks | Where-Object { -not $reportContent.Contains([string] $_) })
            if ($missingScopeTasks.Count -gt 0) {
                throw "Execution report Scope must include the exact active TaskRange or every expected task ID; missing: $($missingScopeTasks -join ', ')"
            }
        }

        $expectedTasks = @()
        if ($context.workScope.PSObject.Properties.Name -contains "expectedTasks") {
            $expectedTasks = @($context.workScope.expectedTasks)
        }
        if ($expectedTasks.Count -eq 0) {
            throw "Active work-cycle context has no expectedTasks; restart Stage start with the current work-cycle script"
        }

        $contractVersion = 0
        if ($context.PSObject.Properties.Name -contains "evidencePolicy" -and $context.evidencePolicy.PSObject.Properties.Name -contains "contractVersion") {
            $contractVersion = [int] $context.evidencePolicy.contractVersion
        }
        $requiresVerificationContract = $contractVersion -ge 2
        $verificationContracts = @{}
        if ($requiresVerificationContract) {
            $contractSection = Get-ReportSection -Content $reportContent -StartHeading "## Verification Contract" -EndHeading "## Completed Items"
            $contractRows = @($contractSection -split "`r?`n" | Where-Object {
                $cells = @(Get-MarkdownTableCells -Line $_)
                $cells.Count -eq 6 -and $cells[0] -ne "Task" -and $cells[0] -notmatch "^-+$"
            })
            foreach ($task in $expectedTasks) {
                $rows = @($contractRows | Where-Object {
                    $cells = @(Get-MarkdownTableCells -Line $_)
                    $cells[0] -eq $task
                })
                if ($rows.Count -ne 1) {
                    throw "Verification Contract must contain exactly one six-column row for $task; found $($rows.Count)"
                }

                $cells = @(Get-MarkdownTableCells -Line $rows[0])
                $level = $cells[1].ToLowerInvariant()
                $browserKind = $cells[2].ToLowerInvariant()
                $environment = $cells[3].ToLowerInvariant()
                $mockAllowed = $cells[4].ToLowerInvariant()
                $realFlow = $cells[5]
                foreach ($field in @(
                    @{ value = $cells[1]; label = "$task verification level" },
                    @{ value = $cells[2]; label = "$task browser evidence kind" },
                    @{ value = $cells[3]; label = "$task verification environment" },
                    @{ value = $cells[4]; label = "$task mock allowance" },
                    @{ value = $realFlow; label = "$task required real flow" }
                )) {
                    Assert-ConcreteEvidence -Value $field.value -Label $field.label
                }

                if ($level -notin @("static", "unit", "integration", "e2e-real", "e2e-real-isolated")) {
                    throw "$task verification level must be static, unit, integration, e2e-real, or e2e-real-isolated"
                }
                if ($browserKind -notin @("real", "mock", "not-required")) {
                    throw "$task browser evidence kind must be real, mock, or not-required"
                }
                if ($environment -notin @("isolated", "shared-readonly", "mock", "not-required")) {
                    throw "$task verification environment must be isolated, shared-readonly, mock, or not-required"
                }
                if ($mockAllowed -notin @("yes", "no")) {
                    throw "$task mock browser allowed must be Yes or No"
                }
                if ($browserKind -eq "real" -and ($environment -notin @("isolated", "shared-readonly") -or $mockAllowed -ne "no")) {
                    throw "$task real browser evidence requires isolated/shared-readonly environment and Mock browser allowed = No"
                }
                if ($browserKind -eq "mock" -and ($environment -ne "mock" -or $mockAllowed -ne "yes")) {
                    throw "$task mock browser evidence requires Environment = mock and Mock browser allowed = Yes"
                }
                if ($browserKind -eq "not-required" -and $environment -ne "not-required") {
                    throw "$task not-required browser evidence requires Environment = not-required"
                }
                if ($level -eq "e2e-real" -and ($browserKind -ne "real" -or $mockAllowed -ne "no" -or $environment -eq "mock")) {
                    throw "$task e2e-real requires real browser evidence and no mock"
                }
                if ($level -eq "e2e-real-isolated" -and ($browserKind -ne "real" -or $environment -ne "isolated" -or $mockAllowed -ne "no")) {
                    throw "$task e2e-real-isolated requires real browser evidence in an isolated environment and no mock"
                }

                $verificationContracts[$task] = [pscustomobject]@{
                    level = $level
                    browserKind = $browserKind
                    environment = $environment
                    mockAllowed = $mockAllowed
                    realFlow = $realFlow
                }
            }
        } else {
            Add-Warning "Legacy work-cycle evidence contract detected; verification contract v2 applies after the next Stage start."
        }

        $roadmapPath = Join-Path $Root "docs/02-roadmap/current-roadmap.md"
        $roadmapLines = @(Get-Content -LiteralPath $roadmapPath)
        $acceptanceSection = Get-ReportSection -Content $reportContent -StartHeading "## Acceptance Evidence" -EndHeading "## Code Changes"
        $acceptanceLines = @($acceptanceSection -split "`r?`n")
        foreach ($task in $expectedTasks) {
            $evidenceRows = @($acceptanceLines | Where-Object {
                $cells = @(Get-MarkdownTableCells -Line $_)
                $cells.Count -eq 6 -and $cells[0] -eq $task
            })
            if ($evidenceRows.Count -ne 1) {
                throw "Acceptance Evidence must contain exactly one six-column row for $task; found $($evidenceRows.Count)"
            }
            $cells = @(Get-MarkdownTableCells -Line $evidenceRows[0])
            Assert-ConcreteEvidence -Value $cells[1] -Label "$task acceptance criterion"
            Assert-ConcreteEvidence -Value $cells[2] -Label "$task implementation evidence"
            Assert-ConcreteEvidence -Value $cells[3] -Label "$task automated evidence"
            Assert-ConcreteEvidence -Value $cells[4] -Label "$task browser evidence"
            if ($strictWorkCycle -and $cells[5] -ne "Done") {
                throw "$task cannot finish because Acceptance Evidence status is '$($cells[5])', expected Done"
            }
            if ($requiresVerificationContract) {
                $contract = $verificationContracts[$task]
                if ((Test-CoreClosureCriterion -Criterion $cells[1]) -and $contract.level -ne "e2e-real-isolated") {
                    throw "$task acceptance criterion describes a core closure and requires e2e-real-isolated evidence, not '$($contract.level)'"
                }
                if ($contract.browserKind -eq "real" -and $cells[4] -notmatch "(?i)\breal\b") {
                    throw "$task Browser evidence must explicitly state real for its real-browser verification contract"
                }
                if ($contract.browserKind -eq "mock" -and $cells[4] -notmatch "(?i)\bmock\b") {
                    throw "$task Browser evidence must explicitly state mock for its mock-browser verification contract"
                }
            }

            $roadmapRows = @($roadmapLines | Where-Object {
                $row = @(Get-MarkdownTableCells -Line $_)
                $row.Count -ge 4 -and $row[0] -eq $task
            })
            if ($roadmapRows.Count -ne 1) {
                throw "Roadmap must contain exactly one row for $task; found $($roadmapRows.Count)"
            }
            $roadmapCells = @(Get-MarkdownTableCells -Line $roadmapRows[0])
            if ($strictWorkCycle -and $roadmapCells[$roadmapCells.Count - 1] -ne "Done") {
                throw "$task roadmap status must be Done before finish"
            }
        }

        if ($strictWorkCycle) {
        $validationMatch = [regex]::Match($reportContent, "(?s)## Validation\s*(.+?)\s*## Remaining Gaps")
        if (-not $validationMatch.Success) {
            throw "Execution report Validation section cannot be parsed"
        }
        $validationContent = $validationMatch.Groups[1].Value
        foreach ($label in @("Backend tests", "Frontend build", "Local quality gate", "Browser smoke")) {
            $prefix = "- ${label}:"
            $markerIndex = $validationContent.IndexOf($prefix, [System.StringComparison]::Ordinal)
            if ($markerIndex -lt 0) {
                throw "Execution report Validation must include '- ${label}: <evidence>'"
            }
            $lineEnd = $validationContent.IndexOf("`n", $markerIndex)
            if ($lineEnd -lt 0) { $lineEnd = $validationContent.Length }
            $validationEvidence = $validationContent.Substring($markerIndex + $prefix.Length, $lineEnd - $markerIndex - $prefix.Length).Trim()
            Assert-ConcreteEvidence -Value $validationEvidence -Label "Validation $label"
            if ($validationEvidence -match "待执行|finish.*待|pending|TODO|TBD") {
                throw "Validation $label still describes unfinished work: $validationEvidence"
            }
        }

        # Proof-of-run: the Validation section must reference at least one quality-gate
        # log produced inside this work cycle, so claimed test results trace back to an
        # actual gate execution instead of free-text assertions.
        $referencedLogs = @([regex]::Matches($validationContent, "quality-gate-\d{8}-\d{6}[A-Za-z0-9_.-]*\.log") | ForEach-Object { $_.Value } | Select-Object -Unique)
        $startedAtForLogs = [datetime]::Parse([string] $context.startedAt)
        $freshReferencedLogs = @($referencedLogs | Where-Object {
            $candidate = Join-Path $ReportDir $_
            (Test-Path -LiteralPath $candidate) -and ((Get-Item -LiteralPath $candidate).LastWriteTime -ge $startedAtForLogs)
        })
        if ($freshReferencedLogs.Count -eq 0) {
            $availableLogs = @($StepLogs | ForEach-Object { Split-Path $_ -Leaf })
            $availableHint = if ($availableLogs.Count -gt 0) { " Fresh logs from this gate run that you can cite: $($availableLogs -join ', ')" } else { "" }
            throw "Execution report Validation must reference at least one quality-gate log file produced during this work cycle (fresh after $($context.startedAt)).$availableHint"
        }

        $gapsMatch = [regex]::Match($reportContent, "(?s)## Remaining Gaps\s*(.+?)\s*## Next Steps")
        if (-not $gapsMatch.Success) {
            throw "Execution report Remaining Gaps section cannot be parsed"
        }
        $gaps = $gapsMatch.Groups[1].Value.Trim()
        if ([string]::IsNullOrWhiteSpace($gaps) -or $gaps -eq "-" -or $gaps -match "(?i)^-\s*(todo|tbd|pending)?\s*$") {
            throw "Execution report Remaining Gaps must explicitly state None or list concrete residual risks"
        }
        if ($requiresVerificationContract) {
            $gapRows = @($gaps -split "`r?`n" | Where-Object {
                $cells = @(Get-MarkdownTableCells -Line $_)
                $cells.Count -eq 4 -and $cells[0] -ne "Related task" -and $cells[0] -notmatch "^-+$"
            })
            if ($gapRows.Count -eq 0) {
                throw "Verification contract v2 requires Remaining Gaps to use the four-column gap table"
            }
            $blockingTerms = "(?i)(未完成|尚未|仍未|缺失|缺少|不支持|未实现|待补|仍保留|仅(?:有|支持)|TODO|TBD|Pending|not implemented|not supported|still missing|remaining implementation|existing single)"
            foreach ($gapRow in $gapRows) {
                $cells = @(Get-MarkdownTableCells -Line $gapRow)
                $relatedTask = $cells[0]
                $gapText = $cells[1]
                $effect = $cells[2].ToLowerInvariant()
                $tracking = $cells[3]
                if ($relatedTask -eq "N/A") {
                    if ($effect -ne "non-blocking") {
                        throw "A non-task Remaining Gaps row must use Acceptance effect = non-blocking"
                    }
                    continue
                }
                if ($expectedTasks -notcontains $relatedTask) {
                    $outsideScopeRows = @($roadmapLines | Where-Object {
                        $row = @(Get-MarkdownTableCells -Line $_)
                        $row.Count -ge 4 -and $row[0] -eq $relatedTask
                    })
                    if ($outsideScopeRows.Count -ne 1) {
                        throw "Remaining Gaps related task must be N/A, an expected task, or one unresolved roadmap task: '$relatedTask'"
                    }
                    $outsideScopeCells = @(Get-MarkdownTableCells -Line $outsideScopeRows[0])
                    if ($outsideScopeCells[$outsideScopeCells.Count - 1] -eq "Done") {
                        throw "$relatedTask is already Done but still has a Remaining Gaps row; resolve or remove the stale gap before another task can finish"
                    }
                    continue
                }
                if ($effect -ne "non-blocking") {
                    throw "$relatedTask has an acceptance-blocking Remaining Gap and cannot finish as Done; reopen the task instead"
                }
                if ($gapText -match $blockingTerms) {
                    throw "$relatedTask Remaining Gap describes incomplete acceptance work and cannot be classified as non-blocking"
                }
                Assert-ConcreteEvidence -Value $tracking -Label "$relatedTask Remaining Gaps tracking"
            }
        }

        if (-not ($context.PSObject.Properties.Name -contains "browserEvidence")) {
            throw "Finish requires fresh browserEvidence in the active work-cycle context"
        }
        $browserEvidence = $context.browserEvidence
        if ($browserEvidence.status -notin @("passed", "not_required")) {
            throw "Browser evidence status must be passed or not_required"
        }
        $startedAt = [datetime]::Parse([string] $context.startedAt)
        $browserCompletedAt = [datetime]::Parse([string] $browserEvidence.completedAt)
        if ($browserCompletedAt -lt $startedAt) {
            throw "Browser evidence predates the active work cycle"
        }
        if ($browserEvidence.status -eq "passed") {
            if ([string]::IsNullOrWhiteSpace([string] $browserEvidence.command) -or -not (Test-Path -LiteralPath ([string] $browserEvidence.logPath))) {
                throw "Passed browser evidence must include the executed command and an existing log path"
            }
        } elseif ([string] $browserEvidence.reason -eq "" -or ([string] $browserEvidence.reason).Length -lt 20) {
            throw "Not-required browser evidence must include a specific reason of at least 20 characters"
        }
        if ($requiresVerificationContract) {
            $requiredBrowserKinds = @($verificationContracts.Values | Where-Object { $_.browserKind -ne "not-required" } | ForEach-Object { $_.browserKind } | Select-Object -Unique)
            if ($requiredBrowserKinds.Count -gt 1) {
                throw "One finish command cannot close mixed real and mock browser contracts; split the work cycle or use real evidence for all browser-verified tasks."
            }
            if ($requiredBrowserKinds.Count -eq 0) {
                if ($browserEvidence.status -ne "not_required") {
                    throw "All Verification Contract rows are not-required, so finish must use -BrowserNotRequiredReason."
                }
            } else {
                $requiredKind = $requiredBrowserKinds[0]
                if ($browserEvidence.status -ne "passed" -or $browserEvidence.kind -ne $requiredKind) {
                    throw "Browser evidence must pass and be declared '$requiredKind' to satisfy the Verification Contract."
                }
                $requiresIsolation = @($verificationContracts.Values | Where-Object { $_.environment -eq "isolated" }).Count -gt 0
                if ($requiresIsolation -and $browserEvidence.environment -ne "isolated") {
                    throw "Verification Contract requires an isolated real browser environment."
                }
                if ($requiredKind -eq "mock" -and $browserEvidence.environment -ne "mock") {
                    throw "Mock Verification Contract requires browser evidence environment = mock."
                }
            }
            $evidenceClassification = if ($requiredBrowserKinds.Count -eq 0) {
                "no browser evidence required"
            } elseif ($requiredBrowserKinds[0] -eq "real") {
                "real browser closure evidence ($($browserEvidence.environment))"
            } else {
                "mock UI evidence only; it cannot close a real workflow"
            }
            Add-Result "  verification contract v2 passed: $evidenceClassification; kind/environment and acceptance gaps were independently checked"
        }
        }

        foreach ($doc in $context.requiredDocs) {
            if ($strictWorkCycle -and -not (Test-DocumentChanged -Path $doc -ChangedPaths $changedPaths -Context $context)) {
                throw "Full work-cycle finish requires updating document: $doc"
            }
        }

        $changedDocPaths = $changedPaths | Where-Object { $_ -like "docs/*.md" -or $_ -like "docs/*/*.md" -or $_ -like "docs/*/*/*.md" }
        $hasBaseline = $context.PSObject.Properties.Name -contains "baselineChangedPaths"
        if ($hasBaseline) {
            $baselineChangedPaths = @($context.baselineChangedPaths)
            $changedDocPaths = $changedDocPaths | Where-Object { $baselineChangedPaths -notcontains $_ }
        } else {
            Add-Warning "Active work-cycle context has no baselineChangedPaths; non-required dirty document boundary checks are skipped for this legacy context."
            $changedDocPaths = @()
        }

        $allowedCycleDocs = New-Object System.Collections.Generic.List[string]
        if ($context.PSObject.Properties.Name -contains "requiredDocs") {
            foreach ($doc in @($context.requiredDocs)) { $allowedCycleDocs.Add([string] $doc) | Out-Null }
        }
        if ($context.PSObject.Properties.Name -contains "allowedActiveDocs") {
            foreach ($doc in @($context.allowedActiveDocs)) { $allowedCycleDocs.Add([string] $doc) | Out-Null }
        }

        foreach ($path in $changedDocPaths) {
            $fullChangedDocPath = Join-Path $Root $path
            if (-not (Test-Path $fullChangedDocPath)) {
                continue
            }
            if ($path -like "docs/99-archive/*") {
                throw "docs/99-archive can only be edited in archive-only mode: $path"
            }
            if ($path -match "^docs/.*roadmap.*\.md$" -and $path -ne "docs/02-roadmap/current-roadmap.md" -and $path -notlike "docs/99-archive/*") {
                throw "New or changed roadmap knowledge_base_items are not allowed outside docs/02-roadmap/current-roadmap.md: $path"
            }
            if ($path -match "^docs/m\d+.*\.md$") {
                throw "Milestone knowledge_base_items are not allowed in docs root: $path"
            }
            $isAllowedDoc = $allowedCycleDocs.Contains($path) -or
                ($path -like "docs/90-reports/*.md") -or
                ($path -like "docs/05-runbooks/*.md")
            if (-not $isAllowedDoc) {
                throw "Document change is outside the work-cycle allowlist (required docs, active truth docs, docs/90-reports, docs/05-runbooks): $path"
            }
        }
    }

if ($Mode -in @("stage", "full") -and (Test-CommandExists "git") -and (Test-Path (Join-Path $Root ".git"))) {
    Invoke-Step "Git diff whitespace and conflict check" {
        Push-Location $Root
        try {
            git diff --check
            if ($LASTEXITCODE -ne 0) {
                throw "git diff --check reported whitespace errors or conflict markers"
            }
            git diff --cached --check
            if ($LASTEXITCODE -ne 0) {
                throw "git diff --cached --check reported whitespace errors or conflict markers"
            }
        } finally {
            Pop-Location
        }
    }
}

$status = if ($Failures.Count -eq 0) { "PASS" } else { "FAIL" }
$report = @()
$report += "# Quality Gate Report"
$report += ""
$report += "- Status: $status"
$report += "- Mode: $Mode"
$report += "- Time: $(Get-Date -Format o)"
$report += "- Root: $Root"
$report += ""
$report += "## Results"
$report += $Results
$report += ""
$report += "## Warnings"
if ($Warnings.Count -eq 0) {
    $report += "- None"
} else {
    $report += $Warnings
}
$report += ""
$report += "## Failures"
if ($Failures.Count -eq 0) {
    $report += "- None"
} else {
    $report += $Failures
}

Set-Content -Path $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host ""
Write-Host "Quality gate report: $ReportPath"

# Record this run into the active work-cycle context so finishes and audits can trace
# which gate executions (report, logs, mode, status) actually happened in the cycle.
$contextPathForEvidence = Join-Path $ReportDir "work-cycle-current.json"
if (Test-Path -LiteralPath $contextPathForEvidence) {
    try {
        $cycleContext = Get-Content -LiteralPath $contextPathForEvidence -Raw | ConvertFrom-Json
        $cycleContext | Add-Member -NotePropertyName lastQualityGate -NotePropertyValue ([ordered]@{
            reportPath = $ReportPath
            mode = $Mode
            status = $status
            stepLogs = @($StepLogs)
            completedAt = (Get-Date -Format o)
        }) -Force
        $cycleContext | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $contextPathForEvidence -Encoding UTF8
    } catch {
        Write-Warning "Could not record quality-gate evidence into the work-cycle context: $($_.Exception.Message)"
    }
}

if ($Failures.Count -gt 0) {
    exit 1
}
