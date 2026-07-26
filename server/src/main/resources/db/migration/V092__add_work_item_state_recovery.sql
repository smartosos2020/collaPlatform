create table project_work_item_state_backfill_batches (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    target_type_version_id uuid not null,
    target_config_hash varchar(64) not null check (target_config_hash ~ '^[0-9a-f]{64}$'),
    target_state_key varchar(64) not null check (target_state_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    status varchar(24) not null check (
        status in ('planned', 'running', 'completed', 'partial_failed', 'failed')
    ),
    requested_count integer not null check (requested_count between 1 and 500),
    completed_count integer not null default 0 check (completed_count >= 0),
    failed_count integer not null default 0 check (failed_count >= 0),
    manifest_hash varchar(64) not null check (manifest_hash ~ '^[0-9a-f]{64}$'),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    reason_hash varchar(64) not null check (reason_hash ~ '^[0-9a-f]{64}$'),
    summary jsonb not null default '{}'::jsonb check (jsonb_typeof(summary) = 'object'),
    created_by uuid not null,
    created_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    constraint uk_project_work_item_state_backfill_request
        unique (workspace_id, space_id, request_id),
    constraint uk_project_work_item_state_backfill_scope
        unique (workspace_id, space_id, id),
    constraint fk_project_work_item_state_backfill_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_work_item_state_backfill_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_state_backfill_version
        foreign key (
            workspace_id, space_id, type_definition_id, target_type_version_id
        ) references project_work_item_type_versions(
            workspace_id, space_id, type_definition_id, id
        ),
    constraint fk_project_work_item_state_backfill_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id)
);

create table project_work_item_state_backfill_units (
    workspace_id uuid not null,
    space_id uuid not null,
    batch_id uuid not null,
    work_item_id uuid not null,
    source_type_version_id uuid not null,
    source_config_hash varchar(64) not null check (source_config_hash ~ '^[0-9a-f]{64}$'),
    source_work_item_version bigint not null check (source_work_item_version >= 0),
    target_state_key varchar(64) not null check (target_state_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    status varchar(16) not null check (status in ('pending', 'completed', 'failed')),
    attempt_count integer not null default 0 check (attempt_count >= 0),
    error_code varchar(80),
    error_message varchar(500),
    result_work_item_version bigint,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, batch_id, work_item_id),
    constraint fk_project_work_item_state_backfill_units_batch
        foreign key (workspace_id, space_id, batch_id)
        references project_work_item_state_backfill_batches(workspace_id, space_id, id),
    constraint fk_project_work_item_state_backfill_units_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
);

create index idx_project_work_item_state_backfill_units_resume
    on project_work_item_state_backfill_units(
        workspace_id, space_id, batch_id, status, work_item_id
    );

create or replace function guard_project_work_item_current_state_identity()
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
        or new.initialized_by <> old.initialized_by
        or new.initialized_at <> old.initialized_at then
        raise exception 'work item current state identity is immutable' using errcode = '23514';
    end if;
    if (
        new.type_version_id <> old.type_version_id
        or new.config_hash <> old.config_hash
    ) and current_setting('colla.workflow_binding_upgrade', true) is distinct from 'on' then
        raise exception 'workflow binding changes require the controlled upgrade command'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create function guard_project_work_item_state_backfill_batch()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'state backfill batches cannot be deleted' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.target_type_version_id <> old.target_type_version_id
        or new.target_config_hash <> old.target_config_hash
        or new.target_state_key <> old.target_state_key
        or new.requested_count <> old.requested_count
        or new.manifest_hash <> old.manifest_hash
        or new.request_id <> old.request_id
        or new.request_hash <> old.request_hash
        or new.reason_hash <> old.reason_hash
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'state backfill batch manifest is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_state_backfill_batch
before update or delete on project_work_item_state_backfill_batches
for each row execute function guard_project_work_item_state_backfill_batch();

create function guard_project_work_item_state_backfill_unit()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'state backfill units cannot be deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.batch_id <> old.batch_id
        or new.work_item_id <> old.work_item_id
        or new.source_type_version_id <> old.source_type_version_id
        or new.source_config_hash <> old.source_config_hash
        or new.source_work_item_version <> old.source_work_item_version
        or new.target_state_key <> old.target_state_key
        or new.created_at <> old.created_at then
        raise exception 'state backfill unit manifest is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_state_backfill_unit
before update or delete on project_work_item_state_backfill_units
for each row execute function guard_project_work_item_state_backfill_unit();
