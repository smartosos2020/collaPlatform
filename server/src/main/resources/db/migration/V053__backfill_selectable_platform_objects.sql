insert into object_links
    (id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, created_at, updated_at, deleted_at)
select gen_random_uuid(), p.workspace_id, 'project', p.id,
       '/projects/' || p.id, 'colla://project/' || p.id, p.name,
       p.created_at, p.updated_at, p.archived_at
from projects p
on conflict (workspace_id, object_type, object_id)
do update set web_path = excluded.web_path,
              deep_link = excluded.deep_link,
              title_snapshot = excluded.title_snapshot,
              updated_at = excluded.updated_at,
              deleted_at = excluded.deleted_at;

insert into object_links
    (id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, created_at, updated_at, deleted_at)
select gen_random_uuid(), f.workspace_id, 'file', f.id,
       '/files/' || f.id, 'colla://file/' || f.id, f.original_name,
       f.created_at, coalesce(f.completed_at, f.created_at), f.deleted_at
from files f
where f.status = 'completed'
on conflict (workspace_id, object_type, object_id)
do update set web_path = excluded.web_path,
              deep_link = excluded.deep_link,
              title_snapshot = excluded.title_snapshot,
              updated_at = excluded.updated_at,
              deleted_at = excluded.deleted_at;

create index if not exists idx_knowledge_base_items_target_object
    on knowledge_base_items (workspace_id, target_object_type, target_object_id)
    where deleted_at is null and content_type = 'object_ref';
