create table project_work_item_type_commands (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    request_id varchar(120) not null,
    operation varchar(64) not null,
    request_hash varchar(64) not null,
    status varchar(32) not null,
    response_type_id uuid,
    created_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_type_commands_request unique (workspace_id, request_id),
    constraint fk_project_work_item_type_commands_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_type_commands_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_type_commands_response
        foreign key (workspace_id, space_id, response_type_id)
        references project_work_item_types(workspace_id, space_id, id),
    constraint ck_project_work_item_type_commands_request_hash
        check (request_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_project_work_item_type_commands_status
        check (status in ('pending', 'completed')),
    constraint ck_project_work_item_type_commands_completion
        check (
            (status = 'pending' and completed_at is null)
            or (status = 'completed' and completed_at is not null)
        )
);

create index idx_project_work_item_type_commands_space_created
    on project_work_item_type_commands (workspace_id, space_id, created_at desc);
