create table workspaces (
    id uuid primary key,
    name varchar(128) not null,
    slug varchar(64) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_workspaces_slug unique (slug)
);

create table users (
    id uuid primary key,
    workspace_id uuid not null,
    username varchar(64) not null,
    password_hash varchar(255) not null,
    display_name varchar(64) not null,
    avatar_file_id uuid,
    email varchar(128),
    phone varchar(32),
    department varchar(128),
    status varchar(32) not null,
    last_login_at timestamptz,
    created_by uuid,
    created_at timestamptz not null,
    updated_by uuid,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    constraint uk_users_workspace_username unique (workspace_id, username)
);

create index idx_users_workspace_status on users (workspace_id, status);

create table user_devices (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    device_type varchar(32) not null,
    device_name varchar(128),
    device_fingerprint varchar(255) not null,
    app_version varchar(64),
    last_active_at timestamptz,
    created_at timestamptz not null,
    revoked_at timestamptz,
    constraint uk_user_devices_fingerprint unique (workspace_id, user_id, device_fingerprint)
);

create index idx_user_devices_user on user_devices (workspace_id, user_id, last_active_at);

create table sessions (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    device_id uuid,
    refresh_token_hash varchar(255) not null,
    user_agent text,
    ip_address varchar(64),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null
);

create index idx_sessions_user on sessions (user_id, created_at);
create index idx_sessions_token on sessions (refresh_token_hash);

create table push_tokens (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    device_id uuid not null,
    provider varchar(32) not null,
    token_hash varchar(255) not null,
    token_encrypted text not null,
    enabled boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    revoked_at timestamptz
);

create index idx_push_tokens_user on push_tokens (workspace_id, user_id, enabled);
create index idx_push_tokens_device on push_tokens (device_id);

