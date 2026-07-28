create table project_resource_allocations (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    work_item_id uuid not null,
    user_id uuid not null,
    start_date date not null,
    end_date date not null,
    allocation_percent numeric(5,2) not null check (allocation_percent between 0.01 and 100),
    status varchar(16) not null check (status in ('active', 'ended', 'archived')),
    aggregate_version bigint not null check (aggregate_version >= 1),
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint ck_project_resource_allocation_dates check (end_date >= start_date),
    constraint uk_project_resource_allocation_identity unique (workspace_id, space_id, id),
    constraint fk_project_resource_allocation_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_allocation_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_resource_allocation_user foreign key (workspace_id, user_id)
        references users(workspace_id, id),
    constraint fk_project_resource_allocation_actor foreign key (workspace_id, updated_by)
        references users(workspace_id, id)
);

create index idx_project_resource_allocation_window
    on project_resource_allocations(
        workspace_id, space_id, user_id, start_date, end_date
    );
create index idx_project_resource_allocation_item
    on project_resource_allocations(workspace_id, space_id, work_item_id, status);

create table project_resource_capacity_rules (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    daily_minutes smallint not null check (daily_minutes between 1 and 1440),
    warning_percent numeric(5,2) not null check (warning_percent between 1 and 100),
    aggregate_version bigint not null check (aggregate_version >= 1),
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_resource_capacity_rule unique (workspace_id, space_id, user_id),
    constraint uk_project_resource_capacity_rule_identity unique (workspace_id, space_id, id),
    constraint fk_project_resource_capacity_rule_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_capacity_rule_user foreign key (workspace_id, user_id)
        references users(workspace_id, id),
    constraint fk_project_resource_capacity_rule_actor foreign key (workspace_id, updated_by)
        references users(workspace_id, id)
);

create table project_resource_load_index (
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    bucket_date date not null,
    capacity_minutes int not null check (capacity_minutes >= 0),
    allocated_minutes int not null check (allocated_minutes >= 0),
    source_version bigint not null check (source_version >= 0),
    expires_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, bucket_date),
    constraint fk_project_resource_load_index_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id)
);

create index idx_project_resource_load_index_expiry
    on project_resource_load_index(workspace_id, space_id, expires_at);

create table project_resource_capacity_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(24) not null
        check (operation in ('create', 'update', 'end', 'archive', 'save_rule')),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_resource_capacity_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_resource_capacity_command_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_capacity_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);
