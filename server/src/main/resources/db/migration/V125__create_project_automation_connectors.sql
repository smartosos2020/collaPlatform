CREATE TABLE project_automation_connectors (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL, space_id uuid NOT NULL,
 name varchar(160) NOT NULL, target_uri varchar(2048) NOT NULL,
 credential_reference varchar(240), status varchar(16) NOT NULL CHECK(status IN('active','disabled','archived')),
 signing_version integer NOT NULL DEFAULT 1, version integer NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(workspace_id,space_id,id), FOREIGN KEY(workspace_id,space_id) REFERENCES project_spaces(workspace_id,id));
CREATE TABLE project_automation_deliveries (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL, space_id uuid NOT NULL, connector_id uuid NOT NULL,
 run_id uuid, payload_version integer NOT NULL, payload_hash char(64) NOT NULL, nonce varchar(160) NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN('pending','processing','succeeded','retry','dead_letter','abandoned')),
 attempt_count integer NOT NULL DEFAULT 0, next_attempt_at timestamptz, lease_owner varchar(120),
 lease_until timestamptz, fencing_token bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(),
 completed_at timestamptz, UNIQUE(workspace_id,space_id,id), UNIQUE(workspace_id,space_id,connector_id,nonce),
 FOREIGN KEY(workspace_id,space_id,connector_id) REFERENCES project_automation_connectors(workspace_id,space_id,id));
CREATE TABLE project_automation_delivery_attempts (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL, space_id uuid NOT NULL, delivery_id uuid NOT NULL,
 attempt_number integer NOT NULL, outcome varchar(20) NOT NULL, http_status integer, error_code varchar(80),
 duration_ms integer NOT NULL DEFAULT 0, attempted_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(workspace_id,space_id,delivery_id,attempt_number),
 FOREIGN KEY(workspace_id,space_id,delivery_id)
   REFERENCES project_automation_deliveries(workspace_id,space_id,id) ON DELETE CASCADE);
CREATE TABLE project_automation_dead_letters (
 delivery_id uuid PRIMARY KEY,
 workspace_id uuid NOT NULL, space_id uuid NOT NULL, reason_code varchar(80) NOT NULL,
 replay_count integer NOT NULL DEFAULT 0, last_reason varchar(512), created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(workspace_id,space_id,delivery_id)
   REFERENCES project_automation_deliveries(workspace_id,space_id,id) ON DELETE CASCADE);
CREATE TABLE project_automation_connector_commands (
 workspace_id uuid NOT NULL, space_id uuid NOT NULL, request_id varchar(120) NOT NULL,
 input_hash char(64) NOT NULL, response_json jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(workspace_id,space_id,request_id));
CREATE INDEX idx_project_automation_delivery_claim ON project_automation_deliveries(status,next_attempt_at,lease_until);
