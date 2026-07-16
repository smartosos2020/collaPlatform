alter table knowledge_content_comments
    add column if not exists anchor_state varchar(32) not null default 'active',
    add column if not exists anchor_invalid_reason varchar(64),
    add column if not exists anchor_updated_at timestamptz;

alter table knowledge_content_comments
    drop constraint if exists ck_knowledge_content_comments_anchor_state;

alter table knowledge_content_comments
    add constraint ck_knowledge_content_comments_anchor_state
        check (anchor_state in ('active', 'detached'));

create index if not exists idx_knowledge_content_comments_anchor_state
    on knowledge_content_comments (workspace_id, item_id, anchor_state)
    where deleted_at is null;
