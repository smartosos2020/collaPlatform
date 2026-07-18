$ErrorActionPreference = "Stop"

foreach ($testScript in @(
    "ai-work-cycle-git-state.tests.ps1",
    "ai-work-cycle-stage-contract.tests.ps1"
)) {
    & (Join-Path $PSScriptRoot $testScript)
}

Write-Host "PASS: all AI work-cycle regression tests completed."
