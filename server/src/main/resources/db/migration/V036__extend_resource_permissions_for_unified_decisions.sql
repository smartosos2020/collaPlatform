alter table resource_permissions
    add column source_type varchar(32) not null default 'direct',
    add column source_id uuid,
    add column expires_at timestamptz,
    add column status varchar(32) not null default 'active',
    add column updated_by uuid,
    add column updated_at timestamptz;

alter table resource_permissions
    add constraint chk_resource_permissions_subject_type check (subject_type in ('user', 'department', 'user_group', 'role')),
    add constraint chk_resource_permissions_level check (permission_level in ('owner', 'manage', 'edit', 'comment', 'view')),
    add constraint chk_resource_permissions_source_type check (source_type in ('direct', 'inherited', 'owner', 'system')),
    add constraint chk_resource_permissions_status check (status in ('active', 'revoked'));

create index idx_resource_permissions_resource_active
    on resource_permissions (workspace_id, resource_type, resource_id, status, expires_at);

create index idx_resource_permissions_subject_active
    on resource_permissions (workspace_id, subject_type, subject_id, status, expires_at);

insert into resource_permissions
    (id, workspace_id, resource_type, resource_id, subject_type, subject_id, permission_level,
     source_type, source_id, expires_at, status, created_by, created_at, updated_by, updated_at)
select dp.id,
       dp.workspace_id,
       'document',
       dp.document_id,
       dp.subject_type,
       dp.subject_id,
       dp.permission_level,
       case when dp.source_type = 'inherited' then 'inherited' else 'direct' end,
       dp.source_document_id,
       null,
       'active',
       dp.created_by,
       dp.created_at,
       dp.updated_by,
       dp.updated_at
from document_permissions dp
where dp.revoked_at is null
  and dp.subject_type in ('user', 'user_group')
  and not exists (
      select 1
      from resource_permissions rp
      where rp.workspace_id = dp.workspace_id
        and rp.resource_type = 'document'
        and rp.resource_id = dp.document_id
        and rp.subject_type = dp.subject_type
        and rp.subject_id = dp.subject_id
        and rp.status = 'active'
  );
