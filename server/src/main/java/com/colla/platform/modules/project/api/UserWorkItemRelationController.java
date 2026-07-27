package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemRelationApiDtos.RelationCapabilitiesResponse;
import com.colla.platform.modules.project.api.WorkItemRelationApiDtos.RelationPageResponse;
import com.colla.platform.modules.project.api.WorkItemRelationApiDtos.RelationResponse;
import com.colla.platform.modules.project.application.WorkItemRelationService;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
@RequestMapping("/api/project-spaces/{spaceId}/work-item-relations")
public class UserWorkItemRelationController {
    private final WorkItemRelationService service;

    public UserWorkItemRelationController(WorkItemRelationService service) {
        this.service = service;
    }

    @GetMapping
    public RelationPageResponse list(
        @PathVariable UUID spaceId,
        @RequestParam UUID workItemId,
        @RequestParam(required = false) String relationKey,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return WorkItemRelationApiDtos.page(service.list(
            currentUser(authentication),
            spaceId,
            workItemId,
            relationKey,
            cursor,
            limit
        ));
    }

    @GetMapping("/{relationId}")
    public RelationResponse get(
        @PathVariable UUID spaceId,
        @PathVariable UUID relationId,
        @RequestParam(required = false) UUID perspectiveWorkItemId,
        Authentication authentication
    ) {
        return WorkItemRelationApiDtos.response(service.get(
            currentUser(authentication),
            spaceId,
            relationId,
            perspectiveWorkItemId
        ));
    }

    @GetMapping("/capabilities")
    public RelationCapabilitiesResponse capabilities(
        @PathVariable UUID spaceId,
        @RequestParam String relationKey,
        @RequestParam UUID sourceWorkItemId,
        @RequestParam UUID targetWorkItemId,
        Authentication authentication
    ) {
        return WorkItemRelationApiDtos.capabilities(service.capabilities(
            currentUser(authentication),
            spaceId,
            relationKey,
            sourceWorkItemId,
            targetWorkItemId
        ));
    }

    @PostMapping
    public RelationResponse create(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateRelationRequest request,
        Authentication authentication
    ) {
        return WorkItemRelationApiDtos.response(service.create(
            currentUser(authentication),
            spaceId,
            request.relationKey(),
            request.sourceWorkItemId(),
            request.targetWorkItemId(),
            request.expectedSourceVersion(),
            request.expectedTargetVersion(),
            requestId()
        ));
    }

    @PostMapping("/{relationId}:withdraw")
    public RelationResponse withdraw(
        @PathVariable UUID spaceId,
        @PathVariable UUID relationId,
        @Valid @RequestBody WithdrawRelationRequest request,
        Authentication authentication
    ) {
        return WorkItemRelationApiDtos.response(service.withdraw(
            currentUser(authentication),
            spaceId,
            relationId,
            request.expectedRelationVersion(),
            request.expectedSourceVersion(),
            request.expectedTargetVersion(),
            request.reason(),
            requestId()
        ));
    }

    @PostMapping("/{relationId}:restore")
    public RelationResponse restore(
        @PathVariable UUID spaceId,
        @PathVariable UUID relationId,
        @Valid @RequestBody RestoreRelationRequest request,
        Authentication authentication
    ) {
        return WorkItemRelationApiDtos.response(service.restore(
            currentUser(authentication),
            spaceId,
            relationId,
            request.expectedRelationVersion(),
            request.expectedSourceVersion(),
            request.expectedTargetVersion(),
            requestId()
        ));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record CreateRelationRequest(
        @NotBlank String relationKey,
        @NotNull UUID sourceWorkItemId,
        @NotNull UUID targetWorkItemId,
        @PositiveOrZero long expectedSourceVersion,
        @PositiveOrZero long expectedTargetVersion
    ) {
    }

    public record WithdrawRelationRequest(
        @PositiveOrZero long expectedRelationVersion,
        @PositiveOrZero long expectedSourceVersion,
        @PositiveOrZero long expectedTargetVersion,
        @NotBlank String reason
    ) {
    }

    public record RestoreRelationRequest(
        @PositiveOrZero long expectedRelationVersion,
        @PositiveOrZero long expectedSourceVersion,
        @PositiveOrZero long expectedTargetVersion
    ) {
    }
}
