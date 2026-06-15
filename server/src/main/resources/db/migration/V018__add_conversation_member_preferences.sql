alter table conversation_members
    add column pinned_at timestamptz;

create index idx_conversation_members_user_pinned
    on conversation_members (workspace_id, user_id, pinned_at desc nulls last)
    where archived_at is null;
