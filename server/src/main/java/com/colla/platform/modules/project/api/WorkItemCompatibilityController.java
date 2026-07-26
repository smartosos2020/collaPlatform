package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemCompatibilityService;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.CompatibilityWorkItem;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.CutoverState;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyProfile;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.ReadStage;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkItemCompatibilityController {
    private final WorkItemCompatibilityService service;

    public WorkItemCompatibilityController(WorkItemCompatibilityService service) {
        this.service = service;
    }

    @GetMapping("/compat/work-items/legacy/issues/{issueId}")
    public CompatibilityWorkItem resolveIssue(
        @PathVariable UUID issueId,
        Authentication authentication
    ) {
        return service.resolveLegacyIssue(currentUser(authentication), issueId);
    }

    @GetMapping("/compat/work-items/legacy/issues/{issueId}/location")
    public Map<String, String> issueLocation(
        @PathVariable UUID issueId,
        Authentication authentication
    ) {
        return Map.of("location", service.canonicalLocation(currentUser(authentication), issueId));
    }

    @GetMapping("/admin/project-migrations/work-items/profile")
    public LegacyProfile profile(Authentication authentication) {
        return service.profile(currentUser(authentication));
    }

    @PostMapping("/admin/project-migrations/work-items/cutover")
    public CutoverState changeCutover(
        @Valid @RequestBody CutoverRequest request,
        Authentication authentication
    ) {
        return service.changeCutover(
            currentUser(authentication),
            request.spaceId(),
            request.readStage(),
            request.legacyWriteEnabled(),
            request.killSwitchEnabled(),
            request.expectedVersion()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CutoverRequest(
        UUID spaceId,
        ReadStage readStage,
        boolean legacyWriteEnabled,
        boolean killSwitchEnabled,
        @PositiveOrZero long expectedVersion
    ) {
    }
}
