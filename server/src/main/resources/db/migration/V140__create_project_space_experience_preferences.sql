create table project_space_experience_preferences (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    schema_version integer not null default 1 check (schema_version = 1),
    mode varchar(16) not null default 'simple' check (mode in ('simple', 'advanced')),
    version bigint not null default 1 check (version > 0),
    updated_at timestamptz not null default now(),
    constraint uk_project_space_experience_preference
        unique (workspace_id, space_id, user_id),
    constraint fk_project_space_experience_preference_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id) on delete cascade,
    constraint fk_project_space_experience_preference_user
        foreign key (workspace_id, user_id)
        references users(workspace_id, id) on delete cascade
);

create index idx_project_space_experience_preference_user
    on project_space_experience_preferences (workspace_id, user_id, updated_at desc);
