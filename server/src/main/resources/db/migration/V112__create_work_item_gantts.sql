create table project_work_item_gantt_preferences (
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null,
    schema_version integer not null default 1,
    binding_json jsonb not null,
    timezone varchar(80) not null,
    zoom varchar(16) not null,
    hierarchy_relation_key varchar(64) not null,
    expanded_node_ids jsonb not null default '[]'::jsonb,
    aggregate_version bigint not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (workspace_id, space_id, user_id, view_key),
    constraint fk_project_work_item_gantt_preferences_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_work_item_gantt_preferences_user
        foreign key (workspace_id, user_id)
        references users(workspace_id, id),
    constraint ck_project_work_item_gantt_preferences_schema
        check (schema_version = 1),
    constraint ck_project_work_item_gantt_preferences_zoom
        check (zoom in ('day', 'week', 'month')),
    constraint ck_project_work_item_gantt_preferences_version
        check (aggregate_version > 0),
    constraint ck_project_work_item_gantt_preferences_expanded
        check (jsonb_typeof(expanded_node_ids) = 'array'
            and jsonb_array_length(expanded_node_ids) <= 64)
);

create index idx_project_work_item_gantt_preferences_user
    on project_work_item_gantt_preferences(workspace_id, user_id, updated_at desc);

create table project_work_item_gantt_schedule_index (
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null,
    work_item_id uuid not null,
    source_work_item_version bigint not null,
    start_date date,
    end_date date,
    parent_work_item_id uuid,
    depth integer not null,
    rebuilt_at timestamptz not null default now(),
    primary key (workspace_id, space_id, user_id, view_key, work_item_id),
    constraint fk_project_work_item_gantt_index_preference
        foreign key (workspace_id, space_id, user_id, view_key)
        references project_work_item_gantt_preferences(
            workspace_id, space_id, user_id, view_key
        ) on delete cascade,
    constraint fk_project_work_item_gantt_index_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_gantt_index_parent
        foreign key (workspace_id, space_id, parent_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint ck_project_work_item_gantt_index_version
        check (source_work_item_version >= 0),
    constraint ck_project_work_item_gantt_index_depth
        check (depth between 0 and 32),
    constraint ck_project_work_item_gantt_index_range
        check (start_date is null and end_date is null
            or start_date is not null and end_date is not null and end_date >= start_date)
);

create index idx_project_work_item_gantt_schedule_window
    on project_work_item_gantt_schedule_index(
        workspace_id, space_id, user_id, view_key, start_date, end_date
    );

create table project_work_item_gantt_projection_stats (
    workspace_id uuid not null,
    space_id uuid not null,
    view_key varchar(80) not null,
    render_count bigint not null default 0,
    last_row_count integer not null default 0,
    last_dependency_count integer not null default 0,
    last_max_depth integer not null default 0,
    updated_at timestamptz not null default now(),
    primary key (workspace_id, space_id, view_key),
    constraint fk_project_work_item_gantt_stats_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint ck_project_work_item_gantt_stats_counts
        check (render_count >= 0
            and last_row_count between 0 and 100
            and last_dependency_count between 0 and 200
            and last_max_depth between 0 and 32)
);
