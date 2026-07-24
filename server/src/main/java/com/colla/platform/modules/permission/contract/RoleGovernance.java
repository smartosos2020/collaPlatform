package com.colla.platform.modules.permission.contract;

import java.util.UUID;

/**
 * Administrative commands for permission-owned roles.
 */
public interface RoleGovernance {

    boolean isActive(UUID workspaceId, UUID roleId);

    int disable(UUID workspaceId, UUID actorId, UUID roleId);
}
