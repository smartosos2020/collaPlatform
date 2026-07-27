create table project_work_item_query_definitions (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    owner_user_id uuid not null,
    name varchar(120),
    schema_version smallint not null default 1 check (schema_version = 1),
    definition_hash varchar(64) not null check (definition_hash ~ '^[0-9a-f]{64}$'),
    definition_json jsonb not null check (jsonb_typeof(definition_json) = 'object'),
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    status varchar(16) not null default 'active' check (status in ('active', 'deleted')),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    constraint uk_project_work_item_query_definition_scope
        unique (workspace_id, space_id, id),
    constraint fk_project_work_item_query_definition_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_query_definition_owner
        foreign key (workspace_id, owner_user_id) references users(workspace_id, id),
    constraint fk_project_work_item_query_definition_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_query_definition_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_work_item_query_definition_deletion
        check (
            (status = 'active' and deleted_at is null)
            or (status = 'deleted' and deleted_at is not null)
        )
);

create index idx_project_work_item_query_definitions_owner
    on project_work_item_query_definitions(
        workspace_id, owner_user_id, status, updated_at desc, id desc
    );

create table project_work_item_query_receipts (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    definition_id uuid,
    operation varchar(32) not null check (
        operation in ('execute', 'explain', 'dry_run', 'rebuild_projection')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    query_hash varchar(64) not null check (query_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status in ('pending', 'completed', 'failed')),
    response_schema_version smallint not null default 1 check (response_schema_version = 1),
    safe_response jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_query_receipt_request
        unique (workspace_id, space_id, operation, request_id),
    constraint fk_project_work_item_query_receipt_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_query_receipt_definition
        foreign key (workspace_id, space_id, definition_id)
        references project_work_item_query_definitions(workspace_id, space_id, id),
    constraint fk_project_work_item_query_receipt_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_work_item_query_receipt_completion
        check (
            (status = 'pending' and safe_response is null and completed_at is null)
            or (status in ('completed', 'failed') and safe_response is not null and completed_at is not null)
        )
);

create index idx_project_work_item_query_receipts_timeline
    on project_work_item_query_receipts(
        workspace_id, space_id, created_at desc, id desc
    );

create table project_work_item_query_projection_stats (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    projection_name varchar(64) not null check (
        projection_name in ('system', 'field', 'participant', 'state', 'node', 'relation', 'hierarchy')
    ),
    projection_version bigint not null default 0 check (projection_version >= 0),
    row_count bigint not null default 0 check (row_count >= 0),
    stale_count bigint not null default 0 check (stale_count >= 0),
    last_rebuilt_at timestamptz,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, projection_name),
    constraint fk_project_work_item_query_projection_stats_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id)
);

create function guard_project_work_item_query_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item query receipts are immutable' using errcode = '23514';
    end if;
    if old.status <> 'pending' then
        raise exception 'completed work item query receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.definition_id is distinct from old.definition_id
        or new.operation <> old.operation
        or new.request_id <> old.request_id
        or new.request_hash <> old.request_hash
        or new.query_hash <> old.query_hash
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'work item query receipt identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_query_receipt
before update or delete on project_work_item_query_receipts
for each row execute function guard_project_work_item_query_receipt();
