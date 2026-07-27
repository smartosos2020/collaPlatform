create table project_deliverables (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    schema_version smallint not null default 1 check (schema_version = 1),
    title varchar(160) not null check (length(trim(title)) between 1 and 160),
    summary varchar(2000) not null default '',
    status varchar(24) not null check (
        status in ('draft', 'submitted', 'withdrawn', 'reviewing', 'reviewed',
                   'accepted', 'rejected', 'archived')
    ),
    owner_user_id uuid,
    due_date date,
    plan_id uuid,
    milestone_id uuid,
    register_entry_ids uuid[] not null default '{}',
    current_version_id uuid,
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_deliverable_scope unique (workspace_id, space_id, id),
    constraint fk_project_deliverable_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_deliverable_owner foreign key (workspace_id, owner_user_id)
        references users(workspace_id, id),
    constraint fk_project_deliverable_plan
        foreign key (workspace_id, space_id, plan_id)
        references project_plans(workspace_id, space_id, id),
    constraint fk_project_deliverable_milestone
        foreign key (workspace_id, space_id, plan_id, milestone_id)
        references project_plan_milestones(workspace_id, space_id, plan_id, id),
    constraint fk_project_deliverable_created_by foreign key (workspace_id, created_by)
        references users(workspace_id, id),
    constraint fk_project_deliverable_updated_by foreign key (workspace_id, updated_by)
        references users(workspace_id, id)
);

create index idx_project_deliverables_list
    on project_deliverables(workspace_id, space_id, status, updated_at desc, id);

create table project_deliverable_versions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    deliverable_id uuid not null,
    version_sequence smallint not null check (version_sequence between 1 and 50),
    version_label varchar(80) not null,
    version_note varchar(1000) not null default '',
    submitted_by uuid not null,
    submitted_at timestamptz not null,
    constraint uk_project_deliverable_version_scope
        unique (workspace_id, space_id, deliverable_id, id),
    constraint uk_project_deliverable_version_sequence
        unique (workspace_id, space_id, deliverable_id, version_sequence),
    constraint fk_project_deliverable_version_parent
        foreign key (workspace_id, space_id, deliverable_id)
        references project_deliverables(workspace_id, space_id, id),
    constraint fk_project_deliverable_version_actor
        foreign key (workspace_id, submitted_by)
        references users(workspace_id, id)
);

alter table project_deliverables
    add constraint fk_project_deliverable_current_version
    foreign key (workspace_id, space_id, id, current_version_id)
    references project_deliverable_versions(workspace_id, space_id, deliverable_id, id);

create table project_deliverable_materials (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    deliverable_id uuid not null,
    version_id uuid not null,
    source_type varchar(32) not null check (
        source_type in ('file', 'knowledge_content', 'work_item', 'plan', 'milestone', 'external')
    ),
    source_id uuid,
    source_version bigint check (source_version is null or source_version >= 1),
    external_uri varchar(1000),
    position smallint not null check (position between 0 and 49),
    constraint uk_project_deliverable_material_scope
        unique (workspace_id, space_id, deliverable_id, version_id, id),
    constraint uk_project_deliverable_material_position
        unique (workspace_id, space_id, deliverable_id, version_id, position),
    constraint fk_project_deliverable_material_version
        foreign key (workspace_id, space_id, deliverable_id, version_id)
        references project_deliverable_versions(workspace_id, space_id, deliverable_id, id),
    constraint ck_project_deliverable_material_source check (
        (source_type = 'external' and source_id is null and source_version is null
         and external_uri is not null)
        or
        (source_type <> 'external' and source_id is not null and source_version is not null
         and external_uri is null)
    )
);

create index idx_project_deliverable_material_source
    on project_deliverable_materials(workspace_id, source_type, source_id, deliverable_id);

create table project_deliverable_reviews (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    deliverable_id uuid not null,
    deliverable_version_id uuid not null,
    review_round smallint not null check (review_round between 1 and 50),
    review_items jsonb not null check (jsonb_typeof(review_items) = 'array'),
    required_signer_ids uuid[] not null,
    quorum smallint not null check (quorum between 1 and 30),
    status varchar(16) not null check (status in ('open', 'approved', 'rejected')),
    conclusion varchar(1000) not null default '',
    opened_by uuid not null,
    opened_at timestamptz not null,
    closed_at timestamptz,
    constraint uk_project_deliverable_review_scope
        unique (workspace_id, space_id, deliverable_id, id),
    constraint uk_project_deliverable_review_round
        unique (workspace_id, space_id, deliverable_id, review_round),
    constraint fk_project_deliverable_review_parent
        foreign key (workspace_id, space_id, deliverable_id)
        references project_deliverables(workspace_id, space_id, id),
    constraint fk_project_deliverable_review_version
        foreign key (workspace_id, space_id, deliverable_id, deliverable_version_id)
        references project_deliverable_versions(workspace_id, space_id, deliverable_id, id),
    constraint fk_project_deliverable_review_actor foreign key (workspace_id, opened_by)
        references users(workspace_id, id)
);

create index idx_project_deliverable_reviews_page
    on project_deliverable_reviews(workspace_id, space_id, deliverable_id, review_round desc);

create table project_deliverable_signoffs (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    deliverable_id uuid not null,
    review_id uuid not null,
    signoff_sequence bigint not null check (signoff_sequence >= 1),
    signer_id uuid not null,
    conclusion varchar(16) not null check (conclusion in ('approve', 'reject', 'revoke')),
    comment varchar(1000) not null default '',
    occurred_at timestamptz not null,
    constraint uk_project_deliverable_signoff_sequence
        unique (workspace_id, space_id, deliverable_id, review_id, signoff_sequence),
    constraint fk_project_deliverable_signoff_review
        foreign key (workspace_id, space_id, deliverable_id, review_id)
        references project_deliverable_reviews(workspace_id, space_id, deliverable_id, id),
    constraint fk_project_deliverable_signoff_actor foreign key (workspace_id, signer_id)
        references users(workspace_id, id)
);

create index idx_project_deliverable_signoffs_page
    on project_deliverable_signoffs(
        workspace_id, space_id, deliverable_id, review_id, signer_id, signoff_sequence desc
    );

create table project_deliverable_acceptances (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    deliverable_id uuid not null,
    acceptance_sequence bigint not null check (acceptance_sequence >= 1),
    review_id uuid not null,
    conclusion varchar(16) not null check (conclusion in ('accepted', 'rejected')),
    comment varchar(1000) not null,
    actor_id uuid not null,
    occurred_at timestamptz not null,
    constraint uk_project_deliverable_acceptance_sequence
        unique (workspace_id, space_id, deliverable_id, acceptance_sequence),
    constraint fk_project_deliverable_acceptance_review
        foreign key (workspace_id, space_id, deliverable_id, review_id)
        references project_deliverable_reviews(workspace_id, space_id, deliverable_id, id),
    constraint fk_project_deliverable_acceptance_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create table project_deliverable_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    deliverable_id uuid not null,
    operation varchar(32) not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_deliverable_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_deliverable_command_parent
        foreign key (workspace_id, space_id, deliverable_id)
        references project_deliverables(workspace_id, space_id, id),
    constraint fk_project_deliverable_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create function guard_project_deliverable_immutable_facts()
returns trigger language plpgsql as $$
begin
    if current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'project deliverable facts are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_deliverable_version_immutable
before update or delete on project_deliverable_versions
for each row execute function guard_project_deliverable_immutable_facts();
create trigger trg_project_deliverable_material_immutable
before update or delete on project_deliverable_materials
for each row execute function guard_project_deliverable_immutable_facts();
create trigger trg_project_deliverable_signoff_immutable
before update or delete on project_deliverable_signoffs
for each row execute function guard_project_deliverable_immutable_facts();
create trigger trg_project_deliverable_acceptance_immutable
before update or delete on project_deliverable_acceptances
for each row execute function guard_project_deliverable_immutable_facts();
