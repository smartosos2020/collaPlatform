create table object_links (
    id uuid primary key,
    workspace_id uuid not null,
    object_type varchar(64) not null,
    object_id uuid not null,
    web_path varchar(512) not null,
    deep_link varchar(512) not null,
    title_snapshot varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    constraint uk_object_links_object unique (workspace_id, object_type, object_id)
);

create index idx_object_links_deep_link on object_links (workspace_id, deep_link);

