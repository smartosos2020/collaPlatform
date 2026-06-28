create table document_collaboration_states (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    document_id uuid not null references documents(id),
    state_vector varchar(128) not null,
    snapshot_content text not null default '',
    snapshot_payload jsonb not null default '{}'::jsonb,
    server_clock bigint not null default 0,
    last_client_id varchar(128),
    updated_by uuid references users(id),
    last_saved_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(workspace_id, document_id)
);

create index idx_document_collaboration_states_document
    on document_collaboration_states(workspace_id, document_id, server_clock desc);

create index idx_document_collaboration_states_unsaved
    on document_collaboration_states(workspace_id, updated_at)
    where last_saved_at is null or last_saved_at < updated_at;
