create table project_register_entries (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    schema_version smallint not null default 1 check (schema_version = 1),
    entry_type varchar(16) not null check (entry_type in ('risk', 'issue', 'decision', 'change')),
    title varchar(160) not null check (length(trim(title)) between 1 and 160),
    summary varchar(2000) not null default '',
    status varchar(24) not null,
    owner_user_id uuid,
    due_date date,
    probability smallint check (probability between 1 and 5),
    impact smallint check (impact between 1 and 5),
    decision_basis varchar(2000) not null default '',
    change_impact varchar(2000) not null default '',
    supersedes_entry_id uuid,
    verification varchar(1000) not null default '',
    aggregate_version bigint not null default 1 check (aggregate_version >= 1),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_register_scope unique (workspace_id, space_id, id),
    constraint fk_project_register_space foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_register_owner foreign key (workspace_id, owner_user_id)
        references users(workspace_id, id),
    constraint fk_project_register_created_by foreign key (workspace_id, created_by)
        references users(workspace_id, id),
    constraint fk_project_register_updated_by foreign key (workspace_id, updated_by)
        references users(workspace_id, id),
    constraint fk_project_register_supersedes
        foreign key (workspace_id, space_id, supersedes_entry_id)
        references project_register_entries(workspace_id, space_id, id),
    constraint ck_project_register_type_detail check (
        (entry_type = 'risk' and probability is not null and impact is not null)
        or (entry_type <> 'risk' and probability is null and impact is null)
    )
);

create index idx_project_register_list
    on project_register_entries(workspace_id, space_id, entry_type, status, updated_at desc, id);
create index idx_project_register_owner_due
    on project_register_entries(workspace_id, space_id, owner_user_id, due_date, id);

create table project_register_references (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    entry_id uuid not null,
    source_type varchar(16) not null check (source_type in ('work_item', 'plan')),
    source_id uuid not null,
    source_version bigint not null check (source_version >= 1),
    created_at timestamptz not null,
    constraint uk_project_register_reference_scope
        unique (workspace_id, space_id, entry_id, id),
    constraint uk_project_register_reference_source
        unique (workspace_id, space_id, entry_id, source_type, source_id),
    constraint fk_project_register_reference_entry
        foreign key (workspace_id, space_id, entry_id)
        references project_register_entries(workspace_id, space_id, id)
);

create index idx_project_register_reference_lookup
    on project_register_references(workspace_id, space_id, source_type, source_id, entry_id);

create table project_register_responses (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    entry_id uuid not null,
    response_type varchar(24) not null,
    description varchar(1000) not null,
    owner_user_id uuid,
    due_date date,
    status varchar(16) not null check (status in ('planned', 'active', 'completed', 'cancelled')),
    position smallint not null check (position between 0 and 19),
    constraint uk_project_register_response_scope
        unique (workspace_id, space_id, entry_id, id),
    constraint uk_project_register_response_position
        unique (workspace_id, space_id, entry_id, position),
    constraint fk_project_register_response_entry
        foreign key (workspace_id, space_id, entry_id)
        references project_register_entries(workspace_id, space_id, id),
    constraint fk_project_register_response_owner
        foreign key (workspace_id, owner_user_id)
        references users(workspace_id, id)
);

create table project_register_history (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    entry_id uuid not null,
    history_sequence bigint not null check (history_sequence >= 1),
    operation varchar(32) not null,
    from_status varchar(24) not null,
    to_status varchar(24) not null,
    reason varchar(500) not null default '',
    actor_id uuid not null,
    entry_version bigint not null check (entry_version >= 1),
    occurred_at timestamptz not null,
    constraint uk_project_register_history_sequence
        unique (workspace_id, space_id, entry_id, history_sequence),
    constraint fk_project_register_history_entry
        foreign key (workspace_id, space_id, entry_id)
        references project_register_entries(workspace_id, space_id, id),
    constraint fk_project_register_history_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create index idx_project_register_history_page
    on project_register_history(workspace_id, space_id, entry_id, history_sequence desc);

create table project_register_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    entry_id uuid not null,
    operation varchar(32) not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_json jsonb not null check (jsonb_typeof(response_json) = 'object'),
    created_at timestamptz not null,
    constraint uk_project_register_command
        unique (workspace_id, space_id, actor_id, operation, request_id),
    constraint fk_project_register_command_entry
        foreign key (workspace_id, space_id, entry_id)
        references project_register_entries(workspace_id, space_id, id),
    constraint fk_project_register_command_actor foreign key (workspace_id, actor_id)
        references users(workspace_id, id)
);

create function guard_project_register_history()
returns trigger language plpgsql as $$
begin
    if current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'project register history is immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_register_history
before update or delete on project_register_history
for each row execute function guard_project_register_history();
