package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository.NewFieldDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemFieldRepository implements WorkItemFieldRepository {
    private static final String SELECT = """
        select id, workspace_id, space_id, type_definition_id, field_key, name, description,
               field_type, config, config_hash, sort_order, status, is_system,
               created_by, created_at, updated_by, updated_at, aggregate_version
          from project_work_item_field_definitions
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemFieldRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<FieldDefinition> findById(UUID workspaceId, UUID spaceId, UUID typeId, UUID fieldId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                SELECT + " where workspace_id = ? and space_id = ? and type_definition_id = ? and id = ?",
                this::map,
                workspaceId,
                spaceId,
                typeId,
                fieldId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<FieldDefinition> listByType(UUID workspaceId, UUID spaceId, UUID typeId, String status) {
        return jdbcTemplate.query(
            SELECT + """
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and (? = '' or status = ?)
                 order by sort_order, field_key, id
                """,
            this::map,
            workspaceId,
            spaceId,
            typeId,
            status,
            status
        );
    }

    @Override
    public void insert(NewFieldDefinition definition) {
        jdbcTemplate.update(
            """
                insert into project_work_item_field_definitions
                    (id, workspace_id, space_id, type_definition_id, field_key, name, description,
                     field_type, config, config_hash, sort_order, status, is_system,
                     created_by, created_at, updated_by, updated_at, aggregate_version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, now(), ?, now(), 0)
                """,
            definition.id(),
            definition.workspaceId(),
            definition.spaceId(),
            definition.typeDefinitionId(),
            definition.fieldKey(),
            definition.name(),
            definition.description(),
            definition.fieldType(),
            json(definition.config()),
            definition.configHash(),
            definition.sortOrder(),
            definition.status(),
            definition.system(),
            definition.actorId(),
            definition.actorId()
        );
    }

    @Override
    public int update(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String name,
        String description,
        JsonNode config,
        String configHash,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_field_definitions
                   set name = ?, description = ?, config = ?::jsonb, config_hash = ?,
                       updated_by = ?, updated_at = now(), aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and type_definition_id = ? and id = ?
                   and aggregate_version = ?
                """,
            name,
            description,
            json(config),
            configHash,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            fieldId,
            expectedAggregateVersion
        );
    }

    @Override
    public int transitionStatus(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String status,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_field_definitions
                   set status = ?, updated_by = ?, updated_at = now(), aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and type_definition_id = ? and id = ?
                   and aggregate_version = ?
                """,
            status,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            fieldId,
            expectedAggregateVersion
        );
    }

    @Override
    public int updateSortOrder(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        int sortOrder,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_field_definitions
                   set sort_order = ?, updated_by = ?, updated_at = now(), aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and type_definition_id = ? and id = ?
                   and aggregate_version = ?
                """,
            sortOrder,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            fieldId,
            expectedAggregateVersion
        );
    }

    private FieldDefinition map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FieldDefinition(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getString("field_key"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            resultSet.getString("field_type"),
            parse(resultSet.getString("config")),
            resultSet.getString("config_hash"),
            resultSet.getInt("sort_order"),
            resultSet.getString("status"),
            resultSet.getBoolean("is_system"),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getObject("updated_by", UUID.class),
            resultSet.getTimestamp("updated_at").toInstant(),
            resultSet.getLong("aggregate_version")
        );
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid work item field config", exception);
        }
    }

    private JsonNode parse(String value) throws SQLException {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid work item field config stored", exception);
        }
    }
}
