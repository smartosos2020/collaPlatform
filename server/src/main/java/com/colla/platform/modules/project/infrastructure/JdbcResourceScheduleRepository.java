package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ResourceScheduleModels.SchedulePreference;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.SavePreferenceCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcResourceScheduleRepository implements ResourceScheduleRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcResourceScheduleRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SchedulePreference> findPreference(
        UUID workspaceId, UUID spaceId, UUID userId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select id, window_start, window_end, zoom, aggregate_version, updated_at
                      from project_resource_schedule_preferences
                     where workspace_id=? and space_id=? and user_id=?
                    """,
                (rs, row) -> new SchedulePreference(
                    rs.getObject("id", UUID.class),
                    rs.getObject("window_start", java.time.LocalDate.class),
                    rs.getObject("window_end", java.time.LocalDate.class),
                    rs.getString("zoom"), rs.getLong("aggregate_version"),
                    rs.getTimestamp("updated_at").toInstant()
                ),
                workspaceId, spaceId, userId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public SchedulePreference savePreference(
        UUID workspaceId, UUID spaceId, UUID userId,
        SavePreferenceCommand command, String hash
    ) {
        int changed;
        if (command.expectedVersion() == 0) {
            changed = jdbc.update(
                """
                    insert into project_resource_schedule_preferences(
                        id, workspace_id, space_id, user_id, window_start,
                        window_end, zoom, aggregate_version, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, 1, now())
                    on conflict (workspace_id, space_id, user_id) do nothing
                    """,
                UUID.randomUUID(), workspaceId, spaceId, userId,
                command.windowStart(), command.windowEnd(), command.zoom()
            );
        } else {
            changed = jdbc.update(
                """
                    update project_resource_schedule_preferences
                       set window_start=?, window_end=?, zoom=?,
                           aggregate_version=aggregate_version+1, updated_at=now()
                     where workspace_id=? and space_id=? and user_id=?
                       and aggregate_version=?
                    """,
                command.windowStart(), command.windowEnd(), command.zoom(),
                workspaceId, spaceId, userId, command.expectedVersion()
            );
        }
        if (changed != 1) {
            throw failure("RESOURCE_SCHEDULE_VERSION_CONFLICT", "Schedule preference changed concurrently");
        }
        SchedulePreference result = findPreference(workspaceId, spaceId, userId).orElseThrow();
        saveCommand(
            workspaceId, spaceId, userId, "save_preference",
            command.requestId(), hash, result
        );
        return result;
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation, String requestId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select request_hash, response_json
                      from project_resource_adjustment_commands
                     where workspace_id=? and space_id=? and actor_id=?
                       and operation=? and request_id=?
                    """,
                (rs, row) -> new CommandRecord(
                    rs.getString("request_hash"), rs.getString("response_json")
                ),
                workspaceId, spaceId, actorId, operation, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void saveCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String operation,
        String requestId, String hash, Object response
    ) {
        jdbc.update(
            """
                insert into project_resource_adjustment_commands(
                    id, workspace_id, space_id, actor_id, operation,
                    request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, operation,
            requestId, hash, json(response)
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
