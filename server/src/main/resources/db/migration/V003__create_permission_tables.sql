create table roles (
    id uuid primary key,
    workspace_id uuid not null,
    code varchar(64) not null,
    name varchar(64) not null,
    scope varchar(32) not null,
    is_builtin boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_roles_workspace_code unique (workspace_id, code)
);

create table permissions (
    id uuid primary key,
    code varchar(128) not null,
    name varchar(128) not null,
    module varchar(64) not null,
    created_at timestamptz not null,
    constraint uk_permissions_code unique (code)
);

create table role_permissions (
    role_id uuid not null,
    permission_id uuid not null,
    primary key (role_id, permission_id)
);

create table user_roles (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    role_id uuid not null,
    created_by uuid,
    created_at timestamptz not null
);

create index idx_user_roles_user on user_roles (workspace_id, user_id);

create table resource_permissions (
    id uuid primary key,
    workspace_id uuid not null,
    resource_type varchar(64) not null,
    resource_id uuid not null,
    subject_type varchar(32) not null,
    subject_id uuid not null,
    permission_level varchar(32) not null,
    created_by uuid,
    created_at timestamptz not null
);

create index idx_resource_permissions_lookup
    on resource_permissions (resource_type, resource_id, subject_type, subject_id);

