package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.infrastructure.RealtimeSignalRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeSignalDomainEventHandlerTests {
    @Test
    void storesObservableSignalWithRestCalibration() {
        RealtimeSignalRepository repository = mock(RealtimeSignalRepository.class);
        EventMessage event = event("/api/notifications");

        new RealtimeSignalDomainEventHandler(repository).handle(event);

        verify(repository).create(
            org.mockito.ArgumentMatchers.any(UUID.class),
            eq(event.workspaceId()),
            eq(event.eventId()),
            org.mockito.ArgumentMatchers.any(UUID.class),
            eq("notification.changed"),
            eq("notification"),
            eq(event.aggregateId()),
            eq(8L),
            eq("/api/notifications")
        );
    }

    @Test
    void rejectsNonApiCalibrationPathAsPermanentFailure() {
        RealtimeSignalRepository repository = mock(RealtimeSignalRepository.class);
        assertThatThrownBy(() -> new RealtimeSignalDomainEventHandler(repository).handle(event("https://example.test")))
            .isInstanceOf(DomainEventHandlingException.Permanent.class);
    }

    private static EventMessage event(String calibrationPath) {
        UUID objectId = UUID.randomUUID();
        return new EventMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "realtime.signal.requested",
            1,
            "notification",
            objectId,
            1,
            UUID.randomUUID(),
            "realtime-test",
            UUID.randomUUID(),
            null,
            Instant.now(),
            Map.of(
                "recipientId", UUID.randomUUID().toString(),
                "signalType", "notification.changed",
                "objectType", "notification",
                "objectId", objectId.toString(),
                "sourceVersion", 8,
                "calibrationPath", calibrationPath
            )
        );
    }
}
