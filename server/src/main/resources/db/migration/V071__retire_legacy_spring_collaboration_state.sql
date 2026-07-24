alter table knowledge_base_items
    add column if not exists collaboration_generation bigint not null default 0;

alter table knowledge_content_collaboration_states
    add column if not exists generation bigint not null default 0;

alter table knowledge_content_collaboration_updates
    add column if not exists generation bigint not null default 0;

do $$
declare
    legacy_constraint record;
begin
    for legacy_constraint in
        select constraint_row.conname
        from pg_constraint constraint_row
        join pg_class table_row on table_row.oid = constraint_row.conrelid
        where table_row.relname = 'knowledge_content_collaboration_updates'
          and constraint_row.contype = 'u'
          and pg_get_constraintdef(constraint_row.oid) =
              'UNIQUE (workspace_id, item_id, update_id)'
    loop
        execute format(
            'alter table knowledge_content_collaboration_updates drop constraint %I',
            legacy_constraint.conname
        );
    end loop;
end
$$;

alter table knowledge_content_collaboration_updates
    add constraint uq_knowledge_collaboration_update_generation
        unique (workspace_id, item_id, generation, update_id);

drop index if exists idx_document_collaboration_states_document;
drop index if exists idx_document_collaboration_states_unsaved;

alter table knowledge_content_collaboration_states
    drop column if exists state_vector,
    drop column if exists snapshot_payload,
    drop column if exists server_clock,
    drop column if exists last_client_id,
    drop column if exists updated_by,
    drop column if exists last_saved_at;

create index if not exists idx_knowledge_collaboration_states_generation
    on knowledge_content_collaboration_states(workspace_id, item_id, generation);

create index if not exists idx_knowledge_collaboration_updates_generation
    on knowledge_content_collaboration_updates(workspace_id, item_id, generation, sequence_no);
