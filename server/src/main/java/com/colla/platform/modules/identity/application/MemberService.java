package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.identity.domain.AuthModels.MemberSummary;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.PasswordPolicy;
import com.colla.platform.shared.auth.PasswordHasher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberService {
    private final IdentityRepository identityRepository;
    private final PermissionService permissionService;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;
    private final OrganizationService organizationService;
    private final JdbcTemplate jdbcTemplate;

    public MemberService(
        IdentityRepository identityRepository,
        PermissionService permissionService,
        PasswordHasher passwordHasher,
        PasswordPolicy passwordPolicy,
        AuditService auditService,
        OrganizationService organizationService,
        JdbcTemplate jdbcTemplate
    ) {
        this.identityRepository = identityRepository;
        this.permissionService = permissionService;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
        this.organizationService = organizationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MemberSummary> listMembers(CurrentUser operator, UUID departmentId) {
        permissionService.requireManageUsers(operator);
        return identityRepository.listMembers(operator.workspaceId(), departmentId);
    }

    public List<MemberSummary> listWorkspaceMembers(CurrentUser operator) {
        return identityRepository.listMembers(operator.workspaceId()).stream()
            .filter(member -> "active".equals(member.status()))
            .toList();
    }

    @Transactional
    public MemberSummary createMember(
        CurrentUser operator,
        String username,
        String password,
        String displayName,
        String email,
        String roleCode,
        UUID primaryDepartmentId
    ) {
        permissionService.requireManageUsers(operator);
        passwordPolicy.validate(password);
        if (identityRepository.findUserByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        UUID userId = identityRepository.createUser(
            operator.workspaceId(),
            username,
            passwordHasher.hash(password),
            displayName,
            email,
            operator.id()
        );
        identityRepository.assignRole(operator.workspaceId(), userId, roleCode == null || roleCode.isBlank() ? "member" : roleCode, operator.id());
        auditService.log(operator, "user.created", "user", userId, Map.of("username", username, "roleCode", roleCode == null ? "member" : roleCode));
        if (primaryDepartmentId != null) {
            organizationService.addMember(operator, primaryDepartmentId, userId, "primary");
        }
        return identityRepository.listMembers(operator.workspaceId()).stream()
            .filter(member -> member.id().equals(userId))
            .findFirst()
            .orElseThrow();
    }

    @Transactional
    public void disableMember(CurrentUser operator, UUID userId) {
        permissionService.requireManageUsers(operator);
        if (operator.id().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot disable current user");
        }
        identityRepository.updateUserStatus(operator.workspaceId(), userId, "disabled", operator.id());
        auditService.log(operator, "user.disabled", "user", userId, Map.of());
    }

    @Transactional
    public OffboardingResult offboardMember(CurrentUser operator, UUID userId, UUID handoverToUserId) {
        permissionService.requireManageUsers(operator);
        if (operator.id().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot offboard current user");
        }
        if (userId.equals(handoverToUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Handover user must be different");
        }
        identityRepository.findUserById(userId)
            .filter(user -> user.workspaceId().equals(operator.workspaceId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        identityRepository.findUserById(handoverToUserId)
            .filter(user -> user.workspaceId().equals(operator.workspaceId()) && "active".equals(user.status()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Handover member must be active in this workspace"));

        int knowledgeBaseCount = jdbcTemplate.update(
            """
                update knowledge_base_spaces
                set owner_id = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and owner_id = ? and deleted_at is null
                """,
            handoverToUserId, operator.id(), operator.workspaceId(), userId
        );
        int conversationCount = jdbcTemplate.update(
            """
                update conversations
                set owner_id = ?, updated_at = now()
                where workspace_id = ? and owner_id = ? and archived_at is null
                """,
            handoverToUserId, operator.workspaceId(), userId
        );
        identityRepository.updateUserStatus(operator.workspaceId(), userId, "disabled", operator.id());
        auditService.log(operator, "user.offboarded", "user", userId, Map.of(
            "handoverToUserId", handoverToUserId.toString(),
            "knowledgeBaseCount", knowledgeBaseCount,
            "conversationCount", conversationCount
        ));
        return new OffboardingResult(knowledgeBaseCount, conversationCount);
    }

    @Transactional
    public void enableMember(CurrentUser operator, UUID userId) {
        permissionService.requireManageUsers(operator);
        identityRepository.updateUserStatus(operator.workspaceId(), userId, "active", operator.id());
        auditService.log(operator, "user.enabled", "user", userId, Map.of());
    }

    @Transactional
    public void resetPassword(CurrentUser operator, UUID userId, String newPassword) {
        permissionService.requireManageUsers(operator);
        passwordPolicy.validate(newPassword);
        identityRepository.updatePassword(operator.workspaceId(), userId, passwordHasher.hash(newPassword), operator.id());
        auditService.log(operator, "user.password.reset", "user", userId, Map.of());
    }

    @Transactional
    public void updateAvatar(CurrentUser operator, UUID userId, UUID avatarFileId) {
        permissionService.requireManageUsers(operator);
        identityRepository.updateAvatarFileId(operator.workspaceId(), userId, avatarFileId, operator.id());
        auditService.log(operator, "user.avatar.updated", "user", userId, Map.of("avatarFileId", avatarFileId == null ? "" : avatarFileId));
    }

    public record OffboardingResult(int knowledgeBaseCount, int conversationCount) {
    }
}
