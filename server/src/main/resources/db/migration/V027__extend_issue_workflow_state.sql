alter table issues
    add column workflow_reason varchar(64),
    add column workflow_note text,
    add column resolution varchar(64),
    add column resolved_at timestamptz,
    add column closed_at timestamptz;

create index idx_issues_workflow_reason
    on issues (workspace_id, workflow_reason, updated_at desc)
    where deleted_at is null;

create index idx_issues_resolution
    on issues (workspace_id, resolution, updated_at desc)
    where deleted_at is null;
