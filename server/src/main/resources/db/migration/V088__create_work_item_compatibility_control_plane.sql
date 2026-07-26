create table project_work_item_migration_batches (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    status varchar(32) not null,
    source_watermark timestamptz not null,
    source_fingerprint varchar(128) not null,
    manifest_fingerprint varchar(128) not null,
    initiated_by uuid not null references users(id),
    initiated_at timestamptz not null,
    finished_at timestamptz,
    constraint uk_project_work_item_migration_batch_workspace_id unique (workspace_id, id),
    constraint ck_project_work_item_migration_batch_status
        check (status in ('planned', 'running', 'paused', 'completed', 'failed', 'rolled_back'))
);

create table project_work_item_migration_units (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    batch_id uuid not null,
    legacy_project_id uuid not null,
    space_id uuid not null,
    status varchar(32) not null,
    attempt integer not null default 0,
    source_fingerprint varchar(128) not null,
    started_at timestamptz,
    finished_at timestamptz,
    constraint uk_project_work_item_migration_unit_project unique (batch_id, legacy_project_id),
    constraint uk_project_work_item_migration_unit_workspace_id unique (workspace_id, id),
    constraint fk_project_work_item_migration_unit_batch
        foreign key (workspace_id, batch_id)
        references project_work_item_migration_batches(workspace_id, id),
    constraint fk_project_work_item_migration_unit_project
        foreign key (workspace_id, legacy_project_id)
        references projects(workspace_id, id),
    constraint fk_project_work_item_migration_unit_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint ck_project_work_item_migration_unit_status
        check (status in ('planned', 'running', 'paused', 'completed', 'failed', 'rolled_back'))
);

create table project_work_item_migration_manifests (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    batch_id uuid not null,
    unit_id uuid not null,
    manifest_version integer not null,
    source_watermark timestamptz not null,
    source_fingerprint varchar(128) not null,
    payload jsonb not null,
    recorded_at timestamptz not null,
    constraint uk_project_work_item_manifest_unit_version unique (unit_id, manifest_version),
    constraint fk_project_work_item_manifest_batch
        foreign key (workspace_id, batch_id)
        references project_work_item_migration_batches(workspace_id, id),
    constraint fk_project_work_item_manifest_unit
        foreign key (workspace_id, unit_id)
        references project_work_item_migration_units(workspace_id, id)
);

create table project_work_item_migration_failures (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    batch_id uuid not null,
    unit_id uuid,
    failure_code varchar(64) not null,
    source_type varchar(32),
    source_id uuid,
    safe_detail jsonb not null default '{}'::jsonb,
    recorded_at timestamptz not null,
    constraint fk_project_work_item_failure_batch
        foreign key (workspace_id, batch_id)
        references project_work_item_migration_batches(workspace_id, id),
    constraint fk_project_work_item_failure_unit
        foreign key (workspace_id, unit_id)
        references project_work_item_migration_units(workspace_id, id)
);

create table project_legacy_work_item_maps (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    batch_id uuid not null,
    unit_id uuid not null,
    source_type varchar(32) not null,
    source_id uuid not null,
    source_project_id uuid not null,
    space_id uuid not null,
    work_item_id uuid not null,
    identity_decision varchar(32) not null,
    source_fingerprint varchar(128) not null,
    status varchar(32) not null,
    mapped_at timestamptz not null,
    rolled_back_at timestamptz,
    constraint uk_project_legacy_work_item_source unique (workspace_id, source_type, source_id),
    constraint uk_project_legacy_work_item_target unique (workspace_id, work_item_id),
    constraint fk_project_legacy_work_item_batch
        foreign key (workspace_id, batch_id)
        references project_work_item_migration_batches(workspace_id, id),
    constraint fk_project_legacy_work_item_unit
        foreign key (workspace_id, unit_id)
        references project_work_item_migration_units(workspace_id, id),
    constraint fk_project_legacy_work_item_project
        foreign key (workspace_id, source_project_id)
        references projects(workspace_id, id),
    constraint fk_project_legacy_work_item_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_legacy_work_item_target
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint ck_project_legacy_work_item_source_type check (source_type in ('project', 'issue')),
    constraint ck_project_legacy_work_item_identity
        check (identity_decision in ('uuid_reused', 'uuid_conflict_remapped')),
    constraint ck_project_legacy_work_item_status check (status in ('active', 'rolled_back'))
);

create table project_work_item_cutovers (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid,
    read_stage varchar(32) not null,
    legacy_write_enabled boolean not null,
    kill_switch_enabled boolean not null,
    version bigint not null default 0,
    changed_by uuid not null references users(id),
    changed_at timestamptz not null,
    constraint uk_project_work_item_cutover_scope
        unique nulls not distinct (workspace_id, space_id),
    constraint fk_project_work_item_cutover_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint ck_project_work_item_cutover_stage
        check (read_stage in ('legacy', 'shadow', 'canonical_read', 'canonical_write', 'complete')),
    constraint ck_project_work_item_cutover_write
        check (legacy_write_enabled or read_stage in ('canonical_write', 'complete'))
);

create table project_work_item_shadow_samples (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid,
    source_type varchar(32) not null,
    source_id uuid not null,
    primary_source varchar(32) not null,
    legacy_fingerprint varchar(128),
    canonical_fingerprint varchar(128),
    outcome varchar(32) not null,
    primary_latency_ms integer not null,
    shadow_latency_ms integer,
    safe_detail jsonb not null default '{}'::jsonb,
    sampled_at timestamptz not null,
    constraint ck_project_work_item_shadow_source check (source_type in ('project', 'issue')),
    constraint ck_project_work_item_shadow_primary check (primary_source in ('legacy', 'canonical')),
    constraint ck_project_work_item_shadow_outcome
        check (outcome in ('match', 'drift', 'legacy_missing', 'canonical_missing', 'shadow_error'))
);

create index idx_project_work_item_migration_batches_status
    on project_work_item_migration_batches (workspace_id, status, initiated_at desc);
create index idx_project_work_item_migration_units_status
    on project_work_item_migration_units (workspace_id, batch_id, status, id);
create index idx_project_work_item_migration_failures_batch
    on project_work_item_migration_failures (workspace_id, batch_id, recorded_at, id);
create index idx_project_legacy_work_item_maps_source
    on project_legacy_work_item_maps (workspace_id, source_type, source_id, status);
create index idx_project_work_item_shadow_samples_scope
    on project_work_item_shadow_samples (workspace_id, space_id, outcome, sampled_at desc);

create or replace function reject_project_work_item_migration_history_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'project work item migration history is append-only';
end;
$$;

create trigger trg_project_work_item_manifest_append_only
before update or delete on project_work_item_migration_manifests
for each row execute function reject_project_work_item_migration_history_mutation();

create trigger trg_project_work_item_failure_append_only
before update or delete on project_work_item_migration_failures
for each row execute function reject_project_work_item_migration_history_mutation();
