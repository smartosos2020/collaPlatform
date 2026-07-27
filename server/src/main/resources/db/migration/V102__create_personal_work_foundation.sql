create table project_personal_work_projections (
    workspace_id uuid not null,
    user_id uuid not null,
    space_id uuid not null,
    work_item_id uuid not null,
    bucket_key varchar(24) not null check (
        bucket_key in ('todo', 'responsible', 'participating', 'watching')
    ),
    source_key varchar(64) not null check (source_key in ('node_task', 'participant')),
    source_version bigint not null check (source_version >= 0),
    source_updated_at timestamptz not null,
    invalidated_at timestamptz,
    refreshed_at timestamptz not null,
    primary key (workspace_id, user_id, work_item_id, bucket_key, source_key),
    constraint fk_project_personal_work_projection_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
        on delete cascade,
    constraint fk_project_personal_work_projection_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete cascade
);

create index idx_project_personal_work_projection_page
    on project_personal_work_projections (
        workspace_id, user_id, bucket_key, invalidated_at, source_updated_at desc, work_item_id desc
    );

create table project_personal_work_invalidation_watermarks (
    workspace_id uuid not null,
    user_id uuid not null,
    source_key varchar(160) not null,
    source_version bigint not null check (source_version >= 0),
    invalidated_at timestamptz not null,
    primary key (workspace_id, user_id, source_key),
    constraint fk_project_personal_work_watermark_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
        on delete cascade
);

create index idx_project_personal_work_watermark_scan
    on project_personal_work_invalidation_watermarks (
        workspace_id, invalidated_at, user_id, source_key
    );

create table project_personal_work_commands (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    operation varchar(48) not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    expected_version bigint check (expected_version is null or expected_version >= 0),
    status varchar(16) not null check (status in ('pending', 'completed')),
    response jsonb,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_personal_work_command_request
        unique (workspace_id, user_id, operation, request_id),
    constraint fk_project_personal_work_command_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
        on delete cascade,
    constraint ck_project_personal_work_command_completion
        check (
            (status = 'pending' and response is null and completed_at is null)
            or
            (status = 'completed' and response is not null and completed_at is not null)
        )
);

create index idx_project_personal_work_command_lookup
    on project_personal_work_commands (
        workspace_id, user_id, operation, created_at desc
    );

create function guard_project_personal_work_command()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'personal work command receipts cannot be deleted' using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed personal work command receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.user_id <> old.user_id
        or new.operation <> old.operation
        or new.request_id <> old.request_id
        or new.request_hash <> old.request_hash
        or new.expected_version is distinct from old.expected_version
        or new.created_at <> old.created_at then
        raise exception 'personal work command receipt identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_personal_work_command
before update or delete on project_personal_work_commands
for each row execute function guard_project_personal_work_command();
