package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemScheduleService;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttRequest;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineCreateCommand;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineDeleteCommand;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineDiff;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSnapshot;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSummary;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineRequest;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineResult;
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
@RequestMapping("/api/project-spaces/{spaceId}")
public final class UserWorkItemScheduleController {
    private final WorkItemScheduleService service;

    public UserWorkItemScheduleController(WorkItemScheduleService service) {
        this.service = service;
    }

    @GetMapping("/work-item-schedule-baselines")
    public List<BaselineSummary> list(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.list(currentUser(authentication), spaceId);
    }

    @PostMapping("/work-item-schedule-baselines")
    public BaselineSnapshot create(
        @PathVariable UUID spaceId,
        @Valid @RequestBody BaselineCreateCommand command,
        Authentication authentication
    ) {
        return service.create(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/work-item-schedule-baselines/{baselineId}:compare")
    public BaselineDiff compare(
        @PathVariable UUID spaceId,
        @PathVariable UUID baselineId,
        @Valid @RequestBody GanttRequest request,
        Authentication authentication
    ) {
        return service.compare(currentUser(authentication), spaceId, baselineId, request);
    }

    @PostMapping("/work-item-schedule-baselines/{baselineId}:delete")
    public BaselineSummary delete(
        @PathVariable UUID spaceId,
        @PathVariable UUID baselineId,
        @Valid @RequestBody BaselineDeleteCommand command,
        Authentication authentication
    ) {
        return service.delete(
            currentUser(authentication), spaceId, baselineId, command
        );
    }

    @PostMapping("/work-item-timeline:render")
    public TimelineResult timeline(
        @PathVariable UUID spaceId,
        @Valid @RequestBody TimelineRequest request,
        Authentication authentication
    ) {
        return service.timeline(currentUser(authentication), spaceId, request);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
