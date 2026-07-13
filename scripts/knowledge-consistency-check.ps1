param(
    [string] $Container = "colla-postgres",
    [string] $Database = "colla_platform",
    [string] $DatabaseUser = "colla",
    [string] $OutputDir = ".local-reports"
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedOutputDir "knowledge-consistency-$Timestamp.md"
$JsonPath = Join-Path $ResolvedOutputDir "knowledge-consistency-$Timestamp.json"
$Checks = New-Object System.Collections.Generic.List[object]

function Invoke-Scalar {
    param([string] $Sql)

    $value = & docker exec $Container psql -U $DatabaseUser -d $Database -v ON_ERROR_STOP=1 -Atc $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "Knowledge consistency query failed."
    }
    return [int](($value | Out-String).Trim())
}

function Add-ZeroCheck {
    param(
        [string] $Name,
        [string] $Sql,
        [ValidateSet("critical", "high", "medium", "low")] [string] $Severity,
        [string] $Remediation
    )

    $count = Invoke-Scalar $Sql
    $status = if ($count -eq 0) { "PASS" } else { "FAIL" }
    $Checks.Add([pscustomobject]@{
        name = $Name
        status = $status
        count = $count
        severity = $Severity
        remediation = $Remediation
    }) | Out-Null
    Write-Host "[$status][$Severity] $Name ($count)"
}

function Write-Reports {
    param([string] $Decision, [int] $ExitCode, [string] $ExecutionError = "")

    $result = [ordered]@{
        checkedAt = (Get-Date -Format o)
        database = $Database
        decision = $Decision
        exitCode = $ExitCode
        executionError = $ExecutionError
        checks = $Checks.ToArray()
    }
    $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $JsonPath -Encoding utf8

    $lines = @(
        "# Knowledge Consistency Check",
        "",
        "- Checked at: $($result.checkedAt)",
        "- Database: $Database",
        "- Decision: $Decision",
        "- Exit code: $ExitCode (0=pass, 2=data inconsistency, 1=execution failure)",
        "- JSON output: $JsonPath",
        "",
        "| Check | Status | Count | Risk | Remediation |",
        "| --- | --- | ---: | --- | --- |"
    )
    foreach ($check in $Checks) {
        $lines += "| $($check.name) | $($check.status) | $($check.count) | $($check.severity) | $($check.remediation) |"
    }
    if ($ExecutionError) {
        $lines += ""
        $lines += "## Execution Error"
        $lines += ""
        $lines += $ExecutionError
    }
    $lines | Set-Content -LiteralPath $ReportPath -Encoding utf8
    Write-Host "Knowledge consistency reports: $ReportPath; $JsonPath"
}

try {
    New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker is not available."
    }
    if ((docker ps --format "{{.Names}}") -notcontains $Container) {
        throw "PostgreSQL container '$Container' is not running."
    }

    Add-ZeroCheck "Space root or home target is missing" @"
select count(*) from knowledge_base_spaces s
where s.deleted_at is null and (
  not exists (select 1 from knowledge_base_items i where i.workspace_id=s.workspace_id and i.id=s.root_item_id and i.deleted_at is null)
  or not exists (select 1 from knowledge_base_items i where i.workspace_id=s.workspace_id and i.id=s.home_item_id and i.deleted_at is null)
);
"@ "critical" "Restore the missing space node from backup before serving the space."

    Add-ZeroCheck "Active item has a missing parent" @"
select count(*) from knowledge_base_items i
where i.deleted_at is null and i.parent_id is not null
and not exists (select 1 from knowledge_base_items p where p.workspace_id=i.workspace_id and p.id=i.parent_id and p.deleted_at is null);
"@ "high" "Repair the parent relationship or soft-delete the detached node after backup."

    Add-ZeroCheck "Editable markdown item has no active block" @"
select count(*) from knowledge_base_items i
where i.deleted_at is null and i.archived_at is null and i.content_type='markdown'
and not exists (select 1 from knowledge_content_blocks b where b.workspace_id=i.workspace_id and b.item_id=i.id and b.deleted_at is null);
"@ "high" "Restore canonical blocks from the latest version snapshot."

    Add-ZeroCheck "Active block has no active item" @"
select count(*) from knowledge_content_blocks b
where b.deleted_at is null
and not exists (select 1 from knowledge_base_items i where i.workspace_id=b.workspace_id and i.id=b.item_id and i.deleted_at is null);
"@ "high" "Soft-delete orphan blocks only after their owning item is confirmed absent."

    Add-ZeroCheck "Block parent is invalid" @"
select count(*) from knowledge_content_blocks b
where b.deleted_at is null and b.parent_id is not null
and not exists (select 1 from knowledge_content_blocks p where p.workspace_id=b.workspace_id and p.item_id=b.item_id and p.id=b.parent_id and p.deleted_at is null);
"@ "medium" "Reattach the block to a valid parent and preserve sort order."

    Add-ZeroCheck "Sibling block sort order is duplicated" @"
select count(*) from (
  select workspace_id,item_id,parent_id,sort_order from knowledge_content_blocks
  where deleted_at is null group by workspace_id,item_id,parent_id,sort_order having count(*) > 1
) x;
"@ "medium" "Reorder affected siblings through the canonical blocks API."

    Add-ZeroCheck "Version has no active item" @"
select count(*) from knowledge_content_versions v
where not exists (select 1 from knowledge_base_items i where i.workspace_id=v.workspace_id and i.id=v.item_id and i.deleted_at is null);
"@ "medium" "Retain only versions recoverable through an active item, or restore the item first."

    Add-ZeroCheck "Knowledge permission has no active resource" @"
select count(*) from resource_permissions rp
where rp.status='active' and rp.resource_type='knowledge_content'
and not exists (select 1 from knowledge_base_items i where i.workspace_id=rp.workspace_id and i.id=rp.resource_id and i.deleted_at is null);
"@ "high" "Deactivate orphan grants so access decisions cannot resolve stale resources."

    Add-ZeroCheck "Knowledge search row has no active item" @"
select count(*) from search_index_entries s
where s.object_type='knowledge_content'
and not exists (select 1 from knowledge_base_items i where i.workspace_id=s.workspace_id and i.id=s.object_id and i.deleted_at is null);
"@ "high" "Remove stale index rows and rebuild the affected workspace search index."

    Add-ZeroCheck "Active item is missing its knowledge search row" @"
select count(*) from knowledge_base_items i
where i.deleted_at is null
and not exists (select 1 from search_index_entries s where s.workspace_id=i.workspace_id and s.object_type='knowledge_content' and s.object_id=i.id);
"@ "medium" "Rebuild the affected workspace search index."

    Add-ZeroCheck "Object entry has an incomplete target" @"
select count(*) from knowledge_base_items i
where i.deleted_at is null and i.item_kind in ('object_ref','external_link')
and (i.target_object_type is null or (i.item_kind='object_ref' and i.target_object_id is null) or (i.item_kind='external_link' and coalesce(i.target_route,'')=''));
"@ "high" "Repair the target metadata, or remove the invalid entry after backup."

    Add-ZeroCheck "Knowledge content reference uses a non-canonical route" @"
select count(*) from knowledge_base_items i
where i.deleted_at is null and i.item_kind='object_ref' and i.target_object_type='knowledge_content'
and coalesce(i.target_route,'') !~ '^/knowledge-bases/[^/]+/items/[^/]+$';
"@ "medium" "Recompute the route from the active target; remove the entry if the target is absent."

    Add-ZeroCheck "Knowledge content reference target is missing" @"
select count(*) from knowledge_base_items r
where r.deleted_at is null and r.item_kind='object_ref' and r.target_object_type='knowledge_content'
and not exists (
  select 1 from knowledge_base_items t
  where t.workspace_id=r.workspace_id and t.id=r.target_object_id and t.deleted_at is null
);
"@ "high" "Use repair-knowledge-reference.ps1 preview, validate the backup, then explicitly soft-delete the orphan entry."

    Add-ZeroCheck "Retired document compatibility remains active" @"
select
  (select count(*) from resource_permissions where resource_type='document') +
  (select count(*) from search_index_entries where object_type='document') +
  (select count(*) from information_schema.tables where table_schema='public' and table_name in ('documents','document_blocks','document_permissions','knowledge_item_permissions'));
"@ "high" "Complete the V047 retirement path; no active document compatibility model may remain."

    $failures = @($Checks | Where-Object status -eq "FAIL").Count
    $exitCode = if ($failures -eq 0) { 0 } else { 2 }
    Write-Reports -Decision $(if ($failures -eq 0) { "PASS" } else { "FAIL" }) -ExitCode $exitCode
    exit $exitCode
} catch {
    try {
        New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null
        Write-Reports -Decision "ERROR" -ExitCode 1 -ExecutionError $_.Exception.Message
    } catch {
        Write-Error $_.Exception.Message
    }
    Write-Error $_.Exception.Message
    exit 1
}
