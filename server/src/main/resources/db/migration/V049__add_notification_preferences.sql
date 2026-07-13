create table notification_preferences (
    workspace_id uuid not null,
    user_id uuid not null,
    source_type varchar(64) not null,
    enabled boolean not null default true,
    updated_at timestamptz not null,
    primary key (workspace_id, user_id, source_type)
);

create index idx_notification_preferences_user
    on notification_preferences (workspace_id, user_id);
