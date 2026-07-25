package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemConfigurationDraftService;
import com.colla.platform.modules.project.application.WorkItemConfigurationDraftService.DraftDetail;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
@RequestMapping("/api/project-spaces/{spaceId}/configuration/types/{typeId}")
public class WorkItemConfigurationDraftController {
    private final WorkItemConfigurationDraftService service;

    public WorkItemConfigurationDraftController(WorkItemConfigurationDraftService service) {
        this.service = service;
    }

    @GetMapping("/draft")
    public DraftDetail detail(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        Authentication authentication
    ) {
        return service.detail(currentUser(authentication), spaceId, typeId);
    }

    @PutMapping("/draft")
    public DraftDetail save(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody SaveDraftRequest request,
        Authentication authentication
    ) {
        return service.save(
            currentUser(authentication),
            spaceId,
            typeId,
            request.snapshot(),
            request.expectedAggregateVersion(),
            requestId()
        );
    }

    @PostMapping("/draft:validate")
    public DraftDetail validate(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody DraftCommandRequest request,
        Authentication authentication
    ) {
        return service.validate(
            currentUser(authentication),
            spaceId,
            typeId,
            request.expectedAggregateVersion(),
            requestId()
        );
    }

    @PostMapping("/draft:abandon")
    public DraftDetail abandon(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody DraftCommandRequest request,
        Authentication authentication
    ) {
        return service.abandon(
            currentUser(authentication),
            spaceId,
            typeId,
            request.expectedAggregateVersion(),
            requestId()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record SaveDraftRequest(
        @NotNull JsonNode snapshot,
        @PositiveOrZero long expectedAggregateVersion
    ) {
    }

    public record DraftCommandRequest(@PositiveOrZero long expectedAggregateVersion) {
    }
}
