package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.CurrentState;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowHistoryEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemStateFlowRepository implements WorkItemStateFlowRepository {
    private static final String CURRENT_SELECT = """
        select workspace_id, space_id, work_item_id, type_definition_id, type_version_id,
               config_hash, current_state_key, work_item_version, aggregate_version,
               initialized_at, updated_at
          from project_work_item_current_states
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemStateFlowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean tryInitialize(CurrentStateInsert value) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_current_states (
                    workspace_id, space_id, work_item_id, type_definition_id, type_version_id,
                    config_hash, current_state_key, work_item_version, aggregate_version,
                    initialized_by, initialized_at, updated_by, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, now(), ?, now())
                on conflict (workspace_id, space_id, work_item_id) do nothing
                """,
            value.workspaceId(), value.spaceId(), value.workItemId(), value.typeDefinitionId(),
            value.typeVersionId(), value.configHash(), value.stateKey(), value.workItemVersion(),
            value.actorId(), value.actorId()
        ) == 1;
    }

    @Override
    public Optional<CurrentState> findCurrent(UUID workspaceId, UUID spaceId, UUID workItemId) {
        return current(workspaceId, spaceId, workItemId, false);
    }

    @Override
    public Optional<CurrentState> lockCurrent(UUID workspaceId, UUID spaceId, UUID workItemId) {
        return current(workspaceId, spaceId, workItemId, true);
    }

    private Optional<CurrentState> current(UUID workspaceId, UUID spaceId, UUID workItemId, boolean lock) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                CURRENT_SELECT + " where workspace_id=? and space_id=? and work_item_id=?"
                    + (lock ? " for update" : ""),
                this::mapCurrent,
                workspaceId, spaceId, workItemId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Map<UUID, CurrentState> findCurrentBatch(
        UUID workspaceId, UUID spaceId, List<UUID> workItemIds
    ) {
        if (workItemIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(workItemIds.size(), "?"));
        ArrayList<Object> arguments = new ArrayList<>();
        arguments.add(workspaceId);
        arguments.add(spaceId);
        arguments.addAll(workItemIds);
        HashMap<UUID, CurrentState> result = new HashMap<>();
        jdbcTemplate.query(
            CURRENT_SELECT + " where workspace_id=? and space_id=? and work_item_id in (" + placeholders + ")",
            row -> {
                CurrentState value = mapCurrent(row, 0);
                result.put(value.workItemId(), value);
            },
            arguments.toArray()
        );
        return Map.copyOf(result);
    }

    @Override
    public int compareAndSetState(
        UUID workspaceId, UUID spaceId, UUID workItemId, String expectedStateKey,
        String targetStateKey, long expectedWorkItemVersion, long targetWorkItemVersion,
        long expectedAggregateVersion, UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_current_states
                   set current_state_key=?, work_item_version=?, aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and work_item_id=?
                   and current_state_key=? and work_item_version=? and aggregate_version=?
                """,
            targetStateKey, targetWorkItemVersion, actorId, workspaceId, spaceId, workItemId,
            expectedStateKey, expectedWorkItemVersion, expectedAggregateVersion
        );
    }

    @Override
    public int alignWorkItemVersion(
        UUID workspaceId, UUID spaceId, UUID workItemId, long expectedWorkItemVersion,
        long targetWorkItemVersion, UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_current_states
                   set work_item_version=?, updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and work_item_id=?
                   and work_item_version=?
                """,
            targetWorkItemVersion, actorId, workspaceId, spaceId, workItemId, expectedWorkItemVersion
        );
    }

    @Override
    public int upgradeBinding(
        UUID workspaceId, UUID spaceId, UUID workItemId, UUID expectedTypeVersionId,
        String expectedConfigHash, String expectedStateKey, UUID targetTypeVersionId,
        String targetConfigHash, String targetStateKey, long expectedWorkItemVersion,
        long targetWorkItemVersion, long expectedAggregateVersion, UUID actorId
    ) {
        jdbcTemplate.queryForObject(
            "select set_config('colla.workflow_binding_upgrade', 'on', true)",
            String.class
        );
        return jdbcTemplate.update(
            """
                update project_work_item_current_states
                   set type_version_id=?, config_hash=?, current_state_key=?,
                       work_item_version=?, aggregate_version=aggregate_version+1,
                       updated_by=?, updated_at=now()
                 where workspace_id=? and space_id=? and work_item_id=?
                   and type_version_id=? and config_hash=? and current_state_key=?
                   and work_item_version=? and aggregate_version=?
                """,
            targetTypeVersionId, targetConfigHash, targetStateKey, targetWorkItemVersion,
            actorId, workspaceId, spaceId, workItemId, expectedTypeVersionId,
            expectedConfigHash, expectedStateKey, expectedWorkItemVersion,
            expectedAggregateVersion
        );
    }

    @Override
    public boolean tryStartCommand(CommandStart command) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_workflow_commands (
                    id, workspace_id, space_id, work_item_id, operation, action_key,
                    from_state_key, expected_work_item_version, request_id, request_hash,
                    status, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, now())
                on conflict (workspace_id, work_item_id, operation, request_id) do nothing
                """,
            command.id(), command.workspaceId(), command.spaceId(), command.workItemId(),
            command.operation(), command.actionKey(), command.fromStateKey(),
            command.expectedWorkItemVersion(), command.requestId(), command.requestHash(),
            command.actorId()
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
                      from project_work_item_workflow_commands
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
                update project_work_item_workflow_commands
                   set status='completed', response_payload=?::jsonb, completed_at=now()
                 where id=? and status='pending'
                """,
            json(response), commandId
        ) != 1) {
            throw new IllegalStateException("Workflow command receipt could not be completed");
        }
    }

    @Override
    public void appendHistory(HistoryAppend value) {
        jdbcTemplate.update(
            """
                insert into project_work_item_workflow_history (
                    id, workspace_id, space_id, work_item_id, sequence_number, type_definition_id,
                    type_version_id, config_hash, from_state_key, to_state_key, action_key,
                    action_kind, actor_id, actor_class, decision_reference, correlation_id,
                    causation_id, public_payload, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.workItemId(),
            value.sequenceNumber(), value.typeDefinitionId(), value.typeVersionId(),
            value.configHash(), value.fromStateKey(), value.toStateKey(), value.actionKey(),
            value.actionKind(), value.actorId(), value.actorClass(), value.decisionReference(),
            value.correlationId(), value.causationId(), json(value.publicPayload())
        );
    }

    @Override
    public List<WorkflowHistoryEntry> pageHistory(
        UUID workspaceId, UUID spaceId, UUID workItemId, Long beforeSequence, int limit
    ) {
        String before = beforeSequence == null ? "" : " and sequence_number < ?";
        ArrayList<Object> arguments = new ArrayList<>(List.of(workspaceId, spaceId, workItemId));
        if (beforeSequence != null) {
            arguments.add(beforeSequence);
        }
        arguments.add(limit);
        return jdbcTemplate.query(
            """
                select id, sequence_number, from_state_key, to_state_key, action_key,
                       action_kind, actor_id, decision_reference, correlation_id, occurred_at
                  from project_work_item_workflow_history
                 where workspace_id=? and space_id=? and work_item_id=?
                """ + before + " order by sequence_number desc limit ?",
            (row, number) -> new WorkflowHistoryEntry(
                row.getObject("id", UUID.class), row.getLong("sequence_number"),
                row.getString("from_state_key"), row.getString("to_state_key"),
                row.getString("action_key"), row.getString("action_kind"),
                row.getObject("actor_id", UUID.class), row.getString("decision_reference"),
                row.getString("correlation_id"), row.getTimestamp("occurred_at").toInstant()
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
    public Map<UUID, Set<String>> participantRolesBatch(
        UUID workspaceId, UUID spaceId, List<UUID> workItemIds, UUID actorId
    ) {
        if (workItemIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(workItemIds.size(), "?"));
        ArrayList<Object> arguments = new ArrayList<>(List.of(workspaceId, spaceId, actorId));
        arguments.addAll(workItemIds);
        HashMap<UUID, Set<String>> result = new HashMap<>();
        jdbcTemplate.query(
            """
                select work_item_id, participant_role from project_work_item_participants
                 where workspace_id=? and space_id=? and user_id=? and work_item_id in (
                """ + placeholders + ")",
            (org.springframework.jdbc.core.RowCallbackHandler) row ->
                result.computeIfAbsent(row.getObject("work_item_id", UUID.class), ignored -> new HashSet<>())
                    .add(row.getString("participant_role")),
            arguments.toArray()
        );
        result.replaceAll((key, value) -> Set.copyOf(value));
        return Map.copyOf(result);
    }

    private CurrentState mapCurrent(ResultSet row, int number) throws SQLException {
        return new CurrentState(
            row.getObject("workspace_id", UUID.class), row.getObject("space_id", UUID.class),
            row.getObject("work_item_id", UUID.class), row.getObject("type_definition_id", UUID.class),
            row.getObject("type_version_id", UUID.class), row.getString("config_hash"),
            row.getString("current_state_key"), row.getLong("work_item_version"),
            row.getLong("aggregate_version"), row.getTimestamp("initialized_at").toInstant(),
            row.getTimestamp("updated_at").toInstant()
        );
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid workflow JSON", exception);
        }
    }

    private JsonNode parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored workflow JSON is invalid", exception);
        }
    }
}
