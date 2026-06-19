package com.colla.platform.modules.notification.api;

import com.colla.platform.modules.notification.application.NotificationService;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationBatchResult;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.notification.domain.NotificationModels.UnreadCount;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationItem> list(
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String targetType,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return notificationService.list(currentUser(authentication), unreadOnly, source, status, targetType, limit);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(Authentication authentication) {
        return notificationService.unreadCount(currentUser(authentication));
    }

    @PostMapping("/{notificationId}/read")
    public void markRead(@PathVariable UUID notificationId, Authentication authentication) {
        notificationService.markRead(currentUser(authentication), notificationId);
    }

    @PostMapping("/read-batch")
    public NotificationBatchResult markReadBatch(@RequestBody NotificationBatchRequest request, Authentication authentication) {
        return notificationService.markReadBatch(currentUser(authentication), request.notificationIds());
    }

    @PostMapping("/read-all")
    public void markAllRead(Authentication authentication) {
        notificationService.markAllRead(currentUser(authentication));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record NotificationBatchRequest(List<UUID> notificationIds) {
    }
}
