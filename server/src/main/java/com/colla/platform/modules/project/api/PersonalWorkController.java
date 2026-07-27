package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.contract.PersonalWorkQuery;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkPage;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ActivityPage;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ConsistencyResult;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.NudgeReceipt;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReadState;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderDispatchResult;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderPreference;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderView;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/personal-work")
public class PersonalWorkController {
    private final PersonalWorkQuery service;
    private final PersonalCollaborationQuery collaboration;

    public PersonalWorkController(
        PersonalWorkQuery service,
        PersonalCollaborationQuery collaboration
    ) {
        this.service = service;
        this.collaboration = collaboration;
    }

    @GetMapping
    public PersonalWorkPage list(
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return service.list((CurrentUser) authentication.getPrincipal(), cursor, limit);
    }

    @GetMapping("/activities")
    public ActivityPage activities(
        @RequestParam(required = false) Long before,
        @RequestParam(defaultValue = "30") int limit,
        Authentication authentication
    ) {
        return collaboration.activities(currentUser(authentication), before, limit);
    }

    @PostMapping("/activities:read")
    public ReadState markActivitiesRead(
        @RequestBody ActivityReadRequest request,
        Authentication authentication
    ) {
        return collaboration.markActivitiesRead(currentUser(authentication), request.throughSequence());
    }

    @GetMapping("/reminders")
    public ReminderView reminders(
        @RequestParam(required = false) String timezone,
        Authentication authentication
    ) {
        return collaboration.reminders(currentUser(authentication), timezone);
    }

    @PostMapping("/reminders:dispatch")
    public ReminderDispatchResult dispatchReminders(
        @RequestBody ReminderDispatchRequest request,
        Authentication authentication
    ) {
        return collaboration.dispatchReminders(
            currentUser(authentication),
            request.timezone(),
            request.requestId()
        );
    }

    @GetMapping("/reminder-preference")
    public ReminderPreference reminderPreference(Authentication authentication) {
        return collaboration.preference(currentUser(authentication));
    }

    @PutMapping("/reminder-preference")
    public ReminderPreference updateReminderPreference(
        @RequestBody ReminderPreferenceRequest request,
        Authentication authentication
    ) {
        return collaboration.updatePreference(
            currentUser(authentication),
            request.timezone(),
            request.approachingMinutes(),
            request.enabled()
        );
    }

    @PostMapping("/spaces/{spaceId}/work-items/{workItemId}/nudges")
    public NudgeReceipt nudge(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @RequestBody NudgeRequest request,
        Authentication authentication
    ) {
        return collaboration.nudge(
            currentUser(authentication),
            spaceId,
            workItemId,
            request.recipientId(),
            request.requestId()
        );
    }

    @PostMapping("/consistency")
    public ConsistencyResult consistency(
        @RequestBody ConsistencyRequest request,
        Authentication authentication
    ) {
        return collaboration.consistency(
            currentUser(authentication),
            request.dryRun(),
            request.rebuild()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record ActivityReadRequest(long throughSequence) {
    }

    public record ReminderDispatchRequest(String timezone, String requestId) {
    }

    public record ReminderPreferenceRequest(
        String timezone,
        int approachingMinutes,
        boolean enabled
    ) {
    }

    public record NudgeRequest(UUID recipientId, String requestId) {
    }

    public record ConsistencyRequest(boolean dryRun, boolean rebuild) {
    }
}
