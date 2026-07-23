create table project_work_item_field_definitions (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    field_key varchar(64) not null,
    name varchar(128) not null,
    description text not null default '',
    field_type varchar(64) not null,
    config jsonb not null,
    config_hash varchar(64) not null,
    sort_order integer not null default 0,
    status varchar(32) not null,
    is_system boolean not null default false,
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    aggregate_version bigint not null default 0,
    constraint uk_project_work_item_fields_type_key
        unique (workspace_id, space_id, type_definition_id, field_key),
    constraint uk_project_work_item_fields_workspace_id unique (workspace_id, id),
    constraint uk_project_work_item_fields_scope_id
        unique (workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_fields_type_scope
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_fields_created_by_workspace
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_fields_updated_by_workspace
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_work_item_fields_key
        check (field_key ~ '^[a-z][a-z0-9_]*$'),
    constraint ck_project_work_item_fields_name
        check (length(btrim(name)) between 1 and 128),
    constraint ck_project_work_item_fields_description
        check (length(description) <= 2000),
    constraint ck_project_work_item_fields_type
        check (field_type ~ '^[a-z][a-z0-9_]*$'),
    constraint ck_project_work_item_fields_config
        check (jsonb_typeof(config) = 'object'),
    constraint ck_project_work_item_fields_config_hash
        check (config_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_fields_sort_order check (sort_order >= 0),
    constraint ck_project_work_item_fields_status
        check (status in ('active', 'disabled', 'retired')),
    constraint ck_project_work_item_fields_aggregate_version
        check (aggregate_version >= 0)
);

create index idx_project_work_item_fields_type_status_order
    on project_work_item_field_definitions
    (workspace_id, space_id, type_definition_id, status, sort_order, id);
create index idx_project_work_item_fields_space_updated
    on project_work_item_field_definitions (workspace_id, space_id, updated_at desc);

create table project_work_item_field_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    request_id varchar(120) not null,
    operation varchar(64) not null,
    request_hash varchar(64) not null,
    status varchar(32) not null,
    response_field_id uuid,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_field_commands_request
        unique (workspace_id, request_id),
    constraint fk_project_work_item_field_commands_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_field_commands_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_field_commands_response
        foreign key (workspace_id, space_id, type_definition_id, response_field_id)
        references project_work_item_field_definitions(workspace_id, space_id, type_definition_id, id),
    constraint ck_project_work_item_field_commands_request_hash
        check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_field_commands_status
        check (status in ('pending', 'completed')),
    constraint ck_project_work_item_field_commands_completion
        check (
            (status = 'pending' and completed_at is null)
            or (status = 'completed' and completed_at is not null)
        )
);

create index idx_project_work_item_field_commands_type_created
    on project_work_item_field_commands
    (workspace_id, space_id, type_definition_id, created_at desc);

create function guard_project_work_item_field_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item field definitions cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.field_key <> old.field_key
        or new.field_type <> old.field_type
        or new.is_system <> old.is_system then
        raise exception 'work item field identity is immutable' using errcode = '23514';
    end if;
    if old.status = 'retired' and new.status <> old.status then
        raise exception 'retired work item fields cannot transition' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_field_identity
before update or delete on project_work_item_field_definitions
for each row execute function guard_project_work_item_field_identity();
