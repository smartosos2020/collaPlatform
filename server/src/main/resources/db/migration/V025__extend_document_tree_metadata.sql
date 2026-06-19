alter table documents
    add column sort_order integer not null default 0,
    add column archived_at timestamptz;

create index idx_documents_tree_order
    on documents(workspace_id, parent_id, sort_order, lower(title))
    where deleted_at is null and archived_at is null;

create index idx_documents_archived
    on documents(workspace_id, archived_at)
    where deleted_at is null;
