package com.colla.platform.modules.project.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal invalidation event. It intentionally excludes policy bodies, role memberships,
 * object titles, subject names and field values.
 */
public record WorkItemPermissionChangedEvent(
    int schemaVersion,
    UUID workspaceId,
    UUID spaceId,
    UUID workItemId,
    String changeKind,
    long permissionVersion,
    UUID actorId,
    Instant occurredAt
) {
    public WorkItemPermissionChangedEvent {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Work-item permission event schema version must be 1");
        }
        if (changeKind == null || !changeKind.matches(
            "policy_published|role_assigned|role_revoked|request_decided|permission_expired|projection_rebuilt"
        )) {
            throw new IllegalArgumentException("Unsupported work-item permission change kind");
        }
    }
}
