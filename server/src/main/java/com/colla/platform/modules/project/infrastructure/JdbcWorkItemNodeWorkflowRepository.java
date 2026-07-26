package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeHistoryEntry;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.DueNodeTask;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeJoin;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTask;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskArtifact;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskInboxItem;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeToken;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeVote;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowInstance;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCompensationRun;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCompensationStep;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemNodeWorkflowRepository implements WorkItemNodeWorkflowRepository {
    private static final String INSTANCE_SELECT = """
        select id, workspace_id, space_id, work_item_id, type_definition_id, type_version_id,
               config_hash, status, work_item_version, aggregate_version, started_at, updated_at,
               completed_at
          from project_node_workflow_instances
        """;
    private static final String TOKEN_SELECT = """
        select id, instance_id, node_key, stage_key, status, parent_token_id, split_key,
               join_key, correlation_key, aggregate_version, entered_at, completed_at
          from project_node_workflow_tokens
        """;
    private static final String TASK_SELECT = """
        select id, instance_id, token_id, node_key, assignment_strategy, candidate_roles::text,
               candidate_user_ids::text, quorum_count, form_snapshot::text,
               artifact_policy_snapshot::text, status, assignee_id, aggregate_version,
               created_at, planned_start_at, due_at, timed_out_at, escalated_at, claimed_at,
               completed_at
          from project_node_workflow_tasks
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemNodeWorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean tryStartInstance(InstanceStart value) {
        return jdbcTemplate.update(
            """
                insert into project_node_workflow_instances (
                    id, workspace_id, space_id, work_item_id, type_definition_id, type_version_id,
                    config_hash, status, work_item_version, aggregate_version, started_by,
                    started_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'active', ?, 0, ?, now(), ?, now())
                on conflict (workspace_id, space_id, work_item_id) do nothing
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.workItemId(),
            value.typeDefinitionId(), value.typeVersionId(), value.configHash(),
            value.workItemVersion(), value.actorId(), value.actorId()
        ) == 1;
    }

    @Override
    public Optional<NodeWorkflowInstance> findInstance(
        UUID workspaceId, UUID spaceId, UUID workItemId
    ) {
        return instance(workspaceId, spaceId, workItemId, false);
    }

    @Override
    public Optional<NodeWorkflowInstance> lockInstance(
        UUID workspaceId, UUID spaceId, UUID workItemId
    ) {
        return instance(workspaceId, spaceId, workItemId, true);
    }

    private Optional<NodeWorkflowInstance> instance(
        UUID workspaceId, UUID spaceId, UUID workItemId, boolean lock
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                INSTANCE_SELECT + " where workspace_id=? and space_id=? and work_item_id=?"
                    + (lock ? " for update" : ""),
                this::mapInstance,
                workspaceId, spaceId, workItemId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public int updateInstance(
        UUID instanceId, String expectedStatus, String targetStatus,
        long expectedWorkItemVersion, long targetWorkItemVersion,
        long expectedAggregateVersion, UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_instances
                   set status=?, work_item_version=?, aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now(),
                       completed_at=case when ?='completed' then now() else completed_at end
                 where id=? and status=? and work_item_version=? and aggregate_version=?
                """,
            targetStatus, targetWorkItemVersion, actorId, targetStatus, instanceId,
            expectedStatus, expectedWorkItemVersion, expectedAggregateVersion
        );
    }

    @Override
    public int alignWorkItemVersion(
        UUID instanceId, long expectedWorkItemVersion, long targetWorkItemVersion, UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_instances
                   set work_item_version=?, updated_by=?, updated_at=now()
                 where id=? and work_item_version=?
                """,
            targetWorkItemVersion, actorId, instanceId, expectedWorkItemVersion
        );
    }

    @Override
    public void insertToken(TokenInsert value) {
        jdbcTemplate.update(
            """
                insert into project_node_workflow_tokens (
                    id, workspace_id, space_id, instance_id, node_key, stage_key, status,
                    parent_token_id, split_key, join_key, correlation_key, aggregate_version,
                    entered_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, now())
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(),
            value.nodeKey(), value.stageKey(), value.status(), value.parentTokenId(),
            value.splitKey(), value.joinKey(), value.correlationKey()
        );
    }

    @Override
    public Optional<NodeToken> lockToken(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID tokenId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                TOKEN_SELECT
                    + " where workspace_id=? and space_id=? and instance_id=? and id=? for update",
                this::mapToken,
                workspaceId, spaceId, instanceId, tokenId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<NodeToken> activeTokens(UUID workspaceId, UUID spaceId, UUID instanceId) {
        return jdbcTemplate.query(
            TOKEN_SELECT
                + " where workspace_id=? and space_id=? and instance_id=?"
                + " and status in ('active','waiting') order by entered_at, id limit 256",
            this::mapToken,
            workspaceId, spaceId, instanceId
        );
    }

    @Override
    public int updateTokenStatus(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID tokenId,
        String expectedStatus, String targetStatus
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tokens
                   set status=?, aggregate_version=aggregate_version+1,
                       completed_at=case when ? in ('completed','canceled') then now()
                                         else completed_at end
                 where workspace_id=? and space_id=? and instance_id=? and id=? and status=?
                """,
            targetStatus, targetStatus, workspaceId, spaceId, instanceId, tokenId, expectedStatus
        );
    }

    @Override
    public int closeJoinTokens(
        UUID workspaceId, UUID spaceId, UUID instanceId, String nodeKey,
        String correlationKey, String targetStatus
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tokens
                   set status=?, aggregate_version=aggregate_version+1, completed_at=now()
                 where workspace_id=? and space_id=? and instance_id=? and node_key=?
                   and correlation_key=? and status in ('active','waiting')
                """,
            targetStatus, workspaceId, spaceId, instanceId, nodeKey, correlationKey
        );
    }

    @Override
    public int cancelOpenTasksForCorrelation(
        UUID workspaceId, UUID spaceId, UUID instanceId, String correlationKey
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tasks task
                   set status='canceled', aggregate_version=aggregate_version+1,
                       completed_at=now()
                 where task.workspace_id=? and task.space_id=? and task.instance_id=?
                   and task.status in ('pending','claimed')
                   and exists (
                       select 1
                         from project_node_workflow_tokens token
                        where token.workspace_id=task.workspace_id
                          and token.space_id=task.space_id
                          and token.instance_id=task.instance_id
                          and token.id=task.token_id
                          and token.correlation_key=?
                   )
                """,
            workspaceId, spaceId, instanceId, correlationKey
        );
    }

    @Override
    public int closeOpenTokensForCorrelation(
        UUID workspaceId, UUID spaceId, UUID instanceId,
        String correlationKey, String targetStatus
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tokens
                   set status=?, aggregate_version=aggregate_version+1, completed_at=now()
                 where workspace_id=? and space_id=? and instance_id=?
                   and correlation_key=? and status in ('active','waiting')
                """,
            targetStatus, workspaceId, spaceId, instanceId, correlationKey
        );
    }

    @Override
    public int cancelOpenTasks(UUID workspaceId, UUID spaceId, UUID instanceId) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tasks
                   set status='canceled', aggregate_version=aggregate_version+1,
                       completed_at=now()
                 where workspace_id=? and space_id=? and instance_id=?
                   and status in ('pending','claimed')
                """,
            workspaceId, spaceId, instanceId
        );
    }

    @Override
    public int cancelOpenTokens(UUID workspaceId, UUID spaceId, UUID instanceId) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tokens
                   set status='canceled', aggregate_version=aggregate_version+1,
                       completed_at=now()
                 where workspace_id=? and space_id=? and instance_id=?
                   and status in ('active','waiting')
                """,
            workspaceId, spaceId, instanceId
        );
    }

    @Override
    public int cancelWaitingJoins(UUID workspaceId, UUID spaceId, UUID instanceId) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_joins
                   set status='canceled', aggregate_version=aggregate_version+1
                 where workspace_id=? and space_id=? and instance_id=? and status='waiting'
                """,
            workspaceId, spaceId, instanceId
        );
    }

    @Override
    public int recoverInstance(
        UUID instanceId,
        String expectedStatus,
        String targetStatus,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        long expectedAggregateVersion,
        UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_instances
                   set status=?, work_item_version=?, aggregate_version=aggregate_version+1,
                       recovery_count=recovery_count+1, last_recovery_at=now(),
                       updated_by=?, updated_at=now(),
                       completed_at=case when ?='terminated' then now() else null end
                 where id=? and status=? and work_item_version=? and aggregate_version=?
                """,
            targetStatus, targetWorkItemVersion, actorId, targetStatus, instanceId,
            expectedStatus, expectedWorkItemVersion, expectedAggregateVersion
        );
    }

    @Override
    public int upgradeInstanceBinding(
        UUID instanceId,
        UUID expectedTypeVersionId,
        String expectedConfigHash,
        UUID targetTypeVersionId,
        String targetConfigHash,
        String targetStatus,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        long expectedAggregateVersion,
        UUID actorId
    ) {
        jdbcTemplate.queryForObject(
            "select set_config('colla.node_workflow_upgrade', 'on', true)",
            String.class
        );
        return jdbcTemplate.update(
            """
                update project_node_workflow_instances
                   set type_version_id=?, config_hash=?, status=?, work_item_version=?,
                       aggregate_version=aggregate_version+1, recovery_count=recovery_count+1,
                       last_recovery_at=now(), updated_by=?, updated_at=now(),
                       completed_at=case when ?='completed' then now() else null end
                 where id=? and type_version_id=? and config_hash=? and status='active'
                   and work_item_version=? and aggregate_version=?
                """,
            targetTypeVersionId, targetConfigHash, targetStatus, targetWorkItemVersion, actorId,
            targetStatus,
            instanceId, expectedTypeVersionId, expectedConfigHash,
            expectedWorkItemVersion, expectedAggregateVersion
        );
    }

    @Override
    public void insertTask(TaskInsert value) {
        jdbcTemplate.update(
            """
                insert into project_node_workflow_tasks (
                    id, workspace_id, space_id, instance_id, token_id, node_key,
                    assignment_strategy, candidate_roles, candidate_user_ids, quorum_count,
                    form_snapshot, artifact_policy_snapshot, planned_start_at, due_at, status,
                    aggregate_version, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb,
                          ?, ?, 'pending', 0, now())
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(),
            value.tokenId(), value.nodeKey(), value.assignmentStrategy(),
            json(objectMapper.valueToTree(value.candidateRoles())),
            json(objectMapper.valueToTree(value.candidateUserIds())), value.quorumCount(),
            json(value.formSnapshot() == null
                ? objectMapper.createObjectNode().set("fields", objectMapper.createArrayNode())
                : value.formSnapshot()),
            json(value.artifactPolicySnapshot() == null
                ? objectMapper.createArrayNode()
                : value.artifactPolicySnapshot()),
            timestamp(value.plannedStartAt()), timestamp(value.dueAt())
        );
    }

    @Override
    public Optional<NodeTask> lockTask(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID taskId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                TASK_SELECT
                    + " where workspace_id=? and space_id=? and instance_id=? and id=? for update",
                this::mapTask,
                workspaceId, spaceId, instanceId, taskId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<NodeTask> findTask(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID taskId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                TASK_SELECT + " where workspace_id=? and space_id=? and instance_id=? and id=?",
                this::mapTask,
                workspaceId, spaceId, instanceId, taskId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<NodeTask> openTasks(UUID workspaceId, UUID spaceId, UUID instanceId) {
        return jdbcTemplate.query(
            TASK_SELECT
                + " where workspace_id=? and space_id=? and instance_id=?"
                + " and status in ('pending','claimed') order by node_key, id limit 200",
            this::mapTask,
            workspaceId, spaceId, instanceId
        );
    }

    @Override
    public int claimTask(
        UUID taskId, String expectedStatus, UUID assigneeId, long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tasks
                   set status='claimed', assignee_id=?, aggregate_version=aggregate_version+1,
                       claimed_at=coalesce(claimed_at, now())
                 where id=? and status=? and aggregate_version=?
                """,
            assigneeId, taskId, expectedStatus, expectedAggregateVersion
        );
    }

    @Override
    public int delegateTask(
        UUID taskId, UUID expectedAssigneeId, UUID targetAssigneeId, long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tasks
                   set assignee_id=?, aggregate_version=aggregate_version+1, claimed_at=now()
                 where id=? and status='claimed' and assignee_id=? and aggregate_version=?
                """,
            targetAssigneeId, taskId, expectedAssigneeId, expectedAggregateVersion
        );
    }

    @Override
    public int transferTask(UUID taskId, UUID targetAssigneeId, long expectedAggregateVersion) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tasks
                   set status='claimed', assignee_id=?, aggregate_version=aggregate_version+1,
                       claimed_at=now()
                 where id=? and status in ('pending','claimed') and aggregate_version=?
                """,
            targetAssigneeId, taskId, expectedAggregateVersion
        );
    }

    @Override
    public int completeTask(UUID taskId, long expectedAggregateVersion) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_tasks
                   set status='completed', aggregate_version=aggregate_version+1, completed_at=now()
                 where id=? and status in ('pending','claimed') and aggregate_version=?
                """,
            taskId, expectedAggregateVersion
        );
    }

    @Override
    public void insertTaskArtifact(TaskArtifactInsert value) {
        jdbcTemplate.update(
            """
                insert into project_node_workflow_task_artifacts (
                    id, workspace_id, space_id, instance_id, task_id, artifact_key,
                    artifact_kind, file_id, object_type, object_id, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(), value.taskId(),
            value.artifactKey(), value.kind(), value.fileId(), value.objectType(), value.objectId(),
            value.actorId()
        );
    }

    @Override
    public List<NodeTaskArtifact> taskArtifacts(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID taskId
    ) {
        return jdbcTemplate.query(
            """
                select id, task_id, artifact_key, artifact_kind, file_id, object_type,
                       object_id, created_by, created_at
                  from project_node_workflow_task_artifacts
                 where workspace_id=? and space_id=? and instance_id=? and task_id=?
                 order by artifact_key, created_at, id
                 limit 512
                """,
            (row, number) -> new NodeTaskArtifact(
                row.getObject("id", UUID.class),
                row.getObject("task_id", UUID.class),
                row.getString("artifact_key"),
                row.getString("artifact_kind"),
                row.getObject("file_id", UUID.class),
                row.getString("object_type"),
                row.getObject("object_id", UUID.class),
                row.getObject("created_by", UUID.class),
                row.getTimestamp("created_at").toInstant()
            ),
            workspaceId, spaceId, instanceId, taskId
        );
    }

    @Override
    public List<NodeVote> votes(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID taskId
    ) {
        return jdbcTemplate.query(
            """
                select id, task_id, voter_id, decision, supersedes_vote_id, sequence_number,
                       occurred_at
                  from project_node_workflow_votes
                 where workspace_id=? and space_id=? and instance_id=? and task_id=?
                 order by sequence_number
                """,
            (row, number) -> new NodeVote(
                row.getObject("id", UUID.class),
                row.getObject("task_id", UUID.class),
                row.getObject("voter_id", UUID.class),
                row.getString("decision"),
                row.getObject("supersedes_vote_id", UUID.class),
                row.getLong("sequence_number"),
                row.getTimestamp("occurred_at").toInstant()
            ),
            workspaceId, spaceId, instanceId, taskId
        );
    }

    @Override
    public void appendVote(VoteInsert value) {
        jdbcTemplate.update(
            """
                insert into project_node_workflow_votes (
                    id, workspace_id, space_id, instance_id, task_id, token_id, node_key,
                    voter_id, decision, supersedes_vote_id, sequence_number, public_payload,
                    occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(),
            value.taskId(), value.tokenId(), value.nodeKey(), value.voterId(),
            value.decision(), value.supersedesVoteId(), value.sequenceNumber(),
            json(value.publicPayload())
        );
    }

    @Override
    public boolean tryCreateJoin(JoinInsert value) {
        return jdbcTemplate.update(
            """
                insert into project_node_workflow_joins (
                    id, workspace_id, space_id, instance_id, join_key, node_key,
                    correlation_key, policy, expected_count, quorum_count, arrived_count,
                    status, aggregate_version, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'waiting', 0, now())
                on conflict (workspace_id, space_id, instance_id, join_key, correlation_key)
                do nothing
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(),
            value.joinKey(), value.nodeKey(), value.correlationKey(), value.policy(),
            value.expectedCount(), value.quorumCount()
        ) == 1;
    }

    @Override
    public Optional<NodeJoin> lockJoin(
        UUID workspaceId, UUID spaceId, UUID instanceId, String joinKey, String correlationKey
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, instance_id, join_key, node_key, correlation_key, policy,
                           expected_count, quorum_count, arrived_count, status, aggregate_version
                      from project_node_workflow_joins
                     where workspace_id=? and space_id=? and instance_id=?
                       and join_key=? and correlation_key=? for update
                    """,
                this::mapJoin,
                workspaceId, spaceId, instanceId, joinKey, correlationKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean tryRecordJoinArrival(JoinArrival value) {
        return jdbcTemplate.update(
            """
                insert into project_node_workflow_join_arrivals (
                    id, workspace_id, space_id, instance_id, join_id, token_id, arrived_at
                ) values (?, ?, ?, ?, ?, ?, now())
                on conflict (workspace_id, space_id, instance_id, join_id, token_id) do nothing
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(),
            value.joinId(), value.tokenId()
        ) == 1;
    }

    @Override
    public int updateJoinArrival(UUID joinId, int expectedArrivedCount, int targetArrivedCount) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_joins
                   set arrived_count=?, aggregate_version=aggregate_version+1
                 where id=? and status='waiting' and arrived_count=?
                """,
            targetArrivedCount, joinId, expectedArrivedCount
        );
    }

    @Override
    public int releaseJoin(UUID joinId, int expectedArrivedCount, long expectedAggregateVersion) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_joins
                   set status='released', aggregate_version=aggregate_version+1, released_at=now()
                 where id=? and status='waiting' and arrived_count=?
                   and aggregate_version=?
                """,
            joinId, expectedArrivedCount, expectedAggregateVersion
        );
    }

    @Override
    public boolean tryStartCommand(CommandStart value) {
        return jdbcTemplate.update(
            """
                insert into project_node_workflow_commands (
                    id, workspace_id, space_id, work_item_id, instance_id, operation, node_key,
                    expected_work_item_version, expected_instance_version, request_id,
                    request_hash, status, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, now())
                on conflict (workspace_id, work_item_id, operation, request_id) do nothing
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.workItemId(),
            value.instanceId(), value.operation(), value.nodeKey(),
            value.expectedWorkItemVersion(), value.expectedInstanceVersion(),
            value.requestId(), value.requestHash(), value.actorId()
        ) == 1;
    }

    @Override
    public Optional<CommandReceipt> findCommand(
        UUID workspaceId, UUID workItemId, String operation, String requestId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, request_hash, status, response_payload, created_by
                      from project_node_workflow_commands
                     where workspace_id=? and work_item_id=? and operation=? and request_id=?
                    """,
                (row, number) -> new CommandReceipt(
                    row.getObject("id", UUID.class),
                    row.getString("request_hash"),
                    row.getString("status"),
                    parse(row.getString("response_payload")),
                    row.getObject("created_by", UUID.class)
                ),
                workspaceId, workItemId, operation, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void completeCommand(UUID commandId, JsonNode response) {
        if (jdbcTemplate.update(
            """
                update project_node_workflow_commands
                   set status='completed', response_payload=?::jsonb, completed_at=now()
                 where id=? and status='pending'
                """,
            json(response), commandId
        ) != 1) {
            throw new IllegalStateException("Node workflow command receipt could not be completed");
        }
    }

    @Override
    public void insertCompensationRun(CompensationRunInsert value) {
        jdbcTemplate.update(
            """
                insert into project_node_workflow_compensation_runs (
                    id, workspace_id, space_id, instance_id, command_id, command_key,
                    status, next_step, total_steps, created_by, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, 'running', 0, ?, ?, now(), now())
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(),
            value.commandId(), value.commandKey(), value.totalSteps(), value.actorId()
        );
    }

    @Override
    public void insertCompensationStep(CompensationStepInsert value) {
        jdbcTemplate.update(
            """
                insert into project_node_workflow_compensation_steps (
                    id, workspace_id, space_id, instance_id, run_id, compensation_key,
                    action_key, sort_order, status, attempt_count
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'pending', 0)
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(),
            value.runId(), value.compensationKey(), value.actionKey(), value.sortOrder()
        );
    }

    @Override
    public Optional<NodeCompensationRun> lockCompensationRun(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID runId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, instance_id, command_id, command_key, status, next_step,
                           total_steps, failure_code
                      from project_node_workflow_compensation_runs
                     where workspace_id=? and space_id=? and instance_id=? and id=?
                     for update
                    """,
                (row, number) -> new NodeCompensationRun(
                    row.getObject("id", UUID.class),
                    row.getObject("instance_id", UUID.class),
                    row.getObject("command_id", UUID.class),
                    row.getString("command_key"),
                    row.getString("status"),
                    row.getInt("next_step"),
                    row.getInt("total_steps"),
                    row.getString("failure_code")
                ),
                workspaceId, spaceId, instanceId, runId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<NodeCompensationStep> compensationSteps(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID runId
    ) {
        return jdbcTemplate.query(
            """
                select id, run_id, compensation_key, action_key, sort_order, status,
                       attempt_count, failure_code
                  from project_node_workflow_compensation_steps
                 where workspace_id=? and space_id=? and instance_id=? and run_id=?
                 order by sort_order, id
                """,
            (row, number) -> new NodeCompensationStep(
                row.getObject("id", UUID.class),
                row.getObject("run_id", UUID.class),
                row.getString("compensation_key"),
                row.getString("action_key"),
                row.getInt("sort_order"),
                row.getString("status"),
                row.getInt("attempt_count"),
                row.getString("failure_code")
            ),
            workspaceId, spaceId, instanceId, runId
        );
    }

    @Override
    public int completeCompensationStep(UUID stepId) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_compensation_steps
                   set status='completed', attempt_count=attempt_count+1,
                       failure_code=null, completed_at=now()
                 where id=? and status in ('pending','failed')
                """,
            stepId
        );
    }

    @Override
    public int completeCompensationRun(UUID runId, int totalSteps) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_compensation_runs
                   set status='completed', next_step=?, failure_code=null,
                       updated_at=now(), completed_at=now()
                 where id=? and status in ('pending','running','failed')
                """,
            totalSteps, runId
        );
    }

    @Override
    public int markCompensationRunRunning(UUID runId) {
        return jdbcTemplate.update(
            """
                update project_node_workflow_compensation_runs
                   set status='running', failure_code=null, updated_at=now()
                 where id=? and status in ('pending','failed','running')
                """,
            runId
        );
    }

    @Override
    public void appendHistory(HistoryAppend value) {
        jdbcTemplate.update(
            """
                insert into project_node_workflow_history (
                    id, workspace_id, space_id, instance_id, work_item_id, sequence_number,
                    type_definition_id, type_version_id, config_hash, event_kind, node_key,
                    token_id, task_id, actor_id, actor_class, decision_reference,
                    correlation_id, causation_id, public_payload, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.instanceId(),
            value.workItemId(), value.sequenceNumber(), value.typeDefinitionId(),
            value.typeVersionId(), value.configHash(), value.eventKind(), value.nodeKey(),
            value.tokenId(), value.taskId(), value.actorId(), value.actorClass(),
            value.decisionReference(), value.correlationId(), value.causationId(),
            json(value.publicPayload())
        );
    }

    @Override
    public long nextHistorySequence(UUID workspaceId, UUID spaceId, UUID instanceId) {
        return jdbcTemplate.queryForObject(
            """
                select coalesce(max(sequence_number), 0) + 1
                  from project_node_workflow_history
                 where workspace_id=? and space_id=? and instance_id=?
                """,
            Long.class,
            workspaceId, spaceId, instanceId
        );
    }

    @Override
    public List<NodeHistoryEntry> pageHistory(
        UUID workspaceId, UUID spaceId, UUID instanceId, Long beforeSequence, int limit
    ) {
        String before = beforeSequence == null ? "" : " and sequence_number < ?";
        ArrayList<Object> arguments = new ArrayList<>(List.of(workspaceId, spaceId, instanceId));
        if (beforeSequence != null) {
            arguments.add(beforeSequence);
        }
        arguments.add(limit);
        return jdbcTemplate.query(
            """
                select id, sequence_number, event_kind, node_key, token_id, task_id, actor_id,
                       actor_class, decision_reference, correlation_id, public_payload::text,
                       occurred_at
                  from project_node_workflow_history
                 where workspace_id=? and space_id=? and instance_id=?
                """ + before + " order by sequence_number desc limit ?",
            (row, number) -> new NodeHistoryEntry(
                row.getObject("id", UUID.class),
                row.getLong("sequence_number"),
                row.getString("event_kind"),
                row.getString("node_key"),
                row.getObject("token_id", UUID.class),
                row.getObject("task_id", UUID.class),
                row.getObject("actor_id", UUID.class),
                row.getString("actor_class"),
                row.getString("decision_reference"),
                row.getString("correlation_id"),
                parse(row.getString("public_payload")),
                row.getTimestamp("occurred_at").toInstant()
            ),
            arguments.toArray()
        );
    }

    @Override
    public Set<String> participantRoles(
        UUID workspaceId, UUID spaceId, UUID workItemId, UUID actorId
    ) {
        return Set.copyOf(jdbcTemplate.queryForList(
            """
                select participant_role from project_work_item_participants
                 where workspace_id=? and space_id=? and work_item_id=? and user_id=?
                """,
            String.class, workspaceId, spaceId, workItemId, actorId
        ));
    }

    @Override
    public Set<UUID> candidateUserIds(
        UUID workspaceId, UUID spaceId, UUID workItemId, List<String> candidateRoles
    ) {
        if (candidateRoles.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(
            ",", java.util.Collections.nCopies(candidateRoles.size(), "?")
        );
        ArrayList<Object> arguments = new ArrayList<>(List.of(
            workspaceId, spaceId, workspaceId, spaceId, workItemId
        ));
        arguments.addAll(candidateRoles);
        return Set.copyOf(jdbcTemplate.queryForList(
            """
                select distinct candidate_id from (
                    select m.user_id as candidate_id, r.role_key as candidate_role
                      from project_space_members m
                      join project_space_role_assignments r
                        on r.workspace_id=m.workspace_id and r.space_id=m.space_id
                       and r.member_id=m.id
                     where m.workspace_id=? and m.space_id=? and m.status='active'
                    union all
                    select p.user_id as candidate_id, p.participant_role as candidate_role
                      from project_work_item_participants p
                      join project_space_members m
                        on m.workspace_id=p.workspace_id and m.space_id=p.space_id
                       and m.user_id=p.user_id and m.status='active'
                     where p.workspace_id=? and p.space_id=? and p.work_item_id=?
                ) candidates
                 where candidate_role in (
                """ + placeholders + ")",
            UUID.class,
            arguments.toArray()
        ));
    }

    @Override
    public Set<UUID> activeMemberUserIds(
        UUID workspaceId, UUID spaceId, Set<UUID> requestedUserIds
    ) {
        if (requestedUserIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(
            ",", java.util.Collections.nCopies(requestedUserIds.size(), "?")
        );
        ArrayList<Object> arguments = new ArrayList<>(List.of(workspaceId, spaceId));
        arguments.addAll(requestedUserIds);
        return Set.copyOf(jdbcTemplate.queryForList(
            """
                select user_id
                  from project_space_members
                 where workspace_id=? and space_id=? and status='active' and user_id in (
                """ + placeholders + ")",
            UUID.class,
            arguments.toArray()
        ));
    }

    @Override
    public List<NodeTaskInboxItem> taskInbox(
        UUID workspaceId, UUID spaceId, UUID actorId, boolean includeAll, UUID cursor, int limit
    ) {
        return jdbcTemplate.query(
            """
                select task.id, instance.work_item_id, item.title, task.node_key, task.status,
                       task.assignee_id, task.due_at, task.created_at
                  from project_node_workflow_tasks task
                  join project_node_workflow_instances instance
                    on instance.workspace_id=task.workspace_id
                   and instance.space_id=task.space_id and instance.id=task.instance_id
                  join project_work_items item
                    on item.workspace_id=instance.workspace_id
                   and item.space_id=instance.space_id and item.id=instance.work_item_id
                 where task.workspace_id=? and task.space_id=?
                   and task.status in ('pending','claimed')
                   and (? or task.assignee_id=? or task.candidate_user_ids @> ?::jsonb)
                   and (
                       ?::uuid is null
                       or (task.created_at, task.id) > (
                           select cursor_task.created_at, cursor_task.id
                             from project_node_workflow_tasks cursor_task
                            where cursor_task.workspace_id=? and cursor_task.space_id=?
                              and cursor_task.id=?::uuid
                       )
                   )
                 order by task.created_at, task.id
                 limit ?
                """,
            (row, number) -> new NodeTaskInboxItem(
                row.getObject("id", UUID.class),
                row.getObject("work_item_id", UUID.class),
                row.getString("title"),
                row.getString("node_key"),
                row.getString("status"),
                row.getObject("assignee_id", UUID.class),
                instant(row, "due_at"),
                row.getTimestamp("created_at").toInstant()
            ),
            workspaceId, spaceId, includeAll, actorId,
            json(objectMapper.valueToTree(List.of(actorId))), cursor,
            workspaceId, spaceId, cursor, limit
        );
    }

    @Override
    public List<DueNodeTask> markDueTasksTimedOut(
        UUID workspaceId, UUID spaceId, java.time.Instant now, int limit
    ) {
        return jdbcTemplate.query(
            """
                with due as (
                    select task.id
                      from project_node_workflow_tasks task
                     where task.workspace_id=? and task.space_id=?
                       and task.status in ('pending','claimed')
                       and task.due_at is not null and task.due_at<=?
                       and task.timed_out_at is null
                     order by task.due_at, task.id
                     for update skip locked
                     limit ?
                )
                update project_node_workflow_tasks task
                   set timed_out_at=?, escalated_at=coalesce(task.escalated_at, ?),
                       aggregate_version=task.aggregate_version+1
                  from due, project_node_workflow_instances instance
                 where task.id=due.id
                   and instance.workspace_id=task.workspace_id
                   and instance.space_id=task.space_id and instance.id=task.instance_id
                returning task.id, task.instance_id, instance.work_item_id,
                          task.node_key, task.due_at
                """,
            (row, number) -> new DueNodeTask(
                row.getObject("id", UUID.class),
                row.getObject("instance_id", UUID.class),
                row.getObject("work_item_id", UUID.class),
                row.getString("node_key"),
                row.getTimestamp("due_at").toInstant()
            ),
            workspaceId, spaceId, timestamp(now), limit, timestamp(now), timestamp(now)
        );
    }

    private NodeWorkflowInstance mapInstance(ResultSet row, int number) throws SQLException {
        return new NodeWorkflowInstance(
            row.getObject("id", UUID.class),
            row.getObject("workspace_id", UUID.class),
            row.getObject("space_id", UUID.class),
            row.getObject("work_item_id", UUID.class),
            row.getObject("type_definition_id", UUID.class),
            row.getObject("type_version_id", UUID.class),
            row.getString("config_hash"),
            row.getString("status"),
            row.getLong("work_item_version"),
            row.getLong("aggregate_version"),
            row.getTimestamp("started_at").toInstant(),
            row.getTimestamp("updated_at").toInstant(),
            instant(row, "completed_at")
        );
    }

    private NodeToken mapToken(ResultSet row, int number) throws SQLException {
        return new NodeToken(
            row.getObject("id", UUID.class),
            row.getObject("instance_id", UUID.class),
            row.getString("node_key"),
            row.getString("stage_key"),
            row.getString("status"),
            row.getObject("parent_token_id", UUID.class),
            row.getString("split_key"),
            row.getString("join_key"),
            row.getString("correlation_key"),
            row.getLong("aggregate_version"),
            row.getTimestamp("entered_at").toInstant(),
            instant(row, "completed_at")
        );
    }

    private NodeTask mapTask(ResultSet row, int number) throws SQLException {
        return new NodeTask(
            row.getObject("id", UUID.class),
            row.getObject("instance_id", UUID.class),
            row.getObject("token_id", UUID.class),
            row.getString("node_key"),
            row.getString("assignment_strategy"),
            strings(row.getString("candidate_roles")),
            uuids(row.getString("candidate_user_ids")),
            (Integer) row.getObject("quorum_count"),
            parse(row.getString("form_snapshot")),
            parse(row.getString("artifact_policy_snapshot")),
            row.getString("status"),
            row.getObject("assignee_id", UUID.class),
            row.getLong("aggregate_version"),
            row.getTimestamp("created_at").toInstant(),
            instant(row, "planned_start_at"),
            instant(row, "due_at"),
            instant(row, "timed_out_at"),
            instant(row, "escalated_at"),
            instant(row, "claimed_at"),
            instant(row, "completed_at")
        );
    }

    private NodeJoin mapJoin(ResultSet row, int number) throws SQLException {
        return new NodeJoin(
            row.getObject("id", UUID.class),
            row.getObject("instance_id", UUID.class),
            row.getString("join_key"),
            row.getString("node_key"),
            row.getString("correlation_key"),
            row.getString("policy"),
            row.getInt("expected_count"),
            (Integer) row.getObject("quorum_count"),
            row.getInt("arrived_count"),
            row.getString("status"),
            row.getLong("aggregate_version")
        );
    }

    private java.time.Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private java.sql.Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : java.sql.Timestamp.from(value);
    }

    private List<String> strings(String value) {
        JsonNode parsed = parse(value);
        ArrayList<String> result = new ArrayList<>();
        parsed.forEach(item -> result.add(item.asText()));
        return List.copyOf(result);
    }

    private List<UUID> uuids(String value) {
        try {
            ArrayList<UUID> result = new ArrayList<>();
            objectMapper.readTree(value).forEach(node -> result.add(UUID.fromString(node.asText())));
            return List.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read node workflow UUID list", exception);
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid node workflow JSON", exception);
        }
    }

    private JsonNode parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored node workflow JSON is invalid", exception);
        }
    }
}
