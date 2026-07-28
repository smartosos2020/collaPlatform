create table project_resource_calendars (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    timezone varchar(80) not null,
    work_days jsonb not null check (jsonb_typeof(work_days) = 'array'),
    daily_minutes smallint not null check (daily_minutes between 1 and 1440),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_resource_calendar unique (workspace_id, space_id),
    constraint uk_project_resource_calendar_identity unique (workspace_id, space_id, id),
    constraint fk_project_resource_calendar_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_calendar_actor foreign key (workspace_id, updated_by)
        references users(workspace_id, id)
);

create table project_resource_calendar_exceptions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    calendar_id uuid not null,
    exception_date date not null,
    available_minutes smallint not null check (available_minutes between 0 and 1440),
    note varchar(240) not null default '',
    constraint uk_project_resource_calendar_exception
        unique (workspace_id, space_id, calendar_id, exception_date),
    constraint fk_project_resource_calendar_exception_calendar
        foreign key (workspace_id, space_id, calendar_id)
        references project_resource_calendars(workspace_id, space_id, id),
    constraint fk_project_resource_calendar_exception_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id)
);

create index idx_project_resource_calendar_exception_date
    on project_resource_calendar_exceptions(workspace_id, space_id, exception_date);

create table project_resource_estimates (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    work_item_id uuid not null,
    estimate_unit varchar(16) not null check (estimate_unit in ('hour', 'day', 'point')),
    estimate_amount numeric(12,2) not null check (estimate_amount between 0.01 and 100000),
    source_work_item_version bigint not null check (source_work_item_version >= 1),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_resource_estimate unique (workspace_id, space_id, work_item_id),
    constraint fk_project_resource_estimate_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_estimate_work_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_resource_estimate_actor foreign key (workspace_id, updated_by)
        references users(workspace_id, id)
);

create index idx_project_resource_estimate_list
    on project_resource_estimates(workspace_id, space_id, updated_at desc, id);

create table project_resource_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(40) not null
        check (operation in ('save_calendar', 'save_estimate')),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_resource_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_resource_command_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);
