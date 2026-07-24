create table search_projection_versions (
    workspace_id uuid not null references workspaces(id),
    object_type varchar(64) not null,
    object_id uuid not null,
    source_version bigint not null,
    operation varchar(16) not null,
    updated_at timestamptz not null default now(),
    primary key (workspace_id, object_type, object_id),
    constraint ck_search_projection_version_non_negative check (source_version >= 0),
    constraint ck_search_projection_operation check (operation in ('upsert', 'delete'))
);

create index idx_search_projection_versions_workspace_updated
    on search_projection_versions (workspace_id, updated_at desc);

create table realtime_signals (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    source_event_id uuid not null references domain_events(id),
    recipient_id uuid references users(id),
    signal_type varchar(96) not null,
    object_type varchar(64),
    object_id uuid,
    source_version bigint not null,
    calibration_path text not null,
    created_at timestamptz not null default now(),
    transported_at timestamptz,
    constraint uk_realtime_signals_source unique (source_event_id),
    constraint ck_realtime_signal_version_non_negative check (source_version >= 0),
    constraint ck_realtime_signal_calibration_path check (calibration_path like '/api/%')
);

create index idx_realtime_signals_pending
    on realtime_signals (created_at, id)
    where transported_at is null;

create index idx_realtime_signals_workspace_created
    on realtime_signals (workspace_id, created_at desc);
