insert into object_type_rules (id, object_type, web_path_pattern, deep_link_pattern, created_at)
values (
    '00000000-0000-0000-0000-000000000209',
    'work_item',
    '/project-spaces/{spaceId}/work-items/{id}',
    'colla://work-item/{id}',
    now()
)
on conflict (object_type) do update
set web_path_pattern = excluded.web_path_pattern,
    deep_link_pattern = excluded.deep_link_pattern;

create table project_work_item_counters (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    next_number bigint not null default 1 check (next_number > 0),
    primary key (workspace_id, space_id, type_definition_id),
    constraint fk_project_work_item_counters_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id)
);

create table project_work_items (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    type_version_id uuid not null,
    config_hash varchar(64) not null check (config_hash ~ '^[0-9a-f]{64}$'),
    item_number bigint not null check (item_number > 0),
    display_key varchar(96) not null,
    title varchar(500) not null,
    field_values jsonb not null default '{}'::jsonb check (jsonb_typeof(field_values) = 'object'),
    status varchar(16) not null default 'active' check (status in ('active', 'archived')),
    version bigint not null default 0 check (version >= 0),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    archived_at timestamptz,
    constraint uk_project_work_items_scope_id unique (workspace_id, space_id, id),
    constraint uk_project_work_items_scope_number
        unique (workspace_id, space_id, type_definition_id, item_number),
    constraint uk_project_work_items_scope_display_key unique (workspace_id, space_id, display_key),
    constraint fk_project_work_items_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_items_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_items_type_version
        foreign key (workspace_id, space_id, type_definition_id, type_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_items_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_items_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id)
);

create index idx_project_work_items_space_page
    on project_work_items(workspace_id, space_id, updated_at desc, id desc);
create index idx_project_work_items_type_page
    on project_work_items(workspace_id, space_id, type_definition_id, updated_at desc, id desc);

create table project_work_item_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid,
    operation varchar(32) not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_schema_version integer not null default 1 check (response_schema_version = 1),
    response_payload jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_commands_request unique (workspace_id, operation, request_id),
    constraint fk_project_work_item_commands_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_commands_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_commands_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id)
);

create index idx_project_work_item_commands_item
    on project_work_item_commands(workspace_id, space_id, work_item_id, created_at desc);

create function guard_project_work_item_command_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item command receipts cannot be deleted' using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed work item command receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or (old.work_item_id is not null and new.work_item_id is distinct from old.work_item_id)
        or new.operation <> old.operation
        or new.request_id <> old.request_id
        or new.request_hash <> old.request_hash
        or new.response_schema_version <> old.response_schema_version
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'work item command receipt identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_command_receipt
before update or delete on project_work_item_commands
for each row execute function guard_project_work_item_command_receipt();
