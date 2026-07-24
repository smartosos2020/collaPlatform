package com.colla.platform.modules.notification.application;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationBatchResult;
import com.colla.platform.modules.notification.domain.NotificationModels.UnreadCount;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationPreference;
import com.colla.platform.modules.notification.infrastructure.NotificationRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private static final List<String> SOURCES = List.of("im", "project", "knowledge", "base", "approval", "resource", "system");
    private final NotificationRepository notificationRepository;
    private final TransactionalOutbox outbox;

    public NotificationService(NotificationRepository notificationRepository, TransactionalOutbox outbox) {
        this.notificationRepository = notificationRepository;
        this.outbox = outbox;
    }

    public List<NotificationItem> list(CurrentUser currentUser, boolean unreadOnly, String source, String status, String targetType, int limit) {
        return notificationRepository.list(currentUser.workspaceId(), currentUser.id(), unreadOnly, source, status, targetType, limit);
    }

    public UnreadCount unreadCount(CurrentUser currentUser) {
        return new UnreadCount(notificationRepository.unreadCount(currentUser.workspaceId(), currentUser.id()));
    }

    @Transactional
    public void markRead(CurrentUser currentUser, UUID notificationId) {
        if (notificationRepository.markRead(currentUser.workspaceId(), currentUser.id(), notificationId)) {
            appendRealtimeFact(currentUser, "read", notificationId, "read:" + notificationId);
        }
    }

    @Transactional
    public NotificationBatchResult markReadBatch(CurrentUser currentUser, List<UUID> notificationIds) {
        int changed = notificationRepository.markReadBatch(currentUser.workspaceId(), currentUser.id(), notificationIds);
        if (changed > 0) {
            appendRealtimeFact(currentUser, "unread", null, "batch-read:" + UUID.randomUUID());
        }
        return new NotificationBatchResult(changed);
    }

    @Transactional
    public void markAllRead(CurrentUser currentUser) {
        int changed = notificationRepository.markAllRead(currentUser.workspaceId(), currentUser.id());
        if (changed > 0) {
            appendRealtimeFact(currentUser, "unread", null, "all-read:" + UUID.randomUUID());
        }
    }

    public List<NotificationPreference> preferences(CurrentUser currentUser) {
        return notificationRepository.listPreferences(currentUser.workspaceId(), currentUser.id());
    }

    public List<NotificationPreference> updatePreference(CurrentUser currentUser, String sourceType, boolean enabled) {
        String normalized = sourceType == null ? "" : sourceType.trim().toLowerCase();
        if (!SOURCES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification source");
        }
        if (!enabled && ("resource".equals(normalized) || "system".equals(normalized))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required notifications cannot be disabled");
        }
        notificationRepository.upsertPreference(currentUser.workspaceId(), currentUser.id(), normalized, enabled);
        return preferences(currentUser);
    }

    private void appendRealtimeFact(
        CurrentUser currentUser,
        String changeType,
        UUID notificationId,
        String idempotencySuffix
    ) {
        Map<String, Object> payload = notificationId == null
            ? Map.of(
                "recipientId", currentUser.id().toString(),
                "changeType", changeType
            )
            : Map.of(
                "recipientId", currentUser.id().toString(),
                "changeType", changeType,
                "notificationId", notificationId.toString()
            );
        outbox.append(
            currentUser.workspaceId(),
            "notification.realtime.changed",
            "notification_recipient",
            currentUser.id(),
            currentUser.id(),
            payload,
            "notification.realtime:" + currentUser.id() + ":" + idempotencySuffix
        );
    }
}
