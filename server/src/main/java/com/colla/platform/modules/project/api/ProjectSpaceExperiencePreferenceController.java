package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectSpaceExperiencePreferenceService;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreferenceView;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/experience-preference")
public class ProjectSpaceExperiencePreferenceController {
    private final ProjectSpaceExperiencePreferenceService service;

    public ProjectSpaceExperiencePreferenceController(
        ProjectSpaceExperiencePreferenceService service
    ) {
        this.service = service;
    }

    @GetMapping
    public ExperiencePreferenceView get(
        @PathVariable UUID spaceId,
        Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId);
    }

    @PutMapping
    public ExperiencePreferenceView save(
        @PathVariable UUID spaceId,
        @Valid @RequestBody SaveExperiencePreferenceRequest request,
        Authentication authentication
    ) {
        return service.save(
            currentUser(authentication),
            spaceId,
            request.schemaVersion(),
            request.mode(),
            request.expectedVersion()
        );
    }

    @DeleteMapping
    public ExperiencePreferenceView reset(
        @PathVariable UUID spaceId,
        @RequestParam @PositiveOrZero long expectedVersion,
        Authentication authentication
    ) {
        return service.reset(currentUser(authentication), spaceId, expectedVersion);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record SaveExperiencePreferenceRequest(
        int schemaVersion,
        String mode,
        @PositiveOrZero long expectedVersion
    ) {
    }
}
