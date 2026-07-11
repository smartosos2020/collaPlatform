delete from object_type_rules where object_type = 'document';

delete from object_links where object_type = 'document';
delete from object_recent_accesses where object_type = 'document';
delete from object_favorites where object_type = 'document';
delete from notifications where target_type = 'document';
delete from resource_permissions where resource_type = 'document';
delete from resource_permission_requests where resource_type = 'document';
delete from search_index_entries where object_type = 'document';
delete from knowledge_subscriptions where target_type = 'document';
delete from file_usages where target_type = 'document';
delete from message_links where target_type = 'document';
delete from issue_relations where target_type = 'document';
delete from base_record_relations where target_type = 'document';
delete from knowledge_item_relations where target_type = 'document';
update knowledge_base_items set target_object_type = null, target_object_id = null
where target_object_type = 'document';

drop table knowledge_item_permissions;

alter table object_links add constraint chk_object_links_no_document check (object_type <> 'document');
alter table object_recent_accesses add constraint chk_object_recent_no_document check (object_type <> 'document');
alter table object_favorites add constraint chk_object_favorites_no_document check (object_type <> 'document');
alter table resource_permissions add constraint chk_resource_permissions_no_document check (resource_type <> 'document');
alter table resource_permission_requests add constraint chk_resource_requests_no_document check (resource_type <> 'document');
alter table search_index_entries add constraint chk_search_entries_no_document check (object_type <> 'document');

