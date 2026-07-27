insert into object_type_rules (
    id, object_type, web_path_pattern, deep_link_pattern, created_at
) values (
    '00000000-0000-0000-0000-000000000109',
    'saved_view',
    '/project-spaces/{spaceId}/work-items?savedViewId={id}',
    'colla://saved-view/{id}',
    now()
) on conflict (object_type) do nothing;

create table project_work_item_saved_views (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    owner_user_id uuid not null,
    scope varchar(16) not null check (scope in ('personal', 'shared')),
    name varchar(120) not null check (length(trim(name)) between 1 and 120),
    description varchar(500) not null default '',
    status varchar(16) not null check (status in ('active', 'deleted')),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    current_version_id uuid not null,
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_work_item_saved_view_scope unique (workspace_id, space_id, id),
    constraint fk_project_work_item_saved_view_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_saved_view_owner
        foreign key (workspace_id, owner_user_id) references users(workspace_id, id),
    constraint fk_project_work_item_saved_view_creator
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_saved_view_updater
        foreign key (workspace_id, updated_by) references users(workspace_id, id)
);

create index idx_project_work_item_saved_views_owner
    on project_work_item_saved_views(
        workspace_id, space_id, owner_user_id, status, updated_at desc, id
    );

create table project_work_item_saved_view_versions (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    view_id uuid not null,
    version_number bigint not null check (version_number >= 1),
    schema_version smallint not null check (schema_version = 1),
    query_json jsonb not null check (jsonb_typeof(query_json) = 'object'),
    presentation_json jsonb not null check (jsonb_typeof(presentation_json) = 'object'),
    config_hash varchar(64) not null check (config_hash ~ '^[0-9a-f]{64}$'),
    created_by uuid not null,
    created_at timestamptz not null,
    constraint uk_project_work_item_saved_view_version
        unique (workspace_id, space_id, view_id, version_number),
    constraint uk_project_work_item_saved_view_version_scope
        unique (workspace_id, space_id, view_id, id),
    constraint fk_project_work_item_saved_view_version_view
        foreign key (workspace_id, space_id, view_id)
        references project_work_item_saved_views(workspace_id, space_id, id),
    constraint fk_project_work_item_saved_view_version_creator
        foreign key (workspace_id, created_by) references users(workspace_id, id)
);

alter table project_work_item_saved_views
    add constraint fk_project_work_item_saved_view_current_version
    foreign key (
        workspace_id, space_id, id, current_version_id
    ) references project_work_item_saved_view_versions(
        workspace_id, space_id, view_id, id
    ) deferrable initially deferred;

create table project_work_item_saved_view_shares (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    view_id uuid not null,
    subject_user_id uuid not null,
    permission varchar(16) not null check (permission in ('use', 'manage')),
    status varchar(16) not null check (status in ('active', 'revoked')),
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    shared_by uuid not null,
    shared_at timestamptz not null,
    revoked_by uuid,
    revoked_at timestamptz,
    constraint uk_project_work_item_saved_view_share
        unique (workspace_id, space_id, view_id, subject_user_id),
    constraint fk_project_work_item_saved_view_share_view
        foreign key (workspace_id, space_id, view_id)
        references project_work_item_saved_views(workspace_id, space_id, id),
    constraint fk_project_work_item_saved_view_share_subject
        foreign key (workspace_id, subject_user_id) references users(workspace_id, id),
    constraint fk_project_work_item_saved_view_share_actor
        foreign key (workspace_id, shared_by) references users(workspace_id, id),
    constraint ck_project_work_item_saved_view_share_revocation check (
        (status='active' and revoked_by is null and revoked_at is null)
        or (status='revoked' and revoked_by is not null and revoked_at is not null)
    )
);

create index idx_project_work_item_saved_view_shares_subject
    on project_work_item_saved_view_shares(
        workspace_id, subject_user_id, status, space_id, view_id
    );

create table project_work_item_saved_view_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    view_id uuid,
    operation varchar(24) not null check (
        operation in ('create', 'update', 'copy', 'share', 'revoke', 'transfer', 'delete')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    expected_version bigint not null check (expected_version >= 0),
    actor_id uuid not null,
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_json jsonb,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_saved_view_command
        unique (workspace_id, space_id, operation, request_id),
    constraint fk_project_work_item_saved_view_command_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_saved_view_command_actor
        foreign key (workspace_id, actor_id) references users(workspace_id, id),
    constraint ck_project_work_item_saved_view_command_completion check (
        (status='pending' and response_json is null and completed_at is null)
        or (status='completed' and jsonb_typeof(response_json)='object' and completed_at is not null)
    )
);

create function guard_project_work_item_saved_view_history()
returns trigger
language plpgsql
as $$
begin
    if tg_op='DELETE' and current_setting('colla.project_space_cleanup', true)='on' then
        return old;
    end if;
    if tg_op='DELETE' or tg_table_name='project_work_item_saved_view_versions' then
        raise exception 'saved view history is immutable' using errcode='23514';
    end if;
    if old.status='completed' then
        raise exception 'completed saved view command is immutable' using errcode='23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_saved_view_version
before update or delete on project_work_item_saved_view_versions
for each row execute function guard_project_work_item_saved_view_history();

create trigger trg_project_work_item_saved_view_command
before update or delete on project_work_item_saved_view_commands
for each row execute function guard_project_work_item_saved_view_history();
