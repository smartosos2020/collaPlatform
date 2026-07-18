param(
    [Parameter(Mandatory = $true)] [Guid] $ReferenceId,
    [ValidateSet("preview", "repair")] [string] $Action = "preview",
    [string] $Container = "colla-postgres",
    [string] $Database = "colla_platform",
    [string] $DatabaseUser = "colla",
    [string] $BackupPath = "",
    [switch] $CreateBackup,
    [switch] $Confirm,
    [string] $OutputDir = ".local-reports"
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$ReportPath = Join-Path $ResolvedOutputDir "knowledge-reference-repair-$Timestamp.md"
$JsonPath = Join-Path $ResolvedOutputDir "knowledge-reference-repair-$Timestamp.json"

function Invoke-Psql {
    param([string] $Sql, [switch] $TuplesOnly)

    $arguments = @("exec", $Container, "psql", "-U", $DatabaseUser, "-d", $Database, "-v", "ON_ERROR_STOP=1")
    if ($TuplesOnly) { $arguments += @("-At") }
    $arguments += @("-c", $Sql)
    $result = & docker @arguments
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed." }
    return ($result | Out-String).Trim()
}

function Invoke-Scalar {
    param([string] $Sql)
    return [int](Invoke-Psql -Sql $Sql -TuplesOnly)
}

function Get-ReferenceSnapshot {
    param([Guid] $Id)

    $id = $Id.ToString()
    $reference = Invoke-Psql -TuplesOnly -Sql @"
select row_to_json(x)::text
from (
  select id, workspace_id, parent_id, title, item_kind, content_type, target_object_type,
         target_object_id, target_route, created_by, created_at, updated_at, deleted_at
  from knowledge_base_items
  where id = '$id'::uuid
) x;
"@
    if ([string]::IsNullOrWhiteSpace($reference)) {
        throw "Knowledge reference '$id' does not exist."
    }
    $referenceObject = $reference | ConvertFrom-Json
    $targetExists = if ($referenceObject.target_object_id) {
        Invoke-Scalar @"
select count(*) from knowledge_base_items
where workspace_id = '$($referenceObject.workspace_id)'::uuid
  and id = '$($referenceObject.target_object_id)'::uuid
  and deleted_at is null;
"@
    } else { 0 }
    $dependencySql = @"
select json_object_agg(source, count) from (
  select 'blocks' as source, count(*)::int as count from knowledge_content_blocks where item_id='$id'::uuid and deleted_at is null
  union all select 'versions', count(*)::int from knowledge_content_versions where item_id='$id'::uuid
  union all select 'permissions', count(*)::int from resource_permissions where resource_type='knowledge_content' and resource_id='$id'::uuid and status='active'
  union all select 'share_links', count(*)::int from knowledge_item_share_links where item_id='$id'::uuid and enabled
  union all select 'subscriptions', count(*)::int from knowledge_subscriptions where target_type='knowledge_content' and target_id='$id'::uuid and deleted_at is null
  union all select 'search', count(*)::int from search_index_entries where object_type='knowledge_content' and object_id='$id'::uuid
  union all select 'recent', count(*)::int from object_recent_accesses where object_type='knowledge_content' and object_id='$id'::uuid
  union all select 'favorites', count(*)::int from object_favorites where object_type='knowledge_content' and object_id='$id'::uuid
  union all select 'object_links', count(*)::int from object_links where object_type='knowledge_content' and object_id='$id'::uuid
  union all select 'notifications', count(*)::int from notifications where target_type='knowledge_content' and target_id='$id'::uuid
  union all select 'relations', count(*)::int from knowledge_item_relations where item_id='$id'::uuid and deleted_at is null
) dependencies;
"@
    $dependencies = Invoke-Psql -TuplesOnly -Sql $dependencySql | ConvertFrom-Json
    return [ordered]@{
        reference = $referenceObject
        targetExists = ($targetExists -gt 0)
        dependencies = $dependencies
        repairEligible = ($referenceObject.deleted_at -eq $null -and $referenceObject.item_kind -eq "object_ref" -and $referenceObject.target_object_type -eq "knowledge_content" -and $referenceObject.target_object_id -and $targetExists -eq 0)
    }
}

function New-ScopedBackup {
    param([string] $RequestedPath)

    $backupDirectory = Join-Path $Root ".local-backups"
    New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
    $path = if ([string]::IsNullOrWhiteSpace($RequestedPath)) { Join-Path $backupDirectory "knowledge-reference-repair-$Timestamp.dump" } elseif ([System.IO.Path]::IsPathRooted($RequestedPath)) { $RequestedPath } else { Join-Path $Root $RequestedPath }
    $containerPath = "/tmp/knowledge-reference-repair-$Timestamp.dump"
    & docker exec $Container pg_dump -U $DatabaseUser --format=custom --file=$containerPath $Database
    if ($LASTEXITCODE -ne 0) { throw "Database backup creation failed." }
    & docker cp "$Container`:$containerPath" $path
    & docker exec $Container rm -f $containerPath | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $path) -or (Get-Item $path).Length -le 0) { throw "Database backup validation failed: dump file is missing or empty." }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
    $manifest = [ordered]@{ backupPath = (Resolve-Path $path).Path; sha256 = $hash; createdAt = (Get-Date -Format o); database = $Database; referenceId = $ReferenceId.ToString() }
    $manifestPath = "$path.manifest.json"
    $manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding utf8
    return [ordered]@{ path = $manifest.backupPath; manifestPath = $manifestPath; sha256 = $hash; validated = $true }
}

function Test-ExistingBackup {
    param([string] $RequestedPath)

    if ([string]::IsNullOrWhiteSpace($RequestedPath)) { throw "Repair requires -CreateBackup or an existing -BackupPath." }
    $path = if ([System.IO.Path]::IsPathRooted($RequestedPath)) { $RequestedPath } else { Join-Path $Root $RequestedPath }
    $manifestPath = "$path.manifest.json"
    if (-not (Test-Path $path) -or -not (Test-Path $manifestPath) -or (Get-Item $path).Length -le 0) { throw "Backup validation failed: dump and manifest are both required." }
    $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
    if ($manifest.sha256 -ne $hash -or $manifest.referenceId -ne $ReferenceId.ToString()) { throw "Backup validation failed: checksum or reference id does not match." }
    return [ordered]@{ path = (Resolve-Path $path).Path; manifestPath = $manifestPath; sha256 = $hash; validated = $true }
}

function Invoke-Repair {
    param([Guid] $Id)

    $id = $Id.ToString()
    Invoke-Psql -Sql @"
begin;
update knowledge_base_items set deleted_at = now(), updated_at = now()
where id = '$id'::uuid and deleted_at is null;
update knowledge_content_blocks set deleted_at = now(), updated_at = now()
where item_id = '$id'::uuid and deleted_at is null;
delete from knowledge_content_versions where item_id = '$id'::uuid;
update resource_permissions set status = 'revoked', updated_at = now()
where resource_type = 'knowledge_content' and resource_id = '$id'::uuid and status = 'active';
update knowledge_item_share_links set enabled = false, disabled_at = now(), updated_at = now()
where item_id = '$id'::uuid and enabled;
update knowledge_subscriptions set deleted_at = now()
where target_type = 'knowledge_content' and target_id = '$id'::uuid and deleted_at is null;
update knowledge_item_relations set deleted_at = now()
where item_id = '$id'::uuid and deleted_at is null;
delete from search_index_entries where object_type = 'knowledge_content' and object_id = '$id'::uuid;
delete from object_recent_accesses where object_type = 'knowledge_content' and object_id = '$id'::uuid;
delete from object_favorites where object_type = 'knowledge_content' and object_id = '$id'::uuid;
delete from object_links where object_type = 'knowledge_content' and object_id = '$id'::uuid;
delete from notifications where target_type = 'knowledge_content' and target_id = '$id'::uuid;
commit;
"@ | Out-Null
}

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker is not available." }
if ((docker ps --format "{{.Names}}") -notcontains $Container) { throw "PostgreSQL container '$Container' is not running." }

$snapshot = Get-ReferenceSnapshot -Id $ReferenceId
$backup = $null
$result = [ordered]@{ action = $Action; referenceId = $ReferenceId.ToString(); checkedAt = (Get-Date -Format o); snapshot = $snapshot; backup = $null; applied = $false; outcome = "preview" }

if ($Action -eq "repair") {
    if (-not $snapshot.repairEligible) { throw "Repair refused: the reference is not an active knowledge_content object_ref with a missing target." }
    if (-not $Confirm) { throw "Repair refused: pass -Confirm after reviewing the preview and backup." }
    $backup = if ($CreateBackup) { New-ScopedBackup -RequestedPath $BackupPath } else { Test-ExistingBackup -RequestedPath $BackupPath }
    $result.backup = $backup
    Invoke-Repair -Id $ReferenceId
    $result.applied = $true
    $result.outcome = "repaired"
}

$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $JsonPath -Encoding utf8
$lines = @(
    "# Knowledge Reference Repair",
    "",
    "- Action: $Action",
    "- Reference: $ReferenceId",
    "- Outcome: $($result.outcome)",
    "- Repair eligible: $($snapshot.repairEligible)",
    "- Target exists: $($snapshot.targetExists)",
    "- JSON output: $JsonPath"
)
if ($backup) { $lines += "- Validated backup: $($backup.path)" }
$lines += ""; $lines += "## Dependency Impact"; $lines += ""; $lines += '```json'; $lines += ($snapshot.dependencies | ConvertTo-Json -Compress); $lines += '```'
$lines | Set-Content -LiteralPath $ReportPath -Encoding utf8
Write-Host "Knowledge reference repair report: $ReportPath; $JsonPath"
