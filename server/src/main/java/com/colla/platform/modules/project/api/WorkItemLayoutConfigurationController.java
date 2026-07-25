package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemLayoutApiDtos.LayoutView;
import com.colla.platform.modules.project.application.WorkItemLayoutConfigurationService;
import com.colla.platform.modules.project.application.WorkItemLayoutGraphCommandHandler.NodeCommand;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
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

    public WorkItemLayoutConfigurationController(WorkItemLayoutConfigurationService service) {
        this.service = service;
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
        return WorkItemLayoutApiDtos.view(service.save(
            currentUser(authentication),
            spaceId,
            typeId,
            layoutKind,
            nodes,
            policies,
            request.aggregateVersion(),
            RequestBoundaryContext.current().requestId()
        ));
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
