create table bases (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    name varchar(128) not null,
    description text,
    status varchar(32) not null default 'active',
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid not null references users(id),
    updated_at timestamptz not null default now(),
    archived_at timestamptz
);

create index idx_bases_workspace_updated on bases(workspace_id, updated_at desc) where archived_at is null;

create table base_members (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    base_id uuid not null references bases(id),
    user_id uuid not null references users(id),
    permission_level varchar(16) not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid references users(id),
    updated_at timestamptz,
    revoked_at timestamptz,
    unique(base_id, user_id),
    constraint chk_base_permission_level check (permission_level in ('view', 'edit', 'manage'))
);

create index idx_base_members_user on base_members(workspace_id, user_id) where revoked_at is null;

create table base_tables (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    base_id uuid not null references bases(id),
    name varchar(128) not null,
    primary_field_id uuid,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid not null references users(id),
    updated_at timestamptz not null default now(),
    archived_at timestamptz
);

create index idx_base_tables_base on base_tables(base_id, created_at) where archived_at is null;

create table base_fields (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    table_id uuid not null references base_tables(id),
    field_key varchar(64) not null,
    name varchar(128) not null,
    field_type varchar(32) not null,
    config jsonb not null default '{}'::jsonb,
    required boolean not null default false,
    sort_order integer not null default 0,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid not null references users(id),
    updated_at timestamptz not null default now(),
    archived_at timestamptz,
    unique(table_id, field_key),
    constraint chk_base_field_type check (field_type in ('text', 'number', 'member', 'date', 'attachment', 'single_select', 'multi_select'))
);

create index idx_base_fields_table on base_fields(table_id, sort_order) where archived_at is null;

create table base_records (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    table_id uuid not null references base_tables(id),
    record_no integer not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid not null references users(id),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique(table_id, record_no)
);

create index idx_base_records_table_updated on base_records(table_id, updated_at desc) where deleted_at is null;

create table base_record_values (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    record_id uuid not null references base_records(id),
    field_id uuid not null references base_fields(id),
    value_json jsonb not null default 'null'::jsonb,
    value_text text,
    value_number numeric,
    value_date date,
    updated_by uuid not null references users(id),
    updated_at timestamptz not null default now(),
    unique(record_id, field_id)
);

create index idx_base_record_values_field_text on base_record_values(field_id, value_text);
create index idx_base_record_values_field_number on base_record_values(field_id, value_number);
create index idx_base_record_values_field_date on base_record_values(field_id, value_date);

create table base_views (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    table_id uuid not null references base_tables(id),
    name varchar(128) not null,
    filters jsonb not null default '[]'::jsonb,
    sorts jsonb not null default '[]'::jsonb,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid not null references users(id),
    updated_at timestamptz not null default now(),
    archived_at timestamptz
);

create index idx_base_views_table on base_views(table_id, created_at) where archived_at is null;
