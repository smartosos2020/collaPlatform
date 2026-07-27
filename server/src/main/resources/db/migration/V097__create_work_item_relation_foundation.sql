create table project_work_item_relations (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    relation_key varchar(64) not null check (
        relation_key ~ '^[a-z][a-z0-9_]{0,63}$'
    ),
    relation_kind varchar(24) not null check (
        relation_kind in ('normal', 'parent_child', 'dependency', 'blocking')
    ),
    direction varchar(16) not null check (direction in ('directed', 'undirected')),
    definition_type_id uuid not null,
    definition_version_id uuid not null,
    definition_config_hash varchar(64) not null check (
        definition_config_hash ~ '^[0-9a-f]{64}$'
    ),
    source_work_item_id uuid not null,
    target_work_item_id uuid not null,
    status varchar(16) not null check (status in ('active', 'withdrawn')),
    version bigint not null default 0 check (version >= 0),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    withdrawn_by uuid,
    withdrawn_at timestamptz,
    withdrawal_reason_hash varchar(64),
    constraint uk_project_work_item_relation_scope
        unique (workspace_id, space_id, id),
    constraint ck_project_work_item_relation_withdrawal
        check (
            (status = 'active'
                and withdrawn_by is null
                and withdrawn_at is null
                and withdrawal_reason_hash is null)
            or
            (status = 'withdrawn'
                and withdrawn_by is not null
                and withdrawn_at is not null
                and withdrawal_reason_hash ~ '^[0-9a-f]{64}$')
        ),
    constraint fk_project_work_item_relation_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_work_item_relation_definition
        foreign key (workspace_id, space_id, definition_type_id, definition_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_relation_source
        foreign key (workspace_id, space_id, source_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_relation_target
        foreign key (workspace_id, space_id, target_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_relation_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_relation_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint fk_project_work_item_relation_withdrawn_by
        foreign key (workspace_id, withdrawn_by) references users(workspace_id, id)
);

create unique index uk_project_work_item_relations_active_edge
    on project_work_item_relations(
        workspace_id, space_id, relation_key, source_work_item_id, target_work_item_id
    )
    where status = 'active';

create index idx_project_work_item_relations_source
    on project_work_item_relations(
        workspace_id, space_id, source_work_item_id, status, relation_key, id
    );

create index idx_project_work_item_relations_target
    on project_work_item_relations(
        workspace_id, space_id, target_work_item_id, status, relation_key, id
    );

create table project_work_item_relation_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    relation_id uuid,
    operation varchar(32) not null check (
        operation in ('create', 'withdraw', 'restore', 'migrate', 'rebuild_projection')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_schema_version smallint not null default 1 check (response_schema_version = 1),
    response_relation_id uuid,
    response_relation_version bigint,
    response_payload jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_relation_command_request
        unique (workspace_id, space_id, operation, request_id),
    constraint fk_project_work_item_relation_command_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_work_item_relation_command_relation
        foreign key (workspace_id, space_id, relation_id)
        references project_work_item_relations(workspace_id, space_id, id),
    constraint fk_project_work_item_relation_command_response
        foreign key (workspace_id, space_id, response_relation_id)
        references project_work_item_relations(workspace_id, space_id, id),
    constraint fk_project_work_item_relation_command_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_work_item_relation_command_response
        check (
            (status = 'pending'
                and response_relation_id is null
                and response_relation_version is null
                and response_payload is null
                and completed_at is null)
            or
            (status = 'completed'
                and response_payload is not null
                and jsonb_typeof(response_payload) = 'object'
                and completed_at is not null)
        )
);

create table project_work_item_relation_history (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    relation_id uuid not null,
    relation_version bigint not null check (relation_version >= 0),
    event_kind varchar(24) not null check (
        event_kind in ('created', 'withdrawn', 'restored', 'migrated')
    ),
    relation_key varchar(64) not null check (
        relation_key ~ '^[a-z][a-z0-9_]{0,63}$'
    ),
    source_work_item_id uuid not null,
    target_work_item_id uuid not null,
    definition_type_id uuid not null,
    definition_version_id uuid not null,
    definition_config_hash varchar(64) not null check (
        definition_config_hash ~ '^[0-9a-f]{64}$'
    ),
    command_id uuid not null,
    safe_metadata jsonb not null default '{}'::jsonb check (
        jsonb_typeof(safe_metadata) = 'object'
    ),
    occurred_by uuid not null,
    occurred_at timestamptz not null,
    constraint uk_project_work_item_relation_history_version
        unique (workspace_id, space_id, relation_id, relation_version),
    constraint fk_project_work_item_relation_history_relation
        foreign key (workspace_id, space_id, relation_id)
        references project_work_item_relations(workspace_id, space_id, id),
    constraint fk_project_work_item_relation_history_source
        foreign key (workspace_id, space_id, source_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_relation_history_target
        foreign key (workspace_id, space_id, target_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_relation_history_definition
        foreign key (workspace_id, space_id, definition_type_id, definition_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_relation_history_command
        foreign key (command_id) references project_work_item_relation_commands(id),
    constraint fk_project_work_item_relation_history_actor
        foreign key (workspace_id, occurred_by) references users(workspace_id, id)
);

create index idx_project_work_item_relation_history_timeline
    on project_work_item_relation_history(
        workspace_id, space_id, relation_id, relation_version desc
    );

create table project_work_item_hierarchy_paths (
    workspace_id uuid not null,
    space_id uuid not null,
    relation_key varchar(64) not null,
    ancestor_work_item_id uuid not null,
    descendant_work_item_id uuid not null,
    depth smallint not null check (depth between 0 and 64),
    direct_relation_id uuid,
    projection_version bigint not null check (projection_version >= 0),
    rebuilt_at timestamptz not null,
    primary key (
        workspace_id, space_id, relation_key, ancestor_work_item_id, descendant_work_item_id
    ),
    constraint ck_project_work_item_hierarchy_path
        check (
            (depth = 0 and ancestor_work_item_id = descendant_work_item_id)
            or
            (depth > 0 and ancestor_work_item_id <> descendant_work_item_id)
        ),
    constraint fk_project_work_item_hierarchy_ancestor
        foreign key (workspace_id, space_id, ancestor_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_hierarchy_descendant
        foreign key (workspace_id, space_id, descendant_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_hierarchy_direct_relation
        foreign key (workspace_id, space_id, direct_relation_id)
        references project_work_item_relations(workspace_id, space_id, id)
);

create index idx_project_work_item_hierarchy_descendants
    on project_work_item_hierarchy_paths(
        workspace_id, space_id, relation_key, descendant_work_item_id, depth, ancestor_work_item_id
    );

create function guard_project_work_item_relation_command()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item relation command receipts cannot be deleted' using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed work item relation command receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.relation_id is distinct from old.relation_id
        or new.operation <> old.operation
        or new.request_id <> old.request_id
        or new.request_hash <> old.request_hash
        or new.response_schema_version <> old.response_schema_version
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'work item relation command identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_relation_command
before update or delete on project_work_item_relation_commands
for each row execute function guard_project_work_item_relation_command();

create function guard_project_work_item_relation_history()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'work item relation history is immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_work_item_relation_history
before update or delete on project_work_item_relation_history
for each row execute function guard_project_work_item_relation_history();
