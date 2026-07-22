alter table project_legacy_space_maps
    drop constraint if exists project_legacy_space_maps_batch_id_fkey;

alter table project_space_migration_batches
    add constraint uq_project_space_migration_batches_id_workspace unique (id, workspace_id);

alter table project_legacy_space_maps
    add constraint fk_project_legacy_space_maps_batch_workspace
    foreign key (batch_id, workspace_id) references project_space_migration_batches (id, workspace_id);
