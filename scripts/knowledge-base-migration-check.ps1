param(
    [string] $OutputDir = ".local-reports",
    [string] $HostName = "localhost",
    [int] $Port = 5432,
    [string] $Database = "colla_platform",
    [string] $Username = "colla",
    [string] $DbPassword = $null,
    [string] $PsqlPath = "psql",
    [string] $DockerPostgresContainer = "colla-postgres"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedOutputDir "kb-migration-check-$Timestamp.md"
$RollbackPath = Join-Path $ResolvedOutputDir "kb-migration-rollback-template-$Timestamp.sql"

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null

$Checks = New-Object System.Collections.Generic.List[object]
$ProbeAvailable = $false
$UseDockerPsql = $false
if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $resolvedPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "colla_dev_password" }
    Set-Variable -Name DbPassword -Value $resolvedPassword
}

function Add-Check {
    param(
        [string] $Key,
        [string] $Name,
        [ValidateSet("PASS", "WARN", "FAIL", "SKIP")]
        [string] $Status,
        [string] $Evidence
    )
    $Checks.Add([pscustomobject]@{ Key = $Key; Name = $Name; Status = $Status; Evidence = $Evidence }) | Out-Null
}

function Invoke-Scalar {
    param([string] $Sql)

    if ($UseDockerPsql) {
        $result = & docker exec -e "PGPASSWORD=$DbPassword" $DockerPostgresContainer psql -U $Username -d $Database -X -A -t -v ON_ERROR_STOP=1 -c $Sql 2>&1
    } else {
        $env:PGPASSWORD = $DbPassword
        $result = & $PsqlPath -h $HostName -p $Port -U $Username -d $Database -X -A -t -v ON_ERROR_STOP=1 -c $Sql 2>&1
    }
    if ($LASTEXITCODE -ne 0) {
        throw ($result -join [Environment]::NewLine)
    }
    return (($result | Select-Object -First 1).ToString()).Trim()
}

function Invoke-CountCheck {
    param(
        [string] $Key,
        [string] $Name,
        [string] $Sql,
        [ValidateSet("zero-pass", "nonzero-pass")]
        [string] $Expectation = "zero-pass",
        [string] $EvidenceSuffix = ""
    )

    if (-not $ProbeAvailable) {
        Add-Check $Key $Name "SKIP" "Database probe unavailable; run again with psql and a reachable PostgreSQL database."
        return
    }

    try {
        $value = [int](Invoke-Scalar $Sql)
        $ok = if ($Expectation -eq "zero-pass") { $value -eq 0 } else { $value -gt 0 }
        $status = if ($ok) { "PASS" } else { "WARN" }
        $evidence = "count=$value"
        if (-not [string]::IsNullOrWhiteSpace($EvidenceSuffix)) {
            $evidence = "$evidence; $EvidenceSuffix"
        }
        Add-Check $Key $Name $status $evidence
    } catch {
        Add-Check $Key $Name "FAIL" $_.Exception.Message.Replace("|", "\|")
    }
}

if (Get-Command $PsqlPath -ErrorAction SilentlyContinue) {
    try {
        [void](Invoke-Scalar "select 1;")
        $ProbeAvailable = $true
    } catch {
        Add-Check "database-connectivity" "Database connectivity" "WARN" $_.Exception.Message.Replace("|", "\|")
    }
} elseif ((Get-Command docker -ErrorAction SilentlyContinue) -and ((docker ps --format "{{.Names}}" 2>$null) -contains $DockerPostgresContainer)) {
    try {
        $UseDockerPsql = $true
        [void](Invoke-Scalar "select 1;")
        $ProbeAvailable = $true
        Add-Check "docker-psql" "Docker psql fallback" "PASS" "Using container $DockerPostgresContainer."
    } catch {
        Add-Check "docker-psql" "Docker psql fallback" "WARN" $_.Exception.Message.Replace("|", "\|")
    }
} else {
    Add-Check "psql" "psql client" "WARN" "Command '$PsqlPath' not found and Docker container '$DockerPostgresContainer' is not running."
}

Invoke-CountCheck `
    -Key "legacy-space-without-kb-space" `
    -Name "Legacy knowledge-base root content nodes without space rows" `
    -Sql @"
select count(*)
from documents d
where d.doc_type = 'space'
  and d.knowledge_base = true
  and d.deleted_at is null
  and not exists (
      select 1
      from knowledge_base_spaces k
      where k.workspace_id = d.workspace_id
        and k.root_document_id = d.id
        and k.deleted_at is null
  );
"@ `
    -EvidenceSuffix "V038 should map legacy knowledge-base root content nodes into knowledge_base_spaces."

Invoke-CountCheck `
    -Key "kb-space-without-root-document" `
    -Name "Knowledge-base spaces without active root directory content node" `
    -Sql @"
select count(*)
from knowledge_base_spaces k
where k.deleted_at is null
  and not exists (
      select 1
      from documents d
      where d.workspace_id = k.workspace_id
        and d.id = k.root_document_id
        and d.doc_type = 'space'
        and d.deleted_at is null
  );
"@

Invoke-CountCheck `
    -Key "kb-space-without-home-document" `
    -Name "Knowledge-base spaces without active home content node" `
    -Sql @"
select count(*)
from knowledge_base_spaces k
where k.deleted_at is null
  and not exists (
      select 1
      from documents d
      where d.workspace_id = k.workspace_id
        and d.id = k.home_document_id
        and d.deleted_at is null
  );
"@

Invoke-CountCheck `
    -Key "kb-root-metadata-shadow-fields" `
    -Name "Knowledge-base root content nodes still carrying deprecated space metadata" `
    -Sql @"
select count(*)
from knowledge_base_spaces k
join documents d
  on d.workspace_id = k.workspace_id
 and d.id = k.root_document_id
 and d.deleted_at is null
where k.deleted_at is null
  and (
      d.description is not null
      or d.cover_url is not null
      or d.default_permission_level <> 'view'
  );
"@ `
    -EvidenceSuffix "New knowledge-base settings must live in knowledge_base_spaces; these root content node fields are compatibility shadows only."

Invoke-CountCheck `
    -Key "kb-root-compat-flag-missing" `
    -Name "Knowledge-base root content nodes missing legacy compatibility flag" `
    -Sql @"
select count(*)
from knowledge_base_spaces k
join documents d
  on d.workspace_id = k.workspace_id
 and d.id = k.root_document_id
 and d.deleted_at is null
where k.deleted_at is null
  and d.knowledge_base = false;
"@ `
    -EvidenceSuffix "The flag is not authoritative, but keeping it true preserves old deep-link and migration compatibility."

Invoke-CountCheck `
    -Key "orphan-document-parent" `
    -Name "Knowledge content nodes with missing active parent" `
    -Sql @"
select count(*)
from documents d
where d.deleted_at is null
  and d.parent_id is not null
  and not exists (
      select 1
      from documents p
      where p.id = d.parent_id
        and p.workspace_id = d.workspace_id
        and p.deleted_at is null
  );
"@

Invoke-CountCheck `
    -Key "document-without-owner-permission" `
    -Name "Active knowledge content nodes without owner permission" `
    -Sql @"
select count(*)
from documents d
where d.deleted_at is null
  and not exists (
      select 1
      from resource_permissions rp
      where rp.workspace_id = d.workspace_id
        and rp.resource_type = 'document'
        and rp.resource_id = d.id
        and rp.permission_level = 'owner'
        and rp.status = 'active'
        and (rp.expires_at is null or rp.expires_at > now())
  );
"@

Invoke-CountCheck `
    -Key "kb-without-owner-permission" `
    -Name "Knowledge bases without active owner permission" `
    -Sql @"
select count(*)
from knowledge_base_spaces k
where k.deleted_at is null
  and not exists (
      select 1
      from resource_permissions rp
      where rp.workspace_id = k.workspace_id
        and rp.resource_type = 'knowledge_base'
        and rp.resource_id = k.id
        and rp.permission_level = 'owner'
        and rp.status = 'active'
        and (rp.expires_at is null or rp.expires_at > now())
  );
"@

Invoke-CountCheck `
    -Key "legacy-document-permission-backfill-gap" `
    -Name "Active legacy content permissions not backfilled to resource_permissions" `
    -Sql @"
select count(*)
from document_permissions dp
join documents d
  on d.workspace_id = dp.workspace_id
 and d.id = dp.document_id
 and d.deleted_at is null
where dp.revoked_at is null
  and not exists (
      select 1
      from resource_permissions rp
      where rp.workspace_id = dp.workspace_id
        and rp.resource_type = 'document'
        and rp.resource_id = dp.document_id
        and rp.subject_type = dp.subject_type
        and rp.subject_id = dp.subject_id
        and rp.permission_level = dp.permission_level
        and rp.status = 'active'
        and (rp.expires_at is null or rp.expires_at > now())
  );
"@

Invoke-CountCheck `
    -Key "inherited-permission-with-missing-source" `
    -Name "Inherited permissions with missing source object" `
    -Sql @"
select count(*)
from resource_permissions rp
left join documents sd
  on sd.workspace_id = rp.workspace_id
 and sd.id = rp.source_id
 and sd.deleted_at is null
left join knowledge_base_spaces k
  on k.workspace_id = rp.workspace_id
 and k.id = rp.source_id
 and k.deleted_at is null
where rp.status = 'active'
  and rp.source_type = 'inherited'
  and rp.source_id is not null
  and sd.id is null
  and k.id is null;
"@

Invoke-CountCheck `
    -Key "kb-document-search-index-gap" `
    -Name "Knowledge-base content nodes missing search index rows" `
    -Sql @"
with recursive kb_docs as (
    select k.id as knowledge_base_id, d.workspace_id, d.id, d.parent_id
    from knowledge_base_spaces k
    join documents d on d.workspace_id = k.workspace_id and d.id = k.root_document_id
    where k.deleted_at is null and d.deleted_at is null
    union all
    select kd.knowledge_base_id, c.workspace_id, c.id, c.parent_id
    from documents c
    join kb_docs kd on kd.workspace_id = c.workspace_id and kd.id = c.parent_id
    where c.deleted_at is null
)
select count(*)
from kb_docs kd
where not exists (
    select 1
    from search_index_documents si
    where si.workspace_id = kd.workspace_id
      and si.object_type = 'document'
      and si.object_id = kd.id
);
"@

Invoke-CountCheck `
    -Key "search-index-deleted-document" `
    -Name "Search index rows pointing to deleted or missing knowledge content nodes" `
    -Sql @"
select count(*)
from search_index_documents si
left join documents d
  on d.workspace_id = si.workspace_id
 and d.id = si.object_id
 and d.deleted_at is null
where si.object_type = 'document'
  and d.id is null;
"@

Invoke-CountCheck `
    -Key "kb-search-index-context-gap" `
    -Name "Knowledge-base index rows without knowledge context" `
    -Sql @"
with recursive kb_docs as (
    select k.id as knowledge_base_id, d.workspace_id, d.id
    from knowledge_base_spaces k
    join documents d on d.workspace_id = k.workspace_id and d.id = k.root_document_id
    where k.deleted_at is null and d.deleted_at is null
    union all
    select kd.knowledge_base_id, c.workspace_id, c.id
    from documents c
    join kb_docs kd on kd.workspace_id = c.workspace_id and kd.id = c.parent_id
    where c.deleted_at is null
)
select count(*)
from kb_docs kd
join search_index_documents si
  on si.workspace_id = kd.workspace_id
 and si.object_type = 'document'
 and si.object_id = kd.id
where si.knowledge_base_id is distinct from kd.knowledge_base_id;
"@

Invoke-CountCheck `
    -Key "block-coverage-gap" `
    -Name "Active markdown content nodes without active blocks" `
    -Sql @"
select count(*)
from documents d
where d.deleted_at is null
  and d.archived_at is null
  and d.doc_type = 'markdown'
  and coalesce(d.content, '') <> ''
  and not exists (
      select 1
      from document_blocks b
      where b.workspace_id = d.workspace_id
        and b.document_id = d.id
        and b.deleted_at is null
  );
"@ `
    -EvidenceSuffix "New content should be readable from document_blocks; content-only rows require projection before block editor cutover."

Invoke-CountCheck `
    -Key "legacy-html-blocks" `
    -Name "Legacy HTML blocks requiring manual review" `
    -Sql @"
select count(*)
from document_blocks b
join documents d on d.workspace_id = b.workspace_id and d.id = b.document_id
where b.deleted_at is null
  and d.deleted_at is null
  and b.block_type = 'legacy_html';
"@ `
    -EvidenceSuffix "legacy_html preserves unconverted content but should trend down before final removal of old rich-text compatibility."

Invoke-CountCheck `
    -Key "empty-content-blocks" `
    -Name "Non-structural blocks with empty text and empty JSON payload" `
    -Sql @"
select count(*)
from document_blocks b
join documents d on d.workspace_id = b.workspace_id and d.id = b.document_id
where b.deleted_at is null
  and d.deleted_at is null
  and b.block_type not in ('divider', 'toc')
  and coalesce(b.plain_text, '') = ''
  and coalesce(b.content, '') = ''
  and coalesce(b.rich_content, '{}'::jsonb) = '{}'::jsonb;
"@

Invoke-CountCheck `
    -Key "orphan-document-block-parent" `
    -Name "Blocks whose parent_id does not point to an active block in the same document" `
    -Sql @"
select count(*)
from document_blocks b
where b.deleted_at is null
  and b.parent_id is not null
  and not exists (
      select 1
      from document_blocks p
      where p.workspace_id = b.workspace_id
        and p.document_id = b.document_id
        and p.id = b.parent_id
        and p.deleted_at is null
  );
"@

Invoke-CountCheck `
    -Key "document-block-sort-conflict" `
    -Name "Active blocks sharing the same parent and sort order" `
    -Sql @"
select count(*)
from (
    select workspace_id, document_id, parent_id, sort_order
    from document_blocks
    where deleted_at is null
    group by workspace_id, document_id, parent_id, sort_order
    having count(*) > 1
) conflicts;
"@

Invoke-CountCheck `
    -Key "invalid-embedded-object-blocks" `
    -Name "Embedded object blocks missing normalized object type or object id" `
    -Sql @"
select count(*)
from document_blocks b
join documents d on d.workspace_id = b.workspace_id and d.id = b.document_id
where b.deleted_at is null
  and d.deleted_at is null
  and b.block_type in ('embed', 'embed_object', 'base_view', 'issue_embed', 'message_embed', 'file_embed', 'link', 'link_card')
  and (
      coalesce(b.content, '') !~ '"objectType"\s*:'
      or coalesce(b.content, '') !~ '"objectId"\s*:'
  );
"@ `
    -EvidenceSuffix "Embedded blocks must degrade safely during export and must not point to unknown targets after migration."

Invoke-CountCheck `
    -Key "old-rich-text-active-coverage" `
    -Name "Active markdown content nodes still carrying old rich text alongside blocks" `
    -Sql @"
select count(*)
from documents d
where d.deleted_at is null
  and d.archived_at is null
  and d.doc_type = 'markdown'
  and coalesce(d.content, '') <> ''
  and exists (
      select 1
      from document_blocks b
      where b.workspace_id = d.workspace_id
        and b.document_id = d.id
        and b.deleted_at is null
  );
"@ `
    -Expectation "nonzero-pass" `
    -EvidenceSuffix "This tracks rollback coverage while blocks become primary; remove only after v2 freeze approves old content retirement."

$failureCount = @($Checks | Where-Object { $_.Status -eq "FAIL" }).Count
$warningCount = @($Checks | Where-Object { $_.Status -eq "WARN" }).Count
$skipCount = @($Checks | Where-Object { $_.Status -eq "SKIP" }).Count

$decision = if ($failureCount -gt 0) {
    "NO-GO"
} elseif (($warningCount + $skipCount) -gt 0) {
    "GO-WITH-REVIEW"
} else {
    "GO"
}

$rollbackSql = @"
-- Knowledge-base migration rollback template
-- Generated: $(Get-Date -Format o)
-- This file is intentionally not executed by the check script.
-- Review counts, set the placeholders, run inside a maintenance window, and keep a backup.

\set workspace_id '00000000-0000-0000-0000-000000000000'
\set cutover_started_at '2026-06-30T00:00:00+08:00'

begin;

-- Preview rows that would be retired.
select id, name, code, root_document_id, created_at
from knowledge_base_spaces
where workspace_id = :'workspace_id'::uuid
  and deleted_at is null
  and created_at >= :'cutover_started_at'::timestamptz;

-- Retire migrated knowledge-base spaces while preserving rows for audit.
update knowledge_base_spaces
set status = 'archived',
    deleted_at = now(),
    updated_at = now()
where workspace_id = :'workspace_id'::uuid
  and deleted_at is null
  and created_at >= :'cutover_started_at'::timestamptz;

-- Optional: revert only root document product flag for spaces created during cutover.
update documents d
set knowledge_base = false,
    updated_at = now()
where d.workspace_id = :'workspace_id'::uuid
  and d.id in (
      select root_document_id
      from knowledge_base_spaces
      where workspace_id = :'workspace_id'::uuid
        and created_at >= :'cutover_started_at'::timestamptz
  );

-- Keep search_index_documents and resource_permissions intact unless a separate audited cleanup is approved.

-- Optional block rollback preview: regenerate active blocks from document content for documents edited during cutover.
-- This is a destructive replacement and must not run without a tested projection script.
select d.id, d.title, count(b.id) active_block_count
from documents d
left join document_blocks b on b.workspace_id = d.workspace_id and b.document_id = d.id and b.deleted_at is null
where d.workspace_id = :'workspace_id'::uuid
  and d.updated_at >= :'cutover_started_at'::timestamptz
group by d.id, d.title
order by d.title;

rollback;
"@
Set-Content -LiteralPath $RollbackPath -Value $rollbackSql -Encoding UTF8

$report = @(
    "# Knowledge Base Migration Check",
    "",
    "- Time: $(Get-Date -Format o)",
    "- Decision: $decision",
    "- Database: $HostName`:$Port/$Database",
    "- Probe available: $ProbeAvailable",
    "- Rollback template: $RollbackPath",
    "",
    "## Checks",
    "",
    "| Key | Check | Status | Evidence |",
    "| --- | --- | --- | --- |"
)
foreach ($check in $Checks) {
    $report += "| $($check.Key) | $($check.Name) | $($check.Status) | $($check.Evidence) |"
}
$report += @(
    "",
    "## Migration Flow",
    "",
    "1. Run this script against the source database and resolve FAIL/WARN rows before cutover.",
    "2. Back up PostgreSQL and MinIO with `pnpm ops:backup`.",
    "3. Apply Flyway migrations from an empty database and from a copy of production data.",
    "4. Execute `POST /api/search/reindex` after cutover so knowledge-base context is refreshed.",
    "5. Re-run this script and require `Decision: GO` or an approved `GO-WITH-REVIEW` exception.",
    "6. Keep the generated rollback template with the backup manifest; execute it only after explicit approval.",
    "",
    "## Rollback Policy",
    "",
    "- Rollback must be paired with a verified backup and an audit note.",
    "- The template retires knowledge-base space rows instead of hard-deleting them.",
    "- Search index and permission rows are preserved by default to avoid losing access history."
)

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Knowledge-base migration check report: $ReportPath"
Write-Host "Rollback template: $RollbackPath"
Write-Host "Decision: $decision"

if ($failureCount -gt 0) {
    exit 1
}
