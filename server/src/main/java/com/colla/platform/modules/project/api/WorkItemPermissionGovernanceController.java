package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemPermissionGovernanceService;
import com.colla.platform.modules.project.application.WorkItemPermissionGovernanceService.BindingSnapshot;
import com.colla.platform.modules.project.application.WorkItemPermissionGovernanceService.ConsistencyFinding;
import com.colla.platform.modules.project.application.WorkItemPermissionGovernanceService.LegacyDisposition;
import com.colla.platform.modules.project.application.WorkItemPermissionGovernanceService.PolicyPreview;
import com.colla.platform.modules.project.application.WorkItemPermissionGovernanceService.RoleMutation;
import com.colla.platform.modules.project.application.WorkItemRelationAccessDecisionService;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Bounded owner/admin governance operations. Enterprise governance alone never passes the
 * project-space manager check, so these endpoints cannot become a content-visibility bypass.
 */
@RestController
@RequestMapping("/api/project-spaces/{spaceId}/permission-governance")
public final class WorkItemPermissionGovernanceController {
    private final WorkItemPermissionGovernanceService service;
    private final WorkItemRelationAccessDecisionService accessDecision;

    public WorkItemPermissionGovernanceController(
        WorkItemPermissionGovernanceService service,
        WorkItemRelationAccessDecisionService accessDecision
    ) {
        this.service = service;
        this.accessDecision = accessDecision;
    }

    @PostMapping("/role-mutations:validate")
    public Object validateRoleMutation(
        @PathVariable UUID spaceId,
        @Valid @RequestBody RoleMutationRequest request,
        Authentication authentication
    ) {
        accessDecision.requireManager(currentUser(authentication), spaceId);
        return service.validateRoleMutation(new RoleMutation(
            request.subjectId(),
            request.roleKey(),
            request.remove(),
            request.activeOwnerIds(),
            request.expiresAt(),
            request.requestId(),
            request.reason(),
            request.confirmation()
        ));
    }

    @PostMapping("/policy-changes:preview")
    public PolicyPreview preview(
        @PathVariable UUID spaceId,
        @Valid @RequestBody PolicyPreviewRequest request,
        Authentication authentication
    ) {
        accessDecision.requireManager(currentUser(authentication), spaceId);
        return service.previewPolicyChange(
            request.expectedVersion(),
            request.currentVersion(),
            request.visibleCandidateCount(),
            request.hiddenCandidateCount(),
            request.grantCount(),
            request.revokeCount()
        );
    }

    @PostMapping("/consistency:scan")
    public List<ConsistencyFinding> scan(
        @PathVariable UUID spaceId,
        @Valid @RequestBody ConsistencyScanRequest request,
        Authentication authentication
    ) {
        accessDecision.requireManager(currentUser(authentication), spaceId);
        return service.scan(request.bindings(), request.activeSubjectIds(), request.configuredRoleKeys());
    }

    @PostMapping("/legacy:classify")
    public LegacyDisposition classifyLegacy(
        @PathVariable UUID spaceId,
        @Valid @RequestBody LegacyClassificationRequest request,
        Authentication authentication
    ) {
        accessDecision.requireManager(currentUser(authentication), spaceId);
        return service.classifyLegacy(request.disposition(), request.wouldExpandAccess());
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record RoleMutationRequest(
        @NotNull UUID subjectId,
        @NotBlank String roleKey,
        boolean remove,
        @NotNull Set<UUID> activeOwnerIds,
        Instant expiresAt,
        @NotBlank @Size(max = 120) String requestId,
        @NotBlank @Size(max = 500) String reason,
        String confirmation
    ) {}

    public record PolicyPreviewRequest(
        @Min(0) long expectedVersion,
        @Min(0) long currentVersion,
        @Min(0) @Max(200) int visibleCandidateCount,
        @Min(0) int hiddenCandidateCount,
        @Min(0) @Max(200) int grantCount,
        @Min(0) @Max(200) int revokeCount
    ) {}

    public record ConsistencyScanRequest(
        @NotNull @Size(max = 500) List<BindingSnapshot> bindings,
        @NotNull @Size(max = 5000) Set<UUID> activeSubjectIds,
        @NotNull @Size(max = 200) Set<String> configuredRoleKeys
    ) {}

    public record LegacyClassificationRequest(
        @NotBlank String disposition,
        boolean wouldExpandAccess
    ) {}
}
