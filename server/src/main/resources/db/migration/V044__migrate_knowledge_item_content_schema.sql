alter table documents rename to knowledge_base_items;
alter table document_blocks rename to knowledge_content_blocks;
alter table document_versions rename to knowledge_content_versions;
alter table document_comments rename to knowledge_content_comments;
alter table document_collaboration_states rename to knowledge_content_collaboration_states;
alter table document_templates rename to knowledge_content_templates;
alter table document_permissions rename to knowledge_item_permissions;
alter table document_relations rename to knowledge_item_relations;
alter table document_share_links rename to knowledge_item_share_links;
alter table search_index_documents rename to search_index_entries;

alter table knowledge_base_spaces rename column root_document_id to root_item_id;
alter table knowledge_base_spaces rename column home_document_id to home_item_id;
alter table knowledge_base_items rename column doc_type to content_type;
alter table knowledge_base_items rename column node_kind to item_kind;
alter table knowledge_content_blocks rename column document_id to item_id;
alter table knowledge_content_versions rename column document_id to item_id;
alter table knowledge_content_comments rename column document_id to item_id;
alter table knowledge_content_collaboration_states rename column document_id to item_id;
alter table knowledge_item_permissions rename column document_id to item_id;
alter table knowledge_item_permissions rename column source_document_id to source_item_id;
alter table knowledge_item_relations rename column document_id to item_id;
alter table knowledge_item_share_links rename column document_id to item_id;
alter table search_index_entries rename column parent_document_id to parent_item_id;
alter table search_index_entries rename column doc_type to content_type;

update knowledge_base_items
set item_kind = case content_type
    when 'folder' then 'directory'
    when 'object_ref' then 'object_ref'
    when 'external_link' then 'external_link'
    else 'content'
end
where item_kind is null
   or item_kind not in ('content', 'directory', 'object_ref', 'external_link');

alter table knowledge_base_items drop constraint if exists ck_documents_node_kind;
alter table knowledge_base_items add constraint ck_knowledge_base_items_item_kind
    check (item_kind in ('content', 'directory', 'object_ref', 'external_link'));
alter table knowledge_base_items add constraint ck_knowledge_base_items_content_type
    check (content_type in ('space', 'folder', 'markdown', 'object_ref', 'external_link'));

insert into object_type_rules (id, object_type, web_path_pattern, deep_link_pattern, created_at)
values (gen_random_uuid(), 'knowledge_content', '/knowledge-bases/{spaceId}/items/{id}', 'colla://knowledge-content/{id}', now())
on conflict (object_type) do update
set web_path_pattern = excluded.web_path_pattern,
    deep_link_pattern = excluded.deep_link_pattern;

do $$
declare
    row record;
    next_name text;
begin
    for row in
        select c.conrelid::regclass::text table_name, c.conname
        from pg_constraint c
        where c.connamespace = 'public'::regnamespace
          and c.conname like '%document%'
    loop
        next_name := replace(row.conname, 'documents', 'knowledge_base_items');
        next_name := replace(next_name, 'document', 'knowledge_item');
        execute format('alter table %I rename constraint %I to %I', row.table_name, row.conname, next_name);
    end loop;

    for row in
        select schemaname, indexname
        from pg_indexes
        where schemaname = 'public'
          and indexname like '%document%'
    loop
        next_name := replace(row.indexname, 'search_index_documents', 'search_index_entries');
        next_name := replace(next_name, 'documents', 'knowledge_base_items');
        next_name := replace(next_name, 'document', 'knowledge_item');
        execute format('alter index %I.%I rename to %I', row.schemaname, row.indexname, next_name);
    end loop;
end $$;
