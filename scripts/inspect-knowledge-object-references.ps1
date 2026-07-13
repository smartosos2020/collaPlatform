param(
    [string]$Container = "colla-postgres",
    [string]$Database = "colla_platform",
    [string]$User = "colla"
)

$ErrorActionPreference = "Stop"

function Invoke-KbReferenceQuery {
    param([string]$Title, [string]$Sql)

    Write-Host ""
    Write-Host "== $Title =="
    docker exec -i $Container psql -U $User -d $Database -v ON_ERROR_STOP=1 -c $Sql
}

Invoke-KbReferenceQuery "Object reference summary" @"
select item_kind, target_object_type, count(*) as count
from knowledge_base_items
where deleted_at is null
  and item_kind in ('object_ref', 'external_link')
group by item_kind, target_object_type
order by item_kind, target_object_type;
"@

Invoke-KbReferenceQuery "Invalid object reference shape" @"
select id, title, content_type, target_object_type, target_object_id, target_route
from knowledge_base_items
where deleted_at is null
  and (
      (item_kind = 'object_ref' and (target_object_type is null or target_object_id is null or target_route is null))
      or (item_kind = 'external_link' and (target_route is null or target_object_type <> 'external_link'))
  )
order by updated_at desc;
"@

Invoke-KbReferenceQuery "Missing knowledge content targets" @"
select ref.id as reference_id, ref.title as reference_title, ref.target_object_id
from knowledge_base_items ref
left join knowledge_base_items target on target.id = ref.target_object_id
    and target.workspace_id = ref.workspace_id
    and target.deleted_at is null
where ref.deleted_at is null
  and ref.item_kind = 'object_ref'
  and ref.target_object_type = 'knowledge_content'
  and target.id is null
order by ref.updated_at desc;
"@

Invoke-KbReferenceQuery "Duplicate aliases under the same parent" @"
select parent_id, lower(coalesce(entry_alias, title)) as alias_key, count(*) as count,
       string_agg(id::text, ', ' order by updated_at desc) as reference_ids
from knowledge_base_items
where deleted_at is null
  and item_kind in ('object_ref', 'external_link')
group by parent_id, lower(coalesce(entry_alias, title))
having count(*) > 1
order by count desc, alias_key;
"@

Invoke-KbReferenceQuery "Repeated target references" @"
select target_object_type, target_object_id, count(*) as count,
       string_agg(id::text, ', ' order by updated_at desc) as reference_ids
from knowledge_base_items
where deleted_at is null
  and item_kind = 'object_ref'
group by target_object_type, target_object_id
having count(*) > 1
order by count desc, target_object_type;
"@
