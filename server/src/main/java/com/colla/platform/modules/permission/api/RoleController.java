package com.colla.platform.modules.permission.api;

import com.colla.platform.modules.permission.application.RoleService;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionCatalogItem;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleAssignmentSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleDetail;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleSummary;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/permissions")
    public List<PermissionCatalogItem> permissions(Authentication authentication) {
        return roleService.listPermissions(currentUser(authentication));
    }

    @GetMapping("/roles")
    public List<RoleSummary> roles(Authentication authentication) {
        return roleService.listRoles(currentUser(authentication));
    }

    @PostMapping("/roles")
    public RoleSummary createRole(@Valid @RequestBody RoleRequest request, Authentication authentication) {
        return roleService.createRole(
            currentUser(authentication),
            request.code(),
            request.name(),
            request.scope(),
            request.description()
        );
    }

    @GetMapping("/roles/{roleId}")
    public RoleDetail role(@PathVariable UUID roleId, Authentication authentication) {
        return roleService.getRole(currentUser(authentication), roleId);
    }

    @PatchMapping("/roles/{roleId}")
    public RoleSummary updateRole(
        @PathVariable UUID roleId,
        @Valid @RequestBody UpdateRoleRequest request,
        Authentication authentication
    ) {
        return roleService.updateRole(
            currentUser(authentication),
            roleId,
            request.name(),
            request.scope(),
            request.description(),
            request.status()
        );
    }

    @PutMapping("/roles/{roleId}/permissions")
    public RoleDetail replaceRolePermissions(
        @PathVariable UUID roleId,
        @Valid @RequestBody RolePermissionsRequest request,
        Authentication authentication
    ) {
        return roleService.replaceRolePermissions(
            currentUser(authentication),
            roleId,
            request.permissionCodes(),
            request.confirmHighRisk()
        );
    }

    @GetMapping("/role-assignments")
    public List<RoleAssignmentSummary> assignments(
        @RequestParam(required = false) UUID roleId,
        Authentication authentication
    ) {
        return roleService.listAssignments(currentUser(authentication), roleId);
    }

    @PostMapping("/role-assignments")
    public RoleAssignmentSummary createAssignment(
        @Valid @RequestBody RoleAssignmentRequest request,
        Authentication authentication
    ) {
        return roleService.createAssignment(
            currentUser(authentication),
            request.roleId(),
            request.subjectType(),
            request.subjectId(),
            request.scopeType(),
            request.scopeId(),
            request.effectiveAt(),
            request.expiresAt(),
            request.confirmHighRisk()
        );
    }

    @DeleteMapping("/role-assignments/{assignmentId}")
    public void revokeAssignment(@PathVariable UUID assignmentId, Authentication authentication) {
        roleService.revokeAssignment(currentUser(authentication), assignmentId);
    }

    @PostMapping("/role-assignments/{assignmentId}/revoke")
    public void revokeAssignmentByPost(@PathVariable UUID assignmentId, Authentication authentication) {
        roleService.revokeAssignment(currentUser(authentication), assignmentId);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record RoleRequest(@NotBlank String code, @NotBlank String name, String scope, String description) {
    }

    public record UpdateRoleRequest(@NotBlank String name, String scope, String description, String status) {
    }

    public record RolePermissionsRequest(List<@NotBlank String> permissionCodes, boolean confirmHighRisk) {
    }

    public record RoleAssignmentRequest(
        @NotNull UUID roleId,
        @NotBlank String subjectType,
        @NotNull UUID subjectId,
        String scopeType,
        UUID scopeId,
        Instant effectiveAt,
        Instant expiresAt,
        boolean confirmHighRisk
    ) {
    }
}
