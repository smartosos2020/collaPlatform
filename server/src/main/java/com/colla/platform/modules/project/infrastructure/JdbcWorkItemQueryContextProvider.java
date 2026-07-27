package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.contract.WorkItemQueryContextProvider;
import com.colla.platform.modules.project.contract.WorkItemQueryContextProvider.QueryContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemQueryContextProvider implements WorkItemQueryContextProvider {
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkItemQueryContextProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<UUID, QueryContext> load(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        List<UUID> visibleWorkItemIds
    ) {
        if (visibleWorkItemIds == null || visibleWorkItemIds.isEmpty()) return Map.of();
        Map<UUID, MutableContext> contexts = new HashMap<>();
        visibleWorkItemIds.forEach(id -> contexts.put(id, new MutableContext()));
        String placeholders = String.join(",", java.util.Collections.nCopies(visibleWorkItemIds.size(), "?"));
        List<Object> scope = new ArrayList<>(List.of(workspaceId, spaceId));
        scope.addAll(visibleWorkItemIds);

        List<Object> participantParameters = new ArrayList<>(List.of(workspaceId, spaceId, actorId));
        participantParameters.addAll(visibleWorkItemIds);
        jdbcTemplate.query(
            "select work_item_id, participant_role from project_work_item_participants "
                + "where workspace_id=? and space_id=? and user_id=? and work_item_id in ("
                + placeholders + ")",
            resultSet -> {
                contexts.get(resultSet.getObject(1, UUID.class)).participantRoles
                    .add(resultSet.getString(2));
            },
            participantParameters.toArray()
        );
        jdbcTemplate.query(
            "select work_item_id, current_state_key from project_work_item_current_states "
                + "where workspace_id=? and space_id=? and work_item_id in (" + placeholders + ")",
            resultSet -> {
                contexts.get(resultSet.getObject(1, UUID.class)).state = resultSet.getString(2);
            },
            scope.toArray()
        );
        jdbcTemplate.query(
            "select i.work_item_id, t.node_key from project_node_workflow_instances i "
                + "join project_node_workflow_tokens t on t.workspace_id=i.workspace_id "
                + "and t.space_id=i.space_id and t.instance_id=i.id "
                + "where i.workspace_id=? and i.space_id=? and t.status in ('active','waiting') "
                + "and i.work_item_id in (" + placeholders + ")",
            resultSet -> {
                contexts.get(resultSet.getObject(1, UUID.class)).nodeStates
                    .add(resultSet.getString(2));
            },
            scope.toArray()
        );
        List<Object> relationParameters = new ArrayList<>(List.of(workspaceId, spaceId));
        relationParameters.addAll(visibleWorkItemIds);
        relationParameters.addAll(visibleWorkItemIds);
        jdbcTemplate.query(
            "select source_work_item_id, target_work_item_id, relation_key "
                + "from project_work_item_relations where workspace_id=? and space_id=? "
                + "and status='active' and (source_work_item_id in (" + placeholders
                + ") or target_work_item_id in (" + placeholders + "))",
            resultSet -> {
                UUID source = resultSet.getObject(1, UUID.class);
                UUID target = resultSet.getObject(2, UUID.class);
                String key = resultSet.getString(3);
                if (contexts.containsKey(source)) contexts.get(source).relations.add(key);
                if (contexts.containsKey(target)) contexts.get(target).relations.add(key);
            },
            relationParameters.toArray()
        );
        List<Object> hierarchyParameters = new ArrayList<>(List.of(workspaceId, spaceId));
        hierarchyParameters.addAll(visibleWorkItemIds);
        hierarchyParameters.addAll(visibleWorkItemIds);
        jdbcTemplate.query(
            "select ancestor_work_item_id, descendant_work_item_id "
                + "from project_work_item_hierarchy_paths where workspace_id=? and space_id=? "
                + "and depth>0 and (ancestor_work_item_id in (" + placeholders
                + ") or descendant_work_item_id in (" + placeholders + "))",
            resultSet -> {
                UUID ancestor = resultSet.getObject(1, UUID.class);
                UUID descendant = resultSet.getObject(2, UUID.class);
                if (contexts.containsKey(ancestor)) contexts.get(ancestor).descendants.add(descendant);
                if (contexts.containsKey(descendant)) contexts.get(descendant).ancestors.add(ancestor);
            },
            hierarchyParameters.toArray()
        );
        Map<UUID, QueryContext> result = new HashMap<>();
        contexts.forEach((id, context) -> result.put(id, context.freeze()));
        return Map.copyOf(result);
    }

    private static final class MutableContext {
        private final Set<String> participantRoles = new LinkedHashSet<>();
        private String state;
        private final Set<String> nodeStates = new LinkedHashSet<>();
        private final Set<String> relations = new LinkedHashSet<>();
        private final Set<UUID> ancestors = new LinkedHashSet<>();
        private final Set<UUID> descendants = new LinkedHashSet<>();

        private QueryContext freeze() {
            return new QueryContext(
                Set.copyOf(participantRoles),
                state,
                Set.copyOf(nodeStates),
                Set.copyOf(relations),
                Set.copyOf(ancestors),
                Set.copyOf(descendants)
            );
        }
    }
}
