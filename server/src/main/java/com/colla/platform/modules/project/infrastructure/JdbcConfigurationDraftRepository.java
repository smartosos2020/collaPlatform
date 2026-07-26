package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDraft;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DraftCommandReceipt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConfigurationDraftRepository implements ConfigurationDraftRepository {
    private static final String DRAFT_SELECT = """
        select id, workspace_id, space_id, type_definition_id, status,
               snapshot_schema_version, config_hash, snapshot, diagnostics,
               aggregate_version, source_legacy_version_id, source_version_id, lineage_kind,
               created_by, created_at, updated_by, updated_at
          from project_work_item_configuration_drafts
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcConfigurationDraftRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ConfigurationDraft> findActive(UUID workspaceId, UUID spaceId, UUID typeId) {
        return queryDraft(
            DRAFT_SELECT + """
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and status in ('editing', 'validating', 'valid', 'invalid')
                """,
            workspaceId,
            spaceId,
            typeId
        );
    }

    @Override
    public Optional<ConfigurationDraft> lockActive(UUID workspaceId, UUID spaceId, UUID typeId) {
        return queryDraft(
            DRAFT_SELECT + """
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and status in ('editing', 'validating', 'valid', 'invalid')
                 for update
                """,
            workspaceId,
            spaceId,
            typeId
        );
    }

    @Override
    public Optional<ConfigurationDraft> findById(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID draftId
    ) {
        return queryDraft(
            DRAFT_SELECT + """
                 where workspace_id = ? and space_id = ? and type_definition_id = ? and id = ?
                """,
            workspaceId,
            spaceId,
            typeId,
            draftId
        );
    }

    @Override
    public boolean tryInsert(NewDraft draft) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_configuration_drafts (
                    id, workspace_id, space_id, type_definition_id, status,
                    snapshot_schema_version, config_hash, snapshot, diagnostics,
                    aggregate_version, source_version_id, lineage_kind,
                    created_by, created_at, updated_by, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, 0, ?, ?, ?, now(), ?, now())
                on conflict do nothing
                """,
            draft.id(),
            draft.workspaceId(),
            draft.spaceId(),
            draft.typeDefinitionId(),
            draft.status(),
            draft.snapshotSchemaVersion(),
            draft.configHash(),
            json(draft.snapshot()),
            json(draft.diagnostics()),
            draft.sourceVersionId(),
            draft.lineageKind(),
            draft.actorId(),
            draft.actorId()
        ) == 1;
    }

    @Override
    public int update(UpdateDraft draft) {
        return jdbcTemplate.update(
            """
                update project_work_item_configuration_drafts
                   set status = ?, snapshot_schema_version = ?, config_hash = ?,
                       snapshot = ?::jsonb, diagnostics = ?::jsonb,
                       updated_by = ?, updated_at = now(),
                       aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and id = ? and aggregate_version = ?
                   and status in ('editing', 'validating', 'valid', 'invalid')
                """,
            draft.status(),
            draft.snapshotSchemaVersion(),
            draft.configHash(),
            json(draft.snapshot()),
            json(draft.diagnostics()),
            draft.actorId(),
            draft.workspaceId(),
            draft.spaceId(),
            draft.typeDefinitionId(),
            draft.draftId(),
            draft.expectedAggregateVersion()
        );
    }

    @Override
    public int abandon(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID draftId,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_configuration_drafts
                   set status = 'abandoned', updated_by = ?, updated_at = now(),
                       aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and id = ? and aggregate_version = ?
                   and status in ('editing', 'validating', 'valid', 'invalid')
                """,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            draftId,
            expectedAggregateVersion
        );
    }

    @Override
    public boolean tryStartCommand(DraftCommandStart command) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_configuration_draft_commands (
                    id, workspace_id, space_id, type_definition_id, request_id,
                    operation, request_hash, status, response_schema_version,
                    created_by, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, 'pending', 1, ?, now())
                on conflict do nothing
                """,
            command.id(),
            command.workspaceId(),
            command.spaceId(),
            command.typeDefinitionId(),
            command.requestId(),
            command.operation(),
            command.requestHash(),
            command.actorId()
        ) == 1;
    }

    @Override
    public Optional<DraftCommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String operation,
        String requestId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, workspace_id, space_id, type_definition_id,
                           request_id, operation, request_hash, status,
                           response_schema_version, response_draft_id,
                           response_aggregate_version, response_config_hash,
                           response_payload, created_by, created_at, completed_at
                      from project_work_item_configuration_draft_commands
                     where workspace_id = ? and space_id = ? and type_definition_id = ?
                       and operation = ? and request_id = ?
                    """,
                this::mapReceipt,
                workspaceId,
                spaceId,
                typeId,
                operation,
                requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void completeCommand(UUID commandId, DraftCommandResponse response) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_configuration_draft_commands
                   set status = 'completed',
                       response_draft_id = ?,
                       response_aggregate_version = ?,
                       response_config_hash = ?,
                       response_payload = ?::jsonb,
                       completed_at = now()
                 where id = ? and status = 'pending'
                   and response_draft_id is null and response_payload is null
                """,
            response.draftId(),
            response.aggregateVersion(),
            response.configHash(),
            json(response.payload()),
            commandId
        );
        if (updated != 1) {
            throw new IllegalStateException("Configuration draft command could not be completed");
        }
    }

    private Optional<ConfigurationDraft> queryDraft(String sql, Object... arguments) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapDraft, arguments));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private ConfigurationDraft mapDraft(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            List<ConfigurationDiagnostic> diagnostics = objectMapper.readValue(
                resultSet.getString("diagnostics"),
                new TypeReference<>() {
                }
            );
            return new ConfigurationDraft(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("workspace_id", UUID.class),
                resultSet.getObject("space_id", UUID.class),
                resultSet.getObject("type_definition_id", UUID.class),
                resultSet.getString("status"),
                resultSet.getInt("snapshot_schema_version"),
                resultSet.getString("config_hash"),
                objectMapper.readTree(resultSet.getString("snapshot")),
                diagnostics,
                resultSet.getLong("aggregate_version"),
                resultSet.getObject("source_legacy_version_id", UUID.class),
                resultSet.getObject("source_version_id", UUID.class),
                resultSet.getString("lineage_kind"),
                resultSet.getObject("created_by", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getObject("updated_by", UUID.class),
                resultSet.getTimestamp("updated_at").toInstant()
            );
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid configuration draft JSON stored", exception);
        }
    }

    private DraftCommandReceipt mapReceipt(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp completedAt = resultSet.getTimestamp("completed_at");
        String payload = resultSet.getString("response_payload");
        return new DraftCommandReceipt(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getString("request_id"),
            resultSet.getString("operation"),
            resultSet.getString("request_hash"),
            resultSet.getString("status"),
            resultSet.getInt("response_schema_version"),
            resultSet.getObject("response_draft_id", UUID.class),
            resultSet.getObject("response_aggregate_version", Long.class),
            resultSet.getString("response_config_hash"),
            parseNullable(payload),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            completedAt == null ? null : completedAt.toInstant()
        );
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid configuration draft JSON", exception);
        }
    }

    private JsonNode parseNullable(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid configuration draft command response stored", exception);
        }
    }
}
