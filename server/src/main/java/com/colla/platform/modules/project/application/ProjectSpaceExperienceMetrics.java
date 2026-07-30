package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RolloutState;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProjectSpaceExperienceMetrics {
    private final MeterRegistry meters;

    public ProjectSpaceExperienceMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    public void recordRollout(RolloutState state) {
        meters.counter(
            "colla.project.space_experience.rollout.decisions",
            "state", state.name()
        ).increment();
    }

    public void recordTelemetry(TelemetryEvent event) {
        meters.counter(
            "colla.project.space_experience.ui.events",
            "event_kind", event.eventKind().value(),
            "outcome", event.outcome().value()
        ).increment();
        meters.counter(
            "colla.project.space_experience.route.events",
            "route_key", event.routeKey().value(),
            "error_code", event.errorCode().value()
        ).increment();
        meters.counter(
            "colla.project.space_experience.mode.events",
            "mode", event.mode().value(),
            "outcome", event.outcome().value()
        ).increment();
        meters.counter(
            "colla.project.space_experience.freshness",
            "freshness", event.freshness().value()
        ).increment();
        meters.counter(
            "colla.project.space_experience.duration",
            "event_kind", event.eventKind().value(),
            "duration_bucket", event.durationBucket().value()
        ).increment();
    }
}
