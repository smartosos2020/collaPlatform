create table project_work_item_configuration_templates (
    id uuid primary key,
    owner_workspace_id uuid references workspaces(id),
    scope varchar(32) not null,
    template_key varchar(96) not null,
    name varchar(128) not null,
    description text,
    visibility varchar(32) not null,
    status varchar(32) not null,
    current_version_id uuid,
    aggregate_version bigint not null default 0,
    created_by uuid,
    created_at timestamptz not null,
    updated_by uuid,
    updated_at timestamptz not null,
    constraint ck_project_configuration_templates_scope
        check (
            (scope = 'platform' and owner_workspace_id is null and visibility = 'platform')
            or (scope = 'workspace' and owner_workspace_id is not null and visibility = 'workspace')
        ),
    constraint ck_project_configuration_templates_status
        check (status in ('active', 'withdrawn')),
    constraint ck_project_configuration_templates_actor
        check (
            (scope = 'platform' and created_by is null and updated_by is null)
            or (scope = 'workspace' and created_by is not null and updated_by is not null)
        )
);

create unique index uk_project_configuration_templates_platform_key
    on project_work_item_configuration_templates (template_key)
    where scope = 'platform';
create unique index uk_project_configuration_templates_workspace_key
    on project_work_item_configuration_templates (owner_workspace_id, template_key)
    where scope = 'workspace';
create unique index uk_project_configuration_templates_owner_id
    on project_work_item_configuration_templates (owner_workspace_id, id);
create index idx_project_configuration_templates_catalog
    on project_work_item_configuration_templates (scope, owner_workspace_id, status, updated_at desc);

create table project_work_item_configuration_template_versions (
    id uuid primary key,
    template_id uuid not null references project_work_item_configuration_templates(id),
    owner_workspace_id uuid,
    version_number integer not null,
    snapshot_schema_version smallint not null,
    config_hash varchar(64) not null,
    snapshot jsonb not null,
    source_space_id uuid,
    source_type_definition_id uuid,
    source_configuration_version_id uuid,
    source_catalog_version varchar(64),
    published_by uuid,
    published_at timestamptz not null,
    constraint uk_project_configuration_template_versions_number
        unique (template_id, version_number),
    constraint uk_project_configuration_template_versions_id
        unique (template_id, id),
    constraint fk_project_configuration_template_versions_owner
        foreign key (owner_workspace_id, template_id)
        references project_work_item_configuration_templates(owner_workspace_id, id),
    constraint ck_project_configuration_template_versions_schema
        check (snapshot_schema_version > 0),
    constraint ck_project_configuration_template_versions_hash
        check (config_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_configuration_template_versions_snapshot
        check (jsonb_typeof(snapshot) = 'object'),
    constraint ck_project_configuration_template_versions_source
        check (
            (source_catalog_version is not null and source_configuration_version_id is null)
            or (source_catalog_version is null and source_configuration_version_id is not null)
        )
);

alter table project_work_item_configuration_templates
    add constraint fk_project_configuration_templates_current_version
        foreign key (id, current_version_id)
        references project_work_item_configuration_template_versions(template_id, id);

create index idx_project_configuration_template_versions_history
    on project_work_item_configuration_template_versions (template_id, version_number desc);

create table project_work_item_configuration_template_installations (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    template_id uuid not null references project_work_item_configuration_templates(id),
    installed_version_id uuid not null,
    upstream_version_id uuid not null,
    status varchar(32) not null,
    last_lineage_summary jsonb not null default '{}'::jsonb,
    aggregate_version bigint not null default 0,
    installed_by uuid not null,
    installed_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    detached_by uuid,
    detached_at timestamptz,
    constraint uk_project_configuration_template_installations_target
        unique (workspace_id, space_id, type_definition_id),
    constraint uk_project_configuration_template_installations_id
        unique (workspace_id, space_id, type_definition_id, id),
    constraint fk_project_configuration_template_installations_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_configuration_template_installations_version
        foreign key (template_id, installed_version_id)
        references project_work_item_configuration_template_versions(template_id, id),
    constraint fk_project_configuration_template_installations_upstream
        foreign key (template_id, upstream_version_id)
        references project_work_item_configuration_template_versions(template_id, id),
    constraint fk_project_configuration_template_installations_installed_actor
        foreign key (workspace_id, installed_by) references users(workspace_id, id),
    constraint fk_project_configuration_template_installations_updated_actor
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint fk_project_configuration_template_installations_detached_actor
        foreign key (workspace_id, detached_by) references users(workspace_id, id),
    constraint ck_project_configuration_template_installations_status
        check (status in ('attached', 'detached')),
    constraint ck_project_configuration_template_installations_lifecycle
        check (
            (status = 'attached' and detached_by is null and detached_at is null)
            or (status = 'detached' and detached_by is not null and detached_at is not null)
        )
);

create index idx_project_configuration_template_installations_template
    on project_work_item_configuration_template_installations
    (template_id, upstream_version_id, status);

create table project_work_item_configuration_template_upgrade_history (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    installation_id uuid not null,
    operation varchar(32) not null,
    from_version_id uuid not null,
    to_version_id uuid not null,
    result_hash varchar(64) not null,
    result_summary jsonb not null,
    created_by uuid not null,
    created_at timestamptz not null,
    constraint fk_project_configuration_template_history_installation
        foreign key (workspace_id, space_id, type_definition_id, installation_id)
        references project_work_item_configuration_template_installations
        (workspace_id, space_id, type_definition_id, id),
    constraint fk_project_configuration_template_history_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_configuration_template_history_operation
        check (operation in ('install', 'upgrade', 'detach')),
    constraint ck_project_configuration_template_history_hash
        check (result_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_configuration_template_history_summary
        check (jsonb_typeof(result_summary) = 'object')
);

create index idx_project_configuration_template_history_target
    on project_work_item_configuration_template_upgrade_history
    (workspace_id, space_id, type_definition_id, created_at desc);

create table project_work_item_configuration_template_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    request_id varchar(120) not null,
    operation varchar(32) not null,
    request_hash varchar(64) not null,
    status varchar(32) not null,
    response_schema_version smallint not null default 1,
    response_payload jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_configuration_template_commands_request
        unique (workspace_id, space_id, type_definition_id, operation, request_id),
    constraint fk_project_configuration_template_commands_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_configuration_template_commands_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_configuration_template_commands_operation
        check (operation in ('install', 'upgrade', 'detach')),
    constraint ck_project_configuration_template_commands_hash
        check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_configuration_template_commands_status
        check (status in ('pending', 'completed')),
    constraint ck_project_configuration_template_commands_response
        check (
            (status = 'pending' and response_payload is null and completed_at is null)
            or (status = 'completed' and jsonb_typeof(response_payload) = 'object' and completed_at is not null)
        )
);

create index idx_project_configuration_template_commands_target
    on project_work_item_configuration_template_commands
    (workspace_id, space_id, type_definition_id, created_at desc);

create function guard_project_configuration_template_version_immutability()
returns trigger
language plpgsql
as $$
begin
    raise exception 'configuration template versions are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_configuration_template_version_immutability
before update or delete on project_work_item_configuration_template_versions
for each row execute function guard_project_configuration_template_version_immutability();

create function guard_project_configuration_template_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'configuration template receipts cannot be deleted' using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed configuration template receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.request_id <> old.request_id
        or new.operation <> old.operation
        or new.request_hash <> old.request_hash
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'configuration template receipt identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_configuration_template_receipt
before update or delete on project_work_item_configuration_template_commands
for each row execute function guard_project_configuration_template_receipt();
