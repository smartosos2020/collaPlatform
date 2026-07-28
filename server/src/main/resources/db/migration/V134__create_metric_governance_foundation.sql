create table project_governance_reports (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    report_key varchar(64) not null check (report_key ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    name varchar(160) not null,
    description varchar(2000) not null default '',
    sections jsonb not null check (jsonb_typeof(sections) = 'array'),
    status varchar(16) not null check (status in ('draft','published','archived')),
    row_version bigint not null check (row_version >= 1),
    created_by uuid not null,
    updated_by uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_project_governance_report_boundary unique (workspace_id,space_id,id),
    constraint uk_project_governance_report_key unique (workspace_id,space_id,report_key),
    constraint fk_project_governance_report_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_governance_report_creator foreign key (workspace_id,created_by)
      references users(workspace_id,id),
    constraint fk_project_governance_report_updater foreign key (workspace_id,updated_by)
      references users(workspace_id,id)
);

create table project_governance_report_runs (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    report_id uuid not null,
    report_version bigint not null check (report_version >= 1),
    status varchar(16) not null check (status in ('completed','failed')),
    result_payload jsonb not null check (jsonb_typeof(result_payload) = 'object'),
    source_fingerprint varchar(64) not null check (source_fingerprint ~ '^[0-9a-f]{64}$'),
    run_by uuid not null,
    started_at timestamptz not null,
    completed_at timestamptz not null,
    constraint uk_project_governance_run_boundary unique (workspace_id,space_id,id),
    constraint fk_project_governance_run_report foreign key (workspace_id,space_id,report_id)
      references project_governance_reports(workspace_id,space_id,id),
    constraint fk_project_governance_run_actor foreign key (workspace_id,run_by)
      references users(workspace_id,id)
);
create index idx_project_governance_run_list
  on project_governance_report_runs(workspace_id,space_id,started_at desc,id);

create table project_governance_exports (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    run_id uuid not null,
    format varchar(8) not null check (format in ('csv','json')),
    row_count integer not null check (row_count between 0 and 500),
    truncated boolean not null,
    content_hash varchar(64) not null check (content_hash ~ '^[0-9a-f]{64}$'),
    exported_by uuid not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    exported_at timestamptz not null default now(),
    constraint uk_project_governance_export_request unique (
      workspace_id,space_id,exported_by,request_id
    ),
    constraint fk_project_governance_export_run foreign key (workspace_id,space_id,run_id)
      references project_governance_report_runs(workspace_id,space_id,id),
    constraint fk_project_governance_export_actor foreign key (workspace_id,exported_by)
      references users(workspace_id,id)
);

create table project_governance_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(24) not null check (
      operation in ('save_report','run_report','export_report')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    object_id uuid not null,
    response_payload jsonb not null,
    status varchar(16) not null check (status = 'completed'),
    created_at timestamptz not null default now(),
    constraint uk_project_governance_command unique (
      workspace_id,space_id,actor_id,operation,request_id
    ),
    constraint fk_project_governance_command_actor foreign key (workspace_id,actor_id)
      references users(workspace_id,id)
);

create trigger trg_project_governance_run_immutable
before update or delete on project_governance_report_runs
for each row execute function guard_project_metric_immutable();
create trigger trg_project_governance_export_immutable
before update or delete on project_governance_exports
for each row execute function guard_project_metric_immutable();
create trigger trg_project_governance_command_immutable
before update or delete on project_governance_commands
for each row execute function guard_project_metric_immutable();
