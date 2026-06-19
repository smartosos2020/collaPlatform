alter table base_fields
    drop constraint chk_base_field_type;

alter table base_fields
    add constraint chk_base_field_type
    check (field_type in (
        'text',
        'number',
        'member',
        'date',
        'attachment',
        'single_select',
        'multi_select',
        'status',
        'url',
        'object_link'
    ));

alter table base_views
    add column visible_field_ids jsonb not null default '[]'::jsonb;

create table base_record_comments (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    record_id uuid not null references base_records(id),
    author_id uuid not null references users(id),
    content text not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index idx_base_record_comments_record
    on base_record_comments(workspace_id, record_id, created_at)
    where deleted_at is null;

create table base_record_relations (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    record_id uuid not null references base_records(id),
    target_type varchar(64) not null,
    target_id uuid not null,
    relation_type varchar(32) not null default 'manual',
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique(record_id, target_type, target_id, relation_type)
);

create index idx_base_record_relations_record
    on base_record_relations(workspace_id, record_id, created_at)
    where deleted_at is null;

create index idx_base_record_relations_target
    on base_record_relations(workspace_id, target_type, target_id, created_at)
    where deleted_at is null;

create table base_record_activity_logs (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    record_id uuid not null references base_records(id),
    actor_id uuid not null references users(id),
    action varchar(64) not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index idx_base_record_activity_logs_record
    on base_record_activity_logs(workspace_id, record_id, created_at desc);
