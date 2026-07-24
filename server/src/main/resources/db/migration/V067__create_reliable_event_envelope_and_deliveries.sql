alter table domain_events
    add column event_version integer not null default 1,
    add column aggregate_sequence bigint,
    add column correlation_id uuid,
    add column causation_id uuid,
    add column occurred_at timestamptz;

with ranked as (
    select id,
           row_number() over (
               partition by workspace_id, aggregate_type, aggregate_id
               order by created_at, id
           ) as aggregate_sequence
    from domain_events
)
update domain_events event
set aggregate_sequence = ranked.aggregate_sequence,
    occurred_at = event.created_at,
    correlation_id = event.id
from ranked
where ranked.id = event.id;

alter table domain_events
    alter column aggregate_sequence set not null,
    alter column occurred_at set not null,
    add constraint ck_domain_events_event_version_positive check (event_version > 0),
    add constraint ck_domain_events_aggregate_sequence_positive check (aggregate_sequence > 0),
    add constraint ck_domain_events_payload_size check (octet_length(payload::text) <= 262144),
    add constraint uk_domain_events_workspace_id unique (workspace_id, id),
    add constraint uk_domain_events_aggregate_sequence
        unique (workspace_id, aggregate_type, aggregate_id, aggregate_sequence);

create index idx_domain_events_type_version_status
    on domain_events (event_type, event_version, status, created_at);

create table domain_event_handler_deliveries (
    id uuid primary key,
    workspace_id uuid not null,
    event_id uuid not null,
    handler_key varchar(96) not null,
    handler_version integer not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz,
    processed_at timestamptz,
    last_error text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_event_handler_deliveries_event
        foreign key (workspace_id, event_id)
        references domain_events(workspace_id, id)
        on delete cascade,
    constraint uk_event_handler_deliveries_identity
        unique (event_id, handler_key, handler_version),
    constraint ck_event_handler_deliveries_handler_version check (handler_version > 0),
    constraint ck_event_handler_deliveries_attempt_count check (attempt_count >= 0),
    constraint ck_event_handler_deliveries_status
        check (status in ('pending', 'processing', 'processed', 'dead_letter', 'unsupported'))
);

create index idx_event_handler_deliveries_claim
    on domain_event_handler_deliveries (status, next_attempt_at, created_at);

create index idx_event_handler_deliveries_workspace_event
    on domain_event_handler_deliveries (workspace_id, event_id);

create table domain_event_handler_receipts (
    id uuid primary key,
    workspace_id uuid not null,
    event_id uuid not null,
    delivery_id uuid not null references domain_event_handler_deliveries(id) on delete cascade,
    handler_key varchar(96) not null,
    handler_version integer not null,
    result jsonb not null default '{}'::jsonb,
    completed_at timestamptz not null,
    constraint fk_event_handler_receipts_event
        foreign key (workspace_id, event_id)
        references domain_events(workspace_id, id)
        on delete cascade,
    constraint uk_event_handler_receipts_delivery unique (delivery_id),
    constraint uk_event_handler_receipts_identity
        unique (event_id, handler_key, handler_version),
    constraint ck_event_handler_receipts_handler_version check (handler_version > 0),
    constraint ck_event_handler_receipts_result_size check (octet_length(result::text) <= 65536)
);

create index idx_event_handler_receipts_workspace_event
    on domain_event_handler_receipts (workspace_id, event_id);
