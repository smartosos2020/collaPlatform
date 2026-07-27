package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemHierarchyApiDtos.HierarchyMutationResponse;
import com.colla.platform.modules.project.api.WorkItemHierarchyApiDtos.HierarchyNavigationResponse;
import com.colla.platform.modules.project.api.WorkItemHierarchyApiDtos.HierarchyPageResponse;
import com.colla.platform.modules.project.application.WorkItemHierarchyService;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/work-item-hierarchy")
public class UserWorkItemHierarchyController {
    private final WorkItemHierarchyService service;

    public UserWorkItemHierarchyController(WorkItemHierarchyService service) {
        this.service = service;
    }

    @GetMapping
    public HierarchyPageResponse query(
        @PathVariable UUID spaceId,
        @RequestParam UUID workItemId,
        @RequestParam String relationKey,
        @RequestParam(defaultValue = "descendants") String direction,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "8") int maxDepth,
        @RequestParam(defaultValue = "100") int limit,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.page(service.query(
            currentUser(authentication),
            spaceId,
            workItemId,
            relationKey,
            direction,
            cursor,
            maxDepth,
            limit
        ));
    }

    @GetMapping("/navigation")
    public HierarchyNavigationResponse navigation(
        @PathVariable UUID spaceId,
        @RequestParam UUID workItemId,
        @RequestParam String relationKey,
        @RequestParam(defaultValue = "8") int maxDepth,
        @RequestParam(defaultValue = "100") int limit,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.navigation(service.navigation(
            currentUser(authentication),
            spaceId,
            workItemId,
            relationKey,
            maxDepth,
            limit
        ));
    }

    @PostMapping(":attach")
    public HierarchyMutationResponse attach(
        @PathVariable UUID spaceId,
        @Valid @RequestBody AttachRequest request,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.mutation(service.attach(
            currentUser(authentication),
            spaceId,
            request.relationKey(),
            request.parentWorkItemId(),
            request.childWorkItemId(),
            request.expectedParentVersion(),
            request.expectedChildVersion(),
            requestId()
        ));
    }

    @PostMapping(":detach")
    public HierarchyMutationResponse detach(
        @PathVariable UUID spaceId,
        @Valid @RequestBody DetachRequest request,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.mutation(service.detach(
            currentUser(authentication),
            spaceId,
            request.relationId(),
            request.expectedRelationVersion(),
            request.expectedParentVersion(),
            request.expectedChildVersion(),
            request.reason(),
            requestId()
        ));
    }

    @PostMapping(":reparent")
    public HierarchyMutationResponse reparent(
        @PathVariable UUID spaceId,
        @Valid @RequestBody ReparentRequest request,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.mutation(service.reparent(
            currentUser(authentication),
            spaceId,
            request.currentRelationId(),
            request.newParentWorkItemId(),
            request.expectedRelationVersion(),
            request.expectedCurrentParentVersion(),
            request.expectedNewParentVersion(),
            request.expectedChildVersion(),
            request.reason(),
            request.confirmation(),
            requestId()
        ));
    }

    @PostMapping(":split-child")
    public HierarchyMutationResponse splitChild(
        @PathVariable UUID spaceId,
        @Valid @RequestBody SplitChildRequest request,
        Authentication authentication
    ) {
        return WorkItemHierarchyApiDtos.mutation(service.splitChild(
            currentUser(authentication),
            spaceId,
            request.parentWorkItemId(),
            request.relationKey(),
            request.childTypeId(),
            request.childTitle(),
            request.childFieldValues(),
            request.inheritFieldKeys(),
            request.expectedParentVersion(),
            requestId()
        ));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record AttachRequest(
        @NotBlank String relationKey,
        @NotNull UUID parentWorkItemId,
        @NotNull UUID childWorkItemId,
        @PositiveOrZero long expectedParentVersion,
        @PositiveOrZero long expectedChildVersion
    ) {
    }

    public record DetachRequest(
        @NotNull UUID relationId,
        @PositiveOrZero long expectedRelationVersion,
        @PositiveOrZero long expectedParentVersion,
        @PositiveOrZero long expectedChildVersion,
        @NotBlank String reason
    ) {
    }

    public record ReparentRequest(
        @NotNull UUID currentRelationId,
        @NotNull UUID newParentWorkItemId,
        @PositiveOrZero long expectedRelationVersion,
        @PositiveOrZero long expectedCurrentParentVersion,
        @PositiveOrZero long expectedNewParentVersion,
        @PositiveOrZero long expectedChildVersion,
        @NotBlank String reason,
        @NotBlank String confirmation
    ) {
    }

    public record SplitChildRequest(
        @NotNull UUID parentWorkItemId,
        @NotBlank String relationKey,
        @NotNull UUID childTypeId,
        @NotBlank @Size(max = 240) String childTitle,
        JsonNode childFieldValues,
        @Size(max = 32) List<@NotBlank String> inheritFieldKeys,
        @PositiveOrZero long expectedParentVersion
    ) {
    }
}
