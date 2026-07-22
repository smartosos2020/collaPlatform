package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeVersion;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository.WorkItemTypeCounts;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemTypeRepository implements WorkItemTypeRepository {
    private static final String DEFINITION_SELECT = """
        select t.id, t.workspace_id, t.space_id, t.type_key, t.name, t.icon, t.description,
               t.sort_order, t.status, t.is_system, t.current_version_id,
               t.created_by, t.created_at, t.updated_by, t.updated_at, t.aggregate_version,
               v.version_number current_version_number, v.status current_version_status,
               v.config_hash current_config_hash,
               v.config current_config
          from project_work_item_types t
          join project_work_item_type_versions v
            on v.workspace_id = t.workspace_id
           and v.space_id = t.space_id
           and v.type_definition_id = t.id
           and v.id = t.current_version_id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemTypeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<String> findSpaceStatus(UUID workspaceId, UUID spaceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "select status from project_spaces where workspace_id = ? and id = ?",
                String.class,
                workspaceId,
                spaceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorkItemTypeDefinition> findById(UUID workspaceId, UUID spaceId, UUID typeId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                DEFINITION_SELECT + " where t.workspace_id = ? and t.space_id = ? and t.id = ?",
                this::mapDefinition,
                workspaceId,
                spaceId,
                typeId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorkItemTypeDefinition> findByKey(UUID workspaceId, UUID spaceId, String typeKey) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                DEFINITION_SELECT + " where t.workspace_id = ? and t.space_id = ? and t.type_key = ?",
                this::mapDefinition,
                workspaceId,
                spaceId,
                typeKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<WorkItemTypeDefinition> listBySpace(UUID workspaceId, UUID spaceId, String status) {
        return jdbcTemplate.query(
            DEFINITION_SELECT + """
                 where t.workspace_id = ? and t.space_id = ?
                   and (? = '' or t.status = ?)
                 order by t.sort_order, t.type_key, t.id
                """,
            this::mapDefinition,
            workspaceId,
            spaceId,
            status,
            status
        );
    }

    @Override
    public List<WorkItemTypeVersion> listVersions(UUID workspaceId, UUID spaceId, UUID typeId) {
        return jdbcTemplate.query(
            """
                select id, workspace_id, space_id, type_definition_id, version_number,
                       config_hash, status, config, created_by, created_at, published_by, published_at
                  from project_work_item_type_versions
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                 order by version_number desc
                """,
            this::mapVersion,
            workspaceId,
            spaceId,
            typeId
        );
    }

    @Override
    public WorkItemTypeCounts countByStatus(UUID workspaceId, UUID spaceId) {
        return jdbcTemplate.queryForObject(
            """
                select count(*) total,
                       count(*) filter (where status = 'active') active_count,
                       count(*) filter (where status = 'disabled') disabled_count,
                       count(*) filter (where status = 'retired') retired_count
                  from project_work_item_types
                 where workspace_id = ? and space_id = ?
                """,
            (resultSet, rowNumber) -> new WorkItemTypeCounts(
                resultSet.getInt("total"),
                resultSet.getInt("active_count"),
                resultSet.getInt("disabled_count"),
                resultSet.getInt("retired_count")
            ),
            workspaceId,
            spaceId
        );
    }

    @Override
    public Map<UUID, WorkItemTypeCounts> countBySpaces(UUID workspaceId, List<UUID> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(spaceIds.size(), "?"));
        List<Object> arguments = new java.util.ArrayList<>();
        arguments.add(workspaceId);
        arguments.addAll(spaceIds);
        Map<UUID, WorkItemTypeCounts> result = new LinkedHashMap<>();
        RowCallbackHandler collectCounts = resultSet -> result.put(
            resultSet.getObject("space_id", UUID.class),
            new WorkItemTypeCounts(
                resultSet.getInt("total"),
                resultSet.getInt("active_count"),
                resultSet.getInt("disabled_count"),
                resultSet.getInt("retired_count")
            )
        );
        jdbcTemplate.query(
            """
                select space_id, count(*) total,
                       count(*) filter (where status = 'active') active_count,
                       count(*) filter (where status = 'disabled') disabled_count,
                       count(*) filter (where status = 'retired') retired_count
                  from project_work_item_types
                 where workspace_id = ? and space_id in (%s)
                 group by space_id
                """.formatted(placeholders),
            collectCounts,
            arguments.toArray()
        );
        return Map.copyOf(result);
    }

    @Override
    public List<PresetSpaceTarget> listActivePresetSpaces() {
        return jdbcTemplate.query(
            "select workspace_id, id space_id, created_by, status from project_spaces where status = 'active' order by workspace_id, id",
            (resultSet, rowNumber) -> new PresetSpaceTarget(
                resultSet.getObject("workspace_id", UUID.class),
                resultSet.getObject("space_id", UUID.class),
                resultSet.getObject("created_by", UUID.class),
                resultSet.getString("status")
            )
        );
    }

    @Override
    public Optional<PresetSpaceTarget> lockPresetSpace(UUID workspaceId, UUID spaceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "select workspace_id, id space_id, created_by, status from project_spaces where workspace_id = ? and id = ? for update",
                (resultSet, rowNumber) -> new PresetSpaceTarget(
                    resultSet.getObject("workspace_id", UUID.class),
                    resultSet.getObject("space_id", UUID.class),
                    resultSet.getObject("created_by", UUID.class),
                    resultSet.getString("status")
                ),
                workspaceId,
                spaceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void insertDefinition(NewDefinition definition) {
        jdbcTemplate.update(
            """
                insert into project_work_item_types
                    (id, workspace_id, space_id, type_key, name, icon, description, sort_order,
                     status, is_system, current_version_id, created_by, created_at,
                     updated_by, updated_at, aggregate_version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now(), 0)
                """,
            definition.id(),
            definition.workspaceId(),
            definition.spaceId(),
            definition.typeKey(),
            definition.name(),
            definition.icon(),
            definition.description(),
            definition.sortOrder(),
            definition.status(),
            definition.system(),
            definition.currentVersionId(),
            definition.actorId(),
            definition.actorId()
        );
    }

    @Override
    public void insertVersion(NewVersion version) {
        String config;
        try {
            config = objectMapper.writeValueAsString(version.config());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid work item type config", exception);
        }
        jdbcTemplate.update(
            """
                insert into project_work_item_type_versions
                    (id, workspace_id, space_id, type_definition_id, version_number,
                     config_hash, status, config, created_by, created_at, published_by, published_at)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now(), ?, now())
                """,
            version.id(),
            version.workspaceId(),
            version.spaceId(),
            version.typeDefinitionId(),
            version.versionNumber(),
            version.configHash(),
            version.status(),
            config,
            version.actorId(),
            version.actorId()
        );
    }

    @Override
    public int updateDisplay(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String name,
        String icon,
        String description,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_types
                   set name = ?, icon = ?, description = ?, updated_by = ?, updated_at = now(),
                       aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and id = ? and aggregate_version = ?
                """,
            name,
            icon,
            description,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            expectedAggregateVersion
        );
    }

    @Override
    public int transitionStatus(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String targetStatus,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_types
                   set status = ?, updated_by = ?, updated_at = now(), aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and id = ? and aggregate_version = ?
                """,
            targetStatus,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            expectedAggregateVersion
        );
    }

    @Override
    public int updateSortOrder(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        int sortOrder,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_types
                   set sort_order = ?, updated_by = ?, updated_at = now(), aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and id = ? and aggregate_version = ?
                """,
            sortOrder,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            expectedAggregateVersion
        );
    }

    private WorkItemTypeDefinition mapDefinition(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkItemTypeDefinition(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getString("type_key"),
            resultSet.getString("name"),
            resultSet.getString("icon"),
            resultSet.getString("description"),
            resultSet.getInt("sort_order"),
            resultSet.getString("status"),
            resultSet.getBoolean("is_system"),
            resultSet.getObject("current_version_id", UUID.class),
            resultSet.getInt("current_version_number"),
            resultSet.getString("current_version_status"),
            resultSet.getString("current_config_hash"),
            json(resultSet.getString("current_config")),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getObject("updated_by", UUID.class),
            resultSet.getTimestamp("updated_at").toInstant(),
            resultSet.getLong("aggregate_version")
        );
    }

    private WorkItemTypeVersion mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkItemTypeVersion(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getInt("version_number"),
            resultSet.getString("config_hash"),
            resultSet.getString("status"),
            json(resultSet.getString("config")),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getObject("published_by", UUID.class),
            resultSet.getTimestamp("published_at").toInstant()
        );
    }

    private JsonNode json(String value) throws SQLException {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid JSON stored for work item type", exception);
        }
    }
}
