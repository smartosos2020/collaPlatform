package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.ConsistencyIssue;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyEdge;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyNode;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyPathRow;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyRebuildBatch;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemHierarchyRepository implements WorkItemHierarchyRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemHierarchyRepository(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<HierarchyEdge> listActiveEdges(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        int limit
    ) {
        return jdbcTemplate.query(
            """
                select id, source_work_item_id, target_work_item_id, version,
                       definition_type_id, definition_version_id, definition_config_hash
                  from project_work_item_relations
                 where workspace_id=? and space_id=? and relation_key=?
                   and relation_kind='parent_child' and status='active'
                 order by source_work_item_id, target_work_item_id, id
                 limit ?
                """,
            (resultSet, rowNumber) -> new HierarchyEdge(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("source_work_item_id", UUID.class),
                resultSet.getObject("target_work_item_id", UUID.class),
                resultSet.getLong("version"),
                resultSet.getObject("definition_type_id", UUID.class),
                resultSet.getObject("definition_version_id", UUID.class),
                resultSet.getString("definition_config_hash")
            ),
            workspaceId,
            spaceId,
            relationKey,
            limit
        );
    }

    @Override
    public List<HierarchyPathRow> listStoredPaths(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        int limit
    ) {
        return jdbcTemplate.query(
            """
                select ancestor_work_item_id, descendant_work_item_id, depth,
                       direct_relation_id, projection_version
                  from project_work_item_hierarchy_paths
                 where workspace_id=? and space_id=? and relation_key=?
                 order by ancestor_work_item_id, descendant_work_item_id
                 limit ?
                """,
            (resultSet, rowNumber) -> new HierarchyPathRow(
                resultSet.getObject("ancestor_work_item_id", UUID.class),
                resultSet.getObject("descendant_work_item_id", UUID.class),
                resultSet.getInt("depth"),
                resultSet.getObject("direct_relation_id", UUID.class),
                resultSet.getLong("projection_version")
            ),
            workspaceId,
            spaceId,
            relationKey,
            limit
        );
    }

    @Override
    public long nextProjectionVersion(
        UUID workspaceId,
        UUID spaceId,
        String relationKey
    ) {
        Long value = jdbcTemplate.queryForObject(
            """
                select coalesce(max(projection_version), 0) + 1
                  from project_work_item_hierarchy_paths
                 where workspace_id=? and space_id=? and relation_key=?
                """,
            Long.class,
            workspaceId,
            spaceId,
            relationKey
        );
        return value == null ? 1 : value;
    }

    @Override
    public void replacePaths(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        List<HierarchyPathRow> paths
    ) {
        jdbcTemplate.update(
            """
                delete from project_work_item_hierarchy_paths
                 where workspace_id=? and space_id=? and relation_key=?
                """,
            workspaceId,
            spaceId,
            relationKey
        );
        if (paths.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
            """
                insert into project_work_item_hierarchy_paths (
                    workspace_id, space_id, relation_key,
                    ancestor_work_item_id, descendant_work_item_id, depth,
                    direct_relation_id, projection_version, rebuilt_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index)
                    throws SQLException {
                    HierarchyPathRow path = paths.get(index);
                    statement.setObject(1, workspaceId);
                    statement.setObject(2, spaceId);
                    statement.setString(3, relationKey);
                    statement.setObject(4, path.ancestorWorkItemId());
                    statement.setObject(5, path.descendantWorkItemId());
                    statement.setInt(6, path.depth());
                    statement.setObject(7, path.directRelationId());
                    statement.setLong(8, path.projectionVersion());
                }

                @Override
                public int getBatchSize() {
                    return paths.size();
                }
            }
        );
    }

    @Override
    public List<HierarchyNode> listNodes(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID workItemId,
        String direction,
        Integer cursorDepth,
        UUID cursorNodeId,
        int maxDepth,
        int limit
    ) {
        boolean ancestors = "ancestors".equals(direction);
        if (!ancestors && !"descendants".equals(direction)) {
            throw new IllegalArgumentException("Unknown hierarchy direction");
        }
        String relatedColumn = ancestors
            ? "p.ancestor_work_item_id"
            : "p.descendant_work_item_id";
        String fixedColumn = ancestors
            ? "p.descendant_work_item_id"
            : "p.ancestor_work_item_id";
        String cursorClause = cursorDepth == null
            ? ""
            : " and (p.depth > ? or (p.depth = ? and " + relatedColumn + "::text > ?))";
        String sql = """
            select w.id, w.type_definition_id, w.type_version_id, wt.type_key,
                   w.display_key, w.title, w.status, w.version,
                   p.depth, p.direct_relation_id
              from project_work_item_hierarchy_paths p
              join project_work_items w
                on w.workspace_id=p.workspace_id and w.space_id=p.space_id
               and w.id=%s
              join project_work_item_types wt
                on wt.workspace_id=w.workspace_id and wt.space_id=w.space_id
               and wt.id=w.type_definition_id
             where p.workspace_id=? and p.space_id=? and p.relation_key=?
               and %s=? and p.depth between 1 and ?%s
             order by p.depth, %s
             limit ?
            """.formatted(
            relatedColumn,
            fixedColumn,
            cursorClause,
            relatedColumn
        );
        Object[] parameters;
        if (cursorDepth == null) {
            parameters = new Object[] {
                workspaceId, spaceId, relationKey, workItemId, maxDepth, limit
            };
        } else {
            parameters = new Object[] {
                workspaceId, spaceId, relationKey, workItemId, maxDepth,
                cursorDepth, cursorDepth, cursorNodeId.toString(), limit
            };
        }
        return jdbcTemplate.query(sql, this::mapNode, parameters);
    }

    @Override
    public Optional<HierarchyNode> findNode(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select w.id, w.type_definition_id, w.type_version_id, wt.type_key,
                           w.display_key, w.title, w.status, w.version,
                           0 as depth, null::uuid as direct_relation_id
                      from project_work_items w
                      join project_work_item_types wt
                        on wt.workspace_id=w.workspace_id and wt.space_id=w.space_id
                       and wt.id=w.type_definition_id
                     where w.workspace_id=? and w.space_id=? and w.id=?
                    """,
                this::mapNode,
                workspaceId,
                spaceId,
                workItemId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean tryCreateRebuildBatch(RebuildBatchStart start) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_hierarchy_rebuild_batches (
                    id, workspace_id, space_id, relation_key, request_id, request_hash,
                    dry_run, status, requested_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'pending', ?, now())
                on conflict (workspace_id, space_id, request_id) do nothing
                """,
            start.id(),
            start.workspaceId(),
            start.spaceId(),
            start.relationKey(),
            start.requestId(),
            start.requestHash(),
            start.dryRun(),
            start.requestedBy()
        ) == 1;
    }

    @Override
    public Optional<HierarchyRebuildRecord> findRebuildBatch(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId
    ) {
        return findBatch(
            """
                select * from project_work_item_hierarchy_rebuild_batches
                 where workspace_id=? and space_id=? and id=?
                """,
            workspaceId,
            spaceId,
            batchId
        );
    }

    @Override
    public Optional<HierarchyRebuildRecord> findRebuildBatchByRequest(
        UUID workspaceId,
        UUID spaceId,
        String requestId
    ) {
        return findBatch(
            """
                select * from project_work_item_hierarchy_rebuild_batches
                 where workspace_id=? and space_id=? and request_id=?
                """,
            workspaceId,
            spaceId,
            requestId
        );
    }

    @Override
    public int completeRebuildBatch(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        int expectedAttempt,
        String status,
        int edgeCount,
        int expectedPathCount,
        List<ConsistencyIssue> failures
    ) {
        return jdbcTemplate.update(
            """
                update project_work_item_hierarchy_rebuild_batches
                   set status=?, attempt=attempt + 1, edge_count=?,
                       expected_path_count=?, issue_count=?,
                       failures=?::jsonb, completed_at=now()
                 where workspace_id=? and space_id=? and id=?
                   and attempt=? and status in ('pending', 'failed')
                """,
            status,
            edgeCount,
            expectedPathCount,
            failures.size(),
            json(failures),
            workspaceId,
            spaceId,
            batchId,
            expectedAttempt
        );
    }

    private Optional<HierarchyRebuildRecord> findBatch(String sql, Object... parameters) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                sql,
                (resultSet, rowNumber) -> {
                    List<ConsistencyIssue> failures = issues(
                        resultSet.getString("failures")
                    );
                    Timestamp completed = resultSet.getTimestamp("completed_at");
                    HierarchyRebuildBatch batch = new HierarchyRebuildBatch(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("space_id", UUID.class),
                        resultSet.getString("relation_key"),
                        resultSet.getString("request_id"),
                        resultSet.getBoolean("dry_run"),
                        resultSet.getString("status"),
                        resultSet.getInt("attempt"),
                        resultSet.getInt("edge_count"),
                        resultSet.getInt("expected_path_count"),
                        resultSet.getInt("issue_count"),
                        failures,
                        resultSet.getObject("requested_by", UUID.class),
                        resultSet.getTimestamp("created_at").toInstant(),
                        completed == null ? null : completed.toInstant()
                    );
                    return new HierarchyRebuildRecord(
                        batch,
                        resultSet.getString("request_hash")
                    );
                },
                parameters
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private HierarchyNode mapNode(java.sql.ResultSet resultSet, int rowNumber)
        throws SQLException {
        return new HierarchyNode(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("type_definition_id", UUID.class),
            resultSet.getObject("type_version_id", UUID.class),
            resultSet.getString("type_key"),
            resultSet.getString("display_key"),
            resultSet.getString("title"),
            resultSet.getString("status"),
            resultSet.getLong("version"),
            resultSet.getInt("depth"),
            resultSet.getObject("direct_relation_id", UUID.class)
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize hierarchy evidence", exception);
        }
    }

    private List<ConsistencyIssue> issues(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            List<ConsistencyIssue> result = new ArrayList<>();
            root.forEach(node -> {
                try {
                    result.add(objectMapper.treeToValue(node, ConsistencyIssue.class));
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException(
                        "Stored hierarchy failure is invalid",
                        exception
                    );
                }
            });
            return List.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored hierarchy failures are invalid", exception);
        }
    }
}
