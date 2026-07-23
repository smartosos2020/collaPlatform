package com.colla.platform.modules.project.application;

import java.util.UUID;

@FunctionalInterface
public interface WorkItemFieldReferenceGuard {
    void assertTransitionAllowed(UUID workspaceId, UUID spaceId, UUID typeDefinitionId, UUID fieldId, String targetStatus);
}
