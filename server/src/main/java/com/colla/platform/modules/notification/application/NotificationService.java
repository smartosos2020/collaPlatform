package com.colla.platform.modules.notification.application;

import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.notification.domain.NotificationModels.UnreadCount;
import com.colla.platform.modules.notification.infrastructure.NotificationRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<NotificationItem> list(CurrentUser currentUser, boolean unreadOnly, String source, String status, String targetType, int limit) {
        return notificationRepository.list(currentUser.workspaceId(), currentUser.id(), unreadOnly, source, status, targetType, limit);
    }

    public UnreadCount unreadCount(CurrentUser currentUser) {
        return new UnreadCount(notificationRepository.unreadCount(currentUser.workspaceId(), currentUser.id()));
    }

    public void markRead(CurrentUser currentUser, UUID notificationId) {
        notificationRepository.markRead(currentUser.workspaceId(), currentUser.id(), notificationId);
    }

    public void markAllRead(CurrentUser currentUser) {
        notificationRepository.markAllRead(currentUser.workspaceId(), currentUser.id());
    }
}
