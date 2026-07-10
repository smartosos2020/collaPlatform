alter table documents
    add column if not exists node_kind varchar(32) not null default 'content',
    add column if not exists target_object_type varchar(64),
    add column if not exists target_object_id uuid,
    add column if not exists target_route varchar(1024),
    add column if not exists display_mode varchar(32) not null default 'default',
    add column if not exists target_title_strategy varchar(32) not null default 'manual',
    add column if not exists entry_alias varchar(255);

update documents
set node_kind = case
        when doc_type in ('space', 'folder') then 'directory'
        when doc_type = 'object_ref' then 'object_ref'
        when doc_type = 'external_link' then 'external_link'
        else 'content'
    end
where node_kind = 'content';

alter table documents
    add constraint ck_documents_node_kind
        check (node_kind in ('content', 'directory', 'object_ref', 'external_link'));

alter table documents
    add constraint ck_documents_display_mode
        check (display_mode in ('default', 'inline', 'preview', 'link'));

alter table documents
    add constraint ck_documents_target_title_strategy
        check (target_title_strategy in ('manual', 'follow_target', 'alias'));

create index if not exists idx_documents_knowledge_target
    on documents (workspace_id, node_kind, target_object_type, target_object_id)
    where deleted_at is null;

create index if not exists idx_documents_knowledge_target_route
    on documents (workspace_id, target_route)
    where deleted_at is null and target_route is not null;
