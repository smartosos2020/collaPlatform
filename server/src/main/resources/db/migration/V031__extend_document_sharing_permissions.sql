alter table document_permissions
    drop constraint chk_document_permission_level;

alter table document_permissions
    add column source_type varchar(16) not null default 'direct',
    add column source_document_id uuid references documents(id);

update document_permissions dp
set permission_level = 'owner'
from documents d
where dp.document_id = d.id
  and dp.user_id = d.created_by
  and dp.revoked_at is null;

insert into document_permissions
    (id, workspace_id, document_id, user_id, permission_level, source_type, created_by, created_at, updated_by, updated_at)
select d.id, d.workspace_id, d.id, d.created_by, 'owner', 'direct', d.created_by, d.created_at, d.updated_by, d.updated_at
from documents d
where d.deleted_at is null
  and not exists (
      select 1
      from document_permissions dp
      where dp.document_id = d.id and dp.user_id = d.created_by
  );

alter table document_permissions
    add constraint chk_document_permission_level check (permission_level in ('view', 'comment', 'edit', 'manage', 'owner')),
    add constraint chk_document_permission_source check (source_type in ('direct', 'inherited'));

create index idx_document_permissions_source
    on document_permissions(workspace_id, source_document_id)
    where revoked_at is null and source_document_id is not null;

alter table documents
    add column description varchar(512),
    add column cover_url varchar(1024),
    add column default_permission_level varchar(16) not null default 'view',
    add column knowledge_base boolean not null default false,
    add constraint chk_document_default_permission_level check (default_permission_level in ('view', 'comment', 'edit'));

create table document_share_links (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    document_id uuid not null references documents(id),
    token varchar(64) not null unique,
    scope varchar(32) not null,
    permission_level varchar(16) not null,
    enabled boolean not null default true,
    expires_at timestamptz,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid references users(id),
    updated_at timestamptz,
    disabled_at timestamptz,
    unique(document_id),
    constraint chk_document_share_link_scope check (scope in ('workspace')),
    constraint chk_document_share_link_permission check (permission_level in ('view', 'comment', 'edit'))
);

create index idx_document_share_links_document
    on document_share_links(document_id)
    where enabled = true;

create index idx_document_share_links_token
    on document_share_links(workspace_id, token)
    where enabled = true;
