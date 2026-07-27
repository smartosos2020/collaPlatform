package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemCalendarService;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreference;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarRequest;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarResult;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutation;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutationResult;
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
public final class UserWorkItemCalendarController {
    private final WorkItemCalendarService service;

    public UserWorkItemCalendarController(WorkItemCalendarService service) {
        this.service = service;
    }

    @PostMapping("/work-item-calendars:render")
    public CalendarResult render(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CalendarRequest request,
        Authentication authentication
    ) {
        return service.render(currentUser(authentication), spaceId, request);
    }

    @GetMapping("/work-item-calendars/{viewKey}/preference")
    public Optional<CalendarPreference> preference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        Authentication authentication
    ) {
        return service.preference(currentUser(authentication), spaceId, viewKey);
    }

    @PutMapping("/work-item-calendars/{viewKey}/preference")
    public CalendarPreference savePreference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        @Valid @RequestBody CalendarPreferenceCommand command,
        Authentication authentication
    ) {
        return service.savePreference(currentUser(authentication), spaceId, viewKey, command);
    }

    @PostMapping("/work-item-calendars/{viewKey}/items/{workItemId}:date")
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
