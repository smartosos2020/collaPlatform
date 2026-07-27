package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ActivityItem;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.NudgeReceipt;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderPreference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPersonalCollaborationRepository implements PersonalCollaborationRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonalCollaborationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ActivityItem> listActivities(
        UUID workspaceId,
        Set<UUID> visibleWorkItemIds,
        Long beforeSequence,
        int limit
    ) {
        if (visibleWorkItemIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", visibleWorkItemIds.stream().map(ignored -> "?").toList());
        String before = beforeSequence == null ? "" : " and a.occurred_at < ?";
        String sql = """
            select a.work_item_id, a.space_id, wi.display_key, wi.title, a.activity_type,
                   wi.version source_version, a.occurred_at
              from project_work_item_activities a
              join project_work_items wi
                on wi.workspace_id=a.workspace_id and wi.space_id=a.space_id
               and wi.id=a.work_item_id
             where a.workspace_id=? and a.work_item_id in (%s)
               and wi.status='active'%s
             order by a.occurred_at desc, a.id desc
             limit ?
            """.formatted(placeholders, before);
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        args.addAll(visibleWorkItemIds);
        if (beforeSequence != null) {
            args.add(Timestamp.from(Instant.ofEpochMilli(beforeSequence)));
        }
        args.add(limit);
        return jdbcTemplate.query(sql, this::mapActivity, args.toArray());
    }

    @Override
    public long readThroughSequence(UUID workspaceId, UUID userId) {
        Long value = jdbcTemplate.queryForObject(
            """
                select coalesce((
                    select read_through_sequence
                      from project_personal_activity_read_states
                     where workspace_id=? and user_id=?
                ), 0)
                """,
            Long.class,
            workspaceId,
            userId
        );
        return value == null ? 0 : value;
    }

    @Override
    public void markRead(UUID workspaceId, UUID userId, long throughSequence, Instant updatedAt) {
        jdbcTemplate.update(
            """
                insert into project_personal_activity_read_states (
                    workspace_id, user_id, read_through_sequence, updated_at
                ) values (?, ?, ?, ?)
                on conflict (workspace_id, user_id)
                do update set read_through_sequence=greatest(
                                  project_personal_activity_read_states.read_through_sequence,
                                  excluded.read_through_sequence
                              ),
                              updated_at=excluded.updated_at
                """,
            workspaceId,
            userId,
            throughSequence,
            Timestamp.from(updatedAt)
        );
    }

    @Override
    public ReminderPreference preference(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select timezone, approaching_minutes, enabled, updated_at
                  from project_reminder_preferences
                 where workspace_id=? and user_id=?
                """,
            this::mapPreference,
            workspaceId,
            userId
        ).stream().findFirst().orElseGet(() ->
            new ReminderPreference("UTC", 1440, true, Instant.EPOCH)
        );
    }

    @Override
    public ReminderPreference updatePreference(
        UUID workspaceId,
        UUID userId,
        String timezone,
        int approachingMinutes,
        boolean enabled,
        Instant updatedAt
    ) {
        jdbcTemplate.update(
            """
                insert into project_reminder_preferences (
                    workspace_id, user_id, timezone, approaching_minutes, enabled, updated_at
                ) values (?, ?, ?, ?, ?, ?)
                on conflict (workspace_id, user_id)
                do update set timezone=excluded.timezone,
                              approaching_minutes=excluded.approaching_minutes,
                              enabled=excluded.enabled,
                              updated_at=excluded.updated_at
                """,
            workspaceId,
            userId,
            timezone,
            approachingMinutes,
            enabled,
            Timestamp.from(updatedAt)
        );
        return new ReminderPreference(timezone, approachingMinutes, enabled, updatedAt);
    }

    @Override
    public Optional<NudgeCommand> findNudge(UUID workspaceId, UUID senderId, String requestId) {
        return jdbcTemplate.query(
            """
                select id, work_item_id, recipient_id, status, created_at, request_hash
                  from project_nudge_receipts
                 where workspace_id=? and sender_id=? and request_id=?
                """,
            (row, index) -> new NudgeCommand(
                new NudgeReceipt(
                    row.getObject("id", UUID.class),
                    row.getObject("work_item_id", UUID.class),
                    row.getObject("recipient_id", UUID.class),
                    row.getString("status"),
                    row.getTimestamp("created_at").toInstant(),
                    true
                ),
                row.getString("request_hash")
            ),
            workspaceId,
            senderId,
            requestId
        ).stream().findFirst();
    }

    @Override
    public boolean createNudge(
        UUID receiptId,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID senderId,
        UUID recipientId,
        String requestId,
        String requestHash,
        Instant createdAt
    ) {
        try {
            return jdbcTemplate.update(
                """
                    insert into project_nudge_receipts (
                        id, workspace_id, space_id, work_item_id, sender_id, recipient_id,
                        request_id, request_hash, status, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, 'accepted', ?)
                    """,
                receiptId,
                workspaceId,
                spaceId,
                workItemId,
                senderId,
                recipientId,
                requestId,
                requestHash,
                Timestamp.from(createdAt)
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Set<UUID> nudgeRecipients(UUID workspaceId, UUID spaceId, UUID workItemId) {
        List<UUID> values = jdbcTemplate.queryForList(
            """
                select distinct candidate.user_id
                  from (
                        select participant.user_id
                          from project_work_item_participants participant
                         where participant.workspace_id=? and participant.space_id=?
                           and participant.work_item_id=?
                           and participant.participant_role in ('owner','assignee')
                        union
                        select task.assignee_id
                          from project_node_workflow_tasks task
                          join project_node_workflow_instances instance
                            on instance.workspace_id=task.workspace_id
                           and instance.space_id=task.space_id and instance.id=task.instance_id
                         where task.workspace_id=? and task.space_id=?
                           and instance.work_item_id=?
                           and task.status in ('pending','claimed')
                           and task.assignee_id is not null
                  ) candidate
                  join project_space_members member
                    on member.workspace_id=? and member.space_id=?
                   and member.user_id=candidate.user_id and member.status='active'
                """,
            UUID.class,
            workspaceId,
            spaceId,
            workItemId,
            workspaceId,
            spaceId,
            workItemId,
            workspaceId,
            spaceId
        );
        return Set.copyOf(new LinkedHashSet<>(values));
    }

    @Override
    public boolean recentlyNudged(
        UUID workspaceId,
        UUID workItemId,
        UUID senderId,
        UUID recipientId,
        Instant since
    ) {
        Boolean value = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                      from project_nudge_receipts
                     where workspace_id=? and work_item_id=? and sender_id=? and recipient_id=?
                       and created_at>=?
                )
                """,
            Boolean.class,
            workspaceId,
            workItemId,
            senderId,
            recipientId,
            Timestamp.from(since)
        );
        return Boolean.TRUE.equals(value);
    }

    @Override
    public long activeProjectionRows(UUID workspaceId, UUID userId) {
        return countProjection(workspaceId, userId, false);
    }

    @Override
    public long invalidProjectionRows(UUID workspaceId, UUID userId) {
        return countProjection(workspaceId, userId, true);
    }

    @Override
    public void clearDiscardableProjection(UUID workspaceId, UUID userId) {
        jdbcTemplate.update(
            "delete from project_personal_work_projections where workspace_id=? and user_id=?",
            workspaceId,
            userId
        );
    }

    private long countProjection(UUID workspaceId, UUID userId, boolean invalid) {
        Long value = jdbcTemplate.queryForObject(
            """
                select count(*) from project_personal_work_projections
                 where workspace_id=? and user_id=? and invalidated_at is %s null
                """.formatted(invalid ? "not" : ""),
            Long.class,
            workspaceId,
            userId
        );
        return value == null ? 0 : value;
    }

    private ActivityItem mapActivity(ResultSet row, int index) throws SQLException {
        Instant occurredAt = row.getTimestamp("occurred_at").toInstant();
        UUID spaceId = row.getObject("space_id", UUID.class);
        UUID workItemId = row.getObject("work_item_id", UUID.class);
        return new ActivityItem(
            occurredAt.toEpochMilli(),
            workItemId,
            spaceId,
            row.getString("display_key"),
            row.getString("title"),
            row.getString("activity_type"),
            row.getLong("source_version"),
            occurredAt,
            "/project-spaces/" + spaceId + "/work-items/" + workItemId
        );
    }

    private ReminderPreference mapPreference(ResultSet row, int index) throws SQLException {
        return new ReminderPreference(
            row.getString("timezone"),
            row.getInt("approaching_minutes"),
            row.getBoolean("enabled"),
            row.getTimestamp("updated_at").toInstant()
        );
    }
}
