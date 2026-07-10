package com.colla.platform.modules.permission.domain;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

public final class PermissionModels {
    private PermissionModels() {
    }

    public record PermissionDecision(
        UUID workspaceId,
        String objectType,
        UUID objectId,
        String action,
        boolean allowed,
        String reason,
        String currentLevel,
        String requiredLevel,
        String source,
        UUID permissionId
    ) {
    }

    public enum PermissionActionCategory {
        user_action,
        object_management,
        space_management,
        admin_management,
        super_admin
    }

    public record PermissionExplanation(
        String objectType,
        UUID objectId,
        String action,
        PermissionActionCategory actionCategory,
        String presentationContext,
        boolean allowed,
        String accessState,
        String reason,
        String actionAdvice,
        String policySourceDetail,
        String currentLevel,
        String requiredLevel,
        String source
    ) {
    }

    public record ResourcePermissionGrant(
        UUID workspaceId,
        String resourceType,
        UUID resourceId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        String sourceType,
        UUID sourceId,
        Instant expiresAt,
        UUID actorId
    ) {
    }

    public record ResourcePermissionMatch(
        UUID permissionId,
        String resourceType,
        UUID resourceId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        String sourceType,
        UUID sourceId,
        Instant expiresAt,
        String matchedVia,
        UUID matchedSubjectId,
        String subjectName
    ) {
    }

    public record ResourcePermissionEntry(
        UUID id,
        String resourceType,
        UUID resourceId,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String subjectDetail,
        String permissionLevel,
        String sourceType,
        UUID sourceId,
        Instant expiresAt,
        String status,
        String effectiveStatus,
        Instant createdAt,
        Instant updatedAt,
        long expandedMemberCount
    ) {
    }

    public record ResourcePermissionRequest(
        UUID id,
        String resourceType,
        UUID resourceId,
        UUID requesterId,
        String requesterName,
        String permissionLevel,
        String reason,
        String status,
        UUID decidedBy,
        String decidedByName,
        Instant decidedAt,
        String decisionNote,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record PermissionCatalogItem(
        UUID id,
        String code,
        String name,
        String module,
        String description,
        String riskLevel,
        boolean builtin,
        int displayOrder
    ) {
    }

    public record RoleSummary(
        UUID id,
        String code,
        String name,
        String scope,
        String description,
        String status,
        boolean builtin,
        List<String> permissionCodes,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record RoleDetail(
        UUID id,
        String code,
        String name,
        String scope,
        String description,
        String status,
        boolean builtin,
        List<PermissionCatalogItem> permissions,
        List<RoleAssignmentSummary> assignments,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record RoleAssignmentSummary(
        UUID id,
        UUID roleId,
        String roleCode,
        String roleName,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String subjectDetail,
        String scopeType,
        UUID scopeId,
        Instant effectiveAt,
        Instant expiresAt,
        String status,
        Instant createdAt
    ) {
    }
}
