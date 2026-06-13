alter table messages
    add column edited_at timestamptz,
    add column revoked_at timestamptz,
    add column pinned_at timestamptz,
    add column pinned_by uuid;

create index idx_messages_workspace_content
    on messages using gin (to_tsvector('simple', coalesce(content, '')))
    where deleted_at is null and revoked_at is null;

create table message_reactions (
    id uuid primary key,
    workspace_id uuid not null,
    conversation_id uuid not null references conversations(id),
    message_id uuid not null references messages(id),
    user_id uuid not null,
    emoji varchar(32) not null,
    created_at timestamptz not null default now(),
    unique(message_id, user_id, emoji)
);

create index idx_message_reactions_message on message_reactions(message_id, created_at);

create index idx_issues_workspace_search
    on issues using gin (to_tsvector('simple', coalesce(issue_key, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, '')))
    where deleted_at is null;

create index idx_documents_workspace_search
    on documents using gin (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, '')))
    where deleted_at is null;

create index idx_base_record_values_workspace_search
    on base_record_values using gin (to_tsvector('simple', coalesce(value_text, '')));
