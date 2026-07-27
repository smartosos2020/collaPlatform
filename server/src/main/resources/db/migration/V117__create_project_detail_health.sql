create table project_detail_preferences (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    schema_version smallint not null default 1 check (schema_version = 1),
    visible_sections jsonb not null check (jsonb_typeof(visible_sections) = 'array'),
    compact boolean not null default false,
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    updated_at timestamptz not null,
    constraint uk_project_detail_preference
        unique (workspace_id, space_id, actor_id),
    constraint fk_project_detail_preference_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_detail_preference_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create table project_health_projection_index (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    health_status varchar(16) not null
        check (health_status in ('healthy', 'attention', 'critical', 'unknown')),
    signal_count smallint not null check (signal_count between 0 and 50),
    truncated boolean not null,
    policy_version varchar(32) not null,
    source_fingerprint varchar(64) not null check (source_fingerprint ~ '^[0-9a-f]{64}$'),
    derived_at timestamptz not null,
    expires_at timestamptz not null,
    constraint uk_project_health_projection
        unique (workspace_id, space_id, actor_id),
    constraint fk_project_health_projection_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_health_projection_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create index idx_project_health_projection_expiry
    on project_health_projection_index(workspace_id, expires_at, space_id, actor_id);

create table project_detail_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(24) not null check (operation = 'save_preference'),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_detail_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_detail_command_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_detail_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);
