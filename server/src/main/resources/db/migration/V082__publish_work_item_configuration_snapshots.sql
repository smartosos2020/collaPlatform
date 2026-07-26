alter table project_work_item_type_versions
    add column snapshot_schema_version smallint not null default 0,
    add column source_draft_id uuid,
    add column rollback_source_version_id uuid,
    add constraint fk_project_work_item_type_versions_source_draft
        foreign key (workspace_id, space_id, type_definition_id, source_draft_id)
        references project_work_item_configuration_drafts(workspace_id, space_id, type_definition_id, id),
    add constraint fk_project_work_item_type_versions_rollback_source
        foreign key (workspace_id, space_id, type_definition_id, rollback_source_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    add constraint ck_project_work_item_type_versions_snapshot_schema
        check (snapshot_schema_version between 0 and 32767);

comment on column project_work_item_type_versions.snapshot_schema_version is
    '0 identifies legacy partial configuration; 1+ identifies a complete self-contained configuration snapshot.';

alter table project_work_item_configuration_drafts
    add column source_version_id uuid,
    add column lineage_kind varchar(32) not null default 'live_edit',
    add constraint fk_project_work_item_configuration_drafts_source_version
        foreign key (workspace_id, space_id, type_definition_id, source_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    add constraint ck_project_work_item_configuration_drafts_lineage
        check (lineage_kind in ('live_edit', 'legacy_import', 'rollback'));

update project_work_item_configuration_drafts
   set lineage_kind = 'legacy_import'
 where source_legacy_version_id is not null;

create table project_work_item_configuration_publication_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    request_id varchar(120) not null,
    operation varchar(64) not null,
    request_hash varchar(64) not null,
    status varchar(32) not null,
    response_schema_version smallint not null default 1,
    response_version_id uuid,
    response_version_number integer,
    response_config_hash varchar(64),
    response_payload jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_configuration_publication_request
        unique (workspace_id, space_id, type_definition_id, operation, request_id),
    constraint fk_project_work_item_configuration_publication_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_configuration_publication_version
        foreign key (workspace_id, space_id, type_definition_id, response_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_configuration_publication_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_work_item_configuration_publication_operation
        check (operation in ('publish', 'prepare_rollback')),
    constraint ck_project_work_item_configuration_publication_hash
        check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_configuration_publication_status
        check (status in ('pending', 'completed')),
    constraint ck_project_work_item_configuration_publication_response_schema
        check (response_schema_version = 1),
    constraint ck_project_work_item_configuration_publication_response
        check (
            (
                status = 'pending'
                and response_version_id is null
                and response_version_number is null
                and response_config_hash is null
                and response_payload is null
                and completed_at is null
            )
            or (
                status = 'completed'
                and response_version_id is not null
                and response_version_number > 0
                and response_config_hash ~ '^[0-9a-f]{64}$'
                and jsonb_typeof(response_payload) = 'object'
                and completed_at is not null
            )
        )
);

create index idx_project_work_item_configuration_publication_type_created
    on project_work_item_configuration_publication_commands
    (workspace_id, space_id, type_definition_id, created_at desc);

create or replace function guard_project_work_item_type_version_immutability()
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
        and new.snapshot_schema_version = old.snapshot_schema_version
        and new.source_draft_id is not distinct from old.source_draft_id
        and new.rollback_source_version_id is not distinct from old.rollback_source_version_id
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

create or replace function guard_project_work_item_configuration_draft_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item configuration drafts cannot be physically deleted'
            using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.source_legacy_version_id is distinct from old.source_legacy_version_id
        or new.source_version_id is distinct from old.source_version_id
        or new.lineage_kind <> old.lineage_kind
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'work item configuration draft identity is immutable'
            using errcode = '23514';
    end if;
    if old.status = 'abandoned' and new is distinct from old then
        raise exception 'abandoned work item configuration drafts are immutable'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create function guard_project_work_item_configuration_publication_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'configuration publication receipts cannot be physically deleted'
            using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed configuration publication receipts are immutable'
            using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.request_id <> old.request_id
        or new.operation <> old.operation
        or new.request_hash <> old.request_hash
        or new.response_schema_version <> old.response_schema_version
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'configuration publication receipt identity is immutable'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_configuration_publication_receipt
before update or delete on project_work_item_configuration_publication_commands
for each row execute function guard_project_work_item_configuration_publication_receipt();
