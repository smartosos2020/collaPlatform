create table domain_events (
    id uuid primary key,
    workspace_id uuid not null,
    event_type varchar(128) not null,
    aggregate_type varchar(64) not null,
    aggregate_id uuid not null,
    actor_id uuid,
    payload jsonb not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    processed_at timestamptz
);

create index idx_domain_events_status on domain_events (status, created_at);
create index idx_domain_events_aggregate on domain_events (aggregate_type, aggregate_id);

create table audit_logs (
    id uuid primary key,
    workspace_id uuid not null,
    actor_id uuid,
    action varchar(128) not null,
    target_type varchar(64) not null,
    target_id uuid,
    ip_address varchar(64),
    user_agent text,
    metadata jsonb,
    created_at timestamptz not null
);

create index idx_audit_logs_target on audit_logs (workspace_id, target_type, target_id, created_at);

