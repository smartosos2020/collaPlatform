create temporary table kb_item_space_map (
    workspace_id uuid not null,
    item_id uuid not null,
    space_id uuid not null,
    primary key (workspace_id, item_id)
);

insert into kb_item_space_map (workspace_id, item_id, space_id)
with recursive item_tree(workspace_id, item_id, space_id) as (
    select s.workspace_id, s.root_item_id, s.id
    from knowledge_base_spaces s
    where s.deleted_at is null
    union all
    select child.workspace_id, child.id, tree.space_id
    from knowledge_base_items child
    join item_tree tree
      on tree.workspace_id = child.workspace_id
     and tree.item_id = child.parent_id
    where child.deleted_at is null
)
select distinct on (workspace_id, item_id) workspace_id, item_id, space_id
from item_tree
order by workspace_id, item_id, space_id;

delete from object_links legacy
using object_links canonical
where legacy.workspace_id = canonical.workspace_id
  and legacy.object_id = canonical.object_id
  and legacy.object_type = 'document'
  and canonical.object_type = 'knowledge_content';

update object_links link
set object_type = 'knowledge_content',
    web_path = coalesce('/knowledge-bases/' || mapping.space_id || '/items/' || link.object_id, link.web_path),
    deep_link = 'colla://knowledge-content/' || link.object_id
        || case when mapping.space_id is null then '' else '?spaceId=' || mapping.space_id end,
    updated_at = now()
from kb_item_space_map mapping
where link.workspace_id = mapping.workspace_id
  and link.object_id = mapping.item_id
  and link.object_type = 'document';

update object_links
set object_type = 'knowledge_content',
    deep_link = replace(deep_link, 'colla://document/', 'colla://knowledge-content/'),
    updated_at = now()
where object_type = 'document';

delete from object_recent_accesses legacy
using object_recent_accesses canonical
where legacy.workspace_id = canonical.workspace_id
  and legacy.user_id = canonical.user_id
  and legacy.object_id = canonical.object_id
  and legacy.object_type = 'document'
  and canonical.object_type = 'knowledge_content';

update object_recent_accesses access
set object_type = 'knowledge_content',
    web_path = '/knowledge-bases/' || mapping.space_id || '/items/' || access.object_id,
    deep_link = 'colla://knowledge-content/' || access.object_id || '?spaceId=' || mapping.space_id
from kb_item_space_map mapping
where access.workspace_id = mapping.workspace_id
  and access.object_id = mapping.item_id
  and access.object_type = 'document';

update object_recent_accesses
set object_type = 'knowledge_content',
    deep_link = replace(deep_link, 'colla://document/', 'colla://knowledge-content/')
where object_type = 'document';

delete from object_favorites legacy
using object_favorites canonical
where legacy.workspace_id = canonical.workspace_id
  and legacy.user_id = canonical.user_id
  and legacy.object_id = canonical.object_id
  and legacy.object_type = 'document'
  and canonical.object_type = 'knowledge_content';

update object_favorites set object_type = 'knowledge_content' where object_type = 'document';

update notifications notification
set target_type = 'knowledge_content',
    web_path = coalesce('/knowledge-bases/' || mapping.space_id || '/items/' || notification.target_id, notification.web_path)
from kb_item_space_map mapping
where notification.workspace_id = mapping.workspace_id
  and notification.target_id = mapping.item_id
  and notification.target_type = 'document';
update notifications set target_type = 'knowledge_content' where target_type = 'document';

update file_usages set target_type = 'knowledge_content' where target_type = 'document';
update message_links set target_type = 'knowledge_content', deep_link = replace(deep_link, 'colla://document/', 'colla://knowledge-content/') where target_type = 'document';
update issue_relations set target_type = 'knowledge_content' where target_type = 'document';
update base_record_relations set target_type = 'knowledge_content' where target_type = 'document';
update knowledge_item_relations set target_type = 'knowledge_content' where target_type = 'document';
update knowledge_base_items set target_object_type = 'knowledge_content' where target_object_type = 'document';

delete from resource_permissions legacy
using resource_permissions canonical
where legacy.workspace_id = canonical.workspace_id
  and legacy.resource_id = canonical.resource_id
  and legacy.subject_type = canonical.subject_type
  and legacy.subject_id = canonical.subject_id
  and legacy.resource_type = 'document'
  and canonical.resource_type = 'knowledge_content';
update resource_permissions set resource_type = 'knowledge_content' where resource_type = 'document';
update resource_permission_requests set resource_type = 'knowledge_content' where resource_type = 'document';

alter table knowledge_subscriptions drop constraint if exists chk_knowledge_subscription_target_type;
delete from knowledge_subscriptions legacy
using knowledge_subscriptions canonical
where legacy.workspace_id = canonical.workspace_id
  and legacy.subscriber_id = canonical.subscriber_id
  and legacy.target_id = canonical.target_id
  and legacy.target_type = 'document'
  and canonical.target_type = 'knowledge_content';
update knowledge_subscriptions set target_type = 'knowledge_content' where target_type = 'document';
alter table knowledge_subscriptions
    add constraint chk_knowledge_subscription_target_type
    check (target_type in ('knowledge_base', 'knowledge_content'));

delete from search_index_entries legacy
using search_index_entries canonical
where legacy.workspace_id = canonical.workspace_id
  and legacy.object_id = canonical.object_id
  and legacy.object_type = 'document'
  and canonical.object_type = 'knowledge_content';

update search_index_entries entry
set object_type = 'knowledge_content',
    web_path = '/knowledge-bases/' || mapping.space_id || '/items/' || entry.object_id,
    deep_link = 'colla://knowledge-content/' || entry.object_id || '?spaceId=' || mapping.space_id,
    indexed_at = now()
from kb_item_space_map mapping
where entry.workspace_id = mapping.workspace_id
  and entry.object_id = mapping.item_id
  and entry.object_type = 'document';

update search_index_entries
set object_type = 'knowledge_content',
    deep_link = replace(deep_link, 'colla://document/', 'colla://knowledge-content/'),
    indexed_at = now()
where object_type = 'document';

drop table kb_item_space_map;
