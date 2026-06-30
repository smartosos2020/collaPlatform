alter table search_index_documents
    add column if not exists knowledge_base_id uuid,
    add column if not exists parent_document_id uuid,
    add column if not exists directory_path text,
    add column if not exists tags text[] not null default '{}',
    add column if not exists maintainer_id uuid,
    add column if not exists knowledge_status varchar(32),
    add column if not exists doc_type varchar(32),
    add column if not exists hit_source varchar(32) not null default 'title';

create index if not exists idx_search_index_documents_knowledge_base
    on search_index_documents(workspace_id, knowledge_base_id, updated_at desc)
    where object_type = 'document' and knowledge_base_id is not null;

create index if not exists idx_search_index_documents_parent_document
    on search_index_documents(workspace_id, parent_document_id, updated_at desc)
    where object_type = 'document';

create index if not exists idx_search_index_documents_tags
    on search_index_documents
    using gin(tags);

create index if not exists idx_search_index_documents_knowledge_filters
    on search_index_documents(workspace_id, object_type, doc_type, maintainer_id, knowledge_status, updated_at desc);

create table if not exists knowledge_subscriptions (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    subscriber_id uuid not null references users(id),
    target_type varchar(32) not null,
    target_id uuid not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint chk_knowledge_subscription_target_type check (target_type in ('knowledge_base', 'document')),
    constraint uq_knowledge_subscription_active unique (workspace_id, subscriber_id, target_type, target_id)
);

create index if not exists idx_knowledge_subscriptions_target
    on knowledge_subscriptions(workspace_id, target_type, target_id)
    where deleted_at is null;

create index if not exists idx_knowledge_subscriptions_user
    on knowledge_subscriptions(workspace_id, subscriber_id, created_at desc)
    where deleted_at is null;
