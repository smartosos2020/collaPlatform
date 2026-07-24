package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.infrastructure.RealtimeSignalRepository;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RealtimeSignalDomainEventHandlerTests {
    @Test
    void storesObservableSignalWithRestCalibration() {
        RealtimeSignalRepository repository = mock(RealtimeSignalRepository.class);
        AtomicReference<TransactionalOutbox.EventEnvelope> transport = new AtomicReference<>();
        TransactionalOutbox outbox = event -> {
            transport.set(event);
            return event.eventId();
        };
        EventMessage event = event("/api/notifications");

        new RealtimeSignalDomainEventHandler(repository, outbox).handle(event);

        ArgumentCaptor<RealtimeSignalEnvelope> envelope = ArgumentCaptor.forClass(RealtimeSignalEnvelope.class);
        verify(repository).create(org.mockito.ArgumentMatchers.eq(event.eventId()), envelope.capture());
        org.assertj.core.api.Assertions.assertThat(envelope.getValue().signalType()).isEqualTo("notification.changed");
        org.assertj.core.api.Assertions.assertThat(envelope.getValue().signalVersion()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(envelope.getValue().audience().recipientId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(envelope.getValue().object().type()).isEqualTo("notification");
        org.assertj.core.api.Assertions.assertThat(envelope.getValue().sequence().value()).isEqualTo(8);
        org.assertj.core.api.Assertions.assertThat(envelope.getValue().calibrationPath()).isEqualTo("/api/notifications");
        org.assertj.core.api.Assertions.assertThat(transport.get().eventType()).isEqualTo("realtime.transport.requested");
        org.assertj.core.api.Assertions.assertThat(transport.get().payload())
            .containsEntry("signalId", envelope.getValue().signalId().toString());
    }

    @Test
    void rejectsNonApiCalibrationPathAsPermanentFailure() {
        RealtimeSignalRepository repository = mock(RealtimeSignalRepository.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        assertThatThrownBy(() -> new RealtimeSignalDomainEventHandler(repository, outbox).handle(event("https://example.test")))
            .isInstanceOf(DomainEventHandlingException.Permanent.class);
    }

    @Test
    void usesDurableRequestAggregateSequenceWhenSourceVersionIsAbsent() {
        RealtimeSignalRepository repository = mock(RealtimeSignalRepository.class);
        TransactionalOutbox outbox = event -> event.eventId();
        EventMessage source = event("/api/notifications");
        Map<String, Object> payload = new java.util.LinkedHashMap<>(source.payload());
        payload.remove("sourceVersion");
        EventMessage request = new EventMessage(
            source.eventId(),
            source.workspaceId(),
            source.eventType(),
            source.eventVersion(),
            source.aggregateType(),
            source.aggregateId(),
            11,
            source.actorId(),
            source.idempotencyKey(),
            source.correlationId(),
            source.causationId(),
            source.occurredAt(),
            payload
        );

        new RealtimeSignalDomainEventHandler(repository, outbox).handle(request);

        ArgumentCaptor<RealtimeSignalEnvelope> envelope = ArgumentCaptor.forClass(RealtimeSignalEnvelope.class);
        verify(repository).create(org.mockito.ArgumentMatchers.eq(request.eventId()), envelope.capture());
        org.assertj.core.api.Assertions.assertThat(envelope.getValue().sequence().value()).isEqualTo(11);
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
