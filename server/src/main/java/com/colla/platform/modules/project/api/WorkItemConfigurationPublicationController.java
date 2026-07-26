package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemConfigurationPublicationService;
import com.colla.platform.modules.project.application.WorkItemConfigurationPublicationService.PublicationResult;
import com.colla.platform.modules.project.application.WorkItemConfigurationPublicationService.RollbackPreparation;
import com.colla.platform.modules.project.application.WorkItemConfigurationPublicationService.VersionDetail;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiff;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
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
@RequestMapping("/api/project-spaces/{spaceId}/configuration/types/{typeId}")
public class WorkItemConfigurationPublicationController {
    private final WorkItemConfigurationPublicationService service;

    public WorkItemConfigurationPublicationController(WorkItemConfigurationPublicationService service) {
        this.service = service;
    }

    @PostMapping("/draft:publish")
    public PublicationResult publish(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody PublishRequest request,
        Authentication authentication
    ) {
        return service.publish(
            currentUser(authentication),
            spaceId,
            typeId,
            request.expectedDraftAggregateVersion(),
            request.breakingConfirmed(),
            requestId()
        );
    }

    @GetMapping("/versions")
    public List<VersionDetail> versions(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        Authentication authentication
    ) {
        return service.versions(currentUser(authentication), spaceId, typeId);
    }

    @GetMapping("/versions/{versionId}")
    public VersionDetail version(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID versionId,
        Authentication authentication
    ) {
        return service.version(currentUser(authentication), spaceId, typeId, versionId);
    }

    @GetMapping("/versions:diff")
    public ConfigurationDiff diff(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @RequestParam UUID fromVersionId,
        @RequestParam UUID toVersionId,
        Authentication authentication
    ) {
        return service.diffVersions(
            currentUser(authentication), spaceId, typeId, fromVersionId, toVersionId
        );
    }

    @GetMapping("/draft:diff")
    public ConfigurationDiff draftDiff(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        Authentication authentication
    ) {
        return service.diffDraft(currentUser(authentication), spaceId, typeId);
    }

    @PostMapping("/versions/{versionId}:prepare-rollback")
    public RollbackPreparation prepareRollback(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID versionId,
        @Valid @RequestBody PrepareRollbackRequest request,
        Authentication authentication
    ) {
        return service.prepareRollback(
            currentUser(authentication),
            spaceId,
            typeId,
            versionId,
            request.expectedDraftAggregateVersion(),
            requestId()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record PublishRequest(
        @PositiveOrZero long expectedDraftAggregateVersion,
        boolean breakingConfirmed
    ) {
    }

    public record PrepareRollbackRequest(@PositiveOrZero long expectedDraftAggregateVersion) {
    }
}
