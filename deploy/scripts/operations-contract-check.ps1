param()

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $PSScriptRoot "operations-common.ps1")
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ScratchRoot = Join-Path $Root ".local-reports\ops-contract-$Timestamp"
$Results = New-Object System.Collections.Generic.List[string]

function Assert-Throws {
    param(
        [Parameter(Mandatory = $true)][scriptblock] $Action,
        [Parameter(Mandatory = $true)][string] $Pattern,
        [Parameter(Mandatory = $true)][string] $Description
    )

    try {
        & $Action
    } catch {
        if ($_.Exception.Message -notmatch $Pattern) {
            throw "$Description failed with an unexpected error: $($_.Exception.Message)"
        }
        $Results.Add("- PASS: $Description") | Out-Null
        return
    }
    throw "$Description did not reject the invalid input"
}

function Assert-ProcessFails {
    param(
        [Parameter(Mandatory = $true)][string[]] $Arguments,
        [Parameter(Mandatory = $true)][string] $Pattern,
        [Parameter(Mandatory = $true)][string] $Description
    )

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & powershell -NoProfile -ExecutionPolicy Bypass @Arguments 2>&1 | Out-String
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($exitCode -eq 0) {
        throw "$Description unexpectedly succeeded"
    }
    if ($output -notmatch $Pattern) {
        throw "$Description failed without the expected message. Output: $output"
    }
    $Results.Add("- PASS: $Description") | Out-Null
}

New-Item -ItemType Directory -Force -Path $ScratchRoot | Out-Null
try {
    $parseErrors = New-Object System.Collections.Generic.List[object]
    foreach ($script in Get-ChildItem -LiteralPath $PSScriptRoot -Filter "*.ps1") {
        $tokens = $null
        $errors = $null
        [void] [System.Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref] $tokens, [ref] $errors)
        foreach ($error in @($errors)) {
            $parseErrors.Add("$($script.Name): $($error.Message)") | Out-Null
        }
    }
    if ($parseErrors.Count -gt 0) {
        throw "PowerShell parse errors: $($parseErrors -join '; ')"
    }
    $Results.Add("- PASS: all active deployment PowerShell scripts parse") | Out-Null

    Assert-Throws -Description "path boundary rejects siblings with the same prefix" -Pattern "outside the allowed root" -Action {
        Assert-CollaPathWithin -Parent (Join-Path $ScratchRoot "allowed") -Candidate (Join-Path $ScratchRoot "allowed-escape\file") | Out-Null
    }

    $envPath = Join-Path $ScratchRoot "invalid.env"
    Set-Content -LiteralPath $envPath -Encoding UTF8 -Value @(
        "POSTGRES_DB=colla_platform",
        "POSTGRES_USER=colla",
        "POSTGRES_PASSWORD=replace-with-password"
    )
    Assert-Throws -Description "production environment rejects placeholders" -Pattern "placeholder|missing" -Action {
        Assert-CollaProductionEnvironment -EnvPath $envPath | Out-Null
    }

    $backupPath = Join-Path $ScratchRoot "backup"
    New-Item -ItemType Directory -Path $backupPath | Out-Null
    $dumpPath = Join-Path $backupPath "postgres.sql"
    Set-Content -LiteralPath $dumpPath -Encoding UTF8 -Value "select 1;"
    $dump = Get-Item -LiteralPath $dumpPath
    [ordered]@{
        manifestVersion = 2
        createdAt = (Get-Date).ToUniversalTime().ToString("o")
        projectName = "contract-source"
        files = @([ordered]@{
            name = "postgres.sql"
            kind = "postgres"
            bytes = $dump.Length
            sha256 = Get-CollaSha256 -Path $dumpPath
        })
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $backupPath "manifest.json") -Encoding UTF8
    Read-CollaBackupManifest -BackupPath $backupPath -VerifyFiles | Out-Null
    $Results.Add("- PASS: manifest-v2 accepts an intact verified backup") | Out-Null

    Add-Content -LiteralPath $dumpPath -Value "tamper"
    Assert-Throws -Description "manifest-v2 rejects a modified backup file" -Pattern "size mismatch|hash mismatch" -Action {
        Read-CollaBackupManifest -BackupPath $backupPath -VerifyFiles | Out-Null
    }

    Assert-ProcessFails -Description "restore requires explicit destructive confirmation" -Pattern "Restore is destructive" -Arguments @(
        "-File", (Join-Path $PSScriptRoot "restore.ps1"),
        "-BackupPath", $backupPath,
        "-ExpectedProjectName", "contract-target",
        "-ConfirmationText", "RESTORE:contract-target"
    )
    Assert-ProcessFails -Description "rollback requires explicit destructive confirmation" -Pattern "Rollback changes the running environment" -Arguments @(
        "-File", (Join-Path $PSScriptRoot "rollback.ps1"),
        "-ServerImage", "colla/server:1.0.0",
        "-WebImage", "colla/web:1.0.0",
        "-ExpectedProjectName", "contract-target",
        "-ConfirmationText", "ROLLBACK:contract-target:colla/server:1.0.0:colla/web:1.0.0"
    )
    Assert-ProcessFails -Description "release skips require explicit partial mode" -Pattern "makes the check partial" -Arguments @(
        "-File", (Join-Path $PSScriptRoot "release-check.ps1"),
        "-SkipImageBuild"
    )

    foreach ($result in $Results) {
        Write-Host $result
    }
    Write-Host "Operations contract check passed ($($Results.Count) checks)"
} finally {
    if (Test-Path -LiteralPath $ScratchRoot) {
        Remove-Item -LiteralPath $ScratchRoot -Recurse -Force
    }
}
