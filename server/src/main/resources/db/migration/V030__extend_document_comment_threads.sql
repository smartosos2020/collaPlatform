alter table document_comments
    add column thread_id uuid,
    add column parent_comment_id uuid references document_comments(id),
    add column anchor_type varchar(16),
    add column anchor_start integer,
    add column anchor_end integer,
    add column anchor_text text,
    add column anchor_prefix text,
    add column anchor_suffix text,
    add column anchor_version_no integer,
    add column reopened_at timestamptz,
    add column reopened_by uuid references users(id);

update document_comments c
set thread_id = c.id,
    anchor_type = case when c.block_id is null then 'document' else 'block' end,
    anchor_version_no = d.current_version_no
from documents d
where d.id = c.document_id
  and c.thread_id is null;

alter table document_comments
    alter column thread_id set not null,
    alter column anchor_type set not null,
    add constraint fk_document_comments_thread foreign key (thread_id) references document_comments(id),
    add constraint chk_document_comment_anchor_type check (anchor_type in ('document', 'block', 'selection')),
    add constraint chk_document_comment_anchor_range check (
        (anchor_start is null and anchor_end is null)
        or (anchor_start is not null and anchor_end is not null and anchor_start >= 0 and anchor_end >= anchor_start)
    );

create index idx_document_comments_thread
    on document_comments(workspace_id, document_id, thread_id, created_at)
    where deleted_at is null;

create index idx_document_comments_anchor
    on document_comments(workspace_id, document_id, anchor_type, anchor_start, anchor_end)
    where deleted_at is null and parent_comment_id is null;
