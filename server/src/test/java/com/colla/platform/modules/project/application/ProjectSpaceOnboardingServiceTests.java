package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.FLOW_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.AcknowledgedStep;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingCommand;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingMutation;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingState;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.TelemetryEvent;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceOnboardingRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ProjectSpaceOnboardingServiceTests {
    @Test
    void defaultViewIsResettableExperienceStateAndNeverAnAuthorizationFact() {
        Fixture fixture = fixture("owner", "active");
        when(fixture.repository().find(
            fixture.user().workspaceId(), fixture.spaceId(), fixture.user().id()
        )).thenReturn(Optional.empty());

        var view = fixture.service().get(fixture.user(), fixture.spaceId());

        assertThat(view.schemaVersion()).isEqualTo(1);
        assertThat(view.flowVersion()).isEqualTo(FLOW_VERSION);
        assertThat(view.currentFlowVersion()).isEqualTo(FLOW_VERSION);
        assertThat(view.version()).isZero();
        assertThat(view.startingPoint().kind()).isEqualTo("unselected");
        assertThat(view.track()).isEqualTo("manager");
        assertThat(view.selectionEffect()).isEqualTo("experience_only");
        assertThat(view.installationRequested()).isFalse();
        assertThat(view.publicationRequested()).isFalse();
        assertThat(view.checklist())
            .extracting(step -> step.stepKey())
            .contains("choose_starting_point", "configure_work_model", "publish_configuration")
            .doesNotContain("install_scenario");
    }

    @Test
    void nonMemberGetsAnOpaqueNotFoundEvenForDiscoverableSpaces() {
        Fixture fixture = fixture(null, "active");

        assertThatThrownBy(() -> fixture.service().get(fixture.user(), fixture.spaceId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        verify(fixture.repository(), never()).find(any(), any(), any());
    }

    @Test
    void scenarioSelectionOnlyPersistsExperienceStateAndIsCallerIdempotent() {
        Fixture fixture = fixture("owner", "active");
        UUID requestId = UUID.randomUUID();
        when(fixture.repository().find(
            fixture.user().workspaceId(), fixture.spaceId(), fixture.user().id()
        )).thenReturn(Optional.empty());
        when(fixture.repository().save(
            eq(fixture.user().workspaceId()),
            eq(fixture.spaceId()),
            eq(fixture.user().id()),
            any(OnboardingMutation.class),
            eq(0L)
        )).thenAnswer(invocation -> {
            OnboardingMutation value = invocation.getArgument(3);
            return persisted(value, 1);
        });

        var selected = fixture.service().command(
            fixture.user(),
            fixture.spaceId(),
            command(requestId, 0, "select_starting_point", "scenario", "development", null, null, null)
        );

        assertThat(selected.startingPoint().kind()).isEqualTo("scenario");
        assertThat(selected.startingPoint().scenarioKey()).isEqualTo("development");
        assertThat(selected.selectionEffect()).isEqualTo("experience_only");
        assertThat(selected.installationRequested()).isFalse();
        assertThat(selected.publicationRequested()).isFalse();
        assertThat(selected.checklist())
            .extracting(step -> step.stepKey())
            .contains("preview_impact", "install_scenario");

        ArgumentCaptor<OnboardingMutation> mutation =
            ArgumentCaptor.forClass(OnboardingMutation.class);
        verify(fixture.repository()).save(
            eq(fixture.user().workspaceId()),
            eq(fixture.spaceId()),
            eq(fixture.user().id()),
            mutation.capture(),
            eq(0L)
        );
        assertThat(mutation.getValue().requestId()).isEqualTo(requestId);
        assertThat(mutation.getValue().startingPoint()).isEqualTo("scenario");
        assertThat(mutation.getValue().scenarioKey()).isEqualTo("development");
    }

    @Test
    void onboardingServiceHasNoOwnerCommandDependency() {
        assertThat(
            Arrays.stream(ProjectSpaceOnboardingService.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .map(Class::getName)
        ).containsExactlyInAnyOrder(
            ProjectSpaceService.class.getName(),
            ProjectSpaceOnboardingRepository.class.getName(),
            ProjectSpaceOnboardingCatalog.class.getName()
        );
    }

    @Test
    void staleCasAndCompletedAcknowledgementAreRejected() {
        Fixture fixture = fixture("member", "active");
        OnboardingState state = state(
            FLOW_VERSION, "unselected", null, List.of(), null, false, null, 4
        );
        when(fixture.repository().find(
            fixture.user().workspaceId(), fixture.spaceId(), fixture.user().id()
        )).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> fixture.service().command(
            fixture.user(),
            fixture.spaceId(),
            command(UUID.randomUUID(), 3, "dismiss", null, null, null, null, null)
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );

        assertThatThrownBy(() -> fixture.service().command(
            fixture.user(),
            fixture.spaceId(),
            command(
                UUID.randomUUID(), 4, "acknowledge_step", null, null,
                "find_work", "completed", null
            )
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        verify(fixture.repository(), never()).save(any(), any(), any(), any(), any(Long.class));
    }

    @Test
    void explicitUpgradePreservesAValidScenarioAndOnlyRecognizedAcknowledgements() {
        Fixture fixture = fixture("owner", "active");
        OnboardingState legacy = state(
            "s21-m6-preview",
            "scenario",
            "development",
            List.of(
                new AcknowledgedStep("configure_work_model", "seen"),
                new AcknowledgedStep("retired_step", "skipped")
            ),
            "s21-m6-preview",
            true,
            null,
            7
        );
        when(fixture.repository().find(
            fixture.user().workspaceId(), fixture.spaceId(), fixture.user().id()
        )).thenReturn(Optional.of(legacy));
        when(fixture.repository().save(
            eq(fixture.user().workspaceId()),
            eq(fixture.spaceId()),
            eq(fixture.user().id()),
            any(OnboardingMutation.class),
            eq(7L)
        )).thenAnswer(invocation -> persisted(invocation.getArgument(3), 8));

        var upgraded = fixture.service().command(
            fixture.user(),
            fixture.spaceId(),
            command(UUID.randomUUID(), 7, "upgrade_flow", null, null, null, null, null)
        );

        assertThat(upgraded.flowVersion()).isEqualTo(FLOW_VERSION);
        assertThat(upgraded.startingPoint().kind()).isEqualTo("scenario");
        assertThat(upgraded.startingPoint().scenarioKey()).isEqualTo("development");
        assertThat(upgraded.acknowledgedSteps())
            .extracting(AcknowledgedStep::stepKey)
            .containsExactly("configure_work_model");
        assertThat(upgraded.dismissed()).isFalse();
        assertThat(upgraded.telemetryOptOut()).isTrue();
    }

    @Test
    void telemetryOptOutRemainsAvailableBeforeFlowUpgrade() {
        Fixture fixture = fixture("member", "active");
        OnboardingState legacy = state(
            "s21-m6-preview", "blank", null, List.of(), null, false, null, 3
        );
        when(fixture.repository().find(
            fixture.user().workspaceId(), fixture.spaceId(), fixture.user().id()
        )).thenReturn(Optional.of(legacy));
        when(fixture.repository().save(
            eq(fixture.user().workspaceId()),
            eq(fixture.spaceId()),
            eq(fixture.user().id()),
            any(OnboardingMutation.class),
            eq(3L)
        )).thenAnswer(invocation -> persisted(invocation.getArgument(3), 4));

        var optedOut = fixture.service().command(
            fixture.user(),
            fixture.spaceId(),
            command(
                UUID.randomUUID(), 3, "set_telemetry_opt_out",
                null, null, null, null, true
            )
        );

        assertThat(optedOut.telemetryOptOut()).isTrue();
        assertThat(optedOut.flowVersion()).isEqualTo("s21-m6-preview");
        assertThat(optedOut.migrationRequired()).isTrue();
    }

    @Test
    void telemetryIsAllowlistedAnonymousAndOptOutIsAWriteBarrier() {
        Fixture fixture = fixture("member", "active");
        OnboardingState optedOut = state(
            FLOW_VERSION, "unselected", null, List.of(), null, true, null, 1
        );
        when(fixture.repository().find(
            fixture.user().workspaceId(), fixture.spaceId(), fixture.user().id()
        )).thenReturn(Optional.of(optedOut));
        TelemetryEvent event = new TelemetryEvent(
            UUID.randomUUID(), FLOW_VERSION, "find_work",
            "shown", "under_5s", "none"
        );

        assertThat(fixture.service().recordTelemetry(
            fixture.user(), fixture.spaceId(), List.of(event)
        )).isZero();
        verify(fixture.repository(), never()).appendTelemetry(any(), any(), anyList());

        OnboardingState enabled = state(
            FLOW_VERSION, "unselected", null, List.of(), null, false, null, 2
        );
        when(fixture.repository().find(
            fixture.user().workspaceId(), fixture.spaceId(), fixture.user().id()
        )).thenReturn(Optional.of(enabled));
        when(fixture.repository().appendTelemetry(
            eq(fixture.user().workspaceId()), eq(fixture.spaceId()), anyList()
        )).thenReturn(1);
        assertThat(fixture.service().recordTelemetry(
            fixture.user(), fixture.spaceId(), List.of(event)
        )).isEqualTo(1);

        assertThatThrownBy(() -> fixture.service().recordTelemetry(
            fixture.user(),
            fixture.spaceId(),
            List.of(new TelemetryEvent(
                UUID.randomUUID(), FLOW_VERSION, "customer-title",
                "shown", "under_5s", "none"
            ))
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    private Fixture fixture(String role, String status) {
        ProjectSpaceService spaces = org.mockito.Mockito.mock(ProjectSpaceService.class);
        ProjectSpaceOnboardingRepository repository =
            org.mockito.Mockito.mock(ProjectSpaceOnboardingRepository.class);
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
        when(spaces.getVisible(user, spaceId))
            .thenReturn(space(user.workspaceId(), spaceId, role, status));
        return new Fixture(
            user,
            spaceId,
            repository,
            new ProjectSpaceOnboardingService(
                spaces, repository, new ProjectSpaceOnboardingCatalog()
            )
        );
    }

    private OnboardingCommand command(
        UUID requestId,
        long expectedVersion,
        String action,
        String startingPoint,
        String scenarioKey,
        String stepKey,
        String acknowledgement,
        Boolean telemetryOptOut
    ) {
        return new OnboardingCommand(
            requestId,
            1,
            FLOW_VERSION,
            expectedVersion,
            action,
            startingPoint,
            scenarioKey,
            stepKey,
            acknowledgement,
            telemetryOptOut
        );
    }

    private OnboardingState persisted(OnboardingMutation mutation, long version) {
        return new OnboardingState(
            mutation.schemaVersion(),
            mutation.flowVersion(),
            mutation.startingPoint(),
            mutation.scenarioKey(),
            mutation.acknowledgedSteps(),
            mutation.dismissedFlowVersion(),
            mutation.telemetryOptOut(),
            mutation.requestId(),
            version,
            Instant.now()
        );
    }

    private OnboardingState state(
        String flowVersion,
        String startingPoint,
        String scenarioKey,
        List<AcknowledgedStep> acknowledgedSteps,
        String dismissedFlowVersion,
        boolean telemetryOptOut,
        UUID requestId,
        long version
    ) {
        return new OnboardingState(
            1,
            flowVersion,
            startingPoint,
            scenarioKey,
            acknowledgedSteps,
            dismissedFlowVersion,
            telemetryOptOut,
            requestId,
            version,
            Instant.now()
        );
    }

    private ProjectSpaceSummary space(
        UUID workspaceId,
        UUID spaceId,
        String role,
        String status
    ) {
        Instant now = Instant.now();
        return new ProjectSpaceSummary(
            spaceId,
            workspaceId,
            "space",
            "Space",
            "",
            status,
            "discoverable",
            1,
            role,
            role == null ? 0 : 1,
            UUID.randomUUID(),
            now,
            UUID.randomUUID(),
            now,
            "disabled".equals(status) ? now : null,
            "archived".equals(status) ? now : null
        );
    }

    private record Fixture(
        CurrentUser user,
        UUID spaceId,
        ProjectSpaceOnboardingRepository repository,
        ProjectSpaceOnboardingService service
    ) {
    }
}
