package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDefinition;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
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
public class JdbcWorkItemLayoutRepository implements WorkItemLayoutRepository {
    private static final String LAYOUT_SELECT = """
        select id, workspace_id, space_id, type_definition_id, layout_kind, config_hash, status,
               created_by, created_at, updated_by, updated_at, aggregate_version
          from project_work_item_layouts
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemLayoutRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LayoutDefinition> findByKind(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String layoutKind
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                LAYOUT_SELECT + """
                     where workspace_id = ? and space_id = ? and type_definition_id = ?
                       and layout_kind = ? and status = 'active'
                    """,
                this::mapLayout,
                workspaceId,
                spaceId,
                typeId,
                layoutKind
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<LayoutDefinition> findAnyByKind(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String layoutKind
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                LAYOUT_SELECT + """
                     where workspace_id = ? and space_id = ? and type_definition_id = ?
                       and layout_kind = ?
                    """,
                this::mapLayout,
                workspaceId,
                spaceId,
                typeId,
                layoutKind
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<LayoutDefinition> findById(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID layoutId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                LAYOUT_SELECT + """
                     where workspace_id = ? and space_id = ? and type_definition_id = ?
                       and id = ? and status = 'active'
                    """,
                this::mapLayout,
                workspaceId,
                spaceId,
                typeId,
                layoutId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<LayoutNode> listNodes(UUID workspaceId, UUID layoutId) {
        return listNodes(workspaceId, layoutId, true);
    }

    @Override
    public List<LayoutNode> listAllNodes(UUID workspaceId, UUID layoutId) {
        return listNodes(workspaceId, layoutId, false);
    }

    private List<LayoutNode> listNodes(UUID workspaceId, UUID layoutId, boolean activeOnly) {
        return jdbcTemplate.query(
            """
                select id, parent_id, node_key, node_type, field_id, field_key, sort_order,
                       config, visibility_condition
                  from project_work_item_layout_nodes
                 where workspace_id = ? and layout_id = ?
                   and (? = false or status = 'active')
                 order by sort_order, node_key, id
                """,
            this::mapNode,
            workspaceId,
            layoutId,
            activeOnly
        );
    }

    @Override
    public List<FieldAccessPolicy> listPolicies(UUID workspaceId, UUID layoutId) {
        return listPolicies(workspaceId, layoutId, true);
    }

    @Override
    public List<FieldAccessPolicy> listAllPolicies(UUID workspaceId, UUID layoutId) {
        return listPolicies(workspaceId, layoutId, false);
    }

    private List<FieldAccessPolicy> listPolicies(UUID workspaceId, UUID layoutId, boolean activeOnly) {
        return jdbcTemplate.query(
            """
                select id, field_id, field_key, policy_key, policy, config_hash
                  from project_work_item_field_access_policies
                 where workspace_id = ? and layout_id = ?
                   and (? = false or status = 'active')
                 order by policy_key, id
                """,
            this::mapPolicy,
            workspaceId,
            layoutId,
            activeOnly
        );
    }

    @Override
    public void insertLayout(LayoutDefinitionInsert definition) {
        jdbcTemplate.update(
            """
                insert into project_work_item_layouts
                    (id, workspace_id, space_id, type_definition_id, layout_kind, config_hash,
                     status, created_by, created_at, updated_by, updated_at, aggregate_version)
                values (?, ?, ?, ?, ?, ?, 'active', ?, now(), ?, now(), 0)
                """,
            definition.id(),
            definition.workspaceId(),
            definition.spaceId(),
            definition.typeDefinitionId(),
            definition.layoutKind(),
            definition.configHash(),
            definition.actorId(),
            definition.actorId()
        );
    }

    @Override
    public int updateLayout(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID layoutId,
        String configHash,
        UUID actorId,
        long expectedAggregateVersion
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_layouts
                   set config_hash = ?, updated_by = ?, updated_at = now(),
                       aggregate_version = aggregate_version + 1
                 where workspace_id = ? and space_id = ? and type_definition_id = ? and id = ?
                   and status = 'active' and aggregate_version = ?
                """,
            configHash,
            actorId,
            workspaceId,
            spaceId,
            typeId,
            layoutId,
            expectedAggregateVersion
        );
    }

    @Override
    public void replaceNodes(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID layoutId,
        List<LayoutNode> nodes,
        UUID actorId
    ) {
        jdbcTemplate.update(
            """
                update project_work_item_layout_nodes
                   set status = 'removed', updated_by = ?, updated_at = now()
                 where workspace_id = ? and layout_id = ? and status = 'active'
                """,
            actorId,
            workspaceId,
            layoutId
        );
        for (LayoutNode node : nodes) {
            jdbcTemplate.update(
                """
                    insert into project_work_item_layout_nodes
                        (id, workspace_id, space_id, type_definition_id, layout_id, node_key,
                         parent_id, node_type, field_id, field_key, sort_order, config,
                         visibility_condition, status, created_by, created_at, updated_by, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb,
                            'active', ?, now(), ?, now())
                    on conflict (id) do update
                       set parent_id = excluded.parent_id, sort_order = excluded.sort_order,
                           config = excluded.config,
                           visibility_condition = excluded.visibility_condition,
                           status = 'active', updated_by = excluded.updated_by, updated_at = now()
                    """,
                node.id(),
                workspaceId,
                spaceId,
                typeId,
                layoutId,
                node.nodeKey(),
                node.parentId(),
                node.nodeType(),
                node.fieldId(),
                node.fieldKey(),
                node.sortOrder(),
                json(node.config()),
                json(node.visibilityCondition()),
                actorId,
                actorId
            );
        }
    }

    @Override
    public void replacePolicies(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID layoutId,
        List<FieldAccessPolicy> policies,
        UUID actorId
    ) {
        jdbcTemplate.update(
            """
                update project_work_item_field_access_policies
                   set status = 'removed', updated_by = ?, updated_at = now()
                 where workspace_id = ? and layout_id = ? and status = 'active'
                """,
            actorId,
            workspaceId,
            layoutId
        );
        for (FieldAccessPolicy policy : policies) {
            jdbcTemplate.update(
                """
                    insert into project_work_item_field_access_policies
                        (id, workspace_id, space_id, type_definition_id, layout_id, field_id,
                         field_key, policy_key, policy, config_hash, status, created_by,
                         created_at, updated_by, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'active', ?, now(), ?, now())
                    on conflict (id) do update
                       set policy = excluded.policy, config_hash = excluded.config_hash,
                           status = 'active', updated_by = excluded.updated_by, updated_at = now()
                    """,
                policy.id(),
                workspaceId,
                spaceId,
                typeId,
                layoutId,
                policy.fieldId(),
                policy.fieldKey(),
                policy.policyKey(),
                json(policy.policy()),
                policy.configHash(),
                actorId,
                actorId
            );
        }
    }

    private LayoutDefinition mapLayout(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LayoutDefinition(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getString("layout_kind"),
            resultSet.getString("config_hash"),
            resultSet.getString("status"),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getObject("updated_by", UUID.class),
            resultSet.getTimestamp("updated_at").toInstant(),
            resultSet.getLong("aggregate_version")
        );
    }

    private LayoutNode mapNode(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LayoutNode(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("parent_id", UUID.class),
            resultSet.getString("node_key"),
            resultSet.getString("node_type"),
            resultSet.getObject("field_id", UUID.class),
            resultSet.getString("field_key"),
            resultSet.getInt("sort_order"),
            parse(resultSet.getString("config")),
            parse(resultSet.getString("visibility_condition"))
        );
    }

    private FieldAccessPolicy mapPolicy(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FieldAccessPolicy(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("field_id", UUID.class),
            resultSet.getString("field_key"),
            resultSet.getString("policy_key"),
            parse(resultSet.getString("policy")),
            resultSet.getString("config_hash")
        );
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid work item layout JSON", exception);
        }
    }

    private JsonNode parse(String value) throws SQLException {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid work item layout JSON stored", exception);
        }
    }
}
