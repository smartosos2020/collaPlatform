package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ResourcePlanningModels.CalendarException;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.Estimate;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.SaveCalendarCommand;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcResourcePlanningRepository implements ResourcePlanningRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcResourcePlanningRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WorkCalendar> findCalendar(UUID workspaceId, UUID spaceId) {
        try {
            WorkCalendar base = jdbc.queryForObject(
                """
                    select id, timezone, work_days, daily_minutes,
                           aggregate_version, updated_by, updated_at
                      from project_resource_calendars
                     where workspace_id=? and space_id=?
                    """,
                (rs, row) -> new WorkCalendar(
                    rs.getObject("id", UUID.class),
                    rs.getString("timezone"),
                    integers(rs.getString("work_days")),
                    rs.getInt("daily_minutes"),
                    List.of(),
                    rs.getLong("aggregate_version"),
                    rs.getObject("updated_by", UUID.class),
                    rs.getTimestamp("updated_at").toInstant()
                ),
                workspaceId, spaceId
            );
            if (base == null) return Optional.empty();
            List<CalendarException> exceptions = jdbc.query(
                """
                    select id, exception_date, available_minutes, note
                      from project_resource_calendar_exceptions
                     where workspace_id=? and space_id=? and calendar_id=?
                     order by exception_date, id
                    """,
                (rs, row) -> new CalendarException(
                    rs.getObject("id", UUID.class),
                    rs.getObject("exception_date", LocalDate.class),
                    rs.getInt("available_minutes"),
                    rs.getString("note")
                ),
                workspaceId, spaceId, base.id()
            );
            return Optional.of(new WorkCalendar(
                base.id(), base.timezone(), base.workDays(), base.dailyMinutes(),
                exceptions, base.version(), base.updatedBy(), base.updatedAt()
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<Estimate> listEstimates(UUID workspaceId, UUID spaceId, int limit) {
        return jdbc.query(
            """
                select id, work_item_id, estimate_unit, estimate_amount,
                       source_work_item_version, aggregate_version, updated_by, updated_at
                  from project_resource_estimates
                 where workspace_id=? and space_id=?
                 order by updated_at desc, id
                 limit ?
                """,
            this::estimate, workspaceId, spaceId, limit
        );
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId,
        String operation, String requestId
    ) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select request_hash, response_json
                      from project_resource_commands
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
    @Transactional
    public WorkCalendar saveCalendar(
        UUID workspaceId, UUID spaceId, UUID actorId,
        SaveCalendarCommand command, String requestHash
    ) {
        UUID id;
        int changed;
        if (command.expectedVersion() == 0) {
            id = UUID.randomUUID();
            changed = jdbc.update(
                """
                    insert into project_resource_calendars(
                        id, workspace_id, space_id, timezone, work_days,
                        daily_minutes, aggregate_version, updated_by, updated_at
                    ) values (?, ?, ?, ?, cast(? as jsonb), ?, 1, ?, now())
                    on conflict (workspace_id, space_id) do nothing
                    """,
                id, workspaceId, spaceId, command.timezone(),
                json(command.workDays()), command.dailyMinutes(), actorId
            );
        } else {
            id = jdbc.queryForObject(
                """
                    select id from project_resource_calendars
                     where workspace_id=? and space_id=?
                    """,
                UUID.class, workspaceId, spaceId
            );
            changed = jdbc.update(
                """
                    update project_resource_calendars
                       set timezone=?, work_days=cast(? as jsonb), daily_minutes=?,
                           aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and aggregate_version=?
                    """,
                command.timezone(), json(command.workDays()), command.dailyMinutes(),
                actorId, workspaceId, spaceId, command.expectedVersion()
            );
        }
        if (changed != 1) {
            throw failure(
                "RESOURCE_CALENDAR_VERSION_CONFLICT",
                "Resource calendar changed concurrently"
            );
        }
        jdbc.update(
            """
                delete from project_resource_calendar_exceptions
                 where workspace_id=? and space_id=? and calendar_id=?
                """,
            workspaceId, spaceId, id
        );
        for (var value : command.exceptions()) {
            jdbc.update(
                """
                    insert into project_resource_calendar_exceptions(
                        id, workspace_id, space_id, calendar_id,
                        exception_date, available_minutes, note
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """,
                value.id(), workspaceId, spaceId, id, value.date(),
                value.availableMinutes(), value.note()
            );
        }
        WorkCalendar result = findCalendar(workspaceId, spaceId).orElseThrow();
        receipt(
            workspaceId, spaceId, actorId, "save_calendar",
            command.requestId(), requestHash, result
        );
        return result;
    }

    @Override
    @Transactional
    public Estimate saveEstimate(
        UUID workspaceId, UUID spaceId, UUID actorId,
        UUID workItemId, long workItemVersion, String unit,
        BigDecimal amount, long expectedVersion,
        String requestId, String requestHash
    ) {
        int changed;
        if (expectedVersion == 0) {
            changed = jdbc.update(
                """
                    insert into project_resource_estimates(
                        id, workspace_id, space_id, work_item_id,
                        estimate_unit, estimate_amount, source_work_item_version,
                        aggregate_version, updated_by, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, 1, ?, now())
                    on conflict (workspace_id, space_id, work_item_id) do nothing
                    """,
                UUID.randomUUID(), workspaceId, spaceId, workItemId,
                unit, amount, workItemVersion, actorId
            );
        } else {
            changed = jdbc.update(
                """
                    update project_resource_estimates
                       set estimate_unit=?, estimate_amount=?,
                           source_work_item_version=?, aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and work_item_id=?
                       and aggregate_version=?
                    """,
                unit, amount, workItemVersion, actorId,
                workspaceId, spaceId, workItemId, expectedVersion
            );
        }
        if (changed != 1) {
            throw failure(
                "RESOURCE_ESTIMATE_VERSION_CONFLICT",
                "Resource estimate changed concurrently"
            );
        }
        Estimate result = jdbc.queryForObject(
            """
                select id, work_item_id, estimate_unit, estimate_amount,
                       source_work_item_version, aggregate_version, updated_by, updated_at
                  from project_resource_estimates
                 where workspace_id=? and space_id=? and work_item_id=?
                """,
            this::estimate, workspaceId, spaceId, workItemId
        );
        receipt(
            workspaceId, spaceId, actorId, "save_estimate",
            requestId, requestHash, result
        );
        return result;
    }

    private Estimate estimate(ResultSet rs, int row) throws SQLException {
        return new Estimate(
            rs.getObject("id", UUID.class),
            rs.getObject("work_item_id", UUID.class),
            rs.getString("estimate_unit"),
            rs.getBigDecimal("estimate_amount"),
            rs.getLong("source_work_item_version"),
            rs.getLong("aggregate_version"),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private void receipt(
        UUID workspaceId, UUID spaceId, UUID actorId,
        String operation, String requestId, String requestHash, Object response
    ) {
        jdbc.update(
            """
                insert into project_resource_commands(
                    id, workspace_id, space_id, actor_id, operation,
                    request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, operation,
            requestId, requestHash, json(response)
        );
    }

    private List<Integer> integers(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read work days", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize resource planning", exception);
        }
    }
}
