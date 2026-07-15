Set-StrictMode -Version Latest

function Resolve-CollaPath {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Root,
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $Root $Path))
}

function Assert-CollaPathWithin {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Parent,
        [Parameter(Mandatory = $true)]
        [string] $Candidate,
        [string] $Description = "path"
    )

    $parentPath = [System.IO.Path]::GetFullPath($Parent).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
    $candidatePath = [System.IO.Path]::GetFullPath($Candidate)
    $prefix = $parentPath + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidatePath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Description is outside the allowed root '$parentPath': $candidatePath"
    }
    return $candidatePath
}

function Get-CollaSha256 {
    param([Parameter(Mandatory = $true)][string] $Path)

    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha256.ComputeHash($stream)
            return ([System.BitConverter]::ToString($hash)).Replace("-", "")
        } finally {
            $sha256.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Get-CollaComposeModel {
    param(
        [Parameter(Mandatory = $true)][string] $ComposePath,
        [Parameter(Mandatory = $true)][string] $EnvPath
    )

    $raw = (& docker compose --env-file $EnvPath -f $ComposePath config --format json | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to render compose configuration"
    }
    try {
        return $raw | ConvertFrom-Json
    } catch {
        throw "Compose configuration is not valid JSON: $($_.Exception.Message)"
    }
}

function Get-CollaComposeProjectName {
    param(
        [Parameter(Mandatory = $true)][string] $ComposePath,
        [Parameter(Mandatory = $true)][string] $EnvPath
    )

    $model = Get-CollaComposeModel -ComposePath $ComposePath -EnvPath $EnvPath
    if (-not ($model.PSObject.Properties.Name -contains "name") -or [string]::IsNullOrWhiteSpace($model.name)) {
        throw "Compose configuration does not expose a project name"
    }
    return [string] $model.name
}

function Get-CollaComposeServiceNames {
    param(
        [Parameter(Mandatory = $true)][string] $ComposePath,
        [Parameter(Mandatory = $true)][string] $EnvPath
    )

    $model = Get-CollaComposeModel -ComposePath $ComposePath -EnvPath $EnvPath
    return @($model.services.PSObject.Properties.Name)
}

function Read-CollaEnvironment {
    param([Parameter(Mandatory = $true)][string] $EnvPath)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $EnvPath) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }
        $parts = $trimmed -split "=", 2
        if ($parts.Count -eq 2) {
            $values[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
    return $values
}

function Assert-CollaProductionEnvironment {
    param([Parameter(Mandatory = $true)][string] $EnvPath)

    $required = @(
        "POSTGRES_DB",
        "POSTGRES_USER",
        "POSTGRES_PASSWORD",
        "MINIO_ACCESS_KEY",
        "MINIO_SECRET_KEY",
        "MINIO_BUCKET",
        "JWT_ACCESS_SECRET",
        "JWT_REFRESH_SECRET",
        "INIT_ADMIN_USERNAME",
        "INIT_ADMIN_PASSWORD",
        "CORS_ALLOWED_ORIGINS",
        "APP_BASE_URL",
        "SERVER_IMAGE",
        "WEB_IMAGE",
        "SOURCE_COMMIT"
    )
    $values = Read-CollaEnvironment -EnvPath $EnvPath
    foreach ($name in $required) {
        if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
            throw "Required environment value is missing: $name"
        }
        if ($values[$name] -match '(?i)(replace-with|change-?me|example\.com)') {
            throw "Environment value still contains a placeholder: $name"
        }
    }
    if ($values["JWT_ACCESS_SECRET"].Length -lt 32 -or $values["JWT_REFRESH_SECRET"].Length -lt 32) {
        throw "JWT secrets must contain at least 32 characters"
    }
    foreach ($imageName in @("SERVER_IMAGE", "WEB_IMAGE")) {
        $image = [string] $values[$imageName]
        if ($image -notmatch '^[^\s:]+(?:/[^\s:]+)*:[^\s:]+$' -or $image.EndsWith(":latest")) {
            throw "$imageName must use an explicit immutable release tag"
        }
    }
    if ([string] $values["SOURCE_COMMIT"] -notmatch '^[0-9a-fA-F]{40}$') {
        throw "SOURCE_COMMIT must be a full 40-character Git commit"
    }
    return $values
}

function Read-CollaBackupManifest {
    param(
        [Parameter(Mandatory = $true)][string] $BackupPath,
        [switch] $VerifyFiles
    )

    $backupDir = (Resolve-Path -LiteralPath $BackupPath).Path
    $manifestPath = Join-Path $backupDir "manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Backup manifest not found: $manifestPath"
    }

    try {
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    } catch {
        throw "Backup manifest is invalid JSON: $($_.Exception.Message)"
    }
    if (-not ($manifest.PSObject.Properties.Name -contains "manifestVersion") -or $manifest.manifestVersion -ne 2) {
        throw "Unsupported backup manifest version; expected version 2"
    }
    if (-not ($manifest.PSObject.Properties.Name -contains "projectName") -or [string]::IsNullOrWhiteSpace($manifest.projectName)) {
        throw "Backup manifest does not identify its source project"
    }
    if (-not ($manifest.PSObject.Properties.Name -contains "files") -or @($manifest.files).Count -eq 0) {
        throw "Backup manifest has no files"
    }

    $seenNames = @{}
    foreach ($file in @($manifest.files)) {
        $name = [string] $file.name
        if ([string]::IsNullOrWhiteSpace($name) -or
            [System.IO.Path]::IsPathRooted($name) -or
            [System.IO.Path]::GetFileName($name) -ne $name) {
            throw "Backup manifest contains an unsafe file name: $name"
        }
        if ($seenNames.ContainsKey($name)) {
            throw "Backup manifest contains a duplicate file name: $name"
        }
        $seenNames[$name] = $true
        $filePath = Assert-CollaPathWithin -Parent $backupDir -Candidate (Join-Path $backupDir $name) -Description "backup file"
        if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
            throw "Backup file is missing: $filePath"
        }
        $item = Get-Item -LiteralPath $filePath
        if ($item.Length -ne [long] $file.bytes) {
            throw "Backup size mismatch for $name. Expected $($file.bytes), got $($item.Length)"
        }
        if ($VerifyFiles) {
            $actualHash = Get-CollaSha256 -Path $filePath
            if ($actualHash -ne ([string] $file.sha256).ToUpperInvariant()) {
                throw "Backup hash mismatch for $name. Expected $($file.sha256), got $actualHash"
            }
        }
    }
    if (-not $seenNames.ContainsKey("postgres.sql")) {
        throw "Backup manifest does not contain postgres.sql"
    }
    return $manifest
}

function Get-CollaDatabaseCounts {
    param(
        [Parameter(Mandatory = $true)][string] $ComposePath,
        [Parameter(Mandatory = $true)][string] $EnvPath
    )

    $sql = @"
select json_build_object(
  'users', (select count(*) from users),
  'workspaces', (select count(*) from workspaces),
  'projects', (select count(*) from projects),
  'bases', (select count(*) from bases),
  'knowledge_base_spaces', (select count(*) from knowledge_base_spaces),
  'audit_logs', (select count(*) from audit_logs)
)::text;
"@
    $lines = $sql | & docker compose --env-file $EnvPath -f $ComposePath exec -T postgres sh -c `
        'psql -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" "$POSTGRES_DB"'
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to query key database counts"
    }
    $json = $lines | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1
    if ([string]::IsNullOrWhiteSpace($json)) {
        throw "Database count query returned no JSON"
    }
    return $json | ConvertFrom-Json
}

function Get-CollaFlywayVersion {
    param(
        [Parameter(Mandatory = $true)][string] $ComposePath,
        [Parameter(Mandatory = $true)][string] $EnvPath
    )

    $query = "select coalesce(max(version), '') from flyway_schema_history where success"
    $value = $query | & docker compose --env-file $EnvPath -f $ComposePath exec -T postgres sh -c `
        'psql -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" "$POSTGRES_DB"'
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to query Flyway version"
    }
    return ([string] ($value | Select-Object -Last 1)).Trim()
}

function Get-CollaMinioObjectCount {
    param(
        [Parameter(Mandatory = $true)][string] $ComposePath,
        [Parameter(Mandatory = $true)][string] $EnvPath,
        [string] $BackupHelperImage = "alpine:3.20@sha256:d9e853e87e55526f6b2917df91a2115c36dd7c696a35be12163d44e6e2a4b6bc"
    )

    $container = (& docker compose --env-file $EnvPath -f $ComposePath ps -q minio) -join ""
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($container)) {
        throw "Unable to resolve running MinIO container"
    }
    $value = & docker run --rm --volumes-from $container $BackupHelperImage sh -c `
        "find /data -type f ! -path '/data/.minio.sys/*' | wc -l"
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to count MinIO objects"
    }
    return [long] ([string] ($value | Select-Object -Last 1)).Trim()
}

function Compare-CollaCounts {
    param(
        [Parameter(Mandatory = $true)] $Expected,
        [Parameter(Mandatory = $true)] $Actual
    )

    foreach ($property in $Expected.PSObject.Properties) {
        $actualProperty = $Actual.PSObject.Properties[$property.Name]
        if ($null -eq $actualProperty -or [long] $actualProperty.Value -ne [long] $property.Value) {
            throw "Restored count mismatch for $($property.Name). Expected $($property.Value), got $($actualProperty.Value)"
        }
    }
}
