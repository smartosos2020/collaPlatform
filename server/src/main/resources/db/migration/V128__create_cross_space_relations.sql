-- S18-owned authorization policy and link intent facts.
create table project_cross_space_relation_policies (
    id uuid primary key,
    workspace_id uuid not null,
    grant_id uuid not null,
    source_space_id uuid not null,
    target_space_id uuid not null,
    relation_key varchar(64) not null check (relation_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    direction varchar(24) not null check (
        direction in ('source_to_target', 'target_to_source', 'bidirectional')
    ),
    source_type_id uuid not null,
    source_version_id uuid not null,
    source_config_hash varchar(64) not null check (source_config_hash ~ '^[0-9a-f]{64}$'),
    target_type_id uuid not null,
    target_version_id uuid not null,
    target_config_hash varchar(64) not null check (target_config_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (
        status in ('draft', 'requested', 'active', 'paused', 'revoked', 'archived')
    ),
    version bigint not null default 1 check (version > 0),
    source_confirmed_by uuid,
    source_confirmed_at timestamptz,
    target_confirmed_by uuid,
    target_confirmed_at timestamptz,
    created_by uuid not null,
    created_at timestamptz not null default now(),
    updated_by uuid not null,
    updated_at timestamptz not null default now(),
    revoked_at timestamptz,
    archived_at timestamptz,
    constraint uk_project_cross_space_relation_policy_scope
        unique (workspace_id, id),
    constraint uk_project_cross_space_relation_policy_definition
        unique (workspace_id, grant_id, relation_key, direction,
                source_type_id, source_version_id,
                target_type_id, target_version_id),
    constraint fk_project_cross_space_relation_policy_grant
        foreign key (workspace_id, grant_id)
        references project_cross_space_grants(workspace_id, id) on delete cascade,
    constraint fk_project_cross_space_relation_policy_source_space
        foreign key (workspace_id, source_space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_cross_space_relation_policy_target_space
        foreign key (workspace_id, target_space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_cross_space_relation_policy_source_type
        foreign key (workspace_id, source_space_id, source_type_id, source_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_cross_space_relation_policy_target_type
        foreign key (workspace_id, target_space_id, target_type_id, target_version_id)
        references project_work_item_type_versions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_cross_space_relation_policy_source_confirmer
        foreign key (workspace_id, source_confirmed_by) references users(workspace_id, id),
    constraint fk_project_cross_space_relation_policy_target_confirmer
        foreign key (workspace_id, target_confirmed_by) references users(workspace_id, id),
    constraint fk_project_cross_space_relation_policy_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_cross_space_relation_policy_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_cross_space_relation_policy_parties
        check (
            (source_confirmed_by is null and source_confirmed_at is null)
            or (source_confirmed_by is not null and source_confirmed_at is not null)
        ),
    constraint ck_project_cross_space_relation_policy_target_party
        check (
            (target_confirmed_by is null and target_confirmed_at is null)
            or (target_confirmed_by is not null and target_confirmed_at is not null)
        )
);

create index idx_project_cross_space_relation_policy_visible
    on project_cross_space_relation_policies(
        workspace_id, source_space_id, target_space_id, status, updated_at desc
    );

create table project_cross_space_link_intents (
    id uuid primary key,
    workspace_id uuid not null,
    policy_id uuid not null,
    policy_version bigint not null check (policy_version > 0),
    source_space_id uuid not null,
    source_work_item_id uuid not null,
    source_expected_version bigint not null check (source_expected_version >= 0),
    target_space_id uuid not null,
    target_work_item_id uuid not null,
    target_expected_version bigint not null check (target_expected_version >= 0),
    status varchar(16) not null check (
        status in ('requested', 'linked', 'rejected', 'cancelled')
    ),
    version bigint not null default 1 check (version > 0),
    source_confirmed_by uuid not null,
    source_confirmed_at timestamptz not null default now(),
    target_confirmed_by uuid,
    target_confirmed_at timestamptz,
    canonical_relation_id uuid,
    reason_hash varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_project_cross_space_link_intent_scope
        unique (workspace_id, id),
    constraint uk_project_cross_space_link_intent_active
        unique (workspace_id, policy_id, source_work_item_id, target_work_item_id),
    constraint fk_project_cross_space_link_intent_policy
        foreign key (workspace_id, policy_id)
        references project_cross_space_relation_policies(workspace_id, id) on delete cascade,
    constraint fk_project_cross_space_link_intent_source
        foreign key (workspace_id, source_space_id, source_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_cross_space_link_intent_target
        foreign key (workspace_id, target_space_id, target_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_cross_space_link_intent_source_actor
        foreign key (workspace_id, source_confirmed_by) references users(workspace_id, id),
    constraint fk_project_cross_space_link_intent_target_actor
        foreign key (workspace_id, target_confirmed_by) references users(workspace_id, id),
    constraint ck_project_cross_space_link_intent_result
        check (
            (status = 'requested' and target_confirmed_by is null
                and target_confirmed_at is null and canonical_relation_id is null)
            or (status = 'linked' and target_confirmed_by is not null
                and target_confirmed_at is not null and canonical_relation_id is not null)
            or (status in ('rejected', 'cancelled') and canonical_relation_id is null
                and reason_hash ~ '^[0-9a-f]{64}$')
        )
);

create index idx_project_cross_space_link_intent_party
    on project_cross_space_link_intents(
        workspace_id, source_space_id, target_space_id, status, updated_at desc
    );

-- S10-owned canonical cross-space edge extension. S18 accesses it only through
-- the CrossSpaceRelationCommand public contract.
create table project_work_item_cross_space_relations (
    id uuid primary key,
    workspace_id uuid not null,
    source_space_id uuid not null,
    source_work_item_id uuid not null,
    target_space_id uuid not null,
    target_work_item_id uuid not null,
    relation_key varchar(64) not null check (relation_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    direction varchar(24) not null check (
        direction in ('source_to_target', 'target_to_source', 'bidirectional')
    ),
    source_definition_type_id uuid not null,
    source_definition_version_id uuid not null,
    source_definition_hash varchar(64) not null check (
        source_definition_hash ~ '^[0-9a-f]{64}$'
    ),
    target_definition_type_id uuid not null,
    target_definition_version_id uuid not null,
    target_definition_hash varchar(64) not null check (
        target_definition_hash ~ '^[0-9a-f]{64}$'
    ),
    source_policy_id uuid not null,
    source_policy_version bigint not null check (source_policy_version > 0),
    status varchar(16) not null check (status in ('active', 'withdrawn')),
    version bigint not null default 0 check (version >= 0),
    created_by uuid not null,
    created_at timestamptz not null default now(),
    updated_by uuid not null,
    updated_at timestamptz not null default now(),
    withdrawn_by uuid,
    withdrawn_at timestamptz,
    withdrawal_reason_hash varchar(64),
    constraint uk_project_work_item_cross_space_relation_scope
        unique (workspace_id, id),
    constraint fk_project_work_item_cross_space_relation_source
        foreign key (workspace_id, source_space_id, source_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_cross_space_relation_target
        foreign key (workspace_id, target_space_id, target_work_item_id)
        references project_work_items(workspace_id, space_id, id),
    constraint fk_project_work_item_cross_space_relation_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_cross_space_relation_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint fk_project_work_item_cross_space_relation_withdrawn_by
        foreign key (workspace_id, withdrawn_by) references users(workspace_id, id),
    constraint ck_project_work_item_cross_space_relation_spaces
        check (source_space_id <> target_space_id),
    constraint ck_project_work_item_cross_space_relation_withdrawal
        check (
            (status = 'active' and withdrawn_by is null and withdrawn_at is null
                and withdrawal_reason_hash is null)
            or (status = 'withdrawn' and withdrawn_by is not null
                and withdrawn_at is not null
                and withdrawal_reason_hash ~ '^[0-9a-f]{64}$')
        )
);

create unique index uk_project_work_item_cross_space_relations_active_edge
    on project_work_item_cross_space_relations(
        workspace_id, relation_key, source_space_id, source_work_item_id,
        target_space_id, target_work_item_id
    ) where status = 'active';

create index idx_project_work_item_cross_space_relations_source
    on project_work_item_cross_space_relations(
        workspace_id, source_space_id, source_work_item_id, status, id
    );

create index idx_project_work_item_cross_space_relations_target
    on project_work_item_cross_space_relations(
        workspace_id, target_space_id, target_work_item_id, status, id
    );

create table project_work_item_cross_space_relation_history (
    id uuid primary key,
    workspace_id uuid not null,
    relation_id uuid not null,
    relation_version bigint not null check (relation_version >= 0),
    event_kind varchar(16) not null check (event_kind in ('created', 'withdrawn')),
    actor_id uuid not null,
    reason_hash varchar(64),
    occurred_at timestamptz not null default now(),
    constraint uk_project_work_item_cross_space_relation_history_version
        unique (workspace_id, relation_id, relation_version),
    constraint fk_project_work_item_cross_space_relation_history_relation
        foreign key (workspace_id, relation_id)
        references project_work_item_cross_space_relations(workspace_id, id) on delete cascade,
    constraint fk_project_work_item_cross_space_relation_history_actor
        foreign key (workspace_id, actor_id) references users(workspace_id, id)
);

create table project_cross_space_relation_receipts (
    id uuid primary key,
    workspace_id uuid not null,
    actor_id uuid not null,
    operation varchar(32) not null check (
        operation in ('policy_create', 'policy_request', 'policy_confirm',
                      'policy_pause', 'policy_resume', 'policy_revoke', 'policy_archive',
                      'intent_create', 'intent_accept', 'intent_reject',
                      'intent_cancel', 'relation_withdraw')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_schema_version smallint not null default 1 check (response_schema_version = 1),
    response_payload jsonb not null check (jsonb_typeof(response_payload) = 'object'),
    created_at timestamptz not null default now(),
    constraint uk_project_cross_space_relation_receipt
        unique (workspace_id, actor_id, operation, request_id),
    constraint fk_project_cross_space_relation_receipt_actor
        foreign key (workspace_id, actor_id) references users(workspace_id, id)
);

create function guard_project_cross_space_relation_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'cross-space relation receipts are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_cross_space_relation_receipt
before update or delete on project_cross_space_relation_receipts
for each row execute function guard_project_cross_space_relation_receipt();

create function guard_project_work_item_cross_space_relation_history()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'cross-space relation history is immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_work_item_cross_space_relation_history
before update or delete on project_work_item_cross_space_relation_history
for each row execute function guard_project_work_item_cross_space_relation_history();
