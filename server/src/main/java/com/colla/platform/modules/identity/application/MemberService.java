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

    public MemberService(
        IdentityRepository identityRepository,
        PermissionService permissionService,
        PasswordHasher passwordHasher,
        PasswordPolicy passwordPolicy,
        AuditService auditService
    ) {
        this.identityRepository = identityRepository;
        this.permissionService = permissionService;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    public List<MemberSummary> listMembers(CurrentUser operator) {
        permissionService.requireManageUsers(operator);
        return identityRepository.listMembers(operator.workspaceId());
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
        String roleCode
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
}
