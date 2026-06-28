package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionEntry;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionGrant;
import com.colla.platform.modules.permission.infrastructure.ResourcePermissionRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResourcePermissionManagementService {
    private static final Set<String> MANAGED_RESOURCE_TYPES = Set.of("document", "base", "project");
    private static final Set<String> SUBJECT_TYPES = Set.of("user", "department", "user_group");

    private final ResourcePermissionRepository resourcePermissionRepository;
    private final PermissionDecisionService permissionDecisionService;
    private final AuditService auditService;

    public ResourcePermissionManagementService(
        ResourcePermissionRepository resourcePermissionRepository,
        PermissionDecisionService permissionDecisionService,
        AuditService auditService
    ) {
        this.resourcePermissionRepository = resourcePermissionRepository;
        this.permissionDecisionService = permissionDecisionService;
        this.auditService = auditService;
    }

    public List<ResourcePermissionEntry> list(CurrentUser currentUser, String resourceType, UUID resourceId) {
        String normalizedResourceType = normalizeResourceType(resourceType);
        requireManage(currentUser, normalizedResourceType, resourceId);
        return resourcePermissionRepository.listGrants(currentUser.workspaceId(), normalizedResourceType, resourceId);
    }

    @Transactional
    public ResourcePermissionEntry grant(
        CurrentUser currentUser,
        String resourceType,
        UUID resourceId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        Instant expiresAt,
        boolean confirmHighRisk
    ) {
        String normalizedResourceType = normalizeResourceType(resourceType);
        String normalizedSubjectType = normalizeSubjectType(subjectType);
        String normalizedPermission = permissionDecisionService.requiredLevel(permissionLevel);
        requireManage(currentUser, normalizedResourceType, resourceId);
        if (!resourcePermissionRepository.subjectExists(currentUser.workspaceId(), normalizedSubjectType, subjectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission subject not found");
        }
        boolean highRisk = isHighRisk(normalizedPermission);
        if (highRisk && !confirmHighRisk) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "High-risk resource permission change requires confirmation");
        }
        resourcePermissionRepository.upsertGrant(new ResourcePermissionGrant(
            currentUser.workspaceId(),
            normalizedResourceType,
            resourceId,
            normalizedSubjectType,
            subjectId,
            normalizedPermission,
            "direct",
            null,
            expiresAt,
            currentUser.id()
        ));
        auditService.log(
            currentUser,
            "resource.permission.granted",
            normalizedResourceType,
            resourceId,
            Map.of(
                "subjectType", normalizedSubjectType,
                "subjectId", subjectId,
                "permissionLevel", normalizedPermission,
                "highRisk", highRisk
            )
        );
        return resourcePermissionRepository.listGrants(currentUser.workspaceId(), normalizedResourceType, resourceId).stream()
            .filter(entry -> entry.subjectType().equals(normalizedSubjectType) && entry.subjectId().equals(subjectId))
            .findFirst()
            .orElseThrow();
    }

    @Transactional
    public void revoke(CurrentUser currentUser, UUID permissionId, boolean confirmHighRisk) {
        ResourcePermissionEntry entry = resourcePermissionRepository.findGrant(currentUser.workspaceId(), permissionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource permission not found"));
        requireManage(currentUser, entry.resourceType(), entry.resourceId());
        if (!"direct".equals(entry.sourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inherited permission cannot be revoked on child resource");
        }
        boolean highRisk = isHighRisk(entry.permissionLevel());
        if (highRisk && !confirmHighRisk) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "High-risk resource permission change requires confirmation");
        }
        if (!resourcePermissionRepository.revokeGrant(currentUser.workspaceId(), permissionId, currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource permission not found");
        }
        auditService.log(
            currentUser,
            "resource.permission.revoked",
            entry.resourceType(),
            entry.resourceId(),
            Map.of(
                "permissionId", permissionId,
                "subjectType", entry.subjectType(),
                "subjectId", entry.subjectId(),
                "permissionLevel", entry.permissionLevel(),
                "highRisk", highRisk
            )
        );
    }

    private void requireManage(CurrentUser currentUser, String resourceType, UUID resourceId) {
        if (currentUser.hasRole("admin")) {
            return;
        }
        PermissionDecision decision = permissionDecisionService.decide(currentUser, resourceType, resourceId, "manage");
        permissionDecisionService.requireAllowed(decision);
    }

    private String normalizeResourceType(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim().toLowerCase();
        if ("doc".equals(normalized)) {
            normalized = "document";
        }
        if (!MANAGED_RESOURCE_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported managed resource type");
        }
        return normalized;
    }

    private String normalizeSubjectType(String subjectType) {
        String normalized = permissionDecisionService.normalizeSubjectType(subjectType);
        if (!SUBJECT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resource permission subject type");
        }
        return normalized;
    }

    private boolean isHighRisk(String permissionLevel) {
        return List.of("manage", "owner").contains(permissionLevel);
    }
}
