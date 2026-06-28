package com.colla.platform.modules.permission.api;

import com.colla.platform.modules.permission.application.ResourcePermissionManagementService;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionEntry;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resource-permissions")
public class ResourcePermissionController {
    private final ResourcePermissionManagementService resourcePermissionManagementService;

    public ResourcePermissionController(ResourcePermissionManagementService resourcePermissionManagementService) {
        this.resourcePermissionManagementService = resourcePermissionManagementService;
    }

    @GetMapping("/{resourceType}/{resourceId}")
    public List<ResourcePermissionEntry> list(
        @PathVariable String resourceType,
        @PathVariable UUID resourceId,
        Authentication authentication
    ) {
        return resourcePermissionManagementService.list(currentUser(authentication), resourceType, resourceId);
    }

    @PostMapping("/{resourceType}/{resourceId}")
    public ResourcePermissionEntry grant(
        @PathVariable String resourceType,
        @PathVariable UUID resourceId,
        @Valid @RequestBody GrantResourcePermissionRequest request,
        Authentication authentication
    ) {
        return resourcePermissionManagementService.grant(
            currentUser(authentication),
            resourceType,
            resourceId,
            request.subjectType(),
            request.subjectId(),
            request.permissionLevel(),
            request.expiresAt(),
            request.confirmHighRisk()
        );
    }

    @DeleteMapping("/{permissionId}")
    public void revoke(
        @PathVariable UUID permissionId,
        @Valid @RequestBody RevokeResourcePermissionRequest request,
        Authentication authentication
    ) {
        resourcePermissionManagementService.revoke(currentUser(authentication), permissionId, request.confirmHighRisk());
    }

    @PostMapping("/{permissionId}/revoke")
    public void revokeByPost(
        @PathVariable UUID permissionId,
        @Valid @RequestBody RevokeResourcePermissionRequest request,
        Authentication authentication
    ) {
        resourcePermissionManagementService.revoke(currentUser(authentication), permissionId, request.confirmHighRisk());
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record GrantResourcePermissionRequest(
        @NotBlank String subjectType,
        @NotNull UUID subjectId,
        @NotBlank String permissionLevel,
        Instant expiresAt,
        boolean confirmHighRisk
    ) {
    }

    public record RevokeResourcePermissionRequest(boolean confirmHighRisk) {
    }
}
