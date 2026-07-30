package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryEvent;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryEventCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class ProjectSpaceExperienceTelemetryServiceTests {
    @Test
    void acceptsOnlyAllowlistedDimensionsAndEmitsTypedMetrics() {
        Fixture fixture = fixture(10_000);
        TelemetryEventCommand command = command(UUID.randomUUID());

        fixture.service().record(1, List.of(command));

        ArgumentCaptor<TelemetryEvent> event = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(fixture.metrics()).recordTelemetry(event.capture());
        org.assertj.core.api.Assertions.assertThat(event.getValue().eventKind().value()).isEqualTo("entry");
        org.assertj.core.api.Assertions.assertThat(event.getValue().routeKey().value()).isEqualTo("overview");

        List<TelemetryEventCommand> invalid = List.of(
            replace(command, 0, "free-form"),
            replace(command, 1, "/api/project-spaces/private"),
            replace(command, 2, "owner"),
            replace(command, 3, "clicked-secret"),
            replace(command, 4, "12345ms"),
            replace(command, 5, "raw-stack"),
            replace(command, 6, "yesterday")
        );
        for (TelemetryEventCommand candidate : invalid) {
            assertThatThrownBy(() -> fixture.service().record(1, List.of(candidate)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("contract is invalid");
        }
    }

    @Test
    void samplingIsStablePerEventIdAndDisabledTelemetryIsANoOp() {
        Fixture sampled = fixture(5_000);
        UUID included = findEventId(sampled.properties(), true);
        UUID excluded = findEventId(sampled.properties(), false);

        sampled.service().record(1, List.of(command(included), command(excluded)));
        verify(sampled.metrics()).recordTelemetry(
            org.mockito.ArgumentMatchers.argThat(event -> event.eventId().equals(included))
        );
        verify(sampled.metrics(), never()).recordTelemetry(
            org.mockito.ArgumentMatchers.argThat(event -> event.eventId().equals(excluded))
        );

        Fixture disabled = fixture(10_000);
        disabled.properties().getTelemetry().setEnabled(false);
        disabled.service().record(1, List.of(command(UUID.randomUUID())));
        verifyNoInteractions(disabled.metrics());
    }

    @Test
    void rejectsWrongSchemaAndConfiguredOrHardBatchOverflow() {
        Fixture fixture = fixture(10_000);
        fixture.properties().getTelemetry().setMaxBatchSize(2);
        List<TelemetryEventCommand> three = List.of(
            command(UUID.randomUUID()),
            command(UUID.randomUUID()),
            command(UUID.randomUUID())
        );
        assertThatThrownBy(() -> fixture.service().record(2, List.of(command(UUID.randomUUID()))))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> fixture.service().record(1, three))
            .isInstanceOf(ResponseStatusException.class);

        fixture.properties().getTelemetry().setMaxBatchSize(20);
        List<TelemetryEventCommand> twentyOne = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            twentyOne.add(command(UUID.randomUUID()));
        }
        assertThatThrownBy(() -> fixture.service().record(1, twentyOne))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void optionalMetricsFailureDoesNotTurnTelemetryIntoAProductDependency() {
        Fixture fixture = fixture(10_000);
        doThrow(new IllegalStateException("registry unavailable"))
            .when(fixture.metrics()).recordTelemetry(
                org.mockito.ArgumentMatchers.any(TelemetryEvent.class)
            );

        fixture.service().record(1, List.of(command(UUID.randomUUID())));
    }

    private UUID findEventId(
        ProjectSpaceExperienceRolloutProperties properties,
        boolean included
    ) {
        for (long index = 0; index < 100_000; index++) {
            UUID eventId = new UUID(0, index);
            int bucket = ProjectSpaceExperienceBucket.stableBucket(
                properties.getEvaluationSalt(),
                properties.getPolicyVersion(),
                "telemetry",
                eventId.toString()
            );
            if ((bucket < 5_000) == included) {
                return eventId;
            }
        }
        throw new AssertionError("Unable to find deterministic sample fixture");
    }

    private Fixture fixture(int sampleBasisPoints) {
        ProjectSpaceExperienceRolloutProperties properties =
            new ProjectSpaceExperienceRolloutProperties();
        properties.setEnabled(true);
        properties.setKillSwitch(false);
        properties.setPolicyVersion("s21-m7-test");
        properties.setEvaluationSalt("stable-test-salt");
        properties.getTelemetry().setEnabled(true);
        properties.getTelemetry().setSampleBasisPoints(sampleBasisPoints);
        properties.getTelemetry().setMaxBatchSize(20);
        ProjectSpaceExperienceMetrics metrics = mock(ProjectSpaceExperienceMetrics.class);
        return new Fixture(
            properties,
            metrics,
            new ProjectSpaceExperienceTelemetryService(properties, metrics)
        );
    }

    private TelemetryEventCommand command(UUID eventId) {
        return new TelemetryEventCommand(
            eventId,
            "entry",
            "overview",
            "simple",
            "shown",
            "under_5s",
            "none",
            "fresh"
        );
    }

    private TelemetryEventCommand replace(
        TelemetryEventCommand source,
        int field,
        String value
    ) {
        String[] values = {
            source.eventKind(),
            source.routeKey(),
            source.mode(),
            source.outcome(),
            source.durationBucket(),
            source.errorCode(),
            source.freshness()
        };
        values[field] = value;
        return new TelemetryEventCommand(
            source.eventId(),
            values[0],
            values[1],
            values[2],
            values[3],
            values[4],
            values[5],
            values[6]
        );
    }

    private record Fixture(
        ProjectSpaceExperienceRolloutProperties properties,
        ProjectSpaceExperienceMetrics metrics,
        ProjectSpaceExperienceTelemetryService service
    ) {
    }
}
