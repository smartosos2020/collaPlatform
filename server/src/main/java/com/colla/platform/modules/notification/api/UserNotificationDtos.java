package com.colla.platform.modules.notification.api;

import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class UserNotificationDtos {
    private UserNotificationDtos() {
    }

    static UserNotificationView notification(NotificationItem notification) {
        return new UserNotificationView(
            notification.id(),
            notification.notificationType(),
            notification.sourceType(),
            notification.notificationScope(),
            notification.title(),
            notification.body(),
            notification.targetType(),
            notification.targetId(),
            notification.webPath(),
            notification.readAt(),
            notification.createdAt(),
            new UserNotificationReminder(notification.readAt() == null, notification.webPath()),
            notification.readAt() == null ? List.of("open", "mark_read") : List.of("open")
        );
    }

    record UserNotificationView(
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
        Instant createdAt,
        UserNotificationReminder reminder,
        List<String> availableActions
    ) {
    }

    record UserNotificationReminder(boolean unread, String webPath) {
    }
}
