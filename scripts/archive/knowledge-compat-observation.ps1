param(
    [string]$Database = "colla_platform",
    [string]$DatabaseUser = "colla",
    [string]$Output = ".local-reports/knowledge-compat-observation.md"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$violations = & rg -n --fixed-strings -e '"/api/docs' -e 'colla://document/' -e '/docs/' "$root/server/src/main/java" "$root/web/src" `
    --glob '!**/knowledge/compat/**' --glob '!**/LegacyKnowledgeContentLocator.tsx' 2>$null
if ($LASTEXITCODE -gt 1) { throw "Source compatibility scan failed" }

$oldReferenceSql = @"
select
  (select count(*) from object_links where object_type='document') +
  (select count(*) from object_recent_accesses where object_type='document') +
  (select count(*) from object_favorites where object_type='document') +
  (select count(*) from notifications where target_type='document') +
  (select count(*) from resource_permissions where resource_type='document') +
  (select count(*) from search_index_entries where object_type='document') +
  (select count(*) from knowledge_subscriptions where target_type='document');
"@
$oldReferences = (& docker exec colla-postgres psql -U $DatabaseUser -d $Database -Atc $oldReferenceSql).Trim()
if ($LASTEXITCODE -ne 0) { throw "Compatibility database scan failed" }

$sourceCount = @($violations).Count
$decision = if ($sourceCount -eq 0 -and [int]$oldReferences -eq 0) { "GO" } else { "NO-GO" }
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
$body = @"
# Knowledge Compatibility Observation

- observed_at: $timestamp
- database: $Database
- internal_old_entry_calls: $sourceCount
- stored_old_references: $oldReferences
- replacement: `/api/knowledge-bases/{spaceId}/items/{itemId}` and `colla://knowledge-content/{itemId}`
- decision: $decision

## Source Findings

$(if ($sourceCount -eq 0) { '- None' } else { ($violations | ForEach-Object { "- ``$_``" }) -join "`n" })

## Recovery Boundary

Set `colla.compat.docs-api-enabled=true` to restore only the edge adapter during the observation window. Never restore retired tables, fields, dual writes, or the old domain model.
"@
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Output) | Out-Null
Set-Content -LiteralPath $Output -Value $body -Encoding utf8
Write-Host "Compatibility decision: $decision"
Write-Host "Observation report: $Output"
if ($decision -ne "GO") { exit 1 }
