alter table domain_event_handler_deliveries
    add column worker_id varchar(128),
    add column claimed_at timestamptz,
    add column lease_until timestamptz,
    add column heartbeat_at timestamptz,
    add column fencing_token bigint not null default 0,
    add column ordered_by_aggregate boolean not null default false,
    add column failure_kind varchar(32),
    add column error_fingerprint varchar(64),
    add column dead_lettered_at timestamptz,
    add column replay_count integer not null default 0,
    add column replayed_at timestamptz,
    add column replayed_by uuid,
    add column replay_reason varchar(512),
    add column abandoned_at timestamptz;

alter table domain_event_handler_deliveries
    drop constraint ck_event_handler_deliveries_status,
    add constraint ck_event_handler_deliveries_status
        check (status in ('pending', 'processing', 'processed', 'dead_letter', 'unsupported', 'abandoned')),
    add constraint ck_event_handler_deliveries_fencing check (fencing_token >= 0),
    add constraint ck_event_handler_deliveries_replay_count check (replay_count >= 0),
    add constraint ck_event_handler_deliveries_processing_owner check (
        status <> 'processing'
        or (worker_id is not null and claimed_at is not null and lease_until is not null)
    ),
    add constraint ck_event_handler_deliveries_dead_letter_time check (
        status <> 'dead_letter' or dead_lettered_at is not null
    ),
    add constraint ck_event_handler_deliveries_abandoned_time check (
        status <> 'abandoned' or abandoned_at is not null
    );

create index idx_event_handler_deliveries_lease
    on domain_event_handler_deliveries (status, lease_until)
    where status = 'processing';

create index idx_event_handler_deliveries_handler_backlog
    on domain_event_handler_deliveries (handler_key, handler_version, status, next_attempt_at, created_at);

create index idx_event_handler_deliveries_ordering
    on domain_event_handler_deliveries (handler_key, handler_version, event_id, status)
    where ordered_by_aggregate;

create table domain_event_delivery_replays (
    id uuid primary key,
    workspace_id uuid not null,
    event_id uuid not null,
    delivery_id uuid not null references domain_event_handler_deliveries(id) on delete cascade,
    action varchar(32) not null,
    actor_id uuid not null,
    reason varchar(512) not null,
    previous_status varchar(32) not null,
    previous_attempt_count integer not null,
    created_at timestamptz not null,
    constraint fk_event_delivery_replays_event
        foreign key (workspace_id, event_id)
        references domain_events(workspace_id, id)
        on delete cascade,
    constraint ck_event_delivery_replays_action check (action in ('replay', 'abandon')),
    constraint ck_event_delivery_replays_attempt check (previous_attempt_count >= 0)
);

create index idx_event_delivery_replays_workspace_created
    on domain_event_delivery_replays (workspace_id, created_at desc);

create index idx_event_delivery_replays_delivery
    on domain_event_delivery_replays (delivery_id, created_at desc);
