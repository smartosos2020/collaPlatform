create table project_space_permission_role_bindings (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    subject_type varchar(24) not null check (
        subject_type in ('user', 'department', 'user_group')
    ),
    subject_id uuid not null,
    role_key varchar(64) not null check (role_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    type_definition_id uuid not null,
    configuration_version_id uuid not null,
    configuration_hash varchar(64) not null check (configuration_hash ~ '^[0-9a-f]{64}$'),
    source_kind varchar(24) not null check (source_kind in ('explicit', 'legacy', 'migration')),
    status varchar(16) not null check (status in ('active', 'revoked', 'expired')),
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    effective_at timestamptz not null,
    expires_at timestamptz,
    assigned_by uuid not null,
    assigned_at timestamptz not null,
    revoked_by uuid,
    revoked_at timestamptz,
    revocation_reason_hash varchar(64),
    constraint uk_project_space_permission_role_binding_scope
        unique (workspace_id, space_id, id),
    constraint fk_project_space_permission_role_binding_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_space_permission_role_binding_version
        foreign key (workspace_id, space_id, type_definition_id, configuration_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_space_permission_role_binding_assigned_by
        foreign key (workspace_id, assigned_by) references users(workspace_id, id),
    constraint fk_project_space_permission_role_binding_revoked_by
        foreign key (workspace_id, revoked_by) references users(workspace_id, id),
    constraint ck_project_space_permission_role_binding_lifecycle
        check (
            (status = 'active' and revoked_by is null and revoked_at is null
                and revocation_reason_hash is null)
            or
            (status in ('revoked', 'expired') and revoked_by is not null and revoked_at is not null
                and revocation_reason_hash ~ '^[0-9a-f]{64}$')
        ),
    constraint ck_project_space_permission_role_binding_expiry
        check (expires_at is null or expires_at > effective_at)
);

create unique index uk_project_space_permission_role_binding_active
    on project_space_permission_role_bindings(
        workspace_id, space_id, subject_type, subject_id, role_key
    )
    where status = 'active';

create index idx_project_space_permission_role_binding_subject
    on project_space_permission_role_bindings(
        workspace_id, subject_type, subject_id, status, expires_at, space_id
    );

create table project_work_item_role_assignments (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    work_item_id uuid not null,
    subject_type varchar(24) not null check (
        subject_type in ('user', 'department', 'user_group')
    ),
    subject_id uuid not null,
    role_key varchar(64) not null check (role_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    source_kind varchar(24) not null check (
        source_kind in ('explicit', 'creator', 'participant', 'field', 'space_role', 'group', 'migration')
    ),
    source_key varchar(128),
    type_definition_id uuid not null,
    configuration_version_id uuid not null,
    configuration_hash varchar(64) not null check (configuration_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status in ('active', 'revoked', 'expired')),
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    effective_at timestamptz not null,
    expires_at timestamptz,
    assigned_by uuid not null,
    assigned_at timestamptz not null,
    revoked_by uuid,
    revoked_at timestamptz,
    revocation_reason_hash varchar(64),
    constraint uk_project_work_item_role_assignment_scope
        unique (workspace_id, space_id, id),
    constraint fk_project_work_item_role_assignment_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_role_assignment_version
        foreign key (workspace_id, space_id, type_definition_id, configuration_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_role_assignment_assigned_by
        foreign key (workspace_id, assigned_by) references users(workspace_id, id),
    constraint fk_project_work_item_role_assignment_revoked_by
        foreign key (workspace_id, revoked_by) references users(workspace_id, id),
    constraint ck_project_work_item_role_assignment_source
        check (
            (source_kind in ('creator', 'participant', 'space_role') and source_key is not null)
            or source_kind not in ('creator', 'participant', 'space_role')
        ),
    constraint ck_project_work_item_role_assignment_lifecycle
        check (
            (status = 'active' and revoked_by is null and revoked_at is null
                and revocation_reason_hash is null)
            or
            (status in ('revoked', 'expired') and revoked_by is not null and revoked_at is not null
                and revocation_reason_hash ~ '^[0-9a-f]{64}$')
        ),
    constraint ck_project_work_item_role_assignment_expiry
        check (expires_at is null or expires_at > effective_at)
);

create unique index uk_project_work_item_role_assignment_active
    on project_work_item_role_assignments(
        workspace_id, space_id, work_item_id, subject_type, subject_id, role_key
    )
    where status = 'active';

create index idx_project_work_item_role_assignment_subject
    on project_work_item_role_assignments(
        workspace_id, subject_type, subject_id, status, expires_at, space_id, work_item_id
    );

create index idx_project_work_item_role_assignment_item
    on project_work_item_role_assignments(
        workspace_id, space_id, work_item_id, status, role_key, subject_id
    );

create table project_permission_command_receipts (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    work_item_id uuid,
    aggregate_kind varchar(24) not null check (
        aggregate_kind in ('space_role', 'work_item_role', 'permission_request', 'migration', 'rebuild')
    ),
    operation varchar(40) not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response_schema_version smallint not null default 1 check (response_schema_version = 1),
    response_payload jsonb,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_permission_command_request
        unique (workspace_id, space_id, aggregate_kind, operation, request_id),
    constraint fk_project_permission_command_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_permission_command_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_permission_command_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_permission_command_response
        check (
            (status = 'pending' and response_payload is null and completed_at is null)
            or
            (status = 'completed' and response_payload is not null
                and jsonb_typeof(response_payload) = 'object' and completed_at is not null)
        )
);

create table project_permission_decision_evidence (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    work_item_id uuid,
    subject_id uuid not null,
    action_key varchar(64) not null check (action_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    allowed boolean not null,
    reason_code varchar(80) not null check (reason_code ~ '^[a-z][a-z0-9_]{0,79}$'),
    disclosure_scope varchar(24) not null check (
        disclosure_scope in ('none', 'minimal', 'user_safe', 'governance_safe')
    ),
    type_definition_id uuid not null,
    policy_version_id uuid not null,
    policy_config_hash varchar(64) not null check (policy_config_hash ~ '^[0-9a-f]{64}$'),
    subject_version bigint not null check (subject_version >= 0),
    context_hash varchar(64) not null check (context_hash ~ '^[0-9a-f]{64}$'),
    safe_policy_sources jsonb not null default '[]'::jsonb check (
        jsonb_typeof(safe_policy_sources) = 'array'
    ),
    evaluated_at timestamptz not null,
    constraint fk_project_permission_decision_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_permission_decision_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_permission_decision_subject
        foreign key (workspace_id, subject_id) references users(workspace_id, id),
    constraint fk_project_permission_decision_version
        foreign key (workspace_id, space_id, type_definition_id, policy_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id)
);

create index idx_project_permission_decision_replay
    on project_permission_decision_evidence(
        workspace_id, space_id, work_item_id, subject_id, action_key, evaluated_at desc
    );

create index idx_project_permission_decision_policy
    on project_permission_decision_evidence(
        workspace_id, space_id, policy_version_id, evaluated_at desc
    );

create function guard_project_permission_command_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'permission command receipts cannot be deleted' using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed permission command receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id or new.workspace_id <> old.workspace_id or new.space_id <> old.space_id
        or new.work_item_id is distinct from old.work_item_id
        or new.aggregate_kind <> old.aggregate_kind or new.operation <> old.operation
        or new.request_id <> old.request_id or new.request_hash <> old.request_hash
        or new.created_by <> old.created_by or new.created_at <> old.created_at then
        raise exception 'permission command receipt identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_permission_command_receipt
before update or delete on project_permission_command_receipts
for each row execute function guard_project_permission_command_receipt();

create function guard_project_permission_decision_evidence()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'permission decision evidence is immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_permission_decision_evidence
before update or delete on project_permission_decision_evidence
for each row execute function guard_project_permission_decision_evidence();
