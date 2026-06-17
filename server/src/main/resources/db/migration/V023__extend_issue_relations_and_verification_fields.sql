alter table issue_verification_logs
    add column environment varchar(128),
    add column reproduction_steps text,
    add column fix_version varchar(64);

create table issue_relations (
    id uuid primary key,
    workspace_id uuid not null,
    issue_id uuid not null references issues(id),
    target_type varchar(64) not null,
    target_id uuid not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint uk_issue_relations_target unique (workspace_id, issue_id, target_type, target_id)
);

create index idx_issue_relations_issue
    on issue_relations (workspace_id, issue_id, created_at desc)
    where deleted_at is null;

create index idx_issue_relations_target
    on issue_relations (workspace_id, target_type, target_id)
    where deleted_at is null;
