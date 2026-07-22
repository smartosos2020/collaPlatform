alter table project_legacy_space_maps
    add column batch_id uuid references project_space_migration_batches(id);

create index idx_project_legacy_space_maps_batch
    on project_legacy_space_maps (workspace_id, batch_id);
