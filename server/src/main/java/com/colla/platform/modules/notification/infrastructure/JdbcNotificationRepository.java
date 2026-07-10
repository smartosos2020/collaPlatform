package com.colla.platform.modules.notification.infrastructure;

import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<NotificationItem> list(UUID workspaceId, UUID recipientId, boolean unreadOnly, String source, String status, String targetType, int limit) {
        StringBuilder sql = new StringBuilder("""
                select id, notification_type, title, body, target_type, target_id, web_path, read_at, created_at
                from notifications
                where workspace_id = ? and recipient_id = ?
                  and notification_type not like 'admin_%'
                  and notification_type not like 'governance_%'
                """);
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        args.add(recipientId);
        if (unreadOnly || "unread".equalsIgnoreCase(status)) {
            sql.append(" and read_at is null");
        } else if ("read".equalsIgnoreCase(status)) {
            sql.append(" and read_at is not null");
        }
        if (source != null && !source.isBlank() && !"all".equalsIgnoreCase(source)) {
            sql.append(" and notification_type like ?");
            args.add(source.toLowerCase() + "%");
        }
        if (targetType != null && !targetType.isBlank() && !"all".equalsIgnoreCase(targetType)) {
            sql.append(" and target_type = ?");
            args.add(targetType);
        }
        sql.append(" order by created_at desc limit ?");
        args.add(Math.min(Math.max(limit, 1), 100));
        return jdbcTemplate.query(
            sql.toString(),
            this::mapNotification,
            args.toArray()
        );
    }

    @Override
    public long unreadCount(UUID workspaceId, UUID recipientId) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from notifications
                where workspace_id = ? and recipient_id = ? and read_at is null
                  and notification_type not like 'admin_%'
                  and notification_type not like 'governance_%'
                """,
            Long.class,
            workspaceId,
            recipientId
        );
        return count == null ? 0 : count;
    }

    @Override
    public Optional<NotificationItem> create(
        UUID workspaceId,
        UUID recipientId,
        UUID actorId,
        String notificationType,
        String title,
        String body,
        String targetType,
        UUID targetId,
        String webPath,
        String dedupeKey
    ) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                """
                    insert into notifications
                        (id, workspace_id, recipient_id, actor_id, notification_type, title, body,
                         target_type, target_id, web_path, dedupe_key, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    """,
                id,
                workspaceId,
                recipientId,
                actorId,
                notificationType,
                title,
                body,
                targetType,
                targetId,
                webPath,
                dedupeKey
            );
        } catch (DuplicateKeyException exception) {
            return Optional.empty();
        }
        return list(workspaceId, recipientId, false, null, null, null, 1).stream()
            .filter(item -> item.id().equals(id))
            .findFirst();
    }

    @Override
    public boolean markRead(UUID workspaceId, UUID recipientId, UUID notificationId) {
        return jdbcTemplate.update(
            """
                update notifications
                set read_at = coalesce(read_at, now())
                where workspace_id = ? and recipient_id = ? and id = ?
                """,
            workspaceId,
            recipientId,
            notificationId
        ) > 0;
    }

    @Override
    public int markReadBatch(UUID workspaceId, UUID recipientId, List<UUID> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", notificationIds.stream().map(ignored -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        args.add(recipientId);
        args.addAll(notificationIds);
        return jdbcTemplate.update(
            """
                update notifications
                set read_at = coalesce(read_at, now())
                where workspace_id = ? and recipient_id = ? and id in (%s) and read_at is null
                """.formatted(placeholders),
            args.toArray()
        );
    }

    @Override
    public int markAllRead(UUID workspaceId, UUID recipientId) {
        return jdbcTemplate.update(
            "update notifications set read_at = coalesce(read_at, now()) where workspace_id = ? and recipient_id = ? and read_at is null",
            workspaceId,
            recipientId
        );
    }

    private NotificationItem mapNotification(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationItem(
            rs.getObject("id", UUID.class),
            rs.getString("notification_type"),
            sourceType(rs.getString("notification_type")),
            notificationScope(rs.getString("notification_type")),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("target_type"),
            rs.getObject("target_id", UUID.class),
            rs.getString("web_path"),
            rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private String sourceType(String notificationType) {
        if (notificationType == null || notificationType.isBlank()) {
            return "system";
        }
        int separator = notificationType.indexOf('_');
        if (separator < 0) {
            separator = notificationType.indexOf('.');
        }
        return separator > 0 ? notificationType.substring(0, separator) : notificationType;
    }

    private String notificationScope(String notificationType) {
        if (notificationType == null) {
            return "user_collaboration";
        }
        return notificationType.startsWith("admin_") || notificationType.startsWith("governance_")
            ? "admin_governance"
            : "user_collaboration";
    }
}
