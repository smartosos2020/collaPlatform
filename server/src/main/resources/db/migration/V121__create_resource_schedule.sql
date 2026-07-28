create table project_resource_schedule_preferences (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    window_start date not null,
    window_end date not null,
    zoom varchar(16) not null check (zoom in ('day', 'week', 'month')),
    aggregate_version bigint not null check (aggregate_version >= 1),
    updated_at timestamptz not null,
    constraint ck_project_resource_schedule_preference_window check (window_end >= window_start),
    constraint uk_project_resource_schedule_preference unique (workspace_id, space_id, user_id),
    constraint fk_project_resource_schedule_preference_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_schedule_preference_user foreign key (workspace_id, user_id)
        references users(workspace_id, id)
);

create table project_resource_schedule_index (
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    window_start date not null,
    window_end date not null,
    row_count int not null check (row_count >= 0),
    bar_count int not null check (bar_count >= 0),
    conflict_count int not null check (conflict_count >= 0),
    source_version bigint not null check (source_version >= 0),
    expires_at timestamptz not null,
    primary key (workspace_id, space_id, user_id),
    constraint fk_project_resource_schedule_index_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id)
);

create index idx_project_resource_schedule_index_expiry
    on project_resource_schedule_index(workspace_id, space_id, expires_at);

create table project_resource_adjustment_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(24) not null check (operation in ('save_preference', 'adjust_allocation')),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_resource_adjustment_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_resource_adjustment_command_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_adjustment_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create table project_resource_schedule_stats (
    workspace_id uuid not null,
    space_id uuid not null,
    observed_date date not null,
    row_count int not null check (row_count >= 0),
    bar_count int not null check (bar_count >= 0),
    conflict_count int not null check (conflict_count >= 0),
    truncated boolean not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, observed_date),
    constraint fk_project_resource_schedule_stats_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id)
);

create index idx_project_resource_schedule_stats_date
    on project_resource_schedule_stats(workspace_id, space_id, observed_date desc);
