package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.infrastructure.RealtimeSignalRepository;
import com.colla.platform.modules.event.infrastructure.RealtimeSignalRepository.StoredRealtimeSignal;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import com.colla.platform.shared.realtime.RealtimeSignalPublisher;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeSignalTransportDomainEventHandlerTests {
    @Test
    void publishesPendingSignalAndMarksItTransported() {
        RealtimeSignalRepository repository = mock(RealtimeSignalRepository.class);
        RealtimeSignalPublisher publisher = mock(RealtimeSignalPublisher.class);
        RealtimeSignalEnvelope envelope = envelope();
        when(repository.find(envelope.signalId()))
            .thenReturn(java.util.Optional.of(new StoredRealtimeSignal(UUID.randomUUID(), envelope, null)));
        when(publisher.publish(envelope)).thenReturn(RealtimeSignalPublisher.PublishResult.published(2));

        new RealtimeSignalTransportDomainEventHandler(repository, publisher).handle(event(envelope.signalId()));

        verify(publisher).publish(envelope);
        verify(repository).markTransported(eq(envelope.signalId()), any(Instant.class));
    }

    @Test
    void leavesRedisFailureForExistingDeliveryRetry() {
        RealtimeSignalRepository repository = mock(RealtimeSignalRepository.class);
        RealtimeSignalPublisher publisher = mock(RealtimeSignalPublisher.class);
        RealtimeSignalEnvelope envelope = envelope();
        when(repository.find(envelope.signalId()))
            .thenReturn(java.util.Optional.of(new StoredRealtimeSignal(UUID.randomUUID(), envelope, null)));
        when(publisher.publish(envelope)).thenReturn(RealtimeSignalPublisher.PublishResult.failed("redis unavailable"));

        assertThatThrownBy(() ->
            new RealtimeSignalTransportDomainEventHandler(repository, publisher).handle(event(envelope.signalId()))
        ).isInstanceOf(DomainEventHandlingException.Transient.class);
        verify(repository, never()).markTransported(any(), any());
    }

    @Test
    void alreadyTransportedSignalIsIdempotent() {
        RealtimeSignalRepository repository = mock(RealtimeSignalRepository.class);
        RealtimeSignalPublisher publisher = mock(RealtimeSignalPublisher.class);
        RealtimeSignalEnvelope envelope = envelope();
        when(repository.find(envelope.signalId()))
            .thenReturn(java.util.Optional.of(new StoredRealtimeSignal(UUID.randomUUID(), envelope, Instant.now())));

        new RealtimeSignalTransportDomainEventHandler(repository, publisher).handle(event(envelope.signalId()));

        verify(publisher, never()).publish(any());
        verify(repository, never()).markTransported(any(), any());
    }

    private static EventMessage event(UUID signalId) {
        return new EventMessage(
            UUID.randomUUID(), UUID.randomUUID(), "realtime.transport.requested", 1,
            "realtime_signal", signalId, 1, UUID.randomUUID(), "transport-test",
            UUID.randomUUID(), null, Instant.now(), Map.of("signalId", signalId.toString())
        );
    }

    private static RealtimeSignalEnvelope envelope() {
        return new RealtimeSignalEnvelope(
            1, "notification.changed", 1, UUID.randomUUID(), UUID.randomUUID(),
            Audience.user(UUID.randomUUID()), new ObjectReference("notification", UUID.randomUUID()),
            new Sequence(SequenceScope.OBJECT, "notification:1", 1), Instant.now(), UUID.randomUUID(),
            "/api/notifications", Map.of()
        );
    }
}
