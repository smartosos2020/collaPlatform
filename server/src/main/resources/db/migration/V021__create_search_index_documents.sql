create table search_index_documents (
    workspace_id uuid not null references workspaces(id),
    object_type varchar(64) not null,
    object_id uuid not null,
    title text not null,
    excerpt text,
    web_path text,
    deep_link text,
    search_text text not null,
    updated_at timestamptz not null,
    indexed_at timestamptz not null default now(),
    primary key (workspace_id, object_type, object_id)
);

create index idx_search_index_documents_text
    on search_index_documents
    using gin (to_tsvector('simple', search_text));

create index idx_search_index_documents_workspace_type
    on search_index_documents (workspace_id, object_type, updated_at desc);
