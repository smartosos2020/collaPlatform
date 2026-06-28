package com.colla.platform.modules.identity.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrganizationModels {
    private OrganizationModels() {
    }

    public record DepartmentSummary(
        UUID id,
        UUID parentId,
        String code,
        String name,
        String path,
        int depth,
        int sortOrder,
        String status,
        int memberCount,
        int managerCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record DepartmentTreeNode(
        DepartmentSummary department,
        List<DepartmentManager> managers,
        List<DepartmentTreeNode> children
    ) {
    }

    public record DepartmentMember(
        UUID id,
        UUID departmentId,
        UUID userId,
        String username,
        String displayName,
        String email,
        String relationType,
        String status,
        Instant startedAt,
        Instant endedAt
    ) {
    }

    public record DepartmentManager(
        UUID id,
        UUID departmentId,
        UUID userId,
        String username,
        String displayName,
        String managerType,
        Instant createdAt
    ) {
    }
}
