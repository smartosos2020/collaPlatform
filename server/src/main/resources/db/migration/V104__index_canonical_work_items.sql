alter table search_index_entries
    add column if not exists space_id uuid,
    add column if not exists object_subtype varchar(80),
    add column if not exists object_status varchar(48),
    add column if not exists source_version bigint not null default 0;

alter table search_index_entries
    add constraint ck_search_index_entries_source_version
    check (source_version >= 0);

create index idx_search_index_entries_work_item_filters
    on search_index_entries(workspace_id, space_id, object_subtype, object_status, updated_at desc)
    where object_type = 'work_item';

create index idx_search_index_entries_work_item_text
    on search_index_entries using gin(to_tsvector('simple', search_text))
    where object_type = 'work_item';

insert into object_type_rules (id, object_type, web_path_pattern, deep_link_pattern, created_at)
values (
    gen_random_uuid(),
    'work_item',
    '/project-spaces/{spaceId}/work-items/{id}',
    'colla://work-item/{id}',
    now()
)
on conflict (object_type) do update
set web_path_pattern = excluded.web_path_pattern,
    deep_link_pattern = excluded.deep_link_pattern;
