package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublicationCommandReceipt;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import com.colla.platform.modules.project.runtime.PublishedSnapshotReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConfigurationPublicationRepository
    implements ConfigurationPublicationRepository, PublishedSnapshotReader {
    private static final String VERSION_SELECT = """
        select id, workspace_id, space_id, type_definition_id, version_number,
               status, snapshot_schema_version, config_hash, config,
               source_draft_id, rollback_source_version_id, published_by, published_at
          from project_work_item_type_versions
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcConfigurationPublicationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LockedType> lockType(UUID workspaceId, UUID spaceId, UUID typeId) {
        try {
            LockedType locked = jdbcTemplate.queryForObject(
                """
                    select current_version_id, aggregate_version
                      from project_work_item_types
                     where workspace_id = ? and space_id = ? and id = ?
                     for update
                    """,
                (resultSet, rowNumber) -> new LockedType(
                    resultSet.getObject("current_version_id", UUID.class),
                    resultSet.getLong("aggregate_version"),
                    0
                ),
                workspaceId,
                spaceId,
                typeId
            );
            Integer next = jdbcTemplate.queryForObject(
                """
                    select coalesce(max(version_number), 0) + 1
                      from project_work_item_type_versions
                     where workspace_id = ? and space_id = ? and type_definition_id = ?
                    """,
                Integer.class,
                workspaceId,
                spaceId,
                typeId
            );
            return Optional.of(new LockedType(
                locked.currentVersionId(),
                locked.aggregateVersion(),
                next == null ? 1 : next
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PublishedConfigurationVersion> findVersion(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                VERSION_SELECT + """
                     where workspace_id = ? and space_id = ? and type_definition_id = ? and id = ?
                       and status in ('published', 'superseded')
                    """,
                this::mapVersion,
                workspaceId,
                spaceId,
                typeId,
                versionId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PublishedConfigurationVersion> findPublishedSnapshot(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    ) {
        return findVersion(workspaceId, spaceId, typeId, versionId);
    }

    @Override
    public List<PublishedConfigurationVersion> findPublishedSnapshots(
        UUID workspaceId,
        UUID spaceId,
        List<UUID> versionIds
    ) {
        List<UUID> distinctVersionIds = versionIds == null
            ? List.of()
            : versionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctVersionIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(
            ", ",
            Collections.nCopies(distinctVersionIds.size(), "?")
        );
        List<Object> arguments = new ArrayList<>(distinctVersionIds.size() + 2);
        arguments.add(workspaceId);
        arguments.add(spaceId);
        arguments.addAll(distinctVersionIds);
        return jdbcTemplate.query(
            VERSION_SELECT + """
                 where workspace_id = ? and space_id = ?
                   and status in ('published', 'superseded')
                   and id in (""" + placeholders + ")",
            this::mapVersion,
            arguments.toArray()
        );
    }

    @Override
    public List<PublishedConfigurationVersion> listVersions(UUID workspaceId, UUID spaceId, UUID typeId) {
        return jdbcTemplate.query(
            VERSION_SELECT + """
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                 order by version_number desc, id
                """,
            this::mapVersion,
            workspaceId,
            spaceId,
            typeId
        );
    }

    @Override
    public void insertPublished(NewPublishedVersion version) {
        jdbcTemplate.update(
            """
                insert into project_work_item_type_versions (
                    id, workspace_id, space_id, type_definition_id, version_number,
                    config_hash, status, config, created_by, created_at,
                    published_by, published_at, snapshot_schema_version,
                    source_draft_id, rollback_source_version_id
                )
                values (?, ?, ?, ?, ?, ?, 'published', ?::jsonb, ?, now(), ?, now(), ?, ?, ?)
                """,
            version.id(),
            version.workspaceId(),
            version.spaceId(),
            version.typeDefinitionId(),
            version.versionNumber(),
            version.configHash(),
            json(version.snapshot()),
            version.actorId(),
            version.actorId(),
            version.snapshotSchemaVersion(),
            version.sourceDraftId(),
            version.rollbackSourceVersionId()
        );
    }

    @Override
    public int supersede(UUID workspaceId, UUID spaceId, UUID typeId, UUID versionId) {
        return jdbcTemplate.update(
            """
                update project_work_item_type_versions
                   set status = 'superseded'
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and id = ? and status = 'published'
                """,
            workspaceId,
            spaceId,
            typeId,
            versionId
        );
    }

    @Override
    public int switchCurrent(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID expectedCurrentVersionId,
        UUID nextVersionId,
        UUID actorId
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_types
                   set current_version_id = ?, updated_by = ?, updated_at = now(),
                       aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and id = ?
                   and current_version_id = ?
                """,
            nextVersionId,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            expectedCurrentVersionId
        );
    }

    @Override
    public boolean tryStartCommand(PublicationCommandStart command) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_configuration_publication_commands (
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
    public Optional<PublicationCommandReceipt> findCommand(
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
                           response_schema_version, response_version_id,
                           response_version_number, response_config_hash,
                           response_payload, created_by, created_at, completed_at
                      from project_work_item_configuration_publication_commands
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
    public void completeCommand(UUID commandId, PublicationCommandResponse response) {
        int updated = jdbcTemplate.update(
            """
                update project_work_item_configuration_publication_commands
                   set status = 'completed', response_version_id = ?,
                       response_version_number = ?, response_config_hash = ?,
                       response_payload = ?::jsonb, completed_at = now()
                 where id = ? and status = 'pending'
                """,
            response.versionId(),
            response.versionNumber(),
            response.configHash(),
            json(response.payload()),
            commandId
        );
        if (updated != 1) {
            throw new IllegalStateException("Configuration publication command could not be completed");
        }
    }

    private PublishedConfigurationVersion mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublishedConfigurationVersion(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getInt("version_number"),
            resultSet.getString("status"),
            resultSet.getInt("snapshot_schema_version"),
            resultSet.getString("config_hash"),
            parse(resultSet.getString("config")),
            resultSet.getObject("source_draft_id", UUID.class),
            resultSet.getObject("rollback_source_version_id", UUID.class),
            resultSet.getObject("published_by", UUID.class),
            resultSet.getTimestamp("published_at").toInstant()
        );
    }

    private PublicationCommandReceipt mapReceipt(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp completedAt = resultSet.getTimestamp("completed_at");
        return new PublicationCommandReceipt(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getString("request_id"),
            resultSet.getString("operation"),
            resultSet.getString("request_hash"),
            resultSet.getString("status"),
            resultSet.getInt("response_schema_version"),
            resultSet.getObject("response_version_id", UUID.class),
            resultSet.getObject("response_version_number", Integer.class),
            resultSet.getString("response_config_hash"),
            parseNullable(resultSet.getString("response_payload")),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            completedAt == null ? null : completedAt.toInstant()
        );
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid configuration publication JSON", exception);
        }
    }

    private JsonNode parse(String value) throws SQLException {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid published configuration JSON", exception);
        }
    }

    private JsonNode parseNullable(String value) throws SQLException {
        return value == null ? null : parse(value);
    }
}
