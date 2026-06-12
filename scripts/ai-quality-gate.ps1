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
    $excludeDirs = @("\node_modules\", "\target\", "\dist\", "\.local-logs\", "\.local-reports\", "\.git\")
    Get-ChildItem -Path $Root -Recurse -File | Where-Object {
        $fullName = $_.FullName
        -not ($excludeDirs | Where-Object { $fullName.Contains($_) }) -and
        $fullName -ne (Join-Path $Root "scripts\ai-quality-gate.ps1") -and
        $_.Length -lt 2MB -and
        $_.Extension -notin @(".png", ".jpg", ".jpeg", ".gif", ".ico", ".jar", ".class", ".zip", ".gz")
    }
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
        $ps = docker compose ps --format json | ConvertFrom-Json
        $required = @("postgres", "redis", "minio")
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
}

if (-not $SkipAudit) {
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
        foreach ($path in @("web\dist", "server\target", "node_modules", ".local-logs")) {
            $fullPath = Join-Path $Root $path
            if (Test-Path $fullPath) {
                Add-Warning "Generated/local path exists: $path. It must stay ignored and out of commits."
            }
        }

        if ((Test-CommandExists "git") -and (Test-Path (Join-Path $Root ".git"))) {
            Push-Location $Root
            try {
                $trackedGenerated = git ls-files "web/dist" "server/target" "node_modules" ".local-logs" ".local-reports" 2>$null
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
            $todo = & rg -n "TODO|FIXME|HACK|XXX" $Root `
                -g "!node_modules" -g "!target" -g "!dist" -g "!.local-logs" -g "!.local-reports" `
                -g "!scripts/ai-quality-gate.ps1" -g "!docs/ai-engineering-governance.md" 2>$null
            if ($todo) {
                Add-Warning "Open implementation markers found. Review before release."
                $todo | Select-Object -First 50 | ForEach-Object { Add-Warning "  $_" }
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
