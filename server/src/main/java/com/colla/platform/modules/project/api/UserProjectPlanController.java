package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectPlanService;
import com.colla.platform.modules.project.domain.ProjectPlanModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectPlanModels.MutateCommand;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanSummary;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/project-plans")
public final class UserProjectPlanController {
    private final ProjectPlanService service;

    public UserProjectPlanController(ProjectPlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlanSummary> list(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.list(currentUser(authentication), spaceId);
    }

    @GetMapping("/{planId}")
    public ProjectPlan get(
        @PathVariable UUID spaceId,
        @PathVariable UUID planId,
        Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId, planId);
    }

    @PostMapping
    public ProjectPlan create(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateCommand command,
        Authentication authentication
    ) {
        return service.create(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/{planId}:mutate")
    public ProjectPlan mutate(
        @PathVariable UUID spaceId,
        @PathVariable UUID planId,
        @Valid @RequestBody MutateCommand command,
        Authentication authentication
    ) {
        return service.mutate(
            currentUser(authentication), spaceId, planId, command
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
