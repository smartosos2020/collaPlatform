package com.colla.platform.modules.identity.api;

import com.colla.platform.modules.identity.domain.AuthModels.MemberDepartment;
import com.colla.platform.modules.identity.domain.AuthModels.MemberSummary;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentManager;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentMember;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentSummary;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentTreeNode;
import com.colla.platform.modules.identity.domain.UserGroupModels.ExpandedUserGroupMember;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupMember;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupSummary;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class AdminIdentityDtos {
    private AdminIdentityDtos() {
    }

    static AdminMemberView member(MemberSummary member) {
        MemberDepartment primaryDepartment = member.departments().stream()
            .filter(department -> "primary".equals(department.relationType()))
            .findFirst()
            .orElse(null);
        return new AdminMemberView(
            member.id(),
            member.username(),
            member.displayName(),
            member.avatarFileId(),
            member.email(),
            member.status(),
            member.lastLoginAt(),
            member.createdAt(),
            member.roles(),
            member.departments(),
            new AdminMemberProfile(member.id(), member.displayName(), member.avatarFileId(), member.email()),
            new AdminLoginAccount(member.username(), member.status(), member.lastLoginAt()),
            new AdminMemberOrganization(
                primaryDepartment == null ? null : primaryDepartment.departmentId(),
                primaryDepartment == null ? null : primaryDepartment.departmentCode(),
                primaryDepartment == null ? null : primaryDepartment.departmentName(),
                member.departments()
            ),
            new AdminMemberManagementState(member.status(), member.roles(), member.createdAt(), member.lastLoginAt()),
            enabledActions(member.status(), List.of("reset_password", "update_avatar"))
        );
    }

    static AdminDepartmentTreeNode treeNode(DepartmentTreeNode node) {
        return new AdminDepartmentTreeNode(
            department(node.department()),
            node.managers().stream().map(AdminIdentityDtos::departmentManager).toList(),
            node.children().stream().map(AdminIdentityDtos::treeNode).toList()
        );
    }

    static AdminDepartmentView department(DepartmentSummary department) {
        return new AdminDepartmentView(
            department.id(),
            department.parentId(),
            department.code(),
            department.name(),
            department.path(),
            department.depth(),
            department.sortOrder(),
            department.status(),
            department.memberCount(),
            department.managerCount(),
            department.createdAt(),
            department.updatedAt(),
            new AdminDepartmentHierarchy(department.parentId(), department.path(), department.depth(), department.sortOrder()),
            new AdminDepartmentMembership(department.memberCount(), department.managerCount()),
            new AdminGovernanceState(department.status(), "department", department.id()),
            new AdminAuditSnapshot(department.createdAt(), department.updatedAt()),
            enabledActions(department.status(), List.of("edit", "move", "delete"))
        );
    }

    static AdminDepartmentMemberView departmentMember(DepartmentMember member) {
        return new AdminDepartmentMemberView(
            member.id(),
            member.departmentId(),
            member.userId(),
            member.username(),
            member.displayName(),
            member.email(),
            member.relationType(),
            member.status(),
            member.startedAt(),
            member.endedAt(),
            new AdminMemberProfile(member.userId(), member.displayName(), null, member.email()),
            new AdminGovernanceState(member.status(), "department_member", member.id()),
            enabledActions(member.status(), List.of("remove"))
        );
    }

    static AdminDepartmentManagerView departmentManager(DepartmentManager manager) {
        return new AdminDepartmentManagerView(
            manager.id(),
            manager.departmentId(),
            manager.userId(),
            manager.username(),
            manager.displayName(),
            manager.managerType(),
            manager.createdAt(),
            new AdminMemberProfile(manager.userId(), manager.displayName(), null, null),
            new AdminAuditSnapshot(manager.createdAt(), manager.createdAt()),
            List.of("remove")
        );
    }

    static AdminUserGroupView userGroup(UserGroupSummary group) {
        return new AdminUserGroupView(
            group.id(),
            group.code(),
            group.name(),
            group.description(),
            group.groupType(),
            group.status(),
            group.directMemberCount(),
            group.expandedMemberCount(),
            group.createdAt(),
            group.updatedAt(),
            new AdminUserGroupExpansion(group.directMemberCount(), group.expandedMemberCount()),
            new AdminAuthorizationSubject("user_group", group.id(), group.name(), group.code()),
            new AdminGovernanceState(group.status(), "user_group", group.id()),
            new AdminAuditSnapshot(group.createdAt(), group.updatedAt()),
            enabledActions(group.status(), List.of("edit", "delete", "manage_members"))
        );
    }

    static AdminUserGroupMemberView userGroupMember(UserGroupMember member) {
        return new AdminUserGroupMemberView(
            member.id(),
            member.groupId(),
            member.subjectType(),
            member.subjectId(),
            member.subjectName(),
            member.subjectDetail(),
            member.subjectStatus(),
            member.createdAt(),
            new AdminAuthorizationSubject(member.subjectType(), member.subjectId(), member.subjectName(), member.subjectDetail()),
            new AdminGovernanceState(member.subjectStatus(), member.subjectType(), member.subjectId()),
            new AdminAuditSnapshot(member.createdAt(), member.createdAt()),
            List.of("remove")
        );
    }

    static AdminExpandedUserGroupMemberView expandedUserGroupMember(ExpandedUserGroupMember member) {
        return new AdminExpandedUserGroupMemberView(
            member.userId(),
            member.username(),
            member.displayName(),
            member.email(),
            member.status(),
            member.sourceType(),
            member.sourceId(),
            member.sourceName(),
            new AdminMemberProfile(member.userId(), member.displayName(), null, member.email()),
            new AdminExpansionSource(member.sourceType(), member.sourceId(), member.sourceName()),
            new AdminGovernanceState(member.status(), "user", member.userId())
        );
    }

    private static List<String> enabledActions(String status, List<String> baseActions) {
        return "disabled".equals(status)
            ? append(baseActions, "enable")
            : append(baseActions, "disable");
    }

    private static List<String> append(List<String> baseActions, String action) {
        return java.util.stream.Stream.concat(baseActions.stream(), java.util.stream.Stream.of(action)).toList();
    }

    record AdminMemberView(
        UUID id,
        String username,
        String displayName,
        UUID avatarFileId,
        String email,
        String status,
        Instant lastLoginAt,
        Instant createdAt,
        Set<String> roles,
        List<MemberDepartment> departments,
        AdminMemberProfile profile,
        AdminLoginAccount account,
        AdminMemberOrganization organization,
        AdminMemberManagementState management,
        List<String> availableActions
    ) {
    }

    record AdminMemberProfile(UUID userId, String displayName, UUID avatarFileId, String email) {
    }

    record AdminLoginAccount(String username, String status, Instant lastLoginAt) {
    }

    record AdminMemberOrganization(
        UUID primaryDepartmentId,
        String primaryDepartmentCode,
        String primaryDepartmentName,
        List<MemberDepartment> departments
    ) {
    }

    record AdminMemberManagementState(String status, Set<String> roles, Instant createdAt, Instant lastLoginAt) {
    }

    record AdminDepartmentTreeNode(
        AdminDepartmentView department,
        List<AdminDepartmentManagerView> managers,
        List<AdminDepartmentTreeNode> children
    ) {
    }

    record AdminDepartmentView(
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
        Instant updatedAt,
        AdminDepartmentHierarchy hierarchy,
        AdminDepartmentMembership membership,
        AdminGovernanceState governance,
        AdminAuditSnapshot audit,
        List<String> availableActions
    ) {
    }

    record AdminDepartmentHierarchy(UUID parentId, String path, int depth, int sortOrder) {
    }

    record AdminDepartmentMembership(int memberCount, int managerCount) {
    }

    record AdminDepartmentMemberView(
        UUID id,
        UUID departmentId,
        UUID userId,
        String username,
        String displayName,
        String email,
        String relationType,
        String status,
        Instant startedAt,
        Instant endedAt,
        AdminMemberProfile profile,
        AdminGovernanceState governance,
        List<String> availableActions
    ) {
    }

    record AdminDepartmentManagerView(
        UUID id,
        UUID departmentId,
        UUID userId,
        String username,
        String displayName,
        String managerType,
        Instant createdAt,
        AdminMemberProfile profile,
        AdminAuditSnapshot audit,
        List<String> availableActions
    ) {
    }

    record AdminUserGroupView(
        UUID id,
        String code,
        String name,
        String description,
        String groupType,
        String status,
        int directMemberCount,
        int expandedMemberCount,
        Instant createdAt,
        Instant updatedAt,
        AdminUserGroupExpansion memberExpansion,
        AdminAuthorizationSubject authorizationSubject,
        AdminGovernanceState governance,
        AdminAuditSnapshot audit,
        List<String> availableActions
    ) {
    }

    record AdminUserGroupExpansion(int directMemberCount, int expandedMemberCount) {
    }

    record AdminUserGroupMemberView(
        UUID id,
        UUID groupId,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String subjectDetail,
        String subjectStatus,
        Instant createdAt,
        AdminAuthorizationSubject authorizationSubject,
        AdminGovernanceState governance,
        AdminAuditSnapshot audit,
        List<String> availableActions
    ) {
    }

    record AdminExpandedUserGroupMemberView(
        UUID userId,
        String username,
        String displayName,
        String email,
        String status,
        String sourceType,
        UUID sourceId,
        String sourceName,
        AdminMemberProfile profile,
        AdminExpansionSource expansionSource,
        AdminGovernanceState governance
    ) {
    }

    record AdminAuthorizationSubject(String subjectType, UUID subjectId, String subjectName, String subjectDetail) {
    }

    record AdminExpansionSource(String sourceType, UUID sourceId, String sourceName) {
    }

    record AdminGovernanceState(String status, String managedObjectType, UUID managedObjectId) {
    }

    record AdminAuditSnapshot(Instant createdAt, Instant updatedAt) {
    }
}
