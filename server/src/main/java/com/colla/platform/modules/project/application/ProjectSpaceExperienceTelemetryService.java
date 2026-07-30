package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.SCHEMA_VERSION;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.DurationBucket;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.ErrorCode;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.EventKind;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.ExperienceMode;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.Freshness;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.Outcome;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RouteKey;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryEvent;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryEventCommand;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectSpaceExperienceTelemetryService {
    private static final int HARD_MAX_BATCH_SIZE = 20;

    private final ProjectSpaceExperienceRolloutProperties properties;
    private final ProjectSpaceExperienceMetrics metrics;

    public ProjectSpaceExperienceTelemetryService(
        ProjectSpaceExperienceRolloutProperties properties,
        ProjectSpaceExperienceMetrics metrics
    ) {
        this.properties = properties;
        this.metrics = metrics;
    }

    public void record(int schemaVersion, List<TelemetryEventCommand> commands) {
        if (schemaVersion != SCHEMA_VERSION || commands == null || commands.isEmpty()) {
            throw invalidContract();
        }
        int configuredMax = properties.getTelemetry().getMaxBatchSize();
        if (commands.size() > HARD_MAX_BATCH_SIZE || commands.size() > configuredMax) {
            throw invalidContract();
        }

        List<TelemetryEvent> events;
        try {
            events = commands.stream().map(this::validate).toList();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidContract();
        }

        if (
            !properties.isEnabled()
                || properties.isKillSwitch()
                || !properties.getTelemetry().isEnabled()
                || properties.getTelemetry().getSampleBasisPoints() == 0
        ) {
            return;
        }
        int sampleBasisPoints = properties.getTelemetry().getSampleBasisPoints();
        for (TelemetryEvent event : events) {
            int bucket = ProjectSpaceExperienceBucket.stableBucket(
                properties.getEvaluationSalt(),
                properties.getPolicyVersion(),
                "telemetry",
                event.eventId().toString()
            );
            if (bucket < sampleBasisPoints) {
                safeRecord(event);
            }
        }
    }

    private void safeRecord(TelemetryEvent event) {
        try {
            metrics.recordTelemetry(event);
        } catch (RuntimeException ignored) {
            // Optional observation must never become a product availability dependency.
        }
    }

    private TelemetryEvent validate(TelemetryEventCommand command) {
        if (command == null || command.eventId() == null) {
            throw new IllegalArgumentException("Missing telemetry event id");
        }
        return new TelemetryEvent(
            command.eventId(),
            EventKind.parse(command.eventKind()),
            RouteKey.parse(command.routeKey()),
            ExperienceMode.parse(command.mode()),
            Outcome.parse(command.outcome()),
            DurationBucket.parse(command.durationBucket()),
            ErrorCode.parse(command.errorCode()),
            Freshness.parse(command.freshness())
        );
    }

    private ResponseStatusException invalidContract() {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Project space experience telemetry contract is invalid"
        );
    }
}
