create table project_cross_space_sync_rules (
    id uuid primary key,
    workspace_id uuid not null,
    grant_id uuid not null,
    policy_id uuid not null,
    canonical_relation_id uuid not null,
    source_space_id uuid not null,
    target_space_id uuid not null,
    name varchar(160) not null,
    status varchar(16) not null check (
        status in ('draft','requested','active','paused','revoked','archived')
    ),
    current_version integer not null default 1 check (current_version > 0),
    source_confirmed_by uuid,
    source_confirmed_at timestamptz,
    target_confirmed_by uuid,
    target_confirmed_at timestamptz,
    created_by uuid not null,
    created_at timestamptz not null default now(),
    updated_by uuid not null,
    updated_at timestamptz not null default now(),
    revoked_at timestamptz,
    archived_at timestamptz,
    constraint uk_project_cross_space_sync_rule_scope unique (workspace_id,id),
    constraint uk_project_cross_space_sync_rule_relation_name
        unique (workspace_id,canonical_relation_id,name),
    constraint fk_project_cross_space_sync_rule_grant
        foreign key (workspace_id,grant_id)
        references project_cross_space_grants(workspace_id,id) on delete cascade,
    constraint fk_project_cross_space_sync_rule_policy
        foreign key (workspace_id,policy_id)
        references project_cross_space_relation_policies(workspace_id,id) on delete cascade,
    constraint fk_project_cross_space_sync_rule_source_space
        foreign key (workspace_id,source_space_id)
        references project_spaces(workspace_id,id),
    constraint fk_project_cross_space_sync_rule_target_space
        foreign key (workspace_id,target_space_id)
        references project_spaces(workspace_id,id),
    constraint fk_project_cross_space_sync_rule_source_confirmer
        foreign key (workspace_id,source_confirmed_by) references users(workspace_id,id),
    constraint fk_project_cross_space_sync_rule_target_confirmer
        foreign key (workspace_id,target_confirmed_by) references users(workspace_id,id),
    constraint fk_project_cross_space_sync_rule_created_by
        foreign key (workspace_id,created_by) references users(workspace_id,id),
    constraint fk_project_cross_space_sync_rule_updated_by
        foreign key (workspace_id,updated_by) references users(workspace_id,id),
    constraint ck_project_cross_space_sync_rule_spaces
        check (source_space_id <> target_space_id)
);

create index idx_project_cross_space_sync_rule_party
    on project_cross_space_sync_rules(
        workspace_id,source_space_id,target_space_id,status,updated_at desc
    );

create table project_cross_space_sync_rule_versions (
    id uuid primary key,
    workspace_id uuid not null,
    rule_id uuid not null,
    version_number integer not null check (version_number > 0),
    schema_version smallint not null default 1 check (schema_version = 1),
    direction varchar(24) not null check (
        direction in ('source_to_target','target_to_source','bidirectional')
    ),
    trigger_kind varchar(24) not null check (
        trigger_kind in ('manual','work_item_changed','workflow_state_changed')
    ),
    field_mappings jsonb not null default '[]'::jsonb check (
        jsonb_typeof(field_mappings)='array' and jsonb_array_length(field_mappings) <= 32
    ),
    state_mappings jsonb not null default '[]'::jsonb check (
        jsonb_typeof(state_mappings)='array' and jsonb_array_length(state_mappings) <= 16
    ),
    conflict_strategy varchar(24) not null check (
        conflict_strategy in ('manual','source_wins','target_wins')
    ),
    config_hash varchar(64) not null check (config_hash ~ '^[0-9a-f]{64}$'),
    created_by uuid not null,
    created_at timestamptz not null default now(),
    constraint uk_project_cross_space_sync_rule_version
        unique (workspace_id,rule_id,version_number),
    constraint fk_project_cross_space_sync_rule_version_rule
        foreign key (workspace_id,rule_id)
        references project_cross_space_sync_rules(workspace_id,id) on delete cascade,
    constraint fk_project_cross_space_sync_rule_version_actor
        foreign key (workspace_id,created_by) references users(workspace_id,id)
);

create table project_cross_space_sync_runs (
    id uuid primary key,
    workspace_id uuid not null,
    rule_id uuid not null,
    rule_version_id uuid not null,
    rule_version_number integer not null check (rule_version_number > 0),
    canonical_relation_id uuid not null,
    direction varchar(24) not null check (
        direction in ('source_to_target','target_to_source')
    ),
    origin_id varchar(120) not null,
    causation_id varchar(120) not null,
    chain_depth smallint not null check (chain_depth between 0 and 8),
    input_fingerprint varchar(64) not null check (input_fingerprint ~ '^[0-9a-f]{64}$'),
    source_space_id uuid not null,
    source_work_item_id uuid not null,
    source_version bigint not null check (source_version >= 0),
    target_space_id uuid not null,
    target_work_item_id uuid not null,
    target_version bigint not null check (target_version >= 0),
    status varchar(20) not null check (
        status in ('running','succeeded','conflict','failed','compensated','dead_letter')
    ),
    retry_count smallint not null default 0 check (retry_count between 0 and 5),
    fencing_token bigint not null default 1 check (fencing_token > 0),
    result_target_version bigint,
    failure_code varchar(80),
    created_by uuid not null,
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint uk_project_cross_space_sync_run_scope unique (workspace_id,id),
    constraint uk_project_cross_space_sync_run_origin
        unique (workspace_id,rule_id,direction,origin_id,input_fingerprint),
    constraint fk_project_cross_space_sync_run_rule
        foreign key (workspace_id,rule_id)
        references project_cross_space_sync_rules(workspace_id,id) on delete cascade,
    constraint fk_project_cross_space_sync_run_version
        foreign key (workspace_id,rule_id,rule_version_number)
        references project_cross_space_sync_rule_versions(
            workspace_id,rule_id,version_number
        ),
    constraint fk_project_cross_space_sync_run_source
        foreign key (workspace_id,source_space_id,source_work_item_id)
        references project_work_items(workspace_id,space_id,id),
    constraint fk_project_cross_space_sync_run_target
        foreign key (workspace_id,target_space_id,target_work_item_id)
        references project_work_items(workspace_id,space_id,id),
    constraint fk_project_cross_space_sync_run_actor
        foreign key (workspace_id,created_by) references users(workspace_id,id)
);

create index idx_project_cross_space_sync_run_timeline
    on project_cross_space_sync_runs(
        workspace_id,rule_id,created_at desc,id
    );

create table project_cross_space_sync_steps (
    id uuid primary key,
    workspace_id uuid not null,
    run_id uuid not null,
    step_index smallint not null check (step_index between 0 and 49),
    step_kind varchar(16) not null check (step_kind in ('field','state','compensation')),
    mapping_key varchar(128) not null,
    input_fingerprint varchar(64) not null check (input_fingerprint ~ '^[0-9a-f]{64}$'),
    command_request_id varchar(120),
    status varchar(16) not null check (
        status in ('succeeded','conflict','failed','skipped','compensated')
    ),
    before_version bigint not null check (before_version >= 0),
    after_version bigint,
    error_code varchar(80),
    created_at timestamptz not null default now(),
    constraint uk_project_cross_space_sync_step
        unique (workspace_id,run_id,step_index),
    constraint fk_project_cross_space_sync_step_run
        foreign key (workspace_id,run_id)
        references project_cross_space_sync_runs(workspace_id,id) on delete cascade
);

create table project_cross_space_sync_conflicts (
    id uuid primary key,
    workspace_id uuid not null,
    run_id uuid not null,
    conflict_kind varchar(32) not null check (
        conflict_kind in ('source_version','target_version','definition',
                          'permission','field','state','partial_failure','loop')
    ),
    source_fingerprint varchar(64) not null check (source_fingerprint ~ '^[0-9a-f]{64}$'),
    target_fingerprint varchar(64) not null check (target_fingerprint ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (
        status in ('open','resolved','compensated','dead_letter')
    ),
    version bigint not null default 1 check (version > 0),
    resolution varchar(24),
    resolution_reason_hash varchar(64),
    resolved_by uuid,
    resolved_at timestamptz,
    created_at timestamptz not null default now(),
    constraint uk_project_cross_space_sync_conflict_scope unique (workspace_id,id),
    constraint uk_project_cross_space_sync_conflict_run unique (workspace_id,run_id),
    constraint fk_project_cross_space_sync_conflict_run
        foreign key (workspace_id,run_id)
        references project_cross_space_sync_runs(workspace_id,id) on delete cascade,
    constraint fk_project_cross_space_sync_conflict_actor
        foreign key (workspace_id,resolved_by) references users(workspace_id,id)
);

create index idx_project_cross_space_sync_conflict_open
    on project_cross_space_sync_conflicts(workspace_id,status,created_at desc)
    where status='open';

create table project_cross_space_sync_receipts (
    id uuid primary key,
    workspace_id uuid not null,
    actor_id uuid not null,
    operation varchar(32) not null check (
        operation in ('rule_create','rule_revise','rule_request','rule_confirm',
                      'rule_pause','rule_resume','rule_revoke','rule_archive',
                      'run_execute','conflict_resolve','run_retry','run_compensate')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_schema_version smallint not null default 1 check (response_schema_version=1),
    response_payload jsonb not null check (jsonb_typeof(response_payload)='object'),
    created_at timestamptz not null default now(),
    constraint uk_project_cross_space_sync_receipt
        unique (workspace_id,actor_id,operation,request_id),
    constraint fk_project_cross_space_sync_receipt_actor
        foreign key (workspace_id,actor_id) references users(workspace_id,id)
);

create function guard_project_cross_space_sync_immutable()
returns trigger
language plpgsql
as $$
begin
    if tg_op='DELETE' and current_setting('colla.project_space_cleanup',true)='on' then
        return old;
    end if;
    raise exception 'cross-space sync immutable fact cannot be changed' using errcode='23514';
end;
$$;

create trigger trg_project_cross_space_sync_rule_version_immutable
before update or delete on project_cross_space_sync_rule_versions
for each row execute function guard_project_cross_space_sync_immutable();

create trigger trg_project_cross_space_sync_step_immutable
before update or delete on project_cross_space_sync_steps
for each row execute function guard_project_cross_space_sync_immutable();

create trigger trg_project_cross_space_sync_receipt_immutable
before update or delete on project_cross_space_sync_receipts
for each row execute function guard_project_cross_space_sync_immutable();
