package com.colla.platform.modules.project.contract;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal identity-only public projection over the S10 canonical hierarchy.
 * Consumers must intersect this projection with a current permission decision.
 */
public interface WorkItemHierarchyProjectionProvider {
    Map<UUID, List<AncestorRef>> ancestors(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        List<UUID> descendantIds
    );

    record AncestorRef(UUID workItemId, int depth) {
    }
}
