package com.colla.platform.modules.permission.infrastructure;

import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionGrant;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionEntry;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionMatch;
import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ResourcePermissionRepository {
    void upsertGrant(ResourcePermissionGrant grant);

    List<ResourcePermissionEntry> listGrants(UUID workspaceId, String resourceType, UUID resourceId);

    Optional<ResourcePermissionEntry> findGrant(UUID workspaceId, UUID permissionId);

    boolean revokeGrant(UUID workspaceId, UUID permissionId, UUID actorId);

    Optional<ResourcePermissionMatch> findBestMatch(UUID workspaceId, UUID userId, String resourceType, UUID resourceId);

    boolean subjectExists(UUID workspaceId, String subjectType, UUID subjectId);
}
