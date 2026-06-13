package com.colla.platform.modules.notification.infrastructure;

import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    List<NotificationItem> list(UUID workspaceId, UUID recipientId, boolean unreadOnly, String source, String status, String targetType, int limit);

    long unreadCount(UUID workspaceId, UUID recipientId);

    Optional<NotificationItem> create(
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
    );

    boolean markRead(UUID workspaceId, UUID recipientId, UUID notificationId);

    int markAllRead(UUID workspaceId, UUID recipientId);
}
