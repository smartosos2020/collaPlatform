create index if not exists idx_knowledge_content_blocks_item_id_fk
    on knowledge_content_blocks (item_id);

create index if not exists idx_knowledge_content_blocks_created_by_fk
    on knowledge_content_blocks (created_by);

create index if not exists idx_knowledge_content_blocks_updated_by_fk
    on knowledge_content_blocks (updated_by);

create index if not exists idx_knowledge_content_blocks_workspace_id_fk
    on knowledge_content_blocks (workspace_id);

create index if not exists idx_knowledge_base_items_created_by_fk
    on knowledge_base_items (created_by);

create index if not exists idx_knowledge_base_items_maintainer_id_fk
    on knowledge_base_items (maintainer_id);

create index if not exists idx_knowledge_base_items_updated_by_fk
    on knowledge_base_items (updated_by);
