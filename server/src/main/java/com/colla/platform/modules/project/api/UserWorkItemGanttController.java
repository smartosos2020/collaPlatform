package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemGanttService;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutation;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutationResult;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttPreference;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttRequest;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttResult;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.Optional;
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
@RequestMapping("/api/project-spaces/{spaceId}")
public final class UserWorkItemGanttController {
    private final WorkItemGanttService service;

    public UserWorkItemGanttController(WorkItemGanttService service) {
        this.service = service;
    }

    @PostMapping("/work-item-gantts:render")
    public GanttResult render(
        @PathVariable UUID spaceId,
        @Valid @RequestBody GanttRequest request,
        Authentication authentication
    ) {
        return service.render(currentUser(authentication), spaceId, request);
    }

    @GetMapping("/work-item-gantts/{viewKey}/preference")
    public Optional<GanttPreference> preference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        Authentication authentication
    ) {
        return service.preference(currentUser(authentication), spaceId, viewKey);
    }

    @PutMapping("/work-item-gantts/{viewKey}/preference")
    public GanttPreference savePreference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        @Valid @RequestBody GanttPreferenceCommand command,
        Authentication authentication
    ) {
        return service.savePreference(currentUser(authentication), spaceId, viewKey, command);
    }

    @PostMapping("/work-item-gantts/{viewKey}/items/{workItemId}:date")
    public DateMutationResult mutateDate(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        @PathVariable UUID workItemId,
        @Valid @RequestBody DateMutation mutation,
        Authentication authentication
    ) {
        return service.mutateDate(
            currentUser(authentication), spaceId, viewKey, workItemId, mutation
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
