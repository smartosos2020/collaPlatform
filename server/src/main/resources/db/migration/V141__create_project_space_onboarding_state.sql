create table project_space_onboarding_states (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    schema_version integer not null default 1 check (schema_version = 1),
    flow_version varchar(32) not null check (flow_version ~ '^[a-z0-9][a-z0-9._-]{0,31}$'),
    starting_point varchar(16) not null default 'unselected'
        check (starting_point in ('unselected', 'blank', 'scenario')),
    scenario_key varchar(96),
    acknowledged_steps jsonb not null default '[]'::jsonb
        check (
            jsonb_typeof(acknowledged_steps) = 'array'
            and jsonb_array_length(acknowledged_steps) <= 32
        ),
    dismissed_flow_version varchar(32),
    telemetry_opt_out boolean not null default false,
    last_request_id uuid not null,
    version bigint not null default 1 check (version > 0),
    updated_at timestamptz not null default now(),
    constraint ck_project_space_onboarding_starting_point check (
        (
            starting_point = 'scenario'
            and scenario_key in ('development', 'marketing', 'human-resources', 'delivery')
        )
        or (
            starting_point in ('unselected', 'blank')
            and scenario_key is null
        )
    ),
    constraint uk_project_space_onboarding_state
        unique (workspace_id, space_id, user_id),
    constraint fk_project_space_onboarding_state_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id) on delete cascade,
    constraint fk_project_space_onboarding_state_user
        foreign key (workspace_id, user_id)
        references users(workspace_id, id) on delete cascade
);

create index idx_project_space_onboarding_state_user
    on project_space_onboarding_states (workspace_id, user_id, updated_at desc);

create table project_space_onboarding_telemetry_events (
    event_id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    flow_version varchar(32) not null
        check (flow_version ~ '^[a-z0-9][a-z0-9._-]{0,31}$'),
    step_key varchar(64) not null
        check (step_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    outcome varchar(24) not null
        check (outcome in (
            'shown', 'started', 'succeeded', 'skipped', 'blocked',
            'failed', 'dismissed', 'reset'
        )),
    duration_bucket varchar(24) not null
        check (duration_bucket in (
            'under_5s', '5_to_30s', '30_to_120s',
            '2_to_10m', 'over_10m', 'unknown'
        )),
    error_code varchar(32) not null
        check (error_code in (
            'none', 'capability_denied', 'space_read_only', 'offline',
            'version_conflict', 'owner_api_failed', 'unknown'
        )),
    recorded_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '30 days'),
    constraint ck_project_space_onboarding_telemetry_expiry
        check (expires_at > recorded_at),
    constraint fk_project_space_onboarding_telemetry_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id) on delete cascade
);

create index idx_project_space_onboarding_telemetry_expiry
    on project_space_onboarding_telemetry_events (expires_at, event_id);
