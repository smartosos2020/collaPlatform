create table project_work_item_configuration_drafts (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    status varchar(32) not null,
    snapshot_schema_version smallint not null,
    config_hash varchar(64) not null,
    snapshot jsonb not null,
    diagnostics jsonb not null default '[]'::jsonb,
    aggregate_version bigint not null default 0,
    source_legacy_version_id uuid,
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_work_item_configuration_drafts_scope_id
        unique (workspace_id, space_id, type_definition_id, id),
    constraint uk_project_work_item_configuration_drafts_legacy_source
        unique (workspace_id, space_id, type_definition_id, source_legacy_version_id),
    constraint fk_project_work_item_configuration_drafts_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_configuration_drafts_legacy_source
        foreign key (workspace_id, space_id, type_definition_id, source_legacy_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_configuration_drafts_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_configuration_drafts_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_work_item_configuration_drafts_status
        check (status in ('editing', 'validating', 'valid', 'invalid', 'abandoned')),
    constraint ck_project_work_item_configuration_drafts_schema
        check (snapshot_schema_version between 0 and 32767),
    constraint ck_project_work_item_configuration_drafts_hash
        check (config_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_configuration_drafts_snapshot
        check (jsonb_typeof(snapshot) = 'object'),
    constraint ck_project_work_item_configuration_drafts_diagnostics
        check (jsonb_typeof(diagnostics) = 'array'),
    constraint ck_project_work_item_configuration_drafts_version
        check (aggregate_version >= 0)
);

create unique index uk_project_work_item_configuration_drafts_active
    on project_work_item_configuration_drafts (workspace_id, space_id, type_definition_id)
    where status in ('editing', 'validating', 'valid', 'invalid');
create index idx_project_work_item_configuration_drafts_type_updated
    on project_work_item_configuration_drafts
    (workspace_id, space_id, type_definition_id, updated_at desc);

create table project_work_item_configuration_draft_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    request_id varchar(120) not null,
    operation varchar(64) not null,
    request_hash varchar(64) not null,
    status varchar(32) not null,
    response_schema_version smallint not null default 1,
    response_draft_id uuid,
    response_aggregate_version bigint,
    response_config_hash varchar(64),
    response_payload jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_configuration_draft_commands_request
        unique (workspace_id, space_id, type_definition_id, operation, request_id),
    constraint fk_project_work_item_configuration_draft_commands_type
        foreign key (workspace_id, space_id, type_definition_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint fk_project_work_item_configuration_draft_commands_response
        foreign key (workspace_id, space_id, type_definition_id, response_draft_id)
        references project_work_item_configuration_drafts(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_configuration_draft_commands_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_work_item_configuration_draft_commands_operation
        check (operation in ('save', 'validate', 'abandon')),
    constraint ck_project_work_item_configuration_draft_commands_hash
        check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_configuration_draft_commands_status
        check (status in ('pending', 'completed')),
    constraint ck_project_work_item_configuration_draft_commands_response_schema
        check (response_schema_version = 1),
    constraint ck_project_work_item_configuration_draft_commands_response
        check (
            (
                status = 'pending'
                and response_draft_id is null
                and response_aggregate_version is null
                and response_config_hash is null
                and response_payload is null
                and completed_at is null
            )
            or (
                status = 'completed'
                and response_draft_id is not null
                and response_aggregate_version is not null
                and response_aggregate_version >= 0
                and response_config_hash ~ '^[0-9a-f]{64}$'
                and jsonb_typeof(response_payload) = 'object'
                and completed_at is not null
            )
        )
);

create index idx_project_work_item_configuration_draft_commands_type_created
    on project_work_item_configuration_draft_commands
    (workspace_id, space_id, type_definition_id, created_at desc);

create table project_work_item_legacy_draft_diagnostics (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    type_definition_id uuid not null,
    legacy_version_id uuid not null,
    draft_id uuid not null,
    diagnostic_code varchar(64) not null,
    severity varchar(16) not null,
    details jsonb not null,
    observed_at timestamptz not null,
    constraint uk_project_work_item_legacy_draft_diagnostics_code
        unique (workspace_id, space_id, type_definition_id, legacy_version_id, diagnostic_code),
    constraint fk_project_work_item_legacy_draft_diagnostics_version
        foreign key (workspace_id, space_id, type_definition_id, legacy_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_legacy_draft_diagnostics_draft
        foreign key (workspace_id, space_id, type_definition_id, draft_id)
        references project_work_item_configuration_drafts(workspace_id, space_id, type_definition_id, id),
    constraint ck_project_work_item_legacy_draft_diagnostics_severity
        check (severity in ('warning', 'error')),
    constraint ck_project_work_item_legacy_draft_diagnostics_details
        check (jsonb_typeof(details) = 'object')
);

create index idx_project_work_item_legacy_draft_diagnostics_type
    on project_work_item_legacy_draft_diagnostics
    (workspace_id, space_id, type_definition_id, observed_at desc);

with ranked as (
    select v.*,
           row_number() over (
               partition by v.workspace_id, v.space_id, v.type_definition_id
               order by v.version_number desc, v.id
           ) as draft_rank,
           count(*) over (
               partition by v.workspace_id, v.space_id, v.type_definition_id
           ) as draft_count
      from project_work_item_type_versions v
     where v.status = 'draft'
),
migrated as (
    insert into project_work_item_configuration_drafts (
        id, workspace_id, space_id, type_definition_id, status,
        snapshot_schema_version, config_hash, snapshot, diagnostics,
        aggregate_version, source_legacy_version_id,
        created_by, created_at, updated_by, updated_at
    )
    select gen_random_uuid(), workspace_id, space_id, type_definition_id,
           case when draft_rank = 1 then 'invalid' else 'abandoned' end,
           0, config_hash, config,
           jsonb_build_array(jsonb_build_object(
               'code', 'legacy_partial_snapshot',
               'severity', 'error',
               'keyPath', '$',
               'message', 'Legacy draft payload requires assembly and validation before publication'
           )),
           0, id, created_by, created_at, created_by, created_at
      from ranked
    returning id, workspace_id, space_id, type_definition_id, source_legacy_version_id
)
insert into project_work_item_legacy_draft_diagnostics (
    id, workspace_id, space_id, type_definition_id, legacy_version_id,
    draft_id, diagnostic_code, severity, details, observed_at
)
select gen_random_uuid(), m.workspace_id, m.space_id, m.type_definition_id,
       m.source_legacy_version_id, m.id, 'legacy_partial_snapshot', 'error',
       jsonb_build_object(
           'snapshotSchemaVersion', 0,
           'action', 'assemble_and_validate_before_publish'
       ),
       now()
  from migrated m;

insert into project_work_item_legacy_draft_diagnostics (
    id, workspace_id, space_id, type_definition_id, legacy_version_id,
    draft_id, diagnostic_code, severity, details, observed_at
)
select gen_random_uuid(), d.workspace_id, d.space_id, d.type_definition_id,
       d.source_legacy_version_id, d.id, 'multiple_legacy_drafts', 'error',
       jsonb_build_object(
           'legacyDraftCount', counts.draft_count,
           'activeCandidate', d.status <> 'abandoned'
       ),
       now()
  from project_work_item_configuration_drafts d
  join (
      select workspace_id, space_id, type_definition_id, count(*) as draft_count
        from project_work_item_type_versions
       where status = 'draft'
       group by workspace_id, space_id, type_definition_id
      having count(*) > 1
  ) counts
    on counts.workspace_id = d.workspace_id
   and counts.space_id = d.space_id
   and counts.type_definition_id = d.type_definition_id
 where d.source_legacy_version_id is not null;

insert into project_work_item_legacy_draft_diagnostics (
    id, workspace_id, space_id, type_definition_id, legacy_version_id,
    draft_id, diagnostic_code, severity, details, observed_at
)
select gen_random_uuid(), d.workspace_id, d.space_id, d.type_definition_id,
       d.source_legacy_version_id, d.id, 'legacy_current_pointer', 'warning',
       jsonb_build_object('action', 'repoint_to_latest_published_or_preserve_as_legacy_published'),
       now()
  from project_work_item_configuration_drafts d
  join project_work_item_types t
    on t.workspace_id = d.workspace_id
   and t.space_id = d.space_id
   and t.id = d.type_definition_id
   and t.current_version_id = d.source_legacy_version_id;

with replacements as (
    select t.workspace_id, t.space_id, t.id type_definition_id,
           (
               select v.id
                 from project_work_item_type_versions v
                where v.workspace_id = t.workspace_id
                  and v.space_id = t.space_id
                  and v.type_definition_id = t.id
                  and v.status = 'published'
                order by v.version_number desc, v.id
                limit 1
           ) replacement_id
      from project_work_item_types t
      join project_work_item_type_versions current_version
        on current_version.workspace_id = t.workspace_id
       and current_version.space_id = t.space_id
       and current_version.type_definition_id = t.id
       and current_version.id = t.current_version_id
       and current_version.status = 'draft'
)
update project_work_item_types t
   set current_version_id = replacements.replacement_id,
       aggregate_version = t.aggregate_version + 1,
       updated_at = now()
  from replacements
 where t.workspace_id = replacements.workspace_id
   and t.space_id = replacements.space_id
   and t.id = replacements.type_definition_id
   and replacements.replacement_id is not null;

create function guard_project_work_item_configuration_draft_identity()
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

create trigger trg_project_work_item_configuration_draft_identity
before update or delete on project_work_item_configuration_drafts
for each row execute function guard_project_work_item_configuration_draft_identity();

create function guard_project_work_item_configuration_draft_command_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item configuration draft command receipts cannot be physically deleted'
            using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed work item configuration draft command receipts are immutable'
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
        raise exception 'work item configuration draft command identity is immutable'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_configuration_draft_command_receipt
before update or delete on project_work_item_configuration_draft_commands
for each row execute function guard_project_work_item_configuration_draft_command_receipt();

comment on column project_work_item_configuration_drafts.snapshot_schema_version is
    'Schema version 0 identifies migrated legacy partial payloads that cannot be published.';
comment on column project_work_item_configuration_draft_commands.response_payload is
    'Immutable application response snapshot used for exact request replay.';
