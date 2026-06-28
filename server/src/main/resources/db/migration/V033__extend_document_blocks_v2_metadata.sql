alter table document_blocks
    add column schema_version integer not null default 1,
    add column attrs jsonb not null default '{}'::jsonb,
    add column rich_content jsonb not null default '{}'::jsonb,
    add column plain_text text not null default '';

update document_blocks
set plain_text = content,
    rich_content = jsonb_build_object('type', 'text', 'text', content)
where plain_text = '';

alter table document_blocks
    drop constraint chk_document_blocks_type;

alter table document_blocks
    add constraint chk_document_blocks_type
        check (block_type in (
            'paragraph',
            'heading',
            'list',
            'bullet_list',
            'ordered_list',
            'task',
            'task_item',
            'quote',
            'code',
            'code_block',
            'table',
            'image',
            'file',
            'embed',
            'base_view',
            'issue_embed',
            'message_embed',
            'file_embed',
            'link',
            'link_card',
            'divider',
            'callout',
            'toc'
        ));

create index idx_document_blocks_workspace_plain_text
    on document_blocks using gin (to_tsvector('simple', coalesce(plain_text, '')))
    where deleted_at is null;
