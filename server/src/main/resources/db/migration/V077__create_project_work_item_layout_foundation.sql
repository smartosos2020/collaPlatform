alter table project_work_item_field_definitions
    add constraint uk_project_work_item_fields_scope_id_key
    unique (workspace_id, space_id, type_definition_id, id, field_key);

create table project_work_item_layouts (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    layout_kind varchar(32) not null,
    config_hash varchar(64) not null,
    status varchar(32) not null default 'active',
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    aggregate_version bigint not null default 0,
    constraint uk_project_work_item_layouts_type_kind
        unique (workspace_id, space_id, type_definition_id, layout_kind),
    constraint uk_project_work_item_layouts_scope_id
        unique (workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_layouts_type_scope
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_layouts_created_by_workspace
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_layouts_updated_by_workspace
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_work_item_layouts_kind check (layout_kind in ('create', 'detail')),
    constraint ck_project_work_item_layouts_hash check (config_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_layouts_status check (status in ('active', 'disabled')),
    constraint ck_project_work_item_layouts_version check (aggregate_version >= 0)
);

create index idx_project_work_item_layouts_type_updated
    on project_work_item_layouts (workspace_id, space_id, type_definition_id, updated_at desc);

create table project_work_item_layout_nodes (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    layout_id uuid not null,
    node_key varchar(64) not null,
    parent_id uuid,
    node_type varchar(32) not null,
    field_id uuid,
    field_key varchar(64),
    sort_order integer not null,
    config jsonb not null,
    visibility_condition jsonb not null,
    status varchar(32) not null default 'active',
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_work_item_layout_nodes_layout_key
        unique (workspace_id, layout_id, node_key),
    constraint uk_project_work_item_layout_nodes_layout_id
        unique (workspace_id, layout_id, id),
    constraint fk_project_work_item_layout_nodes_layout_scope
        foreign key (workspace_id, space_id, type_definition_id, layout_id)
        references project_work_item_layouts(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_layout_nodes_parent
        foreign key (workspace_id, layout_id, parent_id)
        references project_work_item_layout_nodes(workspace_id, layout_id, id),
    constraint fk_project_work_item_layout_nodes_field_scope
        foreign key (workspace_id, space_id, type_definition_id, field_id, field_key)
        references project_work_item_field_definitions(workspace_id, space_id, type_definition_id, id, field_key),
    constraint fk_project_work_item_layout_nodes_created_by_workspace
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_layout_nodes_updated_by_workspace
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_work_item_layout_nodes_key
        check (node_key ~ '^[a-z][a-z0-9_]*$'),
    constraint ck_project_work_item_layout_nodes_type
        check (node_type in ('section', 'tab', 'column', 'field', 'summary')),
    constraint ck_project_work_item_layout_nodes_field
        check (
            (node_type = 'field' and field_id is not null and field_key is not null)
            or (node_type <> 'field' and field_id is null and field_key is null)
        ),
    constraint ck_project_work_item_layout_nodes_order check (sort_order >= 0),
    constraint ck_project_work_item_layout_nodes_config check (jsonb_typeof(config) = 'object'),
    constraint ck_project_work_item_layout_nodes_condition
        check (jsonb_typeof(visibility_condition) = 'object'),
    constraint ck_project_work_item_layout_nodes_status check (status in ('active', 'removed'))
);

create index idx_project_work_item_layout_nodes_tree
    on project_work_item_layout_nodes (workspace_id, layout_id, status, parent_id, sort_order, node_key);
create index idx_project_work_item_layout_nodes_field
    on project_work_item_layout_nodes (workspace_id, space_id, type_definition_id, field_id)
    where field_id is not null and status = 'active';

create table project_work_item_field_access_policies (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    layout_id uuid not null,
    field_id uuid not null,
    field_key varchar(64) not null,
    policy_key varchar(64) not null,
    policy jsonb not null,
    config_hash varchar(64) not null,
    status varchar(32) not null default 'active',
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_work_item_field_policies_type_key
        unique (workspace_id, layout_id, policy_key),
    constraint fk_project_work_item_field_policies_layout_scope
        foreign key (workspace_id, space_id, type_definition_id, layout_id)
        references project_work_item_layouts(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_field_policies_field_scope
        foreign key (workspace_id, space_id, type_definition_id, field_id, field_key)
        references project_work_item_field_definitions(workspace_id, space_id, type_definition_id, id, field_key),
    constraint fk_project_work_item_field_policies_created_by_workspace
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_field_policies_updated_by_workspace
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_work_item_field_policies_key
        check (policy_key ~ '^[a-z][a-z0-9_]*$'),
    constraint ck_project_work_item_field_policies_policy check (jsonb_typeof(policy) = 'object'),
    constraint ck_project_work_item_field_policies_hash check (config_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_field_policies_status check (status in ('active', 'removed'))
);

create index idx_project_work_item_field_policies_type
    on project_work_item_field_access_policies
    (workspace_id, layout_id, status, field_key, policy_key);

create table project_work_item_layout_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    request_id varchar(120) not null,
    operation varchar(64) not null,
    request_hash varchar(64) not null,
    status varchar(32) not null,
    response_layout_id uuid,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_layout_commands_request
        unique (workspace_id, request_id),
    constraint fk_project_work_item_layout_commands_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_layout_commands_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_layout_commands_response
        foreign key (workspace_id, space_id, type_definition_id, response_layout_id)
        references project_work_item_layouts(workspace_id, space_id, type_definition_id, id),
    constraint ck_project_work_item_layout_commands_hash
        check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_layout_commands_status
        check (status in ('pending', 'completed')),
    constraint ck_project_work_item_layout_commands_completion
        check (
            (status = 'pending' and completed_at is null)
            or (status = 'completed' and completed_at is not null)
        )
);

create index idx_project_work_item_layout_commands_type_created
    on project_work_item_layout_commands
    (workspace_id, space_id, type_definition_id, created_at desc);

create function guard_project_work_item_layout_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item layouts cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.layout_kind <> old.layout_kind then
        raise exception 'work item layout identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_layout_identity
before update or delete on project_work_item_layouts
for each row execute function guard_project_work_item_layout_identity();

create function guard_project_work_item_layout_node_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item layout nodes cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.layout_id <> old.layout_id
        or new.node_key <> old.node_key
        or new.node_type <> old.node_type
        or new.field_id is distinct from old.field_id
        or new.field_key is distinct from old.field_key then
        raise exception 'work item layout node identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_layout_node_identity
before update or delete on project_work_item_layout_nodes
for each row execute function guard_project_work_item_layout_node_identity();

create function guard_project_work_item_field_policy_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item field access policies cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.layout_id <> old.layout_id
        or new.field_id <> old.field_id
        or new.field_key <> old.field_key
        or new.policy_key <> old.policy_key then
        raise exception 'work item field access policy identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_field_policy_identity
before update or delete on project_work_item_field_access_policies
for each row execute function guard_project_work_item_field_policy_identity();
