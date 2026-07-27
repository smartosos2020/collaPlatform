package com.colla.platform.modules.project.contract;

import java.util.List;
import java.util.UUID;

/**
 * Identity-only public projection over active S10 dependency relations.
 * Callers must supply a current permission-scoped visible identity set.
 */
public interface WorkItemDependencyProjectionProvider {
    List<DependencyEdge> edges(
        UUID workspaceId,
        UUID spaceId,
        List<UUID> visibleWorkItemIds,
        int limit
    );

    record DependencyEdge(
        UUID relationId,
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        long relationVersion
    ) {
    }
}
