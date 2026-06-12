param(
    [ValidateSet("start", "checkpoint", "finish")]
    [string] $Stage,

    [string] $Goal = "",

    [ValidateSet("quick", "full")]
    [string] $GateMode = "quick"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ReportDir = Join-Path $Root ".local-reports"
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

switch ($Stage) {
    "start" {
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "start-$Goal"
        Write-Host "Start checkpoint created. Next: implement a small vertical slice, then run checkpoint."
    }
    "checkpoint" {
        & (Join-Path $PSScriptRoot "ai-quality-gate.ps1") -Mode $GateMode
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "checkpoint-$Goal"
    }
    "finish" {
        & (Join-Path $PSScriptRoot "ai-quality-gate.ps1") -Mode "full"
        & (Join-Path $PSScriptRoot "ai-audit-snapshot.ps1") -Label "finish-$Goal"
        Write-Host "Finish checkpoint completed. Summarize changed files, validation, residual risks, and next tasks."
    }
}
