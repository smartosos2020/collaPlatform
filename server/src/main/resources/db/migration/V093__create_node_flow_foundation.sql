create table project_node_workflow_instances (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    type_definition_id uuid not null,
    type_version_id uuid not null,
    config_hash varchar(64) not null check (config_hash ~ '^[0-9a-f]{64}$'),
    status varchar(24) not null check (status in ('active', 'completed', 'terminated', 'failed')),
    work_item_version bigint not null check (work_item_version >= 0),
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    started_by uuid not null,
    started_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_node_workflow_instance_item
        unique (workspace_id, space_id, work_item_id),
    constraint uk_project_node_workflow_instance_scope
        unique (workspace_id, space_id, id),
    constraint fk_project_node_workflow_instance_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_instance_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_node_workflow_instance_version
        foreign key (workspace_id, space_id, type_definition_id, type_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_node_workflow_instance_started_by
        foreign key (workspace_id, started_by) references users(workspace_id, id),
    constraint fk_project_node_workflow_instance_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id)
);

create index idx_project_node_workflow_instances_projection
    on project_node_workflow_instances(
        workspace_id, space_id, type_definition_id, status, work_item_id
    );

create table project_node_workflow_tokens (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    node_key varchar(64) not null check (node_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    stage_key varchar(64) not null check (stage_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    status varchar(16) not null check (status in ('active', 'waiting', 'completed', 'canceled')),
    parent_token_id uuid,
    split_key varchar(64),
    join_key varchar(64),
    correlation_key varchar(120) not null,
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    entered_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_node_workflow_token_scope
        unique (workspace_id, space_id, instance_id, id),
    constraint fk_project_node_workflow_token_instance
        foreign key (workspace_id, space_id, instance_id)
        references project_node_workflow_instances(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_token_parent
        foreign key (workspace_id, space_id, instance_id, parent_token_id)
        references project_node_workflow_tokens(workspace_id, space_id, instance_id, id),
    constraint ck_project_node_workflow_token_split_key
        check (split_key is null or split_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    constraint ck_project_node_workflow_token_join_key
        check (join_key is null or join_key ~ '^[a-z][a-z0-9_]{0,63}$')
);

create index idx_project_node_workflow_tokens_active
    on project_node_workflow_tokens(
        workspace_id, space_id, instance_id, status, node_key, id
    );

create table project_node_workflow_tasks (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    token_id uuid not null,
    node_key varchar(64) not null check (node_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    assignment_strategy varchar(16) not null check (
        assignment_strategy in ('single', 'any', 'all', 'quorum')
    ),
    candidate_roles jsonb not null check (jsonb_typeof(candidate_roles) = 'array'),
    quorum_count integer check (quorum_count is null or quorum_count > 0),
    status varchar(16) not null check (status in ('pending', 'claimed', 'completed', 'canceled')),
    assignee_id uuid,
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    created_at timestamptz not null,
    claimed_at timestamptz,
    completed_at timestamptz,
    constraint uk_project_node_workflow_task_scope
        unique (workspace_id, space_id, instance_id, id),
    constraint fk_project_node_workflow_task_token
        foreign key (workspace_id, space_id, instance_id, token_id)
        references project_node_workflow_tokens(workspace_id, space_id, instance_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_task_assignee
        foreign key (workspace_id, assignee_id) references users(workspace_id, id),
    constraint ck_project_node_workflow_task_quorum
        check (
            (assignment_strategy = 'quorum' and quorum_count is not null)
            or (assignment_strategy <> 'quorum' and quorum_count is null)
        )
);

create index idx_project_node_workflow_tasks_candidate
    on project_node_workflow_tasks(
        workspace_id, space_id, status, assignee_id, created_at, id
    );

create table project_node_workflow_votes (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    task_id uuid not null,
    token_id uuid not null,
    node_key varchar(64) not null check (node_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    voter_id uuid not null,
    decision varchar(16) not null check (decision in ('approve', 'reject', 'abstain')),
    sequence_number bigint not null check (sequence_number > 0),
    public_payload jsonb not null default '{}'::jsonb check (jsonb_typeof(public_payload) = 'object'),
    occurred_at timestamptz not null,
    constraint uk_project_node_workflow_vote_actor
        unique (workspace_id, space_id, task_id, voter_id),
    constraint uk_project_node_workflow_vote_sequence
        unique (workspace_id, space_id, instance_id, sequence_number),
    constraint fk_project_node_workflow_vote_task
        foreign key (workspace_id, space_id, instance_id, task_id)
        references project_node_workflow_tasks(workspace_id, space_id, instance_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_vote_token
        foreign key (workspace_id, space_id, instance_id, token_id)
        references project_node_workflow_tokens(workspace_id, space_id, instance_id, id),
    constraint fk_project_node_workflow_vote_actor
        foreign key (workspace_id, voter_id) references users(workspace_id, id)
);

create table project_node_workflow_joins (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    join_key varchar(64) not null check (join_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    node_key varchar(64) not null check (node_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    correlation_key varchar(120) not null,
    policy varchar(16) not null check (policy in ('all', 'any', 'quorum')),
    expected_count integer not null check (expected_count > 0),
    quorum_count integer check (quorum_count is null or quorum_count > 0),
    arrived_count integer not null default 0 check (arrived_count >= 0),
    status varchar(16) not null check (status in ('waiting', 'released', 'canceled')),
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    created_at timestamptz not null,
    released_at timestamptz,
    constraint uk_project_node_workflow_join_correlation
        unique (workspace_id, space_id, instance_id, join_key, correlation_key),
    constraint uk_project_node_workflow_join_scope
        unique (workspace_id, space_id, instance_id, id),
    constraint fk_project_node_workflow_join_instance
        foreign key (workspace_id, space_id, instance_id)
        references project_node_workflow_instances(workspace_id, space_id, id)
        on delete cascade,
    constraint ck_project_node_workflow_join_quorum
        check (
            (policy = 'quorum' and quorum_count is not null and quorum_count <= expected_count)
            or (policy <> 'quorum' and quorum_count is null)
        ),
    constraint ck_project_node_workflow_join_arrivals
        check (arrived_count <= expected_count)
);

create table project_node_workflow_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    work_item_id uuid not null,
    instance_id uuid,
    operation varchar(24) not null check (
        operation in (
            'start', 'advance', 'claim', 'delegate', 'vote', 'complete', 'auto',
            'split', 'join', 'return', 'jump', 'terminate', 'compensate',
            'correct', 'upgrade', 'backfill'
        )
    ),
    node_key varchar(64),
    expected_work_item_version bigint not null check (expected_work_item_version >= 0),
    expected_instance_version bigint check (expected_instance_version is null or expected_instance_version >= 0),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_schema_version integer not null default 1 check (response_schema_version = 1),
    response_payload jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_node_workflow_command_request
        unique (workspace_id, work_item_id, operation, request_id),
    constraint fk_project_node_workflow_command_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_command_instance
        foreign key (workspace_id, space_id, instance_id)
        references project_node_workflow_instances(workspace_id, space_id, id),
    constraint fk_project_node_workflow_command_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_node_workflow_command_node_key
        check (node_key is null or node_key ~ '^[a-z][a-z0-9_]{0,63}$')
);

create index idx_project_node_workflow_commands_item
    on project_node_workflow_commands(
        workspace_id, space_id, work_item_id, created_at desc, id desc
    );

create table project_node_workflow_history (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    work_item_id uuid not null,
    sequence_number bigint not null check (sequence_number > 0),
    type_definition_id uuid not null,
    type_version_id uuid not null,
    config_hash varchar(64) not null check (config_hash ~ '^[0-9a-f]{64}$'),
    event_kind varchar(24) not null check (
        event_kind in (
            'started', 'entered', 'task_created', 'claimed', 'delegated', 'voted',
            'completed', 'split', 'joined', 'returned', 'jumped', 'terminated',
            'compensated', 'corrected', 'upgraded', 'backfilled'
        )
    ),
    node_key varchar(64),
    token_id uuid,
    task_id uuid,
    actor_id uuid not null,
    actor_class varchar(16) not null check (actor_class in ('user', 'system')),
    decision_reference varchar(160),
    correlation_id varchar(160) not null,
    causation_id varchar(160),
    public_payload jsonb not null default '{}'::jsonb check (jsonb_typeof(public_payload) = 'object'),
    occurred_at timestamptz not null,
    constraint uk_project_node_workflow_history_sequence
        unique (workspace_id, space_id, instance_id, sequence_number),
    constraint fk_project_node_workflow_history_instance
        foreign key (workspace_id, space_id, instance_id)
        references project_node_workflow_instances(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_history_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_node_workflow_history_version
        foreign key (workspace_id, space_id, type_definition_id, type_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_node_workflow_history_actor
        foreign key (workspace_id, actor_id) references users(workspace_id, id),
    constraint ck_project_node_workflow_history_node_key
        check (node_key is null or node_key ~ '^[a-z][a-z0-9_]{0,63}$')
);

create index idx_project_node_workflow_history_page
    on project_node_workflow_history(
        workspace_id, space_id, instance_id, sequence_number desc
    );

create function guard_project_node_workflow_instance()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'node workflow instances cannot be deleted directly' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.work_item_id <> old.work_item_id
        or new.type_definition_id <> old.type_definition_id
        or new.type_version_id <> old.type_version_id
        or new.config_hash <> old.config_hash
        or new.started_by <> old.started_by
        or new.started_at <> old.started_at then
        raise exception 'node workflow instance identity and binding are immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_node_workflow_instance
before update or delete on project_node_workflow_instances
for each row execute function guard_project_node_workflow_instance();

create function guard_project_node_workflow_token()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'node workflow tokens cannot be deleted directly' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.instance_id <> old.instance_id
        or new.node_key <> old.node_key
        or new.stage_key <> old.stage_key
        or new.parent_token_id is distinct from old.parent_token_id
        or new.split_key is distinct from old.split_key
        or new.join_key is distinct from old.join_key
        or new.correlation_key <> old.correlation_key
        or new.entered_at <> old.entered_at then
        raise exception 'node workflow token identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_node_workflow_token
before update or delete on project_node_workflow_tokens
for each row execute function guard_project_node_workflow_token();

create function guard_project_node_workflow_task()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'node workflow tasks cannot be deleted directly' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.instance_id <> old.instance_id
        or new.token_id <> old.token_id
        or new.node_key <> old.node_key
        or new.assignment_strategy <> old.assignment_strategy
        or new.candidate_roles <> old.candidate_roles
        or new.quorum_count is distinct from old.quorum_count
        or new.created_at <> old.created_at then
        raise exception 'node workflow task definition is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_node_workflow_task
before update or delete on project_node_workflow_tasks
for each row execute function guard_project_node_workflow_task();

create function guard_project_node_workflow_vote()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'node workflow votes are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_node_workflow_vote
before update or delete on project_node_workflow_votes
for each row execute function guard_project_node_workflow_vote();

create function guard_project_node_workflow_command()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'node workflow command receipts cannot be deleted' using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed node workflow command receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.work_item_id <> old.work_item_id
        or new.instance_id is distinct from old.instance_id
        or new.operation <> old.operation
        or new.node_key is distinct from old.node_key
        or new.expected_work_item_version <> old.expected_work_item_version
        or new.expected_instance_version is distinct from old.expected_instance_version
        or new.request_id <> old.request_id
        or new.request_hash <> old.request_hash
        or new.response_schema_version <> old.response_schema_version
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'node workflow command receipt identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_node_workflow_command
before update or delete on project_node_workflow_commands
for each row execute function guard_project_node_workflow_command();

create function guard_project_node_workflow_history()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'node workflow history is immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_node_workflow_history
before update or delete on project_node_workflow_history
for each row execute function guard_project_node_workflow_history();
