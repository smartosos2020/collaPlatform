package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.notification.infrastructure.NotificationRepository;
import com.colla.platform.modules.search.application.SearchIndexService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DomainEventWorker {
    private static final String NOTIFICATION_CREATED = "notification.created";

    private final DomainEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final SearchIndexService searchIndexService;

    public DomainEventWorker(
        DomainEventRepository eventRepository,
        NotificationRepository notificationRepository,
        SearchIndexService searchIndexService
    ) {
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.searchIndexService = searchIndexService;
    }

    @Scheduled(fixedDelayString = "${colla.events.worker-delay-ms:5000}")
    public void processPendingEvents() {
        for (DomainEvent event : eventRepository.claimPending(20)) {
            try {
                process(event);
                eventRepository.markProcessed(event.id(), Instant.now());
            } catch (RuntimeException exception) {
                int retryCount = event.retryCount() + 1;
                eventRepository.markFailed(
                    event.id(),
                    retryCount,
                    Instant.now().plus(Math.min(retryCount * 10L, 300L), ChronoUnit.SECONDS),
                    exception.getMessage()
                );
            }
        }
    }

    private void process(DomainEvent event) {
        if (NOTIFICATION_CREATED.equals(event.eventType())) {
            Map<String, Object> payload = event.payload();
            UUID recipientId = UUID.fromString(payload.get("recipientId").toString());
            UUID targetId = payload.get("targetId") == null ? null : UUID.fromString(payload.get("targetId").toString());
            notificationRepository.create(
                event.workspaceId(),
                recipientId,
                event.actorId(),
                payload.getOrDefault("notificationType", "system").toString(),
                payload.get("title").toString(),
                payload.getOrDefault("body", "").toString(),
                payload.get("targetType") == null ? null : payload.get("targetType").toString(),
                targetId,
                payload.get("webPath") == null ? null : payload.get("webPath").toString(),
                payload.getOrDefault("dedupeKey", event.id().toString()).toString()
            );
        }
        searchIndexService.handleEvent(event);
    }
}
