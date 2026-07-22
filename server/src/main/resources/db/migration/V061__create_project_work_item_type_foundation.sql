create table project_work_item_types (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null references project_spaces(id),
    type_key varchar(64) not null,
    name varchar(128) not null,
    icon varchar(64) not null default '',
    description text not null default '',
    sort_order integer not null default 0,
    status varchar(32) not null,
    is_system boolean not null default false,
    current_version_id uuid not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null,
    updated_by uuid not null references users(id),
    updated_at timestamptz not null,
    aggregate_version bigint not null default 0,
    constraint uk_project_work_item_types_space_key unique (space_id, type_key),
    constraint uk_project_work_item_types_workspace_id unique (workspace_id, id),
    constraint uk_project_work_item_types_workspace_space_id unique (workspace_id, space_id, id),
    constraint fk_project_work_item_types_space_workspace
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_types_created_by_workspace
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_types_updated_by_workspace
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_work_item_types_key
        check (type_key ~ '^[a-z][a-z0-9_]*$'),
    constraint ck_project_work_item_types_name
        check (length(btrim(name)) between 1 and 128),
    constraint ck_project_work_item_types_description
        check (length(description) <= 2000),
    constraint ck_project_work_item_types_sort_order
        check (sort_order >= 0),
    constraint ck_project_work_item_types_status
        check (status in ('active', 'disabled', 'retired')),
    constraint ck_project_work_item_types_aggregate_version
        check (aggregate_version >= 0)
);

create table project_work_item_type_versions (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null references project_spaces(id),
    type_definition_id uuid not null references project_work_item_types(id),
    version_number integer not null,
    config_hash varchar(64) not null,
    status varchar(32) not null,
    config jsonb not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null,
    published_by uuid,
    published_at timestamptz,
    constraint uk_project_work_item_type_versions_definition_number
        unique (type_definition_id, version_number),
    constraint uk_project_work_item_type_versions_workspace_id
        unique (workspace_id, id),
    constraint uk_project_work_item_type_versions_current_reference
        unique (workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_type_versions_space_workspace
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_type_versions_definition_scope
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_type_versions_created_by_workspace
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_type_versions_published_by_workspace
        foreign key (workspace_id, published_by) references users(workspace_id, id),
    constraint ck_project_work_item_type_versions_number
        check (version_number > 0),
    constraint ck_project_work_item_type_versions_hash
        check (config_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_type_versions_status
        check (status in ('draft', 'published', 'superseded')),
    constraint ck_project_work_item_type_versions_config
        check (jsonb_typeof(config) = 'object'),
    constraint ck_project_work_item_type_versions_publication
        check (
            (status = 'draft' and published_by is null and published_at is null)
            or (status in ('published', 'superseded') and published_by is not null and published_at is not null)
        )
);

alter table project_work_item_types
    add constraint fk_project_work_item_types_current_version
        foreign key (workspace_id, space_id, id, current_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id)
        deferrable initially deferred;

create index idx_project_work_item_types_space_status_order
    on project_work_item_types (workspace_id, space_id, status, sort_order, id);
create index idx_project_work_item_types_workspace_status
    on project_work_item_types (workspace_id, status, updated_at desc);
create index idx_project_work_item_type_versions_definition
    on project_work_item_type_versions (workspace_id, space_id, type_definition_id, version_number desc);

create function guard_project_work_item_type_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item type definitions cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_key <> old.type_key
        or new.is_system <> old.is_system then
        raise exception 'work item type identity is immutable' using errcode = '23514';
    end if;
    if old.status = 'retired' and new.status <> old.status then
        raise exception 'retired work item types cannot transition' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_type_identity
before update or delete on project_work_item_types
for each row execute function guard_project_work_item_type_identity();

create function guard_project_work_item_type_version_immutability()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and old.status in ('published', 'superseded') then
        raise exception 'published work item type versions are immutable' using errcode = '23514';
    end if;
    if tg_op = 'UPDATE' and old.status = 'published'
        and new.status = 'superseded'
        and new.id = old.id
        and new.workspace_id = old.workspace_id
        and new.space_id = old.space_id
        and new.type_definition_id = old.type_definition_id
        and new.version_number = old.version_number
        and new.config_hash = old.config_hash
        and new.config = old.config
        and new.created_by = old.created_by
        and new.created_at = old.created_at
        and new.published_by = old.published_by
        and new.published_at = old.published_at then
        return new;
    end if;
    if tg_op = 'UPDATE' and old.status in ('published', 'superseded') then
        raise exception 'published work item type versions are immutable' using errcode = '23514';
    end if;
    return case when tg_op = 'DELETE' then old else new end;
end;
$$;

create trigger trg_project_work_item_type_version_immutability
before update or delete on project_work_item_type_versions
for each row execute function guard_project_work_item_type_version_immutability();
