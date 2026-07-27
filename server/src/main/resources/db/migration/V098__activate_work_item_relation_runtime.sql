alter table project_work_item_relations
    add constraint ck_project_work_item_relation_canonical_endpoints
    check (
        direction = 'directed'
        or source_work_item_id::text < target_work_item_id::text
    );

create index idx_project_work_item_relations_cycle_walk
    on project_work_item_relations(
        workspace_id, space_id, relation_key, source_work_item_id, target_work_item_id
    )
    where status = 'active';

create index idx_project_work_item_relation_history_endpoints
    on project_work_item_relation_history(
        workspace_id, space_id, source_work_item_id, target_work_item_id, occurred_at desc
    );
