alter table project_space_members
    add constraint uk_project_space_members_workspace_space_id unique (workspace_id, space_id, id);

alter table project_space_role_assignments
    add constraint fk_project_space_roles_member_space
        foreign key (workspace_id, space_id, member_id)
        references project_space_members(workspace_id, space_id, id),
    add constraint ck_project_space_role_revocation check (
        (revoked_at is null and revoked_by is null)
        or (revoked_at is not null and revoked_by is not null)
    );

alter table project_space_invitations
    add column version bigint not null default 0,
    add column updated_at timestamptz not null default now(),
    add column last_sent_at timestamptz not null default now(),
    add column last_request_id varchar(120);

alter table project_space_invitations
    drop constraint ck_project_space_invitation_target,
    add constraint ck_project_space_invitation_target check (
        (invitee_user_id is not null and invitee_email is null)
        or (invitee_user_id is null and invitee_email is not null)
    );

create unique index uk_project_space_pending_user_invitation
    on project_space_invitations (space_id, invitee_user_id)
    where status = 'pending' and invitee_user_id is not null;

create index idx_project_space_invitations_recipient
    on project_space_invitations (workspace_id, invitee_user_id, status, updated_at desc);
