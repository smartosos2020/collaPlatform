CREATE TABLE project_automation_schedules (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL,
    space_id uuid NOT NULL,
    rule_id uuid NOT NULL,
    rule_version integer NOT NULL CHECK (rule_version > 0),
    trigger_kind varchar(32) NOT NULL CHECK (trigger_kind IN ('cron','fixed_time','due','overdue','dwell')),
    timezone varchar(64) NOT NULL,
    schedule_expression varchar(160) NOT NULL,
    missed_policy varchar(16) NOT NULL CHECK (missed_policy IN ('skip','latest','bounded')),
    cooldown_seconds integer NOT NULL DEFAULT 300 CHECK (cooldown_seconds BETWEEN 0 AND 86400),
    status varchar(16) NOT NULL CHECK (status IN ('active','paused','archived')),
    version integer NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, space_id, id),
    FOREIGN KEY (workspace_id, space_id, rule_id)
      REFERENCES project_automation_rules(workspace_id, space_id, id)
);
CREATE TABLE project_automation_schedule_cursors (
    schedule_id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL,
    space_id uuid NOT NULL,
    cursor_at timestamptz NOT NULL,
    next_fire_at timestamptz,
    lease_owner varchar(120),
    lease_until timestamptz,
    fencing_token bigint NOT NULL DEFAULT 0 CHECK (fencing_token >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, space_id, schedule_id)
      REFERENCES project_automation_schedules(workspace_id, space_id, id) ON DELETE CASCADE
);
CREATE TABLE project_automation_fire_receipts (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL,
    space_id uuid NOT NULL,
    schedule_id uuid NOT NULL,
    window_key varchar(160) NOT NULL,
    candidate_id uuid,
    fired_at timestamptz NOT NULL DEFAULT now(),
    run_id uuid,
    UNIQUE (workspace_id, space_id, schedule_id, window_key, candidate_id),
    FOREIGN KEY (workspace_id, space_id, schedule_id)
      REFERENCES project_automation_schedules(workspace_id, space_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_project_automation_schedules_due
    ON project_automation_schedules(workspace_id, status, updated_at);
CREATE INDEX idx_project_automation_cursors_due
    ON project_automation_schedule_cursors(next_fire_at, lease_until);
CREATE INDEX idx_project_automation_fire_receipts_retention
    ON project_automation_fire_receipts(workspace_id, fired_at);
