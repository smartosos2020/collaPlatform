package com.colla.platform.modules.project.contract;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only projection boundary for registered relation, hierarchy and workflow filters.
 */
public interface WorkItemQueryContextProvider {
    Map<UUID, QueryContext> load(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        List<UUID> visibleWorkItemIds
    );

    record QueryContext(
        Set<String> participantRoles,
        String state,
        Set<String> nodeStates,
        Set<String> relations,
        Set<UUID> ancestors,
        Set<UUID> descendants
    ) {
        public static QueryContext empty() {
            return new QueryContext(Set.of(), null, Set.of(), Set.of(), Set.of(), Set.of());
        }
    }
}
