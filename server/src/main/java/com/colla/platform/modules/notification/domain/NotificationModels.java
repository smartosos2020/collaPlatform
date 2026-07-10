package com.colla.platform.modules.notification.domain;

import java.time.Instant;
import java.util.UUID;

public final class NotificationModels {
    private NotificationModels() {
    }

    public record NotificationItem(
        UUID id,
        String notificationType,
        String sourceType,
        String notificationScope,
        String title,
        String body,
        String targetType,
        UUID targetId,
        String webPath,
        Instant readAt,
        Instant createdAt
    ) {
    }

    public record UnreadCount(long count) {
    }

    public record NotificationBatchResult(int changed) {
    }
}
