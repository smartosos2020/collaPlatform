package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.identity.domain.AuthModels.UserAccount;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentSummary;
import com.colla.platform.modules.identity.domain.UserGroupModels.ExpandedUserGroupMember;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupMember;
import com.colla.platform.modules.identity.domain.UserGroupModels.UserGroupSummary;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.identity.infrastructure.OrganizationRepository;
import com.colla.platform.modules.identity.infrastructure.UserGroupRepository;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserGroupService {
    private final UserGroupRepository userGroupRepository;
    private final IdentityRepository identityRepository;
    private final OrganizationRepository organizationRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public UserGroupService(
        UserGroupRepository userGroupRepository,
        IdentityRepository identityRepository,
        OrganizationRepository organizationRepository,
        PermissionService permissionService,
        AuditService auditService
    ) {
        this.userGroupRepository = userGroupRepository;
        this.identityRepository = identityRepository;
        this.organizationRepository = organizationRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public List<UserGroupSummary> listGroups(CurrentUser operator, boolean activeOnly) {
        permissionService.requireViewUserGroups(operator);
        return userGroupRepository.listGroups(operator.workspaceId(), activeOnly);
    }

    public UserGroupSummary getGroup(CurrentUser operator, UUID groupId) {
        permissionService.requireViewUserGroups(operator);
        return requireGroup(operator.workspaceId(), groupId);
    }

    public List<UserGroupMember> listMembers(CurrentUser operator, UUID groupId) {
        permissionService.requireViewUserGroups(operator);
        requireGroup(operator.workspaceId(), groupId);
        return userGroupRepository.listMembers(operator.workspaceId(), groupId);
    }

    public List<ExpandedUserGroupMember> listExpandedMembers(CurrentUser operator, UUID groupId) {
        permissionService.requireViewUserGroups(operator);
        requireGroup(operator.workspaceId(), groupId);
        return userGroupRepository.listExpandedMembers(operator.workspaceId(), groupId);
    }

    @Transactional
    public UserGroupSummary createGroup(CurrentUser operator, String code, String name, String description, String groupType) {
        permissionService.requireManageUserGroups(operator);
        try {
            UUID groupId = userGroupRepository.createGroup(
                operator.workspaceId(),
                normalizeCode(code),
                normalizeName(name),
                normalizeDescription(description),
                normalizeGroupType(groupType),
                operator.id()
            );
            auditService.log(operator, "usergroup.created", "user_group", groupId, Map.of("code", code));
            return requireGroup(operator.workspaceId(), groupId);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User group code already exists");
        }
    }

    @Transactional
    public UserGroupSummary updateGroup(
        CurrentUser operator,
        UUID groupId,
        String code,
        String name,
        String description,
        String groupType
    ) {
        permissionService.requireManageUserGroups(operator);
        requireGroup(operator.workspaceId(), groupId);
        try {
            userGroupRepository.updateGroup(
                operator.workspaceId(),
                groupId,
                normalizeCode(code),
                normalizeName(name),
                normalizeDescription(description),
                normalizeGroupType(groupType),
                operator.id()
            );
            auditService.log(operator, "usergroup.updated", "user_group", groupId, Map.of("code", code));
            return requireGroup(operator.workspaceId(), groupId);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User group code already exists");
        }
    }

    @Transactional
    public void disableGroup(CurrentUser operator, UUID groupId) {
        permissionService.requireManageUserGroups(operator);
        requireGroup(operator.workspaceId(), groupId);
        userGroupRepository.disableGroup(operator.workspaceId(), groupId, operator.id());
        auditService.log(operator, "usergroup.disabled", "user_group", groupId, Map.of());
    }

    @Transactional
    public void enableGroup(CurrentUser operator, UUID groupId) {
        permissionService.requireManageUserGroups(operator);
        requireGroup(operator.workspaceId(), groupId);
        userGroupRepository.enableGroup(operator.workspaceId(), groupId, operator.id());
        auditService.log(operator, "usergroup.enabled", "user_group", groupId, Map.of());
    }

    @Transactional
    public void deleteGroup(CurrentUser operator, UUID groupId) {
        permissionService.requireManageUserGroups(operator);
        requireGroup(operator.workspaceId(), groupId);
        if (userGroupRepository.hasActiveMembers(operator.workspaceId(), groupId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User group with members cannot be deleted");
        }
        userGroupRepository.deleteGroup(operator.workspaceId(), groupId);
        auditService.log(operator, "usergroup.deleted", "user_group", groupId, Map.of());
    }

    @Transactional
    public UserGroupMember addMember(CurrentUser operator, UUID groupId, String subjectType, UUID subjectId) {
        permissionService.requireManageUserGroups(operator);
        UserGroupSummary group = requireGroup(operator.workspaceId(), groupId);
        if (!"active".equals(group.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disabled user group cannot be changed");
        }
        String normalizedSubjectType = normalizeSubjectType(subjectType);
        validateSubject(operator.workspaceId(), normalizedSubjectType, subjectId);
        UUID memberId = userGroupRepository.addMember(operator.workspaceId(), groupId, normalizedSubjectType, subjectId, operator.id());
        auditService.log(
            operator,
            "usergroup.member.added",
            "user_group",
            groupId,
            Map.of("subjectType", normalizedSubjectType, "subjectId", subjectId)
        );
        return userGroupRepository.listMembers(operator.workspaceId(), groupId).stream()
            .filter(member -> member.id().equals(memberId))
            .findFirst()
            .orElseGet(() -> userGroupRepository.listMembers(operator.workspaceId(), groupId).stream()
                .filter(member -> member.subjectType().equals(normalizedSubjectType) && member.subjectId().equals(subjectId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User group member not found")));
    }

    @Transactional
    public void removeMember(CurrentUser operator, UUID groupId, UUID memberId) {
        permissionService.requireManageUserGroups(operator);
        requireGroup(operator.workspaceId(), groupId);
        userGroupRepository.removeMember(operator.workspaceId(), groupId, memberId);
        auditService.log(operator, "usergroup.member.removed", "user_group", groupId, Map.of("memberId", memberId));
    }

    private UserGroupSummary requireGroup(UUID workspaceId, UUID groupId) {
        return userGroupRepository.findGroup(workspaceId, groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User group not found"));
    }

    private void validateSubject(UUID workspaceId, String subjectType, UUID subjectId) {
        if (subjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject id is required");
        }
        if ("user".equals(subjectType)) {
            UserAccount user = identityRepository.findUserById(subjectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            if (!workspaceId.equals(user.workspaceId()) || !"active".equals(user.status())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            return;
        }
        DepartmentSummary department = organizationRepository.findDepartment(workspaceId, subjectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        if (!"active".equals(department.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is disabled");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User group code is required");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User group name is required");
        }
        return name.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private String normalizeGroupType(String groupType) {
        String normalized = groupType == null || groupType.isBlank() ? "normal" : groupType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("normal", "permission").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported user group type");
        }
        return normalized;
    }

    private String normalizeSubjectType(String subjectType) {
        String normalized = subjectType == null ? "" : subjectType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("user", "department").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported user group member subject type");
        }
        return normalized;
    }
}
