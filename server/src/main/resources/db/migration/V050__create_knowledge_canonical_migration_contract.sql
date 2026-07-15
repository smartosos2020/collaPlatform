create table if not exists knowledge_content_canonical_documents (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    item_id uuid not null references knowledge_base_items(id),
    schema_version integer not null,
    document jsonb not null,
    checksum varchar(64) not null,
    source_kind varchar(32) not null default 'migration',
    source_version_no integer,
    state_vector varchar(512),
    created_by uuid references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid references users(id),
    updated_at timestamptz not null default now(),
    constraint uq_knowledge_canonical_document_item unique (workspace_id, item_id),
    constraint chk_knowledge_canonical_document_schema check (schema_version >= 1),
    constraint chk_knowledge_canonical_document_source check (source_kind in ('migration', 'editor', 'restore', 'import', 'projection'))
);

create index if not exists idx_knowledge_canonical_documents_schema
    on knowledge_content_canonical_documents (workspace_id, schema_version, updated_at desc);

create table if not exists knowledge_content_migration_batches (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    mode varchar(16) not null default 'dry_run',
    status varchar(16) not null default 'planned',
    source_schema_version integer not null default 2,
    target_schema_version integer not null,
    idempotency_key varchar(128) not null,
    source_checksum varchar(64),
    target_checksum varchar(64),
    item_count integer not null default 0,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    created_by uuid references users(id),
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint uq_knowledge_migration_batch_key unique (workspace_id, idempotency_key),
    constraint chk_knowledge_migration_batch_mode check (mode in ('dry_run', 'apply', 'rollback')),
    constraint chk_knowledge_migration_batch_status check (status in ('planned', 'running', 'completed', 'failed', 'rolled_back'))
);

create table if not exists knowledge_content_migration_items (
    id uuid primary key,
    batch_id uuid not null references knowledge_content_migration_batches(id) on delete cascade,
    workspace_id uuid not null references workspaces(id),
    item_id uuid not null references knowledge_base_items(id),
    source_checksum varchar(64) not null,
    target_checksum varchar(64),
    source_snapshot jsonb,
    target_document jsonb,
    status varchar(16) not null default 'planned',
    failure_code varchar(64),
    failure_message varchar(1024),
    created_at timestamptz not null default now(),
    processed_at timestamptz,
    constraint uq_knowledge_migration_item_batch unique (batch_id, item_id),
    constraint chk_knowledge_migration_item_status check (status in ('planned', 'migrated', 'skipped', 'failed', 'rolled_back'))
);

create index if not exists idx_knowledge_migration_items_item
    on knowledge_content_migration_items (workspace_id, item_id, created_at desc);

alter table knowledge_content_versions
    add column if not exists schema_version integer not null default 2;

alter table knowledge_content_templates
    add column if not exists schema_version integer not null default 2;

alter table knowledge_content_collaboration_states
    add column if not exists schema_version integer not null default 2;
