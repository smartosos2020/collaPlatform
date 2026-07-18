param(
    [switch] $SkipReport
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "sensitive-data-scan-$Timestamp.md"
$AllowlistPath = Join-Path $PSScriptRoot "sensitive-scan-allowlist.tsv"
$Failures = New-Object System.Collections.Generic.List[string]
$Results = New-Object System.Collections.Generic.List[string]

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
}

function Add-Failure {
    param([string] $Message)
    $Failures.Add($Message) | Out-Null
    Write-Error $Message -ErrorAction Continue
}

# Detection patterns. Keep each pattern on one line; the allowlist references the
# exact pattern text, so changing a pattern requires updating the allowlist too.
$patterns = @(
    "BEGIN RSA PRIVATE KEY",
    "BEGIN OPENSSH PRIVATE KEY",
    "BEGIN EC PRIVATE KEY",
    "AKIA[0-9A-Z]{16}",
    "xox[baprs]-[0-9A-Za-z-]+",
    "ghp_[0-9A-Za-z_]{36,}",
    "sk-[A-Za-z0-9]{20,}",
    "eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{5,}",
    "password\s*=\s*['""][^'""]+['""]",
    "secret\s*=\s*['""][^'""]+['""]",
    "token\s*=\s*['""][^'""]+['""]"
)

# Allowlist entries: <relative path glob><TAB><exact pattern text or *><TAB><reason>.
# A hit is waived only when both the file path and the matched pattern align with an
# entry, so waiving a dummy password fixture never hides a real token in the same file.
$allowlist = New-Object System.Collections.Generic.List[object]
if (Test-Path -LiteralPath $AllowlistPath) {
    foreach ($line in @(Get-Content -LiteralPath $AllowlistPath)) {
        $trimmed = $line.TrimEnd()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }
        $parts = $trimmed -split "`t", 3
        if ($parts.Count -lt 3) {
            Add-Failure "Malformed allowlist line (expected path<TAB>pattern<TAB>reason): $trimmed"
            continue
        }
        $allowlist.Add([pscustomobject]@{
            pathGlob = $parts[0].Trim().Replace("\", "/")
            pattern  = $parts[1].Trim()
            reason   = $parts[2].Trim()
        }) | Out-Null
    }
}

$excludeDirs = @("\node_modules\", "\target\", "\dist\", "\.local-logs\", "\.local-reports\", "\.local-backups\", "\test-results\", "\playwright-report\", "\.git\", "\.idea\")
$selfPath = Join-Path $Root "scripts\sensitive-data-scan.ps1"
$files = Get-ChildItem -Path $Root -Recurse -File | Where-Object {
    $fullName = $_.FullName
    -not ($excludeDirs | Where-Object { $fullName.Contains($_) }) -and
    $fullName -ne $selfPath -and
    $_.Length -lt 2MB -and
    $_.Extension -notin @(".png", ".jpg", ".jpeg", ".gif", ".ico", ".jar", ".class", ".zip", ".gz")
}

$secretHits = New-Object System.Collections.Generic.List[string]
$waivedCount = 0
foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) {
        continue
    }
    $relative = $file.FullName.Substring($Root.Path.Length + 1).Replace("\", "/")
    foreach ($pattern in $patterns) {
        if ($content -notmatch $pattern) {
            continue
        }
        $waiver = $allowlist | Where-Object {
            ($_.pattern -eq "*" -or $_.pattern -eq $pattern) -and ($relative -like $_.pathGlob)
        } | Select-Object -First 1
        if ($null -ne $waiver) {
            $waivedCount++
            Add-Result "  WAIVED: $relative [$pattern] - $($waiver.reason)"
        } else {
            $secretHits.Add("$relative matches $pattern") | Out-Null
        }
    }
}

if ($secretHits.Count -gt 0) {
    foreach ($hit in $secretHits) {
        Add-Failure $hit
    }
} else {
    Add-Result "- PASS: Sensitive data scan (patterns=$($patterns.Count), waived=$waivedCount, allowlist entries=$($allowlist.Count))"
}

if (-not $SkipReport) {
    $status = if ($Failures.Count -eq 0) { "PASS" } else { "FAIL" }
    $report = @(
        "# Sensitive Data Scan",
        "",
        "- Status: $status",
        "- Time: $(Get-Date -Format o)",
        "- Patterns: $($patterns.Count)",
        "- Waived: $waivedCount",
        "",
        "## Results"
    ) + $Results + @(
        "",
        "## Failures"
    )
    if ($Failures.Count -eq 0) {
        $report += "- None"
    } else {
        $report += $Failures
    }
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Sensitive data scan report: $ReportPath"
}

if ($Failures.Count -gt 0) {
    exit 1
}
