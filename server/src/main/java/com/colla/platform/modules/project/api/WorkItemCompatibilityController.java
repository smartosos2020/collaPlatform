package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemCompatibilityService;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyProfile;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkItemCompatibilityController {
    private final WorkItemCompatibilityService service;

    public WorkItemCompatibilityController(WorkItemCompatibilityService service) {
        this.service = service;
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

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
