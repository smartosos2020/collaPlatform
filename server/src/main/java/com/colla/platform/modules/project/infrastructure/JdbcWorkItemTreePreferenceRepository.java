package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreePreference;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreePreferenceCommand;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkItemTreePreferenceRepository implements WorkItemTreePreferenceRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkItemTreePreferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TreePreference> find(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select view_key, relation_key, expanded_node_ids,
                           aggregate_version, updated_at
                      from project_work_item_tree_preferences
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                    """,
                (resultSet, rowNum) -> new TreePreference(
                    resultSet.getString("view_key"),
                    resultSet.getString("relation_key"),
                    uuidList(resultSet.getArray("expanded_node_ids")),
                    resultSet.getLong("aggregate_version"),
                    resultSet.getTimestamp("updated_at").toInstant()
                ),
                workspaceId, spaceId, userId, viewKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public TreePreference save(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        TreePreferenceCommand command
    ) {
        Optional<TreePreference> current = findForUpdate(workspaceId, spaceId, userId, viewKey);
        long version = current.map(TreePreference::version).orElse(0L);
        if (version != command.expectedVersion()) {
            throw failure("TREE_PREFERENCE_VERSION_CONFLICT", "Tree preference version changed");
        }
        Instant now = Instant.now();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                insert into project_work_item_tree_preferences (
                    workspace_id, space_id, user_id, view_key, relation_key,
                    expanded_node_ids, aggregate_version, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, 1, ?, ?)
                on conflict (workspace_id, space_id, user_id, view_key) do update
                    set relation_key=excluded.relation_key,
                        expanded_node_ids=excluded.expanded_node_ids,
                        aggregate_version=project_work_item_tree_preferences.aggregate_version + 1,
                        updated_at=excluded.updated_at
                """);
            statement.setObject(1, workspaceId);
            statement.setObject(2, spaceId);
            statement.setObject(3, userId);
            statement.setString(4, viewKey);
            statement.setString(5, command.relationKey());
            statement.setArray(6, connection.createArrayOf(
                "uuid", command.expandedNodeIds().toArray()
            ));
            statement.setTimestamp(7, java.sql.Timestamp.from(now));
            statement.setTimestamp(8, java.sql.Timestamp.from(now));
            return statement;
        });
        return find(workspaceId, spaceId, userId, viewKey)
            .orElseThrow(() -> failure("TREE_PREFERENCE_CONFLICT", "Tree preference was not saved"));
    }

    private Optional<TreePreference> findForUpdate(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select view_key, relation_key, expanded_node_ids,
                           aggregate_version, updated_at
                      from project_work_item_tree_preferences
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                     for update
                    """,
                (resultSet, rowNum) -> new TreePreference(
                    resultSet.getString("view_key"),
                    resultSet.getString("relation_key"),
                    uuidList(resultSet.getArray("expanded_node_ids")),
                    resultSet.getLong("aggregate_version"),
                    resultSet.getTimestamp("updated_at").toInstant()
                ),
                workspaceId, spaceId, userId, viewKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private static List<UUID> uuidList(Array value) throws SQLException {
        return Arrays.stream((Object[]) value.getArray()).map(item -> (UUID) item).toList();
    }
}
