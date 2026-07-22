package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemTypeApiDtos.SpaceConfigurationWorkItemType;
import com.colla.platform.modules.project.api.WorkItemTypeApiDtos.SpaceConfigurationWorkItemTypeCollection;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationModels.ReorderType;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationService;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/project-spaces/{spaceId}/configuration")
public class WorkItemTypeConfigurationController {
    private final WorkItemTypeConfigurationService service;

    public WorkItemTypeConfigurationController(WorkItemTypeConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public SpaceConfigurationWorkItemTypeCollection list(
        @PathVariable UUID spaceId,
        @RequestParam(required = false) String status,
        Authentication authentication
    ) {
        return WorkItemTypeApiDtos.configuration(service.configuration(currentUser(authentication), spaceId, status));
    }

    @GetMapping("/types/{typeId}")
    public SpaceConfigurationWorkItemType detail(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        Authentication authentication
    ) {
        return WorkItemTypeApiDtos.configuredType(service.detail(currentUser(authentication), spaceId, typeId));
    }

    @PostMapping("/types")
    public SpaceConfigurationWorkItemType create(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateWorkItemTypeRequest request,
        Authentication authentication
    ) {
        return WorkItemTypeApiDtos.configuredType(service.create(
            currentUser(authentication), spaceId, request.typeKey(), request.name(), request.icon(),
            request.description(), request.sortOrder() == null ? 0 : request.sortOrder(), requestId()
        ));
    }

    @PatchMapping("/types/{typeId}")
    public SpaceConfigurationWorkItemType update(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody UpdateWorkItemTypeRequest request,
        Authentication authentication
    ) {
        return WorkItemTypeApiDtos.configuredType(service.update(
            currentUser(authentication), spaceId, typeId, request.name(), request.icon(), request.description(),
            request.aggregateVersion(), requestId()
        ));
    }

    @PostMapping("/types/{typeId}:copy")
    public SpaceConfigurationWorkItemType copy(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody CopyWorkItemTypeRequest request,
        Authentication authentication
    ) {
        return WorkItemTypeApiDtos.configuredType(service.copy(
            currentUser(authentication), spaceId, typeId, request.typeKey(), request.name(), request.icon(),
            request.description(), request.sortOrder(), requestId()
        ));
    }

    @PutMapping("/types:reorder")
    public SpaceConfigurationWorkItemTypeCollection reorder(
        @PathVariable UUID spaceId,
        @Valid @RequestBody ReorderWorkItemTypesRequest request,
        Authentication authentication
    ) {
        List<ReorderType> items = request.items().stream()
            .map(item -> new ReorderType(item.typeId(), item.sortOrder(), item.aggregateVersion()))
            .toList();
        return WorkItemTypeApiDtos.configuration(service.reorder(currentUser(authentication), spaceId, items, requestId()));
    }

    @PostMapping("/types/{typeId}:disable")
    public SpaceConfigurationWorkItemType disable(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody LifecycleWorkItemTypeRequest request,
        Authentication authentication
    ) {
        return transition(spaceId, typeId, "disabled", request, authentication);
    }

    @PostMapping("/types/{typeId}:restore")
    public SpaceConfigurationWorkItemType restore(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody LifecycleWorkItemTypeRequest request,
        Authentication authentication
    ) {
        return transition(spaceId, typeId, "active", request, authentication);
    }

    @PostMapping("/types/{typeId}:retire")
    public SpaceConfigurationWorkItemType retire(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody LifecycleWorkItemTypeRequest request,
        Authentication authentication
    ) {
        return transition(spaceId, typeId, "retired", request, authentication);
    }

    private SpaceConfigurationWorkItemType transition(
        UUID spaceId,
        UUID typeId,
        String targetStatus,
        LifecycleWorkItemTypeRequest request,
        Authentication authentication
    ) {
        return WorkItemTypeApiDtos.configuredType(service.transition(
            currentUser(authentication), spaceId, typeId, targetStatus, request.aggregateVersion(), requestId()
        ));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record CreateWorkItemTypeRequest(
        @NotBlank String typeKey,
        @NotBlank String name,
        String icon,
        String description,
        @PositiveOrZero Integer sortOrder
    ) {
    }

    public record UpdateWorkItemTypeRequest(
        @NotBlank String name,
        String icon,
        String description,
        @PositiveOrZero long aggregateVersion
    ) {
    }

    public record CopyWorkItemTypeRequest(
        @NotBlank String typeKey,
        String name,
        String icon,
        String description,
        @PositiveOrZero Integer sortOrder
    ) {
    }

    public record ReorderWorkItemTypesRequest(@NotEmpty List<@Valid ReorderWorkItemTypeEntry> items) {
    }

    public record ReorderWorkItemTypeEntry(
        @NotNull UUID typeId,
        @PositiveOrZero int sortOrder,
        @PositiveOrZero long aggregateVersion
    ) {
    }

    public record LifecycleWorkItemTypeRequest(@PositiveOrZero long aggregateVersion) {
    }
}
