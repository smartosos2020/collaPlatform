create table project_work_item_current_states (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    type_definition_id uuid not null,
    type_version_id uuid not null,
    config_hash varchar(64) not null check (config_hash ~ '^[0-9a-f]{64}$'),
    current_state_key varchar(64) not null check (current_state_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    work_item_version bigint not null check (work_item_version >= 0),
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    initialized_by uuid not null,
    initialized_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, work_item_id),
    constraint fk_project_work_item_current_states_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_work_item_current_states_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_current_states_version
        foreign key (workspace_id, space_id, type_definition_id, type_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_current_states_initialized_by
        foreign key (workspace_id, initialized_by) references users(workspace_id, id),
    constraint fk_project_work_item_current_states_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id)
);

create index idx_project_work_item_current_states_projection
    on project_work_item_current_states(
        workspace_id, space_id, type_definition_id, current_state_key, work_item_id
    );

create table project_work_item_workflow_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    operation varchar(32) not null check (
        operation in ('initialize', 'execute', 'return', 'reopen', 'terminate', 'restore', 'correct')
    ),
    action_key varchar(64),
    from_state_key varchar(64),
    expected_work_item_version bigint not null check (expected_work_item_version >= 0),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_schema_version integer not null default 1 check (response_schema_version = 1),
    response_payload jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_workflow_commands_request
        unique (workspace_id, work_item_id, operation, request_id),
    constraint fk_project_work_item_workflow_commands_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_work_item_workflow_commands_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_work_item_workflow_command_keys
        check (
            (operation = 'initialize' and action_key is null and from_state_key is null)
            or (
                operation <> 'initialize'
                and action_key ~ '^[a-z][a-z0-9_]{0,63}$'
                and from_state_key ~ '^[a-z][a-z0-9_]{0,63}$'
            )
        )
);

create index idx_project_work_item_workflow_commands_item
    on project_work_item_workflow_commands(
        workspace_id, space_id, work_item_id, created_at desc, id desc
    );

create table project_work_item_workflow_history (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    sequence_number bigint not null check (sequence_number > 0),
    type_definition_id uuid not null,
    type_version_id uuid not null,
    config_hash varchar(64) not null check (config_hash ~ '^[0-9a-f]{64}$'),
    from_state_key varchar(64),
    to_state_key varchar(64) not null check (to_state_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    action_key varchar(64),
    action_kind varchar(24) not null check (
        action_kind in ('initialize', 'forward', 'return', 'reopen', 'terminate', 'restore', 'correction')
    ),
    actor_id uuid not null,
    actor_class varchar(16) not null check (actor_class in ('user', 'system')),
    decision_reference varchar(160),
    correlation_id varchar(160) not null,
    causation_id varchar(160),
    public_payload jsonb not null default '{}'::jsonb
        check (jsonb_typeof(public_payload) = 'object'),
    occurred_at timestamptz not null,
    constraint uk_project_work_item_workflow_history_sequence
        unique (workspace_id, space_id, work_item_id, sequence_number),
    constraint fk_project_work_item_workflow_history_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_work_item_workflow_history_version
        foreign key (workspace_id, space_id, type_definition_id, type_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_workflow_history_actor
        foreign key (workspace_id, actor_id) references users(workspace_id, id),
    constraint ck_project_work_item_workflow_history_keys
        check (
            (action_kind = 'initialize' and from_state_key is null and action_key is null)
            or (
                action_kind <> 'initialize'
                and from_state_key ~ '^[a-z][a-z0-9_]{0,63}$'
                and action_key ~ '^[a-z][a-z0-9_]{0,63}$'
            )
        )
);

create index idx_project_work_item_workflow_history_page
    on project_work_item_workflow_history(
        workspace_id, space_id, work_item_id, sequence_number desc
    );

create function guard_project_work_item_current_state_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item current state cannot be deleted directly' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.work_item_id <> old.work_item_id
        or new.type_definition_id <> old.type_definition_id
        or new.type_version_id <> old.type_version_id
        or new.config_hash <> old.config_hash
        or new.initialized_by <> old.initialized_by
        or new.initialized_at <> old.initialized_at then
        raise exception 'work item current state identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_current_state_identity
before update or delete on project_work_item_current_states
for each row execute function guard_project_work_item_current_state_identity();

create function guard_project_work_item_workflow_command()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'workflow command receipts cannot be deleted' using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed workflow command receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.work_item_id <> old.work_item_id
        or new.operation <> old.operation
        or new.action_key is distinct from old.action_key
        or new.from_state_key is distinct from old.from_state_key
        or new.expected_work_item_version <> old.expected_work_item_version
        or new.request_id <> old.request_id
        or new.request_hash <> old.request_hash
        or new.response_schema_version <> old.response_schema_version
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'workflow command receipt identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_workflow_command
before update or delete on project_work_item_workflow_commands
for each row execute function guard_project_work_item_workflow_command();

create function guard_project_work_item_workflow_history()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'work item workflow history is immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_work_item_workflow_history
before update or delete on project_work_item_workflow_history
for each row execute function guard_project_work_item_workflow_history();
