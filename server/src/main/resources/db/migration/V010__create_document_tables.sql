create table documents (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    parent_id uuid references documents(id),
    title varchar(255) not null,
    doc_type varchar(32) not null default 'markdown',
    content text not null default '',
    current_version_no integer not null default 1,
    status varchar(32) not null default 'active',
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid not null references users(id),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index idx_documents_workspace_updated on documents(workspace_id, updated_at desc) where deleted_at is null;
create index idx_documents_parent on documents(parent_id) where deleted_at is null;

create table document_versions (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    document_id uuid not null references documents(id),
    version_no integer not null,
    title varchar(255) not null,
    content text not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    unique(document_id, version_no)
);

create index idx_document_versions_document on document_versions(document_id, version_no desc);

create table document_permissions (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    document_id uuid not null references documents(id),
    user_id uuid not null references users(id),
    permission_level varchar(16) not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid references users(id),
    updated_at timestamptz,
    revoked_at timestamptz,
    unique(document_id, user_id),
    constraint chk_document_permission_level check (permission_level in ('view', 'edit', 'manage'))
);

create index idx_document_permissions_user on document_permissions(workspace_id, user_id) where revoked_at is null;

create table document_relations (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    document_id uuid not null references documents(id),
    target_type varchar(32) not null,
    target_id uuid not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique(workspace_id, document_id, target_type, target_id)
);

create index idx_document_relations_document on document_relations(document_id) where deleted_at is null;
create index idx_document_relations_target on document_relations(workspace_id, target_type, target_id) where deleted_at is null;

create table document_comments (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    document_id uuid not null references documents(id),
    author_id uuid not null references users(id),
    content text not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index idx_document_comments_document on document_comments(document_id, created_at) where deleted_at is null;
