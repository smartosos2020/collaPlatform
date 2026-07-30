package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.FLOW_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.AcknowledgedStep;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingMutation;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingVersionConflictException;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.TelemetryEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ProjectSpaceOnboardingRepositoryIntegrationTests {
    @Autowired
    private ProjectSpaceOnboardingRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v141StateIsCasVersionedCallerIdempotentResettableAndUserScoped() {
        Fixture fixture = fixture();
        UUID firstRequest = UUID.randomUUID();
        OnboardingMutation selected = mutation(
            firstRequest,
            "scenario",
            "development",
            List.of(new AcknowledgedStep("choose_starting_point", "seen")),
            null,
            false
        );

        var owner = repository.save(
            fixture.workspaceId(), fixture.spaceId(), fixture.ownerId(), selected, 0
        );
        var replay = repository.save(
            fixture.workspaceId(), fixture.spaceId(), fixture.ownerId(), selected, 0
        );
        assertThat(owner.version()).isEqualTo(1);
        assertThat(replay.version()).isEqualTo(1);
        assertThat(replay.scenarioKey()).isEqualTo("development");

        var member = repository.save(
            fixture.workspaceId(),
            fixture.spaceId(),
            fixture.memberId(),
            mutation(UUID.randomUUID(), "blank", null, List.of(), null, true),
            0
        );
        assertThat(member.version()).isEqualTo(1);
        assertThat(repository.find(
            fixture.workspaceId(), fixture.spaceId(), fixture.ownerId()
        )).get().extracting(value -> value.scenarioKey()).isEqualTo("development");
        assertThat(repository.find(
            UUID.randomUUID(), fixture.spaceId(), fixture.ownerId()
        )).isEmpty();
        assertThat(repository.find(
            fixture.workspaceId(), UUID.randomUUID(), fixture.ownerId()
        )).isEmpty();

        assertThatThrownBy(() -> repository.save(
            fixture.workspaceId(),
            fixture.spaceId(),
            fixture.ownerId(),
            mutation(UUID.randomUUID(), "blank", null, List.of(), null, false),
            0
        )).isInstanceOf(OnboardingVersionConflictException.class);

        var reset = repository.save(
            fixture.workspaceId(),
            fixture.spaceId(),
            fixture.ownerId(),
            mutation(UUID.randomUUID(), "unselected", null, List.of(), null, false),
            1
        );
        assertThat(reset.version()).isEqualTo(2);
        assertThat(reset.startingPoint()).isEqualTo("unselected");
        assertThat(reset.acknowledgedSteps()).isEmpty();
        assertThat(repository.find(
            fixture.workspaceId(), fixture.spaceId(), fixture.memberId()
        )).get().extracting(value -> value.startingPoint()).isEqualTo("blank");
    }

    @Test
    void v141TelemetryIsAnonymousAllowlistedIdempotentAndExpirable() {
        Fixture fixture = fixture();
        UUID eventId = UUID.randomUUID();
        TelemetryEvent event = new TelemetryEvent(
            eventId,
            FLOW_VERSION,
            "find_work",
            "shown",
            "under_5s",
            "none"
        );

        assertThat(repository.appendTelemetry(
            fixture.workspaceId(), fixture.spaceId(), List.of(event, event)
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            """
            select count(*)
              from project_space_onboarding_telemetry_events
             where workspace_id=? and space_id=? and event_id=?
            """,
            Integer.class,
            fixture.workspaceId(),
            fixture.spaceId(),
            eventId
        )).isEqualTo(1);

        List<String> columns = jdbc.queryForList("""
            select column_name
              from information_schema.columns
             where table_schema='public'
               and table_name='project_space_onboarding_telemetry_events'
             order by ordinal_position
            """, String.class);
        assertThat(columns)
            .doesNotContain("user_id", "title", "body", "content", "member_id")
            .contains(
                "event_id", "workspace_id", "space_id", "flow_version",
                "step_key", "outcome", "duration_bucket", "error_code",
                "recorded_at", "expires_at"
            );

        jdbc.update("""
            update project_space_onboarding_telemetry_events
               set recorded_at=now() - interval '31 days',
                   expires_at=now() - interval '1 day'
             where event_id=?
            """, eventId);
        assertThat(repository.purgeExpiredTelemetry(10)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from project_space_onboarding_telemetry_events where event_id=?",
            Integer.class,
            eventId
        )).isZero();
    }

    private OnboardingMutation mutation(
        UUID requestId,
        String startingPoint,
        String scenarioKey,
        List<AcknowledgedStep> acknowledgedSteps,
        String dismissedFlowVersion,
        boolean telemetryOptOut
    ) {
        return new OnboardingMutation(
            1,
            FLOW_VERSION,
            startingPoint,
            scenarioKey,
            acknowledgedSteps,
            dismissedFlowVersion,
            telemetryOptOut,
            requestId
        );
    }

    private Fixture fixture() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        String suffix = suffix();
        jdbc.update("""
            insert into workspaces(id, name, slug, status, created_at, updated_at)
            values (?, ?, ?, 'active', now(), now())
            """, workspaceId, "Onboarding " + suffix, "onboarding-" + suffix);
        jdbc.update("""
            insert into users(
                id, workspace_id, username, password_hash, display_name, status, created_at, updated_at
            ) values (?, ?, ?, 'unused', 'Owner', 'active', now(), now())
            """, ownerId, workspaceId, "onboarding_owner_" + suffix);
        jdbc.update("""
            insert into users(
                id, workspace_id, username, password_hash, display_name, status, created_at, updated_at
            ) values (?, ?, ?, 'unused', 'Member', 'active', now(), now())
            """, memberId, workspaceId, "onboarding_member_" + suffix);
        jdbc.update("""
            insert into project_spaces(
                id, workspace_id, space_key, name, description, status, visibility,
                version, created_by, created_at, updated_by, updated_at
            ) values (?, ?, ?, 'Onboarding Space', '', 'active', 'discoverable',
                0, ?, now(), ?, now())
            """, spaceId, workspaceId, "onboarding-" + suffix, ownerId, ownerId);
        return new Fixture(workspaceId, spaceId, ownerId, memberId);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record Fixture(
        UUID workspaceId,
        UUID spaceId,
        UUID ownerId,
        UUID memberId
    ) {
    }
}
