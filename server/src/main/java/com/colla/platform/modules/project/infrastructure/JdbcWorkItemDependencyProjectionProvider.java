package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.contract.WorkItemDependencyProjectionProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcWorkItemDependencyProjectionProvider
    implements WorkItemDependencyProjectionProvider {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkItemDependencyProjectionProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DependencyEdge> edges(
        UUID workspaceId,
        UUID spaceId,
        List<UUID> visibleWorkItemIds,
        int limit
    ) {
        if (visibleWorkItemIds.isEmpty()) return List.of();
        if (visibleWorkItemIds.size() > 200 || limit < 1 || limit > 400) {
            throw new IllegalArgumentException("Dependency projection budget exceeded");
        }
        String placeholders = String.join(
            ",", Collections.nCopies(visibleWorkItemIds.size(), "?")
        );
        List<Object> parameters = new ArrayList<>(List.of(workspaceId, spaceId));
        parameters.addAll(visibleWorkItemIds);
        parameters.addAll(visibleWorkItemIds);
        parameters.add(limit);
        return jdbcTemplate.query(
            """
                select id, relation_key, source_work_item_id, target_work_item_id,
                       version
                  from project_work_item_relations
                 where workspace_id=? and space_id=? and status='active'
                   and relation_kind in ('dependency', 'blocking')
                   and source_work_item_id in (%s)
                   and target_work_item_id in (%s)
                 order by source_work_item_id, target_work_item_id, id
                 limit ?
                """.formatted(placeholders, placeholders),
            (resultSet, rowNumber) -> new DependencyEdge(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("relation_key"),
                resultSet.getObject("source_work_item_id", UUID.class),
                resultSet.getObject("target_work_item_id", UUID.class),
                resultSet.getLong("version")
            ),
            parameters.toArray()
        );
    }
}
