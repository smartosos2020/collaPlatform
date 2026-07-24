package com.colla.platform.modules.permission.contract;

import java.util.UUID;

/**
 * Owner-side commands for knowledge-content permission grants.
 */
public interface KnowledgePermissionCommands {

    void copyInherited(UUID workspaceId, UUID itemId, UUID parentId, UUID actorId);

    void upsertDirect(
        UUID workspaceId,
        UUID itemId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        UUID actorId
    );
}
