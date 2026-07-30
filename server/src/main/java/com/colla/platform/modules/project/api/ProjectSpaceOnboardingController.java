package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectSpaceOnboardingService;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingCommand;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingView;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.TelemetryEvent;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/onboarding")
public class ProjectSpaceOnboardingController {
    private final ProjectSpaceOnboardingService service;

    public ProjectSpaceOnboardingController(ProjectSpaceOnboardingService service) {
        this.service = service;
    }

    @GetMapping
    public OnboardingView get(
        @PathVariable UUID spaceId,
        Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId);
    }

    @PostMapping("/commands")
    public OnboardingView command(
        @PathVariable UUID spaceId,
        @Valid @RequestBody OnboardingCommandRequest request,
        Authentication authentication
    ) {
        return service.command(
            currentUser(authentication),
            spaceId,
            new OnboardingCommand(
                request.requestId(),
                request.schemaVersion(),
                request.flowVersion(),
                request.expectedVersion(),
                request.action(),
                request.startingPoint(),
                request.scenarioKey(),
                request.stepKey(),
                request.acknowledgement(),
                request.telemetryOptOut()
            )
        );
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Void> telemetry(
        @PathVariable UUID spaceId,
        @Valid @RequestBody TelemetryBatchRequest request,
        Authentication authentication
    ) {
        service.recordTelemetry(
            currentUser(authentication),
            spaceId,
            request.events().stream()
                .map(event -> new TelemetryEvent(
                    event.eventId(),
                    event.flowVersion(),
                    event.stepKey(),
                    event.outcome(),
                    event.durationBucket(),
                    event.errorCode()
                ))
                .toList()
        );
        return ResponseEntity.noContent().build();
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record OnboardingCommandRequest(
        @NotNull UUID requestId,
        int schemaVersion,
        @NotBlank String flowVersion,
        @PositiveOrZero long expectedVersion,
        @NotBlank String action,
        String startingPoint,
        String scenarioKey,
        String stepKey,
        String acknowledgement,
        Boolean telemetryOptOut
    ) {
    }

    public record TelemetryBatchRequest(
        @NotNull @Size(min = 1, max = 20) List<@Valid TelemetryEventRequest> events
    ) {
    }

    public record TelemetryEventRequest(
        @NotNull UUID eventId,
        @NotBlank String flowVersion,
        @NotBlank String stepKey,
        @NotBlank String outcome,
        @NotBlank String durationBucket,
        @NotBlank String errorCode
    ) {
    }
}
