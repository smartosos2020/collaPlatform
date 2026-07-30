package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RolloutState;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectSpaceExperienceRolloutServiceTests {
    @Test
    void globalKillWinsAndStillRequiresSpaceVisibility() {
        Fixture fixture = fixture();
        fixture.properties().setEnabled(true);
        fixture.properties().setKillSwitch(true);
        fixture.properties().setRolloutBasisPoints(10_000);
        fixture.properties().getTelemetry().setEnabled(true);

        var result = fixture.service().get(fixture.user(), fixture.spaceId());

        assertThat(result.enabled()).isFalse();
        assertThat(result.state()).isEqualTo(RolloutState.temporarily_disabled);
        assertThat(result.fallbackContext()).isEqualTo("canonical_project_space");
        assertThat(result.cacheMaxAgeSeconds()).isZero();
        assertThat(result.telemetry().enabled()).isFalse();
        verify(fixture.spaces()).getVisible(fixture.user(), fixture.spaceId());
        verify(fixture.metrics()).recordRollout(RolloutState.temporarily_disabled);
    }

    @Test
    void exactExclusionWinsOverInclusionAndInclusionWinsOverTheBucket() {
        Fixture excluded = fixture();
        excluded.properties().setEnabled(true);
        excluded.properties().setKillSwitch(false);
        excluded.properties().setRolloutBasisPoints(10_000);
        excluded.properties().setIncludedUserIds(new LinkedHashSet<>(Set.of(excluded.user().id())));
        excluded.properties().setExcludedSpaceIds(new LinkedHashSet<>(Set.of(excluded.spaceId())));
        assertThat(excluded.service().get(excluded.user(), excluded.spaceId()).state())
            .isEqualTo(RolloutState.baseline);

        Fixture included = fixture();
        included.properties().setEnabled(true);
        included.properties().setKillSwitch(false);
        included.properties().setRolloutBasisPoints(0);
        included.properties().setIncludedWorkspaceIds(
            new LinkedHashSet<>(Set.of(included.user().workspaceId()))
        );
        assertThat(included.service().get(included.user(), included.spaceId()).state())
            .isEqualTo(RolloutState.enabled);
    }

    @Test
    void deterministicBucketHasStableBoundaries() {
        Fixture enabled = fixture();
        enabled.properties().setEnabled(true);
        enabled.properties().setKillSwitch(false);
        enabled.properties().setRolloutBasisPoints(10_000);
        var first = enabled.service().get(enabled.user(), enabled.spaceId());
        var second = enabled.service().get(enabled.user(), enabled.spaceId());
        assertThat(first.state()).isEqualTo(RolloutState.enabled);
        assertThat(second.state()).isEqualTo(first.state());

        enabled.properties().setRolloutBasisPoints(0);
        assertThat(enabled.service().get(enabled.user(), enabled.spaceId()).state())
            .isEqualTo(RolloutState.baseline);
    }

    @Test
    void evaluationFailureReturnsUnknownCanonicalBaselineWithoutSkippingVisibility() {
        ProjectSpaceExperienceRolloutProperties broken =
            mock(ProjectSpaceExperienceRolloutProperties.class);
        Fixture fixture = fixture(broken);
        when(broken.getTelemetry()).thenThrow(new IllegalStateException("configuration unavailable"));

        var result = fixture.service().get(fixture.user(), fixture.spaceId());

        assertThat(result.enabled()).isFalse();
        assertThat(result.state()).isEqualTo(RolloutState.unknown);
        assertThat(result.policyVersion()).isEqualTo("unknown");
        assertThat(result.telemetry().enabled()).isFalse();
        verify(fixture.spaces()).getVisible(fixture.user(), fixture.spaceId());
    }

    private Fixture fixture() {
        ProjectSpaceExperienceRolloutProperties properties =
            new ProjectSpaceExperienceRolloutProperties();
        properties.setPolicyVersion("s21-m7-test");
        properties.setEvaluationSalt("stable-test-salt");
        properties.setCacheMaxAgeSeconds(30);
        properties.getTelemetry().setMaxBatchSize(20);
        return fixture(properties);
    }

    private Fixture fixture(ProjectSpaceExperienceRolloutProperties properties) {
        ProjectSpaceService spaces = mock(ProjectSpaceService.class);
        ProjectSpaceExperienceMetrics metrics = mock(ProjectSpaceExperienceMetrics.class);
        CurrentUser user = new CurrentUser(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "actor",
            "Actor",
            Set.of(),
            Set.of()
        );
        UUID spaceId = UUID.randomUUID();
        when(spaces.getVisible(user, spaceId)).thenReturn(space(user.workspaceId(), spaceId));
        return new Fixture(
            user,
            spaceId,
            spaces,
            properties,
            metrics,
            new ProjectSpaceExperienceRolloutService(spaces, properties, metrics)
        );
    }

    private ProjectSpaceSummary space(UUID workspaceId, UUID spaceId) {
        Instant now = Instant.now();
        return new ProjectSpaceSummary(
            spaceId,
            workspaceId,
            "space",
            "Space",
            "",
            "active",
            "discoverable",
            1,
            "member",
            1,
            UUID.randomUUID(),
            now,
            UUID.randomUUID(),
            now,
            null,
            null
        );
    }

    private record Fixture(
        CurrentUser user,
        UUID spaceId,
        ProjectSpaceService spaces,
        ProjectSpaceExperienceRolloutProperties properties,
        ProjectSpaceExperienceMetrics metrics,
        ProjectSpaceExperienceRolloutService service
    ) {
    }
}
