package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.notification.application.NotificationService;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.notification.infrastructure.NotificationRepository;
import com.colla.platform.modules.search.application.SearchIndexService;
import com.colla.platform.modules.platform.domain.PlatformObjectTypes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DomainEventWorker {
    private static final String NOTIFICATION_CREATED = "notification.created";
    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final DomainEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final SearchIndexService searchIndexService;

    public DomainEventWorker(
        DomainEventRepository eventRepository,
        NotificationRepository notificationRepository,
        NotificationService notificationService,
        SearchIndexService searchIndexService
    ) {
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.searchIndexService = searchIndexService;
    }

    @Scheduled(fixedDelayString = "${colla.events.worker-delay-ms:5000}")
    public void processPendingEvents() {
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch += 1) {
            var events = eventRepository.claimPending(BATCH_SIZE);
            if (events.isEmpty()) {
                return;
            }
            for (DomainEvent event : events) {
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
                payload.get("targetType") == null ? null : PlatformObjectTypes.canonicalize(payload.get("targetType").toString()),
                targetId,
                payload.get("webPath") == null ? null : payload.get("webPath").toString(),
                payload.getOrDefault("dedupeKey", event.id().toString()).toString()
            ).ifPresent(notification -> notificationService.pushCreated(event.workspaceId(), recipientId, notification));
        }
        searchIndexService.handleEvent(event);
    }
}
