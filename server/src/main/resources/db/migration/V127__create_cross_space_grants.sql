CREATE TABLE project_cross_space_grants (
    workspace_id uuid NOT NULL,
    id uuid NOT NULL,
    source_space_id uuid NOT NULL,
    target_space_id uuid NOT NULL,
    name varchar(160) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'draft',
    current_version integer NOT NULL DEFAULT 1,
    source_confirmed_at timestamptz,
    source_confirmed_by uuid,
    target_confirmed_at timestamptz,
    target_confirmed_by uuid,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    archived_at timestamptz,
    PRIMARY KEY (workspace_id, id),
    FOREIGN KEY (workspace_id, source_space_id) REFERENCES project_spaces(workspace_id, id),
    FOREIGN KEY (workspace_id, target_space_id) REFERENCES project_spaces(workspace_id, id),
    FOREIGN KEY (workspace_id, created_by) REFERENCES users(workspace_id, id),
    FOREIGN KEY (workspace_id, updated_by) REFERENCES users(workspace_id, id),
    CHECK (source_space_id <> target_space_id),
    CHECK (status IN ('draft', 'requested', 'active', 'paused', 'revoked', 'archived'))
);

CREATE TABLE project_cross_space_grant_versions (
    workspace_id uuid NOT NULL,
    grant_id uuid NOT NULL,
    version_number integer NOT NULL,
    scope_json jsonb NOT NULL,
    scope_hash char(64) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, grant_id, version_number),
    FOREIGN KEY (workspace_id, grant_id)
        REFERENCES project_cross_space_grants(workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, created_by) REFERENCES users(workspace_id, id)
);

CREATE TABLE project_cross_space_grant_receipts (
    workspace_id uuid NOT NULL,
    request_id varchar(120) NOT NULL,
    actor_id uuid NOT NULL,
    operation varchar(32) NOT NULL,
    grant_id uuid NOT NULL,
    request_hash char(64) NOT NULL,
    response_json jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, actor_id, operation, request_id),
    FOREIGN KEY (workspace_id, grant_id)
        REFERENCES project_cross_space_grants(workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, actor_id) REFERENCES users(workspace_id, id)
);

CREATE INDEX idx_cross_space_grants_source
    ON project_cross_space_grants(workspace_id, source_space_id, status, updated_at DESC);
CREATE INDEX idx_cross_space_grants_target
    ON project_cross_space_grants(workspace_id, target_space_id, status, updated_at DESC);
CREATE INDEX idx_cross_space_grant_versions_history
    ON project_cross_space_grant_versions(workspace_id, grant_id, version_number DESC);
