param(
    [ValidateSet("quick", "full")]
    [string] $Mode = "quick",

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
        [string] $WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        Write-Host "Running: $Command"
        Invoke-Expression $Command
        if ($LASTEXITCODE -ne 0) {
            throw "Command exited with code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Get-TextFiles {
    $excludeDirs = @("\node_modules\", "\target\", "\dist\", "\.local-logs\", "\.local-reports\", "\.local-backups\", "\test-results\", "\playwright-report\", "\.git\")
    Get-ChildItem -Path $Root -Recurse -File | Where-Object {
        $fullName = $_.FullName
        -not ($excludeDirs | Where-Object { $fullName.Contains($_) }) -and
        $fullName -ne (Join-Path $Root "scripts\ai-quality-gate.ps1") -and
        $_.Length -lt 2MB -and
        $_.Extension -notin @(".png", ".jpg", ".jpeg", ".gif", ".ico", ".jar", ".class", ".zip", ".gz")
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

function Test-DocumentChanged {
    param(
        [string] $Path,
        [string[]] $ChangedPaths
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $true
    }
    $normalized = $Path.Replace("\", "/")
    return $ChangedPaths -contains $normalized
}

function Get-ComposeServices {
    $output = docker compose ps --format json
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose ps failed with code $LASTEXITCODE"
    }
    if (-not $output) {
        return @()
    }
    return @($output | ConvertFrom-Json)
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
    Invoke-Step "Backend tests" {
        Invoke-LoggedCommand "mvn test" (Join-Path $Root "server")
    }

    if ($Mode -eq "full") {
        Invoke-Step "Backend package" {
            Invoke-LoggedCommand "mvn -DskipTests package" (Join-Path $Root "server")
        }
    }
}

if (-not $SkipFrontend) {
    Invoke-Step "Frontend lint" {
        Invoke-LoggedCommand "pnpm web:lint" $Root
    }

    Invoke-Step "Frontend build" {
        Invoke-LoggedCommand "pnpm web:build" $Root
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
        $patterns = @(
            "BEGIN RSA PRIVATE KEY",
            "BEGIN OPENSSH PRIVATE KEY",
            "AKIA[0-9A-Z]{16}",
            "xox[baprs]-[0-9A-Za-z-]+",
            "ghp_[0-9A-Za-z_]{36,}",
            "sk-[A-Za-z0-9]{20,}",
            "password\s*=\s*['""][^'""]+['""]",
            "secret\s*=\s*['""][^'""]+['""]",
            "token\s*=\s*['""][^'""]+['""]"
        )

        $secretHits = New-Object System.Collections.Generic.List[string]
        foreach ($file in Get-TextFiles) {
            $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
            foreach ($pattern in $patterns) {
                if ($content -match $pattern) {
                    $relative = Resolve-Path -Relative $file.FullName
                    $secretHits.Add("$relative matches $pattern") | Out-Null
                }
            }
        }

        if ($secretHits.Count -gt 0) {
            throw "Potential secrets found: $($secretHits -join '; ')"
        }
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
            throw "Milestone, roadmap, and report documents are not allowed in docs root: $($illegalRootPattern.Name -join ', ')"
        }
    }

    Invoke-Step "Work-cycle documentation contract" {
        $contextPath = Join-Path $ReportDir "work-cycle-current.json"
        if (-not (Test-Path $contextPath)) {
            Add-Result "  no active work-cycle context; skipped strict document contract"
            return
        }

        $context = Get-Content -LiteralPath $contextPath -Raw | ConvertFrom-Json
        $changedPaths = @(Get-NormalizedGitStatusPaths)
        if ($context.docMode -eq "archive-only") {
            Add-Result "  archive-only document mode; code-doc-report contract not required"
            return
        }

        if ($context.PSObject.Properties.Name -contains "workScope") {
            $scope = $context.workScope
            $maxMilestones = if ($null -ne $scope.maxMilestonesPerCycle) { [int] $scope.maxMilestonesPerCycle } else { 1 }
            $maxTasks = if ($null -ne $scope.maxTasksPerCycle) { [int] $scope.maxTasksPerCycle } else { 8 }

            if (-not [bool] $scope.scopeValid) {
                throw "Active work-cycle scope is invalid: $($scope.scopeWarnings -join '; ')"
            }
            if ($null -ne $scope.milestoneCount -and [int] $scope.milestoneCount -gt $maxMilestones) {
                throw "Active work-cycle crosses $($scope.milestoneCount) milestones; max is $maxMilestones"
            }
            if ($null -ne $scope.taskCount -and [int] $scope.taskCount -gt $maxTasks) {
                throw "Active work-cycle contains $($scope.taskCount) tasks; max is $maxTasks"
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
                if (-not (Test-DocumentChanged -Path $doc -ChangedPaths $changedPaths)) {
                    Add-Warning "Work-cycle document has not changed yet: $doc"
                }
            }
            return
        }

        foreach ($doc in $context.requiredDocs) {
            if (-not (Test-DocumentChanged -Path $doc -ChangedPaths $changedPaths)) {
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

        foreach ($path in $changedDocPaths) {
            $fullChangedDocPath = Join-Path $Root $path
            if (-not (Test-Path $fullChangedDocPath)) {
                continue
            }
            if ($path -like "docs/99-archive/*") {
                throw "docs/99-archive can only be edited in archive-only mode: $path"
            }
            if ($path -match "^docs/.*roadmap.*\.md$" -and $path -ne "docs/02-roadmap/current-roadmap.md" -and $path -notlike "docs/99-archive/*") {
                throw "New or changed roadmap documents are not allowed outside docs/02-roadmap/current-roadmap.md: $path"
            }
            if ($path -match "^docs/m\d+.*\.md$") {
                throw "Milestone documents are not allowed in docs root: $path"
            }
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

if ($Failures.Count -gt 0) {
    exit 1
}
