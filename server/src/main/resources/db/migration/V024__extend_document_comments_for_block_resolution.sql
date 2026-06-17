alter table document_comments
    add column block_id uuid references document_blocks(id),
    add column resolved_at timestamptz,
    add column resolved_by uuid references users(id);

create index idx_document_comments_block
    on document_comments(workspace_id, document_id, block_id)
    where deleted_at is null and block_id is not null;

create index idx_document_comments_unresolved
    on document_comments(workspace_id, document_id, created_at)
    where deleted_at is null and resolved_at is null;
