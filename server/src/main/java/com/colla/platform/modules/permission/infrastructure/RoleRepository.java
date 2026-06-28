package com.colla.platform.modules.permission.infrastructure;

import com.colla.platform.modules.permission.domain.PermissionModels.PermissionCatalogItem;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleAssignmentSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleDetail;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {
    List<PermissionCatalogItem> listPermissions();

    List<PermissionCatalogItem> listPermissionsByCodes(List<String> permissionCodes);

    List<RoleSummary> listRoles(UUID workspaceId);

    Optional<RoleDetail> findRole(UUID workspaceId, UUID roleId);

    Optional<RoleSummary> findRoleSummary(UUID workspaceId, UUID roleId);

    RoleSummary createRole(UUID workspaceId, String code, String name, String scope, String description, UUID actorId);

    RoleSummary updateRole(UUID workspaceId, UUID roleId, String name, String scope, String description, String status, UUID actorId);

    void replaceRolePermissions(UUID workspaceId, UUID roleId, List<String> permissionCodes);

    List<RoleAssignmentSummary> listAssignments(UUID workspaceId, UUID roleId);

    RoleAssignmentSummary createAssignment(
        UUID workspaceId,
        UUID roleId,
        String subjectType,
        UUID subjectId,
        String scopeType,
        UUID scopeId,
        Instant effectiveAt,
        Instant expiresAt,
        UUID actorId
    );

    boolean revokeAssignment(UUID workspaceId, UUID assignmentId, UUID actorId);

    boolean subjectExists(UUID workspaceId, String subjectType, UUID subjectId);
}
