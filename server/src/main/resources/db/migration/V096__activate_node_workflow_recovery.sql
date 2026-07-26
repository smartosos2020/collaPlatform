alter table project_node_workflow_instances
    add column recovery_count integer not null default 0 check (recovery_count >= 0),
    add column last_recovery_at timestamptz;

create index idx_project_node_workflow_instances_binding
    on project_node_workflow_instances(
        workspace_id, space_id, type_definition_id, type_version_id, status, work_item_id
    );

create table project_node_workflow_compensation_runs (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    command_id uuid not null,
    command_key varchar(64) not null check (command_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    status varchar(16) not null check (status in ('pending', 'running', 'failed', 'completed')),
    next_step integer not null default 0 check (next_step >= 0),
    total_steps integer not null check (total_steps between 0 and 64),
    failure_code varchar(80),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_node_workflow_compensation_command unique (command_id),
    constraint uk_project_node_workflow_compensation_scope
        unique (workspace_id, space_id, instance_id, id),
    constraint fk_project_node_workflow_compensation_instance
        foreign key (workspace_id, space_id, instance_id)
        references project_node_workflow_instances(workspace_id, space_id, id),
    constraint fk_project_node_workflow_compensation_command
        foreign key (command_id) references project_node_workflow_commands(id),
    constraint fk_project_node_workflow_compensation_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id)
);

create table project_node_workflow_compensation_steps (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    run_id uuid not null,
    compensation_key varchar(64) not null check (
        compensation_key ~ '^[a-z][a-z0-9_]{0,63}$'
    ),
    action_key varchar(64) not null check (
        action_key in ('record_audit_marker', 'close_open_work')
    ),
    sort_order integer not null check (sort_order >= 0),
    status varchar(16) not null check (status in ('pending', 'failed', 'completed')),
    attempt_count integer not null default 0 check (attempt_count >= 0),
    failure_code varchar(80),
    completed_at timestamptz,
    constraint uk_project_node_workflow_compensation_step
        unique (workspace_id, space_id, instance_id, run_id, compensation_key),
    constraint uk_project_node_workflow_compensation_order
        unique (workspace_id, space_id, instance_id, run_id, sort_order),
    constraint fk_project_node_workflow_compensation_step_run
        foreign key (workspace_id, space_id, instance_id, run_id)
        references project_node_workflow_compensation_runs(workspace_id, space_id, instance_id, id)
        on delete cascade
);

create index idx_project_node_workflow_compensation_resume
    on project_node_workflow_compensation_runs(
        workspace_id, space_id, status, updated_at, id
    )
    where status in ('pending', 'running', 'failed');

create table project_node_workflow_backfill_batches (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    target_type_version_id uuid not null,
    target_config_hash varchar(64) not null check (target_config_hash ~ '^[0-9a-f]{64}$'),
    target_entry_node_key varchar(64) not null check (
        target_entry_node_key ~ '^[a-z][a-z0-9_]{0,63}$'
    ),
    requested_count integer not null check (requested_count between 1 and 500),
    completed_count integer not null default 0 check (completed_count >= 0),
    failed_count integer not null default 0 check (failed_count >= 0),
    manifest_hash varchar(64) not null check (manifest_hash ~ '^[0-9a-f]{64}$'),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    reason_hash varchar(64) not null check (reason_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status in ('planned', 'running', 'partial', 'completed')),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_project_node_workflow_backfill_request
        unique (workspace_id, space_id, request_id),
    constraint uk_project_node_workflow_backfill_scope
        unique (workspace_id, space_id, id),
    constraint fk_project_node_workflow_backfill_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_node_workflow_backfill_version
        foreign key (workspace_id, space_id, type_definition_id, target_type_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_node_workflow_backfill_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id)
);

create table project_node_workflow_backfill_units (
    workspace_id uuid not null,
    space_id uuid not null,
    batch_id uuid not null,
    work_item_id uuid not null,
    source_type_version_id uuid not null,
    source_config_hash varchar(64) not null check (source_config_hash ~ '^[0-9a-f]{64}$'),
    source_work_item_version bigint not null check (source_work_item_version >= 0),
    status varchar(16) not null check (status in ('pending', 'failed', 'completed')),
    failure_code varchar(80),
    failure_message varchar(500),
    target_work_item_version bigint,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, batch_id, work_item_id),
    constraint fk_project_node_workflow_backfill_unit_batch
        foreign key (workspace_id, space_id, batch_id)
        references project_node_workflow_backfill_batches(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_backfill_unit_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
);

create index idx_project_node_workflow_backfill_retry
    on project_node_workflow_backfill_units(
        workspace_id, space_id, batch_id, status, work_item_id
    );

create or replace function guard_project_node_workflow_instance()
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
        or new.started_by <> old.started_by
        or new.started_at <> old.started_at then
        raise exception 'node workflow instance identity is immutable' using errcode = '23514';
    end if;
    if (new.type_version_id <> old.type_version_id or new.config_hash <> old.config_hash)
        and coalesce(current_setting('colla.node_workflow_upgrade', true), '') <> 'on' then
        raise exception 'node workflow binding changes require the upgrade command' using errcode = '23514';
    end if;
    return new;
end;
$$;

create function guard_project_node_workflow_backfill_batch()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'node workflow backfill batches cannot be deleted' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.target_type_version_id <> old.target_type_version_id
        or new.target_config_hash <> old.target_config_hash
        or new.target_entry_node_key <> old.target_entry_node_key
        or new.requested_count <> old.requested_count
        or new.manifest_hash <> old.manifest_hash
        or new.request_id <> old.request_id
        or new.request_hash <> old.request_hash
        or new.reason_hash <> old.reason_hash
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'node workflow backfill manifest is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_node_workflow_backfill_batch
before update or delete on project_node_workflow_backfill_batches
for each row execute function guard_project_node_workflow_backfill_batch();
