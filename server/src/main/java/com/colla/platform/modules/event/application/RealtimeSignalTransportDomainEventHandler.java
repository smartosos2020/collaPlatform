package com.colla.platform.modules.event.application;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.infrastructure.RealtimeSignalRepository;
import com.colla.platform.modules.event.infrastructure.RealtimeSignalRepository.StoredRealtimeSignal;
import com.colla.platform.shared.realtime.RealtimeSignalPublisher;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.WORKER, RuntimeRole.COMBINED})
public class RealtimeSignalTransportDomainEventHandler implements DomainEventHandler {
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "realtime.transport",
        1,
        Set.of(new Subscription("realtime.transport.requested", 1)),
        true
    );

    private final RealtimeSignalRepository repository;
    private final RealtimeSignalPublisher publisher;

    public RealtimeSignalTransportDomainEventHandler(
        RealtimeSignalRepository repository,
        RealtimeSignalPublisher publisher
    ) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    @Transactional
    public void handle(EventMessage event) {
        UUID signalId = requiredUuid(event.payload(), "signalId");
        StoredRealtimeSignal signal = repository.find(signalId)
            .orElseThrow(() -> new DomainEventHandlingException.Permanent("Realtime signal does not exist"));
        if (signal.transported()) {
            return;
        }

        RealtimeSignalPublisher.PublishResult result = publisher.publish(signal.envelope());
        if (!result.published()) {
            throw new DomainEventHandlingException.Transient(result.failure());
        }
        repository.markTransported(signalId, Instant.now());
    }

    private static UUID requiredUuid(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid realtime transport " + key);
        }
    }
}
