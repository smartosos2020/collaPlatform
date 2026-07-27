package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.contract.WorkItemHierarchyProjectionProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

@Component
public final class JdbcWorkItemHierarchyProjectionProvider
    implements WorkItemHierarchyProjectionProvider {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkItemHierarchyProjectionProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<UUID, List<AncestorRef>> ancestors(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        List<UUID> descendantIds
    ) {
        if (descendantIds.isEmpty()) {
            return Map.of();
        }
        if (descendantIds.size() > 200) {
            throw new IllegalArgumentException("Hierarchy identity batch exceeds 200");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(
            descendantIds.size(), "?"
        ));
        List<Object> parameters = new ArrayList<>();
        parameters.add(workspaceId);
        parameters.add(spaceId);
        parameters.add(relationKey);
        parameters.addAll(descendantIds);
        Map<UUID, List<AncestorRef>> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
                select descendant_work_item_id, ancestor_work_item_id, depth
                  from project_work_item_hierarchy_paths
                 where workspace_id=? and space_id=? and relation_key=?
                   and descendant_work_item_id in (%s)
                   and depth between 1 and 64
                 order by descendant_work_item_id, depth, ancestor_work_item_id
                """.formatted(placeholders),
            (RowCallbackHandler) resultSet -> result.computeIfAbsent(
                resultSet.getObject("descendant_work_item_id", UUID.class),
                ignored -> new ArrayList<>()
            ).add(new AncestorRef(
                resultSet.getObject("ancestor_work_item_id", UUID.class),
                resultSet.getInt("depth")
            )),
            parameters.toArray()
        );
        return result.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> List.copyOf(entry.getValue())
        ));
    }
}
