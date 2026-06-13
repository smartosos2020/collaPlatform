create table projects (
    id uuid primary key,
    workspace_id uuid not null,
    project_key varchar(32) not null,
    name varchar(128) not null,
    description text,
    status varchar(32) not null,
    conversation_id uuid,
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid,
    updated_at timestamptz not null,
    archived_at timestamptz,
    constraint uk_projects_workspace_key unique (workspace_id, project_key)
);

create index idx_projects_workspace_status on projects (workspace_id, status, updated_at desc);

create table project_members (
    id uuid primary key,
    workspace_id uuid not null,
    project_id uuid not null references projects(id),
    user_id uuid not null,
    project_role varchar(32) not null,
    joined_at timestamptz not null,
    created_by uuid not null,
    archived_at timestamptz,
    constraint uk_project_members unique (project_id, user_id)
);

create index idx_project_members_user on project_members (workspace_id, user_id, archived_at);

create table iterations (
    id uuid primary key,
    workspace_id uuid not null,
    project_id uuid not null references projects(id),
    name varchar(128) not null,
    status varchar(32) not null,
    starts_at date,
    ends_at date,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_iterations_project on iterations (project_id, status);

create table issues (
    id uuid primary key,
    workspace_id uuid not null,
    project_id uuid not null references projects(id),
    issue_key varchar(32) not null,
    issue_type varchar(32) not null,
    title varchar(255) not null,
    description text,
    priority varchar(32) not null,
    status varchar(32) not null,
    assignee_id uuid,
    reporter_id uuid not null,
    iteration_id uuid,
    due_at date,
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    constraint uk_issues_project_key unique (project_id, issue_key)
);

create index idx_issues_project_status on issues (project_id, status, updated_at desc);
create index idx_issues_assignee on issues (workspace_id, assignee_id, status);
create index idx_issues_updated on issues (workspace_id, updated_at desc);

create table issue_comments (
    id uuid primary key,
    workspace_id uuid not null,
    issue_id uuid not null references issues(id),
    author_id uuid not null,
    content text not null,
    created_at timestamptz not null,
    updated_at timestamptz,
    deleted_at timestamptz
);

create index idx_issue_comments_issue on issue_comments (issue_id, created_at);

create table issue_attachments (
    id uuid primary key,
    workspace_id uuid not null,
    issue_id uuid not null references issues(id),
    file_id uuid not null references files(id),
    created_by uuid not null,
    created_at timestamptz not null,
    constraint uk_issue_attachments_file unique (workspace_id, issue_id, file_id)
);

create table issue_activity_logs (
    id uuid primary key,
    workspace_id uuid not null,
    issue_id uuid not null references issues(id),
    actor_id uuid,
    action varchar(64) not null,
    from_value text,
    to_value text,
    metadata jsonb,
    created_at timestamptz not null
);

create index idx_issue_activity_logs_issue on issue_activity_logs (issue_id, created_at desc);
