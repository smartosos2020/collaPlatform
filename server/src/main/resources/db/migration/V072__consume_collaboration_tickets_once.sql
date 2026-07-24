alter table knowledge_content_collaboration_tickets
    add column if not exists consumed_at timestamptz;

create index if not exists idx_knowledge_collaboration_tickets_session
    on knowledge_content_collaboration_tickets(token_hash, expires_at)
    where revoked_at is null and consumed_at is not null;
