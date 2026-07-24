alter table realtime_signals
    add column envelope_version integer,
    add column signal_version integer,
    add column audience_type varchar(16),
    add column sequence_scope varchar(16),
    add column sequence_key varchar(192),
    add column sequence_value bigint,
    add column occurred_at timestamptz,
    add column correlation_id uuid,
    add column payload jsonb not null default '{}'::jsonb;

update realtime_signals rs
set envelope_version = 1,
    signal_version = 1,
    audience_type = case when recipient_id is null then 'workspace' else 'user' end,
    object_type = coalesce(object_type, 'unknown'),
    object_id = coalesce(object_id, source_event_id),
    sequence_scope = 'object',
    sequence_key = coalesce(object_type, 'unknown') || ':' || coalesce(object_id, source_event_id)::text,
    sequence_value = source_version,
    occurred_at = created_at,
    correlation_id = coalesce(
        (select de.correlation_id from domain_events de where de.id = rs.source_event_id),
        source_event_id
    );

alter table realtime_signals
    alter column envelope_version set not null,
    alter column signal_version set not null,
    alter column audience_type set not null,
    alter column object_type set not null,
    alter column object_id set not null,
    alter column sequence_scope set not null,
    alter column sequence_key set not null,
    alter column sequence_value set not null,
    alter column occurred_at set not null,
    alter column correlation_id set not null,
    add constraint ck_realtime_envelope_version check (envelope_version = 1),
    add constraint ck_realtime_signal_contract_version check (signal_version = 1),
    add constraint ck_realtime_audience_type check (audience_type in ('user', 'workspace')),
    add constraint ck_realtime_audience_recipient check (
        (audience_type = 'user' and recipient_id is not null)
        or (audience_type = 'workspace' and recipient_id is null)
    ),
    add constraint ck_realtime_sequence_scope check (sequence_scope in ('object', 'audience')),
    add constraint ck_realtime_sequence_value check (sequence_value >= 0),
    add constraint ck_realtime_payload_object check (jsonb_typeof(payload) = 'object');

create index idx_realtime_signals_transport_lookup
    on realtime_signals (id, transported_at);

create index idx_realtime_signals_audience_sequence
    on realtime_signals (workspace_id, audience_type, recipient_id, sequence_key, sequence_value desc);
