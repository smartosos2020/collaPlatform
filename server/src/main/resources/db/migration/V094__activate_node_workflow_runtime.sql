alter table project_node_workflow_votes
    drop constraint uk_project_node_workflow_vote_actor;

alter table project_node_workflow_votes
    drop constraint project_node_workflow_votes_decision_check;

alter table project_node_workflow_votes
    add column supersedes_vote_id uuid,
    add constraint ck_project_node_workflow_vote_decision
        check (decision in ('approve', 'reject', 'abstain', 'withdraw')),
    add constraint uk_project_node_workflow_vote_scope
        unique (workspace_id, space_id, instance_id, id),
    add constraint uk_project_node_workflow_vote_supersedes
        unique (workspace_id, space_id, instance_id, supersedes_vote_id),
    add constraint fk_project_node_workflow_vote_supersedes
        foreign key (workspace_id, space_id, instance_id, supersedes_vote_id)
        references project_node_workflow_votes(workspace_id, space_id, instance_id, id);

alter table project_node_workflow_commands
    drop constraint project_node_workflow_commands_operation_check;

alter table project_node_workflow_commands
    add constraint ck_project_node_workflow_command_operation
        check (
            operation in (
                'start', 'advance', 'claim', 'delegate', 'vote', 'withdraw', 'complete',
                'auto', 'split', 'join', 'return', 'jump', 'terminate', 'compensate',
                'correct', 'upgrade', 'backfill'
            )
        );

create index idx_project_node_workflow_votes_latest
    on project_node_workflow_votes(
        workspace_id, space_id, instance_id, task_id, voter_id, sequence_number desc
    );

create table project_node_workflow_join_arrivals (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    instance_id uuid not null,
    join_id uuid not null,
    token_id uuid not null,
    arrived_at timestamptz not null,
    constraint uk_project_node_workflow_join_arrival
        unique (workspace_id, space_id, instance_id, join_id, token_id),
    constraint fk_project_node_workflow_join_arrival_join
        foreign key (workspace_id, space_id, instance_id, join_id)
        references project_node_workflow_joins(workspace_id, space_id, instance_id, id)
        on delete cascade,
    constraint fk_project_node_workflow_join_arrival_token
        foreign key (workspace_id, space_id, instance_id, token_id)
        references project_node_workflow_tokens(workspace_id, space_id, instance_id, id)
);

create index idx_project_node_workflow_join_arrivals_lookup
    on project_node_workflow_join_arrivals(
        workspace_id, space_id, instance_id, join_id, arrived_at, id
    );

create index idx_project_node_workflow_tasks_open
    on project_node_workflow_tasks(
        workspace_id, space_id, instance_id, status, node_key, id
    );

create index idx_project_node_workflow_tasks_candidate_roles
    on project_node_workflow_tasks using gin(candidate_roles);

create function guard_project_node_workflow_join_arrival()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    raise exception 'node workflow join arrivals are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_node_workflow_join_arrival
before update or delete on project_node_workflow_join_arrivals
for each row execute function guard_project_node_workflow_join_arrival();
