create table user_groups (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    code varchar(64) not null,
    name varchar(128) not null,
    description text,
    group_type varchar(32) not null default 'normal',
    status varchar(32) not null default 'active',
    created_by uuid references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid references users(id),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint uk_user_groups_workspace_code unique (workspace_id, code),
    constraint chk_user_groups_type check (group_type in ('normal', 'permission')),
    constraint chk_user_groups_status check (status in ('active', 'disabled'))
);

create index idx_user_groups_workspace_status on user_groups (workspace_id, status, name)
    where deleted_at is null;

create table user_group_members (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    group_id uuid not null references user_groups(id),
    subject_type varchar(32) not null,
    subject_id uuid not null,
    created_by uuid references users(id),
    created_at timestamptz not null default now(),
    removed_at timestamptz,
    constraint chk_user_group_members_subject check (subject_type in ('user', 'department'))
);

create unique index uk_user_group_members_active_subject
    on user_group_members (workspace_id, group_id, subject_type, subject_id)
    where removed_at is null;

create index idx_user_group_members_group on user_group_members (workspace_id, group_id, removed_at);
create index idx_user_group_members_subject on user_group_members (workspace_id, subject_type, subject_id, removed_at);

insert into permissions (id, code, name, module, created_at)
values
    ('00000000-0000-0000-0000-000000000213', 'usergroup.view', 'View user groups', 'identity', now()),
    ('00000000-0000-0000-0000-000000000214', 'usergroup.manage', 'Manage user groups and memberships', 'identity', now())
on conflict (code) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('usergroup.view', 'usergroup.manage')
where r.code = 'admin'
on conflict (role_id, permission_id) do nothing;

alter table document_permissions
    drop constraint if exists document_permissions_document_id_user_id_key;

alter table document_permissions
    alter column user_id drop not null,
    add column subject_type varchar(32) not null default 'user',
    add column subject_id uuid;

update document_permissions
set subject_type = 'user',
    subject_id = user_id
where subject_id is null;

alter table document_permissions
    alter column subject_id set not null,
    add constraint uk_document_permissions_subject unique (document_id, subject_type, subject_id),
    add constraint chk_document_permissions_subject_type check (subject_type in ('user', 'user_group'));

create index idx_document_permissions_subject
    on document_permissions(workspace_id, subject_type, subject_id)
    where revoked_at is null;
