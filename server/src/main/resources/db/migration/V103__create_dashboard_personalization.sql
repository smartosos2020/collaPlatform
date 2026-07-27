alter table object_recent_accesses
    add column if not exists expires_at timestamptz;

alter table object_favorites
    add column if not exists sort_order int not null default 0,
    add column if not exists updated_at timestamptz not null default now();

create table platform_dashboard_card_layouts (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    card_key varchar(80) not null,
    position int not null,
    hidden boolean not null default false,
    layout_version bigint not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_platform_dashboard_card_layouts_key
        unique (workspace_id, user_id, card_key),
    constraint uk_platform_dashboard_card_layouts_position
        unique (workspace_id, user_id, position),
    constraint fk_platform_dashboard_card_layouts_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint ck_platform_dashboard_card_layouts_position check (position between 0 and 63),
    constraint ck_platform_dashboard_card_layouts_version check (layout_version > 0)
);

create index idx_platform_dashboard_card_layouts_user
    on platform_dashboard_card_layouts(workspace_id, user_id, position);

create table platform_personalization_commands (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    request_id varchar(120) not null,
    operation varchar(64) not null,
    request_hash varchar(64) not null,
    status varchar(24) not null,
    response_version bigint,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_platform_personalization_commands_request
        unique (workspace_id, user_id, operation, request_id),
    constraint fk_platform_personalization_commands_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint ck_platform_personalization_commands_status
        check (status in ('started', 'completed')),
    constraint ck_platform_personalization_commands_hash
        check (request_hash ~ '^[0-9a-f]{64}$')
);

create index idx_platform_personalization_commands_user_created
    on platform_personalization_commands(workspace_id, user_id, created_at desc);
