create table project_automation_runs (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    rule_id uuid not null,
    rule_version int not null check (rule_version >= 1),
    source_type varchar(24) not null check (source_type in ('manual', 'event', 'retry')),
    source_key varchar(160) not null,
    actor_id uuid not null,
    status varchar(16) not null check (
        status in ('pending', 'running', 'skipped', 'succeeded', 'failed', 'cancelled')
    ),
    dry_run boolean not null,
    input_hash varchar(64) not null check (input_hash ~ '^[0-9a-f]{64}$'),
    output_json jsonb check (output_json is null or jsonb_typeof(output_json) = 'object'),
    error_code varchar(80),
    worker_id varchar(120),
    lease_until timestamptz,
    fencing_token bigint not null check (fencing_token >= 1),
    attempt int not null check (attempt >= 1),
    started_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_automation_run_source
        unique (workspace_id, space_id, rule_id, rule_version, source_type, source_key),
    constraint uk_project_automation_run_space_id unique (workspace_id, space_id, id),
    constraint fk_project_automation_run_rule
        foreign key (workspace_id, space_id, rule_id)
        references project_automation_rules(workspace_id, space_id, id),
    constraint fk_project_automation_run_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create index idx_project_automation_run_list
    on project_automation_runs(workspace_id, space_id, started_at desc, id);
create index idx_project_automation_run_claim
    on project_automation_runs(status, lease_until, started_at, id)
    where status in ('pending', 'running');

create table project_automation_run_steps (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    run_id uuid not null,
    step_number int not null check (step_number between 0 and 7),
    action_type varchar(64) not null,
    status varchar(16) not null check (
        status in ('pending', 'running', 'skipped', 'succeeded', 'failed', 'cancelled')
    ),
    input_hash varchar(64) not null check (input_hash ~ '^[0-9a-f]{64}$'),
    result_json jsonb check (result_json is null or jsonb_typeof(result_json) = 'object'),
    error_code varchar(80),
    started_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_automation_run_step unique
        (workspace_id, space_id, run_id, step_number),
    constraint fk_project_automation_run_step_run
        foreign key (workspace_id, space_id, run_id)
        references project_automation_runs(workspace_id, space_id, id)
);

create index idx_project_automation_run_step_list
    on project_automation_run_steps(workspace_id, space_id, run_id, step_number);

create table project_automation_action_receipts (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    rule_id uuid not null,
    rule_version int not null check (rule_version >= 1),
    action_index int not null check (action_index between 0 and 7),
    idempotency_key varchar(200) not null,
    input_hash varchar(64) not null check (input_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_automation_action_receipt
        unique (workspace_id, space_id, rule_id, rule_version, action_index, idempotency_key),
    constraint fk_project_automation_action_receipt_rule
        foreign key (workspace_id, space_id, rule_id)
        references project_automation_rules(workspace_id, space_id, id)
);

create table project_automation_execution_stats (
    workspace_id uuid not null,
    space_id uuid not null,
    observed_date date not null,
    run_count int not null check (run_count >= 0),
    success_count int not null check (success_count >= 0),
    failure_count int not null check (failure_count >= 0),
    truncated boolean not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, observed_date),
    constraint fk_project_automation_execution_stats_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id)
);
