create table project_work_item_schedule_baselines (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    schema_version smallint not null default 1 check (schema_version = 1),
    name varchar(120) not null check (length(trim(name)) between 1 and 120),
    query_hash varchar(64) not null check (query_hash ~ '^[0-9a-f]{64}$'),
    binding_json jsonb not null check (jsonb_typeof(binding_json) = 'object'),
    window_start date not null,
    window_end date not null check (window_end >= window_start),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    status varchar(16) not null default 'active' check (status in ('active', 'deleted')),
    created_at timestamptz not null,
    expires_at timestamptz not null,
    deleted_at timestamptz,
    constraint uk_project_work_item_schedule_baseline_scope
        unique (workspace_id, space_id, id),
    constraint fk_project_work_item_schedule_baseline_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_work_item_schedule_baseline_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint ck_project_work_item_schedule_baseline_lifecycle
        check ((status = 'active' and deleted_at is null)
            or (status = 'deleted' and deleted_at is not null))
);

create index idx_project_work_item_schedule_baselines_list
    on project_work_item_schedule_baselines(
        workspace_id, space_id, user_id, status, created_at desc, id
    );

create table project_work_item_schedule_baseline_entries (
    workspace_id uuid not null,
    space_id uuid not null,
    baseline_id uuid not null,
    work_item_id uuid not null,
    work_item_version bigint not null check (work_item_version >= 0),
    start_date date,
    end_date date,
    parent_work_item_id uuid,
    depth smallint not null check (depth between 0 and 32),
    primary key (workspace_id, space_id, baseline_id, work_item_id),
    constraint fk_project_work_item_schedule_baseline_entry_baseline
        foreign key (workspace_id, space_id, baseline_id)
        references project_work_item_schedule_baselines(workspace_id, space_id, id),
    constraint fk_project_work_item_schedule_baseline_entry_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_schedule_baseline_entry_parent
        foreign key (workspace_id, space_id, parent_work_item_id)
        references project_work_items(workspace_id, space_id, id)
);

create table project_work_item_schedule_baseline_dependencies (
    workspace_id uuid not null,
    space_id uuid not null,
    baseline_id uuid not null,
    relation_id uuid not null,
    relation_version bigint not null check (relation_version >= 0),
    source_work_item_id uuid not null,
    target_work_item_id uuid not null,
    primary key (workspace_id, space_id, baseline_id, relation_id),
    constraint fk_project_work_item_schedule_baseline_dependency_baseline
        foreign key (workspace_id, space_id, baseline_id)
        references project_work_item_schedule_baselines(workspace_id, space_id, id),
    constraint fk_project_work_item_schedule_baseline_dependency_source
        foreign key (workspace_id, space_id, source_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_schedule_baseline_dependency_target
        foreign key (workspace_id, space_id, target_work_item_id)
        references project_work_items(workspace_id, space_id, id)
);

create table project_work_item_schedule_baseline_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    operation varchar(16) not null check (operation in ('create', 'delete')),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    baseline_id uuid,
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_work_item_schedule_baseline_command
        unique (workspace_id, space_id, user_id, operation, request_id),
    constraint fk_project_work_item_schedule_baseline_command_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_work_item_schedule_baseline_command_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint fk_project_work_item_schedule_baseline_command_baseline
        foreign key (workspace_id, space_id, baseline_id)
        references project_work_item_schedule_baselines(workspace_id, space_id, id)
);

create table project_work_item_timeline_index (
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(80) not null,
    event_id uuid not null,
    source_kind varchar(24) not null check (
        source_kind in ('activity', 'audit', 'workflow', 'relation')
    ),
    source_id uuid not null,
    work_item_id uuid,
    event_type varchar(128) not null,
    occurred_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, view_key, event_id),
    constraint fk_project_work_item_timeline_index_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_work_item_timeline_index_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
);

create index idx_project_work_item_timeline_index_page
    on project_work_item_timeline_index(
        workspace_id, space_id, user_id, view_key, occurred_at desc, event_id
    );

create function guard_project_work_item_schedule_baseline_entry()
returns trigger language plpgsql as $$
begin
    if current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'schedule baseline entries are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_work_item_schedule_baseline_entry
before update or delete on project_work_item_schedule_baseline_entries
for each row execute function guard_project_work_item_schedule_baseline_entry();

create trigger trg_project_work_item_schedule_baseline_dependency
before update or delete on project_work_item_schedule_baseline_dependencies
for each row execute function guard_project_work_item_schedule_baseline_entry();
