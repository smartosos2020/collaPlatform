package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.PersonalWorkModels.PersonalCandidate;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPersonalWorkRepository implements PersonalWorkRepository {
    private static final String CANDIDATE_SELECT = """
        select wi.id, wi.workspace_id, wi.space_id, wi.type_definition_id, wi.type_version_id,
               type.type_key, type.name type_name, wi.config_hash, wi.item_number, wi.display_key,
               wi.title, wi.field_values, wi.status, wi.version, wi.created_by, wi.created_at,
               wi.updated_by, wi.updated_at, wi.archived_at,
               coalesce(string_agg(distinct participant.participant_role, ',')
                   filter (where participant.participant_role is not null), '') participant_roles,
               coalesce(bool_or(task.status in ('pending', 'claimed')), false) pending_node_task,
               max(task.status) filter (where task.status in ('pending', 'claimed')) node_task_state,
               coalesce(max(task.aggregate_version), 0) node_task_version,
               min(task.due_at) filter (where task.status in ('pending', 'claimed')) node_task_due_at
          from project_work_items wi
          join project_work_item_types type
            on type.workspace_id = wi.workspace_id
           and type.space_id = wi.space_id
           and type.id = wi.type_definition_id
          join project_spaces space
            on space.workspace_id = wi.workspace_id and space.id = wi.space_id
          join project_space_members member
            on member.workspace_id = wi.workspace_id
           and member.space_id = wi.space_id
           and member.user_id = ?
           and member.status = 'active'
          left join project_work_item_participants participant
            on participant.workspace_id = wi.workspace_id
           and participant.space_id = wi.space_id
           and participant.work_item_id = wi.id
           and participant.user_id = ?
          left join project_node_workflow_instances instance
            on instance.workspace_id = wi.workspace_id
           and instance.space_id = wi.space_id
           and instance.work_item_id = wi.id
          left join project_node_workflow_tasks task
            on task.workspace_id = instance.workspace_id
           and task.space_id = instance.space_id
           and task.instance_id = instance.id
           and task.assignee_id = ?
         where wi.workspace_id = ?
           and wi.status = 'active'
           and space.status <> 'archived'
           and (
                participant.user_id is not null
                or task.assignee_id = ?
           )
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcPersonalWorkRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PersonalCandidate> listCandidates(
        UUID workspaceId,
        UUID userId,
        Instant beforeUpdatedAt,
        UUID beforeWorkItemId,
        int limit
    ) {
        String cursor = beforeUpdatedAt == null ? "" : """
             and (wi.updated_at, wi.id) < (?, ?)
            """;
        String suffix = """
             group by wi.id, type.type_key, type.name
             order by wi.updated_at desc, wi.id desc
             limit ?
            """;
        if (beforeUpdatedAt == null) {
            return jdbcTemplate.query(
                CANDIDATE_SELECT + suffix,
                this::mapCandidate,
                userId,
                userId,
                userId,
                workspaceId,
                userId,
                limit
            );
        }
        return jdbcTemplate.query(
            CANDIDATE_SELECT + cursor + suffix,
            this::mapCandidate,
            userId,
            userId,
            userId,
            workspaceId,
            userId,
            Timestamp.from(beforeUpdatedAt),
            beforeWorkItemId,
            limit
        );
    }

    @Override
    @Transactional
    public void synchronizeProjection(
        UUID workspaceId,
        UUID userId,
        List<PersonalWorkItem> visibleItems,
        Instant refreshedAt
    ) {
        for (PersonalWorkItem item : visibleItems) {
            for (var reason : item.reasons()) {
                jdbcTemplate.update(
                    """
                        insert into project_personal_work_projections (
                            workspace_id, user_id, space_id, work_item_id, bucket_key, source_key,
                            source_version, source_updated_at, invalidated_at, refreshed_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, null, ?)
                        on conflict (workspace_id, user_id, work_item_id, bucket_key, source_key)
                        do update set source_version = excluded.source_version,
                                      source_updated_at = excluded.source_updated_at,
                                      invalidated_at = null,
                                      refreshed_at = excluded.refreshed_at
                        """,
                    workspaceId,
                    userId,
                    item.spaceId(),
                    item.workItemId(),
                    reason.bucket().name(),
                    reason.source(),
                    reason.sourceVersion(),
                    Timestamp.from(item.updatedAt()),
                    Timestamp.from(refreshedAt)
                );
            }
        }
    }

    @Override
    public void markInvalidated(
        UUID workspaceId,
        UUID userId,
        String sourceKey,
        long sourceVersion,
        Instant invalidatedAt
    ) {
        jdbcTemplate.update(
            """
                insert into project_personal_work_invalidation_watermarks (
                    workspace_id, user_id, source_key, source_version, invalidated_at
                ) values (?, ?, ?, ?, ?)
                on conflict (workspace_id, user_id, source_key)
                do update set source_version = greatest(
                                  project_personal_work_invalidation_watermarks.source_version,
                                  excluded.source_version
                              ),
                              invalidated_at = greatest(
                                  project_personal_work_invalidation_watermarks.invalidated_at,
                                  excluded.invalidated_at
                              )
                """,
            workspaceId,
            userId,
            sourceKey,
            sourceVersion,
            Timestamp.from(invalidatedAt)
        );
        jdbcTemplate.update(
            """
                update project_personal_work_projections
                   set invalidated_at = ?
                 where workspace_id = ? and user_id = ? and source_key = ?
                   and source_version <= ?
                """,
            Timestamp.from(invalidatedAt),
            workspaceId,
            userId,
            sourceKey,
            sourceVersion
        );
    }

    @Override
    @Transactional
    public void invalidateKnownUsers(
        UUID workspaceId,
        UUID workItemId,
        String sourceKey,
        long sourceVersion,
        Instant invalidatedAt
    ) {
        jdbcTemplate.update(
            """
                insert into project_personal_work_invalidation_watermarks (
                    workspace_id, user_id, source_key, source_version, invalidated_at
                )
                select distinct workspace_id, user_id, ?::varchar, ?::bigint, ?::timestamptz
                  from project_personal_work_projections
                 where workspace_id = ? and work_item_id = ?
                on conflict (workspace_id, user_id, source_key)
                do update set source_version = greatest(
                                  project_personal_work_invalidation_watermarks.source_version,
                                  excluded.source_version
                              ),
                              invalidated_at = greatest(
                                  project_personal_work_invalidation_watermarks.invalidated_at,
                                  excluded.invalidated_at
                              )
                """,
            sourceKey,
            sourceVersion,
            Timestamp.from(invalidatedAt),
            workspaceId,
            workItemId
        );
        jdbcTemplate.update(
            """
                update project_personal_work_projections
                   set invalidated_at = ?
                 where workspace_id = ? and work_item_id = ?
                   and source_version <= ?
                """,
            Timestamp.from(invalidatedAt),
            workspaceId,
            workItemId,
            sourceVersion
        );
    }

    private PersonalCandidate mapCandidate(ResultSet row, int rowNumber) throws SQLException {
        String roles = row.getString("participant_roles");
        LinkedHashSet<String> participantRoles = new LinkedHashSet<>();
        if (roles != null && !roles.isBlank()) {
            participantRoles.addAll(Arrays.asList(roles.split(",")));
        }
        Timestamp dueAt = row.getTimestamp("node_task_due_at");
        return new PersonalCandidate(
            mapItem(row),
            participantRoles,
            row.getBoolean("pending_node_task"),
            row.getString("node_task_state"),
            row.getLong("node_task_version"),
            dueAt == null ? null : dueAt.toInstant()
        );
    }

    private WorkItem mapItem(ResultSet row) throws SQLException {
        try {
            Timestamp archivedAt = row.getTimestamp("archived_at");
            return new WorkItem(
                row.getObject("id", UUID.class),
                row.getObject("workspace_id", UUID.class),
                row.getObject("space_id", UUID.class),
                row.getObject("type_definition_id", UUID.class),
                row.getObject("type_version_id", UUID.class),
                row.getString("type_key"),
                row.getString("type_name"),
                row.getString("config_hash"),
                row.getLong("item_number"),
                row.getString("display_key"),
                row.getString("title"),
                objectMapper.readTree(row.getString("field_values")),
                row.getString("status"),
                row.getLong("version"),
                row.getObject("created_by", UUID.class),
                row.getTimestamp("created_at").toInstant(),
                row.getObject("updated_by", UUID.class),
                row.getTimestamp("updated_at").toInstant(),
                archivedAt == null ? null : archivedAt.toInstant()
            );
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid WorkItem field values", exception);
        }
    }
}
