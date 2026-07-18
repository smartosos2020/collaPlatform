$ErrorActionPreference = "Stop"

$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) "colla-work-cycle-stage-contract-$([guid]::NewGuid().ToString('N'))"
try {
    New-Item -ItemType Directory -Force -Path (Join-Path $testRoot "scripts") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $testRoot "docs/02-roadmap") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $testRoot "docs/90-reports") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $testRoot ".local-reports") | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "..\ai-quality-gate.ps1") -Destination (Join-Path $testRoot "scripts/ai-quality-gate.ps1")
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "..\ai-work-cycle-git-state.ps1") -Destination (Join-Path $testRoot "scripts/ai-work-cycle-git-state.ps1")

    $roadmapPath = Join-Path $testRoot "docs/02-roadmap/current-roadmap.md"
    $reportRelativePath = "docs/90-reports/test-m1-execution-report.md"
    $reportPath = Join-Path $testRoot $reportRelativePath
    Set-Content -LiteralPath $roadmapPath -Encoding UTF8 -Value @(
        "# Current Roadmap",
        "",
        "| Task | Acceptance | Evidence | Status |",
        "| --- | --- | --- | --- |",
        "| TEST-M1-T01 | Stage completion contract rejects an unchanged required document | Integration fixture | Done |"
    )

    Push-Location $testRoot
    try {
        git init -q
        git config user.email "work-cycle-test@example.invalid"
        git config user.name "Work Cycle Test"
        git config core.autocrlf false
        git add .
        git commit -q -m "baseline"
    } finally {
        Pop-Location
    }

    . (Join-Path $testRoot "scripts/ai-work-cycle-git-state.ps1")
    $baselineCommit = Get-WorkCycleHeadCommit -RepositoryRoot $testRoot
    $baselineSignatures = Get-WorkCycleFileSignatures -RepositoryRoot $testRoot -Paths @(
        "docs/02-roadmap/current-roadmap.md",
        $reportRelativePath
    )

    Set-Content -LiteralPath $reportPath -Encoding UTF8 -Value @(
        "# TEST-M1 Execution Report",
        "",
        "## Scope",
        "TEST-M1-T01 到 TEST-M1-T01",
        "",
        "## Completed Items",
        "- TEST-M1-T01 completed by the integration fixture.",
        "",
        "## Acceptance Evidence",
        "| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |",
        "| --- | --- | --- | --- | --- | --- |",
        "| TEST-M1-T01 | Stage bookkeeping remains consistent | scripts/ai-quality-gate.ps1 strict branch | Temporary-repository integration fixture executed | Browser is not required for this script-only fixture | Done |",
        "",
        "## Code Changes",
        "- Script-only integration fixture.",
        "",
        "## Validation",
        "- Backend tests: Not required because this fixture exercises only the PowerShell completion contract.",
        "- Frontend build: Not required because this fixture contains no frontend code.",
        "- Local quality gate: quality-gate-20260718-000000-contract-test.log records the synthetic proof-of-run fixture.",
        "- Browser smoke: Not required because this fixture has no browser-visible behavior.",
        "",
        "## Remaining Gaps",
        "- None; the fixture intentionally leaves the required roadmap unchanged.",
        "",
        "## Next Steps",
        "- Confirm that stage rejects the unchanged required document."
    )

    $startedAt = (Get-Date).AddMinutes(-1)
    $proofLog = Join-Path $testRoot ".local-reports/quality-gate-20260718-000000-contract-test.log"
    Set-Content -LiteralPath $proofLog -Value "synthetic proof of run" -Encoding UTF8
    $context = [ordered]@{
        goal = "TEST-M1 stage contract"
        status = "in-progress"
        taskRange = "TEST-M1-T01 到 TEST-M1-T01"
        milestone = "TEST-M1"
        docMode = "code-doc-report"
        startedAt = $startedAt.ToString("o")
        baselineCommit = $baselineCommit
        baselineChangedPaths = @()
        baselineFileSignatures = $baselineSignatures
        requiredDocs = @("docs/02-roadmap/current-roadmap.md", $reportRelativePath)
        allowedActiveDocs = @("docs/02-roadmap/current-roadmap.md")
        workScope = [ordered]@{
            scopeValid = $true
            scopeWarnings = @()
            milestoneCount = 1
            maxMilestonesPerCycle = 1
            expectedTasks = @("TEST-M1-T01")
        }
        evidencePolicy = [ordered]@{ contractVersion = 1 }
        browserEvidence = [ordered]@{
            status = "not_required"
            kind = "not-required"
            environment = "not-required"
            reason = "This script-only integration fixture has no browser-visible behavior."
            completedAt = (Get-Date).ToString("o")
        }
    }
    $context | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $testRoot ".local-reports/work-cycle-current.json") -Encoding UTF8

    $gateOutputPath = Join-Path $testRoot "stage-gate-output.log"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $testRoot "scripts/ai-quality-gate.ps1") `
            -Mode stage -BackendStrategy skip -FrontendStrategy skip -CollaborationStrategy skip `
            -SkipAudit -SkipDocker -CompactOutput *> $gateOutputPath
        $gateExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $gateOutput = Get-Content -LiteralPath $gateOutputPath -Raw
    if ($gateExitCode -eq 0) {
        throw "Stage completion contract unexpectedly passed with an unchanged required roadmap."
    }
    if ($gateOutput -notmatch "Full work-cycle finish requires updating" -or $gateOutput -notmatch "current-roadmap.md") {
        throw "Stage gate failed for an unexpected reason:`n$gateOutput"
    }

    Write-Host "PASS: stage completion enforces the strict required-document contract."
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
