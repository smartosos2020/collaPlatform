package com.colla.platform.modules.permission.api;

import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionInspectionResult;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskItem;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionCatalogItem;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleAssignmentSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleDetail;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

final class AdminPermissionDtos {
    private AdminPermissionDtos() {
    }

    static AdminPermissionCatalogItemView permission(PermissionCatalogItem permission) {
        return new AdminPermissionCatalogItemView(
            permission.id(),
            permission.code(),
            permission.name(),
            permission.module(),
            permission.description(),
            permission.riskLevel(),
            permission.builtin(),
            permission.displayOrder(),
            new AdminPermissionCategory(permission.module()),
            new AdminRiskBadge(permission.riskLevel(), riskWeight(permission.riskLevel()))
        );
    }

    static AdminRoleView role(RoleSummary role) {
        return new AdminRoleView(
            role.id(),
            role.code(),
            role.name(),
            role.scope(),
            role.description(),
            role.status(),
            role.builtin(),
            role.permissionCodes(),
            role.createdAt(),
            role.updatedAt(),
            roleClassification(role.code(), role.scope(), role.builtin()),
            new AdminRolePermissionMatrix(role.scope(), role.permissionCodes().size(), role.permissionCodes()),
            new AdminRoleAssignmentSummary(0, List.of()),
            new AdminRoleGovernance(role.status(), role.builtin(), role.permissionCodes().size()),
            availableRoleActions(role.builtin())
        );
    }

    static AdminRoleDetailView roleDetail(RoleDetail role) {
        List<AdminPermissionCatalogItemView> permissions = role.permissions().stream()
            .map(AdminPermissionDtos::permission)
            .toList();
        return new AdminRoleDetailView(
            role.id(),
            role.code(),
            role.name(),
            role.scope(),
            role.description(),
            role.status(),
            role.builtin(),
            permissions,
            role.assignments().stream().map(AdminPermissionDtos::assignment).toList(),
            role.createdAt(),
            role.updatedAt(),
            roleClassification(role.code(), role.scope(), role.builtin()),
            permissionGroups(permissions),
            new AdminRoleAssignmentSummary(
                role.assignments().size(),
                role.assignments().stream().map(RoleAssignmentSummary::subjectType).distinct().sorted().toList()
            ),
            new AdminRoleGovernance(role.status(), role.builtin(), permissions.size()),
            availableRoleActions(role.builtin())
        );
    }

    static AdminRoleAssignmentView assignment(RoleAssignmentSummary assignment) {
        return new AdminRoleAssignmentView(
            assignment.id(),
            assignment.roleId(),
            assignment.roleCode(),
            assignment.roleName(),
            assignment.subjectType(),
            assignment.subjectId(),
            assignment.subjectName(),
            assignment.subjectDetail(),
            assignment.scopeType(),
            assignment.scopeId(),
            assignment.effectiveAt(),
            assignment.expiresAt(),
            assignment.status(),
            assignment.createdAt(),
            new AdminAssignmentSubject(assignment.subjectType(), assignment.subjectId(), assignment.subjectName(), assignment.subjectDetail()),
            new AdminAssignmentScope(assignment.scopeType(), assignment.scopeId()),
            new AdminAssignmentLifecycle(assignment.status(), assignment.effectiveAt(), assignment.expiresAt()),
            List.of("revoke")
        );
    }

    static AdminPermissionInspectionView inspection(PermissionInspectionResult result) {
        return new AdminPermissionInspectionView(
            result.userId(),
            result.resourceType(),
            result.resourceId(),
            result.action(),
            result.allowed(),
            result.currentLevel(),
            result.requiredLevel(),
            result.source(),
            result.reason(),
            result.permissionId(),
            new AdminRiskBadge(result.allowed() ? "low" : "medium", result.allowed() ? 1 : 2),
            new AdminImpactScope(result.resourceType(), result.resourceId(), result.userId()),
            result.allowed() ? "monitor" : "review_assignment",
            Map.of("source", nullable(result.source()), "reason", nullable(result.reason()))
        );
    }

    static AdminPermissionRiskSummaryView riskSummary(PermissionRiskSummary summary) {
        List<AdminPermissionRiskItemView> items = summary.items().stream()
            .map(AdminPermissionDtos::riskItem)
            .toList();
        return new AdminPermissionRiskSummaryView(summary.total(), items, riskBuckets(items));
    }

    private static AdminPermissionRiskItemView riskItem(PermissionRiskItem item) {
        return new AdminPermissionRiskItemView(
            item.id(),
            item.ruleCode(),
            item.severity(),
            item.resourceType(),
            item.resourceId(),
            item.subjectType(),
            item.subjectId(),
            item.subjectName(),
            item.permissionLevel(),
            item.reason(),
            item.ruleCode(),
            new AdminImpactScope(item.resourceType(), item.resourceId(), item.subjectId()),
            suggestedRiskAction(item.ruleCode(), item.severity()),
            Map.of(
                "ruleCode", nullable(item.ruleCode()),
                "subjectType", nullable(item.subjectType()),
                "permissionLevel", nullable(item.permissionLevel())
            )
        );
    }

    private static List<AdminPermissionGroup> permissionGroups(List<AdminPermissionCatalogItemView> permissions) {
        return permissions.stream()
            .collect(Collectors.groupingBy(AdminPermissionCatalogItemView::module, java.util.TreeMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> new AdminPermissionGroup(
                entry.getKey(),
                entry.getValue(),
                entry.getValue().size(),
                (int) entry.getValue().stream().filter(permission -> "high".equals(permission.riskLevel())).count()
            ))
            .toList();
    }

    private static Map<String, Long> riskBuckets(List<AdminPermissionRiskItemView> items) {
        return items.stream().collect(Collectors.groupingBy(AdminPermissionRiskItemView::severity, Collectors.counting()));
    }

    private static AdminRoleClassification roleClassification(String code, String scope, boolean builtin) {
        String category = builtin || "workspace".equals(scope) && ("admin".equals(code) || code.startsWith("project_"))
            ? "system_management"
            : "business_collaboration";
        return new AdminRoleClassification(category, scope, builtin);
    }

    private static List<String> availableRoleActions(boolean builtin) {
        return builtin ? List.of("view", "assign") : List.of("edit", "save_permissions", "assign", "delete");
    }

    private static String suggestedRiskAction(String ruleCode, String severity) {
        if ("high".equals(severity)) {
            return "reduce_or_revoke_permission";
        }
        if ("disabled_user_group_active_permission".equals(ruleCode)) {
            return "remove_disabled_subject_grant";
        }
        return "review_permission_source";
    }

    private static int riskWeight(String riskLevel) {
        return switch (riskLevel) {
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    record AdminPermissionCatalogItemView(
        UUID id,
        String code,
        String name,
        String module,
        String description,
        String riskLevel,
        boolean builtin,
        int displayOrder,
        AdminPermissionCategory category,
        AdminRiskBadge risk
    ) {
    }

    record AdminRoleView(
        UUID id,
        String code,
        String name,
        String scope,
        String description,
        String status,
        boolean builtin,
        List<String> permissionCodes,
        Instant createdAt,
        Instant updatedAt,
        AdminRoleClassification roleClassification,
        AdminRolePermissionMatrix permissionMatrix,
        AdminRoleAssignmentSummary assignmentSummary,
        AdminRoleGovernance governance,
        List<String> availableActions
    ) {
    }

    record AdminRoleDetailView(
        UUID id,
        String code,
        String name,
        String scope,
        String description,
        String status,
        boolean builtin,
        List<AdminPermissionCatalogItemView> permissions,
        List<AdminRoleAssignmentView> assignments,
        Instant createdAt,
        Instant updatedAt,
        AdminRoleClassification roleClassification,
        List<AdminPermissionGroup> permissionMatrix,
        AdminRoleAssignmentSummary assignmentSummary,
        AdminRoleGovernance governance,
        List<String> availableActions
    ) {
    }

    record AdminRoleClassification(String category, String scope, boolean builtin) {
    }

    record AdminRolePermissionMatrix(String scope, int permissionCount, List<String> permissionCodes) {
    }

    record AdminPermissionGroup(
        String module,
        List<AdminPermissionCatalogItemView> permissions,
        int permissionCount,
        int highRiskCount
    ) {
    }

    record AdminRoleAssignmentSummary(int total, List<String> subjectTypes) {
    }

    record AdminRoleGovernance(String status, boolean builtin, int permissionCount) {
    }

    record AdminRoleAssignmentView(
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
        Instant createdAt,
        AdminAssignmentSubject subject,
        AdminAssignmentScope scope,
        AdminAssignmentLifecycle lifecycle,
        List<String> availableActions
    ) {
    }

    record AdminAssignmentSubject(String subjectType, UUID subjectId, String subjectName, String subjectDetail) {
    }

    record AdminAssignmentScope(String scopeType, UUID scopeId) {
    }

    record AdminAssignmentLifecycle(String status, Instant effectiveAt, Instant expiresAt) {
    }

    record AdminPermissionInspectionView(
        UUID userId,
        String resourceType,
        UUID resourceId,
        String action,
        boolean allowed,
        String currentLevel,
        String requiredLevel,
        String source,
        String reason,
        UUID permissionId,
        AdminRiskBadge risk,
        AdminImpactScope impactScope,
        String suggestedAction,
        Map<String, String> auditContext
    ) {
    }

    record AdminPermissionRiskSummaryView(
        int total,
        List<AdminPermissionRiskItemView> items,
        Map<String, Long> severityBuckets
    ) {
    }

    record AdminPermissionRiskItemView(
        String id,
        String ruleCode,
        String severity,
        String resourceType,
        UUID resourceId,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String permissionLevel,
        String reason,
        String source,
        AdminImpactScope impactScope,
        String suggestedAction,
        Map<String, String> auditContext
    ) {
    }

    record AdminPermissionCategory(String module) {
    }

    record AdminRiskBadge(String level, int weight) {
    }

    record AdminImpactScope(String resourceType, UUID resourceId, UUID subjectId) {
    }
}
