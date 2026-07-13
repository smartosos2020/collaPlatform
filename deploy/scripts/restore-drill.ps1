param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [string] $BaseUrl = "http://localhost",
    [switch] $RunRestore,
    [switch] $ConfirmRestore
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "restore-drill-$Timestamp.md"
$Results = New-Object System.Collections.Generic.List[string]

function Resolve-ProjectPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $Root $Path
}

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
}

function Assert-FileHash {
    param(
        [string] $Path,
        [string] $ExpectedHash
    )
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha256.ComputeHash($stream)
            $actual = (($hash | ForEach-Object { $_.ToString("x2") }) -join "").ToUpperInvariant()
        } finally {
            $sha256.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
    if ($actual -ne $ExpectedHash) {
        throw "Hash mismatch for $Path. Expected $ExpectedHash, got $actual"
    }
}

$ComposePath = Resolve-ProjectPath $ComposeFile
$EnvPath = Resolve-ProjectPath $EnvFile
$BackupDir = Resolve-Path $BackupPath
$ManifestPath = Join-Path $BackupDir "manifest.json"

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

if (-not (Test-Path $ComposePath)) {
    throw "Compose file not found: $ComposePath"
}
if (-not (Test-Path $EnvPath)) {
    throw "Environment file not found: $EnvPath"
}
if (-not (Test-Path $ManifestPath)) {
    throw "Backup manifest not found: $ManifestPath"
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
foreach ($file in @($manifest.files)) {
    $path = Join-Path $BackupDir $file.name
    if (-not (Test-Path $path)) {
        throw "Missing backup file: $path"
    }
    Assert-FileHash -Path $path -ExpectedHash $file.sha256
    $item = Get-Item -LiteralPath $path
    Add-Result "- PASS: $($file.name) hash verified, $($item.Length) bytes"
}

Push-Location $Root
try {
    docker compose --env-file $EnvPath -f $ComposePath config -q
    if ($LASTEXITCODE -ne 0) {
        throw "Compose config validation failed"
    }
    Add-Result "- PASS: compose config validated"

    if ($RunRestore) {
        if (-not $ConfirmRestore) {
            throw "Restore drill is destructive when -RunRestore is used. Add -ConfirmRestore to continue."
        }
        & (Join-Path $PSScriptRoot "restore.ps1") -BackupPath $BackupDir -ComposeFile $ComposeFile -EnvFile $EnvFile -BaseUrl $BaseUrl -ConfirmRestore
        if ($LASTEXITCODE -ne 0) {
            throw "Restore command failed"
        }
        Add-Result "- PASS: restore command completed"
    } else {
        Add-Result "- PASS: dry-run only; restore command was not executed"
    }

    $report = @(
        "# Restore Drill",
        "",
        "- Time: $(Get-Date -Format o)",
        "- Backup path: $BackupDir",
        "- Mode: $(if ($RunRestore) { "restore" } else { "dry-run" })",
        "",
        "## Results"
    ) + $Results
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Restore drill report: $ReportPath"
} finally {
    Pop-Location
}
