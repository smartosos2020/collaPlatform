update project_work_item_type_versions v
   set status = case
           when t.current_version_id = v.id then 'published'
           else 'superseded'
       end,
       published_by = coalesce(v.published_by, v.created_by),
       published_at = coalesce(v.published_at, v.created_at)
  from project_work_item_types t
 where v.workspace_id = t.workspace_id
   and v.space_id = t.space_id
   and v.type_definition_id = t.id
   and v.status = 'draft';

alter table project_work_item_type_versions
    drop constraint ck_project_work_item_type_versions_status,
    drop constraint ck_project_work_item_type_versions_publication;

alter table project_work_item_type_versions
    add constraint ck_project_work_item_type_versions_status
        check (status in ('published', 'superseded')),
    add constraint ck_project_work_item_type_versions_publication
        check (published_by is not null and published_at is not null);

comment on column project_work_item_type_versions.status is
    'Immutable version lifecycle: published -> superseded. Mutable editing belongs to ConfigurationDraft.';
