create table object_type_rules (
    id uuid primary key,
    object_type varchar(64) not null unique,
    web_path_pattern varchar(255) not null,
    deep_link_pattern varchar(255) not null,
    created_at timestamptz not null
);

insert into object_type_rules (id, object_type, web_path_pattern, deep_link_pattern, created_at)
values
    ('00000000-0000-0000-0000-000000000201', 'issue', '/issues/{id}', 'colla://issue/{id}', now()),
    ('00000000-0000-0000-0000-000000000202', 'document', '/docs/{id}', 'colla://document/{id}', now()),
    ('00000000-0000-0000-0000-000000000203', 'base', '/bases/{id}', 'colla://base/{id}', now()),
    ('00000000-0000-0000-0000-000000000204', 'base_table', '/bases/{baseId}/tables/{id}', 'colla://base-table/{id}', now()),
    ('00000000-0000-0000-0000-000000000205', 'file', '/files/{id}', 'colla://file/{id}', now());

alter table domain_events
    add column retry_count int not null default 0,
    add column next_attempt_at timestamptz,
    add column last_error text,
    add column idempotency_key varchar(128);

create unique index uk_domain_events_idempotency
    on domain_events (workspace_id, idempotency_key)
    where idempotency_key is not null;

create table notifications (
    id uuid primary key,
    workspace_id uuid not null,
    recipient_id uuid not null,
    actor_id uuid,
    notification_type varchar(64) not null,
    title varchar(255) not null,
    body text,
    target_type varchar(64),
    target_id uuid,
    web_path varchar(512),
    dedupe_key varchar(128),
    read_at timestamptz,
    created_at timestamptz not null
);

create index idx_notifications_recipient_unread
    on notifications (workspace_id, recipient_id, read_at, created_at desc);

create unique index uk_notifications_dedupe
    on notifications (workspace_id, recipient_id, dedupe_key)
    where dedupe_key is not null;

create table files (
    id uuid primary key,
    workspace_id uuid not null,
    object_key varchar(512) not null unique,
    original_name varchar(255) not null,
    content_type varchar(128) not null,
    size_bytes bigint not null,
    status varchar(32) not null,
    uploaded_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    deleted_at timestamptz
);

create index idx_files_workspace_status on files (workspace_id, status, created_at desc);

create table file_usages (
    id uuid primary key,
    workspace_id uuid not null,
    file_id uuid not null references files(id),
    target_type varchar(64) not null,
    target_id uuid not null,
    created_by uuid not null,
    created_at timestamptz not null,
    constraint uk_file_usages_target unique (workspace_id, file_id, target_type, target_id)
);

create index idx_file_usages_target on file_usages (workspace_id, target_type, target_id);
