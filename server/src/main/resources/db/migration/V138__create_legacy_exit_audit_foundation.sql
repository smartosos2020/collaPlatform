create table project_legacy_audit_snapshots (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    inventory_version varchar(32) not null,
    status varchar(32) not null,
    source_fingerprint varchar(128) not null,
    totals jsonb not null,
    surfaces jsonb not null,
    generated_by uuid not null,
    generated_at timestamptz not null,
    constraint uk_project_legacy_audit_snapshot_scope
        unique (workspace_id, id),
    constraint fk_project_legacy_audit_snapshot_actor
        foreign key (workspace_id, generated_by) references users(workspace_id, id),
    constraint ck_project_legacy_audit_snapshot_status
        check (status in ('ready', 'blocked')),
    constraint ck_project_legacy_audit_snapshot_totals
        check (jsonb_typeof(totals) = 'object'),
    constraint ck_project_legacy_audit_snapshot_surfaces
        check (jsonb_typeof(surfaces) = 'array')
);

create table project_legacy_audit_findings (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    snapshot_id uuid not null,
    finding_key varchar(160) not null,
    category varchar(64) not null,
    severity varchar(16) not null,
    status varchar(32) not null,
    affected_count bigint not null,
    safe_detail jsonb not null default '{}'::jsonb,
    recorded_at timestamptz not null,
    constraint uk_project_legacy_audit_finding_key
        unique (snapshot_id, finding_key),
    constraint fk_project_legacy_audit_finding_snapshot
        foreign key (workspace_id, snapshot_id)
        references project_legacy_audit_snapshots(workspace_id, id),
    constraint ck_project_legacy_audit_finding_severity
        check (severity in ('info', 'warning', 'blocking')),
    constraint ck_project_legacy_audit_finding_status
        check (status in ('observed', 'resolved', 'accepted')),
    constraint ck_project_legacy_audit_finding_count
        check (affected_count >= 0),
    constraint ck_project_legacy_audit_finding_detail
        check (jsonb_typeof(safe_detail) = 'object')
);

create table project_legacy_removal_decisions (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    snapshot_id uuid not null,
    surface_key varchar(160) not null,
    decision varchar(32) not null,
    reason varchar(1000) not null,
    request_id varchar(160) not null,
    request_hash varchar(128) not null,
    decided_by uuid not null,
    decided_at timestamptz not null,
    constraint uk_project_legacy_removal_decision_request
        unique (workspace_id, request_id),
    constraint uk_project_legacy_removal_decision_scope
        unique (workspace_id, id),
    constraint fk_project_legacy_removal_decision_snapshot
        foreign key (workspace_id, snapshot_id)
        references project_legacy_audit_snapshots(workspace_id, id),
    constraint fk_project_legacy_removal_decision_actor
        foreign key (workspace_id, decided_by) references users(workspace_id, id),
    constraint ck_project_legacy_removal_decision_value
        check (decision in ('remove', 'retain_history', 'blocked')),
    constraint ck_project_legacy_removal_decision_reason
        check (length(btrim(reason)) between 10 and 1000)
);

create index idx_project_legacy_audit_snapshots_latest
    on project_legacy_audit_snapshots(workspace_id, generated_at desc, id);
create index idx_project_legacy_audit_findings_snapshot
    on project_legacy_audit_findings(workspace_id, snapshot_id, severity, finding_key);
create index idx_project_legacy_removal_decisions_latest
    on project_legacy_removal_decisions(workspace_id, surface_key, decided_at desc, id);

create or replace function reject_project_legacy_audit_history_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'project legacy audit history is append-only';
end;
$$;

create trigger trg_project_legacy_audit_snapshot_append_only
before update or delete on project_legacy_audit_snapshots
for each row execute function reject_project_legacy_audit_history_mutation();

create trigger trg_project_legacy_audit_finding_append_only
before update or delete on project_legacy_audit_findings
for each row execute function reject_project_legacy_audit_history_mutation();

create trigger trg_project_legacy_removal_decision_append_only
before update or delete on project_legacy_removal_decisions
for each row execute function reject_project_legacy_audit_history_mutation();
