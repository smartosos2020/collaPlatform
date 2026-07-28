create table project_automation_rules (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    name varchar(160) not null,
    status varchar(16) not null check (status in ('draft', 'enabled', 'disabled', 'archived')),
    trigger_json jsonb not null check (jsonb_typeof(trigger_json) = 'object'),
    condition_json jsonb not null check (jsonb_typeof(condition_json) = 'object'),
    actions_json jsonb not null check (jsonb_typeof(actions_json) = 'array'),
    aggregate_version bigint not null check (aggregate_version >= 1),
    published_version int,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint ck_project_automation_rule_published_version
        check (published_version is null or published_version >= 1),
    constraint uk_project_automation_rule_space_id unique (workspace_id, space_id, id),
    constraint fk_project_automation_rule_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_automation_rule_updated_by foreign key (workspace_id, updated_by)
        references users(workspace_id, id)
);

create index idx_project_automation_rule_list
    on project_automation_rules(workspace_id, space_id, status, updated_at desc, id);

create table project_automation_rule_versions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    rule_id uuid not null,
    version_number int not null check (version_number >= 1),
    definition_hash varchar(64) not null check (definition_hash ~ '^[0-9a-f]{64}$'),
    definition_json jsonb not null check (jsonb_typeof(definition_json) = 'object'),
    published_by uuid not null,
    published_at timestamptz not null,
    constraint uk_project_automation_rule_version unique
        (workspace_id, space_id, rule_id, version_number),
    constraint fk_project_automation_rule_version_rule
        foreign key (workspace_id, space_id, rule_id)
        references project_automation_rules(workspace_id, space_id, id),
    constraint fk_project_automation_rule_version_actor foreign key (workspace_id, published_by)
        references users(workspace_id, id)
);

create index idx_project_automation_rule_version_hash
    on project_automation_rule_versions(workspace_id, space_id, rule_id, definition_hash);

create table project_automation_event_catalog (
    event_type varchar(120) not null,
    event_version int not null check (event_version >= 1),
    allowed_fields jsonb not null check (jsonb_typeof(allowed_fields) = 'array'),
    active boolean not null default true,
    updated_at timestamptz not null,
    primary key (event_type, event_version)
);

insert into project_automation_event_catalog(
    event_type, event_version, allowed_fields, active, updated_at
) values
    ('project.work-item.changed', 1, '["aggregateId","actorId","eventType","occurredAt","workspaceId"]', true, now()),
    ('project.workflow.changed', 1, '["aggregateId","actorId","eventType","occurredAt","workspaceId"]', true, now()),
    ('project.node-workflow.changed', 1, '["aggregateId","actorId","eventType","occurredAt","workspaceId"]', true, now()),
    ('project.relation.changed', 1, '["aggregateId","actorId","eventType","occurredAt","workspaceId"]', true, now()),
    ('project.resource.changed', 1, '["aggregateId","actorId","eventType","kind","occurredAt","version","workspaceId"]', true, now());

create table project_automation_rule_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(32) not null check (
        operation in ('save_rule', 'publish_rule', 'enable_rule', 'disable_rule', 'archive_rule')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_automation_rule_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_automation_rule_command_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_automation_rule_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create table project_automation_rule_stats (
    workspace_id uuid not null,
    space_id uuid not null,
    observed_date date not null,
    rule_count int not null check (rule_count >= 0),
    enabled_count int not null check (enabled_count >= 0),
    truncated boolean not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, observed_date),
    constraint fk_project_automation_rule_stats_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id)
);
