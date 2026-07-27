package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreference;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateBinding;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.WindowIndexEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkItemCalendarRepository implements WorkItemCalendarRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemCalendarRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CalendarPreference> findPreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select view_key, binding_json, timezone, view_mode,
                           aggregate_version, updated_at
                      from project_work_item_calendar_preferences
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                    """,
                (resultSet, rowNumber) -> new CalendarPreference(
                    resultSet.getString("view_key"),
                    binding(resultSet.getString("binding_json")),
                    resultSet.getString("timezone"),
                    resultSet.getString("view_mode"),
                    resultSet.getLong("aggregate_version"),
                    resultSet.getTimestamp("updated_at").toInstant()
                ),
                workspaceId, spaceId, userId, viewKey
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public CalendarPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        CalendarPreferenceCommand command
    ) {
        String requestHash = sha256(json(List.of(
            viewKey, command.expectedVersion(), command.binding(),
            command.timezone(), command.mode()
        )));
        Optional<CommandRecord> existing = findCommand(
            workspaceId, spaceId, userId, "save_preference", command.requestId()
        );
        if (existing.isPresent()) {
            assertReplay(existing.get(), requestHash);
            return preference(existing.get().responseJson());
        }
        Optional<CalendarPreference> current = findPreference(
            workspaceId, spaceId, userId, viewKey
        );
        if (current.isPresent() && current.get().version() != command.expectedVersion()
            || current.isEmpty() && command.expectedVersion() != 0) {
            throw failure(
                "CALENDAR_PREFERENCE_VERSION_CONFLICT",
                "Calendar preference changed; refresh and retry"
            );
        }
        int changed = current.isEmpty()
            ? jdbcTemplate.update(
                """
                    insert into project_work_item_calendar_preferences (
                        workspace_id, space_id, user_id, view_key, schema_version,
                        binding_json, timezone, view_mode, aggregate_version,
                        created_at, updated_at
                    ) values (?, ?, ?, ?, 1, ?::jsonb, ?, ?, 1, now(), now())
                    on conflict do nothing
                    """,
                workspaceId, spaceId, userId, viewKey, json(command.binding()),
                command.timezone(), command.mode()
            )
            : jdbcTemplate.update(
                """
                    update project_work_item_calendar_preferences
                       set binding_json=?::jsonb, timezone=?, view_mode=?,
                           aggregate_version=aggregate_version+1, updated_at=now()
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                       and aggregate_version=?
                    """,
                json(command.binding()), command.timezone(), command.mode(),
                workspaceId, spaceId, userId, viewKey, command.expectedVersion()
            );
        if (changed != 1) {
            throw failure(
                "CALENDAR_PREFERENCE_VERSION_CONFLICT",
                "Calendar preference changed; refresh and retry"
            );
        }
        CalendarPreference result = findPreference(
            workspaceId, spaceId, userId, viewKey
        ).orElseThrow();
        jdbcTemplate.update(
            """
                insert into project_work_item_calendar_commands (
                    id, workspace_id, space_id, user_id, view_key, work_item_id,
                    operation, request_id, request_hash, expected_version, status,
                    response_json, created_at, completed_at
                ) values (?, ?, ?, ?, ?, null, 'save_preference', ?, ?, ?, 'completed',
                          ?::jsonb, now(), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, userId, viewKey,
            command.requestId(), requestHash, command.expectedVersion(), json(result)
        );
        return result;
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String operation,
        String requestId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, request_hash, status, response_json
                      from project_work_item_calendar_commands
                     where workspace_id=? and space_id=? and user_id=?
                       and operation=? and request_id=?
                    """,
                (resultSet, rowNumber) -> new CommandRecord(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("request_hash"),
                    resultSet.getString("status"),
                    resultSet.getString("response_json")
                ),
                workspaceId, spaceId, userId, operation, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public CommandRecord beginCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        UUID workItemId,
        String operation,
        String requestId,
        String requestHash,
        long expectedVersion
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_work_item_calendar_commands (
                    id, workspace_id, space_id, user_id, view_key, work_item_id,
                    operation, request_id, request_hash, expected_version, status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', now())
                on conflict (workspace_id, space_id, user_id, operation, request_id) do nothing
                """,
            id, workspaceId, spaceId, userId, viewKey, workItemId,
            operation, requestId, requestHash, expectedVersion
        );
        return findCommand(workspaceId, spaceId, userId, operation, requestId).orElseThrow();
    }

    @Override
    public void completeCommand(UUID commandId, String responseJson) {
        int changed = jdbcTemplate.update(
            """
                update project_work_item_calendar_commands
                   set status='completed', response_json=?::jsonb, completed_at=now()
                 where id=? and status='pending'
                """,
            responseJson, commandId
        );
        if (changed != 1) {
            throw failure("CALENDAR_REQUEST_CONFLICT", "Calendar command could not be completed");
        }
    }

    @Override
    @Transactional
    public void replaceWindowIndex(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        List<WindowIndexEntry> entries
    ) {
        jdbcTemplate.update(
            """
                delete from project_work_item_calendar_window_index
                 where workspace_id=? and space_id=? and user_id=? and view_key=?
                """,
            workspaceId, spaceId, userId, viewKey
        );
        for (WindowIndexEntry entry : entries) {
            jdbcTemplate.update(
                """
                    insert into project_work_item_calendar_window_index (
                        workspace_id, space_id, user_id, view_key, work_item_id,
                        source_work_item_version, start_date, end_date, all_day, rebuilt_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    on conflict (workspace_id, space_id, user_id, view_key, work_item_id)
                    do update set
                        source_work_item_version=excluded.source_work_item_version,
                        start_date=excluded.start_date,
                        end_date=excluded.end_date,
                        all_day=excluded.all_day,
                        rebuilt_at=excluded.rebuilt_at
                    """,
                workspaceId, spaceId, userId, viewKey, entry.workItemId(),
                entry.sourceWorkItemVersion(), Date.valueOf(entry.startDate()),
                Date.valueOf(entry.endDate()), entry.allDay()
            );
        }
    }

    @Override
    public void recordRender(
        UUID workspaceId,
        UUID spaceId,
        String viewKey,
        int windowDays,
        int eventCount,
        int overlapLanes
    ) {
        jdbcTemplate.update(
            """
                insert into project_work_item_calendar_projection_stats (
                    workspace_id, space_id, view_key, render_count,
                    last_window_days, last_event_count, last_overlap_lanes, updated_at
                ) values (?, ?, ?, 1, ?, ?, ?, now())
                on conflict (workspace_id, space_id, view_key) do update
                    set render_count=project_work_item_calendar_projection_stats.render_count+1,
                        last_window_days=excluded.last_window_days,
                        last_event_count=excluded.last_event_count,
                        last_overlap_lanes=excluded.last_overlap_lanes,
                        updated_at=excluded.updated_at
                """,
            workspaceId, spaceId, viewKey, windowDays, eventCount, overlapLanes
        );
    }

    private void assertReplay(CommandRecord record, String requestHash) {
        if (!requestHash.equals(record.requestHash())) {
            throw failure(
                "CALENDAR_REQUEST_CONFLICT",
                "Calendar request ID was reused with different input"
            );
        }
        if (!"completed".equals(record.status())) {
            throw failure("CALENDAR_REQUEST_IN_PROGRESS", "Calendar request is already in progress");
        }
    }

    private DateBinding binding(String value) {
        try {
            return objectMapper.readValue(value, DateBinding.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored calendar binding is invalid", exception);
        }
    }

    private CalendarPreference preference(String value) {
        try {
            return objectMapper.readValue(value, CalendarPreference.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored calendar preference response is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure("INVALID_CALENDAR_CONFIGURATION", "Calendar configuration is invalid", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
