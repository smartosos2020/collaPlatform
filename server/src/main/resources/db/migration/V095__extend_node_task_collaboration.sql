alter table project_node_workflow_tasks
    add column candidate_user_ids jsonb not null default '[]'::jsonb
        check (jsonb_typeof(candidate_user_ids) = 'array'),
    add column form_snapshot jsonb not null default '{"fields":[]}'::jsonb
        check (jsonb_typeof(form_snapshot) = 'object'),
    add column artifact_policy_snapshot jsonb not null default '[]'::jsonb
        check (jsonb_typeof(artifact_policy_snapshot) = 'array'),
    add column planned_start_at timestamptz,
    add column due_at timestamptz,
    add column timed_out_at timestamptz,
    add column escalated_at timestamptz;

create index idx_project_node_workflow_tasks_inbox_assignee
    on project_node_workflow_tasks(
        assignee_id, workspace_id, space_id, status, created_at, id
    );

create index idx_project_node_workflow_tasks_candidates
    on project_node_workflow_tasks using gin(candidate_user_ids);

create index idx_project_node_workflow_tasks_due
    on project_node_workflow_tasks(
        workspace_id, status, due_at, id
    )
    where status in ('pending', 'claimed') and due_at is not null;

create table project_node_workflow_task_artifacts (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    task_id uuid not null,
    artifact_key varchar(64) not null check (
        artifact_key ~ '^[a-z][a-z0-9_]{0,63}$'
    ),
    artifact_kind varchar(16) not null check (artifact_kind in ('file', 'object')),
    file_id uuid,
    object_type varchar(64),
    object_id uuid,
    created_by uuid not null,
    created_at timestamptz not null,
    constraint fk_project_node_workflow_task_artifact_task
        foreign key (workspace_id, space_id, instance_id, task_id)
        references project_node_workflow_tasks(workspace_id, space_id, instance_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_task_artifact_actor
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint ck_project_node_workflow_task_artifact_reference
        check (
            (artifact_kind = 'file'
                and file_id is not null and object_type is null and object_id is null)
            or
            (artifact_kind = 'object'
                and file_id is null and object_type is not null and object_id is not null)
        )
);

create unique index uk_project_node_workflow_task_file_artifact
    on project_node_workflow_task_artifacts(
        workspace_id, space_id, instance_id, task_id, artifact_key, file_id
    )
    where artifact_kind = 'file';

create unique index uk_project_node_workflow_task_object_artifact
    on project_node_workflow_task_artifacts(
        workspace_id, space_id, instance_id, task_id, artifact_key, object_type, object_id
    )
    where artifact_kind = 'object';

create index idx_project_node_workflow_task_artifacts_lookup
    on project_node_workflow_task_artifacts(
        workspace_id, space_id, instance_id, task_id, artifact_key, created_at, id
    );

alter table project_node_workflow_commands
    drop constraint ck_project_node_workflow_command_operation;

alter table project_node_workflow_commands
    add constraint ck_project_node_workflow_command_operation
        check (
            operation in (
                'start', 'advance', 'claim', 'delegate', 'transfer', 'vote', 'withdraw',
                'complete', 'submit', 'timeout', 'auto', 'split', 'join', 'return',
                'jump', 'terminate', 'compensate', 'correct', 'upgrade', 'backfill'
            )
        );

alter table project_node_workflow_history
    drop constraint project_node_workflow_history_event_kind_check;

alter table project_node_workflow_history
    add constraint ck_project_node_workflow_history_event_kind
        check (
            event_kind in (
                'started', 'entered', 'task_created', 'assignment_empty', 'claimed',
                'delegated', 'transferred', 'voted', 'completed', 'submitted',
                'timed_out', 'split', 'joined', 'returned', 'jumped', 'terminated',
                'compensated', 'corrected', 'upgraded', 'backfilled'
            )
        );

create or replace function guard_project_node_workflow_task()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'node workflow tasks cannot be deleted directly' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.instance_id <> old.instance_id
        or new.token_id <> old.token_id
        or new.node_key <> old.node_key
        or new.assignment_strategy <> old.assignment_strategy
        or new.candidate_roles <> old.candidate_roles
        or new.candidate_user_ids <> old.candidate_user_ids
        or new.quorum_count is distinct from old.quorum_count
        or new.form_snapshot <> old.form_snapshot
        or new.artifact_policy_snapshot <> old.artifact_policy_snapshot
        or new.planned_start_at is distinct from old.planned_start_at
        or new.due_at is distinct from old.due_at
        or new.created_at <> old.created_at then
        raise exception 'node workflow task definition is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create function guard_project_node_workflow_task_artifact()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'node workflow task artifacts are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_node_workflow_task_artifact
before update or delete on project_node_workflow_task_artifacts
for each row execute function guard_project_node_workflow_task_artifact();
