create table knowledge_base_spaces (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    name varchar(255) not null,
    code varchar(64) not null,
    description varchar(512),
    icon varchar(64),
    cover_url varchar(1024),
    status varchar(32) not null default 'active',
    visibility varchar(32) not null default 'private',
    root_document_id uuid not null references documents(id),
    home_document_id uuid references documents(id),
    owner_id uuid not null references users(id),
    default_permission_level varchar(16) not null default 'view',
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid references users(id),
    updated_at timestamptz,
    deleted_at timestamptz,
    constraint chk_knowledge_base_space_status check (status in ('active', 'disabled', 'archived')),
    constraint chk_knowledge_base_space_visibility check (visibility in ('private', 'workspace')),
    constraint chk_knowledge_base_space_default_permission check (default_permission_level in ('view', 'comment', 'edit')),
    constraint uq_knowledge_base_space_root unique (workspace_id, root_document_id)
);

create unique index uq_knowledge_base_spaces_code_active
    on knowledge_base_spaces(workspace_id, code)
    where deleted_at is null;

create index idx_knowledge_base_spaces_workspace
    on knowledge_base_spaces(workspace_id, status, updated_at desc)
    where deleted_at is null;

insert into knowledge_base_spaces
    (id, workspace_id, name, code, description, icon, cover_url, status, visibility,
     root_document_id, home_document_id, owner_id, default_permission_level,
     created_by, created_at, updated_by, updated_at)
select d.id,
       d.workspace_id,
       d.title,
       'kb-' || substring(replace(d.id::text, '-', '') from 1 for 12),
       d.description,
       null,
       d.cover_url,
       case when d.archived_at is not null or d.status = 'archived' then 'archived' else 'active' end,
       'private',
       d.id,
       d.id,
       d.created_by,
       d.default_permission_level,
       d.created_by,
       d.created_at,
       d.updated_by,
       d.updated_at
from documents d
where d.doc_type = 'space'
  and d.knowledge_base = true
  and d.deleted_at is null
  and not exists (
      select 1
      from knowledge_base_spaces k
      where k.workspace_id = d.workspace_id
        and k.root_document_id = d.id
        and k.deleted_at is null
  );
