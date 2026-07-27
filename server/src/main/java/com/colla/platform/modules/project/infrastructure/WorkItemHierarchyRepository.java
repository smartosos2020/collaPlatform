package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyEdge;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyNode;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyPathRow;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyRebuildBatch;
import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.ConsistencyIssue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemHierarchyRepository {
    List<HierarchyEdge> listActiveEdges(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        int limit
    );

    List<HierarchyPathRow> listStoredPaths(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        int limit
    );

    long nextProjectionVersion(UUID workspaceId, UUID spaceId, String relationKey);

    void replacePaths(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        List<HierarchyPathRow> paths
    );

    List<HierarchyNode> listNodes(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID workItemId,
        String direction,
        Integer cursorDepth,
        UUID cursorNodeId,
        int maxDepth,
        int limit
    );

    Optional<HierarchyNode> findNode(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId
    );

    boolean tryCreateRebuildBatch(RebuildBatchStart start);

    Optional<HierarchyRebuildRecord> findRebuildBatch(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId
    );

    Optional<HierarchyRebuildRecord> findRebuildBatchByRequest(
        UUID workspaceId,
        UUID spaceId,
        String requestId
    );

    int completeRebuildBatch(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        int expectedAttempt,
        String status,
        int edgeCount,
        int expectedPathCount,
        List<ConsistencyIssue> failures
    );

    record RebuildBatchStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        String requestId,
        String requestHash,
        boolean dryRun,
        UUID requestedBy
    ) {
    }

    record HierarchyRebuildRecord(
        HierarchyRebuildBatch batch,
        String requestHash
    ) {
    }
}
