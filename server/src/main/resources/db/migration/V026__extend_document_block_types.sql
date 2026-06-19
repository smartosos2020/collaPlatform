alter table document_blocks
    drop constraint chk_document_blocks_type;

alter table document_blocks
    add constraint chk_document_blocks_type
        check (block_type in (
            'paragraph',
            'heading',
            'list',
            'task',
            'quote',
            'code',
            'table',
            'embed',
            'base_view',
            'issue_embed',
            'message_embed',
            'file_embed',
            'link'
        ));
