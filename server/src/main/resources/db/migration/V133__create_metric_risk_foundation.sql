create table project_risk_policies (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    policy_key varchar(64) not null check (policy_key ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    name varchar(160) not null,
    description varchar(2000) not null default '',
    status varchar(16) not null check (status in ('draft','active','disabled','archived')),
    draft_signal_types jsonb not null check (jsonb_typeof(draft_signal_types) = 'array'),
    draft_severity varchar(16) not null check (draft_severity in ('info','warning','critical')),
    draft_cooldown_hours integer not null check (draft_cooldown_hours between 1 and 720),
    current_version_id uuid,
    row_version bigint not null check (row_version >= 1),
    created_by uuid not null,
    updated_by uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_project_risk_policy_boundary unique (workspace_id,space_id,id),
    constraint uk_project_risk_policy_key unique (workspace_id,space_id,policy_key),
    constraint fk_project_risk_policy_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_risk_policy_creator foreign key (workspace_id,created_by)
      references users(workspace_id,id),
    constraint fk_project_risk_policy_updater foreign key (workspace_id,updated_by)
      references users(workspace_id,id)
);
create index idx_project_risk_policy_list
  on project_risk_policies(workspace_id,space_id,status,updated_at desc,id);

create table project_risk_policy_versions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    policy_id uuid not null,
    version_number integer not null check (version_number >= 1),
    schema_version integer not null check (schema_version = 1),
    definition_hash varchar(64) not null check (definition_hash ~ '^[0-9a-f]{64}$'),
    signal_types jsonb not null check (jsonb_typeof(signal_types) = 'array'),
    severity varchar(16) not null check (severity in ('info','warning','critical')),
    cooldown_hours integer not null check (cooldown_hours between 1 and 720),
    published_by uuid not null,
    published_at timestamptz not null default now(),
    constraint uk_project_risk_policy_version_boundary unique (workspace_id,space_id,id),
    constraint uk_project_risk_policy_version_number unique (
      workspace_id,space_id,policy_id,version_number
    ),
    constraint fk_project_risk_policy_version_policy foreign key (
      workspace_id,space_id,policy_id
    ) references project_risk_policies(workspace_id,space_id,id),
    constraint fk_project_risk_policy_version_actor foreign key (workspace_id,published_by)
      references users(workspace_id,id)
);

alter table project_risk_policies
  add constraint fk_project_risk_policy_current_version
  foreign key (workspace_id,space_id,current_version_id)
  references project_risk_policy_versions(workspace_id,space_id,id);

create table project_risk_signals (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    policy_id uuid not null,
    policy_version integer not null check (policy_version >= 1),
    signal_type varchar(24) not null check (
      signal_type in ('overdue','due_soon','stagnation','blocked','quality','resource')
    ),
    severity varchar(16) not null check (severity in ('info','warning','critical')),
    state varchar(16) not null check (
      state in ('open','acknowledged','suppressed','closed','invalidated')
    ),
    dedupe_key varchar(64) not null check (dedupe_key ~ '^[0-9a-f]{64}$'),
    evidence_fingerprint varchar(64) not null check (evidence_fingerprint ~ '^[0-9a-f]{64}$'),
    evidence_refs jsonb not null check (jsonb_typeof(evidence_refs) = 'array'),
    cooldown_until timestamptz not null,
    row_version bigint not null check (row_version >= 1),
    acknowledged_by uuid,
    acknowledged_at timestamptz,
    closed_by uuid,
    closed_at timestamptz,
    resolution_reason varchar(1000) not null default '',
    observed_at timestamptz not null,
    updated_at timestamptz not null default now(),
    constraint uk_project_risk_signal_boundary unique (workspace_id,space_id,id),
    constraint uk_project_risk_signal_dedupe unique (workspace_id,space_id,dedupe_key),
    constraint fk_project_risk_signal_policy foreign key (
      workspace_id,space_id,policy_id
    ) references project_risk_policies(workspace_id,space_id,id),
    constraint fk_project_risk_signal_ack foreign key (workspace_id,acknowledged_by)
      references users(workspace_id,id),
    constraint fk_project_risk_signal_close foreign key (workspace_id,closed_by)
      references users(workspace_id,id)
);
create index idx_project_risk_signal_list
  on project_risk_signals(workspace_id,space_id,state,severity,observed_at desc,id);
create index idx_project_risk_signal_cooldown
  on project_risk_signals(workspace_id,space_id,cooldown_until);

create table project_risk_signal_actions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    signal_id uuid not null,
    signal_version bigint not null check (signal_version >= 1),
    action varchar(16) not null check (
      action in ('acknowledge','close','suppress','reopen','invalidate')
    ),
    reason varchar(1000) not null,
    actor_id uuid not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    evidence_fingerprint varchar(64) not null check (evidence_fingerprint ~ '^[0-9a-f]{64}$'),
    occurred_at timestamptz not null default now(),
    constraint uk_project_risk_signal_action_request unique (
      workspace_id,space_id,actor_id,action,request_id
    ),
    constraint fk_project_risk_signal_action_signal foreign key (
      workspace_id,space_id,signal_id
    ) references project_risk_signals(workspace_id,space_id,id),
    constraint fk_project_risk_signal_action_actor foreign key (workspace_id,actor_id)
      references users(workspace_id,id)
);

create table project_risk_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(32) not null check (
      operation in (
        'save_policy','publish_policy','evaluate_risks',
        'acknowledge_signal','close_signal','suppress_signal',
        'reopen_signal','invalidate_signal'
      )
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    object_type varchar(16) not null check (object_type in ('policy','signal','space')),
    object_id uuid not null,
    response_payload jsonb not null,
    status varchar(16) not null check (status = 'completed'),
    created_at timestamptz not null default now(),
    constraint uk_project_risk_command unique (
      workspace_id,space_id,actor_id,operation,request_id
    ),
    constraint fk_project_risk_command_actor foreign key (workspace_id,actor_id)
      references users(workspace_id,id)
);

create table project_risk_stats (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    policy_id uuid not null,
    active_count integer not null check (active_count between 0 and 200),
    severity_counts jsonb not null check (jsonb_typeof(severity_counts) = 'object'),
    source_fingerprint varchar(64) not null check (source_fingerprint ~ '^[0-9a-f]{64}$'),
    expires_at timestamptz not null,
    rebuilt_at timestamptz not null default now(),
    constraint uk_project_risk_stats unique (workspace_id,space_id,policy_id),
    constraint fk_project_risk_stats_policy foreign key (
      workspace_id,space_id,policy_id
    ) references project_risk_policies(workspace_id,space_id,id)
);
create index idx_project_risk_stats_expiry
  on project_risk_stats(workspace_id,space_id,expires_at);

create trigger trg_project_risk_policy_version_immutable
before update or delete on project_risk_policy_versions
for each row execute function guard_project_metric_immutable();

create trigger trg_project_risk_signal_action_immutable
before update or delete on project_risk_signal_actions
for each row execute function guard_project_metric_immutable();

create trigger trg_project_risk_command_immutable
before update or delete on project_risk_commands
for each row execute function guard_project_metric_immutable();
