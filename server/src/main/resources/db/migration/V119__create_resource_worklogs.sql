create table project_resource_worklogs (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    work_item_id uuid not null,
    user_id uuid not null,
    work_date date not null,
    duration_minutes smallint not null check (duration_minutes between 1 and 1440),
    source varchar(16) not null check (source in ('manual', 'import', 'proxy')),
    approval_state varchar(16) not null check (approval_state in ('draft', 'submitted', 'void')),
    current_revision bigint not null check (current_revision >= 1),
    aggregate_version bigint not null check (aggregate_version >= 1),
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_resource_worklog_identity unique (workspace_id, space_id, id),
    constraint fk_project_resource_worklog_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_resource_worklog_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_resource_worklog_user foreign key (workspace_id, user_id)
        references users(workspace_id, id),
    constraint fk_project_resource_worklog_actor foreign key (workspace_id, updated_by)
        references users(workspace_id, id)
);

create index idx_project_resource_worklog_list
    on project_resource_worklogs(workspace_id, space_id, work_date desc, id);
create index idx_project_resource_worklog_user_date
    on project_resource_worklogs(workspace_id, space_id, user_id, work_date desc);
create index idx_project_resource_worklog_item
    on project_resource_worklogs(workspace_id, space_id, work_item_id, approval_state);

create table project_resource_worklog_revisions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    worklog_id uuid not null,
    revision_number bigint not null check (revision_number >= 1),
    work_date date not null,
    duration_minutes smallint not null check (duration_minutes between 1 and 1440),
    source varchar(16) not null check (source in ('manual', 'import', 'proxy')),
    approval_state varchar(16) not null check (approval_state in ('draft', 'submitted', 'void')),
    reason varchar(500) not null default '',
    actor_id uuid not null,
    created_at timestamptz not null,
    constraint uk_project_resource_worklog_revision
        unique (workspace_id, space_id, worklog_id, revision_number),
    constraint fk_project_resource_worklog_revision_parent
        foreign key (workspace_id, space_id, worklog_id)
        references project_resource_worklogs(workspace_id, space_id, id),
    constraint fk_project_resource_worklog_revision_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create index idx_project_resource_worklog_revision_page
    on project_resource_worklog_revisions(
        workspace_id, space_id, worklog_id, revision_number desc
    );

create table project_resource_worklog_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    worklog_id uuid not null,
    operation varchar(16) not null
        check (operation in ('create', 'update', 'submit', 'withdraw', 'void')),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_resource_worklog_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_resource_worklog_command_parent
        foreign key (workspace_id, space_id, worklog_id)
        references project_resource_worklogs(workspace_id, space_id, id),
    constraint fk_project_resource_worklog_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create function guard_project_resource_worklog_revision()
returns trigger language plpgsql as $$
begin
    if current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'project resource worklog revisions are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_resource_worklog_revision_immutable
before update or delete on project_resource_worklog_revisions
for each row execute function guard_project_resource_worklog_revision();
