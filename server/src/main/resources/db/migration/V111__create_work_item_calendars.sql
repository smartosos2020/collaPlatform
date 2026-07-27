create table project_work_item_calendar_preferences (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null check (view_key ~ '^[a-z][a-z0-9._-]{0,79}$'),
    schema_version smallint not null check (schema_version = 1),
    binding_json jsonb not null check (jsonb_typeof(binding_json) = 'object'),
    timezone varchar(64) not null,
    view_mode varchar(16) not null check (view_mode in ('month', 'week', 'day')),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, view_key),
    constraint fk_project_work_item_calendar_preference_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_calendar_preference_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
);

create table project_work_item_calendar_window_index (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null,
    work_item_id uuid not null,
    source_work_item_version bigint not null check (source_work_item_version >= 0),
    start_date date not null,
    end_date date not null check (end_date >= start_date),
    all_day boolean not null,
    rebuilt_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, view_key, work_item_id),
    constraint fk_project_work_item_calendar_window_preference
        foreign key (workspace_id, space_id, user_id, view_key)
        references project_work_item_calendar_preferences(workspace_id, space_id, user_id, view_key),
    constraint fk_project_work_item_calendar_window_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
);

create index idx_project_work_item_calendar_window_range
    on project_work_item_calendar_window_index(
        workspace_id, space_id, user_id, view_key, start_date, end_date, work_item_id
    );

create table project_work_item_calendar_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null,
    work_item_id uuid,
    operation varchar(24) not null check (operation in ('save_preference', 'move_date', 'resize_date')),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    expected_version bigint not null check (expected_version >= 0),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_json jsonb,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_calendar_command
        unique (workspace_id, space_id, user_id, operation, request_id),
    constraint fk_project_work_item_calendar_command_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_calendar_command_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint ck_project_work_item_calendar_command_completion check (
        (status='pending' and response_json is null and completed_at is null)
        or (status='completed' and jsonb_typeof(response_json)='object' and completed_at is not null)
    )
);

create table project_work_item_calendar_projection_stats (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    view_key varchar(80) not null,
    render_count bigint not null default 0 check (render_count >= 0),
    last_window_days smallint not null check (last_window_days between 1 and 62),
    last_event_count smallint not null check (last_event_count between 0 and 100),
    last_overlap_lanes smallint not null check (last_overlap_lanes between 0 and 8),
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, view_key),
    constraint fk_project_work_item_calendar_stat_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id)
);

create function guard_project_work_item_calendar_command()
returns trigger
language plpgsql
as $$
begin
    if tg_op='DELETE' and current_setting('colla.project_space_cleanup', true)='on' then
        return old;
    end if;
    if tg_op='DELETE' or old.status='completed' then
        raise exception 'completed work item calendar commands are immutable' using errcode='23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_calendar_command
before update or delete on project_work_item_calendar_commands
for each row execute function guard_project_work_item_calendar_command();
