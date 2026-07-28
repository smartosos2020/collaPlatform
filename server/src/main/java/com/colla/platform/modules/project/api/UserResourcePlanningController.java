package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ResourcePlanningService;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.Estimate;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.PlanningFoundation;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.SaveCalendarCommand;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.SaveEstimateCommand;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/resource-planning")
public final class UserResourcePlanningController {
    private final ResourcePlanningService service;

    public UserResourcePlanningController(ResourcePlanningService service) {
        this.service = service;
    }

    @GetMapping
    public PlanningFoundation get(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId);
    }

    @PostMapping("/calendar")
    public WorkCalendar saveCalendar(
        @PathVariable UUID spaceId,
        @RequestBody SaveCalendarCommand command,
        Authentication authentication
    ) {
        return service.saveCalendar(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/estimates")
    public Estimate saveEstimate(
        @PathVariable UUID spaceId,
        @RequestBody SaveEstimateCommand command,
        Authentication authentication
    ) {
        return service.saveEstimate(currentUser(authentication), spaceId, command);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
