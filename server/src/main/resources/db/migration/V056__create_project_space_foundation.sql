insert into object_type_rules (id, object_type, web_path_pattern, deep_link_pattern, created_at)
values ('00000000-0000-0000-0000-000000000208', 'project_space', '/project-spaces/{id}', 'colla://project-space/{id}', now())
on conflict (object_type) do nothing;

create unique index if not exists uk_users_workspace_id on users (workspace_id, id);
create unique index if not exists uk_projects_workspace_id on projects (workspace_id, id);

create table project_spaces (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_key varchar(64) not null,
    name varchar(128) not null,
    description text,
    status varchar(32) not null,
    visibility varchar(32) not null,
    version bigint not null default 0,
    created_by uuid not null references users(id),
    created_at timestamptz not null,
    updated_by uuid not null references users(id),
    updated_at timestamptz not null,
    disabled_at timestamptz,
    archived_at timestamptz,
    constraint uk_project_spaces_workspace_key unique (workspace_id, space_key),
    constraint uk_project_spaces_workspace_id unique (workspace_id, id),
    constraint fk_project_spaces_created_by_workspace foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_spaces_updated_by_workspace foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_spaces_status check (status in ('active', 'disabled', 'archived')),
    constraint ck_project_spaces_visibility check (visibility in ('private', 'discoverable', 'workspace')),
    constraint ck_project_spaces_lifecycle check (
        (status = 'active' and disabled_at is null and archived_at is null)
        or (status = 'disabled' and disabled_at is not null and archived_at is null)
        or (status = 'archived' and archived_at is not null)
    )
);

create index idx_project_spaces_workspace_status
    on project_spaces (workspace_id, status, updated_at desc);
create index idx_project_spaces_workspace_visibility
    on project_spaces (workspace_id, visibility, status, updated_at desc);

create table project_space_members (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null references project_spaces(id),
    user_id uuid not null references users(id),
    status varchar(32) not null,
    joined_at timestamptz not null,
    removed_at timestamptz,
    created_by uuid not null references users(id),
    created_at timestamptz not null,
    updated_by uuid not null references users(id),
    updated_at timestamptz not null,
    constraint uk_project_space_members_space_user unique (space_id, user_id),
    constraint uk_project_space_members_workspace_id unique (workspace_id, id),
    constraint fk_project_space_members_space_workspace foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_space_members_user_workspace foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint fk_project_space_members_created_by_workspace foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_space_members_updated_by_workspace foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_space_members_status check (status in ('active', 'removed')),
    constraint ck_project_space_members_lifecycle check (
        (status = 'active' and removed_at is null)
        or (status = 'removed' and removed_at is not null)
    )
);

create index idx_project_space_members_user
    on project_space_members (workspace_id, user_id, status, updated_at desc);
create index idx_project_space_members_space
    on project_space_members (workspace_id, space_id, status, joined_at);

create table project_space_role_assignments (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null references project_spaces(id),
    member_id uuid not null references project_space_members(id),
    role_key varchar(32) not null,
    assigned_by uuid not null references users(id),
    assigned_at timestamptz not null,
    revoked_by uuid references users(id),
    revoked_at timestamptz,
    constraint fk_project_space_roles_space_workspace foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_space_roles_member_workspace foreign key (workspace_id, member_id) references project_space_members(workspace_id, id),
    constraint fk_project_space_roles_assigned_by_workspace foreign key (workspace_id, assigned_by) references users(workspace_id, id),
    constraint fk_project_space_roles_revoked_by_workspace foreign key (workspace_id, revoked_by) references users(workspace_id, id),
    constraint ck_project_space_role_key check (role_key in ('owner', 'admin', 'member', 'guest'))
);

create unique index uk_project_space_active_member_role
    on project_space_role_assignments (member_id)
    where revoked_at is null;
create index idx_project_space_roles_space
    on project_space_role_assignments (workspace_id, space_id, role_key, revoked_at);

create table project_space_invitations (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null references project_spaces(id),
    invitee_user_id uuid references users(id),
    invitee_email varchar(128),
    role_key varchar(32) not null,
    token_hash varchar(255) not null,
    status varchar(32) not null,
    expires_at timestamptz not null,
    invited_by uuid not null references users(id),
    invited_at timestamptz not null,
    responded_at timestamptz,
    revoked_by uuid references users(id),
    revoked_at timestamptz,
    constraint uk_project_space_invitation_token unique (token_hash),
    constraint fk_project_space_invitations_space_workspace foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_space_invitations_invitee_workspace foreign key (workspace_id, invitee_user_id) references users(workspace_id, id),
    constraint fk_project_space_invitations_invited_by_workspace foreign key (workspace_id, invited_by) references users(workspace_id, id),
    constraint fk_project_space_invitations_revoked_by_workspace foreign key (workspace_id, revoked_by) references users(workspace_id, id),
    constraint ck_project_space_invitation_target check (invitee_user_id is not null or invitee_email is not null),
    constraint ck_project_space_invitation_role check (role_key in ('admin', 'member', 'guest')),
    constraint ck_project_space_invitation_status check (status in ('pending', 'accepted', 'rejected', 'revoked', 'expired'))
);

create index idx_project_space_invitations_space
    on project_space_invitations (workspace_id, space_id, status, expires_at);
create index idx_project_space_invitations_user
    on project_space_invitations (workspace_id, invitee_user_id, status, expires_at);

create table project_legacy_space_maps (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    legacy_project_id uuid not null references projects(id),
    space_id uuid not null references project_spaces(id),
    mapping_version integer not null,
    mapping_status varchar(32) not null,
    source_checksum varchar(128),
    mapped_by uuid not null references users(id),
    mapped_at timestamptz not null,
    rolled_back_by uuid references users(id),
    rolled_back_at timestamptz,
    constraint uk_project_legacy_space_project unique (workspace_id, legacy_project_id),
    constraint uk_project_legacy_space_space unique (workspace_id, space_id),
    constraint fk_project_legacy_space_project_workspace foreign key (workspace_id, legacy_project_id) references projects(workspace_id, id),
    constraint fk_project_legacy_space_space_workspace foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_legacy_space_mapped_by_workspace foreign key (workspace_id, mapped_by) references users(workspace_id, id),
    constraint fk_project_legacy_space_rolled_back_by_workspace foreign key (workspace_id, rolled_back_by) references users(workspace_id, id),
    constraint ck_project_legacy_space_status check (mapping_status in ('active', 'failed', 'rolled_back'))
);

create index idx_project_legacy_space_maps_status
    on project_legacy_space_maps (workspace_id, mapping_status, mapped_at desc);

create table project_space_migration_batches (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    status varchar(32) not null,
    dry_run boolean not null,
    source_watermark timestamptz,
    source_checksum varchar(128),
    result_checksum varchar(128),
    summary jsonb not null default '{}'::jsonb,
    failures jsonb not null default '[]'::jsonb,
    started_by uuid not null references users(id),
    started_at timestamptz not null,
    finished_at timestamptz,
    rolled_back_by uuid references users(id),
    rolled_back_at timestamptz,
    constraint fk_project_space_migration_started_by_workspace foreign key (workspace_id, started_by) references users(workspace_id, id),
    constraint fk_project_space_migration_rolled_back_by_workspace foreign key (workspace_id, rolled_back_by) references users(workspace_id, id),
    constraint ck_project_space_migration_status check (status in ('pending', 'running', 'completed', 'failed', 'rolled_back'))
);

create index idx_project_space_migration_batches_status
    on project_space_migration_batches (workspace_id, status, started_at desc);
