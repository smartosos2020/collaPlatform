package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkItemTreeViewModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_TREE_NODES = 200;
    public static final int MAX_EXPANSION_PAGE = 50;
    public static final int MAX_VISIBLE_DEPTH = 32;

    private WorkItemTreeViewModels() {
    }

    public record TreeRequest(
        int schemaVersion,
        String relationKey,
        QueryDefinition query,
        UUID parentId,
        int limit,
        int maxDepth,
        String cursor
    ) {
    }

    public record TreeNode(
        UUID id,
        UUID parentId,
        String displayKey,
        String title,
        String status,
        long version,
        int depth,
        int visibleChildCount,
        boolean expandable,
        String matchKind,
        List<String> availableActions
    ) {
    }

    public record AncestorPath(UUID focusId, List<TreeNode> items) {
    }

    public record TreeAggregate(
        int visibleNodeCount,
        int rootCount,
        int matchedCount,
        int maxVisibleDepth,
        boolean candidateBoundReached
    ) {
    }

    public record TreeResult(
        int schemaVersion,
        String relationKey,
        String queryHash,
        UUID parentId,
        List<TreeNode> items,
        String nextCursor,
        boolean truncated,
        TreeAggregate aggregate
    ) {
    }

    public record TreePreference(
        String viewKey,
        String relationKey,
        List<UUID> expandedNodeIds,
        long version,
        Instant updatedAt
    ) {
    }

    public record TreePreferenceCommand(
        String requestId,
        long expectedVersion,
        String relationKey,
        List<UUID> expandedNodeIds
    ) {
    }
}
