package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectSpaceExperienceTelemetryService;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryEventCommand;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/project-space-experience/telemetry")
public class ProjectSpaceExperienceTelemetryController {
    private final ProjectSpaceExperienceTelemetryService service;

    public ProjectSpaceExperienceTelemetryController(
        ProjectSpaceExperienceTelemetryService service
    ) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> record(
        @Valid @RequestBody TelemetryBatchRequest request,
        Authentication authentication
    ) {
        requireCurrentUser(authentication);
        service.record(
            request.schemaVersion(),
            request.events().stream()
                .map(event -> new TelemetryEventCommand(
                    event.eventId(),
                    event.eventKind(),
                    event.routeKey(),
                    event.mode(),
                    event.outcome(),
                    event.durationBucket(),
                    event.errorCode(),
                    event.freshness()
                ))
                .toList()
        );
        return ResponseEntity.noContent().build();
    }

    private void requireCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    public record TelemetryBatchRequest(
        int schemaVersion,
        @NotNull @Size(min = 1, max = 20) List<@Valid TelemetryEventRequest> events
    ) {
        @JsonAnySetter
        public void rejectUnknownProperty(String ignoredName, Object ignoredValue) {
            throw new IllegalArgumentException("Unknown experience telemetry property");
        }
    }

    public record TelemetryEventRequest(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 32) String eventKind,
        @NotBlank @Size(max = 32) String routeKey,
        @NotBlank @Size(max = 32) String mode,
        @NotBlank @Size(max = 32) String outcome,
        @NotBlank @Size(max = 32) String durationBucket,
        @NotBlank @Size(max = 32) String errorCode,
        @NotBlank @Size(max = 32) String freshness
    ) {
        @JsonAnySetter
        public void rejectUnknownProperty(String ignoredName, Object ignoredValue) {
            throw new IllegalArgumentException("Unknown experience telemetry event property");
        }
    }
}
