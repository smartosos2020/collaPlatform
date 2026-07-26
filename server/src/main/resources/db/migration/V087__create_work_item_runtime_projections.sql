create table project_work_item_field_projections (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    field_key varchar(64) not null,
    field_type varchar(32) not null,
    config_hash varchar(64) not null check (config_hash ~ '^[0-9a-f]{64}$'),
    canonical_hash varchar(64) not null check (canonical_hash ~ '^[0-9a-f]{64}$'),
    canonical_value jsonb not null,
    text_value text,
    number_value numeric,
    boolean_value boolean,
    date_value date,
    timestamp_value timestamptz,
    reference_values jsonb,
    filterable boolean not null,
    sortable boolean not null,
    index_capability varchar(32) not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, work_item_id, field_key),
    constraint fk_project_work_item_field_projections_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint ck_project_work_item_field_projection_value
        check (
            jsonb_typeof(canonical_value) <> 'null'
            and (reference_values is null or jsonb_typeof(reference_values) = 'array')
        )
);

create index idx_project_work_item_field_projection_text
    on project_work_item_field_projections(workspace_id, space_id, field_key, text_value, work_item_id)
    where filterable and text_value is not null;
create index idx_project_work_item_field_projection_number
    on project_work_item_field_projections(workspace_id, space_id, field_key, number_value, work_item_id)
    where filterable and number_value is not null;
create index idx_project_work_item_field_projection_date
    on project_work_item_field_projections(workspace_id, space_id, field_key, date_value, work_item_id)
    where filterable and date_value is not null;
create index idx_project_work_item_field_projection_timestamp
    on project_work_item_field_projections(workspace_id, space_id, field_key, timestamp_value, work_item_id)
    where filterable and timestamp_value is not null;

create table project_work_item_participants (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    user_id uuid not null,
    participant_role varchar(24) not null
        check (participant_role in ('owner', 'assignee', 'collaborator', 'watcher')),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_work_item_participants_identity
        unique (workspace_id, space_id, work_item_id, user_id),
    constraint fk_project_work_item_participants_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_work_item_participants_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint fk_project_work_item_participants_created_by
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_participants_updated_by
        foreign key (workspace_id, updated_by) references users(workspace_id, id)
);

create index idx_project_work_item_participants_user
    on project_work_item_participants(workspace_id, user_id, space_id, work_item_id);

create table project_work_item_activities (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    work_item_id uuid not null,
    sequence_number bigint not null check (sequence_number > 0),
    activity_type varchar(48) not null,
    actor_id uuid not null,
    public_payload jsonb not null default '{}'::jsonb
        check (jsonb_typeof(public_payload) = 'object'),
    occurred_at timestamptz not null,
    constraint uk_project_work_item_activities_sequence
        unique (workspace_id, space_id, work_item_id, sequence_number),
    constraint fk_project_work_item_activities_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade,
    constraint fk_project_work_item_activities_actor
        foreign key (workspace_id, actor_id) references users(workspace_id, id)
);

create index idx_project_work_item_activities_page
    on project_work_item_activities(
        workspace_id, space_id, work_item_id, sequence_number desc
    );

create function guard_project_work_item_activity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'work item activities are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_work_item_activity
before update or delete on project_work_item_activities
for each row execute function guard_project_work_item_activity();
