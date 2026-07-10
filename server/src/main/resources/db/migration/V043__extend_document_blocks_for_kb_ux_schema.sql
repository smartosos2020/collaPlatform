alter table document_blocks
    add column if not exists parent_id uuid references document_blocks(id),
    add column if not exists anchor_id varchar(128),
    add column if not exists block_version integer not null default 1;

alter table document_blocks
    drop constraint if exists chk_document_blocks_type;

alter table document_blocks
    add constraint chk_document_blocks_type
        check (block_type in (
            'paragraph',
            'heading',
            'list',
            'bullet_list',
            'bulleted_list',
            'ordered_list',
            'task',
            'todo',
            'task_item',
            'quote',
            'code',
            'code_block',
            'table',
            'image',
            'file',
            'embed',
            'embed_object',
            'base_view',
            'issue_embed',
            'message_embed',
            'file_embed',
            'link',
            'link_card',
            'legacy_html',
            'divider',
            'callout',
            'toc'
        ));

create index if not exists idx_document_blocks_parent_order
    on document_blocks (workspace_id, document_id, parent_id, sort_order)
    where deleted_at is null;

create index if not exists idx_document_blocks_anchor
    on document_blocks (workspace_id, document_id, anchor_id)
    where deleted_at is null and anchor_id is not null;
