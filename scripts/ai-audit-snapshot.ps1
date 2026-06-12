param(
    [string] $Label = "manual"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "audit-snapshot-$Timestamp.md"

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Invoke-Capture {
    param([string] $Command)

    try {
        $output = Invoke-Expression $Command 2>&1
        if (-not $output) {
            return "_No output_"
        }
        return ($output | Out-String).TrimEnd()
    } catch {
        return "ERROR: $($_.Exception.Message)"
    }
}

Push-Location $Root
try {
    $fileCount = (Get-ChildItem -Recurse -File |
        Where-Object {
            -not $_.FullName.Contains("\node_modules\") -and
            -not $_.FullName.Contains("\target\") -and
            -not $_.FullName.Contains("\dist\") -and
            -not $_.FullName.Contains("\.local-reports\") -and
            -not $_.FullName.Contains("\.local-logs\")
        }).Count

    $report = @()
    $report += "# AI Audit Snapshot"
    $report += ""
    $report += "- Label: $Label"
    $report += "- Time: $(Get-Date -Format o)"
    $report += "- Root: $Root"
    $report += "- File count excluding generated artifacts: $fileCount"
    $report += ""
    $report += "## Toolchain"
    $report += '```text'
    $report += (Invoke-Capture 'cmd /c "java -version 2>&1"')
    $report += (Invoke-Capture "mvn -version")
    $nodeVersion = Invoke-Capture "node --version"
    $pnpmVersion = Invoke-Capture "pnpm --version"
    $dockerVersion = Invoke-Capture "docker --version"
    $report += "node $nodeVersion"
    $report += "pnpm $pnpmVersion"
    $report += "docker $dockerVersion"
    $report += '```'
    $report += ""
    $report += "## Git Status"
    $report += '```text'
    if (Test-Path ".git") {
        $report += Invoke-Capture "git status --short"
    } else {
        $report += "Not a git repository."
    }
    $report += '```'
    $report += ""
    $report += "## Docker Compose"
    $report += '```text'
    $report += Invoke-Capture "docker compose ps"
    $report += '```'
    $report += ""
    $report += "## Source Inventory"
    $report += '```text'
    $sourceInventoryCommand = "rg --files -g '!node_modules' -g '!target' -g '!dist' -g '!.local-reports' -g '!.local-logs'"
    $report += Invoke-Capture $sourceInventoryCommand
    $report += '```'

    Set-Content -Path $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Audit snapshot: $ReportPath"
} finally {
    Pop-Location
}
