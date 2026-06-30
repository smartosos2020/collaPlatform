create table resource_permission_requests (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    resource_type varchar(64) not null,
    resource_id uuid not null,
    requester_id uuid not null references users(id),
    permission_level varchar(32) not null,
    reason varchar(512),
    status varchar(32) not null default 'submitted',
    decided_by uuid references users(id),
    decided_at timestamptz,
    decision_note varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_resource_permission_requests_status check (status in ('submitted', 'approved', 'rejected')),
    constraint ck_resource_permission_requests_level check (permission_level in ('view', 'comment', 'edit', 'manage', 'owner'))
);

create index idx_resource_permission_requests_resource
    on resource_permission_requests(workspace_id, resource_type, resource_id, status, created_at desc);

create index idx_resource_permission_requests_requester
    on resource_permission_requests(workspace_id, requester_id, created_at desc);
