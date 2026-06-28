alter table permissions
    add column description text,
    add column risk_level varchar(16) not null default 'low',
    add column is_builtin boolean not null default true,
    add column display_order integer not null default 0;

alter table permissions
    add constraint chk_permissions_risk_level check (risk_level in ('low', 'medium', 'high', 'critical'));

alter table roles
    add column description text,
    add column status varchar(32) not null default 'active',
    add column updated_by uuid references users(id);

alter table roles
    add constraint chk_roles_status check (status in ('active', 'disabled'));

create table role_assignments (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    role_id uuid not null references roles(id),
    subject_type varchar(32) not null,
    subject_id uuid not null,
    scope_type varchar(32) not null default 'system',
    scope_id uuid,
    effective_at timestamptz not null default now(),
    expires_at timestamptz,
    status varchar(32) not null default 'active',
    created_by uuid references users(id),
    created_at timestamptz not null default now(),
    revoked_by uuid references users(id),
    revoked_at timestamptz,
    constraint chk_role_assignments_subject_type check (subject_type in ('user', 'department', 'user_group')),
    constraint chk_role_assignments_status check (status in ('active', 'revoked'))
);

create index idx_role_assignments_role
    on role_assignments (workspace_id, role_id, status, created_at desc);

create index idx_role_assignments_subject
    on role_assignments (workspace_id, subject_type, subject_id, status, expires_at);

insert into role_assignments
    (id, workspace_id, role_id, subject_type, subject_id, scope_type, status, created_by, created_at)
select ur.id, ur.workspace_id, ur.role_id, 'user', ur.user_id, 'system', 'active', ur.created_by, ur.created_at
from user_roles ur
where not exists (
    select 1
    from role_assignments ra
    where ra.id = ur.id
);

update roles
set description = case code
        when 'admin' then 'System administrator with all workspace management permissions.'
        when 'member' then 'Default member role for daily collaboration.'
        when 'project_owner' then 'Project owner role with project management permissions.'
        when 'project_member' then 'Project collaborator role.'
        when 'project_viewer' then 'Read-only project collaborator role.'
        else description
    end,
    status = 'active'
where description is null;

update permissions
set description = case code
        when 'admin.access' then 'Access the administration workspace.'
        when 'user.manage' then 'Create, disable, enable and reset workspace members.'
        when 'org.view' then 'View department tree, department members and department managers.'
        when 'org.manage' then 'Create, move, update and delete departments and managers.'
        when 'usergroup.view' then 'View user groups, direct members and expanded members.'
        when 'usergroup.manage' then 'Create, update, disable, delete and edit user group members.'
        when 'audit.view' then 'View audit logs.'
        when 'project.manage' then 'Manage projects and project settings.'
        when 'issue.manage' then 'Manage issues and workflow state.'
        when 'doc.manage' then 'Manage document content and document settings.'
        when 'base.manage' then 'Manage base applications, tables and records.'
        when 'resource.grant' then 'Grant resource-level permissions to users, departments, user groups or roles.'
        else description
    end,
    risk_level = case
        when code in ('admin.access', 'user.manage', 'org.manage', 'usergroup.manage', 'resource.grant') then 'high'
        when code in ('audit.view', 'project.manage', 'issue.manage', 'doc.manage', 'base.manage') then 'medium'
        else 'low'
    end,
    display_order = case module
        when 'admin' then 100
        when 'identity' then 200
        when 'project' then 300
        when 'doc' then 400
        when 'base' then 500
        else 900
    end
where code is not null;

insert into permissions (id, code, name, module, description, risk_level, is_builtin, display_order, created_at)
values
    ('00000000-0000-0000-0000-000000000401', 'role.view', 'View roles', 'identity', 'View roles, role permissions and role assignment records.', 'low', true, 210, now()),
    ('00000000-0000-0000-0000-000000000402', 'role.manage', 'Manage roles', 'identity', 'Create and update roles, bind permissions and assign roles to subjects.', 'high', true, 211, now()),
    ('00000000-0000-0000-0000-000000000403', 'permission.inspect', 'Inspect permissions', 'identity', 'Inspect permission catalog, risk level and permission metadata.', 'medium', true, 212, now())
on conflict (code) do update
set name = excluded.name,
    module = excluded.module,
    description = excluded.description,
    risk_level = excluded.risk_level,
    is_builtin = excluded.is_builtin,
    display_order = excluded.display_order;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('role.view', 'role.manage', 'permission.inspect')
where r.code = 'admin'
on conflict do nothing;
