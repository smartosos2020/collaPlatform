create table project_plans (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    schema_version smallint not null default 1 check (schema_version = 1),
    name varchar(120) not null check (length(trim(name)) between 1 and 120),
    description varchar(1000) not null default '',
    start_date date not null,
    end_date date not null check (end_date >= start_date),
    status varchar(16) not null default 'draft'
        check (status in ('draft', 'published', 'archived')),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    archived_at timestamptz,
    constraint uk_project_plan_scope unique (workspace_id, space_id, id),
    constraint fk_project_plan_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_plan_created_by foreign key (workspace_id, created_by)
        references users(workspace_id, id),
    constraint fk_project_plan_updated_by foreign key (workspace_id, updated_by)
        references users(workspace_id, id),
    constraint ck_project_plan_lifecycle check (
        (status = 'archived' and archived_at is not null)
        or (status <> 'archived' and archived_at is null)
    )
);

create index idx_project_plans_list
    on project_plans(workspace_id, space_id, status, updated_at desc, id);

create table project_plan_phases (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    plan_id uuid not null,
    phase_key varchar(64) not null check (phase_key ~ '^[a-z][a-z0-9_-]{0,63}$'),
    name varchar(120) not null check (length(trim(name)) between 1 and 120),
    position smallint not null check (position between 0 and 23),
    start_date date not null,
    end_date date not null check (end_date >= start_date),
    status varchar(16) not null check (status in ('planned', 'active', 'completed')),
    constraint uk_project_plan_phase_scope unique (workspace_id, space_id, plan_id, id),
    constraint uk_project_plan_phase_key unique (workspace_id, space_id, plan_id, phase_key),
    constraint uk_project_plan_phase_position unique (workspace_id, space_id, plan_id, position),
    constraint fk_project_plan_phase_plan foreign key (workspace_id, space_id, plan_id)
        references project_plans(workspace_id, space_id, id)
);

create index idx_project_plan_phases_order
    on project_plan_phases(workspace_id, space_id, plan_id, position, id);

create table project_plan_milestones (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    plan_id uuid not null,
    phase_id uuid not null,
    milestone_key varchar(64) not null
        check (milestone_key ~ '^[a-z][a-z0-9_-]{0,63}$'),
    name varchar(120) not null check (length(trim(name)) between 1 and 120),
    position smallint not null check (position between 0 and 99),
    target_date date not null,
    status varchar(16) not null check (status in ('planned', 'active', 'completed')),
    owner_user_id uuid,
    constraint uk_project_plan_milestone_scope
        unique (workspace_id, space_id, plan_id, id),
    constraint uk_project_plan_milestone_key
        unique (workspace_id, space_id, plan_id, milestone_key),
    constraint uk_project_plan_milestone_position
        unique (workspace_id, space_id, plan_id, position),
    constraint fk_project_plan_milestone_plan
        foreign key (workspace_id, space_id, plan_id)
        references project_plans(workspace_id, space_id, id),
    constraint fk_project_plan_milestone_phase
        foreign key (workspace_id, space_id, plan_id, phase_id)
        references project_plan_phases(workspace_id, space_id, plan_id, id),
    constraint fk_project_plan_milestone_owner
        foreign key (workspace_id, owner_user_id)
        references users(workspace_id, id)
);

create index idx_project_plan_milestones_order
    on project_plan_milestones(workspace_id, space_id, plan_id, position, id);

create table project_plan_links (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    plan_id uuid not null,
    milestone_id uuid not null,
    work_item_id uuid not null,
    source_work_item_version bigint not null check (source_work_item_version >= 1),
    created_by uuid not null,
    created_at timestamptz not null,
    constraint uk_project_plan_link_scope unique (workspace_id, space_id, plan_id, id),
    constraint uk_project_plan_link_item
        unique (workspace_id, space_id, plan_id, milestone_id, work_item_id),
    constraint fk_project_plan_link_plan foreign key (workspace_id, space_id, plan_id)
        references project_plans(workspace_id, space_id, id),
    constraint fk_project_plan_link_milestone
        foreign key (workspace_id, space_id, plan_id, milestone_id)
        references project_plan_milestones(workspace_id, space_id, plan_id, id),
    constraint fk_project_plan_link_work_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_plan_link_created_by foreign key (workspace_id, created_by)
        references users(workspace_id, id)
);

create index idx_project_plan_links_item
    on project_plan_links(workspace_id, space_id, work_item_id, plan_id);

create table project_plan_changes (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    plan_id uuid not null,
    change_sequence bigint not null check (change_sequence >= 1),
    operation varchar(16) not null
        check (operation in ('create', 'update', 'publish', 'archive', 'restore')),
    reason varchar(500) not null default '',
    actor_id uuid not null,
    plan_version bigint not null check (plan_version >= 1),
    occurred_at timestamptz not null,
    constraint uk_project_plan_change_sequence
        unique (workspace_id, space_id, plan_id, change_sequence),
    constraint fk_project_plan_change_plan foreign key (workspace_id, space_id, plan_id)
        references project_plans(workspace_id, space_id, id),
    constraint fk_project_plan_change_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create index idx_project_plan_changes_page
    on project_plan_changes(workspace_id, space_id, plan_id, change_sequence desc);

create table project_plan_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    plan_id uuid not null,
    operation varchar(16) not null
        check (operation in ('create', 'update', 'publish', 'archive', 'restore')),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_plan_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_plan_command_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_plan_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id),
    constraint fk_project_plan_command_plan foreign key (workspace_id, space_id, plan_id)
        references project_plans(workspace_id, space_id, id)
);

create function guard_project_plan_change()
returns trigger language plpgsql as $$
begin
    if current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'project plan changes are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_plan_change
before update or delete on project_plan_changes
for each row execute function guard_project_plan_change();
