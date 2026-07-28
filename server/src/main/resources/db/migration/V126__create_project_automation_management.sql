CREATE TABLE project_automation_management_preferences (
 workspace_id uuid NOT NULL, space_id uuid NOT NULL, user_id uuid NOT NULL,
 compact_mode boolean NOT NULL DEFAULT false, default_filter varchar(32) NOT NULL DEFAULT 'all',
 version integer NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(workspace_id,space_id,user_id),
 FOREIGN KEY(workspace_id,space_id) REFERENCES project_spaces(workspace_id,id),
 FOREIGN KEY(workspace_id,user_id) REFERENCES users(workspace_id,id));
CREATE TABLE project_automation_quota_states (
 workspace_id uuid NOT NULL, space_id uuid NOT NULL, quota_type varchar(16) NOT NULL,
 quota_key varchar(160) NOT NULL, window_start timestamptz NOT NULL, used_count integer NOT NULL DEFAULT 0,
 limit_count integer NOT NULL CHECK(limit_count>0), paused_until timestamptz,
 version integer NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(workspace_id,space_id,quota_type,quota_key,window_start),
 FOREIGN KEY(workspace_id,space_id) REFERENCES project_spaces(workspace_id,id));
CREATE TABLE project_automation_quota_receipts (
 workspace_id uuid NOT NULL, space_id uuid NOT NULL, request_key varchar(240) NOT NULL,
 rule_id uuid NOT NULL, actor_id uuid NOT NULL, action_type varchar(64) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(workspace_id,space_id,request_key),
 FOREIGN KEY(workspace_id,space_id,rule_id) REFERENCES project_automation_rules(workspace_id,space_id,id));
CREATE TABLE project_automation_governance_receipts (
 workspace_id uuid NOT NULL, space_id uuid NOT NULL, request_id varchar(120) NOT NULL,
 input_hash char(64) NOT NULL, action varchar(32) NOT NULL, target_type varchar(32) NOT NULL,
 target_key varchar(160) NOT NULL, reason varchar(512) NOT NULL, actor_id uuid NOT NULL,
 response_json jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(workspace_id,space_id,request_id));
CREATE INDEX idx_project_automation_quota_active
 ON project_automation_quota_states(workspace_id,space_id,paused_until,updated_at);
