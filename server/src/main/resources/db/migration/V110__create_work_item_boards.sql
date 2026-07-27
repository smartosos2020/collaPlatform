create table project_work_item_board_preferences (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null check (view_key ~ '^[a-z][a-z0-9._-]{0,79}$'),
    schema_version smallint not null check (schema_version = 1),
    column_field varchar(120) not null,
    swimlane_field varchar(120),
    columns_json jsonb not null check (jsonb_typeof(columns_json) = 'array'),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, view_key),
    constraint fk_project_work_item_board_preference_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_board_preference_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
);

create table project_work_item_board_orders (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null,
    work_item_id uuid not null,
    column_key varchar(120) not null,
    swimlane_key varchar(120) not null,
    rank bigint not null check (rank >= 0),
    source_work_item_version bigint not null check (source_work_item_version >= 0),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, view_key, work_item_id),
    constraint fk_project_work_item_board_order_preference
        foreign key (workspace_id, space_id, user_id, view_key)
        references project_work_item_board_preferences(workspace_id, space_id, user_id, view_key),
    constraint fk_project_work_item_board_order_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
);

create index idx_project_work_item_board_orders_lane
    on project_work_item_board_orders(
        workspace_id, space_id, user_id, view_key, column_key, swimlane_key, rank, work_item_id
    );

create table project_work_item_board_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null,
    work_item_id uuid,
    operation varchar(24) not null check (
        operation in ('save_preference', 'move_state', 'move_node', 'reorder')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    expected_version bigint not null check (expected_version >= 0),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_json jsonb,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_board_command
        unique (workspace_id, space_id, user_id, operation, request_id),
    constraint fk_project_work_item_board_command_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_board_command_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint ck_project_work_item_board_command_completion check (
        (status='pending' and response_json is null and completed_at is null)
        or (status='completed' and jsonb_typeof(response_json)='object' and completed_at is not null)
    )
);

create table project_work_item_board_projection_stats (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    view_key varchar(80) not null,
    render_count bigint not null default 0 check (render_count >= 0),
    last_column_count smallint not null check (last_column_count between 1 and 12),
    last_lane_count smallint not null check (last_lane_count between 1 and 24),
    last_card_count smallint not null check (last_card_count between 0 and 100),
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, view_key),
    constraint fk_project_work_item_board_projection_stat_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id)
);

create function guard_project_work_item_board_command()
returns trigger
language plpgsql
as $$
begin
    if tg_op='DELETE' and current_setting('colla.project_space_cleanup', true)='on' then
        return old;
    end if;
    if tg_op='DELETE' or old.status='completed' then
        raise exception 'completed work item board commands are immutable' using errcode='23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_board_command
before update or delete on project_work_item_board_commands
for each row execute function guard_project_work_item_board_command();
