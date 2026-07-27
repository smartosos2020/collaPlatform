create table project_work_item_view_preferences (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(64) not null check (view_key ~ '^[a-z][a-z0-9_-]{0,63}$'),
    view_mode varchar(16) not null check (view_mode in ('table', 'list')),
    density varchar(16) not null check (density in ('compact', 'comfortable')),
    columns_json jsonb not null check (jsonb_typeof(columns_json) = 'array'),
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, view_key),
    constraint fk_project_work_item_view_preference_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_view_preference_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
);

create index idx_project_work_item_view_preferences_user
    on project_work_item_view_preferences(
        workspace_id, user_id, updated_at desc, space_id, view_key
    );

create table project_work_item_view_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(64) not null,
    operation varchar(32) not null check (
        operation in ('save_preference', 'bulk_archive', 'bulk_restore')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    expected_version bigint not null check (expected_version >= 0),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_version bigint,
    response_json jsonb,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_view_command_request
        unique (workspace_id, space_id, user_id, operation, request_id),
    constraint fk_project_work_item_view_command_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_view_command_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint ck_project_work_item_view_command_completion
        check (
            (status='pending' and response_version is null and response_json is null and completed_at is null)
            or (status='completed' and response_version is not null
                and jsonb_typeof(response_json)='object' and completed_at is not null)
        )
);

create table project_work_item_export_jobs (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    owner_user_id uuid not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    query_schema_version smallint not null check (query_schema_version = 1),
    query_json jsonb not null check (jsonb_typeof(query_json) = 'object'),
    columns_json jsonb not null check (jsonb_typeof(columns_json) = 'array'),
    status varchar(16) not null check (
        status in ('queued', 'running', 'ready', 'failed', 'expired')
    ),
    row_limit integer not null check (row_limit between 1 and 200),
    created_at timestamptz not null,
    ready_at timestamptz,
    failed_at timestamptz,
    expires_at timestamptz not null,
    constraint uk_project_work_item_export_job_request
        unique (workspace_id, space_id, owner_user_id, request_id),
    constraint uk_project_work_item_export_job_scope
        unique (workspace_id, space_id, id),
    constraint fk_project_work_item_export_job_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_export_job_owner
        foreign key (workspace_id, owner_user_id) references users(workspace_id, id),
    constraint ck_project_work_item_export_job_state
        check (
            (status in ('queued', 'running') and ready_at is null and failed_at is null)
            or (status='ready' and ready_at is not null and failed_at is null)
            or (status='failed' and ready_at is null and failed_at is not null)
            or (status='expired')
        )
);

create index idx_project_work_item_export_jobs_owner
    on project_work_item_export_jobs(
        workspace_id, owner_user_id, status, expires_at, id
    );

create function guard_project_work_item_view_command()
returns trigger
language plpgsql
as $$
begin
    if tg_op='DELETE' and current_setting('colla.project_space_cleanup', true)='on' then
        return old;
    end if;
    if tg_op='DELETE' then
        raise exception 'work item view commands are immutable' using errcode='23514';
    end if;
    if old.status='completed' then
        raise exception 'completed work item view commands are immutable' using errcode='23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_view_command
before update or delete on project_work_item_view_commands
for each row execute function guard_project_work_item_view_command();
