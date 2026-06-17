package com.colla.platform.modules.notification.application;

import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.notification.domain.NotificationModels.UnreadCount;
import com.colla.platform.modules.notification.infrastructure.NotificationRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.websocket.WebSocketMessageSender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final WebSocketMessageSender webSocketMessageSender;

    public NotificationService(NotificationRepository notificationRepository, WebSocketMessageSender webSocketMessageSender) {
        this.notificationRepository = notificationRepository;
        this.webSocketMessageSender = webSocketMessageSender;
    }

    public List<NotificationItem> list(CurrentUser currentUser, boolean unreadOnly, String source, String status, String targetType, int limit) {
        return notificationRepository.list(currentUser.workspaceId(), currentUser.id(), unreadOnly, source, status, targetType, limit);
    }

    public UnreadCount unreadCount(CurrentUser currentUser) {
        return new UnreadCount(notificationRepository.unreadCount(currentUser.workspaceId(), currentUser.id()));
    }

    public void markRead(CurrentUser currentUser, UUID notificationId) {
        if (notificationRepository.markRead(currentUser.workspaceId(), currentUser.id(), notificationId)) {
            pushUnreadChanged(currentUser.workspaceId(), currentUser.id());
            webSocketMessageSender.sendToUser(
                currentUser.id(),
                "notification.read",
                currentUser.workspaceId(),
                "notification",
                notificationId,
                Map.of("notificationId", notificationId)
            );
        }
    }

    public void markAllRead(CurrentUser currentUser) {
        int changed = notificationRepository.markAllRead(currentUser.workspaceId(), currentUser.id());
        if (changed > 0) {
            pushUnreadChanged(currentUser.workspaceId(), currentUser.id());
        }
    }

    public void pushCreated(UUID workspaceId, UUID recipientId, NotificationItem notification) {
        webSocketMessageSender.sendToUser(
            recipientId,
            "notification.created",
            workspaceId,
            "notification",
            notification.id(),
            Map.of("notification", notification)
        );
        pushUnreadChanged(workspaceId, recipientId);
    }

    private void pushUnreadChanged(UUID workspaceId, UUID recipientId) {
        long unreadCount = notificationRepository.unreadCount(workspaceId, recipientId);
        webSocketMessageSender.sendToUser(
            recipientId,
            "notification.unread.changed",
            workspaceId,
            "notification",
            recipientId,
            Map.of("count", unreadCount)
        );
    }
}
