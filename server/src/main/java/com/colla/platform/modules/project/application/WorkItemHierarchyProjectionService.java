package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemHierarchyModels.MAX_PROJECTION_PATHS;
import static com.colla.platform.modules.project.domain.WorkItemHierarchyModels.MAX_REBUILD_EDGES;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.ConsistencyIssue;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.ConsistencyReport;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyEdge;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyPathRow;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationDefinitionBinding;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.WorkItemRelation;
import com.colla.platform.modules.project.infrastructure.WorkItemHierarchyRepository;
import com.colla.platform.modules.project.runtime.WorkItemRelationRuntimeAdapter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkItemHierarchyProjectionService {
    private final WorkItemHierarchyRepository repository;
    private final WorkItemRelationRuntimeAdapter runtimeAdapter;

    public WorkItemHierarchyProjectionService(
        WorkItemHierarchyRepository repository,
        WorkItemRelationRuntimeAdapter runtimeAdapter
    ) {
        this.repository = repository;
        this.runtimeAdapter = runtimeAdapter;
    }

    public void refreshAfterMutation(WorkItemRelation relation) {
        if (relation.kind() != RelationKind.parent_child) {
            return;
        }
        GraphSnapshot graph = graph(
            relation.workspaceId(),
            relation.spaceId(),
            relation.relationKey()
        );
        requireStructurallyValid(graph);
        replace(
            relation.workspaceId(),
            relation.spaceId(),
            relation.relationKey(),
            graph.paths()
        );
    }

    public ConsistencyReport scan(
        UUID workspaceId,
        UUID spaceId,
        String relationKey
    ) {
        GraphSnapshot graph = graph(workspaceId, spaceId, relationKey);
        List<HierarchyPathRow> actual = repository.listStoredPaths(
            workspaceId,
            spaceId,
            relationKey,
            MAX_PROJECTION_PATHS + 1
        );
        List<ConsistencyIssue> issues = new ArrayList<>(graph.issues());
        boolean truncated = actual.size() > MAX_PROJECTION_PATHS
            || graph.paths().size() > MAX_PROJECTION_PATHS;
        Map<PathKey, HierarchyPathRow> expectedByKey = index(graph.paths());
        Map<PathKey, HierarchyPathRow> actualByKey = index(
            actual.stream().limit(MAX_PROJECTION_PATHS).toList()
        );
        for (Map.Entry<PathKey, HierarchyPathRow> entry : expectedByKey.entrySet()) {
            HierarchyPathRow stored = actualByKey.get(entry.getKey());
            if (stored == null) {
                issues.add(issue("MISSING_PATH", entry.getValue(), null));
            } else if (stored.depth() != entry.getValue().depth()) {
                issues.add(issue("WRONG_DEPTH", entry.getValue(), stored));
            }
        }
        for (Map.Entry<PathKey, HierarchyPathRow> entry : actualByKey.entrySet()) {
            if (!expectedByKey.containsKey(entry.getKey())) {
                issues.add(issue("UNEXPECTED_PATH", null, entry.getValue()));
            }
        }
        return new ConsistencyReport(
            relationKey,
            graph.edgeCount(),
            graph.paths().size(),
            actual.size(),
            List.copyOf(issues.stream().limit(MAX_PROJECTION_PATHS).toList()),
            truncated || issues.size() > MAX_PROJECTION_PATHS
        );
    }

    public ConsistencyReport rebuild(
        UUID workspaceId,
        UUID spaceId,
        String relationKey
    ) {
        GraphSnapshot graph = graph(workspaceId, spaceId, relationKey);
        requireStructurallyValid(graph);
        replace(workspaceId, spaceId, relationKey, graph.paths());
        return scan(workspaceId, spaceId, relationKey);
    }

    private GraphSnapshot graph(
        UUID workspaceId,
        UUID spaceId,
        String relationKey
    ) {
        List<HierarchyEdge> loaded = repository.listActiveEdges(
            workspaceId,
            spaceId,
            relationKey,
            MAX_REBUILD_EDGES + 1
        );
        if (loaded.size() > MAX_REBUILD_EDGES) {
            throw failure(
                "HIERARCHY_EDGE_BUDGET_EXCEEDED",
                "Hierarchy projection rebuild exceeds the configured edge budget"
            );
        }
        Map<UUID, List<BoundEdge>> outgoing = new LinkedHashMap<>();
        Set<UUID> nodes = new LinkedHashSet<>();
        for (HierarchyEdge edge : loaded) {
            RelationDefinitionBinding binding = runtimeAdapter.requireStored(
                workspaceId,
                spaceId,
                edge.definitionTypeId(),
                edge.definitionVersionId(),
                edge.definitionConfigHash(),
                relationKey
            );
            if (binding.kind() != RelationKind.parent_child) {
                throw failure(
                    "HIERARCHY_DEFINITION_MISMATCH",
                    "Hierarchy projection only accepts stored parent-child definitions"
                );
            }
            BoundEdge bound = new BoundEdge(edge, binding.maxDepth());
            outgoing.computeIfAbsent(edge.parentWorkItemId(), ignored -> new ArrayList<>())
                .add(bound);
            nodes.add(edge.parentWorkItemId());
            nodes.add(edge.childWorkItemId());
        }
        outgoing.values().forEach(edges -> edges.sort(
            Comparator.comparing(value -> value.edge().relationId().toString())
        ));

        List<ConsistencyIssue> issues = new ArrayList<>();
        List<HierarchyPathRow> paths = new ArrayList<>();
        for (UUID node : nodes.stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
            paths.add(new HierarchyPathRow(node, node, 0, null, 0));
            Map<UUID, Integer> bestDepth = new HashMap<>();
            bestDepth.put(node, 0);
            ArrayDeque<PathState> queue = new ArrayDeque<>();
            queue.add(new PathState(node, 0, Integer.MAX_VALUE));
            while (!queue.isEmpty()) {
                PathState state = queue.removeFirst();
                for (BoundEdge edge : outgoing.getOrDefault(state.nodeId(), List.of())) {
                    int depth = state.depth() + 1;
                    int budget = Math.min(state.remainingBudget(), edge.maxDepth());
                    UUID child = edge.edge().childWorkItemId();
                    if (child.equals(node)) {
                        issues.add(new ConsistencyIssue(
                            "CANONICAL_EDGE_CYCLE",
                            node,
                            state.nodeId(),
                            null,
                            depth
                        ));
                        continue;
                    }
                    if (depth > budget) {
                        issues.add(new ConsistencyIssue(
                            "MAX_DEPTH_EXCEEDED",
                            node,
                            child,
                            budget,
                            depth
                        ));
                        continue;
                    }
                    Integer known = bestDepth.get(child);
                    if (known != null && known <= depth) {
                        continue;
                    }
                    bestDepth.put(child, depth);
                    paths.add(new HierarchyPathRow(
                        node,
                        child,
                        depth,
                        depth == 1 ? edge.edge().relationId() : null,
                        0
                    ));
                    if (paths.size() > MAX_PROJECTION_PATHS) {
                        throw failure(
                            "HIERARCHY_PATH_BUDGET_EXCEEDED",
                            "Hierarchy projection exceeds the configured path budget"
                        );
                    }
                    queue.addLast(new PathState(child, depth, budget));
                }
            }
        }
        paths.sort(
            Comparator.comparing((HierarchyPathRow value) ->
                value.ancestorWorkItemId().toString())
                .thenComparing(value -> value.descendantWorkItemId().toString())
        );
        return new GraphSnapshot(
            loaded.size(),
            List.copyOf(paths),
            List.copyOf(deduplicate(issues))
        );
    }

    private void replace(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        List<HierarchyPathRow> paths
    ) {
        long projectionVersion = repository.nextProjectionVersion(
            workspaceId,
            spaceId,
            relationKey
        );
        repository.replacePaths(
            workspaceId,
            spaceId,
            relationKey,
            paths.stream().map(path -> new HierarchyPathRow(
                path.ancestorWorkItemId(),
                path.descendantWorkItemId(),
                path.depth(),
                path.directRelationId(),
                projectionVersion
            )).toList()
        );
    }

    private void requireStructurallyValid(GraphSnapshot graph) {
        if (!graph.issues().isEmpty()) {
            throw failure(
                "HIERARCHY_CANONICAL_GRAPH_INVALID",
                "Canonical parent-child edges contain a cycle or exceed a stored depth budget"
            );
        }
    }

    private Map<PathKey, HierarchyPathRow> index(List<HierarchyPathRow> paths) {
        Map<PathKey, HierarchyPathRow> result = new LinkedHashMap<>();
        paths.forEach(path -> result.put(
            new PathKey(path.ancestorWorkItemId(), path.descendantWorkItemId()),
            path
        ));
        return result;
    }

    private ConsistencyIssue issue(
        String code,
        HierarchyPathRow expected,
        HierarchyPathRow actual
    ) {
        HierarchyPathRow path = expected == null ? actual : expected;
        return new ConsistencyIssue(
            code,
            path.ancestorWorkItemId(),
            path.descendantWorkItemId(),
            expected == null ? null : expected.depth(),
            actual == null ? null : actual.depth()
        );
    }

    private List<ConsistencyIssue> deduplicate(List<ConsistencyIssue> issues) {
        Set<String> seen = new HashSet<>();
        return issues.stream().filter(issue -> seen.add(
            issue.code() + ":" + issue.ancestorWorkItemId() + ":" + issue.descendantWorkItemId()
        )).toList();
    }

    private record BoundEdge(HierarchyEdge edge, int maxDepth) {
    }

    private record PathState(UUID nodeId, int depth, int remainingBudget) {
    }

    private record PathKey(UUID ancestor, UUID descendant) {
    }

    private record GraphSnapshot(
        int edgeCount,
        List<HierarchyPathRow> paths,
        List<ConsistencyIssue> issues
    ) {
    }
}
