create table departments (
    id uuid primary key,
    workspace_id uuid not null,
    parent_id uuid,
    code varchar(64) not null,
    name varchar(128) not null,
    path text not null,
    depth int not null,
    sort_order int not null default 0,
    status varchar(32) not null,
    created_by uuid,
    created_at timestamptz not null,
    updated_by uuid,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    constraint uk_departments_workspace_code unique (workspace_id, code),
    constraint fk_departments_parent foreign key (parent_id) references departments(id)
);

create index idx_departments_workspace_parent on departments (workspace_id, parent_id, sort_order, name);
create index idx_departments_workspace_status on departments (workspace_id, status);

create table department_members (
    id uuid primary key,
    workspace_id uuid not null,
    department_id uuid not null,
    user_id uuid not null,
    relation_type varchar(32) not null,
    started_at timestamptz not null,
    ended_at timestamptz,
    created_by uuid,
    created_at timestamptz not null,
    constraint fk_department_members_department foreign key (department_id) references departments(id),
    constraint fk_department_members_user foreign key (user_id) references users(id)
);

create unique index uk_department_members_active_relation
    on department_members (workspace_id, department_id, user_id, relation_type)
    where ended_at is null;

create unique index uk_department_members_primary_user
    on department_members (workspace_id, user_id)
    where relation_type = 'primary' and ended_at is null;

create index idx_department_members_department on department_members (workspace_id, department_id, ended_at);
create index idx_department_members_user on department_members (workspace_id, user_id, ended_at);

create table department_managers (
    id uuid primary key,
    workspace_id uuid not null,
    department_id uuid not null,
    user_id uuid not null,
    manager_type varchar(32) not null,
    created_by uuid,
    created_at timestamptz not null,
    constraint fk_department_managers_department foreign key (department_id) references departments(id),
    constraint fk_department_managers_user foreign key (user_id) references users(id),
    constraint uk_department_managers_user unique (workspace_id, department_id, user_id, manager_type)
);

create index idx_department_managers_department on department_managers (workspace_id, department_id);
create index idx_department_managers_user on department_managers (workspace_id, user_id);

insert into permissions (id, code, name, module, created_at)
values
    ('00000000-0000-0000-0000-000000000211', 'org.view', 'View organization structure', 'identity', now()),
    ('00000000-0000-0000-0000-000000000212', 'org.manage', 'Manage departments and department memberships', 'identity', now())
on conflict (code) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('org.view', 'org.manage')
where r.code = 'admin'
on conflict (role_id, permission_id) do nothing;
