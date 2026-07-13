package com.colla.platform.modules.permission.domain;

import java.util.List;
import java.util.UUID;

public final class PermissionGovernanceModels {
    private PermissionGovernanceModels() {
    }

    public record PermissionInspectionResult(
        UUID userId,
        String resourceType,
        UUID resourceId,
        String action,
        boolean allowed,
        String currentLevel,
        String requiredLevel,
        String source,
        String reason,
        UUID permissionId
    ) {
    }

    public record PermissionRiskItem(
        String id,
        String ruleCode,
        String severity,
        String resourceType,
        UUID resourceId,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String permissionLevel,
        String reason
    ) {
    }

    public record PermissionRiskSummary(int total, List<PermissionRiskItem> items) {
    }

    public record PermissionMatrixEntry(
        String module,
        String resourceType,
        String action,
        String requiredLevel,
        String allowedExpectation,
        String deniedExpectation,
        String auditExpectation
    ) {
    }

    public record PermissionRiskRemediation(
        String riskId,
        String ruleCode,
        boolean executable,
        boolean applied,
        String action,
        String reason
    ) {
    }
}
