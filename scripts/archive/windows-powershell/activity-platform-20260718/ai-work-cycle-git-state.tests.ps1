$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "..\ai-work-cycle-git-state.ps1")

function Assert-ContainsPath {
    param([string[]] $Paths, [string] $Expected)
    if ($Paths -notcontains $Expected) {
        throw "Expected changed paths to contain '$Expected'; actual: $($Paths -join ', ')"
    }
}

function Assert-ExcludesPath {
    param([string[]] $Paths, [string] $Unexpected)
    if ($Paths -contains $Unexpected) {
        throw "Expected changed paths to exclude '$Unexpected'; actual: $($Paths -join ', ')"
    }
}

$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) "colla-work-cycle-git-state-$([guid]::NewGuid().ToString('N'))"
try {
    New-Item -ItemType Directory -Force -Path (Join-Path $testRoot "docs/02-roadmap") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $testRoot "src") | Out-Null
    Set-Content -LiteralPath (Join-Path $testRoot "docs/02-roadmap/current-roadmap.md") -Value "baseline roadmap" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $testRoot "src/app.txt") -Value "baseline app" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $testRoot "preexisting.txt") -Value "committed baseline" -Encoding UTF8

    Push-Location $testRoot
    try {
        git init -q
        git config user.email "work-cycle-test@example.invalid"
        git config user.name "Work Cycle Test"
        git add .
        git commit -q -m "baseline"
    } finally {
        Pop-Location
    }

    Set-Content -LiteralPath (Join-Path $testRoot "preexisting.txt") -Value "dirty before cycle" -Encoding UTF8
    $requiredDocs = @("docs/02-roadmap/current-roadmap.md", "docs/90-reports/test-execution-report.md")
    $baselineChangedPaths = @(Get-WorkCycleGitStatusPaths -RepositoryRoot $testRoot)
    $baselineSignatures = Get-WorkCycleFileSignatures -RepositoryRoot $testRoot -Paths @($baselineChangedPaths + $requiredDocs)
    $context = [pscustomobject]@{
        baselineCommit = Get-WorkCycleHeadCommit -RepositoryRoot $testRoot
        baselineChangedPaths = $baselineChangedPaths
        baselineFileSignatures = [pscustomobject] $baselineSignatures
    }

    $unchanged = @(Get-ChangedWorkCyclePaths -RepositoryRoot $testRoot -Context $context)
    Assert-ExcludesPath -Paths $unchanged -Unexpected "docs/02-roadmap/current-roadmap.md"
    Assert-ExcludesPath -Paths $unchanged -Unexpected "preexisting.txt"

    Set-Content -LiteralPath (Join-Path $testRoot "docs/02-roadmap/current-roadmap.md") -Value "updated roadmap" -Encoding UTF8
    $documentChange = @(Get-ChangedWorkCyclePaths -RepositoryRoot $testRoot -Context $context)
    Assert-ContainsPath -Paths $documentChange -Expected "docs/02-roadmap/current-roadmap.md"

    Push-Location $testRoot
    try {
        git restore -- "docs/02-roadmap/current-roadmap.md"
        Set-Content -LiteralPath "src/app.txt" -Value "committed during cycle" -Encoding UTF8
        git add "src/app.txt"
        git commit -q -m "cycle change"
    } finally {
        Pop-Location
    }
    $committedChange = @(Get-ChangedWorkCyclePaths -RepositoryRoot $testRoot -Context $context)
    Assert-ContainsPath -Paths $committedChange -Expected "src/app.txt"
    Assert-ExcludesPath -Paths $committedChange -Unexpected "preexisting.txt"

    Set-Content -LiteralPath (Join-Path $testRoot "preexisting.txt") -Value "changed again during cycle" -Encoding UTF8
    $baselineDirtyChanged = @(Get-ChangedWorkCyclePaths -RepositoryRoot $testRoot -Context $context)
    Assert-ContainsPath -Paths $baselineDirtyChanged -Expected "preexisting.txt"

    Write-Host "PASS: work-cycle Git baseline tracks clean required docs, baseline-dirty files, and committed cycle changes."
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
