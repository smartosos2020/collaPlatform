package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionCatalogItem;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleAssignmentSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleDetail;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleSummary;
import com.colla.platform.modules.permission.infrastructure.RoleRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoleService {
    private static final Set<String> SUBJECT_TYPES = Set.of("user", "department", "user_group");

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, PermissionService permissionService, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public List<PermissionCatalogItem> listPermissions(CurrentUser currentUser) {
        permissionService.requireInspectPermissions(currentUser);
        return roleRepository.listPermissions();
    }

    public List<RoleSummary> listRoles(CurrentUser currentUser) {
        permissionService.requireViewRoles(currentUser);
        return roleRepository.listRoles(currentUser.workspaceId());
    }

    public RoleDetail getRole(CurrentUser currentUser, UUID roleId) {
        permissionService.requireViewRoles(currentUser);
        return roleRepository.findRole(currentUser.workspaceId(), roleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    @Transactional
    public RoleSummary createRole(CurrentUser currentUser, String code, String name, String scope, String description) {
        permissionService.requireManageRoles(currentUser);
        String normalizedCode = normalizeCode(code);
        RoleSummary role = roleRepository.createRole(
            currentUser.workspaceId(),
            normalizedCode,
            requireText(name, "Role name is required"),
            normalizeScope(scope),
            trimToNull(description),
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "role.created",
            "role",
            role.id(),
            Map.of("code", role.code(), "name", role.name(), "scope", role.scope())
        );
        return role;
    }

    @Transactional
    public RoleSummary updateRole(
        CurrentUser currentUser,
        UUID roleId,
        String name,
        String scope,
        String description,
        String status
    ) {
        permissionService.requireManageRoles(currentUser);
        RoleSummary existing = requireRole(currentUser.workspaceId(), roleId);
        RoleSummary role = roleRepository.updateRole(
            currentUser.workspaceId(),
            roleId,
            requireText(name, "Role name is required"),
            normalizeScope(scope),
            trimToNull(description),
            normalizeStatus(status),
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "role.updated",
            "role",
            role.id(),
            Map.of(
                "code", existing.code(),
                "builtin", existing.builtin(),
                "status", role.status()
            )
        );
        return role;
    }

    @Transactional
    public RoleDetail replaceRolePermissions(
        CurrentUser currentUser,
        UUID roleId,
        List<String> permissionCodes,
        boolean confirmHighRisk
    ) {
        permissionService.requireManageRoles(currentUser);
        requireRole(currentUser.workspaceId(), roleId);
        List<String> codes = normalizePermissionCodes(permissionCodes);
        List<PermissionCatalogItem> permissions = roleRepository.listPermissionsByCodes(codes);
        if (permissions.size() != codes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission code");
        }
        boolean highRisk = containsHighRisk(permissions);
        requireHighRiskConfirmation(highRisk, confirmHighRisk);
        roleRepository.replaceRolePermissions(currentUser.workspaceId(), roleId, codes);
        auditService.log(
            currentUser,
            "role.permissions.updated",
            "role",
            roleId,
            Map.of("permissionCodes", codes, "highRisk", highRisk)
        );
        return getRole(currentUser, roleId);
    }

    public List<RoleAssignmentSummary> listAssignments(CurrentUser currentUser, UUID roleId) {
        permissionService.requireViewRoles(currentUser);
        if (roleId != null) {
            requireRole(currentUser.workspaceId(), roleId);
        }
        return roleRepository.listAssignments(currentUser.workspaceId(), roleId);
    }

    @Transactional
    public RoleAssignmentSummary createAssignment(
        CurrentUser currentUser,
        UUID roleId,
        String subjectType,
        UUID subjectId,
        String scopeType,
        UUID scopeId,
        Instant effectiveAt,
        Instant expiresAt,
        boolean confirmHighRisk
    ) {
        permissionService.requireManageRoles(currentUser);
        requireRole(currentUser.workspaceId(), roleId);
        String normalizedSubjectType = normalizeSubjectType(subjectType);
        if (!roleRepository.subjectExists(currentUser.workspaceId(), normalizedSubjectType, subjectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role assignment subject not found");
        }
        boolean highRisk = containsHighRisk(roleRepository.findRole(currentUser.workspaceId(), roleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"))
            .permissions());
        requireHighRiskConfirmation(highRisk, confirmHighRisk);
        RoleAssignmentSummary assignment = roleRepository.createAssignment(
            currentUser.workspaceId(),
            roleId,
            normalizedSubjectType,
            subjectId,
            normalizeScopeType(scopeType),
            scopeId,
            effectiveAt,
            expiresAt,
            currentUser.id()
        );
        auditService.log(
            currentUser,
            "role.assignment.created",
            "role_assignment",
            assignment.id(),
            Map.of(
                "roleId", roleId,
                "subjectType", assignment.subjectType(),
                "subjectId", assignment.subjectId(),
                "highRisk", highRisk
            )
        );
        return assignment;
    }

    @Transactional
    public void revokeAssignment(CurrentUser currentUser, UUID assignmentId) {
        permissionService.requireManageRoles(currentUser);
        if (!roleRepository.revokeAssignment(currentUser.workspaceId(), assignmentId, currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role assignment not found");
        }
        auditService.log(
            currentUser,
            "role.assignment.revoked",
            "role_assignment",
            assignmentId,
            Map.of()
        );
    }

    private RoleSummary requireRole(UUID workspaceId, UUID roleId) {
        return roleRepository.findRoleSummary(workspaceId, roleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null) {
            return List.of();
        }
        return permissionCodes.stream()
            .map(this::requireText)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));
    }

    private String normalizeCode(String code) {
        String value = requireText(code, "Role code is required").toLowerCase();
        if (!value.matches("[a-z][a-z0-9_]{2,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role code must be snake_case and 3-64 characters");
        }
        return value;
    }

    private String normalizeScope(String scope) {
        String value = trimToNull(scope);
        return value == null ? "workspace" : value;
    }

    private String normalizeScopeType(String scopeType) {
        String value = trimToNull(scopeType);
        return value == null ? "system" : value;
    }

    private String normalizeStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return "active";
        }
        if (!Set.of("active", "disabled").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role status");
        }
        return value;
    }

    private String normalizeSubjectType(String subjectType) {
        String value = requireText(subjectType, "Subject type is required");
        if (!SUBJECT_TYPES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role assignment subject type");
        }
        return value;
    }

    private boolean containsHighRisk(List<PermissionCatalogItem> permissions) {
        return permissions.stream().anyMatch(permission -> Set.of("high", "critical").contains(permission.riskLevel()));
    }

    private void requireHighRiskConfirmation(boolean highRisk, boolean confirmHighRisk) {
        if (highRisk && !confirmHighRisk) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "High-risk permission change requires confirmation");
        }
    }

    private String requireText(String value) {
        return requireText(value, "Value is required");
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
