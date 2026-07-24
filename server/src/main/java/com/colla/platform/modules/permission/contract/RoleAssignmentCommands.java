package com.colla.platform.modules.permission.contract;

import java.util.UUID;

/**
 * Owner-side command for direct role membership.
 */
public interface RoleAssignmentCommands {

    void assign(UUID workspaceId, UUID userId, String roleCode, UUID actorId);
}
