package com.colla.platform.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.notification.infrastructure.NotificationRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationDomainEventHandlerTests {
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();

    @Test
    void createsNotificationOnceAndPublishesOnlyCalibrationSignalData() {
        NotificationRepository repository = mock(NotificationRepository.class);
        AtomicReference<TransactionalOutbox.EventEnvelope> published = new AtomicReference<>();
        TransactionalOutbox outbox = event -> {
            published.set(event);
            return event.eventId();
        };
        NotificationItem item = new NotificationItem(
            UUID.randomUUID(),
            "project",
            "project",
            "user",
            "Private title",
            "Private body",
            "issue",
            UUID.randomUUID(),
            "/issues/1",
            null,
            Instant.now()
        );
        when(repository.isEnabled(WORKSPACE_ID, RECIPIENT_ID, "project")).thenReturn(true);
        when(repository.create(
            eq(WORKSPACE_ID),
            eq(RECIPIENT_ID),
            any(),
            eq("project"),
            eq("Private title"),
            eq("Private body"),
            eq("issue"),
            any(),
            eq("/issues/1"),
            eq("dedupe-1")
        )).thenReturn(Optional.of(item));

        new NotificationDomainEventHandler(repository, outbox).handle(event(Map.of(
            "recipientId", RECIPIENT_ID.toString(),
            "notificationType", "project",
            "title", "Private title",
            "body", "Private body",
            "targetType", "issue",
            "targetId", item.targetId().toString(),
            "webPath", "/issues/1",
            "dedupeKey", "dedupe-1"
        )));

        assertThat(published.get().eventType()).isEqualTo("realtime.signal.requested");
        assertThat(published.get().payload()).containsEntry("calibrationPath", "/api/notifications");
        assertThat(published.get().payload().keySet()).isEqualTo(Set.of(
            "recipientId",
            "signalType",
            "objectType",
            "objectId",
            "sourceVersion",
            "calibrationPath"
        ));
        assertThat(published.get().payload()).doesNotContainKeys("title", "body");
    }

    @Test
    void disabledPreferenceSkipsNotificationAndSignal() {
        NotificationRepository repository = mock(NotificationRepository.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        when(repository.isEnabled(WORKSPACE_ID, RECIPIENT_ID, "project")).thenReturn(false);

        new NotificationDomainEventHandler(repository, outbox).handle(event(Map.of(
            "recipientId", RECIPIENT_ID.toString(),
            "notificationType", "project",
            "title", "Hidden"
        )));

        verify(repository, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(outbox, never()).append(any(TransactionalOutbox.EventEnvelope.class));
    }

    @Test
    void hashesOversizedDedupeKeysAtTheHandlerBoundary() {
        NotificationRepository repository = mock(NotificationRepository.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        String oversized = "issue.status.changed:" + "x".repeat(160);
        when(repository.isEnabled(WORKSPACE_ID, RECIPIENT_ID, "project")).thenReturn(true);
        when(repository.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Optional.empty());

        new NotificationDomainEventHandler(repository, outbox).handle(event(Map.of(
            "recipientId", RECIPIENT_ID.toString(),
            "notificationType", "project",
            "title", "Changed",
            "dedupeKey", oversized
        )));

        ArgumentCaptor<String> dedupeKey = ArgumentCaptor.forClass(String.class);
        verify(repository).create(
            eq(WORKSPACE_ID), eq(RECIPIENT_ID), any(), eq("project"), eq("Changed"), eq(""),
            any(), any(), any(), dedupeKey.capture()
        );
        assertThat(dedupeKey.getValue()).startsWith("sha256:").hasSize(71);
    }

    private static EventMessage event(Map<String, Object> payload) {
        return new EventMessage(
            UUID.randomUUID(),
            WORKSPACE_ID,
            "notification.created",
            1,
            "issue",
            UUID.randomUUID(),
            7,
            UUID.randomUUID(),
            "notification-test",
            UUID.randomUUID(),
            null,
            Instant.now(),
            payload
        );
    }
}
