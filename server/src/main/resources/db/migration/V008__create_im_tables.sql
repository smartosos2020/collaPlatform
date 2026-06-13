create table conversations (
    id uuid primary key,
    workspace_id uuid not null,
    conversation_type varchar(32) not null,
    title varchar(255),
    owner_id uuid,
    project_id uuid,
    last_message_id uuid,
    last_message_at timestamptz,
    created_by uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    archived_at timestamptz
);

create index idx_conversations_workspace_last_message
    on conversations (workspace_id, last_message_at desc nulls last, created_at desc);

create index idx_conversations_project on conversations (project_id);

create table conversation_members (
    id uuid primary key,
    workspace_id uuid not null,
    conversation_id uuid not null references conversations(id),
    user_id uuid not null,
    member_role varchar(32) not null,
    last_read_message_id uuid,
    last_read_at timestamptz,
    joined_at timestamptz not null,
    muted boolean not null default false,
    archived_at timestamptz,
    constraint uk_conversation_members unique (conversation_id, user_id)
);

create index idx_conversation_members_user
    on conversation_members (workspace_id, user_id, archived_at);

create table messages (
    id uuid primary key,
    workspace_id uuid not null,
    conversation_id uuid not null references conversations(id),
    sender_id uuid not null,
    client_message_id varchar(128) not null,
    message_type varchar(32) not null,
    content text not null,
    reply_to_message_id uuid,
    created_at timestamptz not null,
    deleted_at timestamptz,
    constraint uk_messages_client_id unique (conversation_id, sender_id, client_message_id)
);

create index idx_messages_conversation_created
    on messages (conversation_id, created_at desc, id desc);

create table message_mentions (
    id uuid primary key,
    workspace_id uuid not null,
    conversation_id uuid not null,
    message_id uuid not null references messages(id),
    mentioned_user_id uuid not null,
    created_at timestamptz not null,
    constraint uk_message_mentions unique (message_id, mentioned_user_id)
);

create index idx_message_mentions_user
    on message_mentions (workspace_id, mentioned_user_id, created_at desc);

create table message_links (
    id uuid primary key,
    workspace_id uuid not null,
    conversation_id uuid not null,
    message_id uuid not null references messages(id),
    source_url text not null,
    target_type varchar(64),
    target_id uuid,
    web_path varchar(512),
    deep_link varchar(512),
    card_snapshot jsonb,
    created_at timestamptz not null
);

create index idx_message_links_message on message_links (message_id);
create index idx_message_links_target on message_links (target_type, target_id);
