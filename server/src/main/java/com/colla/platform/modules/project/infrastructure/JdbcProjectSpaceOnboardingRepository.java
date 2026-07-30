package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.AcknowledgedStep;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingMutation;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingState;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingVersionConflictException;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.TelemetryEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectSpaceOnboardingRepository implements ProjectSpaceOnboardingRepository {
    private static final TypeReference<List<AcknowledgedStep>> ACKNOWLEDGED_STEP_LIST =
        new TypeReference<>() {
        };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProjectSpaceOnboardingRepository(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<OnboardingState> find(UUID workspaceId, UUID spaceId, UUID userId) {
        return jdbc.query("""
            select schema_version, flow_version, starting_point, scenario_key,
                   acknowledged_steps, dismissed_flow_version, telemetry_opt_out,
                   last_request_id, version, updated_at
              from project_space_onboarding_states
             where workspace_id=? and space_id=? and user_id=?
            """, (rs, row) -> new OnboardingState(
                rs.getInt("schema_version"),
                rs.getString("flow_version"),
                rs.getString("starting_point"),
                rs.getString("scenario_key"),
                readAcknowledgedSteps(rs.getString("acknowledged_steps")),
                rs.getString("dismissed_flow_version"),
                rs.getBoolean("telemetry_opt_out"),
                rs.getObject("last_request_id", UUID.class),
                rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant()
            ), workspaceId, spaceId, userId).stream().findFirst();
    }

    @Override
    public OnboardingState save(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        OnboardingMutation mutation,
        long expectedVersion
    ) {
        String acknowledgements = writeAcknowledgedSteps(mutation.acknowledgedSteps());
        int changed;
        if (expectedVersion == 0) {
            changed = jdbc.update("""
                insert into project_space_onboarding_states(
                    id, workspace_id, space_id, user_id, schema_version, flow_version,
                    starting_point, scenario_key, acknowledged_steps,
                    dismissed_flow_version, telemetry_opt_out, last_request_id,
                    version, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, 1, now())
                on conflict (workspace_id, space_id, user_id) do nothing
                """,
                UUID.randomUUID(),
                workspaceId,
                spaceId,
                userId,
                mutation.schemaVersion(),
                mutation.flowVersion(),
                mutation.startingPoint(),
                mutation.scenarioKey(),
                acknowledgements,
                mutation.dismissedFlowVersion(),
                mutation.telemetryOptOut(),
                mutation.requestId()
            );
        } else {
            changed = jdbc.update("""
                update project_space_onboarding_states
                   set schema_version=?, flow_version=?, starting_point=?, scenario_key=?,
                       acknowledged_steps=?::jsonb, dismissed_flow_version=?,
                       telemetry_opt_out=?, last_request_id=?,
                       version=version+1, updated_at=now()
                 where workspace_id=? and space_id=? and user_id=? and version=?
                """,
                mutation.schemaVersion(),
                mutation.flowVersion(),
                mutation.startingPoint(),
                mutation.scenarioKey(),
                acknowledgements,
                mutation.dismissedFlowVersion(),
                mutation.telemetryOptOut(),
                mutation.requestId(),
                workspaceId,
                spaceId,
                userId,
                expectedVersion
            );
        }
        Optional<OnboardingState> current = find(workspaceId, spaceId, userId);
        if (changed == 1) {
            return current.orElseThrow();
        }
        if (current.map(OnboardingState::lastRequestId).filter(mutation.requestId()::equals).isPresent()) {
            return current.orElseThrow();
        }
        throw new OnboardingVersionConflictException();
    }

    @Override
    public int appendTelemetry(
        UUID workspaceId,
        UUID spaceId,
        List<TelemetryEvent> events
    ) {
        int inserted = 0;
        for (TelemetryEvent event : events) {
            inserted += jdbc.update("""
                insert into project_space_onboarding_telemetry_events(
                    event_id, workspace_id, space_id, flow_version, step_key,
                    outcome, duration_bucket, error_code, recorded_at, expires_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now() + interval '30 days')
                on conflict (event_id) do nothing
                """,
                event.eventId(),
                workspaceId,
                spaceId,
                event.flowVersion(),
                event.stepKey(),
                event.outcome(),
                event.durationBucket(),
                event.errorCode()
            );
        }
        return inserted;
    }

    @Override
    public int purgeExpiredTelemetry(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return jdbc.update("""
            delete from project_space_onboarding_telemetry_events
             where event_id in (
                 select event_id
                   from project_space_onboarding_telemetry_events
                  where expires_at <= now()
                  order by expires_at, event_id
                  limit ?
             )
            """, limit);
    }

    private String writeAcknowledgedSteps(List<AcknowledgedStep> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize onboarding acknowledgements", exception);
        }
    }

    private List<AcknowledgedStep> readAcknowledgedSteps(String value) {
        try {
            return objectMapper.readValue(value, ACKNOWLEDGED_STEP_LIST);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read onboarding acknowledgements", exception);
        }
    }
}
