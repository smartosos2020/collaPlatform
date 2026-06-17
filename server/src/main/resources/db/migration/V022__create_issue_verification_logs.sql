create table issue_verification_logs (
    id uuid primary key,
    workspace_id uuid not null,
    issue_id uuid not null references issues(id),
    verifier_id uuid not null references users(id),
    result varchar(32) not null,
    note text,
    created_at timestamptz not null default now(),
    constraint chk_issue_verification_result check (result in ('passed', 'failed', 'blocked'))
);

create index idx_issue_verification_logs_issue
    on issue_verification_logs (issue_id, created_at desc);
