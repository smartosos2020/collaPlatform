alter table knowledge_content_collaboration_states
    add column if not exists yjs_snapshot bytea,
    add column if not exists yjs_state_vector bytea,
    add column if not exists schema_version integer not null default 3,
    add column if not exists snapshot_sequence bigint not null default 0,
    add column if not exists snapshot_hash varchar(64),
    add column if not exists last_audited_at timestamptz;

create table if not exists knowledge_content_collaboration_updates (
    sequence_no bigint generated always as identity primary key,
    workspace_id uuid not null references workspaces(id),
    item_id uuid not null references knowledge_base_items(id),
    update_id varchar(64) not null,
    update_payload bytea not null,
    actor_id uuid not null references users(id),
    client_id varchar(128) not null,
    schema_version integer not null,
    created_at timestamptz not null default now(),
    unique (workspace_id, item_id, update_id)
);

create index if not exists idx_knowledge_collaboration_updates_recovery
    on knowledge_content_collaboration_updates(workspace_id, item_id, sequence_no);

create table if not exists knowledge_content_collaboration_tickets (
    id uuid primary key,
    token_hash varchar(64) not null unique,
    workspace_id uuid not null references workspaces(id),
    item_id uuid not null references knowledge_base_items(id),
    user_id uuid not null references users(id),
    device_id uuid,
    client_id varchar(128) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists idx_knowledge_collaboration_tickets_active
    on knowledge_content_collaboration_tickets(token_hash, expires_at)
    where revoked_at is null;
