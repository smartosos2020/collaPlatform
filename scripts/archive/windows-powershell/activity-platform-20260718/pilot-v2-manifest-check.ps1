param(
    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,
    [ValidateSet("structural", "initialization", "freeze", "simulation-freeze")]
    [string] $Level = "structural",
    [string] $ReportDirectory = ".local-reports"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "pilot-v2-common.ps1")

$resolvedManifest = if ([System.IO.Path]::IsPathRooted($ManifestPath)) {
    $ManifestPath
} else {
    Join-Path $Root $ManifestPath
}
$resolvedReportDirectory = if ([System.IO.Path]::IsPathRooted($ReportDirectory)) {
    $ReportDirectory
} else {
    Join-Path $Root $ReportDirectory
}
$manifest = Read-PilotManifest -ManifestPath $resolvedManifest
$validation = Test-PilotManifest -Manifest $manifest -Level $Level
$reports = Write-PilotValidationReport -Manifest $manifest -Validation $validation -ReportDirectory $resolvedReportDirectory -Label $Level

foreach ($warning in @($validation.warnings)) {
    Write-Warning $warning
}
foreach ($error in @($validation.errors)) {
    Write-Host "- BLOCKER: $error"
}
Write-Host "Manifest decision: $(if ($validation.valid) { 'PASS' } else { 'BLOCKED' })"
Write-Host "Manifest report: $($reports.markdownPath)"
if (-not $validation.valid) {
    exit 2
}
