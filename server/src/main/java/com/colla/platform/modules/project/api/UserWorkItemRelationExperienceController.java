package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemRelationExperienceService;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.ChangePreview;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.ImpactAnalysis;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.RelationSummary;
import com.colla.platform.modules.project.domain.WorkItemRelationExperienceModels.TargetPage;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/project-spaces/{spaceId}/work-item-relation-experience")
public class UserWorkItemRelationExperienceController {
    private final WorkItemRelationExperienceService service;

    public UserWorkItemRelationExperienceController(
        WorkItemRelationExperienceService service
    ) {
        this.service = service;
    }

    @GetMapping("/targets")
    public TargetPage targets(
        @PathVariable UUID spaceId,
        @RequestParam UUID sourceWorkItemId,
        @RequestParam String relationKey,
        @RequestParam(defaultValue = "") String query,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(defaultValue = "25") int limit,
        Authentication authentication
    ) {
        return service.targets(
            currentUser(authentication), spaceId, sourceWorkItemId, relationKey,
            query, cursor, limit
        );
    }

    @GetMapping("/summary")
    public RelationSummary summary(
        @PathVariable UUID spaceId,
        @RequestParam UUID workItemId,
        @RequestParam(required = false) String relationKey,
        @RequestParam(defaultValue = "100") int limit,
        Authentication authentication
    ) {
        return service.summary(
            currentUser(authentication), spaceId, workItemId, relationKey, limit
        );
    }

    @GetMapping("/impact")
    public ImpactAnalysis impact(
        @PathVariable UUID spaceId,
        @RequestParam UUID focusWorkItemId,
        @RequestParam String relationKey,
        @RequestParam(defaultValue = "downstream") String direction,
        @RequestParam(defaultValue = "8") int maxDepth,
        @RequestParam(defaultValue = "100") int limit,
        Authentication authentication
    ) {
        return service.impact(
            currentUser(authentication), spaceId, focusWorkItemId, relationKey,
            direction, maxDepth, limit
        );
    }

    @PostMapping("/preview")
    public ChangePreview preview(
        @PathVariable UUID spaceId,
        @Valid @RequestBody PreviewRequest request,
        Authentication authentication
    ) {
        return service.preview(
            currentUser(authentication),
            spaceId,
            request.relationKey(),
            request.sourceWorkItemId(),
            request.targetWorkItemId(),
            request.expectedSourceVersion(),
            request.expectedTargetVersion(),
            request.reason()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record PreviewRequest(
        @NotBlank String relationKey,
        @NotNull UUID sourceWorkItemId,
        @NotNull UUID targetWorkItemId,
        @PositiveOrZero long expectedSourceVersion,
        @PositiveOrZero long expectedTargetVersion,
        @Size(max = 500) String reason
    ) {
    }
}
