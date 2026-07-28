package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ResourceWorklogModels.MutateWorklogCommand;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.Worklog;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.WorklogRevision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class JdbcResourceWorklogRepository implements ResourceWorklogRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcResourceWorklogRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Worklog> list(
        UUID workspaceId, UUID spaceId, int limit, int revisionLimit
    ) {
        return jdbc.query(
            """
                select id, work_item_id, user_id, work_date, duration_minutes,
                       source, approval_state, current_revision, aggregate_version,
                       updated_by, updated_at
                  from project_resource_worklogs
                 where workspace_id=? and space_id=?
                 order by work_date desc, updated_at desc, id
                 limit ?
                """,
            (rs, row) -> withRevisions(
                workspaceId, spaceId, worklog(rs), revisionLimit
            ),
            workspaceId, spaceId, limit
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
                      from project_resource_worklog_commands
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
    public Worklog mutate(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        MutateWorklogCommand command,
        UUID effectiveUserId,
        String requestHash
    ) {
        UUID id = command.worklogId() == null ? UUID.randomUUID() : command.worklogId();
        String state;
        LocalDate date;
        int duration;
        String source;
        long revision;
        if ("create".equals(command.operation())) {
            state = "draft";
            date = command.workDate();
            duration = command.durationMinutes();
            source = command.source();
            revision = 1;
            int changed = jdbc.update(
                """
                    insert into project_resource_worklogs(
                        id, workspace_id, space_id, work_item_id, user_id,
                        work_date, duration_minutes, source, approval_state,
                        current_revision, aggregate_version, updated_by, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, 'draft', 1, 1, ?, now())
                    on conflict (id) do nothing
                    """,
                id, workspaceId, spaceId, command.workItemId(), effectiveUserId,
                date, duration, source, actorId
            );
            if (changed != 1) conflict();
        } else {
            Worklog current = findForUpdate(workspaceId, spaceId, id);
            state = nextState(current.approvalState(), command.operation());
            date = "update".equals(command.operation())
                ? command.workDate() : current.workDate();
            duration = "update".equals(command.operation())
                ? command.durationMinutes() : current.durationMinutes();
            source = "update".equals(command.operation())
                ? command.source() : current.source();
            effectiveUserId = current.userId();
            revision = current.currentRevision() + 1;
            int changed = jdbc.update(
                """
                    update project_resource_worklogs
                       set work_date=?, duration_minutes=?, source=?, approval_state=?,
                           current_revision=?, aggregate_version=aggregate_version+1,
                           updated_by=?, updated_at=now()
                     where workspace_id=? and space_id=? and id=? and aggregate_version=?
                    """,
                date, duration, source, state, revision, actorId,
                workspaceId, spaceId, id, command.expectedVersion()
            );
            if (changed != 1) conflict();
        }
        jdbc.update(
            """
                insert into project_resource_worklog_revisions(
                    id, workspace_id, space_id, worklog_id, revision_number,
                    work_date, duration_minutes, source, approval_state,
                    reason, actor_id, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, id, revision,
            date, duration, source, state, command.reason(), actorId
        );
        Worklog result = find(workspaceId, spaceId, id);
        jdbc.update(
            """
                insert into project_resource_worklog_commands(
                    id, workspace_id, space_id, actor_id, worklog_id,
                    operation, request_id, request_hash, response_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, actorId, id,
            command.operation(), command.requestId(), requestHash, json(result)
        );
        return result;
    }

    private Worklog findForUpdate(UUID workspaceId, UUID spaceId, UUID id) {
        try {
            return jdbc.queryForObject(
                """
                    select id, work_item_id, user_id, work_date, duration_minutes,
                           source, approval_state, current_revision, aggregate_version,
                           updated_by, updated_at
                      from project_resource_worklogs
                     where workspace_id=? and space_id=? and id=?
                     for update
                    """,
                (rs, row) -> worklog(rs), workspaceId, spaceId, id
            );
        } catch (EmptyResultDataAccessException exception) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Worklog is not available");
        }
    }

    private Worklog find(UUID workspaceId, UUID spaceId, UUID id) {
        Worklog base = jdbc.queryForObject(
            """
                select id, work_item_id, user_id, work_date, duration_minutes,
                       source, approval_state, current_revision, aggregate_version,
                       updated_by, updated_at
                  from project_resource_worklogs
                 where workspace_id=? and space_id=? and id=?
                """,
            (rs, row) -> worklog(rs), workspaceId, spaceId, id
        );
        return withRevisions(workspaceId, spaceId, base, 100);
    }

    private Worklog withRevisions(
        UUID workspaceId, UUID spaceId, Worklog base, int limit
    ) {
        List<WorklogRevision> revisions = jdbc.query(
            """
                select id, revision_number, work_date, duration_minutes,
                       source, approval_state, reason, actor_id, created_at
                  from project_resource_worklog_revisions
                 where workspace_id=? and space_id=? and worklog_id=?
                 order by revision_number desc
                 limit ?
                """,
            (rs, row) -> new WorklogRevision(
                rs.getObject("id", UUID.class),
                rs.getLong("revision_number"),
                rs.getObject("work_date", LocalDate.class),
                rs.getInt("duration_minutes"),
                rs.getString("source"),
                rs.getString("approval_state"),
                rs.getString("reason"),
                rs.getObject("actor_id", UUID.class),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId, spaceId, base.id(), limit
        );
        return new Worklog(
            base.id(), base.workItemId(), base.userId(), base.workDate(),
            base.durationMinutes(), base.source(), base.approvalState(),
            base.currentRevision(), base.version(), base.updatedBy(),
            base.updatedAt(), revisions
        );
    }

    private Worklog worklog(ResultSet rs) throws SQLException {
        return new Worklog(
            rs.getObject("id", UUID.class),
            rs.getObject("work_item_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("work_date", LocalDate.class),
            rs.getInt("duration_minutes"),
            rs.getString("source"),
            rs.getString("approval_state"),
            rs.getLong("current_revision"),
            rs.getLong("aggregate_version"),
            rs.getObject("updated_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant(),
            List.of()
        );
    }

    private String nextState(String current, String operation) {
        return switch (operation) {
            case "update" -> {
                if (!"draft".equals(current)) invalidTransition();
                yield current;
            }
            case "submit" -> {
                if (!"draft".equals(current)) invalidTransition();
                yield "submitted";
            }
            case "withdraw" -> {
                if (!"submitted".equals(current)) invalidTransition();
                yield "draft";
            }
            case "void" -> {
                if ("void".equals(current)) invalidTransition();
                yield "void";
            }
            default -> throw failure("RESOURCE_WORKLOG_INVALID", "Worklog operation is invalid");
        };
    }

    private void invalidTransition() {
        throw failure("RESOURCE_WORKLOG_TRANSITION_INVALID", "Worklog transition is invalid");
    }

    private void conflict() {
        throw failure("RESOURCE_WORKLOG_VERSION_CONFLICT", "Worklog changed concurrently");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize resource worklog", exception);
        }
    }
}
