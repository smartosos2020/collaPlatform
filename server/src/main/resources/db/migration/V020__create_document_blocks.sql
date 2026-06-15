create table document_blocks (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    document_id uuid not null references documents(id),
    block_type varchar(32) not null,
    content text not null default '',
    sort_order integer not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid not null references users(id),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint chk_document_blocks_type check (block_type in ('paragraph', 'heading', 'list', 'task', 'quote', 'code'))
);

create index idx_document_blocks_document_order
    on document_blocks (workspace_id, document_id, sort_order)
    where deleted_at is null;
