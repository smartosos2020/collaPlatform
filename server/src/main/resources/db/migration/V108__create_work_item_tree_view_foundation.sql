create table project_work_item_tree_preferences (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    view_key varchar(64) not null check (view_key ~ '^[a-z][a-z0-9_-]{0,63}$'),
    relation_key varchar(64) not null check (relation_key ~ '^[a-z][a-z0-9_.-]{0,63}$'),
    expanded_node_ids uuid[] not null default '{}',
    aggregate_version bigint not null default 0 check (aggregate_version >= 0),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, view_key),
    constraint fk_project_work_item_tree_preference_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_tree_preference_user
        foreign key (workspace_id, user_id) references users(workspace_id, id),
    constraint ck_project_work_item_tree_expansion_limit
        check (cardinality(expanded_node_ids) <= 64)
);

create index idx_project_work_item_tree_preferences_user
    on project_work_item_tree_preferences(
        workspace_id, user_id, updated_at desc, space_id, view_key
    );

create table project_work_item_tree_projection_stats (
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    user_id uuid not null,
    relation_key varchar(64) not null,
    query_hash varchar(64) not null check (query_hash ~ '^[0-9a-f]{64}$'),
    source_watermark bigint not null default 0 check (source_watermark >= 0),
    visible_node_count integer not null check (visible_node_count between 0 and 200),
    visible_root_count integer not null check (visible_root_count between 0 and 200),
    max_visible_depth integer not null check (max_visible_depth between 0 and 32),
    rebuilt_at timestamptz not null,
    primary key (workspace_id, space_id, user_id, relation_key, query_hash),
    constraint fk_project_work_item_tree_stats_space
        foreign key (workspace_id, space_id) references project_spaces(workspace_id, id),
    constraint fk_project_work_item_tree_stats_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
);

create index idx_project_work_item_tree_projection_stats_rebuild
    on project_work_item_tree_projection_stats(
        workspace_id, space_id, user_id, rebuilt_at, query_hash
    );
