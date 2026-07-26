alter table project_work_item_migration_batches
    add column plan_payload jsonb not null default '{}'::jsonb,
    add column plan_fingerprint varchar(128),
    add column version bigint not null default 0,
    add column lease_owner varchar(160),
    add column lease_token uuid,
    add column fence_version bigint not null default 0,
    add column heartbeat_at timestamptz,
    add column throttle_millis integer not null default 0,
    add column paused_reason varchar(500),
    add constraint ck_project_work_item_migration_batch_plan
        check (jsonb_typeof(plan_payload) = 'object'),
    add constraint ck_project_work_item_migration_batch_version
        check (version >= 0 and fence_version >= 0),
    add constraint ck_project_work_item_migration_batch_throttle
        check (throttle_millis between 0 and 60000),
    add constraint ck_project_work_item_migration_batch_lease
        check (
            (lease_owner is null and lease_token is null and heartbeat_at is null)
            or (lease_owner is not null and lease_token is not null and heartbeat_at is not null)
        );

alter table project_work_item_migration_units
    add column fence_version bigint not null default 0,
    add column last_error_code varchar(64),
    add column migrated_objects integer not null default 0,
    add constraint ck_project_work_item_migration_unit_runtime
        check (fence_version >= 0 and migrated_objects >= 0);

alter table project_legacy_work_item_maps
    drop constraint uk_project_legacy_work_item_source,
    drop constraint uk_project_legacy_work_item_target,
    drop constraint fk_project_legacy_work_item_target;

create unique index uk_project_legacy_work_item_source_active
    on project_legacy_work_item_maps(workspace_id, source_type, source_id)
    where status = 'active';
create unique index uk_project_legacy_work_item_target_active
    on project_legacy_work_item_maps(workspace_id, work_item_id)
    where status = 'active';

create unique index if not exists uk_files_workspace_id on files(workspace_id, id);

create table project_work_item_comments (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    author_id uuid not null,
    content text not null check (length(btrim(content)) between 1 and 20000),
    version bigint not null default 0 check (version >= 0),
    created_at timestamptz not null,
    updated_at timestamptz,
    deleted_at timestamptz,
    constraint uk_project_work_item_comments_scope_id
        unique (workspace_id, space_id, work_item_id, id),
    constraint fk_project_work_item_comments_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_work_item_comments_author
        foreign key (workspace_id, author_id) references users(workspace_id, id)
);

create index idx_project_work_item_comments_page
    on project_work_item_comments(
        workspace_id, space_id, work_item_id, created_at, id
    ) where deleted_at is null;

create table project_work_item_attachments (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    file_id uuid not null,
    created_by uuid not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    constraint uk_project_work_item_attachments_file
        unique (workspace_id, space_id, work_item_id, file_id),
    constraint uk_project_work_item_attachments_scope_id
        unique (workspace_id, space_id, work_item_id, id),
    constraint fk_project_work_item_attachments_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_work_item_attachments_file
        foreign key (workspace_id, file_id) references files(workspace_id, id),
    constraint fk_project_work_item_attachments_creator
        foreign key (workspace_id, created_by) references users(workspace_id, id)
);

create index idx_project_work_item_attachments_item
    on project_work_item_attachments(
        workspace_id, space_id, work_item_id, created_at, id
    ) where deleted_at is null;

create table project_work_item_migration_provenance (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    batch_id uuid not null,
    unit_id uuid not null,
    source_type varchar(32) not null,
    source_id uuid not null,
    source_project_id uuid not null,
    source_checksum varchar(128) not null,
    target_type varchar(32) not null,
    target_id uuid not null,
    safe_payload jsonb not null default '{}'::jsonb,
    recorded_at timestamptz not null,
    constraint uk_project_work_item_migration_provenance_source
        unique (batch_id, source_type, source_id),
    constraint fk_project_work_item_migration_provenance_batch
        foreign key (workspace_id, batch_id)
        references project_work_item_migration_batches(workspace_id, id),
    constraint fk_project_work_item_migration_provenance_unit
        foreign key (workspace_id, unit_id)
        references project_work_item_migration_units(workspace_id, id),
    constraint fk_project_work_item_migration_provenance_project
        foreign key (workspace_id, source_project_id)
        references projects(workspace_id, id),
    constraint ck_project_work_item_migration_provenance_source
        check (source_type in ('project', 'issue', 'member', 'comment', 'attachment', 'activity')),
    constraint ck_project_work_item_migration_provenance_target
        check (target_type in ('work_item', 'participant', 'comment', 'attachment', 'activity')),
    constraint ck_project_work_item_migration_provenance_payload
        check (jsonb_typeof(safe_payload) = 'object')
);

create index idx_project_work_item_migration_provenance_unit
    on project_work_item_migration_provenance(workspace_id, unit_id, source_type, source_id);

create table project_work_item_migration_verifications (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    batch_id uuid,
    verification_scope varchar(32) not null,
    status varchar(32) not null,
    manifest_fingerprint varchar(128),
    observed_fingerprint varchar(128) not null,
    safe_summary jsonb not null default '{}'::jsonb,
    verified_by uuid not null,
    verified_at timestamptz not null,
    constraint fk_project_work_item_migration_verification_batch
        foreign key (workspace_id, batch_id)
        references project_work_item_migration_batches(workspace_id, id),
    constraint fk_project_work_item_migration_verification_actor
        foreign key (workspace_id, verified_by) references users(workspace_id, id),
    constraint ck_project_work_item_migration_verification_scope
        check (verification_scope in ('batch_manifest', 'workspace_convergence')),
    constraint ck_project_work_item_migration_verification_status
        check (status in ('matched', 'mismatched')),
    constraint ck_project_work_item_migration_verification_summary
        check (jsonb_typeof(safe_summary) = 'object'),
    constraint ck_project_work_item_migration_verification_batch_scope
        check (
            (verification_scope = 'batch_manifest' and batch_id is not null)
            or (verification_scope = 'workspace_convergence' and batch_id is null)
        )
);

create index idx_project_work_item_migration_verifications_scope
    on project_work_item_migration_verifications(
        workspace_id, verification_scope, verified_at desc, id
    );

create trigger trg_project_work_item_migration_provenance_append_only
before update or delete on project_work_item_migration_provenance
for each row execute function reject_project_work_item_migration_history_mutation();

create trigger trg_project_work_item_migration_verification_append_only
before update or delete on project_work_item_migration_verifications
for each row execute function reject_project_work_item_migration_history_mutation();
