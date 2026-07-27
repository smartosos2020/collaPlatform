create table project_work_item_relation_migration_batches (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    relation_key varchar(64) not null check (
        relation_key ~ '^[a-z][a-z0-9_.-]{0,63}$'
    ),
    request_id varchar(120) not null,
    manifest_hash varchar(64) not null check (manifest_hash ~ '^[0-9a-f]{64}$'),
    dry_run boolean not null,
    status varchar(24) not null check (
        status in ('planned', 'running', 'completed', 'failed', 'verified', 'rolled_back')
    ),
    version bigint not null default 0 check (version >= 0),
    total_count integer not null default 0 check (total_count >= 0),
    canonical_count integer not null default 0 check (canonical_count >= 0),
    preserved_count integer not null default 0 check (preserved_count >= 0),
    completed_count integer not null default 0 check (completed_count >= 0),
    failed_count integer not null default 0 check (failed_count >= 0),
    reason_hash varchar(64) not null check (reason_hash ~ '^[0-9a-f]{64}$'),
    initiated_by uuid not null,
    initiated_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint uk_project_relation_migration_request
        unique (workspace_id, space_id, request_id),
    constraint fk_project_relation_migration_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_relation_migration_actor
        foreign key (workspace_id, initiated_by)
        references users(workspace_id, id)
);

create table project_work_item_relation_migration_units (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    batch_id uuid not null,
    source_relation_id uuid not null,
    source_issue_id uuid not null,
    target_type varchar(64) not null,
    target_id uuid not null,
    source_fingerprint varchar(64) not null,
    classification varchar(40) not null check (
        classification in (
            'canonical_work_item', 'preserved_platform_reference',
            'unresolved_target', 'cross_space_target', 'deleted_source'
        )
    ),
    source_work_item_id uuid,
    target_work_item_id uuid,
    relation_id uuid,
    status varchar(24) not null check (
        status in ('planned', 'preserved', 'completed', 'failed', 'verified', 'rolled_back')
    ),
    attempt integer not null default 0 check (attempt >= 0),
    error_code varchar(80),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_project_relation_migration_source
        unique (workspace_id, space_id, batch_id, source_relation_id),
    constraint fk_project_relation_migration_unit_batch
        foreign key (batch_id)
        references project_work_item_relation_migration_batches(id),
    constraint fk_project_relation_migration_unit_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_relation_migration_source_item
        foreign key (workspace_id, space_id, source_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_relation_migration_target_item
        foreign key (workspace_id, space_id, target_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_relation_migration_relation
        foreign key (workspace_id, space_id, relation_id)
        references project_work_item_relations(workspace_id, space_id, id),
    constraint ck_project_relation_migration_canonical
        check (
            (classification = 'canonical_work_item'
                and source_work_item_id is not null and target_work_item_id is not null)
            or
            (classification <> 'canonical_work_item' and relation_id is null)
        )
);

create table project_work_item_relation_migration_verifications (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    batch_id uuid not null,
    outcome varchar(16) not null check (outcome in ('passed', 'failed')),
    checked_count integer not null check (checked_count >= 0),
    failure_count integer not null check (failure_count >= 0),
    safe_failures jsonb not null default '[]'::jsonb check (
        jsonb_typeof(safe_failures) = 'array'
    ),
    verified_by uuid not null,
    verified_at timestamptz not null default now(),
    constraint fk_project_relation_verification_batch
        foreign key (batch_id)
        references project_work_item_relation_migration_batches(id),
    constraint fk_project_relation_verification_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_relation_verification_actor
        foreign key (workspace_id, verified_by)
        references users(workspace_id, id)
);

create index idx_project_relation_migration_batches_status
    on project_work_item_relation_migration_batches(
        workspace_id, space_id, status, initiated_at desc
    );
create index idx_project_relation_migration_units_status
    on project_work_item_relation_migration_units(
        workspace_id, space_id, batch_id, status, id
    );
create index idx_project_relation_migration_verifications_batch
    on project_work_item_relation_migration_verifications(
        workspace_id, space_id, batch_id, verified_at desc
    );
