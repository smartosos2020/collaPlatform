create index idx_audit_logs_actor_action
    on audit_logs (workspace_id, actor_id, action, created_at desc);

create index idx_audit_logs_action_target
    on audit_logs (workspace_id, action, target_type, created_at desc);

create index idx_notifications_target
    on notifications (workspace_id, target_type, target_id, created_at desc);

create index idx_messages_workspace_conversation_created
    on messages (workspace_id, conversation_id, created_at desc);

create index idx_issues_assignee_status
    on issues (workspace_id, assignee_id, status, updated_at desc);

create index idx_documents_workspace_title
    on documents (workspace_id, lower(title));

create index idx_bases_workspace_status_name
    on bases (workspace_id, status, lower(name));

create index idx_base_records_workspace_table_updated
    on base_records (workspace_id, table_id, updated_at desc);
