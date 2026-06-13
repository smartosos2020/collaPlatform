create sequence if not exists messages_message_seq_seq;

alter table messages
    add column if not exists message_seq bigint;

with ordered_messages as (
    select id, row_number() over (order by created_at, id) as row_number
    from messages
    where message_seq is null
)
update messages
set message_seq = ordered_messages.row_number
from ordered_messages
where messages.id = ordered_messages.id;

select setval(
    'messages_message_seq_seq',
    greatest(coalesce((select max(message_seq) from messages), 0), 1),
    true
);

alter table messages
    alter column message_seq set default nextval('messages_message_seq_seq'),
    alter column message_seq set not null;

alter sequence messages_message_seq_seq
    owned by messages.message_seq;

create index idx_messages_conversation_seq on messages (conversation_id, message_seq desc);
