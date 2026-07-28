package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ResourceScheduleService;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.AdjustmentCommand;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.AdjustmentResult;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.ResourceSchedule;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.SavePreferenceCommand;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.SchedulePreference;
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
@RequestMapping("/api/project-spaces/{spaceId}/resource-planning/schedule")
public final class UserResourceScheduleController {
    private final ResourceScheduleService service;

    public UserResourceScheduleController(ResourceScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public ResourceSchedule get(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.get((CurrentUser) authentication.getPrincipal(), spaceId);
    }

    @PostMapping("/preference")
    public SchedulePreference preference(
        @PathVariable UUID spaceId,
        @RequestBody SavePreferenceCommand command,
        Authentication authentication
    ) {
        return service.savePreference(
            (CurrentUser) authentication.getPrincipal(), spaceId, command
        );
    }

    @PostMapping("/adjustments")
    public AdjustmentResult adjustment(
        @PathVariable UUID spaceId,
        @RequestBody AdjustmentCommand command,
        Authentication authentication
    ) {
        return service.adjust(
            (CurrentUser) authentication.getPrincipal(), spaceId, command
        );
    }
}
