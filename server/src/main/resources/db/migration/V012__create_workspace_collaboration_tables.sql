insert into object_type_rules (id, object_type, web_path_pattern, deep_link_pattern, created_at)
values ('00000000-0000-0000-0000-000000000206', 'base_record', '/bases/{baseId}/tables/{tableId}/records/{id}', 'colla://base-record/{id}', now())
on conflict (object_type) do nothing;

create table object_recent_accesses (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    user_id uuid not null references users(id),
    object_type varchar(64) not null,
    object_id uuid not null,
    web_path varchar(512),
    deep_link varchar(512),
    title_snapshot varchar(255),
    access_count int not null default 1,
    last_accessed_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(workspace_id, user_id, object_type, object_id)
);

create index idx_object_recent_accesses_user
    on object_recent_accesses(workspace_id, user_id, last_accessed_at desc);

create table object_favorites (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    user_id uuid not null references users(id),
    object_type varchar(64) not null,
    object_id uuid not null,
    created_at timestamptz not null default now(),
    unique(workspace_id, user_id, object_type, object_id)
);

create index idx_object_favorites_user
    on object_favorites(workspace_id, user_id, created_at desc);
