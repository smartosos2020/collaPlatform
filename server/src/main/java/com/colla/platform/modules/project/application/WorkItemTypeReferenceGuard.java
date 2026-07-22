package com.colla.platform.modules.project.application;

import java.util.UUID;

/** Extension point for later stages that introduce type-bound fields or work item instances. */
@FunctionalInterface
public interface WorkItemTypeReferenceGuard {
    void assertTransitionAllowed(UUID workspaceId, UUID spaceId, UUID typeId, String targetStatus);
}
