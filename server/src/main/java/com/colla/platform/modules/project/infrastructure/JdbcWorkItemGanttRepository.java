package com.colla.platform.modules.project.infrastructure;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateBinding;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttPreference;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.ScheduleIndexEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkItemGanttRepository implements WorkItemGanttRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemGanttRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<GanttPreference> findPreference(
        UUID workspaceId, UUID spaceId, UUID userId, String viewKey
    ) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select view_key, binding_json, timezone, zoom,
                           hierarchy_relation_key, expanded_node_ids,
                           aggregate_version, updated_at
                      from project_work_item_gantt_preferences
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                    """,
                (resultSet, rowNumber) -> new GanttPreference(
                    resultSet.getString("view_key"),
                    read(resultSet.getString("binding_json"), DateBinding.class),
                    resultSet.getString("timezone"),
                    resultSet.getString("zoom"),
                    resultSet.getString("hierarchy_relation_key"),
                    read(resultSet.getString("expanded_node_ids"), new TypeReference<>() {}),
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
    public GanttPreference savePreference(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        GanttPreferenceCommand command
    ) {
        Optional<GanttPreference> current = findPreference(workspaceId, spaceId, userId, viewKey);
        if (current.isPresent() && current.get().version() != command.expectedVersion()
            || current.isEmpty() && command.expectedVersion() != 0) {
            throw failure("GANTT_PREFERENCE_VERSION_CONFLICT", "Gantt preference changed");
        }
        int changed = current.isEmpty()
            ? jdbcTemplate.update(
                """
                    insert into project_work_item_gantt_preferences (
                        workspace_id, space_id, user_id, view_key, schema_version,
                        binding_json, timezone, zoom, hierarchy_relation_key,
                        expanded_node_ids, aggregate_version, created_at, updated_at
                    ) values (?, ?, ?, ?, 1, ?::jsonb, ?, ?, ?, ?::jsonb, 1, now(), now())
                    on conflict do nothing
                    """,
                workspaceId, spaceId, userId, viewKey, json(command.binding()),
                command.timezone(), command.zoom(), command.hierarchyRelationKey(),
                json(command.expandedNodeIds())
            )
            : jdbcTemplate.update(
                """
                    update project_work_item_gantt_preferences
                       set binding_json=?::jsonb, timezone=?, zoom=?,
                           hierarchy_relation_key=?, expanded_node_ids=?::jsonb,
                           aggregate_version=aggregate_version+1, updated_at=now()
                     where workspace_id=? and space_id=? and user_id=? and view_key=?
                       and aggregate_version=?
                    """,
                json(command.binding()), command.timezone(), command.zoom(),
                command.hierarchyRelationKey(), json(command.expandedNodeIds()),
                workspaceId, spaceId, userId, viewKey, command.expectedVersion()
            );
        if (changed != 1) {
            throw failure("GANTT_PREFERENCE_VERSION_CONFLICT", "Gantt preference changed");
        }
        return findPreference(workspaceId, spaceId, userId, viewKey).orElseThrow();
    }

    @Override
    @Transactional
    public void replaceScheduleIndex(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        List<ScheduleIndexEntry> entries
    ) {
        jdbcTemplate.update(
            """
                delete from project_work_item_gantt_schedule_index
                 where workspace_id=? and space_id=? and user_id=? and view_key=?
                """,
            workspaceId, spaceId, userId, viewKey
        );
        for (ScheduleIndexEntry entry : entries) {
            jdbcTemplate.update(
                """
                    insert into project_work_item_gantt_schedule_index (
                        workspace_id, space_id, user_id, view_key, work_item_id,
                        source_work_item_version, start_date, end_date,
                        parent_work_item_id, depth, rebuilt_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    on conflict (workspace_id, space_id, user_id, view_key, work_item_id)
                    do update set
                        source_work_item_version=excluded.source_work_item_version,
                        start_date=excluded.start_date,
                        end_date=excluded.end_date,
                        parent_work_item_id=excluded.parent_work_item_id,
                        depth=excluded.depth,
                        rebuilt_at=excluded.rebuilt_at
                    """,
                workspaceId, spaceId, userId, viewKey, entry.workItemId(),
                entry.sourceWorkItemVersion(),
                entry.startDate() == null ? null : Date.valueOf(entry.startDate()),
                entry.endDate() == null ? null : Date.valueOf(entry.endDate()),
                entry.parentWorkItemId(), entry.depth()
            );
        }
    }

    @Override
    public void recordRender(
        UUID workspaceId,
        UUID spaceId,
        String viewKey,
        int rowCount,
        int dependencyCount,
        int maxDepth
    ) {
        jdbcTemplate.update(
            """
                insert into project_work_item_gantt_projection_stats (
                    workspace_id, space_id, view_key, render_count,
                    last_row_count, last_dependency_count, last_max_depth, updated_at
                ) values (?, ?, ?, 1, ?, ?, ?, now())
                on conflict (workspace_id, space_id, view_key) do update
                    set render_count=project_work_item_gantt_projection_stats.render_count+1,
                        last_row_count=excluded.last_row_count,
                        last_dependency_count=excluded.last_dependency_count,
                        last_max_depth=excluded.last_max_depth,
                        updated_at=now()
                """,
            workspaceId, spaceId, viewKey, rowCount, dependencyCount, maxDepth
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize gantt data", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored gantt data is invalid", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored gantt data is invalid", exception);
        }
    }
}
