package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkItemHierarchyModels {
    public static final int MAX_QUERY_DEPTH = 64;
    public static final int MAX_QUERY_NODES = 200;
    public static final int MAX_REBUILD_EDGES = 5_000;
    public static final int MAX_PROJECTION_PATHS = 20_000;

    private WorkItemHierarchyModels() {
    }

    public record HierarchyEdge(
        UUID relationId,
        UUID parentWorkItemId,
        UUID childWorkItemId,
        long relationVersion,
        UUID definitionTypeId,
        UUID definitionVersionId,
        String definitionConfigHash
    ) {
    }

    public record HierarchyPathRow(
        UUID ancestorWorkItemId,
        UUID descendantWorkItemId,
        int depth,
        UUID directRelationId,
        long projectionVersion
    ) {
    }

    public record HierarchyNode(
        UUID id,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String typeKey,
        String displayKey,
        String title,
        String status,
        long version,
        int depth,
        UUID directRelationId
    ) {
    }

    public record HierarchyPage(
        List<HierarchyNode> items,
        String nextCursor,
        boolean truncated
    ) {
    }

    public record HierarchyNavigation(
        HierarchyNode focus,
        List<HierarchyNode> breadcrumbs,
        HierarchyNode parent,
        List<HierarchyNode> children,
        List<HierarchyNode> siblings,
        List<HierarchyNode> localTree,
        boolean truncated,
        String degradationReason
    ) {
    }

    public record HierarchyMutation(
        RelationView relation,
        WorkItemView child
    ) {
    }

    public record ConsistencyIssue(
        String code,
        UUID ancestorWorkItemId,
        UUID descendantWorkItemId,
        Integer expectedDepth,
        Integer actualDepth
    ) {
    }

    public record ConsistencyReport(
        String relationKey,
        int edgeCount,
        int expectedPathCount,
        int actualPathCount,
        List<ConsistencyIssue> issues,
        boolean truncated
    ) {
    }

    public record HierarchyRebuildBatch(
        UUID id,
        UUID spaceId,
        String relationKey,
        String requestId,
        boolean dryRun,
        String status,
        int attempt,
        int edgeCount,
        int expectedPathCount,
        int issueCount,
        List<ConsistencyIssue> failures,
        UUID requestedBy,
        Instant createdAt,
        Instant completedAt
    ) {
    }
}
