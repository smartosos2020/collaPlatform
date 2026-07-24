package com.colla.platform.modules.identity.contract;

import java.util.UUID;

/**
 * Administrative commands for identity-owned resources.
 */
public interface IdentityGovernance {

    boolean isActive(UUID workspaceId, Resource resource, UUID targetId);

    int disable(UUID workspaceId, UUID actorId, Resource resource, UUID targetId);

    enum Resource {
        MEMBER,
        DEPARTMENT,
        USER_GROUP
    }
}
