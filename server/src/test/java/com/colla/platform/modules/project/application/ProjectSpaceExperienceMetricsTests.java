package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.DurationBucket;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.ErrorCode;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.EventKind;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.ExperienceMode;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.Freshness;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.Outcome;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RouteKey;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RolloutState;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectSpaceExperienceMetricsTests {
    @Test
    void metricTagsRemainBoundedAndNeverContainIdentifiersPathsOrContent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProjectSpaceExperienceMetrics metrics = new ProjectSpaceExperienceMetrics(registry);
        for (int index = 0; index < 200; index++) {
            metrics.recordTelemetry(event(UUID.randomUUID()));
            metrics.recordRollout(RolloutState.enabled);
        }

        assertThat(registry.getMeters()).hasSize(6);
        assertThat(registry.getMeters())
            .allSatisfy(meter -> {
                assertThat(meter.getId().getTags())
                    .extracting(tag -> tag.getKey())
                    .isSubsetOf(
                        "event_kind",
                        "outcome",
                        "route_key",
                        "error_code",
                        "mode",
                        "freshness",
                        "duration_bucket",
                        "state"
                    );
                assertThat(meter.getId().toString())
                    .doesNotContain("/api/", "workspace", "space_id", "user_id", "event_id", "content");
            });
        assertThat(registry.find("colla.project.space_experience.ui.events").counter().count())
            .isEqualTo(200);
    }

    private TelemetryEvent event(UUID eventId) {
        return new TelemetryEvent(
            eventId,
            EventKind.ENTRY,
            RouteKey.OVERVIEW,
            ExperienceMode.SIMPLE,
            Outcome.SHOWN,
            DurationBucket.UNDER_5S,
            ErrorCode.NONE,
            Freshness.FRESH
        );
    }
}
