package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineDependency;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineEntry;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSnapshot;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSummary;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkItemScheduleRepository implements WorkItemScheduleRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemScheduleRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BaselineSummary> listBaselines(
        UUID workspaceId, UUID spaceId, UUID userId, int limit
    ) {
        return jdbcTemplate.query(
            """
                select id, name, query_hash, window_start, window_end,
                       aggregate_version, status, created_at, expires_at
                  from project_work_item_schedule_baselines
                 where workspace_id=? and space_id=? and user_id=?
                   and status='active' and expires_at > now()
                 order by created_at desc, id
                 limit ?
                """,
            (rs, row) -> summary(rs),
            workspaceId, spaceId, userId, limit
        );
    }

    @Override
    public Optional<BaselineSnapshot> findBaseline(
        UUID workspaceId, UUID spaceId, UUID userId, UUID baselineId
    ) {
        try {
            BaselineSummary baseline = jdbcTemplate.queryForObject(
                """
                    select id, name, query_hash, window_start, window_end,
                           aggregate_version, status, created_at, expires_at
                      from project_work_item_schedule_baselines
                     where workspace_id=? and space_id=? and user_id=? and id=?
                       and status='active' and expires_at > now()
                    """,
                (rs, row) -> summary(rs),
                workspaceId, spaceId, userId, baselineId
            );
            List<BaselineEntry> entries = jdbcTemplate.query(
                """
                    select work_item_id, work_item_version, start_date, end_date,
                           parent_work_item_id, depth
                      from project_work_item_schedule_baseline_entries
                     where workspace_id=? and space_id=? and baseline_id=?
                     order by work_item_id
                    """,
                (rs, row) -> new BaselineEntry(
                    rs.getObject("work_item_id", UUID.class),
                    rs.getLong("work_item_version"),
                    date(rs.getDate("start_date")),
                    date(rs.getDate("end_date")),
                    rs.getObject("parent_work_item_id", UUID.class),
                    rs.getInt("depth")
                ),
                workspaceId, spaceId, baselineId
            );
            List<BaselineDependency> dependencies = jdbcTemplate.query(
                """
                    select relation_id, relation_version,
                           source_work_item_id, target_work_item_id
                      from project_work_item_schedule_baseline_dependencies
                     where workspace_id=? and space_id=? and baseline_id=?
                     order by relation_id
                    """,
                (rs, row) -> new BaselineDependency(
                    rs.getObject("relation_id", UUID.class),
                    rs.getLong("relation_version"),
                    rs.getObject("source_work_item_id", UUID.class),
                    rs.getObject("target_work_item_id", UUID.class)
                ),
                workspaceId, spaceId, baselineId
            );
            return Optional.of(new BaselineSnapshot(baseline, entries, dependencies));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID userId, String operation, String requestId
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select request_hash, response_json
                      from project_work_item_schedule_baseline_commands
                     where workspace_id=? and space_id=? and user_id=?
                       and operation=? and request_id=?
                    """,
                (rs, row) -> new CommandRecord(
                    rs.getString("request_hash"), rs.getString("response_json")
                ),
                workspaceId, spaceId, userId, operation, requestId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public BaselineSnapshot createBaseline(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String requestId,
        String requestHash,
        String responseJson,
        String name,
        String queryHash,
        String bindingJson,
        LocalDate windowStart,
        LocalDate windowEnd,
        Instant expiresAt,
        List<BaselineEntry> entries,
        List<BaselineDependency> dependencies
    ) {
        UUID baselineId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_work_item_schedule_baselines(
                    id, workspace_id, space_id, user_id, name, query_hash,
                    binding_json, window_start, window_end, aggregate_version,
                    status, created_at, expires_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 1, 'active', now(), ?)
                """,
            baselineId, workspaceId, spaceId, userId, name, queryHash, bindingJson,
            Date.valueOf(windowStart), Date.valueOf(windowEnd),
            java.sql.Timestamp.from(expiresAt)
        );
        for (BaselineEntry entry : entries) {
            jdbcTemplate.update(
                """
                    insert into project_work_item_schedule_baseline_entries(
                        workspace_id, space_id, baseline_id, work_item_id,
                        work_item_version, start_date, end_date, parent_work_item_id, depth
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                workspaceId, spaceId, baselineId, entry.workItemId(),
                entry.workItemVersion(), sqlDate(entry.startDate()), sqlDate(entry.endDate()),
                entry.parentWorkItemId(), entry.depth()
            );
        }
        for (BaselineDependency dependency : dependencies) {
            jdbcTemplate.update(
                """
                    insert into project_work_item_schedule_baseline_dependencies(
                        workspace_id, space_id, baseline_id, relation_id, relation_version,
                        source_work_item_id, target_work_item_id
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """,
                workspaceId, spaceId, baselineId, dependency.relationId(),
                dependency.relationVersion(), dependency.sourceWorkItemId(),
                dependency.targetWorkItemId()
            );
        }
        BaselineSnapshot result = findBaseline(
            workspaceId, spaceId, userId, baselineId
        ).orElseThrow();
        String actualResponse = json(result);
        jdbcTemplate.update(
            """
                insert into project_work_item_schedule_baseline_commands(
                    id, workspace_id, space_id, user_id, operation, request_id,
                    request_hash, baseline_id, response_json, created_at
                ) values (?, ?, ?, ?, 'create', ?, ?, ?, ?::jsonb, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, userId, requestId,
            requestHash, baselineId, actualResponse
        );
        return result;
    }

    @Override
    @Transactional
    public BaselineSummary deleteBaseline(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        UUID baselineId,
        String requestId,
        String requestHash,
        long expectedVersion,
        String responseJson
    ) {
        int changed = jdbcTemplate.update(
            """
                update project_work_item_schedule_baselines
                   set status='deleted', deleted_at=now(),
                       aggregate_version=aggregate_version+1
                 where workspace_id=? and space_id=? and user_id=? and id=?
                   and status='active' and aggregate_version=?
                """,
            workspaceId, spaceId, userId, baselineId, expectedVersion
        );
        if (changed != 1) {
            throw failure("SCHEDULE_BASELINE_VERSION_CONFLICT", "Baseline changed; refresh and retry");
        }
        BaselineSummary result = jdbcTemplate.queryForObject(
            """
                select id, name, query_hash, window_start, window_end,
                       aggregate_version, status, created_at, expires_at
                  from project_work_item_schedule_baselines
                 where workspace_id=? and space_id=? and user_id=? and id=?
                """,
            (rs, row) -> summary(rs),
            workspaceId, spaceId, userId, baselineId
        );
        jdbcTemplate.update(
            """
                insert into project_work_item_schedule_baseline_commands(
                    id, workspace_id, space_id, user_id, operation, request_id,
                    request_hash, baseline_id, response_json, created_at
                ) values (?, ?, ?, ?, 'delete', ?, ?, ?, ?::jsonb, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, userId, requestId,
            requestHash, baselineId, json(result)
        );
        return result;
    }

    @Override
    public List<TimelineEvent> timeline(
        UUID workspaceId, UUID spaceId, List<UUID> visibleWorkItemIds, int limit
    ) {
        if (visibleWorkItemIds.isEmpty()) return List.of();
        String ids = String.join(",", Collections.nCopies(visibleWorkItemIds.size(), "?"));
        List<Object> values = new ArrayList<>();
        values.add(workspaceId);
        values.add(spaceId);
        values.addAll(visibleWorkItemIds);
        values.add(workspaceId);
        values.add(spaceId);
        values.addAll(visibleWorkItemIds);
        values.add(workspaceId);
        values.add(spaceId);
        values.addAll(visibleWorkItemIds);
        values.addAll(visibleWorkItemIds);
        values.add(limit);
        return jdbcTemplate.query(
            """
                select id, source_kind, source_id, work_item_id, event_type, actor_id, occurred_at
                  from (
                    select a.id, 'activity' source_kind, a.id source_id, a.work_item_id,
                           a.activity_type event_type, a.actor_id, a.occurred_at
                      from project_work_item_activities a
                     where a.workspace_id=? and a.space_id=? and a.work_item_id in (%s)
                    union all
                    select h.id, 'workflow', h.id, h.work_item_id,
                           'workflow.' || h.action_kind, h.actor_id, h.occurred_at
                      from project_work_item_workflow_history h
                     where h.workspace_id=? and h.space_id=? and h.work_item_id in (%s)
                    union all
                    select r.id, 'relation', r.id, r.source_work_item_id,
                           'relation.' || r.event_kind, r.occurred_by, r.occurred_at
                      from project_work_item_relation_history r
                     where r.workspace_id=? and r.space_id=?
                       and r.source_work_item_id in (%s)
                       and r.target_work_item_id in (%s)
                  ) timeline
                 order by occurred_at desc, id
                 limit ?
                """.formatted(ids, ids, ids, ids),
            (rs, row) -> new TimelineEvent(
                rs.getObject("id", UUID.class),
                rs.getString("source_kind"),
                rs.getObject("source_id", UUID.class),
                rs.getObject("work_item_id", UUID.class),
                rs.getString("event_type"),
                rs.getObject("actor_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant()
            ),
            values.toArray()
        );
    }

    @Override
    @Transactional
    public void replaceTimelineIndex(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        List<TimelineEvent> events
    ) {
        jdbcTemplate.update(
            """
                delete from project_work_item_timeline_index
                 where workspace_id=? and space_id=? and user_id=? and view_key=?
                """,
            workspaceId, spaceId, userId, viewKey
        );
        for (TimelineEvent event : events) {
            jdbcTemplate.update(
                """
                    insert into project_work_item_timeline_index(
                        workspace_id, space_id, user_id, view_key, event_id,
                        source_kind, source_id, work_item_id, event_type, occurred_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (workspace_id, space_id, user_id, view_key, event_id)
                    do update set
                        source_kind=excluded.source_kind,
                        source_id=excluded.source_id,
                        work_item_id=excluded.work_item_id,
                        event_type=excluded.event_type,
                        occurred_at=excluded.occurred_at
                    """,
                workspaceId, spaceId, userId, viewKey, event.id(), event.sourceKind(),
                event.sourceId(), event.workItemId(), event.eventType(),
                java.sql.Timestamp.from(event.occurredAt())
            );
        }
    }

    private BaselineSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BaselineSummary(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("query_hash"),
            rs.getDate("window_start").toLocalDate(),
            rs.getDate("window_end").toLocalDate(),
            rs.getLong("aggregate_version"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant()
        );
    }

    private static LocalDate date(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private static Date sqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize schedule baseline", exception);
        }
    }
}
