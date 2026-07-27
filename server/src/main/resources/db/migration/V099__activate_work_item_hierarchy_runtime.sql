create table project_work_item_hierarchy_rebuild_batches (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    relation_key varchar(64) not null check (
        relation_key ~ '^[a-z][a-z0-9_]{0,63}$'
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    dry_run boolean not null,
    status varchar(16) not null check (
        status in ('pending', 'completed', 'failed')
    ),
    attempt integer not null default 0 check (attempt >= 0),
    edge_count integer not null default 0 check (edge_count >= 0),
    expected_path_count integer not null default 0 check (expected_path_count >= 0),
    issue_count integer not null default 0 check (issue_count >= 0),
    failures jsonb not null default '[]'::jsonb check (
        jsonb_typeof(failures) = 'array'
    ),
    requested_by uuid not null,
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_work_item_hierarchy_rebuild_request
        unique (workspace_id, space_id, request_id),
    constraint fk_project_work_item_hierarchy_rebuild_space
        foreign key (workspace_id, space_id)
        references project_spaces(workspace_id, id),
    constraint fk_project_work_item_hierarchy_rebuild_actor
        foreign key (workspace_id, requested_by)
        references users(workspace_id, id),
    constraint ck_project_work_item_hierarchy_rebuild_completion
        check (
            (status = 'pending' and completed_at is null)
            or
            (status in ('completed', 'failed') and completed_at is not null)
        )
);

create index idx_project_work_item_hierarchy_rebuild_status
    on project_work_item_hierarchy_rebuild_batches(
        workspace_id, space_id, relation_key, status, created_at, id
    );

create index idx_project_work_item_hierarchy_ancestors
    on project_work_item_hierarchy_paths(
        workspace_id, space_id, relation_key,
        ancestor_work_item_id, depth, descendant_work_item_id
    );
