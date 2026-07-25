package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemLayoutApiDtos.LayoutView;
import com.colla.platform.modules.project.application.WorkItemLayoutConfigurationService;
import com.colla.platform.modules.project.application.WorkItemLayoutGraphCommandHandler.NodeCommand;
import com.colla.platform.modules.project.application.WorkItemLayoutAccessProjectionService;
import com.colla.platform.modules.project.application.WorkItemLayoutAccessProjectionService.LayoutAccessProjection;
import com.colla.platform.modules.project.application.WorkItemLayoutAccessProjectionService.SyntheticContext;
import com.colla.platform.modules.project.application.WorkItemLayoutSecurityAuditService;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.WorkItemLayoutException;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/configuration/types/{typeId}/layouts")
public class WorkItemLayoutConfigurationController {
    private final WorkItemLayoutConfigurationService service;
    private final WorkItemLayoutAccessProjectionService projectionService;
    private final WorkItemLayoutSecurityAuditService securityAuditService;

    public WorkItemLayoutConfigurationController(
        WorkItemLayoutConfigurationService service,
        WorkItemLayoutAccessProjectionService projectionService,
        WorkItemLayoutSecurityAuditService securityAuditService
    ) {
        this.service = service;
        this.projectionService = projectionService;
        this.securityAuditService = securityAuditService;
    }

    @GetMapping("/{layoutKind}")
    public LayoutView get(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable String layoutKind,
        Authentication authentication
    ) {
        return WorkItemLayoutApiDtos.view(
            service.get(currentUser(authentication), spaceId, typeId, layoutKind)
        );
    }

    @PutMapping("/{layoutKind}")
    public LayoutView save(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable String layoutKind,
        @Valid @RequestBody SaveLayoutRequest request,
        Authentication authentication
    ) {
        List<LayoutNode> nodes = request.nodes().stream()
            .map(this::node)
            .toList();
        List<FieldAccessPolicy> policies = request.policies().stream()
            .map(policy -> new FieldAccessPolicy(
                policy.id(),
                policy.fieldId(),
                policy.fieldKey(),
                policy.policyKey(),
                policy.policy(),
                ""
            ))
            .toList();
        CurrentUser user = currentUser(authentication);
        try {
            return WorkItemLayoutApiDtos.view(service.save(
                user,
                spaceId,
                typeId,
                layoutKind,
                nodes,
                policies,
                request.aggregateVersion(),
                RequestBoundaryContext.current().requestId()
            ));
        } catch (WorkItemLayoutException exception) {
            securityAuditService.recordPolicyWriteDenied(
                user, spaceId, "save_layout", exception.code()
            );
            throw exception;
        }
    }

    @PutMapping("/{layoutKind}/policies")
    public LayoutView savePolicies(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable String layoutKind,
        @Valid @RequestBody SavePoliciesRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUser(authentication);
        List<FieldAccessPolicy> policies = request.policies().stream()
            .map(this::policy)
            .toList();
        try {
            return WorkItemLayoutApiDtos.view(service.savePolicies(
                user,
                spaceId,
                typeId,
                layoutKind,
                policies,
                request.aggregateVersion(),
                RequestBoundaryContext.current().requestId()
            ));
        } catch (WorkItemLayoutException exception) {
            securityAuditService.recordPolicyWriteDenied(
                user, spaceId, "save_policies", exception.code()
            );
            throw exception;
        }
    }

    @PostMapping("/{layoutKind}/preview")
    public LayoutAccessProjection preview(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable String layoutKind,
        @Valid @RequestBody SyntheticPreviewRequest request,
        Authentication authentication
    ) {
        return projectionService.preview(
            currentUser(authentication),
            spaceId,
            typeId,
            layoutKind,
            new SyntheticContext(
                request.role(),
                request.spaceStatus(),
                request.typeStatus(),
                request.fieldValues(),
                request.fieldStatuses()
            )
        );
    }

    @PostMapping("/{layoutKind}/nodes:command")
    public LayoutView command(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable String layoutKind,
        @Valid @RequestBody NodeCommandRequest request,
        Authentication authentication
    ) {
        return WorkItemLayoutApiDtos.view(service.applyNodeCommand(
            currentUser(authentication),
            spaceId,
            typeId,
            layoutKind,
            new NodeCommand(
                request.operation(),
                request.nodeId(),
                request.parentId(),
                request.targetSortOrder(),
                request.node() == null ? null : node(request.node()),
                request.confirmReferences(),
                request.aggregateVersion()
            ),
            RequestBoundaryContext.current().requestId()
        ));
    }

    private LayoutNode node(LayoutNodeRequest node) {
        return new LayoutNode(
            node.id(),
            node.parentId(),
            node.nodeKey(),
            node.nodeType(),
            node.fieldId(),
            node.fieldKey(),
            node.sortOrder(),
            node.config(),
            node.visibilityCondition()
        );
    }

    private FieldAccessPolicy policy(FieldAccessPolicyRequest policy) {
        return new FieldAccessPolicy(
            policy.id(),
            policy.fieldId(),
            policy.fieldKey(),
            policy.policyKey(),
            policy.policy(),
            ""
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record SaveLayoutRequest(
        @NotNull List<@Valid LayoutNodeRequest> nodes,
        @NotNull List<@Valid FieldAccessPolicyRequest> policies,
        @PositiveOrZero long aggregateVersion
    ) {
    }

    public record LayoutNodeRequest(
        @NotNull UUID id,
        UUID parentId,
        @NotBlank String nodeKey,
        @NotBlank String nodeType,
        UUID fieldId,
        String fieldKey,
        @PositiveOrZero int sortOrder,
        JsonNode config,
        JsonNode visibilityCondition
    ) {
    }

    public record FieldAccessPolicyRequest(
        @NotNull UUID id,
        @NotNull UUID fieldId,
        @NotBlank String fieldKey,
        @NotBlank String policyKey,
        @NotNull JsonNode policy
    ) {
    }

    public record SavePoliciesRequest(
        @NotNull List<@Valid FieldAccessPolicyRequest> policies,
        @PositiveOrZero long aggregateVersion
    ) {
    }

    public record SyntheticPreviewRequest(
        @NotBlank String role,
        @NotBlank String spaceStatus,
        @NotBlank String typeStatus,
        Map<String, JsonNode> fieldValues,
        Map<String, String> fieldStatuses
    ) {
    }

    public record NodeCommandRequest(
        @NotBlank String operation,
        UUID nodeId,
        UUID parentId,
        @PositiveOrZero int targetSortOrder,
        @Valid LayoutNodeRequest node,
        boolean confirmReferences,
        @PositiveOrZero long aggregateVersion
    ) {
    }
}
